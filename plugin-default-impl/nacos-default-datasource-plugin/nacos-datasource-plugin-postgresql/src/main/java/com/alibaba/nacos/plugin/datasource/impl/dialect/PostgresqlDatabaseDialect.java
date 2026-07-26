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

package com.alibaba.nacos.plugin.datasource.impl.dialect;

import com.alibaba.nacos.plugin.datasource.constants.DatabaseTypeConstant;
import com.alibaba.nacos.plugin.datasource.impl.enums.postgresql.TrustedPostgresqlFunctionEnum;

import java.sql.SQLException;

/**
 * PostgreSQL database dialect.
 *
 * @author xiweng.yy
 */
public class PostgresqlDatabaseDialect extends AbstractDatabaseDialect {
    
    /**
     * PostgreSQL SQLState for {@code unique_violation}. The driver raises it when an insert breaks
     * a unique or primary key constraint, even in cases where Spring's exception translation cannot
     * classify the failure as a {@code DuplicateKeyException}.
     */
    private static final String UNIQUE_VIOLATION_SQL_STATE = "23505";
    
    @Override
    public String getType() {
        return DatabaseTypeConstant.POSTGRESQL;
    }
    
    @Override
    public boolean isDuplicateKeyException(Throwable throwable) {
        if (super.isDuplicateKeyException(throwable)) {
            return true;
        }
        Throwable cause = throwable;
        while (cause != null) {
            if (cause instanceof SQLException
                && UNIQUE_VIOLATION_SQL_STATE.equals(((SQLException) cause).getSQLState())) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }
    
    @Override
    public String getFunction(String functionName) {
        return TrustedPostgresqlFunctionEnum.getFunctionByName(functionName);
    }
    
    @Override
    public String getLimitPageSqlWithMark(String sql) {
        return sql + " OFFSET ? LIMIT ? ";
    }
    
    @Override
    public String getLimitPageSql(String sql, int pageNo, int pageSize) {
        return sql + "  OFFSET " + getPagePrevNum(pageNo, pageSize) + " LIMIT " + pageSize;
    }
    
    @Override
    public String getLimitPageSqlWithOffset(String sql, int startOffset, int pageSize) {
        return sql + "  OFFSET " + startOffset + " LIMIT " + pageSize;
    }
}
