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

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.IOException;
import java.io.InputStream;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class DefaultClientHttpResponseTest {
    
    @Mock
    private HttpResponse response;
    
    @Mock
    private StatusLine statusLine;
    
    @Mock
    private HttpEntity httpEntity;
    
    @Mock
    private InputStream inputStream;
    
    @Mock
    private Header header;
    
    DefaultClientHttpResponse clientHttpResponse;
    
    @Before
    public void setUp() throws Exception {
        when(httpEntity.getContent()).thenReturn(inputStream);
        when(response.getEntity()).thenReturn(httpEntity);
        when(response.getStatusLine()).thenReturn(statusLine);
        when(response.getAllHeaders()).thenReturn(new Header[] {header});
        when(header.getName()).thenReturn("testName");
        when(header.getValue()).thenReturn("testValue");
        clientHttpResponse = new DefaultClientHttpResponse(response);
    }
    
    @After
    public void tearDown() throws Exception {
        clientHttpResponse.close();
    }
    
    @Test
    public void testGetStatusCode() {
        when(statusLine.getStatusCode()).thenReturn(200);
        assertEquals(200, clientHttpResponse.getStatusCode());
    }
    
    @Test
    public void testGetStatusText() {
        when(statusLine.getReasonPhrase()).thenReturn("test");
        assertEquals("test", clientHttpResponse.getStatusText());
    }
    
    @Test
    public void testGetHeaders() {
        assertEquals(3, clientHttpResponse.getHeaders().getHeader().size());
        assertEquals("testValue", clientHttpResponse.getHeaders().getValue("testName"));
    }
    
    @Test
    public void testGetBody() throws IOException {
        assertEquals(inputStream, clientHttpResponse.getBody());
    }
    
    @Test
    public void testCloseResponseWithException() {
        when(response.getEntity()).thenThrow(new RuntimeException("test"));
        clientHttpResponse.close();
    }
}