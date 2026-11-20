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
import com.alibaba.nacos.plugin.auth.impl.persistence.PermissionInfo;
import com.alibaba.nacos.plugin.auth.impl.persistence.PermissionPersistService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.CannotGetJdbcConnectionException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.alibaba.nacos.plugin.auth.impl.persistence.AuthRowMapperManager.PERMISSION_ROW_MAPPER;

/**
 * Oracle adapted permission persist service. {@code resource} is a reserved word on Oracle, so
 * the physical column is named {@code resources} and must be selected with a quoted
 * {@code "resource"} alias; the alias keeps {@code PermissionRowMapper} reading by the same
 * column label on every database. The SQL is otherwise a port of the default
 * {@code ExternalPermissionPersistServiceImpl}, so the default auth plugin stays untouched and
 * this service takes it over via a primary bean definition.
 *
 * @author liam
 */
public class OraclePermissionPersistServiceImpl extends AbstractOracleAuthPersistService
    implements PermissionPersistService {
    
    private static final Logger LOGGER = LoggerFactory.getLogger("com.alibaba.nacos.persistence");
    
    @Override
    public Page<PermissionInfo> getPermissions(String role, int pageNo, int pageSize) {
        AuthPaginationHelper<PermissionInfo> helper = createPaginationHelper();
        
        String sqlCountRows = "SELECT count(*) FROM permissions WHERE ";
        String sqlFetchRows =
            "SELECT role,resources AS \"resource\",action FROM permissions WHERE ";
        
        String where = " role= ? ";
        List<String> params = new ArrayList<>();
        if (StringUtils.isNotBlank(role)) {
            params = Collections.singletonList(role);
        } else {
            where = " 1=1 ";
        }
        try {
            Page<PermissionInfo> pageInfo =
                helper.fetchPage(sqlCountRows + where, sqlFetchRows + where, params.toArray(),
                    pageNo, pageSize, PERMISSION_ROW_MAPPER);
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
    
    /**
     * Execute add permission operation.
     *
     * @param role     role string value.
     * @param resource resource string value.
     * @param action   action string value.
     */
    @Override
    public void addPermission(String role, String resource, String action) {
        String sql = "INSERT INTO permissions (role, resources, action) VALUES (?, ?, ?)";
        try {
            jt.update(sql, role, resource, action);
        } catch (CannotGetJdbcConnectionException e) {
            LOGGER.error("[db-error] " + e.toString(), e);
            throw e;
        }
    }
    
    /**
     * Execute delete permission operation.
     *
     * @param role     role string value.
     * @param resource resource string value.
     * @param action   action string value.
     */
    @Override
    public void deletePermission(String role, String resource, String action) {
        String sql = "DELETE FROM permissions WHERE role=? AND resources=? AND action=?";
        try {
            jt.update(sql, role, resource, action);
        } catch (CannotGetJdbcConnectionException e) {
            LOGGER.error("[db-error] " + e.toString(), e);
            throw e;
        }
    }
    
    @Override
    public Page<PermissionInfo> findPermissionsLike4Page(String role, int pageNo, int pageSize) {
        AuthPaginationHelper<PermissionInfo> helper = createPaginationHelper();
        
        String sqlCountRows = "SELECT count(*) FROM permissions ";
        String sqlFetchRows = "SELECT role,resources AS \"resource\",action FROM permissions ";
        
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<String> params = new ArrayList<>();
        if (StringUtils.isNotBlank(role)) {
            where.append(" AND role LIKE ?");
            where.append(SQL_ORACLE_ESCAPE_BACK_SLASH_FOR_LIKE);
            params.add(generateLikeArgument(role));
        }
        try {
            Page<PermissionInfo> pageInfo =
                helper.fetchPage(sqlCountRows + where, sqlFetchRows + where, params.toArray(),
                    pageNo, pageSize, PERMISSION_ROW_MAPPER);
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
}
