/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
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
import com.alibaba.nacos.auth.persist.repository.PaginationHelper;
import com.alibaba.nacos.auth.persist.repository.embedded.DatabaseOperate;
import com.alibaba.nacos.auth.persist.repository.embedded.AuthEmbeddedStoragePersistServiceImpl;
import com.alibaba.nacos.auth.roles.RoleInfo;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import java.lang.reflect.Field;

@RunWith(MockitoJUnitRunner.class)
public class AuthEmbeddedRolePersistServiceImplTest {
    
    @Mock
    private DatabaseOperate databaseOperate;
    
    @Mock
    private AuthEmbeddedStoragePersistServiceImpl persistService;
    
    @Mock
    private PaginationHelper paginationHelper;
    
    private AuthEmbeddedRolePersistServiceImpl embeddedRolePersistService;
    
    @Before
    public void setUp() throws Exception {
        embeddedRolePersistService = new AuthEmbeddedRolePersistServiceImpl();
        Class<AuthEmbeddedRolePersistServiceImpl> embeddedRolePersistServiceClass = AuthEmbeddedRolePersistServiceImpl.class;
        Field databaseOperateFields = embeddedRolePersistServiceClass.getDeclaredField("databaseOperate");
        databaseOperateFields.setAccessible(true);
        databaseOperateFields.set(embeddedRolePersistService, databaseOperate);
        
        Field persistServiceField = embeddedRolePersistServiceClass.getDeclaredField("persistService");
        persistServiceField.setAccessible(true);
        persistServiceField.set(embeddedRolePersistService, persistService);
        
        Mockito.when(persistService.createPaginationHelper()).thenReturn(paginationHelper);
    }
    
    @Test
    public void testGetRolesByUserName() {
        Page<RoleInfo> page = embeddedRolePersistService.getRolesByUserName("userName", 1, 10);
        
        Assert.assertNull(page);
    }
}