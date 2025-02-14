/*
 * Copyright 1999-$toady.year Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.maintainer.client.naming;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.naming.pojo.healthcheck.AbstractHealthChecker;
import com.alibaba.nacos.api.naming.pojo.maintainer.InstanceMetadataBatchResult;
import com.alibaba.nacos.api.naming.pojo.maintainer.MetricsInfo;
import com.alibaba.nacos.maintainer.client.core.CoreMaintainerService;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.List;
import java.util.Map;

/**
 * Nacos Naming module maintainer service.
 *
 * @author Nacos
 */
public interface NamingMaintainerService extends CoreMaintainerService, ServiceMaintainerService {
    
    /**
     * Get system metrics.
     *
     * @param onlyStatus whether to return only status information
     * @return the metrics information
     * @throws NacosException if an error occurs
     */
    MetricsInfo getMetrics(boolean onlyStatus) throws NacosException;
    
    /**
     * Set the log level.
     *
     * @param logName  the name of the logger
     * @param logLevel the new log level
     * @return the result of the operation
     * @throws NacosException if an error occurs
     */
    String setLogLevel(String logName, String logLevel) throws NacosException;
    
    // ------------------------- Instance Operations -------------------------
    
    /**
     * Register a new instance.
     *
     * @param namespaceId the namespace ID
     * @param groupName   the group name
     * @param serviceName the service name
     * @param clusterName the cluster name
     * @param ip          the IP address of the instance
     * @param port        the port of the instance
     * @param weight      the weight of the instance
     * @param healthy     whether the instance is healthy
     * @param enabled     whether the instance is enabled
     * @param ephemeral   whether the instance is ephemeral
     * @param metadata    the metadata of the instance
     * @return the result of the operation
     * @throws NacosException if an error occurs
     */
    String registerInstance(String namespaceId, String groupName, String serviceName, String clusterName, String ip,
            int port, String weight, boolean healthy, boolean enabled, String ephemeral, String metadata)
            throws NacosException;
    
    /**
     * Deregister an instance.
     *
     * @param namespaceId the namespace ID
     * @param groupName   the group name
     * @param serviceName the service name
     * @param clusterName the cluster name
     * @param ip          the IP address of the instance
     * @param port        the port of the instance
     * @param weight      the weight of the instance
     * @param healthy     whether the instance is healthy
     * @param enabled     whether the instance is enabled
     * @param ephemeral   whether the instance is ephemeral
     * @param metadata    the metadata of the instance
     * @return the result of the operation
     * @throws NacosException if an error occurs
     */
    String deregisterInstance(String namespaceId, String groupName, String serviceName, String clusterName, String ip,
            int port, String weight, boolean healthy, boolean enabled, String ephemeral, String metadata)
            throws NacosException;
    
    /**
     * Update an existing instance.
     *
     * @param namespaceId the namespace ID
     * @param groupName   the group name
     * @param serviceName the service name
     * @param clusterName the cluster name
     * @param ip          the IP address of the instance
     * @param port        the port of the instance
     * @param weight      the updated weight
     * @param healthy     whether the instance is healthy
     * @param enabled     whether the instance is enabled
     * @param ephemeral   whether the instance is ephemeral
     * @param metadata    the updated metadata
     * @return the result of the operation
     * @throws NacosException if an error occurs
     */
    String updateInstance(String namespaceId, String groupName, String serviceName, String clusterName, String ip,
            int port, double weight, boolean healthy, boolean enabled, boolean ephemeral, String metadata)
            throws NacosException;
    
    /**
     * Batch update instance metadata.
     *
     * @param namespaceId     the namespace ID
     * @param groupName       the group name
     * @param serviceName     the service name
     * @param instance        the instance information
     * @param metadata        the metadata to update
     * @param consistencyType the consistency type
     * @return the result of the operation
     * @throws NacosException if an error occurs
     */
    InstanceMetadataBatchResult batchUpdateInstanceMetadata(String namespaceId, String groupName, String serviceName,
            String instance, Map<String, String> metadata, String consistencyType) throws NacosException;
    
    /**
     * Batch delete instance metadata.
     *
     * @param namespaceId     the namespace ID
     * @param groupName       the group name
     * @param serviceName     the service name
     * @param instance        the instance information
     * @param metadata        the metadata to delete
     * @param consistencyType the consistency type
     * @return the result of the operation
     * @throws NacosException if an error occurs
     */
    InstanceMetadataBatchResult batchDeleteInstanceMetadata(String namespaceId, String groupName, String serviceName,
            String instance, Map<String, String> metadata, String consistencyType) throws NacosException;
    
    /**
     * Partially update an instance.
     *
     * @param namespaceId the namespace ID
     * @param serviceName the service name
     * @param clusterName the cluster name
     * @param ip          the IP address of the instance
     * @param port        the port of the instance
     * @param weight      the updated weight
     * @param enabled     whether the instance is enabled
     * @param metadata    the updated metadata
     * @return the result of the operation
     * @throws NacosException if an error occurs
     */
    String partialUpdateInstance(String namespaceId, String serviceName, String clusterName, int ip, int port,
            double weight, boolean enabled, String metadata) throws NacosException;
    
