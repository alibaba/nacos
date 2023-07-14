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

package com.alibaba.nacos.lock.core.connect;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.runtime.NacosRuntimeException;
import com.alibaba.nacos.common.utils.ConcurrentHashSet;
import com.alibaba.nacos.api.lock.remote.LockOperation;
import com.alibaba.nacos.consistency.SerializeFactory;
import com.alibaba.nacos.consistency.Serializer;
import com.alibaba.nacos.consistency.cp.CPProtocol;
import com.alibaba.nacos.consistency.entity.WriteRequest;
import com.alibaba.nacos.core.distributed.ProtocolManager;
import com.alibaba.nacos.core.remote.ClientConnectionEventListener;
import com.alibaba.nacos.core.remote.Connection;
import com.alibaba.nacos.lock.constants.Constants;
import com.alibaba.nacos.lock.core.service.impl.MutexOperationServiceImpl;
import com.alibaba.nacos.lock.enums.ConnectTypeEnum;
import com.alibaba.nacos.sys.utils.ApplicationUtils;
import com.google.protobuf.ByteString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * lock connection manager.
 *
 * @author 985492783@qq.com
 * @description LockConnectionManager
 * @date 2023/7/10 10:47
 */
@Component("lockConnectionManager")
public class LockConnectionManager extends ClientConnectionEventListener implements ConnectionManager {
    
    private final Logger log = LoggerFactory.getLogger(LockConnectionManager.class);
    
    private final ConcurrentHashSet<String> grpcConnectionMap;
    
    private final CPProtocol protocol;
    
    private final Serializer serializer = SerializeFactory.getDefault();
    
    public LockConnectionManager() {
        grpcConnectionMap = new ConcurrentHashSet<>();
        this.protocol = ApplicationUtils.getBean(ProtocolManager.class).getCpProtocol();
    }
    
    @Override
    public void clientConnected(Connection connect) {
        MutexOperationServiceImpl.ConnectedLockRequest request = new MutexOperationServiceImpl.ConnectedLockRequest(
                connect.getMetaInfo().getConnectionId(), ConnectTypeEnum.GRPC);
        WriteRequest writeRequest = WriteRequest.newBuilder()
                .setData(ByteString.copyFrom(serializer.serialize(request)))
                .setOperation(LockOperation.CONNECTED.name()).setGroup(Constants.LOCK_ACQUIRE_SERVICE_GROUP_V2).build();
        try {
            protocol.write(writeRequest);
        } catch (Exception e) {
            throw new NacosRuntimeException(NacosException.SERVER_ERROR, e);
        }
    }
    
    @Override
    public void clientDisConnected(Connection connect) {
        MutexOperationServiceImpl.DisconnectedLockRequest request = new MutexOperationServiceImpl.DisconnectedLockRequest(
                connect.getMetaInfo().getConnectionId(), ConnectTypeEnum.GRPC);
        WriteRequest writeRequest = WriteRequest.newBuilder()
                .setData(ByteString.copyFrom(serializer.serialize(request)))
                .setOperation(LockOperation.DISCONNECTED.name()).setGroup(Constants.LOCK_ACQUIRE_SERVICE_GROUP_V2)
                .build();
        try {
            protocol.write(writeRequest);
        } catch (Exception e) {
            throw new NacosRuntimeException(NacosException.SERVER_ERROR, e);
        }
    }
    
    @Override
    public boolean isAlive(String connectionId) {
        return grpcConnectionMap.contains(connectionId);
    }
    
    @Override
    public void createConnectionSync(String connectionId) {
        grpcConnectionMap.add(connectionId);
    }
    
    @Override
    public void destroyConnectionSync(String connectionId) {
        grpcConnectionMap.remove(connectionId);
    }
}
