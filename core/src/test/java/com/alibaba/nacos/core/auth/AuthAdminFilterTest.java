/*
 *  Copyright 1999-2025 Alibaba Group Holding Ltd.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package com.alibaba.nacos.core.auth;

import com.alibaba.nacos.api.common.ApiType;
import com.alibaba.nacos.auth.annotation.Secured;
import com.alibaba.nacos.auth.config.NacosAuthConfig;
import com.alibaba.nacos.core.code.ControllerMethodsCache;
import com.alibaba.nacos.core.context.RequestContextHolder;
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

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link AuthAdminFilter} unit test.
 *
 * @author xiweng.yy
 */
@ExtendWith(MockitoExtension.class)
class AuthAdminFilterTest {
    
    private AuthAdminFilter authAdminFilter;
    
    @Mock
    private NacosAuthConfig authConfig;
    
    @Mock
    private ControllerMethodsCache methodsCache;
    
    @Mock
    private FilterChain filterChain;
    
    @Mock
    private HttpServletRequest request;
    
    @Mock
    private HttpServletResponse response;
    
    @BeforeEach
    void setUp() {
        authAdminFilter = new AuthAdminFilter(authConfig, methodsCache);
    }
    
    @AfterEach
    void tearDown() {
        RequestContextHolder.removeContext();
    }
    
    @Test
    void testIsMatchFilterReturnsTrueForAdminApi()
        throws NoSuchMethodException, ServletException, IOException {
        when(authConfig.isAuthEnabled()).thenReturn(true);
        when(authConfig.getServerIdentityKey()).thenReturn("1");
        when(authConfig.getServerIdentityValue()).thenReturn("2");
        when(request.getHeader("1")).thenReturn("2");
        when(methodsCache.getMethod(request)).thenReturn(getMethodWithSecuredAdminApi());
        
        authAdminFilter.doFilter(request, response, filterChain);
        
        verify(filterChain).doFilter(request, response);
    }
    
    @Test
    void testIsMatchFilterSkipsNonAdminApi()
        throws NoSuchMethodException, ServletException, IOException {
        when(methodsCache.getMethod(request)).thenReturn(getMethodWithSecuredOpenApi());
        
        authAdminFilter.doFilter(request, response, filterChain);
        
        verify(filterChain).doFilter(request, response);
    }
    
    @Test
    void testDoFilterWhenAuthDisabledPassesThrough()
        throws NoSuchMethodException, ServletException, IOException {
        when(authConfig.isAuthEnabled()).thenReturn(false);
        when(methodsCache.getMethod(request)).thenReturn(getMethodWithSecuredAdminApi());
        
        authAdminFilter.doFilter(request, response, filterChain);
        
        verify(filterChain).doFilter(request, response);
        assertEquals(ApiType.ADMIN_API.name(),
            RequestContextHolder.getContext().getAuthContext().getApiType());
    }
    
    @Test
    void testDoFilterShouldSetApiTypeWhenSkippingNonAdminApi()
        throws NoSuchMethodException, ServletException, IOException {
        when(methodsCache.getMethod(request)).thenReturn(getMethodWithSecuredOpenApi());
        
        authAdminFilter.doFilter(request, response, filterChain);
        
        verify(filterChain).doFilter(request, response);
        assertEquals(ApiType.OPEN_API.name(),
            RequestContextHolder.getContext().getAuthContext().getApiType());
    }
    
    @Secured(apiType = ApiType.ADMIN_API)
    private static void methodWithAdminApi() {
    }
    
    @Secured(apiType = ApiType.OPEN_API)
    private static void methodWithOpenApi() {
    }
    
    private java.lang.reflect.Method getMethodWithSecuredAdminApi() throws NoSuchMethodException {
        return AuthAdminFilterTest.class.getDeclaredMethod("methodWithAdminApi");
    }
    
    private java.lang.reflect.Method getMethodWithSecuredOpenApi() throws NoSuchMethodException {
        return AuthAdminFilterTest.class.getDeclaredMethod("methodWithOpenApi");
    }
}
