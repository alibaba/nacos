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
import com.alibaba.nacos.config.server.configuration.ConditionOnEmbeddedStorage;
import com.alibaba.nacos.config.server.model.Page;
import com.alibaba.nacos.config.server.service.repository.PaginationHelper;
import com.alibaba.nacos.config.server.service.repository.embedded.DatabaseOperate;
import com.alibaba.nacos.config.server.service.repository.embedded.EmbeddedStoragePersistServiceImpl;
import com.alibaba.nacos.config.server.service.sql.EmbeddedStorageContextUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.alibaba.nacos.plugin.auth.impl.persistence.AuthRowMapperManager.PERMISSION_ROW_MAPPER;

/**
 * There is no self-augmented primary key.
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
@Conditional(value = ConditionOnEmbeddedStorage.class)
@Component
public class EmbeddedPermissionPersistServiceImpl implements PermissionPersistService {
    
    @Autowired
    private DatabaseOperate databaseOperate;
    
    @Autowired
    private EmbeddedStoragePersistServiceImpl persistService;

    private static final String PATTERN_STR = "*";

    private static final String SQL_DERBY_ESCAPE_BACK_SLASH_FOR_LIKE = " ESCAPE '\\' ";
    
    @Override
    public Page<PermissionInfo> getPermissions(String role, int pageNo, int pageSize) {
        PaginationHelper<PermissionInfo> helper = persistService.createPaginationHelper();
        
        String sqlCountRows = "SELECT count(*) FROM permissions WHERE ";
        
        String sqlFetchRows = "SELECT role,resource,action FROM permissions WHERE ";
        
        String where = " role= ? ";
        List<String> params = new ArrayList<>();
        if (StringUtils.isNotBlank(role)) {
            params = Collections.singletonList(role);
        } else {
            where = " 1=1 ";
        }
        
        Page<PermissionInfo> pageInfo = helper
                .fetchPage(sqlCountRows + where, sqlFetchRows + where, params.toArray(), pageNo, pageSize,
                        PERMISSION_ROW_MAPPER);
        
        if (pageInfo == null) {
            pageInfo = new Page<>();
            pageInfo.setTotalCount(0);
            pageInfo.setPageItems(new ArrayList<>());
        }
        return pageInfo;
    }
    
    /**
     * Execute ddd user permission operation.
     *
     * @param role     role info string value.
     * @param resource resource info string value.
     * @param action   action info string value.
     */
    @Override
    public void addPermission(String role, String resource, String action) {
        String sql = "INSERT INTO permissions (role, resource, action) VALUES (?, ?, ?)";
        EmbeddedStorageContextUtils.addSqlContext(sql, role, resource, action);
        databaseOperate.blockUpdate();
    }
    
    /**
     * Execute delete user permission operation.
     *
     * @param role     role info string value.
     * @param resource resource info string value.
     * @param action   action info string value.
     */
    @Override
    public void deletePermission(String role, String resource, String action) {
        String sql = "DELETE FROM permissions WHERE role=? AND resource=? AND action=?";
        EmbeddedStorageContextUtils.addSqlContext(sql, role, resource, action);
        databaseOperate.blockUpdate();
    }

    @Override
    public Page<PermissionInfo> findPermissionsLike4Page(String role, int pageNo, int pageSize) {
        PaginationHelper<PermissionInfo> helper = persistService.createPaginationHelper();

        String sqlCountRows = "SELECT count(*) FROM permissions ";

        String sqlFetchRows = "SELECT role,resource,action FROM permissions ";

        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<String> params = new ArrayList<>();
        if (StringUtils.isNotBlank(role)) {
            where.append(" AND role LIKE ?");
            where.append(SQL_DERBY_ESCAPE_BACK_SLASH_FOR_LIKE);
            params.add(generateLikeArgument(role));
        }

        Page<PermissionInfo> pageInfo = helper
                .fetchPage(sqlCountRows + where, sqlFetchRows + where, params.toArray(), pageNo, pageSize,
                        PERMISSION_ROW_MAPPER);

        if (pageInfo == null) {
            pageInfo = new Page<>();
            pageInfo.setTotalCount(0);
            pageInfo.setPageItems(new ArrayList<>());
        }
        return pageInfo;
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

}
