/*
 * Copyright 2010 Benjamin Manes
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 * Modified by the Nacos project: relocated the package, documented provenance,
 * and replaced optional JCIP annotations with comments. Runtime code is unchanged.
 */
package com.alibaba.nacos.core.remote.thirdparty.clhm;

/**
 * Copied from SOFA Hessian 3.3.6, originally the concurrentlinkedhashmap
 * implementation by Benjamin Manes, under the Apache License, Version 2.0.
 * Nacos needs this implementation without depending on the entire Hessian jar;
 * this relocated copy is maintained and modified by the Nacos project.
 * See <a href="package-summary.html">the package documentation</a> for the exact source and local changes.
 * <p>
 * A class that can determine the weight of a value. The total weight threshold
 * is used to determine when an eviction is required.
 *
 * @author ben.manes@gmail.com (Ben Manes)
 * @see <a href="http://code.google.com/p/concurrentlinkedhashmap/">
 *      http://code.google.com/p/concurrentlinkedhashmap/</a>
 */
// Thread-safe.
public interface Weigher<V> {

    /**
     * Measures an object's weight to determine how many units of capacity that
     * the value consumes. A value must consume a minimum of one unit.
     *
     * @param value the object to weigh
     * @return the object's weight
     */
    int weightOf(V value);
}
