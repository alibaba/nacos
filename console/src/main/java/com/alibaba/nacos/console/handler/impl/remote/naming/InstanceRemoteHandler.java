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
import com.alibaba.nacos.console.handler.impl.remote.EnabledRemoteHandler;
import com.alibaba.nacos.console.handler.naming.InstanceHandler;
import com.alibaba.nacos.naming.model.form.InstanceForm;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;

/**
 * Remote Implementation of InstanceHandler that handles instance-related operations.
 *
 * @author xiweng.yy
 */
@Service
@EnabledRemoteHandler
public class InstanceRemoteHandler implements InstanceHandler {
    
    public InstanceRemoteHandler() {
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
        // TODO get from nacos servers
        return JacksonUtils.createEmptyJsonNode();
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
        // TODO get from nacos servers
    }
}

