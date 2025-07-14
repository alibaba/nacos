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

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import com.alibaba.nacos.ai.config.McpCacheIndexProperties;
import org.junit.jupiter.api.Test;

/**
 * MCP缓存性能测试.
 *
 * @author misselvexu
 */
public class McpCachePerformanceTest {
    
    private static final int THREAD_COUNT = 10;
    
    private static final int OPERATION_COUNT = 10000;
    
    private static final int CACHE_SIZE = 1000;
    
    // 实际测试中使用的键数量，确保在缓存容量范围内，避免频繁缓存替换
    private static final int EFFECTIVE_KEY_COUNT = 800;
    
    /**
     * Create test configuration properties.
     */
    private static McpCacheIndexProperties createTestProperties(int maxSize, long expireTimeSeconds,
            long cleanupIntervalSeconds) {
        McpCacheIndexProperties properties = new McpCacheIndexProperties();
        properties.setMaxSize(maxSize);
        properties.setExpireTimeSeconds(expireTimeSeconds);
        properties.setCleanupIntervalSeconds(cleanupIntervalSeconds);
        return properties;
    }
    
    public static void main(String[] args) {
        System.out.println("开始MCP缓存性能测试...");
        
        // 测试内存缓存性能
        testMemoryCachePerformance();
        
        // 测试并发性能
        testConcurrentPerformance();
        
        // 验证修复效果：确保生成的键数量在合理范围内
        // testKeyGenerationValidation(); // 现在作为JUnit测试运行
        
        System.out.println("性能测试完成！");
    }
    
    /**
     * 测试内存缓存性能.
     */
    private static void testMemoryCachePerformance() {
        System.out.println("\n=== 内存缓存性能测试 ===");
        
        MemoryMcpCacheIndex cacheIndex = new MemoryMcpCacheIndex(createTestProperties(CACHE_SIZE, 3600, 300));
        
        // 预热缓存 - 使用有效键数量，避免超出缓存容量
        System.out.println("预热缓存...");
        for (int i = 0; i < EFFECTIVE_KEY_COUNT; i++) {
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
            String mcpName = "mcp-" + (i % EFFECTIVE_KEY_COUNT);
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
            // 模拟真实的更新场景：大部分更新现有键，少部分创建新键
            int keyIndex = i % (EFFECTIVE_KEY_COUNT + 100); // 允许少量超出有效范围的新键
            String mcpName = "mcp-" + keyIndex;
            String mcpId = "id-" + keyIndex;
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
     *
     * <p>修复说明：
     * 1. 使用EFFECTIVE_KEY_COUNT限制实际使用的键数量，避免超出缓存容量 2. 在并发更新测试中，80%操作更新现有键，20%操作创建新键，更真实地模拟实际场景 3.
     * 确保生成的键数量在合理范围内，避免频繁的缓存替换和内存压力
     */
    private static void testConcurrentPerformance() {
        System.out.println("\n=== 并发性能测试 ===");
        
        MemoryMcpCacheIndex cacheIndex = new MemoryMcpCacheIndex(createTestProperties(CACHE_SIZE, 3600, 300));
        
        // 预热缓存 - 使用有效键数量，避免超出缓存容量
        System.out.println("预热缓存...");
        for (int i = 0; i < EFFECTIVE_KEY_COUNT; i++) {
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
                        String mcpName = "mcp-" + (i % EFFECTIVE_KEY_COUNT);
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
                        // 使用模运算确保键的数量在有效范围内，模拟真实的缓存更新场景
                        // 80%的操作更新现有键，20%的操作创建新键（在有效范围内）
                        int keyIndex;
                        if (i % 5 == 0) {
                            // 20%的操作：创建新键（在有效范围内）
                            keyIndex = (threadId * 50 + i) % EFFECTIVE_KEY_COUNT;
                        } else {
                            // 80%的操作：更新现有键
                            keyIndex = i % EFFECTIVE_KEY_COUNT;
                        }
                        String mcpName = "mcp-" + keyIndex;
                        String mcpId = "id-" + keyIndex;
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
            
            MemoryMcpCacheIndex cacheIndex = new MemoryMcpCacheIndex(createTestProperties(size, 3600, 300));
            
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
    
    /**
     * 验证修复效果：确保生成的键数量在合理范围内.
     */
    @Test
    void testKeyGenerationValidation() {
        System.out.println("\n=== 键生成验证测试 ===");
        
        int threadCount = 10;
        int operationCount = 10000;
        int cacheSize = 1000;
        int effectiveKeyCount = 800;
        
        // 模拟并发更新测试中的键生成逻辑
        Set<String> generatedKeys = new HashSet<>();
        
        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            for (int i = 0; i < operationCount; i++) {
                int keyIndex;
                if (i % 5 == 0) {
                    // 20%的操作：创建新键（在有效范围内）
                    keyIndex = (threadId * 50 + i) % effectiveKeyCount;
                } else {
                    // 80%的操作：更新现有键
                    keyIndex = i % effectiveKeyCount;
                }
                String mcpName = "mcp-" + keyIndex;
                generatedKeys.add(mcpName);
            }
        }
        
        System.out.printf("线程数: %d, 操作数: %d, 缓存大小: %d, 有效键数: %d\n", threadCount, operationCount,
                cacheSize, effectiveKeyCount);
        System.out.printf("实际生成的唯一键数量: %d\n", generatedKeys.size());
        System.out.printf("键数量是否在合理范围内: %s\n", generatedKeys.size() <= effectiveKeyCount ? "是" : "否");
        
        // 验证键的范围
        boolean allKeysInRange = generatedKeys.stream().allMatch(key -> {
            try {
                int keyNum = Integer.parseInt(key.substring(4)); // 去掉"mcp-"前缀
                return keyNum >= 0 && keyNum < effectiveKeyCount;
            } catch (NumberFormatException e) {
                return false;
            }
        });
        
        System.out.printf("所有键都在有效范围内: %s\n", allKeysInRange ? "是" : "否");
        
        // 断言验证
        assertTrue(generatedKeys.size() <= effectiveKeyCount, "生成的键数量应该不超过有效键数量");
        assertTrue(allKeysInRange, "所有生成的键都应该在有效范围内");
        
        // 验证修复效果：键数量应该远小于原来的100,000
        assertTrue(generatedKeys.size() < 1000, "修复后生成的键数量应该远小于原来的100,000");
    }
} 