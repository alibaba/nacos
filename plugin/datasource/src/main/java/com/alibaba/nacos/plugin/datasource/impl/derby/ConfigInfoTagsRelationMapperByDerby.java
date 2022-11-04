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

import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.plugin.datasource.constants.DataSourceConstant;
import com.alibaba.nacos.plugin.datasource.constants.TableConstant;
import com.alibaba.nacos.plugin.datasource.mapper.AbstractMapper;
import com.alibaba.nacos.plugin.datasource.mapper.ConfigTagsRelationMapper;

import java.util.Map;

/**
 * The derby implementation of ConfigTagsRelationMapper.
 *
 * @author hyx
 **/

public class ConfigInfoTagsRelationMapperByDerby extends AbstractMapper implements ConfigTagsRelationMapper {
    
    @Override
    public String findConfigInfoAdvanceInfo(Map<String, String> params, int tagSize) {
        final String appName = params.get("appName");
        StringBuilder sql = new StringBuilder(
                "SELECT a.id,a.data_id,a.group_id,a.tenant_id,a.app_name,a.content FROM config_info  a LEFT JOIN "
                        + "config_tags_relation b ON a.id=b.id WHERE a.data_id=? AND a.group_id=? AND a.tenant_id=? ");
        sql.append(" AND b.tag_name IN (");
        for (int i = 0; i < tagSize; i++) {
            if (i != 0) {
                sql.append(", ");
            }
            sql.append('?');
        }
        sql.append(") ");
        if (StringUtils.isNotBlank(appName)) {
            sql.append(" AND a.app_name=? ");
        }
        return sql.toString();
    }
    
    @Override
    public String findConfigInfoByDataIdAndAdvanceCountRows(Map<String, String> params, int tagSize) {
        final String appName = params.get("appName");
        StringBuilder sqlCount = new StringBuilder(
                "SELECT count(*) FROM config_info  a LEFT JOIN config_tags_relation b ON a.id=b.id WHERE a.data_id=? AND a.tenant_id=? ");
        
        sqlCount.append(" AND b.tag_name IN (");
        for (int i = 0; i < tagSize; i++) {
            if (i != 0) {
                sqlCount.append(", ");
            }
            sqlCount.append('?');
        }
        sqlCount.append(") ");
        
        if (StringUtils.isNotBlank(appName)) {
            sqlCount.append(" AND a.app_name=? ");
        }
        return sqlCount.toString();
    }
    
    @Override
    public String findConfigInfoByDataIdAndAdvanceFetchRows(Map<String, String> params, int tagSize, int startRow, int pageSize) {
        final String appName = params.get("appName");
        StringBuilder sql = new StringBuilder(
                "SELECT a.id,a.data_id,a.group_id,a.tenant_id,a.app_name,a.content FROM config_info  a LEFT JOIN "
                        + "config_tags_relation b ON a.id=b.id WHERE a.data_id=? AND a.tenant_id=? ");
        
        sql.append(" AND b.tag_name IN (");
        for (int i = 0; i < tagSize; i++) {
            if (i != 0) {
                sql.append(", ");
            }
            sql.append('?');
        }
        sql.append(") ");
        
        if (StringUtils.isNotBlank(appName)) {
            sql.append(" AND a.app_name=? ");
        }
        return sql.toString() + " OFFSET " + startRow + " ROWS FETCH NEXT " + pageSize + " ROWS ONLY";
    }
    
    @Override
    public String findConfigInfo4PageCountRows(final Map<String, String> params, int tagSize) {
        final String appName = params.get("appName");
        final String dataId = params.get("dataId");
        final String group = params.get("group");
        StringBuilder where = new StringBuilder(" WHERE ");
        final String sqlCount = "SELECT count(*) FROM config_info  a LEFT JOIN config_tags_relation b ON a.id=b.id";
        
        where.append(" a.tenant_id=? ");
        
        if (StringUtils.isNotBlank(dataId)) {
            where.append(" AND a.data_id=? ");
        }
        if (StringUtils.isNotBlank(group)) {
            where.append(" AND a.group_id=? ");
        }
        if (StringUtils.isNotBlank(appName)) {
            where.append(" AND a.app_name=? ");
        }
        
        where.append(" AND b.tag_name IN (");
        for (int i = 0; i < tagSize; i++) {
            if (i != 0) {
                where.append(", ");
            }
            where.append('?');
        }
        where.append(") ");
        return sqlCount + where;
    }
    
