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

package com.alibaba.nacos.plugin.datasource.impl.oracle;

import com.alibaba.nacos.common.utils.ArrayUtils;
import com.alibaba.nacos.common.utils.CollectionUtils;
import com.alibaba.nacos.common.utils.NamespaceUtil;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.plugin.datasource.constants.ContextConstant;
import com.alibaba.nacos.plugin.datasource.constants.DataSourceConstant;
import com.alibaba.nacos.plugin.datasource.constants.FieldConstant;
import com.alibaba.nacos.plugin.datasource.mapper.ConfigInfoMapper;
import com.alibaba.nacos.plugin.datasource.model.MapperContext;
import com.alibaba.nacos.plugin.datasource.model.MapperResult;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The oracle implementation of ConfigInfoMapper.
 *
 * @author liam.fu
 **/
public class ConfigInfoMapperByOracle extends AbstractMapperByOracle implements ConfigInfoMapper {
    
    @Override
    public MapperResult findConfigInfoByAppFetchRows(MapperContext context) {
        final String appName = (String) context.getWhereParameter(FieldConstant.APP_NAME);
        final String tenantId = (String) context.getWhereParameter(FieldConstant.TENANT_ID);
        String sql = "SELECT id,data_id,group_id,tenant_id,app_name,content FROM config_info"
            + " WHERE tenant_id LIKE ? AND app_name= ?" + " ORDER BY id OFFSET "
            + context.getStartRow() + " ROWS FETCH NEXT "
            + context.getPageSize() + " ROWS ONLY";
        return new MapperResult(sql, CollectionUtils.list(tenantId, appName));
    }
    
    @Override
    public MapperResult getTenantIdList(MapperContext context) {
        String sql = "SELECT tenant_id FROM config_info WHERE tenant_id != '"
            + NamespaceUtil.getNamespaceDefaultId()
            + "' GROUP BY tenant_id ORDER BY tenant_id OFFSET " + context.getStartRow()
            + " ROWS FETCH NEXT " + context.getPageSize() + " ROWS ONLY";
        return new MapperResult(sql, Collections.emptyList());
    }
    
    @Override
    public MapperResult getGroupIdList(MapperContext context) {
        String sql = "SELECT group_id FROM config_info WHERE tenant_id ='"
            + NamespaceUtil.getNamespaceDefaultId()
            + "' GROUP BY group_id ORDER BY group_id OFFSET " + context.getStartRow()
            + " ROWS FETCH NEXT " + context.getPageSize() + " ROWS ONLY";
        return new MapperResult(sql, Collections.emptyList());
    }
    
    @Override
    public MapperResult findAllConfigKey(MapperContext context) {
        String sql = " SELECT data_id,group_id,app_name  FROM ( "
            + " SELECT id FROM config_info WHERE tenant_id LIKE ? ORDER BY id OFFSET "
            + context.getStartRow() + " ROWS FETCH NEXT "
            + context.getPageSize() + " ROWS ONLY)" + " g, config_info t WHERE g.id = t.id  ";
        return new MapperResult(sql,
            CollectionUtils.list(context.getWhereParameter(FieldConstant.TENANT_ID)));
    }
    
    @Override
    public MapperResult findAllConfigInfoBaseFetchRows(MapperContext context) {
        String sql =
            "SELECT t.id,data_id,group_id,content,md5"
                + " FROM ( SELECT id FROM config_info ORDER BY id OFFSET "
                + context.getStartRow() + " ROWS FETCH NEXT " + context.getPageSize()
                + " ROWS ONLY)"
                + " g, config_info t  WHERE g.id = t.id ";
        return new MapperResult(sql, Collections.emptyList());
    }
    
    @Override
    public MapperResult findAllConfigInfoFragment(MapperContext context) {
        String contextParameter = context.getContextParameter(ContextConstant.NEED_CONTENT);
        boolean needContent = contextParameter != null && Boolean.parseBoolean(contextParameter);
        String sql = "SELECT id,data_id,group_id,tenant_id,app_name,"
            + (needContent ? "content," : "")
            + "md5,gmt_modified,type,encrypted_data_key FROM config_info WHERE id > ? ORDER BY id ASC OFFSET "
            + context.getStartRow() + " ROWS FETCH NEXT " + context.getPageSize() + " ROWS ONLY";
        return new MapperResult(sql,
            CollectionUtils.list(context.getWhereParameter(FieldConstant.ID)));
    }
    
    @Override
    public MapperResult findChangeConfig(MapperContext context) {
        String sql =
            "SELECT id, data_id, group_id, tenant_id, app_name,md5, gmt_modified, encrypted_data_key FROM config_info WHERE "
                + "gmt_modified >= ? and id > ? order by id fetch first ? rows only";
        return new MapperResult(sql,
            CollectionUtils.list(context.getWhereParameter(FieldConstant.START_TIME),
                context.getWhereParameter(FieldConstant.LAST_MAX_ID),
                context.getWhereParameter(FieldConstant.PAGE_SIZE)));
    }
    
