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
import com.alibaba.nacos.naming.core.v2.pojo.BatchInstanceData;
import com.alibaba.nacos.naming.core.v2.pojo.BatchInstancePublishInfo;
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
    
    protected final ConcurrentHashMap<Service, InstancePublishInfo> publishers = new ConcurrentHashMap<>(16, 0.75f, 1);
    
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
        if (null == publishers.put(service, instancePublishInfo)) {
            if (instancePublishInfo instanceof BatchInstancePublishInfo) {
                MetricsMonitor.incrementIpCountWithBatchRegister(instancePublishInfo);
            } else {
                MetricsMonitor.incrementInstanceCount();
            }
        }
        NotifyCenter.publishEvent(new ClientEvent.ClientChangedEvent(this));
        Loggers.SRV_LOG.info("Client change for service {}, {}", service, getClientId());
        return true;
    }
    
    @Override
    public InstancePublishInfo removeServiceInstance(Service service) {
        InstancePublishInfo result = publishers.remove(service);
        if (null != result) {
            if (result instanceof BatchInstancePublishInfo) {
                MetricsMonitor.decrementIpCountWithBatchRegister(result);
            } else {
                MetricsMonitor.decrementInstanceCount();
            }
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
    
        List<String> batchNamespaces = new LinkedList<>();
        List<String> batchGroupNames = new LinkedList<>();
        List<String> batchServiceNames = new LinkedList<>();
        
        List<InstancePublishInfo> instances = new LinkedList<>();
        List<BatchInstancePublishInfo> batchInstancePublishInfos = new LinkedList<>();
        BatchInstanceData  batchInstanceData = new BatchInstanceData();
        for (Map.Entry<Service, InstancePublishInfo> entry : publishers.entrySet()) {
            InstancePublishInfo instancePublishInfo = entry.getValue();
            if (instancePublishInfo instanceof BatchInstancePublishInfo) {
                BatchInstancePublishInfo batchInstance = (BatchInstancePublishInfo) instancePublishInfo;
                batchInstancePublishInfos.add(batchInstance);
                buildBatchInstanceData(batchInstanceData, batchNamespaces, batchGroupNames, batchServiceNames, entry);
                batchInstanceData.setBatchInstancePublishInfos(batchInstancePublishInfos);
            } else {
                namespaces.add(entry.getKey().getNamespace());
                groupNames.add(entry.getKey().getGroup());
                serviceNames.add(entry.getKey().getName());
                instances.add(entry.getValue());
            }
        }
        return new ClientSyncData(getClientId(), namespaces, groupNames, serviceNames, instances, batchInstanceData);
    }
    
    private static BatchInstanceData buildBatchInstanceData(BatchInstanceData  batchInstanceData, List<String> batchNamespaces,
            List<String> batchGroupNames, List<String> batchServiceNames, Map.Entry<Service, InstancePublishInfo> entry) {
        batchNamespaces.add(entry.getKey().getNamespace());
        batchGroupNames.add(entry.getKey().getGroup());
        batchServiceNames.add(entry.getKey().getName());
        
        batchInstanceData.setNamespaces(batchNamespaces);
        batchInstanceData.setGroupNames(batchGroupNames);
        batchInstanceData.setServiceNames(batchServiceNames);
        return batchInstanceData;
    }
    
    @Override
    public void release() {
        Collection<InstancePublishInfo> instancePublishInfos = publishers.values();
        for (InstancePublishInfo instancePublishInfo : instancePublishInfos) {
            if (instancePublishInfo instanceof BatchInstancePublishInfo) {
                MetricsMonitor.decrementIpCountWithBatchRegister(instancePublishInfo);
            } else {
                MetricsMonitor.getIpCountMonitor().decrementAndGet();
            }
        }
        MetricsMonitor.getIpCountMonitor().addAndGet(-1 * subscribers.size());
    }
}
