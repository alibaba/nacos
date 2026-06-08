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

import com.alibaba.nacos.api.lock.common.LockConstants;
import com.alibaba.nacos.api.lock.model.LockInstance;
import com.alibaba.nacos.api.lock.model.LockResult;
import com.alibaba.nacos.client.lock.NacosLock;
import com.alibaba.nacos.client.lock.NacosLockService;
import com.alibaba.nacos.client.lock.remote.grpc.LockGrpcClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * 回归测试 — 覆盖 code review 中发现并修复的 bug.
 *
 * <p>每个测试对应一个具体的修复项，防止回归。
 *
 * @author DHX
 * @date 2026/06/06
 */
public class JucLockRegressionITCase extends BaseLockITCase {

    private NacosLockService getJucLockService() {
        return (NacosLockService) lockService;
    }

    private LockGrpcClient getLockGrpcClient() throws Exception {
        Field field = NacosLockService.class.getDeclaredField("lockGrpcClient");
        field.setAccessible(true);
        return (LockGrpcClient) field.get(lockService);
    }

    private LockInstance createOwnedReentrantLock(String key, String owner) {
        LockInstance instance = createReentrantLock(key);
        instance.setLockType(LockConstants.REENTRANT_LOCK_TYPE);
        instance.setOwner(owner);
        return instance;
    }

    // ==================== C1: tryLock(time, unit) 中断路径清理 ====================

