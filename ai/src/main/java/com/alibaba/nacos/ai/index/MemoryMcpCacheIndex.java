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

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import com.alibaba.nacos.ai.model.mcp.McpServerIndexData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Memory MCP cache index implementation with LRU and expiration support.
 *
 * @author misselvexu
 */
public class MemoryMcpCacheIndex implements McpCacheIndex {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(MemoryMcpCacheIndex.class);
    
    private final int maxSize;
    
    private final long expireTimeSeconds;
    
    private final long cleanupIntervalSeconds;
    
    private final Map<String, Entry> idToEntry;
    
    private final Map<String, String> nameKeyToId;
    
    private final AtomicLong hitCount = new AtomicLong();
    
    private final AtomicLong missCount = new AtomicLong();
    
    private final AtomicLong evictionCount = new AtomicLong();
    
    public MemoryMcpCacheIndex(int maxSize, long expireTimeSeconds, long cleanupIntervalSeconds) {
        this.maxSize = maxSize;
        this.expireTimeSeconds = expireTimeSeconds;
        this.cleanupIntervalSeconds = cleanupIntervalSeconds;
        this.idToEntry = Collections.synchronizedMap(new LruMap<>(maxSize));
        this.nameKeyToId = new ConcurrentHashMap<>();
        LOGGER.info("MemoryMcpCacheIndex initialized with maxSize={}, expireTime={}s, cleanupInterval={}s", maxSize,
                expireTimeSeconds, cleanupIntervalSeconds);
    }
    
    /**
     * Get MCP ID.
     */
    @Override
    public String getMcpId(String namespaceId, String mcpName) {
        if (namespaceId == null || mcpName == null || namespaceId.isEmpty() || mcpName.isEmpty()) {
            return null;
        }
        String key = buildNameKey(namespaceId, mcpName);
        String id = nameKeyToId.get(key);
        if (id == null) {
            missCount.incrementAndGet();
            LOGGER.debug("Cache miss for name key: {}", key);
            return null;
        }
        Entry entry = idToEntry.get(id);
        if (entry == null || entry.isExpired(expireTimeSeconds)) {
            // Clean up invalid mapping to maintain cache consistency
            // Use remove(key, id) to ensure atomic removal only if the mapping still exists
            nameKeyToId.remove(key, id);
            missCount.incrementAndGet();
            LOGGER.debug("Cache miss for mcpId: {} (cleaned up invalid mapping for key: {})", id, key);
            return null;
        }
        hitCount.incrementAndGet();
        LOGGER.debug("Cache hit for name key: {}", key);
        return id;
    }
    
    /**
     * Get MCP server information (by name).
     */
    @Override
    public McpServerIndexData getMcpServerByName(String namespaceId, String mcpName) {
        String id = getMcpId(namespaceId, mcpName);
        if (id == null) {
            return null;
        }
        return getMcpServerById(id);
    }
    
    /**
     * Get MCP server information (by ID).
     */
    @Override
    public McpServerIndexData getMcpServerById(String mcpId) {
        if (mcpId == null || mcpId.isEmpty()) {
            return null;
        }
        Entry entry = idToEntry.get(mcpId);
        if (entry == null || entry.isExpired(expireTimeSeconds)) {
            // Clean up invalid mapping to maintain cache consistency
            // Use a safer approach to avoid ConcurrentModificationException
            cleanupInvalidMappings(mcpId);
            missCount.incrementAndGet();
            LOGGER.debug("Cache miss for mcpId: {} (cleaned up invalid mappings)", mcpId);
            return null;
        }
        hitCount.incrementAndGet();
        LOGGER.debug("Cache hit for mcpId: {}", mcpId);
        return entry.data;
    }
    
