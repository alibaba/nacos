/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.console.controller;

import com.alibaba.nacos.console.security.nacos.NacosAuthManager;
import com.alibaba.nacos.console.security.nacos.users.NacosUser;
import com.alibaba.nacos.core.auth.AccessException;
import com.alibaba.nacos.core.auth.AuthConfigs;
import com.alibaba.nacos.core.auth.AuthSystemTypes;
import com.fasterxml.jackson.databind.JsonNode;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import java.lang.reflect.Field;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.junit.Assert.*;
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
    }

    @Test
    public void testLoginWithAuthedUser() throws AccessException {
        when(authManager.login(request)).thenReturn(user);
        when(authConfigs.getNacosAuthSystemType()).thenReturn(AuthSystemTypes.NACOS.name());
        when(authConfigs.getTokenValidityInSeconds()).thenReturn(18000L);
        Object actual = userController.login("nacos", "nacos", response, request);
        assertThat(actual, instanceOf(JsonNode.class));
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
