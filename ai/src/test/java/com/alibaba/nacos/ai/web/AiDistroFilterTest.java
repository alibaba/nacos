/*
 * Copyright 1999-2026 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.ai.web;

import com.alibaba.nacos.common.constant.HttpHeaderConsts;
import com.alibaba.nacos.common.model.RestResult;
import com.alibaba.nacos.core.utils.ReuseHttpServletRequest;
import com.alibaba.nacos.naming.core.DistroMapper;
import com.alibaba.nacos.naming.constants.ClientConstants;
import com.alibaba.nacos.naming.misc.HttpClient;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiDistroFilterTest {
    
    private static final String INTERNAL_CLIENT_ID = "HTTP_CLIENT@@client";
    
    private DistroMapper distroMapper;
    
    private AiDistroFilter filter;
    
    @BeforeAll
    static void setUpEnvironment() {
        EnvUtil.setEnvironment(new MockEnvironment());
    }
    
    @BeforeEach
    void setUp() {
        distroMapper = mock(DistroMapper.class);
        filter = new AiDistroFilter(distroMapper);
    }
    
    @Test
    void testRequestWithoutClientIdStaysLocal() throws Exception {
        MockHttpServletRequest request = request("GET");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        
        filter.doFilter(request, response, chain);
        
        verify(chain).doFilter(any(ReuseHttpServletRequest.class), same(response));
        verify(distroMapper, never()).responsible(anyString());
    }
    
    @Test
    void testResponsibleRequestStaysLocal() throws Exception {
        MockHttpServletRequest request = statefulRequest("GET");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        when(distroMapper.responsible(INTERNAL_CLIENT_ID)).thenReturn(true);
        
        filter.doFilter(request, response, chain);
        
        verify(chain).doFilter(any(ReuseHttpServletRequest.class), same(response));
    }
    
    @Test
    void testPeerRedirectIsRejected() throws Exception {
        MockHttpServletRequest request = statefulRequest("GET");
        request.addHeader(HttpHeaderConsts.USER_AGENT_HEADER, "Nacos-Server:v3");
        MockHttpServletResponse response = new MockHttpServletResponse();
        
        filter.doFilter(request, response, mock(FilterChain.class));
        
        assertEquals(HttpServletResponse.SC_BAD_REQUEST, response.getStatus());
        assertTrue(response.getErrorMessage().contains("receive invalid redirect request"));
    }
    
    @Test
    void testRequestIsProxiedWithOriginalQueryAndBody() throws Exception {
        MockHttpServletRequest request = statefulRequest("POST");
        request.setQueryString("protocol=a2a&protocol=jsonrpc");
        request.setContent("body".getBytes(StandardCharsets.UTF_8));
        request.addHeader("X-Test", "value");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(distroMapper.mapSrv(INTERNAL_CLIENT_ID)).thenReturn("2.2.2.2:8848");
        AtomicReference<String> targetUrl = new AtomicReference<>();
        AtomicReference<List<String>> headers = new AtomicReference<>();
        AtomicReference<Map<String, String>> parameters = new AtomicReference<>();
        
        try (MockedStatic<HttpClient> httpClient = mockStatic(HttpClient.class)) {
            httpClient.when(() -> HttpClient.request(anyString(), anyList(), anyMap(), anyString(),
                anyInt(), anyInt(), anyString(), eq("POST"))).thenAnswer(invocation -> {
                    targetUrl.set(invocation.getArgument(0));
                    headers.set(invocation.getArgument(1));
                    parameters.set(invocation.getArgument(2));
                    assertEquals("body", invocation.getArgument(3));
                    return new RestResult<>(200, "ok", "proxied");
                });
            
            filter.doFilter(request, response, mock(FilterChain.class));
        }
        
        assertEquals(HttpServletResponse.SC_OK, response.getStatus());
        assertEquals("proxied", response.getContentAsString());
        assertEquals(
            "http://2.2.2.2:8848/v3/client/ai/agents/endpoints"
                + "?protocol=a2a&protocol=jsonrpc",
            targetUrl.get());
        assertTrue(headers.get().contains(ClientConstants.HTTP_CLIENT_ID_HEADER));
        assertTrue(headers.get().contains("client"));
        assertTrue(headers.get().contains("X-Test"));
        assertTrue(headers.get().contains("value"));
        assertTrue(parameters.get().isEmpty());
    }
    
    @Test
    void testFailedProxyResponseUsesMessage() throws Exception {
        MockHttpServletRequest request = statefulRequest("GET");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(distroMapper.mapSrv(INTERNAL_CLIENT_ID)).thenReturn("2.2.2.2:8848");
        
        try (MockedStatic<HttpClient> httpClient = mockStatic(HttpClient.class)) {
            httpClient.when(() -> HttpClient.request(anyString(), anyList(), anyMap(), anyString(),
                anyInt(), anyInt(), anyString(), eq("GET")))
                .thenReturn(new RestResult<>(503, "unavailable", null));
            
            filter.doFilter(request, response, mock(FilterChain.class));
        }
        
        assertEquals(HttpServletResponse.SC_SERVICE_UNAVAILABLE, response.getStatus());
        assertEquals("unavailable", response.getContentAsString());
    }
    
    @Test
    void testSecurityExceptionReturnsForbidden() throws Exception {
        MockHttpServletRequest request = statefulRequest("GET");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(distroMapper.mapSrv(INTERNAL_CLIENT_ID)).thenReturn("2.2.2.2:8848");
        
        try (MockedStatic<HttpClient> httpClient = mockStatic(HttpClient.class)) {
            httpClient.when(() -> HttpClient.request(anyString(), anyList(), anyMap(), anyString(),
                anyInt(), anyInt(), anyString(), eq("GET")))
                .thenThrow(new SecurityException("denied"));
            
            filter.doFilter(request, response, mock(FilterChain.class));
        }
        
        assertEquals(HttpServletResponse.SC_FORBIDDEN, response.getStatus());
        assertTrue(response.getErrorMessage().contains("access denied: caused: denied;"));
    }
    
    @Test
    void testUnexpectedExceptionReturnsServerError() throws Exception {
        MockHttpServletRequest request = statefulRequest("GET");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(distroMapper.mapSrv(INTERNAL_CLIENT_ID)).thenReturn("2.2.2.2:8848");
        
        try (MockedStatic<HttpClient> httpClient = mockStatic(HttpClient.class)) {
            httpClient.when(() -> HttpClient.request(anyString(), anyList(), anyMap(), anyString(),
                anyInt(), anyInt(), anyString(), eq("GET")))
                .thenThrow(new IllegalStateException("failed"));
            
            filter.doFilter(request, response, mock(FilterChain.class));
        }
        
        assertEquals(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, response.getStatus());
        assertTrue(response.getErrorMessage().contains("Server failed, caused: failed;"));
    }
    
    private MockHttpServletRequest request(String method) {
        return new MockHttpServletRequest(method, "/v3/client/ai/agents/endpoints");
    }
    
    private MockHttpServletRequest statefulRequest(String method) {
        MockHttpServletRequest result = request(method);
        result.addHeader(ClientConstants.HTTP_CLIENT_ID_HEADER, "client");
        return result;
    }
}
