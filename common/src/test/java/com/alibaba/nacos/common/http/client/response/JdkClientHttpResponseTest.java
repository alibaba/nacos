/*
 * Copyright 1999-2023 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.common.http.client.response;

import com.alibaba.nacos.common.constant.HttpHeaderConsts;
import com.alibaba.nacos.common.utils.IoUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
// todo remove this
@MockitoSettings(strictness = Strictness.LENIENT)
class JdkClientHttpResponseTest {
    
    JdkHttpClientResponse clientHttpResponse;
    
    @Mock
    private HttpURLConnection connection;
    
    @Mock
    private InputStream inputStream;
    
    private Map<String, List<String>> headers;
    
    @BeforeEach
    void setUp() throws Exception {
        headers = new HashMap<>();
        headers.put("testName", Collections.singletonList("testValue"));
        when(connection.getHeaderFields()).thenReturn(headers);
        clientHttpResponse = new JdkHttpClientResponse(connection);
    }
    
    @AfterEach
    void tearDown() throws Exception {
        clientHttpResponse.close();
    }
    
    @Test
    void testGetStatusCode() throws IOException {
        when(connection.getResponseCode()).thenReturn(200);
        assertEquals(200, clientHttpResponse.getStatusCode());
    }
    
    @Test
    void testGetStatusText() throws IOException {
        when(connection.getResponseMessage()).thenReturn("test");
        assertEquals("test", clientHttpResponse.getStatusText());
    }
    
    @Test
    void testGetHeaders() {
        assertEquals(3, clientHttpResponse.getHeaders().getHeader().size());
        assertEquals("testValue", clientHttpResponse.getHeaders().getValue("testName"));
    }
    
    @Test
    void testGetBody() throws IOException {
        when(connection.getInputStream()).thenReturn(inputStream);
        assertEquals(inputStream, clientHttpResponse.getBody());
    }
    
    @Test
    void testGetBodyWithGzip() throws IOException {
        byte[] testCase = IoUtils.tryCompress("test", StandardCharsets.UTF_8.name());
        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(testCase);
        when(connection.getInputStream()).thenReturn(byteArrayInputStream);
        headers.put(HttpHeaderConsts.CONTENT_ENCODING, Collections.singletonList("gzip"));
        assertEquals("test", IoUtils.toString(clientHttpResponse.getBody(), StandardCharsets.UTF_8.name()));
    }
}