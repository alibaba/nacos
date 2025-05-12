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
import com.alibaba.nacos.api.lock.remote.LockOperationEnum;
import com.alibaba.nacos.consistency.SerializeFactory;
import com.alibaba.nacos.consistency.Serializer;
import com.alibaba.nacos.consistency.cp.CPProtocol;
import com.alibaba.nacos.consistency.cp.RequestProcessor4CP;
import com.alibaba.nacos.consistency.entity.ReadRequest;
import com.alibaba.nacos.consistency.entity.Response;
import com.alibaba.nacos.consistency.entity.WriteRequest;
import com.alibaba.nacos.consistency.snapshot.SnapshotOperation;
import com.alibaba.nacos.core.distributed.ProtocolManager;
import com.alibaba.nacos.lock.LockManager;
import com.alibaba.nacos.lock.constant.Constants;
import com.alibaba.nacos.lock.constant.PropertiesConstant;
import com.alibaba.nacos.lock.core.reentrant.AtomicLockService;
import com.alibaba.nacos.lock.exception.NacosLockException;
import com.alibaba.nacos.lock.model.LockInfo;
import com.alibaba.nacos.lock.model.LockKey;
import com.alibaba.nacos.lock.persistence.NacosLockSnapshotOperation;
import com.alibaba.nacos.lock.raft.request.MutexLockRequest;
import com.alibaba.nacos.lock.service.LockOperationService;
import com.alibaba.nacos.sys.env.EnvUtil;
import com.alibaba.nacos.sys.utils.ApplicationUtils;
import com.google.protobuf.ByteString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
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
    
    private final CPProtocol protocol;
    
    private final LockManager lockManager;
    
    private final long defaultExpireTime;
    
    private final long maxExpireTime;
    
    public LockOperationServiceImpl(LockManager lockManager) {
        this.lockManager = lockManager;
        this.protocol = ApplicationUtils.getBean(ProtocolManager.class).getCpProtocol();
        this.protocol.addRequestProcessors(Collections.singletonList(this));
        this.defaultExpireTime = EnvUtil.getProperty(PropertiesConstant.DEFAULT_AUTO_EXPIRE, Long.class,
                PropertiesConstant.DEFAULT_AUTO_EXPIRE_TIME);
        this.maxExpireTime = EnvUtil.getProperty(PropertiesConstant.MAX_AUTO_EXPIRE, Long.class,
                PropertiesConstant.MAX_AUTO_EXPIRE_TIME);
    }
    
    @Override
    public Response onApply(WriteRequest request) {
        final Lock lock = readLock;
        lock.lock();
        try {
            LockOperationEnum lockOperation = LockOperationEnum.valueOf(request.getOperation());
            Object data = null;
            if (lockOperation == LockOperationEnum.ACQUIRE) {
                final MutexLockRequest mutexLockRequest = serializer.deserialize(request.getData().toByteArray());
                data = acquireLock(mutexLockRequest);
            } else if (lockOperation == LockOperationEnum.RELEASE) {
                final MutexLockRequest mutexLockRequest = serializer.deserialize(request.getData().toByteArray());
                data = releaseLock(mutexLockRequest);
            } else {
                throw new NacosLockException("lockOperation is not exist.");
            }
            LOGGER.info("thread: {}, operator: {}, request: {}, success: {}", Thread.currentThread().getName(),
                    lockOperation, serializer.deserialize(request.getData().toByteArray()), data);
            ByteString bytes = ByteString.copyFrom(serializer.serialize(data));
            return Response.newBuilder().setSuccess(true).setData(bytes).build();
        } catch (NacosLockException e) {
            return Response.newBuilder().setSuccess(false).setErrMsg(e.getMessage()).build();
        } finally {
            lock.unlock();
        }
    }
    
    private Boolean releaseLock(MutexLockRequest request) {
        LockInfo lockInfo = request.getLockInfo();
        AtomicLockService mutexLock = lockManager.getMutexLock(lockInfo.getKey());
        Boolean unLock = mutexLock.unLock(lockInfo);
        if (mutexLock.isClear()) {
            lockManager.removeMutexLock(lockInfo.getKey());
        }
        return unLock;
    }
    
    private Boolean acquireLock(MutexLockRequest request) {
        LockInfo lockInfo = request.getLockInfo();
        AtomicLockService mutexLock = lockManager.getMutexLock(lockInfo.getKey());
        return mutexLock.tryLock(lockInfo);
    }
    
    @Override
    public Boolean lock(LockInstance lockInstance) {
        final MutexLockRequest request = new MutexLockRequest();
        final LockInfo lockInfo = new LockInfo();
        lockInfo.setKey(new LockKey(lockInstance.getLockType(), lockInstance.getKey()));
        lockInfo.setParams(lockInstance.getParams());
        
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
            LOGGER.error("key: {}, lockType:{}, paramSize:{} lock fail, errorMsg: {}", lockInstance.getKey(),
                    lockInstance.getLockType(), paramSize, e.getMessage());
            throw e;
        } catch (Exception e) {
            LOGGER.error("lock fail.", e);
            throw new NacosLockException("tryLock error.", e);
        }
    }
    
    @Override
    public List<SnapshotOperation> loadSnapshotOperate() {
        return Collections.singletonList(new NacosLockSnapshotOperation(lockManager, lock.writeLock()));
    }
    
    @Override
    public Boolean unLock(LockInstance lockInstance) {
        MutexLockRequest request = new MutexLockRequest();
        LockInfo lockInfo = new LockInfo();
        lockInfo.setKey(new LockKey(lockInstance.getLockType(), lockInstance.getKey()));
        lockInfo.setParams(lockInstance.getParams());
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
            LOGGER.error("key: {}, lockType:{}, paramSize:{} lock fail, errorMsg: {}", lockInstance.getKey(),
                    lockInstance.getLockType(), paramSize, e.getMessage());
            throw e;
        } catch (Exception e) {
            throw new NacosLockException("unLock error.", e);
        }
    }
    
    public long getNowTimestamp() {
        return System.currentTimeMillis();
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
