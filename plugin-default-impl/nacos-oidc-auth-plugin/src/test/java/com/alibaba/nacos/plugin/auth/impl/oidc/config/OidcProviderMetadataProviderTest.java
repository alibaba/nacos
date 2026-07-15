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

package com.alibaba.nacos.plugin.auth.impl.oidc.config;

import com.alibaba.nacos.plugin.auth.constant.OidcProtocolConstants;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OidcProviderMetadataProviderTest {
    
    @Test
    void testDiscoveryLoadsAllEndpointsAndCachesSuccess() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(OidcProtocolConstants.WELL_KNOWN_PATH, exchange -> {
            requests.incrementAndGet();
            String base = "http://127.0.0.1:" + server.getAddress().getPort();
            writeResponse(exchange, 200, metadataBody(base));
        });
        server.start();
        try {
            String issuer = "http://127.0.0.1:" + server.getAddress().getPort() + "///";
            OidcProviderMetadataProvider provider =
                new OidcProviderMetadataProvider(config(issuer));
            
            OidcProviderMetadata first = provider.getMetadata();
            OidcProviderMetadata second = provider.getMetadata();
            String base = issuer.substring(0, issuer.length() - 3);
            
            assertSame(first, second);
            assertEquals(1, requests.get());
            assertEquals(base + "/authorize", first.getAuthorizationEndpoint());
            assertEquals(base + "/token", first.getTokenEndpoint());
            assertEquals(base + "/userinfo", first.getUserinfoEndpoint());
            assertEquals(base + "/logout", first.getEndSessionEndpoint());
            assertEquals(base + "/jwks", first.getJwksUri());
        } finally {
            server.stop(0);
        }
    }
    
    @Test
    void testFailedDiscoveryIsNotCached() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(OidcProtocolConstants.WELL_KNOWN_PATH, exchange -> {
            requests.incrementAndGet();
            writeResponse(exchange, 500, "{}");
        });
        server.start();
        try {
            String issuer = "http://127.0.0.1:" + server.getAddress().getPort();
            OidcProviderMetadataProvider provider =
                new OidcProviderMetadataProvider(config(issuer));
            
            assertThrows(IOException.class, provider::getMetadata);
            assertThrows(IOException.class, provider::getMetadata);
            assertEquals(2, requests.get());
        } finally {
            server.stop(0);
        }
    }
    
    @Test
    void testDiscoveryRejectsMissingInvalidAndMalformedResponses() throws Exception {
        assertThrows(IOException.class,
            () -> new OidcProviderMetadataProvider(config("")).getMetadata());
        assertThrows(IOException.class,
            () -> new OidcProviderMetadataProvider(config("://bad")).getMetadata());
        
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/null" + OidcProtocolConstants.WELL_KNOWN_PATH,
            exchange -> writeResponse(exchange, 200, "null"));
        server.createContext("/bad" + OidcProtocolConstants.WELL_KNOWN_PATH,
            exchange -> writeResponse(exchange, 200, "{bad"));
        server.start();
        try {
            String base = "http://127.0.0.1:" + server.getAddress().getPort();
            assertThrows(IOException.class,
                () -> new OidcProviderMetadataProvider(config(base + "/null")).getMetadata());
            assertThrows(IOException.class,
                () -> new OidcProviderMetadataProvider(config(base + "/bad")).getMetadata());
        } finally {
            server.stop(0);
        }
    }
    
    @Test
    void testDiscoveryRestoresInterruptedState() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        when(httpClient.<String>send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
            .thenThrow(new InterruptedException("stop"));
        OidcProviderMetadataProvider provider = new OidcProviderMetadataProvider(
            config("http://issuer"), httpClient);
        
        IOException exception = assertThrows(IOException.class, provider::getMetadata);
        
        assertTrue(exception.getMessage().contains("interrupted"));
        assertTrue(Thread.interrupted());
    }
    
    private OidcAuthPluginConfig config(String issuer) {
        return OidcAuthPluginConfig.from(Map.of(OidcAuthPluginConfig.ISSUER_URI, issuer));
    }
    
    private String metadataBody(String base) {
        return "{" + "\"authorization_endpoint\":\"" + base + "/authorize\","
            + "\"token_endpoint\":\"" + base + "/token\","
            + "\"userinfo_endpoint\":\"" + base + "/userinfo\","
            + "\"end_session_endpoint\":\"" + base + "/logout\","
            + "\"jwks_uri\":\"" + base + "/jwks\"}";
    }
    
    private void writeResponse(com.sun.net.httpserver.HttpExchange exchange, int status,
        String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
