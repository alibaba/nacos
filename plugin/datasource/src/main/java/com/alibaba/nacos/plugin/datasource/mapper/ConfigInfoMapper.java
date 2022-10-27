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

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

/**
 * The mapper of config info.
 *
 * @author hyx
 **/

public interface ConfigInfoMapper extends Mapper {
    
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
     * Query configuration information based on dataId.
     * The default sql:
     * SELECT id,data_id,group_id,tenant_id,app_name,content FROM config_info WHERE data_id=? AND tenant_id=?
     *
     * @param startRow The start index.
     * @param pageSize The size of page.
     * @return The sql to
     */
    String findConfigInfoByDataIdFetchRows(int startRow, int pageSize);
    
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
     * @param startRow The start index.
     * @param pageSize The size of page.
     * @return The sql of query configuration information based on dataId.
     */
    String findConfigInfoByDataIdAndAppFetchRows(int startRow, int pageSize);
    
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
     * @param startRow The start index.
     * @param pageSize The size of page.
     * @return The sql of querying configration information based on group.
     */
    String findConfigInfoByAppFetchRows(int startRow, int pageSize);
    
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
     * @param startRow The start index.
     * @param pageSize The size of page.
     * @return The sql of querying all configuration information by page.
     */
    String findAllConfigInfoBaseFetchRows(int startRow, int pageSize);
    
    /**
     * Query all configuration information by page for dump task.
     * The default sql:
     * SELECT t.id,type,data_id,group_id,tenant_id,app_name,content,type,md5,gmt_modified
     * FROM ( SELECT id FROM config_info   ORDER BY id LIMIT ?,?  ) g, config_info t
     * WHERE g.id = t.id
     *
     * @param startRow The start index.
     * @param pageSize The size of page.
     * @return The sql of querying all configuration information by page for dump task.
     */
    String findAllConfigInfoForDumpAllFetchRows(int startRow, int pageSize);
    
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
     * @param startTime start time
     * @param endTime   end time
     * @return The sql of getting the count of config information.
     */
    String findChangeConfigCountRows(Map<String, String> params, final Timestamp startTime, final Timestamp endTime);
    
    /**
     * According to the time period and configuration conditions to query the eligible configuration.
     * The default sql:
     * SELECT id,data_id,group_id,tenant_id,app_name,content,type,md5,gmt_modified FROM config_info WHERE ...
     *
     * @param params The map of params, the key is the parameter name(dataId, groupId, tenantId, appName, startTime, endTime, content),
     *               the value is the key's value.
     * @param startTime start time
     * @param endTime   end time
     * @param startRow The start index.
     * @param pageSize The size of page.
     * @param lastMaxId The max id.
     * @return The sql of getting config information according to the time period.
     */
    String findChangeConfigFetchRows(Map<String, String> params, final Timestamp startTime, final Timestamp endTime,
            int startRow, int pageSize, long lastMaxId);
    
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
     * @param paramSize The size of ids.
     * @return The sql to get config information by batch.
     */
    String findConfigInfoByBatch(int paramSize);
    
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
     * @param startRow The start index.
     * @param pageSize The size of page.
     * @return The sql of getting the config information.
     */
    String findConfigInfoLikeFetchRows(Map<String, String> params, int startRow, int pageSize);
    
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
     * @param startRow The start index.
     * @param pageSize The size of page.
     * @return The sql of getting the config information.
     */
    String findConfigInfoBaseLikeFetchRows(Map<String, String> params, int startRow, int pageSize);
    
    /**
     * To get number of config information by dataId and tenantId.
     * The default sql:
     * SELECT count(*) FROM config_info WHERE data_id=? AND tenant_id=?
     *
     * @return The sql of query number.
     */
    String findConfigInfoByDataIdCountRows();
    
    /**
     * find the count of data_id AND tenant_id.
     * The default sql:
     * SELECT count(*) FROM config_info WHERE data_id=? AND tenant_id=? ...
     *
     * @param params The map of appName/
     * @return The sql of finding the count of data_id and tenant_id.
     */
    String findConfigInfoByDataIdAndAdvanceCountRows(Map<String, String> params);
    
