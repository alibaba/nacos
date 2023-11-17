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

package com.alibaba.nacos.plugin.datasource.dialects;

/**
 * Database pagination statement assembly interface.
 *
 * @author huangKeMing
 */
public interface IDialect {
    
    /**
     * These are of no particular significance, just to make classes easier to use and to distinguish between parameters
     * for paginated SQL.
     */
    String FIRST_MARK = "?";
    String SECOND_MARK = "?";
    
    String COMMA = ",";
    
    /**
     * Assemble the pagination statement.
     *
     * @param sqlFetchRows Original statement.
     * @param args parameters.
     * @param pageNo Start pagination.
     * @param pageSize The size of the pagination.
     * @return
     */
    DialectModel buildPaginationSql(String sqlFetchRows, Object[] args, int pageNo, int pageSize);
}
