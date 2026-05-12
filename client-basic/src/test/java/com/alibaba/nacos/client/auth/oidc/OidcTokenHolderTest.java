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

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test for {@link OidcTokenHolder}.
 *
 * @author wangzji
 */
class OidcTokenHolderTest {
    
    private OidcTokenHolder tokenHolder;
    
    private HttpServer httpServer;
    
    @BeforeEach
    void setUp() {
        tokenHolder = new OidcTokenHolder();
    }
    
    @AfterEach
    void tearDown() {
        if (httpServer != null) {
            httpServer.stop(0);
            httpServer = null;
        }
    }
    
    private OidcClientContext newContextWithEndpoint(String tokenEndpoint) {
        OidcClientContext context = new OidcClientContext();
        Properties properties = new Properties();
        properties.setProperty(OidcClientConstants.PROP_ISSUER_URI, "http://example.com");
        properties.setProperty(OidcClientConstants.PROP_CLIENT_ID, "test-client");
        properties.setProperty(OidcClientConstants.PROP_CLIENT_SECRET, "test-secret");
        properties.setProperty(OidcClientConstants.PROP_TOKEN_ENDPOINT, tokenEndpoint);
        context.init(properties);
        return context;
    }
    
    private OidcClientContext newContextWithEndpointAndScope(String tokenEndpoint, String scope) {
        OidcClientContext context = new OidcClientContext();
        Properties properties = new Properties();
        properties.setProperty(OidcClientConstants.PROP_ISSUER_URI, "http://example.com");
        properties.setProperty(OidcClientConstants.PROP_CLIENT_ID, "test-client");
        properties.setProperty(OidcClientConstants.PROP_CLIENT_SECRET, "test-secret");
        properties.setProperty(OidcClientConstants.PROP_TOKEN_ENDPOINT, tokenEndpoint);
        properties.setProperty(OidcClientConstants.PROP_SCOPE, scope);
        context.init(properties);
        return context;
    }
    
    private void startServer(HttpHandler handler) throws IOException {
        httpServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        httpServer.createContext("/token", handler);
        httpServer.start();
    }
    
    private String tokenEndpointUrl() {
        return "http://127.0.0.1:" + httpServer.getAddress().getPort() + "/token";
    }
    
    private static void writeResponse(HttpExchange exchange, int status, String body)
        throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
    
    @Test
    void testInitialStateNeedsRefresh() {
        // Token holder starts with no token
        assertNull(tokenHolder.getAccessToken());
        assertTrue(tokenHolder.isExpiredOrNeedRefresh());
    }
    
    @Test
    void testFetchTokenWithNullEndpoint() {
        OidcClientContext context = new OidcClientContext();
        // context has no token endpoint
        boolean result = tokenHolder.fetchToken(context);
        assertFalse(result, "Should fail when token endpoint is null");
    }
    
    @Test
    void testGenerateTokenRefreshWindowWithZeroTtl() {
        long window = tokenHolder.generateTokenRefreshWindow(0);
        assertTrue(window == 0, "Window should be 0 for TTL 0");
    }
    
    @Test
    void testGenerateTokenRefreshWindowWithNegativeTtl() {
        long window = tokenHolder.generateTokenRefreshWindow(-1);
        assertTrue(window == 0, "Window should be 0 for negative TTL");
    }
    
    @Test
    void testGenerateTokenRefreshWindowWithNormalTtl() {
        // TTL = 300s => startNumber = 300/15 = 20, endNumber = 300/10 = 30
        long window = tokenHolder.generateTokenRefreshWindow(300);
        assertTrue(window >= 20 && window < 30,
            "Window should be in range [20, 30), got: " + window);
    }
    
    @Test
    void testGenerateTokenRefreshWindowWithSmallTtl() {
        // TTL = 10s => startNumber = 0, endNumber = 1
        long window = tokenHolder.generateTokenRefreshWindow(10);
        assertTrue(window >= 0 && window <= 1,
            "Window should be 0 or 1 for TTL 10, got: " + window);
    }
    
    @Test
    void testGenerateTokenRefreshWindowWithVerySmallTtl() {
        // TTL = 5s => startNumber = 0, endNumber = 0 => falls to startNumber
        long window = tokenHolder.generateTokenRefreshWindow(5);
        assertTrue(window == 0, "Window should be 0 for very small TTL, got: " + window);
    }
    
