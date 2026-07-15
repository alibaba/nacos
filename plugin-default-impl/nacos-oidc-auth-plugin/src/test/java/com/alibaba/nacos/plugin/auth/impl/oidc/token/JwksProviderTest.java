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

import com.alibaba.nacos.plugin.auth.impl.oidc.config.OidcAuthPluginConfig;
import com.alibaba.nacos.plugin.auth.impl.oidc.config.OidcProviderMetadata;
import com.alibaba.nacos.plugin.auth.impl.oidc.config.OidcProviderMetadataProvider;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JwksProviderTest {
    
    @Test
    void testGetJwkSetRejectsMissingJwksUri() throws Exception {
        OidcProviderMetadataProvider metadataProvider = mockMetadataProvider("");
        JwksProvider provider = new JwksProvider(mockConfig(), metadataProvider);
        
        assertThrows(IOException.class, provider::getJwkSet);
    }
    
    @Test
    void testGetJwkSetFetchesUsesCacheRefreshesAndClears() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/jwks", exchange -> {
            requests.incrementAndGet();
            writeResponse(exchange, 200, "{\"keys\":[]}");
        });
        server.start();
        try {
            String jwksUri = "http://127.0.0.1:" + server.getAddress().getPort() + "/jwks";
            JwksProvider provider = new JwksProvider(mockConfig(),
                mockMetadataProvider(jwksUri));
            
            assertEquals(0, provider.getJwkSet().getKeys().size());
            assertEquals(0, provider.getJwkSet().getKeys().size());
            assertEquals(1, requests.get());
            
            assertEquals(0, provider.refreshJwkSet().getKeys().size());
            assertEquals(2, requests.get());
            provider.clearCache();
            assertEquals(0, provider.getJwkSet().getKeys().size());
            assertEquals(3, requests.get());
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
                () -> new JwksProvider(mockConfig(), mockMetadataProvider(base + "/status"))
                    .getJwkSet());
            assertThrows(IOException.class,
                () -> new JwksProvider(mockConfig(), mockMetadataProvider(base + "/bad-json"))
                    .getJwkSet());
        } finally {
            server.stop(0);
        }
    }
    
    @Test
    void testGetJwkSetPropagatesMetadataFailure() throws Exception {
        OidcProviderMetadataProvider metadataProvider = mock(OidcProviderMetadataProvider.class);
        when(metadataProvider.getMetadata()).thenThrow(new IOException("discovery failed"));
        JwksProvider provider = new JwksProvider(mockConfig(), metadataProvider);
        
        IOException exception = assertThrows(IOException.class, provider::getJwkSet);
        
        assertEquals("discovery failed", exception.getMessage());
    }
    
    @Test
    void testGetJwkSetWrapsInterruptedFetch() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        when(httpClient.<String>send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
            .thenThrow(new InterruptedException("stop"));
        JwksProvider provider = new JwksProvider(mockConfig(),
            mockMetadataProvider("http://idp/jwks"), httpClient);
        
        IOException exception = assertThrows(IOException.class, provider::getJwkSet);
        
        assertTrue(exception.getMessage().contains("interrupted"));
        assertTrue(Thread.interrupted());
    }
    
    private OidcAuthPluginConfig mockConfig() {
        OidcAuthPluginConfig config = mock(OidcAuthPluginConfig.class);
        when(config.getJwksCacheTtlSeconds()).thenReturn(60L);
        return config;
    }
    
    private OidcProviderMetadataProvider mockMetadataProvider(String jwksUri) throws Exception {
        OidcProviderMetadataProvider metadataProvider = mock(OidcProviderMetadataProvider.class);
        when(metadataProvider.getMetadata()).thenReturn(new OidcProviderMetadata(
            null, null, null, null, jwksUri));
        return metadataProvider;
    }
    
    private void writeResponse(com.sun.net.httpserver.HttpExchange exchange, int status,
        String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
