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

import com.alibaba.nacos.common.utils.CollectionUtils;
import com.alibaba.nacos.common.utils.NamespaceUtil;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.plugin.datasource.constants.TableConstant;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

/**
 * The mapper of config info.
 *
 * @author hyx
 **/

public interface ConfigInfoMapper extends Mapper {

    String DATA_ID = "dataId";
    
    String GROUP = "group";
    
    String APP_NAME = "appName";
    
    String CONTENT = "content";
    
    String TENANT = "tenant";

    /**
     * Get the maxId.
     * The default sql:
     * SELECT max(id) FROM config_info
     *
     * @return the sql of getting the maxId.
     */
    default String findConfigMaxId() {
        return "SELECT MAX(id) FROM config_info";
    }
    
    /**
     * Find all dataId and group.
     * The default sql:
     * SELECT DISTINCT data_id, group_id FROM config_info
     *
     * @return The sql of finding all dataId and group.
     */
    default String findAllDataIdAndGroup() {
        return "SELECT DISTINCT data_id, group_id FROM config_info";
    }
    
    /**
     * Query the count of config_info by tenantId and appName.
     * The default sql:
     * SELECT count(*) FROM config_info WHERE tenant_id LIKE ? AND app_name=?
     *
     * @return The sql of querying the count of config_info.
     */
    default String findConfigInfoByAppCountRows() {
        return "SELECT count(*) FROM config_info WHERE tenant_id LIKE ? AND app_name = ?";
    }
    
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
    default String configInfoLikeTenantCount() {
        return "SELECT count(*) FROM config_info WHERE tenant_id LIKE ?";
    }
    
    /**
     * Get tenant id list  by page.
     * The default sql:
     * SELECT tenant_id FROM config_info WHERE tenant_id != '' GROUP BY tenant_id LIMIT startRow, pageSize
     *
     * @param startRow The start index.
     * @param pageSize The size of page.
     * @return The sql of getting tenant id list  by page.
     */
    String getTenantIdList(int startRow, int pageSize);
    
    /**
     * Get group id list  by page.
     * The default sql:
     * SELECT group_id FROM config_info WHERE tenant_id ='{defaultNamespaceId}' GROUP BY group_id LIMIT startRow, pageSize
     *
     * @param startRow The start index.
     * @param pageSize The size of page.
     * @return The sql of getting group id list  by page.
     */
    String getGroupIdList(int startRow, int pageSize);
    
    /**
     * Query all configuration information by page.
     * The default sql:
     * SELECT data_id,group_id,app_name  FROM (
     * SELECT id FROM config_info WHERE tenant_id LIKE ? ORDER BY id LIMIT startRow, pageSize ) g,
     * config_info t WHERE g.id = t.id "
     *
     * @param startRow The start index.
     * @param pageSize The size of page.
     * @return The sql of querying all configuration information.
     */
    String findAllConfigKey(int startRow, int pageSize);
    
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
     * Query all config info.
     * The default sql:
     * SELECT id,data_id,group_id,tenant_id,app_name,content,md5,gmt_modified,type,encrypted_data_key
     * FROM config_info WHERE id > ? ORDER BY id ASC LIMIT startRow,pageSize
     *
     * @param startRow The start index.
     * @param pageSize The size of page.
     * @return The sql of querying all config info.
     */
    String findAllConfigInfoFragment(int startRow, int pageSize);
    
