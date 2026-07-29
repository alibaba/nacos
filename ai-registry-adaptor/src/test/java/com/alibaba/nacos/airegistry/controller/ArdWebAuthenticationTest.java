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

package com.alibaba.nacos.airegistry.controller;

import com.alibaba.nacos.api.common.ApiType;
import com.alibaba.nacos.airegistry.config.ArdWebConfiguration;
import com.alibaba.nacos.airegistry.model.ard.ArdListResponse;
import com.alibaba.nacos.airegistry.service.ard.ArdArtifactService;
import com.alibaba.nacos.airegistry.service.ard.ArdSearchService;
import com.alibaba.nacos.auth.HttpProtocolAuthService;
import com.alibaba.nacos.auth.serveridentity.ServerIdentityResult;
import com.alibaba.nacos.auth.annotation.Secured;
import com.alibaba.nacos.auth.config.NacosAuthConfig;
import com.alibaba.nacos.auth.config.NacosAuthConfigHolder;
import com.alibaba.nacos.core.auth.AuthFilter;
import com.alibaba.nacos.core.auth.InnerApiAuthEnabled;
import com.alibaba.nacos.core.code.ControllerMethodsCache;
import com.alibaba.nacos.core.context.RequestContextHolder;
import com.alibaba.nacos.plugin.auth.api.AuthResult;
import com.alibaba.nacos.plugin.auth.api.IdentityContext;
import com.alibaba.nacos.plugin.auth.api.Permission;
import com.alibaba.nacos.plugin.auth.api.Resource;
import com.alibaba.nacos.plugin.auth.constant.Constants;
import com.alibaba.nacos.sys.env.EnvUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.security.autoconfigure.actuate.web.servlet.ManagementWebSecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.web.server.servlet.context.AnnotationConfigServletWebServerApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.support.StandardServletEnvironment;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies authentication inside the independent ARD web context.
 *
 * @author nacos
 */
class ArdWebAuthenticationTest {
    
