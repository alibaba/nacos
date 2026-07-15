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

package com.alibaba.nacos.client.lock.remote.grpc;

import com.alibaba.nacos.api.ability.constant.AbilityKey;
import com.alibaba.nacos.api.ability.constant.AbilityStatus;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.runtime.NacosRuntimeException;
import com.alibaba.nacos.api.lock.common.LockConstants;
import com.alibaba.nacos.api.lock.common.LockNotificationType;
import com.alibaba.nacos.api.lock.model.LockInstance;
import com.alibaba.nacos.api.lock.model.LockResult;
import com.alibaba.nacos.api.lock.remote.AbstractLockRequest;
import com.alibaba.nacos.api.lock.remote.request.LockNotificationRequest;
import com.alibaba.nacos.api.lock.remote.response.LockNotificationResponse;
import com.alibaba.nacos.api.lock.remote.response.LockOperationResponse;
import com.alibaba.nacos.api.remote.request.Request;
import com.alibaba.nacos.api.remote.response.ErrorResponse;
import com.alibaba.nacos.api.remote.response.Response;
import com.alibaba.nacos.api.remote.response.ServerCheckResponse;
import com.alibaba.nacos.client.env.NacosClientProperties;
import com.alibaba.nacos.client.security.SecurityProxy;
import com.alibaba.nacos.common.remote.client.RpcClient;
import com.alibaba.nacos.common.remote.client.ServerRequestHandler;
import com.alibaba.nacos.common.remote.client.ServerListFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LockGrpcClientTest {
    
    @Mock
    private RpcClient rpcClient;
    
    @Mock
    private SecurityProxy securityProxy;
    
    @Mock
    private ServerListFactory serverListFactory;
    
    private LockGrpcClient lockGrpcClient;
    
    @BeforeEach
    void setUp() throws NacosException, NoSuchFieldException, IllegalAccessException {
        lockGrpcClient = new LockGrpcClient(NacosClientProperties.PROTOTYPE, serverListFactory,
            securityProxy);
        Field rpcClientField = LockGrpcClient.class.getDeclaredField("rpcClient");
        rpcClientField.setAccessible(true);
        rpcClientField.set(lockGrpcClient, rpcClient);
    }
    
    @AfterEach
    void tearDown() throws NacosException {
        lockGrpcClient.shutdown();
    }
    
    private void mockRequest() {
        Map<String, String> context = new HashMap<>();
        when(securityProxy.getIdentityContext(any())).thenReturn(context);
        when(rpcClient.getConnectionAbility(AbilityKey.SERVER_DISTRIBUTED_LOCK))
            .thenReturn(AbilityStatus.SUPPORTED);
    }
    
    @Test
    void lockNotSupportedFeature() {
        when(rpcClient.getConnectionAbility(AbilityKey.SERVER_DISTRIBUTED_LOCK))
            .thenReturn(AbilityStatus.NOT_SUPPORTED);
        assertThrows(NacosRuntimeException.class,
            () -> lockGrpcClient.lock(newLockInstance("test", -1L)));
    }
    
    @Test
    void lockWithNacosException() throws NacosException {
        mockRequest();
        when(rpcClient.request(any(AbstractLockRequest.class), anyLong()))
            .thenThrow(new NacosException(NacosException.SERVER_ERROR, "test"));
        assertThrows(NacosException.class,
            () -> lockGrpcClient.lock(newLockInstance("test", -1L)), "test");
    }
    
    @Test
    void lockWithOtherException() throws NacosException {
        mockRequest();
        when(rpcClient.request(any(AbstractLockRequest.class), anyLong()))
            .thenThrow(new RuntimeException("test"));
        assertThrows(NacosException.class,
            () -> lockGrpcClient.lock(newLockInstance("test", -1L)),
            "Request nacos server failed: test");
    }
    
    @Test
    void lockWithUnexpectedResponse() throws NacosException {
        mockRequest();
        when(rpcClient.request(any(AbstractLockRequest.class), anyLong()))
            .thenReturn(new ServerCheckResponse());
        assertThrows(NacosException.class,
            () -> lockGrpcClient.lock(newLockInstance("test", -1L)),
            "Server return invalid response");
    }
    
    @Test
    void lockFailed() throws NacosException {
        mockRequest();
        when(rpcClient.request(any(AbstractLockRequest.class), anyLong()))
            .thenReturn(ErrorResponse.build(500, "test fail code"));
        assertThrows(NacosException.class,
            () -> lockGrpcClient.lock(newLockInstance("test", -1L)), "test fail code");
    }
    
    @Test
    void lockSuccess() throws NacosException {
        mockRequest();
        when(rpcClient.request(any(AbstractLockRequest.class), anyLong()))
            .thenReturn(new LockOperationResponse(true));
        assertTrue(lockGrpcClient.lock(newLockInstance("test", -1L)));
    }
    
    @Test
    void lockWithoutWaitQueueReturnsFalse() throws NacosException {
        mockRequest();
        when(rpcClient.request(any(AbstractLockRequest.class), anyLong()))
            .thenReturn(new LockOperationResponse(false));
        
        assertFalse(lockGrpcClient.lock(newLockInstance("test", -1L)));
    }
    
    @Test
    void lockReturnsFalseWhenClientClosed() throws NacosException {
        when(rpcClient.getConnectionAbility(AbilityKey.SERVER_DISTRIBUTED_LOCK))
            .thenReturn(AbilityStatus.SUPPORTED);
        lockGrpcClient.shutdown();
        
        assertFalse(lockGrpcClient.lock(newLockInstance("test", -1L)));
    }
    
    @Test
    void unLockNotSupportedFeature() {
        when(rpcClient.getConnectionAbility(AbilityKey.SERVER_DISTRIBUTED_LOCK))
            .thenReturn(AbilityStatus.NOT_SUPPORTED);
        assertThrows(NacosRuntimeException.class,
            () -> lockGrpcClient.unLock(newLockInstance("test", -1L)));
    }
    
    @Test
    void unlockSuccess() throws NacosException {
        mockRequest();
        when(rpcClient.request(any(AbstractLockRequest.class), anyLong()))
            .thenReturn(new LockOperationResponse(true));
        assertTrue(lockGrpcClient.unLock(newLockInstance("test", -1L)));
    }
    
    @Test
    void lockWithWaitQueueRetriesAfterNotification() throws Exception {
        mockRequest();
        LockInstance instance = newLockInstance("test", 1000L);
        when(rpcClient.request(any(AbstractLockRequest.class), anyLong()))
            .thenAnswer(invocation -> {
                completeNotificationLater("test", instance.getOwner(),
                    LockNotificationType.AVAILABLE);
                return LockOperationResponse.success(false);
            })
            .thenReturn(LockOperationResponse.success(LockResult.success(1)));
        
        assertTrue(lockGrpcClient.lock(instance));
    }
    
    @Test
    void lockWithDefaultWaitTimeUsesWaitQueue() throws Exception {
        mockRequest();
        LockInstance instance = newLockInstance("test", 0L);
        when(rpcClient.request(any(AbstractLockRequest.class), anyLong()))
            .thenAnswer(invocation -> {
                completeNotificationLater("test", instance.getOwner(),
                    LockNotificationType.TIMEOUT);
                return LockOperationResponse.success(false);
            });
        
        assertFalse(lockGrpcClient.lock(instance));
    }
    
    @Test
    void lockReturnsFalseAndRestoresInterruptWhenNotificationWaitInterrupted()
        throws Exception {
        mockRequest();
        LockInstance instance = newLockInstance("test", 1000L);
        when(rpcClient.request(any(AbstractLockRequest.class), anyLong()))
            .thenReturn(LockOperationResponse.success(false));
        
        Thread.currentThread().interrupt();
        try {
            assertFalse(lockGrpcClient.lock(instance));
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }
    
    @Test
    void lockWithWaitQueueTimeoutReturnsFalse() throws Exception {
        mockRequest();
        LockInstance instance = newLockInstance("test", 50L);
        when(rpcClient.request(any(AbstractLockRequest.class), anyLong()))
            .thenReturn(LockOperationResponse.success(false));
        
        assertFalse(lockGrpcClient.lock(instance));
    }
    
    @Test
    void lockWithStructuredWaitingResultReturnsFalseAfterTimeoutNotification() throws Exception {
        mockRequest();
        LockInstance instance = newLockInstance("test", 1000L);
        when(rpcClient.request(any(AbstractLockRequest.class), anyLong()))
            .thenAnswer(invocation -> {
                completeNotificationLater("test", instance.getOwner(),
                    LockNotificationType.TIMEOUT);
                return LockOperationResponse.success(LockResult.waiting(1));
            });
        
        assertFalse(lockGrpcClient.lock(instance));
    }
    
    @Test
    void structuredResultMethodsReturnLockResult() throws Exception {
        mockRequest();
        LockOperationResponse response = LockOperationResponse.success(LockResult.success(2));
        when(rpcClient.request(any(AbstractLockRequest.class), anyLong())).thenReturn(response);
        LockInstance instance = newLockInstance("test", -1L);
        
        assertEquals(2, lockGrpcClient.lockWithResult(instance).getReentrantCount());
        assertEquals(2, lockGrpcClient.unLockWithResult(instance).getReentrantCount());
        assertEquals(2, lockGrpcClient.renewWithResult(instance).getReentrantCount());
        assertEquals(2, lockGrpcClient.cancelWaitWithResult(instance).getReentrantCount());
    }
    
    @Test
    void renewReturnsRawSuccess() throws Exception {
        mockRequest();
        when(rpcClient.request(any(AbstractLockRequest.class), anyLong()))
            .thenReturn(new LockOperationResponse(true));
        
        assertTrue(lockGrpcClient.renew(newLockInstance("test", -1L)));
    }
    
    @Test
    void rawResultMethodsReturnLockResult() throws Exception {
        mockRequest();
        when(rpcClient.request(any(AbstractLockRequest.class), anyLong()))
            .thenReturn(new LockOperationResponse(false));
        LockInstance instance = newLockInstance("test", -1L);
        
        assertFalse(lockGrpcClient.lockWithResult(instance).isSuccess());
        assertFalse(lockGrpcClient.unLockWithResult(instance).isSuccess());
        assertFalse(lockGrpcClient.renewWithResult(instance).isSuccess());
        assertFalse(lockGrpcClient.cancelWaitWithResult(instance).isSuccess());
    }
    
    @Test
    void renewAndCancelWaitNotSupportedFeature() {
        when(rpcClient.getConnectionAbility(AbilityKey.SERVER_DISTRIBUTED_LOCK))
            .thenReturn(AbilityStatus.NOT_SUPPORTED);
        LockInstance instance = newLockInstance("test", -1L);
        
        assertThrows(NacosRuntimeException.class, () -> lockGrpcClient.lockWithResult(instance));
        assertThrows(NacosRuntimeException.class, () -> lockGrpcClient.renew(instance));
        assertThrows(NacosRuntimeException.class,
            () -> lockGrpcClient.cancelWaitWithResult(instance));
    }
    
    @Test
    void waitForNotificationReturnsAvailableAndTimeout() throws Exception {
        CompletableFuture<LockNotificationType> waiting = CompletableFuture.supplyAsync(() -> {
            try {
                return lockGrpcClient.waitForNotification("key", "owner", 1000L);
            } catch (InterruptedException e) {
                throw new IllegalStateException(e);
            }
        });
        completeNotificationLater("key", "owner", LockNotificationType.AVAILABLE);
        
        assertEquals(LockNotificationType.AVAILABLE, waiting.get(2, TimeUnit.SECONDS));
        assertNull(lockGrpcClient.waitForNotification("key", "owner", 1L));
    }
    
    @Test
    void waitForNotificationWithoutTimeoutBlocksUntilNotification() throws Exception {
        CompletableFuture<LockNotificationType> waiting = CompletableFuture.supplyAsync(() -> {
            try {
                return lockGrpcClient.waitForNotification("key", "owner", 0L);
            } catch (InterruptedException e) {
                throw new IllegalStateException(e);
            }
        });
        completeNotificationLater("key", "owner", LockNotificationType.AVAILABLE);
        
        assertEquals(LockNotificationType.AVAILABLE, waiting.get(2, TimeUnit.SECONDS));
    }
    
    @Test
    void waitForNotificationReturnsNullWhenFutureFails() throws Exception {
        ConcurrentHashMap<String, CompletableFuture<LockNotificationType>> futures =
            getNotificationFutures();
        CompletableFuture<LockNotificationType> future = new CompletableFuture<>();
        futures.put("key:owner", future);
        CompletableFuture.runAsync(() -> future.completeExceptionally(
            new IllegalStateException("failed")));
        
        assertNull(lockGrpcClient.waitForNotification("key", "owner", 100L));
    }
    
    @Test
    void notificationHandlerCompletesPendingFuture() throws Exception {
        ServerRequestHandler handler = registerAndCaptureServerRequestHandler();
        lockGrpcClient.registerForNotification("key", "owner");
        CompletableFuture<LockNotificationType> future = getNotificationFutures().get("key:owner");
        
        Response response = handler.requestReply(
            new LockNotificationRequest("key", LockConstants.NACOS_LOCK_TYPE, "owner",
                LockNotificationType.AVAILABLE),
            null);
        
        assertTrue(response instanceof LockNotificationResponse);
        assertEquals(LockNotificationType.AVAILABLE, future.get(1, TimeUnit.SECONDS));
        assertNull(handler.requestReply(new Request() {
            
            @Override
            public String getModule() {
                return "test";
            }
        }, null));
    }
    
    @Test
    void registerForNotificationCompletesOldFutureAndCancelRemovesIt() throws Exception {
        lockGrpcClient.registerForNotification("key", "owner");
        CompletableFuture<LockNotificationType> oldFuture =
            getNotificationFutures().get("key:owner");
        
        lockGrpcClient.registerForNotification("key", "owner");
        
        assertEquals(LockNotificationType.AVAILABLE, oldFuture.get(1, TimeUnit.SECONDS));
        lockGrpcClient.cancelWait("key", "owner");
        assertFalse(getNotificationFutures().containsKey("key:owner"));
    }
    
    @Test
    void cancelWaitServerSideFailureIsSwallowed() throws Exception {
        mockRequest();
        when(rpcClient.request(any(AbstractLockRequest.class), anyLong()))
            .thenThrow(new NacosException(NacosException.SERVER_ERROR, "failed"));
        
        lockGrpcClient.cancelWait("key", LockConstants.NACOS_LOCK_TYPE, "owner");
        
        assertFalse(getNotificationFutures().containsKey("key:owner"));
    }
    
    @Test
    void shutdownCompletesPendingWaitAndRejectsNewNotificationRegistration() throws Exception {
        lockGrpcClient.registerForNotification("key", "owner");
        CompletableFuture<LockNotificationType> future = getNotificationFutures().get("key:owner");
        
        lockGrpcClient.shutdown();
        lockGrpcClient.registerForNotification("key2", "owner2");
        
        assertTrue(future.isCompletedExceptionally());
        assertTrue(getNotificationFutures().isEmpty());
        assertEquals(LockNotificationType.TIMEOUT,
            lockGrpcClient.waitForNotification("key2", "owner2", 1L));
        verify(rpcClient).shutdown();
    }
    
    @SuppressWarnings("unchecked")
    private ConcurrentHashMap<String, CompletableFuture<LockNotificationType>> getNotificationFutures()
        throws Exception {
        Field field = LockGrpcClient.class.getDeclaredField("notificationFutures");
        field.setAccessible(true);
        return (ConcurrentHashMap<String, CompletableFuture<LockNotificationType>>) field
            .get(lockGrpcClient);
    }
    
    private ServerRequestHandler registerAndCaptureServerRequestHandler() throws Exception {
        Method method = LockGrpcClient.class.getDeclaredMethod("registerServerRequestHandler");
        method.setAccessible(true);
        method.invoke(lockGrpcClient);
        ArgumentCaptor<ServerRequestHandler> captor =
            ArgumentCaptor.forClass(ServerRequestHandler.class);
        verify(rpcClient).registerServerRequestHandler(captor.capture());
        return captor.getValue();
    }
    
    private LockInstance newLockInstance(String key, long waitTime) {
        LockInstance instance = new LockInstance();
        instance.setKey(key);
        instance.setExpiredTime(-1L);
        instance.setLockType(LockConstants.NACOS_LOCK_TYPE);
        instance.setOwner("owner-" + key);
        instance.setWaitTime(waitTime);
        return instance;
    }
    
    private void completeNotificationLater(String lockKey, String owner,
        LockNotificationType notificationType) {
        CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(50L);
                CompletableFuture<LockNotificationType> future =
                    getNotificationFutures().get(lockKey + ":" + owner);
                if (future != null) {
                    future.complete(notificationType);
                }
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
    }
}
