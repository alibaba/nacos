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

package com.alibaba.nacos.ai.service.search;

/**
 * Maintains cluster-shared convergence readiness for one search projection generation.
 *
 * @author Nacos
 */
public interface AiResourceSearchReadinessService {
    
    AiResourceSearchReadinessService NOOP = new AiResourceSearchReadinessService() {
        
        @Override
        public boolean isReady(String resourceType, int projectionVersion) {
            return false;
        }
        
        @Override
        public void recordCompletedScan(String resourceType, int projectionVersion,
            boolean clean) {
        }
    };
    
    /**
     * Check whether the exact projection generation has converged.
     */
    boolean isReady(String resourceType, int projectionVersion);
    
    /**
     * Record one complete all-namespace scan.
     *
     * <p>The first scan establishes verification state. A later clean scan may mark the
     * generation ready after unfinished durable tasks have drained.</p>
     */
    void recordCompletedScan(String resourceType, int projectionVersion, boolean clean);
}
