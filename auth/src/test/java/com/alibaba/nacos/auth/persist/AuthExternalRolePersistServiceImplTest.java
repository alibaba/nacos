/*
 * Copyright 1999-2021 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alibaba.nacos.auth.persist;

import com.alibaba.nacos.auth.model.Page;
import com.alibaba.nacos.auth.persist.repository.PaginationHelper;
import com.alibaba.nacos.auth.persist.repository.externel.AuthExternalStoragePersistServiceImpl;
import com.alibaba.nacos.auth.roles.RoleInfo;
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
public class AuthExternalRolePersistServiceImplTest {
    
    @Mock
    private AuthExternalStoragePersistServiceImpl persistService;
    
    @Mock
    private JdbcTemplate jt;
    
    @Mock
    private PaginationHelper paginationHelper;
    
    private AuthExternalRolePersistServiceImpl externalRolePersistService;
    
    @Before
    public void setUp() throws Exception {
        externalRolePersistService = new AuthExternalRolePersistServiceImpl();
        Class<AuthExternalRolePersistServiceImpl> externalRolePersistServiceClass = AuthExternalRolePersistServiceImpl.class;
        Field persistServiceClassDeclaredField = externalRolePersistServiceClass.getDeclaredField("persistService");
        persistServiceClassDeclaredField.setAccessible(true);
        persistServiceClassDeclaredField.set(externalRolePersistService, persistService);
        
        Mockito.when(persistService.getJdbcTemplate()).thenReturn(jt);
        Mockito.when(persistService.createPaginationHelper()).thenReturn(paginationHelper);
        
        externalRolePersistService.init();
    }
    
    @Test
    public void testGetRolesByUserName() {
        Page<RoleInfo> userName = externalRolePersistService.getRolesByUserName("userName", 1, 10);
        Assert.assertNull(userName);
    }
    
}