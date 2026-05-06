/*
 * Copyright 1999-2023 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.persistence.repository.embedded.sql.limiter;

import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.persistence.repository.embedded.sql.ModifyRequest;
import com.alibaba.nacos.persistence.repository.embedded.sql.SelectRequest;
import com.alibaba.nacos.sys.env.EnvUtil;

import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * SQL Type Limiter, Nacos only allow `INSERT`, `UPDATE`, `DELETE`, `SELECT`, `CREATE SCHEMA`, `CREATE TABLE`, `CREATE
 * INDEX` and `ALTER TABLE`.
 *
 * @author xiweng.yy
 */
public class SqlTypeLimiter implements SqlLimiter {
    
    private static final String ENABLED_SQL_LIMIT = "nacos.persistence.sql.derby.limit.enabled";
    
    private final Set<String> allowedDmlSqls;
    
    private final Set<String> allowedDdlSqls;
    
    private final Set<String> allowedDdlScopes;
    
    private final boolean enabledLimit;
    
    public SqlTypeLimiter() {
        this.enabledLimit = EnvUtil.getProperty(ENABLED_SQL_LIMIT, Boolean.class, true);
        this.allowedDmlSqls = new HashSet<>(4);
        this.allowedDmlSqls.add("INSERT");
        this.allowedDmlSqls.add("UPDATE");
        this.allowedDmlSqls.add("DELETE");
        this.allowedDmlSqls.add("SELECT");
        this.allowedDdlSqls = new HashSet<>(2);
        this.allowedDdlSqls.add("CREATE");
        this.allowedDdlSqls.add("ALTER");
        this.allowedDdlScopes = new HashSet<>(3);
        this.allowedDdlScopes.add("SCHEMA");
        this.allowedDdlScopes.add("TABLE");
        this.allowedDdlScopes.add("INDEX");
    }
    
    @Override
    public void doLimitForModifyRequest(ModifyRequest modifyRequest) throws SQLException {
        if (null == modifyRequest || !enabledLimit) {
            return;
        }
        doLimit(modifyRequest.getSql());
    }
    
    @Override
    public void doLimitForModifyRequest(List<ModifyRequest> modifyRequests) throws SQLException {
        if (null == modifyRequests || !enabledLimit) {
            return;
        }
        for (ModifyRequest each : modifyRequests) {
            doLimitForModifyRequest(each);
        }
    }
    
    @Override
    public void doLimitForSelectRequest(SelectRequest selectRequest) throws SQLException {
        if (null == selectRequest || !enabledLimit) {
            return;
        }
        doLimit(selectRequest.getSql());
    }
    
    @Override
    public void doLimitForSelectRequest(List<SelectRequest> selectRequests) throws SQLException {
        if (null == selectRequests || !enabledLimit) {
            return;
        }
        for (SelectRequest each : selectRequests) {
            doLimitForSelectRequest(each);
        }
    }
    
    @Override
    public void doLimit(String sql) throws SQLException {
        if (!enabledLimit) {
            return;
        }
        String trimmedSql = sql.trim();
        if (StringUtils.isEmpty(trimmedSql)) {
            return;
        }
        int firstTokenIndex = trimmedSql.indexOf(" ");
        if (-1 == firstTokenIndex) {
            throwException(trimmedSql);
        }
        String firstToken = trimmedSql.substring(0, firstTokenIndex).toUpperCase();
        if (allowedDmlSqls.contains(firstToken)) {
            return;
        }
        if (!allowedDdlSqls.contains(firstToken)) {
            throwException(trimmedSql);
        }
        checkSqlForSecondToken(firstTokenIndex, trimmedSql);
    }
    
    @Override
    public void doLimit(List<String> sql) throws SQLException {
        if (null == sql || !enabledLimit) {
            return;
        }
        for (String each : sql) {
            doLimit(each);
        }
    }
    
    private void throwException(String sql) throws SQLException {
        throw new SQLException(String.format("Unsupported SQL: %s. Nacos only support DML and some DDL SQL.", sql));
    }
    
    private void checkSqlForSecondToken(int firstTokenIndex, String trimmedSql) throws SQLException {
        int secondTokenIndex = trimmedSql.indexOf(" ", firstTokenIndex + 1);
        if (-1 == secondTokenIndex) {
            secondTokenIndex = trimmedSql.length();
        }
        String secondToken = trimmedSql.substring(firstTokenIndex + 1, secondTokenIndex).toUpperCase();
        if (!allowedDdlScopes.contains(secondToken)) {
            throwException(trimmedSql);
        }
    }
}
