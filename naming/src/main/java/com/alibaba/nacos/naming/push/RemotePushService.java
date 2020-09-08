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

import com.alibaba.nacos.api.naming.pojo.ServiceInfo;
import com.alibaba.nacos.api.naming.remote.request.NotifySubscriberRequest;
import com.alibaba.nacos.api.naming.utils.NamingUtils;
import com.alibaba.nacos.common.notify.Event;
import com.alibaba.nacos.common.notify.listener.SmartSubscriber;
import com.alibaba.nacos.common.utils.ConcurrentHashSet;
import com.alibaba.nacos.core.remote.RpcPushService;
import com.alibaba.nacos.naming.core.v2.client.manager.impl.ConnectionBasedClientManager;
import com.alibaba.nacos.naming.core.v2.event.service.ServiceEvent;
import com.alibaba.nacos.naming.core.v2.index.ClientServiceIndexesManager;
import com.alibaba.nacos.naming.core.v2.index.ServiceStorage;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import com.alibaba.nacos.naming.pojo.Subscriber;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Remote push services.
 *
 * @author xiweng.yy
 */
@Component
public class RemotePushService extends SmartSubscriber {
    
    private final RpcPushService notifier;
    
    private final ConnectionBasedClientManager clientManager;
    
    private final ClientServiceIndexesManager indexesManager;
    
    private final ServiceStorage serviceStorage;
    
    /**
     * ServiceKey --> actual Subscriber. The Subscriber may be only subscribe part of cluster of service.
     */
    private final ConcurrentMap<String, Set<Subscriber>> serviceSubscribesMap = new ConcurrentHashMap<>();
    
    private final ConcurrentMap<Subscriber, String> subscribeConnectionMap = new ConcurrentHashMap<>();
    
    public RemotePushService(RpcPushService notifier, ConnectionBasedClientManager clientManager,
            ClientServiceIndexesManager indexesManager, ServiceStorage serviceStorage) {
        this.notifier = notifier;
        this.clientManager = clientManager;
        this.indexesManager = indexesManager;
        this.serviceStorage = serviceStorage;
    }
    
    /**
     * Register subscribe For service.
     *
     * @param serviceKey   service key
     * @param subscriber   subscriber
     * @param connectionId connection Id of subscriber
     */
    public void registerSubscribeForService(String serviceKey, Subscriber subscriber, String connectionId) {
        if (!serviceSubscribesMap.containsKey(serviceKey)) {
            serviceSubscribesMap.put(serviceKey, new ConcurrentHashSet<>());
        }
        serviceSubscribesMap.get(serviceKey).add(subscriber);
        subscribeConnectionMap.put(subscriber, connectionId);
    }
    
    /**
     * Remove subscribe For service.
     *
     * @param serviceKey service key
     * @param subscriber subscriber
     */
    public void removeSubscribeForService(String serviceKey, Subscriber subscriber) {
        if (!serviceSubscribesMap.containsKey(serviceKey)) {
            return;
        }
        serviceSubscribesMap.get(serviceKey).remove(subscriber);
        subscribeConnectionMap.remove(subscriber);
    }
    
    /**
     * Remove All subscribe for service.
     *
     * @param serviceKey service key
     */
    public void removeAllSubscribeForService(String serviceKey) {
        Set<Subscriber> subscribers = serviceSubscribesMap.remove(serviceKey);
        if (null != subscribers) {
            for (Subscriber each : subscribers) {
                subscribeConnectionMap.remove(each);
            }
        }
    }
    
    public Set<Subscriber> getSubscribes(String namespaceId, String serviceName) {
        String serviceNameWithoutGroup = NamingUtils.getServiceName(serviceName);
        String groupName = NamingUtils.getGroupName(serviceName);
        Service service = Service.newService(namespaceId, groupName, serviceNameWithoutGroup, true);
        return getSubscribes(service);
    }
    
    public Set<Subscriber> getSubscribes(Service service) {
        Set<Subscriber> result = new HashSet<>();
        for (String each : indexesManager.getAllClientsRegisteredService(service)) {
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
        // TODO delay & merge push task, and dispatch push task to execute async
        ServiceEvent.ServiceChangedEvent serviceChangedEvent = (ServiceEvent.ServiceChangedEvent) event;
        com.alibaba.nacos.naming.core.v2.pojo.Service service = serviceChangedEvent.getService();
        ServiceInfo serviceInfo = serviceStorage.getPushData(service);
        for (String each : indexesManager.getAllClientsSubscribeService(service)) {
            notifier.pushWithoutAck(each, NotifySubscriberRequest.buildSuccessResponse(serviceInfo));
        }
    }
}
