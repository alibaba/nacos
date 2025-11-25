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

import com.alibaba.nacos.console.filter.NacosConsoleAuthFilter;
import com.alibaba.nacos.core.code.ControllerMethodsCache;
import com.alibaba.nacos.core.controller.compatibility.ApiCompatibilityFilter;
import com.alibaba.nacos.core.exception.NacosApiExceptionHandler;
import com.alibaba.nacos.core.paramcheck.ParamCheckerFilter;
import com.alibaba.nacos.plugin.auth.constant.Constants;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;
import org.springframework.security.web.DefaultSecurityFilterChain;
import org.springframework.security.web.SecurityFilterChain;

import java.time.ZoneId;
import java.util.Iterator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConsoleWebConfigTest {
    
    @Mock
    private ControllerMethodsCache methodsCache;
    
    private ConfigurableEnvironment cachedEnvironment;
    
    ConsoleWebConfig consoleWebConfig;
    
    @BeforeEach
    void setUp() {
        cachedEnvironment = EnvUtil.getEnvironment();
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty(Constants.Auth.NACOS_CORE_AUTH_ADMIN_ENABLED, "false");
        EnvUtil.setEnvironment(environment);
        consoleWebConfig = new ConsoleWebConfig(methodsCache);
    }
    
    @Test
    void init() {
        consoleWebConfig.init();
        verify(methodsCache).initClassMethod("com.alibaba.nacos.console.controller");
        EnvUtil.setEnvironment(cachedEnvironment);
    }
    
    @Test
    void corsFilter() {
        assertNotNull(consoleWebConfig.corsFilter());
    }
    
    @Test
    void corsFilterWithCustomConfiguration() {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty(Constants.Auth.NACOS_CORE_AUTH_ADMIN_ENABLED, "false");
        environment.setProperty("nacos.console.cors.allow-credentials", "false");
        environment.setProperty("nacos.console.cors.allowed-headers", "Content-Type");
        environment.setProperty("nacos.console.cors.max-age", "3600");
        environment.setProperty("nacos.console.cors.allowed-methods", "GET,POST");
        environment.setProperty("nacos.console.cors.allowed-origins", "http://localhost:8080");
        EnvUtil.setEnvironment(environment);
        
        assertNotNull(consoleWebConfig.corsFilter());
        EnvUtil.setEnvironment(cachedEnvironment);
    }
    
    @Test
    void xssFilter() {
        assertNotNull(consoleWebConfig.xssFilter());
    }
    
    @Test
    void authFilterRegistration() {
        FilterRegistrationBean<NacosConsoleAuthFilter> registration = consoleWebConfig.authFilterRegistration(
                consoleWebConfig.consoleAuthFilter(methodsCache));
        assertInstanceOf(NacosConsoleAuthFilter.class, registration.getFilter());
        assertEquals("consoleAuthFilter", registration.getFilterName());
        assertEquals(6, registration.getOrder());
        assertEquals(1, registration.getUrlPatterns().size());
        assertEquals("/*", registration.getUrlPatterns().iterator().next());
    }
    
    @Test
    void consoleParamCheckerFilterRegistration() {
        FilterRegistrationBean<ParamCheckerFilter> registration = consoleWebConfig.consoleParamCheckerFilterRegistration(
                consoleWebConfig.consoleParamCheckerFilter(methodsCache));
        assertInstanceOf(ParamCheckerFilter.class, registration.getFilter());
        assertEquals("consoleParamCheckerFilter", registration.getFilterName());
        assertEquals(8, registration.getOrder());
        assertEquals(1, registration.getUrlPatterns().size());
        assertEquals("/*", registration.getUrlPatterns().iterator().next());
    }
    
    @Test
    void consoleApiCompatibilityFilterRegistration() {
        FilterRegistrationBean<ApiCompatibilityFilter> registration = consoleWebConfig.consoleApiCompatibilityFilterRegistration(
                consoleWebConfig.consoleApiCompatibilityFilter(methodsCache));
        assertInstanceOf(ApiCompatibilityFilter.class, registration.getFilter());
        assertEquals("consoleApiCompatibilityFilter", registration.getFilterName());
        assertEquals(5, registration.getOrder());
        assertEquals(2, registration.getUrlPatterns().size());
        Iterator<String> iterator = registration.getUrlPatterns().iterator();
        assertEquals("/v1/*", iterator.next());
        assertEquals("/v2/*", iterator.next());
    }
    
    @Test
    void jacksonObjectMapperCustomization() {
        assertNotNull(consoleWebConfig.jacksonObjectMapperCustomization());
        Jackson2ObjectMapperBuilder builder = Mockito.mock(Jackson2ObjectMapperBuilder.class);
        consoleWebConfig.jacksonObjectMapperCustomization().customize(builder);
        verify(builder).timeZone(ZoneId.systemDefault().toString());
    }
    
    @Test
    void securityFilterChain() throws Exception {
        HttpSecurity mockHttpSecurity = Mockito.mock(HttpSecurity.class);
        AuthorizeHttpRequestsConfigurer.AuthorizationManagerRequestMatcherRegistry mockRegistry = Mockito.mock(
                AuthorizeHttpRequestsConfigurer.AuthorizationManagerRequestMatcherRegistry.class);
        when(mockHttpSecurity.authorizeHttpRequests(any())).then((Answer<HttpSecurity>) invocation -> {
            Customizer customizer = invocation.getArgument(0, Customizer.class);
            customizer.customize(mockRegistry);
            return (HttpSecurity) invocation.getMock();
        });
        DefaultSecurityFilterChain mockSecurityFilterChai = Mockito.mock(DefaultSecurityFilterChain.class);
        when(mockHttpSecurity.build()).thenReturn(mockSecurityFilterChai);
        AuthorizeHttpRequestsConfigurer.AuthorizedUrl mockAuthorizedUrl = Mockito.mock(
                AuthorizeHttpRequestsConfigurer.AuthorizedUrl.class);
        when(mockRegistry.requestMatchers("/**")).thenReturn(mockAuthorizedUrl);
        SecurityFilterChain result = consoleWebConfig.securityFilterChain(mockHttpSecurity);
        assertEquals(mockSecurityFilterChai, result);
        verify(mockAuthorizedUrl).permitAll();
    }
    
    @Test
    void nacosApiExceptionHandler() {
        assertInstanceOf(NacosApiExceptionHandler.class, consoleWebConfig.nacosApiExceptionHandler());
    }
}