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
import com.alibaba.nacos.naming.core.v2.client.Client;
import com.alibaba.nacos.naming.core.v2.client.manager.impl.ConnectionBasedClientManager;
import com.alibaba.nacos.naming.core.v2.pojo.InstancePublishInfo;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
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
    
    private final ConnectionBasedClientManager clientManager;
    
    private final ConcurrentMap<Service, ServiceInfo> serviceDataIndexes;
    
    public ServiceStorage(ClientServiceIndexesManager serviceIndexesManager, ConnectionBasedClientManager clientManager) {
        this.serviceIndexesManager = serviceIndexesManager;
        this.clientManager = clientManager;
        serviceDataIndexes = new ConcurrentHashMap<>();
    }
    
    public ServiceInfo getData(Service service) {
        if (serviceDataIndexes.containsKey(service)) {
            return serviceDataIndexes.get(service);
        }
        return getPushData(service);
    }
    
    public ServiceInfo getPushData(Service service) {
        ServiceInfo result = new ServiceInfo();
        result.setName(service.getName());
        result.setGroupName(service.getGroup());
        List<Instance> instances = new LinkedList<>();
        for (String each : serviceIndexesManager.getAllClientsRegisteredService(service)) {
            Client client = clientManager.getClient(each);
            instances.add(parseInstance(service, client.getInstancePublishInfo(service)));
        }
        result.setHosts(instances);
        serviceDataIndexes.put(service, result);
        return result;
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
}
