/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.plugin.datasource.mapper.ext;

import com.alibaba.nacos.plugin.datasource.model.MapperResult;
import java.util.ArrayList;
import java.util.List;

/**
 * Where Builder.
 *
 * @author kuchikij
 */
public abstract class AbstractWhereBuilder<T extends AbstractWhereBuilder<T>> {

    /**
     * Base sql.
     */
    private final String sql;

    /**
     * Parameters.
     */
    private final List<Object> parameters = new ArrayList<>();

    /**
     * Where Conditional.
     */
    private final StringBuilder where = new StringBuilder();

    /**
     * Paging Conditional.
     */
    protected final StringBuilder paging = new StringBuilder();

    /**
     * Default Construct.
     *
     * @param sql Sql Script
     */
    protected AbstractWhereBuilder(String sql) {
        this.sql = sql;
    }

    /**
     * Build AND.
     *
     * @return Return {@link AbstractWhereBuilder}
     */
    public T and() {
        if (where.length() > 0) {
            where.append(" AND ");
        }
        return (T) this;
    }

    /**
     * Build OR.
     *
     * @return Return {@link AbstractWhereBuilder}
     */
    public T or() {
        if (where.length() > 0) {
            where.append(" OR ");
        }
        return (T) this;
    }

    /**
     * Build Equals.
     *
     * @param filed     Filed name
     * @param parameter Parameters
     * @return Return {@link AbstractWhereBuilder}
     */
    public T eq(String filed, Object parameter) {
        where.append(filed).append(" = ? ");
        parameters.add(parameter);
        return (T) this;
    }

    /**
     * Build LIKE.
     *
     * @param filed     Filed name
     * @param parameter Parameters
     * @return Return {@link AbstractWhereBuilder}
     */
    public T like(String filed, Object parameter) {
        where.append(filed).append(" LIKE ? ");
        parameters.add(parameter);
        return (T) this;
    }

    /**
     * Build IN.
     *
     * @param filed        Filed name
     * @param parameterArr Parameters Array
     * @return Return {@link AbstractWhereBuilder}
     */
    public T in(String filed, Object[] parameterArr) {
        where.append(filed).append(" IN (");
        for (int i = 0; i < parameterArr.length; i++) {
            if (i != 0) {
                where.append(", ");
            }
            where.append('?');
            parameters.add(parameterArr[i]);
        }
        where.append(") ");
        return (T) this;
    }

    /**
     * Build.
     *
     * @return Return {@link AbstractWhereBuilder}
     */
    public MapperResult build() {
        String lastSql = sql;
        if (where.length() > 0) {
            lastSql += " WHERE " + where;
        }
        if (paging.length() > 0) {
            lastSql += " " + paging;
        }
        return new MapperResult(lastSql, parameters);
    }
}