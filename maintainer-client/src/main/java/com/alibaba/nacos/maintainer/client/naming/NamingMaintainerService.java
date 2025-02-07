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
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.api.naming.pojo.ServiceInfo;
import com.alibaba.nacos.api.naming.pojo.healthcheck.AbstractHealthChecker;
import com.alibaba.nacos.maintainer.client.model.core.Connection;
import com.alibaba.nacos.maintainer.client.model.core.IdGeneratorVO;
import com.alibaba.nacos.maintainer.client.model.core.Member;
import com.alibaba.nacos.maintainer.client.model.core.ServerLoaderMetrics;
import com.alibaba.nacos.maintainer.client.model.naming.InstanceDetailInfoVo;
import com.alibaba.nacos.maintainer.client.model.naming.InstanceMetadataBatchOperationVo;
import com.alibaba.nacos.maintainer.client.model.naming.MetricsInfoVo;
import com.alibaba.nacos.maintainer.client.model.naming.ServiceDetailInfo;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Nacos Naming module maintainer service.
 *
 * @author Nacos
 */
public interface NamingMaintainerService {
    
    // ------------------------- Service Operations -------------------------
    /**
     * Create a new service with the given service name.
     *
     * @param serviceName the name of the service
     * @return the result of the operation
     * @throws NacosException if an error occurs
     */
    String createService(String serviceName) throws NacosException;
    
    /**
     * Create a new service with detailed parameters.
     *
     * @param namespaceId      the namespace ID
     * @param groupName        the group name
     * @param serviceName      the service name
     * @param metadata         the metadata of the service
     * @param ephemeral        whether the service is ephemeral
     * @param protectThreshold the protect threshold
     * @param selector         the selector for the service
     * @return the result of the operation
     * @throws NacosException if an error occurs
     */
    String createService(String namespaceId, String groupName, String serviceName, String metadata, boolean ephemeral,
            float protectThreshold, String selector) throws NacosException;
    
    /**
     * Update an existing service.
     *
     * @param namespaceId      the namespace ID
     * @param groupName        the group name
     * @param serviceName      the service name
     * @param metadata         the updated metadata
     * @param ephemeral        whether the service is ephemeral
     * @param protectThreshold the updated protect threshold
     * @param selector         the updated selector
     * @return the result of the operation
     * @throws NacosException if an error occurs
     */
    String updateService(String namespaceId, String groupName, String serviceName, String metadata, boolean ephemeral,
            float protectThreshold, String selector) throws NacosException;
    
    /**
     * Remove a service.
     *
     * @param namespaceId the namespace ID
     * @param groupName   the group name
     * @param serviceName the service name
     * @return the result of the operation
     * @throws NacosException if an error occurs
     */
    String removeService(String namespaceId, String groupName, String serviceName) throws NacosException;
    
    /**
     * Get detailed information of a service.
     *
     * @param namespaceId the namespace ID
     * @param groupName   the group name
     * @param serviceName the service name
     * @return the service detail information
     * @throws NacosException if an error occurs
     */
    ServiceDetailInfo getServiceDetail(String namespaceId, String groupName, String serviceName) throws NacosException;
    
    /**
     * List services with pagination.
     *
     * @param namespaceId the namespace ID
     * @param groupName   the group name
     * @param selector    the selector for filtering
     * @param pageNo      the page number
     * @param pageSize    the page size
     * @return the list of services
     * @throws NacosException if an error occurs
     */
    Object listServices(String namespaceId, String groupName, String selector, int pageNo, int pageSize)
            throws NacosException;
    
    /**
     * Search service names by expression.
     *
     * @param namespaceId the namespace ID
     * @param expr        the search expression
     * @return the search result
     * @throws NacosException if an error occurs
     */
    ObjectNode searchService(String namespaceId, String expr) throws NacosException;
    
    /**
     * Get subscribers of a service with pagination.
     *
     * @param namespaceId the namespace ID
     * @param groupName   the group name
     * @param serviceName the service name
     * @param pageNo      the page number
     * @param pageSize    the page size
     * @param aggregation whether to aggregate results
     * @return the list of subscribers
     * @throws NacosException if an error occurs
     */
    Result<ObjectNode> getSubscribers(String namespaceId, String groupName, String serviceName, int pageNo,
            int pageSize, boolean aggregation) throws NacosException;
    
    /**
     * List all selector types.
     *
     * @return the list of selector types
     * @throws NacosException if an error occurs
     */
    List<String> listSelectorTypes() throws NacosException;
    
