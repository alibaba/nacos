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
import com.alibaba.nacos.api.naming.pojo.maintainer.ServiceDetailInfo;
import com.alibaba.nacos.api.naming.pojo.maintainer.ServiceView;
import com.alibaba.nacos.api.naming.pojo.maintainer.SubscriberInfo;
import com.alibaba.nacos.console.handler.naming.ServiceHandler;
import com.alibaba.nacos.naming.core.v2.metadata.ClusterMetadata;
import com.alibaba.nacos.naming.core.v2.metadata.ServiceMetadata;
import com.alibaba.nacos.naming.model.form.ServiceForm;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Proxy class for handling service-related operations.
 *
 * @author zhangyukun
 */
@Service
public class ServiceProxy {
    
    private final ServiceHandler serviceHandler;
    
    /**
     * Constructs a new ServiceProxy with the given ServiceInnerHandler and ConsoleConfig. The handler is mapped to a
     * deployment type key.
     *
     * @param serviceHandler the default implementation of ServiceHandler
     */
    public ServiceProxy(ServiceHandler serviceHandler) {
        this.serviceHandler = serviceHandler;
    }
    
    /**
     * Creates a new service by delegating the operation to the appropriate handler.
     *
     * @param serviceForm the service form containing the service details
     * @throws Exception if an error occurs during service creation
     */
    public void createService(ServiceForm serviceForm, ServiceMetadata serviceMetadata) throws Exception {
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
        serviceHandler.deleteService(namespaceId, serviceName, groupName);
    }
    
    /**
     * Updates an existing service by delegating the operation to the appropriate handler.
     *
     * @param serviceForm     the service form containing the service details
     * @param serviceMetadata the service metadata created from serviceForm
     * @throws Exception if an error occurs during service update
     */
    public void updateService(ServiceForm serviceForm, ServiceMetadata serviceMetadata) throws Exception {
        serviceHandler.updateService(serviceForm, serviceMetadata);
    }
    
    /**
     * Retrieves all selector types by delegating the operation to the appropriate handler.
     *
     * @return a list of selector types
     */
    public List<String> getSelectorTypeList() throws NacosException {
        return serviceHandler.getSelectorTypeList();
    }
    
    /**
     * Retrieves the list of subscribers for a service by delegating the operation to the appropriate handler.
     *
     * @param pageNo      the page number
     * @param pageSize    the size of the page
     * @param namespaceId the namespace ID
     * @param serviceName the service name
     * @param groupName   the group name
     * @param aggregation whether to aggregate the results
     * @return a JSON node containing the list of subscribers
     * @throws Exception if an error occurs during fetching subscribers
     */
    public Page<SubscriberInfo> getSubscribers(int pageNo, int pageSize, String namespaceId, String serviceName,
            String groupName, boolean aggregation) throws Exception {
        return serviceHandler.getSubscribers(pageNo, pageSize, namespaceId, serviceName, groupName, aggregation);
    }
    
    /**
     * Retrieves the list of services and their details by delegating the operation to the appropriate handler.
     *
     * @param withInstances whether to include instances
     * @param namespaceId   the namespace ID
     * @param pageNo        the page number
     * @param pageSize      the size of the page
     * @param serviceName   the service name
     * @param groupName     the group name
     * @param hasIpCount    whether to filter services with empty instances
     * @return if withInstances is {@code true}, return List of {@link ServiceDetailInfo}, otherwise return List of {@link ServiceView}
     * @throws NacosException if an error occurs during fetching service details
     */
    public Object getServiceList(boolean withInstances, String namespaceId, int pageNo, int pageSize,
            String serviceName, String groupName, boolean hasIpCount) throws NacosException {
        return serviceHandler.getServiceList(withInstances, namespaceId, pageNo, pageSize, serviceName, groupName,
                hasIpCount);
    }
    
    /**
     * Retrieves the details of a specific service by delegating the operation to the appropriate handler.
     *
     * @param namespaceId             the namespace ID
     * @param serviceName the service name without group
     * @param groupName               the group name
     * @return service detail information
     * @throws NacosException if an error occurs during fetching service details
     */
    public ServiceDetailInfo getServiceDetail(String namespaceId, String serviceName, String groupName)
            throws NacosException {
        return serviceHandler.getServiceDetail(namespaceId, serviceName, groupName);
    }
    
    /**
     * Updates the metadata of a cluster.
     *
     * @param namespaceId     the namespace ID
     * @param groupName       the group name
     * @param serviceName     the service name
     * @param clusterName     the cluster name
     * @param clusterMetadata the metadata for the cluster
     * @throws Exception                if the update operation fails
     * @throws IllegalArgumentException if the deployment type is invalid
     */
    public void updateClusterMetadata(String namespaceId, String groupName, String serviceName, String clusterName,
            ClusterMetadata clusterMetadata) throws Exception {
        serviceHandler.updateClusterMetadata(namespaceId, groupName, serviceName, clusterName, clusterMetadata);
    }
}

