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

package com.alibaba.nacos.plugin.ai.importer.defaultimpl.http;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.plugin.ai.importer.model.AiResourceImportSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import javax.net.ssl.SSLSession;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Unit tests for {@link DefaultImportHttpClient}.
 *
 * @author xiweng.yy
 */
@ExtendWith(MockitoExtension.class)
class DefaultImportHttpClientTest {
    
    @Mock
    private HttpClient httpClient;
    
    @BeforeEach
    void setUp() throws Exception {
        lenient().when(httpClient.send(any(HttpRequest.class),
            any(HttpResponse.BodyHandler.class))).thenReturn(response(200, "ok"));
    }
    
    @Test
    void testRejectsHttpByDefault() {
        DefaultImportHttpClient client = newClient("93.184.216.34");
        
        assertThrows(NacosException.class,
            () -> client.get(source(1024), "http://registry.example.com/index.json", "*/*"));
        verifyNoInteractions(httpClient);
    }
    
    @Test
    void testRejectsResolvedPrivateAddressByDefault() {
        DefaultImportHttpClient client = newClient("127.0.0.1");
        
        assertThrows(NacosException.class,
            () -> client.get(source(1024), "https://registry.example.com/index.json", "*/*"));
        verifyNoInteractions(httpClient);
    }
    
    @Test
    void testAllowsPrivateAddressWhenSourceOptIn() throws Exception {
        DefaultImportHttpClient client = newClient("127.0.0.1");
        AiResourceImportSource source = source(1024);
        source.setProperties(Collections.singletonMap(
            DefaultImportHttpClient.PROPERTY_ALLOW_PRIVATE_NETWORK, "true"));
        
        ImportHttpResponse response =
            client.get(source, "https://registry.example.com/index.json", "*/*");
        
        assertEquals(200, response.getStatusCode());
        assertEquals("ok", new String(response.getBody(), StandardCharsets.UTF_8));
    }
    
    @Test
    void testRejectsOversizedResponse() throws Exception {
        lenient().when(httpClient.send(any(HttpRequest.class),
            any(HttpResponse.BodyHandler.class))).thenReturn(response(200, "toolong"));
        DefaultImportHttpClient client = newClient("93.184.216.34");
        
        assertThrows(NacosException.class,
            () -> client.get(source(2), "https://registry.example.com/index.json", "*/*"));
    }
    
    private DefaultImportHttpClient newClient(String address) {
        return new DefaultImportHttpClient(httpClient,
            host -> new InetAddress[] {InetAddress.getByName(address)});
    }
    
    private AiResourceImportSource source(long maxResponseSize) {
        AiResourceImportSource source = new AiResourceImportSource();
        source.setMaxArtifactSize(maxResponseSize);
        return source;
    }
    
    private HttpResponse<byte[]> response(int status, String body) {
        Map<String, java.util.List<String>> headers = new HashMap<>(1);
        headers.put("Content-Type", Collections.singletonList("application/json"));
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        return new HttpResponse<>() {
            
            @Override
            public int statusCode() {
                return status;
            }
            
            @Override
            public HttpRequest request() {
                return null;
            }
            
            @Override
            public Optional<HttpResponse<byte[]>> previousResponse() {
                return Optional.empty();
            }
            
            @Override
            public HttpHeaders headers() {
                return HttpHeaders.of(headers, (key, value) -> true);
            }
            
            @Override
            public byte[] body() {
                return bytes;
            }
            
            @Override
            public Optional<SSLSession> sslSession() {
                return Optional.empty();
            }
            
            @Override
            public URI uri() {
                return null;
            }
            
            @Override
            public HttpClient.Version version() {
                return HttpClient.Version.HTTP_1_1;
            }
        };
    }
}
