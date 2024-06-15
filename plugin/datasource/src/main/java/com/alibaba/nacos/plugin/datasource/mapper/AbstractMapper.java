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

import com.alibaba.nacos.common.utils.CollectionUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.plugin.datasource.model.ColumnFunctionPair;

import java.util.List;

/**
 * The abstract mapper contains CRUD methods.
 *
 * @author hyx
 **/

public abstract class AbstractMapper implements Mapper {
    
    @Override
    public String select(List<String> columns, List<String> where) {
        StringBuilder sql = new StringBuilder();
        String method = "SELECT ";
        sql.append(method);
        for (int i = 0; i < columns.size(); i++) {
            sql.append(columns.get(i));
            if (i == columns.size() - 1) {
                sql.append(" ");
            } else {
                sql.append(",");
            }
        }
        sql.append("FROM ");
        sql.append(getTableName());
        sql.append(" ");
        
        if (CollectionUtils.isEmpty(where)) {
            return sql.toString();
        }
        
        appendWhereClause(where, sql);
        return sql.toString();
    }

    @Override
    public String insert(List<ColumnFunctionPair> columnFunctionPairs) {
        if (columnFunctionPairs == null || columnFunctionPairs.isEmpty()) {
            throw new IllegalArgumentException("The columnFunctionPairs list is null or empty.");
        }

        StringBuilder columns = new StringBuilder();
        StringBuilder values = new StringBuilder();

        // Iterate and validate each ColumnFunctionPair object, building column names and ?/functions
        for (int i = 0; i < columnFunctionPairs.size(); i++) {
            ColumnFunctionPair pair = columnFunctionPairs.get(i);

            if (pair == null || StringUtils.isBlank(pair.getColumn())) {
                throw new IllegalArgumentException("The column is blank, creating SQL error.");
            }

            columns.append(pair.getColumn());
            values.append(StringUtils.isBlank(pair.getFunction()) ? "?" : pair.getFunction());

            if (i != columnFunctionPairs.size() - 1) {
                columns.append(", ");
                values.append(", ");
            }
        }

        // Construct SQL
        return "INSERT INTO " + getTableName() + " ("
                + columns + ") VALUES ("
                + values + ")";
    }

    @Override
    public String update(List<ColumnFunctionPair> columnFunctionPairs, List<String> where) {
        if (columnFunctionPairs == null || columnFunctionPairs.isEmpty()) {
            throw new IllegalArgumentException("The columnFunctionPairs list is null or empty.");
        }

        StringBuilder sql = new StringBuilder();
        String method = "UPDATE ";
        sql.append(method);
        sql.append(getTableName()).append(" ").append("SET ");

        for (int i = 0; i < columnFunctionPairs.size(); i++) {
            ColumnFunctionPair pair = columnFunctionPairs.get(i);
            if (pair == null || StringUtils.isBlank(pair.getColumn())) {
                throw new IllegalArgumentException("The column is blank, creating SQL error.");
            }
            sql.append(pair.getColumn()).append(" = ")
                    .append(StringUtils.isBlank(pair.getFunction()) ? "?" : pair.getFunction());
            if (i != columnFunctionPairs.size() - 1) {
                sql.append(", ");
            }
        }

        if (CollectionUtils.isEmpty(where)) {
            return sql.toString();
        }

        sql.append(" ");
        appendWhereClause(where, sql);

        return sql.toString();
    }

    @Override
    public String delete(List<String> params) {
        StringBuilder sql = new StringBuilder();
        String method = "DELETE ";
        sql.append(method).append("FROM ").append(getTableName()).append(" ").append("WHERE ");
        for (int i = 0; i < params.size(); i++) {
            sql.append(params.get(i)).append(" ").append("=").append(" ? ");
            if (i != params.size() - 1) {
                sql.append("AND ");
            }
        }
        
        return sql.toString();
    }
    
    @Override
    public String count(List<String> where) {
        StringBuilder sql = new StringBuilder();
        String method = "SELECT ";
        sql.append(method);
        sql.append("COUNT(*) FROM ");
        sql.append(getTableName());
        sql.append(" ");
        
        if (null == where || where.size() == 0) {
            return sql.toString();
        }
        
        appendWhereClause(where, sql);
        
        return sql.toString();
    }
    
    @Override
    public String[] getPrimaryKeyGeneratedKeys() {
        return new String[] {"id"};
    }
    
    private void appendWhereClause(List<String> where, StringBuilder sql) {
        sql.append("WHERE ");
        for (int i = 0; i < where.size(); i++) {
            sql.append(where.get(i)).append(" = ").append("?");
            if (i != where.size() - 1) {
                sql.append(" AND ");
            }
        }
    }
}
