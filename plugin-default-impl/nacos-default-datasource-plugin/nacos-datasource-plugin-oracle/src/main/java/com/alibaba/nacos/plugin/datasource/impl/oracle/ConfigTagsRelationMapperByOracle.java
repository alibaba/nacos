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
 * The oracle implementation of ConfigTagsRelationMapper.
 *
 * @author liam.fu
 **/
public class ConfigTagsRelationMapperByOracle extends AbstractMapperByOracle
    implements ConfigTagsRelationMapper {
    
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
        StringBuilder idSql = new StringBuilder();
        idSql.append("SELECT DISTINCT a.id ")
            .append("FROM config_info a ")
            .append("LEFT JOIN config_tags_relation b ON a.id = b.id ")
            .append("WHERE a.tenant_id = ? ");
        paramList.add(tenant);
        
        if (StringUtils.isNotBlank(dataId)) {
            idSql.append(" AND a.data_id=? ");
            paramList.add(dataId);
        }
        if (StringUtils.isNotBlank(group)) {
            idSql.append(" AND a.group_id=? ");
            paramList.add(group);
        }
        if (StringUtils.isNotBlank(appName)) {
            idSql.append(" AND a.app_name=? ");
            paramList.add(appName);
        }
        if (!StringUtils.isBlank(content)) {
            idSql.append(" AND a.content LIKE ? ");
            paramList.add(content);
        }
        if (tagArr != null && tagArr.length > 0) {
            idSql.append(" AND b.tag_name IN (");
            for (int i = 0; i < tagArr.length; i++) {
                if (i != 0) {
                    idSql.append(", ");
                }
                idSql.append('?');
                paramList.add(tagArr[i]);
            }
            idSql.append(") ");
        }
        
        idSql.append(" ORDER BY a.id OFFSET ")
            .append(context.getStartRow())
            .append(" ROWS FETCH NEXT ")
            .append(context.getPageSize())
            .append(" ROWS ONLY ");
        
        // 使用子查询分离筛选逻辑和标签聚合逻辑
        String sql =
            "WITH tag_agg AS ( "
                + "   SELECT id, LISTAGG(DISTINCT tag_name, ',') "
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
    public MapperResult findConfigInfoLike4PageFetchRows(MapperContext context) {
        final String tenant = (String) context.getWhereParameter(FieldConstant.TENANT_ID);
        final String dataId = (String) context.getWhereParameter(FieldConstant.DATA_ID);
        final String group = (String) context.getWhereParameter(FieldConstant.GROUP_ID);
        final String appName = (String) context.getWhereParameter(FieldConstant.APP_NAME);
        final String content = (String) context.getWhereParameter(FieldConstant.CONTENT);
        final String[] tagArr = (String[]) context.getWhereParameter(FieldConstant.TAG_ARR);
        final String[] types = (String[]) context.getWhereParameter(FieldConstant.TYPE);
        
        // 构建内层查询：根据标签条件筛选配置
        WhereBuilder idQuery = new WhereBuilder(
            "SELECT DISTINCT a.id FROM config_info a "
                + "LEFT JOIN config_tags_relation b ON a.id=b.id");
        
        idQuery.like("a.tenant_id", tenant);
        
        if (StringUtils.isNotBlank(dataId)) {
            idQuery.and().like("a.data_id", dataId);
        }
        if (StringUtils.isNotBlank(group)) {
            idQuery.and().like("a.group_id", group);
        }
        if (StringUtils.isNotBlank(appName)) {
            idQuery.and().eq("a.app_name", appName);
        }
        if (StringUtils.isNotBlank(content)) {
            idQuery.and().like("a.content", content);
        }
        if (!ArrayUtils.isEmpty(tagArr)) {
            idQuery.and().startParentheses();
            for (int i = 0; i < tagArr.length; i++) {
                if (i != 0) {
                    idQuery.or();
                }
                idQuery.like("b.tag_name", tagArr[i]);
            }
            idQuery.endParentheses();
        }
        if (!ArrayUtils.isEmpty(types)) {
            idQuery.and().in("a.type", types);
        }
        
        idQuery.orderBy("a.id").offset(context.getStartRow(), context.getPageSize());
        MapperResult idResult = idQuery.build();
        
        // 构建外层查询：获取筛选出的配置的完整标签信息
        final String sql =
            "WITH tag_agg AS ( "
                + "   SELECT id, LISTAGG(DISTINCT tag_name, ',') "
                + "   WITHIN GROUP (ORDER BY tag_name) AS config_tags "
                + "   FROM config_tags_relation GROUP BY id "
                + ") "
                + "SELECT a.id,a.data_id,a.group_id,a.tenant_id,a.app_name,"
                + "       a.content,a.md5,a.encrypted_data_key,a.type,a.c_desc,"
                + "       t.config_tags "
                + "FROM config_info a "
                + "JOIN ("
                + idResult.getSql()
                + ") x ON a.id = x.id "
                + "LEFT JOIN tag_agg t ON a.id = t.id";
        
        return new MapperResult(sql, idResult.getParamList());
    }
    
    @Override
    public String getDataSource() {
        return DataSourceConstant.ORACLE;
    }
}
