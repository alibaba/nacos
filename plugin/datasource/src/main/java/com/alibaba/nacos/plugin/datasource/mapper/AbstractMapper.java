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

import java.util.List;
import java.util.stream.Collectors;

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
        sql.append(columns.stream().collect(Collectors.joining(",")));
        sql.append(" FROM ");
        sql.append(getTableName());

        if (CollectionUtils.isEmpty(where)) {
            return sql.toString();
        }

        appendWhereClause(where, sql);
        return sql.toString();
    }

    @Override
    public String insert(List<String> columns) {
        StringBuilder sql = new StringBuilder();
        String method = "INSERT INTO ";
        sql.append(method);
        sql.append(getTableName());

        int size = columns.size();
        sql.append("(");
        for (int i = 0; i < size; i++) {
            sql.append(columns.get(i).split("@")[0]);
            if (i != columns.size() - 1) {
                sql.append(", ");
            }
        }
        sql.append(") ");

        sql.append("VALUES");
        sql.append("(");
        for (int i = 0; i < size; i++) {
            String[] parts = columns.get(i).split("@");
            if (parts.length == 2) {
                sql.append(getFunction(parts[1]));
            } else {
                sql.append("?");
            }
            if (i != columns.size() - 1) {
                sql.append(",");
            }
        }
        sql.append(")");
        return sql.toString();
    }

    @Override
    public String update(List<String> columns, List<String> where) {
        StringBuilder sql = new StringBuilder();
        String method = "UPDATE ";
        sql.append(method);
        sql.append(getTableName()).append(" ").append("SET ");

        for (int i = 0; i < columns.size(); i++) {
            String[] parts = columns.get(i).split("@");
            String column = parts[0];
            if (parts.length == 2) {
                sql.append(column).append(" = ").append(getFunction(parts[1]));
            } else {
                sql.append(column).append(" = ").append("?");
            }
            if (i != columns.size() - 1) {
                sql.append(",");
            }
        }

        if (CollectionUtils.isEmpty(where)) {
            return sql.toString();
        }

        appendWhereClause(where, sql);

        return sql.toString();
    }

    @Override
    public String delete(List<String> params) {
        StringBuilder sql = new StringBuilder();
        String method = "DELETE ";
        sql.append(method).append("FROM ").append(getTableName());
        appendWhereClause(params, sql);

        return sql.toString();
    }

    @Override
    public String count(List<String> where) {
        StringBuilder sql = new StringBuilder();
        String method = "SELECT ";
        sql.append(method);
        sql.append("COUNT(*) FROM ");
        sql.append(getTableName());

        if (CollectionUtils.isEmpty(where)) {
            return sql.toString();
        }

        appendWhereClause(where, sql);

        return sql.toString();
    }

    @Override
    public String[] getPrimaryKeyGeneratedKeys() {
        return new String[]{"id"};
    }

    private void appendWhereClause(List<String> where, StringBuilder sql) {
        sql.append(" WHERE ");
        sql.append(where.stream().map(str -> (str + " = ?")).collect(Collectors.joining(" AND ")));
    }
}
