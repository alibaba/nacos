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

package com.alibaba.nacos.plugin.auth.impl.persistence;

import com.alibaba.nacos.persistence.configuration.DatasourceConfiguration;
import com.alibaba.nacos.persistence.datasource.DataSourceService;
import com.alibaba.nacos.persistence.datasource.DynamicDataSource;
import com.alibaba.nacos.api.model.Page;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.sql.ResultSet;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
// todo remove this
@MockitoSettings(strictness = Strictness.LENIENT)
class ExternalRolePersistServiceImplTest {
    
    @Mock
    private JdbcTemplate jdbcTemplate;
    
    @Mock
    private DataSourceService dataSourceService;
    
    private boolean embeddedStorageCache;
    
    private DataSourceService dataSourceServiceCache;
    
    private ExternalRolePersistServiceImpl externalRolePersistService;
    
    @BeforeEach
    void setUp() throws Exception {
        externalRolePersistService = new ExternalRolePersistServiceImpl();
        when(jdbcTemplate.queryForObject(any(), any(), eq(Integer.class))).thenReturn(0);
        when(dataSourceService.getJdbcTemplate()).thenReturn(jdbcTemplate);
        embeddedStorageCache = DatasourceConfiguration.isEmbeddedStorage();
        DatasourceConfiguration.setEmbeddedStorage(false);
        Field datasourceField = DynamicDataSource.class.getDeclaredField("basicDataSourceService");
        datasourceField.setAccessible(true);
        dataSourceServiceCache =
            (DataSourceService) datasourceField.get(DynamicDataSource.getInstance());
        datasourceField.set(DynamicDataSource.getInstance(), dataSourceService);
        externalRolePersistService.init();
    }
    
    @AfterEach
    void tearDown() throws NoSuchFieldException, IllegalAccessException {
        DatasourceConfiguration.setEmbeddedStorage(embeddedStorageCache);
        Field datasourceField = DynamicDataSource.class.getDeclaredField("basicDataSourceService");
        datasourceField.setAccessible(true);
        datasourceField.set(DynamicDataSource.getInstance(), dataSourceServiceCache);
    }
    
    @Test
    void testGetRoles() {
        Page<RoleInfo> roles = externalRolePersistService.getRoles(1, 10);
        
        assertNotNull(roles);
    }
    
    @Test
    void testGetRolesByUserName() {
        Page<RoleInfo> userName =
            externalRolePersistService.getRolesByUserNameAndRoleName("userName", "roleName", 1, 10);
        assertNotNull(userName);
    }
    
    @Test
    void testAddRole() {
        externalRolePersistService.addRole("role", "userName");
        
        String sql = "INSERT INTO roles (role, username) VALUES (?, ?)";
        Mockito.verify(jdbcTemplate).update(sql, "role", "userName");
    }
    
    @Test
    void testDeleteRole() {
        
        externalRolePersistService.deleteRole("role");
        String sql = "DELETE FROM roles WHERE role=?";
        Mockito.verify(jdbcTemplate).update(sql, "role");
        
        externalRolePersistService.deleteRole("role", "userName");
        String sql2 = "DELETE FROM roles WHERE role=? AND username=?";
        Mockito.verify(jdbcTemplate).update(sql2, "role", "userName");
        
    }
    
    @Test
    void testFindRolesLikeRoleName() {
        List<String> role = externalRolePersistService.findRolesLikeRoleName("role");
        
        assertEquals(0, role.size());
    }
    
    @Test
    void testFindRolesLikeAndGenerateLikeArgument() {
        assertEquals("ro\\_le%", externalRolePersistService.generateLikeArgument("ro_le*"));
        assertEquals("plain", externalRolePersistService.generateLikeArgument("plain"));
        
        RoleInfo roleInfo = new RoleInfo();
        roleInfo.setRole("role");
        roleInfo.setUsername("userName");
        Page<RoleInfo> page = new Page<>();
        page.setPageItems(Collections.singletonList(roleInfo));
        page.setTotalCount(1);
        AuthPaginationHelper<RoleInfo> helper = Mockito.mock(AuthPaginationHelper.class);
        ExternalRolePersistServiceImpl service = serviceWithHelper(helper);
        when(helper.fetchPage(any(), any(), any(), eq(1), eq(10), any())).thenReturn(page);
        
        assertSame(page, service.findRolesLike4Page("user*", "ro_le*", 1, 10));
        assertSame(page, service.findRolesLike4Page("", "", 1, 10));
    }
    
    @Test
    void testGetRolesReturnsEmptyPageWhenHelperReturnsNull() {
        AuthPaginationHelper<RoleInfo> helper = Mockito.mock(AuthPaginationHelper.class);
        ExternalRolePersistServiceImpl service = serviceWithHelper(helper);
        when(helper.fetchPage(any(), any(), any(), eq(1), eq(10), any())).thenReturn(null);
        
        Page<RoleInfo> result = service.getRoles(1, 10);
        
        assertEquals(0, result.getTotalCount());
        assertEquals(Collections.emptyList(), result.getPageItems());
    }
    
