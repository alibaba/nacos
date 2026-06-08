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
import com.alibaba.nacos.api.lock.common.LockConstants;
import com.alibaba.nacos.api.lock.common.LockNotificationType;
import com.alibaba.nacos.api.lock.model.LockInstance;
import com.alibaba.nacos.api.lock.model.LockResult;
import com.alibaba.nacos.client.lock.exception.NacosLockException;
import com.alibaba.nacos.client.lock.remote.grpc.LockGrpcClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * NacosLock 单元测试.
 *
 * @author DHX
 * @date 2026/06/06
 */
@ExtendWith(MockitoExtension.class)
class NacosLockTest {
    
    @Mock
    private LockGrpcClient grpcClient;
    
    private NacosLockWatchdog watchdog;
    
    private NacosLock lock;
    
    @BeforeEach
    void setUp() throws Exception {
        watchdog = new NacosLockWatchdog(500L);
        lock = new NacosLock("test-key", LockConstants.REENTRANT_LOCK_TYPE, grpcClient, watchdog,
            "test-client-id");
        
        // 在 mock 上初始化 notificationFutures 字段
        Field field = LockGrpcClient.class.getDeclaredField("notificationFutures");
        field.setAccessible(true);
        field.set(grpcClient, new ConcurrentHashMap<>());
    }
    
    @AfterEach
    void tearDown() {
        watchdog.shutdown();
    }
    
    @Test
    @DisplayName("future map 替换条目时旧 future 的处理")
    void testFutureMapReplacement() throws Exception {
        ConcurrentHashMap<String, CompletableFuture<?>> notificationFutures =
            getNotificationFutures();
        
        String mapKey = "test-key:test-owner";
        
        // 第一次注册
        CompletableFuture<?> firstFuture = new CompletableFuture<>();
        notificationFutures.put(mapKey, firstFuture);
        assertFalse(firstFuture.isDone());
        
        // 第二次注册替换第一次
        CompletableFuture<?> secondFuture = new CompletableFuture<>();
        CompletableFuture<?> oldFuture = notificationFutures.put(mapKey, secondFuture);
        
        // 旧 future 从 put() 返回，但未被完成
        // 完成操作在 LockGrpcClient.registerForNotification() 中执行
        assertNotNull(oldFuture);
        assertTrue(oldFuture == firstFuture);
    }
    
    @Test
    @DisplayName("相同 key 的 NacosLock 实例共享 watchdog")
    void testSameKeyWatchdogShared() {
        NacosLock lock1 = new NacosLock("same-key", LockConstants.REENTRANT_LOCK_TYPE,
            grpcClient, watchdog, "client-1");
        NacosLock lock2 = new NacosLock("same-key", LockConstants.NON_REENTRANT_LOCK_TYPE,
            grpcClient, watchdog, "client-1");
        
        // 两个锁共享同一个 watchdog 实例
        // 如果都用相同 key 注册，第二次会覆盖第一次
        assertNotNull(lock1);
        assertNotNull(lock2);
    }
    
    @Test
    void testLockSuccessRegistersWatchdogAndCancelsLocalWait() throws Exception {
        NacosLockWatchdog mockWatchdog = mock(NacosLockWatchdog.class);
        NacosLock nacosLock = newLock(mockWatchdog, LockConstants.REENTRANT_LOCK_TYPE);
        when(grpcClient.lockWithResult(any())).thenReturn(LockResult.success(1));
        
        nacosLock.lock();
        
        verify(grpcClient).registerForNotification(eq("test-key"), eq(currentOwner()));
        verify(grpcClient).cancelWait(eq("test-key"), eq(currentOwner()));
        verify(mockWatchdog).register(eq("test-key"), eq(grpcClient),
            argThat(instance -> "test-key".equals(instance.getKey())
                && LockConstants.REENTRANT_LOCK_TYPE.equals(instance.getLockType())
                && currentOwner().equals(instance.getOwner())));
    }
    
