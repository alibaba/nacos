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

package com.alibaba.nacos.client.lock;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.lock.model.LockInstance;
import com.alibaba.nacos.client.lock.remote.grpc.LockGrpcClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * NacosLockWatchdog 单元测试.
 *
 * @author DHX
 * @date 2026/06/06
 */
@ExtendWith(MockitoExtension.class)
class NacosLockWatchdogTest {
    
    @Mock
    private LockGrpcClient lockGrpcClient;
    
    private NacosLockWatchdog watchdog;
    
    @BeforeEach
    void setUp() {
        // 使用较短的续约间隔加速测试
        watchdog = new NacosLockWatchdog(500L);
    }
    
    @AfterEach
    void tearDown() {
        watchdog.shutdown();
    }
    
    @Test
    @DisplayName("register() 后立即 unregister() 应清理定时任务")
    void testRegisterThenImmediateUnregisterLeaksFuture() throws Exception {
        LockInstance instance = createInstance("key-1", "owner-1");
        
        // 注册后立即注销（模拟快速 lock/unlock 场景）
        watchdog.register("key-1", lockGrpcClient, instance);
        watchdog.unregister("key-1");
        
        // 检查定时任务是否被正确取消
        Map<String, ScheduledFuture<?>> renewTasks = getRenewTasks();
        
        // BUG: 如果 unregister() 在 renewTasks.put() 之前执行，future 会泄漏
        assertTrue(renewTasks.isEmpty(),
            "register+unregister 后 renewTasks 应为空，但包含: " + renewTasks.keySet());
    }
    
    @Test
    @DisplayName("默认构造器创建可用 watchdog")
    void testDefaultConstructor() {
        NacosLockWatchdog defaultWatchdog = new NacosLockWatchdog();
        try {
            assertFalse(defaultWatchdog.isShutdown());
        } finally {
            defaultWatchdog.shutdown();
        }
    }
    
    @Test
    @DisplayName("并发 register/unregister 可能泄漏定时任务")
    void testConcurrentRegisterUnregisterLeaksFutures() throws Exception {
        // 执行多轮快速 register/unregister
        for (int i = 0; i < 100; i++) {
            String key = "key-" + i;
            LockInstance instance = createInstance(key, "owner-" + i);
            watchdog.register(key, lockGrpcClient, instance);
            watchdog.unregister(key);
        }
        
        // 等待泄漏的任务触发
        Thread.sleep(200);
        
        Map<String, ScheduledFuture<?>> renewTasks = getRenewTasks();
        
        // BUG: 部分 future 可能已泄漏
        assertTrue(renewTasks.isEmpty(),
            "100 轮 register/unregister 后 renewTasks 应为空，但有 "
                + renewTasks.size() + " 个条目: " + renewTasks.keySet());
    }
    
    @Test
    @DisplayName("续约抛 NacosException 时应注销锁")
    void testNacosExceptionDuringRenewShouldUnregister() throws Exception {
        LockInstance instance = createInstance("key-1", "owner-1");
        
        // 首次续约成功，第二次抛 NacosException，后续返回 null（默认行为）
        // NacosException 被 watchdog 捕获并记录日志，但不注销
        // 后续 null 返回应触发注销，但 watchdog 继续重试
        when(lockGrpcClient.renew(any()))
            .thenReturn(true)
            .thenThrow(new NacosException(NacosException.SERVER_ERROR, "server error"));
        
        watchdog.register("key-1", lockGrpcClient, instance);
        
        // 轮询等待最多 5 秒
        Map<String, LockInstance> lockInstances = getLockInstances();
        for (int i = 0; i < 50; i++) {
            if (!lockInstances.containsKey("key-1")) {
                break;
            }
            Thread.sleep(100);
        }
        
        // watchdog 捕获异常后仅记录日志，继续重试
        assertNull(lockInstances.get("key-1"),
            "续约抛 NacosException 后锁应被注销，但 lockInstances 仍包含它");
    }
    
    @Test
    @DisplayName("续约抛 RuntimeException 时应注销锁")
    void testRuntimeExceptionDuringRenewShouldUnregister() throws Exception {
        LockInstance instance = createInstance("key-1", "owner-1");
        
        when(lockGrpcClient.renew(any())).thenThrow(new RuntimeException("boom"));
        
        watchdog.register("key-1", lockGrpcClient, instance);
        
        Map<String, LockInstance> lockInstances = getLockInstances();
        waitUntilLockRemoved(lockInstances, "key-1");
        
        assertNull(lockInstances.get("key-1"),
            "续约抛 RuntimeException 后锁应被注销");
    }
    
