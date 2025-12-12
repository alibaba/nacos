/*
 * Copyright 1999-2023 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.config.server.service.repository;

import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.config.server.model.ConfigHistoryInfo;
import com.alibaba.nacos.config.server.model.ConfigInfo;

import java.util.List;

/**
 * Config history persist service.
 *
 * @author your-name
 * @date 2023/12/01
 */
public interface ConfigHistoryPersistService {
    
    /**
     * Save config history.
     *
     * @param configInfo config info
     * @param srcIp      source ip
     * @param srcUser    source user
     * @param opType     operation type
     */
    void saveConfigHistory(ConfigInfo configInfo, String srcIp, String srcUser, String opType);
    
    /**
     * Query config history by dataId, group and tenant.
     *
     * @param dataId   data id
     * @param group    group
     * @param tenant   tenant
     * @param pageNo   page number
     * @param pageSize page size
     * @return page of config history
     */
    Page<ConfigHistoryInfo> queryConfigHistory(String dataId, String group, String tenant, int pageNo, int pageSize);
    
    /**
     * Query config history by id.
     *
     * @param id config history id
     * @return config history info
     */
    ConfigHistoryInfo queryConfigHistoryById(long id);
    
    /**
     * Get latest config history version.
     *
     * @param dataId data id
     * @param group  group
     * @param tenant tenant
     * @return latest version
     */
    int getLatestVersion(String dataId, String group, String tenant);
    
    /**
     * Query config history by version.
     *
     * @param dataId   data id
     * @param group    group
     * @param tenant   tenant
     * @param version  version
     * @return config history info
     */
    ConfigHistoryInfo queryConfigHistoryByVersion(String dataId, String group, String tenant, int version);
    
    /**
     * Query config history versions by dataId, group and tenant.
     *
     * @param dataId data id
     * @param group  group
     * @param tenant tenant
     * @return list of versions
     */
    List<Integer> queryConfigHistoryVersions(String dataId, String group, String tenant);
}
