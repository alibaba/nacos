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

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.runtime.NacosRuntimeException;
import com.alibaba.nacos.consistency.DataOperation;
import com.alibaba.nacos.consistency.SerializeFactory;
import com.alibaba.nacos.consistency.Serializer;
import com.alibaba.nacos.consistency.cp.CPProtocol;
import com.alibaba.nacos.consistency.entity.WriteRequest;
import com.alibaba.nacos.consistency.entity.Response;
import com.alibaba.nacos.core.distributed.ProtocolManager;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import com.alibaba.nacos.naming.utils.Constants;
import com.google.protobuf.ByteString;
import org.springframework.stereotype.Component;

/**
 * Nacos naming metadata operate service.
 *
 * @author xiweng.yy
 */
@Component
public class NamingMetadataOperateService {
    
    private final CPProtocol cpProtocol;
    
    private final Serializer serializer;
    
    public NamingMetadataOperateService(ProtocolManager protocolManager) {
        this.cpProtocol = protocolManager.getCpProtocol();
        this.serializer = SerializeFactory.getSerializer("JSON");
    }
    
    /**
     * Update service metadata.
     *
     * @param service         service of metadata
     * @param serviceMetadata metadata
     */
    public void updateServiceMetadata(Service service, ServiceMetadata serviceMetadata) {
        MetadataOperation<ServiceMetadata> operation = buildMetadataOperation(service);
        operation.setMetadata(serviceMetadata);
        WriteRequest operationLog = WriteRequest.newBuilder().setGroup(Constants.SERVICE_METADATA)
                .setOperation(DataOperation.CHANGE.name()).setData(ByteString.copyFrom(serializer.serialize(operation)))
                .build();
        submitMetadataOperation(operationLog);
    }
    
    /**
     * Delete service metadata.
     *
     * @param service service of metadata
     */
    public void deleteServiceMetadata(Service service) {
        MetadataOperation<ServiceMetadata> operation = buildMetadataOperation(service);
        WriteRequest operationLog = WriteRequest.newBuilder().setGroup(Constants.SERVICE_METADATA)
                .setOperation(DataOperation.DELETE.name()).setData(ByteString.copyFrom(serializer.serialize(operation)))
                .build();
        submitMetadataOperation(operationLog);
    }
    
    /**
     * Update instance metadata.
     *
     * @param service          service of metadata
     * @param instanceId       instance Id
     * @param instanceMetadata metadata
     */
    public void updateInstanceMetadata(Service service, String instanceId, InstanceMetadata instanceMetadata) {
        MetadataOperation<InstanceMetadata> operation = buildMetadataOperation(service);
        operation.setTag(instanceId);
        operation.setMetadata(instanceMetadata);
        WriteRequest operationLog = WriteRequest.newBuilder().setGroup(Constants.INSTANCE_METADATA)
                .setOperation(DataOperation.CHANGE.name()).setData(ByteString.copyFrom(serializer.serialize(operation)))
                .build();
        submitMetadataOperation(operationLog);
    }
    
    /**
     * Delete instance metadata.
     *
     * @param service    service of metadata
     * @param instanceId instance Id
     */
    public void deleteInstanceMetadata(Service service, String instanceId) {
        MetadataOperation<InstanceMetadata> operation = buildMetadataOperation(service);
        operation.setTag(instanceId);
        WriteRequest operationLog = WriteRequest.newBuilder().setGroup(Constants.INSTANCE_METADATA)
                .setOperation(DataOperation.DELETE.name()).setData(ByteString.copyFrom(serializer.serialize(operation)))
                .build();
        submitMetadataOperation(operationLog);
    }
    
    private <T> MetadataOperation<T> buildMetadataOperation(Service service) {
        MetadataOperation<T> result = new MetadataOperation<>();
        result.setNamespace(service.getNamespace());
        result.setGroup(service.getGroup());
        result.setServiceName(service.getName());
        return result;
    }
    
    private void submitMetadataOperation(WriteRequest operationLog) {
        try {
            Response response = cpProtocol.submit(operationLog);
            if (!response.getSuccess()) {
                throw new NacosRuntimeException(NacosException.SERVER_ERROR,
                        "do metadata operation failed " + response.getErrMsg());
            }
        } catch (Exception e) {
            throw new NacosRuntimeException(NacosException.SERVER_ERROR, "do metadata operation failed", e);
        }
    }
}
