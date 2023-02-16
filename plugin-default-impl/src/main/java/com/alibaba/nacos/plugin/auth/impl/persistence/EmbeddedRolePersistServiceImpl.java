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

import java.util.ArrayList;
import java.util.List;

import com.alibaba.nacos.common.utils.CollectionUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.config.server.configuration.ConditionOnEmbeddedStorage;
import com.alibaba.nacos.config.server.model.Page;
import com.alibaba.nacos.config.server.service.repository.PaginationHelper;
import com.alibaba.nacos.config.server.service.repository.embedded.DatabaseOperate;
import com.alibaba.nacos.config.server.service.repository.embedded.EmbeddedStoragePersistServiceImpl;
import com.alibaba.nacos.config.server.service.sql.EmbeddedStorageContextUtils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import static com.alibaba.nacos.plugin.auth.impl.persistence.AuthRowMapperManager.ROLE_INFO_ROW_MAPPER;

/**
 * There is no self-augmented primary key.
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
@Conditional(value = ConditionOnEmbeddedStorage.class)
@Component
public class EmbeddedRolePersistServiceImpl implements RolePersistService {
    
    @Autowired
    private DatabaseOperate databaseOperate;
    
    @Autowired
    private EmbeddedStoragePersistServiceImpl persistService;

    private static final String PATTERN_STR = "*";

    private static final String SQL_DERBY_ESCAPE_BACK_SLASH_FOR_LIKE = " ESCAPE '\\' ";
    
    @Override
    public Page<RoleInfo> getRoles(int pageNo, int pageSize) {
        
        PaginationHelper<RoleInfo> helper = persistService.createPaginationHelper();
        
        String sqlCountRows = "SELECT count(*) FROM (SELECT DISTINCT role FROM roles) roles WHERE ";
        
        String sqlFetchRows = "SELECT role,username FROM roles WHERE ";
        
        String where = " 1=1 ";
        
        Page<RoleInfo> pageInfo = helper
                .fetchPage(sqlCountRows + where, sqlFetchRows + where, new ArrayList<String>().toArray(), pageNo,
                        pageSize, ROLE_INFO_ROW_MAPPER);
        if (pageInfo == null) {
            pageInfo = new Page<>();
            pageInfo.setTotalCount(0);
            pageInfo.setPageItems(new ArrayList<>());
        }
        return pageInfo;
        
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
        
        return helper.fetchPage(sqlCountRows + where, sqlFetchRows + where, params.toArray(), pageNo, pageSize,
                ROLE_INFO_ROW_MAPPER);
        
    }
    
    /**
     * Add user role.
     *
     * @param role     role string value.
     * @param userName username string value.
     */
    @Override
    public void addRole(String role, String userName) {
        
        String sql = "INSERT INTO roles (role, username) VALUES (?, ?)";
        
        try {
            EmbeddedStorageContextUtils.addSqlContext(sql, role, userName);
            databaseOperate.update(EmbeddedStorageContextUtils.getCurrentSqlContext());
        } finally {
            EmbeddedStorageContextUtils.cleanAllContext();
        }
    }
    
    /**
     * Delete user role.
     *
     * @param role role string value.
     */
    @Override
    public void deleteRole(String role) {
        String sql = "DELETE FROM roles WHERE role=?";
        try {
            EmbeddedStorageContextUtils.addSqlContext(sql, role);
            databaseOperate.update(EmbeddedStorageContextUtils.getCurrentSqlContext());
        } finally {
            EmbeddedStorageContextUtils.cleanAllContext();
        }
    }
    
    /**
     * Execute delete role sql operation.
     *
     * @param role     role string value.
     * @param username user string value.
     */
    @Override
    public void deleteRole(String role, String username) {
        String sql = "DELETE FROM roles WHERE role=? AND username=?";
        try {
            EmbeddedStorageContextUtils.addSqlContext(sql, role, username);
            databaseOperate.update(EmbeddedStorageContextUtils.getCurrentSqlContext());
        } finally {
            EmbeddedStorageContextUtils.cleanAllContext();
        }
    }
    
    @Override
    public List<String> findRolesLikeRoleName(String role) {
        String sql = "SELECT role FROM roles WHERE role LIKE ? " + SQL_DERBY_ESCAPE_BACK_SLASH_FOR_LIKE;
        return databaseOperate.queryMany(sql, new String[] {"%" + role + "%"}, String.class);
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
        if (CollectionUtils.isNotEmpty(params)) {
            where.append(SQL_DERBY_ESCAPE_BACK_SLASH_FOR_LIKE);
        }
        String sqlCountRows = "SELECT count(*) FROM roles";
        String sqlFetchRows = "SELECT role, username FROM roles";

        PaginationHelper<RoleInfo> helper = persistService.createPaginationHelper();
        return helper.fetchPage(sqlCountRows + where, sqlFetchRows + where, params.toArray(), pageNo, pageSize,
                ROLE_INFO_ROW_MAPPER);
    }

}
