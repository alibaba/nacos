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
import com.alibaba.nacos.common.model.RestResult;
import com.alibaba.nacos.core.context.RequestContextHolder;
import com.alibaba.nacos.persistence.model.Page;
import com.alibaba.nacos.plugin.auth.api.IdentityContext;
import com.alibaba.nacos.plugin.auth.exception.AccessException;
import com.alibaba.nacos.plugin.auth.impl.authenticate.IAuthenticationManager;
import com.alibaba.nacos.plugin.auth.impl.constant.AuthConstants;
import com.alibaba.nacos.plugin.auth.impl.constant.AuthSystemTypes;
import com.alibaba.nacos.plugin.auth.impl.persistence.RoleInfo;
import com.alibaba.nacos.plugin.auth.impl.persistence.User;
import com.alibaba.nacos.plugin.auth.impl.roles.NacosRoleServiceImpl;
import com.alibaba.nacos.plugin.auth.impl.token.TokenManagerDelegate;
import com.alibaba.nacos.plugin.auth.impl.users.NacosUser;
import com.alibaba.nacos.plugin.auth.impl.users.NacosUserDetailsServiceImpl;
import com.alibaba.nacos.sys.env.EnvUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

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
    
    @Mock
    private NacosUserDetailsServiceImpl userDetailsService;
    
    @Mock
    private NacosRoleServiceImpl roleService;
    
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
        mockEnvironment.setProperty(AuthConstants.TOKEN_SECRET_KEY, Base64.getEncoder().encodeToString(
                "SecretKey0123$567890$234567890123456789012345678901234567890123456789".getBytes(
                        StandardCharsets.UTF_8)));
        mockEnvironment.setProperty(AuthConstants.TOKEN_EXPIRE_SECONDS,
                AuthConstants.DEFAULT_TOKEN_EXPIRE_SECONDS.toString());
        
        EnvUtil.setEnvironment(mockEnvironment);
        RequestContextHolder.getContext().getAuthContext().setIdentityContext(new IdentityContext());
    }
    
    @AfterEach
    public void tearDown() {
        RequestContextHolder.removeContext();
    }
    
    @Test
    void testLoginWithAuthedUser() throws AccessException, IOException {
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
    
    @Test
    void testCreateUser1() {
        when(userDetailsService.getUserFromDatabase("test")).thenReturn(null);
        RestResult<String> result = (RestResult<String>) userController.createUser("test", "test");
        assertEquals(200, result.getCode());
        
    }
    
    @Test
    void testCreateUser2() {
        when(userDetailsService.getUserFromDatabase("test")).thenReturn(new User());
        assertThrows(IllegalArgumentException.class, () -> {
            userController.createUser("test", "test");
        });
    }
    
    @Test
    void testCreateUserNamedNacos() {
        RestResult<String> result = (RestResult<String>) userController.createUser("nacos", "test");
        assertEquals(409, result.getCode());
    }
    
    @Test
    void testCreateAdminUser1() {
        when(authConfigs.getNacosAuthSystemType()).thenReturn(AuthSystemTypes.NACOS.name());
        when(authenticationManager.hasGlobalAdminRole()).thenReturn(true);
        
        RestResult<String> result = (RestResult<String>) userController.createAdminUser("test");
        
        assertEquals(HttpStatus.CONFLICT.value(), result.getCode());
    }
    
    @Test
    void testCreateAdminUser2() {
        RestResult<String> result = (RestResult<String>) userController.createAdminUser("test");
        
        assertEquals(HttpStatus.NOT_IMPLEMENTED.value(), result.getCode());
    }
    
    @Test
    void testCreateAdminUser3() {
        when(authConfigs.getNacosAuthSystemType()).thenReturn(AuthSystemTypes.NACOS.name());
        when(authenticationManager.hasGlobalAdminRole()).thenReturn(false);
        ObjectNode result = (ObjectNode) userController.createAdminUser("test");
        
        assertEquals("test", result.get(AuthConstants.PARAM_PASSWORD).asText());
    }
    
    @Test
    void testDeleteUser1() {
        List<RoleInfo> roleInfoList = new ArrayList<>(1);
        RoleInfo testRole = new RoleInfo();
        testRole.setUsername("nacos");
        testRole.setRole(AuthConstants.GLOBAL_ADMIN_ROLE);
        roleInfoList.add(testRole);
        
        when(roleService.getRoles(anyString())).thenReturn(roleInfoList);
        
        assertThrows(IllegalArgumentException.class, () -> {
            userController.deleteUser("nacos");
        });
        
    }
    
    @Test
    void testDeleteUser2() {
        List<RoleInfo> roleInfoList = new ArrayList<>(1);
        RoleInfo testRole = new RoleInfo();
        testRole.setUsername("nacos");
        testRole.setRole("testRole");
        roleInfoList.add(testRole);
        
        when(roleService.getRoles(anyString())).thenReturn(roleInfoList);
        
        RestResult<String> result = (RestResult<String>) userController.deleteUser("nacos");
        assertEquals(200, result.getCode());
    }
    
    @Test
    void testUpdateUser1() throws IOException {
        
        when(authConfigs.isAuthEnabled()).thenReturn(false);
        when(userDetailsService.getUserFromDatabase(anyString())).thenReturn(new User());
        MockHttpServletRequest mockHttpServletRequest = new MockHttpServletRequest();
        MockHttpServletResponse mockHttpServletResponse = new MockHttpServletResponse();
        RestResult<String> result = (RestResult<String>) userController.updateUser("nacos", "test",
                mockHttpServletResponse, mockHttpServletRequest);
        assertEquals(200, result.getCode());
        
    }
    
    @Test
    void testUpdateUser2() {
        
        when(authConfigs.isAuthEnabled()).thenReturn(false);
        when(userDetailsService.getUserFromDatabase(anyString())).thenReturn(null);
        MockHttpServletRequest mockHttpServletRequest = new MockHttpServletRequest();
        MockHttpServletResponse mockHttpServletResponse = new MockHttpServletResponse();
        
        assertThrows(IllegalArgumentException.class, () -> {
            userController.updateUser("nacos", "test", mockHttpServletResponse, mockHttpServletRequest);
        });
    }
    
    @Test
    void testUpdateUser3() throws IOException {
        RequestContextHolder.getContext().getAuthContext().setIdentityContext(null);
        when(authConfigs.isAuthEnabled()).thenReturn(true);
        MockHttpServletRequest mockHttpServletRequest = new MockHttpServletRequest();
        MockHttpServletResponse mockHttpServletResponse = new MockHttpServletResponse();
        Object result = userController.updateUser("nacos", "test", mockHttpServletResponse, mockHttpServletRequest);
        
        assertNull(result);
        assertEquals(HttpServletResponse.SC_UNAUTHORIZED, mockHttpServletResponse.getStatus());
        
    }
    
    @Test
    void testUpdateUser4() throws IOException {
        RequestContextHolder.getContext().getAuthContext().getIdentityContext()
                .setParameter(AuthConstants.NACOS_USER_KEY, user);
        when(authConfigs.isAuthEnabled()).thenReturn(true);
        when(userDetailsService.getUserFromDatabase(anyString())).thenReturn(new User());
        MockHttpServletRequest mockHttpServletRequest = new MockHttpServletRequest();
        MockHttpServletResponse mockHttpServletResponse = new MockHttpServletResponse();
        RestResult<String> result = (RestResult<String>) userController.updateUser("nacos", "test",
                mockHttpServletResponse, mockHttpServletRequest);
        assertEquals(200, result.getCode());
        
    }
    
    @Test
    void testUpdateUser5() throws IOException, AccessException {
        RequestContextHolder.getContext().getAuthContext().getIdentityContext()
                .setParameter(AuthConstants.NACOS_USER_KEY, null);
        when(authConfigs.isAuthEnabled()).thenReturn(true);
        when(userDetailsService.getUserFromDatabase(anyString())).thenReturn(new User());
        when(authenticationManager.authenticate(any(MockHttpServletRequest.class))).thenReturn(user);
        
        MockHttpServletRequest mockHttpServletRequest = new MockHttpServletRequest();
        MockHttpServletResponse mockHttpServletResponse = new MockHttpServletResponse();
        RestResult<String> result = (RestResult<String>) userController.updateUser("nacos", "test",
                mockHttpServletResponse, mockHttpServletRequest);
        assertEquals(200, result.getCode());
        
    }
    
    @Test
    void testUpdateUser6() throws IOException, AccessException {
        RequestContextHolder.getContext().getAuthContext().getIdentityContext()
                .setParameter(AuthConstants.NACOS_USER_KEY, null);
        when(authConfigs.isAuthEnabled()).thenReturn(true);
        when(authenticationManager.authenticate(any(MockHttpServletRequest.class))).thenReturn(null);
        
        MockHttpServletRequest mockHttpServletRequest = new MockHttpServletRequest();
        MockHttpServletResponse mockHttpServletResponse = new MockHttpServletResponse();
        Object result = userController.updateUser("nacos", "test", mockHttpServletResponse, mockHttpServletRequest);
        
        assertNull(result);
        assertEquals(HttpServletResponse.SC_UNAUTHORIZED, mockHttpServletResponse.getStatus());
        
    }
    
    @Test
    void testUpdateUser7() throws IOException, AccessException {
        RequestContextHolder.getContext().getAuthContext().getIdentityContext()
                .setParameter(AuthConstants.NACOS_USER_KEY, null);
        when(authConfigs.isAuthEnabled()).thenReturn(true);
        when(authenticationManager.authenticate(any(MockHttpServletRequest.class))).thenThrow(
                new AccessException("test"));
        
        MockHttpServletRequest mockHttpServletRequest = new MockHttpServletRequest();
        MockHttpServletResponse mockHttpServletResponse = new MockHttpServletResponse();
        Object result = userController.updateUser("nacos", "test", mockHttpServletResponse, mockHttpServletRequest);
        
        assertNull(result);
        assertEquals(HttpServletResponse.SC_FORBIDDEN, mockHttpServletResponse.getStatus());
        
    }
    
    @Test
    void testGetUsers() {
        Page<User> userPage = new Page<>();
        
        when(userDetailsService.getUsersFromDatabase(anyInt(), anyInt(), anyString())).thenReturn(userPage);
        
        Page<User> nacos = userController.getUsers(1, 10, "nacos");
        assertEquals(userPage, nacos);
    }
    
    @Test
    void testFuzzySearchUser() {
        Page<User> userPage = new Page<>();
        
        when(userDetailsService.findUsersLike4Page(anyString(), anyInt(), anyInt())).thenReturn(userPage);
        
        Page<User> nacos = userController.fuzzySearchUser(1, 10, "nacos");
        assertEquals(userPage, nacos);
    }
    
    @Test
    void testSearchUsersLikeUsername() {
        List<String> test = new ArrayList<>(1);
        
        when(userDetailsService.findUserLikeUsername(anyString())).thenReturn(test);
        List<String> list = userController.searchUsersLikeUsername("nacos");
        
        assertEquals(test, list);
    }
    
}
