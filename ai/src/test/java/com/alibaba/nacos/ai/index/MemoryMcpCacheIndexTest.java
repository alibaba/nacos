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

import java.util.concurrent.atomic.AtomicInteger;

import com.alibaba.nacos.ai.model.mcp.McpServerIndexData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * MemoryMcpCacheIndex unit tests.
 *
 * @author misselvexu
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
    
    @Test
    void testCacheConsistencyAfterExpiration() {
        // Create cache with 1 second expiration time for testing
        MemoryMcpCacheIndex shortExpireCache = new MemoryMcpCacheIndex(100, 1, 60);
        
        String namespaceId = "test-namespace";
        String mcpName = "test-mcp";
        String mcpId = "test-id";
        
        // Add entry to cache
        shortExpireCache.updateIndex(namespaceId, mcpName, mcpId);
        
        // Verify entry is available
        assertNotNull(shortExpireCache.getMcpId(namespaceId, mcpName));
        assertEquals(1, shortExpireCache.getSize());
        
        // Wait for expiration
        try {
            Thread.sleep(1100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // First call should return null and clean up invalid mapping
        assertNull(shortExpireCache.getMcpId(namespaceId, mcpName));
        
        // Second call should also return null (no invalid mapping left)
        assertNull(shortExpireCache.getMcpId(namespaceId, mcpName));
        
        // Cache size should be 0 (both idToEntry and nameKeyToId cleaned up)
        assertEquals(0, shortExpireCache.getSize());
    }
    
    @Test
    void testCacheConsistencyAfterEntryRemoval() {
        MemoryMcpCacheIndex cacheIndex = new MemoryMcpCacheIndex(100, 3600, 300);
        
        String namespaceId = "test-namespace";
        String mcpName = "test-mcp";
        String mcpId = "test-id";
        
        // Add entry to cache
        cacheIndex.updateIndex(namespaceId, mcpName, mcpId);
        
        // Verify entry is available
        assertNotNull(cacheIndex.getMcpId(namespaceId, mcpName));
        assertEquals(1, cacheIndex.getSize());
        
        // Remove entry from idToEntry (simulating LRU eviction or manual removal)
        // This simulates the scenario where entry is removed but nameKeyToId mapping remains
        cacheIndex.removeIndex(mcpId);
        
        // First call should return null and clean up invalid mapping
        assertNull(cacheIndex.getMcpId(namespaceId, mcpName));
        
        // Second call should also return null (no invalid mapping left)
        assertNull(cacheIndex.getMcpId(namespaceId, mcpName));
        
        // Cache size should be 0
        assertEquals(0, cacheIndex.getSize());
    }
    
    @Test
    void testCacheConsistencyWithMultipleNameMappings() {
        MemoryMcpCacheIndex cacheIndex = new MemoryMcpCacheIndex(100, 3600, 300);
        
        String mcpId = "test-id";
        
        // Add multiple name mappings for the same ID
        cacheIndex.updateIndex("ns1", "mcp1", mcpId);
        cacheIndex.updateIndex("ns2", "mcp2", mcpId);
        cacheIndex.updateIndex("ns3", "mcp3", mcpId);
        
        // Verify all mappings work
        assertEquals(mcpId, cacheIndex.getMcpId("ns1", "mcp1"));
        assertEquals(mcpId, cacheIndex.getMcpId("ns2", "mcp2"));
        assertEquals(mcpId, cacheIndex.getMcpId("ns3", "mcp3"));
        
        // Remove the entry (simulating expiration or removal)
        cacheIndex.removeIndex(mcpId);
        
        // All name mappings should now return null and be cleaned up
        assertNull(cacheIndex.getMcpId("ns1", "mcp1"));
        assertNull(cacheIndex.getMcpId("ns2", "mcp2"));
        assertNull(cacheIndex.getMcpId("ns3", "mcp3"));
        
        // Cache should be empty
        assertEquals(0, cacheIndex.getSize());
    }
    
    @Test
    void testCacheConsistencyWithGetMcpServerById() {
        MemoryMcpCacheIndex cacheIndex = new MemoryMcpCacheIndex(100, 3600, 300);
        
        String namespaceId = "test-namespace";
        String mcpName = "test-mcp";
        String mcpId = "test-id";
        
        // Add entry to cache
        cacheIndex.updateIndex(namespaceId, mcpName, mcpId);
        
        // Verify entry is available
        assertNotNull(cacheIndex.getMcpServerById(mcpId));
        assertEquals(1, cacheIndex.getSize());
        
        // Remove entry from idToEntry
        cacheIndex.removeIndex(mcpId);
        
        // First call should return null and clean up invalid mappings
        assertNull(cacheIndex.getMcpServerById(mcpId));
        
        // Second call should also return null
        assertNull(cacheIndex.getMcpServerById(mcpId));
        
        // Cache size should be 0
        assertEquals(0, cacheIndex.getSize());
    }
    
    @Test
    void testConcurrentDeletionSafety() throws InterruptedException {
        MemoryMcpCacheIndex cacheIndex = new MemoryMcpCacheIndex(100, 3600, 300);
        
        // Add multiple entries with the same ID
        String mcpId = "test-id";
        cacheIndex.updateIndex("ns1", "mcp1", mcpId);
        cacheIndex.updateIndex("ns2", "mcp2", mcpId);
        cacheIndex.updateIndex("ns3", "mcp3", mcpId);
        
        // Verify entries exist
        assertEquals(mcpId, cacheIndex.getMcpId("ns1", "mcp1"));
        assertEquals(mcpId, cacheIndex.getMcpId("ns2", "mcp2"));
        assertEquals(mcpId, cacheIndex.getMcpId("ns3", "mcp3"));
        
        // Create multiple threads to concurrently remove the same ID
        int threadCount = 10;
        Thread[] threads = new Thread[threadCount];
        AtomicInteger exceptionCount = new AtomicInteger(0);
        
        for (int i = 0; i < threadCount; i++) {
            threads[i] = new Thread(() -> {
                try {
                    // Simulate concurrent access
                    cacheIndex.removeIndex(mcpId);
                } catch (Exception e) {
                    exceptionCount.incrementAndGet();
                    System.err.println("Exception during concurrent access: " + e.getMessage());
                }
            });
        }
        
        // Start all threads
        for (Thread thread : threads) {
            thread.start();
        }
        
        // Wait for all threads to complete
        for (Thread thread : threads) {
            thread.join();
        }
        
        // Verify no exceptions occurred
        assertEquals(0, exceptionCount.get());
        
        // Verify all mappings are cleaned up
        assertNull(cacheIndex.getMcpId("ns1", "mcp1"));
        assertNull(cacheIndex.getMcpId("ns2", "mcp2"));
        assertNull(cacheIndex.getMcpId("ns3", "mcp3"));
        assertEquals(0, cacheIndex.getSize());
    }
    
    @Test
    void testConcurrentExpirationCleanup() throws InterruptedException {
        // Create cache with 1 second expiration for testing
        MemoryMcpCacheIndex shortExpireCache = new MemoryMcpCacheIndex(100, 1, 60);
        
        String mcpId = "test-id";
        shortExpireCache.updateIndex("ns1", "mcp1", mcpId);
        shortExpireCache.updateIndex("ns2", "mcp2", mcpId);
        shortExpireCache.updateIndex("ns3", "mcp3", mcpId);
        
        // Wait for expiration
        Thread.sleep(1100);
        
        // Create multiple threads to concurrently access expired entries
        int threadCount = 10;
        Thread[] threads = new Thread[threadCount];
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger exceptionCount = new AtomicInteger(0);
        
        for (int i = 0; i < threadCount; i++) {
            threads[i] = new Thread(() -> {
                try {
                    // This should trigger cleanup of invalid mappings
                    String result = shortExpireCache.getMcpId("ns1", "mcp1");
                    if (result == null) {
                        successCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    exceptionCount.incrementAndGet();
                    System.err.println("Exception during concurrent access: " + e.getMessage());
                }
            });
        }
        
        // Start all threads
        for (Thread thread : threads) {
            thread.start();
        }
        
        // Wait for all threads to complete
        for (Thread thread : threads) {
            thread.join();
        }
        
        // Verify no exceptions occurred
        assertEquals(0, exceptionCount.get());
        assertEquals(threadCount, successCount.get());
        
        // Verify cache is cleaned up
        assertEquals(0, shortExpireCache.getSize());
    }
    
    @Test
    void testConcurrentGetMcpServerByIdCleanup() throws InterruptedException {
        MemoryMcpCacheIndex cacheIndex = new MemoryMcpCacheIndex(100, 3600, 300);
        
        String mcpId = "test-id";
        cacheIndex.updateIndex("ns1", "mcp1", mcpId);
        cacheIndex.updateIndex("ns2", "mcp2", mcpId);
        
        // Remove the entry to simulate expiration
        cacheIndex.removeIndex(mcpId);
        
        // Create multiple threads to concurrently access the invalid entry
        int threadCount = 10;
        Thread[] threads = new Thread[threadCount];
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger exceptionCount = new AtomicInteger(0);
        
        for (int i = 0; i < threadCount; i++) {
            threads[i] = new Thread(() -> {
                try {
                    // This should trigger cleanup of invalid mappings
                    McpServerIndexData result = cacheIndex.getMcpServerById(mcpId);
                    if (result == null) {
                        successCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    exceptionCount.incrementAndGet();
                    System.err.println("Exception during concurrent getMcpServerById: " + e.getMessage());
                }
            });
        }
        
        // Start all threads
        for (Thread thread : threads) {
            thread.start();
        }
        
        // Wait for all threads to complete
        for (Thread thread : threads) {
            thread.join();
        }
        
        // Verify no exceptions occurred
        assertEquals(0, exceptionCount.get());
        assertEquals(threadCount, successCount.get());
        
        // Verify cache is cleaned up
        assertEquals(0, cacheIndex.getSize());
    }
} 