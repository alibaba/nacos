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
public class EmbeddedPermissionPersistServiceImplTest {
    
    @Mock
    private DatabaseOperate databaseOperate;
    
    @Mock
    private PaginationHelper paginationHelper;
    
    @Mock
    private EmbeddedStoragePersistServiceImpl embeddedStoragePersistService;
    
    private EmbeddedPermissionPersistServiceImpl embeddedPermissionPersistService;
    
    @Before
    public void setUp() throws Exception {
        embeddedPermissionPersistService = new EmbeddedPermissionPersistServiceImpl();
        Class<EmbeddedPermissionPersistServiceImpl> embeddedPermissionPersistServiceClass = EmbeddedPermissionPersistServiceImpl.class;
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
    
    @Test
    public void testAddPermission() {
        embeddedPermissionPersistService.addPermission("role", "resource", "action");
        List<ModifyRequest> currentSqlContext = EmbeddedStorageContextUtils.getCurrentSqlContext();
        
        Mockito.verify(databaseOperate).blockUpdate();
    }
    
    @Test
    public void testDeletePermission() {
        embeddedPermissionPersistService.deletePermission("role", "resource", "action");
        List<ModifyRequest> currentSqlContext = EmbeddedStorageContextUtils.getCurrentSqlContext();
        
        Mockito.verify(databaseOperate).blockUpdate();
    }
}
