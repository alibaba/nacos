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

package com.alibaba.nacos.plugin.datasource.impl.oracle;

import com.alibaba.nacos.common.utils.CollectionUtils;
import com.alibaba.nacos.plugin.datasource.constants.DataSourceConstant;
import com.alibaba.nacos.plugin.datasource.mapper.TenantInfoMapper;

import java.util.List;
import java.util.StringJoiner;
import java.util.stream.Collectors;

/**
 * The oracle implementation of TenantInfoMapper.
 * Override insert/update to convert epoch-millis (bigint) to Oracle timestamp
 * via {@code TIMESTAMP '1970-01-01 00:00:00' + NUMTODSINTERVAL(? / 1000, 'SECOND')}.
 *
 * @author liam.fu
 **/
public class TenantInfoMapperByOracle extends AbstractMapperByOracle implements TenantInfoMapper {
    
    private static final String COLUMN_SEPARATOR = "@";
    
    private static final String TIMESTAMP_VALUE =
        "TIMESTAMP '1970-01-01 00:00:00' + NUMTODSINTERVAL(? / 1000, 'SECOND')";
    
    @Override
    public String getDataSource() {
        return DataSourceConstant.ORACLE;
    }
    
    @Override
    public String insert(List<String> columns) {
        StringJoiner columnJoiner = new StringJoiner(", ", "(", ")");
        StringJoiner valueJoiner = new StringJoiner(",", "(", ")");
        
        for (String col : columns) {
            String[] parts = col.split(COLUMN_SEPARATOR, 2);
            String columnName = parts[0];
            columnJoiner.add(columnName);
            if (parts.length > 1) {
                valueJoiner.add(getFunction(parts[1]));
            } else if (isTimestampColumn(columnName)) {
                valueJoiner.add(TIMESTAMP_VALUE);
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
            String columnName = parts[0];
            String value;
            if (parts.length > 1) {
                value = getFunction(parts[1]);
            } else if (isTimestampColumn(columnName)) {
                value = TIMESTAMP_VALUE;
            } else {
                value = "?";
            }
            setJoiner.add(columnName + " = " + value);
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
