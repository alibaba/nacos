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

package com.alibaba.nacos.plugin.datasource.impl.mysql;

import com.alibaba.nacos.common.utils.ArrayUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.plugin.datasource.constants.DataSourceConstant;
import com.alibaba.nacos.plugin.datasource.constants.FieldConstant;
import com.alibaba.nacos.plugin.datasource.mapper.ConfigTagsRelationMapper;
import com.alibaba.nacos.plugin.datasource.mapper.ext.WhereBuilder;
import com.alibaba.nacos.plugin.datasource.model.MapperContext;
import com.alibaba.nacos.plugin.datasource.model.MapperResult;

import java.util.ArrayList;
import java.util.List;

/**
 * The mysql implementation of ConfigTagsRelationMapper.
 *
 * @author hyx
 **/

public class ConfigTagsRelationMapperByMySql extends AbstractMapperByMysql implements ConfigTagsRelationMapper {
    
    @Override
    public MapperResult findConfigInfo4PageFetchRows(MapperContext context) {
        final String tenant = (String) context.getWhereParameter(FieldConstant.TENANT_ID);
        final String dataId = (String) context.getWhereParameter(FieldConstant.DATA_ID);
        final String group = (String) context.getWhereParameter(FieldConstant.GROUP_ID);
        final String appName = (String) context.getWhereParameter(FieldConstant.APP_NAME);
        final String content = (String) context.getWhereParameter(FieldConstant.CONTENT);
        final String[] tagArr = (String[]) context.getWhereParameter(FieldConstant.TAG_ARR);
        
        List<Object> paramList = new ArrayList<>();
        
        // 构建内层查询：根据标签条件筛选配置
        StringBuilder innerWhere = new StringBuilder(" WHERE ");
        innerWhere.append(" a.tenant_id=? ");
        paramList.add(tenant);
        
        if (StringUtils.isNotBlank(dataId)) {
            innerWhere.append(" AND a.data_id=? ");
            paramList.add(dataId);
        }
        if (StringUtils.isNotBlank(group)) {
            innerWhere.append(" AND a.group_id=? ");
            paramList.add(group);
        }
        if (StringUtils.isNotBlank(appName)) {
            innerWhere.append(" AND a.app_name=? ");
            paramList.add(appName);
        }
        if (!StringUtils.isBlank(content)) {
            innerWhere.append(" AND a.content LIKE ? ");
            paramList.add(content);
        }
        innerWhere.append(" AND b.tag_name IN (");
        for (int i = 0; i < tagArr.length; i++) {
            if (i != 0) {
                innerWhere.append(", ");
            }
            innerWhere.append('?');
            paramList.add(tagArr[i]);
        }
        innerWhere.append(") ");
        
        // 使用子查询分离筛选逻辑和标签聚合逻辑
        final String sql = "SELECT c.id,c.data_id,c.group_id,c.tenant_id,c.app_name,c.content,c.md5,c.type,c.encrypted_data_key,c.c_desc,"
                + "GROUP_CONCAT(DISTINCT d.tag_name SEPARATOR ',') as config_tags "
                + "FROM ("
                + "SELECT DISTINCT a.id,a.data_id,a.group_id,a.tenant_id,a.app_name,a.content,a.md5,a.type,a.encrypted_data_key,a.c_desc "
                + "FROM config_info a LEFT JOIN config_tags_relation b ON a.id=b.id"
                + innerWhere
                + "LIMIT " + context.getStartRow() + "," + context.getPageSize()
                + ") c LEFT JOIN config_tags_relation d ON c.id=d.id "
                + "GROUP BY c.id,c.data_id,c.group_id,c.tenant_id,c.app_name,c.content,c.md5,c.type,c.encrypted_data_key,c.c_desc";
        
        return new MapperResult(sql, paramList);
    }
    
    @Override
    public MapperResult findConfigInfoLike4PageFetchRows(MapperContext context) {
        final String tenant = (String) context.getWhereParameter(FieldConstant.TENANT_ID);
        final String dataId = (String) context.getWhereParameter(FieldConstant.DATA_ID);
        final String group = (String) context.getWhereParameter(FieldConstant.GROUP_ID);
        final String appName = (String) context.getWhereParameter(FieldConstant.APP_NAME);
        final String content = (String) context.getWhereParameter(FieldConstant.CONTENT);
        final String[] tagArr = (String[]) context.getWhereParameter(FieldConstant.TAG_ARR);
        final String[] types = (String[]) context.getWhereParameter(FieldConstant.TYPE);
        
        // 构建内层查询：根据标签条件筛选配置
        WhereBuilder innerWhere = new WhereBuilder(
                "SELECT DISTINCT a.id,a.data_id,a.group_id,a.tenant_id,a.app_name,a.content,a.md5,a.encrypted_data_key,a.type,a.c_desc "
                + "FROM config_info a LEFT JOIN config_tags_relation b ON a.id=b.id");
        
        innerWhere.like("a.tenant_id", tenant);
        
        if (StringUtils.isNotBlank(dataId)) {
            innerWhere.and().like("a.data_id", dataId);
        }
        if (StringUtils.isNotBlank(group)) {
            innerWhere.and().like("a.group_id", group);
        }
        if (StringUtils.isNotBlank(appName)) {
            innerWhere.and().eq("a.app_name", appName);
        }
        if (StringUtils.isNotBlank(content)) {
            innerWhere.and().like("a.content", content);
        }
        if (!ArrayUtils.isEmpty(tagArr)) {
            innerWhere.and().startParentheses();
            for (int i = 0; i < tagArr.length; i++) {
                if (i != 0) {
                    innerWhere.or();
                }
                innerWhere.like("b.tag_name", tagArr[i]);
            }
            innerWhere.endParentheses();
        }
        if (!ArrayUtils.isEmpty(types)) {
            innerWhere.and().in("a.type", types);
        }
        
        innerWhere.limit(context.getStartRow(), context.getPageSize());
        MapperResult innerResult = innerWhere.build();
        
        // 构建外层查询：获取筛选出的配置的完整标签信息
        final String sql = "SELECT c.id,c.data_id,c.group_id,c.tenant_id,c.app_name,c.content,c.md5,c.encrypted_data_key,c.type,c.c_desc,"
                + "GROUP_CONCAT(DISTINCT d.tag_name SEPARATOR ',') as config_tags "
                + "FROM (" + innerResult.getSql() + ") c "
                + "LEFT JOIN config_tags_relation d ON c.id=d.id "
                + "GROUP BY c.id,c.data_id,c.group_id,c.tenant_id,c.app_name,c.content,c.md5,c.encrypted_data_key,c.type,c.c_desc";
        
        return new MapperResult(sql, innerResult.getParamList());
    }
    
    @Override
    public String getDataSource() {
        return DataSourceConstant.MYSQL;
    }
}
