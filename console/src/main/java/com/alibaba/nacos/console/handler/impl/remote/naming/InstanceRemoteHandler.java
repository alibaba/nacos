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
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.console.handler.impl.remote.EnabledRemoteHandler;
import com.alibaba.nacos.console.handler.impl.remote.NacosMaintainerClientHolder;
import com.alibaba.nacos.console.handler.naming.InstanceHandler;
import com.alibaba.nacos.core.utils.PageUtil;
import com.alibaba.nacos.naming.model.form.InstanceForm;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Remote Implementation of InstanceHandler that handles instance-related operations.
 *
 * @author xiweng.yy
 */
@Service
@EnabledRemoteHandler
public class InstanceRemoteHandler implements InstanceHandler {
    
    private final NacosMaintainerClientHolder clientHolder;
    
    public InstanceRemoteHandler(NacosMaintainerClientHolder clientHolder) {
        this.clientHolder = clientHolder;
    }
    
    @Override
    public ObjectNode listInstances(String namespaceId, String serviceNameWithoutGroup, String groupName, int page,
            int pageSize) throws NacosException {
        List<Instance> instances = clientHolder.getNamingMaintainerService()
                .listInstances(namespaceId, groupName, serviceNameWithoutGroup, StringUtils.EMPTY, false);
        List<? extends Instance> resultInstances = PageUtil.subPage(instances, page, pageSize);
        ObjectNode result = JacksonUtils.createEmptyJsonNode();
        result.replace("instances", JacksonUtils.transferToJsonNode(resultInstances));
        result.put("count", instances.size());
        return result;
    }
    
    @Override
    public void updateInstance(InstanceForm instanceForm, Instance instance) throws NacosException {
        // TODO use instance directly after maintain client support input instance.
        clientHolder.getNamingMaintainerService()
                .updateInstance(instanceForm.getNamespaceId(), instanceForm.getGroupName(),
                        instanceForm.getServiceName(), instance.getClusterName(), instance.getIp(), instance.getPort(),
                        instance.getWeight(), instance.isHealthy(), instance.isEnabled(), instance.isEphemeral(),
                        JacksonUtils.toJson(instance.getMetadata()));
    }
}

