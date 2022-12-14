/*
 * Copyright 1999-2022 Alibaba Group Holding Ltd.
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

import com.alibaba.nacos.config.server.model.ConfigInfo;
import com.alibaba.nacos.config.server.model.ConfigInfo4Beta;
import com.alibaba.nacos.config.server.model.ConfigInfoBetaWrapper;
import com.alibaba.nacos.config.server.model.ConfigInfoWrapper;
import com.alibaba.nacos.config.server.model.Page;

import java.sql.Timestamp;

/**
 * Database service, providing access to config_info_beta in the database.
 *
 * @author lixiaoshuang
 */
public interface ConfigInfoBetaPersistService {
    
    /**
     * create Pagination utils.
     *
     * @param <E> Generic object
     * @return {@link PaginationHelper}
     */
    <E> PaginationHelper<E> createPaginationHelper();
    
    //------------------------------------------insert---------------------------------------------//
    
    /**
     * Add beta configuration information and publish data change events.
     *
     * @param configInfo config info
     * @param betaIps    ip for push
     * @param srcIp      remote ip
     * @param srcUser    user
     * @param time       time
     * @param notify     whether to push
     */
    void addConfigInfo4Beta(ConfigInfo configInfo, String betaIps, String srcIp, String srcUser, Timestamp time,
            boolean notify);
    
    /**
     * insert or update beta config.
     *
     * @param configInfo config info
     * @param betaIps    ip for push
     * @param srcIp      remote ip
     * @param srcUser    user
     * @param time       time
     * @param notify     whether to push
     */
    void insertOrUpdateBeta(final ConfigInfo configInfo, final String betaIps, final String srcIp, final String srcUser,
            final Timestamp time, final boolean notify);
    
    /**
     * insert or update beta config cas.
     *
     * @param configInfo config info
     * @param betaIps    ip for push
     * @param srcIp      remote ip
     * @param srcUser    user
     * @param time       time
     * @param notify     whether to push
     * @return success or not.
     */
    boolean insertOrUpdateBetaCas(final ConfigInfo configInfo, final String betaIps, final String srcIp,
            final String srcUser, final Timestamp time, final boolean notify);
    
    //------------------------------------------delete---------------------------------------------//
    
    /**
     * Delete configuration information, physical deletion.
     *
     * @param dataId data id
     * @param group  group
     * @param tenant tenant
     */
    void removeConfigInfo4Beta(final String dataId, final String group, final String tenant);
    
    //------------------------------------------update---------------------------------------------//
    
    /**
     * Update beta configuration information.
     *
     * @param configInfo config info
     * @param betaIps    ip for push
     * @param srcIp      remote ip
     * @param srcUser    user
     * @param time       time
     * @param notify     whether to push
     */
    void updateConfigInfo4Beta(ConfigInfo configInfo, String betaIps, String srcIp, String srcUser, Timestamp time,
            boolean notify);
    
    /**
     * Update beta configuration information.
     *
     * @param configInfo config info
     * @param betaIps    ip for push
     * @param srcIp      remote ip
     * @param srcUser    user
     * @param time       time
     * @param notify     whether to push
     * @return success or not.
     */
    boolean updateConfigInfo4BetaCas(ConfigInfo configInfo, String betaIps, String srcIp, String srcUser,
            Timestamp time, boolean notify);
    
    //------------------------------------------select---------------------------------------------//
    
    /**
     * Query beta configuration information based on dataId and group.
     *
     * @param dataId data id
     * @param group  group
     * @param tenant tenant
     * @return {@link ConfigInfo4Beta}
     */
    ConfigInfoBetaWrapper findConfigInfo4Beta(final String dataId, final String group, final String tenant);
    
    /**
     * Returns the number of beta configuration items.
     *
     * @return number of configuration items..
     */
    int configInfoBetaCount();
    
    /**
     * Query all beta config info for dump task.
     *
     * @param pageNo   page number
     * @param pageSize page size
     * @return {@link Page} with {@link ConfigInfoWrapper} generation
     */
    Page<ConfigInfoBetaWrapper> findAllConfigInfoBetaForDumpAll(final int pageNo, final int pageSize);
    
}
