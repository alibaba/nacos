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

import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.common.utils.ByteUtils;
import com.alibaba.nacos.naming.consistency.persistent.impl.BatchWriteRequest;
import com.alibaba.nacos.naming.consistency.persistent.impl.Op;
import com.alibaba.nacos.naming.consistency.persistent.impl.PersistentServiceOperator;
import com.alibaba.nacos.naming.consistency.persistent.impl.PersistentServiceProcessor;
import com.alibaba.nacos.naming.core.v2.ServiceManager;
import com.alibaba.nacos.naming.core.v2.client.Client;
import com.alibaba.nacos.naming.core.v2.client.manager.ClientManagerDelegate;
import com.alibaba.nacos.naming.core.v2.client.manager.impl.IpPortBasedClientManager;
import com.alibaba.nacos.naming.core.v2.event.client.ClientOperationEvent;
import com.alibaba.nacos.naming.core.v2.pojo.InstancePublishInfo;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import com.alibaba.nacos.naming.core.v2.service.ClientOperationService;
import com.alibaba.nacos.naming.pojo.Subscriber;

/**
 * Operation service for persistent clients and services. only for v2 For persistent instances, clientId must be in the
 * format of host:port.
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public class PersistentClientOperationServiceImpl extends PersistentServiceOperator implements ClientOperationService {
    
    private static final String SERVICE_KEY = "service";
    
    private static final String INSTANCE_KEY = "instance";
    
    private static final String CLIENT_ID_KEY = "client_id";
    
    private final IpPortBasedClientManager clientManager;
    
    public PersistentClientOperationServiceImpl(final PersistentServiceProcessor processor,
            final ClientManagerDelegate clientManager) {
        this.clientManager = clientManager.getIpPortBasedClientManager();
        processor.registerOperator(this);
    }
    
    @Override
    public void registerInstance(Service service, Instance instance, String clientId) {
        final BatchWriteRequest bwRequest = new BatchWriteRequest();
        bwRequest.append(ByteUtils.toBytes(SERVICE_KEY), Service.easySerialize(service));
        bwRequest.append(ByteUtils.toBytes(INSTANCE_KEY), serializer.serialize(instance));
        bwRequest.append(ByteUtils.toBytes(CLIENT_ID_KEY), ByteUtils.toBytes(clientId));
        try {
            write(instance.getSignKey(), bwRequest, Op.Write.getDesc());
        } catch (Exception ignore) {
            // TODO ignore this exception temporary
        }
    }
    
    @Override
    public void deregisterInstance(Service service, Instance instance, String clientId) {
        final BatchWriteRequest bwRequest = new BatchWriteRequest();
        bwRequest.append(ByteUtils.toBytes(SERVICE_KEY), Service.easySerialize(service));
        bwRequest.append(ByteUtils.toBytes(INSTANCE_KEY), serializer.serialize(instance));
        bwRequest.append(ByteUtils.toBytes(CLIENT_ID_KEY), ByteUtils.toBytes(clientId));
        try {
            write(instance.getSignKey(), bwRequest, Op.Delete.getDesc());
        } catch (Exception ignore) {
            // TODO ignore this exception temporary
        }
    }
    
    @Override
    public void subscribeService(Service service, Subscriber subscriber, String clientId) {
        // don't need to implement
    }
    
    @Override
    public void unsubscribeService(Service service, Subscriber subscriber, String clientId) {
        // don't need to implement
    }
    
    @Override
    protected void onApply(Op op, BatchWriteRequest request) {
        request.forEachWithCompleteSign(new BatchWriteRequest.ForEach() {
            Service service;
            
            Instance instance;
            
            String clientId;
            
            @Override
            public void accept(byte[] key, byte[] value) {
                String s = ByteUtils.toString(key);
                switch (s) {
                    case SERVICE_KEY:
                        service = Service.easyDeserialize(value);
                        break;
                    case INSTANCE_KEY:
                        instance = serializer.deserialize(value);
                        break;
                    case CLIENT_ID_KEY:
                        clientId = ByteUtils.toString(value);
                        break;
                    default:
                }
            }
            
            @Override
            public void finished() {
                switch (op) {
                    case Write:
                        onInstanceRegister(service, instance, clientId);
                        break;
                    case Delete:
                        onInstanceDeregister(service, clientId);
                        break;
                    default:
                }
            }
        });
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
    protected String prefix() {
        return "--v2--";
    }
    
}
