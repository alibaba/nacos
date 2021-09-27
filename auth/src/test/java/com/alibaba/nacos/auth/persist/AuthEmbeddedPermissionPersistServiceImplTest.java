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

package com.alibaba.nacos.auth.persist;

import com.alibaba.nacos.auth.model.Page;
import com.alibaba.nacos.auth.model.PermissionInfo;
import com.alibaba.nacos.auth.persist.repository.PaginationHelper;
import com.alibaba.nacos.auth.persist.repository.embedded.DatabaseOperate;
import com.alibaba.nacos.auth.persist.repository.embedded.AuthEmbeddedStoragePersistServiceImpl;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import java.lang.reflect.Field;

@RunWith(MockitoJUnitRunner.class)
public class AuthEmbeddedPermissionPersistServiceImplTest {
    
    @Mock
    private DatabaseOperate databaseOperate;
    
    @Mock
    private PaginationHelper paginationHelper;
    
    @Mock
    private AuthEmbeddedStoragePersistServiceImpl embeddedStoragePersistService;
    
    private AuthEmbeddedPermissionPersistServiceImpl embeddedPermissionPersistService;
    
    @Before
    public void setUp() throws Exception {
        embeddedPermissionPersistService = new AuthEmbeddedPermissionPersistServiceImpl();
        Class<AuthEmbeddedPermissionPersistServiceImpl> embeddedPermissionPersistServiceClass = AuthEmbeddedPermissionPersistServiceImpl.class;
        Field databaseOperateF = embeddedPermissionPersistServiceClass.getDeclaredField("databaseOperate");
        databaseOperateF.setAccessible(true);
        databaseOperateF.set(embeddedPermissionPersistService, databaseOperate);
        
        Field persistService = embeddedPermissionPersistServiceClass.getDeclaredField("persistService");
        persistService.setAccessible(true);
        persistService.set(embeddedPermissionPersistService, embeddedStoragePersistService);
        Mockito.when(embeddedStoragePersistService.createPaginationHelper()).thenReturn(paginationHelper);
    }
    
    @Test
    public void testGetPermissions() {
        String role = "role";
        Page<PermissionInfo> permissions = embeddedPermissionPersistService.getPermissions(role, 1, 10);
        
        Assert.assertNotNull(permissions);
    }
    
}