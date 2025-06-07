/*
 * Copyright 1999-2020 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.naming.core;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.naming.pojo.healthcheck.AbstractHealthChecker;
import com.alibaba.nacos.api.naming.pojo.healthcheck.HealthCheckType;
import com.alibaba.nacos.common.utils.InternetAddressUtil;
import com.alibaba.nacos.naming.core.v2.client.Client;
import com.alibaba.nacos.naming.core.v2.client.impl.IpPortBasedClient;
import com.alibaba.nacos.naming.core.v2.client.manager.ClientManager;
import com.alibaba.nacos.naming.core.v2.client.manager.ClientManagerDelegate;
import com.alibaba.nacos.naming.core.v2.metadata.ClusterMetadata;
import com.alibaba.nacos.naming.core.v2.metadata.NamingMetadataManager;
import com.alibaba.nacos.naming.core.v2.metadata.ServiceMetadata;
import com.alibaba.nacos.naming.core.v2.pojo.InstancePublishInfo;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import com.alibaba.nacos.naming.core.v2.service.ClientOperationService;
import com.alibaba.nacos.naming.core.v2.service.ClientOperationServiceProxy;
import com.alibaba.nacos.naming.misc.Loggers;
import com.alibaba.nacos.naming.utils.InstanceUtil;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Health operator implementation for v1.x.
 *
 * @author xiweng.yy
 */
@Component
public class HealthOperatorV2Impl implements HealthOperator {
    
    private final NamingMetadataManager metadataManager;
    
    private final ClientManager clientManager;
    
    private final ClientOperationService clientOperationService;
    
    public HealthOperatorV2Impl(NamingMetadataManager metadataManager, ClientManagerDelegate clientManager,
            ClientOperationServiceProxy clientOperationService) {
        this.metadataManager = metadataManager;
        this.clientManager = clientManager;
        this.clientOperationService = clientOperationService;
    }
    
    @Override
    public void updateHealthStatusForPersistentInstance(String namespace, String groupName, String serviceName,
            String clusterName, String ip, int port, boolean healthy) throws NacosException {
        Service service = Service.newService(namespace, groupName, serviceName);
        Optional<ServiceMetadata> serviceMetadata = metadataManager.getServiceMetadata(service);
        if (serviceMetadata.isEmpty() || !serviceMetadata.get().getClusters().containsKey(clusterName)) {
            throwHealthCheckerException(groupName, serviceName, clusterName);
        }
        ClusterMetadata clusterMetadata = serviceMetadata.get().getClusters().get(clusterName);
        if (!HealthCheckType.NONE.name().equals(clusterMetadata.getHealthyCheckType())) {
            throwHealthCheckerException(groupName, serviceName, clusterName);
        }
        String clientId = IpPortBasedClient.getClientId(ip + InternetAddressUtil.IP_PORT_SPLITER + port, false);
        Client client = clientManager.getClient(clientId);
        if (null == client) {
            return;
        }
        InstancePublishInfo oldInstance = client.getInstancePublishInfo(service);
        if (null == oldInstance) {
            return;
        }
        Instance newInstance = InstanceUtil.parseToApiInstance(service, oldInstance);
        newInstance.setHealthy(healthy);
        clientOperationService.registerInstance(service, newInstance, clientId);
    }
    
    @Override
    public Map<String, AbstractHealthChecker> checkers() {
        List<Class<? extends AbstractHealthChecker>> classes = HealthCheckType.getLoadedHealthCheckerClasses();
        Map<String, AbstractHealthChecker> checkerMap = new HashMap<>(8);
        for (Class<? extends AbstractHealthChecker> clazz : classes) {
            try {
                AbstractHealthChecker checker = clazz.newInstance();
                checkerMap.put(checker.getType(), checker);
            } catch (InstantiationException | IllegalAccessException e) {
                Loggers.EVT_LOG.error("checkers error ", e);
            }
        }
        
        return checkerMap;
    }
    
    private void throwHealthCheckerException(String groupName, String serviceName, String clusterName)
            throws NacosException {
        String errorInfo = String.format("health check is still working, service: %s@@%s, cluster: %s", groupName,
                serviceName, clusterName);
        throw new NacosException(NacosException.INVALID_PARAM, errorInfo);
    }
}
