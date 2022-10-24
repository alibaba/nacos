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
 * The config tag info mapper.
 *
 * @author hyx
 **/

public interface ConfigInfoTagMapper extends Mapper {
    
    /**
     * Add tag configuration information and publish data change events.
     * The default sql:
     * INSERT INTO config_info_tag(data_id,group_id,tenant_id,tag_id,app_name,content,md5,src_ip,src_user,
     * gmt_create,gmt_modified) VALUES(?,?,?,?,?,?,?,?,?,?,?)
     *
     * @return The sql of add tag configuration.
     */
    String addConfigInfo4Tag();
    
    /**
     * Update tag configuration information.
     * The default sql:
     * UPDATE config_info_tag SET content=?, md5 = ?, src_ip=?,src_user=?,gmt_modified=?,app_name=? WHERE
     * data_id=? AND group_id=? AND tenant_id=? AND tag_id=?
     *
     * @return The sql of updating tag configuration information.
     */
    String updateConfigInfo4Tag();
    
    /**
     * Update tag configuration information.
     * The default sql:
     * UPDATE config_info_tag SET content=?, md5 = ?, src_ip=?,src_user=?,gmt_modified=?,app_name=? WHERE
     * data_id=? AND group_id=? AND tenant_id=? AND tag_id=? AND (md5=? or md5 is null or md5='')
     *
     * @return The sql of updating tag configuration information.
     */
    String updateConfigInfo4TagCas();
    
    /**
     * Query tag configuration information based on dataId and group.
     * The default sql:
     * SELECT id,data_id,group_id,tenant_id,tag_id,app_name,content FROM config_info_tag WHERE data_id=? AND group_id=? AND tenant_id=? AND tag_id=?
     *
     * @return The sql of querying tag configuration information based on dataId and group.
     */
    String findConfigInfo4Tag();
    
    /**
     * Returns the number of beta configuration items.
     * The default sql:
     * SELECT count(ID) FROM config_info_tag
     *
     * @return The sql of querying the number of beta configuration items.
     */
    String configInfoTagCount();
    
    /**
     * The count of config_info table sql.
     * The default sql:
     * SELECT count(*) FROM config_info_tag
     *
     * @return The sql of the count of config_info table.
     */
    String count();
    
    /**
     * Query all tag config info for dump task.
     * The default sql:
     * SELECT t.id,data_id,group_id,tenant_id,tag_id,app_name,content,md5,gmt_modified
     * FROM (  SELECT id FROM config_info_tag  ORDER BY id LIMIT ?,? ) g,
     * config_info_tag t  WHERE g.id = t.id
     *
     * @param startRow The start index.
     * @param pageSize The size of page.
     * @return The sql of querying all tag config info for dump task.
     */
    String findAllConfigInfoTagForDumpAllFetchRows(int startRow, int pageSize);
    
    /**
     * Delete configuration; database atomic operation, minimum SQL action, no business encapsulation.
     * The default sql:
     * DELETE FROM config_info_tag WHERE data_id=? AND group_id=? AND tenant_id=? AND tag_id=?
     *
     * @return The sql of deleting configuration; database atomic operation, minimum SQL action, no business encapsulation.
     */
    String removeConfigInfoTag();
}
