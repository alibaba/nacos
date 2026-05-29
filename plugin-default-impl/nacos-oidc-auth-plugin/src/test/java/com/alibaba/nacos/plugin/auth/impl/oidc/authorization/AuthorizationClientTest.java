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

package com.alibaba.nacos.plugin.auth.impl.oidc.authorization;

import com.alibaba.nacos.plugin.auth.impl.oidc.config.OidcAuthConfig;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class AuthorizationClientTest {
    
    @AfterEach
    void tearDown() {
        ReflectionTestUtils.setField(AuthorizationClient.class, "instance", null);
    }
    
    @Test
    void testAuthorizeAllowsWhenEndpointIsMissing() {
        OidcAuthConfig config = mockConfig("");
        AuthorizationClient client = newClient(config);
        
        AuthorizationResponse response = client.authorize(request());
        
        assertTrue(response.isAllowed());
        assertTrue(client.isAuthorized("token", "nacos:config", "read"));
    }
    
    @Test
    void testAuthorizeDeniesWhenEndpointIsInvalid() {
        OidcAuthConfig config = mockConfig("://bad-endpoint");
        AuthorizationClient client = newClient(config);
        
        AuthorizationResponse response = client.authorize(request());
        
        assertFalse(response.isAllowed());
        assertTrue(response.getReason().contains("Authorization error"));
    }
    
    @Test
    void testAuthorizeHandlesIdpHttpResponses() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/allow", exchange -> writeResponse(exchange, 200,
            "{\"allowed\":true}"));
        server.createContext("/deny", exchange -> writeResponse(exchange, 403,
            "{\"allowed\":false}"));
        server.createContext("/error", exchange -> writeResponse(exchange, 500,
            "{\"message\":\"error\"}"));
        server.start();
        try {
            String base = "http://127.0.0.1:" + server.getAddress().getPort();
            assertTrue(newClient(mockConfig(base + "/allow")).authorize(request()).isAllowed());
            assertFalse(newClient(mockConfig(base + "/deny")).authorize(request()).isAllowed());
            assertFalse(newClient(mockConfig(base + "/error")).authorize(request()).isAllowed());
        } finally {
            server.stop(0);
        }
    }
    
    @Test
    void testAuthorizeHandlesTransportExceptions() throws Exception {
        AuthorizationClient ioClient = newClient(mockConfig("http://idp/evaluate"));
        HttpClient ioHttpClient = mock(HttpClient.class);
        when(ioHttpClient.<String>send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
            .thenThrow(new IOException("down"));
        ReflectionTestUtils.setField(ioClient, "httpClient", ioHttpClient);
        
        AuthorizationResponse ioResponse = ioClient.authorize(request());
        
        assertFalse(ioResponse.isAllowed());
        assertTrue(ioResponse.getReason().contains("unavailable"));
        
        AuthorizationClient interruptedClient = newClient(mockConfig("http://idp/evaluate"));
        HttpClient interruptedHttpClient = mock(HttpClient.class);
        when(interruptedHttpClient.<String>send(any(HttpRequest.class),
            any(HttpResponse.BodyHandler.class))).thenThrow(new InterruptedException("stop"));
        ReflectionTestUtils.setField(interruptedClient, "httpClient", interruptedHttpClient);
        
        AuthorizationResponse interruptedResponse = interruptedClient.authorize(request());
        
        assertFalse(interruptedResponse.isAllowed());
        assertTrue(interruptedResponse.getReason().contains("interrupted"));
        assertTrue(Thread.interrupted());
    }
    
    private AuthorizationClient newClient(OidcAuthConfig config) {
        ReflectionTestUtils.setField(AuthorizationClient.class, "instance", null);
        try (MockedStatic<OidcAuthConfig> configStatic = mockStatic(OidcAuthConfig.class)) {
            configStatic.when(OidcAuthConfig::getInstance).thenReturn(config);
            return AuthorizationClient.getInstance();
        }
    }
    
    private OidcAuthConfig mockConfig(String endpoint) {
        OidcAuthConfig config = mock(OidcAuthConfig.class);
        when(config.getAuthorizationTimeoutMs()).thenReturn(1000L);
        when(config.getAuthorizationEvaluateEndpoint()).thenReturn(endpoint);
        return config;
    }
    
    private AuthorizationRequest request() {
        return AuthorizationRequest.builder()
            .token("token")
            .resource("nacos:config")
            .action("read")
            .build();
    }
    
    private void writeResponse(com.sun.net.httpserver.HttpExchange exchange, int status,
        String body) throws java.io.IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
