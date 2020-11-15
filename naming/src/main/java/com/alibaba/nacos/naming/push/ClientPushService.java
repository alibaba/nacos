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

package com.alibaba.nacos.naming.push;

import com.alibaba.nacos.api.naming.utils.NamingUtils;
import com.alibaba.nacos.common.notify.Event;
import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.common.notify.listener.SmartSubscriber;
import com.alibaba.nacos.naming.core.v2.client.manager.ClientManager;
import com.alibaba.nacos.naming.core.v2.client.manager.ClientManagerDelegate;
import com.alibaba.nacos.naming.core.v2.event.service.ServiceEvent;
import com.alibaba.nacos.naming.core.v2.index.ClientServiceIndexesManager;
import com.alibaba.nacos.naming.core.v2.index.ServiceStorage;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import com.alibaba.nacos.naming.pojo.Subscriber;
import com.alibaba.nacos.naming.push.v2.PushExecuteServiceDelegate;
import com.alibaba.nacos.naming.push.v2.task.PushDelayTask;
import com.alibaba.nacos.naming.push.v2.task.PushDelayTaskExecuteEngine;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Remote push services.
 *
 * @author xiweng.yy
 */
@Component
public class ClientPushService extends SmartSubscriber {
    
    private final ClientManager clientManager;
    
    private final ClientServiceIndexesManager indexesManager;
    
    private final PushDelayTaskExecuteEngine delayTaskEngine;
    
    public ClientPushService(ClientManagerDelegate clientManager, ClientServiceIndexesManager indexesManager,
            ServiceStorage serviceStorage, PushExecuteServiceDelegate pushExecuteService) {
        this.clientManager = clientManager;
        this.indexesManager = indexesManager;
        this.delayTaskEngine = new PushDelayTaskExecuteEngine(clientManager, indexesManager, serviceStorage,
                pushExecuteService);
        NotifyCenter.registerSubscriber(this);
        
    }
    
    public Set<Subscriber> getSubscribes(String namespaceId, String serviceName) {
        String serviceNameWithoutGroup = NamingUtils.getServiceName(serviceName);
        String groupName = NamingUtils.getGroupName(serviceName);
        Service service = Service.newService(namespaceId, groupName, serviceNameWithoutGroup);
        return getSubscribes(service);
    }
    
    public Set<Subscriber> getSubscribes(Service service) {
        Set<Subscriber> result = new HashSet<>();
        for (String each : indexesManager.getAllClientsSubscribeService(service)) {
            result.add(clientManager.getClient(each).getSubscriber(service));
        }
        return result;
    }
    
    @Override
    public List<Class<? extends Event>> subscribeTypes() {
        return Collections.singletonList(ServiceEvent.ServiceChangedEvent.class);
    }
    
    @Override
    public void onEvent(Event event) {
        ServiceEvent.ServiceChangedEvent serviceChangedEvent = (ServiceEvent.ServiceChangedEvent) event;
        Service service = serviceChangedEvent.getService();
        delayTaskEngine.addTask(service, new PushDelayTask(service, 500L));
    }
}