    @Test
    void testConnectionExceptionsAreRethrown() {
        CannotGetJdbcConnectionException addException =
            new CannotGetJdbcConnectionException("add");
        when(jdbcTemplate.update("INSERT INTO roles (role, username) VALUES (?, ?)", "role",
            "userName")).thenThrow(addException);
        assertSame(addException, assertThrows(CannotGetJdbcConnectionException.class,
            () -> externalRolePersistService.addRole("role", "userName")));
        
        CannotGetJdbcConnectionException deleteRoleException =
            new CannotGetJdbcConnectionException("deleteRole");
        when(jdbcTemplate.update("DELETE FROM roles WHERE role=?",
            "role")).thenThrow(deleteRoleException);
        assertSame(deleteRoleException, assertThrows(CannotGetJdbcConnectionException.class,
            () -> externalRolePersistService.deleteRole("role")));
        
        CannotGetJdbcConnectionException deleteUserRoleException =
            new CannotGetJdbcConnectionException("deleteUserRole");
        when(jdbcTemplate.update("DELETE FROM roles WHERE role=? AND username=?", "role",
            "userName")).thenThrow(deleteUserRoleException);
        assertSame(deleteUserRoleException, assertThrows(CannotGetJdbcConnectionException.class,
            () -> externalRolePersistService.deleteRole("role", "userName")));
    }
    
    @Test
    void testPaginationConnectionExceptionsAreRethrown() {
        AuthPaginationHelper<RoleInfo> getRolesHelper = Mockito.mock(AuthPaginationHelper.class);
        ExternalRolePersistServiceImpl getRolesService = serviceWithHelper(getRolesHelper);
        CannotGetJdbcConnectionException getRolesException =
            new CannotGetJdbcConnectionException("getRoles");
        when(getRolesHelper.fetchPage(any(), any(), any(), eq(1), eq(10), any()))
            .thenThrow(getRolesException);
        assertSame(getRolesException, assertThrows(CannotGetJdbcConnectionException.class,
            () -> getRolesService.getRoles(1, 10)));
        
        AuthPaginationHelper<RoleInfo> byUserHelper = Mockito.mock(AuthPaginationHelper.class);
        ExternalRolePersistServiceImpl byUserService = serviceWithHelper(byUserHelper);
        CannotGetJdbcConnectionException byUserException =
            new CannotGetJdbcConnectionException("byUser");
        when(byUserHelper.fetchPage(any(), any(), any(), eq(1), eq(10), any()))
            .thenThrow(byUserException);
        assertSame(byUserException, assertThrows(CannotGetJdbcConnectionException.class,
            () -> byUserService.getRolesByUserNameAndRoleName("userName", "roleName", 1, 10)));
        
        AuthPaginationHelper<RoleInfo> findHelper = Mockito.mock(AuthPaginationHelper.class);
        ExternalRolePersistServiceImpl findService = serviceWithHelper(findHelper);
        CannotGetJdbcConnectionException findException =
            new CannotGetJdbcConnectionException("find");
        when(findHelper.fetchPage(any(), any(), any(), eq(1), eq(10), any()))
            .thenThrow(findException);
        assertSame(findException, assertThrows(CannotGetJdbcConnectionException.class,
            () -> findService.findRolesLike4Page("userName", "roleName", 1, 10)));
    }
    
    @Test
    @SuppressWarnings("unchecked")
    void testRoleInfoRowMapperByReflection() throws Exception {
        String rowMapperName = ExternalRolePersistServiceImpl.class.getName()
            + "$RoleInfoRowMapper";
        Class<?> rowMapperClass = Class.forName(rowMapperName);
        Constructor<?> constructor = rowMapperClass.getDeclaredConstructor();
        constructor.setAccessible(true);
        RowMapper<RoleInfo> rowMapper = (RowMapper<RoleInfo>) constructor.newInstance();
        ResultSet resultSet = Mockito.mock(ResultSet.class);
        when(resultSet.getString("role")).thenReturn("role");
        when(resultSet.getString("username")).thenReturn("userName");
        
        RoleInfo roleInfo = rowMapper.mapRow(resultSet, 0);
        
        assertEquals("role", roleInfo.getRole());
        assertEquals("userName", roleInfo.getUsername());
    }
    
    private ExternalRolePersistServiceImpl serviceWithHelper(
        AuthPaginationHelper<RoleInfo> helper) {
        return new ExternalRolePersistServiceImpl() {
            
            @Override
            @SuppressWarnings("unchecked")
            public <E> AuthPaginationHelper<E> createPaginationHelper() {
                return (AuthPaginationHelper<E>) helper;
            }
        };
    }
}
