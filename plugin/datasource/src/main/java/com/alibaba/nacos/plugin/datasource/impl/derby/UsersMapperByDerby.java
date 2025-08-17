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

package com.alibaba.nacos.plugin.datasource.impl.derby;

import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.plugin.datasource.constants.DataSourceConstant;
import com.alibaba.nacos.plugin.datasource.constants.FieldConstant;
import com.alibaba.nacos.plugin.datasource.mapper.UsersMapper;
import com.alibaba.nacos.plugin.datasource.mapper.ext.WhereBuilder;
import com.alibaba.nacos.plugin.datasource.model.MapperContext;
import com.alibaba.nacos.plugin.datasource.model.MapperResult;

/**
 * The derby implementation of UsersMapper.
 *
 * @author kuchikij
 */
public class UsersMapperByDerby extends AbstractMapperByDerby implements UsersMapper {

    @Override
    public String getDataSource() {
        return DataSourceConstant.DERBY;
    }

    @Override
    public MapperResult getUsersFetchRows(MapperContext context) {
        final String userName = (String) context.getWhereParameter(FieldConstant.USER_NAME);

        WhereBuilder where = new WhereBuilder("SELECT username,password FROM users");

        if (StringUtils.isNotBlank(userName)) {
            where.and().eq("username", userName);
        }

        where.offset(context.getStartRow(), context.getPageSize());
        return where.build();
    }

    @Override
    public MapperResult findUsersLike4PageFetchRows(MapperContext context) {
        final String userName = (String) context.getWhereParameter(FieldConstant.USER_NAME);

        WhereBuilder where = new WhereBuilder("SELECT username,password FROM users");

        if (StringUtils.isNotBlank(userName)) {
            where.and().like("username", userName);
        }

        where.offset(context.getStartRow(), context.getPageSize());
        return where.build();
    }
}
