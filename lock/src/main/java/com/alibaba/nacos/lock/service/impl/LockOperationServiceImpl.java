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

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.runtime.NacosRuntimeException;
import com.alibaba.nacos.api.lock.model.LockInstance;
import com.alibaba.nacos.api.lock.remote.LockOperationEnum;
import com.alibaba.nacos.consistency.SerializeFactory;
import com.alibaba.nacos.consistency.Serializer;
import com.alibaba.nacos.consistency.cp.CPProtocol;
import com.alibaba.nacos.consistency.cp.RequestProcessor4CP;
import com.alibaba.nacos.consistency.entity.ReadRequest;
import com.alibaba.nacos.consistency.entity.Response;
import com.alibaba.nacos.consistency.entity.WriteRequest;
import com.alibaba.nacos.core.distributed.ProtocolManager;
import com.alibaba.nacos.lock.LockManager;
import com.alibaba.nacos.lock.constant.PropertiesConstant;
import com.alibaba.nacos.lock.constant.Constants;
import com.alibaba.nacos.lock.core.reentrant.AtomicLockService;
import com.alibaba.nacos.lock.raft.request.MutexLockRequest;
import com.alibaba.nacos.lock.service.LockOperationService;
import com.alibaba.nacos.sys.env.EnvUtil;
import com.alibaba.nacos.sys.utils.ApplicationUtils;
import com.google.protobuf.ByteString;
import org.springframework.stereotype.Component;

import java.util.Collections;

/**
 * lock operation and CPHandler.
 *
 * @author 985492783@qq.com
 * @date 2023/8/22 20:17
 */
@Component
public class LockOperationServiceImpl extends RequestProcessor4CP implements LockOperationService {
    
    private final Serializer serializer = SerializeFactory.getDefault();
    
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
                return Response.newBuilder().setSuccess(false).build();
            }
            ByteString bytes = ByteString.copyFrom(serializer.serialize(data));
            return Response.newBuilder().setSuccess(true).setData(bytes).build();
        } catch (Exception e) {
            return Response.newBuilder().setSuccess(false).build();
        }
    }
    
    private Boolean releaseLock(MutexLockRequest request) {
        LockInstance lockInstance = request.getLockInstance();
        AtomicLockService mutexLock = lockManager.getMutexLock(lockInstance.getLockType(), lockInstance.getKey());
        return mutexLock.unLock(request.getLockInstance());
    }
    
    private Boolean acquireLock(MutexLockRequest request) {
        LockInstance lockInstance = request.getLockInstance();
        AtomicLockService mutexLock = lockManager.getMutexLock(lockInstance.getLockType(), lockInstance.getKey());
        return mutexLock.tryLock(request.getLockInstance());
    }
    
    @Override
    public Boolean lock(LockInstance lockInstance) {
        MutexLockRequest request = new MutexLockRequest();
        long expireTimestamp = lockInstance.getExpireTimestamp();
        if (expireTimestamp <= 0) {
            lockInstance.setExpireTimestamp(defaultExpireTime + getNowTimestamp());
        } else {
            lockInstance.setExpireTimestamp(Math.min(maxExpireTime, expireTimestamp) + getNowTimestamp());
        }
        request.setLockInstance(lockInstance);
        WriteRequest writeRequest = WriteRequest.newBuilder().setGroup(group())
                .setData(ByteString.copyFrom(serializer.serialize(request)))
                .setOperation(LockOperationEnum.ACQUIRE.name()).build();
        try {
            Response response = protocol.write(writeRequest);
            return serializer.deserialize(response.getData().toByteArray());
        } catch (Exception e) {
            throw new NacosRuntimeException(NacosException.SERVER_ERROR, e);
        }
    }
    
    @Override
    public Boolean unLock(LockInstance lockInstance) {
        MutexLockRequest request = new MutexLockRequest();
        request.setLockInstance(lockInstance);
        WriteRequest writeRequest = WriteRequest.newBuilder().setGroup(group())
                .setData(ByteString.copyFrom(serializer.serialize(request)))
                .setOperation(LockOperationEnum.RELEASE.name()).build();
        try {
            Response response = protocol.write(writeRequest);
            return serializer.deserialize(response.getData().toByteArray());
        } catch (Exception e) {
            throw new NacosRuntimeException(NacosException.SERVER_ERROR, e);
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
