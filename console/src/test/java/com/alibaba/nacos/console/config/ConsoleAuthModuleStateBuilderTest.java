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

package com.alibaba.nacos.console.config;

import com.alibaba.nacos.auth.config.NacosAuthConfig;
import com.alibaba.nacos.auth.config.NacosAuthConfigHolder;
import com.alibaba.nacos.plugin.auth.constant.Constants;
import com.alibaba.nacos.sys.env.EnvUtil;
import com.alibaba.nacos.sys.module.ModuleState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConsoleAuthModuleStateBuilderTest {
    
    @Mock
    private NacosAuthConfig authConfig;
    
    private NacosAuthConfig cachedAuthConfig;
    
    private ConfigurableEnvironment cachedEnvironment;
    
    ConsoleAuthModuleStateBuilder builder;
    
    @BeforeEach
    void setUp() {
        cachedEnvironment = EnvUtil.getEnvironment();
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty(Constants.Auth.NACOS_CORE_AUTH_SYSTEM_TYPE, "nacos");
        environment.setProperty(Constants.Auth.NACOS_CORE_AUTH_ADMIN_ENABLED, "false");
        EnvUtil.setEnvironment(environment);
        cachedAuthConfig = NacosAuthConfigHolder.getInstance()
                .getNacosAuthConfigByScope(NacosConsoleAuthConfig.NACOS_CONSOLE_AUTH_SCOPE);
        Map<String, NacosAuthConfig> nacosAuthConfigMap = (Map<String, NacosAuthConfig>) ReflectionTestUtils.getField(
                NacosAuthConfigHolder.getInstance(), "nacosAuthConfigMap");
        nacosAuthConfigMap.put(NacosConsoleAuthConfig.NACOS_CONSOLE_AUTH_SCOPE, authConfig);
        builder = new ConsoleAuthModuleStateBuilder();
    }
    
    @AfterEach
    void tearDown() {
        EnvUtil.setEnvironment(cachedEnvironment);
        Map<String, NacosAuthConfig> nacosAuthConfigMap = (Map<String, NacosAuthConfig>) ReflectionTestUtils.getField(
                NacosAuthConfigHolder.getInstance(), "nacosAuthConfigMap");
        nacosAuthConfigMap.put(NacosConsoleAuthConfig.NACOS_CONSOLE_AUTH_SCOPE, cachedAuthConfig);
    }
    
    @Test
    void build() {
        ModuleState state = builder.build();
        assertFalse((Boolean) state.getStates().get(ConsoleAuthModuleStateBuilder.AUTH_ENABLED));
        assertFalse((Boolean) state.getStates().get(ConsoleAuthModuleStateBuilder.LOGIN_PAGE_ENABLED));
        assertNull(state.getStates().get(ConsoleAuthModuleStateBuilder.AUTH_SYSTEM_TYPE));
        assertEquals(ConsoleAuthModuleStateBuilder.AUTH_MODULE, state.getModuleName());
        when(authConfig.isAuthEnabled()).thenReturn(true);
        when(authConfig.getNacosAuthSystemType()).thenReturn("nacos");
        state = builder.build();
        assertTrue((Boolean) state.getStates().get(ConsoleAuthModuleStateBuilder.AUTH_ENABLED));
        assertTrue((Boolean) state.getStates().get(ConsoleAuthModuleStateBuilder.LOGIN_PAGE_ENABLED));
        assertEquals("nacos", state.getStates().get(ConsoleAuthModuleStateBuilder.AUTH_SYSTEM_TYPE));
    }
    
    @Test
    void isCacheable() {
        assertFalse(builder.isCacheable());
    }
}