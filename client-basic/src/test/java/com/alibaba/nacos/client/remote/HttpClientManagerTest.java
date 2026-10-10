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

package com.alibaba.nacos.client.remote;

import com.alibaba.nacos.common.http.HttpClientBeanHolder;
import com.alibaba.nacos.common.constant.HttpHeaderConsts;
import com.alibaba.nacos.common.http.client.NacosRestTemplate;
import com.alibaba.nacos.common.http.param.Header;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;

class HttpClientManagerTest {
    
    @Test
    void testGetInstanceReturnsSingleton() {
        HttpClientManager first = HttpClientManager.getInstance();
        HttpClientManager second = HttpClientManager.getInstance();
        assertNotNull(first);
        assertSame(first, second);
    }
    
    @Test
    void testGetConnectTimeoutOrDefaultUsesMin() {
        // Below 1000 ms minimum → returns 1000
        assertEquals(1000, HttpClientManager.getInstance().getConnectTimeoutOrDefault(500));
    }
    
    @Test
    void testGetConnectTimeoutOrDefaultUsesProvidedWhenLarger() {
        assertEquals(2000, HttpClientManager.getInstance().getConnectTimeoutOrDefault(2000));
    }
    
    @Test
    void testGetNacosRestTemplate() {
        NacosRestTemplate template = HttpClientManager.getInstance().getNacosRestTemplate();
        assertNotNull(template);
    }
    
    @Test
    void testRequestIdsAreSentForEveryHttpMethod() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/test", exchange -> {
            String requestId = exchange.getRequestHeaders().getFirst("Nacos-Request-Id");
            byte[] response = String.valueOf(requestId).getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(response);
            }
        });
        server.start();
        try {
            NacosRestTemplate template = HttpClientManager.getInstance().getNacosRestTemplate();
            String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/test";
            Header headers = Header.newInstance()
                .addParam(HttpHeaderConsts.NACOS_REQUEST_ID, "previous-id");
            Set<String> requestIds = new HashSet<>();
            for (String method : new String[] {"GET", "POST", "PUT", "DELETE", "GET"}) {
                String requestId = template.<String>exchange(url, null, headers, null, null,
                    method, String.class).getData();
                assertEquals(4, UUID.fromString(requestId).version());
                assertTrue(requestIds.add(requestId));
            }
            assertEquals("previous-id", headers.getValue(HttpHeaderConsts.NACOS_REQUEST_ID));
        } finally {
            server.stop(0);
        }
    }
    
    @Test
    void testShutdownDoesNotThrow() {
        // Note: shutdown closes the shared NacosRestTemplate. Run this test last via
        // method name ordering (JUnit runs in alphabetical order without explicit ordering).
        Assertions.assertDoesNotThrow(() -> HttpClientManager.getInstance().shutdown());
    }
    
    @Test
    void testShutdownSwallowsException() {
        try (MockedStatic<HttpClientBeanHolder> mocked =
            Mockito.mockStatic(HttpClientBeanHolder.class)) {
            mocked.when(() -> HttpClientBeanHolder.shutdownNacosSyncRest(anyString()))
                .thenThrow(new RuntimeException("forced"));
            Assertions.assertDoesNotThrow(() -> HttpClientManager.getInstance().shutdown());
        }
    }
    
    @Test
    void testHttpClientFactoryInternalsViaReflection() throws Exception {
        Field factoryField = HttpClientManager.class.getDeclaredField("HTTP_CLIENT_FACTORY");
        factoryField.setAccessible(true);
        Object factory = factoryField.get(null);
        Method buildConfig = factory.getClass().getDeclaredMethod("buildHttpClientConfig");
        buildConfig.setAccessible(true);
        Object config = buildConfig.invoke(factory);
        assertNotNull(config);
        Method assignLogger = factory.getClass().getDeclaredMethod("assignLogger");
        assignLogger.setAccessible(true);
        assertNotNull(assignLogger.invoke(factory));
    }
}
