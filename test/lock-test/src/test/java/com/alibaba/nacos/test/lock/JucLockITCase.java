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

import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.lock.LockService;
import com.alibaba.nacos.api.lock.common.LockConstants;
import com.alibaba.nacos.api.lock.model.LockInstance;
import com.alibaba.nacos.api.lock.model.LockResult;
import com.alibaba.nacos.client.lock.NacosLock;
import com.alibaba.nacos.client.lock.NacosLockService;
import com.alibaba.nacos.client.lock.remote.grpc.LockGrpcClient;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.StandardEnvironment;

import java.lang.reflect.Field;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicIntegerArray;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JUC 风格分布式锁集成测试.
 *
 * <p>测试 {@link NacosLock} 实现的 {@link java.util.concurrent.locks.Lock} 接口
 *
 * @author DHX
 * @date 2026/05/30
 */
public class JucLockITCase extends BaseLockITCase {

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

    // ==================== 可重入锁基础测试 ====================

    @Test
    @DisplayName("JUC-002: 可重入锁 - tryLock 成功")
    void testReentrantTryLock() throws Exception {
        String key = generateUniqueKey("juc-trylock");
        NacosLock lock = getJucLockService().getReentrantLock(key);

        boolean acquired = lock.tryLock();
        assertTrue(acquired, "tryLock should succeed on uncontended lock");

        lock.unlock();
    }

    @Test
    @DisplayName("JUC-003: 可重入锁 - tryLock 带超时成功")
    void testReentrantTryLockWithTimeout() throws Exception {
        String key = generateUniqueKey("juc-trylock-timeout");
        NacosLock lock = getJucLockService().getReentrantLock(key);

        boolean acquired = lock.tryLock(1, TimeUnit.SECONDS);
        assertTrue(acquired, "tryLock with timeout should succeed on uncontended lock");

        lock.unlock();
    }

    @Test
    @DisplayName("JUC-004: 可重入锁 - 同线程重入")
    void testReentrantLockReentry() throws Exception {
        String key = generateUniqueKey("juc-reentry");
        NacosLock lock = getJucLockService().getReentrantLock(key);

        // 第一次加锁
        lock.lock();

        // 同线程第二次加锁（应该成功）
        lock.lock();

        // 同线程第三次加锁（应该成功）
        lock.lock();

        // 逐层解锁
        lock.unlock();
        lock.unlock();
        lock.unlock();
    }

    @Test
    @DisplayName("JUC-005: 可重入锁 - tryLock 重入")
    void testReentrantTryLockReentry() throws Exception {
        String key = generateUniqueKey("juc-trylock-reentry");
        NacosLock lock = getJucLockService().getReentrantLock(key);

        // 第一次加锁
        boolean first = lock.tryLock();
        assertTrue(first);

        // 同线程第二次加锁（应该成功）
        boolean second = lock.tryLock();
        assertTrue(second, "Reentrant tryLock should succeed");

        // 同线程第三次加锁（应该成功）
        boolean third = lock.tryLock();
        assertTrue(third, "Reentrant tryLock should succeed");

        // 逐层解锁
        lock.unlock();
        lock.unlock();
        lock.unlock();
    }

    // ==================== 互斥测试辅助方法 ====================

