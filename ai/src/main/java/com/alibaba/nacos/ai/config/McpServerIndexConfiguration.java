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

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;

import com.alibaba.nacos.ai.index.CachedMcpServerIndex;
import com.alibaba.nacos.ai.index.McpCacheIndex;
import com.alibaba.nacos.ai.index.McpServerIndex;
import com.alibaba.nacos.ai.index.MemoryMcpCacheIndex;
import com.alibaba.nacos.ai.index.PlainMcpServerIndex;
import com.alibaba.nacos.config.server.service.ConfigDetailService;
import com.alibaba.nacos.config.server.service.query.ConfigQueryChainService;
import com.alibaba.nacos.core.service.NamespaceOperationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * MCP server index configuration class.
 *
 * @author misselvexu
 */
@Configuration
@EnableConfigurationProperties(McpCacheIndexProperties.class)
public class McpServerIndexConfiguration {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(McpServerIndexConfiguration.class);
    
    private final McpCacheIndexProperties cacheProperties;
    
    public McpServerIndexConfiguration(McpCacheIndexProperties cacheProperties) {
        this.cacheProperties = cacheProperties;
    }
    
    /**
     * Create memory cache index Bean.
     */
    @Bean
    @ConditionalOnProperty(name = "nacos.mcp.cache.enabled", havingValue = "true", matchIfMissing = true)
    public McpCacheIndex mcpCacheIndex() {
        LOGGER.info("Creating McpCacheIndex bean with maxSize={}, expireTime={}s, cleanupInterval={}s",
                cacheProperties.getMaxSize(), cacheProperties.getExpireTimeSeconds(),
                cacheProperties.getCleanupIntervalSeconds());
        return new MemoryMcpCacheIndex(cacheProperties);
    }
    
    /**
     * Create scheduled task executor Bean.
     */
    @Bean
    @ConditionalOnProperty(name = "nacos.mcp.cache.enabled", havingValue = "true", matchIfMissing = true)
    public ScheduledExecutorService mcpCacheScheduledExecutor() {
        LOGGER.info("Creating ScheduledExecutorService for MCP cache with syncInterval={}s",
                cacheProperties.getSyncIntervalSeconds());
        // Manually create thread pool, following Alibaba coding standards
        return new ScheduledThreadPoolExecutor(1, r -> {
            Thread t = new Thread(r, "mcp-cache-sync");
            t.setDaemon(true);
            return t;
        }, new ThreadPoolExecutor.CallerRunsPolicy());
    }
    
    /**
     * Create the primary MCP server index Bean when cache is enabled.
     */
    @Bean
    @Primary
    @ConditionalOnProperty(name = "nacos.mcp.cache.enabled", havingValue = "true", matchIfMissing = true)
    public McpServerIndex cachedMcpServerIndex(ConfigDetailService configDetailService,
            NamespaceOperationService namespaceOperationService, ConfigQueryChainService configQueryChainService,
            McpCacheIndex mcpCacheIndex, ScheduledExecutorService mcpCacheScheduledExecutor) {
        LOGGER.info("Creating CachedMcpServerIndex bean with cache enabled");
        return new CachedMcpServerIndex(configDetailService, namespaceOperationService, configQueryChainService,
                mcpCacheIndex, mcpCacheScheduledExecutor, cacheProperties.isEnabled(),
                cacheProperties.getSyncIntervalSeconds());
    }
    
    /**
     * Create the primary MCP server index Bean when cache is disabled.
     */
    @Bean
    @Primary
    @ConditionalOnProperty(name = "nacos.mcp.cache.enabled", havingValue = "false")
    public McpServerIndex plainMcpServerIndex(ConfigDetailService configDetailService,
            NamespaceOperationService namespaceOperationService, ConfigQueryChainService configQueryChainService) {
        LOGGER.info("Creating PlainMcpServerIndex bean as cache is disabled");
        return new PlainMcpServerIndex(namespaceOperationService, configDetailService, configQueryChainService);
    }
} 