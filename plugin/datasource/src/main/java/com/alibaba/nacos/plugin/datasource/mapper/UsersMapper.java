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
 * The users mapper.
 *
 * @author kuchikij
 */
public interface UsersMapper extends Mapper {

    /**
     * Get the name of table.
     *
     * @return The name of table.
     */
    @Override
    default String getTableName() {
        return TableConstant.USERS;
    }

    /**
     * Get the sql of users count.
     *
     * @param context sql context
     * @return sql config
     */
    default MapperResult getUsersCountRows(MapperContext context) {
        final String userName = (String) context.getWhereParameter(FieldConstant.USER_NAME);

        WhereBuilder where = new WhereBuilder("SELECT count(*) FROM users");

        if (StringUtils.isNotBlank(userName)) {
            where.and().eq("username", userName);
        }

        return where.build();
    }

    /**
     * Get the sql of users fetch.
     *
     * @param context sql context
     * @return sql config
     */
    MapperResult getUsersFetchRows(MapperContext context);

    /**
     * Get the sql of users count.
     *
     * @param context sql context
     * @return sql config
     */
    default MapperResult findUsersLike4PageCountRows(MapperContext context) {
        final String userName = (String) context.getWhereParameter(FieldConstant.USER_NAME);

        WhereBuilder where = new WhereBuilder("SELECT count(*) FROM users");

        if (StringUtils.isNotBlank(userName)) {
            where.and().like("username", userName);
        }

        return where.build();
    }

    /**
     * Get the sql of users fetch.
     *
     * @param context sql context
     * @return sql config
     */
    MapperResult findUsersLike4PageFetchRows(MapperContext context);

    /**
     * Get the sql of find user like username.
     *
     * @param context sql context
     * @return sql config
     */
    default MapperResult findUserLikeUsername(MapperContext context) {
        final String userName = (String) context.getWhereParameter(FieldConstant.USER_NAME);

        WhereBuilder where = new WhereBuilder("SELECT username FROM users");
        where.and().like("username", userName);

        return where.build();
    }

    /**
     * Get the sql of find user by username.
     *
     * @param context sql context
     * @return sql config
     */
    default MapperResult findUserByUsername(MapperContext context) {
        final String userName = (String) context.getWhereParameter(FieldConstant.USER_NAME);

        WhereBuilder where = new WhereBuilder("SELECT username,password FROM users");
        where.and().eq("username", userName);

        return where.build();
    }
}
