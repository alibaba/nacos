/*
 * Copyright 1999-2024 Alibaba Group Holding Ltd.
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
 *
 */

package com.alibaba.nacos.plugin.auth.impl.controller.v3;

import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.auth.config.NacosAuthConfig;
import com.alibaba.nacos.auth.config.NacosAuthConfigHolder;
import com.alibaba.nacos.core.auth.NacosServerAuthConfig;
import com.alibaba.nacos.core.context.RequestContext;
import com.alibaba.nacos.core.context.RequestContextHolder;
import com.alibaba.nacos.plugin.auth.api.IdentityContext;
import com.alibaba.nacos.plugin.auth.constant.Constants;
import com.alibaba.nacos.plugin.auth.exception.AccessException;
import com.alibaba.nacos.plugin.auth.impl.authenticate.IAuthenticationManager;
import com.alibaba.nacos.plugin.auth.impl.configuration.AuthConfigs;
import com.alibaba.nacos.plugin.auth.impl.constant.AuthConstants;
import com.alibaba.nacos.plugin.auth.impl.constant.AuthSystemTypes;
import com.alibaba.nacos.plugin.auth.impl.persistence.RoleInfo;
import com.alibaba.nacos.plugin.auth.impl.persistence.User;
import com.alibaba.nacos.plugin.auth.impl.roles.NacosRoleService;
import com.alibaba.nacos.plugin.auth.impl.token.TokenManagerDelegate;
import com.alibaba.nacos.plugin.auth.impl.users.NacosUser;
import com.alibaba.nacos.plugin.auth.impl.users.NacosUserService;
import com.alibaba.nacos.sys.env.EnvUtil;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.alibaba.nacos.api.common.Constants.ACCESS_TOKEN;
import static com.alibaba.nacos.api.common.Constants.GLOBAL_ADMIN;
import static com.alibaba.nacos.api.common.Constants.TOKEN_TTL;
import static com.alibaba.nacos.api.common.Constants.USERNAME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * UserControllerV3Test.
 *
 * @author zhangyukun on:2024/9/5
 */
@ExtendWith(MockitoExtension.class)
class UserControllerV3Test {
    
    static {
        try {
            MockEnvironment environment = new MockEnvironment();
            environment.setProperty("nacos.core.auth.system.type", "nacos");
            environment.setProperty("nacos.core.auth.server.identity.key", "nacos");
            environment.setProperty("nacos.core.auth.server.identity.value", "nacos");
            environment.setProperty("nacos.core.auth.admin.enabled", "true");
            com.alibaba.nacos.sys.env.EnvUtil.setEnvironment(environment);
        } catch (Exception e) {
            // Ignore exception during static initialization
        }
    }
    
    @Mock
    private NacosUserService userDetailsService;
    
    @Mock
    private NacosRoleService roleService;
    
    @Mock
    private AuthConfigs authConfigs;
    
    @Mock
    private IAuthenticationManager iAuthenticationManager;
    
    @Mock
    private TokenManagerDelegate jwtTokenManager;
    
    @InjectMocks
    private UserControllerV3 userControllerV3;
    
    private NacosUser user;
    
