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

import com.alibaba.nacos.ai.model.search.AiResourceSearchChunk;
import com.alibaba.nacos.ai.model.search.AiResourceSearchDocument;

import java.util.Collections;
import java.util.List;

/**
 * Optional provider for AI-generated AI resource search text.
 *
 * @author nacos
 */
public interface AiResourceIndexEnhancementService {
    
    /**
     * No-op provider used by tests and disabled deployments.
     */
    AiResourceIndexEnhancementService NOOP = new AiResourceIndexEnhancementService() {
        
        @Override
        public boolean ready() {
            return false;
        }
        
        @Override
        public List<AiResourceIndexEnhancementChunk> enhance(AiResourceSearchDocument entry,
            List<AiResourceSearchChunk> existingChunks) {
            return Collections.emptyList();
        }
    };
    
    /**
     * Whether enhancement is requested and the provider configuration is complete.
     */
    boolean ready();
    
    /**
     * Whether enhancement is requested by the operator, including temporarily invalid settings.
     */
    default boolean requested() {
        return ready();
    }
    
    /**
     * Stable audit fingerprint of the effective enhancement configuration.
     */
    default String fingerprint() {
        return getClass().getName();
    }
    
    /**
     * Generate extra search chunks for one search document.
     */
    List<AiResourceIndexEnhancementChunk> enhance(AiResourceSearchDocument entry,
        List<AiResourceSearchChunk> existingChunks)
        throws Exception;
    
    /**
     * Generate extra search chunks with optional source content snippets.
     */
    default List<AiResourceIndexEnhancementChunk> enhance(AiResourceSearchDocument entry,
        List<AiResourceSearchChunk> existingChunks,
        List<AiResourceIndexEnhancementContent> contents)
        throws Exception {
        return enhance(entry, existingChunks);
    }
    
    /**
     * Generate chunks and return the exact configuration fingerprint used by this invocation.
     */
    default AiResourceIndexEnhancementResult enhanceWithResult(AiResourceSearchDocument entry,
        List<AiResourceSearchChunk> existingChunks,
        List<AiResourceIndexEnhancementContent> contents)
        throws Exception {
        return new AiResourceIndexEnhancementResult(
            enhance(entry, existingChunks, contents), fingerprint());
    }
}
