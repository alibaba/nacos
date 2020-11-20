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
import com.alibaba.nacos.core.distributed.ProtocolManager;
import com.alibaba.nacos.naming.core.v2.event.service.ServiceEvent;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import com.alibaba.nacos.naming.utils.Constants;
import com.google.protobuf.ByteString;
import org.apache.commons.lang3.reflect.TypeUtils;
import org.springframework.stereotype.Component;

import java.lang.reflect.Type;
import java.util.Collections;

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
    
    @SuppressWarnings("unchecked")
    public InstanceMetadataProcessor(NamingMetadataManager namingMetadataManager, ProtocolManager protocolManager) {
        this.namingMetadataManager = namingMetadataManager;
        this.serializer = SerializeFactory.getSerializer("JSON");
        this.processType = TypeUtils.parameterize(MetadataOperation.class, InstanceMetadata.class);
        protocolManager.getCpProtocol().addLogProcessors(Collections.singletonList(this));
    }
    
    @Override
    public Response onRequest(ReadRequest request) {
        return null;
    }
    
    @Override
    public Response onApply(WriteRequest request) {
        switch (DataOperation.valueOf(request.getOperation())) {
            case ADD:
            case CHANGE:
                updateInstanceMetadata(request.getData());
                break;
            case DELETE:
                deleteInstanceMetadata(request.getData());
                break;
            default:
                return Response.newBuilder().setSuccess(false).setErrMsg("Unsupported operation " + request.getOperation())
                        .build();
        }
        return Response.newBuilder().setSuccess(true).build();
    }
    
    private void updateInstanceMetadata(ByteString data) {
        MetadataOperation<InstanceMetadata> op = serializer.deserialize(data.toByteArray(), processType);
        Service service = Service.newService(op.getNamespace(), op.getGroup(), op.getServiceName());
        namingMetadataManager.updateInstanceMetadata(service, op.getTag(), op.getMetadata());
        NotifyCenter.publishEvent(new ServiceEvent.ServiceChangedEvent(service));
    }
    
    private void deleteInstanceMetadata(ByteString data) {
        MetadataOperation<InstanceMetadata> op = serializer.deserialize(data.toByteArray(), processType);
        Service service = Service.newService(op.getNamespace(), op.getGroup(), op.getServiceName());
        namingMetadataManager.removeInstanceMetadata(service, op.getTag());
        NotifyCenter.publishEvent(new ServiceEvent.ServiceChangedEvent(service));
    }
    
    @Override
    public String group() {
        return Constants.INSTANCE_METADATA;
    }
}
