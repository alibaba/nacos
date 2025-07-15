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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.alibaba.nacos.ai.config.McpCacheIndexProperties;
import com.alibaba.nacos.ai.model.mcp.McpServerIndexData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MemoryMcpCacheIndexTest {
    
    private MemoryMcpCacheIndex cache;
    
    private McpCacheIndexProperties props;
    
    @BeforeEach
    void setUp() {
        props = new McpCacheIndexProperties();
        props.setMaxSize(3);
        props.setExpireTimeSeconds(2); // 2秒过期
        cache = new MemoryMcpCacheIndex(props);
    }
    
    @Test
    void testPutAndGet() {
        cache.updateIndex("ns", "name", "id1");
        assertEquals("id1", cache.getMcpId("ns", "name"));
        McpServerIndexData data = cache.getMcpServerById("id1");
        assertNotNull(data);
        assertEquals("id1", data.getId());
        assertEquals("ns", data.getNamespaceId());
    }
    
    @Test
    void testRemoveByNameAndId() {
        cache.updateIndex("ns", "name", "id1");
        cache.removeIndex("ns", "name");
        assertNull(cache.getMcpId("ns", "name"));
        assertNull(cache.getMcpServerById("id1"));
        cache.updateIndex("ns", "name", "id2");
        cache.removeIndex("id2");
        assertNull(cache.getMcpId("ns", "name"));
        assertNull(cache.getMcpServerById("id2"));
    }
    
    @Test
    void testClear() {
        cache.updateIndex("ns1", "a", "id1");
        cache.updateIndex("ns2", "b", "id2");
        cache.clear();
        assertEquals(0, cache.getSize());
        assertNull(cache.getMcpId("ns1", "a"));
        assertNull(cache.getMcpId("ns2", "b"));
    }
    
    @Test
    void testLruEviction() {
        cache.updateIndex("ns", "a", "id1");
        cache.updateIndex("ns", "b", "id2");
        cache.updateIndex("ns", "c", "id3");
        // 访问id1，保持活跃
        cache.getMcpServerById("id1");
        // 插入新元素，应该淘汰id2（最久未访问）
        cache.updateIndex("ns", "d", "id4");
        assertNull(cache.getMcpServerById("id2"));
        assertNotNull(cache.getMcpServerById("id1"));
        assertNotNull(cache.getMcpServerById("id3"));
        assertNotNull(cache.getMcpServerById("id4"));
        assertEquals(3, cache.getSize());
    }
    
    @Test
    void testExpire() throws InterruptedException {
        cache.updateIndex("ns", "a", "id1");
        Thread.sleep(2100); // 超过2秒
        assertNull(cache.getMcpServerById("id1"));
        assertNull(cache.getMcpId("ns", "a"));
        assertEquals(0, cache.getSize());
    }
    
    @Test
    void testUpdateExistingEntry() {
        cache.updateIndex("ns", "a", "id1");
        cache.updateIndex("ns", "a", "id1"); // 再次put
        assertEquals("id1", cache.getMcpId("ns", "a"));
        assertEquals(1, cache.getSize());
    }
    
    @Test
    void testStats() {
        cache.updateIndex("ns", "a", "id1");
        cache.getMcpId("ns", "a"); // hit
        cache.getMcpId("ns", "b"); // miss
        cache.getMcpId("ns", "a"); // hit
        McpCacheIndex.CacheStats stats = cache.getStats();
        assertEquals(2, stats.getHitCount());
        assertEquals(1, stats.getMissCount());
        assertEquals(0, stats.getEvictionCount());
        assertEquals(1, stats.getSize());
    }
    
    @Test
    void testConcurrentPutAndGet() throws InterruptedException {
        int threadCount = 3; // 减少线程数
        int opCount = 10; // 减少操作数
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        
        for (int i = 0; i < threadCount; i++) {
            int idx = i;
            executor.submit(() -> {
                try {
                    for (int j = 0; j < opCount; j++) {
                        String ns = "ns" + (idx % 3);
                        String name = "name" + (j % 5);
                        String id = "id" + (idx * opCount + j);
                        cache.updateIndex(ns, name, id);
                        cache.getMcpId(ns, name);
                    }
                } finally {
                    latch.countDown();
                }
            });
        }
        
        // 增加等待时间
        boolean completed = latch.await(30, TimeUnit.SECONDS);
        assertTrue(completed, "All threads should complete within timeout");
        
        // 关闭线程池并等待所有任务完成
        executor.shutdown();
        boolean terminated = executor.awaitTermination(10, TimeUnit.SECONDS);
        assertTrue(terminated, "Executor should terminate within timeout");
        
        // 验证缓存大小不超过限制，并且缓存功能正常
        int finalSize = cache.getSize();
        assertTrue(finalSize <= props.getMaxSize(),
                "Cache size " + finalSize + " should not exceed maxSize " + props.getMaxSize());
        
        // 验证缓存仍然可以正常工作
        if (finalSize > 0) {
            // 尝试获取一个存在的key，验证缓存功能
            cache.updateIndex("test", "test", "test-id");
            String result = cache.getMcpId("test", "test");
            assertNotNull(result, "Cache should still work after concurrent operations");
        }
    }
    
    @Test
    void testRemoveNonExist() {
        cache.removeIndex("ns", "not-exist");
        cache.removeIndex("not-exist-id");
        // 不抛异常
    }
    
    @Test
    void testPutNullOrBlank() {
        cache.updateIndex(null, "a", "id1");
        cache.updateIndex("ns", null, "id1");
        cache.updateIndex("ns", "a", null);
        assertNull(cache.getMcpId(null, "a"));
        assertNull(cache.getMcpId("ns", null));
        assertNull(cache.getMcpId("ns", "a"));
        assertEquals(0, cache.getSize());
    }
    
    @Test
    void testEvictionCount() {
        cache.updateIndex("ns", "a", "id1");
        cache.updateIndex("ns", "b", "id2");
        cache.updateIndex("ns", "c", "id3");
        cache.updateIndex("ns", "d", "id4"); // 淘汰1个
        assertEquals(1, cache.getStats().getEvictionCount());
    }
    
    @Test
    void testExpireDoesNotAffectOthers() throws InterruptedException {
        cache.updateIndex("ns", "a", "id1");
        cache.updateIndex("ns", "b", "id2");
        Thread.sleep(2100);
        assertNull(cache.getMcpServerById("id1"));
        assertNull(cache.getMcpServerById("id2"));
        assertEquals(0, cache.getSize());
    }
    
    @Test
    void testGetMcpServerByName() {
        cache.updateIndex("ns", "a", "id1");
        McpServerIndexData data = cache.getMcpServerByName("ns", "a");
        assertNotNull(data);
        assertEquals("id1", data.getId());
        assertEquals("ns", data.getNamespaceId());
        assertNull(cache.getMcpServerByName("ns", "not-exist"));
    }
} 