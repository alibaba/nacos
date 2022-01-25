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

import com.alibaba.nacos.config.server.model.Page;
import com.alibaba.nacos.config.server.service.repository.PaginationHelper;
import com.alibaba.nacos.config.server.service.repository.extrnal.ExternalStoragePersistServiceImpl;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.jdbc.core.JdbcTemplate;

import java.lang.reflect.Field;

@RunWith(MockitoJUnitRunner.class)
public class ExternalPermissionPersistServiceImplTest {
    
    @Mock
    private ExternalStoragePersistServiceImpl externalStoragePersistService;
    
    @Mock
    private JdbcTemplate jdbcTemplate;
    
    @Mock
    private PaginationHelper paginationHelper;
    
    private ExternalPermissionPersistServiceImpl externalPermissionPersistService;
    
    @Before
    public void setUp() throws Exception {
        externalPermissionPersistService = new ExternalPermissionPersistServiceImpl();
        
        Class<ExternalPermissionPersistServiceImpl> externalPermissionPersistServiceClass = ExternalPermissionPersistServiceImpl.class;
        Field persistServiceClassDeclaredField = externalPermissionPersistServiceClass
                .getDeclaredField("persistService");
        persistServiceClassDeclaredField.setAccessible(true);
        persistServiceClassDeclaredField.set(externalPermissionPersistService, externalStoragePersistService);
        
        Mockito.when(externalStoragePersistService.getJdbcTemplate()).thenReturn(jdbcTemplate);
        Mockito.when(externalStoragePersistService.createPaginationHelper()).thenReturn(paginationHelper);
        
        externalPermissionPersistService.init();
    }
    
    @Test
    public void testGetPermissions() {
        Page<PermissionInfo> role = externalPermissionPersistService.getPermissions("role", 1, 10);
        
        Assert.assertNotNull(role);
    }
    
    @Test
    public void testAddPermission() {
        String sql = "INSERT INTO permissions (role, resource, action) VALUES (?, ?, ?)";
        externalPermissionPersistService.addPermission("role", "resource", "action");
        
        Mockito.verify(jdbcTemplate).update(sql, "role", "resource", "action");
    }
    
    @Test
    public void testDeletePermission() {
        String sql = "DELETE FROM permissions WHERE role=? AND resource=? AND action=?";
        externalPermissionPersistService.deletePermission("role", "resource", "action");
        
        Mockito.verify(jdbcTemplate).update(sql, "role", "resource", "action");
    }
}
