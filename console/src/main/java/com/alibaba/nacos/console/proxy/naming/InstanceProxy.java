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

package com.alibaba.nacos.console.proxy.naming;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.console.config.ConsoleConfig;
import com.alibaba.nacos.console.handler.inner.naming.InstanceInnerHandler;
import com.alibaba.nacos.console.handler.naming.InstanceHandler;
import com.alibaba.nacos.naming.model.form.InstanceForm;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * Proxy class for handling instance-related operations.
 *
 * @author zhangyukun
 */
@Service
public class InstanceProxy {
    
    private final Map<String, InstanceHandler> instanceHandlerMap = new HashMap<>();
    
    private final ConsoleConfig consoleConfig;
    
    /**
     * Constructs a new InstanceProxy with the given InstanceInnerHandler and ConsoleConfig.
     *
     * @param instanceInnerHandler the default implementation of InstanceHandler
     * @param consoleConfig        the console configuration used to determine the deployment type
     */
    public InstanceProxy(InstanceInnerHandler instanceInnerHandler, ConsoleConfig consoleConfig) {
        this.instanceHandlerMap.put("merged", instanceInnerHandler);
        this.consoleConfig = consoleConfig;
    }
    
    /**
     * Retrieve a list of instances for a specific service and returns as an ObjectNode.
     *
     * @param namespaceId             the namespace ID
     * @param serviceNameWithoutGroup the service name without group
     * @param groupName               the group name
     * @param page                    the page number
     * @param pageSize                the size of the page
     * @param healthyOnly             filter by healthy instances only
     * @param enabledOnly             filter by enabled instances only
     * @return a JSON node containing the instances information
     * @throws IllegalArgumentException if the deployment type is invalid
     */
    public ObjectNode listInstances(String namespaceId, String serviceNameWithoutGroup, String groupName, int page,
            int pageSize, Boolean healthyOnly, Boolean enabledOnly) {
        InstanceHandler instanceHandler = instanceHandlerMap.get(consoleConfig.getType());
        if (instanceHandler == null) {
            throw new IllegalArgumentException("Invalid deployment type");
        }
        return instanceHandler.listInstances(namespaceId, serviceNameWithoutGroup, groupName, page, pageSize,
                healthyOnly, enabledOnly);
    }
    
    /**
     * Updates an instance.
     *
     * @param instanceForm the form containing instance data
     * @param instance     the instance to update
     * @throws NacosException           if the update operation fails
     * @throws IllegalArgumentException if the deployment type is invalid
     */
    public void updateInstance(InstanceForm instanceForm, Instance instance) throws NacosException {
        InstanceHandler instanceHandler = instanceHandlerMap.get(consoleConfig.getType());
        if (instanceHandler == null) {
            throw new IllegalArgumentException("Invalid deployment type");
        }
        instanceHandler.updateInstance(instanceForm, instance);
    }
}

