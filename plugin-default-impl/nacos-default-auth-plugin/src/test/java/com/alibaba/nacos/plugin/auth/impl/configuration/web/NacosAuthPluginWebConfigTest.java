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

package com.alibaba.nacos.plugin.auth.impl.configuration.web;

import com.alibaba.nacos.auth.config.NacosAuthConfig;
import com.alibaba.nacos.auth.config.NacosAuthConfigHolder;
import com.alibaba.nacos.core.auth.NacosServerAuthConfig;
import com.alibaba.nacos.plugin.auth.constant.Constants;
import com.alibaba.nacos.sys.env.EnvUtil;
import com.alibaba.nacos.sys.utils.ApplicationUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.config.Customizer;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;
import org.springframework.security.web.DefaultSecurityFilterChain;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NacosAuthPluginWebConfigTest {
    
    private Map<String, NacosAuthConfig> cachedConfigMap;
    
    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        cachedConfigMap = (Map<String, NacosAuthConfig>) ReflectionTestUtils.getField(
            NacosAuthConfigHolder.getInstance(), "nacosAuthConfigMap");
    }
    
    @AfterEach
    void tearDown() {
        EnvUtil.setEnvironment(null);
        ReflectionTestUtils.setField(NacosAuthConfigHolder.getInstance(), "nacosAuthConfigMap",
            cachedConfigMap);
    }
    
    @Test
    void testSecurityFilterChainBuildsIgnoredUrlChain() throws Exception {
        setValidAuthEnvironment("nacos");
        HttpSecurity http = mock(HttpSecurity.class);
        DefaultSecurityFilterChain chain = mock(DefaultSecurityFilterChain.class);
        when(http.authorizeHttpRequests(any())).thenReturn(http);
        when(http.csrf(any())).thenReturn(http);
        when(http.build()).thenReturn(chain);
        
        SecurityFilterChain actual = new NacosAuthPluginWebConfig().securityFilterChain(http);
        
        assertSame(chain, actual);
    }
    
    @Test
    void testSecurityFilterChainBuildsIgnoredUrlChainForLdap() throws Exception {
        setValidAuthEnvironment("ldap");
        HttpSecurity http = mock(HttpSecurity.class);
        DefaultSecurityFilterChain chain = mock(DefaultSecurityFilterChain.class);
        when(http.authorizeHttpRequests(any())).thenReturn(http);
        when(http.csrf(any())).thenReturn(http);
        when(http.build()).thenReturn(chain);
        
        SecurityFilterChain actual = new NacosAuthPluginWebConfig().securityFilterChain(http);
        
        assertSame(chain, actual);
    }
    
    @Test
    void testSecurityFilterChainUsesConfiguredIgnoreUrlsWhenAuthTypeBlank() throws Exception {
        MockEnvironment environment = setValidAuthEnvironment("");
        environment.setProperty("nacos.security.ignore.urls", "/v1/**,/v3/**");
        HttpSecurity http = mock(HttpSecurity.class);
        DefaultSecurityFilterChain chain = mock(DefaultSecurityFilterChain.class);
        when(http.authorizeHttpRequests(any())).thenReturn(http);
        when(http.csrf(any())).thenReturn(http);
        when(http.build()).thenReturn(chain);
        
        SecurityFilterChain actual = new NacosAuthPluginWebConfig().securityFilterChain(http);
        
        assertSame(chain, actual);
    }
    
    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void testSecurityFilterChainCustomizerPermitsConfiguredIgnoreUrls() throws Exception {
        MockEnvironment environment = setValidAuthEnvironment("");
        environment.setProperty("nacos.security.ignore.urls", "/v1/**,/v3/**");
        HttpSecurity http = mock(HttpSecurity.class);
        DefaultSecurityFilterChain chain = mock(DefaultSecurityFilterChain.class);
        ArgumentCaptor<Customizer<AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry>> customizerCaptor =
            ArgumentCaptor.forClass(Customizer.class);
        ArgumentCaptor<String[]> matchersCaptor = ArgumentCaptor.forClass(String[].class);
        AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry registry =
            mock(AuthorizeHttpRequestsConfigurer.AuthorizationManagerRequestMatcherRegistry.class);
        AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizedUrl authorizedUrl =
            mock(AuthorizeHttpRequestsConfigurer.AuthorizedUrl.class);
        when(http.authorizeHttpRequests(customizerCaptor.capture())).thenReturn(http);
        when(http.csrf(any())).thenReturn(http);
        when(http.build()).thenReturn(chain);
        when(registry.requestMatchers(any(String[].class))).thenReturn(authorizedUrl);
        when(authorizedUrl.permitAll()).thenReturn(registry);
        
        new NacosAuthPluginWebConfig().securityFilterChain(http);
        customizerCaptor.getValue().customize(registry);
        
        verify(registry).requestMatchers(matchersCaptor.capture());
        assertArrayEquals(new String[] {"/v1/**", "/v3/**"},
            matchersCaptor.getValue());
        verify(authorizedUrl).permitAll();
    }
    
    @Test
    void testSecurityFilterChainBuildsDirectlyWhenIgnoreUrlsBlank() throws Exception {
        setValidAuthEnvironment("custom");
        HttpSecurity http = mock(HttpSecurity.class);
        DefaultSecurityFilterChain chain = mock(DefaultSecurityFilterChain.class);
        when(http.build()).thenReturn(chain);
        
        SecurityFilterChain actual = new NacosAuthPluginWebConfig().securityFilterChain(http);
        
        assertSame(chain, actual);
    }
    
    @Test
    void testAuthenticationManagerBean() throws Exception {
        AuthenticationConfiguration authenticationConfiguration =
            mock(AuthenticationConfiguration.class);
        AuthenticationManager authenticationManager = mock(AuthenticationManager.class);
        when(authenticationConfiguration.getAuthenticationManager())
            .thenReturn(authenticationManager);
        
        try (MockedStatic<ApplicationUtils> mocked = mockStatic(ApplicationUtils.class)) {
            mocked.when(() -> ApplicationUtils.getBean(AuthenticationConfiguration.class))
                .thenReturn(authenticationConfiguration);
            
            assertSame(authenticationManager,
                new NacosAuthPluginWebConfig().authenticationManagerBean());
        }
    }
    
    private static MockEnvironment setValidAuthEnvironment(String systemType) {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty(Constants.Auth.NACOS_CORE_AUTH_SYSTEM_TYPE, systemType);
        environment.setProperty(Constants.Auth.NACOS_CORE_AUTH_SERVER_IDENTITY_KEY, "identity");
        environment.setProperty(Constants.Auth.NACOS_CORE_AUTH_SERVER_IDENTITY_VALUE, "value");
        environment.setProperty(Constants.Auth.NACOS_CORE_AUTH_ADMIN_ENABLED, "true");
        EnvUtil.setEnvironment(environment);
        setAuthSystemType(systemType);
        return environment;
    }
    
    private static void setAuthSystemType(String systemType) {
        NacosAuthConfig authConfig = mock(NacosAuthConfig.class);
        when(authConfig.getNacosAuthSystemType()).thenReturn(systemType);
        Map<String, NacosAuthConfig> configMap = new HashMap<>();
        configMap.put(NacosServerAuthConfig.NACOS_SERVER_AUTH_SCOPE, authConfig);
        ReflectionTestUtils.setField(NacosAuthConfigHolder.getInstance(), "nacosAuthConfigMap",
            configMap);
    }
}
