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

import java.lang.reflect.Field;
import java.util.Collections;

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
class ExternalPermissionPersistServiceImplTest {
    
    @Mock
    private JdbcTemplate jdbcTemplate;
    
    @Mock
    private DataSourceService dataSourceService;
    
    private boolean embeddedStorageCache;
    
    private DataSourceService dataSourceServiceCache;
    
    private ExternalPermissionPersistServiceImpl externalPermissionPersistService;
    
    @BeforeEach
    void setUp() throws Exception {
        externalPermissionPersistService = new ExternalPermissionPersistServiceImpl();
        when(jdbcTemplate.queryForObject(any(), any(), eq(Integer.class))).thenReturn(0);
        when(dataSourceService.getJdbcTemplate()).thenReturn(jdbcTemplate);
        embeddedStorageCache = DatasourceConfiguration.isEmbeddedStorage();
        DatasourceConfiguration.setEmbeddedStorage(false);
        Field datasourceField = DynamicDataSource.class.getDeclaredField("basicDataSourceService");
        datasourceField.setAccessible(true);
        dataSourceServiceCache =
            (DataSourceService) datasourceField.get(DynamicDataSource.getInstance());
        datasourceField.set(DynamicDataSource.getInstance(), dataSourceService);
        externalPermissionPersistService.init();
    }
    
    @AfterEach
    void tearDown() throws NoSuchFieldException, IllegalAccessException {
        DatasourceConfiguration.setEmbeddedStorage(embeddedStorageCache);
        Field datasourceField = DynamicDataSource.class.getDeclaredField("basicDataSourceService");
        datasourceField.setAccessible(true);
        datasourceField.set(DynamicDataSource.getInstance(), dataSourceServiceCache);
    }
    
    @Test
    void testGetPermissions() {
        Page<PermissionInfo> role = externalPermissionPersistService.getPermissions("role", 1, 10);
        assertNotNull(role);
    }
    
    @Test
    void testAddPermission() {
        String sql = "INSERT INTO permissions (role, resource, action) VALUES (?, ?, ?)";
        externalPermissionPersistService.addPermission("role", "resource", "action");
        
        Mockito.verify(jdbcTemplate).update(sql, "role", "resource", "action");
    }
    
    @Test
    void testDeletePermission() {
        String sql = "DELETE FROM permissions WHERE role=? AND resource=? AND action=?";
        externalPermissionPersistService.deletePermission("role", "resource", "action");
        
        Mockito.verify(jdbcTemplate).update(sql, "role", "resource", "action");
    }
    
    @Test
    void testBlankRoleAndFindLikeReturnEmptyPageWhenHelperReturnsNull() {
        AuthPaginationHelper<PermissionInfo> helper = Mockito.mock(AuthPaginationHelper.class);
        ExternalPermissionPersistServiceImpl service = serviceWithHelper(helper);
        when(helper.fetchPage(any(), any(), any(), eq(1), eq(10), any())).thenReturn(null);
        
        Page<PermissionInfo> permissions = service.getPermissions("", 1, 10);
        Page<PermissionInfo> found = service.findPermissionsLike4Page("ro_le*", 1, 10);
        
        assertEquals(0, permissions.getTotalCount());
        assertEquals(Collections.emptyList(), permissions.getPageItems());
        assertEquals(0, found.getTotalCount());
        assertEquals(Collections.emptyList(), found.getPageItems());
        assertEquals("ro\\_le%", service.generateLikeArgument("ro_le*"));
        assertEquals("plain", service.generateLikeArgument("plain"));
    }
    
    @Test
    void testFindPermissionsLikeReturnsHelperPage() {
        PermissionInfo permissionInfo = new PermissionInfo();
        permissionInfo.setRole("role");
        permissionInfo.setResource("resource");
        permissionInfo.setAction("action");
        Page<PermissionInfo> page = new Page<>();
        page.setPageItems(Collections.singletonList(permissionInfo));
        page.setTotalCount(1);
        AuthPaginationHelper<PermissionInfo> helper = Mockito.mock(AuthPaginationHelper.class);
        ExternalPermissionPersistServiceImpl service = serviceWithHelper(helper);
        when(helper.fetchPage(any(), any(), any(), eq(1), eq(10), any())).thenReturn(page);
        
        assertSame(page, service.findPermissionsLike4Page("", 1, 10));
    }
    
    @Test
    void testConnectionExceptionsAreRethrown() {
        CannotGetJdbcConnectionException addException =
            new CannotGetJdbcConnectionException("add");
        when(
            jdbcTemplate.update("INSERT INTO permissions (role, resource, action) VALUES (?, ?, ?)",
                "role", "resource", "action"))
            .thenThrow(addException);
        assertSame(addException, assertThrows(CannotGetJdbcConnectionException.class,
            () -> externalPermissionPersistService.addPermission("role", "resource", "action")));
        
        CannotGetJdbcConnectionException deleteException =
            new CannotGetJdbcConnectionException("delete");
        when(jdbcTemplate.update("DELETE FROM permissions WHERE role=? AND resource=? AND action=?",
            "role", "resource", "action")).thenThrow(deleteException);
        assertSame(deleteException, assertThrows(CannotGetJdbcConnectionException.class,
            () -> externalPermissionPersistService.deletePermission("role", "resource",
                "action")));
        
        AuthPaginationHelper<PermissionInfo> helper = Mockito.mock(AuthPaginationHelper.class);
        ExternalPermissionPersistServiceImpl service = serviceWithHelper(helper);
        CannotGetJdbcConnectionException getException =
            new CannotGetJdbcConnectionException("get");
        when(helper.fetchPage(any(), any(), any(), eq(1), eq(10), any())).thenThrow(getException);
        assertSame(getException, assertThrows(CannotGetJdbcConnectionException.class,
            () -> service.getPermissions("role", 1, 10)));
        
        CannotGetJdbcConnectionException findException =
            new CannotGetJdbcConnectionException("find");
        when(helper.fetchPage(any(), any(), any(), eq(2), eq(20), any()))
            .thenThrow(findException);
        assertSame(findException, assertThrows(CannotGetJdbcConnectionException.class,
            () -> service.findPermissionsLike4Page("role", 2, 20)));
    }
    
    private ExternalPermissionPersistServiceImpl serviceWithHelper(
        AuthPaginationHelper<PermissionInfo> helper) {
        return new ExternalPermissionPersistServiceImpl() {
            
            @Override
            @SuppressWarnings("unchecked")
            public <E> AuthPaginationHelper<E> createPaginationHelper() {
                return (AuthPaginationHelper<E>) helper;
            }
        };
    }
}