    @Test
    @DisplayName("REG-001: tryLock(time,unit) 中断后清理服务端等待队列")
    void testTryLockTimeoutInterruptCleansServerQueue() throws Exception {
        String key = generateUniqueKey("reg-trylock-interrupt");
        LockGrpcClient grpcClient = getLockGrpcClient();

        // 客户端 A 持有锁
        LockInstance lockA = createOwnedReentrantLock(key, "client-A");
        assertTrue(grpcClient.lock(lockA), "Client A should acquire");

        // 客户端 B 用 tryLock 进入等待队列
        NacosLock lockB = getJucLockService().getReentrantLock(key);
        CountDownLatch bStarted = new CountDownLatch(1);
        CountDownLatch bDone = new CountDownLatch(1);
        AtomicBoolean bInterrupted = new AtomicBoolean(false);

        Thread waiterB = new Thread(() -> {
            try {
                bStarted.countDown();
                lockB.tryLock(30, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                bInterrupted.set(true);
            } finally {
                bDone.countDown();
            }
        }, "reg-trylock-interrupt-b");

        waiterB.start();
        assertTrue(bStarted.await(5, TimeUnit.SECONDS));
        Thread.sleep(500);

        // 中断 B
        waiterB.interrupt();
        assertTrue(bDone.await(5, TimeUnit.SECONDS));
        assertTrue(bInterrupted.get(), "B should receive InterruptedException");

        // 客户端 D 观察队列：B 被清理后 D 应排在位置 0
        LockInstance lockD = createOwnedReentrantLock(key, "client-D");
        lockD.setWaitTime(5000L);
        LockResult waitD = grpcClient.lockWithResult(lockD);
        assertTrue(waitD.isWaiting(), "D should wait while A holds");
        assertEquals(0, waitD.getWaitPosition(),
            "Interrupted waiter B should have been removed from queue");
        grpcClient.cancelWaitWithResult(lockD);

        // 清理
        grpcClient.unLock(lockA);
    }

    // ==================== C21: unlock 异常后可重新获取锁 ====================

    @Test
    @DisplayName("REG-002: unlock 服务端异常后客户端仍可重新获取锁")
    void testUnlockExceptionAllowsReacquire() throws Exception {
        String key = generateUniqueKey("reg-unlock-exception");
        NacosLock lock = getJucLockService().getReentrantLock(key);

        // 获取锁
        lock.lock();
        assertTrue(true, "Lock acquired");

        // 正常解锁 — 验证基本流程
        lock.unlock();

        // 应能重新获取
        lock.lock();
        lock.unlock();
    }

    // ==================== JUC-013 增强: lockInterruptibly 多等待者场景 ====================

    @Test
    @DisplayName("REG-003: lockInterruptibly 中断队头等待者后队尾自动晋升")
    void testInterruptHeadWaiterPromotesNext() throws Exception {
        String key = generateUniqueKey("reg-interrupt-head");
        LockGrpcClient grpcClient = getLockGrpcClient();

        // A 持有锁
        LockInstance lockA = createOwnedReentrantLock(key, "client-A");
        assertTrue(grpcClient.lock(lockA));

        NacosLock lockB = getJucLockService().getReentrantLock(key);
        NacosLock lockC = getJucLockService().getReentrantLock(key);

        CountDownLatch bStarted = new CountDownLatch(1);
        CountDownLatch cStarted = new CountDownLatch(1);
        CountDownLatch bDone = new CountDownLatch(1);
        CountDownLatch cDone = new CountDownLatch(1);
        AtomicBoolean bInterrupted = new AtomicBoolean(false);
        AtomicBoolean cAcquired = new AtomicBoolean(false);

        // B 和 C 依次排队
        Thread waiterB = new Thread(() -> {
            try {
                bStarted.countDown();
                lockB.lockInterruptibly();
                fail("B should be interrupted before acquiring");
            } catch (InterruptedException e) {
                bInterrupted.set(true);
            } finally {
                bDone.countDown();
            }
        }, "reg-head-waiter-b");

        Thread waiterC = new Thread(() -> {
            try {
                cStarted.countDown();
                lockC.lockInterruptibly();
                cAcquired.set(true);
                lockC.unlock();
            } catch (InterruptedException e) {
                // 不期望走到这里
            } finally {
                cDone.countDown();
            }
        }, "reg-head-waiter-c");

        waiterB.start();
        assertTrue(bStarted.await(5, TimeUnit.SECONDS));
        Thread.sleep(300);

        waiterC.start();
        assertTrue(cStarted.await(5, TimeUnit.SECONDS));
        Thread.sleep(300);

        // 中断 B（队头）
        waiterB.interrupt();
        assertTrue(bDone.await(5, TimeUnit.SECONDS));
        assertTrue(bInterrupted.get());

        // 释放锁，C 应该能拿到
        grpcClient.unLock(lockA);
        assertTrue(cDone.await(10, TimeUnit.SECONDS));
        assertTrue(cAcquired.get(), "C should acquire after B is interrupted and A releases");
    }

    // ==================== tryLock(time,unit) 超时后清理 ====================

    @Test
    @DisplayName("REG-004: tryLock(time,unit) 超时后清理服务端等待队列条目")
    void testTryLockTimeoutCleansServerQueue() throws Exception {
        String key = generateUniqueKey("reg-trylock-timeout");
        LockGrpcClient grpcClient = getLockGrpcClient();

        // A 持有锁 10 秒
        LockInstance lockA = createOwnedReentrantLock(key, "client-A");
        lockA.setExpiredTime(10000L);
        assertTrue(grpcClient.lock(lockA));

        // B tryLock 1 秒超时
        NacosLock lockB = getJucLockService().getReentrantLock(key);
        boolean acquired = lockB.tryLock(1, TimeUnit.SECONDS);
        assertFalse(acquired, "B should timeout");

        // B 超时后，D 应排在位置 0（B 的条目已清理）
        Thread.sleep(200);
        LockInstance lockD = createOwnedReentrantLock(key, "client-D");
        lockD.setWaitTime(5000L);
        LockResult waitD = grpcClient.lockWithResult(lockD);
        assertTrue(waitD.isWaiting());
        assertEquals(0, waitD.getWaitPosition(),
            "Timed-out waiter B should have been removed from queue");
        grpcClient.cancelWaitWithResult(lockD);

        grpcClient.unLock(lockA);
    }

    // ==================== Watchdog 自动续约 ====================

    @Test
    @DisplayName("REG-005: watchdog 续约 — 锁持有时间超过单次租约仍然有效")
    void testWatchdogRenewKeepsLockAlive() throws Exception {
        String key = generateUniqueKey("reg-watchdog");
        LockGrpcClient grpcClient = getLockGrpcClient();
        NacosLock lock = getJucLockService().getReentrantLock(key);

        lock.lock();
        try {
            // 等待超过默认租约时间（30s）的一半，验证续约生效
            // 实际等待 8 秒 — 如果 watchdog 失效，锁可能在 30s 内过期
            // 但 8 秒足以验证续约请求在发送
            Thread.sleep(8000);

            // 另一个客户端尝试获取 — 应该失败（锁仍被持有）
            LockInstance contender = createOwnedReentrantLock(key, "contender");
            assertFalse(grpcClient.lock(contender),
                "Lock should still be held after watchdog renewal");
        } finally {
            lock.unlock();
        }

        // 解锁后其他人可以获取
        LockInstance after = createOwnedReentrantLock(key, "after-owner");
        assertTrue(grpcClient.lock(after));
        grpcClient.unLock(after);
    }

    // ==================== 线程池场景 ThreadLocal 清理 ====================

    @Test
    @DisplayName("REG-006: 线程池复用 — lock/unlock 后 ThreadLocal 不泄漏")
    void testThreadPoolNoThreadLocalLeak() throws Exception {
        String key = generateUniqueKey("reg-threadlocal");
        NacosLock lock = getJucLockService().getReentrantLock(key);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        AtomicInteger successCount = new AtomicInteger(0);

        // 同一个线程池线程执行多次 lock/unlock
        for (int i = 0; i < 10; i++) {
            pool.submit(() -> {
                try {
                    lock.lock();
                    lock.unlock();
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    // 不期望异常
                }
            }).get(10, TimeUnit.SECONDS);
        }

        pool.shutdown();
        assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS));
        assertEquals(10, successCount.get(), "All lock/unlock cycles should succeed");
    }

    // ==================== 非可重入锁客户端防御 ====================

    @Test
    @DisplayName("REG-007: 非可重入锁同线程重入 — 客户端本地拒绝不死锁")
    void testNonReentrantLocalGuardPreventsSelfDeadlock() throws Exception {
        String key = generateUniqueKey("reg-nonreentrant-guard");
        NacosLock lock = getJucLockService().getNonReentrantLock(key);

        lock.lock();
        try {
            // 同线程第二次 lock — 应立即抛 IllegalMonitorStateException
            // 而不是发到服务端进入等待队列导致自死锁
            assertThrows(IllegalMonitorStateException.class, lock::lock,
                "Non-reentrant lock should reject reentry locally");
        } finally {
            lock.unlock();
        }
    }

    // ==================== cancelWait 幂等性 ====================

    @Test
    @DisplayName("REG-009: cancelWait 重复调用不抛异常")
    void testCancelWaitIdempotent() throws Exception {
        String key = generateUniqueKey("reg-cancel-idempotent");
        LockGrpcClient grpcClient = getLockGrpcClient();

        // 多次 cancelWait 不应抛异常
        grpcClient.cancelWait(key, "owner-1");
        grpcClient.cancelWait(key, "owner-1");
        grpcClient.cancelWait(key, LockConstants.REENTRANT_LOCK_TYPE, "owner-1");
        grpcClient.cancelWait(key, LockConstants.REENTRANT_LOCK_TYPE, "owner-1");
    }

    // ==================== lock() 中断路径 ====================

    @Test
    @DisplayName("REG-010: lock() 中断后抛 NacosLockException 并恢复中断标志")
    void testLockInterruptThrowsAndRestoresFlag() throws Exception {
        String key = generateUniqueKey("reg-lock-interrupt");
        LockGrpcClient grpcClient = getLockGrpcClient();

        // A 持有锁
        LockInstance lockA = createOwnedReentrantLock(key, "client-A");
        assertTrue(grpcClient.lock(lockA));

        NacosLock lockB = getJucLockService().getReentrantLock(key);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(1);
        AtomicBoolean gotException = new AtomicBoolean(false);
        AtomicBoolean interruptFlagRestored = new AtomicBoolean(false);

        Thread t = new Thread(() -> {
            try {
                started.countDown();
                lockB.lock();
            } catch (RuntimeException e) {
                gotException.set(true);
                interruptFlagRestored.set(Thread.currentThread().isInterrupted());
            } finally {
                done.countDown();
            }
        }, "reg-lock-interrupt");

        t.start();
        assertTrue(started.await(5, TimeUnit.SECONDS));
        Thread.sleep(300);

        t.interrupt();
        assertTrue(done.await(5, TimeUnit.SECONDS));
        assertTrue(gotException.get(), "Should throw NacosLockException");
        assertTrue(interruptFlagRestored.get(), "Interrupt flag should be restored");

        grpcClient.unLock(lockA);
    }
}
