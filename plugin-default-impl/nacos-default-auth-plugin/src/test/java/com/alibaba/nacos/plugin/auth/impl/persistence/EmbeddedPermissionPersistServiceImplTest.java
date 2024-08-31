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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
// todo remove this
@MockitoSettings(strictness = Strictness.LENIENT)
class EmbeddedPermissionPersistServiceImplTest {
    
    @Mock
    private DatabaseOperate databaseOperate;
    
    private EmbeddedPermissionPersistServiceImpl embeddedPermissionPersistService;
    
    @BeforeEach
    void setUp() throws Exception {
        when(databaseOperate.queryOne(any(String.class), any(Object[].class), eq(Integer.class))).thenReturn(0);
        embeddedPermissionPersistService = new EmbeddedPermissionPersistServiceImpl();
        Class<EmbeddedPermissionPersistServiceImpl> embeddedPermissionPersistServiceClass = EmbeddedPermissionPersistServiceImpl.class;
        Field databaseOperateF = embeddedPermissionPersistServiceClass.getDeclaredField("databaseOperate");
        databaseOperateF.setAccessible(true);
        databaseOperateF.set(embeddedPermissionPersistService, databaseOperate);
    }
    
    @Test
    void testGetPermissions() {
        String role = "role";
        Page<PermissionInfo> permissions = embeddedPermissionPersistService.getPermissions(role, 1, 10);
        
        assertNotNull(permissions);
    }
    
    @Test
    void testAddPermission() {
        embeddedPermissionPersistService.addPermission("role", "resource", "action");
        List<ModifyRequest> currentSqlContext = EmbeddedStorageContextHolder.getCurrentSqlContext();
        
        Mockito.verify(databaseOperate).blockUpdate();
    }
    
    @Test
    void testDeletePermission() {
        embeddedPermissionPersistService.deletePermission("role", "resource", "action");
        List<ModifyRequest> currentSqlContext = EmbeddedStorageContextHolder.getCurrentSqlContext();
        
        Mockito.verify(databaseOperate).blockUpdate();
    }
}
