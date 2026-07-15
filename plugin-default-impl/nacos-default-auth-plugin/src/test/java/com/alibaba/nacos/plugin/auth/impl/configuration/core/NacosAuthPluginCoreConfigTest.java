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

import com.alibaba.nacos.auth.config.NacosAuthConfig;
import com.alibaba.nacos.auth.config.NacosAuthConfigHolder;
import com.alibaba.nacos.core.auth.NacosServerAuthConfig;
import com.alibaba.nacos.core.code.ControllerMethodsCache;
import com.alibaba.nacos.plugin.auth.constant.Constants;
import com.alibaba.nacos.plugin.auth.impl.NacosAuthPluginService;
import com.alibaba.nacos.plugin.auth.impl.authenticate.DefaultAuthenticationManager;
import com.alibaba.nacos.plugin.auth.impl.configuration.NacosAuthPluginConfigProvider;
import com.alibaba.nacos.plugin.auth.impl.constant.AuthConstants;
import com.alibaba.nacos.plugin.auth.impl.constant.AuthSystemTypes;
import com.alibaba.nacos.plugin.auth.impl.roles.NacosRoleService;
import com.alibaba.nacos.plugin.auth.impl.token.TokenManagerDelegate;
import com.alibaba.nacos.plugin.auth.impl.users.NacosUserService;
import com.alibaba.nacos.plugin.auth.spi.server.AuthPluginManager;
import com.alibaba.nacos.plugin.auth.spi.server.AuthPluginService;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.authentication.configuration.GlobalAuthenticationConfigurerAdapter;
import org.springframework.security.config.annotation.authentication.configurers.userdetails.DaoAuthenticationConfigurer;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NacosAuthPluginCoreConfigTest {
    
    @Mock
    private NacosUserService userDetailsService;
    
    @Mock
    private ControllerMethodsCache methodsCache;
    
    @Mock
    private NacosRoleService roleService;
    
    private Map<String, NacosAuthConfig> cachedConfigMap;
    
    private Map<String, AuthPluginService> cachedPluginMap;
    
    private NacosAuthPluginService pluginService;
    
    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        setValidEnvironment(AuthSystemTypes.NACOS.name());
        cachedConfigMap = (Map<String, NacosAuthConfig>) ReflectionTestUtils.getField(
            NacosAuthConfigHolder.getInstance(), "nacosAuthConfigMap");
        Map<String, AuthPluginService> pluginMap = getMutablePluginMap();
        cachedPluginMap = new LinkedHashMap<>(pluginMap);
        pluginMap.clear();
        pluginService = new NacosAuthPluginService();
        pluginMap.put(AuthConstants.AUTH_PLUGIN_TYPE, pluginService);
    }
    
    @AfterEach
    void tearDown() {
        EnvUtil.setEnvironment(null);
        ReflectionTestUtils.setField(NacosAuthConfigHolder.getInstance(), "nacosAuthConfigMap",
            cachedConfigMap);
        Map<String, AuthPluginService> pluginMap = getMutablePluginMap();
        pluginMap.clear();
        pluginMap.putAll(cachedPluginMap);
    }
    
    @Test
    void testInitRegistersControllerPackage() {
        NacosAuthPluginCoreConfig config =
            new NacosAuthPluginCoreConfig(userDetailsService, methodsCache);
        
        config.init();
        
        verify(methodsCache).initClassMethod("com.alibaba.nacos.plugin.auth.impl.controller");
    }
    
    @Test
    void testAuthenticationConfigurerUsesNacosUserService() throws Exception {
        NacosAuthPluginCoreConfig config =
            new NacosAuthPluginCoreConfig(userDetailsService, methodsCache);
        AuthenticationManagerBuilder builder = mock(AuthenticationManagerBuilder.class);
        DaoAuthenticationConfigurer<AuthenticationManagerBuilder, NacosUserService> daoConfig =
            mock(DaoAuthenticationConfigurer.class);
        setValidAuthEnvironment(AuthSystemTypes.NACOS.name());
        when(builder.userDetailsService(userDetailsService)).thenReturn(daoConfig);
        
        GlobalAuthenticationConfigurerAdapter adapter = config.authenticationConfigurer();
        adapter.init(builder);
        
        verify(daoConfig).passwordEncoder(any(PasswordEncoder.class));
    }
    
    @Test
    void testBeanFactories() {
        NacosAuthPluginCoreConfig config =
            new NacosAuthPluginCoreConfig(userDetailsService, methodsCache);
        NacosAuthPluginConfigProvider provider =
            NacosAuthPluginCoreConfig.nacosAuthPluginConfigProvider();
        TokenManagerDelegate delegate = NacosAuthPluginCoreConfig.tokenManagerDelegate();
        
        assertTrue(config.passwordEncoder() instanceof PasswordEncoder);
        assertTrue(config.defaultAuthenticationManager(userDetailsService, delegate,
            roleService) instanceof DefaultAuthenticationManager);
        assertFalse(provider instanceof NacosAuthPluginService);
        assertNotSame(pluginService, provider);
        assertSame(pluginService.getConfig(), provider.getConfig());
        assertSame(pluginService.getTokenManagerDelegate(), delegate);
        assertNotNull(delegate);
    }
    
    @Test
    void testBeanFactoriesFailWithoutBuiltInPlugin() {
        getMutablePluginMap().clear();
        assertThrows(IllegalStateException.class,
            NacosAuthPluginCoreConfig::nacosAuthPluginConfigProvider);
    }
    
    private static void setValidAuthEnvironment(String systemType) {
        setValidEnvironment(systemType);
        Map<String, NacosAuthConfig> configMap = new HashMap<>();
        configMap.put(NacosServerAuthConfig.NACOS_SERVER_AUTH_SCOPE,
            new TestNacosAuthConfig(true, systemType));
        ReflectionTestUtils.setField(NacosAuthConfigHolder.getInstance(), "nacosAuthConfigMap",
            configMap);
    }
    
    private static void setValidEnvironment(String systemType) {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty(Constants.Auth.NACOS_CORE_AUTH_ENABLED, "true");
        environment.setProperty(Constants.Auth.NACOS_CORE_AUTH_SYSTEM_TYPE, systemType);
        environment.setProperty(Constants.Auth.NACOS_CORE_AUTH_SERVER_IDENTITY_KEY, "identity");
        environment.setProperty(Constants.Auth.NACOS_CORE_AUTH_SERVER_IDENTITY_VALUE, "value");
        environment.setProperty(Constants.Auth.NACOS_CORE_AUTH_ADMIN_ENABLED, "true");
        EnvUtil.setEnvironment(environment);
    }
    
    @SuppressWarnings("unchecked")
    private static Map<String, AuthPluginService> getMutablePluginMap() {
        return (Map<String, AuthPluginService>) ReflectionTestUtils.getField(
            AuthPluginManager.getInstance(), "authServiceMap");
    }
    
    private static final class TestNacosAuthConfig implements NacosAuthConfig {
        
        private final boolean authEnabled;
        
        private final String systemType;
        
        private TestNacosAuthConfig(boolean authEnabled, String systemType) {
            this.authEnabled = authEnabled;
            this.systemType = systemType;
        }
        
        @Override
        public String getAuthScope() {
            return NacosServerAuthConfig.NACOS_SERVER_AUTH_SCOPE;
        }
        
        @Override
        public boolean isAuthEnabled() {
            return authEnabled;
        }
        
        @Override
        public String getNacosAuthSystemType() {
            return systemType;
        }
        
        @Override
        public boolean isSupportServerIdentity() {
            return true;
        }
        
        @Override
        public String getServerIdentityKey() {
            return "identity";
        }
        
        @Override
        public String getServerIdentityValue() {
            return "value";
        }
    }
}