    /**
     * find config info.
     * The default sql:
     * SELECT id,data_id,group_id,tenant_id,app_name,content FROM config_info WHERE data_id=? AND tenant_id=? ...
     *
     * @param params The map of app name.
     * @param startRow The start index.
     * @param pageSize The size of page.
     * @return The sql of finding config info.
     */
    String findConfigInfoByDataIdAndAdvanceFetchRows(Map<String, String> params, int startRow, int pageSize);
    
    /**
     * find the count of config info.
     * The default sql:
     * SELECT count(*) FROM config_info ...
     *
     * @param params The mpa of dataId, groupId and appName.
     * @return The count of config info.
     */
    String findConfigInfo4PageCountRows(Map<String, String> params);
    
    /**
     * find config info.
     * The default sql:
     * SELECT id,data_id,group_id,tenant_id,app_name,content,type,encrypted_data_key FROM config_info ...
     *
     * @param params The mpa of dataId, groupId and appName.
     * @param startRow The start index.
     * @param pageSize The size of page.
     * @return The sql of finding config info.
     */
    String findConfigInfo4PageFetchRows(Map<String, String> params, int startRow, int pageSize);
    
    /**
     * The count of querying configuration information based on dataId.
     * The default sql:
     * SELECT count(*) FROM config_info WHERE data_id=? AND tenant_id=?
     *
     * @return The sql of query count.
     */
    String findConfigInfoBaseByDataIdCountRows();
    
    /**
     * Query configuration information based on dataId.
     * The default sql:
     * SELECT id,data_id,group_id,content FROM config_info WHERE data_id=? AND tenant_id=?
     *
     * @param startRow The start index.
     * @param pageSize The size of page.
     * @return The sql of query configuration information based on dataId.
     */
    String findConfigInfoBaseByDataIdFetchRows(int startRow, int pageSize);
    
    /**
     * Get the count of querying configuration information based on group.
     * The default sql:
     * SELECT count(*) FROM config_info WHERE group_id=? AND tenant_id=?
     *
     * @return The count of config info by groupId and tenantId.
     */
    String findConfigInfoByGroupCountRows();
    
    /**
     * Get the config info by groupId and tenantId.
     * The default sql:
     * SELECT id,data_id,group_id,tenant_id,app_name,content FROM config_info WHERE group_id=? AND tenant_id=?
     *
     * @param startRow The start index.
     * @param pageSize The size of page.
     * @return Get the config info by groupId and tenantId.
     */
    String findConfigInfoByGroupFetchRows(int startRow, int pageSize);
    
    /**
     * The count of querying configuration information based on group.
     * The default sql:
     * SELECT count(*) FROM config_info WHERE group_id=? AND tenant_id=? AND app_name =?
     *
     * @return The sql of the count of querying configuration information based on group.
     */
    String findConfigInfoByGroupAndAppCountRows();
    
    /**
     * Query configuration information based on group.
     * The default sql:
     * SELECT id,data_id,group_id,tenant_id,app_name,content FROM config_info WHERE group_id=? AND tenant_id=? AND app_name =?
     *
     * @param startRow The start index.
     * @param pageSize The size of page.
     * @return The sql of querying configuration information based on group.
     */
    String findConfigInfoByGroupAndAppFetchRows(int startRow, int pageSize);
    
    /**
     * Query configuration information count.
     * The default sql:
     * SELECT count(*) FROM config_info WHERE tenant_id LIKE ?  ...
     *
     * @param params The map of appName.
     * @return Query configuration information count.
     */
    String findConfigInfoByAdvanceCountRows(Map<String, String> params);
    
    /**
     * Query configuration information.
     * The default sql:
     * SELECT id,data_id,group_id,tenant_id,app_name,content FROM config_info WHERE tenant_id LIKE ? ...
     *
     * @param startRow The start index.
     * @param pageSize The size of page.
     * @param params The map of appName.
     * @return Query configuration information.
     */
    String findConfigInfoByAdvanceFetchRows(Map<String, String> params, int startRow, int pageSize);
    