    private void runMutualExclusionTest(NacosLock lock) throws Exception {
        AtomicInteger counter = new AtomicInteger(0);
        AtomicBoolean conflict = new AtomicBoolean(false);

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(2);

        Thread t1 = new Thread(() -> {
            try {
                startLatch.await();
                lock.lock();
                try {
                    enterCriticalSection(lock, counter, conflict, 2000);
                } finally {
                    lock.unlock();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                doneLatch.countDown();
            }
        });

        Thread t2 = new Thread(() -> {
            try {
                startLatch.await();
                Thread.sleep(500);
                lock.lock();
                try {
                    enterCriticalSection(lock, counter, conflict, 100);
                } finally {
                    lock.unlock();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                doneLatch.countDown();
            }
        });

        t1.start();
        t2.start();
        startLatch.countDown();

        assertTrue(doneLatch.await(15, TimeUnit.SECONDS), "Threads should complete");
        assertFalse(conflict.get(), "No concurrent access should occur");
    }

    private void enterCriticalSection(NacosLock lock, AtomicInteger counter,
            AtomicBoolean conflict, long holdTimeMs) throws InterruptedException {
        if (counter.incrementAndGet() != 1) {
            conflict.set(true);
        }
        Thread.sleep(holdTimeMs);
        if (counter.decrementAndGet() != 0) {
            conflict.set(true);
        }
    }

    // ==================== 可重入锁互斥测试 ====================

    @Test
    @DisplayName("JUC-006: 可重入锁 - 跨线程互斥")
    void testReentrantLockMutualExclusion() throws Exception {
        String key = generateUniqueKey("juc-mutex");
        NacosLock lock = getJucLockService().getReentrantLock(key);
        runMutualExclusionTest(lock);
    }

    @Test
    @DisplayName("JUC-007: 可重入锁 - tryLock 失败")
    void testReentrantTryLockFails() throws Exception {
        String key = generateUniqueKey("juc-trylock-fail");
        NacosLock lock1 = getJucLockService().getReentrantLock(key);
        NacosLock lock2 = getJucLockService().getReentrantLock(key);

        // 线程 1 持有锁
        lock1.lock();

        // 线程 2 尝试获取锁（应该失败）
        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean acquired = new AtomicBoolean(false);

        Thread t = new Thread(() -> {
            try {
                acquired.set(lock2.tryLock());
            } finally {
                latch.countDown();
            }
        });
        t.start();

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertFalse(acquired.get(), "tryLock should fail when lock is held by another thread");

        lock1.unlock();
    }

    @Test
    @DisplayName("JUC-008: 可重入锁 - tryLock 超时等待后成功")
    void testReentrantTryLockTimeoutThenSuccess() throws Exception {
        String key = generateUniqueKey("juc-trylock-timeout-success");
        NacosLock lock1 = getJucLockService().getReentrantLock(key);
        NacosLock lock2 = getJucLockService().getReentrantLock(key);

        // 线程 1 持有锁 1 秒
        lock1.lock();

        AtomicBoolean acquired = new AtomicBoolean(false);
        CountDownLatch latch = new CountDownLatch(1);

        // 线程 2 尝试获取锁，等待 3 秒
        Thread t = new Thread(() -> {
            try {
                acquired.set(lock2.tryLock(3, TimeUnit.SECONDS));
                if (acquired.get()) {
                    lock2.unlock();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                latch.countDown();
            }
        });
        t.start();

        // 1 秒后释放锁
        Thread.sleep(1000);
        lock1.unlock();

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertTrue(acquired.get(), "tryLock should succeed after lock is released");
    }

    @Test
    @DisplayName("JUC-009: 可重入锁 - tryLock 超时失败")
    void testReentrantTryLockTimeoutFails() throws Exception {
        String key = generateUniqueKey("juc-trylock-timeout-fail");
        NacosLock lock1 = getJucLockService().getReentrantLock(key);
        NacosLock lock2 = getJucLockService().getReentrantLock(key);

        // 线程 1 持有锁 5 秒
        lock1.lock();

        AtomicBoolean acquired = new AtomicBoolean(true);
        CountDownLatch latch = new CountDownLatch(1);

        // 线程 2 尝试获取锁，只等待 1 秒
        Thread t = new Thread(() -> {
            try {
                acquired.set(lock2.tryLock(1, TimeUnit.SECONDS));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                latch.countDown();
            }
        });
        t.start();

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertFalse(acquired.get(), "tryLock should timeout and fail");

        lock1.unlock();
    }

    // ==================== 可重入锁异常测试 ====================

    @Test
    @DisplayName("JUC-010: 可重入锁 - unlock 未持有锁抛出异常")
    void testReentrantUnlockWithoutLock() throws Exception {
        String key = generateUniqueKey("juc-unlock-without-lock");
        NacosLock lock = getJucLockService().getReentrantLock(key);

        assertThrows(IllegalMonitorStateException.class, lock::unlock,
                "unlock without holding lock should throw IllegalMonitorStateException");
    }

    @Test
    @DisplayName("JUC-011: 可重入锁 - newCondition 不支持")
    void testReentrantNewConditionUnsupported() throws Exception {
        String key = generateUniqueKey("juc-condition");
        NacosLock lock = getJucLockService().getReentrantLock(key);

        assertThrows(UnsupportedOperationException.class, lock::newCondition,
                "newCondition should throw UnsupportedOperationException");
    }

    @Test
    @DisplayName("JUC-012: 可重入锁 - lockInterruptibly 响应中断")
    void testReentrantLockInterruptibly() throws Exception {
        String key = generateUniqueKey("juc-interruptibly");
        NacosLock lock1 = getJucLockService().getReentrantLock(key);
        NacosLock lock2 = getJucLockService().getReentrantLock(key);

        // 线程 1 持有锁
        lock1.lock();

        AtomicBoolean interrupted = new AtomicBoolean(false);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(1);

        // 线程 2 尝试获取锁（会阻塞）
        Thread t = new Thread(() -> {
            try {
                started.countDown();
                lock2.lockInterruptibly();
            } catch (InterruptedException e) {
                interrupted.set(true);
            } finally {
                done.countDown();
            }
        });
        t.start();

        // 等待线程 2 开始
        assertTrue(started.await(5, TimeUnit.SECONDS));
        Thread.sleep(500);

        // 中断线程 2
        t.interrupt();

        assertTrue(done.await(5, TimeUnit.SECONDS));
        assertTrue(interrupted.get(), "lockInterruptibly should respond to interrupt");

        lock1.unlock();
    }

    @Test
    @DisplayName("JUC-013: 可重入锁 - lockInterruptibly 中断后清理服务端等待队列")
    void testLockInterruptiblyInterruptShouldCancelServerWaiter() throws Exception {
        String key = generateUniqueKey("juc-interrupt-cancel-waiter");
        LockGrpcClient grpcClient = getLockGrpcClient();
        NacosLock lockA = getJucLockService().getReentrantLock(key);
        NacosLock lockB = getJucLockService().getReentrantLock(key);
        NacosLock lockC = getJucLockService().getReentrantLock(key);

        lockA.lock();
        boolean releasedA = false;

        AtomicBoolean bInterrupted = new AtomicBoolean(false);
        AtomicBoolean cAcquired = new AtomicBoolean(false);
        CountDownLatch bStarted = new CountDownLatch(1);
        CountDownLatch bDone = new CountDownLatch(1);
        CountDownLatch cStarted = new CountDownLatch(1);
        CountDownLatch cDone = new CountDownLatch(1);

        Thread waiterB = new Thread(() -> {
            boolean acquired = false;
            try {
                bStarted.countDown();
                lockB.lockInterruptibly();
                acquired = true;
            } catch (InterruptedException e) {
                bInterrupted.set(true);
            } finally {
                if (acquired) {
                    lockB.unlock();
                }
                bDone.countDown();
            }
        }, "lock-interruptibly-waiter-b");

        Thread waiterC = new Thread(() -> {
            boolean acquired = false;
            try {
                cStarted.countDown();
                lockC.lockInterruptibly();
                acquired = true;
                cAcquired.set(true);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                if (acquired) {
                    lockC.unlock();
                }
                cDone.countDown();
            }
        }, "lock-interruptibly-waiter-c");

        try {
            waiterB.start();
            assertTrue(bStarted.await(5, TimeUnit.SECONDS));
            Thread.sleep(500);

            waiterC.start();
            assertTrue(cStarted.await(5, TimeUnit.SECONDS));
            Thread.sleep(500);

            // B 是服务端等待队列的队头；中断 B 后，客户端应自动发送 cancelWait。
            waiterB.interrupt();
            assertTrue(bDone.await(5, TimeUnit.SECONDS));
            assertTrue(bInterrupted.get(), "Client B should exit via InterruptedException");

            // D 用底层 RPC 观察服务端队列：若 B 的 cancel 已生效，C 是队头，D 应排在 C 后面。
            LockInstance lockD = createOwnedReentrantLock(key, "client-D");
            lockD.setWaitTime(5000L);
            LockResult waitD = grpcClient.lockWithResult(lockD);
            assertTrue(waitD.isWaiting(), "Client D should wait while A still holds the lock");
            assertEquals(1, waitD.getWaitPosition(),
                    "Interrupted waiter B should have been removed from the server-side queue");
            assertTrue(grpcClient.cancelWaitWithResult(lockD).isSuccess(),
                    "Client D should cancel its observer wait entry");

            lockA.unlock();
            releasedA = true;

            assertTrue(cDone.await(5, TimeUnit.SECONDS),
                    "Client C should not wait for interrupted client B's server-side waiter to expire");
            assertTrue(cAcquired.get(), "Client C should acquire after B is interrupted and A releases");
        } finally {
            if (!releasedA) {
                lockA.unlock();
            }
            waiterB.interrupt();
            waiterC.interrupt();
            waiterB.join(1000);
            waiterC.join(1000);
        }
    }

    // ==================== 非可重入锁测试 ====================

    @Test
    @DisplayName("JUC-014: 非可重入锁 - tryLock 拒绝同线程重入")
    void testNonReentrantLockTryLockRejectsReentry() throws Exception {
        String key = generateUniqueKey("juc-nonreentrant-reentry");
        NacosLock lock = getJucLockService().getNonReentrantLock(key);

        // 第一次加锁
        lock.lock();

        // 同线程第二次加锁 - 应在客户端本地拒绝
        assertThrows(IllegalMonitorStateException.class,
            () -> lock.tryLock(),
            "Non-reentrant lock should throw IllegalMonitorStateException on reentry");

        lock.unlock();
    }

    @Test
    @DisplayName("JUC-014b: 非可重入锁 - lock() 同线程重入应在客户端拒绝")
    void testNonReentrantLockLockRejectsReentry() throws Exception {
        String key = generateUniqueKey("juc-nonreentrant-lock-reentry");
        NacosLock lock = getJucLockService().getNonReentrantLock(key);

        // 同线程第一次加锁
        lock.lock();

        // 同线程第二次加锁 - 应在客户端本地拒绝
        assertThrows(IllegalMonitorStateException.class,
                () -> lock.lock(),
                "Non-reentrant lock should throw IllegalMonitorStateException on reentry");

        lock.unlock();
    }

    @Test
    @DisplayName("JUC-014c: 非可重入锁 - tryLock() 同线程重入应在客户端拒绝")
    void testNonReentrantTryLockRejectsReentry() throws Exception {
        String key = generateUniqueKey("juc-nonreentrant-trylock-reentry");
        NacosLock lock = getJucLockService().getNonReentrantLock(key);

        // 同线程第一次加锁
        lock.lock();

        // 同线程调用 tryLock() - 应立即抛出 IllegalMonitorStateException
        assertThrows(IllegalMonitorStateException.class,
                lock::tryLock,
                "Non-reentrant lock should throw IllegalMonitorStateException on tryLock() reentry");

        lock.unlock();
    }

    @Test
    @DisplayName("JUC-014d: 非可重入锁 - tryLock(time, unit) 同线程重入应在客户端拒绝")
    void testNonReentrantTryLockWithTimeoutRejectsReentry() throws Exception {
        String key = generateUniqueKey("juc-nonreentrant-trylock-timeout-reentry");
        NacosLock lock = getJucLockService().getNonReentrantLock(key);

        // 同线程第一次加锁
        lock.lock();

        // 同线程调用 tryLock(5s) - 应立即抛出 IllegalMonitorStateException
        assertThrows(IllegalMonitorStateException.class,
                () -> lock.tryLock(5, TimeUnit.SECONDS),
                "Non-reentrant lock should throw IllegalMonitorStateException on tryLock(timeout) reentry");

        lock.unlock();
    }

    @Test
    @DisplayName("JUC-014e: 非可重入锁 - lockInterruptibly() 同线程重入应在客户端拒绝")
    void testNonReentrantLockInterruptiblyRejectsReentry() throws Exception {
        String key = generateUniqueKey("juc-nonreentrant-interruptibly-reentry");
        NacosLock lock = getJucLockService().getNonReentrantLock(key);

        // 同线程第一次加锁
        lock.lock();

        // 同线程调用 lockInterruptibly() - 应立即抛出 IllegalMonitorStateException
        assertThrows(IllegalMonitorStateException.class,
                lock::lockInterruptibly,
                "Non-reentrant lock should throw IllegalMonitorStateException on lockInterruptibly() reentry");

        lock.unlock();
    }

    @Test
    @DisplayName("JUC-015: 非可重入锁 - 跨线程互斥")
    void testNonReentrantLockMutualExclusion() throws Exception {
        String key = generateUniqueKey("juc-nonreentrant-mutex");
        NacosLock lock = getJucLockService().getNonReentrantLock(key);
        runMutualExclusionTest(lock);
    }

    // ==================== 看门狗续租测试 ====================

    @Test
    @DisplayName("JUC-016: 看门狗 - 非可重入锁长时间持有自动续租，同线程竞争方无法重入")
    void testWatchdogAutoRenew() throws Exception {
        String key = generateUniqueKey("juc-watchdog");
        NacosLock lock = getJucLockService().getNonReentrantLock(key);
        NacosLock competingLock = getJucLockService().getNonReentrantLock(key);

        // 加锁（默认 leaseTime=-1，启用看门狗）
        lock.lock();

        // 持有锁 12 秒，确保看门狗至少执行一次续租
        Thread.sleep(12000);

        // 验证锁仍然被持有：竞争方无法获取
        boolean competingAcquired = competingLock.tryLock();
        assertFalse(competingAcquired, "Competing client should not acquire lock held by watchdog");

        // 解锁后竞争方应该可以获取
        lock.unlock();
        boolean acquiredAfterRelease = competingLock.tryLock(3, TimeUnit.SECONDS);
        assertTrue(acquiredAfterRelease, "Competing client should acquire after lock is released");
        competingLock.unlock();
    }

    // ==================== 并发压力测试 ====================

    @Test
    @DisplayName("JUC-018: 可重入锁 - 多线程竞争")
    void testReentrantLockConcurrency() throws Exception {
        String key = generateUniqueKey("juc-concurrency");
        NacosLock lock = getJucLockService().getReentrantLock(key);
        AtomicInteger counter = new AtomicInteger(0);
        int threadCount = 10;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            new Thread(() -> {
                try {
                    startLatch.await();
                    lock.lock();
                    try {
                        counter.incrementAndGet();
                        Thread.sleep(100);
                    } finally {
                        lock.unlock();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            }).start();
        }

        startLatch.countDown();
        assertTrue(doneLatch.await(30, TimeUnit.SECONDS), "All threads should complete");
        assertEquals(threadCount, counter.get(), "All threads should have executed");
    }

    @Test
    @DisplayName("JUC-018b: 可重入锁 - 高并发场景下锁的稳定性")
    void testJucHighConcurrencyStability() throws Exception {
        String key = generateUniqueKey("juc-high-concurrency");
        int iterations = 1000;

        ExecutorService executor = Executors.newFixedThreadPool(20);
        CountDownLatch latch = new CountDownLatch(iterations);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger activeHolders = new AtomicInteger(0);
        AtomicInteger maxConcurrentHolders = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);
        AtomicInteger exceptionCount = new AtomicInteger(0);

        for (int i = 0; i < iterations; i++) {
            executor.submit(() -> {
                NacosLock lock = getJucLockService().getReentrantLock(key);
                boolean locked = false;
                try {
                    lock.lock();
                    locked = true;
                    successCount.incrementAndGet();
                    int currentHolders = activeHolders.incrementAndGet();
                    maxConcurrentHolders.accumulateAndGet(currentHolders, Math::max);
                    if (currentHolders > 1) {
                        conflictCount.incrementAndGet();
                    }
                    // Keep the critical section observable under high contention.
                    Thread.sleep(5);
                } catch (Exception e) {
                    exceptionCount.incrementAndGet();
                } finally {
                    try {
                        if (locked) {
                            activeHolders.decrementAndGet();
                            lock.unlock();
                        }
                    } catch (Exception e) {
                        exceptionCount.incrementAndGet();
                    } finally {
                        latch.countDown();
                    }
                }
            });
        }

        assertTrue(latch.await(60, TimeUnit.SECONDS), "All iterations should complete within 60s");
        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);

        assertEquals(iterations, successCount.get(),
                "All iterations should successfully acquire lock");
        assertEquals(0, exceptionCount.get(), "Lock operations should not throw exceptions");
        assertEquals(0, conflictCount.get(),
                "No concurrent access should occur in critical section");
        assertEquals(1, maxConcurrentHolders.get(),
                "Only one holder should enter critical section at a time");
    }

    @Test
    @DisplayName("JUC-019: 可重入锁 - 锁释放后其他线程可获取")
    void testReentrantLockHandoff() throws Exception {
        String key = generateUniqueKey("juc-handoff");
        NacosLock lock1 = getJucLockService().getReentrantLock(key);
        NacosLock lock2 = getJucLockService().getReentrantLock(key);

        // 线程 1 获取锁
        lock1.lock();

        AtomicBoolean acquired = new AtomicBoolean(false);
        CountDownLatch latch = new CountDownLatch(1);

        // 线程 2 等待获取锁
        Thread t = new Thread(() -> {
            try {
                lock2.lock();
                acquired.set(true);
                lock2.unlock();
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                latch.countDown();
            }
        });
        t.start();

        // 等待 500ms 后释放锁
        Thread.sleep(500);
        lock1.unlock();

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertTrue(acquired.get(), "Thread 2 should acquire lock after Thread 1 releases");
    }

    // ==================== 连接断开清理测试 ====================

    private LockService createSeparateLockService() throws NacosException {
        EnvUtil.setEnvironment(new StandardEnvironment());
        Properties properties = new Properties();
        properties.setProperty(PropertyKeyConst.SERVER_ADDR, SERVER_ADDR);
        properties.setProperty(PropertyKeyConst.USERNAME, "nacos");
        properties.setProperty(PropertyKeyConst.PASSWORD, "nacos");
        return NacosFactory.createLockService(properties);
    }

    @Test
    @DisplayName("JUC-020: 可重入锁连接断开清理 - 多次重入后断开，锁应完全释放")
    void testConnectionCleanupFullyReleasesReentrantLock() throws Exception {
        String key = generateUniqueKey("juc-conn-cleanup-reentrant");
        LockService separateService = createSeparateLockService();
        try {
            NacosLock lockA = ((NacosLockService) separateService).getReentrantLock(key);

            for (int i = 0; i < 100; i++) {
                lockA.lock();
            }

            // 关闭连接，触发 releaseLocksByConnection()
            separateService.shutdown();

            // 使用基类 lockService（不同连接）尝试获取同一把锁
            NacosLock lockB = getJucLockService().getReentrantLock(key);
            boolean acquired = lockB.tryLock(1, TimeUnit.SECONDS);
            assertTrue(acquired,
                    "The lock should be acquirable after the connection is cleaned up."
                            + "If it fails, it means that releaseLocksByConnection() did not fully release the reentrant lock.");
            if (acquired) {
                lockB.unlock();
            }
        } finally {
            try {
                separateService.shutdown();
            } catch (Exception ignored) {
            }
        }
    }

    // ==================== FIFO 等待队列语义测试 ====================
    /**
     * <p>时间线：
     *
     * <pre>
     * T=0ms     主线程：t0.lock() 成功，持有锁
     * T=0ms     启动 N 个等待线程 t[1..N]
     * T=1~N*ms  t[i]：tryLock(timeout) → 服务端锁被持有 → 按到达顺序入队
     *             所有 t[i] 进入 waitForNotification() 阻塞
     * T=allQueued  主线程：确认所有等待者已入队
     * T=allQueued  主线程：t0.unlock() → 服务端通知队头 t[1]
     *
     * t[1] 被唤醒 → tryLock 成功 → 记录顺序=0 → unlock → 服务端通知 t[2]
     * t[2] 被唤醒 → tryLock 成功 → 记录顺序=1 → unlock → 服务端通知 t[3]
     * ...
     * t[N] 被唤醒 → tryLock 成功 → 记录顺序=N-1 → unlock
     *
     * <p>验证：acquireOrder[i] 必须严格等于 i（FIFO 顺序）。
     * Bug 下：acquireOrder 可能出现乱序（如 [0,2,1,3,...]）。
     * </pre>
     */
    @Test
    @DisplayName("JUC-021: FIFO 语义 - 多个等待者按顺序获取锁")
    void testFifoMultipleWaitersOrdered() throws Exception {
        final int waiterCount = 100;
        String key = generateUniqueKey("juc-fifo-order");
        NacosLock holderLock = getJucLockService().getReentrantLock(key);

        // acquireOrder[i] 记录第 i 个获取锁的线程编号（0-based）
        AtomicIntegerArray acquireOrder = new AtomicIntegerArray(waiterCount);
        AtomicInteger acquireIndex = new AtomicInteger(0);
        CountDownLatch allDone = new CountDownLatch(waiterCount);

        // T=0: 主线程持有锁
        holderLock.lock();

        // 串行入队：逐个启动等待线程，每次 sleep 确保前一个已入队再启动下一个。
        // NacosLock.lock() 内部先发 gRPC 请求（服务端串行处理入队），再 waitForNotification 阻塞。
        // 200ms 足够一次 gRPC 往返 + Raft onApply 完成。
        Thread[] waiters = new Thread[waiterCount];
        for (int i = 0; i < waiterCount; i++) {
            final int threadId = i;
            NacosLock waiterLock = getJucLockService().getReentrantLock(key);
            waiters[i] = new Thread(() -> {
                try {
                    // lock() 内部：lockWithResult(waiting) → 入队 → waitForNotification 阻塞
                    waiterLock.lock();
                    try {
                        // 获取锁后记录自己的获取顺序
                        int pos = acquireIndex.getAndIncrement();
                        acquireOrder.set(pos, threadId);
                    } finally {
                        // 释放锁 → 服务端通知下一个等待者
                        waiterLock.unlock();
                        allDone.countDown();
                    }
                } catch (Exception e) {
                    allDone.countDown();
                }
            });
            waiters[i].start();
            // 等待当前线程入队后再启动下一个，确保入队顺序 = [0,1,2,3,...]
            Thread.sleep(200);
        }

        // 释放锁 → 服务端通知队头 → 链式传递
        holderLock.unlock();

        assertTrue(allDone.await(waiterCount, TimeUnit.SECONDS),
                "All waiters should complete");

        // 验证 FIFO 顺序：第 i 个获取锁的必须是线程 i
        boolean fifo = true;
        for (int i = 0; i < waiterCount; i++) {
            if (acquireOrder.get(i) != i) {
                fifo = false;
                break;
            }
        }
        assertTrue(fifo,
                "等待者应按入队顺序依次获取锁。"
                        + "实际获取顺序=" + orderToString(acquireOrder, waiterCount)
                        + "，预期=[0,1,2,3,...]。"
                        + "若乱序说明 FIFO 语义未被强制执行。");
    }

    /**
     * <p>时间线：
     *
     * <pre>
     * 场景：
     *   主线程持有锁 → 多个等待者排队 → 主线程释放锁 → 新客户端立即 tryLock()
     *
     * 预期：
     *   新客户端的 tryLock() 应返回 false，因为它不是队头等待者。
     *   acquireLock() 检测到队列中有等待者，强制将新请求入队（返回 waiting），
     *   而不是让新客户端直接获取锁。
     *
     * </pre>
     */
    @Test
    @DisplayName("JUC-022: 有等待者时新客户端 tryLock 不能抢锁")
    void testNewClientCannotStealLockWithWaiters() throws Exception {
        final int waiterCount = 5;
        String key = generateUniqueKey("juc-fifo-steal");
        NacosLock holderLock = getJucLockService().getReentrantLock(key);

        AtomicIntegerArray acquireOrder = new AtomicIntegerArray(waiterCount);
        AtomicInteger acquireIndex = new AtomicInteger(0);
        CountDownLatch allDone = new CountDownLatch(waiterCount);

        // T=0: 主线程持有锁
        holderLock.lock();

        // 串行入队：逐个启动等待线程，确保入队顺序 = [0,1,2,3,4]
        Thread[] waiters = new Thread[waiterCount];
        for (int i = 0; i < waiterCount; i++) {
            final int threadId = i;
            NacosLock waiterLock = getJucLockService().getReentrantLock(key);
            waiters[i] = new Thread(() -> {
                try {
                    waiterLock.lock();
                    try {
                        int pos = acquireIndex.getAndIncrement();
                        acquireOrder.set(pos, threadId);
                    } finally {
                        waiterLock.unlock();
                        allDone.countDown();
                    }
                } catch (Exception e) {
                    allDone.countDown();
                }
            });
            waiters[i].start();
            Thread.sleep(200);
        }

        // 释放锁后立即用全新客户端 tryLock() → 应失败（队列中有等待者）
        holderLock.unlock();
        NacosLock stealerLock = getJucLockService().getReentrantLock(key);
        boolean stolen = stealerLock.tryLock();
        if (stolen) {
            stealerLock.unlock();
        }

        assertFalse(stolen, "有等待者时新客户端不应通过 tryLock() 抢到锁");

        // 等待所有等待者完成
        assertTrue(allDone.await(waiterCount * 10L, TimeUnit.SECONDS),
                "All waiters should complete");

        // 验证 FIFO 顺序
        for (int i = 0; i < waiterCount; i++) {
            assertEquals(i, acquireOrder.get(i),
                    "等待者应按入队顺序获取锁，实际=" + orderToString(acquireOrder, waiterCount));
        }
    }

    private String orderToString(AtomicIntegerArray arr, int len) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < len; i++) {
            if (i > 0) {
                sb.append(",");
            }
            sb.append(arr.get(i));
        }
        return sb.append("]").toString();
    }
}