    @Test
    @DisplayName("renew 返回 false 时正确注销")
    void testRenewReturningFalseCorrectlyUnregisters() throws Exception {
        LockInstance instance = createInstance("key-1", "owner-1");
        
        // 首次续约成功，第二次返回 false（触发注销）
        when(lockGrpcClient.renew(any()))
            .thenReturn(true)
            .thenReturn(false);
        
        watchdog.register("key-1", lockGrpcClient, instance);
        
        // 轮询等待最多 5 秒
        Map<String, LockInstance> lockInstances = getLockInstances();
        for (int i = 0; i < 50; i++) {
            if (!lockInstances.containsKey("key-1")) {
                break;
            }
            Thread.sleep(100);
        }
        
        // renew 返回 false 时正确触发注销
        assertNull(lockInstances.get("key-1"),
            "renew 返回 false 后锁应被注销");
    }
    
    @Test
    @DisplayName("实例已被移除时续约任务应跳过")
    void testRenewTaskSkipsRemovedInstance() throws Exception {
        LockInstance instance = createInstance("key-1", "owner-1");
        
        watchdog.register("key-1", lockGrpcClient, instance);
        getLockInstances().remove("key-1");
        Thread.sleep(1200L);
        
        assertNull(getLockInstances().get("key-1"));
    }
    
    @Test
    @DisplayName("相同 key 不同锁类型在 watchdog 中冲突")
    void testSameKeyDifferentLockTypeCollision() throws Exception {
        LockInstance reentrantInstance = createInstance("key-1", "owner-1");
        reentrantInstance.setLockType("REENTRANT");
        
        LockInstance nonReentrantInstance = createInstance("key-1", "owner-2");
        nonReentrantInstance.setLockType("NON_REENTRANT");
        
        // 两次注册使用相同的 key "key-1"
        watchdog.register("key-1", lockGrpcClient, reentrantInstance);
        
        Map<String, LockInstance> lockInstances = getLockInstances();
        assertEquals(1, lockInstances.size());
        assertEquals("owner-1", lockInstances.get("key-1").getOwner());
        
        // 第二次注册覆盖第一次
        watchdog.register("key-1", lockGrpcClient, nonReentrantInstance);
        
        // BUG: 第一次注册被静默覆盖
        assertEquals("owner-2", lockInstances.get("key-1").getOwner(),
            "第二次 register 覆盖了第一次");
        
        // 旧的定时任务也泄漏了（未被取消）
        Map<String, ScheduledFuture<?>> renewTasks = getRenewTasks();
        assertEquals(1, renewTasks.size(),
            "应只有 1 个续约任务，但有: " + renewTasks.size());
    }
    
    @Test
    @DisplayName("BUG-W3b: 冲突后 unregister 只取消第二个定时任务")
    void testUnregisterAfterCollisionOnlyCancelsSecondFuture() throws Exception {
        LockInstance instance1 = createInstance("key-1", "owner-1");
        LockInstance instance2 = createInstance("key-1", "owner-2");
        
        watchdog.register("key-1", lockGrpcClient, instance1);
        watchdog.register("key-1", lockGrpcClient, instance2);
        
        // unregister 取消第二次注册的 future
        watchdog.unregister("key-1");
        
        Map<String, ScheduledFuture<?>> renewTasks = getRenewTasks();
        
        // BUG: 第一个 future 被泄漏
        assertTrue(renewTasks.isEmpty(),
            "unregister 后 renewTasks 应为空，但包含: " + renewTasks.keySet());
    }
    
    // ==================== shutdown 行为 ====================
    
    @Test
    @DisplayName("shutdown 幂等，多次调用不抛异常")
    void testShutdownIdempotent() {
        watchdog.shutdown();
        watchdog.shutdown();
        watchdog.shutdown();
        assertTrue(watchdog.isShutdown());
    }
    
    @Test
    @DisplayName("shutdown 被中断时保留中断标记")
    void testShutdownPreservesInterruptedFlag() {
        Thread.currentThread().interrupt();
        
        watchdog.shutdown();
        
        assertTrue(Thread.interrupted());
    }
    
    @Test
    @DisplayName("shutdown 后 register 被拒绝")
    void testRegisterAfterShutdownRejected() throws Exception {
        watchdog.shutdown();
        
        LockInstance instance = createInstance("key-1", "owner-1");
        watchdog.register("key-1", lockGrpcClient, instance);
        
        Map<String, LockInstance> lockInstances = getLockInstances();
        assertTrue(lockInstances.isEmpty(),
            "shutdown 后 register 应被拒绝，lockInstances 应为空");
    }
    
