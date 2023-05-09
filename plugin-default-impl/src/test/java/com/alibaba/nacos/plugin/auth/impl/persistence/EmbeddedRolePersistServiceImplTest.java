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

import com.alibaba.nacos.persistence.model.Page;
import com.alibaba.nacos.persistence.repository.embedded.EmbeddedStorageContextHolder;
import com.alibaba.nacos.persistence.repository.embedded.operate.DatabaseOperate;
import com.alibaba.nacos.persistence.repository.embedded.sql.ModifyRequest;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.lang.reflect.Field;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class EmbeddedRolePersistServiceImplTest {
    
    @Mock
    private DatabaseOperate databaseOperate;
    
    private EmbeddedRolePersistServiceImpl embeddedRolePersistService;
    
    @Before
    public void setUp() throws Exception {
        when(databaseOperate.queryOne(any(String.class), any(Object[].class), eq(Integer.class))).thenReturn(0);
        embeddedRolePersistService = new EmbeddedRolePersistServiceImpl();
        Class<EmbeddedRolePersistServiceImpl> embeddedRolePersistServiceClass = EmbeddedRolePersistServiceImpl.class;
        Field databaseOperateFields = embeddedRolePersistServiceClass.getDeclaredField("databaseOperate");
        databaseOperateFields.setAccessible(true);
        databaseOperateFields.set(embeddedRolePersistService, databaseOperate);
    }
    
    @Test
    public void testGetRoles() {
        Page<RoleInfo> roles = embeddedRolePersistService.getRoles(1, 10);
        Assert.assertNotNull(roles);
    }
    
    @Test
    public void testGetRolesByUserName() {
        Page<RoleInfo> page = embeddedRolePersistService.getRolesByUserNameAndRoleName("userName", "roleName", 1, 10);
        
        Assert.assertNotNull(page);
    }
    
    @Test
    public void testAddRole() {
        embeddedRolePersistService.addRole("role", "userName");
        List<ModifyRequest> currentSqlContext = EmbeddedStorageContextHolder.getCurrentSqlContext();
        
        Assert.assertEquals(currentSqlContext.size(), 0);
    }
    
    @Test
    public void testDeleteRole() {
        embeddedRolePersistService.deleteRole("role");
        embeddedRolePersistService.deleteRole("role", "userName");
        
        List<ModifyRequest> currentSqlContext = EmbeddedStorageContextHolder.getCurrentSqlContext();
        
        Assert.assertEquals(currentSqlContext.size(), 0);
    }
    
    @Test
    public void testFindRolesLikeRoleName() {
        
        List<String> role = embeddedRolePersistService.findRolesLikeRoleName("role");
        
        Assert.assertEquals(role.size(), 0);
    }
}
