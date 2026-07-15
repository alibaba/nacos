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

package com.alibaba.nacos.plugin.auth.impl.configuration.core;

import com.alibaba.nacos.plugin.auth.impl.AnonymousAccessInitializer;
import com.alibaba.nacos.plugin.auth.impl.NacosAuthPluginService;
import com.alibaba.nacos.plugin.auth.impl.constant.AuthConstants;
import com.alibaba.nacos.plugin.auth.impl.persistence.PermissionPersistService;
import com.alibaba.nacos.plugin.auth.impl.persistence.RolePersistService;
import com.alibaba.nacos.plugin.auth.impl.persistence.UserPersistService;
import com.alibaba.nacos.plugin.auth.impl.roles.NacosRoleServiceDirectImpl;
import com.alibaba.nacos.plugin.auth.impl.roles.NacosRoleServiceRemoteImpl;
import com.alibaba.nacos.plugin.auth.impl.users.NacosUserService;
import com.alibaba.nacos.plugin.auth.impl.users.NacosUserServiceDirectImpl;
import com.alibaba.nacos.plugin.auth.impl.users.NacosUserServiceRemoteImpl;
import com.alibaba.nacos.plugin.auth.spi.server.AuthPluginManager;
import com.alibaba.nacos.plugin.auth.spi.server.AuthPluginService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class NacosAuthPluginServiceConfigTest {
    
    private Map<String, AuthPluginService> cachedPluginMap;
    
    private NacosAuthPluginService pluginService;
    
    @BeforeEach
    void setUp() {
        Map<String, AuthPluginService> pluginMap = getMutablePluginMap();
        cachedPluginMap = new LinkedHashMap<>(pluginMap);
        pluginMap.clear();
        pluginService = new NacosAuthPluginService();
        pluginMap.put(AuthConstants.AUTH_PLUGIN_TYPE, pluginService);
    }
    
    @AfterEach
    void tearDown() {
        Map<String, AuthPluginService> pluginMap = getMutablePluginMap();
        pluginMap.clear();
        pluginMap.putAll(cachedPluginMap);
    }
    
    @Test
    void testInnerServiceBeans() {
        NacosAuthPluginInnerServiceConfig config = new NacosAuthPluginInnerServiceConfig();
        RolePersistService rolePersistService = mock(RolePersistService.class);
        NacosUserService userService = mock(NacosUserService.class);
        PermissionPersistService permissionPersistService = mock(PermissionPersistService.class);
        UserPersistService userPersistService = mock(UserPersistService.class);
        
        assertTrue(config.nacosRoleService(pluginService, rolePersistService, userService,
            permissionPersistService) instanceof NacosRoleServiceDirectImpl);
        assertTrue(config.nacosUserService(pluginService,
            userPersistService) instanceof NacosUserServiceDirectImpl);
        assertTrue(config.anonymousAccessInitializer(pluginService, userPersistService,
            rolePersistService, permissionPersistService) instanceof AnonymousAccessInitializer);
    }
    
    @Test
    void testRemoteServiceBeans() {
        NacosAuthPluginRemoteServiceConfig config = new NacosAuthPluginRemoteServiceConfig();
        
        assertTrue(config.nacosRoleService() instanceof NacosRoleServiceRemoteImpl);
        assertTrue(config.nacosUserService() instanceof NacosUserServiceRemoteImpl);
    }
    
    @SuppressWarnings("unchecked")
    private static Map<String, AuthPluginService> getMutablePluginMap() {
        return (Map<String, AuthPluginService>) ReflectionTestUtils.getField(
            AuthPluginManager.getInstance(), "authServiceMap");
    }
}
