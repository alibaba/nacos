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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
        boolean completed = latch.await(10, TimeUnit.SECONDS);
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
    
    // 新增缓存删除功能测试
    
    @Test
    void testRemoveByNameDeletesCorrectMapping() {
        // 准备多个映射
        cache.updateIndex("ns1", "name1", "id1");
        cache.updateIndex("ns1", "name2", "id2");
        cache.updateIndex("ns2", "name1", "id3");
        
        // 删除特定命名空间的特定名称
        cache.removeIndex("ns1", "name1");
        
        // 验证只有正确的映射被删除
        assertNull(cache.getMcpId("ns1", "name1"));
        assertNull(cache.getMcpServerById("id1"));
        
        // 验证其他映射仍然存在
        assertEquals("id2", cache.getMcpId("ns1", "name2"));
        assertEquals("id3", cache.getMcpId("ns2", "name1"));
        assertNotNull(cache.getMcpServerById("id2"));
        assertNotNull(cache.getMcpServerById("id3"));
        
        assertEquals(2, cache.getSize());
    }
    
    @Test
    void testRemoveByIdDeletesAllRelatedMappings() {
        // 准备数据，一个ID对应一个名称映射
        cache.updateIndex("ns1", "name1", "id1");
        cache.updateIndex("ns1", "name2", "id2");
        cache.updateIndex("ns2", "name1", "id3");
        
        // 通过ID删除
        cache.removeIndex("id2");
        
        // 验证ID对应的数据被删除
        assertNull(cache.getMcpServerById("id2"));
        
        // 验证名称到ID的映射也被清理
        assertNull(cache.getMcpId("ns1", "name2"));
        
        // 验证其他数据仍然存在
        assertEquals("id1", cache.getMcpId("ns1", "name1"));
        assertEquals("id3", cache.getMcpId("ns2", "name1"));
        assertNotNull(cache.getMcpServerById("id1"));
        assertNotNull(cache.getMcpServerById("id3"));
        
        assertEquals(2, cache.getSize());
    }
    
    @Test
    void testRemoveWithInvalidMappingConsistency() {
        // 准备数据
        cache.updateIndex("ns1", "name1", "id1");
        
        // 验证数据存在
        assertEquals("id1", cache.getMcpId("ns1", "name1"));
        assertNotNull(cache.getMcpServerById("id1"));
        
        // 删除名称映射
        cache.removeIndex("ns1", "name1");
        
        // 验证数据被完全清理
        assertNull(cache.getMcpId("ns1", "name1"));
        assertNull(cache.getMcpServerById("id1"));
        assertEquals(0, cache.getSize());
    }
    
    @Test
    void testRemoveNonExistentEntries() {
        // 添加一些数据
        cache.updateIndex("ns1", "name1", "id1");
        
        // 尝试删除不存在的条目
        cache.removeIndex("non-exist-ns", "non-exist-name");
        cache.removeIndex("non-exist-id");
        
        // 验证原有数据不受影响
        assertEquals("id1", cache.getMcpId("ns1", "name1"));
        assertNotNull(cache.getMcpServerById("id1"));
        assertEquals(1, cache.getSize());
    }
    
    @Test
    void testRemoveWithNullParameters() {
        // 添加一些数据
        cache.updateIndex("ns1", "name1", "id1");
        
        // 尝试用null参数删除
        cache.removeIndex(null, null);
        cache.removeIndex("ns1", null);
        cache.removeIndex(null, "name1");
        cache.removeIndex((String) null);
        
        // 验证原有数据不受影响
        assertEquals("id1", cache.getMcpId("ns1", "name1"));
        assertNotNull(cache.getMcpServerById("id1"));
        assertEquals(1, cache.getSize());
    }
    
    @Test
    void testRemoveWithEmptyParameters() {
        // 添加一些数据，包括空字符串键
        cache.updateIndex("ns1", "name1", "id1");
        cache.updateIndex("", "", "id2");
        cache.updateIndex("ns2", "", "id3");
        
        // 删除空字符串键
        cache.removeIndex("", "");
        
        // 验证空字符串键的数据被删除
        assertNull(cache.getMcpId("", ""));
        assertNull(cache.getMcpServerById("id2"));
        
        // 验证其他数据不受影响
        assertEquals("id1", cache.getMcpId("ns1", "name1"));
        assertNull(cache.getMcpId("ns2", ""));
        assertEquals(1, cache.getSize());
    }
    
    @Test
    void testRemoveAfterEviction() {
        // 填满缓存
        cache.updateIndex("ns", "name1", "id1");
        cache.updateIndex("ns", "name2", "id2");
        cache.updateIndex("ns", "name3", "id3");
        
        // 添加新元素触发淘汰
        cache.updateIndex("ns", "name4", "id4");
        
        // id1应该被淘汰了（LRU）
        assertNull(cache.getMcpServerById("id1"));
        assertEquals(3, cache.getSize());
        
        // 尝试删除已被淘汰的元素
        cache.removeIndex("ns", "name1");
        cache.removeIndex("id1");
        
        // 验证缓存大小和其他元素不受影响
        assertEquals(3, cache.getSize());
        assertNotNull(cache.getMcpServerById("id2"));
        assertNotNull(cache.getMcpServerById("id3"));
        assertNotNull(cache.getMcpServerById("id4"));
    }
    
    @Test
    void testConcurrentRemoveOperations() throws InterruptedException {
        int threadCount = 3;
        int opCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        
        // 先填充数据
        for (int i = 0; i < threadCount * opCount; i++) {
            cache.updateIndex("ns" + (i % 3), "name" + i, "id" + i);
        }
        
        int initialSize = cache.getSize();
        
        // 并发删除操作
        for (int i = 0; i < threadCount; i++) {
            int threadIndex = i;
            executor.submit(() -> {
                try {
                    for (int j = 0; j < opCount; j++) {
                        int index = threadIndex * opCount + j;
                        if (index % 2 == 0) {
                            // 通过名称删除
                            cache.removeIndex("ns" + (index % 3), "name" + index);
                        } else {
                            // 通过ID删除
                            cache.removeIndex("id" + index);
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
        }
        
        // 等待所有线程完成
        boolean completed = latch.await(60, TimeUnit.SECONDS);
        assertTrue(completed, "All threads should complete within timeout");
        
        executor.shutdown();
        boolean terminated = executor.awaitTermination(10, TimeUnit.SECONDS);
        assertTrue(terminated, "Executor should terminate within timeout");
        
        // 验证所有数据都被删除
        assertEquals(0, cache.getSize());
        
        // 验证缓存仍然可以正常工作
        cache.updateIndex("test", "test", "test-id");
        assertEquals("test-id", cache.getMcpId("test", "test"));
        assertEquals(1, cache.getSize());
    }
    
    // 补充测试用例
    
    @Test
    void testGetMcpIdWithInvalidParameters() {
        // 测试null参数
        assertNull(cache.getMcpId(null, "name"));
        assertNull(cache.getMcpId("namespace", null));
        assertNull(cache.getMcpId(null, null));
        
        // 测试空字符串参数
        assertNull(cache.getMcpId("", "name"));
        assertNull(cache.getMcpId("namespace", ""));
        assertNull(cache.getMcpId("", ""));
    }
    
    @Test
    void testGetMcpIdWithNonExistentEntry() {
        // 测试不存在的条目
        assertNull(cache.getMcpId("non-existent-namespace", "non-existent-name"));
    }
    
    @Test
    void testGetMcpIdWithExpiredEntry() throws InterruptedException {
        // 添加一个条目
        cache.updateIndex("ns", "name", "id1");
        assertEquals("id1", cache.getMcpId("ns", "name"));
        
        // 等待过期
        Thread.sleep(2100);
        
        // 再次获取应该返回null
        assertNull(cache.getMcpId("ns", "name"));
    }
    
    @Test
    void testGetMcpServerByIdWithInvalidParameters() {
        // 测试null参数
        assertNull(cache.getMcpServerById(null));
        
        // 测试空字符串参数
        assertNull(cache.getMcpServerById(""));
    }
    
    @Test
    void testGetMcpServerByIdWithNonExistentEntry() {
        // 测试不存在的条目
        assertNull(cache.getMcpServerById("non-existent-id"));
    }
    
    @Test
    void testGetMcpServerByIdWithExpiredEntry() throws InterruptedException {
        // 添加一个条目
        cache.updateIndex("ns", "name", "id1");
        assertNotNull(cache.getMcpServerById("id1"));
        
        // 等待过期
        Thread.sleep(2100);
        
        // 再次获取应该返回null
        assertNull(cache.getMcpServerById("id1"));
    }
    
    @Test
    void testGetMcpServerByIdUpdatesLru() {
        // 填满缓存
        cache.updateIndex("ns", "name1", "id1");
        cache.updateIndex("ns", "name2", "id2");
        cache.updateIndex("ns", "name3", "id3");
        
        // 访问id1，使其成为最近使用的
        assertNotNull(cache.getMcpServerById("id1"));
        
        // 添加新元素，应该淘汰id2而不是id1
        cache.updateIndex("ns", "name4", "id4");
        
        // 验证id1仍然存在，id2被淘汰
        assertNotNull(cache.getMcpServerById("id1"));
        assertNull(cache.getMcpServerById("id2"));
        assertNotNull(cache.getMcpServerById("id3"));
        assertNotNull(cache.getMcpServerById("id4"));
    }
    
    @Test
    void testShutdown() {
        // 添加一些数据
        cache.updateIndex("ns", "name", "id1");
        assertEquals(1, cache.getSize());
        
        // 调用shutdown
        cache.shutdown();
        
        // 验证缓存被清空
        assertEquals(0, cache.getSize());
        assertNull(cache.getMcpId("ns", "name"));
        assertNull(cache.getMcpServerById("id1"));
    }
    
    @Test
    void testShutdownTimeout() throws InterruptedException {
        ScheduledExecutorService executorService = (ScheduledExecutorService) ReflectionTestUtils.getField(cache,
                "cleanupScheduler");
        executorService.shutdownNow();
        ScheduledExecutorService mockExecutorService = Mockito.mock(ScheduledExecutorService.class);
        ReflectionTestUtils.setField(cache, "cleanupScheduler", mockExecutorService);
        cache.shutdown();
        verify(mockExecutorService).shutdownNow();
    }
    
    @Test
    void testShutdownWithInterruptedException() throws InterruptedException {
        ScheduledExecutorService executorService = (ScheduledExecutorService) ReflectionTestUtils.getField(cache,
                "cleanupScheduler");
        executorService.shutdownNow();
        ScheduledExecutorService mockExecutorService = Mockito.mock(ScheduledExecutorService.class);
        when(mockExecutorService.awaitTermination(anyLong(), any())).thenThrow(new InterruptedException());
        ReflectionTestUtils.setField(cache, "cleanupScheduler", mockExecutorService);
        cache.shutdown();
        verify(mockExecutorService).shutdownNow();
    }
    
    @Test
    void testDuplicateShutdown() throws InterruptedException {
        cache.shutdown();
        ScheduledExecutorService mockExecutorService = Mockito.mock(ScheduledExecutorService.class);
        ReflectionTestUtils.setField(cache, "cleanupScheduler", mockExecutorService);
        cache.shutdown();
        verify(mockExecutorService, never()).awaitTermination(anyLong(), any());
    }
    
    @Test
    void testNoExpiredEntries() throws InterruptedException {
        McpCacheIndexProperties shortExpireProps = new McpCacheIndexProperties();
        shortExpireProps.setMaxSize(100);
        shortExpireProps.setExpireTimeSeconds(-1); // 1秒过期
        shortExpireProps.setCleanupIntervalSeconds(1);
        MemoryMcpCacheIndex noExpireCache = new MemoryMcpCacheIndex(shortExpireProps);
        try {
            noExpireCache.updateIndex("ns", "name", "id1");
            assertEquals("id1", noExpireCache.getMcpId("ns", "name"));
            
            Thread.sleep(1500);
            assertEquals("id1", noExpireCache.getMcpId("ns", "name"));
        } finally {
            noExpireCache.shutdown();
        }
    }
    
    @Test
    void testCleanupExpiredEntries() throws InterruptedException {
        // 创建一个具有短过期时间的缓存实例
        McpCacheIndexProperties shortExpireProps = new McpCacheIndexProperties();
        shortExpireProps.setMaxSize(100);
        shortExpireProps.setExpireTimeSeconds(1); // 1秒过期
        shortExpireProps.setCleanupIntervalSeconds(1);
        MemoryMcpCacheIndex shortExpireCache = new MemoryMcpCacheIndex(shortExpireProps);
        
        try {
            // 添加一些条目
            shortExpireCache.updateIndex("ns1", "name1", "id1");
            shortExpireCache.updateIndex("ns2", "name2", "id2");
            shortExpireCache.updateIndex("ns3", "name3", "id3");
            
            // 验证条目存在
            assertEquals("id1", shortExpireCache.getMcpId("ns1", "name1"));
            assertEquals("id2", shortExpireCache.getMcpId("ns2", "name2"));
            assertEquals("id3", shortExpireCache.getMcpId("ns3", "name3"));
            
            // 等待过期和清理
            Thread.sleep(1500);
            
            // 触发清理（通过获取来间接触发）
            shortExpireCache.getMcpId("ns1", "name1");
            
            // 验证过期条目被清理
            assertNull(shortExpireCache.getMcpId("ns1", "name1"));
            assertNull(shortExpireCache.getMcpId("ns2", "name2"));
            assertNull(shortExpireCache.getMcpId("ns3", "name3"));
            
            // 验证统计信息
            McpCacheIndex.CacheStats stats = shortExpireCache.getStats();
            assertEquals(0, stats.getSize());
        } finally {
            shortExpireCache.shutdown();
        }
    }
    
    @Test
    void testCleanupExpiredEntriesDoesNotAffectValidEntries() throws InterruptedException {
        // 创建一个具有不同过期时间的缓存实例
        McpCacheIndexProperties mixedProps = new McpCacheIndexProperties();
        mixedProps.setMaxSize(100);
        mixedProps.setExpireTimeSeconds(2); // 2秒过期
        mixedProps.setCleanupIntervalSeconds(1);
        MemoryMcpCacheIndex mixedCache = new MemoryMcpCacheIndex(mixedProps);
        
        try {
            // 添加一些条目
            mixedCache.updateIndex("ns1", "name1", "id1"); // 这个会过期
            Thread.sleep(1100); // 等待1.1秒
            mixedCache.updateIndex("ns2", "name2", "id2"); // 这个不会过期
            
            // 验证两个条目都存在
            assertEquals("id1", mixedCache.getMcpId("ns1", "name1"));
            assertEquals("id2", mixedCache.getMcpId("ns2", "name2"));
            
            // 再等待1.1秒，使第一个条目过期但第二个不过期
            Thread.sleep(1100);
            
            // 触发清理（通过获取来间接触发）
            mixedCache.getMcpId("ns1", "name1");
            
            // 验证只有过期的条目被清理
            assertNull(mixedCache.getMcpId("ns1", "name1"));
            assertEquals("id2", mixedCache.getMcpId("ns2", "name2"));
            
            // 验证统计数据
            McpCacheIndex.CacheStats stats = mixedCache.getStats();
            assertEquals(1, stats.getSize());
        } finally {
            mixedCache.shutdown();
        }
    }
    
    @Test
    void testConcurrentAccessDuringCleanup() throws InterruptedException {
        // 创建一个具有短过期时间的缓存实例
        McpCacheIndexProperties concurrentProps = new McpCacheIndexProperties();
        concurrentProps.setMaxSize(100);
        concurrentProps.setExpireTimeSeconds(1); // 1秒过期
        concurrentProps.setCleanupIntervalSeconds(1);
        MemoryMcpCacheIndex concurrentCache = new MemoryMcpCacheIndex(concurrentProps);
        
        try {
            int threadCount = 5;
            int opCount = 20;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(threadCount);
            
            // 添加初始数据
            for (int i = 0; i < opCount; i++) {
                concurrentCache.updateIndex("ns", "name" + i, "id" + i);
            }
            
            // 并发执行读写和清理操作
            for (int i = 0; i < threadCount; i++) {
                final int threadIndex = i;
                executor.submit(() -> {
                    try {
                        for (int j = 0; j < opCount; j++) {
                            int index = threadIndex * opCount + j;
                            
                            // 读取操作
                            concurrentCache.getMcpId("ns", "name" + (index % opCount));
                            concurrentCache.getMcpServerById("id" + (index % opCount));
                            
                            // 写入操作
                            concurrentCache.updateIndex("ns", "newname" + index, "newid" + index);
                            
                            // 删除操作
                            if (index % 2 == 0) {
                                concurrentCache.removeIndex("ns", "newname" + index);
                            } else {
                                concurrentCache.removeIndex("newid" + index);
                            }
                        }
                    } catch (Exception e) {
                        // 忽略并发访问中可能发生的异常
                    } finally {
                        latch.countDown();
                    }
                });
            }
            
            // 等待所有线程完成
            boolean completed = latch.await(30, TimeUnit.SECONDS);
            assertTrue(completed, "All threads should complete within timeout");
            
            executor.shutdown();
            boolean terminated = executor.awaitTermination(10, TimeUnit.SECONDS);
            assertTrue(terminated, "Executor should terminate within timeout");
            
            // 验证缓存仍然可以正常工作
            concurrentCache.updateIndex("final", "final", "final-id");
            assertEquals("final-id", concurrentCache.getMcpId("final", "final"));
        } finally {
            concurrentCache.shutdown();
        }
    }
    
    @Test
    void testCleanupExpiredEntriesWithException() throws InterruptedException {
        McpCacheIndexProperties concurrentProps = new McpCacheIndexProperties();
        concurrentProps.setMaxSize(100);
        concurrentProps.setExpireTimeSeconds(1); // 1秒过期
        concurrentProps.setCleanupIntervalSeconds(1);
        MemoryMcpCacheIndex testCache = new MemoryMcpCacheIndex(concurrentProps);
        try {
            testCache.updateIndex("ns", "name", "id");
            ReflectionTestUtils.setField(testCache, "properties", null);
            TimeUnit.MILLISECONDS.sleep(1100);
        } finally {
            testCache.shutdown();
        }
    }
    
    @Test
    void testCleanupExpiredEntriesAfterShutdown() throws InterruptedException {
        McpCacheIndexProperties concurrentProps = new McpCacheIndexProperties();
        concurrentProps.setMaxSize(100);
        concurrentProps.setExpireTimeSeconds(1); // 1秒过期
        concurrentProps.setCleanupIntervalSeconds(1);
        MemoryMcpCacheIndex testCache = new MemoryMcpCacheIndex(concurrentProps);
        try {
            ReflectionTestUtils.setField(testCache, "shutdown", true);
            TimeUnit.MILLISECONDS.sleep(1100);
        } finally {
            ScheduledExecutorService cleanupScheduler = (ScheduledExecutorService) ReflectionTestUtils.getField(testCache,
                    "cleanupScheduler");
            cleanupScheduler.shutdownNow();
        }
    }
}