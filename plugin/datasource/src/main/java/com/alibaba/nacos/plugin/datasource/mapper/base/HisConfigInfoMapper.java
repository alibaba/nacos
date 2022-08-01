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

/**
 * The history configInfo mapper.
 *
 * @author hyx
 **/

public interface HisConfigInfoMapper {
    /**
     * Delete data before startTime.
     *
     * @param startTime start time
     * @param limitSize limit size
     * @return the number of success.
     */
    Integer removeConfigHistory(final Timestamp startTime, final int limitSize);
    
    /**
     * Get the number of configurations before the specified time.
     *
     * @param startTime start time
     * @return count of history config that meet the conditions
     */
    Integer findConfigHistoryCountByTime(final Timestamp startTime);
    
    /**
     * Update change records; database atomic operations, minimal sql actions, no business encapsulation.
     *
     * @param id         id
     * @param configInfo config info
     * @param srcIp      ip
     * @param srcUser    user
     * @param time       time
     * @param ops        ops type
     * @return the number of success.
     */
    Integer insertConfigHistoryAtomic(long id, ConfigInfo configInfo, String srcIp, String srcUser, final Timestamp time,
            String ops);
}
