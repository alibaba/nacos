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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 等待队列机制集成测试.
 *
 * <p>测试锁的等待队列、超时机制和公平性。
 *
 * @author DHX
 * @date 2026/05/30
 */
public class WaitQueueLockITCase extends BaseLockITCase {

    @Test
    @DisplayName("IT-016: 等待队列 - 锁被占用时加入等待队列")
    void testWaitQueueEnqueue() throws Exception {
        String key = generateUniqueKey("wait-queue-test");

        // 客户端 A 先获取锁
        LockInstance lockA = createReentrantLock(key);
        lockA.setOwner("client-A");
        Boolean resultA = lockService.lock(lockA);
        assertTrue(resultA, "Client A should acquire the lock");

        // 客户端 B 尝试获取锁，设置等待超时
        LockInstance lockB = createReentrantLock(key);
        lockB.setOwner("client-B");
        lockB.setWaitTimeMs(5000L); // 等待5秒

        // 在另一个线程中尝试加锁
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<Boolean> future = executor.submit(() -> lockService.lock(lockB));

        // 给客户端 B 一点时间进入等待队列
        Thread.sleep(1000);

        // 客户端 A 释放锁
        Boolean unlockA = lockService.unLock(lockA);
        assertTrue(unlockA, "Client A should release the lock");

        // 等待客户端 B 获取锁
        Boolean resultB = future.get(10, TimeUnit.SECONDS);
        assertTrue(resultB, "Client B should acquire the lock after A releases");

        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);
    }

    @Test
    @DisplayName("IT-017: 等待队列 - 等待超时返回失败")
    void testWaitQueueTimeout() throws Exception {
        String key = generateUniqueKey("wait-timeout-test");

        // 客户端 A 获取锁，设置较长过期时间
        LockInstance lockA = createReentrantLock(key);
        lockA.setOwner("client-A");
        lockA.setExpiredTime(30000L); // 30秒
        Boolean resultA = lockService.lock(lockA);
        assertTrue(resultA, "Client A should acquire the lock");

        // 客户端 B 尝试获取锁，设置较短的等待超时
        LockInstance lockB = createReentrantLock(key);
        lockB.setOwner("client-B");
        lockB.setWaitTimeMs(2000L); // 只等待2秒

        long startTime = System.currentTimeMillis();
        Boolean resultB = lockService.lock(lockB);
        long duration = System.currentTimeMillis() - startTime;

        // 应该超时失败
        assertFalse(resultB, "Client B should timeout");

        // 等待时间应该在2秒左右（允许一定误差）
        assertTrue(duration >= 1900 && duration <= 3000,
            "Wait duration should be around 2 seconds, actual: " + duration);

        // 客户端 A 释放锁
        lockService.unLock(lockA);
    }

    @Test
    @DisplayName("IT-018: 等待队列 - 多个客户端依次获取锁")
    void testWaitQueueSequentialAcquisition() throws Exception {
        String key = generateUniqueKey("wait-sequential-test");

        // 客户端 A 先获取锁
        LockInstance lockA = createReentrantLock(key);
        lockA.setOwner("client-A");
        Boolean resultA = lockService.lock(lockA);
        assertTrue(resultA);

        // 创建3个客户端同时竞争同一把锁
        int clientCount = 3;
        CountDownLatch startLatch = new CountDownLatch(clientCount);
        CountDownLatch finishLatch = new CountDownLatch(clientCount);
        AtomicInteger acquireCount = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(clientCount);

        for (int i = 0; i < clientCount; i++) {
            final int clientId = i;
            executor.submit(() -> {
                try {
                    LockInstance lock = createReentrantLock(key);
                    lock.setOwner("client-" + clientId);
                    lock.setWaitTimeMs(10000L);

                    startLatch.countDown();
                    startLatch.await();

                    Boolean result = lockService.lock(lock);
                    if (result) {
                        acquireCount.incrementAndGet();
                        Thread.sleep(200);
                        lockService.unLock(lock);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    finishLatch.countDown();
                }
            });
        }

        // 等待所有客户端进入等待队列
        Thread.sleep(1000);

        // 客户端 A 释放锁
        lockService.unLock(lockA);

        // 等待所有客户端完成
        assertTrue(finishLatch.await(15, TimeUnit.SECONDS), "All clients should finish");

        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);

        // 验证所有客户端都成功获取了锁
        assertEquals(3, acquireCount.get(), "All 3 clients should acquire the lock");
    }

    @Test
    @DisplayName("IT-019: 等待队列 - 多个等待者依次获取锁")
    void testWaitQueueMultipleWaiters() throws Exception {
        String key = generateUniqueKey("multi-waiter-test");

        // 客户端 A 获取锁
        LockInstance lockA = createReentrantLock(key);
        lockA.setOwner("client-A");
        Boolean resultA = lockService.lock(lockA);
        assertTrue(resultA);

        // 客户端 B 和 C 加入等待队列
        CountDownLatch bStarted = new CountDownLatch(1);
        CountDownLatch bAcquired = new CountDownLatch(1);
        CountDownLatch cAcquired = new CountDownLatch(1);

        ExecutorService executor = Executors.newFixedThreadPool(2);

        // 客户端 B
        executor.submit(() -> {
            LockInstance lockB = createReentrantLock(key);
            lockB.setOwner("client-B");
            lockB.setWaitTimeMs(10000L);

            bStarted.countDown();
            try {
                Boolean resultB = lockService.lock(lockB);
                if (resultB) {
                    bAcquired.countDown();
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    lockService.unLock(lockB);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        // 客户端 C
        executor.submit(() -> {
            LockInstance lockC = createReentrantLock(key);
            lockC.setOwner("client-C");
            lockC.setWaitTimeMs(10000L);

            try {
                Boolean resultC = lockService.lock(lockC);
                if (resultC) {
                    cAcquired.countDown();
                    lockService.unLock(lockC);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        // 等待客户端 B 开始等待
        bStarted.await();
        Thread.sleep(500);

        // 客户端 A 释放锁
        lockService.unLock(lockA);

        // 验证 B 和 C 都能获取到锁
        assertTrue(bAcquired.await(5, TimeUnit.SECONDS), "Client B should acquire the lock");
        assertTrue(cAcquired.await(5, TimeUnit.SECONDS), "Client C should acquire the lock");

        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);
    }

    @Test
    @DisplayName("IT-020: 等待队列 - 立即失败（不等待）")
    void testWaitQueueNoWait() throws Exception {
        String key = generateUniqueKey("no-wait-test");

        // 客户端 A 获取锁
        LockInstance lockA = createReentrantLock(key);
        lockA.setOwner("client-A");
        Boolean resultA = lockService.lock(lockA);
        assertTrue(resultA);

        // 客户端 B 尝试获取锁，不等待
        LockInstance lockB = createReentrantLock(key);
        lockB.setOwner("client-B");
        lockB.setWaitTimeMs(-1L); // 不等待，立即失败

        long startTime = System.currentTimeMillis();
        Boolean resultB = lockService.lock(lockB);
        long duration = System.currentTimeMillis() - startTime;

        // 应该立即失败
        assertFalse(resultB, "Client B should fail immediately");
        assertTrue(duration < 1000, "Should return immediately, actual: " + duration);

        // 清理
        lockService.unLock(lockA);
    }
}
