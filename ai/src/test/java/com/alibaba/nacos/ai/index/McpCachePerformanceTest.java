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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * MCP缓存性能测试.
 *
 * @author xinluo
 */
public class McpCachePerformanceTest {
    
    private static final int THREAD_COUNT = 10;
    
    private static final int OPERATION_COUNT = 10000;
    
    private static final int CACHE_SIZE = 1000;
    
    public static void main(String[] args) {
        System.out.println("开始MCP缓存性能测试...");
        
        // 测试内存缓存性能
        testMemoryCachePerformance();
        
        // 测试并发性能
        testConcurrentPerformance();
        
        System.out.println("性能测试完成！");
    }
    
    /**
     * 测试内存缓存性能.
     */
    private static void testMemoryCachePerformance() {
        System.out.println("\n=== 内存缓存性能测试 ===");
        
        MemoryMcpCacheIndex cacheIndex = new MemoryMcpCacheIndex(CACHE_SIZE, 3600, 300);
        
        // 预热缓存
        System.out.println("预热缓存...");
        for (int i = 0; i < CACHE_SIZE; i++) {
            String namespaceId = "namespace-" + (i % 10);
            String mcpName = "mcp-" + i;
            String mcpId = "id-" + i;
            cacheIndex.updateIndex(namespaceId, mcpName, mcpId);
        }
        
        // 测试查询性能
        System.out.println("测试查询性能...");
        long startTime = System.nanoTime();
        
        for (int i = 0; i < OPERATION_COUNT; i++) {
            String namespaceId = "namespace-" + (i % 10);
            String mcpName = "mcp-" + (i % CACHE_SIZE);
            cacheIndex.getMcpId(namespaceId, mcpName);
        }
        
        long endTime = System.nanoTime();
        long duration = TimeUnit.NANOSECONDS.toMillis(endTime - startTime);
        
        System.out.printf("查询 %d 次操作耗时: %d ms\n", OPERATION_COUNT, duration);
        System.out.printf("平均每次查询耗时: %.2f μs\n", (duration * 1000.0) / OPERATION_COUNT);
        
        // 测试更新性能
        System.out.println("测试更新性能...");
        startTime = System.nanoTime();
        
        for (int i = 0; i < OPERATION_COUNT; i++) {
            String namespaceId = "namespace-" + (i % 10);
            String mcpName = "mcp-" + i;
            String mcpId = "id-" + i;
            cacheIndex.updateIndex(namespaceId, mcpName, mcpId);
        }
        
        endTime = System.nanoTime();
        duration = TimeUnit.NANOSECONDS.toMillis(endTime - startTime);
        
        System.out.printf("更新 %d 次操作耗时: %d ms\n", OPERATION_COUNT, duration);
        System.out.printf("平均每次更新耗时: %.2f μs\n", (duration * 1000.0) / OPERATION_COUNT);
        
        // 显示缓存统计
        McpCacheIndex.CacheStats stats = cacheIndex.getStats();
        System.out.printf("缓存统计 - 命中次数: %d, 未命中次数: %d, 命中率: %.2f%%, 缓存大小: %d\n",
                stats.getHitCount(), stats.getMissCount(), stats.getHitRate() * 100, stats.getSize());
    }
    
