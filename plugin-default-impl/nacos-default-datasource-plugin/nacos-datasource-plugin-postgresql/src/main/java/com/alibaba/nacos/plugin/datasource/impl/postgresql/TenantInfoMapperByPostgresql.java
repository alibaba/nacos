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

package com.alibaba.nacos.plugin.datasource.impl.postgresql;

import com.alibaba.nacos.common.utils.CollectionUtils;
import com.alibaba.nacos.plugin.datasource.constants.DatabaseTypeConstant;
import com.alibaba.nacos.plugin.datasource.impl.base.BaseTenantInfoMapper;

import java.util.List;
import java.util.StringJoiner;
import java.util.stream.Collectors;

/**
 * The postgresql implementation of TenantInfoMapper.
 * Override insert/update to convert epoch-millis (bigint) to PostgreSQL timestamp
 * via {@code TO_TIMESTAMP(? / 1000.0)}.
 *
 * @author Long Yu
 **/
public class TenantInfoMapperByPostgresql extends BaseTenantInfoMapper {
    
    private static final String COLUMN_SEPARATOR = "@";
    
    @Override
    public String getDataSource() {
        return DatabaseTypeConstant.POSTGRESQL;
    }
    
    @Override
    public String insert(List<String> columns) {
        StringJoiner columnJoiner = new StringJoiner(", ", "(", ")");
        StringJoiner valueJoiner = new StringJoiner(",", "(", ")");
        
        for (String col : columns) {
            String[] parts = col.split(COLUMN_SEPARATOR, 2);
            String colName = parts[0];
            columnJoiner.add(colName);
            if (parts.length > 1) {
                valueJoiner.add(getFunction(parts[1]));
            } else if (isTimestampColumn(colName)) {
                valueJoiner.add("TO_TIMESTAMP(? / 1000.0)");
            } else {
                valueJoiner.add("?");
            }
        }
        
        return "INSERT INTO " + getTableName() + columnJoiner + " VALUES" + valueJoiner;
    }
    
    @Override
    public String update(List<String> columns, List<String> where) {
        StringJoiner setJoiner = new StringJoiner(",");
        for (String col : columns) {
            String[] parts = col.split(COLUMN_SEPARATOR, 2);
            String colName = parts[0];
            String value;
            if (parts.length > 1) {
                value = getFunction(parts[1]);
            } else if (isTimestampColumn(colName)) {
                value = "TO_TIMESTAMP(? / 1000.0)";
            } else {
                value = "?";
            }
            setJoiner.add(colName + " = " + value);
        }
        
        StringBuilder sql =
            new StringBuilder("UPDATE ").append(getTableName()).append(" SET ").append(setJoiner);
        
        if (CollectionUtils.isNotEmpty(where)) {
            sql.append(" WHERE ");
            sql.append(
                where.stream().map(str -> (str + " = ?")).collect(Collectors.joining(" AND ")));
        }
        
        return sql.toString();
    }
    
    private boolean isTimestampColumn(String columnName) {
        return "gmt_create".equals(columnName) || "gmt_modified".equals(columnName);
    }
    
}
