/*
 * Copyright 1999-2021 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.plugin.auth.impl.persistence;

import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.config.server.configuration.ConditionOnExternalStorage;
import com.alibaba.nacos.config.server.model.Page;
import com.alibaba.nacos.config.server.service.repository.PaginationHelper;
import com.alibaba.nacos.config.server.service.repository.extrnal.ExternalStoragePersistServiceImpl;
import com.alibaba.nacos.config.server.utils.LogUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import static com.alibaba.nacos.plugin.auth.impl.persistence.AuthRowMapperManager.ROLE_INFO_ROW_MAPPER;

/**
 * Implemetation of ExternalRolePersistServiceImpl.
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
@Conditional(value = ConditionOnExternalStorage.class)
@Component
public class ExternalRolePersistServiceImpl implements RolePersistService {
    
    @Autowired
    private ExternalStoragePersistServiceImpl persistService;
    
    private JdbcTemplate jt;

    private static final String PATTERN_STR = "*";
    
    @PostConstruct
    protected void init() {
        jt = persistService.getJdbcTemplate();
    }
    
    @Override
    public Page<RoleInfo> getRoles(int pageNo, int pageSize) {
        
        PaginationHelper<RoleInfo> helper = persistService.createPaginationHelper();
        
        String sqlCountRows = "SELECT count(*) FROM (SELECT DISTINCT role FROM roles) roles WHERE ";
        
        String sqlFetchRows = "SELECT role,username FROM roles WHERE ";
        
        String where = " 1=1 ";
        
        try {
            Page<RoleInfo> pageInfo = helper
                    .fetchPage(sqlCountRows + where, sqlFetchRows + where, new ArrayList<String>().toArray(), pageNo,
                            pageSize, ROLE_INFO_ROW_MAPPER);
            if (pageInfo == null) {
                pageInfo = new Page<>();
                pageInfo.setTotalCount(0);
                pageInfo.setPageItems(new ArrayList<>());
            }
            return pageInfo;
        } catch (CannotGetJdbcConnectionException e) {
            LogUtil.FATAL_LOG.error("[db-error] " + e.toString(), e);
            throw e;
        }
    }
    
    @Override
    public Page<RoleInfo> getRolesByUserNameAndRoleName(String username, String role, int pageNo, int pageSize) {
        
        PaginationHelper<RoleInfo> helper = persistService.createPaginationHelper();
        
        String sqlCountRows = "SELECT count(*) FROM roles ";
        
        String sqlFetchRows = "SELECT role,username FROM roles ";

        StringBuilder where = new StringBuilder(" WHERE 1 = 1 ");
        List<String> params = new ArrayList<>();
        if (StringUtils.isNotBlank(username)) {
            where.append(" AND username = ? ");
            params.add(username);
        }
        if (StringUtils.isNotBlank(role)) {
            where.append(" AND role = ? ");
            params.add(role);
        }
        
        try {
            return helper.fetchPage(sqlCountRows + where, sqlFetchRows + where, params.toArray(), pageNo, pageSize,
                    ROLE_INFO_ROW_MAPPER);
        } catch (CannotGetJdbcConnectionException e) {
            LogUtil.FATAL_LOG.error("[db-error] " + e.toString(), e);
            throw e;
        }
    }
    
    /**
     * Execute add role operation.
     *
     * @param role     role string value.
     * @param userName username string value.
     */
    @Override
    public void addRole(String role, String userName) {
        
        String sql = "INSERT INTO roles (role, username) VALUES (?, ?)";
        
        try {
            jt.update(sql, role, userName);
        } catch (CannotGetJdbcConnectionException e) {
            LogUtil.FATAL_LOG.error("[db-error] " + e.toString(), e);
            throw e;
        }
    }
    
    /**
     * Execute delete role operation.
     *
     * @param role role string value.
     */
    @Override
    public void deleteRole(String role) {
        String sql = "DELETE FROM roles WHERE role=?";
        try {
            jt.update(sql, role);
        } catch (CannotGetJdbcConnectionException e) {
            LogUtil.FATAL_LOG.error("[db-error] " + e.toString(), e);
            throw e;
        }
    }
    
    /**
     * Execute delete role operation.
     *
     * @param role     role string value.
     * @param username username string value.
     */
    @Override
    public void deleteRole(String role, String username) {
        String sql = "DELETE FROM roles WHERE role=? AND username=?";
        try {
            jt.update(sql, role, username);
        } catch (CannotGetJdbcConnectionException e) {
            LogUtil.FATAL_LOG.error("[db-error] " + e.toString(), e);
            throw e;
        }
    }
    
    @Override
    public List<String> findRolesLikeRoleName(String role) {
        String sql = "SELECT role FROM roles WHERE role LIKE ?";
        List<String> users = this.jt.queryForList(sql, new String[] {String.format("%%%s%%", role)}, String.class);
        return users;
    }

    @Override
    public String generateLikeArgument(String s) {
        String underscore = "_";
        if (s.contains(underscore)) {
            s = s.replaceAll(underscore, "\\\\_");
        }
        String fuzzySearchSign = "\\*";
        String sqlLikePercentSign = "%";
        if (s.contains(PATTERN_STR)) {
            return s.replaceAll(fuzzySearchSign, sqlLikePercentSign);
        } else {
            return s;
        }
    }

    @Override
    public Page<RoleInfo> findRolesLike4Page(String username, String role, int pageNo, int pageSize) {
        String sqlCountRows = "SELECT count(*) FROM roles";
        String sqlFetchRows = "SELECT role, username FROM roles";
        StringBuilder where = new StringBuilder(" WHERE 1 = 1 ");
        List<String> params = new ArrayList<>();

        if (StringUtils.isNotBlank(username)) {
            where.append(" AND username LIKE ? ");
            params.add(generateLikeArgument(username));
        }
        if (StringUtils.isNotBlank(role)) {
            where.append(" AND role LIKE ? ");
            params.add(generateLikeArgument(role));
        }

        PaginationHelper<RoleInfo> helper = persistService.createPaginationHelper();
        try {
            return helper.fetchPage(sqlCountRows + where, sqlFetchRows + where, params.toArray(), pageNo, pageSize,
                    ROLE_INFO_ROW_MAPPER);
        } catch (CannotGetJdbcConnectionException e) {
            LogUtil.FATAL_LOG.error("[db-error] " + e.toString(), e);
            throw e;
        }
    }

    private static final class RoleInfoRowMapper implements RowMapper<RoleInfo> {
        
        @Override
        public RoleInfo mapRow(ResultSet rs, int rowNum) throws SQLException {
            RoleInfo roleInfo = new RoleInfo();
            roleInfo.setRole(rs.getString("role"));
            roleInfo.setUsername(rs.getString("username"));
            return roleInfo;
        }
    }
}
