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

import com.alibaba.nacos.client.lock.NacosLock;
import com.alibaba.nacos.client.lock.NacosLockService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

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

    // ==================== 非可重入锁测试 ====================

    @Test
    @DisplayName("JUC-014: 非可重入锁 - 拒绝同线程重入")
    void testNonReentrantLockRejectsReentry() throws Exception {
        String key = generateUniqueKey("juc-nonreentrant-reentry");
        NacosLock lock = getJucLockService().getNonReentrantLock(key);

        // 第一次加锁
        lock.lock();

        // 同线程第二次加锁（应该失败或阻塞）
        boolean acquired = lock.tryLock();
        assertFalse(acquired, "Non-reentrant lock should reject reentry from same thread");

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
}
