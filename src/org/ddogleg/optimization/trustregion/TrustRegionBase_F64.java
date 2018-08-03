/*
 * Copyright (c) 2012-2018, Peter Abeles. All Rights Reserved.
 *
 * This file is part of DDogleg (http://ddogleg.org).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.ddogleg.optimization.trustregion;

import org.ddogleg.optimization.OptimizationException;
import org.ejml.UtilEjml;
import org.ejml.data.DMatrix;
import org.ejml.data.DMatrixRMaj;
import org.ejml.dense.row.CommonOps_DDRM;
import org.ejml.dense.row.NormOps_DDRM;

import java.util.Arrays;

import static java.lang.Math.*;

/**
 * <p>Base class for all trust region implementations. The Trust Region approach assumes that a quadratic model is valid
 * within the trust region. At each iteration the Trust Region's subproblem is solved for and a new state is selected.
 * Depending on how accurately the quadratic model predicted the new score the size of the region will be increased
 * or decreased. This implementation is primarily based on [1] and is fully described in the DDogleg Technical
 * Report [2].</p>
 *
 * <p>
 *     Scaling can be optionally turned on. By default it is off. If scaling is turned on then a non symmetric
 *     trust region is used. The length of each axis is determined by the absolute value of diagonal elements in
 *     the hessian. Minimum and maximum possible scaling values are an important tuning parameter.
 * </p>
 *
 * <ul>
 * <li>[1] Jorge Nocedal,and Stephen J. Wright "Numerical Optimization" 2nd Ed. Springer 2006</li>
 * <li>[2] JK. Madsen and H. B. Nielsen and O. Tingleff, "Methods for Non-Linear Least Squares Problems (2nd ed.)"
 * Informatics and Mathematical Modelling, Technical University of Denmark</li>
 * <li>[3] Peter Abeles, "DDogleg Technical Report: Nonlinear Optimization R1", August 2018</li>
 * </ul>
 *
 * @see ConfigTrustRegion
 *
 * @author Peter Abeles
 */
public abstract class TrustRegionBase_F64<S extends DMatrix> {

	// Technique used to compute the change in parameters
	protected ParameterUpdate<S> parameterUpdate;

	// Math for some matrix operations
	protected OptimizationMath<S> math;

	/**
	 * Storage for the gradient
	 */
	protected DMatrixRMaj gradient = new DMatrixRMaj(1,1);
	/**
	 * F-norm of the gradient
	 */
	protected double gradientNorm;

	/**
	 * Storage for the Hessian. Update algorithms should not modify the Hessian
	 */
	protected S hessian;
	// NOTE: This could be stored as a cholesky decomposition

	// Number of parameters being optimized
	int numberOfParameters;

	// Current parameter state
	protected DMatrixRMaj x = new DMatrixRMaj(1,1);
	// proposed next state of parameters
	protected DMatrixRMaj x_next = new DMatrixRMaj(1,1);
	// proposed relative change in parameter's state
	protected DMatrixRMaj p = new DMatrixRMaj(1,1);
	protected DMatrixRMaj tmp_p = new DMatrixRMaj(1,1);

	// Is the value of x being passed in for the hessian the same as the value of x used to compute the cost
	protected boolean sameStateAsCost;

	// Scaling used to compensate for poorly scaled variables
	protected DMatrixRMaj scaling = new DMatrixRMaj(1,1);

	protected ConfigTrustRegion config = new ConfigTrustRegion();

	// error function at x
	protected double fx;

	// size of the current trust region
	double regionRadius;

	// which processing step it's on
	protected Mode mode = Mode.FULL_STEP;

	// number of each type of step it has taken
	protected int totalFullSteps, totalRetries;

	// print additional debugging messages to standard out
	protected boolean verbose;

	public TrustRegionBase_F64(ParameterUpdate<S> parameterUpdate, OptimizationMath<S> math ) {
		this();
		this.parameterUpdate = parameterUpdate;
		this.math = math;
		this.hessian = math.createMatrix();
	}

	protected TrustRegionBase_F64() {
		// so that the RNG gets set up correctly
		configure(config);
	}