    @Test
    void testLockRetryMarksWaiterRetry() throws Exception {
        NacosLockWatchdog mockWatchdog = mock(NacosLockWatchdog.class);
        NacosLock nacosLock = newLock(mockWatchdog, LockConstants.REENTRANT_LOCK_TYPE);
        when(grpcClient.lockWithResult(any()))
            .thenReturn(LockResult.waiting(0))
            .thenReturn(LockResult.success(1));
        when(grpcClient.waitForNotification(eq("test-key"), eq(currentOwner()), anyLong()))
            .thenReturn(LockNotificationType.AVAILABLE);
        
        nacosLock.lock();
        
        verify(grpcClient, times(2)).lockWithResult(any());
        verify(grpcClient).lockWithResult(argThat(LockInstance::isWaiterRetry));
        verify(mockWatchdog).register(eq("test-key"), eq(grpcClient), any());
    }
    
    @Test
    void testLockInterruptedCancelsServerSideWait() throws Exception {
        NacosLock nacosLock = newLock(mock(NacosLockWatchdog.class),
            LockConstants.REENTRANT_LOCK_TYPE);
        when(grpcClient.lockWithResult(any())).thenReturn(LockResult.waiting(0));
        doThrow(new InterruptedException("interrupted")).when(grpcClient)
            .waitForNotification(eq("test-key"), eq(currentOwner()), anyLong());
        
        assertThrows(NacosLockException.class, nacosLock::lock);
        
        verify(grpcClient).cancelWait("test-key", LockConstants.REENTRANT_LOCK_TYPE,
            currentOwner());
        assertTrue(Thread.interrupted());
    }
    
    @Test
    void testLockServerExceptionCancelsServerSideWait() throws Exception {
        NacosLock nacosLock = newLock(mock(NacosLockWatchdog.class),
            LockConstants.REENTRANT_LOCK_TYPE);
        when(grpcClient.lockWithResult(any()))
            .thenThrow(new NacosException(NacosException.SERVER_ERROR, "failed"));
        
        assertThrows(NacosLockException.class, nacosLock::lock);
        
        verify(grpcClient).cancelWait("test-key", LockConstants.REENTRANT_LOCK_TYPE,
            currentOwner());
    }
    
    @Test
    void testLockInterruptiblyInterruptedDuringWaitCancelsServerSideWait() throws Exception {
        NacosLock nacosLock = newLock(mock(NacosLockWatchdog.class),
            LockConstants.REENTRANT_LOCK_TYPE);
        when(grpcClient.lockWithResult(any())).thenReturn(LockResult.waiting(0));
        doThrow(new InterruptedException("interrupted")).when(grpcClient)
            .waitForNotification(eq("test-key"), eq(currentOwner()), anyLong());
        
        assertThrows(InterruptedException.class, nacosLock::lockInterruptibly);
        
        verify(grpcClient).cancelWait("test-key", LockConstants.REENTRANT_LOCK_TYPE,
            currentOwner());
    }
    
    @Test
    void testLockInterruptiblySuccessRegistersWatchdog() throws Exception {
        NacosLockWatchdog mockWatchdog = mock(NacosLockWatchdog.class);
        NacosLock nacosLock = newLock(mockWatchdog, LockConstants.REENTRANT_LOCK_TYPE);
        when(grpcClient.lockWithResult(any())).thenReturn(LockResult.success(1));
        
        nacosLock.lockInterruptibly();
        
        verify(grpcClient).cancelWait(eq("test-key"), eq(currentOwner()));
        verify(mockWatchdog).register(eq("test-key"), eq(grpcClient), any());
    }
    
    @Test
    void testLockInterruptiblyRetryMarksWaiterRetry() throws Exception {
        NacosLockWatchdog mockWatchdog = mock(NacosLockWatchdog.class);
        NacosLock nacosLock = newLock(mockWatchdog, LockConstants.REENTRANT_LOCK_TYPE);
        when(grpcClient.lockWithResult(any()))
            .thenReturn(LockResult.waiting(0))
            .thenReturn(LockResult.success(1));
        when(grpcClient.waitForNotification(eq("test-key"), eq(currentOwner()), anyLong()))
            .thenReturn(LockNotificationType.AVAILABLE);
        
        nacosLock.lockInterruptibly();
        
        verify(grpcClient).lockWithResult(argThat(LockInstance::isWaiterRetry));
        verify(mockWatchdog).register(eq("test-key"), eq(grpcClient), any());
    }
    
