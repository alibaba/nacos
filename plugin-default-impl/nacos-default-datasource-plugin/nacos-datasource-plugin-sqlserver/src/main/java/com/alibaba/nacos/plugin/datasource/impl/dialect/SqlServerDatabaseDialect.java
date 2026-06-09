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
import com.alibaba.nacos.plugin.datasource.impl.enums.sqlserver.TrustedSqlServerFunctionEnum;

/**
 * Microsoft SQL Server database dialect.
 *
 * @author ThinkGem
 */
public class SqlServerDatabaseDialect extends AbstractDatabaseDialect {
    
    @Override
    public String getType() {
        return DatabaseTypeConstant.SQLSERVER;
    }
    
    @Override
    public String getFunction(String functionName) {
        return TrustedSqlServerFunctionEnum.getFunctionByName(functionName);
    }
    
    /**
     * Get the TopN query SQL with parameter marker.
     *
     * @param sql the original SQL
     * @return the SQL with TopN limit
     */
    @Override
    public String getLimitTopSqlWithMark(String sql) {
        return sql + " ORDER BY id OFFSET 0 ROWS FETCH NEXT ? ROWS ONLY ";
    }
    
    /**
     * Get the pagination query SQL with parameter markers.
     *
     * @param sql the original SQL
     * @return the SQL with pagination parameters
     */
    @Override
    public String getLimitPageSqlWithMark(String sql) {
        return sql + " ORDER BY id OFFSET ? ROWS FETCH NEXT ? ROWS ONLY ";
    }
    
    /**
     * Get the pagination query SQL.
     *
     * @param sql the original SQL
     * @param pageNo the page number (starting from 1)
     * @param pageSize the page size
     * @return the SQL with pagination
     */
    @Override
    public String getLimitPageSql(String sql, int pageNo, int pageSize) {
        return sql + " ORDER BY id OFFSET " + getPagePrevNum(pageNo, pageSize)
                + " ROWS FETCH NEXT " + pageSize + " ROWS ONLY ";
    }
    
    /**
     * Get the pagination query SQL with start offset.
     *
     * @param sql the original SQL
     * @param startOffset the start offset
     * @param pageSize the page size
     * @return the SQL with pagination
     */
    @Override
    public String getLimitPageSqlWithOffset(String sql, int startOffset, int pageSize) {
        return sql + " ORDER BY id OFFSET " + startOffset + " ROWS FETCH NEXT "
                + pageSize + " ROWS ONLY ";
    }
}
