/*
 * Copyright 1999-2026 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.plugin.auth.impl.oidc;

import com.alibaba.nacos.plugin.auth.api.AuthResult;
import com.alibaba.nacos.plugin.auth.api.IdentityContext;
import com.alibaba.nacos.plugin.auth.api.Permission;
import com.alibaba.nacos.plugin.auth.api.Resource;
import com.alibaba.nacos.plugin.auth.constant.Constants;
import com.alibaba.nacos.plugin.auth.exception.AccessException;
import com.alibaba.nacos.plugin.auth.impl.oidc.authenticate.OidcAuthenticationManager;
import com.alibaba.nacos.plugin.auth.impl.oidc.config.OidcAuthConfig;
import com.alibaba.nacos.plugin.auth.impl.oidc.identity.OidcUserMapper.OidcUser;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OidcProviderTest {
    
    private ConfigurableEnvironment originalEnvironment;
    
    private OidcAuthenticationManager authManager;
    
    private OidcAuthConfig config;
    
    private IdentityContext identityContext;
    
    private OidcUser user;
    
    @BeforeEach
    void setUp() {
        originalEnvironment = EnvUtil.getEnvironment();
        EnvUtil.setEnvironment(new MockEnvironment());
        ReflectionTestUtils.setField(OidcAuthConfig.class, "instance", null);
        ReflectionTestUtils.setField(OidcAuthenticationManager.class, "instance", null);
        authManager = mock(OidcAuthenticationManager.class);
        config = mock(OidcAuthConfig.class);
        identityContext = new IdentityContext();
        user = new OidcUser();
        user.setUsername("nacos");
        user.setToken("token");
    }
    
    @AfterEach
    void tearDown() {
        EnvUtil.setEnvironment(originalEnvironment);
        ReflectionTestUtils.setField(OidcAuthConfig.class, "instance", null);
        ReflectionTestUtils.setField(OidcAuthenticationManager.class, "instance", null);
    }
    
    @Test
    void testIdentityProviderStoresAuthenticatedUser() throws AccessException {
        OidcIdentityProvider provider = newIdentityProvider();
        when(authManager.authenticate(identityContext)).thenReturn(user);
        
        AuthResult<?> result = provider.validateIdentity(identityContext, Resource.EMPTY_RESOURCE);
        
        assertTrue(result.isSuccess());
        assertSame(user, result.getData());
        assertEquals("nacos", identityContext.getParameter(Constants.Identity.IDENTITY_ID));
        verify(authManager).setUserInContext(identityContext, user);
    }
    
    @Test
    void testIdentityProviderReturnsUnauthorizedForAccessException() throws AccessException {
        OidcIdentityProvider provider = newIdentityProvider();
        when(authManager.authenticate(identityContext)).thenThrow(new AccessException("bad token"));
        
        AuthResult<?> result = provider.validateIdentity(identityContext, Resource.EMPTY_RESOURCE);
        
        assertFalse(result.isSuccess());
        assertEquals(401, result.getErrorCode());
        assertEquals("bad token", result.getErrorMessage());
    }
    
    @Test
    void testIdentityProviderReturnsUnauthorizedForUnexpectedException() throws AccessException {
        OidcIdentityProvider provider = newIdentityProvider();
        when(authManager.authenticate(identityContext))
            .thenThrow(new IllegalStateException("boom"));
        
        AuthResult<?> result = provider.validateIdentity(identityContext, Resource.EMPTY_RESOURCE);
        
        assertFalse(result.isSuccess());
        assertEquals(401, result.getErrorCode());
        assertEquals("Authentication failed", result.getErrorMessage());
    }
    
    @Test
    void testIdentityProviderInitializesBeforeTokenFailure() {
        OidcIdentityProvider provider = new OidcIdentityProvider();
        
        AuthResult<?> result = provider.validateIdentity(new IdentityContext(),
            Resource.EMPTY_RESOURCE);
        
        assertFalse(result.isSuccess());
        assertEquals(401, result.getErrorCode());
    }
    
    @Test
    void testAuthorityProviderRejectsMissingUser() {
        OidcAuthorityProvider provider = newAuthorityProvider();
        
        AuthResult<?> result = provider.validateAuthority(identityContext, permission());
        
        assertFalse(result.isSuccess());
        assertEquals(403, result.getErrorCode());
        assertEquals("User not authenticated", result.getErrorMessage());
    }
    
    @Test
    void testAuthorityProviderAllowsGlobalAdmin() {
        OidcAuthorityProvider provider = newAuthorityProvider();
        when(authManager.getUserFromContext(identityContext)).thenReturn(user);
        when(authManager.isGlobalAdmin(user)).thenReturn(true);
        
        AuthResult<?> result = provider.validateAuthority(identityContext, permission());
        
        assertTrue(result.isSuccess());
        assertSame(user, result.getData());
    }
    
    @Test
    void testAuthorityProviderAllowsPermittedUser() {
        Permission permission = permission();
        OidcAuthorityProvider provider = newAuthorityProvider();
        when(authManager.getUserFromContext(identityContext)).thenReturn(user);
        when(authManager.hasPermission(user, permission)).thenReturn(true);
        
        AuthResult<?> result = provider.validateAuthority(identityContext, permission);
        
        assertTrue(result.isSuccess());
        assertSame(user, result.getData());
    }
    
    @Test
    void testAuthorityProviderDeniesWhenPermissionRejected() {
        Permission permission = permission();
        OidcAuthorityProvider provider = newAuthorityProvider();
        when(authManager.getUserFromContext(identityContext)).thenReturn(user);
        when(authManager.hasPermission(user, permission)).thenReturn(false);
        
        AuthResult<?> result = provider.validateAuthority(identityContext, permission);
        
        assertFalse(result.isSuccess());
        assertEquals(403, result.getErrorCode());
        assertEquals("Access denied", result.getErrorMessage());
    }
    
    @Test
    void testAuthorityProviderReturnsFailureForException() {
        OidcAuthorityProvider provider = newAuthorityProvider();
        when(authManager.getUserFromContext(identityContext))
            .thenThrow(new RuntimeException("boom"));
        
        AuthResult<?> result = provider.validateAuthority(identityContext, permission());
        
        assertFalse(result.isSuccess());
        assertEquals(403, result.getErrorCode());
        assertEquals("Authorization failed", result.getErrorMessage());
    }
    
    @Test
    void testAuthorityProviderInitializesBeforeMissingUserFailure() {
        OidcAuthorityProvider provider = new OidcAuthorityProvider();
        
        AuthResult<?> result = provider.validateAuthority(new IdentityContext(), permission());
        
        assertFalse(result.isSuccess());
        assertEquals(403, result.getErrorCode());
    }
    
    private OidcIdentityProvider newIdentityProvider() {
        OidcIdentityProvider provider = new OidcIdentityProvider();
        ReflectionTestUtils.setField(provider, "config", config);
        ReflectionTestUtils.setField(provider, "authManager", authManager);
        return provider;
    }
    
    private OidcAuthorityProvider newAuthorityProvider() {
        OidcAuthorityProvider provider = new OidcAuthorityProvider();
        ReflectionTestUtils.setField(provider, "config", config);
        ReflectionTestUtils.setField(provider, "authManager", authManager);
        return provider;
    }
    
    private Permission permission() {
        Resource resource = new Resource("public", "DEFAULT_GROUP", "data.yaml", "config", null);
        return new Permission(resource, "read");
    }
}
