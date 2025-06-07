/*
 * Copyright 1999-2025 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.core.config;

import com.alibaba.nacos.auth.config.NacosAuthConfigHolder;
import com.alibaba.nacos.core.auth.AuthModuleStateBuilder;
import com.alibaba.nacos.core.auth.NacosServerAdminAuthConfig;
import com.alibaba.nacos.core.auth.NacosServerAuthConfig;
import com.alibaba.nacos.core.mock.MockAuthPluginServiceB;
import com.alibaba.nacos.plugin.auth.constant.Constants;
import com.alibaba.nacos.sys.env.EnvUtil;
import com.alibaba.nacos.sys.module.ModuleState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.mock.env.MockEnvironment;

import static com.alibaba.nacos.core.auth.AuthModuleStateBuilder.AUTH_ENABLED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class AuthModuleStateBuilderTest {
    
    MockEnvironment environment;
    
    ConfigurableEnvironment cachedEnvironment;
    
    @BeforeEach
    void setUp() throws Exception {
        cachedEnvironment = EnvUtil.getEnvironment();
        environment = new MockEnvironment();
        environment.setProperty(Constants.Auth.NACOS_CORE_AUTH_SERVER_IDENTITY_KEY, "111");
        environment.setProperty(Constants.Auth.NACOS_CORE_AUTH_SERVER_IDENTITY_VALUE, "111");
        EnvUtil.setEnvironment(environment);
    }
    
    @AfterEach
    void tearDown() throws Exception {
        EnvUtil.setEnvironment(null != cachedEnvironment ? cachedEnvironment : new MockEnvironment());
        resetAuthConfig();
    }
    
    private void resetAuthConfig() {
        AbstractDynamicConfig config = (AbstractDynamicConfig) NacosAuthConfigHolder.getInstance()
                .getNacosAuthConfigByScope(NacosServerAuthConfig.NACOS_SERVER_AUTH_SCOPE);
        config.resetConfig();
        config = (AbstractDynamicConfig) NacosAuthConfigHolder.getInstance()
                .getNacosAuthConfigByScope(NacosServerAdminAuthConfig.NACOS_SERVER_ADMIN_AUTH_SCOPE);
        config.resetConfig();
    }
    
    @Test
    void testBuild() {
        environment.setProperty(Constants.Auth.NACOS_CORE_AUTH_SYSTEM_TYPE, "nacos");
        resetAuthConfig();
        ModuleState actual = new AuthModuleStateBuilder().build();
        assertTrue((Boolean) actual.getStates().get(AUTH_ENABLED));
        assertEquals("nacos", actual.getStates().get("auth_system_type"));
        assertTrue((Boolean) actual.getStates().get("auth_admin_request"));
        
        environment.setProperty(Constants.Auth.NACOS_CORE_AUTH_SYSTEM_TYPE, MockAuthPluginServiceB.TEST_PLUGIN);
        resetAuthConfig();
        ModuleState actual2 = new AuthModuleStateBuilder().build();
        Assertions.assertEquals(MockAuthPluginServiceB.TEST_PLUGIN, actual2.getStates().get("auth_system_type"));
        assertFalse((Boolean) actual2.getStates().get("auth_admin_request"));
    }
    
    @Test
    void testCacheable() {
        environment.setProperty(Constants.Auth.NACOS_CORE_AUTH_SYSTEM_TYPE, "nacos");
        resetAuthConfig();
        AuthModuleStateBuilder authModuleStateBuilder = new AuthModuleStateBuilder();
        authModuleStateBuilder.build();
        boolean cacheable = authModuleStateBuilder.isCacheable();
        assertFalse(cacheable);
        
        environment.setProperty(Constants.Auth.NACOS_CORE_AUTH_SYSTEM_TYPE, MockAuthPluginServiceB.TEST_PLUGIN);
        resetAuthConfig();
        AuthModuleStateBuilder authModuleStateBuilder2 = new AuthModuleStateBuilder();
        authModuleStateBuilder2.build();
        boolean cacheable2 = authModuleStateBuilder2.isCacheable();
        assertTrue(cacheable2);
    }
}