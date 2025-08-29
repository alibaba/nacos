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

package com.alibaba.nacos.core.console;

import com.alibaba.nacos.sys.env.EnvUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.env.MockEnvironment;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * nacos console path filter test.
 * @author cxhello
 * @date 2025/7/24
 */
@ExtendWith(MockitoExtension.class)
class NacosConsolePathTipFilterTest {
    
    @InjectMocks
    private NacosConsolePathTipFilter filter;
    
    @Mock
    private FilterChain filterChain;
    
    @Mock
    private HttpServletRequest request;
    
    @Mock
    private HttpServletResponse response;
    
    private StringWriter responseWriter;
    
    private MockEnvironment environment;
    
    private static final String NACOS_CONSOLE_DEFAULT_PATH = "/";
    
    private static final String NACOS_SERVER_CONTEXT_PATH = "/nacos-server";
    
    private static final String NACOS_SERVER_CONTEXT_SLASH_PATH = "/nacos-server/";
    
    @BeforeEach
    void setup() throws IOException {
        environment = new MockEnvironment();
        EnvUtil.setEnvironment(environment);
        environment.setProperty("nacos.server.main.port", "18848");
        environment.setProperty("nacos.console.port", "18080");
        environment.setProperty("nacos.console.contextPath", "/console");
        responseWriter = new StringWriter();
        PrintWriter printWriter = new PrintWriter(responseWriter);
        when(response.getWriter()).thenReturn(printWriter);
    }
    
    @Test
    void testRootPathShouldReturnTip1() throws IOException, ServletException {
        environment.setProperty("nacos.server.contextPath", NACOS_CONSOLE_DEFAULT_PATH);
        when(request.getRequestURI()).thenReturn("/");
        responseWriter.getBuffer().setLength(0);
        filter.doFilter(request, response, filterChain);
        response.getWriter().flush();
        String responseText = responseWriter.toString();
        assertEquals(responseText, String.format("Nacos Console default port is %s, and the path is %s.",
                EnvUtil.getProperty("nacos.console.port"), EnvUtil.getProperty("nacos.console.contextPath")));
    }
    
    @Test
    void testRootPathShouldReturnTip2() throws IOException, ServletException {
        environment.setProperty("nacos.server.contextPath", NACOS_CONSOLE_DEFAULT_PATH);
        when(request.getRequestURI()).thenReturn("/index.html");
        responseWriter.getBuffer().setLength(0);
        filter.doFilter(request, response, filterChain);
        response.getWriter().flush();
        String responseText = responseWriter.toString();
        assertEquals(responseText, String.format("Nacos Console default port is %s, and the path is %s.",
                EnvUtil.getProperty("nacos.console.port"), EnvUtil.getProperty("nacos.console.contextPath")));
    }
    
    @Test
    void testRootPathShouldReturnTip3() throws IOException, ServletException {
        environment.setProperty("nacos.server.contextPath", NACOS_CONSOLE_DEFAULT_PATH);
        when(request.getRequestURI()).thenReturn("/test");
        filter.doFilter(request, response, filterChain);
        response.getWriter().flush();
        verify(filterChain).doFilter(request, response);
    }
    
    @Test
    void testRootPathShouldReturnTip4() throws IOException, ServletException {
        environment.setProperty("nacos.server.contextPath", NACOS_CONSOLE_DEFAULT_PATH);
        when(request.getRequestURI()).thenReturn("/v3/admin/core/cluster/node/self");
        filter.doFilter(request, response, filterChain);
        response.getWriter().flush();
        verify(filterChain).doFilter(request, response);
    }
    
    @Test
    void testContextPathShouldReturnTip1() throws IOException, ServletException {
        environment.setProperty("nacos.server.contextPath", NACOS_SERVER_CONTEXT_PATH);
        when(request.getRequestURI()).thenReturn("/nacos-server/");
        responseWriter.getBuffer().setLength(0);
        filter.doFilter(request, response, filterChain);
        response.getWriter().flush();
        String responseText = responseWriter.toString();
        assertEquals(responseText, String.format("Nacos Console default port is %s, and the path is %s.",
                EnvUtil.getProperty("nacos.console.port"), EnvUtil.getProperty("nacos.console.contextPath")));
    }
    
