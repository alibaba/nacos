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

package com.alibaba.nacos.core.auth;

import com.alibaba.nacos.api.plugin.PluginStateChecker;
import com.alibaba.nacos.api.plugin.PluginStateCheckerHolder;
import com.alibaba.nacos.api.plugin.PluginType;
import com.alibaba.nacos.auth.config.NacosAuthConfigHolder;
import com.alibaba.nacos.core.config.AbstractDynamicConfig;
import com.alibaba.nacos.core.mock.MockAuthPluginServiceB;
import com.alibaba.nacos.plugin.auth.constant.Constants;
import com.alibaba.nacos.sys.env.EnvUtil;
import com.alibaba.nacos.sys.module.ModuleState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;

import static com.alibaba.nacos.core.auth.AuthModuleStateBuilder.AUTH_ENABLED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;

/**
 * {@link AuthModuleStateBuilder} unit test.
 *
 * @author xiweng.yy
 */
@ExtendWith(MockitoExtension.class)
class AuthModuleStateBuilderTest {
    
    MockEnvironment environment;
    
    ConfigurableEnvironment cachedEnvironment;
    
    @Mock
    PluginStateChecker pluginStateChecker;
    
    @BeforeEach
    void setUp() throws Exception {
        cachedEnvironment = EnvUtil.getEnvironment();
        environment = new MockEnvironment();
        environment.setProperty(Constants.Auth.NACOS_CORE_AUTH_SERVER_IDENTITY_KEY, "111");
        environment.setProperty(Constants.Auth.NACOS_CORE_AUTH_SERVER_IDENTITY_VALUE, "111");
        EnvUtil.setEnvironment(environment);
        PluginStateCheckerHolder.setInstance(pluginStateChecker);
        lenient().when(pluginStateChecker.isPluginEnabled(PluginType.AUTH.getType(), "nacos"))
            .thenReturn(true);
        lenient()
            .when(pluginStateChecker.isPluginEnabled(PluginType.AUTH.getType(),
                MockAuthPluginServiceB.TEST_PLUGIN))
            .thenReturn(true);
    }
    
    @AfterEach
    void tearDown() throws Exception {
        EnvUtil
            .setEnvironment(null != cachedEnvironment ? cachedEnvironment : new MockEnvironment());
        resetAuthConfig();
        // Reset PluginStateCheckerHolder to avoid effect.
        PluginStateCheckerHolder.setInstance(null);
    }
    
    private void resetAuthConfig() {
        AbstractDynamicConfig config = (AbstractDynamicConfig) NacosAuthConfigHolder.getInstance()
            .getNacosAuthConfigByScope(NacosServerAuthConfig.NACOS_SERVER_AUTH_SCOPE);
        if (config != null) {
            ReflectionTestUtils.invokeMethod(config, "resetConfig");
        }
        config = (AbstractDynamicConfig) NacosAuthConfigHolder.getInstance()
            .getNacosAuthConfigByScope(NacosServerAdminAuthConfig.NACOS_SERVER_ADMIN_AUTH_SCOPE);
        if (config != null) {
            ReflectionTestUtils.invokeMethod(config, "resetConfig");
        }
    }
    
    @Test
    void testBuild() {
        environment.setProperty(Constants.Auth.NACOS_CORE_AUTH_SYSTEM_TYPE, "nacos");
        resetAuthConfig();
        ModuleState actual = new AuthModuleStateBuilder().build();
        assertEquals(AuthModuleStateBuilder.AUTH_MODULE, actual.getModuleName());
        assertTrue((Boolean) actual.getStates().get(AUTH_ENABLED));
        assertEquals("nacos", actual.getStates().get(AuthModuleStateBuilder.AUTH_SYSTEM_TYPE));
        assertTrue((Boolean) actual.getStates().get(AuthModuleStateBuilder.AUTH_ADMIN_REQUEST));
        
        environment.setProperty(Constants.Auth.NACOS_CORE_AUTH_SYSTEM_TYPE,
            MockAuthPluginServiceB.TEST_PLUGIN);
        resetAuthConfig();
        ModuleState actual2 = new AuthModuleStateBuilder().build();
        assertNotNull(actual2);
        assertEquals(AuthModuleStateBuilder.AUTH_MODULE, actual2.getModuleName());
        Assertions.assertEquals(MockAuthPluginServiceB.TEST_PLUGIN,
            actual2.getStates().get(AuthModuleStateBuilder.AUTH_SYSTEM_TYPE));
        assertFalse((Boolean) actual2.getStates().get(AuthModuleStateBuilder.AUTH_ADMIN_REQUEST));
    }
    
    @Test
    void testBuildContainsExpectedStateKeys() {
        environment.setProperty(Constants.Auth.NACOS_CORE_AUTH_ENABLED, "false");
        environment.setProperty(Constants.Auth.NACOS_CORE_AUTH_ADMIN_ENABLED, "false");
        resetAuthConfig();
        ModuleState state = new AuthModuleStateBuilder().build();
        assertTrue(state.getStates().containsKey(AuthModuleStateBuilder.AUTH_ENABLED));
        assertTrue(state.getStates().containsKey(AuthModuleStateBuilder.AUTH_SYSTEM_TYPE));
        assertTrue(state.getStates().containsKey(AuthModuleStateBuilder.AUTH_ADMIN_REQUEST));
    }
    
    @Test
    void testCacheable() {
        environment.setProperty(Constants.Auth.NACOS_CORE_AUTH_SYSTEM_TYPE, "nacos");
        resetAuthConfig();
        AuthModuleStateBuilder authModuleStateBuilder = new AuthModuleStateBuilder();
        authModuleStateBuilder.build();
        boolean cacheable = authModuleStateBuilder.isCacheable();
        assertFalse(cacheable);
        
        environment.setProperty(Constants.Auth.NACOS_CORE_AUTH_SYSTEM_TYPE,
            MockAuthPluginServiceB.TEST_PLUGIN);
        resetAuthConfig();
        AuthModuleStateBuilder authModuleStateBuilder2 = new AuthModuleStateBuilder();
        authModuleStateBuilder2.build();
        boolean cacheable2 = authModuleStateBuilder2.isCacheable();
        assertTrue(cacheable2);
    }
}
