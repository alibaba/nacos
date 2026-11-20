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

package com.alibaba.nacos.plugin.auth.impl.oracle.persistence;

import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.plugin.auth.impl.persistence.AuthPaginationHelper;
import com.alibaba.nacos.plugin.auth.impl.persistence.User;
import com.alibaba.nacos.plugin.auth.impl.persistence.UserPersistService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.CannotGetJdbcConnectionException;

import java.util.ArrayList;
import java.util.List;

import static com.alibaba.nacos.plugin.auth.impl.persistence.AuthRowMapperManager.USER_ROW_MAPPER;

/**
 * Oracle adapted user persist service. The {@code users} table has no Oracle reserved word
 * conflicts, so the SQL is a port of the default {@code ExternalUserPersistServiceImpl}; the
 * service exists because the default pagination helper resolves its paging syntax through a
 * static factory without an Oracle adapter and would silently run unpaginated SQL on Oracle.
 *
 * @author liam
 */
public class OracleUserPersistServiceImpl extends AbstractOracleAuthPersistService
    implements UserPersistService {
    
    private static final Logger LOGGER = LoggerFactory.getLogger("com.alibaba.nacos.persistence");
    
    /**
     * Execute create user operation.
     *
     * @param username username string value.
     * @param password password string value.
     */
    @Override
    public void createUser(String username, String password) {
        String sql = "INSERT INTO users (username, password, enabled) VALUES (?, ?, ?)";
        try {
            jt.update(sql, username, password, true);
        } catch (CannotGetJdbcConnectionException e) {
            LOGGER.error("[db-error] " + e.toString(), e);
            throw e;
        }
    }
    
    /**
     * Execute delete user operation.
     *
     * @param username username string value.
     */
    @Override
    public void deleteUser(String username) {
        String sql = "DELETE FROM users WHERE username=?";
        try {
            jt.update(sql, username);
        } catch (CannotGetJdbcConnectionException e) {
            LOGGER.error("[db-error] " + e.toString(), e);
            throw e;
        }
    }
    
    /**
     * Execute update user password operation.
     *
     * @param username username string value.
     * @param password password string value.
     */
    @Override
    public void updateUserPassword(String username, String password) {
        try {
            jt.update("UPDATE users SET password = ? WHERE username=?", password, username);
        } catch (CannotGetJdbcConnectionException e) {
            LOGGER.error("[db-error] " + e.toString(), e);
            throw e;
        }
    }
    
    /**
     * Execute find user by username operation.
     *
     * @param username username string value.
     * @return User model.
     */
    @Override
    public User findUserByUsername(String username) {
        String sql = "SELECT username,password FROM users WHERE username=? ";
        try {
            return jt.queryForObject(sql, new Object[] {username}, USER_ROW_MAPPER);
        } catch (CannotGetJdbcConnectionException e) {
            LOGGER.error("[db-error] " + e.toString(), e);
            throw e;
        } catch (EmptyResultDataAccessException e) {
            return null;
        } catch (Exception e) {
            LOGGER.error("[db-other-error]" + e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }
    
    @Override
    public Page<User> getUsers(int pageNo, int pageSize, String username) {
        AuthPaginationHelper<User> helper = createPaginationHelper();
        
        String sqlCountRows = "SELECT count(*) FROM users ";
        String sqlFetchRows = "SELECT username,password FROM users ";
        
        StringBuilder where = new StringBuilder(" WHERE 1 = 1 ");
        List<String> params = new ArrayList<>();
        if (StringUtils.isNotBlank(username)) {
            where.append(" AND username = ? ");
            params.add(username);
        }
        
        try {
            Page<User> pageInfo =
                helper.fetchPage(sqlCountRows + where, sqlFetchRows + where, params.toArray(),
                    pageNo, pageSize, USER_ROW_MAPPER);
            if (pageInfo == null) {
                pageInfo = new Page<>();
                pageInfo.setTotalCount(0);
                pageInfo.setPageItems(new ArrayList<>());
            }
            return pageInfo;
        } catch (CannotGetJdbcConnectionException e) {
            LOGGER.error("[db-error] " + e.toString(), e);
            throw e;
        }
    }
    
    @Override
    public List<String> findUserLikeUsername(String username) {
        String sql = "SELECT username FROM users WHERE username LIKE ?";
        return jt.queryForList(sql,
            new String[] {String.format("%%%s%%", generateLikeArgument(username))}, String.class);
    }
    
    @Override
    public Page<User> findUsersLike4Page(String username, int pageNo, int pageSize) {
        String sqlCountRows = "SELECT count(*) FROM users ";
        String sqlFetchRows = "SELECT username,password FROM users ";
        
        StringBuilder where = new StringBuilder(" WHERE 1 = 1 ");
        List<String> params = new ArrayList<>();
        if (StringUtils.isNotBlank(username)) {
            where.append(" AND username LIKE ?");
            where.append(SQL_ORACLE_ESCAPE_BACK_SLASH_FOR_LIKE);
            params.add(generateLikeArgument(username));
        }
        
        AuthPaginationHelper<User> helper = createPaginationHelper();
        try {
            return helper.fetchPage(sqlCountRows + where, sqlFetchRows + where, params.toArray(),
                pageNo, pageSize, USER_ROW_MAPPER);
        } catch (CannotGetJdbcConnectionException e) {
            LOGGER.error("[db-error] " + e.toString(), e);
            throw e;
        }
    }
}