    @Test
    @DisplayName("renewIntervalMs<=0 时 register 被拒绝")
    void testRegisterWithZeroIntervalRejected() throws Exception {
        NacosLockWatchdog zeroWatchdog = new NacosLockWatchdog(0L);
        try {
            LockInstance instance = createInstance("key-1", "owner-1");
            zeroWatchdog.register("key-1", lockGrpcClient, instance);
            
            Field field = NacosLockWatchdog.class.getDeclaredField("lockInstances");
            field.setAccessible(true);
            Map<String, LockInstance> lockInstances =
                (Map<String, LockInstance>) field.get(zeroWatchdog);
            assertTrue(lockInstances.isEmpty(),
                "renewIntervalMs=0 时 register 应被拒绝");
        } finally {
            zeroWatchdog.shutdown();
        }
    }
    
    @Test
    @DisplayName("expiredTime<=0 时使用默认 TTL 注册续约任务")
    void testRegisterWithNonPositiveExpiredTimeUsesDefaultTtl() throws Exception {
        LockInstance instance = createInstance("key-1", "owner-1");
        instance.setExpiredTime(-1L);
        
        watchdog.register("key-1", lockGrpcClient, instance);
        
        assertTrue(getRenewTasks().containsKey("key-1"));
        assertEquals(instance, getLockInstances().get("key-1"));
    }
    
    @Test
    @DisplayName("shutdown 超时等待未结束时调用 shutdownNow")
    void testShutdownNowWhenAwaitTerminationTimesOut() throws Exception {
        ScheduledExecutorService scheduler = replaceSchedulerWithMock();
        when(scheduler.awaitTermination(5, TimeUnit.SECONDS)).thenReturn(false);
        
        watchdog.shutdown();
        
        verify(scheduler).shutdown();
        verify(scheduler).shutdownNow();
    }
    
    @Test
    @DisplayName("shutdown 等待被中断时调用 shutdownNow 并保留中断标记")
    void testShutdownNowAndPreserveInterruptWhenAwaitTerminationInterrupted()
        throws Exception {
        ScheduledExecutorService scheduler = replaceSchedulerWithMock();
        when(scheduler.awaitTermination(5, TimeUnit.SECONDS))
            .thenThrow(new InterruptedException("interrupted"));
        
        try {
            watchdog.shutdown();
            verify(scheduler).shutdown();
            verify(scheduler).shutdownNow();
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }
    
    @Test
    @DisplayName("renew 返回 null 时正确注销")
    void testRenewReturningNullCorrectlyUnregisters() throws Exception {
        LockInstance instance = createInstance("key-1", "owner-1");
        
        when(lockGrpcClient.renew(any()))
            .thenReturn(true)
            .thenReturn(null);
        
        watchdog.register("key-1", lockGrpcClient, instance);
        
        Map<String, LockInstance> lockInstances = getLockInstances();
        for (int i = 0; i < 50; i++) {
            if (!lockInstances.containsKey("key-1")) {
                break;
            }
            Thread.sleep(100);
        }
        
        assertNull(lockInstances.get("key-1"),
            "renew 返回 null 后锁应被注销");
    }
    
    // ==================== 辅助方法 ====================
    
    @SuppressWarnings("unchecked")
    private Map<String, ScheduledFuture<?>> getRenewTasks() throws Exception {
        Field field = NacosLockWatchdog.class.getDeclaredField("renewTasks");
        field.setAccessible(true);
        return (Map<String, ScheduledFuture<?>>) field.get(watchdog);
    }
    
    @SuppressWarnings("unchecked")
    private Map<String, LockInstance> getLockInstances() throws Exception {
        Field field = NacosLockWatchdog.class.getDeclaredField("lockInstances");
        field.setAccessible(true);
        return (Map<String, LockInstance>) field.get(watchdog);
    }
    
    private ScheduledExecutorService replaceSchedulerWithMock() throws Exception {
        Field field = NacosLockWatchdog.class.getDeclaredField("scheduler");
        field.setAccessible(true);
        ScheduledExecutorService original = (ScheduledExecutorService) field.get(watchdog);
        original.shutdownNow();
        
        ScheduledExecutorService scheduler = mock(ScheduledExecutorService.class);
        field.set(watchdog, scheduler);
        return scheduler;
    }
    
    private void waitUntilLockRemoved(Map<String, LockInstance> lockInstances, String key)
        throws InterruptedException {
        for (int i = 0; i < 50; i++) {
            if (!lockInstances.containsKey(key)) {
                return;
            }
            Thread.sleep(100);
        }
    }
    
    private LockInstance createInstance(String key, String owner) {
        LockInstance instance = new LockInstance();
        instance.setKey(key);
        instance.setOwner(owner);
        instance.setExpiredTime(30000L);
        instance.setLockType("REENTRANT");
        return instance;
    }
}
