/*
 * Copyright 1999-2021 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.plugin.auth.impl.controller;

import com.alibaba.nacos.api.common.ApiType;
import com.alibaba.nacos.auth.config.NacosAuthConfig;
import com.alibaba.nacos.auth.config.NacosAuthConfigHolder;
import com.alibaba.nacos.common.model.RestResult;
import com.alibaba.nacos.plugin.auth.impl.authenticate.IAuthenticationManager;
import com.alibaba.nacos.plugin.auth.impl.constant.AuthConstants;
import com.alibaba.nacos.plugin.auth.impl.constant.AuthSystemTypes;
import com.alibaba.nacos.plugin.auth.impl.token.TokenManagerDelegate;
import com.alibaba.nacos.plugin.auth.impl.users.NacosUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.Map;

import static com.alibaba.nacos.api.common.Constants.ACCESS_TOKEN;
import static com.alibaba.nacos.api.common.Constants.GLOBAL_ADMIN;
import static com.alibaba.nacos.api.common.Constants.TOKEN_TTL;
import static com.alibaba.nacos.api.common.Constants.USERNAME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for UserController (v1 login API only; other v1 user APIs moved to legacy-adapter).
 */
@ExtendWith(MockitoExtension.class)
class UserControllerTest {
    
    @Mock
    private HttpServletRequest request;
    
    @Mock
    private HttpServletResponse response;
    
    @Mock
    private NacosAuthConfig serverAuthConfig;
    
    @Mock
    private IAuthenticationManager authenticationManager;
    
    @Mock
    private AuthenticationManager legacyAuthenticationManager;
    
    @Mock
    private Authentication legacyAuthentication;
    
    @Mock
    private TokenManagerDelegate tokenManagerDelegate;
    
    @InjectMocks
    private UserController userController;
    
    private NacosUser user;
    
    private Map<String, NacosAuthConfig> cachedConfigMap;
    
    @BeforeEach
    void setUp() throws Exception {
        user = new NacosUser();
        user.setUserName("nacos");
        user.setGlobalAdmin(true);
        user.setToken("1234567890");
        
        cachedConfigMap = getAuthConfigMap();
        ReflectionTestUtils.setField(NacosAuthConfigHolder.getInstance(), "nacosAuthConfigMap",
            Collections.singletonMap(ApiType.OPEN_API.name(), serverAuthConfig));
    }
    
    @AfterEach
    void tearDown() {
        ReflectionTestUtils.setField(NacosAuthConfigHolder.getInstance(), "nacosAuthConfigMap",
            cachedConfigMap);
        SecurityContextHolder.clearContext();
    }
    
    @Test
    void testLoginWithAuthedUser() throws Exception {
        when(authenticationManager.authenticate(request)).thenReturn(user);
        when(authenticationManager.hasGlobalAdminRole(user)).thenReturn(true);
        when(serverAuthConfig.getNacosAuthSystemType()).thenReturn(AuthSystemTypes.NACOS.name());
        when(tokenManagerDelegate.getTokenTtlInSeconds(anyString())).thenReturn(18000L);
        Object actual = userController.login("nacos", "nacos", response, request);
        Map<?, ?> map = (Map<?, ?>) actual;
        assertTrue(map.containsKey(ACCESS_TOKEN));
        assertTrue(map.containsKey(TOKEN_TTL));
        assertTrue(map.containsKey(GLOBAL_ADMIN));
        assertEquals(user.getToken(), map.get(ACCESS_TOKEN));
        assertEquals(18000L, map.get(TOKEN_TTL));
        assertEquals(true, map.get(GLOBAL_ADMIN));
        assertEquals(user.getUserName(), map.get(USERNAME));
    }
    
    @Test
    void testLoginWithLdapAuthedUser() throws Exception {
        when(authenticationManager.authenticate(request)).thenReturn(user);
        when(authenticationManager.hasGlobalAdminRole(user)).thenReturn(false);
        when(serverAuthConfig.getNacosAuthSystemType()).thenReturn(AuthSystemTypes.LDAP.name());
        when(tokenManagerDelegate.getTokenTtlInSeconds(anyString())).thenReturn(60L);
        
        Object actual = userController.login("nacos", "nacos", response, request);
        
        assertTrue(actual instanceof Map);
        String actualString = actual.toString();
        assertTrue(actualString.contains("accessToken=1234567890"));
        assertTrue(actualString.contains("globalAdmin=false"));
    }
    
    @Test
    void testLoginWithLegacySpringAuthentication() throws Exception {
        when(serverAuthConfig.getNacosAuthSystemType()).thenReturn("custom");
        when(legacyAuthenticationManager
            .authenticate(any(UsernamePasswordAuthenticationToken.class)))
            .thenReturn(legacyAuthentication);
        when(tokenManagerDelegate.createToken(legacyAuthentication)).thenReturn("legacy-token");
        
        Object actual = userController.login("nacos", "password", response, request);
        
        assertTrue(actual instanceof RestResult);
        RestResult<?> result = (RestResult<?>) actual;
        assertTrue(result.ok());
        assertEquals("Bearer legacy-token", result.getData());
        verify(response).addHeader(AuthConstants.AUTHORIZATION_HEADER, "Bearer legacy-token");
    }
    
    @Test
    void testLoginWithLegacySpringAuthenticationFailure() throws Exception {
        when(serverAuthConfig.getNacosAuthSystemType()).thenReturn("custom");
        when(legacyAuthenticationManager
            .authenticate(any(UsernamePasswordAuthenticationToken.class)))
            .thenThrow(new BadCredentialsException("bad"));
        
        Object actual = userController.login("nacos", "bad", response, request);
        
        assertTrue(actual instanceof RestResult);
        RestResult<?> result = (RestResult<?>) actual;
        assertEquals(401, result.getCode());
        assertEquals("Login failed", result.getMessage());
    }
    
    @SuppressWarnings("unchecked")
    private Map<String, NacosAuthConfig> getAuthConfigMap() {
        return (Map<String, NacosAuthConfig>) ReflectionTestUtils.getField(
            NacosAuthConfigHolder.getInstance(), "nacosAuthConfigMap");
    }
}
