/*
 * Copyright 1999-2025 Alibaba Group Holding Ltd.
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
import com.alibaba.nacos.plugin.datasource.mapper.ext.WhereBuilder;
import com.alibaba.nacos.plugin.datasource.model.MapperContext;
import com.alibaba.nacos.plugin.datasource.model.MapperResult;

/**
 * The mapper of ai_resource.
 *
 * @author nacos
 * @since 3.2.0
 */
public interface AiResourceMapper extends Mapper {

    /**
     * Query count rows for ai_resource list.
     *
     * <p>Filters: namespace_id (required), type (optional), name (optional like), biz_tags (optional like).</p>
     */
    default MapperResult findAiResourceCountRows(MapperContext context) {
        WhereBuilder where = new WhereBuilder("SELECT count(*) FROM ai_resource");
        where.eq("namespace_id", context.getWhereParameter(FieldConstant.NAMESPACE_ID));

        Object type = context.getWhereParameter(FieldConstant.TYPE);
        if (type != null && StringUtils.isNotBlank(String.valueOf(type))) {
            where.and().eq("type", type);
        }

        Object name = context.getWhereParameter(FieldConstant.NAME);
        if (name != null && StringUtils.isNotBlank(String.valueOf(name))) {
            where.and().like("name", name);
        }

        Object bizTags = context.getWhereParameter(FieldConstant.BIZ_TAGS);
        if (bizTags != null && StringUtils.isNotBlank(String.valueOf(bizTags))) {
            where.and().like("biz_tags", bizTags);
        }

        return where.build();
    }

    /**
     * Query fetch rows for ai_resource list.
     */
    MapperResult findAiResourceFetchRows(MapperContext context);

    /**
     * Resolve the ORDER BY clause based on the orderBy parameter in the context. Only whitelisted values are accepted to
     * prevent SQL injection.
     *
     * @param context mapper context that may contain an {@link FieldConstant#ORDER_BY} parameter
     * @return SQL ORDER BY clause, e.g. {@code " ORDER BY download_count DESC"} or {@code " ORDER BY gmt_modified DESC"}
     */
    default String resolveOrderByClause(MapperContext context) {
        Object orderBy = context.getWhereParameter(FieldConstant.ORDER_BY);
        if (orderBy != null && FieldConstant.ORDER_BY_DOWNLOAD_COUNT.equals(String.valueOf(orderBy))) {
            return " ORDER BY download_count DESC";
        }
        return " ORDER BY gmt_modified DESC";
    }

    @Override
    default String getTableName() {
        return TableConstant.AI_RESOURCE;
    }
}

