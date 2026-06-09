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
import com.alibaba.nacos.api.lock.model.LockResult;
import com.alibaba.nacos.client.lock.NacosLockService;
import com.alibaba.nacos.client.lock.remote.grpc.LockGrpcClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
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
        lockB.setWaitTime(5000L); // 等待5秒

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
        lockB.setWaitTime(2000L); // 只等待2秒

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
                    lock.setWaitTime(10000L);

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
            lockB.setWaitTime(10000L);

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
            lockC.setWaitTime(10000L);

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
        lockB.setWaitTime(-1L); // 不等待，立即失败

        long startTime = System.currentTimeMillis();
        Boolean resultB = lockService.lock(lockB);
        long duration = System.currentTimeMillis() - startTime;

        // 应该立即失败
        assertFalse(resultB, "Client B should fail immediately");
        assertTrue(duration < 1000, "Should return immediately, actual: " + duration);

        // 清理
        lockService.unLock(lockA);
    }

    @Test
    @DisplayName("IT-021: 等待队列 - 非队头 waiterRetry 不能抢占空锁")
    void testNonHeadWaiterRetryMustNotAcquireFreeLock() throws Exception {
        String key = generateUniqueKey("non-head-retry");
        LockGrpcClient grpcClient = getLockGrpcClient();

        // 构造 A 持锁、B/C 入队等待的场景，其中 B 是队头，C 是非队头 waiter。
        LockInstance lockA = createOwnedReentrantLock(key, "client-A");
        LockResult resultA = grpcClient.lockWithResult(lockA);
        assertTrue(resultA.isSuccess(), "Client A should acquire the lock");

        LockInstance lockB = createOwnedReentrantLock(key, "client-B");
        lockB.setWaitTime(10000L);
        LockResult resultB = grpcClient.lockWithResult(lockB);
        assertFalse(resultB.isSuccess(), "Client B should wait while A holds the lock");
        assertTrue(resultB.isWaiting(), "Client B should be queued as the first waiter");

        LockInstance lockC = createOwnedReentrantLock(key, "client-C");
        lockC.setWaitTime(10000L);
        LockResult resultC = grpcClient.lockWithResult(lockC);
        assertFalse(resultC.isSuccess(), "Client C should wait while A holds the lock");
        assertTrue(resultC.isWaiting(), "Client C should be queued behind B");

        LockInstance retryC = createOwnedReentrantLock(key, "client-C");
        retryC.setWaiterRetry(true);
        LockResult retryResult = null;
        boolean releasedA = false;
        try {
            // A 释放后锁变为空锁，但 FIFO 语义要求只有队头 B 可以通过 waiterRetry 获锁。
            assertTrue(lockService.unLock(lockA), "Client A should release the lock");
            releasedA = true;

            // C 即使带 waiterRetry 重试，也不能跳过 B 抢占这个空锁。
            retryResult = grpcClient.lockWithResult(retryC);
            assertFalse(retryResult.isSuccess(),
                    "Non-head waiter retry must not acquire a clear lock before the queue head");
        } finally {
            if (retryResult != null && retryResult.isSuccess()) {
                lockService.unLock(retryC);
            }
            if (!releasedA) {
                lockService.unLock(lockA);
            }
        }
    }

    @Test
    @DisplayName("IT-022: 等待队列 - 非队头抢锁不应残留 stale waiter")
    void testOutOfOrderRetryMustNotLeaveStaleWaiter() throws Exception {
        String key = generateUniqueKey("stale-waiter");
        LockGrpcClient grpcClient = getLockGrpcClient();

        // 构造 A 持锁、B 为队头、C 为非队头的等待队列，用于复现乱序 retry。
        LockInstance lockA = createOwnedReentrantLock(key, "client-A");
        assertTrue(grpcClient.lockWithResult(lockA).isSuccess(), "Client A should acquire the lock");

        LockInstance lockB = createOwnedReentrantLock(key, "client-B");
        lockB.setWaitTime(10000L);
        LockResult waitB = grpcClient.lockWithResult(lockB);
        assertTrue(waitB.isWaiting(), "Client B should be queued as the first waiter");

        LockInstance lockC = createOwnedReentrantLock(key, "client-C");
        lockC.setWaitTime(10000L);
        LockResult waitC = grpcClient.lockWithResult(lockC);
        assertTrue(waitC.isWaiting(), "Client C should be queued behind B");

        LockInstance retryC = createOwnedReentrantLock(key, "client-C");
        retryC.setWaiterRetry(true);
        LockInstance retryB = createOwnedReentrantLock(key, "client-B");
        retryB.setWaiterRetry(true);
        LockInstance lockD = createOwnedReentrantLock(key, "client-D");

        boolean cAcquired = false;
        boolean bAcquired = false;
        boolean dAcquired = false;
        try {
            assertTrue(lockService.unLock(lockA), "Client A should release the lock");

            // C 不是队头，即使设置 waiterRetry 也必须失败，避免乱序获锁后残留 stale waiter。
            LockResult retryCResult = grpcClient.lockWithResult(retryC);
            assertFalse(retryCResult.isSuccess(), "Non-head waiter retry should be rejected");

            LockResult retryBResult = grpcClient.lockWithResult(retryB);
            assertTrue(retryBResult.isSuccess(), "Client B should acquire as the queue head");
            bAcquired = true;
            assertTrue(lockService.unLock(retryB), "Client B should release the lock");
            bAcquired = false;

            LockResult retryCAfterBResult = grpcClient.lockWithResult(retryC);
            assertTrue(retryCAfterBResult.isSuccess(), "Client C should acquire after B releases");
            cAcquired = true;
            assertTrue(lockService.unLock(retryC), "Client C should release the lock");
            cAcquired = false;

            // B/C 都释放后，新客户端 D 应能立即获锁；若 C 残留为 stale waiter，这里会失败。
            LockResult resultD = grpcClient.lockWithResult(lockD);
            dAcquired = resultD.isSuccess();
            assertTrue(resultD.isSuccess(),
                    "Fresh client should acquire after queued waiters have completed; stale waiter blocks it");
        } finally {
            if (dAcquired) {
                lockService.unLock(lockD);
            }
            if (bAcquired) {
                lockService.unLock(retryB);
            }
            if (cAcquired) {
                lockService.unLock(retryC);
            }
        }
    }
    
    @Test
    @DisplayName("IT-023: 等待队列 - 取消等待后不应阻塞后续 waiter")
    void testCancelWaitShouldRemoveServerWaiter() throws Exception {
        String key = generateUniqueKey("local-cancel-wait");
        LockGrpcClient grpcClient = getLockGrpcClient();
        
        // 客户端 A 先持有锁，后续 B/C 都会进入等待队列。
        LockInstance lockA = createOwnedReentrantLock(key, "client-A");
        assertTrue(grpcClient.lockWithResult(lockA).isSuccess(), "Client A should acquire the lock");
        
        // 客户端 B 进入服务端等待队列，并成为当前队头。
        LockInstance lockB = createOwnedReentrantLock(key, "client-B");
        lockB.setWaitTime(5000L);
        grpcClient.registerForNotification(key, "client-B");
        LockResult waitB = grpcClient.lockWithResult(lockB);
        assertTrue(waitB.isWaiting(), "Client B should be queued as the first waiter");
        
        // B 在客户端本地取消等待；正确实现应同步删除服务端等待队列中的 B。
        LockResult cancelBResult = grpcClient.cancelWaitWithResult(lockB);
        assertTrue(cancelBResult.isSuccess(), "Client B should cancel server-side wait entry");
        
        // C 随后入队。如果 B 已从服务端删除，A 释放后 C 应成为队头。
        LockInstance lockC = createOwnedReentrantLock(key, "client-C");
        lockC.setWaitTime(5000L);
        LockResult waitC = grpcClient.lockWithResult(lockC);
        assertTrue(waitC.isWaiting(), "Client C should wait while A holds the lock");
        assertEquals(0, waitC.getWaitPosition(), "Client C should become the queue head");
        
        LockInstance retryC = createOwnedReentrantLock(key, "client-C");
        retryC.setWaiterRetry(true);
        
        boolean releasedA = false;
        boolean cAcquired = false;
        try {
            // A 释放锁后，服务端应跳过/移除已取消的 B，并通知或允许 C 作为队头重试。
            LockResult unlockAResult = grpcClient.unLockWithResult(lockA);
            assertTrue(unlockAResult.isSuccess(), unlockAResult.getErrorMessage());
            releasedA = true;
            
            // C 不应被已取消的 B 阻塞到 wait deadline 过期。
            LockResult retryCResult = grpcClient.lockWithResult(retryC);
            assertTrue(retryCResult.isSuccess(),
                    "Client C should acquire without waiting for canceled client B to expire");
            cAcquired = true;
        } finally {
            if (cAcquired) {
                lockService.unLock(retryC);
            }
            if (!releasedA) {
                lockService.unLock(lockA);
            }
        }
    }

    private LockInstance createOwnedReentrantLock(String key, String owner) {
        LockInstance instance = createReentrantLock(key);
        instance.setOwner(owner);
        return instance;
    }

    private LockGrpcClient getLockGrpcClient() throws Exception {
        Field field = NacosLockService.class.getDeclaredField("lockGrpcClient");
        field.setAccessible(true);
        return (LockGrpcClient) field.get(lockService);
    }
}
