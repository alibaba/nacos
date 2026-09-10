/*
 * Copyright 1999-2026 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/**
 * Concurrent bounded LRU map maintained locally by Nacos to avoid a dependency
 * on the entire SOFA Hessian jar for this utility.
 *
 * <p>Source: {@code com.alipay.sofa:hessian:3.3.6}, package
 * {@code com.alipay.hessian.clhm}, SOFA Hessian tag {@code v3.3.6}, commit
 * {@code 8eb8411c113aaaba7a95fb188d646951374a6482}:
 * <a href="https://github.com/sofastack/sofa-hessian/tree/8eb8411c113aaaba7a95fb188d646951374a6482/src/main/java/com/alipay/hessian/clhm">upstream source</a>.
 * The implementation originates from Benjamin Manes' concurrentlinkedhashmap
 * (upstream package documentation identifies {@code 1.2_jdk5}).
 *
 * <p>The five implementation files retain their original copyright and
 * Apache License 2.0 headers. Local changes are limited to package relocation,
 * provenance documentation, and replacement of optional JCIP annotations with
 * comments. The four annotation source files, licensed separately under
 * CC BY 2.5, are not incorporated. The locking, buffering, eviction, weighting,
 * and listener algorithms are unchanged. Only JDK classes are required.
 *
 * <p>Future modifications are maintained by the Nacos project and must be
 * documented here. Keep the implementation close to the pinned source so that
 * changes to concurrency behavior can be reviewed against upstream.
 * Capacity and access ordering retain upstream's best-effort, eventually
 * consistent semantics during concurrent operations.
 */
package com.alibaba.nacos.core.remote.thirdparty.clhm;
