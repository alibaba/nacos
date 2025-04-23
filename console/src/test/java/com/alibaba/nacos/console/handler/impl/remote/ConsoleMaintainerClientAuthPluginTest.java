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

package com.alibaba.nacos.console.handler.impl.remote;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.auth.config.NacosAuthConfig;
import com.alibaba.nacos.auth.config.NacosAuthConfigHolder;
import com.alibaba.nacos.console.config.NacosConsoleAuthConfig;
import com.alibaba.nacos.plugin.auth.api.LoginIdentityContext;
import com.alibaba.nacos.plugin.auth.constant.Constants;
import com.alibaba.nacos.sys.env.EnvUtil;
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
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConsoleMaintainerClientAuthPluginTest {
    
    private static final String MOCK_IDENTITY_KEY = "mockIdentityKey";
    
    private static final String MOCK_IDENTITY_VALUE = "mockIdentityValue";
    
    private ConfigurableEnvironment cachedEnvironment;
    
    @Mock
    NacosConsoleAuthConfig mockNacosAuthConfig;
    
    NacosAuthConfig cachedConsoleAuthConfig;
    
    ConsoleMaintainerClientAuthPlugin authPlugin;
    
    @BeforeEach
    void setUp() {
        cachedEnvironment = EnvUtil.getEnvironment();
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty(Constants.Auth.NACOS_CORE_AUTH_ADMIN_ENABLED, "false");
        EnvUtil.setEnvironment(environment);
        authPlugin = new ConsoleMaintainerClientAuthPlugin();
        cachedConsoleAuthConfig = NacosAuthConfigHolder.getInstance()
                .getNacosAuthConfigByScope(NacosConsoleAuthConfig.NACOS_CONSOLE_AUTH_SCOPE);
        Map<String, NacosAuthConfig> nacosAuthConfigMap = (Map<String, NacosAuthConfig>) ReflectionTestUtils.getField(
                NacosAuthConfigHolder.getInstance(), "nacosAuthConfigMap");
        nacosAuthConfigMap.put(NacosConsoleAuthConfig.NACOS_CONSOLE_AUTH_SCOPE, mockNacosAuthConfig);
    }
    
    @AfterEach
    void tearDown() throws NacosException {
        Map<String, NacosAuthConfig> nacosAuthConfigMap = (Map<String, NacosAuthConfig>) ReflectionTestUtils.getField(
                NacosAuthConfigHolder.getInstance(), "nacosAuthConfigMap");
        nacosAuthConfigMap.put(NacosConsoleAuthConfig.NACOS_CONSOLE_AUTH_SCOPE, cachedConsoleAuthConfig);
        authPlugin.shutdown();
        EnvUtil.setEnvironment(cachedEnvironment);
    }
    
    @Test
    void login() {
        assertTrue(authPlugin.login(new Properties()));
        LoginIdentityContext identityContext = authPlugin.getLoginIdentityContext(null);
        assertFalse(identityContext.getAllKey().contains(MOCK_IDENTITY_KEY));
        when(mockNacosAuthConfig.isSupportServerIdentity()).thenReturn(true);
        when(mockNacosAuthConfig.getServerIdentityKey()).thenReturn(MOCK_IDENTITY_KEY);
        when(mockNacosAuthConfig.getServerIdentityValue()).thenReturn(MOCK_IDENTITY_VALUE);
        assertTrue(authPlugin.login(new Properties()));
        identityContext = authPlugin.getLoginIdentityContext(null);
        assertTrue(identityContext.getAllKey().contains(MOCK_IDENTITY_KEY));
        assertEquals(MOCK_IDENTITY_VALUE, identityContext.getParameter(MOCK_IDENTITY_KEY));
    }
}