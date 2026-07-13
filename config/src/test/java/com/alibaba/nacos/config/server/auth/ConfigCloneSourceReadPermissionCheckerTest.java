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

package com.alibaba.nacos.config.server.auth;

import com.alibaba.nacos.api.common.ApiType;
import com.alibaba.nacos.auth.config.NacosAuthConfig;
import com.alibaba.nacos.core.context.RequestContextHolder;
import com.alibaba.nacos.plugin.auth.api.AuthResult;
import com.alibaba.nacos.plugin.auth.api.IdentityContext;
import com.alibaba.nacos.plugin.auth.api.Permission;
import com.alibaba.nacos.plugin.auth.api.Resource;
import com.alibaba.nacos.plugin.auth.constant.ActionTypes;
import com.alibaba.nacos.plugin.auth.constant.Constants;
import com.alibaba.nacos.plugin.auth.constant.SignType;
import com.alibaba.nacos.plugin.auth.exception.AccessException;
import com.alibaba.nacos.plugin.auth.spi.server.AuthPluginService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConfigCloneSourceReadPermissionCheckerTest {
    
    private static final String TEST_API_TYPE = ApiType.ADMIN_API.name();
    
    private static final String TEST_AUTH_TYPE = "testAuth";
    
    private ConfigCloneSourceReadPermissionChecker checker;
    
    @AfterEach
    void tearDown() {
        RequestContextHolder.removeContext();
    }
    
    @Test
    void testCheckSourceReadPermissionSkipsWhenAuthDisabled() throws AccessException {
        setUserIdentity(new IdentityContext());
        try (MockedAuthContext mockedAuth = mockAuthContext(false, true,
            AuthResult.successResult())) {
            
            checker.checkSourceReadPermission("testNamespace");
            
            verify(mockedAuth.authPluginService, never()).enableAuth(any(ActionTypes.class),
                any(String.class));
            verify(mockedAuth.authPluginService, never()).validateAuthority(
                any(IdentityContext.class), any(Permission.class));
        }
    }
    
    @Test
    void testCheckSourceReadPermissionSkipsWhenReadAuthDisabled() throws AccessException {
        setUserIdentity(new IdentityContext());
        try (MockedAuthContext mockedAuth = mockAuthContext(true, false,
            AuthResult.successResult())) {
            
            checker.checkSourceReadPermission("testNamespace");
            
            verify(mockedAuth.authPluginService).enableAuth(ActionTypes.READ, SignType.CONFIG);
            verify(mockedAuth.authPluginService, never()).validateAuthority(
                any(IdentityContext.class), any(Permission.class));
        }
    }
    
    @Test
    void testCheckSourceReadPermissionSkipsWhenIdentityContextAbsent() throws AccessException {
        RequestContextHolder.getContext().getAuthContext().setApiType(TEST_API_TYPE);
        try (MockedAuthContext mockedAuth = mockAuthContext(true, true,
            AuthResult.successResult())) {
            
            checker.checkSourceReadPermission("testNamespace");
            
            verify(mockedAuth.authPluginService, never()).validateAuthority(
                any(IdentityContext.class), any(Permission.class));
        }
    }
    
    @Test
    void testCheckSourceReadPermissionSkipsWhenServerIdentity() throws AccessException {
        IdentityContext identityContext = new IdentityContext();
        identityContext.setParameter(Constants.Identity.SERVER_IDENTITY, Boolean.TRUE);
        setUserIdentity(identityContext);
        try (MockedAuthContext mockedAuth = mockAuthContext(true, true,
            AuthResult.successResult())) {
            
            checker.checkSourceReadPermission("testNamespace");
            
            verify(mockedAuth.authPluginService, never()).validateAuthority(
                any(IdentityContext.class), any(Permission.class));
        }
    }
    
    @Test
    void testCheckSourceReadPermissionBuildsNamespaceReadPermission() throws AccessException {
        IdentityContext identityContext = new IdentityContext();
        setUserIdentity(identityContext);
        try (MockedAuthContext mockedAuth = mockAuthContext(true, true,
            AuthResult.successResult())) {
            
            checker.checkSourceReadPermission("testNamespace");
            
            ArgumentCaptor<IdentityContext> identityCaptor =
                ArgumentCaptor.forClass(IdentityContext.class);
            ArgumentCaptor<Permission> permissionCaptor =
                ArgumentCaptor.forClass(Permission.class);
            verify(mockedAuth.authPluginService).validateAuthority(identityCaptor.capture(),
                permissionCaptor.capture());
            assertSame(identityContext, identityCaptor.getValue());
            Permission permission = permissionCaptor.getValue();
            assertNotNull(permission);
            assertEquals(ActionTypes.READ.toString(), permission.getAction());
            Resource resource = permission.getResource();
            assertEquals("testNamespace", resource.getNamespaceId());
            assertEquals("", resource.getGroup());
            assertEquals("", resource.getName());
            assertEquals(SignType.CONFIG, resource.getType());
            assertEquals(ActionTypes.READ.toString(),
                resource.getProperties().get(Constants.Resource.ACTION));
        }
    }
    
    @Test
    void testCheckSourceReadPermissionThrowsAccessExceptionWhenAuthFailed()
        throws AccessException {
        setUserIdentity(new IdentityContext());
        try (MockedAuthContext ignored = mockAuthContext(true, true,
            AuthResult.failureResult(403, "authorization failed"))) {
            
            AccessException actual = assertThrows(AccessException.class,
                () -> checker.checkSourceReadPermission("testNamespace"));
            
            assertTrue(actual.getErrMsg().contains("authorization failed"));
        }
    }
    
    private void setUserIdentity(IdentityContext identityContext) {
        RequestContextHolder.getContext().getAuthContext().setApiType(TEST_API_TYPE);
        RequestContextHolder.getContext().getAuthContext().setIdentityContext(identityContext);
    }
    
    private MockedAuthContext mockAuthContext(boolean authEnabled, boolean readAuthEnabled,
        AuthResult<?> validateResult)
        throws AccessException {
        NacosAuthConfig authConfig = mock(NacosAuthConfig.class);
        AuthPluginService authPluginService = mock(AuthPluginService.class);
        when(authConfig.isAuthEnabled()).thenReturn(authEnabled);
        when(authConfig.getNacosAuthSystemType()).thenReturn(TEST_AUTH_TYPE);
        when(authPluginService.enableAuth(ActionTypes.READ, SignType.CONFIG))
            .thenReturn(readAuthEnabled);
        when(authPluginService.validateAuthority(any(IdentityContext.class),
            any(Permission.class))).thenReturn(validateResult);
        checker = new ConfigCloneSourceReadPermissionChecker(apiType -> authConfig,
            authType -> Optional.of(authPluginService));
        return new MockedAuthContext(authPluginService);
    }
    
    private static class MockedAuthContext implements AutoCloseable {
        
        private final AuthPluginService authPluginService;
        
        MockedAuthContext(AuthPluginService authPluginService) {
            this.authPluginService = authPluginService;
        }
        
        @Override
        public void close() {
        }
    }
}