    @Test
    void testContextPathShouldReturnTip2() throws IOException, ServletException {
        environment.setProperty("nacos.server.contextPath", NACOS_SERVER_CONTEXT_PATH);
        when(request.getRequestURI()).thenReturn(NACOS_SERVER_CONTEXT_PATH + "/index.html");
        responseWriter.getBuffer().setLength(0);
        filter.doFilter(request, response, filterChain);
        response.getWriter().flush();
        String responseText = responseWriter.toString();
        assertEquals(responseText, String.format("Nacos Console default port is %s, and the path is %s.",
                EnvUtil.getProperty("nacos.console.port"), EnvUtil.getProperty("nacos.console.contextPath")));
    }
    
    @Test
    void testContextPathShouldReturnTip3() throws IOException, ServletException {
        environment.setProperty("nacos.server.contextPath", NACOS_SERVER_CONTEXT_PATH);
        when(request.getRequestURI()).thenReturn("/test");
        filter.doFilter(request, response, filterChain);
        response.getWriter().flush();
        verify(filterChain).doFilter(request, response);
    }
    
    @Test
    void testContextPathShouldReturnTip4() throws IOException, ServletException {
        environment.setProperty("nacos.server.contextPath", NACOS_SERVER_CONTEXT_PATH);
        when(request.getRequestURI()).thenReturn("/v3/admin/core/cluster/node/self");
        filter.doFilter(request, response, filterChain);
        response.getWriter().flush();
        verify(filterChain).doFilter(request, response);
    }
    
    @Test
    void testContextSlashPathShouldReturnTip1() throws IOException, ServletException {
        environment.setProperty("nacos.server.contextPath", NACOS_SERVER_CONTEXT_SLASH_PATH);
        when(request.getRequestURI()).thenReturn("/nacos-server/");
        responseWriter.getBuffer().setLength(0);
        filter.doFilter(request, response, filterChain);
        response.getWriter().flush();
        String responseText = responseWriter.toString();
        assertEquals(responseText, String.format("Nacos Console default port is %s, and the path is %s.",
                EnvUtil.getProperty("nacos.console.port"), EnvUtil.getProperty("nacos.console.contextPath")));
    }
    
    @Test
    void testContextSlashPathShouldReturnTip2() throws IOException, ServletException {
        environment.setProperty("nacos.server.contextPath", NACOS_SERVER_CONTEXT_SLASH_PATH);
        when(request.getRequestURI()).thenReturn(NACOS_SERVER_CONTEXT_PATH + "/index.html");
        responseWriter.getBuffer().setLength(0);
        filter.doFilter(request, response, filterChain);
        response.getWriter().flush();
        String responseText = responseWriter.toString();
        assertEquals(responseText, String.format("Nacos Console default port is %s, and the path is %s.",
                EnvUtil.getProperty("nacos.console.port"), EnvUtil.getProperty("nacos.console.contextPath")));
    }
    
    @Test
    void testContextSlashPathShouldReturnTip3() throws IOException, ServletException {
        environment.setProperty("nacos.server.contextPath", NACOS_SERVER_CONTEXT_SLASH_PATH);
        when(request.getRequestURI()).thenReturn("/test");
        filter.doFilter(request, response, filterChain);
        response.getWriter().flush();
        verify(filterChain).doFilter(request, response);
    }
    
    @Test
    void testContextSlashPathShouldReturnTip4() throws IOException, ServletException {
        environment.setProperty("nacos.server.contextPath", NACOS_SERVER_CONTEXT_SLASH_PATH);
        when(request.getRequestURI()).thenReturn("/v3/admin/core/cluster/node/self");
        filter.doFilter(request, response, filterChain);
        response.getWriter().flush();
        verify(filterChain).doFilter(request, response);
    }
    
}
