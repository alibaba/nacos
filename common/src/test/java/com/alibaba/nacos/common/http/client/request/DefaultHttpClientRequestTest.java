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

import com.alibaba.nacos.common.constant.HttpHeaderConsts;
import com.alibaba.nacos.common.http.HttpClientConfig;
import com.alibaba.nacos.common.http.client.response.HttpClientResponse;
import com.alibaba.nacos.common.http.param.Header;
import com.alibaba.nacos.common.http.param.MediaType;
import com.alibaba.nacos.common.http.param.Query;
import com.alibaba.nacos.common.model.RequestHttpEntity;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.impl.client.CloseableHttpClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultHttpClientRequestTest {
    
    DefaultHttpClientRequest httpClientRequest;
    
    @Mock
    private CloseableHttpClient client;
    
    @Mock
    private CloseableHttpResponse response;
    
    private RequestConfig defaultConfig;
    
    private boolean isForm;
    
    private boolean withConfig;
    
    private URI uri;
    
    @BeforeEach
    void setUp() throws Exception {
        defaultConfig = RequestConfig.DEFAULT;
        httpClientRequest = new DefaultHttpClientRequest(client, defaultConfig);
        when(client.execute(argThat(httpUriRequest -> {
            HttpEntityEnclosingRequest entityRequest = (HttpEntityEnclosingRequest) httpUriRequest;
            boolean result = isForm == (entityRequest.getEntity() instanceof UrlEncodedFormEntity);
            HttpRequestBase baseHttpRequest = (HttpRequestBase) httpUriRequest;
            if (withConfig) {
                result &= null != baseHttpRequest.getConfig();
            }
            return result;
        }))).thenReturn(response);
        uri = URI.create("http://127.0.0.1:8080");
        
    }
    
    @AfterEach
    void tearDown() throws Exception {
        isForm = false;
        withConfig = false;
        httpClientRequest.close();
    }
    
    @Test
    void testExecuteForFormWithoutConfig() throws Exception {
        isForm = true;
        Header header = Header.newInstance().addParam(HttpHeaderConsts.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED);
        Map<String, String> body = new HashMap<>();
        body.put("test", "test");
        RequestHttpEntity httpEntity = new RequestHttpEntity(header, Query.EMPTY, body);
        HttpClientResponse actual = httpClientRequest.execute(uri, "PUT", httpEntity);
        assertEquals(response, getActualResponse(actual));
    }
    
    @Test
    void testExecuteForFormWithConfig() throws Exception {
        isForm = true;
        withConfig = true;
        Header header = Header.newInstance().addParam(HttpHeaderConsts.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED);
        Map<String, String> body = new HashMap<>();
        body.put("test", "test");
        RequestHttpEntity httpEntity = new RequestHttpEntity(HttpClientConfig.builder().build(), header, Query.EMPTY, body);
        HttpClientResponse actual = httpClientRequest.execute(uri, "PUT", httpEntity);
        assertEquals(response, getActualResponse(actual));
    }
    
    @Test
    void testExecuteForOther() throws Exception {
        Header header = Header.newInstance();
        RequestHttpEntity httpEntity = new RequestHttpEntity(header, Query.EMPTY, "body");
        HttpClientResponse actual = httpClientRequest.execute(uri, "PUT", httpEntity);
        assertEquals(response, getActualResponse(actual));
    }
    
    private CloseableHttpResponse getActualResponse(HttpClientResponse actual)
            throws IllegalAccessException, NoSuchFieldException {
        Field field = actual.getClass().getDeclaredField("response");
        field.setAccessible(true);
        return (CloseableHttpResponse) field.get(actual);
    }
}