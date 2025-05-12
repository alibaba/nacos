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

package com.alibaba.nacos.maintainer.client.config;

import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.config.model.ConfigGrayInfo;
import com.alibaba.nacos.api.exception.NacosException;

/**
 *  Nacos Config module beta config maintainer service.
 *
 * @author xiweng.yy
 */
public interface BetaConfigMaintainerService {
    
    /**
     * Beta Publish a configuration with additional metadata.
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
     * @param betaIps     Configuration beta IPs, multiple IPs separated by commas ',' (required).
     * @return Whether the configuration was published successfully.
     * @throws NacosException If publishing fails.
     */
    boolean publishBetaConfig(String dataId, String groupName, String namespaceId, String content, String appName,
            String srcUser, String configTags, String desc, String type, String betaIps) throws NacosException;
    
    /**
     * Stop a beta configuration by dataId and groupName.
     *
     * @param dataId    Configuration data ID (required).
     * @param groupName Configuration group name (required).
     * @return Whether the beta configuration was stopped successfully.
     * @throws NacosException If stopping fails.
     */
    default boolean stopBeta(String dataId, String groupName) throws NacosException {
        return stopBeta(dataId, groupName, Constants.DEFAULT_NAMESPACE_ID);
    }
    
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
    default ConfigGrayInfo queryBeta(String dataId, String groupName) throws NacosException {
        return queryBeta(dataId, groupName, Constants.DEFAULT_NAMESPACE_ID);
    }
    
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
    
}
