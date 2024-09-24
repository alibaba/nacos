/*
 * Copyright 1999-2024 Alibaba Group Holding Ltd.
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
 *
 */

package com.alibaba.nacos.console.handler.inner.naming;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.common.trace.event.naming.DeregisterServiceTraceEvent;
import com.alibaba.nacos.common.trace.event.naming.RegisterServiceTraceEvent;
import com.alibaba.nacos.common.trace.event.naming.UpdateServiceTraceEvent;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.console.handler.naming.ServiceHandler;
import com.alibaba.nacos.naming.core.CatalogServiceV2Impl;
import com.alibaba.nacos.naming.core.ClusterOperatorV2Impl;
import com.alibaba.nacos.naming.core.ServiceOperatorV2Impl;
import com.alibaba.nacos.naming.core.SubscribeManager;
import com.alibaba.nacos.naming.core.v2.metadata.ClusterMetadata;
import com.alibaba.nacos.naming.core.v2.metadata.ServiceMetadata;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import com.alibaba.nacos.naming.misc.Loggers;
import com.alibaba.nacos.naming.model.form.ServiceForm;
import com.alibaba.nacos.naming.pojo.Subscriber;
import com.alibaba.nacos.naming.selector.SelectorManager;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;

/**
 * Implementation of ServiceHandler that handles service-related operations.
 *
 * @author zhangyukun
 */
@org.springframework.stereotype.Service
public class ServiceInnerHandler implements ServiceHandler {
    
    private final ServiceOperatorV2Impl serviceOperatorV2;
    
    private final SelectorManager selectorManager;
    
    private final CatalogServiceV2Impl catalogServiceV2;
    
    private final SubscribeManager subscribeManager;
    
    private final ClusterOperatorV2Impl clusterOperatorV2;
    
    @Autowired
    public ServiceInnerHandler(ServiceOperatorV2Impl serviceOperatorV2, SelectorManager selectorManager,
            CatalogServiceV2Impl catalogServiceV2, SubscribeManager subscribeManager,
            ClusterOperatorV2Impl clusterOperatorV2) {
        this.serviceOperatorV2 = serviceOperatorV2;
        this.selectorManager = selectorManager;
        this.catalogServiceV2 = catalogServiceV2;
        this.subscribeManager = subscribeManager;
        this.clusterOperatorV2 = clusterOperatorV2;
    }
    
    @Override
    public void createService(ServiceForm serviceForm, ServiceMetadata serviceMetadata) throws Exception {
        serviceOperatorV2.create(com.alibaba.nacos.naming.core.v2.pojo.Service.newService(serviceForm.getNamespaceId(),
                serviceForm.getGroupName(), serviceForm.getServiceName(), serviceForm.getEphemeral()), serviceMetadata);
        NotifyCenter.publishEvent(
                new RegisterServiceTraceEvent(System.currentTimeMillis(), serviceForm.getNamespaceId(),
                        serviceForm.getGroupName(), serviceForm.getServiceName()));
    }
    
    @Override
    public void deleteService(String namespaceId, String serviceName, String groupName) throws Exception {
        serviceOperatorV2.delete(
                com.alibaba.nacos.naming.core.v2.pojo.Service.newService(namespaceId, groupName, serviceName));
        NotifyCenter.publishEvent(
                new DeregisterServiceTraceEvent(System.currentTimeMillis(), namespaceId, groupName, serviceName));
    }
    
    @Override
    public void updateService(ServiceForm serviceForm, com.alibaba.nacos.naming.core.v2.pojo.Service service,
            ServiceMetadata serviceMetadata, Map<String, String> metadata) throws Exception {
        serviceOperatorV2.update(service, serviceMetadata);
        NotifyCenter.publishEvent(new UpdateServiceTraceEvent(System.currentTimeMillis(), serviceForm.getNamespaceId(),
                serviceForm.getGroupName(), serviceForm.getServiceName(), metadata));
    }
    
    @Override
    public List<String> getSelectorTypeList() {
        return selectorManager.getAllSelectorTypes();
    }
    
    @Override
    public ObjectNode getSubscribers(int pageNo, int pageSize, String namespaceId, String serviceName, String groupName,
            boolean aggregation) throws Exception {
        
        Service service = Service.newService(namespaceId, groupName, serviceName);
        
        ObjectNode result = JacksonUtils.createEmptyJsonNode();
        
        int count = 0;
        
        try {
            List<Subscriber> subscribers = subscribeManager.getSubscribers(service, aggregation);
            
            int start = (pageNo - 1) * pageSize;
            if (start < 0) {
                start = 0;
            }
            
            int end = start + pageSize;
            count = subscribers.size();
            if (end > count) {
                end = count;
            }
            
            result.replace("subscribers", JacksonUtils.transferToJsonNode(subscribers.subList(start, end)));
            result.put("count", count);
            
            return result;
        } catch (Exception e) {
            Loggers.SRV_LOG.warn("query subscribers failed!", e);
            result.replace("subscribers", JacksonUtils.createEmptyArrayNode());
            result.put("count", count);
            return result;
        }
    }
    
    @Override
    public Object getServiceList(boolean withInstances, String namespaceId, int pageNo, int pageSize,
            String serviceName, String groupName, String containedInstance, boolean hasIpCount) throws NacosException {
        if (withInstances) {
            return catalogServiceV2.pageListServiceDetail(namespaceId, groupName, serviceName, pageNo, pageSize);
        }
        return catalogServiceV2.pageListService(namespaceId, groupName, serviceName, pageNo, pageSize,
                containedInstance, hasIpCount);
    }
    
    @Override
    public Object getServiceDetail(String namespaceId, String serviceName, String groupName) throws NacosException {
        return catalogServiceV2.getServiceDetail(namespaceId, groupName, serviceName);
    }
    
    @Override
    public void updateClusterMetadata(String namespaceId, String serviceName, String clusterName,
            ClusterMetadata clusterMetadata) throws Exception {
        clusterOperatorV2.updateClusterMetadata(namespaceId, serviceName, clusterName, clusterMetadata);
    }
    
}

