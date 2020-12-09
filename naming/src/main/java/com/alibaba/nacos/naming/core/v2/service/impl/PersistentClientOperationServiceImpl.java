/*
 *  Copyright 1999-2018 Alibaba Group Holding Ltd.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.alibaba.nacos.naming.core.v2.service.impl;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.runtime.NacosRuntimeException;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.consistency.DataOperation;
import com.alibaba.nacos.consistency.SerializeFactory;
import com.alibaba.nacos.consistency.Serializer;
import com.alibaba.nacos.consistency.cp.CPProtocol;
import com.alibaba.nacos.consistency.cp.RequestProcessor4CP;
import com.alibaba.nacos.consistency.entity.ReadRequest;
import com.alibaba.nacos.consistency.entity.Response;
import com.alibaba.nacos.consistency.entity.WriteRequest;
import com.alibaba.nacos.consistency.snapshot.SnapshotOperation;
import com.alibaba.nacos.core.distributed.ProtocolManager;
import com.alibaba.nacos.naming.core.v2.ServiceManager;
import com.alibaba.nacos.naming.core.v2.client.Client;
import com.alibaba.nacos.naming.core.v2.client.manager.ClientManagerDelegate;
import com.alibaba.nacos.naming.core.v2.client.manager.impl.IpPortBasedClientManager;
import com.alibaba.nacos.naming.core.v2.event.client.ClientOperationEvent;
import com.alibaba.nacos.naming.core.v2.pojo.InstancePublishInfo;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import com.alibaba.nacos.naming.core.v2.service.ClientOperationService;
import com.alibaba.nacos.naming.utils.Constants;
import com.google.protobuf.ByteString;

import java.util.Collections;
import java.util.List;

/**
 * Operation service for persistent clients and services. only for v2 For persistent instances, clientId must be in the
 * format of host:port.
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public class PersistentClientOperationServiceImpl extends RequestProcessor4CP implements ClientOperationService {
    
    private final IpPortBasedClientManager clientManager;
    
    private final Serializer serializer = SerializeFactory.getDefault();
    
    private final CPProtocol protocol;
    
    public PersistentClientOperationServiceImpl(final ClientManagerDelegate clientManager) {
        this.clientManager = clientManager.getIpPortBasedClientManager();
        this.protocol = ProtocolManager.getCpProtocol();
        this.protocol.addRequestProcessors(Collections.singletonList(this));
    }
    
    @Override
    public void registerInstance(Service service, Instance instance, String clientId) {
        final PersistentInstanceRequest request = new PersistentInstanceRequest();
        request.setService(service);
        request.setInstance(instance);
        request.setClientID(clientId);
        try {
            protocol.write(WriteRequest.newBuilder().setGroup(group())
                    .setData(ByteString.copyFrom(serializer.serialize(request)))
                    .setOperation(DataOperation.DELETE.name()).build());
        } catch (Exception e) {
            throw new NacosRuntimeException(NacosException.SERVER_ERROR, e);
        }
    }
    
    @Override
    public void deregisterInstance(Service service, Instance instance, String clientId) {
        final PersistentInstanceRequest request = new PersistentInstanceRequest();
        request.setService(service);
        request.setInstance(instance);
        request.setClientID(clientId);
        try {
            protocol.write(WriteRequest.newBuilder().setGroup(group())
                    .setData(ByteString.copyFrom(serializer.serialize(request)))
                    .setOperation(DataOperation.DELETE.name()).build());
        } catch (Exception e) {
            throw new NacosRuntimeException(NacosException.SERVER_ERROR, e);
        }
    }
    
    @Override
    public Response onRequest(ReadRequest request) {
        return null;
    }
    
    @Override
    public Response onApply(WriteRequest request) {
        final PersistentInstanceRequest instanceRequest = serializer.deserialize(request.getData().toByteArray());
        final DataOperation operation = DataOperation.valueOf(request.getOperation());
        switch (operation) {
            case ADD:
                onInstanceRegister(instanceRequest.service, instanceRequest.instance, instanceRequest.getClientID());
                break;
            case DELETE:
                onInstanceDeregister(instanceRequest.service, instanceRequest.getClientID());
                break;
            default:
                return Response.newBuilder().setSuccess(false).setErrMsg("unsupport operation : " + operation).build();
        }
        return Response.newBuilder().setSuccess(true).build();
    }
    
    private void onInstanceRegister(Service service, Instance instance, String clientId) {
        Service singleton = ServiceManager.getInstance().getSingleton(service);
        Client client = clientManager.getClient(clientId);
        InstancePublishInfo instancePublishInfo = getPublishInfo(instance);
        client.addServiceInstance(singleton, instancePublishInfo);
        client.setLastUpdatedTime();
        NotifyCenter.publishEvent(new ClientOperationEvent.ClientRegisterServiceEvent(singleton, clientId));
    }
    
    private void onInstanceDeregister(Service service, String clientId) {
        Service singleton = ServiceManager.getInstance().getSingleton(service);
        Client client = clientManager.getClient(clientId);
        client.removeServiceInstance(singleton);
        client.setLastUpdatedTime();
        NotifyCenter.publishEvent(new ClientOperationEvent.ClientDeregisterServiceEvent(singleton, clientId));
    }
    
    @Override
    public List<SnapshotOperation> loadSnapshotOperate() {
        return super.loadSnapshotOperate();
    }
    
    @Override
    public String group() {
        return Constants.NAMING_PERSISTENT_SERVICE_GROUP_V2;
    }
    
    protected static class PersistentInstanceRequest {
        
        private Service service;
        
        private Instance instance;
        
        private String clientID;
        
        public Service getService() {
            return service;
        }
        
        public void setService(Service service) {
            this.service = service;
        }
        
        public Instance getInstance() {
            return instance;
        }
        
        public void setInstance(Instance instance) {
            this.instance = instance;
        }
        
        public String getClientID() {
            return clientID;
        }
        
        public void setClientID(String clientID) {
            this.clientID = clientID;
        }
        
    }
    
}
