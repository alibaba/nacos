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

package com.alibaba.nacos.plugin.auth.impl.oidc.controller;

import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.plugin.auth.exception.AccessException;
import com.alibaba.nacos.plugin.auth.impl.oidc.authenticate.AuthorizationCodeHandler;
import com.alibaba.nacos.plugin.auth.impl.oidc.config.OidcAuthConfig;
import com.alibaba.nacos.plugin.auth.impl.oidc.identity.OidcUserMapper.OidcUser;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class OidcLoginControllerTest {
    
    private AuthorizationCodeHandler authHandler;
    
    private OidcAuthConfig config;
    
    private OidcLoginController controller;
    
    @BeforeEach
    void setUp() {
        authHandler = mock(AuthorizationCodeHandler.class);
        config = mock(OidcAuthConfig.class);
        controller = new OidcLoginController();
        ReflectionTestUtils.setField(controller, "authHandler", authHandler);
        ReflectionTestUtils.setField(controller, "config", config);
    }
    
    @Test
    void testLoginRedirectsToAuthorizationUrl() throws Exception {
        MockHttpServletRequest request = httpRequest("http", 8848, "/nacos");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(authHandler.buildAuthorizationUrl(
            "http://nacos.example:8848/nacos/v1/auth/oidc/callback"))
            .thenReturn("http://idp/authorize");
        
        controller.login(request, response);
        
        assertEquals("http://idp/authorize", response.getRedirectedUrl());
    }
    
    @Test
    void testLoginSendsErrorWhenAuthorizationUrlFails() throws Exception {
        MockHttpServletRequest request = httpRequest("http", 80, "");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(authHandler.buildAuthorizationUrl("http://nacos.example/v1/auth/oidc/callback"))
            .thenThrow(new AccessException("bad config"));
        
        controller.login(request, response);
        
        assertEquals(MockHttpServletResponse.SC_INTERNAL_SERVER_ERROR, response.getStatus());
        assertTrue(response.getErrorMessage().startsWith("Failed to initiate login:"));
    }
    
    @Test
    void testCallbackRedirectsIdpError() throws IOException {
        MockHttpServletRequest request = httpRequest("http", 80, "");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockHttpServletResponse fallbackResponse = new MockHttpServletResponse();
        
        Result<Map<String, Object>> result = controller.callback(null, null, "access_denied",
            "no access", request, response);
        Result<Map<String, Object>> fallbackResult = controller.callback(null, null,
            "server_error", null, request, fallbackResponse);
        
        assertNull(result);
        assertNull(fallbackResult);
        assertEquals("http://nacos.example/#/login?error=no+access", response.getRedirectedUrl());
        assertEquals("http://nacos.example/#/login?error=server_error",
            fallbackResponse.getRedirectedUrl());
    }
    
    @Test
    void testCallbackRedirectsMissingRequiredParameters() throws IOException {
        MockHttpServletRequest request = httpRequest("http", 80, "");
        MockHttpServletResponse missingCodeResponse = new MockHttpServletResponse();
        MockHttpServletResponse missingStateResponse = new MockHttpServletResponse();
        
        assertNull(controller.callback("", "state", null, null, request, missingCodeResponse));
        assertNull(controller.callback("code", "", null, null, request, missingStateResponse));
        
        assertEquals("http://nacos.example/#/login?error=Missing+authorization+code",
            missingCodeResponse.getRedirectedUrl());
        assertEquals("http://nacos.example/#/login?error=Missing+state+parameter",
            missingStateResponse.getRedirectedUrl());
    }
    
    @Test
    void testCallbackSetsTokenCookiesAndRedirectsHome() throws Exception {
        MockHttpServletRequest request = httpRequest("https", 443, "/nacos");
        MockHttpServletResponse response = new MockHttpServletResponse();
        OidcUser user = new OidcUser();
        user.setUsername("alice bob");
        user.setToken("token-value");
        when(authHandler.exchangeCodeForUser("code", "state",
            "https://nacos.example/nacos/v1/auth/oidc/callback")).thenReturn(user);
        
        Result<Map<String, Object>> result =
            controller.callback("code", "state", null, null, request, response);
        
        assertNull(result);
        assertEquals("https://nacos.example/nacos/#/", response.getRedirectedUrl());
        Cookie accessTokenCookie = response.getCookie("accessToken");
        Cookie usernameCookie = response.getCookie("username");
        assertEquals("token-value", accessTokenCookie.getValue());
        assertEquals("alice+bob", usernameCookie.getValue());
        assertFalse(accessTokenCookie.isHttpOnly());
        assertTrue(accessTokenCookie.getSecure());
        assertEquals("/nacos/", accessTokenCookie.getPath());
        assertEquals(60, accessTokenCookie.getMaxAge());
        
        MockHttpServletRequest rootRequest = httpRequest("https", 8443, "");
        MockHttpServletResponse rootResponse = new MockHttpServletResponse();
        when(authHandler.exchangeCodeForUser("root-code", "state",
            "https://nacos.example:8443/v1/auth/oidc/callback")).thenReturn(user);
        
        assertNull(controller.callback("root-code", "state", null, null, rootRequest,
            rootResponse));
        
        assertEquals("https://nacos.example:8443/#/", rootResponse.getRedirectedUrl());
        assertEquals("/", rootResponse.getCookie("accessToken").getPath());
    }
    
    @Test
    void testCallbackRedirectsAccessExceptionAndUnexpectedException() throws Exception {
        MockHttpServletRequest request = httpRequest("http", 8080, "");
        MockHttpServletResponse accessExceptionResponse = new MockHttpServletResponse();
        MockHttpServletResponse unexpectedResponse = new MockHttpServletResponse();
        when(authHandler.exchangeCodeForUser("bad", "state",
            "http://nacos.example:8080/v1/auth/oidc/callback"))
            .thenThrow(new AccessException("invalid state"));
        when(authHandler.exchangeCodeForUser("boom", "state",
            "http://nacos.example:8080/v1/auth/oidc/callback"))
            .thenThrow(new IllegalStateException("broken"));
        
        assertNull(controller.callback("bad", "state", null, null, request,
            accessExceptionResponse));
        assertNull(controller.callback("boom", "state", null, null, request,
            unexpectedResponse));
        
        assertEquals("http://nacos.example:8080/#/login?error=invalid+state",
            accessExceptionResponse.getRedirectedUrl());
        assertEquals("http://nacos.example:8080/#/login?error=Authentication+failed%3A+broken",
            unexpectedResponse.getRedirectedUrl());
    }
    
    @Test
    void testLogoutRedirectsWhenProviderLogoutUrlExists() throws Exception {
        MockHttpServletRequest request = httpRequest("http", 80, "");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(authHandler.buildLogoutUrl("id-token", "http://nacos.example"))
            .thenReturn("http://idp/logout");
        
        Result<String> result = controller.logout("id-token", true, request, response);
        
        assertNull(result);
        assertEquals("http://idp/logout", response.getRedirectedUrl());
    }
    
    @Test
    void testLogoutReturnsSuccessWhenNoRedirect() throws IOException {
        MockHttpServletRequest request = httpRequest("http", 80, "");
        MockHttpServletResponse response = new MockHttpServletResponse();
        
        Result<String> result = controller.logout(null, false, request, response);
        
        assertEquals("Logged out successfully", result.getData());
    }
    
    @Test
    void testLogoutReturnsFailureWhenHandlerThrows() throws Exception {
        MockHttpServletRequest request = httpRequest("http", 80, "");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(authHandler.buildLogoutUrl("id-token", "http://nacos.example"))
            .thenThrow(new IllegalStateException("broken"));
        
        Result<String> result = controller.logout("id-token", true, request, response);
        
        assertEquals(500, result.getCode());
        assertEquals("Logout failed", result.getMessage());
    }
    
    @Test
    void testGetConfigReturnsOidcFrontendConfig() {
        when(config.isValid()).thenReturn(true);
        
        Result<Map<String, Object>> result = controller.getConfig();
        
        assertTrue((Boolean) result.getData().get("enabled"));
        assertEquals("oidc", result.getData().get("authType"));
        assertEquals("/v1/auth/oidc/login", result.getData().get("loginUrl"));
        assertEquals(false, result.getData().get("userManagementEnabled"));
    }
    
    @Test
    void testGetConfigReturnsFailureWhenConfigThrows() {
        when(config.isValid()).thenThrow(new IllegalStateException("broken"));
        
        Result<Map<String, Object>> result = controller.getConfig();
        
        assertEquals(500, result.getCode());
        assertEquals("Failed to get configuration", result.getMessage());
    }
    
    @Test
    void testInitializeIfNeededLoadsSingletons() {
        OidcLoginController lazyController = new OidcLoginController();
        OidcAuthConfig singletonConfig = mock(OidcAuthConfig.class);
        AuthorizationCodeHandler singletonHandler = mock(AuthorizationCodeHandler.class);
        when(singletonConfig.isValid()).thenReturn(true);
        
        try (MockedStatic<OidcAuthConfig> configStatic = mockStatic(OidcAuthConfig.class);
            MockedStatic<AuthorizationCodeHandler> handlerStatic =
                mockStatic(AuthorizationCodeHandler.class)) {
            configStatic.when(OidcAuthConfig::getInstance).thenReturn(singletonConfig);
            handlerStatic.when(AuthorizationCodeHandler::getInstance).thenReturn(singletonHandler);
            
            Result<Map<String, Object>> result = lazyController.getConfig();
            
            assertTrue((Boolean) result.getData().get("enabled"));
        }
    }
    
    private MockHttpServletRequest httpRequest(String scheme, int port, String contextPath) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setScheme(scheme);
        request.setServerName("nacos.example");
        request.setServerPort(port);
        request.setContextPath(contextPath);
        return request;
    }
}