	/**
	 * Specifies initial state of the search and completion criteria
	 *
	 * @param initial Initial parameter state
	 * @param numberOfParameters Number many parameters are being optimized.
	 * @param minimumFunctionValue The minimum possible value that the function can output
	 */
	public void initialize(double initial[] , int numberOfParameters , double minimumFunctionValue ) {
		this.numberOfParameters = numberOfParameters;

		x.reshape(numberOfParameters,1);
		x_next.reshape(numberOfParameters,1);
		p.reshape(numberOfParameters,1);
		tmp_p.reshape(numberOfParameters,1);
		gradient.reshape(numberOfParameters,1);

		// initialize scaling to 1, which is no scaling
		scaling.reshape(numberOfParameters,1);
		Arrays.fill(scaling.data,0,numberOfParameters,1);

		System.arraycopy(initial,0,x.data,0,numberOfParameters);
		fx = cost(x);
		sameStateAsCost = true;

		totalFullSteps = 0;
		totalRetries = 0;

		regionRadius = config.regionInitial;

		// a perfect initial guess is a pathological case. easiest to handle it here
		if( fx <= minimumFunctionValue ) {
			mode = Mode.CONVERGED;
		} else {
			mode = Mode.FULL_STEP;
		}

		this.parameterUpdate.initialize(this,numberOfParameters, minimumFunctionValue);
	}

	/**
	 * Performs one iteration
	 *
	 * @return true if it has converged or false if not
	 */
	public boolean iterate() {
		boolean converged;
		switch( mode ) {
			case FULL_STEP:
				totalFullSteps++;
				converged = updateState();
				if( !converged )
					converged = computeAndConsiderNew();
				break;

			case RETRY:
				totalRetries++;
				converged = computeAndConsiderNew();
				break;

			case CONVERGED:
				return true;

			default:
				throw new RuntimeException("BUG! mode="+mode);
		}

		if( converged ) {
			mode = Mode.CONVERGED;
			return true;
		} else {
			return false;
		}
	}

	/**
	 * Computes all the derived data structures and attempts to update the parameters
	 * @return true if it has converged.
	 */
	protected boolean updateState() {
		functionGradientHessian(x,sameStateAsCost,gradient,hessian);

		if( isScaling() ) {
			computeScaling();
			applyScaling();
		}

		// Convergence should be tested on scaled variables to remove their arbitrary natural scale
		// from influencing convergence
		if( checkConvergenceGTest(gradient))
			return true;

		gradientNorm = NormOps_DDRM.normF(gradient);
		if(UtilEjml.isUncountable(gradientNorm))
			throw new OptimizationException("Uncountable. gradientNorm="+gradientNorm);

		parameterUpdate.initializeUpdate();
		return false;
	}

	/**
	 * Sets scaling to the sqrt() of the diagonal elements in the Hessian matrix
	 */
	protected void computeScaling() {
		math.extractDiag(hessian,scaling.data);
		computeScaling(scaling, config.scalingMinimum, config.scalingMaximum);
	}

	/**
	 * Applies the standard formula for computing scaling. This is broken off into its own
	 * function so that it easily invoked if the function above is overriden
	 */
	public void computeScaling( DMatrixRMaj scaling , double minimum , double maximum ) {
		for (int i = 0; i < scaling.numRows; i++) {
			// mathematically it should never be negative but...
			double scale = sqrt(abs(scaling.data[i]));
			// clamp the scale factor
			scaling.data[i] = min(maximum, max(minimum, scale));
		}
	}

	/**
	 * Apply scaling to gradient and Hessian
	 */
	protected void applyScaling() {
		CommonOps_DDRM.elementDiv(gradient,scaling);

		math.divideRows(scaling.data,hessian);
		math.divideColumns(scaling.data,hessian);
	}

	/**
	 * Undo scaling on estimated parameters
	 */
	protected void undoScalingOnParameters( DMatrixRMaj p ) {
		CommonOps_DDRM.elementDiv(p,scaling);
	}

