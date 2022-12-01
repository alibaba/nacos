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
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.api.naming.utils.NamingUtils;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.naming.constants.FieldsConstants;
import com.alibaba.nacos.naming.core.v2.ServiceManager;
import com.alibaba.nacos.naming.core.v2.index.ServiceStorage;
import com.alibaba.nacos.naming.core.v2.metadata.ClusterMetadata;
import com.alibaba.nacos.naming.core.v2.metadata.NamingMetadataManager;
import com.alibaba.nacos.naming.core.v2.metadata.NamingMetadataOperateService;
import com.alibaba.nacos.naming.core.v2.metadata.ServiceMetadata;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import com.alibaba.nacos.naming.pojo.ClusterInfo;
import com.alibaba.nacos.naming.pojo.ServiceDetailInfo;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;

/**
 * Implementation of service operator for v2.x.
 *
 * @author xiweng.yy
 */
@Component
public class ServiceOperatorV2Impl implements ServiceOperator {
    
    private final NamingMetadataOperateService metadataOperateService;
    
    private final NamingMetadataManager metadataManager;
    
    private final ServiceStorage serviceStorage;
    
    public ServiceOperatorV2Impl(NamingMetadataOperateService metadataOperateService,
            NamingMetadataManager metadataManager, ServiceStorage serviceStorage) {
        this.metadataOperateService = metadataOperateService;
        this.metadataManager = metadataManager;
        this.serviceStorage = serviceStorage;
    }
    
    @Override
    public void create(String namespaceId, String serviceName, ServiceMetadata metadata) throws NacosException {
        Service service = getServiceFromGroupedServiceName(namespaceId, serviceName, metadata.isEphemeral());
        create(service, metadata);
    }
    
