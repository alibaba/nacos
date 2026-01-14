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

import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.config.model.ConfigBasicInfo;
import com.alibaba.nacos.api.config.model.ConfigCloneInfo;
import com.alibaba.nacos.api.config.model.ConfigDetailInfo;
import com.alibaba.nacos.api.config.model.ConfigListenerInfo;
import com.alibaba.nacos.api.config.model.SameConfigPolicy;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.maintainer.client.core.CoreMaintainerService;

import java.util.List;
import java.util.Map;

/**
 * Nacos Config module maintainer service.
 *
 * @author Nacos
 */
public interface ConfigMaintainerService
        extends CoreMaintainerService, BetaConfigMaintainerService, ConfigHistoryMaintainerService, ConfigOpsMaintainerService {
    
    /**
     * Get configuration information by dataId and default groupName with default namespace id.
     *
     * @param dataId    Configuration data ID (required).
     * @return Configuration information.
     * @throws NacosException If the query fails.
     */
    default ConfigDetailInfo getConfig(String dataId) throws NacosException {
        return getConfig(dataId, Constants.DEFAULT_GROUP);
    }
    
    /**
     * Get configuration information by dataId and groupName with default namespace id.
     *
     * @param dataId    Configuration data ID (required).
     * @param groupName Configuration group name (required).
     * @return Configuration information.
     * @throws NacosException If the query fails.
     */
    default ConfigDetailInfo getConfig(String dataId, String groupName) throws NacosException {
        return getConfig(dataId, groupName, Constants.DEFAULT_NAMESPACE_ID);
    }
    
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
     * Publish a configuration by dataId and default groupName with default namespace id.
     *
     * @param dataId    Configuration data ID (required).
     * @param content   Configuration content (required).
     * @return Whether the configuration was published successfully.
     * @throws NacosException If the publish operation fails.
     */
    default boolean publishConfig(String dataId, String content) throws NacosException {
        return publishConfig(dataId, Constants.DEFAULT_GROUP, content);
    }
    
    /**
     * Publish a configuration by dataId and groupName with default namespace id.
     *
     * @param dataId    Configuration data ID (required).
     * @param groupName Configuration group name (required).
     * @param content   Configuration content (required).
     * @return Whether the configuration was published successfully.
     * @throws NacosException If the publish operation fails.
     */
    default boolean publishConfig(String dataId, String groupName, String content) throws NacosException {
        return publishConfig(dataId, groupName, Constants.DEFAULT_NAMESPACE_ID, content);
    }
    
    /**
     * Publish a configuration by dataId, groupName, and namespaceId without any extend information.
     *
     * @param dataId      Configuration data ID (required).
     * @param groupName   Configuration group name (required).
     * @param namespaceId Namespace ID (optional, defaults to "public").
     * @param content     Configuration content (required).
     * @return Whether the configuration was published successfully.
     * @throws NacosException If the publish operation fails.
     */
    default boolean publishConfig(String dataId, String groupName, String namespaceId, String content)
            throws NacosException {
        return publishConfig(dataId, groupName, namespaceId, content, null);
    }
    
    /**
     * Publish a configuration by dataId, groupName, and namespaceId with description.
     *
     * @param dataId      Configuration data ID (required).
     * @param groupName   Configuration group name (required).
     * @param namespaceId Namespace ID (optional, defaults to "public").
     * @param content     Configuration content (required).
     * @param desc        Configuration description (optional).
     * @return Whether the configuration was published successfully.
     * @throws NacosException If the publish operation fails.
     */
    default boolean publishConfig(String dataId, String groupName, String namespaceId, String content, String desc)
            throws NacosException {
        return publishConfig(dataId, groupName, namespaceId, content, desc, null);
    }
    
    /**
     * Publish a configuration by dataId, groupName, and namespaceId with description and type.
     *
     * @param dataId      Configuration data ID (required).
     * @param groupName   Configuration group name (required).
     * @param namespaceId Namespace ID (optional, defaults to "public").
     * @param content     Configuration content (required).
     * @param desc        Configuration description (optional).
     * @param type        Configuration type (optional). The type is a metadata for the configuration content, such as TEXT, YAML, PROPERTIES, etc.
     *                    Only used by mark and filter in console, and is not used in the actual configuration storage.
     *                    If not specified, will be updated to default type `TEXT`.
     * @return Whether the configuration was published successfully.
     * @throws NacosException If the publish operation fails.
     */
    default boolean publishConfig(String dataId, String groupName, String namespaceId, String content, String desc,
            String type) throws NacosException {
        return publishConfig(dataId, groupName, namespaceId, content, null, null, null, desc, type);
    }
    
    /**
     * Publish a configuration with additional metadata.
     *
     * @param dataId      Configuration data ID (required).
     * @param groupName   Configuration group name (required).
     * @param namespaceId Namespace ID (optional, defaults to "public").
     * @param content     Configuration content (required).
     * @param appName     Application name (optional).
     * @param srcUser     Source user (optional). If not specified, will use the current user's name.
     * @param configTags  Configuration tags, multiple tags separated by commas (optional).
     * @param desc        Configuration description (optional).
     * @param type        Configuration type (optional). The type is a metadata for the configuration content, such as TEXT, YAML, PROPERTIES, etc.
     *                    Only used by mark and filter in console, and is not used in the actual configuration storage.
     *                    If not specified, will be updated to default type `TEXT`.
     * @return Whether the configuration was published successfully.
     * @throws NacosException If publishing fails.
     */
    boolean publishConfig(String dataId, String groupName, String namespaceId, String content, String appName,
            String srcUser, String configTags, String desc, String type) throws NacosException;
    
    
    /**
     * Update config metadata boolean.
     *
     * @param dataId      the data id
     * @param groupName   the group name
     * @param namespaceId the namespace id
     * @param description the description
     * @param configTags  the config tags
     * @return the boolean
     * @throws NacosException the nacos exception
     */
    boolean updateConfigMetadata(String dataId, String groupName, String namespaceId, String description,
            String configTags) throws NacosException;
    
    /**
     * Delete a configuration by dataId and default groupName.
     *
     * @param dataId    Configuration data ID (required).
     * @return Whether the configuration was deleted successfully.
     * @throws NacosException If deletion fails.
     */
    default boolean deleteConfig(String dataId) throws NacosException {
        return deleteConfig(dataId, Constants.DEFAULT_GROUP);
    }
    
    /**
     * Delete a configuration by dataId and groupName with default namespace id.
     *
     * @param dataId    Configuration data ID (required).
     * @param groupName Configuration group name (required).
     * @return Whether the configuration was deleted successfully.
     * @throws NacosException If deletion fails.
     */
    default boolean deleteConfig(String dataId, String groupName) throws NacosException {
        return deleteConfig(dataId, groupName, Constants.DEFAULT_NAMESPACE_ID);
    }
    
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
     * Delete multiple configurations by their IDs.
     *
     * @param ids List of configuration IDs to delete.
     * @return Whether the configurations were deleted successfully.
     * @throws NacosException If deletion fails.
     */
    boolean deleteConfigs(List<Long> ids) throws NacosException;
    
    /**
     * List first 100 configurations in namespaceId .
     *
     * @param namespaceId  Namespace ID (optional, defaults to "public").
     * @return A paginated list of configurations matching the search criteria.
     * @throws NacosException If the search fails.
     */
    default Page<ConfigBasicInfo> listConfigs(String namespaceId) throws NacosException {
        return listConfigs(StringUtils.EMPTY, StringUtils.EMPTY, namespaceId);
    }
    
    /**
     * List first 100 configurations in namespaceId .
     *
     * @param dataId       Configuration data ID (optional, defaults to ""). Accurate dataId, if "", will return all data id.
     * @param groupName    Configuration group name (optional, defaults to ""). Accurate groupName, if "", will return all group.
     * @param namespaceId  Namespace ID (optional, defaults to "public").
     * @return A paginated list of configurations matching the search criteria.
     * @throws NacosException If the search fails.
     */
    default Page<ConfigBasicInfo> listConfigs(String dataId, String groupName, String namespaceId)
            throws NacosException {
        return listConfigs(dataId, groupName, namespaceId, null);
    }
    
    /**
     * List first 100 configurations in namespaceId .
     *
     * @param dataId       Configuration data ID (optional, defaults to ""). Accurate dataId, if "", will return all data id.
     * @param groupName    Configuration group name (optional, defaults to ""). Accurate groupName, if "", will return all group.
     * @param namespaceId  Namespace ID (optional, defaults to "public").
     * @param type        Configuration type (optional). The type is a metadata for the configuration content, such as TEXT, YAML, PROPERTIES, etc.
     *                    Only used by mark and filter in console, and is not used in the actual configuration storage.
     *                    If not specified, will return all type.
     * @return A paginated list of configurations matching the search criteria.
     * @throws NacosException If the search fails.
     */
    default Page<ConfigBasicInfo> listConfigs(String dataId, String groupName, String namespaceId, String type)
            throws NacosException {
        return listConfigs(dataId, groupName, namespaceId, type, null, null);
    }
    
    /**
     * List first 100 configurations in namespaceId .
     *
     * @param dataId       Configuration data ID (optional, defaults to ""). Accurate dataId, if "", will return all data id.
     * @param groupName    Configuration group name (optional, defaults to ""). Accurate groupName, if "", will return all group.
     * @param namespaceId  Namespace ID (optional, defaults to "public").
     * @param type        Configuration type (optional). The type is a metadata for the configuration content, such as TEXT, YAML, PROPERTIES, etc.
     *                    Only used by mark and filter in console, and is not used in the actual configuration storage.
     *                    If not specified, will return all type.
     * @param configTags   Configuration tags (optional).
     * @param appName      Application name of Configuration (optional).
     * @return A paginated list of configurations matching the search criteria.
     * @throws NacosException If the search fails.
     */
    default Page<ConfigBasicInfo> listConfigs(String dataId, String groupName, String namespaceId, String type,
            String configTags, String appName) throws NacosException {
        return listConfigs(dataId, groupName, namespaceId, type, configTags, appName, 1, 100);
    }
    
    /**
     * List target page configurations in namespaceId .
     *
     * @param dataId       Configuration data ID (optional, defaults to ""). Accurate dataId, if "", will return all data id.
     * @param groupName    Configuration group name (optional, defaults to ""). Accurate groupName, if "", will return all group.
     * @param namespaceId  Namespace ID (optional, defaults to "public").
     * @param type        Configuration type (optional). The type is a metadata for the configuration content, such as TEXT, YAML, PROPERTIES, etc.
     *                    Only used by mark and filter in console, and is not used in the actual configuration storage.
     *                    If not specified, will return all type.
     * @param configTags   Configuration tags (optional).
     * @param appName      Application name of Configuration (optional).
     * @param pageNo       Page number (required, defaults to 1).
     * @param pageSize     Page size (required, defaults to 100).
     * @return A paginated list of configurations matching the search criteria.
     * @throws NacosException If the search fails.
     */
    default Page<ConfigBasicInfo> listConfigs(String dataId, String groupName, String namespaceId, String type,
            String configTags, String appName, int pageNo, int pageSize) throws NacosException {
        return searchConfigByDetails(dataId, groupName, namespaceId, "accurate", null, type, configTags, appName,
                pageNo, pageSize);
    }
    
    /**
     * Search first 100 configurations in namespaceId match pattern.
     *
     * @param dataId       Configuration data ID (optional, defaults to ""). dataId pattern, if "", will return all data id.
     * @param groupName    Configuration group name (optional, defaults to ""). groupName pattern, if "", will return all group.
     * @param namespaceId  Namespace ID (optional, defaults to "public").
     * @return A paginated list of configurations matching the search criteria.
     * @throws NacosException If the search fails.
     */
    default Page<ConfigBasicInfo> searchConfigs(String dataId, String groupName, String namespaceId)
            throws NacosException {
        dataId = fillAllPattern(dataId);
        groupName = fillAllPattern(groupName);
        return searchConfigs(dataId, groupName, namespaceId, null);
    }
    
    /**
     * Search first 100 configurations in namespaceId match pattern.
     *
     * @param dataId       Configuration data ID (optional, defaults to ""). dataId pattern, if "", will return all data id.
     * @param groupName    Configuration group name (optional, defaults to ""). groupName pattern, if "", will return all group.
     * @param namespaceId  Namespace ID (optional, defaults to "public").
     * @param type         Configuration type (optional). The type is a metadata for the configuration content, such as TEXT, YAML, PROPERTIES, etc.
     *                     Only used by mark and filter in console, and is not used in the actual configuration storage.
     *                     If not specified, will return all type.
     * @return A paginated list of configurations matching the search criteria.
     * @throws NacosException If the search fails.
     */
    default Page<ConfigBasicInfo> searchConfigs(String dataId, String groupName, String namespaceId, String type)
            throws NacosException {
        dataId = fillAllPattern(dataId);
        groupName = fillAllPattern(groupName);
        return searchConfigs(dataId, groupName, namespaceId, null, type);
    }
    
    /**
     * Search first 100 configurations in namespaceId match pattern.
     *
     * @param dataId       Configuration data ID (optional, defaults to ""). dataId pattern, if "", will return all data id.
     * @param groupName    Configuration group name (optional, defaults to ""). groupName pattern, if "", will return all group.
     * @param namespaceId  Namespace ID (optional, defaults to "public").
     * @param configDetail Configuration content part (optional).
     * @param type         Configuration type (optional). The type is a metadata for the configuration content, such as TEXT, YAML, PROPERTIES, etc.
     *                     Only used by mark and filter in console, and is not used in the actual configuration storage.
     *                     If not specified, will return all type.
     * @return A paginated list of configurations matching the search criteria.
     * @throws NacosException If the search fails.
     */
    default Page<ConfigBasicInfo> searchConfigs(String dataId, String groupName, String namespaceId,
            String configDetail, String type) throws NacosException {
        dataId = fillAllPattern(dataId);
        groupName = fillAllPattern(groupName);
        return searchConfigs(dataId, groupName, namespaceId, configDetail, type, null, null);
    }
    
    /**
     * Search target page configurations in namespaceId match pattern.
     *
     * @param dataId       Configuration data ID (optional, defaults to ""). dataId pattern, if "", will return all data id.
     * @param groupName    Configuration group name (optional, defaults to ""). groupName pattern, if "", will return all group.
     * @param namespaceId  Namespace ID (optional, defaults to "public").
     * @param configDetail Configuration content part (optional).
     * @param type         Configuration type (optional). The type is a metadata for the configuration content, such as TEXT, YAML, PROPERTIES, etc.
     *                     Only used by mark and filter in console, and is not used in the actual configuration storage.
     *                     If not specified, will return all type.
     * @param configTags   Configuration tags (optional).
     * @param appName      Application name of Configuration (optional).
     * @return A paginated list of configurations matching the search criteria.
     * @throws NacosException If the search fails.
     */
    default Page<ConfigBasicInfo> searchConfigs(String dataId, String groupName, String namespaceId,
            String configDetail, String type, String configTags, String appName) throws NacosException {
        dataId = fillAllPattern(dataId);
        groupName = fillAllPattern(groupName);
        return searchConfigs(dataId, groupName, namespaceId, configDetail, type, configTags, appName, 1, 100);
    }
    
    /**
     * Search target page configurations in namespaceId match pattern.
     *
     * @param dataId       Configuration data ID (optional, defaults to ""). dataId pattern, if "", will return all data id.
     * @param groupName    Configuration group name (optional, defaults to ""). groupName pattern, if "", will return all group.
     * @param namespaceId  Namespace ID (optional, defaults to "public").
     * @param configDetail Configuration content part (optional).
     * @param type         Configuration type (optional). The type is a metadata for the configuration content, such as TEXT, YAML, PROPERTIES, etc.
     *                     Only used by mark and filter in console, and is not used in the actual configuration storage.
     *                     If not specified, will return all type.
     * @param configTags   Configuration tags (optional).
     * @param appName      Application name of Configuration (optional).
     * @param pageNo       Page number (required, defaults to 1).
     * @param pageSize     Page size (required, defaults to 100).
     * @return A paginated list of configurations matching the search criteria.
     * @throws NacosException If the search fails.
     */
    default Page<ConfigBasicInfo> searchConfigs(String dataId, String groupName, String namespaceId,
            String configDetail, String type, String configTags, String appName, int pageNo, int pageSize)
            throws NacosException {
        dataId = fillAllPattern(dataId);
        groupName = fillAllPattern(groupName);
        configDetail = fillAllPattern(configDetail);
        return searchConfigByDetails(dataId, groupName, namespaceId, "blur", configDetail, type, configTags, appName,
                pageNo, pageSize);
    }
    
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
     * Query configurations list by namespace.
     *
     * @param namespaceId Namespace ID (required).
     * @return A list of configurations in the specified namespace.
     * @throws NacosException If the namespace is invalid or the query fails.
     */
    List<ConfigBasicInfo> getConfigListByNamespace(String namespaceId) throws NacosException;
    
    /**
     * Get the listeners of a configuration by dataId and groupName.
     *
     * @param dataId    Configuration data ID (required).
     * @param groupName Configuration group name (required).
     * @return List of listeners for the configuration.
     * @throws NacosException If retrieval fails.
     */
    default ConfigListenerInfo getListeners(String dataId, String groupName) throws NacosException {
        return getListeners(dataId, groupName, Constants.DEFAULT_NAMESPACE_ID, true);
    }
    
    /**
     * Get the listeners of a configuration by dataId, groupName, namespaceId, and sampleTime.
     *
     * @param dataId      Configuration data ID (required).
     * @param groupName   Configuration group name (required).
     * @param namespaceId Namespace ID (optional, defaults to "public").
     * @param aggregation Whether aggregation from other servers (optional).
     * @return List of listeners for the configuration.
     * @throws NacosException If retrieval fails.
     */
    ConfigListenerInfo getListeners(String dataId, String groupName, String namespaceId, boolean aggregation)
            throws NacosException;
    
    /**
     * Get all subscribed client configurations by IP.
     *
     * @param ip          Client IP address (required).
     * @param all         Whether to include all subscriptions (optional, defaults to false).
     * @param namespaceId Namespace ID (optional).
     * @param aggregation Whether aggregation from other servers (optional, defaults to 0).
     * @return the subscription status.
     * @throws NacosException if the operation fails.
     */
    ConfigListenerInfo getAllSubClientConfigByIp(String ip, boolean all, String namespaceId, boolean aggregation)
            throws NacosException;
    
    /**
     * Fill all pattern to basic String.
     *
     * <li>If basic string is empty or null, return directly</li>
     * <li>If basic string has wrapper `*`, return basic String</li>
     * <li>If basic string only prefix `*`, add `*` to end</li>
     * <li>If basic string only end with `*`, add `*` to prefix</li>
     * <li>If basic string not `*` in prefix and end, wrapper basic with `*`</li>
     *
     * @param basic basic string
     * @return Filled all pattern string
     */
    default String fillAllPattern(String basic) {
        if (StringUtils.isBlank(basic)) {
            return basic;
        }
        String result = basic;
        if (!basic.startsWith(Constants.ALL_PATTERN)) {
            result = Constants.ALL_PATTERN + result;
        }
        if (!basic.endsWith(Constants.ALL_PATTERN)) {
            result = result + Constants.ALL_PATTERN;
        }
        return result;
    }
}
