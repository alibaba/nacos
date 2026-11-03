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
 * The mapper of ai_resource_version.
 *
 * @author nacos
 * @since 3.2.0
 */
public interface AiResourceVersionMapper extends Mapper {
    
    /**
     * Query count rows for ai_resource_version list.
     *
     * <p>Filters: namespace_id (required), name (required), type(optional), status(optional), version(optional).</p>
     */
    default MapperResult findAiResourceVersionCountRows(MapperContext context) {
        WhereBuilder where = new WhereBuilder("SELECT count(*) FROM ai_resource_version");
        where.eq("namespace_id", context.getWhereParameter(FieldConstant.NAMESPACE_ID));
        where.and().eq("name", context.getWhereParameter(FieldConstant.NAME));
        
        Object type = context.getWhereParameter(FieldConstant.TYPE);
        if (type != null && StringUtils.isNotBlank(String.valueOf(type))) {
            where.and().eq("type", type);
        }
        
        Object status = context.getWhereParameter(FieldConstant.STATUS);
        if (status != null && StringUtils.isNotBlank(String.valueOf(status))) {
            where.and().eq("status", status);
        }
        
        Object version = context.getWhereParameter(FieldConstant.VERSION);
        if (version != null && StringUtils.isNotBlank(String.valueOf(version))) {
            where.and().eq("version", version);
        }
        
        return where.build();
    }
    
    /**
     * Query fetch rows for ai_resource_version list.
     */
    MapperResult findAiResourceVersionFetchRows(MapperContext context);
    
    @Override
    default String getTableName() {
        return TableConstant.AI_RESOURCE_VERSION;
    }
}
