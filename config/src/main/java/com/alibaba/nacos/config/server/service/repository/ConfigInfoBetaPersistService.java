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
import com.alibaba.nacos.config.server.model.ConfigInfoStateWrapper;
import com.alibaba.nacos.config.server.model.ConfigInfoWrapper;
import com.alibaba.nacos.config.server.model.ConfigOperateResult;
import com.alibaba.nacos.persistence.model.Page;
import com.alibaba.nacos.persistence.repository.PaginationHelper;

/**
 * Database service, providing access to config_info_beta in the database.
 * Deprecated since 2.5.0，only support on compatibility,replaced with ConfigInfoGray model, will be  soon removed on further version.
 * @author lixiaoshuang
 */
@Deprecated
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
     * get config info beta.
     *
     * @param dataId dataId.
     * @param group  group.
     * @param tenant tenant.
     * @return config info state.
     */
    ConfigInfoStateWrapper findConfigInfo4BetaState(final String dataId, final String group, final String tenant);
    
    /**
     * Add beta configuration information and publish data change events.
     *
     * @param configInfo config info
     * @param betaIps    ip for push
     * @param srcIp      remote ip
     * @param srcUser    user
     * @return config operation result.
     */
    ConfigOperateResult addConfigInfo4Beta(ConfigInfo configInfo, String betaIps, String srcIp, String srcUser);
    
    /**
     * insert or update beta config.
     *
     * @param configInfo config info
     * @param betaIps    ip for push
     * @param srcIp      remote ip
     * @param srcUser    user
     * @return config operation result.
     */
    ConfigOperateResult insertOrUpdateBeta(final ConfigInfo configInfo, final String betaIps, final String srcIp,
            final String srcUser);
    
    /**
     * insert or update beta config cas.
     *
     * @param configInfo config info
     * @param betaIps    ip for push
     * @param srcIp      remote ip
     * @param srcUser    user
     * @return success or not.
     */
    ConfigOperateResult insertOrUpdateBetaCas(final ConfigInfo configInfo, final String betaIps, final String srcIp,
            final String srcUser);
    
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
     * @return config operation result.
     */
    ConfigOperateResult updateConfigInfo4Beta(ConfigInfo configInfo, String betaIps, String srcIp, String srcUser);
    
    /**
     * Update beta configuration information.
     *
     * @param configInfo config info
     * @param betaIps    ip for push
     * @param srcIp      remote ip
     * @param srcUser    user
     * @return success or not.
     */
    ConfigOperateResult updateConfigInfo4BetaCas(ConfigInfo configInfo, String betaIps, String srcIp, String srcUser);
    
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