    @Override
    public String findConfigInfo4PageFetchRows(final Map<String, String> params, int tagSize, int startRow, int pageSize) {
        final String appName = params.get("appName");
        final String dataId = params.get("dataId");
        final String group = params.get("group");
        StringBuilder where = new StringBuilder(" WHERE ");
        final String sql = "SELECT a.id,a.data_id,a.group_id,a.tenant_id,a.app_name,a.content FROM config_info  a LEFT JOIN "
                + "config_tags_relation b ON a.id=b.id";
        
        where.append(" a.tenant_id=? ");
        
        if (StringUtils.isNotBlank(dataId)) {
            where.append(" AND a.data_id=? ");
        }
        if (StringUtils.isNotBlank(group)) {
            where.append(" AND a.group_id=? ");
        }
        if (StringUtils.isNotBlank(appName)) {
            where.append(" AND a.app_name=? ");
        }
        
        where.append(" AND b.tag_name IN (");
        for (int i = 0; i < tagSize; i++) {
            if (i != 0) {
                where.append(", ");
            }
            where.append('?');
        }
        where.append(") ");
        return sql + where + " OFFSET " + startRow + " ROWS FETCH NEXT " + pageSize + " ROWS ONLY";
    }
    
    @Override
    public String findConfigInfoByGroupAndAdvanceCountRows(final Map<String, String> params, int tagSize) {
        final String appName = params.get("appName");
        StringBuilder sqlCount = new StringBuilder(
                "SELECT count(*) FROM config_info  a LEFT JOIN config_tags_relation b ON a.id=b.id WHERE a.group_id=?  AND a.tenant_id=? ");
        sqlCount.append(" AND b.tag_name IN (");
        for (int i = 0; i < tagSize; i++) {
            if (i != 0) {
                sqlCount.append(", ");
            }
            sqlCount.append('?');
        }
        sqlCount.append(") ");
        if (StringUtils.isNotBlank(appName)) {
            sqlCount.append(" AND a.app_name=? ");
        }
        return sqlCount.toString();
    }
    
    @Override
    public String findConfigInfoByGroupAndAdvanceFetchRows(final Map<String, String> params, int tagSize, int startRow, int pageSize) {
        final String appName = params.get("appName");
        StringBuilder sql = new StringBuilder(
                "SELECT a.id,a.data_id,a.group_id,a.tenant_id,a.app_name,a.content FROM config_info  a LEFT JOIN "
                        + "config_tags_relation b ON a.id=b.id WHERE a.group_id=? AND a.tenant_id=? ");
        
        sql.append(" AND b.tag_name IN (");
        for (int i = 0; i < tagSize; i++) {
            if (i != 0) {
                sql.append(", ");
            }
            sql.append('?');
        }
        sql.append(") ");
        
        if (StringUtils.isNotBlank(appName)) {
            sql.append(" AND a.app_name=? ");
        }
        return sql.toString() + " OFFSET " + startRow + " ROWS FETCH NEXT " + pageSize + " ROWS ONLY";
    }
    
