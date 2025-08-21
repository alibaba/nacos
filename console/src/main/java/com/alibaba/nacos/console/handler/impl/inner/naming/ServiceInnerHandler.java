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

package com.alibaba.nacos.console.handler.impl.inner.naming;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.api.naming.pojo.maintainer.ServiceDetailInfo;
import com.alibaba.nacos.api.naming.pojo.maintainer.SubscriberInfo;
import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.common.trace.event.naming.DeregisterServiceTraceEvent;
import com.alibaba.nacos.common.trace.event.naming.RegisterServiceTraceEvent;
import com.alibaba.nacos.common.trace.event.naming.UpdateServiceTraceEvent;
import com.alibaba.nacos.console.handler.impl.ConditionFunctionEnabled;
import com.alibaba.nacos.console.handler.impl.inner.EnabledInnerHandler;
import com.alibaba.nacos.console.handler.naming.ServiceHandler;
import com.alibaba.nacos.naming.core.CatalogServiceV2Impl;
import com.alibaba.nacos.naming.core.ClusterOperatorV2Impl;
import com.alibaba.nacos.naming.core.ServiceOperatorV2Impl;
import com.alibaba.nacos.naming.core.v2.ServiceManager;
import com.alibaba.nacos.naming.core.v2.metadata.ClusterMetadata;
import com.alibaba.nacos.naming.core.v2.metadata.ServiceMetadata;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import com.alibaba.nacos.naming.model.form.ServiceForm;
import com.alibaba.nacos.naming.selector.SelectorManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;

import java.util.List;

/**
 * Implementation of ServiceHandler that handles service-related operations.
 *
 * @author zhangyukun
 */
@org.springframework.stereotype.Service
@EnabledInnerHandler
@Conditional(ConditionFunctionEnabled.ConditionNamingEnabled.class)
public class ServiceInnerHandler implements ServiceHandler {
    
    private final ServiceOperatorV2Impl serviceOperatorV2;
    
    private final SelectorManager selectorManager;
    
    private final CatalogServiceV2Impl catalogServiceV2;
    
    private final ClusterOperatorV2Impl clusterOperatorV2;
    
    @Autowired
    public ServiceInnerHandler(ServiceOperatorV2Impl serviceOperatorV2, SelectorManager selectorManager,
            CatalogServiceV2Impl catalogServiceV2, ClusterOperatorV2Impl clusterOperatorV2) {
        this.serviceOperatorV2 = serviceOperatorV2;
        this.selectorManager = selectorManager;
        this.catalogServiceV2 = catalogServiceV2;
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
    public void updateService(ServiceForm serviceForm, ServiceMetadata serviceMetadata) throws Exception {
        Service service = Service.newService(serviceForm.getNamespaceId(), serviceForm.getGroupName(),
                serviceForm.getServiceName());
        if (!ServiceManager.getInstance().containSingleton(service)) {
            throw new NacosApiException(NacosException.NOT_FOUND, ErrorCode.SERVICE_NOT_EXIST,
                    "service %s is not exist.".formatted(service.toString()));
        }
        service = ServiceManager.getInstance().getSingleton(service);
        serviceOperatorV2.update(service, serviceMetadata);
        NotifyCenter.publishEvent(new UpdateServiceTraceEvent(System.currentTimeMillis(), serviceForm.getNamespaceId(),
                serviceForm.getGroupName(), serviceForm.getServiceName(), serviceMetadata.getExtendData()));
    }
    
    @Override
    public List<String> getSelectorTypeList() throws NacosException {
        return selectorManager.getAllSelectorTypes();
    }
    
    @Override
    public Page<SubscriberInfo> getSubscribers(int pageNo, int pageSize, String namespaceId, String serviceName,
            String groupName, boolean aggregation) throws Exception {
        return serviceOperatorV2.getSubscribers(namespaceId, serviceName, groupName, aggregation, pageNo, pageSize);
    }
    
    @Override
    public Object getServiceList(boolean withInstances, String namespaceId, int pageNo, int pageSize,
            String serviceName, String groupName, boolean ignoreEmptyService) throws NacosException {
        if (withInstances) {
            return catalogServiceV2.pageListServiceDetail(namespaceId, groupName, serviceName, pageNo, pageSize);
        }
        return catalogServiceV2.listService(namespaceId, groupName, serviceName, pageNo, pageSize, ignoreEmptyService);
    }
    
    @Override
    public ServiceDetailInfo getServiceDetail(String namespaceId, String serviceName, String groupName)
            throws NacosException {
        return catalogServiceV2.getServiceDetail(namespaceId, groupName, serviceName);
    }
    
    @Override
    public void updateClusterMetadata(String namespaceId, String groupName, String serviceName, String clusterName,
            ClusterMetadata clusterMetadata) throws Exception {
        clusterOperatorV2.updateClusterMetadata(namespaceId, groupName, serviceName, clusterName, clusterMetadata);
    }
    
}

