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

import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.plugin.auth.api.AuthResult;
import com.alibaba.nacos.plugin.auth.api.IdentityContext;
import com.alibaba.nacos.plugin.auth.api.Resource;
import com.alibaba.nacos.plugin.auth.constant.Constants.Identity;
import com.alibaba.nacos.plugin.auth.exception.AccessException;
import com.alibaba.nacos.plugin.auth.impl.authenticate.IAuthenticationManager;
import com.alibaba.nacos.plugin.auth.impl.configuration.AuthConfigs;
import com.alibaba.nacos.plugin.auth.impl.constant.AuthConstants;
import com.alibaba.nacos.plugin.auth.impl.users.NacosUser;
import com.alibaba.nacos.sys.utils.ApplicationUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.lang.reflect.Field;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NacosAuthPluginServiceTest {
    
    @Mock
    private IAuthenticationManager authenticationManager;
    
    @Mock
    private AuthConfigs authConfigs;
    
    private NacosAuthPluginService authPluginService;
    
    private MockedStatic<ApplicationUtils> applicationUtilsMockedStatic;
    
    @BeforeEach
    void setUp() throws Exception {
        authPluginService = new NacosAuthPluginService();
        Field field = NacosAuthPluginService.class.getDeclaredField("authenticationManager");
        field.setAccessible(true);
        field.set(authPluginService, authenticationManager);
        
        applicationUtilsMockedStatic = Mockito.mockStatic(ApplicationUtils.class);
        applicationUtilsMockedStatic.when(() -> ApplicationUtils.getBean(AuthConfigs.class)).thenReturn(authConfigs);
    }
    
    @AfterEach
    void tearDown() {
        applicationUtilsMockedStatic.close();
    }
    
    @Test
    void testValidateIdentityAnonymousAllowed() throws AccessException {
        when(authenticationManager.authenticate(any(), any())).thenThrow(new AccessException("no credentials"));
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
        assertEquals(AuthConstants.ANONYMOUS_USER, identityContext.getParameter(Identity.IDENTITY_ID, ""));
    }
    
    @Test
    void testValidateIdentityNoTagDenied() throws AccessException {
        when(authenticationManager.authenticate(any(), any())).thenThrow(new AccessException("no credentials"));
        
        Resource resource = new Resource("ns", "g", "name", "type", new Properties());
        IdentityContext identityContext = new IdentityContext();
        
        AuthResult<?> result = authPluginService.validateIdentity(identityContext, resource);
        
        assertFalse(result.isSuccess());
        assertEquals(HttpStatus.UNAUTHORIZED.value(), result.getErrorCode());
        verify(authenticationManager).authenticate(any(), any());
    }
    
    @Test
    void testValidateIdentitySwitchOffDenied() throws AccessException {
        when(authenticationManager.authenticate(any(), any())).thenThrow(new AccessException("no credentials"));
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
}
