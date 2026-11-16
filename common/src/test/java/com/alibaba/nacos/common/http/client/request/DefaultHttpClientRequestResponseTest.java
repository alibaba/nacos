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

import com.alibaba.nacos.common.http.HttpRestResult;
import com.alibaba.nacos.common.http.client.NacosRestTemplate;
import com.alibaba.nacos.common.http.client.response.HttpClientResponse;
import com.alibaba.nacos.common.http.param.Header;
import com.alibaba.nacos.common.http.param.Query;
import com.alibaba.nacos.common.model.RequestHttpEntity;
import com.alibaba.nacos.common.utils.IoUtils;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.core5.util.Timeout;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DefaultHttpClientRequestResponseTest {
    
    private static final String JSON = "{\"value\":\"  中文\\n🚀  \"}";
    
    private HttpServer server;
    
    private DefaultHttpClientRequest client;
    
    private NacosRestTemplate template;
    
    private PoolingHttpClientConnectionManager connections;
    
    private final Set<Integer> remotePorts = Collections.synchronizedSet(new HashSet<>());
    
    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        connections = new PoolingHttpClientConnectionManager();
        connections.setMaxTotal(1);
        connections.setDefaultMaxPerRoute(1);
        RequestConfig config = RequestConfig.custom()
            .setConnectionRequestTimeout(Timeout.ofSeconds(2))
            .setResponseTimeout(Timeout.ofSeconds(5)).build();
        client = new DefaultHttpClientRequest(HttpClients.custom().setConnectionManager(connections)
            .setDefaultRequestConfig(config).disableAutomaticRetries().build(), config);
        template = new NacosRestTemplate(LoggerFactory.getLogger(getClass()), client);
        server.start();
    }
    
    @AfterEach
    void tearDown() throws IOException {
        try {
            if (client != null) {
                client.close();
            }
        } finally {
            if (server != null) {
                server.stop(0);
            }
        }
    }
    
    @ParameterizedTest
    @ValueSource(strings = {"application/json", "application/json;charset=UTF-8"})
    void preservesUtf8Json(String contentType) throws Exception {
        byte[] expected = JSON.getBytes(StandardCharsets.UTF_8);
        serve("/json", 200, contentType, expected);
        assertResponse("/json", 200, contentType, expected);
        HttpRestResult<String> result =
            template.get(url("/json"), Header.newInstance(), Query.EMPTY,
                String.class);
        assertEquals(JSON, result.getData());
    }
    
    @Test
    void preserves512LiteralEmojiWithoutCharset() throws Exception {
        StringBuilder value = new StringBuilder("{\"value\":\"");
        for (int i = 0; i < 512; i++) {
            value.append("🚀");
        }
        value.append("\"}");
        byte[] expected = value.toString().getBytes(StandardCharsets.UTF_8);
        serve("/emoji", 200, "application/json", expected);
        assertResponse("/emoji", 200, "application/json", expected);
    }
    
    @Test
    void preservesAllBinaryByteValues() throws Exception {
        byte[] expected = new byte[256];
        for (int i = 0; i < expected.length; i++) {
            expected[i] = (byte) i;
        }
        serve("/binary", 200, "application/octet-stream", expected);
        assertResponse("/binary", 200, "application/octet-stream", expected);
        HttpRestResult<byte[]> result =
            template.get(url("/binary"), Header.newInstance(), Query.EMPTY,
                byte[].class);
        assertArrayEquals(expected, result.getData());
    }
    
    @ParameterizedTest
    @ValueSource(strings = {"ISO-8859-1", "GB18030"})
    void preservesExplicitlyEncodedEntityBytes(String charset) throws Exception {
        String contentType = "text/plain;charset=" + charset;
        byte[] expected = "café 中文🚀".getBytes(Charset.forName(charset));
        serve("/encoded", 200, contentType, expected);
        assertResponse("/encoded", 200, contentType, expected);
    }
    
    @Test
    void preservesEmptyEntity() throws Exception {
        serve("/empty", 200, "application/json", new byte[0]);
        assertResponse("/empty", 200, "application/json", new byte[0]);
    }
    
    @Test
    void preservesBodyWithoutContentType() throws Exception {
        byte[] expected = JSON.getBytes(StandardCharsets.UTF_8);
        serve("/untyped", 200, null, expected);
        assertResponse("/untyped", 200, null, expected);
    }
    
    @ParameterizedTest
    @ValueSource(strings = {"application/json", "application/json;charset=UTF-8"})
    void preservesErrorStatusHeadersAndMessage(String contentType) throws Exception {
        byte[] expected = JSON.getBytes(StandardCharsets.UTF_8);
        serve("/error", 500, contentType, expected);
        assertResponse("/error", 500, contentType, expected);
        HttpRestResult<String> result =
            template.get(url("/error"), Header.newInstance(), Query.EMPTY,
                String.class);
        assertEquals(500, result.getCode());
        assertNull(result.getData());
        assertEquals(JSON, result.getMessage());
        assertEquals("preserved", result.getHeader().getValue("X-Test-Header"));
    }
    
    @Test
    void consumesEntityBeforeReturningAndReusesConnection() throws Exception {
        byte[] expected = JSON.getBytes(StandardCharsets.UTF_8);
        serve("/reuse", 200, "application/json;charset=UTF-8", expected);
        for (int i = 0; i < 20; i++) {
            assertResponse("/reuse", 200, "application/json;charset=UTF-8", expected);
        }
        assertEquals(1, remotePorts.size());
    }
    
    @Test
    void releasesConnectionWhenResponseHandlerThrows() throws Exception {
        byte[] expected = JSON.getBytes(StandardCharsets.UTF_8);
        serve("/invalid", 200, "text/plain;charset=invalid-test-charset", expected);
        serve("/next", 200, "application/json;charset=UTF-8", expected);
        assertThrows(UnsupportedCharsetException.class, () -> execute("/invalid"));
        assertEquals(0, connections.getTotalStats().getLeased());
        assertResponse("/next", 200, "application/json;charset=UTF-8", expected);
    }
    
    @Test
    void releasesConnectionWhenConsumerThrowsAfterPartialRead() throws Exception {
        byte[] expected = JSON.getBytes(StandardCharsets.UTF_8);
        serve("/partial", 200, "application/json;charset=UTF-8", expected);
        assertThrows(IOException.class, () -> {
            try (HttpClientResponse response = execute("/partial")) {
                response.getBody().read();
                throw new IOException("consumer failed");
            }
        });
        assertEquals(0, connections.getTotalStats().getLeased());
        assertResponse("/partial", 200, "application/json;charset=UTF-8", expected);
    }
    
    private HttpClientResponse execute(String path) throws Exception {
        return client.execute(URI.create(url(path)), "GET",
            new RequestHttpEntity(Header.newInstance(), Query.EMPTY, null));
    }
    
    private String url(String path) {
        return "http://127.0.0.1:" + server.getAddress().getPort() + path;
    }
    
    private void assertResponse(String path, int status, String contentType, byte[] expected)
        throws Exception {
        try (HttpClientResponse response = execute(path)) {
            assertEquals(0, connections.getTotalStats().getLeased());
            assertEquals(status, response.getStatusCode());
            assertEquals("preserved", response.getHeaders().getValue("X-Test-Header"));
            if (contentType != null) {
                assertEquals(contentType, response.getHeaders().getValue("Content-Type"));
            }
            try (InputStream input = response.getBody();
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                IoUtils.copy(input, output);
                assertArrayEquals(expected, output.toByteArray());
            }
        }
    }
    
    private void serve(String path, int status, String contentType, byte[] body) {
        server.createContext(path, exchange -> send(exchange, status, contentType, body));
    }
    
    private void send(HttpExchange exchange, int status, String contentType, byte[] body)
        throws IOException {
        try {
            remotePorts.add(exchange.getRemoteAddress().getPort());
            if (contentType != null) {
                exchange.getResponseHeaders().set("Content-Type", contentType);
            }
            exchange.getResponseHeaders().set("X-Test-Header", "preserved");
            exchange.sendResponseHeaders(status, body.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(body);
            }
        } finally {
            exchange.close();
        }
    }
}
