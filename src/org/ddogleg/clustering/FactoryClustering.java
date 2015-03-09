/*
 * Copyright (c) 2012-2015, Peter Abeles. All Rights Reserved.
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

package org.ddogleg.clustering;

import org.ddogleg.clustering.gmm.ExpectationMaximizationGmm_F64;
import org.ddogleg.clustering.kmeans.StandardKMeans_F64;

/**
 * Factory for creating clustering algorithms.
 *
 * @author Peter Abeles
 */
public class FactoryClustering {

	// todo move number of iterations and convergence threshold into high level interface?

	public static ExpectationMaximizationGmm_F64 expectMaximGmm( ) {
		return null;
	}

	public static StandardKMeans_F64 kMeans_F64( ) {
		return null;
	}
}