    /**
     * Query change config.
     * The default sql:
     * SELECT data_id, group_id, tenant_id, app_name, content, gmt_modified,encrypted_data_key
     * FROM config_info WHERE gmt_modified >=? AND gmt_modified <= ?
     *
     * @return The sql of querying change config.
     */
    default String findChangeConfig() {
        return "SELECT data_id, group_id, tenant_id, app_name, content, gmt_modified, encrypted_data_key FROM config_info WHERE "
                + "gmt_modified >= ? AND gmt_modified <= ?";
    }
    
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
    default String findChangeConfigCountRows(Map<String, String> params, final Timestamp startTime,
            final Timestamp endTime) {
        final String tenant = params.get(TENANT);
        final String dataId = params.get(DATA_ID);
        final String group = params.get(GROUP);
        final String appName = params.get(APP_NAME);
        final String sqlCountRows = "SELECT count(*) FROM config_info WHERE ";
        String where = " 1=1 ";
        
        if (!StringUtils.isBlank(dataId)) {
            where += " AND data_id LIKE ? ";
        }
        if (!StringUtils.isBlank(group)) {
            where += " AND group_id LIKE ? ";
        }
        
        if (!StringUtils.isBlank(tenant)) {
            where += " AND tenant_id = ? ";
        }
        
        if (!StringUtils.isBlank(appName)) {
            where += " AND app_name = ? ";
        }
        if (startTime != null) {
            where += " AND gmt_modified >=? ";
        }
        if (endTime != null) {
            where += " AND gmt_modified <=? ";
        }
        return sqlCountRows + where;
    }
    
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
     * @param startRow The start index.
     * @param pageSize The size of page.
     * @return The sql of listing group key md5 by page.
     */
    String listGroupKeyMd5ByPageFetchRows(int startRow, int pageSize);
    
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
    default String findAllConfigInfo4Export(List<Long> ids, Map<String, String> params) {
        String sql = "SELECT id,data_id,group_id,tenant_id,app_name,content,type,md5,gmt_create,gmt_modified,"
                + "src_user,src_ip,c_desc,c_use,effect,c_schema,encrypted_data_key FROM config_info";
        StringBuilder where = new StringBuilder(" WHERE ");
        if (!CollectionUtils.isEmpty(ids)) {
            where.append(" id IN (");
            for (int i = 0; i < ids.size(); i++) {
                if (i != 0) {
                    where.append(", ");
                }
                where.append('?');
            }
            where.append(") ");
        } else {
            where.append(" tenant_id = ? ");
            if (StringUtils.isNotBlank(params.get(DATA_ID))) {
                where.append(" AND data_id LIKE ? ");
            }
            if (StringUtils.isNotBlank(params.get(GROUP))) {
                where.append(" AND group_id= ? ");
            }
            if (StringUtils.isNotBlank(params.get(APP_NAME))) {
                where.append(" AND app_name= ? ");
            }
        }
        return sql + where;
    }
    
