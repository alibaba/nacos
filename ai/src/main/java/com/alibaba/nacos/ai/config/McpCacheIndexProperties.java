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

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * MCP Cache Index configuration properties. Centralized configuration management for MCP cache index related settings.
 *
 * @author misselvexu
 */
@ConfigurationProperties(prefix = "nacos.mcp.cache")
public class McpCacheIndexProperties {
    
    /**
     * Whether MCP cache is enabled.
     */
    private boolean enabled = true;
    
    /**
     * Maximum size of the cache.
     */
    private int maxSize = 10000;
    
    /**
     * Cache entry expiration time in seconds.
     */
    private long expireTimeSeconds = 3600;
    
    /**
     * Cache cleanup interval in seconds.
     */
    private long cleanupIntervalSeconds = 300;
    
    /**
     * Cache synchronization interval in seconds.
     */
    private long syncIntervalSeconds = 300;
    
    public boolean isEnabled() {
        return enabled;
    }
    
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
    
    public int getMaxSize() {
        return maxSize;
    }
    
    public void setMaxSize(int maxSize) {
        this.maxSize = maxSize;
    }
    
    public long getExpireTimeSeconds() {
        return expireTimeSeconds;
    }
    
    public void setExpireTimeSeconds(long expireTimeSeconds) {
        this.expireTimeSeconds = expireTimeSeconds;
    }
    
    public long getCleanupIntervalSeconds() {
        return cleanupIntervalSeconds;
    }
    
    public void setCleanupIntervalSeconds(long cleanupIntervalSeconds) {
        this.cleanupIntervalSeconds = cleanupIntervalSeconds;
    }
    
    public long getSyncIntervalSeconds() {
        return syncIntervalSeconds;
    }
    
    public void setSyncIntervalSeconds(long syncIntervalSeconds) {
        this.syncIntervalSeconds = syncIntervalSeconds;
    }
    
    @Override
    public String toString() {
        return "McpCacheIndexProperties{" + "enabled=" + enabled + ", maxSize=" + maxSize + ", expireTimeSeconds="
                + expireTimeSeconds + ", cleanupIntervalSeconds=" + cleanupIntervalSeconds + ", syncIntervalSeconds="
                + syncIntervalSeconds + '}';
    }
} 