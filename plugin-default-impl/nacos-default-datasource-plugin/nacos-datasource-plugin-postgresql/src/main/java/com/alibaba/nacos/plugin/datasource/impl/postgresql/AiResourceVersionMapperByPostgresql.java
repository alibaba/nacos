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

package com.alibaba.nacos.plugin.datasource.impl.postgresql;

import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.plugin.datasource.constants.DatabaseTypeConstant;
import com.alibaba.nacos.plugin.datasource.constants.FieldConstant;
import com.alibaba.nacos.plugin.datasource.impl.enums.postgresql.TrustedPostgresqlFunctionEnum;
import com.alibaba.nacos.plugin.datasource.mapper.AbstractMapper;
import com.alibaba.nacos.plugin.datasource.mapper.AiResourceVersionMapper;
import com.alibaba.nacos.plugin.datasource.mapper.ext.WhereBuilder;
import com.alibaba.nacos.plugin.datasource.model.MapperContext;
import com.alibaba.nacos.plugin.datasource.model.MapperResult;

/**
 * The postgresql implementation of {@link AiResourceVersionMapper}.
 *
 * @author nacos
 */
public class AiResourceVersionMapperByPostgresql extends AbstractMapper
    implements AiResourceVersionMapper {
    
    @Override
    public MapperResult findAiResourceVersionFetchRows(MapperContext context) {
        WhereBuilder where = new WhereBuilder(
            "SELECT id,gmt_create,gmt_modified,type,author,name,c_desc,status,version,namespace_id,storage,publish_pipeline_info,download_count "
                + "FROM ai_resource_version");
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
        
        MapperResult built = where.build();
        String sql = built.getSql() + " ORDER BY gmt_modified DESC LIMIT " + context.getPageSize()
            + " OFFSET "
            + context.getStartRow();
        return new MapperResult(sql, built.getParamList());
    }
    
    @Override
    public String getDataSource() {
        return DatabaseTypeConstant.POSTGRESQL;
    }
    
    @Override
    public String getFunction(String functionName) {
        return TrustedPostgresqlFunctionEnum.getFunctionByName(functionName);
    }
}
