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

import com.alibaba.nacos.ai.model.mcp.McpServerIndexData;

/**
 * MCP cache index interface providing fast mapping between MCP Name and MCP ID.
 *
 * @author misselvexu
 */
public interface McpCacheIndex {
    
    /**
     * Get MCP ID by namespace ID and MCP name.
     *
     * @param namespaceId namespace ID
     * @param mcpName     MCP name
     * @return MCP ID, returns null if not found
     */
    String getMcpId(String namespaceId, String mcpName);
    
    /**
     * Get MCP server information by namespace ID and MCP name.
     *
     * @param namespaceId namespace ID
     * @param mcpName     MCP name
     * @return MCP server information, returns null if not found
     */
    McpServerIndexData getMcpServerByName(String namespaceId, String mcpName);
    
    /**
     * Get MCP server information by MCP ID.
     *
     * @param mcpId MCP ID
     * @return MCP server information, returns null if not found
     */
    McpServerIndexData getMcpServerById(String mcpId);
    
    /**
     * Update index.
     *
     * @param namespaceId namespace ID
     * @param mcpName     MCP name
     * @param mcpId       MCP ID
     */
    void updateIndex(String namespaceId, String mcpName, String mcpId);
    
    /**
     * Remove index by name.
     *
     * @param namespaceId namespace ID
     * @param mcpName     MCP name
     */
    void removeIndex(String namespaceId, String mcpName);
    
    /**
     * Remove index by ID.
     *
     * @param mcpId MCP ID
     */
    void removeIndex(String mcpId);
    
    /**
     * Clear cache.
     */
    void clear();
    
    /**
     * Get cache size.
     *
     * @return number of cache entries
     */
    int getSize();
    
    /**
     * Get cache statistics.
     *
     * @return cache statistics
     */
    CacheStats getStats();
    
    /**
     * Cache statistics.
     */
    class CacheStats {
        
        private final long hitCount;
        
        private final long missCount;
        
        private final long evictionCount;
        
        private final long size;
        
        public CacheStats(long hitCount, long missCount, long evictionCount, long size) {
            this.hitCount = hitCount;
            this.missCount = missCount;
            this.evictionCount = evictionCount;
            this.size = size;
        }
        
        public long getHitCount() {
            return hitCount;
        }
        
        public long getMissCount() {
            return missCount;
        }
        
        public long getEvictionCount() {
            return evictionCount;
        }
        
        public long getSize() {
            return size;
        }
        
        public double getHitRate() {
            long total = hitCount + missCount;
            return total == 0 ? 0.0 : (double) hitCount / total;
        }
    }
} 