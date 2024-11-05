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
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.naming.utils.NamingUtils;
import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.common.trace.event.naming.UpdateInstanceTraceEvent;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.console.handler.naming.InstanceHandler;
import com.alibaba.nacos.naming.core.CatalogServiceV2Impl;
import com.alibaba.nacos.naming.core.InstanceOperatorClientImpl;
import com.alibaba.nacos.naming.model.form.InstanceForm;
import com.alibaba.nacos.naming.model.form.InstanceMetadataBatchOperationForm;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Implementation of InstanceHandler that handles instance-related operations.
 *
 * @author zhangyukun
 */
@Service
public class InstanceInnerHandler implements InstanceHandler {
    
    private final CatalogServiceV2Impl catalogServiceV2;
    
    private final InstanceOperatorClientImpl instanceServiceV2;
    
    /**
     * Constructs a new InstanceInnerHandler with the provided dependencies.
     *
     * @param catalogServiceV2 the service for catalog-related operations
     */
    @Autowired
    public InstanceInnerHandler(CatalogServiceV2Impl catalogServiceV2, InstanceOperatorClientImpl instanceServiceV2) {
        this.catalogServiceV2 = catalogServiceV2;
        this.instanceServiceV2 = instanceServiceV2;
    }
    
    /**
     * Retrieves a list of instances for a specific service and returns as an ObjectNode.
     *
     * @param namespaceId             the namespace ID
     * @param serviceNameWithoutGroup the service name without group
     * @param groupName               the group name
     * @param page                    the page number
     * @param pageSize                the size of the page
     * @param healthyOnly             filter by healthy instances only
     * @param enabledOnly             filter by enabled instances only
     * @return a JSON node containing the instances information
     */
    @Override
    public ObjectNode listInstances(String namespaceId, String serviceNameWithoutGroup, String groupName, int page,
            int pageSize, Boolean healthyOnly, Boolean enabledOnly) {
        List<? extends Instance> instances = catalogServiceV2.listAllInstances(namespaceId, groupName,
                serviceNameWithoutGroup);
        int start = (page - 1) * pageSize;
        
        if (start < 0) {
            start = 0;
        }
        int end = start + pageSize;
        
        if (start > instances.size()) {
            start = instances.size();
        }
        
        if (end > instances.size()) {
            end = instances.size();
        }
        
        Stream<? extends Instance> stream = instances.stream();
        if (healthyOnly != null) {
            stream = stream.filter(instance -> instance.isHealthy() == healthyOnly);
        }
        if (enabledOnly != null) {
            stream = stream.filter(i -> i.isEnabled() == enabledOnly);
        }
        List<? extends Instance> ins = stream.collect(Collectors.toList());
        
        ObjectNode result = JacksonUtils.createEmptyJsonNode();
        if (ins.size() > start) {
            result.replace("instances", JacksonUtils.transferToJsonNode(ins.subList(start, end)));
        }
        result.put("count", ins.size());
        
        return result;
    }
    
    /**
     * Updates an instance.
     *
     * @param instanceForm the instanceForm
     * @param instance    the instance to update
     * @throws NacosException if the update operation fails
     */
    @Override
    public void updateInstance(InstanceForm instanceForm, Instance instance) throws NacosException {
        instanceServiceV2.updateInstance(instanceForm.getNamespaceId(), buildCompositeServiceName(instanceForm),
                instance);
        NotifyCenter.publishEvent(
                new UpdateInstanceTraceEvent(System.currentTimeMillis(), "", instanceForm.getNamespaceId(),
                        instanceForm.getGroupName(), instanceForm.getServiceName(), instance.getIp(),
                        instance.getPort(), instance.getMetadata()));
    }
    
    private String buildCompositeServiceName(InstanceForm instanceForm) {
        return NamingUtils.getGroupedName(instanceForm.getServiceName(), instanceForm.getGroupName());
    }
    
    private String buildCompositeServiceName(InstanceMetadataBatchOperationForm form) {
        return NamingUtils.getGroupedName(form.getServiceName(), form.getGroupName());
    }
}

