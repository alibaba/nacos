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
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.console.handler.naming.InstanceHandler;
import com.alibaba.nacos.naming.model.form.InstanceForm;
import org.springframework.stereotype.Service;

/**
 * Proxy class for handling instance-related operations.
 *
 * @author zhangyukun
 */
@Service
public class InstanceProxy {
    
    private final InstanceHandler instanceHandler;
    
    /**
     * Constructs a new InstanceProxy with the given InstanceInnerHandler and ConsoleConfig.
     *
     * @param instanceHandler the default implementation of InstanceHandler
     */
    public InstanceProxy(InstanceHandler instanceHandler) {
        this.instanceHandler = instanceHandler;
    }
    
    /**
     * Retrieve a list of instances for a specific service and returns as an ObjectNode.
     *
     * @param namespaceId             the namespace ID
     * @param serviceNameWithoutGroup the service name without group
     * @param groupName               the group name
     * @param clusterName             the cluster name
     * @param page                    the page number
     * @param pageSize                the size of the page
     * @return the page object of {@link Instance}
     * @throws IllegalArgumentException if the deployment type is invalid
     * @throws NacosException           if the list operation fails
     */
    public Page<? extends Instance> listInstances(String namespaceId, String serviceNameWithoutGroup, String groupName,
            String clusterName, int page, int pageSize) throws NacosException {
        return instanceHandler.listInstances(namespaceId, serviceNameWithoutGroup, groupName, clusterName, page,
                pageSize);
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
        instanceHandler.updateInstance(instanceForm, instance);
    }
}

