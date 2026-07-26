/*
 * Copyright 1999-2026 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.test.lock;

import com.alibaba.nacos.api.lock.model.LockInstance;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 并发锁竞争集成测试.
 *
 * @author DHX
 * @date 2026/05/30
 */
public class ConcurrentLockITCase extends BaseLockITCase {

    @Test
    @DisplayName("IT-021: 多客户端并发竞争同一把锁")
    void testConcurrentLockCompetition() throws Exception {
        String key = generateUniqueKey("concurrent-test");
        int clientCount = 10;

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(clientCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(clientCount);
        List<Future<Boolean>> futures = new ArrayList<>();

        // 启动多个客户端同时尝试加锁
        for (int i = 0; i < clientCount; i++) {
            final int clientId = i;
            Future<Boolean> future = executor.submit(() -> {
                LockInstance lock = createReentrantLock(key);
                lock.setOwner("client-" + clientId);

                startLatch.await(); // 等待所有客户端就绪

                Boolean result = lockService.lock(lock);
                if (result) {
                    successCount.incrementAndGet();
                } else {
                    failCount.incrementAndGet();
                }

                finishLatch.countDown();
                return result;
            });
            futures.add(future);
        }

        // 释放所有客户端同时开始竞争
        startLatch.countDown();

        // 等待所有客户端完成
        assertTrue(finishLatch.await(30, TimeUnit.SECONDS), "All clients should finish within 30s");

        // 验证只有一个客户端成功获取锁
        assertEquals(1, successCount.get(), "Only one client should acquire the lock");
        assertEquals(clientCount - 1, failCount.get(), "Other clients should fail to acquire the lock");

        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);
    }

    @Test
    @DisplayName("IT-022: 锁释放后其他客户端可以获取")
    void testLockReleaseAndAcquire() throws Exception {
        String key = generateUniqueKey("release-acquire-test");

        // 客户端 A 获取锁
        LockInstance lockA = createReentrantLock(key);
        lockA.setOwner("client-A");
        Boolean resultA = lockService.lock(lockA);
        assertTrue(resultA, "Client A should acquire the lock");

        // 客户端 B 尝试获取锁（应该失败）
        LockInstance lockB = createReentrantLock(key);
        lockB.setOwner("client-B");
        Boolean resultB1 = lockService.lock(lockB);
        assertTrue(!resultB1, "Client B should fail when A holds the lock");

        // 客户端 A 释放锁
        Boolean unlockA = lockService.unLock(lockA);
        assertTrue(unlockA, "Client A should release the lock");

        // 等待一小段时间确保锁完全释放
        Thread.sleep(500);

        // 客户端 B 再次尝试获取锁（应该成功）
        Boolean resultB2 = lockService.lock(lockB);
        assertTrue(resultB2, "Client B should acquire the lock after A releases");

        // 清理
        lockService.unLock(lockB);
    }

    @Test
    @DisplayName("IT-023: 高并发场景下锁的稳定性")
    void testHighConcurrencyStability() throws Exception {
        String key = generateUniqueKey("high-concurrency-test");
        int iterations = 1000;

        ExecutorService executor = Executors.newFixedThreadPool(20);
        CountDownLatch latch = new CountDownLatch(iterations);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger activeHolders = new AtomicInteger(0);
        AtomicInteger maxConcurrentHolders = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);
        AtomicInteger exceptionCount = new AtomicInteger(0);

        for (int i = 0; i < iterations; i++) {
            final int iteration = i;
            executor.submit(() -> {
                LockInstance lock = createReentrantLock(key);
                try {
                    lock.setOwner("iteration-" + iteration);
                    lock.setExpiredTime(5000L);
                    lock.setWaitTime(30000L);

                    Boolean lockResult = lockService.lock(lock);
                    if (lockResult) {
                        try {
                            successCount.incrementAndGet();
                            int currentHolders = activeHolders.incrementAndGet();
                            maxConcurrentHolders.accumulateAndGet(currentHolders, Math::max);
                            if (currentHolders > 1) {
                                conflictCount.incrementAndGet();
                            }
                            // Keep the critical section observable under high contention.
                            Thread.sleep(5);
                        } finally {
                            activeHolders.decrementAndGet();
                            lockService.unLock(lock);
                        }
                    }
                } catch (Exception e) {
                    exceptionCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(60, TimeUnit.SECONDS), "All iterations should complete within 60s");
        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);

        // 所有迭代都应该成功获取过锁（串行执行）
        assertEquals(iterations, successCount.get(),
                "All iterations should successfully acquire lock");
        assertEquals(0, exceptionCount.get(), "Lock operations should not throw exceptions");
        assertEquals(0, conflictCount.get(),
                "No concurrent access should occur in critical section");
        assertEquals(1, maxConcurrentHolders.get(),
                "Only one holder should enter critical section at a time");
    }
}
