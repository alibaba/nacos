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

import com.alibaba.nacos.config.server.model.ConfigHistoryInfo;
import com.alibaba.nacos.config.server.model.ConfigInfo;
import com.alibaba.nacos.config.server.model.Page;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

/**
 * Database service, providing access to his_config_info in the database.
 *
 * @author lixiaoshuang
 */
public interface HistoryConfigInfoPersistService {
    
    /**
     * create Pagination utils.
     *
     * @param <E> Generic object
     * @return {@link PaginationHelper}
     */
    <E> PaginationHelper<E> createPaginationHelper();
    
    /**
     * Convert delete config.
     *
     * @param list origin data
     * @return {@link ConfigInfo} list
     */
    List<ConfigInfo> convertDeletedConfig(List<Map<String, Object>> list);
    
    //------------------------------------------insert---------------------------------------------//
    
    /**
     * Update change records; database atomic operations, minimal sql actions, no business encapsulation.
     *
     * @param id         id
     * @param configInfo config info
     * @param srcIp      ip
     * @param srcUser    user
     * @param time       time
     * @param ops        ops type
     */
    void insertConfigHistoryAtomic(long id, ConfigInfo configInfo, String srcIp, String srcUser, final Timestamp time,
            String ops);
    //------------------------------------------delete---------------------------------------------//
    
    /**
     * Delete data before startTime.
     *
     * @param startTime start time
     * @param limitSize limit size
     */
    void removeConfigHistory(final Timestamp startTime, final int limitSize);
    //------------------------------------------update---------------------------------------------//
    //------------------------------------------select---------------------------------------------//
    
    /**
     * Query deleted config.
     *
     * @param startTime start time
     * @param endTime   end time
     * @return {@link ConfigInfo} list
     */
    List<ConfigInfo> findDeletedConfig(final Timestamp startTime, final Timestamp endTime);
    
    /**
     * List configuration history change record.
     *
     * @param dataId   data Id
     * @param group    group
     * @param tenant   tenant
     * @param pageNo   no
     * @param pageSize size
     * @return {@link Page} with {@link ConfigHistoryInfo} generation
     */
    Page<ConfigHistoryInfo> findConfigHistory(String dataId, String group, String tenant, int pageNo, int pageSize);
    
    /**
     * Get history config detail.
     *
     * @param nid nid
     * @return {@link ConfigHistoryInfo}
     */
    ConfigHistoryInfo detailConfigHistory(Long nid);
    
    /**
     * Get previous config detail.
     *
     * @param id id
     * @return {@link ConfigHistoryInfo}
     */
    ConfigHistoryInfo detailPreviousConfigHistory(Long id);
    
    /**
     * Get the number of configurations before the specified time.
     *
     * @param startTime start time
     * @return count of history config that meet the conditions
     */
    @Deprecated
    int findConfigHistoryCountByTime(final Timestamp startTime);
}
