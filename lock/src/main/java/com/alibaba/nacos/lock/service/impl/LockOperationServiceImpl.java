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

import com.alibaba.nacos.api.lock.model.LockInstance;
import com.alibaba.nacos.api.lock.model.LockResult;
import com.alibaba.nacos.api.lock.remote.LockOperationEnum;
import com.alibaba.nacos.api.lock.remote.request.LockNotificationRequest;
import com.alibaba.nacos.consistency.SerializeFactory;
import com.alibaba.nacos.consistency.Serializer;
import com.alibaba.nacos.consistency.cp.CPProtocol;
import com.alibaba.nacos.consistency.cp.RequestProcessor4CP;
import com.alibaba.nacos.consistency.entity.ReadRequest;
import com.alibaba.nacos.consistency.entity.Response;
import com.alibaba.nacos.consistency.entity.WriteRequest;
import com.alibaba.nacos.consistency.snapshot.SnapshotOperation;
import com.alibaba.nacos.core.distributed.ProtocolManager;
import com.alibaba.nacos.core.remote.RpcPushService;
import com.alibaba.nacos.lock.LockManager;
import com.alibaba.nacos.lock.constant.Constants;
import com.alibaba.nacos.lock.constant.PropertiesConstant;
import com.alibaba.nacos.lock.core.reentrant.AbstractAtomicLock;
import com.alibaba.nacos.lock.core.reentrant.AtomicLockService;
import com.alibaba.nacos.lock.exception.NacosLockException;
import com.alibaba.nacos.lock.model.LockInfo;
import com.alibaba.nacos.lock.model.LockKey;
import com.alibaba.nacos.lock.model.WaitEntry;
import com.alibaba.nacos.lock.persistence.NacosLockSnapshotOperation;
import com.alibaba.nacos.lock.raft.request.MutexLockRequest;
import com.alibaba.nacos.lock.service.LockOperationService;
import com.alibaba.nacos.sys.env.EnvUtil;
import com.google.protobuf.ByteString;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * lock operation and CPHandler.
 *
 * @author 985492783@qq.com
 * @date 2023/8/22 20:17
 */
@Component
public class LockOperationServiceImpl extends RequestProcessor4CP implements LockOperationService {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(LockOperationServiceImpl.class);
    
    private final Serializer serializer = SerializeFactory.getDefault();
    
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    
    private final ReentrantReadWriteLock.ReadLock readLock = lock.readLock();
    
    private final ProtocolManager protocolManager;
    
    private final LockManager lockManager;
    
    private CPProtocol protocol;
    
    private long defaultExpireTime;
    
    private long maxExpireTime;
    
