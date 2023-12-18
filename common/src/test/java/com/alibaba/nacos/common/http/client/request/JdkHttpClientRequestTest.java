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

package com.alibaba.nacos.common.http.client.request;

import com.alibaba.nacos.common.http.HttpClientConfig;
import com.alibaba.nacos.common.http.HttpUtils;
import com.alibaba.nacos.common.http.client.response.HttpClientResponse;
import com.alibaba.nacos.common.http.param.Header;
import com.alibaba.nacos.common.http.param.MediaType;
import com.alibaba.nacos.common.http.param.Query;
import com.alibaba.nacos.common.model.RequestHttpEntity;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.OutputStream;
import java.lang.reflect.Field;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class JdkHttpClientRequestTest {
    
    @Mock
    private HttpURLConnection connection;
    
    @Mock
    private URI uri;
    
    @Mock
    private URL url;
    
    @Mock
    private OutputStream outputStream;
    
    JdkHttpClientRequest httpClientRequest;
    
    private HttpClientConfig httpClientConfig;
    
    @Before
    public void setUp() throws Exception {
        when(uri.toURL()).thenReturn(url);
        when(url.openConnection()).thenReturn(connection);
        when(connection.getOutputStream()).thenReturn(outputStream);
        httpClientConfig = HttpClientConfig.builder().build();
        httpClientRequest = new JdkHttpClientRequest(httpClientConfig);
    }
    
    @After
    public void tearDown() throws Exception {
        httpClientRequest.close();
    }
    
    @Test
    public void testExecuteNormal() throws Exception {
        Header header = Header.newInstance();
        HttpClientConfig config = HttpClientConfig.builder().build();
        RequestHttpEntity httpEntity = new RequestHttpEntity(config, header, Query.EMPTY, "a=bo&dy");
        HttpClientResponse response = httpClientRequest.execute(uri, "GET", httpEntity);
        byte[] writeBytes = "a=bo&dy".getBytes(StandardCharsets.UTF_8);
        verify(outputStream).write(writeBytes, 0, writeBytes.length);
        assertEquals(connection, getActualConnection(response));
    }
    
    @Test
    public void testExecuteForm() throws Exception {
        Header header = Header.newInstance();
        header.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        HttpClientConfig config = HttpClientConfig.builder().build();
        Map<String, String> body = new HashMap<>();
        body.put("a", "bo&dy");
        RequestHttpEntity httpEntity = new RequestHttpEntity(config, header, Query.EMPTY, body);
        HttpClientResponse response = httpClientRequest.execute(uri, "GET", httpEntity);
        byte[] writeBytes = HttpUtils.encodingParams(body, StandardCharsets.UTF_8.name())
                .getBytes(StandardCharsets.UTF_8);
        verify(outputStream).write(writeBytes, 0, writeBytes.length);
        assertEquals(connection, getActualConnection(response));
    }
    
    @Test
    public void testExecuteEmptyBody() throws Exception {
        Header header = Header.newInstance();
        RequestHttpEntity httpEntity = new RequestHttpEntity(header, Query.EMPTY);
        HttpClientResponse response = httpClientRequest.execute(uri, "GET", httpEntity);
        verify(outputStream, never()).write(any(), eq(0), anyInt());
        assertEquals(connection, getActualConnection(response));
        
    }
    
    private HttpURLConnection getActualConnection(HttpClientResponse actual)
            throws IllegalAccessException, NoSuchFieldException {
        Field field = actual.getClass().getDeclaredField("conn");
        field.setAccessible(true);
        return (HttpURLConnection) field.get(actual);
    }
}