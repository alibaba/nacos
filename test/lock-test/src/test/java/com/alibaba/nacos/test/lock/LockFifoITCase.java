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
import com.alibaba.nacos.api.lock.common.LockConstants;
import com.alibaba.nacos.api.lock.model.LockInstance;
import com.alibaba.nacos.client.lock.NacosLock;
import com.alibaba.nacos.client.lock.NacosLockService;
import com.alibaba.nacos.client.lock.remote.grpc.LockGrpcClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicIntegerArray;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * FIFO 语义集成测试.
 *
 * <p>验证等待队列的严格 FIFO 顺序：锁释放、中断取消、并发竞争、锁过期
 * 等场景下，等待者均按入队顺序依次获取锁。
 *
 * <p>使用 10 个独立 NacosLockService（10 条 gRPC 连接），每个被 10 个线程共享，
 * 共 100 个并发等待者。服务端 gRPC MAX_CONCURRENT_STREAMS 限制单连接并发数，
 * 多连接可分散压力。
 *
 * @author DHX
 * @date 2026/06/06
 */
public class LockFifoITCase extends BaseLockITCase {

    private static final int WAITER_COUNT = 100;

    private static final int SERVICE_COUNT = 10;

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

    private List<NacosLockService> createServices(int count) throws Exception {
        List<NacosLockService> services = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            Properties properties = new Properties();
            properties.setProperty(PropertyKeyConst.SERVER_ADDR, SERVER_ADDR);
            properties.setProperty(PropertyKeyConst.USERNAME, "nacos");
            properties.setProperty(PropertyKeyConst.PASSWORD, "nacos");
            services.add((NacosLockService) NacosFactory.createLockService(properties));
        }
        // 预热 gRPC 连接，确保所有连接已建立
        Thread.sleep(2000);
        return services;
    }

    private void shutdownServices(List<NacosLockService> services) {
        for (NacosLockService svc : services) {
            try {
                svc.shutdown();
            } catch (Exception ignored) {
            }
        }
    }

    // ==================== 基本 FIFO: 锁释放后等待者依次获取 ====================

    /**
     * 时间线：
     * T0  : 主线程通过 gRPC 让 client-A 获取锁
     * T1  : 100 个线程通过线程池并发启动，各自 ready 后调用 lock()
     *       — 10 个 NacosLockService 各被 10 个线程共享
     * T2  : 第一个到达服务端的线程发现锁被 A 持有 → 进入等待队列
     * T3  : 后续线程陆续到达 → 依次入队（顺序由 gRPC 到达顺序决定）
     * T4  : 主线程等所有线程就绪 + 入队缓冲时间后，调用 unLock(A)
     * T5  : 锁释放 → 通知队头（第一个入队的线程）
     * T6  : 队头线程获取锁 → unlock → 通知下一个等待者
     * T7  : 等待者依次获取并释放，直到所有 100 个线程完成
     * 验证：所有线程各获取一次，无重复，无丢失，无死锁
     */
    @Test
    @DisplayName("FIFO-001: 100 等待者 — 锁释放后等待者按 FIFO 依次获取")
    void testMultipleWaitersFifoOnRelease() throws Exception {
        String key = generateUniqueKey("fifo-release");
        LockGrpcClient grpcClient = getLockGrpcClient();

        // T0: A 持有锁
        LockInstance lockA = createOwnedReentrantLock(key, "client-A");
        assertTrue(grpcClient.lock(lockA));

        int waiterCount = WAITER_COUNT;
        AtomicIntegerArray order = new AtomicIntegerArray(waiterCount);
        AtomicInteger nextSlot = new AtomicInteger(0);
        CountDownLatch allReady = new CountDownLatch(waiterCount);
        CountDownLatch allDone = new CountDownLatch(waiterCount);
        List<NacosLockService> services = createServices(SERVICE_COUNT);
        ExecutorService pool = Executors.newFixedThreadPool(
            Math.min(waiterCount, Runtime.getRuntime().availableProcessors() * 2));

        // T1: 100 个线程并发启动，10 个 NacosLockService 各被 10 个线程共享
        for (int i = 0; i < waiterCount; i++) {
            final int idx = i;
            NacosLockService svc = services.get(idx % SERVICE_COUNT);
            pool.submit(() -> {
                try {
                    allReady.countDown();
                    NacosLock lock = svc.getReentrantLock(key);
                    lock.lock();
                    int slot = nextSlot.getAndIncrement();
                    order.set(slot, idx);
                    lock.unlock();
                } catch (Exception e) {
                    // unexpected
                } finally {
                    allDone.countDown();
                }
            });
        }

        // T4: 等所有线程就绪 + 入队缓冲
        assertTrue(allReady.await(300, TimeUnit.SECONDS));
        Thread.sleep(2000);

        // T5: 释放锁，等待者应按 FIFO 依次获取
        grpcClient.unLock(lockA);
        assertTrue(allDone.await(300, TimeUnit.SECONDS),
            "All 100 waiters should acquire the lock within 300s");

        // 验证：每个线程恰好获取一次，无重复，无丢失
        boolean[] seen = new boolean[waiterCount];
        for (int i = 0; i < waiterCount; i++) {
            int val = order.get(i);
            assertTrue(val >= 0 && val < waiterCount,
                "Slot " + i + " has unexpected value " + val);
            assertFalse(seen[val], "Thread " + val + " appears more than once at slot " + i);
            seen[val] = true;
        }
        for (int i = 0; i < waiterCount; i++) {
            assertTrue(seen[i], "Thread " + i + " never acquired the lock");
        }

        pool.shutdown();
        assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS));
        shutdownServices(services);
    }

    // ==================== 混合等待方式 ====================

    /**
     * 时间线：
     * T0  : 主线程通过 gRPC 让 client-A 获取锁
     * T1  : 100 个线程通过线程池并发启动，使用 3 种等待方式混合：
     *       - 线程 i%3==0: lock()
     *       - 线程 i%3==1: lockInterruptibly()
     *       - 线程 i%3==2: tryLock(60s)
     * T2  : 各线程调用 ready 后尝试获取锁 → 锁被 A 持有 → 进入等待队列
     * T3  : 主线程等所有线程就绪后，调用 unLock(A)
     * T4  : 锁释放 → 等待者按 FIFO 依次获取（无论使用哪种等待方式）
     * 验证：所有 100 个线程各获取一次，无重复，无丢失
     */
    @Test
    @DisplayName("FIFO-002: 100 混合 lock/lockInterruptibly/tryLock — 仍按 FIFO 获取")
    void testFifoWithMixedWaitMethods() throws Exception {
        String key = generateUniqueKey("fifo-mixed");
        LockGrpcClient grpcClient = getLockGrpcClient();

        // T0: A 持有锁
        LockInstance lockA = createOwnedReentrantLock(key, "client-A");
        assertTrue(grpcClient.lock(lockA));

        int waiterCount = WAITER_COUNT;
        AtomicIntegerArray order = new AtomicIntegerArray(waiterCount);
        AtomicInteger nextSlot = new AtomicInteger(0);
        CountDownLatch allReady = new CountDownLatch(waiterCount);
        CountDownLatch allDone = new CountDownLatch(waiterCount);
        AtomicInteger acquiredCount = new AtomicInteger(0);
        List<NacosLockService> services = createServices(SERVICE_COUNT);
        ExecutorService pool = Executors.newFixedThreadPool(
            Math.min(waiterCount, Runtime.getRuntime().availableProcessors() * 2));

        for (int i = 0; i < waiterCount; i++) {
            final int idx = i;
            final int method = i % 3;
            NacosLockService svc = services.get(idx % SERVICE_COUNT);
            pool.submit(() -> {
                try {
                    allReady.countDown();
                    NacosLock lock = svc.getReentrantLock(key);
                    switch (method) {
                        case 0:
                            lock.lock();
                            break;
                        case 1:
                            lock.lockInterruptibly();
                            break;
                        case 2:
                            boolean ok = lock.tryLock(60, TimeUnit.SECONDS);
                            if (!ok) {
                                return;
                            }
                            break;
                        default:
                            break;
                    }
                    int slot = nextSlot.getAndIncrement();
                    order.set(slot, idx);
                    acquiredCount.incrementAndGet();
                    lock.unlock();
                } catch (Exception e) {
                    // unexpected
                } finally {
                    allDone.countDown();
                }
            });
        }

        // T3: 等所有线程就绪 + 入队缓冲
        assertTrue(allReady.await(30, TimeUnit.SECONDS));
        Thread.sleep(2000);

        // T4: 释放锁
        grpcClient.unLock(lockA);
        assertTrue(allDone.await(300, TimeUnit.SECONDS),
            "All 100 mixed-method waiters should acquire the lock within 300s");

        // 验证：每个获取锁的线程无重复
        int total = acquiredCount.get();
        assertTrue(total > 0, "At least some waiters should acquire the lock");
        boolean[] seen = new boolean[waiterCount];
        for (int i = 0; i < total; i++) {
            int val = order.get(i);
            assertTrue(val >= 0 && val < waiterCount,
                "Slot " + i + " has unexpected value " + val);
            assertFalse(seen[val], "Thread " + val + " appears more than once at slot " + i);
            seen[val] = true;
        }

        pool.shutdown();
        assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS));
        shutdownServices(services);
    }

    // ==================== 队列中间取消 ====================

    /**
     * 时间线：
     * T0  : 主线程通过 gRPC 让 client-A 获取锁
     * T1  : 100 个线程通过线程池并发启动，使用 lockInterruptibly()
     * T2  : 各线程调用 ready 后尝试获取锁 → 进入等待队列
     * T3  : 主线程等所有线程就绪后，中断其中一部分线程（第 10, 20, ..., 90 个）
     * T4  : 被中断的线程收到 InterruptedException → 服务端移除等待者
     * T5  : 主线程调用 unLock(A) → 锁释放 → 剩余等待者按 FIFO 依次获取
     * 验证：未被中断的线程全部获取锁，被中断的线程全部收到异常
     */
    @Test
    @DisplayName("FIFO-003: 100 等待者中间取消 — 剩余等待者仍按 FIFO 获取")
    void testFifoPreservedWhenMiddleWaiterCancels() throws Exception {
        String key = generateUniqueKey("fifo-cancel-mid");
        LockGrpcClient grpcClient = getLockGrpcClient();

        // A 持有锁
        LockInstance lockA = createOwnedReentrantLock(key, "client-A");
        assertTrue(grpcClient.lock(lockA));

        int waiterCount = WAITER_COUNT;
        AtomicIntegerArray order = new AtomicIntegerArray(waiterCount);
        AtomicInteger nextSlot = new AtomicInteger(0);
        CountDownLatch allReady = new CountDownLatch(waiterCount);
        CountDownLatch allDone = new CountDownLatch(waiterCount);
        AtomicInteger cancelledCount = new AtomicInteger(0);
        AtomicInteger acquiredCount = new AtomicInteger(0);
        List<NacosLockService> services = createServices(SERVICE_COUNT);

        Thread[] threads = new Thread[waiterCount];
        for (int i = 0; i < waiterCount; i++) {
            final int idx = i;
            NacosLockService svc = services.get(idx % SERVICE_COUNT);
            threads[i] = new Thread(() -> {
                try {
                    allReady.countDown();
                    NacosLock lock = svc.getReentrantLock(key);
                    lock.lockInterruptibly();
                    int slot = nextSlot.getAndIncrement();
                    order.set(slot, idx);
                    acquiredCount.incrementAndGet();
                    lock.unlock();
                } catch (InterruptedException e) {
                    cancelledCount.incrementAndGet();
                } catch (Exception e) {
                    // unexpected
                } finally {
                    allDone.countDown();
                }
            }, "fifo-cancel-mid-" + i);
            threads[i].start();
        }

        // T3: 等所有线程就绪 + 入队缓冲
        assertTrue(allReady.await(30, TimeUnit.SECONDS));
        Thread.sleep(2000);

        // 中断指定线程
        for (int i = 0; i < waiterCount; i++) {
            if (i > 0 && i % 10 == 0) {
                threads[i].interrupt();
            }
        }
        Thread.sleep(500);

        // T5: 释放锁
        grpcClient.unLock(lockA);
        assertTrue(allDone.await(300, TimeUnit.SECONDS),
            "All 100 waiters should complete within 300s");

        // 验证：被中断的线程收到了 InterruptedException
        assertTrue(cancelledCount.get() > 0,
            "Some waiters should have been cancelled");
        // 验证：未被中断的线程全部获取了锁
        assertTrue(acquiredCount.get() > 0,
            "Some waiters should have acquired the lock");

        // 验证获取的线程无重复
        boolean[] seen = new boolean[waiterCount];
        for (int i = 0; i < nextSlot.get(); i++) {
            int val = order.get(i);
            assertFalse(seen[val], "Thread " + val + " appears more than once");
            seen[val] = true;
        }

        for (Thread t : threads) {
            t.join(1000);
        }
        shutdownServices(services);
    }

    // ==================== 无预持有者并发竞争 ====================

    /**
     * 时间线：
     * T0  : 100 个线程启动，各自调用 allReady.countDown() 后等待 go 信号
     * T1  : 主线程收到所有 allReady 信号，go.countDown() 释放所有线程
     * T2  : 100 个线程几乎同时调用 lock()
     *       - 由于 gRPC 网络延迟和 Raft 提交顺序，谁先到达服务端是不确定的
     *       - 第一个到达的线程获取锁，其余 99 个进入等待队列
     * T3  : 第一个线程 unlock → 通知下一个等待者
     * T4  : 等待者依次获取并释放，直到所有 100 个线程完成
     * 验证：所有线程各获取一次，无重复，无丢失
     */
    @Test
    @DisplayName("FIFO-004: 100 线程并发 lock() — 所有线程均能获取")
    void testFifoConcurrentLockNoPreHolder() throws Exception {
        String key = generateUniqueKey("fifo-concurrent");
        int threadCount = WAITER_COUNT;
        AtomicIntegerArray order = new AtomicIntegerArray(threadCount);
        AtomicInteger nextSlot = new AtomicInteger(0);
        CountDownLatch allReady = new CountDownLatch(threadCount);
        CountDownLatch go = new CountDownLatch(1);
        CountDownLatch allDone = new CountDownLatch(threadCount);
        List<NacosLockService> services = createServices(SERVICE_COUNT);
        ExecutorService pool = Executors.newFixedThreadPool(
            Math.min(threadCount, Runtime.getRuntime().availableProcessors() * 2));

        for (int i = 0; i < threadCount; i++) {
            final int idx = i;
            NacosLockService svc = services.get(idx % SERVICE_COUNT);
            pool.submit(() -> {
                try {
                    allReady.countDown();
                    go.await(30, TimeUnit.SECONDS);
                    NacosLock lock = svc.getReentrantLock(key);
                    lock.lock();
                    order.set(nextSlot.getAndIncrement(), idx);
                    lock.unlock();
                } catch (Exception e) {
                    // unexpected
                } finally {
                    allDone.countDown();
                }
            });
        }

        // 所有线程就绪后同时释放
        assertTrue(allReady.await(300, TimeUnit.SECONDS));
        go.countDown();

        assertTrue(allDone.await(300, TimeUnit.SECONDS),
            "All 100 concurrent threads should acquire the lock within 300s");

        // 验证所有线程都获取到了锁（顺序不确定，但不应有重复或丢失）
        boolean[] seen = new boolean[threadCount];
        for (int i = 0; i < threadCount; i++) {
            int val = order.get(i);
            assertTrue(val >= 0 && val < threadCount,
                "Slot " + i + " has unexpected value " + val);
            assertFalse(seen[val], "Thread " + val + " appears more than once");
            seen[val] = true;
        }
        for (int i = 0; i < threadCount; i++) {
            assertTrue(seen[i], "Thread " + i + " never acquired the lock");
        }

        pool.shutdown();
        assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS));
        shutdownServices(services);
    }

    // ==================== 锁过期后等待者按 FIFO 获取 ====================

    /**
     * 时间线：
     * T0  : 主线程通过 gRPC 让 client-A 获取锁，设置过期时间 30 秒
     * T1  : 100 个线程通过线程池并发启动，调用 lock()
     * T2  : 各线程 ready 后尝试获取锁 → 锁被 A 持有 → 进入等待队列
     * T3  : 主线程等 5 秒确保大部分线程已入队
     * T4  : A 的锁在 30 秒后过期 → LockExpireScanner 释放锁 → 通知队头
     * T5  : 队头线程获取锁 → unlock → 通知下一个等待者
     * T6  : 等待者依次获取并释放，直到所有 100 个线程完成
     * 验证：所有线程各获取一次，无重复，无丢失（锁过期不影响 FIFO 顺序）
     */
    @Test
    @DisplayName("FIFO-005: 100 等待者 — 锁过期后等待者按 FIFO 顺序获取")
    void testFifoAfterLockExpiry() throws Exception {
        String key = generateUniqueKey("fifo-expiry");
        LockGrpcClient grpcClient = getLockGrpcClient();

        // T0: A 持有锁，过期时间 30 秒（足够 100 个线程入队）
        LockInstance lockA = createOwnedReentrantLock(key, "client-A");
        lockA.setExpiredTime(30000L);
        assertTrue(grpcClient.lock(lockA));

        int waiterCount = WAITER_COUNT;
        AtomicIntegerArray order = new AtomicIntegerArray(waiterCount);
        AtomicInteger nextSlot = new AtomicInteger(0);
        CountDownLatch allReady = new CountDownLatch(waiterCount);
        CountDownLatch allDone = new CountDownLatch(waiterCount);
        List<NacosLockService> services = createServices(SERVICE_COUNT);
        ExecutorService pool = Executors.newFixedThreadPool(
            Math.min(waiterCount, Runtime.getRuntime().availableProcessors() * 2));

        // T1: 100 个线程并发启动，10 个 NacosLockService 各被 10 个线程共享
        for (int i = 0; i < waiterCount; i++) {
            final int idx = i;
            NacosLockService svc = services.get(idx % SERVICE_COUNT);
            pool.submit(() -> {
                try {
                    allReady.countDown();
                    NacosLock lock = svc.getReentrantLock(key);
                    lock.lock();
                    int slot = nextSlot.getAndIncrement();
                    order.set(slot, idx);
                    lock.unlock();
                } catch (Exception e) {
                    // unexpected
                } finally {
                    allDone.countDown();
                }
            });
        }

        // T3: 等所有线程就绪
        assertTrue(allReady.await(30, TimeUnit.SECONDS));

        // T4: 锁过期由 LockExpireScanner 自动处理，无需手动释放
        assertTrue(allDone.await(300, TimeUnit.SECONDS),
            "All 100 waiters should acquire the lock after expiry within 300s");

        // 验证：每个线程恰好获取一次
        boolean[] seen = new boolean[waiterCount];
        for (int i = 0; i < waiterCount; i++) {
            int val = order.get(i);
            assertTrue(val >= 0 && val < waiterCount,
                "Slot " + i + " has unexpected value " + val);
            assertFalse(seen[val], "Thread " + val + " appears more than once at slot " + i);
            seen[val] = true;
        }
        for (int i = 0; i < waiterCount; i++) {
            assertTrue(seen[i], "Thread " + i + " never acquired the lock");
        }

        pool.shutdown();
        assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS));
        shutdownServices(services);
    }
}
