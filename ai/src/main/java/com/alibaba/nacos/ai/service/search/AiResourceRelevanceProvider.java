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

import java.util.List;
import java.util.Map;

/**
 * Batch relevance evaluation extension point. Scores are independent per candidate.
 * @author nacos
 */
public interface AiResourceRelevanceProvider {
    
    /**
     * Describe provider capability.
     * @return unique provider name
     */
    String name();
    
    /**
     * Describe provider capability.
     * @return maximum candidates accepted in one call
     */
    int maxBatchSize();
    
    /**
     * Describe provider capability.
     * @return maximum UTF-8 input bytes accepted in one call
     */
    int maxInputBytes();
    
    /**
     * Describe provider capability.
     * @return version of the provider's prompt and output contract
     */
    String contractVersion();
    
    /**
     * Evaluate declared capabilities, with exactly one finite 0..1 score per ID.
     * @param query user query
     * @param candidates minimal candidate payloads, each containing an opaque id
     * @param configuration immutable provider settings
     * @param deadlineNanos absolute monotonic deadline shared by all batches
     * @return scores keyed by candidate id
     * @throws Exception when the whole batch cannot be evaluated
     */
    Map<String, Double> evaluateBatch(String query, List<Map<String, String>> candidates,
        AiResourceRelevanceConfiguration configuration, long deadlineNanos) throws Exception;
}
