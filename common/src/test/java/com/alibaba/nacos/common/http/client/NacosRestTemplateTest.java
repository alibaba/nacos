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

package com.alibaba.nacos.common.http.client;

import com.alibaba.nacos.common.constant.HttpHeaderConsts;
import com.alibaba.nacos.common.http.HttpClientConfig;
import com.alibaba.nacos.common.http.HttpRestResult;
import com.alibaba.nacos.common.http.client.request.HttpClientRequest;
import com.alibaba.nacos.common.http.client.response.HttpClientResponse;
import com.alibaba.nacos.common.http.param.Header;
import com.alibaba.nacos.common.http.param.MediaType;
import com.alibaba.nacos.common.http.param.Query;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.slf4j.Logger;

import java.io.ByteArrayInputStream;
import java.util.Collections;
import java.util.HashMap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class NacosRestTemplateTest {
    
    @Mock
    private HttpClientRequest requestClient;
    
    @Mock
    private Logger logger;
    
    @Mock
    private HttpClientResponse mockResponse;
    
    @Mock
    private HttpClientRequestInterceptor interceptor;
    
    private NacosRestTemplate restTemplate;
    
    @Before
    public void setUp() throws Exception {
        restTemplate = new NacosRestTemplate(logger, requestClient);
        when(logger.isDebugEnabled()).thenReturn(true);
        when(mockResponse.getHeaders()).thenReturn(Header.EMPTY);
        when(interceptor.isIntercept(any(), any(), any())).thenReturn(true);
        when(interceptor.intercept()).thenReturn(mockResponse);
    }
    
    @After
    public void tearDown() throws Exception {
        restTemplate.close();
    }
    
    @Test
    public void testGetWithDefaultConfig() throws Exception {
        when(requestClient.execute(any(), eq("GET"), any())).thenReturn(mockResponse);
        when(mockResponse.getStatusCode()).thenReturn(200);
        when(mockResponse.getBody()).thenReturn(new ByteArrayInputStream("test".getBytes()));
        HttpRestResult<String> result = restTemplate
                .get("http://127.0.0.1:8848/nacos/test", Header.EMPTY, Query.EMPTY, String.class);
        assertTrue(result.ok());
        assertEquals(Header.EMPTY, result.getHeader());
        assertEquals("test", result.getData());
    }
    
    @Test
    public void testGetWithCustomConfig() throws Exception {
        when(requestClient.execute(any(), eq("GET"), any())).thenReturn(mockResponse);
        when(mockResponse.getStatusCode()).thenReturn(200);
        when(mockResponse.getBody()).thenReturn(new ByteArrayInputStream("test".getBytes()));
        HttpClientConfig config = HttpClientConfig.builder().setConTimeOutMillis(1000).build();
        HttpRestResult<String> result = restTemplate
                .get("http://127.0.0.1:8848/nacos/test", config, Header.EMPTY, Query.EMPTY, String.class);
        assertTrue(result.ok());
        assertEquals(Header.EMPTY, result.getHeader());
        assertEquals("test", result.getData());
    }
    
    @Test
    public void testGetWithInterceptor() throws Exception {
        when(mockResponse.getStatusCode()).thenReturn(300);
        when(mockResponse.getBody()).thenReturn(new ByteArrayInputStream("test interceptor".getBytes()));
        restTemplate.setInterceptors(Collections.singletonList(interceptor));
        HttpRestResult<String> result = restTemplate
                .get("http://127.0.0.1:8848/nacos/test", Header.EMPTY, Query.EMPTY, String.class);
        assertFalse(result.ok());
        assertEquals(Header.EMPTY, result.getHeader());
        assertEquals("test interceptor", result.getMessage());
    }
    
    @Test(expected = RuntimeException.class)
    public void testGetWithException() throws Exception {
        when(requestClient.execute(any(), eq("GET"), any())).thenThrow(new RuntimeException("test"));
        restTemplate.get("http://127.0.0.1:8848/nacos/test", Header.EMPTY, Query.EMPTY, String.class);
    }
    
    @Test
    public void testGetLarge() throws Exception {
        when(requestClient.execute(any(), eq("GET-LARGE"), any())).thenReturn(mockResponse);
        when(mockResponse.getStatusCode()).thenReturn(200);
        when(mockResponse.getBody()).thenReturn(new ByteArrayInputStream("test".getBytes()));
        HttpRestResult<String> result = restTemplate
                .getLarge("http://127.0.0.1:8848/nacos/test", Header.EMPTY, Query.EMPTY, new Object(), String.class);
        assertTrue(result.ok());
        assertEquals(Header.EMPTY, result.getHeader());
        assertEquals("test", result.getData());
    }
    
    @Test
    public void testDeleteWithDefaultConfig() throws Exception {
        when(requestClient.execute(any(), eq("DELETE"), any())).thenReturn(mockResponse);
        when(mockResponse.getStatusCode()).thenReturn(200);
        when(mockResponse.getBody()).thenReturn(new ByteArrayInputStream("test".getBytes()));
        HttpRestResult<String> result = restTemplate
                .delete("http://127.0.0.1:8848/nacos/test", Header.EMPTY, Query.EMPTY, String.class);
        assertTrue(result.ok());
        assertEquals(Header.EMPTY, result.getHeader());
        assertEquals("test", result.getData());
    }
    
    @Test
    public void testDeleteWithCustomConfig() throws Exception {
        when(requestClient.execute(any(), eq("DELETE"), any())).thenReturn(mockResponse);
        when(mockResponse.getStatusCode()).thenReturn(200);
        when(mockResponse.getBody()).thenReturn(new ByteArrayInputStream("test".getBytes()));
        HttpClientConfig config = HttpClientConfig.builder().setConTimeOutMillis(1000).build();
        HttpRestResult<String> result = restTemplate
                .delete("http://127.0.0.1:8848/nacos/test", config, Header.EMPTY, Query.EMPTY, String.class);
        assertTrue(result.ok());
        assertEquals(Header.EMPTY, result.getHeader());
        assertEquals("test", result.getData());
    }
    
    @Test
    public void testPut() throws Exception {
        when(requestClient.execute(any(), eq("PUT"), any())).thenReturn(mockResponse);
        when(mockResponse.getStatusCode()).thenReturn(200);
        when(mockResponse.getBody()).thenReturn(new ByteArrayInputStream("test".getBytes()));
        HttpRestResult<String> result = restTemplate
                .put("http://127.0.0.1:8848/nacos/test", Header.EMPTY, Query.EMPTY, new Object(), String.class);
        assertTrue(result.ok());
        assertEquals(Header.EMPTY, result.getHeader());
        assertEquals("test", result.getData());
    }
    
    @Test
    public void testPutJson() throws Exception {
        when(requestClient.execute(any(), eq("PUT"), any())).thenReturn(mockResponse);
        when(mockResponse.getStatusCode()).thenReturn(200);
        when(mockResponse.getBody()).thenReturn(new ByteArrayInputStream("test".getBytes()));
        Header header = Header.newInstance().setContentType(MediaType.APPLICATION_XML);
        HttpRestResult<String> result = restTemplate
                .putJson("http://127.0.0.1:8848/nacos/test", header, "body", String.class);
        assertTrue(result.ok());
        assertEquals(Header.EMPTY, result.getHeader());
        assertEquals("test", result.getData());
        assertEquals(MediaType.APPLICATION_JSON, header.getValue(HttpHeaderConsts.CONTENT_TYPE));
    }
    
    @Test
    public void testPutJsonWithQuery() throws Exception {
        when(requestClient.execute(any(), eq("PUT"), any())).thenReturn(mockResponse);
        when(mockResponse.getStatusCode()).thenReturn(200);
        when(mockResponse.getBody()).thenReturn(new ByteArrayInputStream("test".getBytes()));
        Header header = Header.newInstance().setContentType(MediaType.APPLICATION_XML);
        HttpRestResult<String> result = restTemplate
                .putJson("http://127.0.0.1:8848/nacos/test", header, Query.EMPTY, "body", String.class);
        assertTrue(result.ok());
        assertEquals(Header.EMPTY, result.getHeader());
        assertEquals("test", result.getData());
        assertEquals(MediaType.APPLICATION_JSON, header.getValue(HttpHeaderConsts.CONTENT_TYPE));
    }
    
    @Test
    public void testPutForm() throws Exception {
        when(requestClient.execute(any(), eq("PUT"), any())).thenReturn(mockResponse);
        when(mockResponse.getStatusCode()).thenReturn(200);
        when(mockResponse.getBody()).thenReturn(new ByteArrayInputStream("test".getBytes()));
        Header header = Header.newInstance().setContentType(MediaType.APPLICATION_XML);
        HttpRestResult<String> result = restTemplate
                .putForm("http://127.0.0.1:8848/nacos/test", header, new HashMap<>(), String.class);
        assertTrue(result.ok());
        assertEquals(Header.EMPTY, result.getHeader());
        assertEquals("test", result.getData());
        assertEquals(MediaType.APPLICATION_FORM_URLENCODED, header.getValue(HttpHeaderConsts.CONTENT_TYPE));
    }
    
    @Test
    public void testPutFormWithQuery() throws Exception {
        when(requestClient.execute(any(), eq("PUT"), any())).thenReturn(mockResponse);
        when(mockResponse.getStatusCode()).thenReturn(200);
        when(mockResponse.getBody()).thenReturn(new ByteArrayInputStream("test".getBytes()));
        Header header = Header.newInstance().setContentType(MediaType.APPLICATION_XML);
        HttpRestResult<String> result = restTemplate
                .putForm("http://127.0.0.1:8848/nacos/test", header, Query.EMPTY, new HashMap<>(), String.class);
        assertTrue(result.ok());
        assertEquals(Header.EMPTY, result.getHeader());
        assertEquals("test", result.getData());
        assertEquals(MediaType.APPLICATION_FORM_URLENCODED, header.getValue(HttpHeaderConsts.CONTENT_TYPE));
    }
    
    @Test
    public void testPutFormWithConfig() throws Exception {
        when(requestClient.execute(any(), eq("PUT"), any())).thenReturn(mockResponse);
        when(mockResponse.getStatusCode()).thenReturn(200);
        when(mockResponse.getBody()).thenReturn(new ByteArrayInputStream("test".getBytes()));
        HttpClientConfig config = HttpClientConfig.builder().setConTimeOutMillis(1000).build();
        Header header = Header.newInstance().setContentType(MediaType.APPLICATION_XML);
        HttpRestResult<String> result = restTemplate
                .putForm("http://127.0.0.1:8848/nacos/test", config, header, new HashMap<>(), String.class);
        assertTrue(result.ok());
        assertEquals(Header.EMPTY, result.getHeader());
        assertEquals("test", result.getData());
        assertEquals(MediaType.APPLICATION_FORM_URLENCODED, header.getValue(HttpHeaderConsts.CONTENT_TYPE));
    }
    
    @Test
    public void testPost() throws Exception {
        when(requestClient.execute(any(), eq("POST"), any())).thenReturn(mockResponse);
        when(mockResponse.getStatusCode()).thenReturn(200);
        when(mockResponse.getBody()).thenReturn(new ByteArrayInputStream("test".getBytes()));
        HttpRestResult<String> result = restTemplate
                .post("http://127.0.0.1:8848/nacos/test", Header.EMPTY, Query.EMPTY, new Object(), String.class);
        assertTrue(result.ok());
        assertEquals(Header.EMPTY, result.getHeader());
        assertEquals("test", result.getData());
    }
    
    @Test
    public void testPostJson() throws Exception {
        when(requestClient.execute(any(), eq("POST"), any())).thenReturn(mockResponse);
        when(mockResponse.getStatusCode()).thenReturn(200);
        when(mockResponse.getBody()).thenReturn(new ByteArrayInputStream("test".getBytes()));
        Header header = Header.newInstance().setContentType(MediaType.APPLICATION_XML);
        HttpRestResult<String> result = restTemplate
                .postJson("http://127.0.0.1:8848/nacos/test", header, "body", String.class);
        assertTrue(result.ok());
        assertEquals(Header.EMPTY, result.getHeader());
        assertEquals("test", result.getData());
        assertEquals(MediaType.APPLICATION_JSON, header.getValue(HttpHeaderConsts.CONTENT_TYPE));
    }
    
    @Test
    public void testPostJsonWithQuery() throws Exception {
        when(requestClient.execute(any(), eq("POST"), any())).thenReturn(mockResponse);
        when(mockResponse.getStatusCode()).thenReturn(200);
        when(mockResponse.getBody()).thenReturn(new ByteArrayInputStream("test".getBytes()));
        Header header = Header.newInstance().setContentType(MediaType.APPLICATION_XML);
        HttpRestResult<String> result = restTemplate
                .postJson("http://127.0.0.1:8848/nacos/test", header, Query.EMPTY, "body", String.class);
        assertTrue(result.ok());
        assertEquals(Header.EMPTY, result.getHeader());
        assertEquals("test", result.getData());
        assertEquals(MediaType.APPLICATION_JSON, header.getValue(HttpHeaderConsts.CONTENT_TYPE));
    }
    
    @Test
    public void testPostForm() throws Exception {
        when(requestClient.execute(any(), eq("POST"), any())).thenReturn(mockResponse);
        when(mockResponse.getStatusCode()).thenReturn(200);
        when(mockResponse.getBody()).thenReturn(new ByteArrayInputStream("test".getBytes()));
        Header header = Header.newInstance().setContentType(MediaType.APPLICATION_XML);
        HttpRestResult<String> result = restTemplate
                .postForm("http://127.0.0.1:8848/nacos/test", header, new HashMap<>(), String.class);
        assertTrue(result.ok());
        assertEquals(Header.EMPTY, result.getHeader());
        assertEquals("test", result.getData());
        assertEquals(MediaType.APPLICATION_FORM_URLENCODED, header.getValue(HttpHeaderConsts.CONTENT_TYPE));
    }
    
    @Test
    public void testPostFormWithQuery() throws Exception {
        when(requestClient.execute(any(), eq("POST"), any())).thenReturn(mockResponse);
        when(mockResponse.getStatusCode()).thenReturn(200);
        when(mockResponse.getBody()).thenReturn(new ByteArrayInputStream("test".getBytes()));
        Header header = Header.newInstance().setContentType(MediaType.APPLICATION_XML);
        HttpRestResult<String> result = restTemplate
                .postForm("http://127.0.0.1:8848/nacos/test", header, Query.EMPTY, new HashMap<>(), String.class);
        assertTrue(result.ok());
        assertEquals(Header.EMPTY, result.getHeader());
        assertEquals("test", result.getData());
        assertEquals(MediaType.APPLICATION_FORM_URLENCODED, header.getValue(HttpHeaderConsts.CONTENT_TYPE));
    }
    
    @Test
    public void testPostFormWithConfig() throws Exception {
        when(requestClient.execute(any(), eq("POST"), any())).thenReturn(mockResponse);
        when(mockResponse.getStatusCode()).thenReturn(200);
        when(mockResponse.getBody()).thenReturn(new ByteArrayInputStream("test".getBytes()));
        HttpClientConfig config = HttpClientConfig.builder().setConTimeOutMillis(1000).build();
        Header header = Header.newInstance().setContentType(MediaType.APPLICATION_XML);
        HttpRestResult<String> result = restTemplate
                .postForm("http://127.0.0.1:8848/nacos/test", config, header, new HashMap<>(), String.class);
        assertTrue(result.ok());
        assertEquals(Header.EMPTY, result.getHeader());
        assertEquals("test", result.getData());
        assertEquals(MediaType.APPLICATION_FORM_URLENCODED, header.getValue(HttpHeaderConsts.CONTENT_TYPE));
    }
    
    @Test
    public void testExchangeForm() throws Exception {
        when(requestClient.execute(any(), eq("PUT"), any())).thenReturn(mockResponse);
        when(mockResponse.getStatusCode()).thenReturn(200);
        when(mockResponse.getBody()).thenReturn(new ByteArrayInputStream("test".getBytes()));
        Header header = Header.newInstance().setContentType(MediaType.APPLICATION_XML);
        HttpRestResult<String> result = restTemplate
                .exchangeForm("http://127.0.0.1:8848/nacos/test", header, Query.EMPTY, new HashMap<>(), "PUT",
                        String.class);
        assertTrue(result.ok());
        assertEquals(Header.EMPTY, result.getHeader());
        assertEquals("test", result.getData());
        assertEquals(MediaType.APPLICATION_FORM_URLENCODED, header.getValue(HttpHeaderConsts.CONTENT_TYPE));
    }
    
    @Test
    public void testExchange() throws Exception {
        when(requestClient.execute(any(), eq("PUT"), any())).thenReturn(mockResponse);
        when(mockResponse.getStatusCode()).thenReturn(200);
        when(mockResponse.getBody()).thenReturn(new ByteArrayInputStream("test".getBytes()));
        HttpClientConfig config = HttpClientConfig.builder().setConTimeOutMillis(1000).build();
        HttpRestResult<String> result = restTemplate
                .exchange("http://127.0.0.1:8848/nacos/test", config, Header.EMPTY, Query.EMPTY, new Object(), "PUT",
                        String.class);
        assertTrue(result.ok());
        assertEquals(Header.EMPTY, result.getHeader());
        assertEquals("test", result.getData());
    }
    
    @Test
    public void testGetInterceptors() {
        assertTrue(restTemplate.getInterceptors().isEmpty());
        restTemplate.setInterceptors(Collections.singletonList(interceptor));
        assertEquals(1, restTemplate.getInterceptors().size());
    }
}