    /**
     * Get system metrics.
     *
     * @param onlyStatus whether to return only status information
     * @return the metrics information
     * @throws NacosException if an error occurs
     */
    MetricsInfoVo getMetrics(boolean onlyStatus) throws NacosException;
    
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
            int port, String weight, boolean healthy, boolean enabled, String ephemeral, String metadata)
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
    InstanceMetadataBatchOperationVo batchUpdateInstanceMetadata(String namespaceId, String groupName,
            String serviceName, String instance, Map<String, String> metadata, String consistencyType)
            throws NacosException;
    
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
    InstanceMetadataBatchOperationVo batchDeleteInstanceMetadata(String namespaceId, String groupName,
            String serviceName, String instance, Map<String, String> metadata, String consistencyType)
            throws NacosException;
    
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
     * @param ip          the IP address of the instance (optional)
     * @param port        the port of the instance (optional)
     * @param healthyOnly whether to list only healthy instances
     * @return the list of instances
     * @throws NacosException if an error occurs
     */
    ServiceInfo listInstances(String namespaceId, String groupName, String serviceName, String clusterName, String ip,
            int port, boolean healthyOnly) throws NacosException;
    
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
    InstanceDetailInfoVo getInstanceDetail(String namespaceId, String groupName, String serviceName, String clusterName,
            String ip, int port) throws NacosException;
    
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
     * @param clusterName           the cluster name
     * @param checkPort             the health check port
     * @param useInstancePort4Check whether to use the instance port for health check
     * @param healthChecker         the health checker configuration
     * @param metadata              the metadata of the cluster
     * @return the result of the operation
     * @throws NacosException if an error occurs
     */
    String updateCluster(String namespaceId, String groupName, String clusterName, Integer checkPort,
            Boolean useInstancePort4Check, String healthChecker, Map<String, String> metadata) throws NacosException;
    
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
    
    // ------------------------- Core Operations -------------------------
    
    /**
     * Execute a Raft operation with the specified command, value, and group ID.
     *
     * @param command the command to execute.
     * @param value   the value associated with the command.
     * @param groupId the group ID for the operation.
     * @return the result of the Raft operation.
     * @throws NacosException if the operation fails.
     */
    String raftOps(String command, String value, String groupId) throws NacosException;
    
    /**
     * Retrieve the current health status of the ID generator.
     *
     * @return a list of ID generator status objects.
     * @throws NacosException if the operation fails.
     */
    List<IdGeneratorVO> getIdsHealth() throws NacosException;
    
    /**
     * Update the log level for a specific logger.
     *
     * @param logName  the name of the logger to update.
     * @param logLevel the new log level to set.
     * @throws NacosException if the operation fails.
     */
    void updateLogLevel(String logName, String logLevel) throws NacosException;
    
    /**
     * Retrieve information about the current node.
     *
     * @return the current node's information.
     * @throws NacosException if the operation fails.
     */
    Member getSelfNode() throws NacosException;
    
    /**
     * List cluster nodes based on the specified address and state.
     *
     * @param address the address to filter nodes by.
     * @param state   the state to filter nodes by.
     * @return a collection of matching nodes.
     * @throws NacosException if an error occurs during the operation.
     */
    Collection<Member> listClusterNodes(String address, String state) throws NacosException;
    
    /**
     * Retrieve the health status of the current node.
     *
     * @return the health status of the current node.
     * @throws NacosException if the operation fails.
     */
    String getSelfNodeHealth() throws NacosException;
    
    /**
     * Update the list of cluster nodes.
     *
     * @param nodes the list of nodes to update.
     * @return true if the operation was successful, false otherwise.
     * @throws NacosException if an error occurs during the operation.
     */
    Boolean updateClusterNodes(List<Member> nodes) throws NacosException;
    
    /**
     * Update the lookup mode for the cluster.
     *
     * @param type the type of lookup mode to set.
     * @return true if the operation was successful, false otherwise.
     * @throws NacosException if an error occurs during the operation.
     */
    Boolean updateLookupMode(String type) throws NacosException;
    
    /**
     * Retrieve the current client connections.
     *
     * @return a map of current client connections.
     * @throws NacosException if the operation fails.
     */
    Map<String, Connection> getCurrentClients() throws NacosException;
    
    /**
     * Reload the number of SDK connections on the current server.
     *
     * @param count           the number of connections to reload.
     * @param redirectAddress the address to redirect connections to.
     * @return the result of the operation.
     * @throws NacosException if the operation fails.
     */
    String reloadConnectionCount(Integer count, String redirectAddress) throws NacosException;
    
    /**
     * Smartly reload the cluster based on the specified loader factor.
     *
     * @param loaderFactorStr the loader factor string.
     * @return the result of the operation.
     * @throws NacosException if the operation fails.
     */
    String smartReloadCluster(String loaderFactorStr) throws NacosException;
    
    /**
     * Reload a single client connection.
     *
     * @param connectionId    the ID of the connection to reload.
     * @param redirectAddress the address to redirect the connection to.
     * @return the result of the operation.
     * @throws NacosException if the operation fails.
     */
    String reloadSingleClient(String connectionId, String redirectAddress) throws NacosException;
    
    /**
     * Retrieve the current cluster loader metrics.
     *
     * @return the loader metrics for the cluster.
     * @throws NacosException if the operation fails.
     */
    ServerLoaderMetrics getClusterLoaderMetrics() throws NacosException;
}