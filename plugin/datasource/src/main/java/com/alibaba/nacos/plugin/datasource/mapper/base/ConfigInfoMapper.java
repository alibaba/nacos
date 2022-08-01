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

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

/**
 * The ConfigInfo Mapper, providing access to ConfigInfo in the database.
 *
 * @author hyx
 **/
public interface ConfigInfoMapper extends BaseMapper<ConfigInfo> {
    
    /**
     * Write to the main table, insert or update.
     *
     * @param srcIp             remote ip
     * @param srcUser           user
     * @param configInfo        config info
     * @param time              time
     * @param configAdvanceInfo advance info
     * @return Whether insert or update succeed
     */
    boolean insertOrUpdateConfigInfo(String srcIp, String srcUser, ConfigInfo configInfo,
            Timestamp time, Map<String, Object> configAdvanceInfo);
    
    /**
     * To get the all configInfo by dataId, group and tenant.
     *
     * @param dataId            data id
     * @param group             group
     * @param tenant            tenant
     * @return The all configInfo by dataId, group and tenant.
     */
    List<ConfigInfo> selectAll(String dataId, String group, String tenant);
    
    /**
     * To delete a configInfo.
     * @param dataId            data id
     * @param group             group
     * @param tenant            tenant
     * @param clientIp          remote ip
     * @param srcUser           user
     * @return The result of delete.
     */
    boolean delete(String dataId, String group, String tenant, String clientIp, String srcUser);
}
