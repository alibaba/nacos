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

package com.alibaba.nacos.plugin.auth.impl.oidc.token;

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
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JwksProviderTest {
    
    @AfterEach
    void tearDown() {
        ReflectionTestUtils.setField(JwksProvider.class, "instance", null);
    }
    
    @Test
    void testGetJwkSetRejectsMissingIssuerAndJwksUri() {
        OidcAuthConfig config = mockConfig("", "");
        JwksProvider provider = newProvider(config);
        
        assertThrows(IOException.class, provider::getJwkSet);
    }
    
    @Test
    void testGetJwkSetFetchesConfiguredUriUsesCacheAndRefreshes() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/jwks", exchange -> {
            requests.incrementAndGet();
            writeResponse(exchange, 200, "{\"keys\":[]}");
        });
        server.start();
        try {
            String jwksUri = "http://127.0.0.1:" + server.getAddress().getPort() + "/jwks";
            JwksProvider provider = newProvider(mockConfig(jwksUri, ""));
            
            assertEquals(0, provider.getJwkSet().getKeys().size());
            assertEquals(0, provider.getJwkSet().getKeys().size());
            assertEquals(1, requests.get());
            
            assertEquals(0, provider.refreshJwkSet().getKeys().size());
            assertEquals(2, requests.get());
        } finally {
            server.stop(0);
        }
    }
    
    @Test
    void testGetJwkSetRejectsHttpAndParseFailures() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/status", exchange -> writeResponse(exchange, 500, "{}"));
        server.createContext("/bad-json", exchange -> writeResponse(exchange, 200, "{bad"));
        server.start();
        try {
            String base = "http://127.0.0.1:" + server.getAddress().getPort();
            assertThrows(IOException.class,
                () -> newProvider(mockConfig(base + "/status", "")).getJwkSet());
            assertThrows(IOException.class,
                () -> newProvider(mockConfig(base + "/bad-json", "")).getJwkSet());
        } finally {
            server.stop(0);
        }
    }
    
    @Test
    void testGetJwkSetDiscoversProviderConfiguration() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/.well-known/openid-configuration", exchange -> {
            String base = "http://127.0.0.1:" + server.getAddress().getPort();
            String body = "{"
                + "\"jwks_uri\":\"" + base + "/jwks\","
                + "\"authorization_endpoint\":\"" + base + "/authorize\","
                + "\"token_endpoint\":\"" + base + "/token\","
                + "\"userinfo_endpoint\":\"" + base + "/userinfo\","
                + "\"end_session_endpoint\":\"" + base + "/logout\""
                + "}";
            writeResponse(exchange, 200, body);
        });
        server.createContext("/jwks", exchange -> writeResponse(exchange, 200, "{\"keys\":[]}"));
        server.start();
        try {
            String issuerUri = "http://127.0.0.1:" + server.getAddress().getPort();
            OidcAuthConfig config = mockConfig("", issuerUri);
            JwksProvider provider = newProvider(config);
            
            assertEquals(0, provider.getJwkSet().getKeys().size());
            
            verify(config).setJwksUri(issuerUri + "/jwks");
            verify(config).setAuthorizationEndpoint(issuerUri + "/authorize");
            verify(config).setTokenEndpoint(issuerUri + "/token");
            verify(config).setUserinfoEndpoint(issuerUri + "/userinfo");
            verify(config).setEndSessionEndpoint(issuerUri + "/logout");
            
            OidcAuthConfig trailingSlashConfig = mockConfig("", issuerUri + "/");
            JwksProvider trailingSlashProvider = newProvider(trailingSlashConfig);
            
            assertEquals(0, trailingSlashProvider.getJwkSet().getKeys().size());
            verify(trailingSlashConfig).setJwksUri(issuerUri + "/jwks");
        } finally {
            server.stop(0);
        }
    }
    
    @Test
    void testGetJwkSetRejectsDiscoveryFailures() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/status/.well-known/openid-configuration",
            exchange -> writeResponse(exchange, 500, "{}"));
        server.createContext("/parse/.well-known/openid-configuration",
            exchange -> writeResponse(exchange, 200, "{bad"));
        server.createContext("/missing-jwks/.well-known/openid-configuration",
            exchange -> writeResponse(exchange, 200, "{}"));
        server.start();
        try {
            String base = "http://127.0.0.1:" + server.getAddress().getPort();
            assertThrows(IOException.class,
                () -> newProvider(mockConfig("", base + "/status")).getJwkSet());
            assertThrows(IOException.class,
                () -> newProvider(mockConfig("", base + "/parse")).getJwkSet());
            assertThrows(IOException.class,
                () -> newProvider(mockConfig("", base + "/missing-jwks")).getJwkSet());
        } finally {
            server.stop(0);
        }
    }
    
    @Test
    void testGetJwkSetWrapsInterruptedFetchAndDiscovery() throws Exception {
        JwksProvider fetchProvider = newProvider(mockConfig("http://idp/jwks", ""));
        HttpClient fetchClient = mock(HttpClient.class);
        when(fetchClient.<String>send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
            .thenThrow(new InterruptedException("stop"));
        ReflectionTestUtils.setField(fetchProvider, "httpClient", fetchClient);
        
        IOException fetchException = assertThrows(IOException.class, fetchProvider::getJwkSet);
        
        assertTrue(fetchException.getMessage().contains("interrupted"));
        assertTrue(Thread.interrupted());
        
        JwksProvider discoveryProvider = newProvider(mockConfig("", "http://idp"));
        HttpClient discoveryClient = mock(HttpClient.class);
        when(discoveryClient.<String>send(any(HttpRequest.class),
            any(HttpResponse.BodyHandler.class))).thenThrow(new InterruptedException("stop"));
        ReflectionTestUtils.setField(discoveryProvider, "httpClient", discoveryClient);
        
        IOException discoveryException =
            assertThrows(IOException.class, discoveryProvider::getJwkSet);
        
        assertTrue(discoveryException.getMessage().contains("interrupted"));
        assertTrue(Thread.interrupted());
    }
    
    @Test
    void testClearCacheClearsCachedJwksUri() {
        OidcAuthConfig config = mockConfig("http://issuer/jwks", "");
        JwksProvider provider = newProvider(config);
        ReflectionTestUtils.setField(provider, "jwksUri", "http://issuer/jwks");
        
        provider.clearCache();
        
        assertNull(ReflectionTestUtils.getField(provider, "jwksUri"));
    }
    
    private JwksProvider newProvider(OidcAuthConfig config) {
        ReflectionTestUtils.setField(JwksProvider.class, "instance", null);
        try (MockedStatic<OidcAuthConfig> configStatic = mockStatic(OidcAuthConfig.class)) {
            configStatic.when(OidcAuthConfig::getInstance).thenReturn(config);
            return JwksProvider.getInstance();
        }
    }
    
    private OidcAuthConfig mockConfig(String jwksUri, String issuerUri) {
        OidcAuthConfig config = mock(OidcAuthConfig.class);
        when(config.getJwksCacheTtlSeconds()).thenReturn(60L);
        when(config.getJwksUri()).thenReturn(jwksUri);
        when(config.getIssuerUri()).thenReturn(issuerUri);
        return config;
    }
    
    private void writeResponse(com.sun.net.httpserver.HttpExchange exchange, int status,
        String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
