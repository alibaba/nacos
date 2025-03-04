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

package com.alibaba.nacos.console.handler.config;

import com.alibaba.nacos.api.config.model.ConfigBasicInfo;
import com.alibaba.nacos.api.config.model.ConfigHistoryBasicInfo;
import com.alibaba.nacos.api.config.model.ConfigHistoryDetailInfo;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.Page;

import java.util.List;

/**
 * Interface for handling configuration history related operations.
 *
 * @author zhangyukun
 */
public interface HistoryHandler {
    
    /**
     * Query the detailed configuration history information.
     *
     * @param dataId      the ID of the data
     * @param group       the group ID
     * @param namespaceId the namespace ID
     * @param nid         the history record ID
     * @return the detailed configuration history information
     * @throws NacosException if any error occurs during the operation
     */
    ConfigHistoryDetailInfo getConfigHistoryInfo(String dataId, String group, String namespaceId, Long nid)
            throws NacosException;
    
    /**
     * Query the list of configuration history.
     *
     * @param dataId      the ID of the data
     * @param group       the group ID
     * @param namespaceId the namespace ID
     * @param pageNo      the page number
     * @param pageSize    the number of items per page
     * @return the paginated list of configuration history
     * @throws NacosException if any error occurs during the operation
     */
    Page<ConfigHistoryBasicInfo> listConfigHistory(String dataId, String group, String namespaceId, Integer pageNo,
            Integer pageSize) throws NacosException;
    
    /**
     * Query the previous configuration history information.
     *
     * @param dataId      the ID of the data
     * @param group       the group ID
     * @param namespaceId the namespace ID
     * @param id          the configuration ID
     * @return the previous configuration history information
     * @throws NacosException if any error occurs during the operation
     */
    ConfigHistoryDetailInfo getPreviousConfigHistoryInfo(String dataId, String group, String namespaceId, Long id)
            throws NacosException;
    
    /**
     * Query the list of configurations by namespace.
     *
     * @param namespaceId the namespace ID
     * @return the list of configurations
     * @throws NacosException if any error occurs during the operation
     */
    List<ConfigBasicInfo> getConfigsByTenant(String namespaceId) throws NacosException;
}
