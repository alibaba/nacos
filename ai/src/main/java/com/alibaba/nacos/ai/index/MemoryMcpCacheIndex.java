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

package com.alibaba.nacos.ai.index;

import com.alibaba.nacos.ai.config.McpCacheIndexProperties;
import com.alibaba.nacos.ai.model.mcp.McpServerIndexData;
import com.alibaba.nacos.common.utils.StringUtils;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import javax.annotation.PreDestroy;

/**
 * Memory-based MCP cache index implementation backed by Caffeine.
 *
 * @author misselvexu
 */
public class MemoryMcpCacheIndex implements McpCacheIndex {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(MemoryMcpCacheIndex.class);
    
    private static final int DEFAULT_SHUTDOWN_TIMEOUT_SECONDS = 5;
    
    private final Cache<String, CacheEntry> idToEntry;
    
    private final ConcurrentHashMap<String, String> nameKeyToId;
    
    private final AtomicLong hitCount;
    
    private final AtomicLong missCount;
    
    private final AtomicLong evictionCount;
    
    private final ScheduledExecutorService cleanupScheduler;
    
    private volatile boolean shutdown = false;
    
    public MemoryMcpCacheIndex(McpCacheIndexProperties properties) {
        this.nameKeyToId = new ConcurrentHashMap<>(Math.max(1, properties.getMaxSize()));
        this.hitCount = new AtomicLong(0);
        this.missCount = new AtomicLong(0);
        this.evictionCount = new AtomicLong(0);
        this.idToEntry = createCache(properties);
        this.cleanupScheduler = new ScheduledThreadPoolExecutor(1, r -> {
            Thread t = new Thread(r, "mcp-cache-cleanup");
            t.setDaemon(true);
            return t;
        }, new ThreadPoolExecutor.CallerRunsPolicy());
        if (properties.getCleanupIntervalSeconds() > 0) {
            this.cleanupScheduler.scheduleWithFixedDelay(this::cleanupExpiredEntries, properties.getCleanupIntervalSeconds(),
                    properties.getCleanupIntervalSeconds(), TimeUnit.SECONDS);
        }
    }
    
    private Cache<String, CacheEntry> createCache(McpCacheIndexProperties properties) {
        Caffeine<String, CacheEntry> builder = Caffeine.<String, CacheEntry>newBuilder()
                .maximumSize(Math.max(0, properties.getMaxSize()))
                .removalListener(this::onEntryRemoved);
        if (properties.getExpireTimeSeconds() > 0) {
            builder.expireAfterWrite(properties.getExpireTimeSeconds(), TimeUnit.SECONDS);
        }
        return builder.build();
    }
    
    @Override
    public String getMcpId(String namespaceId, String mcpName) {
        if (shutdown || StringUtils.isBlank(namespaceId) || StringUtils.isBlank(mcpName)) {
            return null;
        }
        String nameKey = buildNameKey(namespaceId, mcpName);
        String mcpId = nameKeyToId.get(nameKey);
        if (mcpId == null) {
            missCount.incrementAndGet();
            return null;
        }
        CacheEntry entry = idToEntry.getIfPresent(mcpId);
        if (entry == null || !nameKey.equals(entry.nameKey)) {
            nameKeyToId.remove(nameKey, mcpId);
            missCount.incrementAndGet();
            return null;
        }
        hitCount.incrementAndGet();
        return mcpId;
    }
    
    @Override
    public McpServerIndexData getMcpServerByName(String namespaceId, String mcpName) {
        String mcpId = getMcpId(namespaceId, mcpName);
        return mcpId == null ? null : getMcpServerById(mcpId);
    }
    
    @Override
    public McpServerIndexData getMcpServerById(String mcpId) {
        if (shutdown || StringUtils.isBlank(mcpId)) {
            return null;
        }
        CacheEntry entry = idToEntry.getIfPresent(mcpId);
        if (entry == null) {
            missCount.incrementAndGet();
            return null;
        }
        hitCount.incrementAndGet();
        return entry.data;
    }
    
    @Override
    public void updateIndex(String namespaceId, String mcpName, String mcpId) {
        if (shutdown || StringUtils.isBlank(namespaceId) || StringUtils.isBlank(mcpName) || StringUtils.isBlank(mcpId)) {
            return;
        }
        String nameKey = buildNameKey(namespaceId, mcpName);
        CacheEntry newEntry = new CacheEntry(nameKey, McpServerIndexData.newIndexData(mcpId, namespaceId));
        CacheEntry oldEntry = idToEntry.asMap().put(mcpId, newEntry);
        if (oldEntry != null && !nameKey.equals(oldEntry.nameKey)) {
            nameKeyToId.remove(oldEntry.nameKey, mcpId);
        }
        String oldMcpId = nameKeyToId.put(nameKey, mcpId);
        if (oldMcpId != null && !oldMcpId.equals(mcpId)) {
            idToEntry.invalidate(oldMcpId);
        }
        idToEntry.cleanUp();
    }
    
    @Override
    public void removeIndex(String namespaceId, String mcpName) {
        if (shutdown || StringUtils.isBlank(namespaceId) || StringUtils.isBlank(mcpName)) {
            return;
        }
        String nameKey = buildNameKey(namespaceId, mcpName);
        String mcpId = nameKeyToId.remove(nameKey);
        if (mcpId == null) {
            return;
        }
        CacheEntry entry = idToEntry.getIfPresent(mcpId);
        if (entry != null && nameKey.equals(entry.nameKey)) {
            idToEntry.invalidate(mcpId);
        }
    }
    
    @Override
    public void removeIndex(String mcpId) {
        if (shutdown || StringUtils.isBlank(mcpId)) {
            return;
        }
        idToEntry.invalidate(mcpId);
    }
    
    @Override
    public void clear() {
        idToEntry.invalidateAll();
        idToEntry.cleanUp();
        nameKeyToId.clear();
        hitCount.set(0);
        missCount.set(0);
        evictionCount.set(0);
    }
    
    @Override
    public int getSize() {
        idToEntry.cleanUp();
        return idToEntry.asMap().size();
    }
    
    @Override
    public CacheStats getStats() {
        return new CacheStats(hitCount.get(), missCount.get(), evictionCount.get(), getSize());
    }
    
    /**
     * Shut down the cache cleanup task and release cache resources.
     */
    @PreDestroy
    public void shutdown() {
        if (shutdown) {
            return;
        }
        shutdown = true;
        cleanupScheduler.shutdown();
        try {
            if (!cleanupScheduler.awaitTermination(DEFAULT_SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                cleanupScheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            cleanupScheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        clear();
    }
    
    private String buildNameKey(String namespaceId, String mcpName) {
        return namespaceId + "::" + mcpName;
    }
    
    private void cleanupExpiredEntries() {
        if (shutdown) {
            return;
        }
        try {
            idToEntry.cleanUp();
        } catch (Exception e) {
            LOGGER.error("Clean up expired mcp id and name cache failed.", e);
        }
    }
    
    private void onEntryRemoved(String mcpId, CacheEntry entry, RemovalCause removalCause) {
        if (entry == null || StringUtils.isBlank(mcpId)) {
            return;
        }
        nameKeyToId.remove(entry.nameKey, mcpId);
        if (removalCause != null && removalCause.wasEvicted()) {
            evictionCount.incrementAndGet();
        }
    }
    
    private static final class CacheEntry {
        
        private final String nameKey;
        
        private final McpServerIndexData data;
        
        private CacheEntry(String nameKey, McpServerIndexData data) {
            this.nameKey = nameKey;
            this.data = data;
        }
    }
}
