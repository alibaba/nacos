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

import com.alibaba.nacos.api.config.model.ConfigBasicInfo;
import com.alibaba.nacos.api.config.model.ConfigCloneInfo;
import com.alibaba.nacos.api.config.model.ConfigDetailInfo;
import com.alibaba.nacos.api.config.model.ConfigGrayInfo;
import com.alibaba.nacos.api.config.model.ConfigHistoryBasicInfo;
import com.alibaba.nacos.api.config.model.ConfigHistoryDetailInfo;
import com.alibaba.nacos.api.config.model.ConfigListenerInfo;
import com.alibaba.nacos.api.config.model.SameConfigPolicy;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.maintainer.client.core.CoreMaintainerService;

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
    ConfigDetailInfo getConfig(String dataId, String groupName) throws NacosException;
    
    /**
     * Get configuration information by dataId, groupName, and namespaceId.
     *
     * @param dataId      Configuration data ID (required).
     * @param groupName   Configuration group name (required).
     * @param namespaceId Namespace ID (optional, defaults to "public").
     * @return Configuration information.
     * @throws NacosException If the query fails.
     */
    ConfigDetailInfo getConfig(String dataId, String groupName, String namespaceId) throws NacosException;
    
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
     * @param type        Configuration type (optional).
     * @param betaIps     Configuration beta IPs, multiple IPs separated by commas ',' (required).
     * @return Whether the configuration was published successfully.
     * @throws NacosException If publishing fails.
     */
    boolean publishConfigWithBeta(String dataId, String groupName, String namespaceId, String content, String tag,
            String appName, String srcUser, String configTags, String desc, String type, String betaIps) throws NacosException;
    
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
     * Get the listeners of a configuration by dataId and groupName.
     *
     * @param dataId    Configuration data ID (required).
     * @param groupName Configuration group name (required).
     * @return List of listeners for the configuration.
     * @throws NacosException If retrieval fails.
     */
    ConfigListenerInfo getListeners(String dataId, String groupName) throws NacosException;
    
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
    ConfigListenerInfo getListeners(String dataId, String groupName, String namespaceId, int sampleTime)
            throws NacosException;
    
    /**
     * Search configurations by details.
     *
     * @param dataId       Configuration data ID (optional, defaults to "").
     * @param groupName    Configuration group name (optional, defaults to "").
     * @param namespaceId  Namespace ID (optional, defaults to "public").
     * @param search       Search mode ("blur" or "exact", optional, defaults to "blur").
     * @param configDetail Configuration detail (optional).
     * @param type         Type of Configuration (optional).
     * @param configTags   Configuration tags (optional).
     * @param appName      Application name of Configuration (optional).
     * @param pageNo       Page number (required, defaults to 1).
     * @param pageSize     Page size (required, defaults to 100).
     * @return A paginated list of configurations matching the search criteria.
     * @throws NacosException If the search fails.
     */
    Page<ConfigBasicInfo> searchConfigByDetails(String dataId, String groupName, String namespaceId, String search,
            String configDetail, String type, String configTags, String appName, int pageNo, int pageSize)
            throws NacosException;
    
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
    ConfigGrayInfo queryBeta(String dataId, String groupName) throws NacosException;
    
    /**
     * Query beta configuration by dataId, groupName, and namespaceId.
     *
     * @param dataId      Configuration data ID (required).
     * @param groupName   Configuration group name (required).
     * @param namespaceId Namespace ID (optional, defaults to "public").
     * @return Beta configuration information.
     * @throws NacosException If the query fails.
     */
    ConfigGrayInfo queryBeta(String dataId, String groupName, String namespaceId) throws NacosException;
    
    /**
     * Clone configurations within the same namespace.
     *
     * @param namespaceId     Namespace ID (optional, defaults to "public").
     * @param cloneInfos      List of configurations to clone (required).
     * @param srcUser         Source user (optional).
     * @param policy          Conflict resolution policy (required).
     * @return A map containing the clone result (e.g., success count, unrecognized data).
     * @throws NacosException If the clone operation fails.
     */
    Map<String, Object> cloneConfig(String namespaceId, List<ConfigCloneInfo> cloneInfos, String srcUser,
            SameConfigPolicy policy) throws NacosException;
    
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
    Page<ConfigHistoryBasicInfo> listConfigHistory(String dataId, String groupName, String namespaceId, int pageNo,
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
    ConfigHistoryDetailInfo getConfigHistoryInfo(String dataId, String groupName, String namespaceId, Long nid)
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
    ConfigHistoryDetailInfo getPreviousConfigHistoryInfo(String dataId, String groupName, String namespaceId, Long id)
            throws NacosException;
    
    /**
     * Query configurations list by namespace.
     *
     * @param namespaceId Namespace ID (required).
     * @return A list of configurations in the specified namespace.
     * @throws NacosException If the namespace is invalid or the query fails.
     */
    List<ConfigBasicInfo> getConfigListByNamespace(String namespaceId) throws NacosException;
    
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
    ConfigListenerInfo getAllSubClientConfigByIp(String ip, boolean all, String namespaceId, int sampleTime)
            throws NacosException;
}
