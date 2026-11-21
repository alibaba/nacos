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
import com.alibaba.nacos.plugin.auth.impl.oidc.OidcAuthPluginService;
import com.alibaba.nacos.plugin.auth.impl.oidc.authenticate.AuthorizationCodeHandler.AuthorizationRequestData;
import com.alibaba.nacos.plugin.auth.impl.oidc.identity.OidcUserMapper.OidcUser;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OidcLoginControllerTest {
    
    private OidcAuthPluginService pluginService;
    
    private OidcLoginController controller;
    
    @BeforeEach
    void setUp() {
        pluginService = mock(OidcAuthPluginService.class);
        controller = new OidcLoginController(pluginService);
    }
    
    @Test
    void testLoginRedirectsToAuthorizationUrlAndSetsTransactionCookie() throws Exception {
        MockHttpServletRequest request = httpRequest("https", 443, "/nacos");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(pluginService.createAuthorizationRequest(
            "https://nacos.example/nacos/v1/auth/oidc/callback"))
            .thenReturn(new AuthorizationRequestData("http://idp/authorize", "state-val",
                "verifier-val", "tx-cookie-val"));
        
        controller.login(request, response);
        
        assertEquals("http://idp/authorize", response.getRedirectedUrl());
        String txHeader = findSetCookieHeader(response, OidcLoginController.OIDC_TX_COOKIE_NAME);
        assertNotNull(txHeader);
        assertTrue(txHeader.startsWith("nacos_oidc_tx=tx-cookie-val;"));
        assertTrue(txHeader.contains("; Path=/nacos/"));
        assertTrue(txHeader.contains("; Max-Age=600"));
        assertTrue(txHeader.contains("; Secure"));
        assertTrue(txHeader.contains("; HttpOnly"));
        assertTrue(txHeader.contains("; SameSite=Lax"));
        
        MockHttpServletRequest httpReq = httpRequest("http", 8848, "");
        MockHttpServletResponse httpResp = new MockHttpServletResponse();
        when(pluginService.createAuthorizationRequest(
            "http://nacos.example:8848/v1/auth/oidc/callback"))
            .thenReturn(new AuthorizationRequestData("http://idp/authorize", "state-2",
                "verifier-2", "tx-2"));
        
        controller.login(httpReq, httpResp);
        
        String httpTxHeader =
            findSetCookieHeader(httpResp, OidcLoginController.OIDC_TX_COOKIE_NAME);
        assertNotNull(httpTxHeader);
        assertTrue(httpTxHeader.contains("; Path=/"));
        assertFalse(httpTxHeader.contains("; Secure"));
    }
    
    @Test
    void testLoginSendsErrorWhenAuthorizationUrlFails() throws Exception {
        MockHttpServletRequest request = httpRequest("http", 80, "");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(pluginService.createAuthorizationRequest(
            "http://nacos.example/v1/auth/oidc/callback"))
            .thenThrow(new AccessException("bad config"));
        
        controller.login(request, response);
        
        assertEquals(MockHttpServletResponse.SC_INTERNAL_SERVER_ERROR, response.getStatus());
        assertTrue(response.getErrorMessage().startsWith("Failed to initiate login:"));
    }
    
    @Test
    void testCallbackRedirectsIdpError() throws IOException {
        MockHttpServletRequest request = httpRequest("http", 80, "");
        request.setCookies(new Cookie(OidcLoginController.OIDC_TX_COOKIE_NAME, "tx-err"));
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
        String clearedTx = findSetCookieHeader(response, OidcLoginController.OIDC_TX_COOKIE_NAME);
        assertNotNull(clearedTx);
        assertTrue(clearedTx.contains("; Max-Age=0"));
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
        assertTrue(findSetCookieHeader(missingCodeResponse, OidcLoginController.OIDC_TX_COOKIE_NAME)
            .contains("; Max-Age=0"));
        assertTrue(
            findSetCookieHeader(missingStateResponse, OidcLoginController.OIDC_TX_COOKIE_NAME)
                .contains("; Max-Age=0"));
    }
    
    @Test
    void testCallbackSetsTokenCookiesAndRedirectsHome() throws Exception {
        MockHttpServletRequest request = httpRequest("https", 443, "/nacos");
        request.setCookies(new Cookie(OidcLoginController.OIDC_TX_COOKIE_NAME, "tx-cookie"));
        MockHttpServletResponse response = new MockHttpServletResponse();
        OidcUser user = new OidcUser();
        user.setUsername("alice bob");
        user.setToken("token-value");
        user.setGlobalAdmin(true);
        user.setIdToken("id-token-value");
        when(pluginService.exchangeCodeForUser("code", "state",
            "https://nacos.example/nacos/v1/auth/oidc/callback", "tx-cookie")).thenReturn(user);
        
        Result<Map<String, Object>> result =
            controller.callback("code", "state", null, null, request, response);
        
        assertNull(result);
        assertEquals("https://nacos.example/nacos/#/", response.getRedirectedUrl());
        Cookie accessTokenCookie = response.getCookie("accessToken");
        Cookie usernameCookie = response.getCookie("username");
        Cookie globalAdminCookie = response.getCookie("globalAdmin");
        assertEquals("token-value", accessTokenCookie.getValue());
        assertEquals("alice+bob", usernameCookie.getValue());
        assertNotNull(globalAdminCookie);
        assertEquals("true", globalAdminCookie.getValue());
        assertFalse(accessTokenCookie.isHttpOnly());
        assertFalse(globalAdminCookie.isHttpOnly());
        assertTrue(accessTokenCookie.getSecure());
        assertTrue(globalAdminCookie.getSecure());
        assertEquals("/nacos/", accessTokenCookie.getPath());
        assertEquals("/nacos/", globalAdminCookie.getPath());
        assertEquals(60, accessTokenCookie.getMaxAge());
        assertEquals(60, globalAdminCookie.getMaxAge());
        String clearedTx = findSetCookieHeader(response, OidcLoginController.OIDC_TX_COOKIE_NAME);
        assertNotNull(clearedTx);
        assertTrue(clearedTx.contains("; Max-Age=0"));
        String idTokenHeader =
            findSetCookieHeader(response, OidcLoginController.OIDC_ID_TOKEN_COOKIE_NAME);
        assertNotNull(idTokenHeader);
        assertTrue(idTokenHeader.startsWith("nacos_oidc_id_token=id-token-value;"));
        assertTrue(idTokenHeader.contains("; Path=/nacos/"));
        assertTrue(idTokenHeader.contains("; Secure"));
        assertTrue(idTokenHeader.contains("; HttpOnly"));
        assertTrue(idTokenHeader.contains("; SameSite=Lax"));
        
        MockHttpServletRequest rootRequest = httpRequest("https", 8443, "");
        rootRequest.setCookies(new Cookie(OidcLoginController.OIDC_TX_COOKIE_NAME, "root-tx"));
        MockHttpServletResponse rootResponse = new MockHttpServletResponse();
        OidcUser nonAdmin = new OidcUser();
        nonAdmin.setUsername("bob");
        nonAdmin.setToken("root-token");
        nonAdmin.setGlobalAdmin(false);
        when(pluginService.exchangeCodeForUser("root-code", "state",
            "https://nacos.example:8443/v1/auth/oidc/callback", "root-tx"))
            .thenReturn(nonAdmin);
        
        assertNull(controller.callback("root-code", "state", null, null, rootRequest,
            rootResponse));
        
        assertEquals("https://nacos.example:8443/#/", rootResponse.getRedirectedUrl());
        assertEquals("/", rootResponse.getCookie("accessToken").getPath());
        assertEquals("false", rootResponse.getCookie("globalAdmin").getValue());
        assertEquals("/", rootResponse.getCookie("globalAdmin").getPath());
        String clearedIdToken =
            findSetCookieHeader(rootResponse, OidcLoginController.OIDC_ID_TOKEN_COOKIE_NAME);
        assertNotNull(clearedIdToken);
        assertTrue(clearedIdToken.contains("; Max-Age=0"));
    }
    
    @Test
    void testCallbackDegradesWhenIdTokenTooLongOrUnsafe() throws Exception {
        MockHttpServletRequest request = httpRequest("http", 8080, "");
        request.setCookies(new Cookie(OidcLoginController.OIDC_TX_COOKIE_NAME, "tx"));
        MockHttpServletResponse tooLongResponse = new MockHttpServletResponse();
        OidcUser longUser = new OidcUser();
        longUser.setUsername("alice");
        longUser.setToken("tok");
        longUser.setIdToken("a".repeat(3801));
        when(pluginService.exchangeCodeForUser("c1", "s1",
            "http://nacos.example:8080/v1/auth/oidc/callback", "tx")).thenReturn(longUser);
        
        assertNull(controller.callback("c1", "s1", null, null, request, tooLongResponse));
        assertEquals("http://nacos.example:8080/#/", tooLongResponse.getRedirectedUrl());
        String longIdHeader =
            findSetCookieHeader(tooLongResponse, OidcLoginController.OIDC_ID_TOKEN_COOKIE_NAME);
        assertNotNull(longIdHeader);
        assertTrue(longIdHeader.contains("; Max-Age=0"));
        assertFalse(longIdHeader.contains("aaaa"));
        
        MockHttpServletResponse unsafeResponse = new MockHttpServletResponse();
        OidcUser unsafeUser = new OidcUser();
        unsafeUser.setUsername("alice");
        unsafeUser.setToken("tok");
        unsafeUser.setIdToken("bad;token\r\n");
        when(pluginService.exchangeCodeForUser("c2", "s2",
            "http://nacos.example:8080/v1/auth/oidc/callback", "tx")).thenReturn(unsafeUser);
        
        assertNull(controller.callback("c2", "s2", null, null, request, unsafeResponse));
        String unsafeHeader =
            findSetCookieHeader(unsafeResponse, OidcLoginController.OIDC_ID_TOKEN_COOKIE_NAME);
        assertNotNull(unsafeHeader);
        assertTrue(unsafeHeader.contains("; Max-Age=0"));
    }
    
    @Test
    void testCallbackRedirectsAccessExceptionAndUnexpectedException() throws Exception {
        MockHttpServletRequest request = httpRequest("http", 8080, "");
        request.setCookies(new Cookie(OidcLoginController.OIDC_TX_COOKIE_NAME, "tx"));
        MockHttpServletResponse accessExceptionResponse = new MockHttpServletResponse();
        MockHttpServletResponse unexpectedResponse = new MockHttpServletResponse();
        when(pluginService.exchangeCodeForUser("bad", "state",
            "http://nacos.example:8080/v1/auth/oidc/callback", "tx"))
            .thenThrow(new AccessException("invalid state"));
        when(pluginService.exchangeCodeForUser("boom", "state",
            "http://nacos.example:8080/v1/auth/oidc/callback", "tx"))
            .thenThrow(new IllegalStateException("broken"));
        
        assertNull(controller.callback("bad", "state", null, null, request,
            accessExceptionResponse));
        assertNull(controller.callback("boom", "state", null, null, request,
            unexpectedResponse));
        
        assertEquals("http://nacos.example:8080/#/login?error=invalid+state",
            accessExceptionResponse.getRedirectedUrl());
        assertEquals("http://nacos.example:8080/#/login?error=Authentication+failed%3A+broken",
            unexpectedResponse.getRedirectedUrl());
        assertTrue(findSetCookieHeader(accessExceptionResponse,
            OidcLoginController.OIDC_TX_COOKIE_NAME).contains("; Max-Age=0"));
        assertTrue(findSetCookieHeader(unexpectedResponse,
            OidcLoginController.OIDC_TX_COOKIE_NAME).contains("; Max-Age=0"));
    }
    
    @Test
    void testLogoutRedirectsWhenProviderLogoutUrlExists() throws Exception {
        MockHttpServletRequest request = httpRequest("http", 80, "");
        request.setCookies(
            new Cookie(OidcLoginController.OIDC_ID_TOKEN_COOKIE_NAME, "cookie-id-token"));
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(pluginService.buildLogoutUrl("id-token", "http://nacos.example"))
            .thenReturn("http://idp/logout");
        
        Result<String> result = controller.logout("id-token", true, request, response);
        
        assertNull(result);
        assertEquals("http://idp/logout", response.getRedirectedUrl());
        String clearedId =
            findSetCookieHeader(response, OidcLoginController.OIDC_ID_TOKEN_COOKIE_NAME);
        assertNotNull(clearedId);
        assertTrue(clearedId.contains("; Max-Age=0"));
    }
    
    @Test
    void testLogoutUsesIdTokenCookieWhenParamMissingAndClearsCookie() throws Exception {
        MockHttpServletRequest request = httpRequest("https", 443, "/nacos");
        request.setCookies(
            new Cookie(OidcLoginController.OIDC_ID_TOKEN_COOKIE_NAME, "cookie-id-token"));
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(pluginService.buildLogoutUrl("cookie-id-token", "https://nacos.example/nacos"))
            .thenReturn("https://idp/logout?id_token_hint=cookie-id-token");
        
        Result<String> result = controller.logout(null, true, request, response);
        
        assertNull(result);
        assertEquals("https://idp/logout?id_token_hint=cookie-id-token",
            response.getRedirectedUrl());
        String clearedId =
            findSetCookieHeader(response, OidcLoginController.OIDC_ID_TOKEN_COOKIE_NAME);
        assertNotNull(clearedId);
        assertTrue(clearedId.contains("; Max-Age=0"));
        assertTrue(clearedId.contains("; Path=/nacos/"));
        assertTrue(clearedId.contains("; Secure"));
    }
    
    @Test
    void testLogoutRedirectsToLoginWhenEndSessionEndpointMissing() throws IOException {
        MockHttpServletRequest request = httpRequest("http", 80, "");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(pluginService.buildLogoutUrl(null, "http://nacos.example")).thenReturn(null);
        
        Result<String> result = controller.logout(null, true, request, response);
        
        assertNull(result);
        assertEquals("http://nacos.example/#/login", response.getRedirectedUrl());
        
        MockHttpServletRequest ctxRequest = httpRequest("https", 443, "/nacos");
        MockHttpServletResponse ctxResponse = new MockHttpServletResponse();
        when(pluginService.buildLogoutUrl(null, "https://nacos.example/nacos")).thenReturn("  ");
        
        Result<String> ctxResult = controller.logout(null, true, ctxRequest, ctxResponse);
        
        assertNull(ctxResult);
        assertEquals("https://nacos.example/nacos/#/login", ctxResponse.getRedirectedUrl());
    }
    
    @Test
    void testLogoutReturnsSuccessWhenNoRedirect() throws IOException {
        MockHttpServletRequest request = httpRequest("http", 80, "");
        MockHttpServletResponse response = new MockHttpServletResponse();
        
        Result<String> result = controller.logout(null, false, request, response);
        
        assertEquals("Logged out successfully", result.getData());
        assertTrue(findSetCookieHeader(response, OidcLoginController.OIDC_ID_TOKEN_COOKIE_NAME)
            .contains("; Max-Age=0"));
    }
    
    @Test
    void testLogoutReturnsFailureWhenHandlerThrows() throws Exception {
        MockHttpServletRequest request = httpRequest("http", 80, "");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(pluginService.buildLogoutUrl("id-token", "http://nacos.example"))
            .thenThrow(new IllegalStateException("broken"));
        
        Result<String> result = controller.logout("id-token", true, request, response);
        
        assertEquals(500, result.getCode());
        assertEquals("Logout failed", result.getMessage());
    }
    
    @Test
    void testGetConfigReturnsOidcFrontendConfig() {
        when(pluginService.isConfigurationValid()).thenReturn(true);
        
        Result<Map<String, Object>> result = controller.getConfig();
        
        assertTrue((Boolean) result.getData().get("enabled"));
        assertEquals("oidc", result.getData().get("authType"));
        assertEquals("/v1/auth/oidc/login", result.getData().get("loginUrl"));
        assertEquals(false, result.getData().get("userManagementEnabled"));
    }
    
    @Test
    void testGetConfigReturnsFailureWhenConfigThrows() {
        when(pluginService.isConfigurationValid()).thenThrow(new IllegalStateException("broken"));
        
        Result<Map<String, Object>> result = controller.getConfig();
        
        assertEquals(500, result.getCode());
        assertEquals("Failed to get configuration", result.getMessage());
    }
    
    private String findSetCookieHeader(MockHttpServletResponse response, String name) {
        List<String> headers = response.getHeaders(HttpHeaders.SET_COOKIE);
        String prefix = name + "=";
        for (String header : headers) {
            if (header.startsWith(prefix)) {
                return header;
            }
        }
        return null;
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
