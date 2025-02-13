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
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.naming.utils.NamingUtils;
import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.common.trace.event.naming.UpdateInstanceTraceEvent;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.console.handler.impl.inner.EnabledInnerHandler;
import com.alibaba.nacos.console.handler.naming.InstanceHandler;
import com.alibaba.nacos.core.utils.PageUtil;
import com.alibaba.nacos.naming.core.CatalogServiceV2Impl;
import com.alibaba.nacos.naming.core.InstanceOperatorClientImpl;
import com.alibaba.nacos.naming.model.form.InstanceForm;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Implementation of InstanceHandler that handles instance-related operations.
 *
 * @author zhangyukun
 */
@Service
@EnabledInnerHandler
public class InstanceInnerHandler implements InstanceHandler {
    
    private final CatalogServiceV2Impl catalogServiceV2;
    
    private final InstanceOperatorClientImpl instanceServiceV2;
    
    /**
     * Constructs a new InstanceInnerHandler with the provided dependencies.
     *
     * @param catalogServiceV2 the service for catalog-related operations
     */
    public InstanceInnerHandler(CatalogServiceV2Impl catalogServiceV2, InstanceOperatorClientImpl instanceServiceV2) {
        this.catalogServiceV2 = catalogServiceV2;
        this.instanceServiceV2 = instanceServiceV2;
    }
    
    @Override
    public ObjectNode listInstances(String namespaceId, String serviceNameWithoutGroup, String groupName, int page,
            int pageSize) {
        List<? extends Instance> instances = catalogServiceV2.listAllInstances(namespaceId, groupName,
                serviceNameWithoutGroup);
        List<? extends Instance> resultInstances = PageUtil.subPage(instances, page, pageSize);
        ObjectNode result = JacksonUtils.createEmptyJsonNode();
        result.replace("instances", JacksonUtils.transferToJsonNode(resultInstances));
        result.put("count", instances.size());
        return result;
    }
    
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
    
}