    @Override
    public MapperResult findChangeConfigFetchRows(MapperContext context) {
        final String tenant = (String) context.getWhereParameter(FieldConstant.TENANT_ID);
        final String dataId = (String) context.getWhereParameter(FieldConstant.DATA_ID);
        final String group = (String) context.getWhereParameter(FieldConstant.GROUP_ID);
        final String appName = (String) context.getWhereParameter(FieldConstant.APP_NAME);
        final String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
        final Timestamp startTime = (Timestamp) context.getWhereParameter(FieldConstant.START_TIME);
        final Timestamp endTime = (Timestamp) context.getWhereParameter(FieldConstant.END_TIME);
        
        List<Object> paramList = new ArrayList<>();
        
        final String sqlFetchRows =
            "SELECT id,data_id,group_id,tenant_id,app_name,type,md5,gmt_modified FROM config_info WHERE ";
        String where = " 1=1 ";
        if (!StringUtils.isBlank(dataId)) {
            where += " AND data_id LIKE ? ";
            paramList.add(dataId);
        }
        if (!StringUtils.isBlank(group)) {
            where += " AND group_id LIKE ? ";
            paramList.add(group);
        }
        
        if (!StringUtils.isBlank(tenantTmp)) {
            where += " AND tenant_id = ? ";
            paramList.add(tenantTmp);
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
        return new MapperResult(
            sqlFetchRows + where + " AND id > "
                + context.getWhereParameter(FieldConstant.LAST_MAX_ID)
                + " ORDER BY id ASC" + " OFFSET " + 0 + " ROWS FETCH NEXT " + context.getPageSize()
                + " ROWS ONLY",
            paramList);
    }
    
    @Override
    public MapperResult listGroupKeyMd5ByPageFetchRows(MapperContext context) {
        String sql =
            "SELECT t.id,data_id,group_id,tenant_id,app_name,md5,type,gmt_modified,encrypted_data_key FROM "
                + "( SELECT id FROM config_info ORDER BY id OFFSET " + context.getStartRow()
                + " ROWS FETCH NEXT "
                + context.getPageSize() + " ROWS ONLY) g, config_info t WHERE g.id = t.id";
        return new MapperResult(sql, Collections.emptyList());
    }
    
    @Override
    public MapperResult findConfigInfoBaseLikeFetchRows(MapperContext context) {
        final String dataId = (String) context.getWhereParameter(FieldConstant.DATA_ID);
        final String group = (String) context.getWhereParameter(FieldConstant.GROUP_ID);
        final String content = (String) context.getWhereParameter(FieldConstant.CONTENT);
        
        final String sqlFetchRows =
            "SELECT id,data_id,group_id,tenant_id,content FROM config_info WHERE ";
        String where = " 1=1 AND tenant_id='" + NamespaceUtil.getNamespaceDefaultId() + "' ";
        
        List<Object> paramList = new ArrayList<>();
        
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
        return new MapperResult(sqlFetchRows + where
            + " ORDER BY id OFFSET " + context.getStartRow()
            + " ROWS FETCH NEXT " + context.getPageSize()
            + " ROWS ONLY",
            paramList);
    }
    
    @Override
    public MapperResult findConfigInfo4PageFetchRows(MapperContext context) {
        final String tenant = (String) context.getWhereParameter(FieldConstant.TENANT_ID);
        final String dataId = (String) context.getWhereParameter(FieldConstant.DATA_ID);
        final String group = (String) context.getWhereParameter(FieldConstant.GROUP_ID);
        final String appName = (String) context.getWhereParameter(FieldConstant.APP_NAME);
        final String content = (String) context.getWhereParameter(FieldConstant.CONTENT);
        
        List<Object> paramList = new ArrayList<>();
        
        StringBuilder idSql = new StringBuilder(
            "SELECT id FROM config_info WHERE tenant_id=? ");
        paramList.add(tenant);
        
        if (StringUtils.isNotBlank(dataId)) {
            idSql.append(" AND data_id=?");
            paramList.add(dataId);
        }
        if (StringUtils.isNotBlank(group)) {
            idSql.append(" AND group_id=?");
            paramList.add(group);
        }
        if (StringUtils.isNotBlank(appName)) {
            idSql.append(" AND app_name=?");
            paramList.add(appName);
        }
        if (!StringUtils.isBlank(content)) {
            idSql.append(" AND content LIKE ?");
            paramList.add(content);
        }
        
        // 先分页，减少后续 JOIN 的数据量
        idSql.append(" ORDER BY id OFFSET ")
            .append(context.getStartRow())
            .append(" ROWS FETCH NEXT ")
            .append(context.getPageSize())
            .append(" ROWS ONLY");
        
        // 外层查询：对分页后的结果进行标签关联
        String sql =
            "WITH tag_agg AS ( "
                + "   SELECT id, LISTAGG(tag_name, ',') "
                + "   WITHIN GROUP (ORDER BY tag_name) AS config_tags "
                + "   FROM config_tags_relation GROUP BY id "
                + ") "
                + "SELECT a.id,a.data_id,a.group_id,a.tenant_id,a.app_name,"
                + "       a.content,a.md5,a.type,a.encrypted_data_key,a.c_desc,"
                + "       t.config_tags "
                + "FROM config_info a "
                + "JOIN ("
                + idSql.toString()
                + ") x ON a.id = x.id "
                + "LEFT JOIN tag_agg t ON a.id = t.id";
        
        return new MapperResult(sql, paramList);
    }
    
    @Override
    public MapperResult findConfigInfoBaseByGroupFetchRows(MapperContext context) {
        String sql =
            "SELECT id,data_id,group_id,content FROM config_info WHERE group_id=? AND tenant_id=?"
                + " ORDER BY id OFFSET "
                + context.getStartRow() + " ROWS FETCH NEXT " + context.getPageSize()
                + " ROWS ONLY";
        return new MapperResult(sql,
            CollectionUtils.list(context.getWhereParameter(FieldConstant.GROUP_ID),
                context.getWhereParameter(FieldConstant.TENANT_ID)));
    }
    
    @Override
    public MapperResult findConfigInfoLike4PageFetchRows(MapperContext context) {
        final String tenant = (String) context.getWhereParameter(FieldConstant.TENANT_ID);
        final String dataId = (String) context.getWhereParameter(FieldConstant.DATA_ID);
        final String group = (String) context.getWhereParameter(FieldConstant.GROUP_ID);
        final String appName = (String) context.getWhereParameter(FieldConstant.APP_NAME);
        final String content = (String) context.getWhereParameter(FieldConstant.CONTENT);
        final String[] types = (String[]) context.getWhereParameter(FieldConstant.TYPE);
        
        List<Object> paramList = new ArrayList<>();
        
        StringBuilder idSql =
            new StringBuilder("SELECT id FROM config_info WHERE tenant_id LIKE ?");
        paramList.add(tenant);
        
        if (StringUtils.isNotBlank(dataId)) {
            idSql.append(" AND data_id LIKE ?");
            paramList.add(dataId);
        }
        if (StringUtils.isNotBlank(group)) {
            idSql.append(" AND group_id LIKE ?");
            paramList.add(group);
        }
        if (StringUtils.isNotBlank(appName)) {
            idSql.append(" AND app_name = ?");
            paramList.add(appName);
        }
        if (StringUtils.isNotBlank(content)) {
            idSql.append(" AND content LIKE ?");
            paramList.add(content);
        }
        if (!ArrayUtils.isEmpty(types)) {
            idSql.append(" AND type IN (");
            for (int i = 0; i < types.length; i++) {
                if (i != 0) {
                    idSql.append(", ");
                }
                idSql.append("?");
                paramList.add(types[i]);
            }
            idSql.append(")");
        }
        
        // 先分页，减少后续 JOIN 的数据量
        idSql.append(" ORDER BY id OFFSET ")
            .append(context.getStartRow())
            .append(" ROWS FETCH NEXT ")
            .append(context.getPageSize())
            .append(" ROWS ONLY");
        
        // 外层查询：对分页后的结果进行标签关联
        String sql =
            "WITH tag_agg AS ( "
                + "   SELECT id, LISTAGG(tag_name, ',') WITHIN GROUP (ORDER BY tag_name) AS config_tags "
                + "   FROM config_tags_relation GROUP BY id " + ") "
                + "SELECT a.id, a.data_id, a.group_id, a.tenant_id, a.app_name, a.content, "
                + "       a.md5, a.encrypted_data_key, a.type, a.c_desc, t.config_tags "
                + "FROM config_info a "
                + "JOIN ("
                + idSql.toString()
                + ") x ON a.id = x.id "
                + "LEFT JOIN tag_agg t ON a.id = t.id";
        
        return new MapperResult(sql, paramList);
    }
    
    @Override
    public MapperResult findAllConfigInfoFetchRows(MapperContext context) {
        String sql = "SELECT t.id,data_id,group_id,tenant_id,app_name,content,md5 "
            + " FROM (  SELECT id FROM config_info WHERE tenant_id LIKE ? ORDER BY id OFFSET ? ROWS FETCH NEXT ? ROWS ONLY )"
            + " g, config_info t  WHERE g.id = t.id ";
        return new MapperResult(sql,
            CollectionUtils.list(context.getWhereParameter(FieldConstant.TENANT_ID),
                context.getStartRow(),
                context.getPageSize()));
    }
    
    @Override
    public String getDataSource() {
        return DataSourceConstant.ORACLE;
    }
}
