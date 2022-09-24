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

package com.alibaba.nacos.plugin.datasource.impl.derby;

import com.alibaba.nacos.common.utils.CollectionUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.plugin.datasource.constants.TableConstant;
import com.alibaba.nacos.plugin.datasource.mapper.ConfigInfoMapper;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

/**
 * The derby implementation of ConfigInfoMapper.
 *
 * @author hyx
 **/

public class ConfigInfoMapperByDerby implements ConfigInfoMapper {
    
    private static final String DATA_ID = "data_id";
    
    private static final String GROUP = "group_id";
    
    private static final String APP_NAME = "app_name";
    
    private static final String CONTENT = "content";
    
    @Override
    public String updateMd5() {
        return "UPDATE config_info SET md5 = ? WHERE data_id = ? AND group_id = ? AND tenant_id = ? AND gmt_modified = ?";
    }
    
    @Override
    public String findConfigMaxId() {
        return "SELECT max(id) FROM config_info";
    }
    
    @Override
    public String findAllDataIdAndGroup() {
        return "SELECT DISTINCT data_id, group_id FROM config_info";
    }
    
    @Override
    public String findConfigInfoApp() {
        return "SELECT id,data_id,group_id,tenant_id,app_name,content FROM config_info WHERE data_id = ? "
                + "AND group_id = ? AND tenant_id = ? AND app_name = ?";
    }
    
    @Override
    public String findConfigInfoBase() {
        return "SELECT id,data_id,group_id,content FROM config_info WHERE data_id = ? AND group_id = ? AND tenant_id = ?";
    }
    
    @Override
    public String findConfigInfoById() {
        return "SELECT id,data_id,group_id,tenant_id,app_name,content FROM config_info WHERE id = ?";
    }
    
    @Override
    public String findConfigInfoByDataIdFetchRows() {
        return "SELECT id,data_id,group_id,tenant_id,app_name,content FROM config_info WHERE data_id = ? AND "
                + "tenant_id = ?";
    }
    
    @Override
    public String findConfigInfoByDataIdAndAppCountRows() {
        return "SELECT count(*) FROM config_info WHERE data_id = ? AND tenant_id = ? AND app_name = ?";
    }
    
    @Override
    public String findConfigInfoByDataIdAndAppFetchRows() {
        return "SELECT id,data_id,group_id,tenant_id,app_name,content FROM config_info WHERE data_id = ? AND "
                + "tenant_id = ? AND app_name = ?";
    }
    
    @Override
    public String count() {
        return "SELECT COUNT(*) FROM config_info";
    }
    
    @Override
    public String findConfigInfoByAppCountRows() {
        return "SELECT count(*) FROM config_info WHERE tenant_id LIKE ? AND app_name = ?";
    }
    
    @Override
    public String findConfigInfoByAppFetchRows() {
        return "SELECT ID,data_id,group_id,tenant_id,app_name,content FROM config_info WHERE tenant_id LIKE ? AND "
                + "app_name = ?";
    }
    
    @Override
    public String configInfoLikeTenantCount() {
        return "SELECT count(*) FROM config_info WHERE tenant_id LIKE ?";
    }
    
    @Override
    public String getTenantIdList() {
        return "SELECT tenant_id FROM config_info WHERE tenant_id != '' GROUP BY tenant_id LIMIT ?,?";
    }
    
    @Override
    public String getGroupIdList() {
        return "SELECT group_id FROM config_info WHERE tenant_id ='' GROUP BY group_id LIMIT ?,?";
    }
    
    @Override
    public String findAllConfigKey() {
        return " SELECT data_id,group_id,app_name FROM "
                + " ( SELECT id FROM config_info WHERE tenant_id LIKE ? ORDER BY id LIMIT ?, ? ) "
                + "g, config_info t  WHERE g.id = t.id ";
    }
    
    @Override
    public String findAllConfigInfoBaseFetchRows() {
        return "SELECT t.id,data_id,group_id,content,md5 "
                + " FROM ( SELECT id FROM config_info ORDER BY id LIMIT ?,? )  "
                + " g, config_info t WHERE g.id = t.id ";
    }
    
    @Override
    public String findAllConfigInfoForDumpAllFetchRows() {
        return " SELECT t.id,data_id,group_id,tenant_id,app_name,content,type,md5,gmt_modified "
                + " FROM ( SELECT id FROM config_info ORDER BY id LIMIT ?,? )"
                + " g, config_info t  WHERE g.id = t.id ";
    }
    
    @Override
    public String findAllConfigInfoFragment() {
        return "SELECT id,data_id,group_id,tenant_id,app_name,content,md5,gmt_modified,type FROM config_info WHERE id > ? "
                + "ORDER BY id ASC LIMIT ?,?";
    }
    
