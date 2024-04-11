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
import com.alibaba.nacos.plugin.datasource.constants.FieldConstant;
import com.alibaba.nacos.plugin.datasource.constants.TableConstant;
import com.alibaba.nacos.plugin.datasource.model.MapperContext;
import com.alibaba.nacos.plugin.datasource.model.MapperResult;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The mapper of config info.
 *
 * @author hyx
 **/

public interface ConfigInfoMapper extends Mapper {
    
    /**
     * Get the maxId. The default sql: SELECT max(id) FROM config_info
     *
     * @param context sql paramMap
     * @return the sql of getting the maxId.
     */
    default MapperResult findConfigMaxId(MapperContext context) {
        return new MapperResult("SELECT MAX(id) FROM config_info", Collections.emptyList());
    }
    
    /**
     * Find all dataId and group. The default sql: SELECT DISTINCT data_id, group_id FROM config_info
     *
     * @param context sql paramMap
     * @return The sql of finding all dataId and group.
     */
    default MapperResult findAllDataIdAndGroup(MapperContext context) {
        return new MapperResult("SELECT DISTINCT data_id, group_id FROM config_info", Collections.emptyList());
    }
    
    /**
     * Query the count of config_info by tenantId and appName. The default sql: SELECT count(*) FROM config_info WHERE
     * tenant_id LIKE ? AND app_name=?
     *
     * @param context sql paramMap
     * @return The sql of querying the count of config_info.
     */
    default MapperResult findConfigInfoByAppCountRows(MapperContext context) {
        Object tenantId = context.getWhereParameter(FieldConstant.TENANT_ID);
        Object appName = context.getWhereParameter(FieldConstant.APP_NAME);
        String sql = "SELECT count(*) FROM config_info WHERE tenant_id LIKE ? AND app_name = ?";
        return new MapperResult(sql, CollectionUtils.list(tenantId, appName));
    }
    
    /**
     * Query configuration information based on group. <br/>The default sql: SELECT
     * id,data_id,group_id,tenant_id,app_name,content FROM config_info WHERE tenant_id LIKE ? AND app_name=?
     *
     * @param context The context of startRow, pageSize
     * @return The sql of querying configration information based on group.
     */
    MapperResult findConfigInfoByAppFetchRows(MapperContext context);
    
    /**
     * Returns the number of configuration items. The default sql: SELECT count(*) FROM config_info WHERE tenant_id LIKE
     * ?
     *
     * @param context sql paramMap
     * @return The sql of querying the number of configuration items.
     */
    default MapperResult configInfoLikeTenantCount(MapperContext context) {
        Object tenantId = context.getWhereParameter(FieldConstant.TENANT_ID);
        String sql = "SELECT count(*) FROM config_info WHERE tenant_id LIKE ?";
        return new MapperResult(sql, Collections.singletonList(tenantId));
    }
    
    /**
     * Get tenant id list  by page. The default sql: SELECT tenant_id FROM config_info WHERE tenant_id != '' GROUP BY
     * tenant_id LIMIT startRow, pageSize
     *
     * @param context The context of startRow, pageSize
     * @return The sql of getting tenant id list  by page.
     */
    MapperResult getTenantIdList(MapperContext context);
    
    /**
     * Get group id list  by page. The default sql: SELECT group_id FROM config_info WHERE tenant_id
     * ='{defaultNamespaceId}' GROUP BY group_id LIMIT startRow, pageSize
     *
     * @param context The context of startRow, pageSize
     * @return The sql of getting group id list  by page.
     */
    MapperResult getGroupIdList(MapperContext context);
    
    /**
     * Query all configuration information by page. The default sql: SELECT data_id,group_id,app_name  FROM ( SELECT id
     * FROM config_info WHERE tenant_id LIKE ? ORDER BY id LIMIT startRow, pageSize ) g, config_info t WHERE g.id = t.id
     * "
     *
     * @param context The context of startRow, pageSize
     * @return The sql of querying all configuration information.
     */
    MapperResult findAllConfigKey(MapperContext context);
    
    /**
     * Query all configuration information by page. The default sql: SELECT t.id,data_id,group_id,content,md5 FROM (
     * SELECT id FROM config_info ORDER BY id LIMIT ?,?) g, config_info t  WHERE g.id = t.id
     *
     * @param context The context of startRow, pageSize
     * @return The sql of querying all configuration information by page.
     */
    MapperResult findAllConfigInfoBaseFetchRows(MapperContext context);
    
