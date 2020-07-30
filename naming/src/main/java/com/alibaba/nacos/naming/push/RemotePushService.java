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
import com.alibaba.nacos.api.naming.remote.response.NotifySubscriberResponse;
import com.alibaba.nacos.common.utils.ConcurrentHashSet;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.core.remote.RpcPushService;
import com.alibaba.nacos.naming.core.Service;
import com.alibaba.nacos.naming.core.ServiceInfoGenerator;
import com.alibaba.nacos.naming.misc.UtilsAndCommons;
import com.alibaba.nacos.naming.pojo.Subscriber;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Remote push services.
 *
 * @author xiweng.yy
 */
@Component
public class RemotePushService implements ApplicationListener<ServiceChangeEvent> {
    
    private final ServiceInfoGenerator serviceInfoGenerator;
    
    private final RpcPushService notifier;
    
    /**
     * ServiceKey --> actual Subscriber. The Subscriber may be only subscribe part of cluster of service.
     */
    private final ConcurrentMap<String, Set<Subscriber>> serviceSubscribesMap = new ConcurrentHashMap<>();
    
    private final ConcurrentMap<Subscriber, String> subscribeConnectionMap = new ConcurrentHashMap<>();
    
    public RemotePushService(ServiceInfoGenerator serviceInfoGenerator, RpcPushService notifier) {
        this.serviceInfoGenerator = serviceInfoGenerator;
        this.notifier = notifier;
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
        return getSubscribes(UtilsAndCommons.assembleFullServiceName(namespaceId, serviceName));
    }
    
    public Set<Subscriber> getSubscribes(String serviceKey) {
        return serviceSubscribesMap.getOrDefault(serviceKey, new HashSet<>());
    }
    
    @Override
    public void onApplicationEvent(ServiceChangeEvent serviceChangeEvent) {
        Service service = serviceChangeEvent.getService();
        String serviceKey = UtilsAndCommons.assembleFullServiceName(service.getNamespaceId(), service.getName());
        ServiceInfo serviceInfo = serviceInfoGenerator
                .generateServiceInfo(service, StringUtils.EMPTY, false, StringUtils.EMPTY);
        for (Subscriber each : serviceSubscribesMap.getOrDefault(serviceKey, new HashSet<>())) {
            notifier.pushWithoutAck(subscribeConnectionMap.get(each),
                    NotifySubscriberResponse.buildSuccessResponse(serviceInfo));
        }
    }
}