    @Override
    public String findChangeConfig() {
        return "SELECT data_id, group_id, tenant_id, app_name, content, gmt_modified FROM config_info WHERE "
                + "gmt_modified > = ? AND gmt_modified <= ?";
    }
    
    @Override
    public String findChangeConfigCountRows(Map<String, String> params, final Timestamp startTime,
            final Timestamp endTime) {
        return "SELECT data_id, group_id, tenant_id, app_name, content, gmt_modified FROM config_info WHERE "
                + "gmt_modified > = ? AND gmt_modified <= ?";
    }
    
    @Override
    public String findChangeConfigFetchRows(Map<String, String> params, final Timestamp startTime,
            final Timestamp endTime) {
        return "SELECT data_id, group_id, tenant_id, app_name, content, gmt_modified FROM config_info WHERE "
                + "gmt_modified > = ? AND gmt_modified <= ?";
    }
    
    @Override
    public String addConfigInfoAtomic() {
        return "INSERT INTO config_info(id, data_id, group_id, tenant_id, app_name, content, md5, src_ip, src_user, gmt_create,"
                + "gmt_modified, c_desc, c_use, effect, type, c_schema,encrypted_data_key) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
    }
    
    @Override
    public String removeConfigInfoAtomic() {
        return "DELETE FROM config_info WHERE data_id = ? AND group_id = ? AND tenant_id = ?";
    }
    
    @Override
    public String updateConfigInfoAtomic() {
        return "UPDATE config_info SET content = ?, md5 = ?, src_ip = ?,src_user = ?,gmt_modified = ?,app_name = ?,"
                + "c_desc = ?,c_use = ?,effect = ?,type = ?,c_schema = ?,encrypted_data_key = ? WHERE data_id = ? AND group_id = ? AND tenant_id = ?";
    }
    
    @Override
    public String findConfigAdvanceInfo() {
        return "SELECT gmt_create,gmt_modified,src_user,src_ip,c_desc,c_use,effect,type,c_schema FROM config_info "
                + "WHERE data_id = ? AND group_id = ? AND tenant_id = ?";
    }
    
    @Override
    public String findConfigAllInfo() {
        return "SELECT id,data_id,group_id,tenant_id,app_name,content,md5,gmt_create,"
                + "gmt_modified,src_user,src_ip,c_desc,c_use,effect,type,c_schema,encrypted_data_key FROM config_info "
                + "WHERE data_id = ? AND group_id = ? AND tenant_id = ?";
    }
    
    @Override
    public String listGroupKeyMd5ByPageFetchRows() {
        return " SELECT t.id,data_id,group_id,tenant_id,app_name,type,md5,gmt_modified "
                + "FROM ( SELECT id FROM config_info ORDER BY id LIMIT ?,?  ) g, config_info t WHERE g.id = t.id";
    }
    
    @Override
    public String queryConfigInfo() {
        return "SELECT id,data_id,group_id,tenant_id,app_name,content,type,gmt_modified,md5 "
                + "FROM config_info WHERE data_id = ? AND group_id = ? AND tenant_id = ?";
    }
    
    @Override
    public String queryConfigInfoByNamespace() {
        return "SELECT data_id,group_id,tenant_id,app_name,type FROM config_info WHERE tenant_id = ?";
    }
    
