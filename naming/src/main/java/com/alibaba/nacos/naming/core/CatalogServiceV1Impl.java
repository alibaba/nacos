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
import com.alibaba.nacos.api.naming.utils.NamingUtils;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.naming.constants.FieldsConstants;
import com.alibaba.nacos.naming.pojo.ClusterInfo;
import com.alibaba.nacos.naming.pojo.IpAddressInfo;
import com.alibaba.nacos.naming.pojo.ServiceDetailInfo;
import com.alibaba.nacos.naming.pojo.ServiceView;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Catalog service for v1.x .
 *
 * @author xiweng.yy
 */
@Component
public class CatalogServiceV1Impl implements CatalogService {
    
    private final ServiceManager serviceManager;
    
    public CatalogServiceV1Impl(ServiceManager serviceManager) {
        this.serviceManager = serviceManager;
    }
    
    @Override
    public Object getServiceDetail(String namespaceId, String groupName, String serviceName) throws NacosException {
        Service detailedService = serviceManager
                .getService(namespaceId, NamingUtils.getGroupedName(serviceName, groupName));
        
        serviceManager.checkServiceIsNull(detailedService, namespaceId, serviceName);
        
        ObjectNode serviceObject = JacksonUtils.createEmptyJsonNode();
        serviceObject.put(FieldsConstants.NAME, serviceName);
        serviceObject.put(FieldsConstants.PROTECT_THRESHOLD, detailedService.getProtectThreshold());
        serviceObject.put(FieldsConstants.GROUP_NAME, groupName);
        serviceObject.replace(FieldsConstants.SELECTOR, JacksonUtils.transferToJsonNode(detailedService.getSelector()));
        serviceObject.replace(FieldsConstants.METADATA, JacksonUtils.transferToJsonNode(detailedService.getMetadata()));
        
        ObjectNode detailView = JacksonUtils.createEmptyJsonNode();
        detailView.replace(FieldsConstants.SERVICE, serviceObject);
        detailView.replace(FieldsConstants.CLUSTERS,
                JacksonUtils.transferToJsonNode(detailedService.getClusterMap().values()));
        return detailView;
    }
    
    @Override
    public List<? extends Instance> listInstances(String namespaceId, String groupName, String serviceName,
            String clusterName) throws NacosException {
        Service service = serviceManager.getService(namespaceId, NamingUtils.getGroupedName(serviceName, groupName));
        
        serviceManager.checkServiceIsNull(service, namespaceId, serviceName);
        
        if (!service.getClusterMap().containsKey(clusterName)) {
            throw new NacosException(NacosException.NOT_FOUND, "cluster " + clusterName + " is not found!");
        }
        return service.getClusterMap().get(clusterName).allIPs();
    }
    
    @Override
    public Object pageListService(String namespaceId, String groupName, String serviceName, int pageNo, int pageSize,
            String instancePattern, boolean ignoreEmptyService) throws NacosException {
        String param = StringUtils.isBlank(serviceName) && StringUtils.isBlank(groupName) ? StringUtils.EMPTY
                : NamingUtils.getGroupedNameOptional(serviceName, groupName);
        ObjectNode result = JacksonUtils.createEmptyJsonNode();
        
        List<Service> services = new ArrayList<>();
        final int total = serviceManager
                .getPagedService(namespaceId, pageNo - 1, pageSize, param, instancePattern, services,
                        ignoreEmptyService);
        if (CollectionUtils.isEmpty(services)) {
            result.replace(FieldsConstants.SERVICE_LIST, JacksonUtils.transferToJsonNode(Collections.emptyList()));
            result.put(FieldsConstants.COUNT, 0);
            return result;
        }
        
        List<ServiceView> serviceViews = new LinkedList<>();
        for (Service each : services) {
            ServiceView serviceView = new ServiceView();
            serviceView.setName(NamingUtils.getServiceName(each.getName()));
            serviceView.setGroupName(NamingUtils.getGroupName(each.getName()));
            serviceView.setClusterCount(each.getClusterMap().size());
            serviceView.setIpCount(each.allIPs().size());
            serviceView.setHealthyInstanceCount(each.healthyInstanceCount());
            serviceView.setTriggerFlag(each.triggerFlag() ? "true" : "false");
            serviceViews.add(serviceView);
        }
        
        result.set(FieldsConstants.SERVICE_LIST, JacksonUtils.transferToJsonNode(serviceViews));
        result.put(FieldsConstants.COUNT, total);
        
        return result;
    }
    
    @Override
    public Object pageListServiceDetail(String namespaceId, String groupName, String serviceName, int pageNo,
            int pageSize) throws NacosException {
        String param = StringUtils.isBlank(serviceName) && StringUtils.isBlank(groupName) ? StringUtils.EMPTY
                : NamingUtils.getGroupedNameOptional(serviceName, groupName);
        List<ServiceDetailInfo> serviceDetailInfoList = new ArrayList<>();
        List<Service> services = new ArrayList<>(8);
        serviceManager.getPagedService(namespaceId, pageNo, pageSize, param, StringUtils.EMPTY, services, false);
        
        for (Service each : services) {
            ServiceDetailInfo serviceDetailInfo = new ServiceDetailInfo();
            serviceDetailInfo.setServiceName(NamingUtils.getServiceName(each.getName()));
            serviceDetailInfo.setGroupName(NamingUtils.getGroupName(each.getName()));
            serviceDetailInfo.setMetadata(each.getMetadata());
            
            Map<String, ClusterInfo> clusterInfoMap = getStringClusterInfoMap(each);
            serviceDetailInfo.setClusterMap(clusterInfoMap);
            
            serviceDetailInfoList.add(serviceDetailInfo);
        }
        
        return serviceDetailInfoList;
    }
    
    private Map<String, ClusterInfo> getStringClusterInfoMap(Service service) {
        Map<String, ClusterInfo> clusterInfoMap = new HashMap<>(8);
        
        service.getClusterMap().forEach((clusterName, cluster) -> {
            
            ClusterInfo clusterInfo = new ClusterInfo();
            List<IpAddressInfo> ipAddressInfos = getIpAddressInfos(cluster.allIPs());
            clusterInfo.setHosts(ipAddressInfos);
            clusterInfoMap.put(clusterName, clusterInfo);
            
        });
        return clusterInfoMap;
    }
    
    private List<IpAddressInfo> getIpAddressInfos(List<com.alibaba.nacos.naming.core.Instance> instances) {
        List<IpAddressInfo> ipAddressInfos = new ArrayList<>();
        
        instances.forEach((ipAddress) -> {
            
            IpAddressInfo ipAddressInfo = new IpAddressInfo();
            ipAddressInfo.setIp(ipAddress.getIp());
            ipAddressInfo.setPort(ipAddress.getPort());
            ipAddressInfo.setMetadata(ipAddress.getMetadata());
            ipAddressInfo.setValid(ipAddress.isHealthy());
            ipAddressInfo.setWeight(ipAddress.getWeight());
            ipAddressInfo.setEnabled(ipAddress.isEnabled());
            ipAddressInfos.add(ipAddressInfo);
            
        });
        return ipAddressInfos;
    }
}