    /**
     * Query configuration information count based on group.
     * The default sql:
     * SELECT count(*) FROM config_info WHERE group_id=? AND tenant_id=?
     *
     * @return Query configuration information count based on group.
     */
    String findConfigInfoBaseByGroupCountRows();
    
    /**
     * Query configuration information based on group.
     * The default sql:
     * SELECT id,data_id,group_id,content FROM config_info WHERE group_id=? AND tenant_id=?
     *
     * @param startRow The start index.
     * @param pageSize The size of page.
     * @return Query configuration information based on group.
     */
    String findConfigInfoBaseByGroupFetchRows(int startRow, int pageSize);
    
    /**
     * Query config info count.
     * The default sql:
     * SELECT count(*) FROM config_info ...
     *
     * @param params The map of dataId, group, appName, content
     * @return The sql of querying config info count
     */
    String findConfigInfoLike4PageCountRows(Map<String, String> params);
    
    /**
     * Query config info.
     * The default sql:
     * SELECT id,data_id,group_id,tenant_id,app_name,content,encrypted_data_key FROM config_info ...
     *
     * @param params The map of dataId, group, appName, content
     * @param startRow The start index.
     * @param pageSize The size of page.
     * @return The sql of querying config info
     */
    String findConfigInfoLike4PageFetchRows(Map<String, String> params, int startRow, int pageSize);
    
    /**
     * Query all configuration information by page.
     * The default sql:
     * SELECT t.id,data_id,group_id,tenant_id,app_name,content,md5 "
     *                 + " FROM (  SELECT id FROM config_info WHERE tenant_id LIKE ? ORDER BY id LIMIT ?,? )"
     *                 + " g, config_info t  WHERE g.id = t.id
     *
     * @param startRow The start index.
     * @param pageSize The size of page.
     * @return Query all configuration information by page.
     */
    String findAllConfigInfoFetchRows(int startRow, int pageSize);
    
    /**
     * Query configuration information based on dataId and group.
     * The default sql:
     * SELECT id,data_id,group_id,tenant_id,app_name,content FROM config_info WHERE data_id=? AND group_id=? AND tenant_id=? ...
     *
     * @param params The map of appName.
     * @return The sql of querying configuration information based on dataId and group.
     */
    String findConfigInfoAdvanceInfo(Map<String, String> params);
    
    /**
     * Query configuration information count.
     * The default sql:
     * SELECT count(*) FROM config_info WHERE group_id=? AND tenant_id=? ...
     *
     * @param params The map of appName.
     * @return The sql of querying configuration information count
     */
    String findConfigInfoByGroupAndAdvanceCountRows(Map<String, String> params);
    
    /**
     * Query configuration information.
     * The default sql:
     * SELECT id,data_id,group_id,tenant_id,app_name,content FROM config_info WHERE group_id=? AND tenant_id=? ...
     *
     * @param params The map of appName.
     * @param startRow The start index.
     * @param pageSize The size of page.
     * @return The sql of querying configuration information
     */
    String findConfigInfoByGroupAndAdvanceFetchRows(Map<String, String> params, int startRow, int pageSize);
    
    /**
     * find ConfigInfo by ids.
     * The default sql:
     * SELECT ID,data_id,group_id,tenant_id,app_name,content,md5 FROM config_info WHERE id IN (...)
     *
     * @param idSize the size of ids.
     * @return find ConfigInfo by ids.
     */
    String findConfigInfosByIds(int idSize);
    
    /**
     * Remove configuration; database atomic operation, minimum SQL action, no business encapsulation.
     *
     * @param size The size of ids.
     * @return The sql of removing configuration.
     */
    String removeConfigInfoByIdsAtomic(int size);
    
    /**
     * Update configuration; database atomic operation, minimum SQL action, no business encapsulation.
     * The default sql:
     * UPDATE config_info SET content=?, md5 = ?, src_ip=?,src_user=?,gmt_modified=?, app_name=?,c_desc=?,c_use=?,
     * effect=?,type=?,c_schema=? WHERE data_id=? AND group_id=? AND tenant_id=? AND (md5=? OR md5 IS NULL OR md5='')
     *
     * @return The sql of updating configuration cas.
     */
    String updateConfigInfoAtomicCas();
}
