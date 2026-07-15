/*
 * Copyright 1999-2026 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.plugin.auth.impl.configuration.persistence;

import com.alibaba.nacos.persistence.repository.embedded.operate.DatabaseOperate;
import com.alibaba.nacos.plugin.auth.impl.persistence.EmbeddedPermissionPersistServiceImpl;
import com.alibaba.nacos.plugin.auth.impl.persistence.EmbeddedRolePersistServiceImpl;
import com.alibaba.nacos.plugin.auth.impl.persistence.EmbeddedUserPersistServiceImpl;
import com.alibaba.nacos.plugin.auth.impl.persistence.ExternalPermissionPersistServiceImpl;
import com.alibaba.nacos.plugin.auth.impl.persistence.ExternalRolePersistServiceImpl;
import com.alibaba.nacos.plugin.auth.impl.persistence.ExternalUserPersistServiceImpl;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class NacosAuthPluginPersistenceConfigTest {
    
    @Test
    void testPersistenceConfigConstructor() {
        assertNotNull(new NacosAuthPluginPersistenceConfig());
    }
    
    @Test
    void testEmbeddedStorageBeans() {
        NacosAuthPluginEmbeddedStorageConfig config = new NacosAuthPluginEmbeddedStorageConfig();
        DatabaseOperate databaseOperate = mock(DatabaseOperate.class);
        
        assertTrue(config.permissionPersistService(
            databaseOperate) instanceof EmbeddedPermissionPersistServiceImpl);
        assertTrue(
            config.rolePersistService(databaseOperate) instanceof EmbeddedRolePersistServiceImpl);
        assertTrue(
            config.userPersistService(databaseOperate) instanceof EmbeddedUserPersistServiceImpl);
    }
    
    @Test
    void testExternalStorageBeans() {
        NacosAuthPluginExternalStorageConfig config = new NacosAuthPluginExternalStorageConfig();
        
        assertTrue(
            config.permissionPersistService() instanceof ExternalPermissionPersistServiceImpl);
        assertTrue(config.rolePersistService() instanceof ExternalRolePersistServiceImpl);
        assertTrue(config.userPersistService() instanceof ExternalUserPersistServiceImpl);
    }
}
