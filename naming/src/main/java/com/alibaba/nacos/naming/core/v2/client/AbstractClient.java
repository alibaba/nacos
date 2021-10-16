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

package com.alibaba.nacos.naming.core.v2.client;

import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.naming.core.v2.event.client.ClientEvent;
import com.alibaba.nacos.naming.core.v2.pojo.InstancePublishInfo;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import com.alibaba.nacos.naming.misc.Loggers;
import com.alibaba.nacos.naming.monitor.MetricsMonitor;
import com.alibaba.nacos.naming.pojo.Subscriber;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Abstract implementation of {@code Client}.
 *
 * @author xiweng.yy
 */
public abstract class AbstractClient implements Client {

    /**
     * 缓存了服务和实例的映射关系(针对同一个连接对象)
     * 注意:客户端的同一个连接,禁止在同一个服务下,注册多个实例,原因是publishers属性中[服务-实例]是一对一的关系
     * */
    protected final ConcurrentHashMap<Service, InstancePublishInfo> publishers = new ConcurrentHashMap<>(16, 0.75f, 1);

    /**
     * 缓存了服务和该服务订阅者的映射关系(针对同一个连接对象)
     * 注意:客户端的同一个连接,禁止在同一个服务下,订阅不同的其他服务,原因是subscribers属性中[服务-订阅]是一对一的关系
     * */
    protected final ConcurrentHashMap<Service, Subscriber> subscribers = new ConcurrentHashMap<>(16, 0.75f, 1);
    
    protected volatile long lastUpdatedTime;
    
    public AbstractClient() {
        lastUpdatedTime = System.currentTimeMillis();
    }
    
    @Override
    public void setLastUpdatedTime() {
        this.lastUpdatedTime = System.currentTimeMillis();
    }
    
    @Override
    public long getLastUpdatedTime() {
        return lastUpdatedTime;
    }
    
    @Override
    public boolean addServiceInstance(Service service, InstancePublishInfo instancePublishInfo) {
        // publishers缓存了服务和实例的映射关系
        if (null == publishers.put(service, instancePublishInfo)) {
            MetricsMonitor.incrementInstanceCount();
        }
        // 发布实例变更事件
        NotifyCenter.publishEvent(new ClientEvent.ClientChangedEvent(this));
        Loggers.SRV_LOG.info("Client change for service {}, {}", service, getClientId());
        return true;
    }
    
    @Override
    public InstancePublishInfo removeServiceInstance(Service service) {
        InstancePublishInfo result = publishers.remove(service);
        if (null != result) {
            MetricsMonitor.decrementInstanceCount();
            NotifyCenter.publishEvent(new ClientEvent.ClientChangedEvent(this));
        }
        Loggers.SRV_LOG.info("Client remove for service {}, {}", service, getClientId());
        return result;
    }
    
    @Override
    public InstancePublishInfo getInstancePublishInfo(Service service) {
        return publishers.get(service);
    }
    
    @Override
    public Collection<Service> getAllPublishedService() {
        return publishers.keySet();
    }
    
    @Override
    public boolean addServiceSubscriber(Service service, Subscriber subscriber) {
        if (null == subscribers.put(service, subscriber)) {
            MetricsMonitor.incrementSubscribeCount();
        }
        return true;
    }
    
    @Override
    public boolean removeServiceSubscriber(Service service) {
        if (null != subscribers.remove(service)) {
            MetricsMonitor.decrementSubscribeCount();
        }
        return true;
    }
    
    @Override
    public Subscriber getSubscriber(Service service) {
        return subscribers.get(service);
    }
    
    @Override
    public Collection<Service> getAllSubscribeService() {
        return subscribers.keySet();
    }
    
    @Override
    public ClientSyncData generateSyncData() {
        List<String> namespaces = new LinkedList<>();
        List<String> groupNames = new LinkedList<>();
        List<String> serviceNames = new LinkedList<>();
        List<InstancePublishInfo> instances = new LinkedList<>();
        for (Map.Entry<Service, InstancePublishInfo> entry : publishers.entrySet()) {
            namespaces.add(entry.getKey().getNamespace());
            groupNames.add(entry.getKey().getGroup());
            serviceNames.add(entry.getKey().getName());
            instances.add(entry.getValue());
        }
        return new ClientSyncData(getClientId(), namespaces, groupNames, serviceNames, instances);
    }
    
    @Override
    public void release() {
        MetricsMonitor.getIpCountMonitor().addAndGet(-1 * publishers.size());
        MetricsMonitor.getSubscriberCount().addAndGet(-1 * subscribers.size());
    }
}
