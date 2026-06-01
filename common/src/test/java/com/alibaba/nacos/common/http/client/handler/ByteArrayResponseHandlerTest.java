/*
 * Copyright 1999-2025 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.common.http.client.handler;

import com.alibaba.nacos.common.http.HttpRestResult;
import com.alibaba.nacos.common.http.client.response.HttpClientResponse;
import com.alibaba.nacos.common.http.param.Header;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Type;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;

/**
 * Unit tests for ByteArrayResponseHandler.
 *
 * @author nacos
 */
@ExtendWith(MockitoExtension.class)
class ByteArrayResponseHandlerTest {
    
    @Mock
    private HttpClientResponse response;
    
    @Mock
    private Type responseType;
    
    private ByteArrayResponseHandler handler;
    
    @BeforeEach
    void setUp() {
        handler = new ByteArrayResponseHandler();
    }
    
    @Test
    @DisplayName("convertResult should return byte array from response body")
    void testConvertResultShouldReturnByteArray() throws Exception {
        byte[] expectedBytes = "test content".getBytes();
        Header headers = Header.newInstance();
        
        when(response.getHeaders()).thenReturn(headers);
        when(response.getBody()).thenReturn(new ByteArrayInputStream(expectedBytes));
        when(response.getStatusCode()).thenReturn(200);
        
        HttpRestResult<byte[]> result = handler.convertResult(response, responseType);
        
        assertNotNull(result);
        assertArrayEquals(expectedBytes, result.getData());
    }
    
    @Test
    @DisplayName("handle should return HttpRestResult with status code 200")
    void testHandleShouldReturnHttpRestResult() throws Exception {
        byte[] expectedBytes = "hello world".getBytes();
        Header headers = Header.newInstance();
        
        when(response.getHeaders()).thenReturn(headers);
        when(response.getBody()).thenReturn(new ByteArrayInputStream(expectedBytes));
        when(response.getStatusCode()).thenReturn(200);
        
        HttpRestResult<byte[]> result = handler.handle(response);
        
        assertNotNull(result);
        assertEquals(200, result.getCode());
        assertArrayEquals(expectedBytes, result.getData());
    }
    
    @Test
    @DisplayName("convertResult with empty body should return empty array")
    void testConvertResultWithEmptyBodyShouldReturnEmptyArray() throws Exception {
        Header headers = Header.newInstance();
        
        when(response.getHeaders()).thenReturn(headers);
        when(response.getBody()).thenReturn(new ByteArrayInputStream(new byte[0]));
        when(response.getStatusCode()).thenReturn(200);
        
        HttpRestResult<byte[]> result = handler.convertResult(response, responseType);
        
        assertNotNull(result);
        assertArrayEquals(new byte[0], result.getData());
    }
}
