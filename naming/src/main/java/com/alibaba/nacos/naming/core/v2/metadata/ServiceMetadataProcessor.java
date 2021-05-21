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

import com.alibaba.nacos.consistency.DataOperation;
import com.alibaba.nacos.consistency.SerializeFactory;
import com.alibaba.nacos.consistency.Serializer;
import com.alibaba.nacos.consistency.cp.RequestProcessor4CP;
import com.alibaba.nacos.consistency.entity.ReadRequest;
import com.alibaba.nacos.consistency.entity.Response;
import com.alibaba.nacos.consistency.entity.WriteRequest;
import com.alibaba.nacos.consistency.snapshot.SnapshotOperation;
import com.alibaba.nacos.core.distributed.ProtocolManager;
import com.alibaba.nacos.core.utils.Loggers;
import com.alibaba.nacos.naming.core.v2.ServiceManager;
import com.alibaba.nacos.naming.core.v2.index.ServiceStorage;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import com.alibaba.nacos.naming.core.v2.upgrade.doublewrite.delay.DoubleWriteEventListener;
import com.alibaba.nacos.naming.constants.Constants;
import com.alibaba.nacos.sys.utils.ApplicationUtils;
import org.apache.commons.lang3.reflect.TypeUtils;
import org.springframework.stereotype.Component;

import java.lang.reflect.Type;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Service metadata processor.
 *
 * @author xiweng.yy
 */
@Component
public class ServiceMetadataProcessor extends RequestProcessor4CP {
    
    private final NamingMetadataManager namingMetadataManager;
    
    private final ServiceStorage serviceStorage;
    
    private final Serializer serializer;
    
    private final Type processType;
    
    private final ReentrantReadWriteLock lock;
    
    private final ReentrantReadWriteLock.ReadLock readLock;
    
    @SuppressWarnings("unchecked")
    public ServiceMetadataProcessor(NamingMetadataManager namingMetadataManager, ProtocolManager protocolManager,
            ServiceStorage serviceStorage) {
        this.namingMetadataManager = namingMetadataManager;
        this.serviceStorage = serviceStorage;
        this.serializer = SerializeFactory.getDefault();
        this.processType = TypeUtils.parameterize(MetadataOperation.class, ServiceMetadata.class);
        this.lock = new ReentrantReadWriteLock();
        this.readLock = lock.readLock();
        protocolManager.getCpProtocol().addRequestProcessors(Collections.singletonList(this));
    }
    
    @Override
    public List<SnapshotOperation> loadSnapshotOperate() {
        return Collections.singletonList(new ServiceMetadataSnapshotOperation(namingMetadataManager, lock));
    }
    
    @Override
    public Response onRequest(ReadRequest request) {
        return null;
    }
    
    @Override
    public Response onApply(WriteRequest request) {
        MetadataOperation<ServiceMetadata> op = serializer.deserialize(request.getData().toByteArray(), processType);
        readLock.lock();
        try {
            switch (DataOperation.valueOf(request.getOperation())) {
                case ADD:
                    addClusterMetadataToService(op);
                    break;
                case CHANGE:
                    updateServiceMetadata(op);
                    break;
                case DELETE:
                    deleteServiceMetadata(op);
                    break;
                default:
                    return Response.newBuilder().setSuccess(false)
                            .setErrMsg("Unsupported operation " + request.getOperation()).build();
            }
            return Response.newBuilder().setSuccess(true).build();
        } catch (Exception e) {
            Loggers.RAFT.error("apply service metadata error: ", e);
            return Response.newBuilder().setSuccess(false).setErrMsg(e.getMessage()).build();
        } finally {
            readLock.unlock();
        }
    }
    
    private void addClusterMetadataToService(MetadataOperation<ServiceMetadata> op) {
        Service service = Service
                .newService(op.getNamespace(), op.getGroup(), op.getServiceName(), op.getMetadata().isEphemeral());
        Optional<ServiceMetadata> currentMetadata = namingMetadataManager.getServiceMetadata(service);
        if (currentMetadata.isPresent()) {
            currentMetadata.get().getClusters().putAll(op.getMetadata().getClusters());
        } else {
            Service singleton = ServiceManager.getInstance().getSingleton(service);
            namingMetadataManager.updateServiceMetadata(singleton, op.getMetadata());
        }
        doubleWriteMetadata(service, false);
    }
    
    private void updateServiceMetadata(MetadataOperation<ServiceMetadata> op) {
        Service service = Service
                .newService(op.getNamespace(), op.getGroup(), op.getServiceName(), op.getMetadata().isEphemeral());
        Optional<ServiceMetadata> currentMetadata = namingMetadataManager.getServiceMetadata(service);
        if (currentMetadata.isPresent()) {
            ServiceMetadata newMetadata = mergeMetadata(currentMetadata.get(), op.getMetadata());
            Service singleton = ServiceManager.getInstance().getSingleton(service);
            namingMetadataManager.updateServiceMetadata(singleton, newMetadata);
        } else {
            Service singleton = ServiceManager.getInstance().getSingleton(service);
            namingMetadataManager.updateServiceMetadata(singleton, op.getMetadata());
        }
        doubleWriteMetadata(service, false);
    }
    
    /**
     * Only for downgrade to v1.x.
     *
     * @param service double write service
     * @param remove  is removing service of v2
     * @deprecated will remove in v2.1.x
     */
    private void doubleWriteMetadata(Service service, boolean remove) {
        ApplicationUtils.getBean(DoubleWriteEventListener.class).doubleWriteMetadataToV1(service, remove);
    }
    
    /**
     * Do not modified old metadata directly to avoid read half status.
     *
     * <p>Ephemeral variable should only use the value the metadata create.
     *
     * @param oldMetadata old metadata
     * @param newMetadata new metadata
     * @return merged metadata
     */
    private ServiceMetadata mergeMetadata(ServiceMetadata oldMetadata, ServiceMetadata newMetadata) {
        ServiceMetadata result = new ServiceMetadata();
        result.setEphemeral(oldMetadata.isEphemeral());
        result.setClusters(oldMetadata.getClusters());
        result.setProtectThreshold(newMetadata.getProtectThreshold());
        result.setSelector(newMetadata.getSelector());
        result.setExtendData(newMetadata.getExtendData());
        return result;
    }
    
    private void deleteServiceMetadata(MetadataOperation<ServiceMetadata> op) {
        Service service = Service.newService(op.getNamespace(), op.getGroup(), op.getServiceName());
        namingMetadataManager.removeServiceMetadata(service);
        Service removed = ServiceManager.getInstance().removeSingleton(service);
        if (removed != null) {
            service = removed;
        }
        serviceStorage.removeData(service);
        doubleWriteMetadata(service, true);
    }
    
    @Override
    public String group() {
        return Constants.SERVICE_METADATA;
    }
}
