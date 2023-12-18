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

import com.alibaba.nacos.common.http.client.request.HttpClientRequest;
import com.alibaba.nacos.common.http.client.response.HttpClientResponse;
import com.alibaba.nacos.common.http.param.Header;
import com.alibaba.nacos.common.http.param.Query;
import com.alibaba.nacos.common.model.RequestHttpEntity;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.net.URI;
import java.util.LinkedList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class InterceptingHttpClientRequestTest {
    
    @Mock
    private HttpClientRequest httpClientRequest;
    
    @Mock
    private HttpClientRequestInterceptor interceptor;
    
    @Mock
    private HttpClientResponse interceptorResponse;
    
    @Mock
    private HttpClientResponse httpClientResponse;
    
    InterceptingHttpClientRequest clientRequest;
    
    @Before
    public void setUp() throws Exception {
        List<HttpClientRequestInterceptor> interceptorList = new LinkedList<>();
        interceptorList.add(interceptor);
        clientRequest = new InterceptingHttpClientRequest(httpClientRequest, interceptorList.listIterator());
        when(interceptor.intercept()).thenReturn(interceptorResponse);
        when(httpClientRequest.execute(any(), any(), any())).thenReturn(httpClientResponse);
    }
    
    @After
    public void tearDown() throws Exception {
        clientRequest.close();
    }
    
    @Test
    public void testExecuteIntercepted() throws Exception {
        when(interceptor.isIntercept(any(), any(), any())).thenReturn(true);
        HttpClientResponse response = clientRequest
                .execute(URI.create("http://example.com"), "GET", new RequestHttpEntity(Header.EMPTY, Query.EMPTY));
        assertEquals(interceptorResponse, response);
    }
    
    @Test
    public void testExecuteNotIntercepted() throws Exception {
        HttpClientResponse response = clientRequest
                .execute(URI.create("http://example.com"), "GET", new RequestHttpEntity(Header.EMPTY, Query.EMPTY));
        assertEquals(httpClientResponse, response);
    }
}