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
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.RemovalCause;
import com.google.common.cache.RemovalNotification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Memory-based MCP cache index implementation backed by a Guava {@link Cache}.
 *
 * <p>
 * The primary {@code mcpId -> data} store delegates LRU eviction and time-based expiration to
 * Guava, which removes the read-path lock contention of the previous hand-written LRU linked
 * list. Reads ({@link #getMcpId} / {@link #getMcpServerById}) no longer mutate any shared list
 * under a lock; Guava records access recency internally without blocking concurrent readers.
 * A single segment ({@code concurrencyLevel(1)}) keeps size-based eviction as strict global LRU
 * while still leaving reads lock-free.
 * </p>
 *
 * <p>
 * The lightweight {@code namespaceId::mcpName -> mcpId} secondary index is kept consistent through
 * a removal listener: when Guava evicts or expires an entry, the related name mappings are cleaned
 * up automatically. Explicit removals and replacements are handled directly by the mutating
 * methods, so the listener only reacts to genuine evictions.
 * </p>
 *
 * @author misselvexu
 */
public class MemoryMcpCacheIndex implements McpCacheIndex {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(MemoryMcpCacheIndex.class);
    
    private static final int DEFAULT_SHUTDOWN_TIMEOUT_SECONDS = 5;
    
    private static final String NAME_KEY_SEPARATOR = "::";
    
    private final McpCacheIndexProperties properties;
    
    private final Cache<String, McpServerIndexData> idCache;
    
    private final ConcurrentHashMap<String, String> nameKeyToId;
    
    private final AtomicLong hitCount;
    
    private final AtomicLong missCount;
    
    private final AtomicLong evictionCount;
    
    private final ScheduledExecutorService cleanupScheduler;
    
    private volatile boolean shutdown = false;
    
    public MemoryMcpCacheIndex(McpCacheIndexProperties properties) {
        this.properties = properties;
        this.nameKeyToId = new ConcurrentHashMap<>();
        this.hitCount = new AtomicLong(0);
        this.missCount = new AtomicLong(0);
        this.evictionCount = new AtomicLong(0);
        this.idCache = buildCache(properties);
        
        // Start cleanup scheduler
        this.cleanupScheduler = new ScheduledThreadPoolExecutor(1, r -> {
            Thread t = new Thread(r, "mcp-cache-cleanup");
            t.setDaemon(true);
            return t;
        }, new ThreadPoolExecutor.CallerRunsPolicy());
        
        // Schedule periodic cleanup to proactively drain expired entries
        this.cleanupScheduler.scheduleWithFixedDelay(this::cleanupExpiredEntries,
            properties.getCleanupIntervalSeconds(), properties.getCleanupIntervalSeconds(),
            TimeUnit.SECONDS);
    }
    
    private Cache<String, McpServerIndexData> buildCache(McpCacheIndexProperties cacheProperties) {
        CacheBuilder<String, McpServerIndexData> builder = CacheBuilder.newBuilder()
            .maximumSize(cacheProperties.getMaxSize())
            // Single segment keeps size eviction as strict global LRU while reads stay lock-free.
            .concurrencyLevel(1)
            .removalListener(this::onRemoval);
        long expireTimeSeconds = cacheProperties.getExpireTimeSeconds();
        // Non-positive expire time means entries never expire, matching the previous behavior.
        if (expireTimeSeconds > 0) {
            builder.expireAfterWrite(expireTimeSeconds, TimeUnit.SECONDS);
        }
        return builder.build();
    }
    
    @Override
    public String getMcpId(String namespaceId, String mcpName) {
        if (StringUtils.isBlank(namespaceId) || StringUtils.isBlank(mcpName)) {
            return null;
        }
        
        String key = buildNameKey(namespaceId, mcpName);
        String id = nameKeyToId.get(key);
        if (id == null) {
            missCount.incrementAndGet();
            return null;
        }
        
        McpServerIndexData data = idCache.getIfPresent(id);
        if (data == null) {
            // Entry has been evicted or expired; drop the stale name mapping.
            nameKeyToId.remove(key, id);
            missCount.incrementAndGet();
            return null;
        }
        
        hitCount.incrementAndGet();
        return id;
    }
    
    @Override
    public McpServerIndexData getMcpServerByName(String namespaceId, String mcpName) {
        String id = getMcpId(namespaceId, mcpName);
        if (id == null) {
            return null;
        }
        return getMcpServerById(id);
    }
    
    @Override
    public McpServerIndexData getMcpServerById(String mcpId) {
        if (StringUtils.isBlank(mcpId)) {
            return null;
        }
        
        McpServerIndexData data = idCache.getIfPresent(mcpId);
        if (data == null) {
            missCount.incrementAndGet();
            return null;
        }
        
        hitCount.incrementAndGet();
        return data;
    }
    
    @Override
    public void updateIndex(String namespaceId, String mcpName, String mcpId) {
        if (StringUtils.isBlank(namespaceId) || StringUtils.isBlank(mcpName)
            || StringUtils.isBlank(mcpId)) {
            return;
        }
        
        McpServerIndexData data = McpServerIndexData.newIndexData(mcpId, namespaceId);
        // Guava handles LRU insertion and size-based eviction internally.
        idCache.put(mcpId, data);
        nameKeyToId.put(buildNameKey(namespaceId, mcpName), mcpId);
    }
    
    @Override
    public void removeIndex(String namespaceId, String mcpName) {
        if (StringUtils.isBlank(namespaceId) || StringUtils.isBlank(mcpName)) {
            return;
        }
        
        String id = nameKeyToId.remove(buildNameKey(namespaceId, mcpName));
        if (id != null) {
            idCache.invalidate(id);
        }
    }
    
    @Override
    public void removeIndex(String mcpId) {
        if (StringUtils.isBlank(mcpId)) {
            return;
        }
        
        idCache.invalidate(mcpId);
        cleanupInvalidMappings(mcpId);
    }
    
    @Override
    public void clear() {
        idCache.invalidateAll();
        nameKeyToId.clear();
        hitCount.set(0);
        missCount.set(0);
        evictionCount.set(0);
    }
    
    @Override
    public int getSize() {
        // Force pending eviction/expiration so the reported size reflects live entries only.
        idCache.cleanUp();
        return (int) idCache.size();
    }
    
    @Override
    public CacheStats getStats() {
        return new CacheStats(hitCount.get(), missCount.get(), evictionCount.get(), getSize());
    }
    
    /**
     * Shuts down the cache and cleans up resources.
     */
    public void shutdown() {
        if (!shutdown) {
            shutdown = true;
            cleanupScheduler.shutdown();
            try {
                if (!cleanupScheduler.awaitTermination(DEFAULT_SHUTDOWN_TIMEOUT_SECONDS,
                    TimeUnit.SECONDS)) {
                    cleanupScheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                cleanupScheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
            clear();
        }
    }
    
    private String buildNameKey(String namespaceId, String mcpName) {
        return namespaceId + NAME_KEY_SEPARATOR + mcpName;
    }
    
    private void cleanupInvalidMappings(String mcpId) {
        nameKeyToId.entrySet().removeIf(entry -> mcpId.equals(entry.getValue()));
    }
    
    /**
     * Handle Guava cache removals. Only genuine evictions (size, expiration, garbage collection)
     * trigger secondary-index cleanup; explicit removals and replacements are handled by the
     * mutating methods themselves.
     */
    private void onRemoval(RemovalNotification<String, McpServerIndexData> notification) {
        if (isEviction(notification.getCause())) {
            evictionCount.incrementAndGet();
            String removedId = notification.getKey();
            if (removedId != null) {
                cleanupInvalidMappings(removedId);
            }
        }
    }
    
    /**
     * Whether the removal was caused by a genuine eviction (size limit, expiration or garbage
     * collection) rather than an explicit removal or replacement. Mirrors the package-private
     * {@code RemovalCause#wasEvicted()} using the public enum constants.
     *
     * @param cause removal cause reported by Guava
     * @return {@code true} if the entry was evicted rather than explicitly removed or replaced
     */
    private boolean isEviction(RemovalCause cause) {
        return cause == RemovalCause.SIZE || cause == RemovalCause.EXPIRED
            || cause == RemovalCause.COLLECTED;
    }
    
    private void cleanupExpiredEntries() {
        if (shutdown) {
            return;
        }
        
        try {
            // Guava drains expired entries during cleanUp and fires the removal listener,
            // which keeps the name index consistent.
            idCache.cleanUp();
        } catch (Exception e) {
            LOGGER.error("Clean up expired mcp id and name cache failed.", e);
        }
    }
}
