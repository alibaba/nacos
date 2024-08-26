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
import com.alibaba.nacos.console.config.ConsoleConfig;
import com.alibaba.nacos.console.handler.inner.naming.ServiceInnerHandler;
import com.alibaba.nacos.console.handler.naming.ServiceHandler;
import com.alibaba.nacos.naming.core.v2.metadata.ServiceMetadata;

import com.alibaba.nacos.naming.model.form.ServiceForm;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Proxy class for handling service-related operations.
 *
 * @author zhangyukun
 */
@Service
public class ServiceProxy {
    
    private final Map<String, ServiceHandler> serviceHandlerMap = new HashMap<>();
    
    private final ConsoleConfig consoleConfig;
    
    /**
     * Constructs a new ServiceProxy with the given ServiceInnerHandler and ConsoleConfig. The handler is mapped to a
     * deployment type key.
     *
     * @param serviceInnerHandler the default implementation of ServiceHandler
     * @param consoleConfig       the console configuration used to determine the deployment type
     */
    public ServiceProxy(ServiceInnerHandler serviceInnerHandler, ConsoleConfig consoleConfig) {
        this.serviceHandlerMap.put("merged", serviceInnerHandler);
        this.consoleConfig = consoleConfig;
    }
    
    /**
     * Creates a new service by delegating the operation to the appropriate handler.
     *
     * @param serviceForm the service form containing the service details
     * @throws Exception if an error occurs during service creation
     */
    public void createService(ServiceForm serviceForm, ServiceMetadata serviceMetadata) throws Exception {
        ServiceHandler serviceHandler = serviceHandlerMap.get(consoleConfig.getType());
        if (serviceHandler == null) {
            throw new IllegalArgumentException("Invalid deployment type");
        }
        serviceHandler.createService(serviceForm, serviceMetadata);
    }
    
    /**
     * Deletes an existing service by delegating the operation to the appropriate handler.
     *
     * @param namespaceId the namespace ID
     * @param serviceName the service name
     * @param groupName   the group name
     * @throws Exception if an error occurs during service deletion
     */
    public void deleteService(String namespaceId, String serviceName, String groupName) throws Exception {
        ServiceHandler serviceHandler = serviceHandlerMap.get(consoleConfig.getType());
        if (serviceHandler == null) {
            throw new IllegalArgumentException("Invalid deployment type");
        }
        serviceHandler.deleteService(namespaceId, serviceName, groupName);
    }
    
    /**
     * Updates an existing service by delegating the operation to the appropriate handler.
     *
     * @param serviceForm     the service form containing the service details
     * @param service         the service object created from serviceForm
     * @param serviceMetadata the service metadata created from serviceForm
     * @throws Exception if an error occurs during service update
     */
    public void updateService(ServiceForm serviceForm, com.alibaba.nacos.naming.core.v2.pojo.Service service,
            ServiceMetadata serviceMetadata, Map<String, String> metadata) throws Exception {
        ServiceHandler serviceHandler = serviceHandlerMap.get(consoleConfig.getType());
        if (serviceHandler == null) {
            throw new IllegalArgumentException("Invalid deployment type");
        }
        serviceHandler.updateService(serviceForm, service, serviceMetadata, metadata);
    }
    
    /**
     * Retrieves all selector types by delegating the operation to the appropriate handler.
     *
     * @return a list of selector types
     */
    public List<String> getSelectorTypeList() {
        ServiceHandler serviceHandler = serviceHandlerMap.get(consoleConfig.getType());
        if (serviceHandler == null) {
            throw new IllegalArgumentException("Invalid deployment type");
        }
        return serviceHandler.getSelectorTypeList();
    }
    
    /**
     * Retrieves the list of subscribers for a service by delegating the operation to the appropriate handler.
     *
     * @param pageNo      the page number
     * @param pageSize    the size of the page
     * @param namespaceId the namespace ID
     * @param serviceName the service name
     * @param aggregation whether to aggregate the results
     * @return a JSON node containing the list of subscribers
     * @throws Exception if an error occurs during fetching subscribers
     */
    public ObjectNode getSubscribers(int pageNo, int pageSize, String namespaceId, String serviceName,
            boolean aggregation) throws Exception {
        ServiceHandler serviceHandler = serviceHandlerMap.get(consoleConfig.getType());
        if (serviceHandler == null) {
            throw new IllegalArgumentException("Invalid deployment type");
        }
        return serviceHandler.getSubscribers(pageNo, pageSize, namespaceId, serviceName, aggregation);
    }
    
    /**
     * Retrieves the list of services and their details by delegating the operation to the appropriate handler.
     *
     * @param withInstances     whether to include instances
     * @param namespaceId       the namespace ID
     * @param pageNo            the page number
     * @param pageSize          the size of the page
     * @param serviceName       the service name
     * @param groupName         the group name
     * @param containedInstance instance name pattern which will be contained in detail
     * @param hasIpCount        whether to filter services with empty instances
     * @return service detail information
     * @throws NacosException if an error occurs during fetching service details
     */
    public Object getServiceList(boolean withInstances, String namespaceId, int pageNo, int pageSize,
            String serviceName, String groupName, String containedInstance, boolean hasIpCount) throws NacosException {
        ServiceHandler serviceHandler = serviceHandlerMap.get(consoleConfig.getType());
        if (serviceHandler == null) {
            throw new IllegalArgumentException("Invalid deployment type");
        }
        return serviceHandler.getServiceList(withInstances, namespaceId, pageNo, pageSize, serviceName, groupName,
                containedInstance, hasIpCount);
    }
    
    /**
     * Retrieves the details of a specific service by delegating the operation to the appropriate handler.
     *
     * @param namespaceId             the namespace ID
     * @param serviceNameWithoutGroup the service name without group
     * @param groupName               the group name
     * @return service detail information
     * @throws NacosException if an error occurs during fetching service details
     */
    public Object getServiceDetail(String namespaceId, String serviceNameWithoutGroup, String groupName)
            throws NacosException {
        ServiceHandler serviceHandler = serviceHandlerMap.get(consoleConfig.getType());
        if (serviceHandler == null) {
            throw new IllegalArgumentException("Invalid deployment type");
        }
        return serviceHandler.getServiceDetail(namespaceId, serviceNameWithoutGroup, groupName);
    }
}

