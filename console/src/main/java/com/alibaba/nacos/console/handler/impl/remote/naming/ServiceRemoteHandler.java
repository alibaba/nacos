/*
 * Copyright 1999-2023 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.console.handler.impl.remote.naming;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.pojo.Cluster;
import com.alibaba.nacos.api.naming.pojo.maintainer.ClusterInfo;
import com.alibaba.nacos.api.naming.pojo.maintainer.ServiceDetailInfo;
import com.alibaba.nacos.api.naming.utils.NamingUtils;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.console.handler.impl.remote.EnabledRemoteHandler;
import com.alibaba.nacos.console.handler.impl.remote.NacosMaintainerClientHolder;
import com.alibaba.nacos.console.handler.naming.ServiceHandler;
import com.alibaba.nacos.naming.constants.FieldsConstants;
import com.alibaba.nacos.naming.core.v2.metadata.ClusterMetadata;
import com.alibaba.nacos.naming.core.v2.metadata.ServiceMetadata;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import com.alibaba.nacos.naming.model.form.ServiceForm;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Remote Implementation of ServiceHandler that handles service-related operations.
 *
 * @author xiweng.yy
 */
@org.springframework.stereotype.Service
@EnabledRemoteHandler
public class ServiceRemoteHandler implements ServiceHandler {
    
    private final NacosMaintainerClientHolder clientHolder;
    
    public ServiceRemoteHandler(NacosMaintainerClientHolder clientHolder) {
        this.clientHolder = clientHolder;
    }
    
    @Override
    public void createService(ServiceForm serviceForm, ServiceMetadata serviceMetadata) throws Exception {
        clientHolder.getNamingMaintainerService()
                .createService(serviceForm.getNamespaceId(), serviceForm.getGroupName(), serviceForm.getServiceName(),
                        serviceForm.getMetadata(), serviceForm.getEphemeral(), serviceForm.getProtectThreshold(),
                        serviceForm.getSelector());
    }
    
    @Override
    public void deleteService(String namespaceId, String serviceName, String groupName) throws Exception {
        clientHolder.getNamingMaintainerService().removeService(namespaceId, groupName, serviceName);
    }
    
    @Override
    public void updateService(ServiceForm serviceForm, Service service, ServiceMetadata serviceMetadata,
            Map<String, String> metadata) throws Exception {
        clientHolder.getNamingMaintainerService()
                .updateService(serviceForm.getNamespaceId(), serviceForm.getGroupName(), serviceForm.getServiceName(),
                        serviceForm.getMetadata(), serviceForm.getEphemeral(), serviceForm.getProtectThreshold(),
                        serviceForm.getSelector());
    }
    
    @Override
    public List<String> getSelectorTypeList() throws NacosException {
        return clientHolder.getNamingMaintainerService().listSelectorTypes();
    }
    
    @Override
    public ObjectNode getSubscribers(int pageNo, int pageSize, String namespaceId, String serviceName, String groupName,
            boolean aggregation) throws Exception {
        return clientHolder.getNamingMaintainerService()
                .getSubscribers(namespaceId, groupName, serviceName, pageNo, pageSize, aggregation);
    }
    
    @Override
    public Object getServiceList(boolean withInstances, String namespaceId, int pageNo, int pageSize,
            String serviceName, String groupName, boolean hasIpCount) throws NacosException {
        // TODO match right parameters after maintainer client fixed.
        return clientHolder.getNamingMaintainerService()
                .listServices(namespaceId, groupName, serviceName, withInstances, hasIpCount, pageNo, pageSize);
    }
    
    @Override
    public Object getServiceDetail(String namespaceId, String serviceName, String groupName) throws NacosException {
        ServiceDetailInfo serviceDetailInfo = clientHolder.getNamingMaintainerService()
                .getServiceDetail(namespaceId, groupName, serviceName);
        return transferToOldResult(serviceDetailInfo);
    }
    
    /**
     * TODO removed after console controller and console-ui support use ServiceDetailInfo directly.
     *
     * @param serviceDetailInfo serviceDetailInfo
     * @return old console ui custom result
     */
    private Object transferToOldResult(ServiceDetailInfo serviceDetailInfo) {
        ObjectNode serviceObject = JacksonUtils.createEmptyJsonNode();
        serviceObject.put(FieldsConstants.NAME, serviceDetailInfo.getServiceName());
        serviceObject.put(FieldsConstants.GROUP_NAME, serviceDetailInfo.getGroupName());
        serviceObject.put(FieldsConstants.PROTECT_THRESHOLD, serviceDetailInfo.getProtectThreshold());
        serviceObject.replace(FieldsConstants.SELECTOR,
                JacksonUtils.transferToJsonNode(serviceDetailInfo.getSelector()));
        serviceObject.replace(FieldsConstants.METADATA,
                JacksonUtils.transferToJsonNode(serviceDetailInfo.getMetadata()));
        
        ObjectNode detailView = JacksonUtils.createEmptyJsonNode();
        detailView.replace(FieldsConstants.SERVICE, serviceObject);
        
        List<com.alibaba.nacos.api.naming.pojo.Cluster> clusters = new ArrayList<>();
        String groupedServiceName = NamingUtils.getGroupedName(serviceDetailInfo.getServiceName(),
                serviceDetailInfo.getGroupName());
        for (Map.Entry<String, ClusterInfo> entry : serviceDetailInfo.getClusterMap().entrySet()) {
            com.alibaba.nacos.api.naming.pojo.Cluster clusterView = new Cluster();
            clusterView.setName(entry.getKey());
            clusterView.setHealthChecker(entry.getValue().getHealthChecker());
            clusterView.setMetadata(entry.getValue().getMetadata());
            clusterView.setDefaultPort(80);
            clusterView.setUseIpPort4Check(entry.getValue().isUseInstancePortForCheck());
            clusterView.setDefaultCheckPort(entry.getValue().getHealthyCheckPort());
            clusterView.setServiceName(groupedServiceName);
            clusters.add(clusterView);
        }
        
        detailView.replace(FieldsConstants.CLUSTERS, JacksonUtils.transferToJsonNode(clusters));
        return detailView;
    }
    
    @Override
    public void updateClusterMetadata(String namespaceId, String serviceName, String clusterName,
            ClusterMetadata clusterMetadata) throws Exception {
        String groupName = NamingUtils.getGroupName(serviceName);
        String serviceNameWithoutGroup = NamingUtils.getServiceName(serviceName);
        clientHolder.getNamingMaintainerService()
                .updateCluster(namespaceId, groupName, serviceNameWithoutGroup, clusterName,
                        clusterMetadata.getHealthyCheckPort(), clusterMetadata.isUseInstancePortForCheck(),
                        JacksonUtils.toJson(clusterMetadata.getHealthChecker()), clusterMetadata.getExtendData());
    }
    
}

