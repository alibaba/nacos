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

package com.alibaba.nacos.plugin.auth.impl.persistence;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.ResultSet;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthRowMapperManagerTest {
    
    @Mock
    private ResultSet resultSet;
    
    @Test
    void testUserRowMapper() throws SQLException {
        when(resultSet.getString("username")).thenReturn("nacos");
        when(resultSet.getString("password")).thenReturn("password");
        
        User user = AuthRowMapperManager.USER_ROW_MAPPER.mapRow(resultSet, 0);
        
        assertEquals("nacos", user.getUsername());
        assertEquals("password", user.getPassword());
    }
    
    @Test
    void testRoleInfoRowMapper() throws SQLException {
        when(resultSet.getString("role")).thenReturn("ROLE_ADMIN");
        when(resultSet.getString("username")).thenReturn("nacos");
        
        RoleInfo roleInfo = AuthRowMapperManager.ROLE_INFO_ROW_MAPPER.mapRow(resultSet, 0);
        
        assertEquals("ROLE_ADMIN", roleInfo.getRole());
        assertEquals("nacos", roleInfo.getUsername());
    }
    
    @Test
    void testPermissionRowMapper() throws SQLException {
        when(resultSet.getString("resource")).thenReturn("public:*:*");
        when(resultSet.getString("action")).thenReturn("rw");
        when(resultSet.getString("role")).thenReturn("ROLE_ADMIN");
        
        PermissionInfo permissionInfo =
            AuthRowMapperManager.PERMISSION_ROW_MAPPER.mapRow(resultSet, 0);
        
        assertEquals("public:*:*", permissionInfo.getResource());
        assertEquals("rw", permissionInfo.getAction());
        assertEquals("ROLE_ADMIN", permissionInfo.getRole());
        assertNotNull(AuthRowMapperManager.USER_ROW_MAPPER);
    }
    
    @Test
    void testConstructor() {
        assertNotNull(new AuthRowMapperManager());
    }
}
