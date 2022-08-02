/*
 * Copyright 1999-2021 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.plugin.datasource.mapper.base;

import com.alibaba.nacos.config.server.model.ConfigInfo;
import com.alibaba.nacos.config.server.model.ConfigInfo4Beta;
import com.alibaba.nacos.config.server.model.ConfigInfoBetaWrapper;

import java.sql.Timestamp;

/**
 * The beta config info mapper.
 *
 * @author hyx
 **/

public interface ConfigInfoBetaMapper extends BaseMapper<ConfigInfo4Beta> {
    
    /**
     * Add beta configuration information and publish data change events.
     *
     * @param configInfo config info
     * @param betaIps    ip for push
     * @param srcIp      remote ip
     * @param srcUser    user
     * @param time       time
     * @return success or not.
     */
    boolean addConfigInfo4Beta(ConfigInfo configInfo, String betaIps, String srcIp, String srcUser, Timestamp time);
    
    /**
     * Update beta configuration information.
     *
     * @param configInfo config info
     * @param betaIps    ip for push
     * @param srcIp      remote ip
     * @param srcUser    user
     * @param time       time
     * @return success or not.
     */
    boolean updateConfigInfo4Beta(ConfigInfo configInfo, String betaIps, String srcIp, String srcUser,
            Timestamp time);
    
    /**
     * insert or update beta config cas.
     *
     * @param configInfo config info
     * @param betaIps    ip for push
     * @param srcIp      remote ip
     * @param srcUser    user
     * @param time       time
     * @return success or not.
     */
    boolean insertOrUpdateBeta(final ConfigInfo configInfo, final String betaIps, final String srcIp,
            final String srcUser, final Timestamp time);
    
    /**
     * Delete configuration information, physical deletion.
     *
     * @param dataId data id
     * @param group  group
     * @param tenant tenant
     * @return success or not.
     */
    boolean removeConfigInfo4Beta(final String dataId, final String group, final String tenant);
    
    /**
     * Query beta configuration information based on dataId and group.
     *
     * @param dataId data id
     * @param group  group
     * @param tenant tenant
     * @return {@link ConfigInfo4Beta}
     */
    ConfigInfoBetaWrapper findConfigInfo4Beta(final String dataId, final String group, final String tenant);
}
