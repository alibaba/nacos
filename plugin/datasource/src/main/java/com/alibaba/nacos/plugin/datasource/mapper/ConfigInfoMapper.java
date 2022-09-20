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

import java.util.List;
import java.util.Map;

/**
 * The mapper of config info.
 *
 * @author hyx
 **/

public interface ConfigInfoMapper extends Mapper {
    
    /**
     * Update md5.
     * The default sql:
     * UPDATE config_info SET md5 = ? WHERE data_id=? AND group_id=? AND tenant_id=? AND gmt_modified=?
     *
     * @return the sql of updating md5.
     */
    String updateMd5();
    
    /**
     * Get the maxId.
     * The default sql:
     * SELECT max(id) FROM config_info
     *
     * @return the sql of getting the maxId.
     */
    String findConfigMaxId();
    
    /**
     * Find all dataId and group.
     * The default sql:
     * SELECT DISTINCT data_id, group_id FROM config_info
     *
     * @return The sql of finding all dataId and group.
     */
    String findAllDataIdAndGroup();
    
    /**
     * Query common configuration information based on dataId and group.
     * The default sql:
     * SELECT id,data_id,group_id,tenant_id,app_name,content FROM config_info WHERE data_id=? AND group_id=? AND tenant_id=? AND app_name=?
     *
     * @return Query common configur.
     */
    String findConfigInfoApp();
    
    /**
     * Query configuration information based on dataId and group.
     * The default sql:
     * SELECT id,data_id,group_id,content FROM config_info WHERE data_id=? AND group_id=? AND tenant_id=?
     *
     * @return The sql to select config_info by dataId and group.
     */
    String findConfigInfoBase();
    
    /**
     * Query configuration information by primary key ID.
     * The default sql:
     * SELECT id,data_id,group_id,tenant_id,app_name,content FROM config_info WHERE id=?
     *
     * @return The sql to select configInfo by ID.
     */
    String findConfigInfo();
    
    /**
     * Query configuration information based on dataId.
     * The default sql:
     * SELECT id,data_id,group_id,tenant_id,app_name,content FROM config_info WHERE data_id=? AND tenant_id=?
     *
     * @return The sql to
     */
    String findConfigInfoByDataIdFetchRows();
    
    /**
     * Query the count of the configInfo by dataId.
     * The default sql:
     * SELECT count(*) FROM config_info WHERE data_id=? AND tenant_id=? AND app_name=?
     *
     * @return The num of the count of configInfo.
     */
    String findConfigInfoByDataIdAndAppCountRows();
    
    /**
     * Query configuration information based on dataId.
     * The default sql:
     * SELECT id,data_id,group_id,tenant_id,app_name,content FROM config_info WHERE data_id=? AND tenant_id=? AND app_name=?
     *
     * @return The sql of query configuration information based on dataId.
     */
    String findConfigInfoByDataIdAndAppFetchRows();
    
    /**
     * The count of config_info table sql.
     * The default sql:
     * SELECT count(*) FROM config_info
     *
     * @return The sql of the count of config_info table.
     */
    String count();
    
    
    /**
     * Query the count of config_info by tenantId and appName.
     * The default sql:
     * SELECT count(*) FROM config_info WHERE tenant_id LIKE ? AND app_name=?
     *
     * @return The sql of querying the count of config_info.
     */
    String findConfigInfoByAppCountRows();
    
    /**
     * Query configuration information based on group.
     * The default sql:
     * SELECT id,data_id,group_id,tenant_id,app_name,content FROM config_info WHERE tenant_id LIKE ? AND app_name=?
     *
     * @return The sql of querying configration information based on group.
     */
    String findConfigInfoByAppFetchRows();
    
    /**
     * Returns the number of configuration items.
     * The default sql:
     * SELECT count(*) FROM config_info WHERE tenant_id LIKE ?
     *
     * @return The sql of querying the number of configuration items.
     */
    String configInfoLikeTenantCount();
    
    /**
     * Get tenant id list  by page.
     * The default sql:
     * SELECT tenant_id FROM config_info WHERE tenant_id != '' GROUP BY tenant_id LIMIT ?, ?
     *
     * @return The sql of getting tenant id list  by page.
     */
    String getTenantIdList();
    
    /**
     * Get group id list  by page.
     * The default sql:
     * SELECT group_id FROM config_info WHERE tenant_id ='' GROUP BY group_id LIMIT ?, ?
     *
     * @return The sql of getting group id list  by page.
     */
    String getGroupIdList();
    
