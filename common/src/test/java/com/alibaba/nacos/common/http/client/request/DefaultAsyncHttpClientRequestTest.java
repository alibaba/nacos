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

import com.alibaba.nacos.common.http.Callback;
import com.alibaba.nacos.common.http.HttpRestResult;
import com.alibaba.nacos.common.http.client.handler.ResponseHandler;
import com.alibaba.nacos.common.http.param.Header;
import com.alibaba.nacos.common.http.param.Query;
import com.alibaba.nacos.common.model.RequestHttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.impl.nio.reactor.DefaultConnectingIOReactor;
import org.apache.http.impl.nio.reactor.ExceptionEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.URI;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultAsyncHttpClientRequestTest {
    
    DefaultAsyncHttpClientRequest httpClientRequest;
    
    @Mock
    private CloseableHttpAsyncClient client;
    
    @Mock
    private DefaultConnectingIOReactor ioReactor;
    
    @Mock
    private Callback callback;
    
    @Mock
    private ResponseHandler responseHandler;
    
    private RequestConfig defaultConfig;
    
    private URI uri;
    
    @BeforeEach
    void setUp() throws Exception {
        defaultConfig = RequestConfig.DEFAULT;
        httpClientRequest = new DefaultAsyncHttpClientRequest(client, ioReactor, defaultConfig);
        uri = URI.create("http://127.0.0.1:8080");
    }
    
    @AfterEach
    void tearDown() throws Exception {
        httpClientRequest.close();
    }
    
    @Test
    void testExecuteOnFail() throws Exception {
        Header header = Header.newInstance();
        Map<String, String> body = new HashMap<>();
        body.put("test", "test");
        RequestHttpEntity httpEntity = new RequestHttpEntity(header, Query.EMPTY, body);
        RuntimeException exception = new RuntimeException("test");
        when(client.execute(any(), any())).thenAnswer(invocationOnMock -> {
            ((FutureCallback) invocationOnMock.getArgument(1)).failed(exception);
            return null;
        });
        httpClientRequest.execute(uri, "PUT", httpEntity, responseHandler, callback);
        verify(callback).onError(exception);
    }
    
    @Test
    void testExecuteOnCancel() throws Exception {
        Header header = Header.newInstance();
        Map<String, String> body = new HashMap<>();
        body.put("test", "test");
        RequestHttpEntity httpEntity = new RequestHttpEntity(header, Query.EMPTY, body);
        when(client.execute(any(), any())).thenAnswer(invocationOnMock -> {
            ((FutureCallback) invocationOnMock.getArgument(1)).cancelled();
            return null;
        });
        httpClientRequest.execute(uri, "PUT", httpEntity, responseHandler, callback);
        verify(callback).onCancel();
    }
    
    @Test
    void testExecuteOnComplete() throws Exception {
        Header header = Header.newInstance();
        Map<String, String> body = new HashMap<>();
        body.put("test", "test");
        RequestHttpEntity httpEntity = new RequestHttpEntity(header, Query.EMPTY, body);
        HttpResponse response = mock(HttpResponse.class);
        HttpRestResult restResult = new HttpRestResult();
        when(responseHandler.handle(any())).thenReturn(restResult);
        when(client.execute(any(), any())).thenAnswer(invocationOnMock -> {
            ((FutureCallback) invocationOnMock.getArgument(1)).completed(response);
            return null;
        });
        httpClientRequest.execute(uri, "PUT", httpEntity, responseHandler, callback);
        verify(callback).onReceive(restResult);
    }
    
    @Test
    void testExecuteOnCompleteWithException() throws Exception {
        Header header = Header.newInstance();
        Map<String, String> body = new HashMap<>();
        body.put("test", "test");
        RequestHttpEntity httpEntity = new RequestHttpEntity(header, Query.EMPTY, body);
        HttpResponse response = mock(HttpResponse.class);
        RuntimeException exception = new RuntimeException("test");
        when(responseHandler.handle(any())).thenThrow(exception);
        when(client.execute(any(), any())).thenAnswer(invocationOnMock -> {
            ((FutureCallback) invocationOnMock.getArgument(1)).completed(response);
            return null;
        });
        httpClientRequest.execute(uri, "PUT", httpEntity, responseHandler, callback);
        verify(callback).onError(exception);
    }
    
    @Test
    void testExecuteException() throws Exception {
        Header header = Header.newInstance();
        Map<String, String> body = new HashMap<>();
        body.put("test", "test");
        RequestHttpEntity httpEntity = new RequestHttpEntity(header, Query.EMPTY, body);
        IllegalStateException exception = new IllegalStateException("test");
        when(client.execute(any(), any())).thenThrow(exception);
        when(ioReactor.getAuditLog()).thenReturn(Collections.singletonList(new ExceptionEvent(exception, new Date())));
        try {
            httpClientRequest.execute(uri, "PUT", httpEntity, responseHandler, callback);
        } catch (Exception e) {
            assertEquals(exception, e);
        }
    }
}