    /**
     * 测试并发性能.
     */
    private static void testConcurrentPerformance() {
        System.out.println("\n=== 并发性能测试 ===");
        
        MemoryMcpCacheIndex cacheIndex = new MemoryMcpCacheIndex(CACHE_SIZE, 3600, 300);
        
        // 预热缓存
        System.out.println("预热缓存...");
        for (int i = 0; i < CACHE_SIZE; i++) {
            String namespaceId = "namespace-" + (i % 10);
            String mcpName = "mcp-" + i;
            String mcpId = "id-" + i;
            cacheIndex.updateIndex(namespaceId, mcpName, mcpId);
        }
        
        // 并发查询测试
        System.out.printf("启动 %d 个线程进行并发查询测试...\n", THREAD_COUNT);
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        CountDownLatch latch = new CountDownLatch(THREAD_COUNT);
        AtomicLong totalOperations = new AtomicLong(0);
        
        long startTime = System.nanoTime();
        
        for (int t = 0; t < THREAD_COUNT; t++) {
            final int threadId = t;
            executor.submit(() -> {
                try {
                    for (int i = 0; i < OPERATION_COUNT; i++) {
                        String namespaceId = "namespace-" + (threadId % 10);
                        String mcpName = "mcp-" + (i % CACHE_SIZE);
                        cacheIndex.getMcpId(namespaceId, mcpName);
                        totalOperations.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }
        
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        long endTime = System.nanoTime();
        long duration = TimeUnit.NANOSECONDS.toMillis(endTime - startTime);
        
        System.out.printf("并发查询 %d 次操作耗时: %d ms\n", totalOperations.get(), duration);
        System.out.printf("平均每次查询耗时: %.2f μs\n", (duration * 1000.0) / totalOperations.get());
        System.out.printf("吞吐量: %.2f ops/ms\n", (double) totalOperations.get() / duration);
        
        // 并发更新测试
        System.out.printf("启动 %d 个线程进行并发更新测试...\n", THREAD_COUNT);
        CountDownLatch updateLatch = new CountDownLatch(THREAD_COUNT);
        AtomicLong totalUpdates = new AtomicLong(0);
        
        startTime = System.nanoTime();
        
        for (int t = 0; t < THREAD_COUNT; t++) {
            final int threadId = t;
            executor.submit(() -> {
                try {
                    for (int i = 0; i < OPERATION_COUNT; i++) {
                        String namespaceId = "namespace-" + (threadId % 10);
                        String mcpName = "mcp-" + (threadId * 1000 + i);
                        String mcpId = "id-" + (threadId * 1000 + i);
                        cacheIndex.updateIndex(namespaceId, mcpName, mcpId);
                        totalUpdates.incrementAndGet();
                    }
                } finally {
                    updateLatch.countDown();
                }
            });
        }
        
        try {
            updateLatch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        endTime = System.nanoTime();
        duration = TimeUnit.NANOSECONDS.toMillis(endTime - startTime);
        
        System.out.printf("并发更新 %d 次操作耗时: %d ms\n", totalUpdates.get(), duration);
        System.out.printf("平均每次更新耗时: %.2f μs\n", (duration * 1000.0) / totalUpdates.get());
        System.out.printf("吞吐量: %.2f ops/ms\n", (double) totalUpdates.get() / duration);
        
        executor.shutdown();
        
        // 显示最终缓存统计
        McpCacheIndex.CacheStats stats = cacheIndex.getStats();
        System.out.printf("最终缓存统计 - 命中次数: %d, 未命中次数: %d, 命中率: %.2f%%, 缓存大小: %d\n",
                stats.getHitCount(), stats.getMissCount(), stats.getHitRate() * 100, stats.getSize());
    }
    
    /**
     * 测试缓存大小对性能的影响.
     */
    private static void testCacheSizeImpact() {
        System.out.println("\n=== 缓存大小对性能的影响测试 ===");
        
        int[] cacheSizes = {100, 1000, 10000, 100000};
        
        for (int size : cacheSizes) {
            System.out.printf("\n测试缓存大小: %d\n", size);
            
            MemoryMcpCacheIndex cacheIndex = new MemoryMcpCacheIndex(size, 3600, 300);
            
            // 预热缓存
            for (int i = 0; i < size; i++) {
                String namespaceId = "namespace-" + (i % 10);
                String mcpName = "mcp-" + i;
                String mcpId = "id-" + i;
                cacheIndex.updateIndex(namespaceId, mcpName, mcpId);
            }
            
            // 测试查询性能
            long startTime = System.nanoTime();
            
            for (int i = 0; i < 10000; i++) {
                String namespaceId = "namespace-" + (i % 10);
                String mcpName = "mcp-" + (i % size);
                cacheIndex.getMcpId(namespaceId, mcpName);
            }
            
            long endTime = System.nanoTime();
            long duration = TimeUnit.NANOSECONDS.toMillis(endTime - startTime);
            
            System.out.printf("查询 10000 次操作耗时: %d ms, 平均: %.2f μs\n", duration, (duration * 1000.0) / 10000);
            
            // 显示内存使用情况
            Runtime runtime = Runtime.getRuntime();
            long memoryUsed = runtime.totalMemory() - runtime.freeMemory();
            System.out.printf("内存使用: %.2f MB\n", memoryUsed / (1024.0 * 1024.0));
        }
    }
} 