    /**
     * List instances of a service.
     *
     * @param namespaceId the namespace ID
     * @param groupName   the group name
     * @param serviceName the service name
     * @param clusterName the cluster name
     * @param healthyOnly whether to list only healthy instances
     * @return the list of instances
     * @throws NacosException if an error occurs
     */
    List<Instance> listInstances(String namespaceId, String groupName, String serviceName, String clusterName,
            boolean healthyOnly) throws NacosException;
    
    /**
     * Get detailed information of an instance.
     *
     * @param namespaceId the namespace ID
     * @param groupName   the group name
     * @param serviceName the service name
     * @param clusterName the cluster name
     * @param ip          the IP address of the instance
     * @param port        the port of the instance
     * @return the instance detail information
     * @throws NacosException if an error occurs
     */
    Instance getInstanceDetail(String namespaceId, String groupName, String serviceName, String clusterName, String ip,
            int port) throws NacosException;
    
    // ------------------------- Health Check Operations -------------------------
    
    /**
     * Update the health status of an instance.
     *
     * @param namespaceId      the namespace ID
     * @param groupName        the group name
     * @param serviceName      the service name
     * @param clusterName      the cluster name
     * @param metadata         the metadata of the instance
     * @param ephemeral        whether the instance is ephemeral
     * @param protectThreshold the protect threshold
     * @param selector         the selector for the instance
     * @return the result of the operation
     * @throws NacosException if an error occurs
     */
    String updateInstanceHealthStatus(String namespaceId, String groupName, String serviceName, String clusterName,
            String metadata, boolean ephemeral, float protectThreshold, String selector) throws NacosException;
    
    /**
     * Get all health checkers.
     *
     * @return a map of health checkers
     * @throws NacosException if an error occurs
     */
    Map<String, AbstractHealthChecker> getHealthCheckers() throws NacosException;
    
    // ------------------------- Cluster Operations -------------------------
    
    /**
     * Update cluster configuration.
     *
     * @param namespaceId           the namespace ID
     * @param groupName             the group name
     * @param serviceName           the service name
     * @param clusterName           the cluster name
     * @param checkPort             the health check port
     * @param useInstancePort4Check whether to use the instance port for health check
     * @param healthChecker         the health checker configuration
     * @param metadata              the metadata of the cluster
     * @return the result of the operation
     * @throws NacosException if an error occurs
     */
    String updateCluster(String namespaceId, String groupName, String serviceName, String clusterName,
            Integer checkPort, Boolean useInstancePort4Check, String healthChecker, Map<String, String> metadata)
            throws NacosException;
    
    // ------------------------- Client Operations -------------------------
    
    /**
     * Get the list of all clients.
     *
     * @return the list of client IDs
     * @throws NacosException if an error occurs
     */
    List<String> getClientList() throws NacosException;
    
    /**
     * Get detailed information of a client.
     *
     * @param clientId the client ID
     * @return the client detail information
     * @throws NacosException if an error occurs
     */
    ObjectNode getClientDetail(String clientId) throws NacosException;
    
    /**
     * Get the list of services published by a client.
     *
     * @param clientId the client ID
     * @return the list of published services
     * @throws NacosException if an error occurs
     */
    List<ObjectNode> getPublishedServiceList(String clientId) throws NacosException;
    
    /**
     * Get the list of services subscribed by a client.
     *
     * @param clientId the client ID
     * @return the list of subscribed services
     * @throws NacosException if an error occurs
     */
    List<ObjectNode> getSubscribeServiceList(String clientId) throws NacosException;
    
    /**
     * Get the list of clients that published a specific service.
     *
     * @param namespaceId the namespace ID
     * @param groupName   the group name
     * @param serviceName the service name
     * @param ephemeral   whether the service is ephemeral
     * @param ip          the IP address of the client
     * @param port        the port of the client
     * @return the list of clients
     * @throws NacosException if an error occurs
     */
    List<ObjectNode> getPublishedClientList(String namespaceId, String groupName, String serviceName, boolean ephemeral,
            String ip, Integer port) throws NacosException;
    
    /**
     * Get the list of clients that subscribed to a specific service.
     *
     * @param namespaceId the namespace ID
     * @param groupName   the group name
     * @param serviceName the service name
     * @param ephemeral   whether the service is ephemeral
     * @param ip          the IP address of the client
     * @param port        the port of the client
     * @return the list of clients
     * @throws NacosException if an error occurs
     */
    List<ObjectNode> getSubscribeClientList(String namespaceId, String groupName, String serviceName, boolean ephemeral,
            String ip, Integer port) throws NacosException;
    
    /**
     * Get the responsible server for a client based on its IP and port.
     *
     * @param ip   the IP address of the client
     * @param port the port of the client
     * @return the responsible server information
     * @throws NacosException if an error occurs
     */
    ObjectNode getResponsibleServerForClient(String ip, String port) throws NacosException;
}
