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
import com.alibaba.nacos.maintainer.client.core.CoreMaintainerService;
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

import java.util.List;
import java.util.Map;

/**
 * Nacos Config module maintainer service.
 *
 * @author Nacos
 */
public interface ConfigMaintainerService extends CoreMaintainerService {
    
    /**
     * Get configuration information by dataId and groupName.
     *
     * @param dataId    Configuration data ID (required).
     * @param groupName Configuration group name (required).
     * @return Configuration information.
     * @throws NacosException If the query fails.
     */
    ConfigAllInfo getConfig(String dataId, String groupName) throws NacosException;
    
    /**
     * Get configuration information by dataId, groupName, and namespaceId.
     *
     * @param dataId      Configuration data ID (required).
     * @param groupName   Configuration group name (required).
     * @param namespaceId Namespace ID (optional, defaults to "public").
     * @return Configuration information.
     * @throws NacosException If the query fails.
     */
    ConfigAllInfo getConfig(String dataId, String groupName, String namespaceId) throws NacosException;
    
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
     * Publish a configuration with additional metadata.
     *
     * @param dataId      Configuration data ID (required).
     * @param groupName   Configuration group name (required).
     * @param namespaceId Namespace ID (optional, defaults to "public").
     * @param content     Configuration content (required).
     * @param tag         Configuration tag (optional).
     * @param appName     Application name (optional).
     * @param srcUser     Source user (optional).
     * @param configTags  Configuration tags, multiple tags separated by commas (optional).
     * @param desc        Configuration description (optional).
     * @param use         Configuration usage (optional).
     * @param effect      Configuration effect (optional).
     * @param type        Configuration type (optional).
     * @param schema      Configuration schema (optional).
     * @return Whether the configuration was published successfully.
     * @throws NacosException If publishing fails.
     */
    boolean publishConfig(String dataId, String groupName, String namespaceId, String content, String tag,
            String appName, String srcUser, String configTags, String desc, String use, String effect, String type,
            String schema) throws NacosException;
    
    /**
     * Delete a configuration by dataId and groupName.
     *
     * @param dataId    Configuration data ID (required).
     * @param groupName Configuration group name (required).
     * @return Whether the configuration was deleted successfully.
     * @throws NacosException If deletion fails.
     */
    boolean deleteConfig(String dataId, String groupName) throws NacosException;
    
    /**
     * Delete a configuration by dataId, groupName, and namespaceId.
     *
     * @param dataId      Configuration data ID (required).
     * @param groupName   Configuration group name (required).
     * @param namespaceId Namespace ID (optional, defaults to "public").
     * @return Whether the configuration was deleted successfully.
     * @throws NacosException If deletion fails.
     */
    boolean deleteConfig(String dataId, String groupName, String namespaceId) throws NacosException;
    
    /**
     * Delete a configuration by dataId, groupName, namespaceId, and tag.
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
     * Delete multiple configurations by their IDs.
     *
     * @param ids List of configuration IDs to delete.
     * @return Whether the configurations were deleted successfully.
     * @throws NacosException If deletion fails.
     */
    boolean deleteConfigs(List<Long> ids) throws NacosException;
    
    /**
     * Get the advanced information of a configuration by dataId and groupName.
     *
     * @param dataId    Configuration data ID (required).
     * @param groupName Configuration group name (required).
     * @return Advanced information of the configuration.
     * @throws NacosException If retrieval fails.
     */
    ConfigAdvanceInfo getConfigAdvanceInfo(String dataId, String groupName) throws NacosException;
    
    /**
     * Get the advanced information of a configuration by dataId, groupName, and namespaceId.
     *
     * @param dataId      Configuration data ID (required).
     * @param groupName   Configuration group name (required).
     * @param namespaceId Namespace ID (optional, defaults to "public").
     * @return Advanced information of the configuration.
     * @throws NacosException If retrieval fails.
     */
    ConfigAdvanceInfo getConfigAdvanceInfo(String dataId, String groupName, String namespaceId) throws NacosException;
    
    /**
     * Get the listeners of a configuration by dataId and groupName.
     *
     * @param dataId    Configuration data ID (required).
     * @param groupName Configuration group name (required).
     * @return List of listeners for the configuration.
     * @throws NacosException If retrieval fails.
     */
    GroupkeyListenserStatus getListeners(String dataId, String groupName) throws NacosException;
    
    /**
     * Get the listeners of a configuration by dataId, groupName, namespaceId, and sampleTime.
     *
     * @param dataId      Configuration data ID (required).
     * @param groupName   Configuration group name (required).
     * @param namespaceId Namespace ID (optional, defaults to "public").
     * @param sampleTime  Sample time (optional).
     * @return List of listeners for the configuration.
     * @throws NacosException If retrieval fails.
     */
    GroupkeyListenserStatus getListeners(String dataId, String groupName, String namespaceId, int sampleTime)
            throws NacosException;
    
    /**
     * Search configurations by details.
     *
     * @param dataId       Configuration data ID (optional, defaults to "").
     * @param groupName    Configuration group name (optional, defaults to "").
     * @param namespaceId  Namespace ID (optional, defaults to "public").
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
     * Stop a beta configuration by dataId and groupName.
     *
     * @param dataId    Configuration data ID (required).
     * @param groupName Configuration group name (required).
     * @return Whether the beta configuration was stopped successfully.
     * @throws NacosException If stopping fails.
     */
    boolean stopBeta(String dataId, String groupName) throws NacosException;
    
