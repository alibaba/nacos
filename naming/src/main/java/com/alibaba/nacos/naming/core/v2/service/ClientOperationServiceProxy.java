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

package com.alibaba.nacos.naming.core.v2.service;

import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.naming.core.v2.ServiceManager;
import com.alibaba.nacos.naming.core.v2.client.Client;
import com.alibaba.nacos.naming.core.v2.client.manager.ClientManager;
import com.alibaba.nacos.naming.core.v2.client.manager.ClientManagerDelegate;
import com.alibaba.nacos.naming.core.v2.client.manager.impl.NoConnectionClientManager;
import com.alibaba.nacos.naming.core.v2.event.client.ClientOperationEvent;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import com.alibaba.nacos.naming.core.v2.service.impl.EphemeralClientOperationServiceImpl;
import com.alibaba.nacos.naming.core.v2.service.impl.PersistentClientOperationServiceImpl;
import com.alibaba.nacos.naming.misc.Loggers;
import com.alibaba.nacos.naming.pojo.Subscriber;
import org.springframework.stereotype.Component;

/**
 * Implementation of external exposure.
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
@SuppressWarnings("PMD.ServiceOrDaoClassShouldEndWithImplRule")
@Component
public class ClientOperationServiceProxy implements ClientOperationService {
    
    private final ClientOperationService ephemeraClientOperationService;
    
    private final ClientOperationService persistentClientOperationService;
    
    private final ClientManager connectionClientManager;
    
    private final NoConnectionClientManager noConnectionClientManager;
    
    public ClientOperationServiceProxy(ClientManagerDelegate connectionClientManager,
            NoConnectionClientManager noConnectionClientManager) {
        this.connectionClientManager = connectionClientManager;
        this.noConnectionClientManager = noConnectionClientManager;
        this.ephemeraClientOperationService = new EphemeralClientOperationServiceImpl(connectionClientManager);
        this.persistentClientOperationService = new PersistentClientOperationServiceImpl(noConnectionClientManager);
    }
    
    @Override
    public void registerInstance(Service service, Instance instance, String clientId) {
        final ClientOperationService operationService = chooseClientOperationService(instance);
        operationService.registerInstance(service, instance, clientId);
    }
    
    @Override
    public void deregisterInstance(Service service, Instance instance, String clientId) {
        if (!ServiceManager.getInstance().containSingleton(service)) {
            Loggers.SRV_LOG.warn("remove instance from non-exist service: {}", service);
            return;
        }
        final ClientOperationService operationService = chooseClientOperationService(instance);
        operationService.deregisterInstance(service, instance, clientId);
    }
    
    @Override
    public void subscribeService(Service service, Subscriber subscriber, String clientId) {
        Service singleton = ServiceManager.getInstance().getSingleton(service);
        
        Client client = connectionClientManager.getClient(clientId);
        client.addServiceSubscriber(singleton, subscriber);
        client.setLastUpdatedTime();
    
        Client pClient = noConnectionClientManager.getClient(clientId);
        pClient.addServiceSubscriber(singleton, subscriber);
        
        NotifyCenter.publishEvent(new ClientOperationEvent.ClientSubscribeServiceEvent(singleton, clientId));
    }
    
    @Override
    public void unsubscribeService(Service service, Subscriber subscriber, String clientId) {
        Service singleton = ServiceManager.getInstance().getSingleton(service);
        
        Client eClient = connectionClientManager.getClient(clientId);
        eClient.removeServiceSubscriber(singleton);
        eClient.setLastUpdatedTime();
    
        Client pClient = noConnectionClientManager.getClient(clientId);
        pClient.removeServiceSubscriber(singleton);
    
        NotifyCenter.publishEvent(new ClientOperationEvent.ClientUnsubscribeServiceEvent(singleton, clientId));
    }
    
    private ClientOperationService chooseClientOperationService(final Instance instance) {
        return instance.isEphemeral() ? ephemeraClientOperationService : persistentClientOperationService;
    }
    
    private ClientManager chooseClientManager(final Instance instance) {
        return instance.isEphemeral() ? connectionClientManager : noConnectionClientManager;
    }
}