    /**
     * Create new service.
     *
     * @param service  v2 service
     * @param metadata new metadata of service
     * @throws NacosException nacos exception during creating
     */
    public void create(Service service, ServiceMetadata metadata) throws NacosException {
        if (ServiceManager.getInstance().containSingleton(service)) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.SERVICE_ALREADY_EXIST,
                    String.format("specified service %s already exists!", service.getGroupedServiceName()));
        }
        metadataOperateService.updateServiceMetadata(service, metadata);
    }
    
    @Override
    public void update(Service service, ServiceMetadata metadata) throws NacosException {
        if (!ServiceManager.getInstance().containSingleton(service)) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.SERVICE_NOT_EXIST,
                    String.format("service %s not found!", service.getGroupedServiceName()));
        }
        metadataOperateService.updateServiceMetadata(service, metadata);
    }
    
    @Override
    public void delete(String namespaceId, String serviceName) throws NacosException {
        Service service = getServiceFromGroupedServiceName(namespaceId, serviceName, true);
        delete(service);
    }
    
    /**
     * Delete service.
     *
     * @param service service v2
     * @throws NacosException nacos exception during delete
     */
    public void delete(Service service) throws NacosException {
        if (!ServiceManager.getInstance().containSingleton(service)) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.SERVICE_NOT_EXIST,
                    String.format("service %s not found!", service.getGroupedServiceName()));
        }
        
        if (!serviceStorage.getPushData(service).getHosts().isEmpty()) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.SERVICE_DELETE_FAILURE,
                    "Service " + service.getGroupedServiceName()
                            + " is not empty, can't be delete. Please unregister instance first");
        }
        metadataOperateService.deleteServiceMetadata(service);
    }
    
    @Override
    public ObjectNode queryService(String namespaceId, String serviceName) throws NacosException {
        Service service = getServiceFromGroupedServiceName(namespaceId, serviceName, true);
        if (!ServiceManager.getInstance().containSingleton(service)) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.SERVICE_NOT_EXIST,
                    "service not found, namespace: " + namespaceId + ", serviceName: " + serviceName);
        }
        ObjectNode result = JacksonUtils.createEmptyJsonNode();
        ServiceMetadata serviceMetadata = metadataManager.getServiceMetadata(service).orElse(new ServiceMetadata());
        setServiceMetadata(result, serviceMetadata, service);
        ArrayNode clusters = JacksonUtils.createEmptyArrayNode();
        for (String each : serviceStorage.getClusters(service)) {
            ClusterMetadata clusterMetadata =
                    serviceMetadata.getClusters().containsKey(each) ? serviceMetadata.getClusters().get(each)
                            : new ClusterMetadata();
            clusters.add(newClusterNode(each, clusterMetadata));
        }
        result.set(FieldsConstants.CLUSTERS, clusters);
        return result;
    }
    
    /**
     * Query service detail.
     *
     * @param service service
     * @return service detail with cluster info
     * @throws NacosException nacos exception during query
     */
    public ServiceDetailInfo queryService(Service service) throws NacosException {
        if (!ServiceManager.getInstance().containSingleton(service)) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.SERVICE_NOT_EXIST,
                    "service not found, namespace: " + service.getNamespace() + ", serviceName: " + service
                            .getGroupedServiceName());
        }
        Service singleton = ServiceManager.getInstance().getSingleton(service);
        ServiceDetailInfo result = new ServiceDetailInfo();
        ServiceMetadata serviceMetadata = metadataManager.getServiceMetadata(singleton).orElse(new ServiceMetadata());
        setServiceMetadata(result, serviceMetadata, singleton);
        Map<String, ClusterInfo> clusters = new HashMap<>(2);
        for (String each : serviceStorage.getClusters(singleton)) {
            ClusterMetadata clusterMetadata =
                    serviceMetadata.getClusters().containsKey(each) ? serviceMetadata.getClusters().get(each)
                            : new ClusterMetadata();
            clusters.put(each, newClusterNodeV2(each, clusterMetadata));
        }
        result.setClusterMap(clusters);
        result.setEphemeral(singleton.isEphemeral());
        return result;
    }
    
    private void setServiceMetadata(ObjectNode serviceDetail, ServiceMetadata serviceMetadata, Service service) {
        serviceDetail.put(FieldsConstants.NAME_SPACE_ID, service.getNamespace());
        serviceDetail.put(FieldsConstants.GROUP_NAME, service.getGroup());
        serviceDetail.put(FieldsConstants.NAME, service.getName());
        serviceDetail.put(FieldsConstants.PROTECT_THRESHOLD, serviceMetadata.getProtectThreshold());
        serviceDetail
                .replace(FieldsConstants.METADATA, JacksonUtils.transferToJsonNode(serviceMetadata.getExtendData()));
        serviceDetail.replace(FieldsConstants.SELECTOR, JacksonUtils.transferToJsonNode(serviceMetadata.getSelector()));
    }
    
    private void setServiceMetadata(ServiceDetailInfo serviceDetail, ServiceMetadata serviceMetadata, Service service) {
        serviceDetail.setNamespace(service.getNamespace());
        serviceDetail.setGroupName(service.getGroup());
        serviceDetail.setServiceName(service.getName());
        serviceDetail.setProtectThreshold(serviceMetadata.getProtectThreshold());
        serviceDetail.setMetadata(serviceMetadata.getExtendData());
        serviceDetail.setSelector(serviceMetadata.getSelector());
    }
    
    private ObjectNode newClusterNode(String clusterName, ClusterMetadata clusterMetadata) {
        ObjectNode result = JacksonUtils.createEmptyJsonNode();
        result.put(FieldsConstants.NAME, clusterName);
        result.replace(FieldsConstants.HEALTH_CHECKER,
                JacksonUtils.transferToJsonNode(clusterMetadata.getHealthChecker()));
        result.replace(FieldsConstants.METADATA, JacksonUtils.transferToJsonNode(clusterMetadata.getExtendData()));
        return result;
    }
    
    private ClusterInfo newClusterNodeV2(String clusterName, ClusterMetadata clusterMetadata) {
        ClusterInfo result = new ClusterInfo();
        result.setClusterName(clusterName);
        result.setHealthChecker(clusterMetadata.getHealthChecker());
        result.setMetadata(clusterMetadata.getExtendData());
        return result;
    }
    
    @Override
    @SuppressWarnings("unchecked")
    public Collection<String> listService(String namespaceId, String groupName, String selector) throws NacosException {
        Collection<Service> services = ServiceManager.getInstance().getSingletons(namespaceId);
        if (services.isEmpty()) {
            return Collections.EMPTY_LIST;
        }
        // TODO select service by selector
        return selectServiceWithGroupName(services, groupName);
    }
    
    private Collection<String> selectServiceWithGroupName(Collection<Service> serviceSet, String groupName) {
        Collection<String> result = new HashSet<>(serviceSet.size());
        for (Service each : serviceSet) {
            if (Objects.equals(groupName, each.getGroup())) {
                result.add(each.getGroupedServiceName());
            }
        }
        return result;
    }
    
    private Service getServiceFromGroupedServiceName(String namespaceId, String groupedServiceName, boolean ephemeral) {
        String groupName = NamingUtils.getGroupName(groupedServiceName);
        String serviceName = NamingUtils.getServiceName(groupedServiceName);
        return Service.newService(namespaceId, groupName, serviceName, ephemeral);
    }
    
    @Override
    public Collection<String> listAllNamespace() {
        return ServiceManager.getInstance().getAllNamespaces();
    }
    
    @Override
    public Collection<String> searchServiceName(String namespaceId, String expr) throws NacosException {
        String regex = Constants.ANY_PATTERN + expr + Constants.ANY_PATTERN;
        Collection<String> result = new HashSet<>();
        for (Service each : ServiceManager.getInstance().getSingletons(namespaceId)) {
            String groupedServiceName = each.getGroupedServiceName();
            if (groupedServiceName.matches(regex)) {
                result.add(groupedServiceName);
            }
        }
        return result;
    }
}
