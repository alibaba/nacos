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

import com.alibaba.nacos.config.server.service.repository.RowMapperManager;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Auth plugin row mapper manager.
 *
 * @author xiweng.yy
 */
public class AuthRowMapperManager {
    
    public static final RowMapper<User> USER_ROW_MAPPER = new UserRowMapper();
    
    public static final RoleInfoRowMapper ROLE_INFO_ROW_MAPPER = new RoleInfoRowMapper();
    
    public static final PermissionRowMapper PERMISSION_ROW_MAPPER = new PermissionRowMapper();
    
    static {
        // USER_ROW_MAPPER
        RowMapperManager.registerRowMapper(USER_ROW_MAPPER.getClass().getCanonicalName(), USER_ROW_MAPPER);
    
        // ROLE_INFO_ROW_MAPPER
        RowMapperManager.registerRowMapper(ROLE_INFO_ROW_MAPPER.getClass().getCanonicalName(), ROLE_INFO_ROW_MAPPER);
        
        // PERMISSION_ROW_MAPPER
        RowMapperManager.registerRowMapper(PERMISSION_ROW_MAPPER.getClass().getCanonicalName(), PERMISSION_ROW_MAPPER);
    }
    
    public static final class UserRowMapper implements RowMapper<User> {
        
        @Override
        public User mapRow(ResultSet rs, int rowNum) throws SQLException {
            User user = new User();
            user.setUsername(rs.getString("username"));
            user.setPassword(rs.getString("password"));
            return user;
        }
    }
    
    public static final class RoleInfoRowMapper implements RowMapper<RoleInfo> {
        
        @Override
        public RoleInfo mapRow(ResultSet rs, int rowNum) throws SQLException {
            RoleInfo roleInfo = new RoleInfo();
            roleInfo.setRole(rs.getString("role"));
            roleInfo.setUsername(rs.getString("username"));
            return roleInfo;
        }
    }
    
    public static final class PermissionRowMapper implements RowMapper<PermissionInfo> {
        
        @Override
        public PermissionInfo mapRow(ResultSet rs, int rowNum) throws SQLException {
            PermissionInfo info = new PermissionInfo();
            info.setResource(rs.getString("resource"));
            info.setAction(rs.getString("action"));
            info.setRole(rs.getString("role"));
            return info;
        }
    }
}