    @Override
    public String findAllConfigInfo4Export(List<Long> ids, Map<String, String> params) {
        String sql = "SELECT id,data_id,group_id,tenant_id,app_name,content,type,md5,gmt_create,gmt_modified,src_user,"
                + "src_ip,c_desc,c_use,effect,c_schema,encrypted_data_key FROM config_info";
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
            if (!StringUtils.isEmpty(params.get(DATA_ID))) {
                where.append(" AND data_id LIKE ? ");
            }
            if (StringUtils.isNotEmpty(params.get(GROUP))) {
                where.append(" AND group_id = ? ");
            }
            if (StringUtils.isNotEmpty(params.get(APP_NAME))) {
                where.append(" AND app_name = ? ");
            }
        }
        return sql + where;
    }
    
    @Override
    public String findConfigInfoByBatch(int paramSize) {
        String sqlStart = "SELECT data_id, group_id, tenant_id, app_name, content FROM config_info"
                + " WHERE group_id = ? AND tenant_id = ? AND data_id IN (";
        String sqlEnd = ")";
        StringBuilder subQuerySql = new StringBuilder();
        
        for (int i = 0; i < paramSize; i++) {
            subQuerySql.append('?');
            if (i != paramSize - 1) {
                subQuerySql.append(',');
            }
        }
    
        return sqlStart + subQuerySql.toString() + sqlEnd;
    }
    
    @Override
    public String findConfigInfoLikeCountRows(Map<String, String> params) {
        final String sqlCountRows = "SELECT count(*) FROM config_info WHERE ";
        String where = " 1=1 ";
        if (!StringUtils.isEmpty(params.get(DATA_ID))) {
            where += " AND data_id LIKE ? ";
        }
        if (!StringUtils.isBlank(params.get(GROUP))) {
            where += " AND group_id LIKE ? ";
        }
        where += " AND tenant_id LIKE ? ";
        if (!StringUtils.isBlank(params.get(APP_NAME))) {
            where += " AND app_name = ? ";
        }
        if (!StringUtils.isBlank(params.get(CONTENT))) {
            where += " AND content LIKE ? ";
        }
        return sqlCountRows + where;
    }
    
    @Override
    public String findConfigInfoLikeFetchRows(Map<String, String> params) {
        final String sqlFetchRows = "SELECT id,data_id,group_id,tenant_id,app_name,content FROM config_info WHERE ";
        String where = " 1=1 ";
        if (!StringUtils.isEmpty(params.get(DATA_ID))) {
            where += " AND data_id LIKE ? ";
        }
        if (!StringUtils.isBlank(params.get(GROUP))) {
            where += " AND group_id LIKE ? ";
        }
        where += " AND tenant_id LIKE ? ";
        if (!StringUtils.isBlank(params.get(APP_NAME))) {
            where += " AND app_name = ? ";
        }
        if (!StringUtils.isBlank(params.get(CONTENT))) {
            where += " AND content LIKE ? ";
        }
        return sqlFetchRows + where;
    }
    
    @Override
    public String findConfigInfoBaseLikeCountRows(Map<String, String> params) {
        final String sqlCountRows = "SELECT count(*) FROM config_info WHERE ";
        String where = " 1=1 AND tenant_id='' ";
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
    
    @Override
    public String findConfigInfoBaseLikeFetchRows(Map<String, String> params) {
        final String sqlFetchRows = "SELECT id,data_id,group_id,tenant_id,content FROM config_info WHERE ";
        String where = " 1=1 AND tenant_id='' ";
        if (!StringUtils.isBlank(params.get(DATA_ID))) {
            where += " AND data_id LIKE ? ";
        }
        if (!StringUtils.isBlank(params.get(GROUP))) {
            where += " AND group_id LIKE ? ";
        }
        if (!StringUtils.isBlank(params.get(CONTENT))) {
            where += " AND content LIKE ? ";
        }
        return sqlFetchRows + where;
    }
    
    @Override
    public String findConfigInfoByDataIdCountRows() {
        return "SELECT count(*) FROM config_info WHERE data_id = ? AND tenant_id = ?";
    }
    
    @Override
    public String findConfigInfoByDataIdAndAdvanceCountRows(Map<String, String> params) {
        final String appName = params.get("appName");
        StringBuilder sqlCount = new StringBuilder("SELECT count(*) FROM config_info WHERE data_id=? AND tenant_id=? ");
        if (StringUtils.isNotBlank(appName)) {
            sqlCount.append(" AND app_name=? ");
        }
        
        return sqlCount.toString();
    }
    
    @Override
    public String findConfigInfoByDataIdAndAdvanceFetchRows(Map<String, String> params) {
        final String appName = params.get("appName");
        StringBuilder sql = new StringBuilder(
                "SELECT id,data_id,group_id,tenant_id,app_name,content FROM config_info WHERE data_id=? AND tenant_id=? ");
        if (StringUtils.isNotBlank(appName)) {
            sql.append(" AND app_name=? ");
        }
        
        return sql.toString();
    }
    
    @Override
    public String findConfigInfo4PageCountRows(Map<String, String> params) {
        final String appName = params.get(APP_NAME);
        final String dataId = params.get(DATA_ID);
        final String group = params.get(GROUP);
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
        return sqlCount + where;
    }
    
    @Override
    public String findConfigInfo4PageFetchRows(Map<String, String> params) {
        final String appName = params.get(APP_NAME);
        final String dataId = params.get(DATA_ID);
        final String group = params.get(GROUP);
        final String sql = "SELECT id,data_id,group_id,tenant_id,app_name,content,type FROM config_info";
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
        return sql + where;
    }
    
    @Override
    public String findConfigInfoBaseByDataIdCountRows() {
        return "SELECT count(*) FROM config_info WHERE data_id=? AND tenant_id=?";
    }
    
    @Override
    public String findConfigInfoBaseByDataIdFetchRows() {
        return "SELECT id,data_id,group_id,content FROM config_info WHERE data_id=? AND tenant_id=?";
    }
    
    @Override
    public String findConfigInfoByGroupCountRows() {
        return "SELECT count(*) FROM config_info WHERE group_id=? AND tenant_id=?";
    }
    
    @Override
    public String findConfigInfoByGroupFetchRows() {
        return "SELECT id,data_id,group_id,tenant_id,app_name,content FROM config_info WHERE group_id=? AND "
                + "tenant_id=?";
    }
    
    @Override
    public String findConfigInfoByGroupAndAppCountRows() {
        return "SELECT count(*) FROM config_info WHERE group_id=? AND tenant_id=? AND app_name =?";
    }
    
    @Override
    public String findConfigInfoByGroupAndAppFetchRows() {
        return "SELECT id,data_id,group_id,tenant_id,app_name,content FROM config_info WHERE group_id=? AND "
                + "tenant_id=? AND app_name =?";
    }
    
    @Override
    public String findConfigInfoByAdvanceCountRows(Map<String, String> params) {
        final String appName = params.get("appName");
        StringBuilder sqlCount = new StringBuilder("SELECT count(*) FROM config_info WHERE tenant_id LIKE ? ");
        if (StringUtils.isNotBlank(appName)) {
            sqlCount.append(" AND app_name=? ");
        }
        return sqlCount.toString();
    }
    
    @Override
    public String findConfigInfoByAdvanceFetchRows(Map<String, String> params) {
        final String appName = params.get("appName");
        StringBuilder sql = new StringBuilder(
                "SELECT id,data_id,group_id,tenant_id,app_name,content FROM config_info where tenant_id LIKE ? ");
        if (StringUtils.isNotBlank(appName)) {
            sql.append(" AND app_name=? ");
        }
        return sql.toString();
    }
    
    @Override
    public String findConfigInfoBaseByGroupCountRows() {
        return "SELECT count(*) FROM config_info WHERE group_id=? AND tenant_id=?";
    }
    
    @Override
    public String findConfigInfoBaseByGroupFetchRows() {
        return "SELECT id,data_id,group_id,content FROM config_info WHERE group_id=? AND tenant_id=?";
    }
    
    @Override
    public String findConfigInfoLike4PageCountRows(Map<String, String> params) {
        final String appName = params.get("appName");
        final String content = params.get("content");
        final String dataId = params.get("dataId");
        final String group = params.get("groupId");
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
    
    @Override
    public String findConfigInfoLike4PageFetchRows(Map<String, String> params) {
        final String appName = params.get("appName");
        final String content = params.get("content");
        final String dataId = params.get(DATA_ID);
        final String group = params.get(GROUP);
        final String sqlFetchRows = "SELECT id,data_id,group_id,tenant_id,app_name,content,encrypted_data_key FROM config_info";
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
        return sqlFetchRows + where;
    }
    
    @Override
    public String findAllConfigInfoFetchRows() {
        return " SELECT t.id,data_id,group_id,tenant_id,app_name,content,md5 "
                + " FROM ( SELECT id FROM config_info  WHERE tenant_id LIKE ? ORDER BY id LIMIT ?,? )"
                + " g, config_info t  WHERE g.id = t.id ";
    }
    
    @Override
    public String findConfigInfoAdvanceInfo(Map<String, String> params) {
        final String appName = params.get("appName");
        
        StringBuilder sql = new StringBuilder(
                "SELECT id,data_id,group_id,tenant_id,app_name,content FROM config_info WHERE data_id=? AND group_id=? AND tenant_id=? ");
        if (StringUtils.isNotBlank(appName)) {
            sql.append(" AND app_name=? ");
        }
        
        return sql.toString();
    }
    
    @Override
    public String findConfigInfoByGroupAndAdvanceCountRows(Map<String, String> params) {
        final String appName = params.get("appName");
        StringBuilder sqlCount = new StringBuilder(
                "SELECT count(*) FROM config_info WHERE group_id=? AND tenant_id=? ");
        if (StringUtils.isNotBlank(appName)) {
            sqlCount.append(" AND app_name=? ");
        }
        return sqlCount.toString();
    }
    
    @Override
    public String findConfigInfoByGroupAndAdvanceFetchRows(Map<String, String> params) {
        final String appName = params.get("appName");
        StringBuilder sql = new StringBuilder(
                "SELECT id,data_id,group_id,tenant_id,app_name,content FROM config_info WHERE group_id=? AND tenant_id=? ");
        if (StringUtils.isNotBlank(appName)) {
            sql.append(" AND app_name=? ");
        }
        return sql.toString();
    }
    
    @Override
    public String findConfigInfosByIds(int idSize) {
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
    
    @Override
    public String findConfigInfoByDataId2Group2Tenant() {
        return "SELECT id,data_id,group_id,tenant_id,app_name,content FROM config_info WHERE id=?";
    }
    
    @Override
    public String removeConfigInfoByIdsAtomic(int size) {
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
    
    @Override
    public String getTableName() {
        return TableConstant.CONFIG_INFO;
    }
}
