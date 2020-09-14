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

package com.alibaba.nacos.naming.core.v2.index;

import com.alibaba.nacos.api.naming.CommonParams;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.naming.pojo.ServiceInfo;
import com.alibaba.nacos.api.naming.utils.NamingUtils;
import com.alibaba.nacos.common.utils.ConcurrentHashSet;
import com.alibaba.nacos.naming.core.v2.client.Client;
import com.alibaba.nacos.naming.core.v2.client.manager.ClientManager;
import com.alibaba.nacos.naming.core.v2.client.manager.ClientManagerDelegate;
import com.alibaba.nacos.naming.core.v2.pojo.InstancePublishInfo;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import com.alibaba.nacos.naming.misc.SwitchDomain;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Service storage.
 *
 * @author xiweng.yy
 */
@Component
public class ServiceStorage {
    
    private final ClientServiceIndexesManager serviceIndexesManager;
    
    private final ClientManager clientManager;
    
    private final SwitchDomain switchDomain;
    
    private final ConcurrentMap<Service, ServiceInfo> serviceDataIndexes;
    
    private final ConcurrentMap<String, Set<Service>> namespaceServiceIndex;
    
    public ServiceStorage(ClientServiceIndexesManager serviceIndexesManager, ClientManagerDelegate clientManager,
            SwitchDomain switchDomain) {
        this.serviceIndexesManager = serviceIndexesManager;
        this.clientManager = clientManager;
        this.switchDomain = switchDomain;
        serviceDataIndexes = new ConcurrentHashMap<>();
        namespaceServiceIndex = new ConcurrentHashMap<>();
    }
    
    public ServiceInfo getData(Service service) {
        return serviceDataIndexes.containsKey(service) ? serviceDataIndexes.get(service) : getPushData(service);
    }
    
    public ServiceInfo getPushData(Service service) {
        ServiceInfo result = new ServiceInfo();
        result.setName(service.getName());
        result.setGroupName(service.getGroup());
        result.setLastRefTime(System.currentTimeMillis());
        result.setCacheMillis(switchDomain.getDefaultPushCacheMillis());
        List<Instance> instances = new LinkedList<>();
        for (String each : serviceIndexesManager.getAllClientsRegisteredService(service)) {
            Optional<InstancePublishInfo> instancePublishInfo = getInstanceInfo(each, service);
            instancePublishInfo.ifPresent(publishInfo -> instances.add(parseInstance(service, publishInfo)));
        }
        result.setHosts(instances);
        serviceDataIndexes.put(service, result);
        updateNamespaceIndex(service);
        return result;
    }
    
    public Collection<Service> getAllServicesOfNamespace(String namespace) {
        return namespaceServiceIndex.getOrDefault(namespace, new ConcurrentHashSet<>());
    }
    
    private Optional<InstancePublishInfo> getInstanceInfo(String clientId, Service service) {
        Client client = clientManager.getClient(clientId);
        if (null == client) {
            return Optional.empty();
        }
        return Optional.ofNullable(client.getInstancePublishInfo(service));
    }
    
    private Instance parseInstance(Service service, InstancePublishInfo instancePublishInfo) {
        Instance result = new Instance();
        result.setIp(instancePublishInfo.getIp());
        result.setPort(instancePublishInfo.getPort());
        result.setServiceName(NamingUtils.getGroupedName(service.getName(), service.getGroup()));
        Map<String, String> instanceMetadata = new HashMap<>(instancePublishInfo.getExtendDatum().size());
        for (Map.Entry<String, Object> entry : instancePublishInfo.getExtendDatum().entrySet()) {
            if (CommonParams.CLUSTER_NAME.equals(entry.getKey())) {
                result.setClusterName(entry.getValue().toString());
            } else {
                instanceMetadata.put(entry.getKey(), entry.getValue().toString());
            }
        }
        result.setMetadata(instanceMetadata);
        result.setEphemeral(service.isEphemeral());
        return result;
    }
    
    private void updateNamespaceIndex(Service service) {
        if (!namespaceServiceIndex.containsKey(service.getNamespace())) {
            namespaceServiceIndex.putIfAbsent(service.getNamespace(), new ConcurrentHashSet<>());
        }
        namespaceServiceIndex.get(service.getNamespace()).add(service);
    }
}