    /**
     * Query all configuration information by page.
     * The default sql:
     * SELECT data_id,group_id,app_name  FROM (
     * SELECT id FROM config_info WHERE tenant_id LIKE ? ORDER BY id LIMIT ?, ? ) g,
     * config_info t WHERE g.id = t.id "
     *
     * @return The sql of querying all configuration information.
     */
    String findAllConfigKey();
    
    /**
     * Query all configuration information by page.
     * The default sql:
     * SELECT t.id,data_id,group_id,content,md5 FROM (
     * SELECT id FROM config_info ORDER BY id LIMIT ?,?) g,
     * config_info t  WHERE g.id = t.id
     *
     * @return The sql of querying all configuration information by page.
     */
    String findAllConfigInfoBaseFetchRows();
    
    /**
     * Query all configuration information by page for dump task.
     * The default sql:
     * SELECT t.id,type,data_id,group_id,tenant_id,app_name,content,type,md5,gmt_modified
     * FROM ( SELECT id FROM config_info   ORDER BY id LIMIT ?,?  ) g, config_info t
     * WHERE g.id = t.id
     *
     * @return The sql of querying all configuration information by page for dump task.
     */
    String findAllConfigInfoForDumpAllFetchRows();
    
    /**
     * Query all config info.
     * The default sql:
     * SELECT id,data_id,group_id,tenant_id,app_name,content,md5,gmt_modified,type,encrypted_data_key
     * FROM config_info WHERE id > ? ORDER BY id ASC LIMIT ?,?
     *
     * @return The sql of querying all config info.
     */
    String findAllConfigInfoFragment();
    
    /**
     * Query change config.
     * The default sql:
     * SELECT data_id, group_id, tenant_id, app_name, content, gmt_modified,encrypted_data_key
     * FROM config_info WHERE gmt_modified >=? AND gmt_modified <= ?
     *
     * @return The sql of querying change config.
     */
    String findChangeConfig();
    
    /**
     * Get the count of config information.
     * The default sql:
     * SELECT count(*) FROM config_info WHERE ...
     *
     * @param params The map of params, the key is the parameter name(dataId, groupId, tenantId, appName, startTime, endTime, content),
     *               the value is the key's value.
     * @return The sql of getting the count of config information.
     */
    String findChangeConfigCountRows(Map<String, String> params);
    
    /**
     * According to the time period and configuration conditions to query the eligible configuration.
     * The default sql:
     * SELECT id,data_id,group_id,tenant_id,app_name,content,type,md5,gmt_modified FROM config_info WHERE ...
     *
     * @param params The map of params, the key is the parameter name(dataId, groupId, tenantId, appName, startTime, endTime, content),
     *               the value is the key's value.
     * @return The sql of getting config information according to the time period.
     */
    String findChangeConfigFetchRows(Map<String, String> params);
    
    /**
     * Add configuration; database atomic operation, minimum sql action, no business encapsulation.
     * The default sql:
     * INSERT INTO config_info(data_id,group_id,tenant_id,app_name,content,md5,src_ip,src_user,gmt_create,
     * gmt_modified,c_desc,c_use,effect,type,c_schema,encrypted_data_key) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
     *
     * @return The sql of adding configuration.
     */
    String addConfigInfoAtomic();
    
    /**
     * Remove configuration; database atomic operation, minimum SQL action, no business encapsulation.
     * The default sql:
     * DELETE FROM config_info WHERE data_id=? AND group_id=? AND tenant_id=?
     *
     * @return The sql of removing configuration.
     */
    String removeConfigInfoAtomic();
    
    /**
     * Update configuration; database atomic operation, minimum SQL action, no business encapsulation.
     * The default sql:
     * UPDATE config_info SET content=?, md5 = ?, src_ip=?,src_user=?,gmt_modified=?,
     * app_name=?,c_desc=?,c_use=?,effect=?,type=?,c_schema=?,encrypted_data_key=?
     * WHERE data_id=? AND group_id=? AND tenant_id=?
     *
     * @return The sql of updating configuration.
     */
    String updateConfigInfoAtomic();
    
    /**
     * Query configuration information; database atomic operation, minimum SQL action, no business encapsulation.
     * The default sql:
     * SELECT gmt_create,gmt_modified,src_user,src_ip,c_desc,c_use,effect,type,c_schema
     * FROM config_info WHERE data_id=? AND group_id=? AND tenant_id=?
     *
     * @return The sql of querying configuration information.
     */
    String findConfigAdvanceInfo();
    
