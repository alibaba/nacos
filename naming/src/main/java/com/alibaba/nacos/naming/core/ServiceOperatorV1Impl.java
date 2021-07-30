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

import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.utils.NamingUtils;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.naming.constants.FieldsConstants;
import com.alibaba.nacos.naming.core.v2.metadata.ServiceMetadata;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import com.alibaba.nacos.naming.utils.ServiceUtil;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/**
 * Implementation of service operator for v1.x.
 *
 * @author xiweng.yy
 */
@Component
public class ServiceOperatorV1Impl implements ServiceOperator {
    
    private final ServiceManager serviceManager;
    
    private final DistroMapper distroMapper;
    
    public ServiceOperatorV1Impl(ServiceManager serviceManager, DistroMapper distroMapper) {
        this.serviceManager = serviceManager;
        this.distroMapper = distroMapper;
    }
    
    @Override
    public void create(String namespaceId, String serviceName, ServiceMetadata metadata) throws NacosException {
        if (serviceManager.getService(namespaceId, serviceName) != null) {
            throw new IllegalArgumentException("specified service already exists, serviceName : " + serviceName);
        }
        com.alibaba.nacos.naming.core.Service service = new com.alibaba.nacos.naming.core.Service(serviceName);
        service.setProtectThreshold(metadata.getProtectThreshold());
        service.setEnabled(true);
        service.setMetadata(metadata.getExtendData());
        service.setSelector(metadata.getSelector());
        service.setNamespaceId(namespaceId);
        service.setGroupName(NamingUtils.getGroupName(serviceName));
        
        // now valid the service. if failed, exception will be thrown
        service.setLastModifiedMillis(System.currentTimeMillis());
        service.recalculateChecksum();
        service.validate();
        
        serviceManager.addOrReplaceService(service);
    }
    
    @Override
    public void update(Service service, ServiceMetadata metadata) throws NacosException {
        String namespaceId = service.getNamespace();
        String serviceName = service.getGroupedServiceName();
        com.alibaba.nacos.naming.core.Service serviceV1 = serviceManager.getService(namespaceId, serviceName);
        
        serviceManager.checkServiceIsNull(serviceV1, namespaceId, serviceName);
        
        serviceV1.setProtectThreshold(metadata.getProtectThreshold());
        serviceV1.setSelector(metadata.getSelector());
        serviceV1.setMetadata(metadata.getExtendData());
        serviceV1.setLastModifiedMillis(System.currentTimeMillis());
        serviceV1.recalculateChecksum();
        serviceV1.validate();
        serviceManager.addOrReplaceService(serviceV1);
    }
    
    @Override
    public void delete(String namespaceId, String serviceName) throws NacosException {
        serviceManager.easyRemoveService(namespaceId, serviceName);
    }
    
    @Override
    public ObjectNode queryService(String namespaceId, String serviceName) throws NacosException {
        com.alibaba.nacos.naming.core.Service service = serviceManager.getService(namespaceId, serviceName);
        
        serviceManager.checkServiceIsNull(service, namespaceId, serviceName);
        
        ObjectNode res = JacksonUtils.createEmptyJsonNode();
        res.put(FieldsConstants.NAME, NamingUtils.getServiceName(serviceName));
        res.put(FieldsConstants.NAME_SPACE_ID, service.getNamespaceId());
        res.put(FieldsConstants.PROTECT_THRESHOLD, service.getProtectThreshold());
        res.replace(FieldsConstants.METADATA, JacksonUtils.transferToJsonNode(service.getMetadata()));
        res.replace(FieldsConstants.SELECTOR, JacksonUtils.transferToJsonNode(service.getSelector()));
        res.put(FieldsConstants.GROUP_NAME, NamingUtils.getGroupName(serviceName));
        
        ArrayNode clusters = JacksonUtils.createEmptyArrayNode();
        for (Cluster cluster : service.getClusterMap().values()) {
            ObjectNode clusterJson = JacksonUtils.createEmptyJsonNode();
            clusterJson.put(FieldsConstants.NAME, cluster.getName());
            clusterJson.replace(FieldsConstants.HEALTH_CHECKER,
                    JacksonUtils.transferToJsonNode(cluster.getHealthChecker()));
            clusterJson.replace(FieldsConstants.METADATA, JacksonUtils.transferToJsonNode(cluster.getMetadata()));
            clusters.add(clusterJson);
        }
        res.replace(FieldsConstants.CLUSTERS, clusters);
        return res;
    }
    
    @Override
    public List<String> listService(String namespaceId, String groupName, String selector, int pageSize, int pageNo)
            throws NacosException {
        Map<String, com.alibaba.nacos.naming.core.Service> serviceMap = serviceManager.chooseServiceMap(namespaceId);
        if (serviceMap == null || serviceMap.isEmpty()) {
            return Collections.emptyList();
        }
        serviceMap = ServiceUtil.selectServiceWithGroupName(serviceMap, groupName);
        serviceMap = ServiceUtil.selectServiceBySelector(serviceMap, selector);
        if (!Constants.ALL_PATTERN.equals(groupName)) {
            serviceMap.entrySet()
                    .removeIf(entry -> !entry.getKey().startsWith(groupName + Constants.SERVICE_INFO_SPLITER));
        }
        return ServiceUtil.pageServiceName(pageNo, pageSize, serviceMap);
    }
    
    @Override
    public Collection<String> listAllNamespace() {
        return serviceManager.getAllNamespaces();
    }
    
    @Override
    public Collection<String> searchServiceName(String namespaceId, String expr, boolean responsibleOnly)
            throws NacosException {
        List<com.alibaba.nacos.naming.core.Service> services = serviceManager
                .searchServices(namespaceId, Constants.ANY_PATTERN + expr + Constants.ANY_PATTERN);
        Collection<String> result = new HashSet<>();
        for (com.alibaba.nacos.naming.core.Service each : services) {
            if (!responsibleOnly || distroMapper.responsible(each.getName())) {
                result.add(NamingUtils.getServiceName(each.getName()));
            }
        }
        return result;
    }
}
