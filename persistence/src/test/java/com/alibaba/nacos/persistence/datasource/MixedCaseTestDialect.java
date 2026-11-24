/*
 * Copyright 1999-2026 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.persistence.datasource;

import com.alibaba.nacos.plugin.datasource.dialect.DatabaseDialect;

/**
 * A third-party dialect that declares its type in mixed case, like a plugin written before Nacos lower-cased the
 * datasource platform. It is registered through {@code src/test/resources/META-INF/services}.
 */
public class MixedCaseTestDialect implements DatabaseDialect {
    
    public static final String TYPE = "AcmeDB";
    
    public static final String DRIVER = "org.h2.Driver";
    
    @Override
    public String getType() {
        return TYPE;
    }
    
    @Override
    public int getPagePrevNum(int page, int pageSize) {
        return 0;
    }
    
    @Override
    public int getPageLastNum(int page, int pageSize) {
        return 0;
    }
    
    @Override
    public String getLimitTopSqlWithMark(String sql) {
        return sql;
    }
    
    @Override
    public String getLimitPageSqlWithMark(String sql) {
        return sql;
    }
    
    @Override
    public String getLimitPageSql(String sql, int pageNo, int pageSize) {
        return sql;
    }
    
    @Override
    public String getLimitPageSqlWithOffset(String sql, int startOffset, int pageSize) {
        return sql;
    }
    
    @Override
    public String[] getReturnPrimaryKeys() {
        return new String[] {"id"};
    }
    
    @Override
    public String getFunction(String functionName) {
        return functionName;
    }
    
    @Override
    public String getDefaultDriverClassName() {
        return DRIVER;
    }
}
