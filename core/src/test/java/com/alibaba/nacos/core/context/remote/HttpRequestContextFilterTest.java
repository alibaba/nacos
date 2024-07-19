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
import org.apache.http.HttpHeaders;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opentest4j.AssertionFailedError;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.Servlet;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
        when(servletRequest.getHeader(HttpHeaders.HOST)).thenReturn("localhost");
        when(servletRequest.getHeader(HttpHeaders.USER_AGENT)).thenReturn("Nacos-Java-Client:v1.4.7");
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
        public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain)
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