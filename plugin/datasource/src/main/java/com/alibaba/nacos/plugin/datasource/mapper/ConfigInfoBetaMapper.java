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
 * The beta config info mapper.
 *
 * @author hyx
 **/

public interface ConfigInfoBetaMapper extends Mapper {
    
    /**
     * Update beta configuration information.
     * UPDATE config_info_beta SET content=?, md5=?, beta_ips=?, src_ip=?,src_user=?,gmt_modified=?,app_name=?
     * WHERE data_id=? AND group_id=? AND tenant_id=? AND (md5=? or md5 is null or md5='')
     *
     * @return The sql of updating beta configuration information.
     */
    String updateConfigInfo4BetaCas();
    
    /**
     * Query all beta config info for dump task.
     * The default sql:
     * SELECT t.id,data_id,group_id,tenant_id,app_name,content,md5,gmt_modified,beta_ips,encrypted_data_key
     * FROM ( SELECT id FROM config_info_beta  ORDER BY id LIMIT ?,?  ) g, config_info_beta t WHERE g.id = t.id
     *
     * @param startRow The start index.
     * @param pageSize The size of page.
     * @return The sql of querying all beta config info for dump task.
     */
    String findAllConfigInfoBetaForDumpAllFetchRows(int startRow, int pageSize);
}
