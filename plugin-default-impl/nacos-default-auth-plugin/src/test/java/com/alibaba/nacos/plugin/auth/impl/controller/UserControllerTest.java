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

import com.alibaba.nacos.plugin.auth.impl.authenticate.IAuthenticationManager;
import com.alibaba.nacos.plugin.auth.impl.configuration.AuthConfigs;
import com.alibaba.nacos.plugin.auth.impl.constant.AuthConstants;
import com.alibaba.nacos.plugin.auth.impl.constant.AuthSystemTypes;
import com.alibaba.nacos.plugin.auth.impl.token.TokenManagerDelegate;
import com.alibaba.nacos.plugin.auth.impl.users.NacosUser;
import com.alibaba.nacos.sys.env.EnvUtil;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.env.MockEnvironment;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
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
    private AuthConfigs authConfigs;
    
    @Mock
    private IAuthenticationManager authenticationManager;
    
    @Mock
    private TokenManagerDelegate tokenManagerDelegate;
    
    @InjectMocks
    private UserController userController;
    
    private NacosUser user;
    
    @BeforeEach
    void setUp() throws Exception {
        user = new NacosUser();
        user.setUserName("nacos");
        user.setGlobalAdmin(true);
        user.setToken("1234567890");
        
        MockEnvironment mockEnvironment = new MockEnvironment();
        mockEnvironment.setProperty(AuthConstants.TOKEN_SECRET_KEY,
            Base64.getEncoder().encodeToString(
                "SecretKey0123$567890$234567890123456789012345678901234567890123456789".getBytes(
                    StandardCharsets.UTF_8)));
        mockEnvironment.setProperty(AuthConstants.TOKEN_EXPIRE_SECONDS,
            AuthConstants.DEFAULT_TOKEN_EXPIRE_SECONDS.toString());
        
        EnvUtil.setEnvironment(mockEnvironment);
    }
    
    @AfterEach
    void tearDown() {
        EnvUtil.setEnvironment(null);
    }
    
    @Test
    void testLoginWithAuthedUser() throws Exception {
        when(authenticationManager.authenticate(request)).thenReturn(user);
        when(authenticationManager.hasGlobalAdminRole(user)).thenReturn(true);
        when(authConfigs.getNacosAuthSystemType()).thenReturn(AuthSystemTypes.NACOS.name());
        when(tokenManagerDelegate.getTokenTtlInSeconds(anyString())).thenReturn(18000L);
        Object actual = userController.login("nacos", "nacos", response, request);
        assertTrue(actual instanceof JsonNode);
        String actualString = actual.toString();
        assertTrue(actualString.contains("\"accessToken\":\"1234567890\""));
        assertTrue(actualString.contains("\"tokenTtl\":18000"));
        assertTrue(actualString.contains("\"globalAdmin\":true"));
    }
}
