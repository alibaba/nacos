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

package com.alibaba.nacos.client.ai.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class CacheKeyUtilsTest {
    
    @Test
    void buildPromptKeyShouldUseLabelFirst() {
        String key = CacheKeyUtils.buildPromptKey("p1", "1.0.0", "prod");
        assertEquals("p1::label:prod", key);
    }
    
    @Test
    void buildPromptKeyShouldUseVersionWhenNoLabel() {
        String key = CacheKeyUtils.buildPromptKey("p1", "1.0.0", null);
        assertEquals("p1::version:1.0.0", key);
    }
    
    @Test
    void buildPromptKeyShouldUseLatestWhenNoLabelAndVersion() {
        String key = CacheKeyUtils.buildPromptKey("p1", null, null);
        assertEquals("p1::latest", key);
    }
    
    @Test
    void buildAgentSpecKeyShouldReturnNameItself() {
        String key = CacheKeyUtils.buildAgentSpecKey("my-agent-spec");
        assertEquals("my-agent-spec", key);
    }
    
    @Test
    void buildSkillKeyShouldReturnNameItself() {
        String key = CacheKeyUtils.buildSkillKey("my-skill");
        assertEquals("my-skill", key);
    }
    
    @Test
    void buildPromptKeySingleArgShouldReturnKeyItself() {
        String key = CacheKeyUtils.buildPromptKey("p1");
        assertEquals("p1", key);
    }
    
    @Test
    void buildMcpServerKeyWithBlankVersion() {
        assertEquals("mcp::latest", CacheKeyUtils.buildMcpServerKey("mcp", null));
        assertEquals("mcp::latest", CacheKeyUtils.buildMcpServerKey("mcp", ""));
        assertEquals("mcp::1.0.0", CacheKeyUtils.buildMcpServerKey("mcp", "1.0.0"));
    }
    
    @Test
    void buildAgentCardKeyWithBlankVersion() {
        assertEquals("a1::latest", CacheKeyUtils.buildAgentCardKey("a1", null));
        assertEquals("a1::1.0.0", CacheKeyUtils.buildAgentCardKey("a1", "1.0.0"));
    }
    
    @Test
    void constructorIsAccessible() {
        // Ensure default constructor counts toward class coverage.
        assertNotNull(new CacheKeyUtils());
    }
}
