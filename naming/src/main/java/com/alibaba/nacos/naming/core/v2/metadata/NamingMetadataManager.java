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

package com.alibaba.nacos.naming.core.v2.metadata;

import com.alibaba.nacos.common.notify.Event;
import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.common.notify.listener.SmartSubscriber;
import com.alibaba.nacos.common.utils.ConcurrentHashSet;
import com.alibaba.nacos.naming.core.v2.event.client.ClientEvent;
import com.alibaba.nacos.naming.core.v2.event.metadata.MetadataEvent;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import org.springframework.stereotype.Component;

import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Nacos naming metadata manager.
 *
 * @author xiweng.yy
 */
@Component
public class NamingMetadataManager extends SmartSubscriber {
    
    private final ConcurrentMap<Service, ServiceMetadata> serviceMetadataMap;
    
    private final ConcurrentMap<Service, ConcurrentMap<String, InstanceMetadata>> instanceMetadataMap;
    
    private final Set<ExpiredMetadataInfo> expiredMetadataInfos;
    
    public NamingMetadataManager() {
        serviceMetadataMap = new ConcurrentHashMap<>(1 << 10);
        instanceMetadataMap = new ConcurrentHashMap<>(1 << 10);
        expiredMetadataInfos = new ConcurrentHashSet<>();
        NotifyCenter.registerSubscriber(this);
    }
    
    /**
     * Whether contain service metadata for {@link Service}.
     *
     * @param service service
     * @return true if contain service metadata, otherwise false
     */
    public boolean containServiceMetadata(Service service) {
        return serviceMetadataMap.containsKey(service);
    }
    
    /**
     * Whether instance metadata metadata for instance of {@link Service}.
     *
     * @param service service
     * @return true if contain instance metadata, otherwise false
     */
    public boolean containInstanceMetadata(Service service, String instanceId) {
        return instanceMetadataMap.containsKey(service) && instanceMetadataMap.get(service).containsKey(instanceId);
    }
    
    /**
     * Get service metadata for {@link Service}.
     *
     * @param service service
     * @return service metadata
     */
    public Optional<ServiceMetadata> getServiceMetadata(Service service) {
        return Optional.ofNullable(serviceMetadataMap.get(service));
    }
    
    /**
     * Get instance metadata for instance of {@link Service}.
     *
     * @param service    service
     * @param instanceId instance id
     * @return instance metadata
     */
    public Optional<InstanceMetadata> getInstanceMetadata(Service service, String instanceId) {
        ConcurrentMap<String, InstanceMetadata> instanceMetadataMapForService = instanceMetadataMap.get(service);
        if (null == instanceMetadataMapForService) {
            return Optional.empty();
        }
        return Optional.ofNullable(instanceMetadataMapForService.get(instanceId));
    }
    
    /**
     * Update service metadata.
     *
     * @param service         service
     * @param serviceMetadata new service metadata
     */
    public void updateServiceMetadata(Service service, ServiceMetadata serviceMetadata) {
        serviceMetadataMap.put(service, serviceMetadata);
    }
    
    /**
     * Update instance metadata.
     *
     * @param service          service
     * @param instanceId       instance id
     * @param instanceMetadata new instance metadata
     */
    public void updateInstanceMetadata(Service service, String instanceId, InstanceMetadata instanceMetadata) {
        if (!instanceMetadataMap.containsKey(service)) {
            instanceMetadataMap.putIfAbsent(service, new ConcurrentHashMap<>(1));
        }
        instanceMetadataMap.get(service).put(instanceId, instanceMetadata);
    }
    
    /**
     * Remove service metadata.
     *
     * @param service service
     */
    public void removeServiceMetadata(Service service) {
        serviceMetadataMap.remove(service);
        expiredMetadataInfos.remove(ExpiredMetadataInfo.newExpiredServiceMetadata(service));
    }
    
    /**
     * Remove instance metadata.
     *
     * @param service    service
     * @param instanceId instance id
     */
    public void removeInstanceMetadata(Service service, String instanceId) {
        ConcurrentMap<String, InstanceMetadata> instanceMetadataMapForService = instanceMetadataMap.get(service);
        instanceMetadataMapForService.remove(instanceId);
        if (instanceMetadataMapForService.isEmpty()) {
            serviceMetadataMap.remove(service);
        }
        expiredMetadataInfos.remove(ExpiredMetadataInfo.newExpiredInstanceMetadata(service, instanceId));
    }
    
    public Set<ExpiredMetadataInfo> getExpiredMetadataInfos() {
        return expiredMetadataInfos;
    }
    
    @Override
    public List<Class<? extends Event>> subscribeTypes() {
        List<Class<? extends Event>> result = new LinkedList<>();
        result.add(MetadataEvent.InstanceMetadataEvent.class);
        result.add(MetadataEvent.ServiceMetadataEvent.class);
        result.add(ClientEvent.ClientDisconnectEvent.class);
        return result;
    }
    
    @Override
    public void onEvent(Event event) {
        if (event instanceof MetadataEvent.InstanceMetadataEvent) {
            handleInstanceMetadataEvent((MetadataEvent.InstanceMetadataEvent) event);
        } else if (event instanceof MetadataEvent.ServiceMetadataEvent) {
            handleServiceMetadataEvent((MetadataEvent.ServiceMetadataEvent) event);
        } else {
            handleClientDisconnectEvent((ClientEvent.ClientDisconnectEvent) event);
        }
    }
    
    private void handleClientDisconnectEvent(ClientEvent.ClientDisconnectEvent event) {
        for (Service each : event.getClient().getAllPublishedService()) {
            String instanceId = event.getClient().getInstancePublishInfo(each).getIp();
            updateExpiredInfo(true, ExpiredMetadataInfo.newExpiredInstanceMetadata(each, instanceId));
        }
    }
    
    private void handleServiceMetadataEvent(MetadataEvent.ServiceMetadataEvent event) {
        Service service = event.getService();
        if (!containServiceMetadata(service)) {
            return;
        }
        updateExpiredInfo(event.isExpired(), ExpiredMetadataInfo.newExpiredServiceMetadata(service));
    }
    
    private void handleInstanceMetadataEvent(MetadataEvent.InstanceMetadataEvent event) {
        Service service = event.getService();
        String instanceId = event.getInstanceId();
        if (!containInstanceMetadata(service, instanceId)) {
            return;
        }
        updateExpiredInfo(event.isExpired(),
                ExpiredMetadataInfo.newExpiredInstanceMetadata(event.getService(), event.getInstanceId()));
    }
    
    private void updateExpiredInfo(boolean expired, ExpiredMetadataInfo expiredMetadataInfo) {
        if (expired) {
            expiredMetadataInfos.add(expiredMetadataInfo);
        } else {
            expiredMetadataInfos.remove(expiredMetadataInfo);
        }
    }
}