    @Test
    void testFetchTokenSuccess() throws Exception {
        startServer(exchange -> writeResponse(exchange, 200,
            "{\"access_token\":\"abc123\",\"expires_in\":600}"));
        OidcClientContext context = newContextWithEndpoint(tokenEndpointUrl());
        assertTrue(tokenHolder.fetchToken(context));
        assertEquals("abc123", tokenHolder.getAccessToken());
        assertEquals(600, tokenHolder.getExpiresInSeconds());
        assertTrue(tokenHolder.getObtainedAtMs() > 0);
        assertFalse(tokenHolder.isExpiredOrNeedRefresh());
    }
    
    @Test
    void testFetchTokenSuccessWithScope() throws Exception {
        AtomicReference<String> bodyRef = new AtomicReference<>();
        startServer(exchange -> {
            byte[] buf = new byte[4096];
            int n = exchange.getRequestBody().read(buf);
            bodyRef.set(new String(buf, 0, Math.max(n, 0), StandardCharsets.UTF_8));
            writeResponse(exchange, 200,
                "{\"access_token\":\"with-scope\",\"expires_in\":120}");
        });
        OidcClientContext context = newContextWithEndpointAndScope(tokenEndpointUrl(),
            "openid profile");
        assertTrue(tokenHolder.fetchToken(context));
        assertEquals("with-scope", tokenHolder.getAccessToken());
        assertNotNull(bodyRef.get());
        assertTrue(bodyRef.get().contains("scope=openid"));
    }
    
    @Test
    void testFetchTokenWithMissingExpiresInUsesDefault() throws Exception {
        startServer(exchange -> writeResponse(exchange, 200,
            "{\"access_token\":\"no-expires\"}"));
        OidcClientContext context = newContextWithEndpoint(tokenEndpointUrl());
        assertTrue(tokenHolder.fetchToken(context));
        assertEquals(300L, tokenHolder.getExpiresInSeconds());
    }
    
    @Test
    void testFetchTokenServerErrorReturnsFalse() throws Exception {
        startServer(exchange -> writeResponse(exchange, 500, "{\"error\":\"server\"}"));
        OidcClientContext context = newContextWithEndpoint(tokenEndpointUrl());
        assertFalse(tokenHolder.fetchToken(context));
        assertNull(tokenHolder.getAccessToken());
    }
    
    @Test
    void testFetchTokenServerErrorWithoutBodyReturnsFalse() throws Exception {
        startServer(exchange -> {
            // 401 with no body
            exchange.sendResponseHeaders(401, -1);
            exchange.close();
        });
        OidcClientContext context = newContextWithEndpoint(tokenEndpointUrl());
        assertFalse(tokenHolder.fetchToken(context));
    }
    
    @Test
    void testFetchTokenIoExceptionReturnsFalse() {
        // Use unreachable port (0 means kernel-chosen, but here we use 1 which usually fails)
        OidcClientContext context = newContextWithEndpoint("http://127.0.0.1:1/token");
        assertFalse(tokenHolder.fetchToken(context));
    }
    
    @Test
    void testFetchTokenInvalidJsonReturnsFalse() throws Exception {
        startServer(exchange -> writeResponse(exchange, 200, "not-json{"));
        OidcClientContext context = newContextWithEndpoint(tokenEndpointUrl());
        assertFalse(tokenHolder.fetchToken(context));
    }
    
    @Test
    void testFetchTokenMissingAccessTokenReturnsFalse() throws Exception {
        startServer(exchange -> writeResponse(exchange, 200, "{\"expires_in\":300}"));
        OidcClientContext context = newContextWithEndpoint(tokenEndpointUrl());
        assertFalse(tokenHolder.fetchToken(context));
    }
    
    @Test
    void testFetchTokenAccessTokenIsNullReturnsFalse() throws Exception {
        startServer(exchange -> writeResponse(exchange, 200,
            "{\"access_token\":null,\"expires_in\":300}"));
        OidcClientContext context = newContextWithEndpoint(tokenEndpointUrl());
        assertFalse(tokenHolder.fetchToken(context));
    }
    
    @Test
    void testIsExpiredOrNeedRefreshAfterRecentFetch() throws Exception {
        startServer(exchange -> writeResponse(exchange, 200,
            "{\"access_token\":\"fresh\",\"expires_in\":3600}"));
        OidcClientContext context = newContextWithEndpoint(tokenEndpointUrl());
        assertTrue(tokenHolder.fetchToken(context));
        // freshly fetched 1h token should not need refresh
        assertFalse(tokenHolder.isExpiredOrNeedRefresh());
    }
}
