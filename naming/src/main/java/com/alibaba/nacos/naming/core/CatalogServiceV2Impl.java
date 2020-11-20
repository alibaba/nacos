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
import com.alibaba.nacos.api.naming.pojo.Cluster;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.naming.pojo.ServiceInfo;
import com.alibaba.nacos.api.naming.utils.NamingUtils;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.naming.core.v2.ServiceManager;
import com.alibaba.nacos.naming.core.v2.index.ServiceStorage;
import com.alibaba.nacos.naming.core.v2.metadata.ClusterMetadata;
import com.alibaba.nacos.naming.core.v2.metadata.NamingMetadataManager;
import com.alibaba.nacos.naming.core.v2.metadata.ServiceMetadata;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import com.alibaba.nacos.naming.pojo.ServiceView;
import com.alibaba.nacos.naming.utils.ServiceUtil;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.stream.Collectors;

/**
 * Catalog service for v1.x .
 *
 * @author xiweng.yy
 */
@Component()
public class CatalogServiceV2Impl implements CatalogService {
    
    private final ServiceStorage serviceStorage;
    
    private final NamingMetadataManager metadataManager;
    
    public CatalogServiceV2Impl(ServiceStorage serviceStorage, NamingMetadataManager metadataManager) {
        this.serviceStorage = serviceStorage;
        this.metadataManager = metadataManager;
    }
    
    @Override
    public Object getServiceDetail(String namespaceId, String groupName, String serviceName) throws NacosException {
        Service service = Service.newService(namespaceId, NamingUtils.getGroupName(serviceName),
                NamingUtils.getServiceName(serviceName));
        if (!ServiceManager.getInstance().containSingleton(service)) {
            throw new NacosException(NacosException.NOT_FOUND,
                    String.format("service %s@@%s is not found!", groupName, serviceName));
        }
        
        Optional<ServiceMetadata> metadata = metadataManager.getServiceMetadata(service);
        ServiceMetadata detailedService = metadata.orElseGet(ServiceMetadata::new);
        
        ObjectNode serviceObject = JacksonUtils.createEmptyJsonNode();
        serviceObject.put("name", NamingUtils.getServiceName(serviceName));
        serviceObject.put("groupName", NamingUtils.getGroupName(serviceName));
        serviceObject.put("protectThreshold", detailedService.getProtectThreshold());
        serviceObject.replace("selector", JacksonUtils.transferToJsonNode(detailedService.getSelector()));
        serviceObject.replace("metadata", JacksonUtils.transferToJsonNode(detailedService.getExtendData()));
        
        ObjectNode detailView = JacksonUtils.createEmptyJsonNode();
        detailView.replace("service", serviceObject);
        
        List<com.alibaba.nacos.api.naming.pojo.Cluster> clusters = new ArrayList<>();
        
        for (String each : serviceStorage.getClusters(service)) {
            ClusterMetadata clusterMetadata =
                    detailedService.getClusters().containsKey(each) ? detailedService.getClusters().get(each)
                            : new ClusterMetadata();
            com.alibaba.nacos.api.naming.pojo.Cluster clusterView = new Cluster();
            clusterView.setName(each);
            clusterView.setHealthChecker(clusterMetadata.getHealthChecker());
            clusterView.setMetadata(clusterMetadata.getExtendData());
            clusterView.setUseIPPort4Check(clusterMetadata.isUseInstancePortForCheck());
            clusterView.setDefaultPort(80);
            clusterView.setDefaultCheckPort(clusterMetadata.getHealthyCheckPort());
            clusterView.setServiceName(service.getGroupedServiceName());
            clusters.add(clusterView);
        }
        
        detailView.replace("clusters", JacksonUtils.transferToJsonNode(clusters));
        
        return detailView;
    }
    
