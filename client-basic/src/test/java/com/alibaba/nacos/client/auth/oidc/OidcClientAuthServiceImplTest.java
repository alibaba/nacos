/*
 * Copyright 1999-2024 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.client.auth.oidc;

import com.alibaba.nacos.plugin.auth.api.LoginIdentityContext;
import com.alibaba.nacos.plugin.auth.api.RequestResource;
import com.alibaba.nacos.plugin.auth.constant.OidcProtocolConstants;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test for {@link OidcClientAuthServiceImpl}.
 *
 * @author wangzji
 */
class OidcClientAuthServiceImplTest {
    
    private OidcClientAuthServiceImpl oidcClientAuthService;
    
    @BeforeEach
    void setUp() {
        oidcClientAuthService = new OidcClientAuthServiceImpl();
    }
    
    @Test
    void testLoginWithoutOidcConfig() {
        // Given: no OIDC properties configured
        Properties properties = new Properties();
        properties.setProperty("serverAddr", "localhost:8848");
        properties.setProperty("username", "nacos");
        properties.setProperty("password", "nacos");
        
        // When
        boolean result = oidcClientAuthService.login(properties);
        
        // Then: should succeed silently (OIDC not configured, let other plugins handle)
        assertTrue(result);
        
        // LoginIdentityContext should not contain accessToken
        LoginIdentityContext ctx = oidcClientAuthService.getLoginIdentityContext(
            RequestResource.configBuilder().build());
        assertNotNull(ctx);
        assertNull(ctx.getParameter(OidcProtocolConstants.ACCESS_TOKEN_PARAM));
    }
    
    @Test
    void testLoginWithoutOidcConfigSkipsOnSubsequentCalls() {
        // Given: no OIDC properties configured
        Properties properties = new Properties();
        
        // When: call login multiple times
        boolean result1 = oidcClientAuthService.login(properties);
        boolean result2 = oidcClientAuthService.login(properties);
        
        // Then: both should succeed
        assertTrue(result1);
        assertTrue(result2);
    }
    
    @Test
    void testLoginWithPartialOidcConfigMissingSecret() {
        // Given: only client-id but no client-secret
        Properties properties = new Properties();
        properties.setProperty(OidcClientConstants.PROP_CLIENT_ID, "my-client");
        properties.setProperty(OidcClientConstants.PROP_ISSUER_URI, "https://idp.example.com");
        
        // When
        boolean result = oidcClientAuthService.login(properties);
        
        // Then: should succeed (incomplete OIDC config, skip)
        assertTrue(result);
        LoginIdentityContext ctx = oidcClientAuthService.getLoginIdentityContext(
            RequestResource.configBuilder().build());
        assertNull(ctx.getParameter(OidcProtocolConstants.ACCESS_TOKEN_PARAM));
    }
    
    @Test
    void testLoginWithPartialOidcConfigMissingEndpoint() {
        // Given: client-id and secret but no issuer-uri or token-endpoint
        Properties properties = new Properties();
        properties.setProperty(OidcClientConstants.PROP_CLIENT_ID, "my-client");
        properties.setProperty(OidcClientConstants.PROP_CLIENT_SECRET, "my-secret");
        
        // When
        boolean result = oidcClientAuthService.login(properties);
        
        // Then: should succeed (incomplete OIDC config, skip)
        assertTrue(result);
        LoginIdentityContext ctx = oidcClientAuthService.getLoginIdentityContext(
            RequestResource.configBuilder().build());
        assertNull(ctx.getParameter(OidcProtocolConstants.ACCESS_TOKEN_PARAM));
    }
    
    @Test
    void testGetLoginIdentityContextReturnsEmptyByDefault() {
        LoginIdentityContext ctx = oidcClientAuthService.getLoginIdentityContext(
            RequestResource.configBuilder().build());
        assertNotNull(ctx);
    }
    
    private HttpServer httpServer;
    
    @AfterEach
    void afterEach() {
        if (httpServer != null) {
            httpServer.stop(0);
            httpServer = null;
        }
    }
    
    private static void writeJson(HttpExchange exchange, int status, String body)
        throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
    
    private void startServer(String path, HttpHandler handler) throws IOException {
        httpServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        httpServer.createContext(path, handler);
        httpServer.start();
    }
    
    private String baseUri() {
        return "http://127.0.0.1:" + httpServer.getAddress().getPort();
    }
    
    @Test
    void testLoginFullFlowSuccess() throws Exception {
        startServer("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            if (path.endsWith("/.well-known/openid-configuration")) {
                writeJson(exchange, 200, "{\"token_endpoint\":\"" + baseUri() + "/token\"}");
            } else if (path.endsWith("/token")) {
                writeJson(exchange, 200, "{\"access_token\":\"abc\",\"expires_in\":3600}");
            } else {
                writeJson(exchange, 404, "");
            }
        });
        Properties properties = new Properties();
        properties.setProperty(OidcClientConstants.PROP_ISSUER_URI, baseUri());
        properties.setProperty(OidcClientConstants.PROP_CLIENT_ID, "my-client");
        properties.setProperty(OidcClientConstants.PROP_CLIENT_SECRET, "my-secret");
        
        assertTrue(oidcClientAuthService.login(properties));
        
        LoginIdentityContext ctx = oidcClientAuthService.getLoginIdentityContext(
            RequestResource.configBuilder().build());
        assertEquals("abc", ctx.getParameter(OidcProtocolConstants.ACCESS_TOKEN_PARAM));
        assertEquals(OidcProtocolConstants.BEARER_PREFIX + "abc",
            ctx.getParameter(OidcProtocolConstants.AUTHORIZATION_HEADER));
        
        // second call: token still fresh, no refresh needed → still success
        assertTrue(oidcClientAuthService.login(properties));
    }
    
    @Test
    void testLoginDiscoveryFails() throws Exception {
        startServer("/.well-known/openid-configuration",
            exchange -> writeJson(exchange, 500, ""));
        Properties properties = new Properties();
        properties.setProperty(OidcClientConstants.PROP_ISSUER_URI, baseUri());
        properties.setProperty(OidcClientConstants.PROP_CLIENT_ID, "my-client");
        properties.setProperty(OidcClientConstants.PROP_CLIENT_SECRET, "my-secret");
        
        assertFalse(oidcClientAuthService.login(properties));
    }
    
    @Test
    void testLoginTokenFetchFails() throws Exception {
        startServer("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            if (path.endsWith("/.well-known/openid-configuration")) {
                writeJson(exchange, 200, "{\"token_endpoint\":\"" + baseUri() + "/token\"}");
            } else {
                writeJson(exchange, 500, "");
            }
        });
        Properties properties = new Properties();
        properties.setProperty(OidcClientConstants.PROP_ISSUER_URI, baseUri());
        properties.setProperty(OidcClientConstants.PROP_CLIENT_ID, "my-client");
        properties.setProperty(OidcClientConstants.PROP_CLIENT_SECRET, "my-secret");
        
        assertFalse(oidcClientAuthService.login(properties));
    }
    
    @Test
    void testLoginCatchesUnexpectedException() {
        // Pass a Properties impl that throws on getProperty to trigger the catch (Throwable) block
        Properties properties = new Properties() {
            
            @Override
            public String getProperty(String key) {
                throw new RuntimeException("boom");
            }
        };
        assertFalse(oidcClientAuthService.login(properties));
    }
    
    @Test
    void testShutdownDoesNotThrow() {
        Assertions.assertDoesNotThrow(oidcClientAuthService::shutdown);
    }
}