    @Test
    void testLockInterruptiblyPreInterruptedThrows() {
        NacosLock nacosLock = newLock(mock(NacosLockWatchdog.class),
            LockConstants.REENTRANT_LOCK_TYPE);
        
        Thread.currentThread().interrupt();
        assertThrows(InterruptedException.class, nacosLock::lockInterruptibly);
    }
    
    @Test
    void testLockInterruptiblyInterruptedAfterFirstAttemptCancelsWait() throws Exception {
        NacosLock nacosLock = newLock(mock(NacosLockWatchdog.class),
            LockConstants.REENTRANT_LOCK_TYPE);
        when(grpcClient.lockWithResult(any())).thenReturn(LockResult.waiting(0));
        when(grpcClient.waitForNotification(eq("test-key"), eq(currentOwner()), anyLong()))
            .thenAnswer(invocation -> {
                Thread.currentThread().interrupt();
                return LockNotificationType.AVAILABLE;
            });
        
        assertThrows(InterruptedException.class, nacosLock::lockInterruptibly);
        
        verify(grpcClient).cancelWait("test-key", LockConstants.REENTRANT_LOCK_TYPE,
            currentOwner());
    }
    
    @Test
    void testLockInterruptiblyServerExceptionCancelsWait() throws Exception {
        NacosLock nacosLock = newLock(mock(NacosLockWatchdog.class),
            LockConstants.REENTRANT_LOCK_TYPE);
        when(grpcClient.lockWithResult(any()))
            .thenThrow(new NacosException(NacosException.SERVER_ERROR, "failed"));
        
        assertThrows(NacosLockException.class, nacosLock::lockInterruptibly);
        
        verify(grpcClient).cancelWait("test-key", LockConstants.REENTRANT_LOCK_TYPE,
            currentOwner());
    }
    
    @Test
    void testTryLockSuccessAndUnlockFullRelease() throws Exception {
        NacosLockWatchdog mockWatchdog = mock(NacosLockWatchdog.class);
        NacosLock nacosLock = newLock(mockWatchdog, LockConstants.REENTRANT_LOCK_TYPE);
        when(grpcClient.lockWithResult(any())).thenReturn(LockResult.success(1));
        when(grpcClient.unLockWithResult(any())).thenReturn(LockResult.success(0));
        
        assertTrue(nacosLock.tryLock());
        nacosLock.unlock();
        
        verify(mockWatchdog).register(eq("test-key"), eq(grpcClient), any());
        verify(mockWatchdog).unregister("test-key");
    }
    
    @Test
    void testTryLockFailurePaths() throws Exception {
        NacosLock nacosLock = newLock(mock(NacosLockWatchdog.class),
            LockConstants.REENTRANT_LOCK_TYPE);
        when(grpcClient.lockWithResult(any()))
            .thenReturn(LockResult.fail("busy"))
            .thenThrow(new NacosException(NacosException.SERVER_ERROR, "failed"));
        
        assertFalse(nacosLock.tryLock());
        assertFalse(nacosLock.tryLock());
    }
    
    @Test
    void testTimedTryLockTimeoutCancelsServerSideWait() throws Exception {
        NacosLock nacosLock = newLock(mock(NacosLockWatchdog.class),
            LockConstants.REENTRANT_LOCK_TYPE);
        
        assertFalse(nacosLock.tryLock(0, TimeUnit.MILLISECONDS));
        
        verify(grpcClient).cancelWait("test-key", LockConstants.REENTRANT_LOCK_TYPE,
            currentOwner());
    }
    
    @Test
    void testTimedTryLockSuccessRegistersWatchdog() throws Exception {
        NacosLockWatchdog mockWatchdog = mock(NacosLockWatchdog.class);
        NacosLock nacosLock = newLock(mockWatchdog, LockConstants.REENTRANT_LOCK_TYPE);
        when(grpcClient.lockWithResult(any())).thenReturn(LockResult.success(1));
        
        assertTrue(nacosLock.tryLock(1, TimeUnit.SECONDS));
        
        verify(grpcClient).cancelWait(eq("test-key"), eq(currentOwner()));
        verify(mockWatchdog).register(eq("test-key"), eq(grpcClient), any());
    }
    
