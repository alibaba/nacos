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

package com.alibaba.nacos.common.http.client.request;

import com.alibaba.nacos.common.http.client.response.HttpClientResponse;
import com.alibaba.nacos.common.http.param.Header;
import com.alibaba.nacos.common.http.param.Query;
import com.alibaba.nacos.common.model.RequestHttpEntity;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.core5.util.Timeout;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class DefaultHttpClientRequestResponseTest {
    
    private static final byte[] JSON = "{\"value\":\"  中文\\n🚀  \"}"
        .getBytes(StandardCharsets.UTF_8);
    
    private static final byte[] BINARY = {0, 1, 0x7f, (byte) 0x80, (byte) 0xff};
    
    private static final byte[] LATIN1 = "café".getBytes(StandardCharsets.ISO_8859_1);
    
    private HttpServer server;
    
    private DefaultHttpClientRequest client;
    
    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/json", exchange -> send(exchange, 200, "application/json", JSON));
        server.createContext("/utf8", exchange -> send(exchange, 200,
            "application/json; charset=UTF-8", JSON));
        server.createContext("/binary", exchange -> send(exchange, 200,
            "application/octet-stream", BINARY));
        server.createContext("/latin1", exchange -> send(exchange, 200,
            "text/plain; charset=ISO-8859-1", LATIN1));
        server.createContext("/error", exchange -> send(exchange, 404,
            "application/json", JSON));
        server.start();
        PoolingHttpClientConnectionManager connections = new PoolingHttpClientConnectionManager();
        connections.setMaxTotal(1);
        connections.setDefaultMaxPerRoute(1);
        RequestConfig config = RequestConfig.custom()
            .setConnectionRequestTimeout(Timeout.ofSeconds(2))
            .setResponseTimeout(Timeout.ofSeconds(5)).build();
        client = new DefaultHttpClientRequest(HttpClients.custom()
            .setConnectionManager(connections).setDefaultRequestConfig(config).build(), config);
    }
    
    @AfterEach
    void tearDown() throws IOException {
        if (client != null) {
            client.close();
        }
        if (server != null) {
            server.stop(0);
        }
    }
    
    @Test
    void preservesUtf8JsonBytesWithAndWithoutDeclaredCharset() throws Exception {
        assertResponse("/json", 200, JSON);
        assertResponse("/utf8", 200, JSON);
    }
    
    @Test
    void preservesBinaryBytesAndAllowsNextRequest() throws Exception {
        assertResponse("/binary", 200, BINARY);
        assertResponse("/utf8", 200, JSON);
    }
    
    @Test
    void preservesBytesForAnExplicitNonUtf8Charset() throws Exception {
        assertResponse("/latin1", 200, LATIN1);
    }
    
    @Test
    void preservesErrorStatusHeadersAndResponseBody() throws Exception {
        assertResponse("/error", 404, JSON);
    }
    
    private void assertResponse(String path, int status, byte[] expected) throws Exception {
        URI uri = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + path);
        RequestHttpEntity request = new RequestHttpEntity(Header.newInstance(), Query.EMPTY, null);
        try (HttpClientResponse response = client.execute(uri, "GET", request)) {
            assertEquals(status, response.getStatusCode());
            assertEquals("preserved", response.getHeaders().getValue("X-Test-Header"));
            try (InputStream input = response.getBody();
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[128];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    output.write(buffer, 0, count);
                }
                assertArrayEquals(expected, output.toByteArray());
            }
        }
    }
    
    private static void send(HttpExchange exchange, int status, String contentType, byte[] body)
        throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.getResponseHeaders().set("X-Test-Header", "preserved");
        exchange.sendResponseHeaders(status, body.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(body);
        }
    }
}
