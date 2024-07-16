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

import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.message.StatusLine;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.IOException;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
// todo remove this
@MockitoSettings(strictness = Strictness.LENIENT)
class DefaultClientHttpResponseTest {
    
    DefaultClientHttpResponse clientHttpResponse;
    
    @Mock
    private SimpleHttpResponse response;
    
    @Mock
    private StatusLine statusLine;
    
    @Mock
    private HttpEntity httpEntity;
    
    @Mock
    private InputStream inputStream;
    
    @Mock
    private Header header;
    
    @BeforeEach
    void setUp() throws Exception {
        when(httpEntity.getContent()).thenReturn(inputStream);
        // when(response.getEntity()).thenReturn(httpEntity);
        // when(response.getStatusLine()).thenReturn(statusLine);
        when(response.getHeaders()).thenReturn(new Header[] {header});
        when(header.getName()).thenReturn("testName");
        when(header.getValue()).thenReturn("testValue");
        clientHttpResponse = new DefaultClientHttpResponse(response);
    }
    
    @AfterEach
    void tearDown() throws Exception {
        clientHttpResponse.close();
    }
    
    @Test
    void testGetStatusCode() {
        when(statusLine.getStatusCode()).thenReturn(200);
        assertEquals(200, clientHttpResponse.getStatusCode());
    }
    
    @Test
    void testGetStatusText() {
        when(statusLine.getReasonPhrase()).thenReturn("test");
        assertEquals("test", clientHttpResponse.getStatusText());
    }
    
    @Test
    void testGetHeaders() {
        assertEquals(3, clientHttpResponse.getHeaders().getHeader().size());
        assertEquals("testValue", clientHttpResponse.getHeaders().getValue("testName"));
    }
    
    @Test
    void testGetBody() throws IOException {
        assertEquals(inputStream, clientHttpResponse.getBody());
    }
}