    @Override
    public List<? extends Instance> listInstances(String namespaceId, String groupName, String serviceName,
            String clusterName) throws NacosException {
        Service service = Service.newService(namespaceId, groupName, serviceName);
        if (!ServiceManager.getInstance().containSingleton(service)) {
            throw new NacosException(NacosException.NOT_FOUND,
                    String.format("service %s@@%s is not found!", groupName, serviceName));
        }
        if (!serviceStorage.getClusters(service).contains(clusterName)) {
            throw new NacosException(NacosException.NOT_FOUND, "cluster " + clusterName + " is not found!");
        }
        ServiceInfo serviceInfo = serviceStorage.getData(service);
        ServiceInfo result = ServiceUtil.selectInstances(serviceInfo, clusterName);
        return result.getHosts();
    }
    
    @Override
    public Object pageListService(String namespaceId, String groupName, String serviceName, int pageNo, int pageSize,
            String instancePattern, boolean ignoreEmptyService) throws NacosException {
        ObjectNode result = JacksonUtils.createEmptyJsonNode();
        List<ServiceView> serviceViews = new LinkedList<>();
        Collection<Service> services = patternServices(namespaceId, groupName, serviceName);
        if (ignoreEmptyService) {
            services = services.stream().filter(each -> 0 != serviceStorage.getData(each).ipCount())
                    .collect(Collectors.toList());
        }
        result.put("count", services.size());
        services = doPage(services, pageNo - 1, pageSize);
        for (Service each : services) {
            ServiceMetadata serviceMetadata = metadataManager.getServiceMetadata(each).orElseGet(ServiceMetadata::new);
            ServiceView serviceView = new ServiceView();
            serviceView.setName(each.getName());
            serviceView.setGroupName(each.getGroup());
            serviceView.setClusterCount(serviceStorage.getClusters(each).size());
            serviceView.setIpCount(serviceStorage.getData(each).ipCount());
            serviceView.setHealthyInstanceCount(countHealthyInstance(serviceStorage.getData(each)));
            serviceView.setTriggerFlag(isProtectThreshold(serviceView, serviceMetadata) ? "true" : "false");
            serviceViews.add(serviceView);
        }
        result.set("serviceList", JacksonUtils.transferToJsonNode(serviceViews));
        return result;
    }
    
    private int countHealthyInstance(ServiceInfo data) {
        int result = 0;
        for (Instance each : data.getHosts()) {
            if (each.isHealthy()) {
                result++;
            }
        }
        return result;
    }
    
    private boolean isProtectThreshold(ServiceView serviceView, ServiceMetadata metadata) {
        return (serviceView.getHealthyInstanceCount() * 1.0 / serviceView.getIpCount()) <= metadata
                .getProtectThreshold();
    }
    
    @Override
    public Object pageListServiceDetail(String namespaceId, String groupName, String serviceName, int pageNo,
            int pageSize) throws NacosException {
        return null;
    }
    
    private Collection<Service> patternServices(String namespaceId, String group, String serviceName) {
        boolean noFilter = StringUtils.isBlank(serviceName) && StringUtils.isBlank(group);
        if (noFilter) {
            return serviceStorage.getAllServicesOfNamespace(namespaceId);
        }
        Collection<Service> result = new LinkedList<>();
        StringJoiner regex = new StringJoiner(Constants.SERVICE_INFO_SPLITER);
        regex.add(getRegexString(group));
        regex.add(getRegexString(serviceName));
        String regexString = regex.toString();
        for (Service each : serviceStorage.getAllServicesOfNamespace(namespaceId)) {
            if (each.getGroupedServiceName().matches(regexString)) {
                result.add(each);
            }
        }
        return result;
    }
    
    private String getRegexString(String target) {
        return StringUtils.isBlank(target) ? Constants.ANY_PATTERN
                : Constants.ANY_PATTERN + target + Constants.ANY_PATTERN;
    }
    
    private Collection<Service> doPage(Collection<Service> services, int pageNo, int pageSize) {
        if (services.size() < pageSize) {
            return services;
        }
        Collection<Service> result = new LinkedList<>();
        int i = 0;
        for (Service each : services) {
            if (i++ < pageNo * pageSize) {
                continue;
            }
            result.add(each);
            if (result.size() >= pageSize) {
                break;
            }
        }
        return result;
    }
}
