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

package org.ddogleg.optimization.math;

import org.ejml.data.DGrowArray;
import org.ejml.data.DMatrixRMaj;
import org.ejml.data.DMatrixSparseCSC;
import org.ejml.data.IGrowArray;
import org.ejml.dense.row.CommonOps_DDRM;
import org.ejml.interfaces.linsol.LinearSolverSparse;
import org.ejml.sparse.FillReducing;
import org.ejml.sparse.csc.CommonOps_DSCC;
import org.ejml.sparse.csc.factory.LinearSolverFactory_DSCC;
import org.ejml.sparse.csc.mult.MatrixVectorMult_DSCC;

/**
 * The approximate Hessian matrix (J'*J) is assumed to have the
 * following block triangle form: [A B;C D]. The system being solved for
 * is as follows: [A B;C D] [x_1;x_2] = [b_1;b_2]. Then the following steps
 * are followed to solve it:
 *
 * <ol>
 *     <li>D' = D - C*inv(A)*B and b_2' = b_2 - C*inv(A)*b_1</li>
 *     <li>D'*x_2 = b_2'</li>
 *     <li>A*x_1 = b_1 - B*x_2</li>
 * </ol>
 *
 * [1] Triggs, Bill, et al. "Bundle adjustment—a modern synthesis." International workshop on vision algorithms.
 * Springer, Berlin, Heidelberg, 1999.
 *
 * @author Peter Abeles
 */