    private final ExecutorService notificationExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "lock-notification");
        t.setDaemon(true);
        return t;
    });
    
    @Autowired
    private RpcPushService rpcPushService;
    
    public LockOperationServiceImpl(LockManager lockManager, ProtocolManager protocolManager) {
        this.lockManager = lockManager;
        this.protocolManager = protocolManager;
    }
    
    /**
     * Initialize protocol and configuration after Spring bean construction.
     */
    @PostConstruct
    public void init() {
        this.protocol = protocolManager.getCpProtocol();
        this.protocol.addRequestProcessors(Collections.singletonList(this));
        this.defaultExpireTime =
            EnvUtil.getProperty(PropertiesConstant.DEFAULT_AUTO_EXPIRE, Long.class,
                PropertiesConstant.DEFAULT_AUTO_EXPIRE_TIME);
        this.maxExpireTime = EnvUtil.getProperty(PropertiesConstant.MAX_AUTO_EXPIRE, Long.class,
            PropertiesConstant.MAX_AUTO_EXPIRE_TIME);
    }
    
    @PreDestroy
    public void destroy() {
        notificationExecutor.shutdown();
    }
    
    @Override
    public Response onApply(WriteRequest request) {
        final Lock lock = readLock;
        lock.lock();
        try {
            LockOperationEnum lockOperation = LockOperationEnum.valueOf(request.getOperation());
            Object data;
            final MutexLockRequest mutexLockRequest =
                serializer.deserialize(request.getData().toByteArray());
            if (lockOperation == LockOperationEnum.ACQUIRE) {
                data = acquireLock(mutexLockRequest);
            } else if (lockOperation == LockOperationEnum.RELEASE) {
                data = releaseLock(mutexLockRequest);
            } else if (lockOperation == LockOperationEnum.RENEW) {
                data = renewLock(mutexLockRequest);
            } else if (lockOperation == LockOperationEnum.EXPIRE) {
                data = expireLock(mutexLockRequest);
            } else if (lockOperation == LockOperationEnum.CANCEL_WAIT) {
                data = cancelWaitInternal(mutexLockRequest);
            } else if (lockOperation == LockOperationEnum.CLEANUP_CONNECTION) {
                data = cleanupConnection(mutexLockRequest);
            } else {
                throw new NacosLockException("lockOperation is not exist.");
            }
            if (LOGGER.isDebugEnabled()) {
                LockInfo lockInfo = mutexLockRequest.getLockInfo();
                LOGGER.debug("onApply {} key={}, owner={}, result={}",
                    lockOperation,
                    lockInfo != null ? lockInfo.getKey() : "batch",
                    lockInfo != null ? lockInfo.getOwner() : null,
                    data);
            }
            ByteString bytes = ByteString.copyFrom(serializer.serialize(data));
            return Response.newBuilder().setSuccess(true).setData(bytes).build();
        } catch (NacosLockException e) {
            return Response.newBuilder().setSuccess(false).setErrMsg(e.getMessage()).build();
        } catch (Exception e) {
            LOGGER.error("onApply error, operation: {}", request.getOperation(), e);
            return Response.newBuilder().setSuccess(false).setErrMsg(e.getMessage()).build();
        } finally {
            lock.unlock();
        }
    }
    
    private LockResult releaseLock(MutexLockRequest request) {
        LockInfo lockInfo = request.getLockInfo();
        AtomicLockService mutexLock = lockManager.showLocks().get(lockInfo.getKey());
        if (mutexLock == null) {
            return LockResult.fail("Lock does not exist or already expired");
        }
        Boolean released;
        if (request.isForceRelease()) {
            released = mutexLock.forceRelease();
        } else {
            released = mutexLock.unLock(lockInfo);
        }
        int remainingCount = 0;
        if (mutexLock instanceof AbstractAtomicLock) {
            remainingCount = ((AbstractAtomicLock) mutexLock).getReentrantCount();
        }
        if (mutexLock.isClear()) {
            boolean hasWaiters = mutexLock instanceof AbstractAtomicLock
                && ((AbstractAtomicLock) mutexLock).hasWaiters();
            if (hasWaiters) {
                AbstractAtomicLock atomicLock = (AbstractAtomicLock) mutexLock;
                WaitEntry entry = atomicLock.peekFirstWaiter();
                if (entry != null) {
                    LockKey lockKey = lockInfo.getKey();
                    LockNotificationRequest notification = LockNotificationRequest.available(
                        lockKey.getKey(), lockKey.getLockType(), entry.getOwner());
                    String targetConn = entry.getConnectionId();
                    notificationExecutor.submit(() -> {
                        try {
                            rpcPushService.pushWithoutAck(targetConn, notification);
                        } catch (Exception e) {
                            LOGGER.warn("Lock: failed to notify waiter after release, key={}",
                                lockKey, e);
                        }
                    });
                }
            } else {
                lockManager.removeMutexLock(lockInfo.getKey());
            }
        }
        if (released) {
            return LockResult.success(remainingCount);
        }
        return LockResult.fail("Unlock failed: not held by this owner");
    }
    
    private LockResult acquireLock(MutexLockRequest request) {
        LockInfo lockInfo = request.getLockInfo();
        AtomicLockService mutexLock = lockManager.getMutexLock(lockInfo.getKey());
        
        if (mutexLock instanceof AbstractAtomicLock atomicLock) {
            if (lockInfo.isWaiterRetry()) {
                return atomicLock.tryLockAsQueueHead(lockInfo);
            }
            
            // Strict FIFO: non-retry requests must queue behind existing waiters
            if (!lockInfo.isWaiterRetry() && atomicLock.hasWaiters()) {
                if (lockInfo.getWaitTime() > 0) {
                    int position = atomicLock.addWaiter(lockInfo);
                    return LockResult.waiting(position);
                }
                return LockResult.fail("Lock is held by another owner");
            }
            
            Boolean acquired = mutexLock.tryLock(lockInfo);
            if (acquired) {
                return LockResult.success(atomicLock.getReentrantCount());
            }
            
            if (lockInfo.getWaitTime() > 0) {
                int position = atomicLock.addWaiter(lockInfo);
                return LockResult.waiting(position);
            }
            return LockResult.fail("Lock is held by another owner");
        }
        
        Boolean acquired = mutexLock.tryLock(lockInfo);
        if (acquired) {
            return LockResult.success(1);
        }
        return LockResult.fail("Lock is held by another owner");
    }
    
    private Boolean renewLock(MutexLockRequest request) {
        LockInfo lockInfo = request.getLockInfo();
        AtomicLockService mutexLock = lockManager.getMutexLock(lockInfo.getKey());
        return mutexLock.renew(lockInfo);
    }
    
    private LockResult cancelWaitInternal(MutexLockRequest request) {
        LockInfo lockInfo = request.getLockInfo();
        AtomicLockService mutexLock = lockManager.showLocks().get(lockInfo.getKey());
        if (!(mutexLock instanceof AbstractAtomicLock atomicLock)) {
            return LockResult.success(0);
        }
        
        boolean removed = atomicLock.removeWaiter(lockInfo.getOwner(), lockInfo.getConnectionId());
        if (removed && atomicLock.isClear()) {
            if (atomicLock.hasWaiters()) {
                notifyFirstWaiter(lockInfo.getKey(), atomicLock);
            } else {
                lockManager.removeMutexLock(lockInfo.getKey());
            }
        }
        // If the removed waiter was the queue head and lock is still held,
        // notify the new first waiter so it knows it is now first in line.
        if (removed && !atomicLock.isClear()) {
            if (atomicLock.hasWaiters()) {
                notifyFirstWaiter(lockInfo.getKey(), atomicLock);
            }
        }
        return LockResult.success(0);
    }
    
    @Override
    public LockResult cancelWait(LockInstance lockInstance, String connectionId) {
        MutexLockRequest request = new MutexLockRequest();
        LockInfo lockInfo = new LockInfo();
        lockInfo.setKey(new LockKey(lockInstance.getLockType(), lockInstance.getKey()));
        lockInfo.setOwner(lockInstance.getOwner());
        lockInfo.setConnectionId(connectionId);
        request.setLockInfo(lockInfo);
        WriteRequest writeRequest = WriteRequest.newBuilder().setGroup(group())
            .setData(ByteString.copyFrom(serializer.serialize(request)))
            .setOperation(LockOperationEnum.CANCEL_WAIT.name()).build();
        try {
            Response response = protocol.write(writeRequest);
            if (response.getSuccess()) {
                return serializer.deserialize(response.getData().toByteArray());
            }
            throw new NacosLockException(response.getErrMsg());
        } catch (NacosLockException e) {
            LOGGER.error("key: {}, lockType:{} cancel wait fail, errorMsg: {}",
                lockInstance.getKey(), lockInstance.getLockType(), e.getMessage());
            throw e;
        } catch (Exception e) {
            throw new NacosLockException("cancel wait error.", e);
        }
    }
    
    private LockResult expireLock(MutexLockRequest request) {
        LockInfo lockInfo = request.getLockInfo();
        AtomicLockService mutexLock = lockManager.getMutexLock(lockInfo.getKey());
        if (mutexLock instanceof AbstractAtomicLock) {
            AbstractAtomicLock atomicLock = (AbstractAtomicLock) mutexLock;
            Boolean expired = atomicLock.autoExpire();
            if (expired) {
                if (atomicLock.isClear()) {
                    boolean hasWaiters = atomicLock.hasWaiters();
                    if (hasWaiters) {
                        WaitEntry entry = atomicLock.peekFirstWaiter();
                        if (entry != null) {
                            LockKey lockKey = lockInfo.getKey();
                            LockNotificationRequest notification =
                                LockNotificationRequest.available(
                                    lockKey.getKey(), lockKey.getLockType(), entry.getOwner());
                            String targetConn = entry.getConnectionId();
                            notificationExecutor.submit(() -> {
                                try {
                                    rpcPushService.pushWithoutAck(targetConn, notification);
                                } catch (Exception e) {
                                    LOGGER.warn(
                                        "Lock: failed to notify waiter after expire, key={}",
                                        lockKey, e);
                                }
                            });
                        }
                    } else {
                        lockManager.removeMutexLock(lockInfo.getKey());
                    }
                }
                return LockResult.success(0);
            }
        }
        return LockResult.fail("Lock not expired or not found");
    }
    
    /**
     * Cleanup lock state for a disconnected connection inside the Raft state machine.
     *
     * <p>If the request contains a specific lock key, cleans up only that lock.
     * Otherwise (batch mode), iterates all locks and cleans up each one.
     *
     * <p>For each affected lock, atomically performs:
     * <ol>
     *     <li>Force-release the lock if held by this connection</li>
     *     <li>Notify the next waiter (if any) after release</li>
     *     <li>Remove all wait queue entries belonging to this connection</li>
     * </ol>
     *
     * @param request lock request with connectionId (and optional key)
     * @return lock result indicating cleanup outcome
     */
    private LockResult cleanupConnection(MutexLockRequest request) {
        String connectionId = request.getConnectionId();
        LockInfo lockInfo = request.getLockInfo();
        
        if (lockInfo != null && lockInfo.getKey() != null) {
            cleanupSingleLock(lockInfo.getKey(), connectionId);
        } else {
            for (Map.Entry<LockKey, AtomicLockService> entry : lockManager.showLocks().entrySet()) {
                if (entry.getValue() instanceof AbstractAtomicLock) {
                    cleanupSingleLock(entry.getKey(), connectionId);
                }
            }
        }
        return LockResult.success(0);
    }
    
    private void cleanupSingleLock(LockKey lockKey, String connectionId) {
        AtomicLockService lockService = lockManager.showLocks().get(lockKey);
        if (lockService == null || !(lockService instanceof AbstractAtomicLock atomicLock)) {
            return;
        }
        
        // Step 1: Force-release if held by this connection
        boolean wasHeld = false;
        if (connectionId.equals(atomicLock.getConnectionId())) {
            String owner = atomicLock.getOwner();
            if (owner != null) {
                wasHeld = atomicLock.forceRelease();
            }
        }
        
        // Step 2: Remove all wait queue entries belonging to this connection
        atomicLock.removeWaiterByConnection(connectionId);
        
        // Step 3: If lock was released and now clear, handle post-release logic
        if (wasHeld && atomicLock.isClear()) {
            if (atomicLock.hasWaiters()) {
                notifyFirstWaiter(lockKey, atomicLock);
            } else {
                lockManager.removeMutexLock(lockKey);
            }
        }
        
        // Step 4: If lock is still held by another connection, notify the new first waiter.
        // The removed waiter may have been the queue head; the new head needs to know
        // it is now first in line.
        if (!wasHeld && !atomicLock.isClear()) {
            if (atomicLock.hasWaiters()) {
                notifyFirstWaiter(lockKey, atomicLock);
            }
        }
    }
    
    @Override
    public LockResult lock(LockInstance lockInstance, String connectionId) {
        final MutexLockRequest request = new MutexLockRequest();
        final LockInfo lockInfo = new LockInfo();
        lockInfo.setKey(new LockKey(lockInstance.getLockType(), lockInstance.getKey()));
        lockInfo.setParams(lockInstance.getParams());
        lockInfo.setOwner(lockInstance.getOwner());
        lockInfo.setConnectionId(connectionId);
        lockInfo.setWaitTime(lockInstance.getWaitTime());
        lockInfo.setWaiterRetry(lockInstance.isWaiterRetry());
        
        long expiredTime = lockInstance.getExpiredTime();
        if (expiredTime < 0) {
            lockInfo.setEndTime(defaultExpireTime + getNowTimestamp());
        } else {
            lockInfo.setEndTime(Math.min(maxExpireTime, expiredTime) + getNowTimestamp());
        }
        request.setLockInfo(lockInfo);
        WriteRequest writeRequest = WriteRequest.newBuilder().setGroup(group())
            .setData(ByteString.copyFrom(serializer.serialize(request)))
            .setOperation(LockOperationEnum.ACQUIRE.name()).build();
        try {
            Response response = protocol.write(writeRequest);
            if (response.getSuccess()) {
                return serializer.deserialize(response.getData().toByteArray());
            }
            throw new NacosLockException(response.getErrMsg());
        } catch (NacosLockException e) {
            int paramSize = lockInstance.getParams() == null ? 0 : lockInstance.getParams().size();
            LOGGER.error("key: {}, lockType:{}, paramSize:{} lock fail, errorMsg: {}",
                lockInstance.getKey(),
                lockInstance.getLockType(), paramSize, e.getMessage());
            throw e;
        } catch (Exception e) {
            LOGGER.error("lock fail.", e);
            throw new NacosLockException("tryLock error.", e);
        }
    }
    
    @Override
    public LockResult unLock(LockInstance lockInstance) {
        MutexLockRequest request = new MutexLockRequest();
        LockInfo lockInfo = new LockInfo();
        lockInfo.setKey(new LockKey(lockInstance.getLockType(), lockInstance.getKey()));
        lockInfo.setParams(lockInstance.getParams());
        lockInfo.setOwner(lockInstance.getOwner());
        request.setLockInfo(lockInfo);
        WriteRequest writeRequest = WriteRequest.newBuilder().setGroup(group())
            .setData(ByteString.copyFrom(serializer.serialize(request)))
            .setOperation(LockOperationEnum.RELEASE.name()).build();
        try {
            Response response = protocol.write(writeRequest);
            if (response.getSuccess()) {
                return serializer.deserialize(response.getData().toByteArray());
            }
            throw new NacosLockException(response.getErrMsg());
        } catch (NacosLockException e) {
            int paramSize = lockInstance.getParams() == null ? 0 : lockInstance.getParams().size();
            LOGGER.error("key: {}, lockType:{}, paramSize:{} unlock fail, errorMsg: {}",
                lockInstance.getKey(),
                lockInstance.getLockType(), paramSize, e.getMessage());
            throw e;
        } catch (Exception e) {
            throw new NacosLockException("unLock error.", e);
        }
    }
    
    @Override
    public Boolean renew(LockInstance lockInstance) {
        MutexLockRequest request = new MutexLockRequest();
        LockInfo lockInfo = new LockInfo();
        lockInfo.setKey(new LockKey(lockInstance.getLockType(), lockInstance.getKey()));
        lockInfo.setParams(lockInstance.getParams());
        lockInfo.setOwner(lockInstance.getOwner());
        
        long expiredTime = lockInstance.getExpiredTime();
        if (expiredTime < 0) {
            lockInfo.setEndTime(defaultExpireTime + getNowTimestamp());
        } else {
            lockInfo.setEndTime(Math.min(maxExpireTime, expiredTime) + getNowTimestamp());
        }
        request.setLockInfo(lockInfo);
        WriteRequest writeRequest = WriteRequest.newBuilder().setGroup(group())
            .setData(ByteString.copyFrom(serializer.serialize(request)))
            .setOperation(LockOperationEnum.RENEW.name()).build();
        try {
            Response response = protocol.write(writeRequest);
            if (response.getSuccess()) {
                return serializer.deserialize(response.getData().toByteArray());
            }
            throw new NacosLockException(response.getErrMsg());
        } catch (NacosLockException e) {
            LOGGER.error("key: {}, lockType:{} renew fail, errorMsg: {}", lockInstance.getKey(),
                lockInstance.getLockType(), e.getMessage());
            throw e;
        } catch (Exception e) {
            throw new NacosLockException("renew error.", e);
        }
    }
    
    @Override
    public LockResult expire(LockInstance lockInstance) {
        MutexLockRequest request = new MutexLockRequest();
        LockInfo lockInfo = new LockInfo();
        lockInfo.setKey(new LockKey(lockInstance.getLockType(), lockInstance.getKey()));
        lockInfo.setOwner(lockInstance.getOwner());
        request.setLockInfo(lockInfo);
        WriteRequest writeRequest = WriteRequest.newBuilder().setGroup(group())
            .setData(ByteString.copyFrom(serializer.serialize(request)))
            .setOperation(LockOperationEnum.EXPIRE.name()).build();
        try {
            Response response = protocol.write(writeRequest);
            if (response.getSuccess()) {
                return serializer.deserialize(response.getData().toByteArray());
            }
            throw new NacosLockException(response.getErrMsg());
        } catch (NacosLockException e) {
            LOGGER.error("key: {}, lockType:{} expire fail, errorMsg: {}", lockInstance.getKey(),
                lockInstance.getLockType(), e.getMessage());
            throw e;
        } catch (Exception e) {
            throw new NacosLockException("expire error.", e);
        }
    }
    
    private void notifyFirstWaiter(LockKey lockKey, AbstractAtomicLock atomicLock) {
        WaitEntry entry = atomicLock.peekFirstWaiter();
        if (entry == null) {
            return;
        }
        String targetConnectionId = entry.getConnectionId();
        LockNotificationRequest notification = LockNotificationRequest.available(
            lockKey.getKey(), lockKey.getLockType(), entry.getOwner());
        notificationExecutor.submit(() -> {
            try {
                rpcPushService.pushWithoutAck(targetConnectionId, notification);
            } catch (Exception e) {
                LOGGER.warn("Lock: failed to notify waiter, key={}, connectionId={}",
                    lockKey, targetConnectionId, e);
            }
        });
        LOGGER.info("notifyFirstWaiter key={}, notified owner={}", lockKey, entry.getOwner());
    }
    
    /**
     * Force release all locks held by the specified connection and clean up wait queue entries.
     *
     * <p>Submits a single {@code CLEANUP_CONNECTION} Raft request. The batch iteration
     * happens inside {@code onApply()} under Raft consensus, ensuring cluster-wide consistency.
     *
     * @param connectionId the gRPC connection ID of the disconnected client
     */
    public void releaseLocksByConnection(String connectionId) {
        try {
            cleanupConnectionViaRaft(connectionId);
        } catch (Exception e) {
            LOGGER.warn("Lock: failed to cleanup connection via Raft, connectionId={}",
                connectionId, e);
        }
    }
    
    private void cleanupConnectionViaRaft(String connectionId) throws Exception {
        MutexLockRequest request = new MutexLockRequest();
        request.setConnectionId(connectionId);
        WriteRequest writeRequest = WriteRequest.newBuilder().setGroup(group())
            .setData(ByteString.copyFrom(serializer.serialize(request)))
            .setOperation(LockOperationEnum.CLEANUP_CONNECTION.name()).build();
        Response response = protocol.write(writeRequest);
        if (!response.getSuccess()) {
            throw new NacosLockException(response.getErrMsg());
        }
    }
    
    public long getNowTimestamp() {
        return System.currentTimeMillis();
    }
    
    @Override
    public List<SnapshotOperation> loadSnapshotOperate() {
        return Collections
            .singletonList(new NacosLockSnapshotOperation(lockManager, lock.writeLock()));
    }
    
    @Override
    public Response onRequest(ReadRequest request) {
        return null;
    }
    
    @Override
    public String group() {
        return Constants.LOCK_ACQUIRE_SERVICE_GROUP_V2;
    }
    
}
