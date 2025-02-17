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
import com.alibaba.nacos.api.naming.pojo.Service;
import com.alibaba.nacos.api.naming.pojo.healthcheck.AbstractHealthChecker;
import com.alibaba.nacos.api.naming.pojo.maintainer.ClusterInfo;
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
public interface NamingMaintainerService
        extends CoreMaintainerService, ServiceMaintainerService, InstanceMaintainerService {
    
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
    
    /**
     * Update the health status of a persistent instance.
     *
     * <p>
     *     This API is designed to work with the persistent instance which don't need to auto-check and maintainer by admin
     *     to change the health status.
     *     So This API works at following several condition:
     *     <ul>
     *         <li>1. instance should be persistent instance</li>
     *         <li>2. health checker for cluster of this instance should be `NONE` </li>
     *     </ul>
     * </p>
     * <p>
     *     How to change the health checker for cluster: see {@link #updateCluster(Service, ClusterInfo)}.
     * </p>
     *
     * @param service  service need to be updated, {@link Service#getNamespaceId()}, {@link Service#getGroupName()}
     *                 and {@link Service#getName()} are required.
     *                 {@link Service#isEphemeral()} must be `false`.
     * @param instance instance need to be updated, {@link Instance#getIp()}, {@link Instance#getPort()}
     *                 and {@link Instance#getClusterName()} are required.
     *                 {@link Instance#isEphemeral()} must be `false`.
     * @return the result of the operation
     * @throws NacosException if an error occurs
     */
    String updateInstanceHealthStatus(Service service, Instance instance) throws NacosException;
    
    /**
     * Get all health checkers for current nacos cluster.
     *
     * @return a map of health checkers
     * @throws NacosException if an error occurs
     */
    Map<String, AbstractHealthChecker> getHealthCheckers() throws NacosException;
    
    /**
     * Update cluster metadata in target service.
     *
     * @param service   the service of updated cluster
     * @param cluster   the new cluster metadata. {@link ClusterInfo#getClusterName()} is required and can't be changed,
     *                  used to locate which cluster need to be updated.
     *                  {@link ClusterInfo#getHealthChecker()} is from {@link #getHealthCheckers()}.
     *                  {@link ClusterInfo#getMetadata()}, {@link ClusterInfo#getHealthyCheckPort()}
     *                  and {@link ClusterInfo#isUseInstancePortForCheck()} will full replace server side.
     *                  {@link ClusterInfo#getHosts()} will be ignored in this API.
     * @return the result of the operation
     * @throws NacosException if an error occurs
     */
    String updateCluster(Service service, ClusterInfo cluster) throws NacosException;
    
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
    
}
