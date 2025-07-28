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

import com.alibaba.nacos.ai.index.CachedMcpServerIndex;
import com.alibaba.nacos.ai.index.McpServerIndex;
import com.alibaba.nacos.ai.index.PlainMcpServerIndex;
import com.alibaba.nacos.config.server.service.ConfigDetailService;
import com.alibaba.nacos.config.server.service.query.ConfigQueryChainService;
import com.alibaba.nacos.core.service.NamespaceOperationService;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

/**
 * Unit tests for McpServerIndexConfiguration.
 */
class McpServerIndexConfigurationTest {
    
    @Configuration
    static class TestConfig {
        
        @Bean
        public ConfigDetailService configDetailService() {
            return mock(ConfigDetailService.class);
        }
        
        @Bean
        public NamespaceOperationService namespaceOperationService() {
            return mock(NamespaceOperationService.class);
        }
        
        @Bean
        public ConfigQueryChainService configQueryChainService() {
            return mock(ConfigQueryChainService.class);
        }
    }
    
    @Nested
    @ExtendWith(SpringExtension.class)
    @ContextConfiguration(classes = {McpServerIndexConfiguration.class, TestConfig.class})
    @TestPropertySource(properties = {"nacos.mcp.cache.enabled=true"})
    class CacheEnabled {
        
        @org.springframework.beans.factory.annotation.Autowired(required = false)
        private McpServerIndex mcpServerIndex;
        
        @Test
        void shouldInjectCachedMcpServerIndexWhenCacheEnabled() {
            assertNotNull(mcpServerIndex, "McpServerIndex should be injected");
            assertInstanceOf(CachedMcpServerIndex.class, mcpServerIndex,
                    "Should be CachedMcpServerIndex when cache enabled");
        }
    }
    
    @Nested
    @ExtendWith(SpringExtension.class)
    @ContextConfiguration(classes = {McpServerIndexConfiguration.class, TestConfig.class})
    @TestPropertySource(properties = {"nacos.mcp.cache.enabled=false"})
    class CacheDisabled {
        
        @org.springframework.beans.factory.annotation.Autowired(required = false)
        private McpServerIndex mcpServerIndex;
        
        @Test
        void shouldInjectPlainMcpServerIndexWhenCacheDisabled() {
            assertNotNull(mcpServerIndex, "McpServerIndex should be injected");
            assertInstanceOf(PlainMcpServerIndex.class, mcpServerIndex,
                    "Should be PlainMcpServerIndex when cache disabled");
        }
    }
} 