	/**
	 * Changes the trust region's size and attempts to do a step again
	 * @return true if it has converged.
	 */
	protected boolean computeAndConsiderNew() {
		// If first iteration and automatic
		if( regionRadius == -1 ) {
			// user has selected unconstrained method for initial step size
			parameterUpdate.computeUpdate(p, Double.MAX_VALUE);
			regionRadius = parameterUpdate.getStepLength();

			if( regionRadius == Double.MAX_VALUE || UtilEjml.isUncountable(regionRadius)) {
				if( verbose )
					System.out.println("unconstrained initialization failed. Using Cauchy initialization instead.");
				regionRadius = -2;
			} else {
				if( verbose )
					System.out.println("unconstrained initialization radius="+regionRadius);
			}
		}
		if( regionRadius == -2 ) {
			// User has selected Cauchy method for initial step size
			regionRadius = solveCauchyStepLength()*10;
			parameterUpdate.computeUpdate(p, regionRadius);
			if( verbose )
				System.out.println("cauchy initialization radius="+regionRadius);

		} else {
			parameterUpdate.computeUpdate(p, regionRadius);
		}

		if( isScaling() )
			undoScalingOnParameters(p);
		CommonOps_DDRM.add(x,p,x_next);
		double fx_candidate = cost(x_next);

		// this notes that the cost was computed at x_next for the Hessian calculation.
		// This is a relic from a variant on this implementation where another candidate might be considered. I'm
		// leaving this code where since it might be useful in the future and doesn't add much complexity
		sameStateAsCost = true;

		// NOTE: step length was computed using the weighted/scaled version of 'p', which is correct
		Convergence result = considerCandidate(fx_candidate,fx,
				parameterUpdate.getPredictedReduction(),
				parameterUpdate.getStepLength());

		// The new state has been accepted. See if it has converged and change the candidate state to the actual state
		if ( result != Convergence.REJECT ) {
			boolean converged = checkConvergenceFTest(fx_candidate,fx);
			return acceptNewState(converged,fx_candidate);
		} else {
			mode = Mode.RETRY;
			return false;
		}
	}

	protected boolean acceptNewState(boolean converged , double fx_candidate) {
		// Assign values from candidate to current state
		fx = fx_candidate;

		DMatrixRMaj tmp = x;
		x = x_next;
		x_next = tmp;

		// Update the state
		if( converged ) {
			mode = Mode.CONVERGED;
			return true;
		} else {
			mode = Mode.FULL_STEP;
			return false;
		}
	}

	protected double solveCauchyStepLength() {
		double gBg = math.innerProductVectorMatrix(gradient,hessian);

		return gradientNorm*gradientNorm/gBg;
	}

	/**
	 * Consider updating the system with the change in state p. The update will never
	 * be accepted if the cost function increases.
	 *
	 * @param fx_candidate Actual score at the candidate 'x'
	 * @param fx_prev  Score at the current 'x'
	 * @param predictedReduction Reduction in score predicted by quadratic model
	 * @param stepLength The length of the step, i.e. |p|
	 * @return true if it should update the state or false if it should try agian
	 */
	protected Convergence considerCandidate(double fx_candidate, double fx_prev,
											double predictedReduction, double stepLength ) {

		// compute model prediction accuracy
		double actualReduction = fx_prev-fx_candidate;

		if( actualReduction == 0 || predictedReduction == 0 ) {
			if( verbose )
				System.out.println(totalFullSteps+" reduction of zero");
			return Convergence.ACCEPT;
		}

		double ratio = actualReduction/predictedReduction;

		if( fx_candidate > fx_prev || ratio < 0.25 ) {
			// if the improvement is too small (or not an improvement) reduce the region size
			regionRadius = 0.5*regionRadius;
		} else {
			if( ratio > 0.75 ) {
				regionRadius = min(max(3*stepLength,regionRadius),config.regionMaximum);
			}
		}

		if( verbose )
			System.out.println(totalFullSteps+" fx_candidate="+fx_candidate+" ratio="+ratio+" region="+regionRadius);

//		System.out.println(totalRetries+" ratio="+ratio+"   rate="+((fx_prev-fx_candidate)/stepLength));
		if( fx_candidate < fx_prev && ratio > 0 ) {
			return Convergence.ACCEPT;
		} else
			return Convergence.REJECT;
	}


