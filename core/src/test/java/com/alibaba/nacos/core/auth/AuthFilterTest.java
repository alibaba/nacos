/*
 *  Copyright 1999-2021 Alibaba Group Holding Ltd.
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

import com.alibaba.nacos.auth.HttpProtocolAuthService;
import com.alibaba.nacos.auth.annotation.Secured;
import com.alibaba.nacos.auth.config.AuthConfigs;
import com.alibaba.nacos.core.code.ControllerMethodsCache;
import com.alibaba.nacos.core.context.RequestContextHolder;
import com.alibaba.nacos.plugin.auth.api.IdentityContext;
import com.alibaba.nacos.plugin.auth.api.Permission;
import com.alibaba.nacos.plugin.auth.api.Resource;
import com.alibaba.nacos.plugin.auth.exception.AccessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link AuthFilter} unit test.
 *
 * @author chenglu
 * @date 2021-07-06 13:44
 */
@ExtendWith(MockitoExtension.class)
class AuthFilterTest {
    
    private AuthFilter authFilter;
    
    @Mock
    private AuthConfigs authConfigs;
    
    @Mock
    private ControllerMethodsCache methodsCache;
    
    @Mock
    FilterChain filterChain;
    
    @Mock
    HttpServletRequest request;
    
    @Mock
    HttpServletResponse response;
    
    @BeforeEach
    void setUp() {
        authFilter = new AuthFilter(authConfigs, methodsCache);
    }
    
    @AfterEach
    void tearDown() {
        RequestContextHolder.removeContext();
    }
    
    @Test
    void testDoFilterDisabledAuth() throws ServletException, IOException {
        when(authConfigs.isAuthEnabled()).thenReturn(false);
        authFilter.doFilter(request, response, filterChain);
        verify(filterChain).doFilter(request, response);
        verify(response, never()).sendError(anyInt(), anyString());
    }
    
    @Test
    @Secured
    void testDoFilterWithoutServerIdentity() throws ServletException, IOException, NoSuchMethodException {
        when(methodsCache.getMethod(request)).thenReturn(
                this.getClass().getDeclaredMethod("testDoFilterWithoutServerIdentity"));
        when(authConfigs.isAuthEnabled()).thenReturn(true);
        authFilter.doFilter(request, response, filterChain);
        verify(filterChain, never()).doFilter(request, response);
        verify(response).sendError(403,
                "Invalid server identity key or value, Please make sure set `nacos.core.auth.server.identity.key`"
                        + " and `nacos.core.auth.server.identity.value`, or open `nacos.core.auth.enable.userAgentAuthWhite`");
    }
    
    @Test
    @Secured
    void testDoFilterWithServerIdentity() throws ServletException, IOException, NoSuchMethodException {
        when(methodsCache.getMethod(request)).thenReturn(
                this.getClass().getDeclaredMethod("testDoFilterWithServerIdentity"));
        when(authConfigs.isAuthEnabled()).thenReturn(true);
        when(authConfigs.getServerIdentityKey()).thenReturn("1");
        when(authConfigs.getServerIdentityValue()).thenReturn("2");
        when(request.getHeader("1")).thenReturn("2");
        authFilter.doFilter(request, response, filterChain);
        verify(filterChain).doFilter(request, response);
        verify(response, never()).sendError(anyInt(), anyString());
    }
    
    @Test
    @Secured
    void testDoFilterWithoutMethod() throws ServletException, IOException {
        when(authConfigs.isAuthEnabled()).thenReturn(true);
        authFilter.doFilter(request, response, filterChain);
        verify(filterChain).doFilter(request, response);
        verify(response, never()).sendError(anyInt(), anyString());
    }
    
    @Test
    void testDoFilterWithoutSecured() throws ServletException, IOException, NoSuchMethodException {
        when(authConfigs.isAuthEnabled()).thenReturn(true);
        when(methodsCache.getMethod(request)).thenReturn(
                this.getClass().getDeclaredMethod("testDoFilterWithoutSecured"));
        authFilter.doFilter(request, response, filterChain);
        verify(filterChain).doFilter(request, response);
        verify(response, never()).sendError(anyInt(), anyString());
    }
    
    @Test
    @Secured
    void testDoFilterWithNoNeedAuthSecured() throws NoSuchMethodException, ServletException, IOException {
        when(authConfigs.isAuthEnabled()).thenReturn(true);
        when(authConfigs.getServerIdentityKey()).thenReturn("1");
        when(authConfigs.getServerIdentityValue()).thenReturn("2");
        when(methodsCache.getMethod(request)).thenReturn(
                this.getClass().getDeclaredMethod("testDoFilterWithNoNeedAuthSecured"));
        HttpProtocolAuthService protocolAuthService = injectMockPlugins();
        when(protocolAuthService.enableAuth(any(Secured.class))).thenReturn(false);
        authFilter.doFilter(request, response, filterChain);
        verify(filterChain).doFilter(request, response);
        verify(response, never()).sendError(anyInt(), anyString());
    }
    
