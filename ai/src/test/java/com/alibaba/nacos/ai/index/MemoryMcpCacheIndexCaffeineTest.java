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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MemoryMcpCacheIndexCaffeineTest {
    
    private MemoryMcpCacheIndex cacheIndex;
    
    @AfterEach
    void tearDown() {
        if (cacheIndex != null) {
            cacheIndex.shutdown();
        }
    }
    
    @Test
    void testPutAndGet() {
        cacheIndex = new MemoryMcpCacheIndex(createProperties(3, 60, 300));
        
        cacheIndex.updateIndex("ns", "mcp", "id-1");
        
        assertEquals("id-1", cacheIndex.getMcpId("ns", "mcp"));
        McpServerIndexData data = cacheIndex.getMcpServerById("id-1");
        assertNotNull(data);
        assertEquals("id-1", data.getId());
        assertEquals("ns", data.getNamespaceId());
    }
    
    @Test
    void testUpdateIndexReplacesOldNameMappingForSameId() {
        cacheIndex = new MemoryMcpCacheIndex(createProperties(3, 60, 300));
        
        cacheIndex.updateIndex("ns", "old-name", "id-1");
        cacheIndex.updateIndex("ns", "new-name", "id-1");
        
        assertNull(cacheIndex.getMcpId("ns", "old-name"));
        assertEquals("id-1", cacheIndex.getMcpId("ns", "new-name"));
    }
    
    @Test
    void testRemoveIndexByIdCleansNameMapping() {
        cacheIndex = new MemoryMcpCacheIndex(createProperties(3, 60, 300));
        
        cacheIndex.updateIndex("ns", "mcp", "id-1");
        cacheIndex.removeIndex("id-1");
        
        assertNull(cacheIndex.getMcpId("ns", "mcp"));
        assertNull(cacheIndex.getMcpServerById("id-1"));
        assertEquals(0, cacheIndex.getSize());
    }
    
    @Test
    void testExpireAfterWriteCleansBothIndexes() throws Exception {
        cacheIndex = new MemoryMcpCacheIndex(createProperties(3, 1, 3600));
        
        cacheIndex.updateIndex("ns", "mcp", "id-1");
        Thread.sleep(1200L);
        
        assertNull(cacheIndex.getMcpServerById("id-1"));
        assertNull(cacheIndex.getMcpId("ns", "mcp"));
        assertEquals(0, cacheIndex.getSize());
    }
    
    @Test
    void testEvictionKeepsRecentlyAccessedEntry() {
        cacheIndex = new MemoryMcpCacheIndex(createProperties(3, 60, 300));
        
        cacheIndex.updateIndex("ns", "mcp-1", "id-1");
        cacheIndex.updateIndex("ns", "mcp-2", "id-2");
        cacheIndex.updateIndex("ns", "mcp-3", "id-3");
        for (int i = 0; i < 5; i++) {
            assertNotNull(cacheIndex.getMcpServerById("id-1"));
        }
        
        cacheIndex.updateIndex("ns", "mcp-4", "id-4");
        
        assertNotNull(cacheIndex.getMcpServerById("id-1"));
        assertEquals(3, cacheIndex.getSize());
        assertTrue(cacheIndex.getMcpServerById("id-2") == null || cacheIndex.getMcpServerById("id-3") == null);
    }
    
    @Test
    void testConcurrentReadWriteDoesNotBreakIndexConsistency() throws Exception {
        cacheIndex = new MemoryMcpCacheIndex(createProperties(64, 60, 300));
        ExecutorService executorService = Executors.newFixedThreadPool(4);
        CountDownLatch latch = new CountDownLatch(4);
        
        for (int thread = 0; thread < 4; thread++) {
            final int threadIndex = thread;
            executorService.submit(() -> {
                try {
                    for (int i = 0; i < 200; i++) {
                        String namespaceId = "ns-" + threadIndex;
                        String mcpName = "mcp-" + i;
                        String mcpId = "id-" + threadIndex + '-' + i;
                        cacheIndex.updateIndex(namespaceId, mcpName, mcpId);
                        cacheIndex.getMcpId(namespaceId, mcpName);
                        cacheIndex.getMcpServerById(mcpId);
                        cacheIndex.removeIndex(namespaceId, mcpName);
                    }
                } finally {
                    latch.countDown();
                }
            });
        }
        
        assertTrue(latch.await(10, TimeUnit.SECONDS));
        executorService.shutdown();
        assertTrue(executorService.awaitTermination(10, TimeUnit.SECONDS));
        
        cacheIndex.updateIndex("final-ns", "final-name", "final-id");
        assertEquals("final-id", cacheIndex.getMcpId("final-ns", "final-name"));
        assertNotNull(cacheIndex.getMcpServerById("final-id"));
    }
    
    @Test
    void testShutdownClearsAndRejectsFurtherAccess() {
        cacheIndex = new MemoryMcpCacheIndex(createProperties(3, 60, 300));
        cacheIndex.updateIndex("ns", "mcp", "id-1");
        
        cacheIndex.shutdown();
        
        assertEquals(0, cacheIndex.getSize());
        assertNull(cacheIndex.getMcpId("ns", "mcp"));
        assertNull(cacheIndex.getMcpServerById("id-1"));
        cacheIndex.updateIndex("ns", "new-mcp", "new-id");
        assertNull(cacheIndex.getMcpId("ns", "new-mcp"));
    }
    
    private McpCacheIndexProperties createProperties(int maxSize, long expireTimeSeconds, long cleanupIntervalSeconds) {
        McpCacheIndexProperties properties = new McpCacheIndexProperties();
        properties.setMaxSize(maxSize);
        properties.setExpireTimeSeconds(expireTimeSeconds);
        properties.setCleanupIntervalSeconds(cleanupIntervalSeconds);
        return properties;
    }
}