    @Override
    public String findConfigInfoLike4PageCountRows(final Map<String, String> params, int tagSize) {
        final String appName = params.get("appName");
        final String content = params.get("content");
        final String dataId = params.get("dataId");
        final String group = params.get("group");
        StringBuilder where = new StringBuilder(" WHERE ");
        final String sqlCountRows = "SELECT count(*) FROM config_info  a LEFT JOIN config_tags_relation b ON a.id=b.id ";
        where.append(" a.tenant_id LIKE ? ");
        if (!StringUtils.isBlank(dataId)) {
            where.append(" AND a.data_id LIKE ? ");
        }
        if (!StringUtils.isBlank(group)) {
            where.append(" AND a.group_id LIKE ? ");
        }
        if (!StringUtils.isBlank(appName)) {
            where.append(" AND a.app_name = ? ");
        }
        if (!StringUtils.isBlank(content)) {
            where.append(" AND a.content LIKE ? ");
        }
        
        where.append(" AND b.tag_name IN (");
        for (int i = 0; i < tagSize; i++) {
            if (i != 0) {
                where.append(", ");
            }
            where.append('?');
        }
        where.append(") ");
        return sqlCountRows + where;
    }
    
    @Override
    public String findConfigInfoLike4PageFetchRows(final Map<String, String> params, int tagSize, int startRow, int pageSize) {
        final String appName = params.get("appName");
        final String content = params.get("content");
        final String dataId = params.get("dataId");
        final String group = params.get("group");
        StringBuilder where = new StringBuilder(" WHERE ");
        final String sqlFetchRows =
                "SELECT a.ID,a.data_id,a.group_id,a.tenant_id,a.app_name,a.content FROM config_info  a LEFT JOIN "
                        + "config_tags_relation b ON a.id=b.id ";
        
        where.append(" a.tenant_id LIKE ? ");
        if (!StringUtils.isBlank(dataId)) {
            where.append(" AND a.data_id LIKE ? ");
        }
        if (!StringUtils.isBlank(group)) {
            where.append(" AND a.group_id LIKE ? ");
        }
        if (!StringUtils.isBlank(appName)) {
            where.append(" AND a.app_name = ? ");
        }
        if (!StringUtils.isBlank(content)) {
            where.append(" AND a.content LIKE ? ");
        }
        
        where.append(" AND b.tag_name IN (");
        for (int i = 0; i < tagSize; i++) {
            if (i != 0) {
                where.append(", ");
            }
            where.append('?');
        }
        where.append(") ");
        return sqlFetchRows + where + " OFFSET " + startRow + " ROWS FETCH NEXT " + pageSize + " ROWS ONLY";
    }
    
    @Override
    public String findConfigInfoByAdvanceCountRows(Map<String, String> params, int tagSize) {
        final String appName = params.get("appName");
        StringBuilder sqlCount = new StringBuilder(
                "SELECT count(*) FROM config_info a LEFT JOIN config_tags_relation b ON a.id=b.id WHERE a.tenant_id=? ");
        
        sqlCount.append(" AND b.tag_name IN (");
        for (int i = 0; i < tagSize; i++) {
            if (i != 0) {
                sqlCount.append(", ");
            }
            sqlCount.append('?');
        }
        sqlCount.append(") ");
        
        if (StringUtils.isNotBlank(appName)) {
            sqlCount.append(" AND a.app_name=? ");
        }
        return sqlCount.toString();
    }
    
    @Override
    public String findConfigInfoByAdvanceFetchRows(Map<String, String> params, int tagSize, int startRow, int pageSize) {
        final String appName = params.get("appName");
        StringBuilder sql = new StringBuilder(
                "SELECT a.id,a.data_id,a.group_id,a.tenant_id,a.app_name,a.content FROM config_info  a LEFT JOIN "
                        + "config_tags_relation b ON a.id=b.id WHERE a.tenant_id=? ");
        sql.append(" AND b.tag_name IN (");
        for (int i = 0; i < tagSize; i++) {
            if (i != 0) {
                sql.append(", ");
            }
            sql.append('?');
        }
        sql.append(") ");
        
        if (StringUtils.isNotBlank(appName)) {
            sql.append(" AND a.app_name=? ");
        }
        return sql.toString() + " OFFSET " + startRow + " ROWS FETCH NEXT " + pageSize + " ROWS ONLY";
    }
    
    @Override
    public String getTableName() {
        return TableConstant.CONFIG_TAGS_RELATION;
    }
    
    @Override
    public String getDataSource() {
        return DataSourceConstant.DERBY;
    }
}
