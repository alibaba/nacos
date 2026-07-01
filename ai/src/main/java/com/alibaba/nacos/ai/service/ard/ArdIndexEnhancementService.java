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

package com.alibaba.nacos.ai.service.ard;

import com.alibaba.nacos.ai.model.ard.ArdChunk;
import com.alibaba.nacos.ai.model.ard.ArdEntry;

import java.util.Collections;
import java.util.List;

/**
 * Optional provider for AI-generated ARD search text.
 *
 * @author nacos
 */
public interface ArdIndexEnhancementService {
    
    /**
     * No-op provider used by tests and disabled deployments.
     */
    ArdIndexEnhancementService NOOP = new ArdIndexEnhancementService() {
        
        @Override
        public boolean enabled() {
            return false;
        }
        
        @Override
        public List<ArdIndexEnhancementChunk> enhance(ArdEntry entry,
            List<ArdChunk> existingChunks) {
            return Collections.emptyList();
        }
    };
    
    /**
     * Whether this provider should run for newly built ARD entries.
     */
    boolean enabled();
    
    /**
     * Generate extra search chunks for one ARD entry.
     */
    List<ArdIndexEnhancementChunk> enhance(ArdEntry entry, List<ArdChunk> existingChunks)
        throws Exception;
    
    /**
     * Generate extra search chunks with optional source content snippets.
     */
    default List<ArdIndexEnhancementChunk> enhance(ArdEntry entry,
        List<ArdChunk> existingChunks, List<ArdIndexEnhancementContent> contents)
        throws Exception {
        return enhance(entry, existingChunks);
    }
}
