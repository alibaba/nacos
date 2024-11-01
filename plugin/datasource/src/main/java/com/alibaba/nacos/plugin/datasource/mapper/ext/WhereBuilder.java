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

import com.alibaba.nacos.common.constant.Symbols;
import com.alibaba.nacos.plugin.datasource.model.MapperResult;

import java.util.ArrayList;
import java.util.List;

/**
 * Where Builder.
 *
 * @author haiqi.wang
 * @date 2024/08/13
 */
public final class WhereBuilder {
    
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
    private final StringBuilder where = new StringBuilder(" WHERE ");
    
    /**
     * Default Construct.
     *
     * @param sql Sql Script
     */
    public WhereBuilder(String sql) {
        this.sql = sql;
    }
    
    /**
     * Build AND.
     *
     * @return Return {@link WhereBuilder}
     */
    public WhereBuilder and() {
        where.append(" AND ");
        return this;
    }
    
    /**
     * Build OR.
     *
     * @return Return {@link WhereBuilder}
     */
    public WhereBuilder or() {
        where.append(" OR ");
        return this;
    }
    
    /**
     * Build Equals.
     *
     * @param filed Filed name
     * @param parameter Parameters
     * @return Return {@link WhereBuilder}
     */
    public WhereBuilder eq(String filed, Object parameter) {
        where.append(filed).append(" = ? ");
        parameters.add(parameter);
        return this;
    }
    
    /**
     * Build LIKE.
     *
     * @param filed Filed name
     * @param parameter Parameters
     * @return Return {@link WhereBuilder}
     */
    public WhereBuilder like(String filed, Object parameter) {
        where.append(filed).append(" LIKE ? ");
        parameters.add(parameter);
        return this;
    }
    
    /**
     * Build IN.
     *
     * @param filed Filed name
     * @param parameterArr Parameters Array
     * @return Return {@link WhereBuilder}
     */
    public WhereBuilder in(String filed, Object[] parameterArr) {
        where.append(filed).append(" IN (");
        for (int i = 0; i < parameterArr.length; i++) {
            if (i != 0) {
                where.append(", ");
            }
            where.append('?');
            parameters.add(parameterArr[i]);
        }
        where.append(") ");
        return this;
    }
    
    /**
     * Build offset.
     *
     * @param startRow Start row
     * @param pageSize Page size
     * @return Return {@link WhereBuilder}
     */
    public WhereBuilder offset(int startRow, int pageSize) {
        where.append(" OFFSET ")
                .append(startRow)
                .append(" ROWS FETCH NEXT ")
                .append(pageSize)
                .append(" ROWS ONLY");
        return this;
    }
    
    /**
     * Build limit.
     *
     * @param startRow Start row
     * @param pageSize Page size
     * @return Return {@link WhereBuilder}
     */
    public WhereBuilder limit(int startRow, int pageSize) {
        where.append(" LIMIT ")
                .append(startRow)
                .append(Symbols.COMMA)
                .append(pageSize);
        return this;
    }
    
    /**
     * Build.
     *
     * @return Return {@link WhereBuilder}
     */
    public MapperResult build() {
        return new MapperResult(sql + where, parameters);
    }
}