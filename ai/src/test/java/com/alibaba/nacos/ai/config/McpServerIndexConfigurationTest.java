/*
 * Copyright 1999-2025 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.ai.config;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.alibaba.nacos.ai.index.CachedMcpServerIndex;
import com.alibaba.nacos.ai.index.McpServerIndex;
import com.alibaba.nacos.ai.index.PlainMcpServerIndex;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/**
 * Unit tests for McpServerIndexConfiguration.
 */
class McpServerIndexConfigurationTest {
    
    @Nested
    @SpringBootTest(classes = McpServerIndexConfiguration.class)
    @EnableAutoConfiguration
    @TestPropertySource(properties = {"nacos.mcp.cache.enabled=true"})
    class CacheEnabled {
        
        @Autowired(required = false)
        private McpServerIndex mcpServerIndex;
        
        @Test
        void shouldInjectCachedMcpServerIndexWhenCacheEnabled() {
            assertNotNull(mcpServerIndex, "McpServerIndex should be injected");
            assertTrue(mcpServerIndex instanceof CachedMcpServerIndex,
                    "Should be CachedMcpServerIndex when cache enabled");
        }
    }
    
    @Nested
    @SpringBootTest(classes = McpServerIndexConfiguration.class)
    @EnableAutoConfiguration
    @TestPropertySource(properties = {"nacos.mcp.cache.enabled=false"})
    class CacheDisabled {
        
        @Autowired(required = false)
        private McpServerIndex mcpServerIndex;
        
        @Test
        void shouldInjectPlainMcpServerIndexWhenCacheDisabled() {
            assertNotNull(mcpServerIndex, "McpServerIndex should be injected");
            assertTrue(mcpServerIndex instanceof PlainMcpServerIndex,
                    "Should be PlainMcpServerIndex when cache disabled");
        }
    }
} 