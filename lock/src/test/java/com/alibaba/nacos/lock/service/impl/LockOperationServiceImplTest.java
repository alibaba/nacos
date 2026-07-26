/*
 * Copyright 1999-2023 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.lock.service.impl;

import com.alibaba.nacos.api.lock.common.LockConstants;
import com.alibaba.nacos.api.lock.model.LockInstance;
import com.alibaba.nacos.api.lock.model.LockResult;
import com.alibaba.nacos.api.lock.remote.LockOperationEnum;
import com.alibaba.nacos.consistency.SerializeFactory;
import com.alibaba.nacos.consistency.Serializer;
import com.alibaba.nacos.consistency.cp.CPProtocol;
import com.alibaba.nacos.consistency.entity.ReadRequest;
import com.alibaba.nacos.consistency.entity.Response;
import com.alibaba.nacos.consistency.entity.WriteRequest;
import com.alibaba.nacos.core.distributed.ProtocolManager;
import com.alibaba.nacos.core.remote.RpcPushService;
import com.alibaba.nacos.lock.LockManager;
import com.alibaba.nacos.lock.constant.PropertiesConstant;
import com.alibaba.nacos.lock.core.reentrant.AtomicLockService;
import com.alibaba.nacos.lock.core.reentrant.mutex.MutexAtomicLock;
import com.alibaba.nacos.lock.core.reentrant.mutex.ReentrantAtomicLock;
import com.alibaba.nacos.lock.exception.NacosLockException;
import com.alibaba.nacos.lock.model.LockInfo;
import com.alibaba.nacos.lock.model.LockKey;
import com.alibaba.nacos.lock.raft.request.MutexLockRequest;
import com.alibaba.nacos.sys.env.EnvUtil;
import com.alibaba.nacos.sys.utils.ApplicationUtils;
import com.google.protobuf.ByteString;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.lang.reflect.Method;

import static com.alibaba.nacos.lock.constant.Constants.LOCK_ACQUIRE_SERVICE_GROUP_V2;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * lock operation service test.
 *
 * @author 985492783@qq.com
 * @date 2023/8/30 14:01
 */
@ExtendWith(MockitoExtension.class)
public class LockOperationServiceImplTest {
    
    @Mock
    private ProtocolManager protocolManager;
    
    @Mock
    private static CPProtocol cpProtocol;
    
    @Mock
    private static LockManager lockManager;
    
    @Mock
    private static RpcPushService rpcPushService;
    
    private final Serializer serializer = SerializeFactory.getDefault();
    
    private LockOperationServiceImpl lockOperationService;
    
    private static MockedStatic<ApplicationUtils> mockedStatic;
    
    private static MockedStatic<EnvUtil> mockedEnv;
    
    @AfterEach
    void tearDown() {
        if (lockOperationService != null) {
            lockOperationService.destroy();
        }
    }
    
    @BeforeAll
    public static void setUp() {
        mockedStatic = Mockito.mockStatic(ApplicationUtils.class);
        mockedEnv = Mockito.mockStatic(EnvUtil.class);
        mockedEnv.when(() -> EnvUtil.getProperty(Mockito.any(), Mockito.any(), Mockito.any()))
            .thenAnswer(ins -> ins.getArgument(2));
    }
    