    /**
     * Stop a beta configuration by dataId, groupName, and namespaceId.
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
     * Clone configurations within the same namespace.
     *
     * @param namespaceId     Namespace ID (optional, defaults to "public").
     * @param configBeansList List of configurations to clone (required).
     * @param srcUser         Source user (optional).
     * @param policy          Conflict resolution policy (required).
     * @return A map containing the clone result (e.g., success count, unrecognized data).
     * @throws NacosException If the clone operation fails.
     */
    Map<String, Object> cloneConfig(String namespaceId, List<SameNamespaceCloneConfigBean> configBeansList,
            String srcUser, SameConfigPolicy policy) throws NacosException;
    
    /**
     * Query the list of configuration history by dataId, groupName, namespaceId, pageNo, and pageSize.
     *
     * @param dataId      Configuration data ID (required).
     * @param groupName   Configuration group name (required).
     * @param namespaceId Namespace ID (optional, defaults to "public").
     * @param pageNo      Page number (required).
     * @param pageSize    Page size (required, max 500).
     * @return A paginated list of configuration history.
     * @throws NacosException If the query fails.
     */
    Page<ConfigHistoryInfo> listConfigHistory(String dataId, String groupName, String namespaceId, int pageNo,
            int pageSize) throws NacosException;
    
    /**
     * Query detailed configuration history information by dataId, groupName, namespaceId, and nid.
     *
     * @param dataId      Configuration data ID (required).
     * @param groupName   Configuration group name (required).
     * @param namespaceId Namespace ID (optional, defaults to "public").
     * @param nid         History record ID (required).
     * @return Detailed configuration history information.
     * @throws NacosException If the history record does not exist or the query fails.
     */
    ConfigHistoryInfo getConfigHistoryInfo(String dataId, String groupName, String namespaceId, Long nid)
            throws NacosException;
    
    /**
     * Query previous configuration history information by dataId, groupName, namespaceId, and id.
     *
     * @param dataId      Configuration data ID (required).
     * @param groupName   Configuration group name (required).
     * @param namespaceId Namespace ID (optional, defaults to "public").
     * @param id          Current history record ID (required).
     * @return Previous configuration history information.
     * @throws NacosException If the previous history record does not exist or the query fails.
     */
    ConfigHistoryInfo getPreviousConfigHistoryInfo(String dataId, String groupName, String namespaceId, Long id)
            throws NacosException;
    
    /**
     * Query configurations list by namespace.
     *
     * @param namespaceId Namespace ID (required).
     * @return A list of configurations in the specified namespace.
     * @throws NacosException If the namespace is invalid or the query fails.
     */
    List<ConfigInfoWrapper> getConfigListByNamespace(String namespaceId) throws NacosException;
    
    /**
     * Get capacity information for a specific group or namespace.
     *
     * @param groupName   Group name (optional).
     * @param namespaceId Namespace ID (optional).
     * @return Capacity information.
     * @throws NacosException If the query fails or parameters are invalid.
     */
    Capacity getCapacityWithDefault(String groupName, String namespaceId) throws NacosException;
    
    /**
     * Insert or update capacity information.
     *
     * @param groupName    Group name (required).
     * @param namespaceId  Namespace ID (required).
     * @param quota        Quota value (required).
     * @param maxSize      Maximum size (required).
     * @param maxAggrCount Maximum aggregation count (required).
     * @param maxAggrSize  Maximum aggregation size (required).
     * @return Whether the operation was successful.
     * @throws NacosException If the operation fails.
     */
    boolean insertOrUpdateCapacity(String groupName, String namespaceId, Integer quota, Integer maxSize,
            Integer maxAggrCount, Integer maxAggrSize) throws NacosException;
    
    /**
     * Manually trigger dump of local configuration files from the store.
     *
     * @return A success message or error details.
     * @throws NacosException if the operation fails.
     */
    String updateLocalCacheFromStore() throws NacosException;
    
    /**
     * Set the log level for a specific module.
     *
     * @param logName  Name of the log module (required).
     * @param logLevel Desired log level (required).
     * @return A success message or error details.
     * @throws NacosException if the operation fails.
     */
    String setLogLevel(String logName, String logLevel) throws NacosException;
    
    /**
     * Get all subscribed client configurations by IP.
     *
     * @param ip          Client IP address (required).
     * @param all         Whether to include all subscriptions (optional, defaults to false).
     * @param namespaceId Namespace ID (optional).
     * @param sampleTime  Sample time (optional, defaults to 0).
     * @return the subscription status.
     * @throws NacosException if the operation fails.
     */
    GroupkeyListenserStatus getAllSubClientConfigByIp(String ip, boolean all, String namespaceId, int sampleTime)
            throws NacosException;
    
    /**
     * Get client metrics for a specific IP.
     *
     * @param ip          Client IP address (required).
     * @param dataId      Configuration data ID (optional).
     * @param groupName   Configuration group name (optional).
     * @param namespaceId Namespace ID (optional).
     * @return a map containing the client metrics.
     * @throws NacosException if the operation fails.
     */
    Map<String, Object> getClientMetrics(String ip, String dataId, String groupName, String namespaceId)
            throws NacosException;
    
    /**
     * Get cluster-wide metrics for a specific IP.
     *
     * @param ip          Client IP address (required).
     * @param dataId      Configuration data ID (optional).
     * @param groupName   Configuration group name (optional).
     * @param namespaceId Namespace ID (optional).
     * @return a map containing the cluster-wide metrics.
     * @throws NacosException if the operation fails.
     */
    Map<String, Object> getClusterMetrics(String ip, String dataId, String groupName, String namespaceId)
            throws NacosException;
}
