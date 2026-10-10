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

package com.alibaba.nacos.core.context.remote;

import com.alibaba.nacos.common.constant.HttpHeaderConsts;
import com.alibaba.nacos.core.context.RequestContext;
import com.alibaba.nacos.core.context.RequestContextHolder;
import com.alibaba.nacos.core.context.addition.BasicContext;
import org.apache.hc.core5.http.HttpHeaders;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opentest4j.AssertionFailedError;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.Servlet;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import java.io.IOException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HttpRequestContextFilterTest {
    
    @Mock
    private MockHttpServletRequest servletRequest;
    
    @Mock
    private MockHttpServletResponse servletResponse;
    
    @Mock
    private Servlet servlet;
    
    HttpRequestContextFilter filter;
    
    @BeforeEach
    void setUp() {
        filter = new HttpRequestContextFilter();
        RequestContextHolder.getContext();
        when(servletRequest.getHeader(HttpHeaderConsts.NACOS_REQUEST_ID)).thenReturn(null);
        when(servletRequest.getHeader(HttpHeaders.HOST)).thenReturn("localhost");
        when(servletRequest.getHeader(HttpHeaders.USER_AGENT))
            .thenReturn("Nacos-Java-Client:v1.4.7");
        when(servletRequest.getHeader(HttpHeaderConsts.APP_FILED)).thenReturn("testApp");
        when(servletRequest.getMethod()).thenReturn("GET");
        when(servletRequest.getRequestURI()).thenReturn("/test/path");
        when(servletRequest.getCharacterEncoding()).thenReturn("GBK");
        when(servletRequest.getRemoteAddr()).thenReturn("1.1.1.1");
        when(servletRequest.getRemotePort()).thenReturn(3306);
        when(servletRequest.getHeader("X-Forwarded-For")).thenReturn("2.2.2.2");
    }
    
    @AfterEach
    void tearDown() {
        RequestContextHolder.removeContext();
    }
    
    @Test
    public void testDoFilterSetsCorrectContextValues() throws Exception {
        MockNextFilter nextFilter = new MockNextFilter("testApp", "GBK");
        filter.doFilter(servletRequest, servletResponse, new MockFilterChain(servlet, nextFilter));
        if (null != nextFilter.error) {
            throw nextFilter.error;
        }
    }
    
    @Test
    public void testDoFilterWithoutEncoding() throws Exception {
        when(servletRequest.getCharacterEncoding()).thenReturn("");
        MockNextFilter nextFilter = new MockNextFilter("testApp", "UTF-8");
        filter.doFilter(servletRequest, servletResponse, new MockFilterChain(servlet, nextFilter));
        if (null != nextFilter.error) {
            throw nextFilter.error;
        }
    }
    
    @Test
    public void testGetAppNameWithFallback() throws Exception {
        when(servletRequest.getHeader(HttpHeaderConsts.APP_FILED)).thenReturn("");
        MockNextFilter nextFilter = new MockNextFilter("unknown", "GBK");
        filter.doFilter(servletRequest, servletResponse, new MockFilterChain(servlet, nextFilter));
        if (null != nextFilter.error) {
            throw nextFilter.error;
        }
    }
    
    @Test
    void testDoFilterUsesClientRequestId() throws Exception {
        String requestId = UUID.randomUUID().toString();
        when(servletRequest.getHeader(HttpHeaderConsts.NACOS_REQUEST_ID)).thenReturn(requestId);
        RequestContext context = RequestContextHolder.getContext();
        
        filter.doFilter(servletRequest, servletResponse, (request, response) -> {
            assertSame(context, RequestContextHolder.getContext());
            assertEquals(requestId, RequestContextHolder.getContext().getRequestId());
            assertEquals(BasicContext.HTTP_PROTOCOL,
                context.getBasicContext().getRequestProtocol());
        });
        
        assertNotSame(context, RequestContextHolder.getContext());
    }
    
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void testDoFilterKeepsGeneratedIdWithoutClientRequestId(String requestId) throws Exception {
        when(servletRequest.getHeader(HttpHeaderConsts.NACOS_REQUEST_ID)).thenReturn(requestId);
        RequestContext context = RequestContextHolder.getContext();
        String generatedId = context.getRequestId();
        
        filter.doFilter(servletRequest, servletResponse, (request, response) -> {
            assertEquals(generatedId, RequestContextHolder.getContext().getRequestId());
            assertEquals(4, UUID.fromString(generatedId).version());
        });
        
        assertNotSame(context, RequestContextHolder.getContext());
    }
    
    @Test
    void testDoFilterDoesNotLeakClientRequestIdToNextRequest() throws Exception {
        String requestId = UUID.randomUUID().toString();
        when(servletRequest.getHeader(HttpHeaderConsts.NACOS_REQUEST_ID))
            .thenReturn(requestId, null);
        RequestContext firstContext = RequestContextHolder.getContext();
        filter.doFilter(servletRequest, servletResponse, (request,
            response) -> assertEquals(requestId, RequestContextHolder.getContext().getRequestId()));
        
        filter.doFilter(servletRequest, servletResponse, (request, response) -> {
            RequestContext nextContext = RequestContextHolder.getContext();
            assertNotSame(firstContext, nextContext);
            assertNotEquals(requestId, nextContext.getRequestId());
            assertEquals(4, UUID.fromString(nextContext.getRequestId()).version());
        });
    }
    
    @Test
    void testDoFilterClearsContextAfterException() {
        String requestId = UUID.randomUUID().toString();
        when(servletRequest.getHeader(HttpHeaderConsts.NACOS_REQUEST_ID)).thenReturn(requestId);
        RequestContext context = RequestContextHolder.getContext();
        ServletException failure = new ServletException("downstream failure");
        
        ServletException actual = assertThrows(ServletException.class,
            () -> filter.doFilter(servletRequest, servletResponse, (request, response) -> {
                assertEquals(requestId, RequestContextHolder.getContext().getRequestId());
                throw failure;
            }));
        
        assertSame(failure, actual);
        assertNotSame(context, RequestContextHolder.getContext());
        assertNotEquals(requestId, RequestContextHolder.getContext().getRequestId());
    }
    
    private static class MockNextFilter implements Filter {
        
        private final String app;
        
        private final String encoding;
        
        AssertionError error;
        
        public MockNextFilter(String app, String encoding) {
            this.app = app;
            this.encoding = encoding;
        }
        
        @Override
        public void init(FilterConfig filterConfig) throws ServletException {
            Filter.super.init(filterConfig);
        }
        
        @Override
        public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse,
            FilterChain filterChain)
            throws IOException, ServletException {
            try {
                RequestContext requestContext = RequestContextHolder.getContext();
                BasicContext basicContext = requestContext.getBasicContext();
                assertEquals("GET /test/path", basicContext.getRequestTarget());
                assertEquals(encoding, basicContext.getEncoding());
                assertEquals("Nacos-Java-Client:v1.4.7", basicContext.getUserAgent());
                assertEquals(app, basicContext.getApp());
                assertEquals("1.1.1.1", basicContext.getAddressContext().getRemoteIp());
                assertEquals("2.2.2.2", basicContext.getAddressContext().getSourceIp());
                assertEquals(3306, basicContext.getAddressContext().getRemotePort());
                assertEquals("localhost", basicContext.getAddressContext().getHost());
            } catch (AssertionFailedError error) {
                this.error = error;
            }
        }
        
        @Override
        public void destroy() {
            Filter.super.destroy();
        }
    }
}
