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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.alibaba.nacos.ai.model.mcp.McpServerIndexData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * MemoryMcpCacheIndex unit tests.
 *
 * @author xinluo
 */
class MemoryMcpCacheIndexTest {
    
    private MemoryMcpCacheIndex cacheIndex;
    
    @BeforeEach
    void setUp() {
        cacheIndex = new MemoryMcpCacheIndex(100, 3600, 300);
    }
    
    @Test
    void testUpdateAndGetIndex() {
        // Test updating and getting index
        final String namespaceId = "test-namespace";
        final String mcpName = "test-mcp";
        final String mcpId = "test-id-123";
        
        // Initial state should be empty
        assertNull(cacheIndex.getMcpId(namespaceId, mcpName));
        assertNull(cacheIndex.getMcpServerById(mcpId));
        
        // Update index
        cacheIndex.updateIndex(namespaceId, mcpName, mcpId);
        
        // Verify can get ID by name
        assertEquals(mcpId, cacheIndex.getMcpId(namespaceId, mcpName));
        
        // Verify can get server information by ID
        McpServerIndexData serverData = cacheIndex.getMcpServerById(mcpId);
        assertNotNull(serverData);
        assertEquals(mcpId, serverData.getId());
        assertEquals(namespaceId, serverData.getNamespaceId());
        
        // Verify can get server information by name
        McpServerIndexData serverDataByName = cacheIndex.getMcpServerByName(namespaceId, mcpName);
        assertNotNull(serverDataByName);
        assertEquals(mcpId, serverDataByName.getId());
        assertEquals(namespaceId, serverDataByName.getNamespaceId());
    }
    
    @Test
    void testRemoveIndex() {
        final String namespaceId = "test-namespace";
        final String mcpName = "test-mcp";
        final String mcpId = "test-id-123";
        
        // Add index
        cacheIndex.updateIndex(namespaceId, mcpName, mcpId);
        assertNotNull(cacheIndex.getMcpId(namespaceId, mcpName));
        
        // Remove by name
        cacheIndex.removeIndex(namespaceId, mcpName);
        assertNull(cacheIndex.getMcpId(namespaceId, mcpName));
        assertNull(cacheIndex.getMcpServerById(mcpId));
    }
    
    @Test
    void testRemoveIndexById() {
        final String namespaceId = "test-namespace";
        final String mcpName = "test-mcp";
        final String mcpId = "test-id-123";
        
        // Add index
        cacheIndex.updateIndex(namespaceId, mcpName, mcpId);
        assertNotNull(cacheIndex.getMcpId(namespaceId, mcpName));
        
        // Remove by ID
        cacheIndex.removeIndex(mcpId);
        assertNull(cacheIndex.getMcpId(namespaceId, mcpName));
        assertNull(cacheIndex.getMcpServerById(mcpId));
    }
    
    @Test
    void testClearCache() {
        final String namespaceId = "test-namespace";
        final String mcpName = "test-mcp";
        final String mcpId = "test-id-123";
        
        // Add index
        cacheIndex.updateIndex(namespaceId, mcpName, mcpId);
        assertNotNull(cacheIndex.getMcpId(namespaceId, mcpName));
        
        // Clear cache
        cacheIndex.clear();
        assertNull(cacheIndex.getMcpId(namespaceId, mcpName));
        assertNull(cacheIndex.getMcpServerById(mcpId));
        
        // Verify statistics are reset
        McpCacheIndex.CacheStats stats = cacheIndex.getStats();
        assertEquals(0, stats.getHitCount());
        assertEquals(0, stats.getMissCount());
        assertEquals(0, stats.getSize());
    }
    
    @Test
    void testCacheStats() {
        final String namespaceId = "test-namespace";
        final String mcpName = "test-mcp";
        final String mcpId = "test-id-123";
        
        // Initial statistics
        McpCacheIndex.CacheStats initialStats = cacheIndex.getStats();
        assertEquals(0, initialStats.getHitCount());
        assertEquals(0, initialStats.getMissCount());
        assertEquals(0, initialStats.getSize());
        
        // Add index
        cacheIndex.updateIndex(namespaceId, mcpName, mcpId);
        
        // Query hits
        cacheIndex.getMcpId(namespaceId, mcpName);
        cacheIndex.getMcpServerById(mcpId);
        
        // Query miss
        cacheIndex.getMcpId("non-existent", "non-existent");
        
        // Verify statistics
        McpCacheIndex.CacheStats stats = cacheIndex.getStats();
        assertEquals(2, stats.getHitCount()); // Two hits
        assertEquals(1, stats.getMissCount()); // One miss
        assertEquals(1, stats.getSize()); // One entry
        assertTrue(stats.getHitRate() > 0.5); // Hit rate should be greater than 50%
    }
    
    @Test
    void testNullAndEmptyParameters() {
        // Test null parameters
        assertNull(cacheIndex.getMcpId(null, "test"));
        assertNull(cacheIndex.getMcpId("test", null));
        assertNull(cacheIndex.getMcpId("", "test"));
        assertNull(cacheIndex.getMcpId("test", ""));
        
        assertNull(cacheIndex.getMcpServerById(null));
        assertNull(cacheIndex.getMcpServerById(""));
        
        // Test update operations with null parameters (should not throw exceptions)
        assertDoesNotThrow(() -> cacheIndex.updateIndex(null, "test", "id"));
        assertDoesNotThrow(() -> cacheIndex.updateIndex("test", null, "id"));
        assertDoesNotThrow(() -> cacheIndex.updateIndex("test", "name", null));
        
        assertDoesNotThrow(() -> cacheIndex.removeIndex(null, "test"));
        assertDoesNotThrow(() -> cacheIndex.removeIndex("test", null));
        assertDoesNotThrow(() -> cacheIndex.removeIndex((String) null));
    }
    
    @Test
    void testMultipleEntries() {
        // Test multiple entries
        cacheIndex.updateIndex("ns1", "mcp1", "id1");
        cacheIndex.updateIndex("ns1", "mcp2", "id2");
        cacheIndex.updateIndex("ns2", "mcp1", "id3");
        
        assertEquals("id1", cacheIndex.getMcpId("ns1", "mcp1"));
        assertEquals("id2", cacheIndex.getMcpId("ns1", "mcp2"));
        assertEquals("id3", cacheIndex.getMcpId("ns2", "mcp1"));
        
        assertEquals(3, cacheIndex.getSize());
    }
    
    @Test
    void testUpdateExistingEntry() {
        final String namespaceId = "test-namespace";
        final String mcpName = "test-mcp";
        final String mcpId1 = "test-id-1";
        final String mcpId2 = "test-id-2";
        
        // Add initial index
        cacheIndex.updateIndex(namespaceId, mcpName, mcpId1);
        assertEquals(mcpId1, cacheIndex.getMcpId(namespaceId, mcpName));
        
        // Update to new ID
        cacheIndex.updateIndex(namespaceId, mcpName, mcpId2);
        assertEquals(mcpId2, cacheIndex.getMcpId(namespaceId, mcpName));
        
        // Verify old ID no longer exists
        assertNull(cacheIndex.getMcpServerById(mcpId1));
        
        // Verify new ID exists
        McpServerIndexData serverData = cacheIndex.getMcpServerById(mcpId2);
        assertNotNull(serverData);
        assertEquals(mcpId2, serverData.getId());
        assertEquals(namespaceId, serverData.getNamespaceId());
    }
} 