    /**
     * Query all config info. The default sql: SELECT
     * id,data_id,group_id,tenant_id,app_name,content,md5,gmt_modified,type,encrypted_data_key FROM config_info WHERE id
     * > ? ORDER BY id ASC LIMIT startRow,pageSize
     *
     * @param context     The context of startRow, pageSize
     * @return The sql of querying all config info.
     */
    MapperResult findAllConfigInfoFragment(MapperContext context);
    
    /**
     * Query change config. <br/>The default sql: SELECT data_id, group_id, tenant_id, app_name, content,
     * gmt_modified,encrypted_data_key FROM config_info WHERE gmt_modified >=? AND gmt_modified <= ?
     *
     * @param context sql paramMap
     * @return The sql of querying change config.
     */
    default MapperResult findChangeConfig(MapperContext context) {
        String sql =
                "SELECT id, data_id, group_id, tenant_id, app_name,md5, gmt_modified, encrypted_data_key FROM config_info WHERE "
                        + "gmt_modified >= ? and id > ? order by id  limit ? ";
        return new MapperResult(sql, CollectionUtils.list(context.getWhereParameter(FieldConstant.START_TIME),
                context.getWhereParameter(FieldConstant.LAST_MAX_ID),
                context.getWhereParameter(FieldConstant.PAGE_SIZE)));
    }
    
    /**
     * Get the count of config information. The default sql: SELECT count(*) FROM config_info WHERE ...
     *
     * @param context The map of params, the key is the parameter name(dataId, groupId, tenantId, appName, startTime,
     *                endTime, content), the value is the key's value.
     * @return The sql of getting the count of config information.
     */
    default MapperResult findChangeConfigCountRows(MapperContext context) {
        final String tenant = (String) context.getWhereParameter(FieldConstant.TENANT);
        final String dataId = (String) context.getWhereParameter(FieldConstant.DATA_ID);
        final String group = (String) context.getWhereParameter(FieldConstant.GROUP_ID);
        final String appName = (String) context.getWhereParameter(FieldConstant.APP_NAME);
        
        final Timestamp startTime = (Timestamp) context.getWhereParameter(FieldConstant.START_TIME);
        final Timestamp endTime = (Timestamp) context.getWhereParameter(FieldConstant.END_TIME);
        
        List<Object> paramList = new ArrayList<>();
        final String sqlCountRows = "SELECT count(*) FROM config_info WHERE ";
        String where = " 1=1 ";
        
        if (!StringUtils.isBlank(dataId)) {
            where += " AND data_id LIKE ? ";
            paramList.add(dataId);
        }
        if (!StringUtils.isBlank(group)) {
            where += " AND group_id LIKE ? ";
            paramList.add(group);
        }
        
        if (!StringUtils.isBlank(tenant)) {
            where += " AND tenant_id = ? ";
            paramList.add(tenant);
        }
        
        if (!StringUtils.isBlank(appName)) {
            where += " AND app_name = ? ";
            paramList.add(appName);
        }
        if (startTime != null) {
            where += " AND gmt_modified >=? ";
            paramList.add(startTime);
        }
        if (endTime != null) {
            where += " AND gmt_modified <=? ";
            paramList.add(endTime);
        }
        return new MapperResult(sqlCountRows + where, paramList);
    }
    
    /**
     * According to the time period and configuration conditions to query the eligible configuration. The default sql:
     * SELECT id,data_id,group_id,tenant_id,app_name,content,type,md5,gmt_modified FROM config_info WHERE ...
     *
     * @param context The map of params, the key is the parameter name(dataId, groupId, tenantId, appName, startTime,
     *                endTime, content, startTime, endTime), the value is the key's value.
     * @return The sql of getting config information according to the time period.
     */
    MapperResult findChangeConfigFetchRows(MapperContext context);
    
    /**
     * list group key md5 by page. The default sql: SELECT
     * t.id,data_id,group_id,tenant_id,app_name,md5,type,gmt_modified,encrypted_data_key FROM ( SELECT id FROM
     * config_info ORDER BY id LIMIT ?,?  ) g, config_info t WHERE g.id = t.id
     *
     * @param context The context of startRow, pageSize
     * @return The sql of listing group key md5 by page.
     */
    MapperResult listGroupKeyMd5ByPageFetchRows(MapperContext context);
    
