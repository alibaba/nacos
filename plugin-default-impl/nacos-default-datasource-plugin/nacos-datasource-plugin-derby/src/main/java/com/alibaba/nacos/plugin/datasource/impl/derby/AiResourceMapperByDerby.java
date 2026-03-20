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

import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.plugin.datasource.constants.DataSourceConstant;
import com.alibaba.nacos.plugin.datasource.constants.FieldConstant;
import com.alibaba.nacos.plugin.datasource.mapper.AiResourceMapper;
import com.alibaba.nacos.plugin.datasource.mapper.ext.WhereBuilder;
import com.alibaba.nacos.plugin.datasource.model.MapperContext;
import com.alibaba.nacos.plugin.datasource.model.MapperResult;

/**
 * The derby implementation of {@link AiResourceMapper}.
 *
 * @author nacos
 */
public class AiResourceMapperByDerby extends AbstractMapperByDerby implements AiResourceMapper {

    @Override
    public MapperResult findAiResourceFetchRows(MapperContext context) {
        WhereBuilder where = new WhereBuilder(
                "SELECT id,gmt_create,gmt_modified,name,type,c_desc,status,namespace_id,biz_tags,ext,version_info,meta_version,scope,owner "
                        + "FROM ai_resource");
        where.eq("namespace_id", context.getWhereParameter(FieldConstant.NAMESPACE_ID));

        Object type = context.getWhereParameter(FieldConstant.TYPE);
        if (type != null && StringUtils.isNotBlank(String.valueOf(type))) {
            where.and().eq("type", type);
        }
        Object name = context.getWhereParameter(FieldConstant.NAME);
        if (name != null && StringUtils.isNotBlank(String.valueOf(name))) {
            where.and().likeWithEscape("name", name);
        }
        Object bizTags = context.getWhereParameter(FieldConstant.BIZ_TAGS);
        if (bizTags != null && StringUtils.isNotBlank(String.valueOf(bizTags))) {
            where.and().likeWithEscape("biz_tags", bizTags);
        }

        MapperResult built = where.build();
        String sql = built.getSql() + " ORDER BY gmt_modified DESC OFFSET " + context.getStartRow()
                + " ROWS FETCH NEXT " + context.getPageSize() + " ROWS ONLY";
        return new MapperResult(sql, built.getParamList());
    }

    @Override
    public String getDataSource() {
        return DataSourceConstant.DERBY;
    }
}