    @Test
    @Secured
    void testDoFilterWithNeedAuthSecuredSuccess()
            throws NoSuchMethodException, ServletException, IOException, AccessException {
        when(authConfigs.isAuthEnabled()).thenReturn(true);
        when(authConfigs.getServerIdentityKey()).thenReturn("1");
        when(authConfigs.getServerIdentityValue()).thenReturn("2");
        when(methodsCache.getMethod(request)).thenReturn(
                this.getClass().getDeclaredMethod("testDoFilterWithNeedAuthSecuredSuccess"));
        HttpProtocolAuthService protocolAuthService = injectMockPlugins();
        when(protocolAuthService.enableAuth(any(Secured.class))).thenReturn(true);
        doReturn(new IdentityContext()).when(protocolAuthService).parseIdentity(eq(request));
        doReturn(Resource.EMPTY_RESOURCE).when(protocolAuthService).parseResource(eq(request), any(Secured.class));
        when(protocolAuthService.validateIdentity(any(IdentityContext.class), any(Resource.class))).thenReturn(true);
        when(protocolAuthService.validateAuthority(any(IdentityContext.class), any(Permission.class))).thenReturn(true);
        authFilter.doFilter(request, response, filterChain);
        verify(filterChain).doFilter(request, response);
        verify(response, never()).sendError(anyInt(), anyString());
    }
    
    @Test
    @Secured
    void testDoFilterWithNeedAuthSecuredIdentityFailure()
            throws NoSuchMethodException, ServletException, IOException, AccessException {
        when(authConfigs.isAuthEnabled()).thenReturn(true);
        when(authConfigs.getServerIdentityKey()).thenReturn("1");
        when(authConfigs.getServerIdentityValue()).thenReturn("2");
        when(methodsCache.getMethod(request)).thenReturn(
                this.getClass().getDeclaredMethod("testDoFilterWithNeedAuthSecuredIdentityFailure"));
        HttpProtocolAuthService protocolAuthService = injectMockPlugins();
        when(protocolAuthService.enableAuth(any(Secured.class))).thenReturn(true);
        doReturn(new IdentityContext()).when(protocolAuthService).parseIdentity(eq(request));
        doReturn(Resource.EMPTY_RESOURCE).when(protocolAuthService).parseResource(eq(request), any(Secured.class));
        when(protocolAuthService.validateIdentity(any(IdentityContext.class), any(Resource.class))).thenReturn(false);
        authFilter.doFilter(request, response, filterChain);
        verify(filterChain, never()).doFilter(request, response);
        verify(response).sendError(eq(403), anyString());
    }
    
    @Test
    @Secured
    void testDoFilterWithNeedAuthSecuredAuthorityFailure()
            throws NoSuchMethodException, ServletException, IOException, AccessException {
        when(authConfigs.isAuthEnabled()).thenReturn(true);
        when(authConfigs.getServerIdentityKey()).thenReturn("1");
        when(authConfigs.getServerIdentityValue()).thenReturn("2");
        when(methodsCache.getMethod(request)).thenReturn(
                this.getClass().getDeclaredMethod("testDoFilterWithNeedAuthSecuredAuthorityFailure"));
        HttpProtocolAuthService protocolAuthService = injectMockPlugins();
        when(protocolAuthService.enableAuth(any(Secured.class))).thenReturn(true);
        doReturn(new IdentityContext()).when(protocolAuthService).parseIdentity(eq(request));
        doReturn(Resource.EMPTY_RESOURCE).when(protocolAuthService).parseResource(eq(request), any(Secured.class));
        when(protocolAuthService.validateIdentity(any(IdentityContext.class), any(Resource.class))).thenReturn(true);
        when(protocolAuthService.validateAuthority(any(IdentityContext.class), any(Permission.class))).thenReturn(
                false);
        authFilter.doFilter(request, response, filterChain);
        verify(filterChain, never()).doFilter(request, response);
        verify(response).sendError(eq(403), anyString());
    }
    
    private HttpProtocolAuthService injectMockPlugins() {
        HttpProtocolAuthService protocolAuthService = new HttpProtocolAuthService(authConfigs);
        protocolAuthService.initialize();
        HttpProtocolAuthService spyProtocolAuthService = spy(protocolAuthService);
        ReflectionTestUtils.setField(authFilter, "protocolAuthService", spyProtocolAuthService);
        return spyProtocolAuthService;
    }
}