    /**
     * query all configuration information according to group, appName, tenant (for export). The default sql: SELECT
     * id,data_id,group_id,tenant_id,app_name,content,type,md5,gmt_create,gmt_modified,
     * src_user,src_ip,c_desc,c_use,effect,c_schema,encrypted_data_key FROM config_info WHERE ...
     *
     * @param context The map of params, the key is the parameter name(dataId, group, appName), the value is the key's
     *                value.
     * @return Collection of ConfigInfo objects
     */
    default MapperResult findAllConfigInfo4Export(MapperContext context) {
        List<Long> ids = (List<Long>) context.getWhereParameter(FieldConstant.IDS);
        
        String sql = "SELECT id,data_id,group_id,tenant_id,app_name,content,type,md5,gmt_create,gmt_modified,"
                + "src_user,src_ip,c_desc,c_use,effect,c_schema,encrypted_data_key FROM config_info";
        StringBuilder where = new StringBuilder(" WHERE ");
        
        List<Object> paramList = new ArrayList<>();
        if (!CollectionUtils.isEmpty(ids)) {
            where.append(" id IN (");
            for (int i = 0; i < ids.size(); i++) {
                if (i != 0) {
                    where.append(", ");
                }
                where.append('?');
                paramList.add(ids.get(i));
            }
            where.append(") ");
        } else {
            where.append(" tenant_id = ? ");
            paramList.add(context.getWhereParameter(FieldConstant.TENANT_ID));
            
            String dataId = (String) context.getWhereParameter(FieldConstant.DATA_ID);
            String group = (String) context.getWhereParameter(FieldConstant.GROUP_ID);
            String appName = (String) context.getWhereParameter(FieldConstant.APP_NAME);
            
            if (StringUtils.isNotBlank(dataId)) {
                where.append(" AND data_id LIKE ? ");
                paramList.add(dataId);
            }
            if (StringUtils.isNotBlank(group)) {
                where.append(" AND group_id= ? ");
                paramList.add(group);
            }
            if (StringUtils.isNotBlank(appName)) {
                where.append(" AND app_name= ? ");
                paramList.add(appName);
            }
        }
        return new MapperResult(sql + where, paramList);
    }
    
    /**
     * Get the count of config information. The default sql: SELECT count(*) FROM config_info WHERE ...
     *
     * @param context The map of params, the key is the parameter name(dataId, groupId, tenant_id, content), the value
     *                is the arbitrary object.
     * @return The sql of getting the count of config information.
     */
    default MapperResult findConfigInfoBaseLikeCountRows(MapperContext context) {
        final String dataId = (String) context.getWhereParameter(FieldConstant.DATA_ID);
        final String group = (String) context.getWhereParameter(FieldConstant.GROUP_ID);
        final String content = (String) context.getWhereParameter(FieldConstant.CONTENT);
        
        final List<Object> paramList = new ArrayList<>();
        final String sqlCountRows = "SELECT count(*) FROM config_info WHERE ";
        String where = " 1=1 AND tenant_id='" + NamespaceUtil.getNamespaceDefaultId() + "' ";
        
        if (!StringUtils.isBlank(dataId)) {
            where += " AND data_id LIKE ? ";
            paramList.add(dataId);
        }
        if (!StringUtils.isBlank(group)) {
            where += " AND group_id LIKE ? ";
            paramList.add(group);
        }
        if (!StringUtils.isBlank(content)) {
            where += " AND content LIKE ? ";
            paramList.add(content);
        }
        return new MapperResult(sqlCountRows + where, paramList);
    }
    
    /**
     * Get the config information. The default sql: SELECT id,data_id,group_id,tenant_id,content FROM config_info WHERE
     * ...
     *
     * @param context The map of params, the key is the parameter name(dataId, groupId, tenant_id, content), the value
     *                is the key's value.
     * @return The sql of getting the config information.
     */
    MapperResult findConfigInfoBaseLikeFetchRows(MapperContext context);
    
