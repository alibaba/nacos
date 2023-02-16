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
import com.alibaba.nacos.config.server.service.repository.embedded.DatabaseOperate;
import com.alibaba.nacos.config.server.service.repository.embedded.EmbeddedStoragePersistServiceImpl;
import com.alibaba.nacos.config.server.service.sql.EmbeddedStorageContextUtils;
import com.alibaba.nacos.config.server.service.sql.ModifyRequest;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import java.lang.reflect.Field;
import java.util.List;

@RunWith(MockitoJUnitRunner.class)
public class EmbeddedRolePersistServiceImplTest {
    
    @Mock
    private DatabaseOperate databaseOperate;
    
    @Mock
    private EmbeddedStoragePersistServiceImpl persistService;
    
    @Mock
    private PaginationHelper paginationHelper;
    
    private EmbeddedRolePersistServiceImpl embeddedRolePersistService;
    
    @Before
    public void setUp() throws Exception {
        embeddedRolePersistService = new EmbeddedRolePersistServiceImpl();
        Class<EmbeddedRolePersistServiceImpl> embeddedRolePersistServiceClass = EmbeddedRolePersistServiceImpl.class;
        Field databaseOperateFields = embeddedRolePersistServiceClass.getDeclaredField("databaseOperate");
        databaseOperateFields.setAccessible(true);
        databaseOperateFields.set(embeddedRolePersistService, databaseOperate);
        
        Field persistServiceField = embeddedRolePersistServiceClass.getDeclaredField("persistService");
        persistServiceField.setAccessible(true);
        persistServiceField.set(embeddedRolePersistService, persistService);
        
        Mockito.when(persistService.createPaginationHelper()).thenReturn(paginationHelper);
    }
    
    @Test
    public void testGetRoles() {
        Page<RoleInfo> roles = embeddedRolePersistService.getRoles(1, 10);
        Assert.assertNotNull(roles);
    }
    
    @Test
    public void testGetRolesByUserName() {
        Page<RoleInfo> page = embeddedRolePersistService.getRolesByUserNameAndRoleName(
                "userName", "roleName", 1, 10);
        
        Assert.assertNull(page);
    }
    
    @Test
    public void testAddRole() {
        embeddedRolePersistService.addRole("role", "userName");
        List<ModifyRequest> currentSqlContext = EmbeddedStorageContextUtils.getCurrentSqlContext();
        
        Assert.assertEquals(currentSqlContext.size(), 0);
    }
    
    @Test
    public void testDeleteRole() {
        embeddedRolePersistService.deleteRole("role");
        embeddedRolePersistService.deleteRole("role", "userName");
        
        List<ModifyRequest> currentSqlContext = EmbeddedStorageContextUtils.getCurrentSqlContext();
        
        Assert.assertEquals(currentSqlContext.size(), 0);
    }
    
    @Test
    public void testFindRolesLikeRoleName() {
        
        List<String> role = embeddedRolePersistService.findRolesLikeRoleName("role");
        
        Assert.assertEquals(role.size(), 0);
    }
}
