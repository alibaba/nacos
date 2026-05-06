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
import com.alibaba.nacos.common.http.Callback;
import com.alibaba.nacos.common.http.client.request.AsyncHttpClientRequest;
import com.alibaba.nacos.common.http.param.Header;
import com.alibaba.nacos.common.http.param.MediaType;
import com.alibaba.nacos.common.http.param.Query;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;

import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NacosAsyncRestTemplateTest {
    
    private static final String TEST_URL = "http://127.0.0.1:8848/nacos/test";
    
    @Mock
    private AsyncHttpClientRequest requestClient;
    
    @Mock
    private Logger logger;
    
    @Mock
    private Callback<String> mockCallback;
    
    private NacosAsyncRestTemplate restTemplate;
    
    @BeforeEach
    void setUp() throws Exception {
        restTemplate = new NacosAsyncRestTemplate(logger, requestClient);
        when(logger.isDebugEnabled()).thenReturn(true);
    }
    
    @AfterEach
    void tearDown() throws Exception {
        restTemplate.close();
    }
    
    @Test
    void testGet() throws Exception {
        restTemplate.get(TEST_URL, Header.EMPTY, Query.EMPTY, String.class, mockCallback);
        verify(requestClient).execute(any(), eq("GET"), any(), any(), eq(mockCallback));
    }
    
    @Test
    void testGetWithException() throws Exception {
        doThrow(new RuntimeException("test")).when(requestClient).execute(any(), any(), any(), any(), any());
        restTemplate.get(TEST_URL, Header.EMPTY, Query.EMPTY, String.class, mockCallback);
        verify(requestClient).execute(any(), eq("GET"), any(), any(), eq(mockCallback));
        verify(mockCallback).onError(any(RuntimeException.class));
    }
    
    @Test
    void testGetLarge() throws Exception {
        restTemplate.getLarge(TEST_URL, Header.EMPTY, Query.EMPTY, new Object(), String.class, mockCallback);
        verify(requestClient).execute(any(), eq("GET-LARGE"), any(), any(), eq(mockCallback));
    }
    
    @Test
    void testDeleteWithBody() throws Exception {
        restTemplate.delete(TEST_URL, Header.EMPTY, Query.EMPTY, String.class, mockCallback);
        verify(requestClient).execute(any(), eq("DELETE"), any(), any(), eq(mockCallback));
    }
    
    @Test
    void testDeleteLarge() throws Exception {
        restTemplate.delete(TEST_URL, Header.EMPTY, "body", String.class, mockCallback);
        verify(requestClient).execute(any(), eq("DELETE_LARGE"), any(), any(), eq(mockCallback));
    }
    
    @Test
    void testPut() throws Exception {
        restTemplate.put(TEST_URL, Header.EMPTY, Query.EMPTY, "body", String.class, mockCallback);
        verify(requestClient).execute(any(), eq("PUT"), any(), any(), eq(mockCallback));
    }
    
    @Test
    void testPutJson() throws Exception {
        Header header = Header.newInstance().setContentType(MediaType.APPLICATION_XML);
        restTemplate.putJson(TEST_URL, header, "body", String.class, mockCallback);
        verify(requestClient).execute(any(), eq("PUT"), any(), any(), eq(mockCallback));
        assertEquals(MediaType.APPLICATION_JSON, header.getValue(HttpHeaderConsts.CONTENT_TYPE));
    }
    
    @Test
    void testPutJsonWithQuery() throws Exception {
        Header header = Header.newInstance().setContentType(MediaType.APPLICATION_XML);
        restTemplate.putJson(TEST_URL, header, Query.EMPTY, "body", String.class, mockCallback);
        verify(requestClient).execute(any(), eq("PUT"), any(), any(), eq(mockCallback));
        assertEquals(MediaType.APPLICATION_JSON, header.getValue(HttpHeaderConsts.CONTENT_TYPE));
    }
    
    @Test
    void testPutForm() throws Exception {
        Header header = Header.newInstance().setContentType(MediaType.APPLICATION_XML);
        restTemplate.putForm(TEST_URL, header, new HashMap<>(), String.class, mockCallback);
        verify(requestClient).execute(any(), eq("PUT"), any(), any(), eq(mockCallback));
        assertEquals(MediaType.APPLICATION_FORM_URLENCODED, header.getValue(HttpHeaderConsts.CONTENT_TYPE));
    }
    
    @Test
    void testPutFormWithQuery() throws Exception {
        Header header = Header.newInstance().setContentType(MediaType.APPLICATION_XML);
        restTemplate.putForm(TEST_URL, header, Query.EMPTY, new HashMap<>(), String.class, mockCallback);
        verify(requestClient).execute(any(), eq("PUT"), any(), any(), eq(mockCallback));
        assertEquals(MediaType.APPLICATION_FORM_URLENCODED, header.getValue(HttpHeaderConsts.CONTENT_TYPE));
    }
    
    @Test
    void testPost() throws Exception {
        restTemplate.post(TEST_URL, Header.EMPTY, Query.EMPTY, "body", String.class, mockCallback);
        verify(requestClient).execute(any(), eq("POST"), any(), any(), eq(mockCallback));
    }
    
    @Test
    void testPostJson() throws Exception {
        Header header = Header.newInstance().setContentType(MediaType.APPLICATION_XML);
        restTemplate.postJson(TEST_URL, header, "body", String.class, mockCallback);
        verify(requestClient).execute(any(), eq("POST"), any(), any(), eq(mockCallback));
        assertEquals(MediaType.APPLICATION_JSON, header.getValue(HttpHeaderConsts.CONTENT_TYPE));
    }
    
    @Test
    void testPostJsonWithQuery() throws Exception {
        Header header = Header.newInstance().setContentType(MediaType.APPLICATION_XML);
        restTemplate.postJson(TEST_URL, header, Query.EMPTY, "body", String.class, mockCallback);
        verify(requestClient).execute(any(), eq("POST"), any(), any(), eq(mockCallback));
        assertEquals(MediaType.APPLICATION_JSON, header.getValue(HttpHeaderConsts.CONTENT_TYPE));
    }
    
    @Test
    void testPostForm() throws Exception {
        Header header = Header.newInstance().setContentType(MediaType.APPLICATION_XML);
        restTemplate.postForm(TEST_URL, header, new HashMap<>(), String.class, mockCallback);
        verify(requestClient).execute(any(), eq("POST"), any(), any(), eq(mockCallback));
        assertEquals(MediaType.APPLICATION_FORM_URLENCODED, header.getValue(HttpHeaderConsts.CONTENT_TYPE));
    }
    
    @Test
    void testPostFormWithQuery() throws Exception {
        Header header = Header.newInstance().setContentType(MediaType.APPLICATION_XML);
        restTemplate.postForm(TEST_URL, header, Query.EMPTY, new HashMap<>(), String.class, mockCallback);
        verify(requestClient).execute(any(), eq("POST"), any(), any(), eq(mockCallback));
        assertEquals(MediaType.APPLICATION_FORM_URLENCODED, header.getValue(HttpHeaderConsts.CONTENT_TYPE));
    }
}