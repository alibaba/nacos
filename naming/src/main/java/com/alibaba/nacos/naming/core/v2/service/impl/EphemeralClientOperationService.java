/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.naming.core.v2.service.impl;

import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.naming.core.v2.client.Client;
import com.alibaba.nacos.naming.core.v2.client.manager.impl.ConnectionBasedClientManager;
import com.alibaba.nacos.naming.core.v2.pojo.InstancePublishInfo;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import com.alibaba.nacos.naming.core.v2.service.ClientOperationService;
import com.alibaba.nacos.naming.pojo.Subscriber;

/**
 * Operation service for ephemeral clients and services.
 *
 * @author xiweng.yy
 */
public class EphemeralClientOperationService implements ClientOperationService {
    
    private final ConnectionBasedClientManager connectionBasedClientManager;
    
    public EphemeralClientOperationService(ConnectionBasedClientManager connectionBasedClientManager) {
        this.connectionBasedClientManager = connectionBasedClientManager;
    }
    
    @Override
    public void registerInstance(Service service, Instance instance, String clientId) {
        Client client = connectionBasedClientManager.getClient(clientId);
        InstancePublishInfo instancePublishInfo = getPublishInfo(instance);
        client.addServiceInstance(service, instancePublishInfo);
        // TODO send register service event
    }
    
    @Override
    public void deregisterInstance(Service service, Instance instance, String clientId) {
        Client client = connectionBasedClientManager.getClient(clientId);
        client.removeServiceInstance(service);
        // TODO send deregister service event
    }
    
    private InstancePublishInfo getPublishInfo(Instance instance) {
        InstancePublishInfo result = new InstancePublishInfo(instance.getIp(), instance.getPort());
        result.getExtendDatum().putAll(instance.getMetadata());
        result.getExtendDatum().put("cluster", instance.getClusterName());
        return result;
    }
    
    @Override
    public void subscribeService(Service service, Subscriber subscriber, String clientId) {
        Client client = connectionBasedClientManager.getClient(clientId);
        client.addServiceSubscriber(service, subscriber);
        // TODO send subscribe service event
    }
    
    @Override
    public void unsubscribeService(Service service, Subscriber subscriber, String clientId) {
        Client client = connectionBasedClientManager.getClient(clientId);
        client.removeServiceSubscriber(service);
        // TODO send unsubscribe service event
    }
}