    /**
     * Query configuration information; database atomic operation, minimum SQL action, no business encapsulation.
     * The default sql:
     * SELECT id,data_id,group_id,tenant_id,app_name,content,md5,
     * gmt_create,gmt_modified,src_user,src_ip,c_desc,c_use,effect,type,c_schema,encrypted_data_key FROM config_info
     * WHERE data_id=? AND group_id=? AND tenant_id=?
     *
     * @return The sql of getting all config info.
     */
    String findConfigAllInfo();
    
    /**
     * list group key md5 by page.
     * The default sql:
     * SELECT t.id,data_id,group_id,tenant_id,app_name,md5,type,gmt_modified,encrypted_data_key FROM (
     * SELECT id FROM config_info ORDER BY id LIMIT ?,?  ) g, config_info t
     * WHERE g.id = t.id
     *
     * @return The sql of listing group key md5 by page.
     */
    String listGroupKeyMd5ByPageFetchRows();
    
    /**
     * Query config info.
     * The default sql:
     * SELECT id,data_id,group_id,tenant_id,app_name,content,type,gmt_modified,md5,encrypted_data_key FROM config_info
     * WHERE data_id=? AND group_id=? AND tenant_id=?
     *
     * @return The sql of querying config info.
     */
    String queryConfigInfo();
    
    /**
     * Query base config info list by namespace.
     * The default sql:
     * SELECT data_id,group_id,tenant_id,app_name,type FROM config_info WHERE tenant_id=?
     *
     * @return The sql of querying dataId list by namespace.
     */
    String queryConfigInfoByNamespace();
    
    /**
     * query all configuration information according to group, appName, tenant (for export).
     * The default sql:
     * SELECT id,data_id,group_id,tenant_id,app_name,content,type,md5,gmt_create,gmt_modified,
     * src_user,src_ip,c_desc,c_use,effect,c_schema,encrypted_data_key
     * FROM config_info WHERE ...
     *
     * @param ids       ids
     * @param params    The map of params, the key is the parameter name(dataId, group, appName),
     *                  the value is the key's value.
     * @return Collection of ConfigInfo objects
     */
    String findAllConfigInfo4Export(List<Long> ids, Map<String, String> params);
    
    /**
     * Use select in to realize batch query of db records; subQueryLimit specifies the number of conditions in in, with
     * an upper limit of 20.
     * The default sql:
     * SELECT data_id, group_id, tenant_id, app_name, content FROM config_info WHERE group_id = ? AND tenant_id = ? AND data_id IN (...)
     *
     * @param dataIds data id list
     * @param subQueryLimit sub query limit
     * @return The sql to get config information by batch.
     */
    String findConfigInfoByBatch(List<String> dataIds, int subQueryLimit);
    
    /**
     * Get the count of config information.
     * The default sql:
     * SELECT count(*) FROM config_info WHERE ...
     *
     * @param params The map of params, the key is the parameter name(dataId, groupId, tenant_id, appName, content),
     *               the value is the key's value.
     * @return The sql of getting the count of config information.
     */
    String findConfigInfoLikeCountRows(Map<String, String> params);
    
    /**
     * Get the config information.
     * The default sql:
     * SELECT id,data_id,group_id,tenant_id,app_name,content FROM config_info WHERE
     *
     * @param params The map of params, the key is the parameter name(dataId, groupId, tenant_id, appName, content),
     *               the value is the key's value.
     * @return The sql of getting the config information.
     */
    String findConfigInfoLikeFetchRows(Map<String, String> params);
    
    /**
     * Get the count of config information.
     * The default sql:
     * SELECT count(*) FROM config_info WHERE ...
     *
     * @param params The map of params, the key is the parameter name(dataId, groupId, tenant_id, content),
     *               the value is the arbitrary object.
     * @return The sql of getting the count of config information.
     */
    String findConfigInfoBaseLikeCountRows(Map<String, String> params);
    
    /**
     * Get the config information.
     * The default sql:
     * SELECT id,data_id,group_id,tenant_id,content FROM config_info WHERE ...
     *
     * @param params The map of params, the key is the parameter name(dataId, groupId, tenant_id, content),
     *               the value is the key's value.
     * @return The sql of getting the config information.
     */
    String findConfigInfoBaseLikeFetchRows(Map<String, String> params);
}
