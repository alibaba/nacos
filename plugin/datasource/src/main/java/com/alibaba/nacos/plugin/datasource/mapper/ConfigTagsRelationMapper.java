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

import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.plugin.datasource.constants.FieldConstant;
import com.alibaba.nacos.plugin.datasource.constants.TableConstant;
import com.alibaba.nacos.plugin.datasource.model.MapperContext;
import com.alibaba.nacos.plugin.datasource.model.MapperResult;

import java.util.ArrayList;
import java.util.List;

/**
 * The config with tags mapper.
 *
 * @author hyx
 **/

public interface ConfigTagsRelationMapper extends Mapper {
    
    /**
     * Get the count of config info.
     * The default sql:
     * SELECT count(*) FROM config_info WHERE ...
     *
     * @param context The map of params, the key is the parameter name(dataId, groupId, tenantId, appName, startTime,
     *                endTime, content), the value is the key's value.
     * @return The sql of get config info.
     */
    default MapperResult findConfigInfo4PageCountRows(final MapperContext context) {
        final String appName = (String) context.getWhereParameter(FieldConstant.APP_NAME);
        final String tenantId = (String) context.getWhereParameter(FieldConstant.TENANT_ID);
        final String dataId = (String) context.getWhereParameter(FieldConstant.DATA_ID);
        final String group = (String) context.getWhereParameter(FieldConstant.GROUP_ID);
        final String content = (String) context.getWhereParameter(FieldConstant.CONTENT);
        final String[] tagArr = (String[]) context.getWhereParameter(FieldConstant.TAG_ARR);
        
        List<Object> paramList = new ArrayList<>();
        StringBuilder where = new StringBuilder(" WHERE ");
        final String sqlCount = "SELECT count(*) FROM config_info  a LEFT JOIN config_tags_relation b ON a.id=b.id";
        
        where.append(" a.tenant_id=? ");
        paramList.add(tenantId);
        if (StringUtils.isNotBlank(dataId)) {
            where.append(" AND a.data_id=? ");
            paramList.add(dataId);
        }
        if (StringUtils.isNotBlank(group)) {
            where.append(" AND a.group_id=? ");
            paramList.add(group);
        }
        if (StringUtils.isNotBlank(appName)) {
            where.append(" AND a.app_name=? ");
            paramList.add(appName);
        }
        if (!StringUtils.isBlank(content)) {
            where.append(" AND a.content LIKE ? ");
            paramList.add(content);
        }
        where.append(" AND b.tag_name IN (");
        for (int i = 0; i < tagArr.length; i++) {
            if (i != 0) {
                where.append(", ");
            }
            where.append('?');
            paramList.add(tagArr[i]);
    
        }
        where.append(") ");
        return new MapperResult(sqlCount + where, paramList);
    }
    
    /**
     * Find config info.
     * The default sql:
     * SELECT a.id,a.data_id,a.group_id,a.tenant_id,a.app_name,a.content FROM config_info  a LEFT JOIN
     * config_tags_relation b ON a.id=b.i ...
     *
     * @param context The keys and values are dataId and group.
     * @return The sql of finding config info.
     */
    MapperResult findConfigInfo4PageFetchRows(final MapperContext context);
    
    /**
     * Get the count of config information by config tags relation.
     * The default sql:
     * SELECT count(*) FROM config_info  a LEFT JOIN config_tags_relation b ON a.id=b.id
     *
     * @param context the keys and values are dataId and group.
     * @return The sql of getting the count of config information.
     */
    default MapperResult findConfigInfoLike4PageCountRows(final MapperContext context) {
        final String appName = (String) context.getWhereParameter(FieldConstant.APP_NAME);
        final String tenantId = (String) context.getWhereParameter(FieldConstant.TENANT_ID);
        final String dataId = (String) context.getWhereParameter(FieldConstant.DATA_ID);
        final String group = (String) context.getWhereParameter(FieldConstant.GROUP_ID);
        final String content = (String) context.getWhereParameter(FieldConstant.CONTENT);
        final String[] tagArr = (String[]) context.getWhereParameter(FieldConstant.TAG_ARR);
        
        List<Object> paramList = new ArrayList<>();
        StringBuilder where = new StringBuilder(" WHERE ");
        final String sqlCount = "SELECT count(*) FROM config_info  a LEFT JOIN config_tags_relation b ON a.id=b.id ";
        
        where.append(" a.tenant_id LIKE ? ");
        paramList.add(tenantId);
        if (!StringUtils.isBlank(dataId)) {
            where.append(" AND a.data_id LIKE ? ");
            paramList.add(dataId);
        }
        if (StringUtils.isNotBlank(group)) {
            where.append(" AND a.group_id LIKE ? ");
            paramList.add(group);
        }
        if (StringUtils.isNotBlank(appName)) {
            where.append(" AND a.app_name = ? ");
            paramList.add(appName);
        }
        if (StringUtils.isNotBlank(content)) {
            where.append(" AND a.content LIKE ? ");
            paramList.add(content);
        }
        
        where.append(" AND b.tag_name IN (");
        for (int i = 0; i < tagArr.length; i++) {
            if (i != 0) {
                where.append(", ");
            }
            where.append('?');
            paramList.add(tagArr[i]);
        }
        where.append(") ");
        return new MapperResult(sqlCount + where, paramList);
    }
    
    /**
     * Query config info.
     * The default sql:
     * SELECT a.id,a.data_id,a.group_id,a.tenant_id,a.app_name,a.content
     * FROM config_info a LEFT JOIN config_tags_relation b ON a.id=b.id
     *
     * @param context the keys and values are dataId and group.
     * @return The sql of querying config info.
     */
    MapperResult findConfigInfoLike4PageFetchRows(final MapperContext context);
    
    /**
     * 获取返回表名.
     *
     * @return 表名
     */
    default String getTableName() {
        return TableConstant.CONFIG_TAGS_RELATION;
    }
}
