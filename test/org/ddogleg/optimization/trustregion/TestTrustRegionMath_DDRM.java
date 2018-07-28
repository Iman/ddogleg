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

import org.ejml.data.DMatrixRMaj;

/**
 * @author Peter Abeles
 */
public class TestTrustRegionMath_DDRM extends StandardTrustRegionMathChecks<DMatrixRMaj> {
	public TestTrustRegionMath_DDRM() {
		super(new TrustRegionMath_DDRM());
	}

	@Override
	public DMatrixRMaj convertA(DMatrixRMaj A) {
		return A.copy();
	}

	@Override
	public DMatrixRMaj convertB(DMatrixRMaj A) {
		return A.copy();
	}

	@Override
	public DMatrixRMaj create(int numRows, int numCols) {
		return new DMatrixRMaj(numRows,numCols);
	}
}