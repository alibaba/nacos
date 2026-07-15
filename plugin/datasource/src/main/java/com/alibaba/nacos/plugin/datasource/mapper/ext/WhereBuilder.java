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
import java.util.function.Consumer;

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
    
    public WhereBuilder startParentheses() {
        where.append(" ( ");
        return this;
    }
    
    public WhereBuilder endParentheses() {
        where.append(" ) ");
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
     * Build LIKE with escape.
     *
     * @param filed Filed name
     * @param parameter Parameters
     * @return Return {@link WhereBuilder}
     */
    public WhereBuilder likeWithEscape(String filed, Object parameter) {
        where.append(filed).append(" LIKE ? ESCAPE '\\' ");
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
     * Build GROUP BY.
     *
     * @param fields Group by fields
     * @return Return {@link WhereBuilder}
     */
    public WhereBuilder groupBy(String fields) {
        where.append(" GROUP BY ").append(fields);
        return this;
    }
    
    /**
     * Build ORDER BY.
     *
     * @param fields Order by fields
     * @return Return {@link WhereBuilder}
     */
    public WhereBuilder orderBy(String fields) {
        where.append(" ORDER BY ").append(fields);
        return this;
    }
    
    /**
     * Build EXISTS conditional.
     * <p>
     * Used for sub-query filtering. Example:
     * <pre>
     * builder.exists(SELECT 1 FROM tags b WHERE, sub -> {
     * sub.eqColumn("b.id", "a.id").and().like("b.tag", "dev");
     * });
     * </pre>
     *
     * @param subSqlPrefix The prefix of sub-query, usually "SELECT 1 FROM table WHERE "
     * @param consumer     The lambda to build sub-query conditions
     * @return Return {@link WhereBuilder}
     */
    public WhereBuilder exists(String subSqlPrefix, Consumer<WhereBuilder> consumer) {
        WhereBuilder subBuilder = new WhereBuilder("");
        subBuilder.where.setLength(0);
        consumer.accept(subBuilder);
        MapperResult res = subBuilder.build();
        
        where.append(" EXISTS ( ").append(subSqlPrefix).append(res.getSql()).append(" ) ");
        
        if (res.getParamList() != null) {
            parameters.addAll(res.getParamList());
        }
        return this;
    }
    
    /**
     * Build column-to-column equality.
     * <p>
     * Unlike {@link #eq(String, Object)}, this method compares two columns directly without using placeholders (?) and
     * adding parameters.
     *
     * @param field1 The first field name (e.g., "b.id")
     * @param field2 The second field name to compare with (e.g., "a.id")
     * @return Return {@link WhereBuilder}
     */
    public WhereBuilder eqColumn(String field1, String field2) {
        where.append(field1).append(" = ").append(field2).append(" ");
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
