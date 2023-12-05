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
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class JdkClientHttpResponseTest {
    
    @Mock
    private HttpURLConnection connection;
    
    @Mock
    private InputStream inputStream;
    
    private Map<String, List<String>> headers;
    
    JdkHttpClientResponse clientHttpResponse;
    
    @Before
    public void setUp() throws Exception {
        headers = new HashMap<>();
        headers.put("testName", Collections.singletonList("testValue"));
        when(connection.getHeaderFields()).thenReturn(headers);
        clientHttpResponse = new JdkHttpClientResponse(connection);
    }
    
    @After
    public void tearDown() throws Exception {
        clientHttpResponse.close();
    }
    
    @Test
    public void testGetStatusCode() throws IOException {
        when(connection.getResponseCode()).thenReturn(200);
        assertEquals(200, clientHttpResponse.getStatusCode());
    }
    
    @Test
    public void testGetStatusText() throws IOException {
        when(connection.getResponseMessage()).thenReturn("test");
        assertEquals("test", clientHttpResponse.getStatusText());
    }
    
    @Test
    public void testGetHeaders() {
        assertEquals(3, clientHttpResponse.getHeaders().getHeader().size());
        assertEquals("testValue", clientHttpResponse.getHeaders().getValue("testName"));
    }
    
    @Test
    public void testGetBody() throws IOException {
        when(connection.getInputStream()).thenReturn(inputStream);
        assertEquals(inputStream, clientHttpResponse.getBody());
    }
    
    @Test
    public void testGetBodyWithGzip() throws IOException {
        byte[] testCase = IoUtils.tryCompress("test", StandardCharsets.UTF_8.name());
        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(testCase);
        when(connection.getInputStream()).thenReturn(byteArrayInputStream);
        headers.put(HttpHeaderConsts.CONTENT_ENCODING, Collections.singletonList("gzip"));
        assertEquals("test", IoUtils.toString(clientHttpResponse.getBody(), StandardCharsets.UTF_8.name()));
    }
}