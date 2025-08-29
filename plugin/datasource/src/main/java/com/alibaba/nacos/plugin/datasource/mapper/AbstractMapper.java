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
import java.util.StringJoiner;
import java.util.stream.Collectors;

/**
 * The abstract mapper contains CRUD methods.
 *
 * @author hyx
 **/

public abstract class AbstractMapper implements Mapper {

    private static final String COLUMN_SEPARATOR = "@";

    @Override
    public String select(List<String> columns, List<String> where) {
        StringBuilder sql = new StringBuilder();
        String method = "SELECT ";
        sql.append(method);
        sql.append(String.join(",", columns));
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
        StringJoiner columnJoiner = new StringJoiner(", ", "(", ")");
        StringJoiner valueJoiner = new StringJoiner(",", "(", ")");

        for (String col : columns) {
            String[] parts = col.split(COLUMN_SEPARATOR, 2);
            columnJoiner.add(parts[0]);
            valueJoiner.add(parts.length > 1 ? getFunction(parts[1]) : "?");
        }

        return "INSERT INTO " + getTableName() + columnJoiner + " VALUES" + valueJoiner;
    }

    @Override
    public String update(List<String> columns, List<String> where) {
        StringJoiner setJoiner = new StringJoiner(",");
        for (String col : columns) {
            String[] parts = col.split(COLUMN_SEPARATOR, 2);
            String value = parts.length > 1 ? getFunction(parts[1]) : "?";
            setJoiner.add(parts[0] + " = " + value);
        }

        StringBuilder sql = new StringBuilder("UPDATE ")
                .append(getTableName())
                .append(" SET ")
                .append(setJoiner);

        if (CollectionUtils.isNotEmpty(where)) {
            appendWhereClause(where, sql);
        }

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
