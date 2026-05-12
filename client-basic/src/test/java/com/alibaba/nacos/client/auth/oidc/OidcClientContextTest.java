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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test for {@link OidcClientContext}.
 *
 * @author wangzji
 */
class OidcClientContextTest {
    
    private OidcClientContext context;
    
    @BeforeEach
    void setUp() {
        context = new OidcClientContext();
    }
    
    @Test
    void testInitWithFullConfig() {
        Properties properties = new Properties();
        properties.setProperty(OidcClientConstants.PROP_ISSUER_URI,
            "https://idp.example.com/realms/test");
        properties.setProperty(OidcClientConstants.PROP_CLIENT_ID, "my-client");
        properties.setProperty(OidcClientConstants.PROP_CLIENT_SECRET, "my-secret");
        properties.setProperty(OidcClientConstants.PROP_SCOPE, "openid profile");
        
        boolean configured = context.init(properties);
        
        assertTrue(configured);
        assertTrue(context.isConfigured());
        assertEquals("https://idp.example.com/realms/test", context.getIssuerUri());
        assertEquals("my-client", context.getClientId());
        assertEquals("my-secret", context.getClientSecret());
        assertEquals("openid profile", context.getScope());
        assertFalse(context.isDiscovered());
    }
    
    @Test
    void testInitWithDefaultScope() {
        Properties properties = new Properties();
        properties.setProperty(OidcClientConstants.PROP_ISSUER_URI, "https://idp.example.com");
        properties.setProperty(OidcClientConstants.PROP_CLIENT_ID, "my-client");
        properties.setProperty(OidcClientConstants.PROP_CLIENT_SECRET, "my-secret");
        
        boolean configured = context.init(properties);
        
        assertTrue(configured);
        assertEquals(OidcClientConstants.DEFAULT_SCOPE, context.getScope());
    }
    
    @Test
    void testInitWithDirectTokenEndpoint() {
        Properties properties = new Properties();
        properties.setProperty(OidcClientConstants.PROP_CLIENT_ID, "my-client");
        properties.setProperty(OidcClientConstants.PROP_CLIENT_SECRET, "my-secret");
        properties.setProperty(OidcClientConstants.PROP_TOKEN_ENDPOINT,
            "https://idp.example.com/token");
        
        boolean configured = context.init(properties);
        
        assertTrue(configured);
        assertTrue(context.isDiscovered()); // discovery is skipped
        assertEquals("https://idp.example.com/token", context.getTokenEndpoint());
    }
    
    @Test
    void testInitWithEmptyConfig() {
        Properties properties = new Properties();
        
        boolean configured = context.init(properties);
        
        assertFalse(configured);
        assertFalse(context.isConfigured());
    }
    
    @Test
    void testInitWithOnlyClientId() {
        Properties properties = new Properties();
        properties.setProperty(OidcClientConstants.PROP_CLIENT_ID, "my-client");
        
        boolean configured = context.init(properties);
        
        assertFalse(configured);
    }
    
    @Test
    void testInitWithClientCredentialsButNoEndpoint() {
        Properties properties = new Properties();
        properties.setProperty(OidcClientConstants.PROP_CLIENT_ID, "my-client");
        properties.setProperty(OidcClientConstants.PROP_CLIENT_SECRET, "my-secret");
        
        boolean configured = context.init(properties);
        
        assertFalse(configured, "Should not be configured without issuer-uri or token-endpoint");
    }
    
    @Test
    void testDiscoverWithoutIssuerUri() {
        Properties properties = new Properties();
        properties.setProperty(OidcClientConstants.PROP_CLIENT_ID, "my-client");
        properties.setProperty(OidcClientConstants.PROP_CLIENT_SECRET, "my-secret");
        context.init(properties);
        
        boolean result = context.discover();
        
        assertFalse(result, "Discovery should fail without issuer-uri");
    }
    
    @Test
    void testDiscoverSkipsIfAlreadyDone() {
        Properties properties = new Properties();
        properties.setProperty(OidcClientConstants.PROP_CLIENT_ID, "my-client");
        properties.setProperty(OidcClientConstants.PROP_CLIENT_SECRET, "my-secret");
        properties.setProperty(OidcClientConstants.PROP_TOKEN_ENDPOINT,
            "https://idp.example.com/token");
        context.init(properties);
        
        // Already discovered via direct token endpoint
        assertTrue(context.isDiscovered());
        boolean result = context.discover();
        assertTrue(result, "Should return true when already discovered");
    }
    
