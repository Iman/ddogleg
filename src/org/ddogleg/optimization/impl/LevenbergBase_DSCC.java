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

package org.ddogleg.optimization.impl;

import org.ddogleg.optimization.functions.CoupledJacobian;
import org.ejml.data.DGrowArray;
import org.ejml.data.DMatrixRMaj;
import org.ejml.data.DMatrixSparseCSC;
import org.ejml.data.IGrowArray;
import org.ejml.dense.row.CommonOps_DDRM;
import org.ejml.interfaces.linsol.LinearSolverSparse;

/**
 * Base class for Levenberg solvers which use {@link org.ejml.data.DMatrixSparseCSC}
 *
 * @author Peter Abeles
 */
public abstract class LevenbergBase_DSCC extends LevenbergFuncBase<DMatrixSparseCSC> {

	// jacobian at x. M by N matrix.
	protected DMatrixSparseCSC jacobianVals = new DMatrixSparseCSC(1,1);
	protected DMatrixSparseCSC tmp = new DMatrixSparseCSC(1,1);

	// Jacobian inner product. Used to approximate Hessian
	// B=J'*J
	protected DMatrixSparseCSC B = new DMatrixSparseCSC(1,1);

	// Workspace variables
	IGrowArray gw = new IGrowArray();
	DGrowArray gx = new DGrowArray();

	// solver used to compute (A + mu*diag(A))d = g
	protected LinearSolverSparse<DMatrixSparseCSC,DMatrixRMaj> solver;

	public LevenbergBase_DSCC(double initialDampParam) {
		super(initialDampParam);
	}

	@Override
	public void initialize(double[] initial) {
		super.initialize(initial);

		// Structure only needs to be computed once when optimizing
		// after each solve the structure is locked so this effectively tells it
		// to recompute the structure the first time
		solver.setStructureLocked(false);
	}

	@Override
	protected double getMinimumDampening() {
		return CommonOps_DDRM.elementMax(Bdiag);
	}

	/**
	 * Specifies function being optimized.
	 *
	 * @param function Computes residuals and Jacobian.
	 */
	@Override
	public void setFunction( CoupledJacobian<DMatrixSparseCSC> function ) {
		internalInitialize(function.getNumOfInputsN(),function.getNumOfOutputsM());
		this.function = function;

		jacobianVals.reshape(M,N,M);

		B.reshape(N, N, N);
		Bdiag.reshape(N,1);
	}
}