    private Map<String, NacosAuthConfig> cachedConfigMap;
    
    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        cachedConfigMap = (Map<String, NacosAuthConfig>) ReflectionTestUtils.getField(
            NacosAuthConfigHolder.getInstance(), "nacosAuthConfigMap");
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty(Constants.Auth.NACOS_CORE_AUTH_ENABLED, "true");
        environment.setProperty(Constants.Auth.NACOS_CORE_AUTH_SYSTEM_TYPE, "nacos");
        environment.setProperty(Constants.Auth.NACOS_CORE_AUTH_SERVER_IDENTITY_KEY, "nacos");
        environment.setProperty(Constants.Auth.NACOS_CORE_AUTH_SERVER_IDENTITY_VALUE, "nacos");
        EnvUtil.setEnvironment(environment);
        setAuthEnabled();
        user = new NacosUser();
        user.setUserName("nacos");
        user.setToken("1234567890");
        user.setGlobalAdmin(true);
    }
    
    @AfterEach
    void tearDown() {
        EnvUtil.setEnvironment(null);
        ReflectionTestUtils.setField(NacosAuthConfigHolder.getInstance(), "nacosAuthConfigMap",
            cachedConfigMap);
        RequestContextHolder.removeContext();
    }
    
    private static void setAuthEnabled() {
        Map<String, NacosAuthConfig> configMap = new HashMap<>();
        configMap.put(NacosServerAuthConfig.NACOS_SERVER_AUTH_SCOPE,
            new TestNacosAuthConfig(true));
        ReflectionTestUtils.setField(NacosAuthConfigHolder.getInstance(), "nacosAuthConfigMap",
            configMap);
    }
    
    private static final class TestNacosAuthConfig implements NacosAuthConfig {
        
        private final boolean authEnabled;
        
        private TestNacosAuthConfig(boolean authEnabled) {
            this.authEnabled = authEnabled;
        }
        
        @Override
        public String getAuthScope() {
            return NacosServerAuthConfig.NACOS_SERVER_AUTH_SCOPE;
        }
        
        @Override
        public boolean isAuthEnabled() {
            return authEnabled;
        }
        
        @Override
        public String getNacosAuthSystemType() {
            return AuthSystemTypes.NACOS.name();
        }
        
        @Override
        public boolean isSupportServerIdentity() {
            return true;
        }
        
        @Override
        public String getServerIdentityKey() {
            return "nacos";
        }
        
        @Override
        public String getServerIdentityValue() {
            return "nacos";
        }
    }
    
    @Test
    void testCreateUserSuccess() {
        when(userDetailsService.getUser("test")).thenReturn(null);
        
        ArgumentCaptor<String> passwordCaptor = ArgumentCaptor.forClass(String.class);
        
        Result<String> result = (Result<String>) userControllerV3.createUser("test", "testPass");
        
        verify(userDetailsService, times(1)).createUser(eq("test"), passwordCaptor.capture());
        
        assertEquals("testPass", passwordCaptor.getValue(), "Password hash should be 'testPass'");
        
        assertEquals("create user ok!", result.getData());
    }
    
    @Test
    void testCreateUserUserAlreadyExists() {
        when(userDetailsService.getUser("test")).thenReturn(new User());
        
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            userControllerV3.createUser("test", "testPass");
        });
        
        assertEquals("user 'test' already exist!", exception.getMessage());
    }
    
    @Test
    void testDeleteUserSuccess() {
        when(roleService.getRoles("nacos")).thenReturn(new ArrayList<>());
        
        Result<String> result = (Result<String>) userControllerV3.deleteUser("nacos");
        
        verify(userDetailsService, times(1)).deleteUser("nacos");
        assertEquals("delete user ok!", result.getData());
    }
    
    @Test
    void testDeleteUserSuccessWhenRolesAreNull() {
        when(roleService.getRoles("nacos")).thenReturn(null);
        
        Result<String> result = userControllerV3.deleteUser("nacos");
        
        verify(userDetailsService, times(1)).deleteUser("nacos");
        assertEquals("delete user ok!", result.getData());
    }
    
    @Test
    void testDeleteUserCannotDeleteAdmin() {
        List<RoleInfo> roleInfoList = new ArrayList<>();
        RoleInfo adminRole = new RoleInfo();
        adminRole.setRole(AuthConstants.GLOBAL_ADMIN_ROLE);
        roleInfoList.add(adminRole);
        
        when(roleService.getRoles("nacos")).thenReturn(roleInfoList);
        
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            userControllerV3.deleteUser("nacos");
        });
        
        assertEquals("cannot delete admin: nacos", exception.getMessage());
    }
    
    @Test
    void testUpdateUserSuccess() throws IOException {
        RequestContext requestContext = RequestContextHolder.getContext();
        IdentityContext identityContext = new IdentityContext();
        requestContext.getAuthContext().setIdentityContext(identityContext);
        NacosUser nacosUser = new NacosUser("nacos");
        nacosUser.setGlobalAdmin(true);
        identityContext.setParameter(AuthConstants.NACOS_USER_KEY, nacosUser);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        
        when(authConfigs.getServerIdentityKey()).thenReturn("nacos");
        when(authConfigs.getServerIdentityValue()).thenReturn("nacos");
        when(userDetailsService.getUser("nacos")).thenReturn(new User());
        
        ArgumentCaptor<String> passwordCaptor = ArgumentCaptor.forClass(String.class);
        Result<String> result = userControllerV3.updateUser("nacos", "newPass", response, request);
        
        verify(userDetailsService, times(1)).updateUserPassword(eq("nacos"),
            passwordCaptor.capture());
        
        assertEquals("newPass", passwordCaptor.getValue());
        assertEquals("update user ok!", result.getData());
    }
    
    @Test
    void testUpdateUserFromServerIdentitySuccess() throws IOException {
        // 测试通过服务器身份验证的情况
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("nacos", "nacos"); // 添加服务器身份标识
        
        MockHttpServletResponse response = new MockHttpServletResponse();
        
        when(userDetailsService.getUser("anyUser")).thenReturn(new User());
        when(authConfigs.getServerIdentityKey()).thenReturn("nacos");
        when(authConfigs.getServerIdentityValue()).thenReturn("nacos");
        
        Result<String> result =
            userControllerV3.updateUser("anyUser", "newPass", response, request);
        
        verify(userDetailsService, times(1)).updateUserPassword(eq("anyUser"), anyString());
        assertEquals("update user ok!", result.getData());
    }
    
    @Test
    void testUpdateUserRejectsMissingUserAfterPermissionPassed() throws IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("nacos", "nacos");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(authConfigs.getServerIdentityKey()).thenReturn("nacos");
        when(authConfigs.getServerIdentityValue()).thenReturn("nacos");
        when(userDetailsService.getUser("missing")).thenReturn(null);
        
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> userControllerV3.updateUser("missing", "newPass", response, request));
        
        assertEquals("user missing not exist!", exception.getMessage());
    }
    
    @Test
    void testUpdateUserFromServerIdentityFailure() throws IOException {
        // 测试服务器身份验证失败的情况
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("nacos", "invalid"); // 错误的服务器身份标识
        
        MockHttpServletResponse response = new MockHttpServletResponse();
        
        when(authConfigs.getServerIdentityKey()).thenReturn("nacos");
        when(authConfigs.getServerIdentityValue()).thenReturn("nacos");
        // 不设置用户上下文，模拟无权限情况
        
        Result<String> result =
            userControllerV3.updateUser("anyUser", "newPass", response, request);
        
        assertEquals(HttpServletResponse.SC_UNAUTHORIZED, response.getStatus());
        assertEquals(null, result);
    }
    
    @Test
    void testUpdateUserSuccessForSameNonAdminUser() throws IOException {
        RequestContext requestContext = RequestContextHolder.getContext();
        IdentityContext identityContext = new IdentityContext();
        NacosUser sameUser = new NacosUser("nacos");
        sameUser.setGlobalAdmin(false);
        identityContext.setParameter(AuthConstants.NACOS_USER_KEY, sameUser);
        requestContext.getAuthContext().setIdentityContext(identityContext);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("nacos", "invalid");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(authConfigs.getServerIdentityKey()).thenReturn("nacos");
        when(authConfigs.getServerIdentityValue()).thenReturn("nacos");
        when(userDetailsService.getUser("nacos")).thenReturn(new User());
        
        Result<String> result = userControllerV3.updateUser("nacos", "newPass", response, request);
        
        assertEquals("update user ok!", result.getData());
        verify(userDetailsService).updateUserPassword("nacos", "newPass");
        verify(iAuthenticationManager).hasGlobalAdminRole(sameUser);
    }
    
    @Test
    void testUpdateUserForbiddenForDifferentNonAdminUser() throws IOException {
        RequestContext requestContext = RequestContextHolder.getContext();
        IdentityContext identityContext = new IdentityContext();
        NacosUser otherUser = new NacosUser("other");
        otherUser.setGlobalAdmin(false);
        identityContext.setParameter(AuthConstants.NACOS_USER_KEY, otherUser);
        requestContext.getAuthContext().setIdentityContext(identityContext);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("nacos", "invalid");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(authConfigs.getServerIdentityKey()).thenReturn("nacos");
        when(authConfigs.getServerIdentityValue()).thenReturn("nacos");
        
        Result<String> result = userControllerV3.updateUser("nacos", "newPass", response, request);
        
        assertEquals(HttpServletResponse.SC_FORBIDDEN, response.getStatus());
        assertEquals(null, result);
        verify(iAuthenticationManager).hasGlobalAdminRole(otherUser);
    }
    
    @Test
    void testUpdateUserSessionExpiredWhenAuthenticationReturnsNull()
        throws AccessException, IOException {
        RequestContext requestContext = RequestContextHolder.getContext();
        requestContext.getAuthContext().setIdentityContext(new IdentityContext());
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("nacos", "invalid");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(authConfigs.getServerIdentityKey()).thenReturn("nacos");
        when(authConfigs.getServerIdentityValue()).thenReturn("nacos");
        when(iAuthenticationManager.authenticate(request)).thenReturn(null);
        
        Result<String> result = userControllerV3.updateUser("nacos", "newPass", response, request);
        
        assertEquals(HttpServletResponse.SC_UNAUTHORIZED, response.getStatus());
        assertEquals(null, result);
    }
    
    @Test
    void testUpdateUserForbiddenWhenAuthenticationThrowsAccessException()
        throws AccessException, IOException {
        RequestContext requestContext = RequestContextHolder.getContext();
        requestContext.getAuthContext().setIdentityContext(new IdentityContext());
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("nacos", "invalid");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(authConfigs.getServerIdentityKey()).thenReturn("nacos");
        when(authConfigs.getServerIdentityValue()).thenReturn("nacos");
        when(iAuthenticationManager.authenticate(request)).thenThrow(new AccessException("denied"));
        
        Result<String> result = userControllerV3.updateUser("nacos", "newPass", response, request);
        
        assertEquals(HttpServletResponse.SC_FORBIDDEN, response.getStatus());
        assertEquals(null, result);
    }
    
    @Test
    void testGetUserListAccurateAndBlur() {
        Page<User> accuratePage = new Page<>();
        accuratePage.setPageNumber(1);
        Page<User> blurPage = new Page<>();
        blurPage.setPageNumber(2);
        when(userDetailsService.getUsers(1, 10, "nacos")).thenReturn(accuratePage);
        when(userDetailsService.findUsers("nacos", 2, 20)).thenReturn(blurPage);
        
        assertEquals(accuratePage, userControllerV3.getUserList(1, 10, "nacos", "accurate")
            .getData());
        assertEquals(blurPage, userControllerV3.getUserList(2, 20, "nacos", "blur").getData());
    }
    
    @Test
    void testGetUserListByUsername() {
        List<String> users = List.of("nacos", "nacos-dev");
        when(userDetailsService.findUserNames("nacos")).thenReturn(users);
        
        assertEquals(users, userControllerV3.getUserListByUsername("nacos").getData());
    }
    
    @Test
    void testLoginSuccess() throws AccessException, IOException {
        NacosUser user = new NacosUser();
        user.setUserName("nacos");
        user.setToken("1234567890");
        user.setGlobalAdmin(true);
        MockHttpServletRequest request = new MockHttpServletRequest();
        when(iAuthenticationManager.authenticate(request)).thenReturn(user);
        when(iAuthenticationManager.hasGlobalAdminRole(user)).thenReturn(true);
        when(authConfigs.getNacosAuthSystemType()).thenReturn(AuthSystemTypes.NACOS.name());
        when(jwtTokenManager.getTokenTtlInSeconds(anyString())).thenReturn(18000L);
        MockHttpServletResponse response = new MockHttpServletResponse();
        Object actual = userControllerV3.login(response, request);
        
        assertInstanceOf(Map.class, actual);
        
        Map<?, ?> map = (Map<?, ?>) actual;
        assertTrue(map.containsKey(ACCESS_TOKEN));
        assertTrue(map.containsKey(TOKEN_TTL));
        assertTrue(map.containsKey(GLOBAL_ADMIN));
        assertEquals(user.getToken(), map.get(ACCESS_TOKEN));
        assertEquals(18000L, map.get(TOKEN_TTL));
        assertEquals(true, map.get(GLOBAL_ADMIN));
        assertEquals(user.getUserName(), map.get(USERNAME));
        
        assertEquals(AuthConstants.TOKEN_PREFIX + user.getToken(),
            response.getHeader(AuthConstants.AUTHORIZATION_HEADER));
    }
    
    @Test
    void testLoginForLdapAuthSystem() throws AccessException, IOException {
        NacosUser ldapUser = new NacosUser();
        ldapUser.setUserName("ldapUser");
        ldapUser.setToken("ldap-token");
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(authConfigs.getNacosAuthSystemType()).thenReturn(AuthSystemTypes.LDAP.name());
        when(iAuthenticationManager.authenticate(request)).thenReturn(ldapUser);
        when(iAuthenticationManager.hasGlobalAdminRole(ldapUser)).thenReturn(false);
        when(jwtTokenManager.getTokenTtlInSeconds("ldap-token")).thenReturn(60L);
        
        Object actual = userControllerV3.login(response, request);
        
        assertTrue(actual instanceof Map<?, ?>);
        String actualString = actual.toString();
        assertTrue(actualString.contains("accessToken=ldap-token"));
        assertTrue(actualString.contains("globalAdmin=false"));
        assertEquals(AuthConstants.TOKEN_PREFIX + "ldap-token",
            response.getHeader(AuthConstants.AUTHORIZATION_HEADER));
    }
    
    @Test
    void testLoginRejectsUnsupportedAuthSystem() throws AccessException, IOException {
        when(authConfigs.getNacosAuthSystemType()).thenReturn("oidc");
        MockHttpServletResponse response = new MockHttpServletResponse();
        
        Object actual = userControllerV3.login(response, new MockHttpServletRequest());
        
        assertTrue(actual instanceof Result);
        Result<?> result = (Result<?>) actual;
        assertEquals(com.alibaba.nacos.api.model.v2.ErrorCode.ILLEGAL_STATE.getCode(),
            result.getCode());
    }
    
    @Test
    void testCreateAdminUserSuccess() {
        when(authConfigs.getNacosAuthSystemType()).thenReturn(AuthSystemTypes.NACOS.name());
        when(iAuthenticationManager.hasGlobalAdminRole()).thenReturn(false);
        
        Result<User> result = userControllerV3.createAdminUser("testAdminPass");
        
        ArgumentCaptor<String> usernameCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> passwordCaptor = ArgumentCaptor.forClass(String.class);
        
        verify(userDetailsService, times(1)).createUser(usernameCaptor.capture(),
            passwordCaptor.capture());
        
        assertEquals(AuthConstants.DEFAULT_USER, usernameCaptor.getValue());
        
        User data = result.getData();
        assertEquals(AuthConstants.DEFAULT_USER, data.getUsername());
        assertEquals("testAdminPass", data.getPassword());
        
        assertEquals("testAdminPass", passwordCaptor.getValue());
    }
    
    @Test
    void testCreateAdminUserGeneratesPasswordWhenBlank() {
        when(authConfigs.getNacosAuthSystemType()).thenReturn(AuthSystemTypes.NACOS.name());
        when(iAuthenticationManager.hasGlobalAdminRole()).thenReturn(false);
        
        Result<User> result = userControllerV3.createAdminUser("");
        
        assertEquals(AuthConstants.DEFAULT_USER, result.getData().getUsername());
        assertFalse(result.getData().getPassword().isEmpty());
        verify(roleService).addAdminRole(AuthConstants.DEFAULT_USER);
    }
    
    @Test
    void testCreateAdminUserConflict() {
        when(authConfigs.getNacosAuthSystemType()).thenReturn(AuthSystemTypes.NACOS.name());
        when(iAuthenticationManager.hasGlobalAdminRole()).thenReturn(true);
        
        Result<User> result = userControllerV3.createAdminUser("adminPass");
        
        assertEquals(HttpStatus.CONFLICT.value(), result.getCode());
    }
    
    @Test
    void testCreateAdminUserRejectsUnsupportedAuthSystem() {
        when(authConfigs.getNacosAuthSystemType()).thenReturn("oidc");
        
        Result<User> result = userControllerV3.createAdminUser("adminPass");
        
        assertEquals(HttpStatus.NOT_IMPLEMENTED.value(), result.getCode());
    }
}
