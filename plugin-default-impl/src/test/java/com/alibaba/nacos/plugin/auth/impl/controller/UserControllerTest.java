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

import com.alibaba.nacos.auth.config.AuthConfigs;
import com.alibaba.nacos.plugin.auth.exception.AccessException;
import com.alibaba.nacos.plugin.auth.impl.JwtTokenManager;
import com.alibaba.nacos.plugin.auth.impl.NacosAuthManager;
import com.alibaba.nacos.plugin.auth.impl.constant.AuthConstants;
import com.alibaba.nacos.plugin.auth.impl.constant.AuthSystemTypes;
import com.alibaba.nacos.plugin.auth.impl.users.NacosUser;
import com.alibaba.nacos.sys.env.EnvUtil;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.mock.env.MockEnvironment;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class UserControllerTest {
    
    @Mock
    private HttpServletRequest request;
    
    @Mock
    private HttpServletResponse response;
    
    @Mock
    private AuthConfigs authConfigs;
    
    @Mock
    private NacosAuthManager authManager;
    
    private UserController userController;
    
    private NacosUser user;
    
    @Before
    public void setUp() throws Exception {
        userController = new UserController();
        user = new NacosUser();
        user.setUserName("nacos");
        user.setGlobalAdmin(true);
        user.setToken("1234567890");
        injectObject("authConfigs", authConfigs);
        injectObject("authManager", authManager);
        
        MockEnvironment mockEnvironment = new MockEnvironment();
        mockEnvironment.setProperty(AuthConstants.TOKEN_SECRET_KEY, Base64.getEncoder().encodeToString(
                "SecretKey0123$567890$234567890123456789012345678901234567890123456789".getBytes(
                        StandardCharsets.UTF_8)));
        mockEnvironment.setProperty(AuthConstants.TOKEN_EXPIRE_SECONDS,
                AuthConstants.DEFAULT_TOKEN_EXPIRE_SECONDS.toString());
    
        EnvUtil.setEnvironment(mockEnvironment);
        JwtTokenManager jwtTokenManager = new JwtTokenManager();
        injectObject("jwtTokenManager", jwtTokenManager);
    }
    
    @Test
    public void testLoginWithAuthedUser() throws AccessException {
        when(authManager.login(request)).thenReturn(user);
        when(authConfigs.getNacosAuthSystemType()).thenReturn(AuthSystemTypes.NACOS.name());
        Object actual = userController.login("nacos", "nacos", response, request);
        assertTrue(actual instanceof JsonNode);
        String actualString = actual.toString();
        assertTrue(actualString.contains("\"accessToken\":\"1234567890\""));
        assertTrue(actualString.contains("\"tokenTtl\":18000"));
        assertTrue(actualString.contains("\"globalAdmin\":true"));
    }
    
    private void injectObject(String fieldName, Object value) throws NoSuchFieldException, IllegalAccessException {
        Field field = UserController.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(userController, value);
    }
}
