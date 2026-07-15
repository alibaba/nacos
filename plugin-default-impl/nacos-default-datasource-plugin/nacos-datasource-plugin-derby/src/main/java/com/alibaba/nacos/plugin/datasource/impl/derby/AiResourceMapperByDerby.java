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

package com.alibaba.nacos.plugin.datasource.impl.derby;

import com.alibaba.nacos.plugin.datasource.constants.DataSourceConstant;
import com.alibaba.nacos.plugin.datasource.constants.FieldConstant;
import com.alibaba.nacos.plugin.datasource.mapper.AiResourceMapper;
import com.alibaba.nacos.plugin.datasource.mapper.ext.WhereBuilder;
import com.alibaba.nacos.plugin.datasource.model.MapperContext;
import com.alibaba.nacos.plugin.datasource.model.MapperResult;

import java.util.List;

/**
 * The derby implementation of {@link AiResourceMapper}.
 *
 * @author nacos
 */
public class AiResourceMapperByDerby extends AbstractMapperByDerby implements AiResourceMapper {
    
    @Override
    public MapperResult findAiResourceFetchRows(MapperContext context) {
        WhereBuilder where = new WhereBuilder(
            "SELECT id,gmt_create,gmt_modified,name,type,c_desc,status,namespace_id,"
                + "biz_tags,ext,c_from,version_info,meta_version,scope,owner,download_count "
                + "FROM ai_resource");
        where.eq("namespace_id", context.getWhereParameter(FieldConstant.NAMESPACE_ID));
        
        appendExtraQueryCondition(where, context);
        
        MapperResult built = where.build();
        String sql =
            built.getSql() + resolveOrderByClause(context) + " OFFSET " + context.getStartRow()
                + " ROWS FETCH NEXT " + context.getPageSize() + " ROWS ONLY";
        return new MapperResult(sql, built.getParamList());
    }
    
    @Override
    public void appendSingleAndCondition(WhereBuilder where, String field, Object value,
        boolean likeMatch) {
        if (field == null || value == null) {
            return;
        }
        if (value instanceof List) {
            if (((List<?>) value).isEmpty()) {
                return;
            }
            where.and().in(field, ((List<?>) value).toArray());
            return;
        }
        if (likeMatch) {
            where.and().likeWithEscape(field, value);
        } else {
            where.and().eq(field, value);
        }
    }
    
    @Override
    public String getDataSource() {
        return DataSourceConstant.DERBY;
    }
}