    /**
     * find the count of config info. The default sql: SELECT count(*) FROM config_info ...
     *
     * @param context The mpa of dataId, groupId and appName.
     * @return The count of config info.
     */
    default MapperResult findConfigInfo4PageCountRows(MapperContext context) {
        final String dataId = (String) context.getWhereParameter(FieldConstant.DATA_ID);
        final String group = (String) context.getWhereParameter(FieldConstant.GROUP_ID);
        final String content = (String) context.getWhereParameter(FieldConstant.CONTENT);
        final String appName = (String) context.getWhereParameter(FieldConstant.APP_NAME);
        final String tenantId = (String) context.getWhereParameter(FieldConstant.TENANT_ID);
        final List<Object> paramList = new ArrayList<>();
        
        final String sqlCount = "SELECT count(*) FROM config_info";
        StringBuilder where = new StringBuilder(" WHERE ");
        where.append(" tenant_id=? ");
        paramList.add(tenantId);
        if (StringUtils.isNotBlank(dataId)) {
            where.append(" AND data_id=? ");
            paramList.add(dataId);
        }
        if (StringUtils.isNotBlank(group)) {
            where.append(" AND group_id=? ");
            paramList.add(group);
        }
        if (StringUtils.isNotBlank(appName)) {
            where.append(" AND app_name=? ");
            paramList.add(appName);
        }
        if (!StringUtils.isBlank(content)) {
            where.append(" AND content LIKE ? ");
            paramList.add(content);
        }
        return new MapperResult(sqlCount + where, paramList);
    }
    
    /**
     * find config info. The default sql: SELECT id,data_id,group_id,tenant_id,app_name,content,type,encrypted_data_key
     * FROM config_info ...
     *
     * @param context The mpa of dataId, groupId and appName.
     * @return The sql of finding config info.
     */
    MapperResult findConfigInfo4PageFetchRows(MapperContext context);
    
    /**
     * Query configuration information based on group. The default sql: SELECT id,data_id,group_id,content FROM
     * config_info WHERE group_id=? AND tenant_id=?
     *
     * @param context The context of startRow, pageSize
     * @return Query configuration information based on group.
     */
    MapperResult findConfigInfoBaseByGroupFetchRows(MapperContext context);
    
    /**
     * Query config info count. The default sql: SELECT count(*) FROM config_info ...
     *
     * @param context The map of dataId, group, appName, content
     * @return The sql of querying config info count
     */
    default MapperResult findConfigInfoLike4PageCountRows(MapperContext context) {
        final String dataId = (String) context.getWhereParameter(FieldConstant.DATA_ID);
        final String group = (String) context.getWhereParameter(FieldConstant.GROUP_ID);
        final String content = (String) context.getWhereParameter(FieldConstant.CONTENT);
        final String appName = (String) context.getWhereParameter(FieldConstant.APP_NAME);
        final String tenantId = (String) context.getWhereParameter(FieldConstant.TENANT_ID);
        
        final List<Object> paramList = new ArrayList<>();
        
        final String sqlCountRows = "SELECT count(*) FROM config_info";
        StringBuilder where = new StringBuilder(" WHERE ");
        where.append(" tenant_id LIKE ? ");
        paramList.add(tenantId);
        if (!StringUtils.isBlank(dataId)) {
            where.append(" AND data_id LIKE ? ");
            paramList.add(dataId);
        }
        if (!StringUtils.isBlank(group)) {
            where.append(" AND group_id LIKE ? ");
            paramList.add(group);
        }
        if (!StringUtils.isBlank(appName)) {
            where.append(" AND app_name = ? ");
            paramList.add(appName);
        }
        if (!StringUtils.isBlank(content)) {
            where.append(" AND content LIKE ? ");
            paramList.add(content);
        }
        return new MapperResult(sqlCountRows + where, paramList);
    }
    
    /**
     * Query config info. <br/>The default sql: <br/>SELECT
     * id,data_id,group_id,tenant_id,app_name,content,encrypted_data_key FROM config_info ...
     *
     * @param context The context of startRow, pageSize
     * @return The sql of querying config info
     */
    MapperResult findConfigInfoLike4PageFetchRows(MapperContext context);
    
    /**
     * Query all configuration information by page. <br/>The default sql: <br/>SELECT
     * t.id,data_id,group_id,tenant_id,app_name,content,md5 " + " FROM (  SELECT id FROM config_info WHERE tenant_id
     * LIKE ? ORDER BY id LIMIT ?,? )" + " g, config_info t  WHERE g.id = t.id
     *
     * @param context The context of startRow, pageSize
     * @return Query all configuration information by page.
     */
    MapperResult findAllConfigInfoFetchRows(MapperContext context);
    
