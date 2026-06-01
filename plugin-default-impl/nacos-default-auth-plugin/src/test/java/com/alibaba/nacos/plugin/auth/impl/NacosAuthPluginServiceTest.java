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

package com.alibaba.nacos.plugin.auth.impl;

import com.alibaba.nacos.api.common.ApiType;
import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.auth.config.NacosAuthConfig;
import com.alibaba.nacos.auth.config.NacosAuthConfigHolder;
import com.alibaba.nacos.plugin.auth.api.AuthResult;
import com.alibaba.nacos.plugin.auth.api.IdentityContext;
import com.alibaba.nacos.plugin.auth.api.Permission;
import com.alibaba.nacos.plugin.auth.api.Resource;
import com.alibaba.nacos.plugin.auth.constant.Constants.Identity;
import com.alibaba.nacos.plugin.auth.constant.ActionTypes;
import com.alibaba.nacos.plugin.auth.exception.AccessException;
import com.alibaba.nacos.plugin.auth.impl.authenticate.IAuthenticationManager;
import com.alibaba.nacos.plugin.auth.impl.configuration.AuthConfigs;
import com.alibaba.nacos.plugin.auth.impl.constant.AuthConstants;
import com.alibaba.nacos.plugin.auth.impl.users.NacosUser;
import com.alibaba.nacos.sys.utils.ApplicationUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NacosAuthPluginServiceTest {
    
    @Mock
    private IAuthenticationManager authenticationManager;
    
    @Mock
    private AuthConfigs authConfigs;
    
    private NacosAuthPluginService authPluginService;
    
    @BeforeEach
    void setUp() throws Exception {
        authPluginService = new NacosAuthPluginService();
        Field amField = NacosAuthPluginService.class.getDeclaredField("authenticationManager");
        amField.setAccessible(true);
        amField.set(authPluginService, authenticationManager);
        Field acField = NacosAuthPluginService.class.getDeclaredField("authConfigs");
        acField.setAccessible(true);
        acField.set(authPluginService, authConfigs);
    }
    
    @Test
    void testMetadataMethods() {
        assertTrue(authPluginService.identityNames().contains(AuthConstants.AUTHORIZATION_HEADER));
        assertTrue(authPluginService.identityNames().contains(Constants.ACCESS_TOKEN));
        assertTrue(authPluginService.enableAuth(ActionTypes.READ, "naming"));
        assertEquals(AuthConstants.AUTH_PLUGIN_TYPE, authPluginService.getAuthServiceName());
    }
    
    @Test
    void testValidateIdentityAnonymousAllowed() throws AccessException {
        when(authenticationManager.authenticate(any(), any()))
            .thenThrow(new AccessException("no credentials"));
        when(authConfigs.isAiAnonymousEnabled()).thenReturn(true);
        
        Properties properties = new Properties();
        properties.setProperty(AuthConstants.TAG_ALLOW_ANONYMOUS, "true");
        Resource resource = new Resource("ns", "g", "name", "type", properties);
        IdentityContext identityContext = new IdentityContext();
        
        AuthResult<?> result = authPluginService.validateIdentity(identityContext, resource);
        
        assertTrue(result.isSuccess());
        assertInstanceOf(NacosUser.class, result.getData());
        assertEquals(AuthConstants.ANONYMOUS_USER, ((NacosUser) result.getData()).getUserName());
        Object nacosUserInContext = identityContext.getParameter(AuthConstants.NACOS_USER_KEY);
        assertInstanceOf(NacosUser.class, nacosUserInContext);
        assertEquals(AuthConstants.ANONYMOUS_USER, ((NacosUser) nacosUserInContext).getUserName());
        assertEquals(AuthConstants.ANONYMOUS_USER,
            identityContext.getParameter(Identity.IDENTITY_ID, ""));
    }
    
    @Test
    void testValidateIdentityNoTagDenied() throws AccessException {
        when(authenticationManager.authenticate(any(), any()))
            .thenThrow(new AccessException("no credentials"));
        
        Resource resource = new Resource("ns", "g", "name", "type", new Properties());
        IdentityContext identityContext = new IdentityContext();
        
        AuthResult<?> result = authPluginService.validateIdentity(identityContext, resource);
        
        assertFalse(result.isSuccess());
        assertEquals(HttpStatus.UNAUTHORIZED.value(), result.getErrorCode());
        verify(authenticationManager).authenticate(any(), any());
    }
    
    @Test
    void testValidateIdentitySwitchOffDenied() throws AccessException {
        when(authenticationManager.authenticate(any(), any()))
            .thenThrow(new AccessException("no credentials"));
        when(authConfigs.isAiAnonymousEnabled()).thenReturn(false);
        
        Properties properties = new Properties();
        properties.setProperty(AuthConstants.TAG_ALLOW_ANONYMOUS, "true");
        Resource resource = new Resource("ns", "g", "name", "type", properties);
        IdentityContext identityContext = new IdentityContext();
        
        AuthResult<?> result = authPluginService.validateIdentity(identityContext, resource);
        
        assertFalse(result.isSuccess());
        assertEquals(HttpStatus.UNAUTHORIZED.value(), result.getErrorCode());
    }
    
    @Test
    void testValidateIdentityNormalUserSuccess() throws AccessException {
        NacosUser expectedUser = new NacosUser("realuser");
        when(authenticationManager.authenticate(anyString())).thenReturn(expectedUser);
        
        IdentityContext identityContext = new IdentityContext();
        identityContext.setParameter(Constants.ACCESS_TOKEN, "jwt-token");
        Resource resource = Resource.EMPTY_RESOURCE;
        
        AuthResult<?> result = authPluginService.validateIdentity(identityContext, resource);
        
        assertTrue(result.isSuccess());
        assertEquals(expectedUser, result.getData());
        Object nacosUserInContext = identityContext.getParameter(AuthConstants.NACOS_USER_KEY);
        assertInstanceOf(NacosUser.class, nacosUserInContext);
        assertEquals("realuser", ((NacosUser) nacosUserInContext).getUserName());
        assertEquals("realuser", identityContext.getParameter(Identity.IDENTITY_ID, ""));
        verify(authenticationManager).authenticate("jwt-token");
    }
    
    @Test
    void testValidateIdentityUsesBearerToken() throws AccessException {
        NacosUser expectedUser = new NacosUser("bearerUser");
        when(authenticationManager.authenticate("jwt-token")).thenReturn(expectedUser);
        IdentityContext identityContext = new IdentityContext();
        identityContext.setParameter(AuthConstants.AUTHORIZATION_HEADER,
            AuthConstants.TOKEN_PREFIX + "jwt-token");
        
        AuthResult<?> result =
            authPluginService.validateIdentity(identityContext, Resource.EMPTY_RESOURCE);
        
        assertTrue(result.isSuccess());
        assertEquals(expectedUser, result.getData());
        verify(authenticationManager).authenticate("jwt-token");
    }
    
    @Test
    void testValidateIdentityUsesUsernameAndPassword() throws AccessException {
        NacosUser expectedUser = new NacosUser("nacos");
        when(authenticationManager.authenticate("nacos", "password")).thenReturn(expectedUser);
        IdentityContext identityContext = new IdentityContext();
        identityContext.setParameter(AuthConstants.PARAM_USERNAME, "nacos");
        identityContext.setParameter(AuthConstants.PARAM_PASSWORD, "password");
        
        AuthResult<?> result =
            authPluginService.validateIdentity(identityContext, Resource.EMPTY_RESOURCE);
        
        assertTrue(result.isSuccess());
        assertEquals(expectedUser, result.getData());
        verify(authenticationManager).authenticate("nacos", "password");
    }
    
    @Test
    void testValidateIdentityRejectsNullResourceAndAnonymousConfigFailure()
        throws AccessException {
        when(authenticationManager.authenticate(any(), any()))
            .thenThrow(new AccessException("no credentials"));
        
        AuthResult<?> nullResourceResult =
            authPluginService.validateIdentity(new IdentityContext(), null);
        
        assertFalse(nullResourceResult.isSuccess());
        assertEquals(HttpStatus.UNAUTHORIZED.value(), nullResourceResult.getErrorCode());
        
        Field acField;
        try {
            acField = NacosAuthPluginService.class.getDeclaredField("authConfigs");
            acField.setAccessible(true);
            acField.set(authPluginService, null);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
        Properties properties = new Properties();
        properties.setProperty(AuthConstants.TAG_ALLOW_ANONYMOUS, "true");
        Resource resource = new Resource("ns", "g", "name", "type", properties);
        AuthResult<?> missingConfigResult =
            authPluginService.validateIdentity(new IdentityContext(), resource);
        
        assertFalse(missingConfigResult.isSuccess());
        assertEquals(HttpStatus.UNAUTHORIZED.value(), missingConfigResult.getErrorCode());
    }
    
    @Test
    void testValidateIdentityLoadsAuthConfigsForAnonymousAccess() throws Exception {
        when(authenticationManager.authenticate(any(), any()))
            .thenThrow(new AccessException("no credentials"));
        when(authConfigs.isAiAnonymousEnabled()).thenReturn(true);
        Field acField = NacosAuthPluginService.class.getDeclaredField("authConfigs");
        acField.setAccessible(true);
        acField.set(authPluginService, null);
        Properties properties = new Properties();
        properties.setProperty(AuthConstants.TAG_ALLOW_ANONYMOUS, "true");
        Resource resource = new Resource("ns", "g", "name", "type", properties);
        
        try (MockedStatic<ApplicationUtils> applicationUtils = mockStatic(ApplicationUtils.class)) {
            applicationUtils.when(() -> ApplicationUtils.getBean(AuthConfigs.class))
                .thenReturn(authConfigs);
            
            AuthResult<?> result =
                authPluginService.validateIdentity(new IdentityContext(), resource);
            
            assertTrue(result.isSuccess());
        }
    }
    
    @Test
    void testValidateAuthoritySuccessAndFailure() throws AccessException {
        IdentityContext identityContext = new IdentityContext();
        NacosUser user = new NacosUser("nacos");
        identityContext.setParameter(AuthConstants.NACOS_USER_KEY, user);
        Permission permission = new Permission();
        
        AuthResult<?> success = authPluginService.validateAuthority(identityContext, permission);
        
        assertTrue(success.isSuccess());
        assertEquals(user, success.getData());
        verify(authenticationManager).authorize(permission, user);
        
        doThrow(new AccessException("forbidden")).when(authenticationManager)
            .authorize(permission, user);
        
        AuthResult<?> failure = authPluginService.validateAuthority(identityContext, permission);
        
        assertFalse(failure.isSuccess());
        assertEquals(HttpStatus.FORBIDDEN.value(), failure.getErrorCode());
    }
    
    @Test
    @SuppressWarnings("unchecked")
    void testLoginEnabledAndAdminRequest() {
        Map<String, NacosAuthConfig> cachedConfigMap =
            (Map<String, NacosAuthConfig>) ReflectionTestUtils.getField(
                NacosAuthConfigHolder.getInstance(), "nacosAuthConfigMap");
        NacosAuthConfig consoleConfig = mock(NacosAuthConfig.class);
        when(consoleConfig.isAuthEnabled()).thenReturn(true);
        try {
            Map<String, NacosAuthConfig> map = new HashMap<>();
            map.put(ApiType.CONSOLE_API.name(), consoleConfig);
            ReflectionTestUtils.setField(NacosAuthConfigHolder.getInstance(), "nacosAuthConfigMap",
                map);
            
            assertTrue(authPluginService.isLoginEnabled());
            
            try (MockedStatic<ApplicationUtils> applicationUtils =
                mockStatic(ApplicationUtils.class)) {
                applicationUtils.when(() -> ApplicationUtils.getBean(IAuthenticationManager.class))
                    .thenReturn(authenticationManager);
                when(authenticationManager.hasGlobalAdminRole()).thenReturn(false, true);
                
                assertTrue(authPluginService.isAdminRequest());
                assertFalse(authPluginService.isAdminRequest());
            }
        } finally {
            ReflectionTestUtils.setField(NacosAuthConfigHolder.getInstance(), "nacosAuthConfigMap",
                cachedConfigMap);
        }
    }
}
