/*
 * Copyright 1999-2020 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.naming.core.v2.metadata;

import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.consistency.DataOperation;
import com.alibaba.nacos.consistency.SerializeFactory;
import com.alibaba.nacos.consistency.Serializer;
import com.alibaba.nacos.consistency.cp.RequestProcessor4CP;
import com.alibaba.nacos.consistency.entity.ReadRequest;
import com.alibaba.nacos.consistency.entity.Response;
import com.alibaba.nacos.consistency.entity.WriteRequest;
import com.alibaba.nacos.consistency.snapshot.SnapshotOperation;
import com.alibaba.nacos.core.distributed.ProtocolManager;
import com.alibaba.nacos.naming.core.v2.event.service.ServiceEvent;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import com.alibaba.nacos.naming.constants.Constants;
import org.apache.commons.lang3.reflect.TypeUtils;
import org.springframework.stereotype.Component;

import java.lang.reflect.Type;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Instance metadata processor.
 *
 * @author xiweng.yy
 */
@Component
public class InstanceMetadataProcessor extends RequestProcessor4CP {
    
    private final NamingMetadataManager namingMetadataManager;
    
    private final Serializer serializer;
    
    private final Type processType;
    
    private final ReentrantReadWriteLock lock;
    
    private final ReentrantReadWriteLock.ReadLock readLock;
    
    @SuppressWarnings("unchecked")
    public InstanceMetadataProcessor(NamingMetadataManager namingMetadataManager, ProtocolManager protocolManager) {
        this.namingMetadataManager = namingMetadataManager;
        this.serializer = SerializeFactory.getDefault();
        this.processType = TypeUtils.parameterize(MetadataOperation.class, InstanceMetadata.class);
        this.lock = new ReentrantReadWriteLock();
        this.readLock = lock.readLock();
        protocolManager.getCpProtocol().addRequestProcessors(Collections.singletonList(this));
    }
    
    @Override
    public List<SnapshotOperation> loadSnapshotOperate() {
        return Collections.singletonList(new InstanceMetadataSnapshotOperation(namingMetadataManager, lock));
    }
    
    @Override
    public Response onRequest(ReadRequest request) {
        return null;
    }
    
    @Override
    public Response onApply(WriteRequest request) {
        MetadataOperation<InstanceMetadata> op = serializer.deserialize(request.getData().toByteArray(), processType);
        readLock.lock();
        try {
            switch (DataOperation.valueOf(request.getOperation())) {
                case ADD:
                case CHANGE:
                    updateInstanceMetadata(op);
                    break;
                case DELETE:
                    deleteInstanceMetadata(op);
                    break;
                default:
                    return Response.newBuilder().setSuccess(false)
                            .setErrMsg("Unsupported operation " + request.getOperation()).build();
            }
            return Response.newBuilder().setSuccess(true).build();
        } catch (Exception e) {
            return Response.newBuilder().setSuccess(false).setErrMsg(e.getMessage()).build();
        } finally {
            readLock.unlock();
        }
    }
    
    private void updateInstanceMetadata(MetadataOperation<InstanceMetadata> op) {
        Service service = Service.newService(op.getNamespace(), op.getGroup(), op.getServiceName());
        namingMetadataManager.updateInstanceMetadata(service, op.getTag(), op.getMetadata());
        NotifyCenter.publishEvent(new ServiceEvent.ServiceChangedEvent(service, true));
    }
    
    private void deleteInstanceMetadata(MetadataOperation<InstanceMetadata> op) {
        Service service = Service.newService(op.getNamespace(), op.getGroup(), op.getServiceName());
        namingMetadataManager.removeInstanceMetadata(service, op.getTag());
    }
    
    @Override
    public String group() {
        return Constants.INSTANCE_METADATA;
    }
}
