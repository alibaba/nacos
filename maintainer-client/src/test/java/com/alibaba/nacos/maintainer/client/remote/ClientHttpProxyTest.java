/*
 * Copyright 1999-$toady.year Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.maintainer.client.remote;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.common.http.HttpRestResult;
import com.alibaba.nacos.common.http.client.NacosRestTemplate;
import com.alibaba.nacos.maintainer.client.address.DefaultServerListManager;
import com.alibaba.nacos.maintainer.client.model.HttpRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.net.ConnectException;
import java.util.HashMap;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ClientHttpProxyTest {
    
    private ClientHttpProxy clientHttpProxy;
    
    private DefaultServerListManager mockServerListManager;
    
    private NacosRestTemplate mockNacosRestTemplate;
    
    @BeforeEach
    public void setUp() throws Exception {
        mockServerListManager = mock(DefaultServerListManager.class);
        mockNacosRestTemplate = mock(NacosRestTemplate.class);
        
        HttpClientManager mockHttpClientManager = mock(HttpClientManager.class);
        when(mockHttpClientManager.getNacosRestTemplate()).thenReturn(mockNacosRestTemplate);
        
        Field instanceField = HttpClientManager.class.getDeclaredField("httpClientManager");
        instanceField.setAccessible(true);
        instanceField.set(null, mockHttpClientManager);
        
        Properties properties = new Properties();
        properties.setProperty("serverAddr", "localhost:8848");
        clientHttpProxy = new ClientHttpProxy(properties);
        
        Field serverListManagerField = ClientHttpProxy.class.getDeclaredField("serverListManager");
        serverListManagerField.setAccessible(true);
        serverListManagerField.set(clientHttpProxy, mockServerListManager);
    }
    
    @Test
    void testExecuteSyncHttpRequestSuccess() throws Exception {
        when(mockServerListManager.getCurrentServer()).thenReturn("http://127.0.0.1:8848");
        
        HttpRestResult<Object> mockResult = new HttpRestResult<>();
        mockResult.setCode(200);
        mockResult.setData("Success");
        when(mockNacosRestTemplate.get(anyString(), any(), any(), any(), eq(String.class))).thenReturn(mockResult);
        
        HttpRequest request = new HttpRequest("GET", "/test", new HashMap<>(), new HashMap<>(), null);
        
        HttpRestResult<String> result = clientHttpProxy.executeSyncHttpRequest(request);
        
        assertNotNull(result);
        assertEquals(200, result.getCode());
        assertEquals("Success", result.getData());
        
        verify(mockNacosRestTemplate, times(1)).get(anyString(), any(), any(), any(), eq(String.class));
    }
    
    @Test
    void testExecuteSyncHttpRequestRetryOnFailure() throws Exception {
        when(mockServerListManager.getCurrentServer()).thenReturn("http://127.0.0.1:8848");
        when(mockServerListManager.getIterator()).thenReturn(new ServerAddressIteratorMock());
        
        HttpRestResult<Object> mockFailureResult = new HttpRestResult<>();
        mockFailureResult.setCode(500);
        HttpRestResult<Object> mockSuccessResult = new HttpRestResult<>();
        mockSuccessResult.setCode(200);
        mockSuccessResult.setData("Success");
        
        when(mockNacosRestTemplate.get(anyString(), any(), any(), any(), eq(String.class))).thenReturn(
                mockFailureResult).thenReturn(mockSuccessResult);
        
        HttpRequest request = new HttpRequest("GET", "/test", new HashMap<>(), new HashMap<>(), null);
        
        HttpRestResult<String> result = clientHttpProxy.executeSyncHttpRequest(request);
        
        assertNotNull(result);
        assertEquals(200, result.getCode());
        assertEquals("Success", result.getData());
        
        verify(mockNacosRestTemplate, times(2)).get(anyString(), any(), any(), any(), eq(String.class));
    }
    
    @Test
    void testExecuteSyncHttpRequestMaxRetryExceeded() throws Exception {
        when(mockServerListManager.getCurrentServer()).thenReturn("http://127.0.0.1:8848");
        when(mockServerListManager.getIterator()).thenReturn(new ServerAddressIteratorMock());
        
        HttpRestResult<Object> mockFailureResult = new HttpRestResult<>();
        mockFailureResult.setCode(500);
        when(mockNacosRestTemplate.get(anyString(), any(), any(), any(), eq(String.class))).thenReturn(
                mockFailureResult);
        
        HttpRequest request = new HttpRequest("GET", "/test", new HashMap<>(), new HashMap<>(), null);
        
        Exception exception = assertThrows(NacosException.class, () -> {
            clientHttpProxy.executeSyncHttpRequest(request);
        });
        
        assertTrue(exception.getMessage().contains("No available server after"));
        
        verify(mockNacosRestTemplate, times(4)).get(anyString(), any(), any(), any(), eq(String.class));
    }
    
    private static class ServerAddressIteratorMock implements java.util.Iterator<String> {
        
        private int count = 0;
        
        @Override
        public boolean hasNext() {
            return count < 2;
        }
        
        @Override
        public String next() {
            count++;
            return "http://127.0.0.1:884" + count;
        }
    }
}

