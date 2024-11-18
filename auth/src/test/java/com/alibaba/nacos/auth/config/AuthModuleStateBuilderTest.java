/*
 * Copyright 1999-2023 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.auth.config;

import com.alibaba.nacos.auth.mock.MockAuthPluginServiceB;
import com.alibaba.nacos.sys.module.ModuleState;
import com.alibaba.nacos.sys.utils.ApplicationUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ConfigurableApplicationContext;

import static com.alibaba.nacos.auth.config.AuthModuleStateBuilder.AUTH_ENABLED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthModuleStateBuilderTest {
    
    @Mock
    private ConfigurableApplicationContext context;
    
    @Mock
    private AuthConfigs authConfigs;
    
    @BeforeEach
    void setUp() throws Exception {
        when(context.getBean(AuthConfigs.class)).thenReturn(authConfigs);
        ApplicationUtils.injectContext(context);
    }
    
    @AfterEach
    void tearDown() throws Exception {
    }
    
    @Test
    void testBuild() {
        when(authConfigs.getNacosAuthSystemType()).thenReturn("nacos");
        
        ModuleState actual = new AuthModuleStateBuilder().build();
        assertFalse((Boolean) actual.getStates().get(AUTH_ENABLED));
        assertFalse((Boolean) actual.getStates().get("login_page_enabled"));
        assertEquals("nacos", actual.getStates().get("auth_system_type"));
        assertTrue((Boolean) actual.getStates().get("auth_admin_request"));
    
        when(authConfigs.getNacosAuthSystemType()).thenReturn(MockAuthPluginServiceB.TEST_PLUGIN);
        ModuleState actual2 = new AuthModuleStateBuilder().build();
        assertTrue((Boolean) actual2.getStates().get("login_page_enabled"));
        assertEquals(MockAuthPluginServiceB.TEST_PLUGIN, actual2.getStates().get("auth_system_type"));
        assertFalse((Boolean) actual2.getStates().get("auth_admin_request"));
    }
    
    @Test
    void testCacheable() {
        AuthModuleStateBuilder authModuleStateBuilder = new AuthModuleStateBuilder();
        authModuleStateBuilder.build();
        boolean cacheable = authModuleStateBuilder.isCacheable();
        assertFalse(cacheable);
        
        when(authConfigs.getNacosAuthSystemType()).thenReturn(MockAuthPluginServiceB.TEST_PLUGIN);
        AuthModuleStateBuilder authModuleStateBuilder2 = new AuthModuleStateBuilder();
        authModuleStateBuilder2.build();
        boolean cacheable2 = authModuleStateBuilder2.isCacheable();
        assertTrue(cacheable2);
    }
}