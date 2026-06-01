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
import com.alibaba.nacos.consistency.entity.Response;
import com.alibaba.nacos.consistency.entity.WriteRequest;
import com.alibaba.nacos.core.distributed.ProtocolManager;
import com.alibaba.nacos.lock.LockManager;
import com.alibaba.nacos.lock.constant.PropertiesConstant;
import com.alibaba.nacos.lock.core.reentrant.AtomicLockService;
import com.alibaba.nacos.lock.core.reentrant.mutex.MutexAtomicLock;
import com.alibaba.nacos.lock.core.reentrant.mutex.ReentrantAtomicLock;
import com.alibaba.nacos.lock.model.LockInfo;
import com.alibaba.nacos.lock.model.LockKey;
import com.alibaba.nacos.lock.raft.request.MutexLockRequest;
import com.alibaba.nacos.sys.env.EnvUtil;
import com.alibaba.nacos.sys.utils.ApplicationUtils;
import com.google.protobuf.ByteString;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.ConcurrentHashMap;

import static com.alibaba.nacos.lock.constant.Constants.LOCK_ACQUIRE_SERVICE_GROUP_V2;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
    
    private final Serializer serializer = SerializeFactory.getDefault();
    
    private LockOperationServiceImpl lockOperationService;
    
    private static MockedStatic<ApplicationUtils> mockedStatic;
    
    private static MockedStatic<EnvUtil> mockedEnv;
    
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
        lockOperationService = Mockito.spy(new LockOperationServiceImpl(lockManager, protocolManager));
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
        return Response.newBuilder().setSuccess(true).setData(ByteString.copyFrom(serializer.serialize(LockResult.success(1)))).build();
    }
    
    @AfterAll
    public static void destroy() {
        mockedStatic.close();
        mockedEnv.close();
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
        waiterInfo.setWaitTimeMs(5000);
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
        waiterInfo.setWaitTimeMs(5000);
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

    // ==================== helper methods ====================

    private WriteRequest buildAcquireRequest(String owner, String connectionId,
            long waitTimeMs, boolean waiterRetry) {
        MutexLockRequest mutexLockRequest = new MutexLockRequest();
        LockInfo lockInfo = new LockInfo();
        lockInfo.setKey(new LockKey(LockConstants.NACOS_LOCK_TYPE, "fifo-key"));
        lockInfo.setOwner(owner);
        lockInfo.setConnectionId(connectionId);
        lockInfo.setWaitTimeMs(waitTimeMs);
        lockInfo.setWaiterRetry(waiterRetry);
        lockInfo.setEndTime(System.currentTimeMillis() + 30000);
        mutexLockRequest.setLockInfo(lockInfo);
        return WriteRequest.newBuilder().setGroup(lockOperationService.group())
                .setData(ByteString.copyFrom(serializer.serialize(mutexLockRequest)))
                .setOperation(LockOperationEnum.ACQUIRE.name()).build();
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
        waiterInfo.setWaitTimeMs(5000);
        reentrantLock.addWaiter(waiterInfo);

        // Another waiter also in queue
        LockInfo waiterInfo2 = new LockInfo();
        waiterInfo2.setOwner("waiter-other");
        waiterInfo2.setConnectionId("conn-other");
        waiterInfo2.setWaitTimeMs(5000);
        reentrantLock.addWaiter(waiterInfo2);

        ConcurrentHashMap<LockKey, AtomicLockService> locks = new ConcurrentHashMap<>();
        locks.put(new LockKey(LockConstants.NACOS_LOCK_TYPE, "cleanup-waiter-key"), reentrantLock);
        Mockito.when(lockManager.showLocks()).thenReturn(locks);

        // Cleanup the disconnected connection
        WriteRequest request = buildCleanupConnectionRequest("cleanup-waiter-key", "conn-disconnected");
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
        assertFalse(result.isSuccess());
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
        locks.put(new LockKey(LockConstants.NACOS_LOCK_TYPE, "cleanup-reentrant-key"), reentrantLock);
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
}
