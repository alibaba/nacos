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
package com.alibaba.nacos.config.server.auth;


import com.alibaba.nacos.config.server.model.Page;
import com.alibaba.nacos.config.server.model.User;
import com.alibaba.nacos.config.server.service.PersistService;
import com.alibaba.nacos.config.server.utils.PaginationHelper;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;

import static com.alibaba.nacos.config.server.utils.LogUtil.fatalLog;

/**
 * Permission CRUD service
 *
 * @author nkorange
 * @since 1.2.0
 */
@Service
public class PermissionPersistService extends PersistService {


    public Page<Permission> getPermissions(String role, int pageNo, int pageSize) {
        PaginationHelper<Permission> helper = new PaginationHelper<>();

        String sqlCountRows = "select count(*) from permissions where ";
        String sqlFetchRows
            = "select distinct role from roles where ";

        String where = " role='" + role + "' ";

        try {
            return helper.fetchPage(jt, sqlCountRows
                    + where, sqlFetchRows + where, new ArrayList<String>().toArray(), pageNo,
                pageSize, PERMISSION_ROW_MAPPER);
        } catch (CannotGetJdbcConnectionException e) {
            fatalLog.error("[db-error] " + e.toString(), e);
            throw e;
        }
    }

    public void addPermission(String role, String resource) {

        String sql = "INSERT into permissions (role, resource) VALUES (?, ?)";

        try {
            jt.update(sql, role, resource);
        } catch (CannotGetJdbcConnectionException e) {
            fatalLog.error("[db-error] " + e.toString(), e);
            throw e;
        }
    }

    public void deletePermission(String role, String resource) {

        String sql = "DELETE from permissions WHERE role=? and resource=?";
        try {
            jt.update(sql, role, resource);
        } catch (CannotGetJdbcConnectionException e) {
            fatalLog.error("[db-error] " + e.toString(), e);
            throw e;
        }
    }

    private static final class PermissionRowMapper implements
        RowMapper<Permission> {
        @Override
        public Permission mapRow(ResultSet rs, int rowNum)
            throws SQLException {
            Permission info = new Permission();
            info.setResource(rs.getString("resource"));
            info.setRole(rs.getString("role"));
            return info;
        }
    }

    private static final PermissionRowMapper PERMISSION_ROW_MAPPER = new PermissionRowMapper();
}
