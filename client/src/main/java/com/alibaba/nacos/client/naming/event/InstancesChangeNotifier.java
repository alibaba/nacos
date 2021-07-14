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

package com.alibaba.nacos.client.naming.event;

import com.alibaba.nacos.api.naming.listener.AbstractEventListener;
import com.alibaba.nacos.api.naming.listener.EventListener;
import com.alibaba.nacos.api.naming.listener.NamingEvent;
import com.alibaba.nacos.api.naming.pojo.ServiceInfo;
import com.alibaba.nacos.api.naming.utils.NamingUtils;
import com.alibaba.nacos.common.notify.Event;
import com.alibaba.nacos.common.notify.listener.Subscriber;
import com.alibaba.nacos.common.utils.CollectionUtils;
import com.alibaba.nacos.common.utils.ConcurrentHashSet;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A subscriber to notify eventListener callback.
 *
 * @author horizonzy
 * @since 1.4.1
 */
public class InstancesChangeNotifier extends Subscriber<InstancesChangeEvent> {
    
    private final Map<String, ConcurrentHashSet<EventListener>> listenerMap = new ConcurrentHashMap<String, ConcurrentHashSet<EventListener>>();
    
    private final Object lock = new Object();
    
    /**
     * register listener.
     *
     * @param groupName   group name
     * @param serviceName serviceName
     * @param clusters    clusters, concat by ','. such as 'xxx,yyy'
     * @param listener    custom listener
     */
    public void registerListener(String groupName, String serviceName, String clusters, EventListener listener) {
        String key = ServiceInfo.getKey(NamingUtils.getGroupedName(serviceName, groupName), clusters);
        ConcurrentHashSet<EventListener> eventListeners = listenerMap.get(key);
        if (eventListeners == null) {
            synchronized (lock) {
                eventListeners = listenerMap.get(key);
                if (eventListeners == null) {
                    eventListeners = new ConcurrentHashSet<EventListener>();
                    listenerMap.put(key, eventListeners);
                }
            }
        }
        eventListeners.add(listener);
    }
    
    /**
     * deregister listener.
     *
     * @param groupName   group name
     * @param serviceName serviceName
     * @param clusters    clusters, concat by ','. such as 'xxx,yyy'
     * @param listener    custom listener
     */
    public void deregisterListener(String groupName, String serviceName, String clusters, EventListener listener) {
        String key = ServiceInfo.getKey(NamingUtils.getGroupedName(serviceName, groupName), clusters);
        ConcurrentHashSet<EventListener> eventListeners = listenerMap.get(key);
        if (eventListeners == null) {
            return;
        }
        eventListeners.remove(listener);
        if (CollectionUtils.isEmpty(eventListeners)) {
            listenerMap.remove(key);
        }
    }
    
    /**
     * check serviceName,clusters is subscribed.
     *
     * @param groupName   group name
     * @param serviceName serviceName
     * @param clusters    clusters, concat by ','. such as 'xxx,yyy'
     * @return is serviceName,clusters subscribed
     */
    public boolean isSubscribed(String groupName, String serviceName, String clusters) {
        String key = ServiceInfo.getKey(NamingUtils.getGroupedName(serviceName, groupName), clusters);
        ConcurrentHashSet<EventListener> eventListeners = listenerMap.get(key);
        return CollectionUtils.isNotEmpty(eventListeners);
    }
    
    public List<ServiceInfo> getSubscribeServices() {
        List<ServiceInfo> serviceInfos = new ArrayList<ServiceInfo>();
        for (String key : listenerMap.keySet()) {
            serviceInfos.add(ServiceInfo.fromKey(key));
        }
        return serviceInfos;
    }
    
    @Override
    public void onEvent(InstancesChangeEvent event) {
        String key = ServiceInfo
                .getKey(NamingUtils.getGroupedName(event.getServiceName(), event.getGroupName()), event.getClusters());
        ConcurrentHashSet<EventListener> eventListeners = listenerMap.get(key);
        if (CollectionUtils.isEmpty(eventListeners)) {
            return;
        }
        for (final EventListener listener : eventListeners) {
            final com.alibaba.nacos.api.naming.listener.Event namingEvent = transferToNamingEvent(event);
            if (listener instanceof AbstractEventListener && ((AbstractEventListener) listener).getExecutor() != null) {
                ((AbstractEventListener) listener).getExecutor().execute(() -> listener.onEvent(namingEvent));
            } else {
                listener.onEvent(namingEvent);
            }
        }
    }
    
    private com.alibaba.nacos.api.naming.listener.Event transferToNamingEvent(
            InstancesChangeEvent instancesChangeEvent) {
        return new NamingEvent(instancesChangeEvent.getServiceName(), instancesChangeEvent.getGroupName(),
                instancesChangeEvent.getClusters(), instancesChangeEvent.getHosts());
    }
    
    @Override
    public Class<? extends Event> subscribeType() {
        return InstancesChangeEvent.class;
    }
    
}