    @Test
    void adaptorContextShouldRejectInvalidCredentialsAndPropagateValidIdentity() throws Exception {
        ConfigurableEnvironment previousEnvironment = EnvUtil.getEnvironment();
        ConfigurableEnvironment environment = new StandardServletEnvironment();
        environment.getPropertySources().addFirst(
            new MapPropertySource("test", Map.of("server.port", "0",
                "nacos.ai.ard.enabled", "true",
                "management.elastic.metrics.export.enabled", "false",
                "management.influx.metrics.export.enabled", "false",
                Constants.Auth.NACOS_CORE_AUTH_ENABLED, "true",
                Constants.Auth.NACOS_PLUGIN_AUTH_TYPE, "test",
                Constants.Auth.NACOS_CORE_AUTH_SERVER_IDENTITY_KEY, "nacos",
                Constants.Auth.NACOS_CORE_AUTH_SERVER_IDENTITY_VALUE, "nacos")));
        EnvUtil.setEnvironment(environment);
        NacosAuthConfig authConfig = NacosAuthConfigHolder.getInstance()
            .getNacosAuthConfigByScope(ApiType.OPEN_API.name());
        boolean previousAuthEnabled = authConfig.isAuthEnabled();
        String previousAuthSystemType = authConfig.getNacosAuthSystemType();
        String previousServerIdentityKey = authConfig.getServerIdentityKey();
        String previousServerIdentityValue = authConfig.getServerIdentityValue();
        ReflectionTestUtils.setField(authConfig, "authEnabled", true);
        ReflectionTestUtils.setField(authConfig, "nacosAuthSystemType", "test");
        ReflectionTestUtils.setField(authConfig, "serverIdentityKey", "nacos");
        ReflectionTestUtils.setField(authConfig, "serverIdentityValue", "nacos");
        
        try (AnnotationConfigServletWebServerApplicationContext context =
            start(environment)) {
            AuthFilter authFilter = context.getBean(AuthFilter.class);
            HttpProtocolAuthService protocolAuthService = mock(HttpProtocolAuthService.class);
            ReflectionTestUtils.setField(authFilter, "protocolAuthService", protocolAuthService);
            prepareProtocolAuth(protocolAuthService);
            IdentityContext rejectedIdentity = new IdentityContext();
            when(protocolAuthService.parseIdentity(any(HttpServletRequest.class)))
                .thenReturn(rejectedIdentity);
            when(protocolAuthService.validateIdentity(eq(rejectedIdentity), any(Resource.class)))
                .thenReturn(AuthResult.failureResult(401, "invalid token"));
            
            int port = context.getWebServer().getPort();
            HttpResponse<String> rejected = get(port,
                "/v3/ai/ard/agents?namespaceId=public&pageSize=1&accessToken=invalid");
            
            assertEquals(401, rejected.statusCode());
            assertEquals("{\"errorCode\":\"UNAUTHENTICATED\","
                + "\"message\":\"Code: 401, Message: invalid token.\"}", rejected.body());
            
            reset(protocolAuthService);
            prepareProtocolAuth(protocolAuthService);
            IdentityContext acceptedIdentity = new IdentityContext();
            when(protocolAuthService.parseIdentity(any(HttpServletRequest.class)))
                .thenReturn(acceptedIdentity);
            when(protocolAuthService.validateIdentity(eq(acceptedIdentity), any(Resource.class)))
                .thenReturn(AuthResult.successResult());
            when(protocolAuthService.validateAuthority(eq(acceptedIdentity),
                any(Permission.class))).thenReturn(AuthResult.successResult());
            AtomicBoolean identityObserved = new AtomicBoolean();
            ArdSearchService searchService = context.getBean(ArdSearchService.class);
            when(searchService.list(any(), any(), any(), any(), any())).thenAnswer(invocation -> {
                identityObserved.set(RequestContextHolder.getContext().getAuthContext()
                    .getIdentityContext() == acceptedIdentity);
                return new ArdListResponse();
            });
            
            HttpResponse<String> accepted = get(port,
                "/v3/ai/ard/agents?namespaceId=public&pageSize=1&accessToken=valid");
            
            assertEquals(200, accepted.statusCode());
            assertTrue(identityObserved.get());
            verify(protocolAuthService).validateAuthority(eq(acceptedIdentity),
                any(Permission.class));
        } finally {
            ReflectionTestUtils.setField(authConfig, "authEnabled", previousAuthEnabled);
            ReflectionTestUtils.setField(authConfig, "nacosAuthSystemType",
                previousAuthSystemType);
            ReflectionTestUtils.setField(authConfig, "serverIdentityKey",
                previousServerIdentityKey);
            ReflectionTestUtils.setField(authConfig, "serverIdentityValue",
                previousServerIdentityValue);
            EnvUtil.setEnvironment(previousEnvironment);
        }
    }
    
    private AnnotationConfigServletWebServerApplicationContext start(
        ConfigurableEnvironment environment) {
        AnnotationConfigServletWebServerApplicationContext context =
            new AnnotationConfigServletWebServerApplicationContext();
        context.setEnvironment(environment);
        context.register(AdaptorApplication.class);
        context.refresh();
        return context;
    }
    
    private void prepareProtocolAuth(HttpProtocolAuthService protocolAuthService)
        throws Exception {
        when(protocolAuthService.checkServerIdentity(any(HttpServletRequest.class),
            any(Secured.class))).thenReturn(ServerIdentityResult.noMatched());
        when(protocolAuthService.enableAuth(any(Secured.class))).thenReturn(true);
        when(protocolAuthService.parseResource(any(HttpServletRequest.class),
            any(Secured.class))).thenReturn(Resource.EMPTY_RESOURCE);
    }
    
    private HttpResponse<String> get(int port, String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("http://127.0.0.1:" + port + path)).GET().build();
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    }
    
    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration(exclude = {DataSourceAutoConfiguration.class,
        SecurityAutoConfiguration.class, ServletWebSecurityAutoConfiguration.class,
        UserDetailsServiceAutoConfiguration.class,
        ManagementWebSecurityAutoConfiguration.class})
    @Import({ArdSearchController.class, ArdExceptionHandler.class, ArdWebConfiguration.class})
    static class AdaptorApplication {
        
        @Bean
        ControllerMethodsCache controllerMethodsCache() {
            return new ControllerMethodsCache();
        }
        
        @Bean
        InnerApiAuthEnabled innerApiAuthEnabled() {
            return mock(InnerApiAuthEnabled.class);
        }
        
        @Bean
        ArdSearchService ardSearchService() {
            return mock(ArdSearchService.class);
        }
        
        @Bean
        ArdArtifactService ardArtifactService() {
            return mock(ArdArtifactService.class);
        }
    }
}
