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
import com.alibaba.nacos.auth.model.PermissionInfo;
import com.alibaba.nacos.auth.persist.repository.PaginationHelper;
import com.alibaba.nacos.auth.persist.repository.externel.AuthExternalStoragePersistServiceImpl;
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
public class AuthExternalPermissionPersistServiceImplTest {
    
    @Mock
    private AuthExternalStoragePersistServiceImpl externalStoragePersistService;
    
    @Mock
    private JdbcTemplate jdbcTemplate;
    
    @Mock
    private PaginationHelper paginationHelper;
    
    private AuthExternalPermissionPersistServiceImpl externalPermissionPersistService;
    
    @Before
    public void setUp() throws Exception {
        externalPermissionPersistService = new AuthExternalPermissionPersistServiceImpl();
        
        Class<AuthExternalPermissionPersistServiceImpl> externalPermissionPersistServiceClass = AuthExternalPermissionPersistServiceImpl.class;
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
    
}