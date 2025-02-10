/*
 * Copyright 1999-2025 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.maintainer.client.core;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.response.IdGeneratorInfo;
import com.alibaba.nacos.api.model.response.NacosMember;
import com.alibaba.nacos.api.model.response.ServerLoaderMetrics;
import com.alibaba.nacos.api.model.response.ConnectionInfo;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Nacos Core module maintainer service.
 *
 * @author Nacos
 */
public interface CoreMaintainerService {
    
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
    List<IdGeneratorInfo> getIdGenerators() throws NacosException;
    
    /**
     * Update the log level for a specific logger.
     *
     * @param logName  the name of the logger to update.
     * @param logLevel the new log level to set.
     * @throws NacosException if the operation fails.
     */
    void updateLogLevel(String logName, String logLevel) throws NacosException;
    
    /**
     * List cluster nodes based on the specified address and state.
     *
     * @param address the address to filter nodes by.
     * @param state   the state to filter nodes by.
     * @return a collection of matching nodes.
     * @throws NacosException if an error occurs during the operation.
     */
    Collection<NacosMember> listClusterNodes(String address, String state) throws NacosException;
    
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
    Map<String, ConnectionInfo> getCurrentClients() throws NacosException;
    
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