	/**
	 * <p>Checks for convergence using f-test. f-test is defined differently for different problems</p>
	 *
	 * @return true if converged or false if it hasn't converged
	 */
	protected abstract boolean checkConvergenceFTest(double fx, double fx_prev );

	/**
	 * <p>Checks for convergence using f-test:</p>
	 *
	 * g-test : gtol &le; ||g(x)||_inf
	 *
	 * @return true if converged or false if it hasn't converged
	 */
	protected boolean checkConvergenceGTest( DMatrixRMaj g ) {
		return CommonOps_DDRM.elementMaxAbs(g) <= config.gtol;
	}

	/**
	 * Computes the function's value at x
	 * @param x parameters
	 * @return function value
	 */
	protected abstract double cost( DMatrixRMaj x );

	/**
	 * Computes the gradient and Hessian at 'x'. If sameStateAsCost is true then it can be assumed that 'x' has
	 * not changed since the cost was last computed.
	 * @param gradient (Input) x
	 * @param gradient (Output) gradient
	 * @param gradient (Output) gradient
	 * @param hessian (Output) hessian
	 */
	protected abstract void functionGradientHessian(DMatrixRMaj x , boolean sameStateAsCost , DMatrixRMaj gradient , S hessian);

	/**
	 * Computes predicted reduction for step 'p'
	 *
	 * @param p Change in state or the step
	 * @return predicted reduction in quadratic model
	 */
	public double computePredictedReduction( DMatrixRMaj p ) {
		return -CommonOps_DDRM.dot(gradient,p) - 0.5*math.innerProductVectorMatrix(p,hessian);
	}

	public interface ParameterUpdate<S extends DMatrix> {

		/**
		 * Must call this function first. Specifies the number of parameters that are being optimized.
		 *
		 * @param minimumFunctionValue The minimum possible value that the function can output
		 */
		void initialize ( TrustRegionBase_F64<S> base , int numberOfParameters,
						  double minimumFunctionValue );

		/**
		 * Initialize the parameter update. This is typically where all the expensive computations take place
		 *
		 * Useful internal class variables:
		 * <ul>
		 *     <li>{@link #x} current state</li>
		 *     <li>{@link #gradient} Gradient a x</li>
		 *     <li>{@link #hessian} Hessian at x</li>
		 * </ul>
		 *
		 * Inputs are not passed in explicitly since it varies by implementation which ones are needed.
		 */
		void initializeUpdate();

		/**
		 * Compute the value of p given a new parameter state x and the region radius.
		 *
		 * @param p (Output) change in state
		 * @param regionRadius (Input) Radius of the region
		 */
		void computeUpdate(DMatrixRMaj p , double regionRadius );

		/**
		 * <p>
		 *     Returns the predicted reduction from the quadratic model.<br><br>
		 * 	   reduction = m(0) - m(p) = -g(0)*p - 0.5*p<sup>T</sup>*H(0)*p
		 * </p>
		 *
		 * <p>This computation is done inside the update because it can often be done more
		 * efficiently without repeating previous computations</p>
		 *
		 */
		double getPredictedReduction();

		/**
		 * This function returns ||p||.
		 *
		 * <p>This computation is done inside the update because it can often be done more
		 * efficiently without repeating previous computations</p>
		 * @return step length
		 */
		double getStepLength();

		void setVerbose( boolean verbose );
	}

	protected enum Mode {
		FULL_STEP,
		RETRY,
		CONVERGED
	}

	protected enum Convergence {
		REJECT,
		ACCEPT
	}

	/**
	 * True if scaling is turned on
	 */
	public boolean isScaling() {
		return config.scalingMaximum > config.scalingMinimum;
	}

	public void setVerbose( boolean verbose ) {
		this.verbose = verbose;
		this.parameterUpdate.setVerbose(verbose);
	}

	public void configure(ConfigTrustRegion config) {
		if( config.regionInitial <= 0 && (config.regionInitial != -1 && config.regionInitial != -2 ))
			throw new IllegalArgumentException("Invalid regionInitial. Read javadoc and try again.");
		this.config = config.copy();
	}

	public ConfigTrustRegion getConfig() {
		return config;
	}
}
