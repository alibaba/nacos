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

package com.alibaba.nacos.maintainer.client.config;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.maintainer.client.model.config.Capacity;
import com.alibaba.nacos.maintainer.client.model.config.ConfigAdvanceInfo;
import com.alibaba.nacos.maintainer.client.model.config.ConfigAllInfo;
import com.alibaba.nacos.maintainer.client.model.config.ConfigHistoryInfo;
import com.alibaba.nacos.maintainer.client.model.config.ConfigInfo;
import com.alibaba.nacos.maintainer.client.model.config.ConfigInfo4Beta;
import com.alibaba.nacos.maintainer.client.model.config.ConfigInfoWrapper;
import com.alibaba.nacos.maintainer.client.model.config.GroupkeyListenserStatus;
import com.alibaba.nacos.maintainer.client.model.config.Page;
import com.alibaba.nacos.maintainer.client.model.config.SameConfigPolicy;
import com.alibaba.nacos.maintainer.client.model.config.SameNamespaceCloneConfigBean;
import com.alibaba.nacos.maintainer.client.model.core.Connection;
import com.alibaba.nacos.maintainer.client.model.core.IdGeneratorVO;
import com.alibaba.nacos.maintainer.client.model.core.Member;
import com.alibaba.nacos.maintainer.client.model.core.ServerLoaderMetrics;
import org.springframework.http.ResponseEntity;
import org.springframework.web.context.request.async.DeferredResult;
import org.springframework.web.multipart.MultipartFile;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface ConfigMaintainerService {
    
    /**
     * Get configuration information by dataId and groupName.
     *
     * @param dataId    Configuration data ID (required).
     * @param groupName Configuration group name (required).
     * @return Configuration information.
     * @throws NacosException If the query fails.
     */
    ConfigAllInfo getConfig(String dataId, String groupName) throws Exception;
    
    /**
     * Get configuration information by dataId, groupName, and namespaceId.
     *
     * @param dataId      Configuration data ID (required).
     * @param groupName   Configuration group name (required).
     * @param namespaceId Namespace ID (optional, defaults to "public").
     * @return Configuration information.
     * @throws NacosException If the query fails.
     */
    ConfigAllInfo getConfig(String dataId, String groupName, String namespaceId) throws Exception;
    
    /**
     * Publish a configuration by dataId and groupName.
     *
     * @param dataId    Configuration data ID (required).
     * @param groupName Configuration group name (required).
     * @param content   Configuration content (required).
     * @return Whether the configuration was published successfully.
     * @throws NacosException If the publish operation fails.
     */
    boolean publishConfig(String dataId, String groupName, String content) throws NacosException;
    
    /**
     * Publish a configuration by dataId, groupName, and namespaceId.
     *
     * @param dataId      Configuration data ID (required).
     * @param groupName   Configuration group name (required).
     * @param namespaceId Namespace ID (optional, defaults to "public").
     * @param content     Configuration content (required).
     * @return Whether the configuration was published successfully.
     * @throws NacosException If the publish operation fails.
     */
    boolean publishConfig(String dataId, String groupName, String namespaceId, String content) throws NacosException;
    
    /**
     * Publish a configuration.
     *
     * @param dataId      Configuration data ID (required)
     * @param groupName   Configuration group name (required)
     * @param namespaceId Namespace ID (optional, default is "public")
     * @param content     Configuration content (required)
     * @param tag         Configuration tag (optional)
     * @param appName     Application name (optional)
     * @param srcUser     Source user (optional)
     * @param configTags  Configuration tags, multiple tags separated by commas (optional)
     * @param desc        Configuration description (optional)
     * @param use         Configuration usage (optional)
     * @param effect      Configuration effect (optional)
     * @param type        Configuration type (optional)
     * @param schema      Configuration schema (optional)
     * @return Whether the configuration was published successfully
     * @throws NacosException If publishing fails
     */
    boolean publishConfig(String dataId, String groupName, String namespaceId, String content, String tag, String appName,
            String srcUser, String configTags, String desc, String use, String effect, String type, String schema) throws NacosException;
    
    /**
     * Delete a configuration.
     *
     * @param dataId      Configuration data ID (required).
     * @param groupName   Configuration group name (required).
     * @param namespaceId Namespace ID (optional, defaults to "public").
     * @return Whether the configuration was deleted successfully.
     * @throws NacosException If deletion fails.
     */
    boolean deleteConfig(String dataId, String groupName, String namespaceId) throws NacosException;
    
    /**
     * Delete a configuration.
     *
     * @param dataId      Configuration data ID (required).
     * @param groupName   Configuration group name (required).
     * @param namespaceId Namespace ID (optional, defaults to "public").
     * @param tag         Configuration tag (optional).
     * @return Whether the configuration was deleted successfully.
     * @throws NacosException If deletion fails.
     */
    boolean deleteConfig(String dataId, String groupName, String namespaceId, String tag) throws NacosException;
    
    /**
     * Delete multiple configurations.
     *
     * @param ids List of configuration IDs to delete.
     * @return Whether the configurations were deleted successfully.
     * @throws NacosException If deletion fails.
     */
    boolean deleteConfigs(List<Long> ids) throws NacosException;
    
    /**
     * Get the advanced information of a configuration.
     *
     * @param dataId      Configuration data ID (required).
     * @param groupName   Configuration group name (required).
     * @param namespaceId Namespace ID (optional, defaults to "public").
     * @return Advanced information of the configuration.
     * @throws NacosException If retrieval fails.
     */
    ConfigAdvanceInfo getConfigAdvanceInfo(String dataId, String groupName, String namespaceId) throws NacosException;
    
    /**
     * Get the listeners of a configuration.
     *
     * @param dataId      Configuration data ID (required).
     * @param groupName   Configuration group name (required).
     * @param namespaceId Namespace ID (optional, defaults to "public").
     * @return List of listeners for the configuration.
     * @throws Exception If retrieval fails.
     */
    GroupkeyListenserStatus getListeners(String dataId, String groupName, String namespaceId) throws Exception;
    
    /**
     * Get the advanced information of a configuration.
     *
     * @param dataId      Configuration data ID (required).
     * @param groupName   Configuration group name (required).
     * @param namespaceId Namespace ID (optional, defaults to "public").
     * @param sampleTime  Sample time (optional).
     * @return Advanced information of the configuration.
     * @throws NacosException If retrieval fails.
     */
    GroupkeyListenserStatus getListeners(String dataId, String groupName, String namespaceId, int sampleTime) throws Exception;
    
    
    /**
     * Search configurations by details.
     *
     * @param namespaceId  Namespace ID (optional, defaults to "public").
     * @param dataId       Configuration data ID (optional, defaults to "").
     * @param groupName    Configuration group name (optional, defaults to "").
     * @param configDetail Configuration detail (optional).
     * @param search       Search mode ("blur" or "exact", optional, defaults to "blur").
     * @param pageNo       Page number (required, defaults to 1).
     * @param pageSize     Page size (required, defaults to 100).
     * @return A paginated list of configurations matching the search criteria.
     * @throws NacosException If the search fails.
     */
    Page<ConfigInfo> searchConfigByDetails(String dataId, String groupName, String namespaceId, String configDetail,
            String search, int pageNo, int pageSize) throws NacosException;

    /**
     * Stop a beta configuration.
     *
     * @param dataId      Configuration data ID (required).
     * @param groupName   Configuration group name (required).
     * @return Whether the beta configuration was stopped successfully.
     * @throws NacosException If stopping fails.
     */
    boolean stopBeta(String dataId, String groupName) throws NacosException;
    
    /**
     * Stop a beta configuration.
     *
     * @param dataId      Configuration data ID (required).
     * @param groupName   Configuration group name (required).
     * @param namespaceId Namespace ID (optional, defaults to "public").
     * @return Whether the beta configuration was stopped successfully.
     * @throws NacosException If stopping fails.
     */
    boolean stopBeta(String dataId, String groupName, String namespaceId) throws NacosException;
    
    /**
     * Query beta configuration by dataId and groupName.
     *
     * @param dataId    Configuration data ID (required).
     * @param groupName Configuration group name (required).
     * @return Beta configuration information.
     * @throws NacosException If the query fails.
     */
    ConfigInfo4Beta queryBeta(String dataId, String groupName) throws NacosException;
    
    /**
     * Query beta configuration by dataId, groupName, and namespaceId.
     *
     * @param dataId      Configuration data ID (required).
     * @param groupName   Configuration group name (required).
     * @param namespaceId Namespace ID (optional, defaults to "public").
     * @return Beta configuration information.
     * @throws NacosException If the query fails.
     */
    ConfigInfo4Beta queryBeta(String dataId, String groupName, String namespaceId) throws NacosException;
    
    /**
     * Import and publish configurations from a file.
     *
     * @param namespaceId Namespace ID (optional, defaults to "public").
     * @param srcUser     Source user (optional).
     * @param policy      Conflict resolution policy (required).
     * @param file        Configuration file to import (required).
     * @return A map containing the import result (e.g., success count, unrecognized data).
     * @throws NacosException If the import fails.
     */
    Map<String, Object> importAndPublishConfig(String namespaceId, String srcUser, SameConfigPolicy policy, MultipartFile file) throws NacosException;
    
    /**
     * Export configurations as a zip file.
     *
     * @param dataId      Configuration data ID (optional).
     * @param groupName   Configuration group name (optional).
     * @param namespaceId Namespace ID (optional, defaults to "public").
     * @param ids         List of configuration IDs to export (optional).
     * @return A ResponseEntity containing the exported zip file.
     * @throws NacosException If the export fails.
     */
    ResponseEntity<byte[]> exportConfig(String dataId, String groupName, String namespaceId, List<Long> ids) throws NacosException;
    
    /**
     * Clone configurations within the same namespace.
     *
     * @param namespaceId       Namespace ID (optional, defaults to "public").
     * @param configBeansList   List of configurations to clone (required).
     * @param srcUser           Source user (optional).
     * @param policy            Conflict resolution policy (required).
     * @return A map containing the clone result (e.g., success count, unrecognized data).
     * @throws NacosException If the clone operation fails.
     */
    Map<String, Object> cloneConfig(String namespaceId, List<SameNamespaceCloneConfigBean> configBeansList, String srcUser, SameConfigPolicy policy)
            throws NacosException;
    
    /**
     * Query the list of configuration history.
     *
     * @param dataId      Configuration data ID (required).
     * @param groupName   Configuration group name (required).
     * @param namespaceId Namespace ID (optional, defaults to "public").
     * @param pageNo      Page number (required).
     * @param pageSize    Page size (required, max 500).
     * @return A paginated list of configuration history.
     * @throws NacosApiException If the query fails.
     */
    Page<ConfigHistoryInfo> listConfigHistory(String dataId, String groupName, String namespaceId, int pageNo, int pageSize) throws NacosApiException;
    
    /**
     * Query detailed configuration history information.
     *
     * @param dataId      Configuration data ID (required).
     * @param groupName   Configuration group name (required).
     * @param namespaceId Namespace ID (optional, defaults to "public").
     * @param nid         History record ID (required).
     * @return Detailed configuration history information.
     * @throws NacosApiException If the history record does not exist or the query fails.
     */
    ConfigHistoryInfo getConfigHistoryInfo(String dataId, String groupName, String namespaceId, Long nid) throws NacosApiException;
    
    /**
     * Query previous configuration history information.
     *
     * @param dataId      Configuration data ID (required).
     * @param groupName   Configuration group name (required).
     * @param namespaceId Namespace ID (optional, defaults to "public").
     * @param id          Current history record ID (required).
     * @return Previous configuration history information.
     * @throws NacosApiException If the previous history record does not exist or the query fails.
     */
    ConfigHistoryInfo getPreviousConfigHistoryInfo(String dataId, String groupName, String namespaceId, Long id) throws NacosApiException;
    
    /**
     * Query configurations list by namespace.
     *
     * @param namespaceId Namespace ID (required).
     * @return A list of configurations in the specified namespace.
     * @throws NacosApiException If the namespace is invalid or the query fails.
     */
    List<ConfigInfoWrapper> getConfigListByNamespace(String namespaceId) throws NacosApiException;
    
    /**
     * Get capacity information for a specific group or namespace.
     *
     * @param groupName   Group name (optional).
     * @param namespaceId Namespace ID (optional).
     * @return Capacity information.
     * @throws NacosApiException If the query fails or parameters are invalid.
     */
    Capacity getCapacityWithDefault(String groupName, String namespaceId) throws NacosApiException;
    
    /**
     * Initialize capacity information for a specific group or namespace.
     *
     * @param groupName   Group name (required).
     * @param namespaceId Namespace ID (required).
     * @throws NacosApiException If initialization fails.
     */
    void initCapacity(String groupName, String namespaceId) throws NacosApiException;
    
    /**
     * Insert or update capacity information.
     *
     * @param groupName     Group name (required).
     * @param namespaceId   Namespace ID (required).
     * @param quota         Quota value (required).
     * @param maxSize       Maximum size (required).
     * @param maxAggrCount  Maximum aggregation count (required).
     * @param maxAggrSize   Maximum aggregation size (required).
     * @return Whether the operation was successful.
     * @throws NacosApiException If the operation fails.
     */
    boolean insertOrUpdateCapacity(String groupName, String namespaceId, Integer quota, Integer maxSize,
            Integer maxAggrCount, Integer maxAggrSize) throws NacosApiException;
    
    /**
     * Manually trigger dump of local configuration files from the store.
     *
     * @return A success message or error details.
     */
    String updateLocalCacheFromStore();
    
    /**
     * Set the log level for a specific module.
     *
     * @param logName  Name of the log module (required).
     * @param logLevel Desired log level (required).
     * @return A success message or error details.
     */
    String setLogLevel(String logName, String logLevel);
    
    /**
     * Execute a SQL query on the embedded Derby database.
     *
     * @param sql SQL query to execute (required).
     * @return Query results or an error message.
     */
    Object derbyOps(String sql);
    
    /**
     * Import data into the embedded Derby database from a file.
     *
     * @param multipartFile File containing the data to import (required).
     * @return A deferred result indicating success or failure.
     */
    DeferredResult<Result<String>> importDerby(MultipartFile multipartFile);
    
    /**
     * Get all subscribed client configurations by IP.
     *
     * @param ip          Client IP address (required).
     * @param all         Whether to retrieve all configurations (optional, default is false).
     * @param namespaceId Namespace ID (optional).
     * @param sampleTime  Sampling time in seconds (optional, default is 1).
     * @return Client subscription status.
     */
    GroupkeyListenserStatus getAllSubClientConfigByIp(String ip, boolean all, String namespaceId, int sampleTime);
    
    /**
     * Get client metrics for a specific IP.
     *
     * @param ip          Client IP address (required).
     * @param dataId      Configuration data ID (optional).
     * @param groupName   Configuration group name (optional).
     * @param namespaceId Namespace ID (optional).
     * @return Client metrics.
     * @throws NacosException If the operation fails.
     */
    Map<String, Object> getClientMetrics(String ip, String dataId, String groupName, String namespaceId) throws NacosException;
    
    /**
     * Get cluster-wide metrics for a specific IP.
     *
     * @param ip          Client IP address (required).
     * @param dataId      Configuration data ID (optional).
     * @param groupName   Configuration group name (optional).
     * @param namespaceId Namespace ID (optional).
     * @return Cluster-wide metrics.
     * @throws NacosException If the operation fails.
     */
    Map<String, Object> getClusterMetrics(String ip, String dataId, String groupName, String namespaceId) throws NacosException;
    
    // ------------------------- Core Operations -------------------------
    
    /**
     * Execute a Raft operation with the specified command, value, and group ID.
     *
     * @param command the command to execute
     * @param value the value associated with the command
     * @param groupId the group ID for the operation
     * @return the result of the Raft operation
     */
    String raftOps(String command, String value, String groupId);
    
    /**
     * Retrieve the current health status of the ID generator.
     *
     * @return a list of ID generator status objects
     */
    List<IdGeneratorVO> getIdsHealth();
    
    /**
     * Update the log level for a specific logger.
     *
     * @param logName the name of the logger to update
     * @param logLevel the new log level to set
     */
    void updateLogLevel(String logName, String logLevel);
    
    /**
     * Retrieve information about the current node.
     *
     * @return the current node's information
     */
    Member getSelfNode();
    
    /**
     * List cluster nodes based on the specified address and state.
     *
     * @param address the address to filter nodes by
     * @param state the state to filter nodes by
     * @return a collection of matching nodes
     * @throws NacosException if an error occurs during the operation
     */
    Collection<Member> listClusterNodes(String address, String state) throws NacosException;
    
    /**
     * Retrieve the health status of the current node.
     *
     * @return the health status of the current node
     */
    String getSelfNodeHealth();
    
    /**
     * Update the list of cluster nodes.
     *
     * @param nodes the list of nodes to update
     * @return true if the operation was successful, false otherwise
     * @throws NacosApiException if an error occurs during the operation
     */
    Boolean updateClusterNodes(List<Member> nodes) throws NacosApiException;
    
    /**
     * Update the lookup mode for the cluster.
     *
     * @param type the type of lookup mode to set
     * @return true if the operation was successful, false otherwise
     * @throws NacosException if an error occurs during the operation
     */
    Boolean updateLookupMode(String type) throws NacosException;
    
    /**
     * Retrieve the current client connections.
     *
     * @return a map of current client connections
     */
    Map<String, Connection> getCurrentClients();
    
    /**
     * Reload the number of SDK connections on the current server.
     *
     * @param count the number of connections to reload
     * @param redirectAddress the address to redirect connections to
     * @return the result of the operation
     */
    String reloadConnectionCount(Integer count, String redirectAddress);
    
    /**
     * Smartly reload the cluster based on the specified loader factor.
     *
     * @param loaderFactorStr the loader factor string
     * @return the result of the operation
     */
    String smartReloadCluster(String loaderFactorStr);
    
    /**
     * Reload a single client connection.
     *
     * @param connectionId the ID of the connection to reload
     * @param redirectAddress the address to redirect the connection to
     * @return the result of the operation
     */
    String reloadSingleClient(String connectionId, String redirectAddress);
    
    /**
     * Retrieve the current cluster loader metrics.
     *
     * @return the loader metrics for the cluster
     */
    ServerLoaderMetrics getClusterLoaderMetrics();
}