    @Test
    void testDiscoverWithInvalidUrl() {
        Properties properties = new Properties();
        properties.setProperty(OidcClientConstants.PROP_ISSUER_URI, "http://0.0.0.0");
        properties.setProperty(OidcClientConstants.PROP_CLIENT_ID, "my-client");
        properties.setProperty(OidcClientConstants.PROP_CLIENT_SECRET, "my-secret");
        context.init(properties);
        
        boolean result = context.discover();
        
        assertFalse(result, "Discovery should fail with invalid URL");
    }
    
    private HttpServer httpServer;
    
    @AfterEach
    void afterEach() {
        if (httpServer != null) {
            httpServer.stop(0);
            httpServer = null;
        }
    }
    
    private void startServer(String path, HttpHandler handler) throws IOException {
        httpServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        httpServer.createContext(path, handler);
        httpServer.start();
    }
    
    private String baseIssuerUri() {
        return "http://127.0.0.1:" + httpServer.getAddress().getPort();
    }
    
    private static void writeJson(HttpExchange exchange, int status, String body)
        throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
    
    @Test
    void testDiscoverSuccess() throws Exception {
        startServer("/.well-known/openid-configuration", exchange -> writeJson(exchange, 200,
            "{\"token_endpoint\":\"http://idp.example.com/token\"}"));
        Properties properties = new Properties();
        properties.setProperty(OidcClientConstants.PROP_ISSUER_URI, baseIssuerUri());
        properties.setProperty(OidcClientConstants.PROP_CLIENT_ID, "my-client");
        properties.setProperty(OidcClientConstants.PROP_CLIENT_SECRET, "my-secret");
        context.init(properties);
        assertTrue(context.discover());
        assertTrue(context.isDiscovered());
        assertEquals("http://idp.example.com/token", context.getTokenEndpoint());
    }
    
    @Test
    void testDiscoverSuccessWithTrailingSlashIssuer() throws Exception {
        startServer("/.well-known/openid-configuration", exchange -> writeJson(exchange, 200,
            "{\"token_endpoint\":\"http://idp.example.com/token\"}"));
        Properties properties = new Properties();
        properties.setProperty(OidcClientConstants.PROP_ISSUER_URI, baseIssuerUri() + "/");
        properties.setProperty(OidcClientConstants.PROP_CLIENT_ID, "my-client");
        properties.setProperty(OidcClientConstants.PROP_CLIENT_SECRET, "my-secret");
        context.init(properties);
        assertTrue(context.discover());
    }
    
    @Test
    void testDiscoverHttpErrorReturnsFalse() throws Exception {
        startServer("/.well-known/openid-configuration",
            exchange -> writeJson(exchange, 500, "boom"));
        Properties properties = new Properties();
        properties.setProperty(OidcClientConstants.PROP_ISSUER_URI, baseIssuerUri());
        properties.setProperty(OidcClientConstants.PROP_CLIENT_ID, "my-client");
        properties.setProperty(OidcClientConstants.PROP_CLIENT_SECRET, "my-secret");
        context.init(properties);
        assertFalse(context.discover());
    }
    
    @Test
    void testDiscoverMissingTokenEndpointReturnsFalse() throws Exception {
        startServer("/.well-known/openid-configuration",
            exchange -> writeJson(exchange, 200, "{\"issuer\":\"x\"}"));
        Properties properties = new Properties();
        properties.setProperty(OidcClientConstants.PROP_ISSUER_URI, baseIssuerUri());
        properties.setProperty(OidcClientConstants.PROP_CLIENT_ID, "my-client");
        properties.setProperty(OidcClientConstants.PROP_CLIENT_SECRET, "my-secret");
        context.init(properties);
        assertFalse(context.discover());
    }
    
    @Test
    void testDiscoverNullTokenEndpointReturnsFalse() throws Exception {
        startServer("/.well-known/openid-configuration",
            exchange -> writeJson(exchange, 200, "{\"token_endpoint\":null}"));
        Properties properties = new Properties();
        properties.setProperty(OidcClientConstants.PROP_ISSUER_URI, baseIssuerUri());
        properties.setProperty(OidcClientConstants.PROP_CLIENT_ID, "my-client");
        properties.setProperty(OidcClientConstants.PROP_CLIENT_SECRET, "my-secret");
        context.init(properties);
        assertFalse(context.discover());
    }
    
    @Test
    void testReadInputStreamAsString() throws Exception {
        String payload = "abcdef-数据";
        ByteArrayInputStream bis =
            new ByteArrayInputStream(payload.getBytes(StandardCharsets.UTF_8));
        assertEquals(payload, OidcClientContext.readInputStreamAsString(bis));
    }
}