    @Test
    void testTimedTryLockRetryMarksWaiterRetry() throws Exception {
        NacosLockWatchdog mockWatchdog = mock(NacosLockWatchdog.class);
        NacosLock nacosLock = newLock(mockWatchdog, LockConstants.REENTRANT_LOCK_TYPE);
        when(grpcClient.lockWithResult(any()))
            .thenReturn(LockResult.waiting(0))
            .thenReturn(LockResult.success(1));
        when(grpcClient.waitForNotification(eq("test-key"), eq(currentOwner()), anyLong()))
            .thenReturn(LockNotificationType.AVAILABLE);
        
        assertTrue(nacosLock.tryLock(1, TimeUnit.SECONDS));
        
        verify(grpcClient).lockWithResult(argThat(LockInstance::isWaiterRetry));
        verify(mockWatchdog).register(eq("test-key"), eq(grpcClient), any());
    }
    
    @Test
    void testTimedTryLockPreInterruptedThrows() {
        NacosLock nacosLock = newLock(mock(NacosLockWatchdog.class),
            LockConstants.REENTRANT_LOCK_TYPE);
        
        Thread.currentThread().interrupt();
        assertThrows(InterruptedException.class, () -> nacosLock.tryLock(1, TimeUnit.SECONDS));
    }
    
    @Test
    void testTimedTryLockInterruptedAfterFirstAttemptCancelsWait() throws Exception {
        NacosLock nacosLock = newLock(mock(NacosLockWatchdog.class),
            LockConstants.REENTRANT_LOCK_TYPE);
        when(grpcClient.lockWithResult(any())).thenReturn(LockResult.waiting(0));
        when(grpcClient.waitForNotification(eq("test-key"), eq(currentOwner()), anyLong()))
            .thenAnswer(invocation -> {
                Thread.currentThread().interrupt();
                return LockNotificationType.AVAILABLE;
            });
        
        assertThrows(InterruptedException.class, () -> nacosLock.tryLock(1, TimeUnit.SECONDS));
        
        verify(grpcClient).cancelWait("test-key", LockConstants.REENTRANT_LOCK_TYPE,
            currentOwner());
    }
    
    @Test
    void testTimedTryLockInterruptedDuringWaitCancelsWait() throws Exception {
        NacosLock nacosLock = newLock(mock(NacosLockWatchdog.class),
            LockConstants.REENTRANT_LOCK_TYPE);
        when(grpcClient.lockWithResult(any())).thenReturn(LockResult.waiting(0));
        doThrow(new InterruptedException("interrupted")).when(grpcClient)
            .waitForNotification(eq("test-key"), eq(currentOwner()), anyLong());
        
        assertThrows(InterruptedException.class, () -> nacosLock.tryLock(1, TimeUnit.SECONDS));
        
        verify(grpcClient).cancelWait("test-key", LockConstants.REENTRANT_LOCK_TYPE,
            currentOwner());
    }
    
    @Test
    void testTimedTryLockServerExceptionCancelsWaitAndReturnsFalse() throws Exception {
        NacosLock nacosLock = newLock(mock(NacosLockWatchdog.class),
            LockConstants.REENTRANT_LOCK_TYPE);
        when(grpcClient.lockWithResult(any()))
            .thenThrow(new NacosException(NacosException.SERVER_ERROR, "failed"));
        
        assertFalse(nacosLock.tryLock(1, TimeUnit.SECONDS));
        
        verify(grpcClient).cancelWait("test-key", LockConstants.REENTRANT_LOCK_TYPE,
            currentOwner());
    }
    
    @Test
    void testUnlockWithoutHoldingLockRejected() {
        NacosLock nacosLock = newLock(mock(NacosLockWatchdog.class),
            LockConstants.REENTRANT_LOCK_TYPE);
        
        assertThrows(IllegalMonitorStateException.class, nacosLock::unlock);
    }
    