    /**
     * Update index.
     */
    @Override
    public void updateIndex(String namespaceId, String mcpName, String mcpId) {
        if (namespaceId == null || mcpName == null || mcpId == null || namespaceId.isEmpty() || mcpName.isEmpty()
                || mcpId.isEmpty()) {
            LOGGER.warn("Invalid parameters for updateIndex: namespaceId={}, mcpName={}, mcpId={}", namespaceId,
                    mcpName, mcpId);
            return;
        }
        
        McpServerIndexData data = new McpServerIndexData();
        data.setId(mcpId);
        data.setNamespaceId(namespaceId);
        Entry entry = new Entry(data, System.currentTimeMillis() / 1000);
        idToEntry.put(mcpId, entry);
        String key = buildNameKey(namespaceId, mcpName);
        nameKeyToId.put(key, mcpId);
        LOGGER.debug("Updated cache index: nameKey={}, mcpId={}", key, mcpId);
    }
    
    /**
     * Remove index by name.
     */
    @Override
    public void removeIndex(String namespaceId, String mcpName) {
        if (namespaceId == null || mcpName == null || namespaceId.isEmpty() || mcpName.isEmpty()) {
            return;
        }
        String key = buildNameKey(namespaceId, mcpName);
        String id = nameKeyToId.remove(key);
        if (id != null) {
            idToEntry.remove(id);
            LOGGER.debug("Removed cache index: nameKey={}, mcpId={}", key, id);
        }
    }
    
    /**
     * Remove index by ID.
     */
    @Override
    public void removeIndex(String mcpId) {
        if (mcpId == null || mcpId.isEmpty()) {
            return;
        }
        idToEntry.remove(mcpId);
        // Also remove all entries in nameKeyToId that point to this id
        // Use the same safe cleanup method
        cleanupInvalidMappings(mcpId);
        LOGGER.debug("Removed cache index: mcpId={}", mcpId);
    }
    
    /**
     * Clear cache.
     */
    @Override
    public void clear() {
        int size = idToEntry.size();
        LOGGER.info("Cache cleared, removed {} entries", size);
        idToEntry.clear();
        nameKeyToId.clear();
        hitCount.set(0);
        missCount.set(0);
        evictionCount.set(0);
    }
    
    /**
     * Get cache size.
     */
    @Override
    public int getSize() {
        return idToEntry.size();
    }
    
    /**
     * Get cache statistics.
     */
    @Override
    public CacheStats getStats() {
        CacheStats stats = new CacheStats(hitCount.get(), missCount.get(), evictionCount.get(), getSize());
        LOGGER.debug("Cache stats: hitCount={}, missCount={}, evictionCount={}, size={}, hitRate={:.2f}%",
                stats.getHitCount(), stats.getMissCount(), stats.getEvictionCount(), stats.getSize(),
                stats.getHitRate() * 100);
        return stats;
    }
    
    /**
     * Build name key.
     */
    private String buildNameKey(String namespaceId, String mcpName) {
        return namespaceId + "::" + mcpName;
    }
    
    /**
     * Safely cleanup invalid mappings for a given mcpId. This method avoids ConcurrentModificationException by using a
     * safer approach.
     */
    private void cleanupInvalidMappings(String mcpId) {
        // Collect keys to remove first, then remove them
        List<String> keysToRemove = new ArrayList<>();
        for (Map.Entry<String, String> entry : nameKeyToId.entrySet()) {
            if (mcpId.equals(entry.getValue())) {
                keysToRemove.add(entry.getKey());
            }
        }
        // Remove collected keys
        for (String key : keysToRemove) {
            nameKeyToId.remove(key, mcpId);
        }
    }
    
    /**
     * Cache entry.
     */
    private static class Entry {
        
        private final McpServerIndexData data;
        
        private final long createTimeSeconds;
        
        Entry(McpServerIndexData data, long createTimeSeconds) {
            this.data = data;
            this.createTimeSeconds = createTimeSeconds;
        }
        
        boolean isExpired(long expireTimeSeconds) {
            // Check if the entry has expired based on the configured expireTimeSeconds
            long currentTimeSeconds = System.currentTimeMillis() / 1000;
            return currentTimeSeconds - createTimeSeconds > expireTimeSeconds;
        }
    }
    
    /**
     * LRU Map implementation.
     */
    private static class LruMap<K, V> extends LinkedHashMap<K, V> {
        
        private final int maxSize;
        
        LruMap(int maxSize) {
            super(16, 0.75f, true);
            this.maxSize = maxSize;
        }
        
        @Override
        protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
            return size() > maxSize;
        }
    }
} 