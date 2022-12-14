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

package com.alibaba.nacos.plugin.datasource.mapper;

/**
 * The history config info mapper.
 *
 * @author hyx
 **/

public interface HistoryConfigInfoMapper extends Mapper {
    
    /**
     * Delete data before startTime.
     * The default sql:
     * DELETE FROM his_config_info WHERE gmt_modified < ? LIMIT ?
     *
     * @return The sql of deleting data before startTime.
     */
    String removeConfigHistory();
    
    /**
     * Get the number of configurations before the specified time.
     * The default sql:
     * SELECT count(*) FROM his_config_info WHERE gmt_modified < ?
     *
     * @return The sql of getting the number of configurations before the specified time.
     */
    String findConfigHistoryCountByTime();
    
    /**
     * Query deleted config.
     * The default sql:
     * SELECT DISTINCT data_id, group_id, tenant_id FROM his_config_info WHERE op_type = 'D' AND gmt_modified >=? AND gmt_modified <= ?
     *
     * @return The sql of querying deleted config.
     */
    String findDeletedConfig();
    
    /**
     * List configuration history change record.
     * The default sql:
     * SELECT nid,data_id,group_id,tenant_id,app_name,src_ip,src_user,op_type,gmt_create,gmt_modified FROM his_config_info
     * WHERE data_id = ? AND group_id = ? AND tenant_id = ? ORDER BY nid DESC
     *
     * @return The sql of listing configuration history change record.
     */
    String findConfigHistoryFetchRows();
    
    /**
     * Get previous config detail.
     * The default sql:
     * SELECT nid,data_id,group_id,tenant_id,app_name,content,md5,src_user,src_ip,op_type,gmt_create,gmt_modified
     * FROM his_config_info WHERE nid = (SELECT max(nid) FROM his_config_info WHERE id = ?)
     *
     * @return The sql of getting previous config detail.
     */
    String detailPreviousConfigHistory();
}