public class HessianSchurComplement_DSCC
		implements HessianSchurComplement<DMatrixSparseCSC>
{
	// Blocks inside the Hessian matrix
	DMatrixSparseCSC A = new DMatrixSparseCSC(1,1);
	DMatrixSparseCSC B = new DMatrixSparseCSC(1,1);
	DMatrixSparseCSC D = new DMatrixSparseCSC(1,1);

	// Workspace variables
	IGrowArray gw = new IGrowArray();
	DGrowArray gx = new DGrowArray();

	DMatrixRMaj b1 = new DMatrixRMaj(1,1);
	DMatrixRMaj b2 = new DMatrixRMaj(1,1);
	DMatrixRMaj b2_m = new DMatrixRMaj(1,1);
	DMatrixRMaj x = new DMatrixRMaj(1,1);
	DMatrixRMaj x1 = new DMatrixRMaj(1,1);
	DMatrixRMaj x2 = new DMatrixRMaj(1,1);
	DMatrixSparseCSC tmp0 = new DMatrixSparseCSC(1,1);
	DMatrixSparseCSC D_m = new DMatrixSparseCSC(1,1);

	// Two solvers are created so that the structure can be saved and not recomputed each iteration
	protected LinearSolverSparse<DMatrixSparseCSC,DMatrixRMaj> solverA, solverD;

	public HessianSchurComplement_DSCC() {
		this( LinearSolverFactory_DSCC.cholesky(FillReducing.NONE),
				LinearSolverFactory_DSCC.cholesky(FillReducing.NONE));
	}

	public HessianSchurComplement_DSCC(LinearSolverSparse<DMatrixSparseCSC, DMatrixRMaj> solverA,
									   LinearSolverSparse<DMatrixSparseCSC, DMatrixRMaj> solverD) {
		this.solverA = solverA;
		this.solverD = solverD;
	}

	@Override
	public void init(int numParameters) {
		// CODE BELOW IS COMMENTED OUT TO PREVENT A BAD IDEA FROM BEING REPEATED
		// In theory, you could lock the structure and avoid the computation. However, one of the
		// matrix multiplications algorithms is a bit too smart and refuses to fill in zeros.
		// This changes the structure! A quick test showed no noticeable change in speed on a large system.
//		solverA.setStructureLocked(false);
//		solverD.setStructureLocked(false);
	}

	/**
	 * Vector matrix inner product of Hessian in block format.
	 *
	 * <p>
	 * [A B;C D]*[x;y] = [c;d]<br>
	 *  A*x + B*y = c<br>
	 *  C*x + D*y = d<br>
	 *  [x;y]<sup>T</sup>[A B;C D]*[x;y] = [x;y]<sup>T</sup>*[c;d]<br>
	 * </p>
	 * @param v row vector
	 * @return v'*H*v = v'*[A B;C D]*v
	 */
	@Override
	public double innerVectorHessian( DMatrixRMaj v ) {
		int M = A.numRows;

		double sum = 0;
		sum += MatrixVectorMult_DSCC.innerProduct(v.data, 0, A, v.data, 0);
		sum += 2*MatrixVectorMult_DSCC.innerProduct(v.data, 0, B, v.data, M);
		sum += MatrixVectorMult_DSCC.innerProduct(v.data, M, D, v.data, M);

		return sum;
	}

	@Override
	public void extractDiagonals(DMatrixRMaj diag) {
		// extract diagonal elements from Hessian matrix
		CommonOps_DSCC.extractDiag(A, x1);
		CommonOps_DSCC.extractDiag(D, x2);

		diag.reshape(A.numCols+D.numCols,1);
		CommonOps_DDRM.insert(x1,diag,0,0);
		CommonOps_DDRM.insert(x2,diag,x1.numRows,0);
	}

	@Override
	public void setDiagonals(DMatrixRMaj diag) {
		int N = A.numCols;
		for (int i = 0; i < N; i++) {
			A.set(i,i, diag.data[i]);
		}
		for (int i = 0; i < D.numCols; i++) {
			D.set(i,i, diag.data[i+N]);
		}
	}

	@Override
	public void divideRowsCols(DMatrixRMaj scaling) {
		double []d = scaling.data;
		CommonOps_DSCC.divideRowsCols(d,0,A,d,0);
		CommonOps_DSCC.divideRowsCols(d,0,B,d,A.numCols);
		CommonOps_DSCC.divideRowsCols(d,A.numRows,D,d,A.numCols);
	}

	@Override
	public boolean initializeSolver() {
		// Don't use quality to reject a solution since it's meaning is too dependent on implementation
		if( !solverA.setA(A) )
			return false;
//		solverA.setStructureLocked(true); // for why this is commented out see above

		return true;
	}

	@Override
	public boolean solve(DMatrixRMaj gradient, DMatrixRMaj step) {
		// extract b1
		CommonOps_DDRM.extract(gradient,0,A.numCols,0,gradient.numCols, b1);
		CommonOps_DDRM.extract(gradient,A.numCols,gradient.numRows,0,gradient.numCols, b2);

		// x=inv(A)*b1
		x.reshape(A.numRows,1);
		solverA.solve(b1,x);
		// b2_m = -b_2 - C*inv(A)*b1 = -b_2 - C*x
		// C = B'
		CommonOps_DSCC.multTransA(B,x,b2_m); // C*x
		CommonOps_DDRM.subtract(b2,b2_m,b2_m); // b2_m = b_2 - C*x

		// D_m = D - C*inv(A)*B = D - B'*inv(A)*B (thus symmetric)
		D_m.reshape(A.numRows,B.numCols);
		solverA.solveSparse(B,D_m); // D_m = inv(A)*B
		CommonOps_DSCC.multTransA(B,D_m,tmp0,gw,gx); // tmp0 = C*D_m = C*inv(A)*B
		CommonOps_DSCC.add(1,D,-1,tmp0,D_m,gw,gx);

		// Reduced System
		// D_m*x_2 = b_2
		if( !solverD.setA(D_m) ) {
			return false;
		}
//		solverD.setStructureLocked(true); // for why this is commented out see above
		x2.reshape(D_m.numRows,b2_m.numCols);
		solverD.solve(b2_m,x2);

		// back-substitution
		// A*x1 = b1-B*x2
		CommonOps_DSCC.mult(B,x2,x1);
		CommonOps_DDRM.subtract(b1,x1,b1);
		solverA.solve(b1,x1);

//		x1.print();
		CommonOps_DDRM.insert(x1,step,0,0);
		CommonOps_DDRM.insert(x2,step,x1.numRows,0);

		return true;
	}

	/**
	 * Compuets the Hessian in block form
	 * @param jacLeft (Input) Left side of Jacobian
	 * @param jacRight (Input) Right side of Jacobian
	 */
	@Override
	public void computeHessian(DMatrixSparseCSC jacLeft , DMatrixSparseCSC jacRight) {
		A.reshape(jacLeft.numCols,jacLeft.numCols,1);
		B.reshape(jacLeft.numCols,jacRight.numCols,1);
		D.reshape(jacRight.numCols,jacRight.numCols,1);

		// take advantage of the inner product's symmetry when possible to reduce
		// the number of calculations
		CommonOps_DSCC.innerProductLower(jacLeft,tmp0,gw,gx);
		CommonOps_DSCC.symmLowerToFull(tmp0,A,gw);
		CommonOps_DSCC.multTransA(jacLeft,jacRight,B,gw,gx);
		CommonOps_DSCC.innerProductLower(jacRight,tmp0,gw,gx);
		CommonOps_DSCC.symmLowerToFull(tmp0,D,gw);
	}

	/**
	 * Computes the gradient using Schur complement
	 *
	 * @param jacLeft (Input) Left side of Jacobian
	 * @param jacRight (Input) Right side of Jacobian
	 * @param residuals (Input) Residuals
	 * @param gradient (Output) Gradient
	 */
	@Override
	public void computeGradient(DMatrixSparseCSC jacLeft , DMatrixSparseCSC jacRight ,
								DMatrixRMaj residuals, DMatrixRMaj gradient) {

		// Find the gradient using the two matrices for Jacobian
		// g = J'*r = [L,R]'*r
		x1.reshape(jacLeft.numCols,1);
		x2.reshape(jacRight.numCols,1);
		CommonOps_DSCC.multTransA(jacLeft,residuals,x1);
		CommonOps_DSCC.multTransA(jacRight,residuals,x2);

		CommonOps_DDRM.insert(x1,gradient,0,0);
		CommonOps_DDRM.insert(x2,gradient,x1.numRows,0);
	}

	@Override
	public DMatrixSparseCSC createMatrix() {
		return new DMatrixSparseCSC(1,1);
	}
}
