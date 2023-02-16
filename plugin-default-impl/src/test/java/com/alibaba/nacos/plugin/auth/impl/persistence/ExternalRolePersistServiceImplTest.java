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
import java.util.List;

@RunWith(MockitoJUnitRunner.class)
public class ExternalRolePersistServiceImplTest {
    
    @Mock
    private ExternalStoragePersistServiceImpl persistService;
    
    @Mock
    private JdbcTemplate jt;
    
    @Mock
    private PaginationHelper paginationHelper;
    
    private ExternalRolePersistServiceImpl externalRolePersistService;
    
    @Before
    public void setUp() throws Exception {
        externalRolePersistService = new ExternalRolePersistServiceImpl();
        Class<ExternalRolePersistServiceImpl> externalRolePersistServiceClass = ExternalRolePersistServiceImpl.class;
        Field persistServiceClassDeclaredField = externalRolePersistServiceClass.getDeclaredField("persistService");
        persistServiceClassDeclaredField.setAccessible(true);
        persistServiceClassDeclaredField.set(externalRolePersistService, persistService);
        
        Mockito.when(persistService.getJdbcTemplate()).thenReturn(jt);
        Mockito.when(persistService.createPaginationHelper()).thenReturn(paginationHelper);
        
        externalRolePersistService.init();
    }
    
    @Test
    public void testGetRoles() {
        Page<RoleInfo> roles = externalRolePersistService.getRoles(1, 10);
        
        Assert.assertNotNull(roles);
    }
    
    @Test
    public void testGetRolesByUserName() {
        Page<RoleInfo> userName = externalRolePersistService.getRolesByUserNameAndRoleName(
                "userName", "roleName", 1, 10);
        Assert.assertNull(userName);
    }
    
    @Test
    public void testAddRole() {
        externalRolePersistService.addRole("role", "userName");
        
        String sql = "INSERT INTO roles (role, username) VALUES (?, ?)";
        Mockito.verify(jt).update(sql, "role", "userName");
    }
    
    @Test
    public void testDeleteRole() {
        
        externalRolePersistService.deleteRole("role");
        String sql = "DELETE FROM roles WHERE role=?";
        Mockito.verify(jt).update(sql, "role");
        
        externalRolePersistService.deleteRole("role", "userName");
        String sql2 = "DELETE FROM roles WHERE role=? AND username=?";
        Mockito.verify(jt).update(sql2, "role", "userName");
        
    }
    
    @Test
    public void testFindRolesLikeRoleName() {
        List<String> role = externalRolePersistService.findRolesLikeRoleName("role");
        
        Assert.assertEquals(role.size(), 0);
    }
}
