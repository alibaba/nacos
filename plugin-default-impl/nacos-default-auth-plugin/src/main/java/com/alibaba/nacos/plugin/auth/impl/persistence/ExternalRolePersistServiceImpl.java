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

import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.common.utils.CollectionUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.config.server.utils.LogUtil;
import com.alibaba.nacos.persistence.configuration.condition.ConditionOnExternalStorage;
import com.alibaba.nacos.persistence.datasource.DataSourceService;
import com.alibaba.nacos.persistence.datasource.DynamicDataSource;
import com.alibaba.nacos.persistence.model.Page;
import com.alibaba.nacos.plugin.auth.constant.ActionTypes;
import com.alibaba.nacos.plugin.auth.impl.constant.AuthConstants;
import com.alibaba.nacos.plugin.auth.impl.persistence.extrnal.AuthExternalPaginationHelperImpl;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

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
    private PermissionPersistService permissionPersistService;
    
    private JdbcTemplate jt;
    
    private String dataSourceType = "";
    
    private static final String PATTERN_STR = "*";
    
    @PostConstruct
    protected void init() {
        DataSourceService dataSource = DynamicDataSource.getInstance().getDataSource();
        jt = dataSource.getJdbcTemplate();
        dataSourceType = dataSource.getDataSourceType();
    }
    
    @Override
    public Page<RoleInfo> getRoles(int pageNo, int pageSize) {
        
        AuthPaginationHelper<RoleInfo> helper = createPaginationHelper();
        
        String sqlCountRows = "SELECT count(*) FROM (SELECT DISTINCT role FROM roles) roles WHERE ";
        
        String sqlFetchRows = "SELECT role,username FROM roles WHERE ";
        
        String where = " 1=1 ";
        
        try {
            Page<RoleInfo> pageInfo = helper.fetchPage(sqlCountRows + where, sqlFetchRows + where,
                    new ArrayList<String>().toArray(), pageNo, pageSize, ROLE_INFO_ROW_MAPPER);
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
        
        AuthPaginationHelper<RoleInfo> helper = createPaginationHelper();
        
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
    
    @Override
    public Page<RoleMapInfo> getRoleMapByUserNameAndRoleName(String username, String role, int pageNo, int pageSize) {
        String sqlCountRows = "SELECT count(*) FROM roles ";
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
        
        Integer count = jt.queryForObject(sqlCountRows + where, params.toArray(), Integer.class);
        if (null == count || 0 == count) {
            return null;
        }
        Page<RoleMapInfo> page = new Page<>();
        page.setTotalCount(count);
        page.setPageNumber(pageNo);
        int pageCount = count / pageSize;
        if (count > pageSize * pageCount) {
            pageCount++;
        }
        page.setPagesAvailable(pageCount);
        if (pageNo > pageCount) {
            return page;
        }
        int startRow = (pageNo - 1) * pageSize;
        String sqlSelectRole = "select DISTINCT role from roles " + where + " limit " + startRow + "," + pageSize;
        List<String> roleString = jt.queryForList(sqlSelectRole, params.toArray(), String.class);
        MapSqlParameterSource parameterSource = new MapSqlParameterSource();
        parameterSource.addValue("roleString", roleString);
        String sqlRole = "select role,username from roles where role in (:roleString)";
        NamedParameterJdbcTemplate namedParameterJdbcTemplate = new NamedParameterJdbcTemplate(
                Objects.requireNonNull(this.jt.getDataSource()));
        List<RoleInfo> roleInfos = namedParameterJdbcTemplate.query(sqlRole, parameterSource, ROLE_INFO_ROW_MAPPER);
        Map<String, List<RoleInfo>> collect = roleInfos.stream().collect(Collectors.groupingBy(RoleInfo::getRole));
        List<RoleMapInfo> roleMapInfos = Lists.newArrayList();
        for (String roleName : roleString) {
            RoleMapInfo mapInfo = new RoleMapInfo();
            mapInfo.setRole(roleName);
            List<String> userNames = collect.get(roleName).stream().map(RoleInfo::getUsername)
                    .collect(Collectors.toList());
            mapInfo.setUsernames(userNames);
            roleMapInfos.add(mapInfo);
        }
        page.setPageItems(roleMapInfos);
        return page;
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
        
        AuthPaginationHelper<RoleInfo> helper = createPaginationHelper();
        try {
            return helper.fetchPage(sqlCountRows + where, sqlFetchRows + where, params.toArray(), pageNo, pageSize,
                    ROLE_INFO_ROW_MAPPER);
        } catch (CannotGetJdbcConnectionException e) {
            LogUtil.FATAL_LOG.error("[db-error] " + e.toString(), e);
            throw e;
        }
    }
    
    @Override
    public Page<RoleMapInfo> findRoleMapLike4Page(String username, String role, int pageNo, int pageSize) {
        String sqlCountRows = "SELECT count(*) FROM roles ";
        StringBuilder where = new StringBuilder(" WHERE 1 = 1 ");
        List<String> params = new ArrayList<>();
        if (StringUtils.isNotBlank(username)) {
            where.append(" AND username like ? ");
            params.add(username);
        }
        if (StringUtils.isNotBlank(role)) {
            where.append(" AND role like ? ");
            params.add(role);
        }
    
        Integer count = jt.queryForObject(sqlCountRows + where, params.toArray(), Integer.class);
        if (null == count || 0 == count) {
            return null;
        }
        Page<RoleMapInfo> page = new Page<>();
        page.setTotalCount(count);
        page.setPageNumber(pageNo);
        int pageCount = count / pageSize;
        if (count > pageSize * pageCount) {
            pageCount++;
        }
        page.setPagesAvailable(pageCount);
        if (pageNo > pageCount) {
            return page;
        }
        int startRow = (pageNo - 1) * pageSize;
        String sqlSelectRole = "select DISTINCT role from roles where " + where + " limit " + startRow + "," + pageSize;
        List<String> roleString = jt.queryForList(sqlSelectRole, params.toArray(), String.class);
        MapSqlParameterSource parameterSource = new MapSqlParameterSource();
        parameterSource.addValue("roleString", roleString);
        String sqlRole = "select role,username from roles where role in (:roleString)";
        NamedParameterJdbcTemplate namedParameterJdbcTemplate = new NamedParameterJdbcTemplate(
                Objects.requireNonNull(this.jt.getDataSource()));
        List<RoleInfo> roleInfos = namedParameterJdbcTemplate.query(sqlRole, parameterSource, ROLE_INFO_ROW_MAPPER);
        Map<String, List<RoleInfo>> collect = roleInfos.stream().collect(Collectors.groupingBy(RoleInfo::getRole));
        List<RoleMapInfo> roleMapInfos = Lists.newArrayList();
        for (String roleName : roleString) {
            RoleMapInfo mapInfo = new RoleMapInfo();
            mapInfo.setRole(roleName);
            List<String> userNames = collect.get(roleName).stream().map(RoleInfo::getUsername)
                    .collect(Collectors.toList());
            mapInfo.setUsernames(userNames);
            roleMapInfos.add(mapInfo);
        }
        page.setPageItems(roleMapInfos);
        return page;
    
    }
    
    @Override
    public <E> AuthPaginationHelper<E> createPaginationHelper() {
        return new AuthExternalPaginationHelperImpl<>(jt, dataSourceType);
    }
    
    @Override
    public Map<String, Set<String>> getAppPermissions(String userName) {
        Page<RoleInfo> pageRoles = getRolesByUserNameAndRoleName(userName, null, 1, Integer.MAX_VALUE);
        if (null == pageRoles || CollectionUtils.isEmpty(pageRoles.getPageItems())) {
            return Maps.newHashMap();
        }
        List<RoleInfo> roleInfos = pageRoles.getPageItems();
        Map<String, Set<String>> appPermissionMap = Maps.newHashMap();
        for (RoleInfo roleInfo : roleInfos) {
            if (StringUtils.equals(roleInfo.getRole(), AuthConstants.GLOBAL_ADMIN_ROLE)) {
                appPermissionMap.put(Constants.ALL_PATTERN, Sets.newHashSet(ActionTypes.READ_WRITE.toString()));
            }
            Page<PermissionInfo> permissionInfoPage = permissionPersistService.getPermissions(roleInfo.getRole(), 1,
                    Integer.MAX_VALUE);
            if (null == permissionInfoPage || CollectionUtils.isEmpty(permissionInfoPage.getPageItems())) {
                continue;
            }
            for (PermissionInfo permissionInfo : permissionInfoPage.getPageItems()) {
                Set<String> permissions = appPermissionMap.computeIfAbsent(permissionInfo.getAppName(),
                        key -> Sets.newHashSet());
                permissions.add(permissionInfo.getAction());
            }
        }
        return appPermissionMap;
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
