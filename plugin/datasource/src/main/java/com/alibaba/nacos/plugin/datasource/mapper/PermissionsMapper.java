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
import com.alibaba.nacos.plugin.datasource.mapper.ext.WhereBuilder;
import com.alibaba.nacos.plugin.datasource.model.MapperContext;
import com.alibaba.nacos.plugin.datasource.model.MapperResult;

/**
 * The permissions mapper.
 *
 * @author kuchikij
 */
public interface PermissionsMapper extends Mapper {

    /**
     * Get the name of table.
     *
     * @return The name of table.
     */
    @Override
    default String getTableName() {
        return TableConstant.PERMISSIONS;
    }

    /**
     * Get the sql of permissions count.
     *
     * @param context sql context
     * @return sql config
     */
    default MapperResult getPermissionsCountRows(MapperContext context) {
        final String role = (String) context.getWhereParameter(FieldConstant.ROLE);

        WhereBuilder where = new WhereBuilder("SELECT count(*) FROM permissions");

        if (StringUtils.isNotBlank(role)) {
            where.and().eq("role", role);
        }

        return where.build();
    }

    /**
     * Get the sql of permissions fetch.
     *
     * @param context sql context
     * @return sql config
     */
    MapperResult getPermissionsFetchRows(MapperContext context);

    /**
     * Get the sql of permissions count.
     *
     * @param context sql context
     * @return sql config
     */
    default MapperResult findPermissionsLike4PageCountRows(MapperContext context) {
        final String role = (String) context.getWhereParameter(FieldConstant.ROLE);

        WhereBuilder where = new WhereBuilder("SELECT count(*) FROM permissions");

        if (StringUtils.isNotBlank(role)) {
            where.and().like("role", role);
        }

        return where.build();
    }

    /**
     * Get the sql of permissions fetch.
     *
     * @param context sql context
     * @return sql config
     */
    MapperResult findPermissionsLike4PageFetchRows(MapperContext context);
}
