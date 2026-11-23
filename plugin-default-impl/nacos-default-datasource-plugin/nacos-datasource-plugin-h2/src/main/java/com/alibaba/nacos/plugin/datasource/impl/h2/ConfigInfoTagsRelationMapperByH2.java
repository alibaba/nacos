/*
 * Copyright 1999-2026 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.plugin.datasource.impl.h2;

import com.alibaba.nacos.common.utils.ArrayUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.plugin.datasource.constants.DataSourceConstant;
import com.alibaba.nacos.plugin.datasource.constants.FieldConstant;
import com.alibaba.nacos.plugin.datasource.mapper.ConfigTagsRelationMapper;
import com.alibaba.nacos.plugin.datasource.mapper.ext.WhereBuilder;
import com.alibaba.nacos.plugin.datasource.model.MapperContext;
import com.alibaba.nacos.plugin.datasource.model.MapperResult;

/**
 * The h2 implementation of ConfigTagsRelationMapper.
 *
 * <p>A configuration is matched with an {@code EXISTS} sub-query on its tags instead of a join. A join returns one row
 * for each matching tag, so a configuration that matches two tags would be listed twice and counted twice. The count
 * and the page use the same conditions, and every page holds distinct configurations.
 *
 * @author hyx
 **/

public class ConfigInfoTagsRelationMapperByH2 extends AbstractMapperByH2
    implements ConfigTagsRelationMapper {
    
    private static final String TAG_SUB_QUERY = "SELECT 1 FROM config_tags_relation b WHERE ";
    
    private static final String SELECT_EXACT_PAGE =
        "SELECT a.id,a.data_id,a.group_id,a.tenant_id,a.app_name,"
            + "a.content,a.md5,a.type,a.encrypted_data_key,a.c_desc FROM config_info a";
    
    private static final String SELECT_LIKE_PAGE =
        "SELECT a.ID,a.data_id,a.group_id,a.tenant_id,a.app_name,"
            + "a.content,a.md5,a.encrypted_data_key,a.type,a.c_desc FROM config_info a";
    
    private static final String COUNT_CONFIG = "SELECT count(*) FROM config_info a";
    
    @Override
    public MapperResult findConfigInfo4PageCountRows(MapperContext context) {
        return exactSearch(COUNT_CONFIG, context).build();
    }
    
    @Override
    public MapperResult findConfigInfo4PageFetchRows(MapperContext context) {
        return exactSearch(SELECT_EXACT_PAGE, context).orderBy("a.id")
            .offset(context.getStartRow(), context.getPageSize()).build();
    }
    
    @Override
    public MapperResult findConfigInfoLike4PageCountRows(MapperContext context) {
        return likeSearch(COUNT_CONFIG, context).build();
    }
    
    @Override
    public MapperResult findConfigInfoLike4PageFetchRows(MapperContext context) {
        return likeSearch(SELECT_LIKE_PAGE, context).orderBy("a.id")
            .offset(context.getStartRow(), context.getPageSize()).build();
    }
    
    @Override
    public String getDataSource() {
        return DataSourceConstant.H2;
    }
    
    /**
     * Exact search: the tenant, the optional exact fields, and a configuration that has one of the requested tags.
     *
     * @param select  the select clause and the FROM clause of the query
     * @param context the query parameters
     * @return the conditions, without ordering or paging
     */
    private WhereBuilder exactSearch(String select, MapperContext context) {
        final String appName = (String) context.getWhereParameter(FieldConstant.APP_NAME);
        final String dataId = (String) context.getWhereParameter(FieldConstant.DATA_ID);
        final String group = (String) context.getWhereParameter(FieldConstant.GROUP_ID);
        final String content = (String) context.getWhereParameter(FieldConstant.CONTENT);
        final String tenantId = (String) context.getWhereParameter(FieldConstant.TENANT_ID);
        final String[] tagArr = (String[]) context.getWhereParameter(FieldConstant.TAG_ARR);
        WhereBuilder where = new WhereBuilder(select);
        where.eq("a.tenant_id", tenantId);
        if (StringUtils.isNotBlank(dataId)) {
            where.and().eq("a.data_id", dataId);
        }
        if (StringUtils.isNotBlank(group)) {
            where.and().eq("a.group_id", group);
        }
        if (StringUtils.isNotBlank(appName)) {
            where.and().eq("a.app_name", appName);
        }
        if (StringUtils.isNotBlank(content)) {
            where.and().likeWithEscape("a.content", content);
        }
        if (!ArrayUtils.isEmpty(tagArr)) {
            where.and().exists(TAG_SUB_QUERY,
                sub -> sub.eqColumn("b.id", "a.id").and().in("b.tag_name", tagArr));
        }
        return where;
    }
    
    /**
     * Fuzzy search: the optional fuzzy fields, a configuration that has a tag matching one of the requested patterns,
     * and the requested types.
     *
     * @param select  the select clause and the FROM clause of the query
     * @param context the query parameters
     * @return the conditions, without ordering or paging
     */
    private WhereBuilder likeSearch(String select, MapperContext context) {
        final String appName = (String) context.getWhereParameter(FieldConstant.APP_NAME);
        final String dataId = (String) context.getWhereParameter(FieldConstant.DATA_ID);
        final String group = (String) context.getWhereParameter(FieldConstant.GROUP_ID);
        final String content = (String) context.getWhereParameter(FieldConstant.CONTENT);
        final String tenantId = (String) context.getWhereParameter(FieldConstant.TENANT_ID);
        final String[] tagArr = (String[]) context.getWhereParameter(FieldConstant.TAG_ARR);
        final String[] types = (String[]) context.getWhereParameter(FieldConstant.TYPE);
        WhereBuilder where = new WhereBuilder(select);
        where.likeWithEscape("a.tenant_id", tenantId);
        if (StringUtils.isNotBlank(dataId)) {
            where.and().likeWithEscape("a.data_id", dataId);
        }
        if (StringUtils.isNotBlank(group)) {
            where.and().likeWithEscape("a.group_id", group);
        }
        if (StringUtils.isNotBlank(appName)) {
            where.and().eq("a.app_name", appName);
        }
        if (StringUtils.isNotBlank(content)) {
            where.and().likeWithEscape("a.content", content);
        }
        if (!ArrayUtils.isEmpty(tagArr)) {
            where.and().exists(TAG_SUB_QUERY, sub -> appendLikeTags(sub, tagArr));
        }
        if (!ArrayUtils.isEmpty(types)) {
            where.and().in("a.type", types);
        }
        return where;
    }
    
    private static void appendLikeTags(WhereBuilder sub, String[] tagArr) {
        sub.eqColumn("b.id", "a.id").and().startParentheses();
        for (int i = 0; i < tagArr.length; i++) {
            if (i != 0) {
                sub.or();
            }
            sub.likeWithEscape("b.tag_name", tagArr[i]);
        }
        sub.endParentheses();
    }
}
