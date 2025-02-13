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

package com.alibaba.nacos.console.handler.naming;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.pojo.maintainer.ServiceDetailInfo;
import com.alibaba.nacos.naming.core.v2.metadata.ClusterMetadata;
import com.alibaba.nacos.naming.core.v2.metadata.ServiceMetadata;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import com.alibaba.nacos.naming.model.form.ServiceForm;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.List;
import java.util.Map;

/**
 * Interface for handling service-related operations.
 *
 * @author zhangyukun
 */
public interface ServiceHandler {
    
    /**
     * Create a new service.
     *
     * @param serviceForm     the service form containing the service details
     * @param serviceMetadata the service metadata created from serviceForm
     * @throws Exception if an error occurs during service creation
     */
    void createService(ServiceForm serviceForm, ServiceMetadata serviceMetadata) throws Exception;
    
    /**
     * Delete an existing service.
     *
     * @param namespaceId the namespace ID
     * @param serviceName the service name
     * @param groupName   the group name
     * @throws Exception if an error occurs during service deletion
     */
    void deleteService(String namespaceId, String serviceName, String groupName) throws Exception;
    
    /**
     * Update an existing service.
     *
     * @param serviceForm     the service form containing the service details
     * @param service         the service object created from serviceForm
     * @param serviceMetadata the service metadata created from serviceForm
     * @param metadata        the service metadata
     * @throws Exception if an error occurs during service update
     */
    void updateService(ServiceForm serviceForm, Service service, ServiceMetadata serviceMetadata,
            Map<String, String> metadata) throws Exception;
    
    /**
     * Get all selector types.
     *
     * @return a list of selector types
     * @throws NacosException if an error occurs during get selector types
     */
    List<String> getSelectorTypeList() throws NacosException;
    
    /**
     * Get the list of subscribers for a service.
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
    ObjectNode getSubscribers(int pageNo, int pageSize, String namespaceId, String serviceName, String groupName,
            boolean aggregation) throws Exception;
    
    /**
     * List service detail information.
     *
     * @param withInstances whether to include instances
     * @param namespaceId   the namespace ID
     * @param pageNo        the page number
     * @param pageSize      the size of the page
     * @param serviceName   the service name
     * @param groupName     the group name
     * @param hasIpCount    whether to filter services with empty instances
     * @return service detail information
     * @throws NacosException if an error occurs during fetching service details
     */
    Object getServiceList(boolean withInstances, String namespaceId, int pageNo, int pageSize, String serviceName,
            String groupName, boolean hasIpCount) throws NacosException;
    
    /**
     * Get the detail of a specific service.
     *
     * @param namespaceId the namespace ID
     * @param serviceName the service name without group
     * @param groupName   the group name
     * @return service detail information
     * @throws NacosException if an error occurs during fetching service details
     */
    ServiceDetailInfo getServiceDetail(String namespaceId, String serviceName, String groupName) throws NacosException;
    
    /**
     * Update the metadata of a cluster.
     *
     * @param namespaceId     the namespace ID
     * @param serviceName     the service name
     * @param clusterName     the cluster name
     * @param clusterMetadata the metadata for the cluster
     * @throws Exception if the update operation fails
     */
    void updateClusterMetadata(String namespaceId, String serviceName, String clusterName,
            ClusterMetadata clusterMetadata) throws Exception;
}

