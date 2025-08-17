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

package com.alibaba.nacos.plugin.datasource.impl.mysql;

import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.plugin.datasource.constants.DataSourceConstant;
import com.alibaba.nacos.plugin.datasource.constants.FieldConstant;
import com.alibaba.nacos.plugin.datasource.mapper.RolesMapper;
import com.alibaba.nacos.plugin.datasource.mapper.ext.WhereBuilder;
import com.alibaba.nacos.plugin.datasource.model.MapperContext;
import com.alibaba.nacos.plugin.datasource.model.MapperResult;

/**
 * The mysql implementation of RolesMapper.
 *
 * @author kuchikij
 */
public class RolesMapperByMySql extends AbstractMapperByMysql implements RolesMapper {

    @Override
    public String getDataSource() {
        return DataSourceConstant.MYSQL;
    }

    @Override
    public MapperResult getRolesFetchRows(MapperContext context) {
        WhereBuilder where = new WhereBuilder("SELECT role, username FROM roles");
        where.limit(context.getStartRow(), context.getPageSize());
        return where.build();
    }

    @Override
    public MapperResult getRolesByUserNameAndRoleNameFetchRows(MapperContext context) {
        final String userName = (String) context.getWhereParameter(FieldConstant.USER_NAME);
        final String role = (String) context.getWhereParameter(FieldConstant.ROLE);

        WhereBuilder where = new WhereBuilder("SELECT role, username FROM roles");

        if (StringUtils.isNotBlank(userName)) {
            where.and().eq("username", userName);
        }
        if (StringUtils.isNotBlank(role)) {
            where.and().eq("role", role);
        }

        where.limit(context.getStartRow(), context.getPageSize());
        return where.build();
    }

    @Override
    public MapperResult findRolesLike4PageFetchRows(MapperContext context) {
        final String userName = (String) context.getWhereParameter(FieldConstant.USER_NAME);
        final String role = (String) context.getWhereParameter(FieldConstant.ROLE);

        WhereBuilder where = new WhereBuilder("SELECT role, username FROM roles");

        if (StringUtils.isNotBlank(userName)) {
            where.and().like("username", userName);
        }
        if (StringUtils.isNotBlank(role)) {
            where.and().like("role", role);
        }

        where.limit(context.getStartRow(), context.getPageSize());
        return where.build();
    }
}
