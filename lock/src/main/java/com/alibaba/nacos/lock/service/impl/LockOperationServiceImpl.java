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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.Collections;
import java.util.List;
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
            final MutexLockRequest mutexLockRequest = serializer.deserialize(request.getData().toByteArray());
            if (lockOperation == LockOperationEnum.ACQUIRE) {
                data = acquireLock(mutexLockRequest);
            } else if (lockOperation == LockOperationEnum.RELEASE) {
                data = releaseLock(mutexLockRequest);
            } else if (lockOperation == LockOperationEnum.RENEW) {
                data = renewLock(mutexLockRequest);
            } else if (lockOperation == LockOperationEnum.EXPIRE) {
                data = expireLock(mutexLockRequest);
            } else {
                throw new NacosLockException("lockOperation is not exist.");
            }
            if (LOGGER.isDebugEnabled()) {
                LockInfo lockInfo = mutexLockRequest.getLockInfo();
                LOGGER.debug("onApply {} key={}, owner={}, result={}",
                        lockOperation, lockInfo.getKey(), lockInfo.getOwner(), data);
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
                    notificationExecutor.submit(() -> rpcPushService.pushWithoutAck(entry.getConnectionId(), notification));
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
        Boolean acquired = mutexLock.tryLock(lockInfo);
        if (acquired) {
            if (mutexLock instanceof AbstractAtomicLock) {
                AbstractAtomicLock atomicLock = (AbstractAtomicLock) mutexLock;
                if (atomicLock.hasWaiters()) {
                    if (lockInfo.isWaiterRetry()) {
                        WaitEntry head = atomicLock.peekFirstWaiter();
                        if (head != null && head.getOwner().equals(lockInfo.getOwner())) {
                            // Head waiter retrying after notification — remove stale queue entry
                            atomicLock.pollFirstWaiter();
                        } else {
                            // Non-head waiter acquired due to timeout retry — undo to preserve FIFO.
                            // forceRelease() + removeStaleWaiter() + addWaiter() is NOT atomic,
                            // but safe because this method only runs inside the Raft onApply()
                            // single-threaded state machine — no other request can observe the gap.
                            atomicLock.forceRelease();
                            atomicLock.removeStaleWaiter(lockInfo.getOwner());
                            int position = atomicLock.addWaiter(lockInfo);
                            notifyFirstWaiter(lockInfo.getKey(), atomicLock);
                            return LockResult.waiting(position);
                        }
                    } else {
                        // New request but queue has waiters — undo and enqueue (FIFO).
                        // Same non-atomic gap as above; safe under Raft single-threaded apply.
                        atomicLock.forceRelease();
                        int position = atomicLock.addWaiter(lockInfo);
                        return LockResult.waiting(position);
                    }
                }
                return LockResult.success(atomicLock.getReentrantCount());
            }
            return LockResult.success(1);
        }
        if (lockInfo.getWaitTimeMs() > 0 && mutexLock instanceof AbstractAtomicLock) {
            AbstractAtomicLock atomicLock = (AbstractAtomicLock) mutexLock;
            int position = atomicLock.addWaiter(lockInfo);
            return LockResult.waiting(position);
        }
        return LockResult.fail("Lock is held by another owner");
    }

    private Boolean renewLock(MutexLockRequest request) {
        LockInfo lockInfo = request.getLockInfo();
        AtomicLockService mutexLock = lockManager.getMutexLock(lockInfo.getKey());
        return mutexLock.renew(lockInfo);
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
                            LockNotificationRequest notification = LockNotificationRequest.available(
                                    lockKey.getKey(), lockKey.getLockType(), entry.getOwner());
                            notificationExecutor.submit(
                                    () -> rpcPushService.pushWithoutAck(entry.getConnectionId(), notification));
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

    @Override
    public LockResult lock(LockInstance lockInstance, String connectionId) {
        final MutexLockRequest request = new MutexLockRequest();
        final LockInfo lockInfo = new LockInfo();
        lockInfo.setKey(new LockKey(lockInstance.getLockType(), lockInstance.getKey()));
        lockInfo.setParams(lockInstance.getParams());
        lockInfo.setOwner(lockInstance.getOwner());
        lockInfo.setConnectionId(connectionId);
        lockInfo.setWaitTimeMs(lockInstance.getWaitTimeMs());
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
            LOGGER.error("key: {}, lockType:{}, paramSize:{} unlock fail, errorMsg: {}", lockInstance.getKey(),
                    lockInstance.getLockType(), paramSize, e.getMessage());
            throw e;
        } catch (Exception e) {
            throw new NacosLockException("unLock error.", e);
        }
    }

    private void forceUnLock(LockKey lockKey, String owner) {
        MutexLockRequest request = new MutexLockRequest();
        LockInfo lockInfo = new LockInfo();
        lockInfo.setKey(lockKey);
        lockInfo.setOwner(owner);
        request.setLockInfo(lockInfo);
        request.setForceRelease(true);
        WriteRequest writeRequest = WriteRequest.newBuilder().setGroup(group())
            .setData(ByteString.copyFrom(serializer.serialize(request)))
            .setOperation(LockOperationEnum.RELEASE.name()).build();
        try {
            Response response = protocol.write(writeRequest);
            if (!response.getSuccess()) {
                throw new NacosLockException(response.getErrMsg());
            }
        } catch (NacosLockException e) {
            LOGGER.error("key: {}, owner: {} forceUnlock fail, errorMsg: {}", lockKey, owner, e.getMessage());
            throw e;
        } catch (Exception e) {
            throw new NacosLockException("forceUnLock error.", e);
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
        LockNotificationRequest notification = LockNotificationRequest.available(
                lockKey.getKey(), lockKey.getLockType(), entry.getOwner());
        rpcPushService.pushWithoutAck(entry.getConnectionId(), notification);
        LOGGER.info("notifyFirstWaiter key={}, notified owner={}", lockKey, entry.getOwner());
    }

    /**
     * Force release all locks held by the specified connection and clean up wait queue entries.
     *
     * @param connectionId the gRPC connection ID of the disconnected client
     */
    public void releaseLocksByConnection(String connectionId) {
        java.util.Map<LockKey, AtomicLockService> snapshot = new java.util.HashMap<>(lockManager.showLocks());
        for (java.util.Map.Entry<LockKey, AtomicLockService> entry : snapshot.entrySet()) {
            AtomicLockService lockService = entry.getValue();
            if (lockService instanceof AbstractAtomicLock) {
                AbstractAtomicLock atomicLock = (AbstractAtomicLock) lockService;
                LockKey lockKey = entry.getKey();
                if (connectionId.equals(atomicLock.getConnectionId())) {
                    String owner = atomicLock.getOwner();
                    if (owner == null) {
                        continue;
                    }
                    try {
                        forceUnLock(lockKey, owner);
                    } catch (Exception e) {
                        LOGGER.warn("Lock: failed to force release lock via Raft for connectionId={}, key={}",
                                connectionId, lockKey, e);
                    }
                }
                atomicLock.removeWaiterByConnection(connectionId);
            }
        }
    }

    public long getNowTimestamp() {
        return System.currentTimeMillis();
    }

    @Override
    public List<SnapshotOperation> loadSnapshotOperate() {
        return Collections.singletonList(new NacosLockSnapshotOperation(lockManager, lock.writeLock()));
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
