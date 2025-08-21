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

package com.alibaba.nacos.core.controller.compatibility;

import com.alibaba.nacos.core.auth.InnerApiAuthEnabled;
import com.alibaba.nacos.core.code.ControllerMethodsCache;
import com.alibaba.nacos.plugin.auth.constant.ApiType;
import com.alibaba.nacos.sys.env.EnvUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.env.MockEnvironment;

import java.io.IOException;
import java.lang.reflect.Method;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.matches;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApiCompatibilityFilterTest {
    
    @Mock
    ControllerMethodsCache methodsCache;
    
    @Mock
    HttpServletRequest servletRequest;
    
    @Mock
    HttpServletResponse servletResponse;
    
    @Mock
    InnerApiAuthEnabled innerApiAuthEnabled;
    
    @Mock
    FilterChain filterChain;
    
    ApiCompatibilityFilter filter;
    
    @BeforeEach
    void setUp() {
        EnvUtil.setEnvironment(new MockEnvironment());
        filter = new ApiCompatibilityFilter(methodsCache, innerApiAuthEnabled);
    }
    
    @AfterEach
    void tearDown() {
        EnvUtil.setEnvironment(null);
        ApiCompatibilityConfig config = ApiCompatibilityConfig.getInstance();
        config.setConsoleApiCompatibility(false);
        config.setClientApiCompatibility(true);
        config.setAdminApiCompatibility(true);
    }
    
    @Test
    void testDoFilterWithoutMethod() throws ServletException, IOException {
        filter.doFilter(servletRequest, servletResponse, filterChain);
        verify(filterChain).doFilter(servletRequest, servletResponse);
        verify(servletResponse, never()).sendError(anyInt(), anyString());
    }
    
    @Test
    void testDoFilterWithoutCompatibility() throws ServletException, IOException, NoSuchMethodException {
        Method method = ApiCompatibilityFilterTest.class.getDeclaredMethod("testDoFilterWithoutCompatibility");
        when(methodsCache.getMethod(servletRequest)).thenReturn(method);
        filter.doFilter(servletRequest, servletResponse, filterChain);
        verify(filterChain).doFilter(servletRequest, servletResponse);
        verify(servletResponse, never()).sendError(anyInt(), anyString());
    }
    
    @Test
    @Compatibility(apiType = ApiType.ADMIN_API)
    void testDoFilterWithAdminApiAndCompatibilityEnabled() throws ServletException, IOException, NoSuchMethodException {
        Method method = ApiCompatibilityFilterTest.class.getDeclaredMethod(
                "testDoFilterWithAdminApiAndCompatibilityEnabled");
        when(methodsCache.getMethod(servletRequest)).thenReturn(method);
        ApiCompatibilityConfig.getInstance().setAdminApiCompatibility(true);
        filter.doFilter(servletRequest, servletResponse, filterChain);
        verify(filterChain).doFilter(servletRequest, servletResponse);
        verify(servletResponse, never()).sendError(anyInt(), anyString());
    }
    
    @Test
    @Compatibility(apiType = ApiType.ADMIN_API)
    void testDoFilterWithAdminApiAndCompatibilityDisabled()
            throws ServletException, IOException, NoSuchMethodException {
        Method method = ApiCompatibilityFilterTest.class.getDeclaredMethod(
                "testDoFilterWithAdminApiAndCompatibilityDisabled");
        when(methodsCache.getMethod(servletRequest)).thenReturn(method);
        ApiCompatibilityConfig.getInstance().setAdminApiCompatibility(false);
        filter.doFilter(servletRequest, servletResponse, filterChain);
        verify(filterChain, never()).doFilter(servletRequest, servletResponse);
        verify(servletResponse).sendError(eq(HttpServletResponse.SC_GONE),
                matches(".*" + ApiCompatibilityConfig.ADMIN_API_COMPATIBILITY_KEY + ".*"));
    }
    
    @Test
    @Compatibility(apiType = ApiType.ADMIN_API, alternatives = "/test/admin")
    void testDoFilterWithAdminApiAndCompatibilityDisabledAndAlternatives()
            throws NoSuchMethodException, ServletException, IOException {
        Method method = ApiCompatibilityFilterTest.class.getDeclaredMethod(
                "testDoFilterWithAdminApiAndCompatibilityDisabledAndAlternatives");
        when(methodsCache.getMethod(servletRequest)).thenReturn(method);
        ApiCompatibilityConfig.getInstance().setAdminApiCompatibility(false);
        filter.doFilter(servletRequest, servletResponse, filterChain);
        verify(filterChain, never()).doFilter(servletRequest, servletResponse);
        verify(servletResponse).sendError(eq(HttpServletResponse.SC_GONE), matches(".*/test/admin.*"));
    }
    
    @Test
    @Compatibility(apiType = ApiType.CONSOLE_API)
    void testDoFilterWithConsoleApiAndCompatibilityEnabled()
            throws ServletException, IOException, NoSuchMethodException {
        ApiCompatibilityConfig.getInstance().setConsoleApiCompatibility(true);
        Method method = ApiCompatibilityFilterTest.class.getDeclaredMethod(
                "testDoFilterWithConsoleApiAndCompatibilityEnabled");
        when(methodsCache.getMethod(servletRequest)).thenReturn(method);
        filter.doFilter(servletRequest, servletResponse, filterChain);
        verify(filterChain).doFilter(servletRequest, servletResponse);
        verify(servletResponse, never()).sendError(anyInt(), anyString());
    }
    
    @Test
    @Compatibility(apiType = ApiType.CONSOLE_API)
    void testDoFilterWithConsoleApiAndCompatibilityDisabled()
            throws ServletException, IOException, NoSuchMethodException {
        Method method = ApiCompatibilityFilterTest.class.getDeclaredMethod(
                "testDoFilterWithConsoleApiAndCompatibilityDisabled");
        ApiCompatibilityConfig.getInstance().setConsoleApiCompatibility(false);
        when(methodsCache.getMethod(servletRequest)).thenReturn(method);
        filter.doFilter(servletRequest, servletResponse, filterChain);
        verify(filterChain, never()).doFilter(servletRequest, servletResponse);
        verify(servletResponse).sendError(eq(HttpServletResponse.SC_GONE),
                matches(".*" + ApiCompatibilityConfig.CONSOLE_API_COMPATIBILITY_KEY + ".*"));
    }
    
    @Test
    @Compatibility(apiType = ApiType.CONSOLE_API, alternatives = "/test/console")
    void testDoFilterWithConsoleApiAndCompatibilityDisabledAndAlternatives()
            throws NoSuchMethodException, ServletException, IOException {
        Method method = ApiCompatibilityFilterTest.class.getDeclaredMethod(
                "testDoFilterWithConsoleApiAndCompatibilityDisabledAndAlternatives");
        ApiCompatibilityConfig.getInstance().setConsoleApiCompatibility(false);
        when(methodsCache.getMethod(servletRequest)).thenReturn(method);
        filter.doFilter(servletRequest, servletResponse, filterChain);
        verify(filterChain, never()).doFilter(servletRequest, servletResponse);
        verify(servletResponse).sendError(eq(HttpServletResponse.SC_GONE), matches(".*/test/console.*"));
    }
    
    @Test
    @Compatibility(apiType = ApiType.OPEN_API)
    void testDoFilterWithOpenApiAndCompatibilityEnabled() throws ServletException, IOException, NoSuchMethodException {
        Method method = ApiCompatibilityFilterTest.class.getDeclaredMethod(
                "testDoFilterWithOpenApiAndCompatibilityEnabled");
        ApiCompatibilityConfig.getInstance().setClientApiCompatibility(true);
        when(methodsCache.getMethod(servletRequest)).thenReturn(method);
        filter.doFilter(servletRequest, servletResponse, filterChain);
        verify(filterChain).doFilter(servletRequest, servletResponse);
        verify(servletResponse, never()).sendError(anyInt(), anyString());
    }
    
    @Test
    @Compatibility(apiType = ApiType.OPEN_API)
    void testDoFilterWithOpenApiAndCompatibilityDisabled() throws ServletException, IOException, NoSuchMethodException {
        Method method = ApiCompatibilityFilterTest.class.getDeclaredMethod(
                "testDoFilterWithOpenApiAndCompatibilityDisabled");
        ApiCompatibilityConfig.getInstance().setClientApiCompatibility(false);
        when(methodsCache.getMethod(servletRequest)).thenReturn(method);
        filter.doFilter(servletRequest, servletResponse, filterChain);
        verify(filterChain, never()).doFilter(servletRequest, servletResponse);
        verify(servletResponse).sendError(eq(HttpServletResponse.SC_GONE),
                matches(".*" + ApiCompatibilityConfig.CLIENT_API_COMPATIBILITY_KEY + ".*"));
    }
    
    @Test
    @Compatibility(apiType = ApiType.OPEN_API, alternatives = "/test/client")
    void testDoFilterWithOpenApiAndCompatibilityDisabledAndAlternatives()
            throws NoSuchMethodException, ServletException, IOException {
        Method method = ApiCompatibilityFilterTest.class.getDeclaredMethod(
                "testDoFilterWithOpenApiAndCompatibilityDisabledAndAlternatives");
        ApiCompatibilityConfig.getInstance().setClientApiCompatibility(false);
        when(methodsCache.getMethod(servletRequest)).thenReturn(method);
        filter.doFilter(servletRequest, servletResponse, filterChain);
        verify(filterChain, never()).doFilter(servletRequest, servletResponse);
        verify(servletResponse).sendError(eq(HttpServletResponse.SC_GONE), matches(".*/test/client.*"));
    }
    
    @Test
    @Compatibility(apiType = ApiType.INNER_API)
    void testDoFilterWithInnerApi() throws NoSuchMethodException, ServletException, IOException {
        Method method = ApiCompatibilityFilterTest.class.getDeclaredMethod("testDoFilterWithInnerApi");
        when(methodsCache.getMethod(servletRequest)).thenReturn(method);
        filter.doFilter(servletRequest, servletResponse, filterChain);
        verify(filterChain).doFilter(servletRequest, servletResponse);
        verify(servletResponse, never()).sendError(anyInt(), anyString());
    }
    
    @Test
    void testDoFilterWithException() throws ServletException, IOException {
        doThrow(new ServletException()).when(filterChain).doFilter(servletRequest, servletResponse);
        filter.doFilter(servletRequest, servletResponse, filterChain);
        verify(servletResponse).sendError(eq(HttpServletResponse.SC_INTERNAL_SERVER_ERROR),
                eq("Handle API Compatibility failed, please see log for detail."));
    }
}