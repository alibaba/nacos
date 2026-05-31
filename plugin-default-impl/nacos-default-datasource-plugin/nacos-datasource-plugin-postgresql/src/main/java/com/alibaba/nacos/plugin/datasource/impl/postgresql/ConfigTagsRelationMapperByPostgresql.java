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

package com.alibaba.nacos.plugin.datasource.impl.postgresql;

import com.alibaba.nacos.common.utils.ArrayUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.plugin.datasource.constants.DatabaseTypeConstant;
import com.alibaba.nacos.plugin.datasource.constants.FieldConstant;
import com.alibaba.nacos.plugin.datasource.impl.base.BaseConfigTagsRelationMapper;
import com.alibaba.nacos.plugin.datasource.mapper.ext.WhereBuilder;
import com.alibaba.nacos.plugin.datasource.model.MapperContext;
import com.alibaba.nacos.plugin.datasource.model.MapperResult;

/**
 * The postgresql implementation of ConfigTagsRelationMapper.
 *
 * @author Long Yu
 **/

public class ConfigTagsRelationMapperByPostgresql extends BaseConfigTagsRelationMapper {
    
    @Override
    public String getDataSource() {
        return DatabaseTypeConstant.POSTGRESQL;
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
            "SELECT a.id,a.data_id,a.group_id,a.tenant_id,a.app_name,a.content,a.md5,a.encrypted_data_key,a.type,a.c_desc "
                + "FROM config_info a ");
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
            innerWhere.and().exists("SELECT 1 FROM config_tags_relation b WHERE ", sub -> {
                sub.eqColumn("b.id", "a.id").and().startParentheses();
                for (int i = 0; i < tagArr.length; i++) {
                    if (i != 0) {
                        sub.or();
                    }
                    sub.like("b.tag_name", tagArr[i]);
                }
                sub.endParentheses();
            });
        }
        if (!ArrayUtils.isEmpty(types)) {
            innerWhere.and().in("a.type", types);
        }
        
        innerWhere.offset(context.getStartRow(), context.getPageSize());
        MapperResult innerResult = innerWhere.build();
        
        // 构建外层查询：获取筛选出的配置的完整标签信息
        // 使用exists和标量子查询规避group by ...content..带来的大字段分组开销
        final String sql =
            "SELECT c.id,c.data_id,c.group_id,c.tenant_id,c.app_name,c.content,c.md5,c.encrypted_data_key,c.type,c.c_desc,"
                + "(SELECT STRING_AGG(tag_name, ',') FROM config_tags_relation d WHERE d.id = c.id) as config_tags "
                + "FROM (" + innerResult.getSql() + ") c ";
        return new MapperResult(sql, innerResult.getParamList());
    }
    
}