    /**
     * find ConfigInfo by ids. <br/>The default sql: <br/>SELECT ID,data_id,group_id,tenant_id,app_name,content,md5 FROM
     * config_info WHERE id IN (...)
     *
     * @param context the size of ids.
     * @return find ConfigInfo by ids.
     */
    default MapperResult findConfigInfosByIds(MapperContext context) {
        List<Long> ids = (List<Long>) context.getWhereParameter(FieldConstant.IDS);
        StringBuilder sql = new StringBuilder(
                "SELECT id,data_id,group_id,tenant_id,app_name,content,md5 FROM config_info WHERE ");
        sql.append("id IN (");
        ArrayList<Object> paramList = new ArrayList<>();
        
        for (int i = 0; i < ids.size(); i++) {
            if (i != 0) {
                sql.append(", ");
            }
            sql.append('?');
            paramList.add(ids.get(i));
        }
        sql.append(") ");
        return new MapperResult(sql.toString(), paramList);
    }
    
    /**
     * Remove configuration; database atomic operation, minimum SQL action, no business encapsulation.
     *
     * @param context The size of ids.
     * @return The sql of removing configuration.
     */
    default MapperResult removeConfigInfoByIdsAtomic(MapperContext context) {
        List<Long> ids = (List<Long>) context.getWhereParameter(FieldConstant.IDS);
        StringBuilder sql = new StringBuilder("DELETE FROM config_info WHERE ");
        sql.append("id IN (");
        ArrayList<Object> paramList = new ArrayList<>();
        
        for (int i = 0; i < ids.size(); i++) {
            if (i != 0) {
                sql.append(", ");
            }
            sql.append('?');
            paramList.add(ids.get(i));
        }
        sql.append(") ");
        return new MapperResult(sql.toString(), paramList);
    }
    
    /**
     * Update configuration; database atomic operation, minimum SQL action, no business encapsulation. The default sql:
     * UPDATE config_info SET content=?, md5 = ?, src_ip=?,src_user=?,gmt_modified=?, app_name=?,c_desc=?,c_use=?,
     * effect=?,type=?,c_schema=? WHERE data_id=? AND group_id=? AND tenant_id=? AND (md5=? OR md5 IS NULL OR md5='')
     *
     * @param context sql paramMap
     * @return The sql of updating configuration cas.
     */
    default MapperResult updateConfigInfoAtomicCas(MapperContext context) {
        List<Object> paramList = new ArrayList<>();
        
        paramList.add(context.getUpdateParameter(FieldConstant.CONTENT));
        paramList.add(context.getUpdateParameter(FieldConstant.MD5));
        paramList.add(context.getUpdateParameter(FieldConstant.SRC_IP));
        paramList.add(context.getUpdateParameter(FieldConstant.SRC_USER));
        paramList.add(context.getUpdateParameter(FieldConstant.GMT_MODIFIED));
        paramList.add(context.getUpdateParameter(FieldConstant.APP_NAME));
        paramList.add(context.getUpdateParameter(FieldConstant.C_DESC));
        paramList.add(context.getUpdateParameter(FieldConstant.C_USE));
        paramList.add(context.getUpdateParameter(FieldConstant.EFFECT));
        paramList.add(context.getUpdateParameter(FieldConstant.TYPE));
        paramList.add(context.getUpdateParameter(FieldConstant.C_SCHEMA));
        paramList.add(context.getUpdateParameter(FieldConstant.ENCRYPTED_DATA_KEY));
        paramList.add(context.getWhereParameter(FieldConstant.DATA_ID));
        paramList.add(context.getWhereParameter(FieldConstant.GROUP_ID));
        paramList.add(context.getWhereParameter(FieldConstant.TENANT_ID));
        paramList.add(context.getWhereParameter(FieldConstant.MD5));
        String sql = "UPDATE config_info SET " + "content=?, md5 = ?, src_ip=?,src_user=?,gmt_modified=?,"
                + " app_name=?,c_desc=?,c_use=?,effect=?,type=?,c_schema=?,encrypted_data_key=? "
                + "WHERE data_id=? AND group_id=? AND tenant_id=? AND (md5=? OR md5 IS NULL OR md5='')";
        return new MapperResult(sql, paramList);
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
