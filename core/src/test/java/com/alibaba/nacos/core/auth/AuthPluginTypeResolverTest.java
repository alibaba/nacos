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

package com.alibaba.nacos.core.auth;

import com.alibaba.nacos.plugin.auth.constant.Constants;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.mock.env.MockEnvironment;

import java.lang.reflect.Constructor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class AuthPluginTypeResolverTest {
    
    private ConfigurableEnvironment cachedEnvironment;
    
    private MockEnvironment environment;
    
    @BeforeEach
    void setUp() {
        cachedEnvironment = EnvUtil.getEnvironment();
        environment = new MockEnvironment();
        EnvUtil.setEnvironment(environment);
    }
    
    @AfterEach
    void tearDown() {
        EnvUtil.setEnvironment(cachedEnvironment);
    }
    
    @Test
    void testResolveDefaultPlugin() {
        assertEquals("", AuthPluginTypeResolver.resolve());
    }
    
    @Test
    void testResolveLegacyAlias() {
        environment.setProperty(Constants.Auth.NACOS_CORE_AUTH_SYSTEM_TYPE, " ldap ");
        assertEquals("ldap", AuthPluginTypeResolver.resolve());
    }
    
    @Test
    void testResolveStandardKeyFirst() {
        environment.setProperty(Constants.Auth.NACOS_CORE_AUTH_SYSTEM_TYPE, "ldap");
        environment.setProperty(Constants.Auth.NACOS_PLUGIN_AUTH_TYPE, " oidc ");
        assertEquals("oidc", AuthPluginTypeResolver.resolve());
    }
    
    @Test
    void testResolveBlankStandardKeyFallsBackToAlias() {
        environment.setProperty(Constants.Auth.NACOS_PLUGIN_AUTH_TYPE, " ");
        environment.setProperty(Constants.Auth.NACOS_CORE_AUTH_SYSTEM_TYPE, " ldap ");
        assertEquals("ldap", AuthPluginTypeResolver.resolve());
    }
    
    @Test
    void testPrivateConstructor() throws Exception {
        Constructor<AuthPluginTypeResolver> constructor =
            AuthPluginTypeResolver.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        assertNotNull(constructor.newInstance());
    }
}