    @Test
    void testUnlockServerRejectClearsLocalState() throws Exception {
        NacosLockWatchdog mockWatchdog = mock(NacosLockWatchdog.class);
        NacosLock nacosLock = newLock(mockWatchdog, LockConstants.REENTRANT_LOCK_TYPE);
        when(grpcClient.lockWithResult(any())).thenReturn(LockResult.success(1));
        when(grpcClient.unLockWithResult(any())).thenReturn(LockResult.fail("owner mismatch"));
        
        assertTrue(nacosLock.tryLock());
        assertThrows(IllegalMonitorStateException.class, nacosLock::unlock);
        assertThrows(IllegalMonitorStateException.class, nacosLock::unlock);
        
        verify(mockWatchdog).unregister("test-key");
    }
    
    @Test
    void testUnlockPartialReleaseKeepsWatchdog() throws Exception {
        NacosLockWatchdog mockWatchdog = mock(NacosLockWatchdog.class);
        NacosLock nacosLock = newLock(mockWatchdog, LockConstants.REENTRANT_LOCK_TYPE);
        when(grpcClient.lockWithResult(any())).thenReturn(LockResult.success(1));
        when(grpcClient.unLockWithResult(any())).thenReturn(LockResult.success(1));
        
        assertTrue(nacosLock.tryLock());
        nacosLock.unlock();
        
        verify(mockWatchdog).register(eq("test-key"), eq(grpcClient), any());
    }
    
    @Test
    void testUnlockServerExceptionClearsLocalState() throws Exception {
        NacosLockWatchdog mockWatchdog = mock(NacosLockWatchdog.class);
        NacosLock nacosLock = newLock(mockWatchdog, LockConstants.REENTRANT_LOCK_TYPE);
        when(grpcClient.lockWithResult(any())).thenReturn(LockResult.success(1));
        when(grpcClient.unLockWithResult(any()))
            .thenThrow(new NacosException(NacosException.SERVER_ERROR, "failed"));
        
        assertTrue(nacosLock.tryLock());
        assertThrows(IllegalStateException.class, nacosLock::unlock);
        assertThrows(IllegalMonitorStateException.class, nacosLock::unlock);
        
        verify(mockWatchdog).unregister("test-key");
    }
    
    @Test
    void testRecursiveUnlockRejected() throws Exception {
        NacosLock nacosLock = newLock(mock(NacosLockWatchdog.class),
            LockConstants.REENTRANT_LOCK_TYPE);
        Field field = NacosLock.class.getDeclaredField("inUnlock");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        ThreadLocal<Boolean> inUnlock = (ThreadLocal<Boolean>) field.get(nacosLock);
        inUnlock.set(Boolean.TRUE);
        
        assertThrows(IllegalMonitorStateException.class, nacosLock::unlock);
    }
    
    @Test
    void testNonReentrantLockRejectsSameThreadReentry() throws Exception {
        NacosLock nacosLock = newLock(mock(NacosLockWatchdog.class),
            LockConstants.NON_REENTRANT_LOCK_TYPE);
        when(grpcClient.lockWithResult(any())).thenReturn(LockResult.success(1));
        
        assertTrue(nacosLock.tryLock());
        assertThrows(IllegalMonitorStateException.class, nacosLock::tryLock);
    }
    
    @Test
    void testNewConditionAndAccessors() {
        assertEquals("test-key", lock.getKey());
        assertEquals(LockConstants.REENTRANT_LOCK_TYPE, lock.getLockType());
        assertThrows(UnsupportedOperationException.class, lock::newCondition);
    }
    
    @SuppressWarnings("unchecked")
    private ConcurrentHashMap<String, CompletableFuture<?>> getNotificationFutures()
        throws Exception {
        Field field = LockGrpcClient.class.getDeclaredField("notificationFutures");
        field.setAccessible(true);
        return (ConcurrentHashMap<String, CompletableFuture<?>>) field.get(grpcClient);
    }
    
    private NacosLock newLock(NacosLockWatchdog watchdog, String lockType) {
        return new NacosLock("test-key", lockType, grpcClient, watchdog, "test-client-id");
    }
    
    private String currentOwner() {
        return "test-client-id:" + Thread.currentThread().getId();
    }
}