    /**
     * build test service.
     */
    public void buildService() {
        Mockito.when(protocolManager.getCpProtocol()).thenReturn(cpProtocol);
        lockOperationService =
            Mockito.spy(new LockOperationServiceImpl(lockManager, protocolManager));
        try {
            java.lang.reflect.Field field =
                LockOperationServiceImpl.class.getDeclaredField("rpcPushService");
            field.setAccessible(true);
            field.set(lockOperationService, rpcPushService);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        lockOperationService.init();
    }
    
    @Test
    public void testGroup() {
        buildService();
        
        assertEquals(lockOperationService.group(), LOCK_ACQUIRE_SERVICE_GROUP_V2);
    }
    
    @Test
    public void testLockExpire() throws Exception {
        buildService();
        
        long timestamp = 1 << 10;
        Mockito.when(lockOperationService.getNowTimestamp()).thenReturn(timestamp);
        Mockito.when(cpProtocol.write(Mockito.any())).thenAnswer((i) -> {
            WriteRequest request = i.getArgument(0);
            MutexLockRequest mutexLockRequest =
                serializer.deserialize(request.getData().toByteArray());
            LockInfo lockInfo = mutexLockRequest.getLockInfo();
            assertEquals(LockConstants.NACOS_LOCK_TYPE, lockInfo.getKey().getLockType());
            assertEquals(timestamp + PropertiesConstant.DEFAULT_AUTO_EXPIRE_TIME,
                (long) lockInfo.getEndTime());
            
            return getResponse();
        });
        LockInstance lockInstance = new LockInstance("key", -1L, LockConstants.NACOS_LOCK_TYPE);
        lockOperationService.lock(lockInstance, "test-connection-id");
    }
    
    @Test
    public void testLockSimple() throws Exception {
        buildService();
        
        long timestamp = 1 << 10;
        Mockito.when(lockOperationService.getNowTimestamp()).thenReturn(timestamp);
        Mockito.when(cpProtocol.write(Mockito.any())).thenAnswer((i) -> {
            WriteRequest request = i.getArgument(0);
            MutexLockRequest mutexLockRequest =
                serializer.deserialize(request.getData().toByteArray());
            LockInfo lockInfo = mutexLockRequest.getLockInfo();
            assertEquals(lockInfo.getKey().getLockType(), LockConstants.NACOS_LOCK_TYPE);
            assertEquals((long) lockInfo.getEndTime(), timestamp + 1_000L);
            
            return getResponse();
        });
        LockInstance lockInstance = new LockInstance("key", 1_000L, LockConstants.NACOS_LOCK_TYPE);
        lockOperationService.lock(lockInstance, "test-connection-id");
    }
    
    @Test
    public void testLockMaxExpire() throws Exception {
        buildService();
        
        long timestamp = 1 << 10;
        Mockito.when(lockOperationService.getNowTimestamp()).thenReturn(timestamp);
        Mockito.when(cpProtocol.write(Mockito.any())).thenAnswer((i) -> {
            WriteRequest request = i.getArgument(0);
            MutexLockRequest mutexLockRequest =
                serializer.deserialize(request.getData().toByteArray());
            LockInfo lockInfo = mutexLockRequest.getLockInfo();
            assertEquals(lockInfo.getKey().getLockType(), LockConstants.NACOS_LOCK_TYPE);
            assertEquals((long) lockInfo.getEndTime(),
                timestamp + PropertiesConstant.MAX_AUTO_EXPIRE_TIME);
            
            return getResponse();
        });
        LockInstance lockInstance =
            new LockInstance("key", PropertiesConstant.MAX_AUTO_EXPIRE_TIME + 1_000L,
                LockConstants.NACOS_LOCK_TYPE);
        lockOperationService.lock(lockInstance, "test-connection-id");
    }
    
    @Test
    public void testOnApply() {
        buildService();
        Mockito.when(lockManager.getMutexLock(new LockKey(LockConstants.NACOS_LOCK_TYPE, "key")))
            .thenReturn(new MutexAtomicLock("key"));
        
        WriteRequest request = getRequest(LockOperationEnum.ACQUIRE);
        Response response = lockOperationService.onApply(request);
        assertTrue(response.getSuccess());
        LockResult result = serializer.deserialize(response.getData().toByteArray());
        assertTrue(result.isSuccess());
    }
    
    public WriteRequest getRequest(LockOperationEnum lockOperationEnum) {
        MutexLockRequest mutexLockRequest = new MutexLockRequest();
        LockInfo lockInfo = new LockInfo();
        lockInfo.setEndTime(1L + System.currentTimeMillis());
        lockInfo.setKey(new LockKey(LockConstants.NACOS_LOCK_TYPE, "key"));
        mutexLockRequest.setLockInfo(lockInfo);
        WriteRequest writeRequest = WriteRequest.newBuilder().setGroup(lockOperationService.group())
            .setData(ByteString.copyFrom(serializer.serialize(mutexLockRequest)))
            .setOperation(lockOperationEnum.name()).build();
        return writeRequest;
    }
    
    public Response getResponse() {
        return Response.newBuilder().setSuccess(true)
            .setData(ByteString.copyFrom(serializer.serialize(LockResult.success(1)))).build();
    }
    
    private Response successResponse(Object data) {
        return Response.newBuilder().setSuccess(true)
            .setData(ByteString.copyFrom(serializer.serialize(data))).build();
    }
    
    private Response failedResponse(String errorMessage) {
        return Response.newBuilder().setSuccess(false).setErrMsg(errorMessage).build();
    }
    
    @AfterAll
    public static void destroy() {
        mockedStatic.close();
        mockedEnv.close();
    }
    
    @Test
    public void testDestroyShutdownsNotificationExecutor() {
        buildService();
        
        lockOperationService.destroy();
    }
    
    @Test
    public void testOnApplyReturnsFailureWhenOperationIsInvalid() {
        buildService();
        MutexLockRequest mutexLockRequest = new MutexLockRequest();
        WriteRequest request = WriteRequest.newBuilder().setGroup(lockOperationService.group())
            .setData(ByteString.copyFrom(serializer.serialize(mutexLockRequest)))
            .setOperation("UNKNOWN").build();
        
        Response response = lockOperationService.onApply(request);
        
        assertFalse(response.getSuccess());
        assertTrue(response.getErrMsg().contains("UNKNOWN"));
    }
    
    @Test
    public void testOnApplyDebugLogging() {
        buildService();
        ch.qos.logback.classic.Logger logger =
            (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory
                .getLogger(LockOperationServiceImpl.class);
        ch.qos.logback.classic.Level originLevel = logger.getLevel();
        try {
            logger.setLevel(ch.qos.logback.classic.Level.DEBUG);
            MutexLockRequest mutexLockRequest = new MutexLockRequest();
            mutexLockRequest.setConnectionId("conn-1");
            WriteRequest request = WriteRequest.newBuilder().setGroup(lockOperationService.group())
                .setData(ByteString.copyFrom(serializer.serialize(mutexLockRequest)))
                .setOperation(LockOperationEnum.CLEANUP_CONNECTION.name()).build();
            
            Response response = lockOperationService.onApply(request);
            
            assertTrue(response.getSuccess());
        } finally {
            logger.setLevel(originLevel);
        }
    }
    
    @Test
    public void testOnApplyReturnsFailureWhenLockOperationThrowsNacosLockException() {
        buildService();
        Mockito.when(lockManager.getMutexLock(Mockito.any(LockKey.class)))
            .thenThrow(new NacosLockException("apply failed"));
        
        Response response = lockOperationService.onApply(getRequest(LockOperationEnum.RENEW));
        
        assertFalse(response.getSuccess());
        assertEquals("apply failed", response.getErrMsg());
    }
    
    @Test
    public void testOnApplyReturnsFailureWhenLockOperationThrowsRuntimeException() {
        buildService();
        Mockito.when(lockManager.getMutexLock(Mockito.any(LockKey.class)))
            .thenThrow(new IllegalStateException("runtime failed"));
        
        Response response = lockOperationService.onApply(getRequest(LockOperationEnum.RENEW));
        
        assertFalse(response.getSuccess());
        assertEquals("runtime failed", response.getErrMsg());
    }
    
    @Test
    public void testPublicOperationsReturnSuccessfulRaftResponses() throws Exception {
        buildService();
        LockInstance lockInstance = new LockInstance("key", 1_000L, LockConstants.NACOS_LOCK_TYPE);
        lockInstance.setOwner("owner-1");
        Mockito.when(cpProtocol.write(Mockito.any())).thenAnswer(invocation -> {
            WriteRequest request = invocation.getArgument(0);
            LockOperationEnum operation = LockOperationEnum.valueOf(request.getOperation());
            if (operation == LockOperationEnum.RENEW) {
                return successResponse(Boolean.TRUE);
            }
            return successResponse(LockResult.success(1));
        });
        
        assertTrue(lockOperationService.unLock(lockInstance).isSuccess());
        assertTrue(lockOperationService.renew(lockInstance));
        assertTrue(lockOperationService.expire(lockInstance).isSuccess());
        assertTrue(lockOperationService.cancelWait(lockInstance, "conn-1").isSuccess());
    }
    
    @Test
    public void testRenewUsesDefaultExpireTimeWhenExpiredTimeIsNegative() throws Exception {
        buildService();
        long timestamp = 1 << 10;
        Mockito.when(lockOperationService.getNowTimestamp()).thenReturn(timestamp);
        Mockito.when(cpProtocol.write(Mockito.any())).thenAnswer(invocation -> {
            WriteRequest request = invocation.getArgument(0);
            MutexLockRequest mutexLockRequest =
                serializer.deserialize(request.getData().toByteArray());
            assertEquals(timestamp + PropertiesConstant.DEFAULT_AUTO_EXPIRE_TIME,
                (long) mutexLockRequest.getLockInfo().getEndTime());
            return successResponse(Boolean.TRUE);
        });
        LockInstance lockInstance = new LockInstance("key", -1L, LockConstants.NACOS_LOCK_TYPE);
        lockInstance.setOwner("owner-1");
        
        assertTrue(lockOperationService.renew(lockInstance));
    }
    
    @Test
    public void testPublicOperationsRethrowFailedRaftResponses() throws Exception {
        buildService();
        LockInstance lockInstance = new LockInstance("key", 1_000L, LockConstants.NACOS_LOCK_TYPE);
        lockInstance.setOwner("owner-1");
        Mockito.when(cpProtocol.write(Mockito.any())).thenReturn(failedResponse("raft failed"));
        
        assertThrows(NacosLockException.class,
            () -> lockOperationService.lock(lockInstance, "conn-1"));
        assertThrows(NacosLockException.class, () -> lockOperationService.unLock(lockInstance));
        assertThrows(NacosLockException.class, () -> lockOperationService.renew(lockInstance));
        assertThrows(NacosLockException.class, () -> lockOperationService.expire(lockInstance));
        assertThrows(NacosLockException.class,
            () -> lockOperationService.cancelWait(lockInstance, "conn-1"));
    }
    
    @Test
    public void testPublicOperationsWrapWriteExceptions() throws Exception {
        buildService();
        LockInstance lockInstance = new LockInstance("key", 1_000L, LockConstants.NACOS_LOCK_TYPE);
        lockInstance.setOwner("owner-1");
        Mockito.when(cpProtocol.write(Mockito.any()))
            .thenThrow(new RuntimeException("write failed"));
        
        assertEquals("tryLock error.",
            assertThrows(NacosLockException.class,
                () -> lockOperationService.lock(lockInstance, "conn-1")).getMessage());
        assertEquals("unLock error.",
            assertThrows(NacosLockException.class,
                () -> lockOperationService.unLock(lockInstance)).getMessage());
        assertEquals("renew error.",
            assertThrows(NacosLockException.class,
                () -> lockOperationService.renew(lockInstance)).getMessage());
        assertEquals("expire error.",
            assertThrows(NacosLockException.class,
                () -> lockOperationService.expire(lockInstance)).getMessage());
        assertEquals("cancel wait error.",
            assertThrows(NacosLockException.class,
                () -> lockOperationService.cancelWait(lockInstance, "conn-1")).getMessage());
    }
    
    @Test
    public void testLoadSnapshotOperateAndOnRequest() {
        buildService();
        
        List<?> snapshotOperations = lockOperationService.loadSnapshotOperate();
        
        assertEquals(1, snapshotOperations.size());
        assertNull(lockOperationService.onRequest(ReadRequest.newBuilder().build()));
    }
    
    @Test
    public void testReleaseLockNotifiesFirstWaiterAndKeepsLockEntry() throws Exception {
        buildService();
        ReentrantAtomicLock reentrantLock = new ReentrantAtomicLock("release-waiter-key");
        LockKey lockKey = new LockKey(LockConstants.NACOS_LOCK_TYPE, "release-waiter-key");
        LockInfo holderInfo = newLockInfo("owner-1", "conn-holder", "release-waiter-key");
        reentrantLock.tryLock(holderInfo);
        reentrantLock.addWaiter(newLockInfo("waiter-1", "conn-waiter", "release-waiter-key"));
        ConcurrentHashMap<LockKey, AtomicLockService> locks = new ConcurrentHashMap<>();
        locks.put(lockKey, reentrantLock);
        Mockito.when(lockManager.showLocks()).thenReturn(locks);
        
        Response response = lockOperationService.onApply(
            buildReleaseRequest("owner-1", "release-waiter-key", false));
        
        assertTrue(response.getSuccess());
        LockResult result = serializer.deserialize(response.getData().toByteArray());
        assertTrue(result.isSuccess());
        TimeUnit.MILLISECONDS.sleep(100);
        Mockito.verify(rpcPushService, Mockito.timeout(1000))
            .pushWithoutAck(Mockito.eq("conn-waiter"), Mockito.any());
        Mockito.verify(lockManager, Mockito.never()).removeMutexLock(lockKey);
    }
    
    @Test
    public void testReleaseLockIgnoresNotificationFailure() throws Exception {
        buildService();
        ReentrantAtomicLock reentrantLock = new ReentrantAtomicLock("release-notify-fail-key");
        LockInfo holderInfo = newLockInfo("owner-1", "conn-holder", "release-notify-fail-key");
        reentrantLock.tryLock(holderInfo);
        reentrantLock.addWaiter(newLockInfo("waiter-1", "conn-waiter", "release-notify-fail-key"));
        ConcurrentHashMap<LockKey, AtomicLockService> locks = new ConcurrentHashMap<>();
        locks.put(new LockKey(LockConstants.NACOS_LOCK_TYPE, "release-notify-fail-key"),
            reentrantLock);
        Mockito.when(lockManager.showLocks()).thenReturn(locks);
        Mockito.doThrow(new RuntimeException("push failed")).when(rpcPushService)
            .pushWithoutAck(Mockito.eq("conn-waiter"), Mockito.any());
        
        Response response = lockOperationService.onApply(
            buildReleaseRequest("owner-1", "release-notify-fail-key", false));
        
        assertTrue(response.getSuccess());
        Mockito.verify(rpcPushService, Mockito.timeout(1000))
            .pushWithoutAck(Mockito.eq("conn-waiter"), Mockito.any());
    }
    
    @Test
    public void testReleaseLockReturnsFailureForWrongOwner() {
        buildService();
        ReentrantAtomicLock reentrantLock = new ReentrantAtomicLock("wrong-owner-key");
        LockInfo holderInfo = newLockInfo("owner-1", "conn-holder", "wrong-owner-key");
        reentrantLock.tryLock(holderInfo);
        ConcurrentHashMap<LockKey, AtomicLockService> locks = new ConcurrentHashMap<>();
        locks.put(new LockKey(LockConstants.NACOS_LOCK_TYPE, "wrong-owner-key"), reentrantLock);
        Mockito.when(lockManager.showLocks()).thenReturn(locks);
        
        Response response = lockOperationService.onApply(
            buildReleaseRequest("other-owner", "wrong-owner-key", false));
        
        assertTrue(response.getSuccess());
        LockResult result = serializer.deserialize(response.getData().toByteArray());
        assertFalse(result.isSuccess());
        assertEquals("Unlock failed: not held by this owner", result.getErrorMessage());
    }
    
    @Test
    public void testAcquireLockHeldByAnotherOwnerReturnsWaitingOrFailure() {
        buildService();
        ReentrantAtomicLock reentrantLock = new ReentrantAtomicLock("fifo-key");
        reentrantLock.tryLock(newLockInfo("owner-1", "conn-holder", "fifo-key"));
        Mockito.when(lockManager.getMutexLock(Mockito.any(LockKey.class)))
            .thenReturn(reentrantLock);
        
        Response waitResponse = lockOperationService.onApply(
            buildAcquireRequest("waiter-1", "conn-waiter", 5000L, false));
        LockResult waitResult = serializer.deserialize(waitResponse.getData().toByteArray());
        assertFalse(waitResult.isSuccess());
        assertTrue(waitResult.isWaiting());
        
        Response failResponse = lockOperationService.onApply(
            buildAcquireRequest("other-owner", "conn-other", 0L, false));
        LockResult failResult = serializer.deserialize(failResponse.getData().toByteArray());
        assertFalse(failResult.isSuccess());
        assertEquals("Lock is held by another owner", failResult.getErrorMessage());
    }
    
    @Test
    public void testAcquireLockRejectsNewRequestWhenWaitersExistAndWaitTimeIsZero() {
        buildService();
        ReentrantAtomicLock reentrantLock = new ReentrantAtomicLock("fifo-key");
        reentrantLock.addWaiter(newLockInfo("waiter-1", "conn-waiter", "fifo-key"));
        Mockito.when(lockManager.getMutexLock(Mockito.any(LockKey.class)))
            .thenReturn(reentrantLock);
        
        Response response = lockOperationService.onApply(
            buildAcquireRequest("owner-1", "conn-1", 0L, false));
        
        assertTrue(response.getSuccess());
        LockResult result = serializer.deserialize(response.getData().toByteArray());
        assertFalse(result.isSuccess());
        assertEquals("Lock is held by another owner", result.getErrorMessage());
    }
    
    @Test
    public void testAcquireLockRejectsWhenHeldWithoutWaitersAndWaitTimeIsZero() {
        buildService();
        ReentrantAtomicLock reentrantLock = new ReentrantAtomicLock("fifo-key");
        reentrantLock.tryLock(newLockInfo("owner-1", "conn-holder", "fifo-key"));
        Mockito.when(lockManager.getMutexLock(Mockito.any(LockKey.class)))
            .thenReturn(reentrantLock);
        
        Response response = lockOperationService.onApply(
            buildAcquireRequest("owner-2", "conn-2", 0L, false));
        
        assertTrue(response.getSuccess());
        LockResult result = serializer.deserialize(response.getData().toByteArray());
        assertFalse(result.isSuccess());
        assertEquals("Lock is held by another owner", result.getErrorMessage());
    }
    
    @Test
    public void testAcquireLockWithNonAbstractAtomicLock() {
        buildService();
        AtomicLockService atomicLock = Mockito.mock(AtomicLockService.class);
        Mockito.when(atomicLock.tryLock(Mockito.any())).thenReturn(true, false);
        Mockito.when(lockManager.getMutexLock(Mockito.any(LockKey.class))).thenReturn(atomicLock);
        
        Response successResponse = lockOperationService.onApply(
            buildAcquireRequest("owner-1", "conn-1", 0L, false));
        LockResult successResult = serializer.deserialize(successResponse.getData().toByteArray());
        assertTrue(successResult.isSuccess());
        
        Response failResponse = lockOperationService.onApply(
            buildAcquireRequest("owner-2", "conn-2", 0L, false));
        LockResult failResult = serializer.deserialize(failResponse.getData().toByteArray());
        assertFalse(failResult.isSuccess());
        assertEquals("Lock is held by another owner", failResult.getErrorMessage());
    }
    
    @Test
    public void testCancelWaitReturnsSuccessWhenLockMissingOrNonAbstract() {
        buildService();
        ConcurrentHashMap<LockKey, AtomicLockService> locks = new ConcurrentHashMap<>();
        Mockito.when(lockManager.showLocks()).thenReturn(locks);
        
        Response missingResponse = lockOperationService.onApply(
            buildCancelWaitRequest("owner-1", "conn-1"));
        LockResult missingResult = serializer.deserialize(missingResponse.getData().toByteArray());
        assertTrue(missingResult.isSuccess());
        
        locks.put(new LockKey(LockConstants.NACOS_LOCK_TYPE, "fifo-key"),
            Mockito.mock(AtomicLockService.class));
        Response nonAbstractResponse = lockOperationService.onApply(
            buildCancelWaitRequest("owner-1", "conn-1"));
        LockResult nonAbstractResult =
            serializer.deserialize(nonAbstractResponse.getData().toByteArray());
        assertTrue(nonAbstractResult.isSuccess());
    }
    
    @Test
    public void testCancelWaitRemovesClearLockWithoutRemainingWaiters() {
        buildService();
        LockKey lockKey = new LockKey(LockConstants.NACOS_LOCK_TYPE, "fifo-key");
        ReentrantAtomicLock reentrantLock = new ReentrantAtomicLock("fifo-key");
        reentrantLock.addWaiter(newLockInfo("waiter-1", "conn-1", "fifo-key"));
        ConcurrentHashMap<LockKey, AtomicLockService> locks = new ConcurrentHashMap<>();
        locks.put(lockKey, reentrantLock);
        Mockito.when(lockManager.showLocks()).thenReturn(locks);
        
        Response response =
            lockOperationService.onApply(buildCancelWaitRequest("waiter-1", "conn-1"));
        
        assertTrue(response.getSuccess());
        Mockito.verify(lockManager).removeMutexLock(lockKey);
    }
    
    @Test
    public void testCancelWaitNotifiesNextWaiterWhenClearLockStillHasWaiters() throws Exception {
        buildService();
        LockKey lockKey = new LockKey(LockConstants.NACOS_LOCK_TYPE, "fifo-key");
        ReentrantAtomicLock reentrantLock = new ReentrantAtomicLock("fifo-key");
        reentrantLock.addWaiter(newLockInfo("waiter-1", "conn-1", "fifo-key"));
        reentrantLock.addWaiter(newLockInfo("waiter-2", "conn-2", "fifo-key"));
        ConcurrentHashMap<LockKey, AtomicLockService> locks = new ConcurrentHashMap<>();
        locks.put(lockKey, reentrantLock);
        Mockito.when(lockManager.showLocks()).thenReturn(locks);
        
        Response response =
            lockOperationService.onApply(buildCancelWaitRequest("waiter-1", "conn-1"));
        
        assertTrue(response.getSuccess());
        TimeUnit.MILLISECONDS.sleep(100);
        Mockito.verify(rpcPushService, Mockito.timeout(1000))
            .pushWithoutAck(Mockito.eq("conn-2"), Mockito.any());
    }
    
    @Test
    public void testCleanupConnectionBatchMode() {
        buildService();
        LockKey lockKey = new LockKey(LockConstants.NACOS_LOCK_TYPE, "batch-key");
        ReentrantAtomicLock reentrantLock = new ReentrantAtomicLock("batch-key");
        reentrantLock.tryLock(newLockInfo("owner-1", "conn-1", "batch-key"));
        ConcurrentHashMap<LockKey, AtomicLockService> locks = new ConcurrentHashMap<>();
        locks.put(lockKey, reentrantLock);
        Mockito.when(lockManager.showLocks()).thenReturn(locks);
        
        Response response =
            lockOperationService.onApply(buildBatchCleanupConnectionRequest("conn-1"));
        
        assertTrue(response.getSuccess());
        assertTrue(reentrantLock.isClear());
    }
    
    @Test
    public void testCleanupConnectionHeldLockNotifiesRemainingWaiter() throws Exception {
        buildService();
        LockKey lockKey = new LockKey(LockConstants.NACOS_LOCK_TYPE, "cleanup-notify-key");
        ReentrantAtomicLock reentrantLock = new ReentrantAtomicLock("cleanup-notify-key");
        reentrantLock.tryLock(newLockInfo("owner-1", "conn-1", "cleanup-notify-key"));
        reentrantLock.addWaiter(newLockInfo("waiter-1", "conn-waiter", "cleanup-notify-key"));
        ConcurrentHashMap<LockKey, AtomicLockService> locks = new ConcurrentHashMap<>();
        locks.put(lockKey, reentrantLock);
        Mockito.when(lockManager.showLocks()).thenReturn(locks);
        
        Response response = lockOperationService.onApply(
            buildCleanupConnectionRequest("cleanup-notify-key", "conn-1"));
        
        assertTrue(response.getSuccess());
        Mockito.verify(rpcPushService, Mockito.timeout(1000))
            .pushWithoutAck(Mockito.eq("conn-waiter"), Mockito.any());
    }
    
    @Test
    public void testNotifyFirstWaiterIgnoresPushFailure() throws Exception {
        buildService();
        LockKey lockKey = new LockKey(LockConstants.NACOS_LOCK_TYPE, "cleanup-push-fail-key");
        ReentrantAtomicLock reentrantLock = new ReentrantAtomicLock("cleanup-push-fail-key");
        reentrantLock.tryLock(newLockInfo("holder", "conn-holder", "cleanup-push-fail-key"));
        reentrantLock.addWaiter(newLockInfo("waiter-1", "conn-waiter", "cleanup-push-fail-key"));
        ConcurrentHashMap<LockKey, AtomicLockService> locks = new ConcurrentHashMap<>();
        locks.put(lockKey, reentrantLock);
        Mockito.when(lockManager.showLocks()).thenReturn(locks);
        Mockito.doThrow(new RuntimeException("push failed")).when(rpcPushService)
            .pushWithoutAck(Mockito.eq("conn-waiter"), Mockito.any());
        
        Response response = lockOperationService.onApply(
            buildCleanupConnectionRequest("cleanup-push-fail-key", "conn-holder"));
        
        assertTrue(response.getSuccess());
        Mockito.verify(rpcPushService, Mockito.timeout(1000))
            .pushWithoutAck(Mockito.eq("conn-waiter"), Mockito.any());
    }
    
    @Test
    public void testNotifyFirstWaiterReturnsWhenQueueIsEmpty() throws Exception {
        buildService();
        Method method = LockOperationServiceImpl.class.getDeclaredMethod("notifyFirstWaiter",
            LockKey.class, com.alibaba.nacos.lock.core.reentrant.AbstractAtomicLock.class);
        method.setAccessible(true);
        
        method.invoke(lockOperationService,
            new LockKey(LockConstants.NACOS_LOCK_TYPE, "empty-waiter-key"),
            new ReentrantAtomicLock("empty-waiter-key"));
        
        Mockito.verifyNoInteractions(rpcPushService);
    }
    
    @Test
    public void testCleanupConnectionReturnsWhenLockIsNonAbstract() {
        buildService();
        LockKey lockKey = new LockKey(LockConstants.NACOS_LOCK_TYPE, "cleanup-non-abstract-key");
        ConcurrentHashMap<LockKey, AtomicLockService> locks = new ConcurrentHashMap<>();
        locks.put(lockKey, Mockito.mock(AtomicLockService.class));
        Mockito.when(lockManager.showLocks()).thenReturn(locks);
        
        Response response = lockOperationService.onApply(
            buildCleanupConnectionRequest("cleanup-non-abstract-key", "conn-1"));
        
        assertTrue(response.getSuccess());
    }
    
    @Test
    public void testExpireLockNotifiesWaiterAndIgnoresNotificationFailure() throws Exception {
        buildService();
        ReentrantAtomicLock reentrantLock = new ReentrantAtomicLock("expire-notify-key");
        LockInfo holderInfo = newLockInfo("owner-1", "conn-holder", "expire-notify-key");
        holderInfo.setEndTime(System.currentTimeMillis() - 1000);
        reentrantLock.tryLock(holderInfo);
        reentrantLock.addWaiter(newLockInfo("waiter-1", "conn-waiter", "expire-notify-key"));
        Mockito.when(lockManager.getMutexLock(Mockito.any(LockKey.class)))
            .thenReturn(reentrantLock);
        Mockito.doThrow(new RuntimeException("push failed")).when(rpcPushService)
            .pushWithoutAck(Mockito.eq("conn-waiter"), Mockito.any());
        
        Response response = lockOperationService.onApply(
            buildExpireRequest("owner-1", "expire-notify-key"));
        
        assertTrue(response.getSuccess());
        LockResult result = serializer.deserialize(response.getData().toByteArray());
        assertTrue(result.isSuccess());
        Mockito.verify(rpcPushService, Mockito.timeout(1000))
            .pushWithoutAck(Mockito.eq("conn-waiter"), Mockito.any());
    }
    
    @Test
    public void testReleaseLocksByConnectionSuccess() throws Exception {
        buildService();
        Mockito.when(cpProtocol.write(Mockito.any()))
            .thenReturn(successResponse(LockResult.success(0)));
        
        lockOperationService.releaseLocksByConnection("conn-success");
    }
    
    @Test
    public void testReleaseLocksByConnectionIgnoresRaftFailures() throws Exception {
        buildService();
        Mockito.when(cpProtocol.write(Mockito.any())).thenReturn(failedResponse("cleanup failed"))
            .thenThrow(new RuntimeException("write failed"));
        
        lockOperationService.releaseLocksByConnection("conn-1");
        lockOperationService.releaseLocksByConnection("conn-2");
    }
    
    // ==================== acquireLock FIFO enforcement tests ====================
    
    @Test
    public void testAcquireLockNewRequestWithWaitersEnqueues() {
        buildService();
        ReentrantAtomicLock reentrantLock = new ReentrantAtomicLock("fifo-key");
        
        // Pre-populate a waiter
        LockInfo waiterInfo = new LockInfo();
        waiterInfo.setOwner("waiter-1");
        waiterInfo.setConnectionId("conn-waiter");
        waiterInfo.setWaitTime(5000L);
        waiterInfo.setEndTime(System.currentTimeMillis() + 30000);
        reentrantLock.addWaiter(waiterInfo);
        
        // First acquire the lock for another owner
        LockInfo holderInfo = new LockInfo();
        holderInfo.setOwner("holder-1");
        holderInfo.setConnectionId("conn-holder");
        holderInfo.setEndTime(System.currentTimeMillis() + 30000);
        reentrantLock.tryLock(holderInfo);
        
        Mockito.when(lockManager.getMutexLock(Mockito.any(LockKey.class)))
            .thenReturn(reentrantLock);
        
        // New request from different owner — should be enqueued, not acquired
        WriteRequest request = buildAcquireRequest("new-owner", "conn-new", 5000, false);
        Response response = lockOperationService.onApply(request);
        assertTrue(response.getSuccess());
        LockResult result = serializer.deserialize(response.getData().toByteArray());
        assertFalse(result.isSuccess());
        assertTrue(result.isWaiting());
    }
    
    @Test
    public void testAcquireLockHeadWaiterRetryAcquires() {
        buildService();
        ReentrantAtomicLock reentrantLock = new ReentrantAtomicLock("fifo-key");
        
        // Add waiter and acquire lock
        LockInfo waiterInfo = new LockInfo();
        waiterInfo.setOwner("waiter-1");
        waiterInfo.setConnectionId("conn-1");
        waiterInfo.setWaitTime(5000L);
        waiterInfo.setEndTime(System.currentTimeMillis() + 30000);
        reentrantLock.addWaiter(waiterInfo);
        
        LockInfo holderInfo = new LockInfo();
        holderInfo.setOwner("holder-1");
        holderInfo.setConnectionId("conn-holder");
        holderInfo.setEndTime(System.currentTimeMillis() + 30000);
        reentrantLock.tryLock(holderInfo);
        
        // Release the lock
        reentrantLock.unLock(holderInfo);
        
        Mockito.when(lockManager.getMutexLock(Mockito.any(LockKey.class)))
            .thenReturn(reentrantLock);
        
        // Head waiter retry — should acquire and remove from queue
        WriteRequest request = buildAcquireRequest("waiter-1", "conn-1", 0, true);
        Response response = lockOperationService.onApply(request);
        assertTrue(response.getSuccess());
        LockResult result = serializer.deserialize(response.getData().toByteArray());
        assertTrue(result.isSuccess());
        assertEquals("waiter-1", reentrantLock.getOwner());
        assertEquals(0, reentrantLock.getWaitQueue().size());
    }
    
    @Test
    public void testAcquireLockNonHeadWaiterRetryDoesNotAcquireFreeLock() {
        buildService();
        ReentrantAtomicLock reentrantLock = new ReentrantAtomicLock("fifo-key");
        
        LockInfo waiterInfoB = new LockInfo();
        waiterInfoB.setOwner("waiter-1");
        waiterInfoB.setConnectionId("conn-1");
        waiterInfoB.setWaitTime(5000L);
        waiterInfoB.setEndTime(System.currentTimeMillis() + 30000);
        reentrantLock.addWaiter(waiterInfoB);
        
        LockInfo waiterInfoC = new LockInfo();
        waiterInfoC.setOwner("waiter-2");
        waiterInfoC.setConnectionId("conn-2");
        waiterInfoC.setWaitTime(5000L);
        waiterInfoC.setEndTime(System.currentTimeMillis() + 30000);
        reentrantLock.addWaiter(waiterInfoC);
        
        LockInfo holderInfo = new LockInfo();
        holderInfo.setOwner("holder-1");
        holderInfo.setConnectionId("conn-holder");
        holderInfo.setEndTime(System.currentTimeMillis() + 30000);
        reentrantLock.tryLock(holderInfo);
        reentrantLock.unLock(holderInfo);
        
        Mockito.when(lockManager.getMutexLock(Mockito.any(LockKey.class)))
            .thenReturn(reentrantLock);
        
        WriteRequest request = buildAcquireRequest("waiter-2", "conn-2", 0, true);
        Response response = lockOperationService.onApply(request);
        assertTrue(response.getSuccess());
        LockResult result = serializer.deserialize(response.getData().toByteArray());
        assertFalse(result.isSuccess());
        
        assertNull(reentrantLock.getOwner(), "Lock should remain free for queue head");
        assertEquals(2, reentrantLock.getWaitQueue().size());
        assertEquals("waiter-1", reentrantLock.getWaitQueue().get(0).getOwner());
        assertEquals("waiter-2", reentrantLock.getWaitQueue().get(1).getOwner());
    }
    
    @Test
    public void testCancelWaitRemovesServerWaiter() {
        buildService();
        ReentrantAtomicLock reentrantLock = new ReentrantAtomicLock("fifo-key");
        
        LockInfo waiterInfoB = new LockInfo();
        waiterInfoB.setOwner("waiter-1");
        waiterInfoB.setConnectionId("conn-1");
        waiterInfoB.setWaitTime(5000L);
        waiterInfoB.setEndTime(System.currentTimeMillis() + 30000);
        reentrantLock.addWaiter(waiterInfoB);
        
        LockInfo waiterInfoC = new LockInfo();
        waiterInfoC.setOwner("waiter-2");
        waiterInfoC.setConnectionId("conn-2");
        waiterInfoC.setWaitTime(5000L);
        waiterInfoC.setEndTime(System.currentTimeMillis() + 30000);
        reentrantLock.addWaiter(waiterInfoC);
        
        LockInfo holderInfo = new LockInfo();
        holderInfo.setOwner("holder-1");
        holderInfo.setConnectionId("conn-holder");
        holderInfo.setEndTime(System.currentTimeMillis() + 30000);
        reentrantLock.tryLock(holderInfo);
        
        LockKey lockKey = new LockKey(LockConstants.NACOS_LOCK_TYPE, "fifo-key");
        ConcurrentHashMap<LockKey, AtomicLockService> locks = new ConcurrentHashMap<>();
        locks.put(lockKey, reentrantLock);
        Mockito.when(lockManager.showLocks()).thenReturn(locks);
        Mockito.when(lockManager.getMutexLock(Mockito.any(LockKey.class)))
            .thenReturn(reentrantLock);
        
        WriteRequest cancelRequest = buildCancelWaitRequest("waiter-1", "conn-1");
        Response cancelResponse = lockOperationService.onApply(cancelRequest);
        assertTrue(cancelResponse.getSuccess());
        LockResult cancelResult = serializer.deserialize(cancelResponse.getData().toByteArray());
        assertTrue(cancelResult.isSuccess());
        assertEquals(1, reentrantLock.getWaitQueue().size());
        assertEquals("waiter-2", reentrantLock.getWaitQueue().get(0).getOwner());
        
        reentrantLock.unLock(holderInfo);
        WriteRequest retryCRequest = buildAcquireRequest("waiter-2", "conn-2", 0, true);
        Response retryCResponse = lockOperationService.onApply(retryCRequest);
        assertTrue(retryCResponse.getSuccess());
        LockResult retryCResult = serializer.deserialize(retryCResponse.getData().toByteArray());
        assertTrue(retryCResult.isSuccess());
        assertEquals("waiter-2", reentrantLock.getOwner());
        assertEquals(0, reentrantLock.getWaitQueue().size());
    }
    
    @Test
    public void testNewRequestQueuesBehindExistingWaiters() {
        buildService();
        ReentrantAtomicLock reentrantLock = new ReentrantAtomicLock("fifo-key");
        Mockito.when(lockManager.getMutexLock(Mockito.any(LockKey.class)))
            .thenReturn(reentrantLock);
        
        // Waiter-1 is waiting in queue
        LockInfo waiterInfo = new LockInfo();
        waiterInfo.setOwner("waiter-1");
        waiterInfo.setConnectionId("conn-waiter");
        waiterInfo.setWaitTime(30000L);
        waiterInfo.setEndTime(System.currentTimeMillis() + 30000);
        reentrantLock.addWaiter(waiterInfo);
        
        // Lock is free — new request should be enqueued behind waiter-1 (strict FIFO)
        WriteRequest request = buildAcquireRequest("new-owner", "conn-new", 5000, false);
        Response response = lockOperationService.onApply(request);
        assertTrue(response.getSuccess());
        LockResult result = serializer.deserialize(response.getData().toByteArray());
        assertFalse(result.isSuccess());
        assertTrue(result.isWaiting());
        
        // Lock remains free, new request enqueued after waiter-1
        assertNull(reentrantLock.getOwner(), "Lock should remain free");
        assertEquals(2, reentrantLock.getWaitQueue().size());
        assertEquals("waiter-1", reentrantLock.getWaitQueue().get(0).getOwner());
        assertEquals("new-owner", reentrantLock.getWaitQueue().get(1).getOwner());
    }
    
    // ==================== releaseLock tests ====================
    
    @Test
    public void testReleaseLockNormal() {
        buildService();
        ReentrantAtomicLock reentrantLock = new ReentrantAtomicLock("release-key");
        LockInfo lockInfo = new LockInfo();
        lockInfo.setOwner("owner-1");
        lockInfo.setConnectionId("conn-1");
        lockInfo.setKey(new LockKey(LockConstants.NACOS_LOCK_TYPE, "release-key"));
        lockInfo.setEndTime(System.currentTimeMillis() + 30000);
        reentrantLock.tryLock(lockInfo);
        
        ConcurrentHashMap<LockKey, AtomicLockService> locks = new ConcurrentHashMap<>();
        locks.put(new LockKey(LockConstants.NACOS_LOCK_TYPE, "release-key"), reentrantLock);
        Mockito.when(lockManager.showLocks()).thenReturn(locks);
        
        WriteRequest request = buildReleaseRequest("owner-1", "release-key", false);
        Response response = lockOperationService.onApply(request);
        assertTrue(response.getSuccess());
        LockResult result = serializer.deserialize(response.getData().toByteArray());
        assertTrue(result.isSuccess());
    }
    
    @Test
    public void testReleaseLockForce() {
        buildService();
        ReentrantAtomicLock reentrantLock = new ReentrantAtomicLock("force-key");
        LockInfo lockInfo = new LockInfo();
        lockInfo.setOwner("owner-1");
        lockInfo.setConnectionId("conn-1");
        lockInfo.setKey(new LockKey(LockConstants.NACOS_LOCK_TYPE, "force-key"));
        lockInfo.setEndTime(System.currentTimeMillis() + 30000);
        reentrantLock.tryLock(lockInfo);
        reentrantLock.tryLock(lockInfo); // reentrant count = 2
        
        ConcurrentHashMap<LockKey, AtomicLockService> locks = new ConcurrentHashMap<>();
        locks.put(new LockKey(LockConstants.NACOS_LOCK_TYPE, "force-key"), reentrantLock);
        Mockito.when(lockManager.showLocks()).thenReturn(locks);
        
        WriteRequest request = buildReleaseRequest("owner-1", "force-key", true);
        Response response = lockOperationService.onApply(request);
        assertTrue(response.getSuccess());
        assertTrue(reentrantLock.isClear());
    }
    
    @Test
    public void testReleaseLockNonExistent() {
        buildService();
        ConcurrentHashMap<LockKey, AtomicLockService> locks = new ConcurrentHashMap<>();
        Mockito.when(lockManager.showLocks()).thenReturn(locks);
        
        WriteRequest request = buildReleaseRequest("owner-1", "nonexistent", false);
        Response response = lockOperationService.onApply(request);
        assertTrue(response.getSuccess());
        LockResult result = serializer.deserialize(response.getData().toByteArray());
        assertFalse(result.isSuccess());
    }
    
    // ==================== renewLock tests ====================
    
    @Test
    public void testRenewLock() {
        buildService();
        ReentrantAtomicLock reentrantLock = new ReentrantAtomicLock("renew-key");
        LockInfo lockInfo = new LockInfo();
        lockInfo.setOwner("owner-1");
        lockInfo.setConnectionId("conn-1");
        lockInfo.setKey(new LockKey(LockConstants.NACOS_LOCK_TYPE, "renew-key"));
        lockInfo.setEndTime(System.currentTimeMillis() + 10000);
        reentrantLock.tryLock(lockInfo);
        
        Mockito.when(lockManager.getMutexLock(Mockito.any(LockKey.class)))
            .thenReturn(reentrantLock);
        
        WriteRequest request = buildRenewRequest("owner-1", "renew-key");
        Response response = lockOperationService.onApply(request);
        assertTrue(response.getSuccess());
    }
    
    // ==================== expireLock tests ====================
    
    @Test
    public void testExpireLock() {
        buildService();
        ReentrantAtomicLock reentrantLock = new ReentrantAtomicLock("expire-key");
        LockInfo lockInfo = new LockInfo();
        lockInfo.setOwner("owner-1");
        lockInfo.setConnectionId("conn-1");
        lockInfo.setKey(new LockKey(LockConstants.NACOS_LOCK_TYPE, "expire-key"));
        lockInfo.setEndTime(System.currentTimeMillis() - 1000); // already expired
        reentrantLock.tryLock(lockInfo);
        
        Mockito.when(lockManager.getMutexLock(Mockito.any(LockKey.class)))
            .thenReturn(reentrantLock);
        
        WriteRequest request = buildExpireRequest("owner-1", "expire-key");
        Response response = lockOperationService.onApply(request);
        assertTrue(response.getSuccess());
        LockResult result = serializer.deserialize(response.getData().toByteArray());
        assertTrue(result.isSuccess());
    }
    
    @Test
    public void testStaleConnectionIdAfterExpireCausesWrongOwnerRelease() {
        buildService();
        ReentrantAtomicLock reentrantLock = new ReentrantAtomicLock("m1-key");
        
        // Owner-1 holds the lock on conn-1 (expired)
        LockInfo holderInfo = new LockInfo();
        holderInfo.setOwner("owner-1");
        holderInfo.setConnectionId("conn-1");
        holderInfo.setKey(new LockKey(LockConstants.NACOS_LOCK_TYPE, "m1-key"));
        holderInfo.setEndTime(System.currentTimeMillis() - 1000);
        reentrantLock.tryLock(holderInfo);
        
        // Owner-2 waiting in queue on conn-2
        LockInfo waiterInfo = new LockInfo();
        waiterInfo.setOwner("owner-2");
        waiterInfo.setConnectionId("conn-2");
        waiterInfo.setWaitTime(30000L);
        waiterInfo.setEndTime(System.currentTimeMillis() + 30000);
        reentrantLock.addWaiter(waiterInfo);
        
        ConcurrentHashMap<LockKey, AtomicLockService> locks = new ConcurrentHashMap<>();
        locks.put(new LockKey(LockConstants.NACOS_LOCK_TYPE, "m1-key"), reentrantLock);
        Mockito.when(lockManager.showLocks()).thenReturn(locks);
        Mockito.when(lockManager.getMutexLock(Mockito.any(LockKey.class)))
            .thenReturn(reentrantLock);
        
        // Expire the lock — autoExpire clears owner but NOT connectionId
        WriteRequest expireRequest = buildExpireRequest("owner-1", "m1-key");
        lockOperationService.onApply(expireRequest);
        
        // Owner-2 acquires the lock
        LockInfo newOwnerInfo = new LockInfo();
        newOwnerInfo.setOwner("owner-2");
        newOwnerInfo.setConnectionId("conn-2");
        newOwnerInfo.setKey(new LockKey(LockConstants.NACOS_LOCK_TYPE, "m1-key"));
        newOwnerInfo.setEndTime(System.currentTimeMillis() + 30000);
        reentrantLock.tryLock(newOwnerInfo);
        assertEquals("owner-2", reentrantLock.getOwner());
        
        // conn-1 disconnects — should NOT affect owner-2
        WriteRequest cleanupRequest = buildCleanupConnectionRequest("m1-key", "conn-1");
        lockOperationService.onApply(cleanupRequest);
        
        // BUG: stale connectionId="conn-1" matches, owner-2 gets force-released
        assertEquals("owner-2", reentrantLock.getOwner(),
            "owner-2 should still hold the lock after conn-1 disconnects");
    }
    
    // ==================== helper methods ====================
    
    private WriteRequest buildAcquireRequest(String owner, String connectionId,
        long waitTime, boolean waiterRetry) {
        MutexLockRequest mutexLockRequest = new MutexLockRequest();
        LockInfo lockInfo = new LockInfo();
        lockInfo.setKey(new LockKey(LockConstants.NACOS_LOCK_TYPE, "fifo-key"));
        lockInfo.setOwner(owner);
        lockInfo.setConnectionId(connectionId);
        lockInfo.setWaitTime(waitTime);
        lockInfo.setWaiterRetry(waiterRetry);
        lockInfo.setEndTime(System.currentTimeMillis() + 30000);
        mutexLockRequest.setLockInfo(lockInfo);
        return WriteRequest.newBuilder().setGroup(lockOperationService.group())
            .setData(ByteString.copyFrom(serializer.serialize(mutexLockRequest)))
            .setOperation(LockOperationEnum.ACQUIRE.name()).build();
    }
    
    private WriteRequest buildCancelWaitRequest(String owner, String connectionId) {
        MutexLockRequest mutexLockRequest = new MutexLockRequest();
        LockInfo lockInfo = new LockInfo();
        lockInfo.setKey(new LockKey(LockConstants.NACOS_LOCK_TYPE, "fifo-key"));
        lockInfo.setOwner(owner);
        lockInfo.setConnectionId(connectionId);
        mutexLockRequest.setLockInfo(lockInfo);
        return WriteRequest.newBuilder().setGroup(lockOperationService.group())
            .setData(ByteString.copyFrom(serializer.serialize(mutexLockRequest)))
            .setOperation(LockOperationEnum.CANCEL_WAIT.name()).build();
    }
    
    private WriteRequest buildReleaseRequest(String owner, String key, boolean force) {
        MutexLockRequest mutexLockRequest = new MutexLockRequest();
        LockInfo lockInfo = new LockInfo();
        lockInfo.setKey(new LockKey(LockConstants.NACOS_LOCK_TYPE, key));
        lockInfo.setOwner(owner);
        mutexLockRequest.setLockInfo(lockInfo);
        mutexLockRequest.setForceRelease(force);
        return WriteRequest.newBuilder().setGroup(lockOperationService.group())
            .setData(ByteString.copyFrom(serializer.serialize(mutexLockRequest)))
            .setOperation(LockOperationEnum.RELEASE.name()).build();
    }
    
    private WriteRequest buildRenewRequest(String owner, String key) {
        MutexLockRequest mutexLockRequest = new MutexLockRequest();
        LockInfo lockInfo = new LockInfo();
        lockInfo.setKey(new LockKey(LockConstants.NACOS_LOCK_TYPE, key));
        lockInfo.setOwner(owner);
        lockInfo.setEndTime(System.currentTimeMillis() + 30000);
        mutexLockRequest.setLockInfo(lockInfo);
        return WriteRequest.newBuilder().setGroup(lockOperationService.group())
            .setData(ByteString.copyFrom(serializer.serialize(mutexLockRequest)))
            .setOperation(LockOperationEnum.RENEW.name()).build();
    }
    
    private WriteRequest buildExpireRequest(String owner, String key) {
        MutexLockRequest mutexLockRequest = new MutexLockRequest();
        LockInfo lockInfo = new LockInfo();
        lockInfo.setKey(new LockKey(LockConstants.NACOS_LOCK_TYPE, key));
        lockInfo.setOwner(owner);
        mutexLockRequest.setLockInfo(lockInfo);
        return WriteRequest.newBuilder().setGroup(lockOperationService.group())
            .setData(ByteString.copyFrom(serializer.serialize(mutexLockRequest)))
            .setOperation(LockOperationEnum.EXPIRE.name()).build();
    }
    
    // ==================== expireLock 僵尸锁测试 ====================
    
    @Test
    public void testExpireLockCreatesZombieEntryWhenLockAlreadyRemoved() {
        buildService();
        // 模拟场景：过期扫描器读取到一个锁，但当 onApply 执行时，
        // 该锁已被移除（例如被 CLEANUP_CONNECTION 清理）。
        // expireLock 调用 getMutexLock()，该方法通过 computeIfAbsent 创建新的空锁。
        
        ConcurrentHashMap<LockKey, AtomicLockService> locks = new ConcurrentHashMap<>();
        // getMutexLock 在 key 不存在时会创建新锁（computeIfAbsent）
        Mockito.when(lockManager.getMutexLock(Mockito.any(LockKey.class)))
            .thenAnswer(invocation -> {
                LockKey key = invocation.getArgument(0);
                return locks.computeIfAbsent(key, k -> new ReentrantAtomicLock(k.getKey()));
            });
        
        WriteRequest request = buildExpireRequest("owner-1", "zombie-key");
        Response response = lockOperationService.onApply(request);
        assertTrue(response.getSuccess());
        
        // BUG: 虽然之前不存在锁，但 map 中创建了一个僵尸锁条目
        LockKey zombieKey = new LockKey(LockConstants.NACOS_LOCK_TYPE, "zombie-key");
        assertTrue(locks.containsKey(zombieKey),
            "expireLock 不应为不存在的锁创建条目，但产生了僵尸锁");
        AtomicLockService zombieLock = locks.get(zombieKey);
        assertTrue(zombieLock.isClear(),
            "僵尸锁应为空（无 owner）");
    }
    
    // ==================== CLEANUP_CONNECTION tests ====================
    
    @Test
    public void testCleanupConnectionForceReleasesHeldLock() {
        buildService();
        ReentrantAtomicLock reentrantLock = new ReentrantAtomicLock("cleanup-key");
        LockInfo lockInfo = new LockInfo();
        lockInfo.setOwner("owner-1");
        lockInfo.setConnectionId("conn-1");
        lockInfo.setKey(new LockKey(LockConstants.NACOS_LOCK_TYPE, "cleanup-key"));
        lockInfo.setEndTime(System.currentTimeMillis() + 30000);
        reentrantLock.tryLock(lockInfo);
        
        ConcurrentHashMap<LockKey, AtomicLockService> locks = new ConcurrentHashMap<>();
        locks.put(new LockKey(LockConstants.NACOS_LOCK_TYPE, "cleanup-key"), reentrantLock);
        Mockito.when(lockManager.showLocks()).thenReturn(locks);
        
        WriteRequest request = buildCleanupConnectionRequest("cleanup-key", "conn-1");
        Response response = lockOperationService.onApply(request);
        assertTrue(response.getSuccess());
        assertTrue(reentrantLock.isClear());
    }
    
    @Test
    public void testCleanupConnectionRemovesWaiterEntries() {
        buildService();
        ReentrantAtomicLock reentrantLock = new ReentrantAtomicLock("cleanup-waiter-key");
        
        // Holder holds the lock
        LockInfo holderInfo = new LockInfo();
        holderInfo.setOwner("holder-1");
        holderInfo.setConnectionId("conn-holder");
        holderInfo.setKey(new LockKey(LockConstants.NACOS_LOCK_TYPE, "cleanup-waiter-key"));
        holderInfo.setEndTime(System.currentTimeMillis() + 30000);
        reentrantLock.tryLock(holderInfo);
        
        // Disconnected client is in the wait queue
        LockInfo waiterInfo = new LockInfo();
        waiterInfo.setOwner("waiter-disconnected");
        waiterInfo.setConnectionId("conn-disconnected");
        waiterInfo.setWaitTime(5000L);
        reentrantLock.addWaiter(waiterInfo);
        
        // Another waiter also in queue
        LockInfo waiterInfo2 = new LockInfo();
        waiterInfo2.setOwner("waiter-other");
        waiterInfo2.setConnectionId("conn-other");
        waiterInfo2.setWaitTime(5000L);
        reentrantLock.addWaiter(waiterInfo2);
        
        ConcurrentHashMap<LockKey, AtomicLockService> locks = new ConcurrentHashMap<>();
        locks.put(new LockKey(LockConstants.NACOS_LOCK_TYPE, "cleanup-waiter-key"), reentrantLock);
        Mockito.when(lockManager.showLocks()).thenReturn(locks);
        
        // Cleanup the disconnected connection
        WriteRequest request =
            buildCleanupConnectionRequest("cleanup-waiter-key", "conn-disconnected");
        Response response = lockOperationService.onApply(request);
        assertTrue(response.getSuccess());
        
        // Disconnected waiter should be removed; other waiter should remain
        assertEquals(1, reentrantLock.getWaitQueue().size());
        assertEquals("waiter-other", reentrantLock.getWaitQueue().get(0).getOwner());
    }
    
    @Test
    public void testCleanupConnectionNonExistentLock() {
        buildService();
        ConcurrentHashMap<LockKey, AtomicLockService> locks = new ConcurrentHashMap<>();
        Mockito.when(lockManager.showLocks()).thenReturn(locks);
        
        WriteRequest request = buildCleanupConnectionRequest("nonexistent", "conn-1");
        Response response = lockOperationService.onApply(request);
        assertTrue(response.getSuccess());
        LockResult result = serializer.deserialize(response.getData().toByteArray());
        assertTrue(result.isSuccess(), "Cleaning up non-existent lock is a no-op, should succeed");
    }
    
    @Test
    public void testCleanupConnectionReentrantLockForceReleasesAll() {
        buildService();
        ReentrantAtomicLock reentrantLock = new ReentrantAtomicLock("cleanup-reentrant-key");
        LockInfo lockInfo = new LockInfo();
        lockInfo.setOwner("owner-1");
        lockInfo.setConnectionId("conn-1");
        lockInfo.setKey(new LockKey(LockConstants.NACOS_LOCK_TYPE, "cleanup-reentrant-key"));
        lockInfo.setEndTime(System.currentTimeMillis() + 30000);
        reentrantLock.tryLock(lockInfo);
        reentrantLock.tryLock(lockInfo);
        reentrantLock.tryLock(lockInfo);
        
        ConcurrentHashMap<LockKey, AtomicLockService> locks = new ConcurrentHashMap<>();
        locks.put(new LockKey(LockConstants.NACOS_LOCK_TYPE, "cleanup-reentrant-key"),
            reentrantLock);
        Mockito.when(lockManager.showLocks()).thenReturn(locks);
        
        WriteRequest request = buildCleanupConnectionRequest("cleanup-reentrant-key", "conn-1");
        Response response = lockOperationService.onApply(request);
        assertTrue(response.getSuccess());
        assertTrue(reentrantLock.isClear());
        assertEquals(0, reentrantLock.getReentrantCount());
    }
    
    private WriteRequest buildCleanupConnectionRequest(String key, String connectionId) {
        MutexLockRequest mutexLockRequest = new MutexLockRequest();
        LockInfo lockInfo = new LockInfo();
        lockInfo.setKey(new LockKey(LockConstants.NACOS_LOCK_TYPE, key));
        mutexLockRequest.setLockInfo(lockInfo);
        mutexLockRequest.setConnectionId(connectionId);
        return WriteRequest.newBuilder().setGroup(lockOperationService.group())
            .setData(ByteString.copyFrom(serializer.serialize(mutexLockRequest)))
            .setOperation(LockOperationEnum.CLEANUP_CONNECTION.name()).build();
    }
    
    private WriteRequest buildBatchCleanupConnectionRequest(String connectionId) {
        MutexLockRequest mutexLockRequest = new MutexLockRequest();
        mutexLockRequest.setConnectionId(connectionId);
        return WriteRequest.newBuilder().setGroup(lockOperationService.group())
            .setData(ByteString.copyFrom(serializer.serialize(mutexLockRequest)))
            .setOperation(LockOperationEnum.CLEANUP_CONNECTION.name()).build();
    }
    
    private LockInfo newLockInfo(String owner, String connectionId, String key) {
        LockInfo lockInfo = new LockInfo();
        lockInfo.setKey(new LockKey(LockConstants.NACOS_LOCK_TYPE, key));
        lockInfo.setOwner(owner);
        lockInfo.setConnectionId(connectionId);
        lockInfo.setWaitTime(5000L);
        lockInfo.setEndTime(System.currentTimeMillis() + 30000);
        return lockInfo;
    }
}