    /**
     * Get the count of config information.
     * The default sql:
     * SELECT count(*) FROM config_info WHERE ...
     *
     * @param params The map of params, the key is the parameter name(dataId, groupId, tenant_id, content),
     *               the value is the arbitrary object.
     * @return The sql of getting the count of config information.
     */
    default String findConfigInfoBaseLikeCountRows(Map<String, String> params) {
        final String sqlCountRows = "SELECT count(*) FROM config_info WHERE ";
        String where = " 1=1 AND tenant_id='"
                + NamespaceUtil.getNamespaceDefaultId()
                + "' ";

        if (!StringUtils.isBlank(params.get(DATA_ID))) {
            where += " AND data_id LIKE ? ";
        }
        if (!StringUtils.isBlank(params.get(GROUP))) {
            where += " AND group_id LIKE ? ";
        }
        if (!StringUtils.isBlank(params.get(CONTENT))) {
            where += " AND content LIKE ? ";
        }
        return sqlCountRows + where;
    }
    
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
     * find the count of config info.
     * The default sql:
     * SELECT count(*) FROM config_info ...
     *
     * @param params The mpa of dataId, groupId and appName.
     * @return The count of config info.
     */
    default String findConfigInfo4PageCountRows(Map<String, String> params) {
        final String appName = params.get(APP_NAME);
        final String dataId = params.get(DATA_ID);
        final String group = params.get(GROUP);
        final String content = params.get(CONTENT);
        final String sqlCount = "SELECT count(*) FROM config_info";
        StringBuilder where = new StringBuilder(" WHERE ");
        where.append(" tenant_id=? ");
        if (StringUtils.isNotBlank(dataId)) {
            where.append(" AND data_id=? ");
        }
        if (StringUtils.isNotBlank(group)) {
            where.append(" AND group_id=? ");
        }
        if (StringUtils.isNotBlank(appName)) {
            where.append(" AND app_name=? ");
        }
        if (!StringUtils.isBlank(content)) {
            where.append(" AND content LIKE ? ");
        }
        return sqlCount + where;
    }
    
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
    default String findConfigInfoLike4PageCountRows(Map<String, String> params) {
        final String appName = params.get("appName");
        final String content = params.get("content");
        final String dataId = params.get("dataId");
        final String group = params.get(GROUP);
        final String sqlCountRows = "SELECT count(*) FROM config_info";
        StringBuilder where = new StringBuilder(" WHERE ");
        where.append(" tenant_id LIKE ? ");
        if (!StringUtils.isBlank(dataId)) {
            where.append(" AND data_id LIKE ? ");
        }
        if (!StringUtils.isBlank(group)) {
            where.append(" AND group_id LIKE ? ");
        }
        if (!StringUtils.isBlank(appName)) {
            where.append(" AND app_name = ? ");
        }
        if (!StringUtils.isBlank(content)) {
            where.append(" AND content LIKE ? ");
        }
        return sqlCountRows + where;
    }
    
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
     * find ConfigInfo by ids.
     * The default sql:
     * SELECT ID,data_id,group_id,tenant_id,app_name,content,md5 FROM config_info WHERE id IN (...)
     *
     * @param idSize the size of ids.
     * @return find ConfigInfo by ids.
     */
    default String findConfigInfosByIds(int idSize) {
        StringBuilder sql = new StringBuilder(
                "SELECT id,data_id,group_id,tenant_id,app_name,content,md5 FROM config_info WHERE ");
        sql.append("id IN (");
        for (int i = 0; i < idSize; i++) {
            if (i != 0) {
                sql.append(", ");
            }
            sql.append('?');
        }
        sql.append(") ");
        return sql.toString();
    }
    
    /**
     * Remove configuration; database atomic operation, minimum SQL action, no business encapsulation.
     *
     * @param size The size of ids.
     * @return The sql of removing configuration.
     */
    default String removeConfigInfoByIdsAtomic(int size) {
        StringBuilder sql = new StringBuilder("DELETE FROM config_info WHERE ");
        sql.append("id IN (");
        for (int i = 0; i < size; i++) {
            if (i != 0) {
                sql.append(", ");
            }
            sql.append('?');
        }
        sql.append(") ");
        return sql.toString();
    }
    
    /**
     * Update configuration; database atomic operation, minimum SQL action, no business encapsulation.
     * The default sql:
     * UPDATE config_info SET content=?, md5 = ?, src_ip=?,src_user=?,gmt_modified=?, app_name=?,c_desc=?,c_use=?,
     * effect=?,type=?,c_schema=? WHERE data_id=? AND group_id=? AND tenant_id=? AND (md5=? OR md5 IS NULL OR md5='')
     *
     * @return The sql of updating configuration cas.
     */
    default String updateConfigInfoAtomicCas() {
        return "UPDATE config_info SET "
                + "content=?, md5 = ?, src_ip=?,src_user=?,gmt_modified=?, app_name=?,c_desc=?,c_use=?,effect=?,type=?,c_schema=? "
                + "WHERE data_id=? AND group_id=? AND tenant_id=? AND (md5=? OR md5 IS NULL OR md5='')";
    }
    
    /**
     * 获取返回表名.
     *
     * @return 表名
     */
    default String getTableName() {
        return TableConstant.CONFIG_INFO;
    }
}
