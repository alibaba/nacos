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

package com.alibaba.nacos.ai.service.mcp;

import com.alibaba.nacos.auth.config.NacosAuthConfig;
import com.alibaba.nacos.core.context.RequestContextHolder;
import com.alibaba.nacos.core.context.addition.AuthContext;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class McpCanonicalAuthorizationServiceTest {
    
    private static final String NAMESPACE_ID = "team-a";
    
    private static final String MCP_NAME = "weather";
    
    private static final String MCP_ID = "4d7939c0-72ea-4ef4-b232-418d1e16b45c";
    
    @Mock
    private NacosAuthConfig authConfig;
    
    @Mock
    private AuthPluginService authPluginService;
    
    private IdentityContext identityContext;
    
    private McpCanonicalAuthorizationService service;
    
    @BeforeEach
    void setUp() {
        AuthContext authContext = RequestContextHolder.getContext().getAuthContext();
        authContext.setApiType("ADMIN_API");
        identityContext = new IdentityContext();
        authContext.setIdentityContext(identityContext);
        service = new McpCanonicalAuthorizationService(apiType -> authConfig,
            authType -> Optional.of(authPluginService));
    }
    
    @AfterEach
    void tearDown() {
        RequestContextHolder.removeContext();
    }
    
    @Test
    void canonicalOrNonIdRequestShouldNotRepeatAuthorization() throws Exception {
        service.authorizeIdOnly(NAMESPACE_ID, MCP_NAME, MCP_NAME, MCP_ID, ActionTypes.READ);
        service.authorizeIdOnly(NAMESPACE_ID, MCP_NAME, null, null, ActionTypes.READ);
        
        verifyNoInteractions(authConfig, authPluginService);
    }
    
    @Test
    void defaultConstructorShouldBeAvailable() throws Exception {
        McpCanonicalAuthorizationService defaultService =
            new McpCanonicalAuthorizationService();
        assertNotNull(defaultService);
        defaultService.authorizeIdOnly(NAMESPACE_ID, MCP_NAME, MCP_NAME, MCP_ID,
            ActionTypes.READ);
    }
    
    @Test
    void disabledOrUnavailableAuthenticationShouldBeNoop() throws Exception {
        when(authConfig.isAuthEnabled()).thenReturn(false);
        service.authorizeIdOnly(NAMESPACE_ID, MCP_NAME, null, MCP_ID, ActionTypes.READ);
        verify(authPluginService, never()).validateIdentity(any(), any());
        
        RequestContextHolder.getContext().getAuthContext().setApiType(null);
        service.authorizeIdOnly(NAMESPACE_ID, MCP_NAME, null, MCP_ID, ActionTypes.READ);
        
        McpCanonicalAuthorizationService unavailable =
            new McpCanonicalAuthorizationService(apiType -> null,
                authType -> Optional.of(authPluginService));
        RequestContextHolder.getContext().getAuthContext().setApiType("ADMIN_API");
        unavailable.authorizeIdOnly(NAMESPACE_ID, MCP_NAME, null, MCP_ID, ActionTypes.READ);
    }
    
    @Test
    void missingOrDisabledPluginShouldBeNoop() throws Exception {
        when(authConfig.isAuthEnabled()).thenReturn(true);
        when(authConfig.getNacosAuthSystemType()).thenReturn("", "mock");
        service.authorizeIdOnly(NAMESPACE_ID, MCP_NAME, null, MCP_ID, ActionTypes.READ);
        
        McpCanonicalAuthorizationService missingPlugin =
            new McpCanonicalAuthorizationService(apiType -> authConfig,
                authType -> Optional.empty());
        missingPlugin.authorizeIdOnly(NAMESPACE_ID, MCP_NAME, null, MCP_ID, ActionTypes.READ);
        
        when(authConfig.getNacosAuthSystemType()).thenReturn("mock");
        when(authPluginService.enableAuth(ActionTypes.READ, SignType.AI)).thenReturn(false);
        service.authorizeIdOnly(NAMESPACE_ID, MCP_NAME, null, MCP_ID, ActionTypes.READ);
        verify(authPluginService, never()).validateIdentity(any(), any());
    }
    
    @Test
    void absentOrServerIdentityShouldSkipCanonicalAuthorization() throws Exception {
        enableAuthentication(ActionTypes.WRITE);
        RequestContextHolder.getContext().getAuthContext().setIdentityContext(null);
        service.authorizeIdOnly(NAMESPACE_ID, MCP_NAME, null, MCP_ID, ActionTypes.WRITE);
        
        RequestContextHolder.getContext().getAuthContext().setIdentityContext(identityContext);
        identityContext.setParameter(Constants.Identity.SERVER_IDENTITY, true);
        service.authorizeIdOnly(NAMESPACE_ID, MCP_NAME, null, MCP_ID, ActionTypes.WRITE);
        
        verify(authPluginService, never()).validateIdentity(any(), any());
    }
    
    @Test
    void idOnlyRequestShouldAuthorizeExactCanonicalResource() throws Exception {
        enableAuthentication(ActionTypes.WRITE);
        AuthResult<?> identityResult = AuthResult.successResult("identity");
        AuthResult<?> authorityResult = AuthResult.successResult();
        when(authPluginService.validateIdentity(any(IdentityContext.class), any(Resource.class)))
            .thenReturn(identityResult);
        when(authPluginService.validateAuthority(any(IdentityContext.class),
            any(Permission.class))).thenReturn(authorityResult);
        
        service.authorizeIdOnly(NAMESPACE_ID, MCP_NAME, null, MCP_ID, ActionTypes.WRITE);
        
        ArgumentCaptor<Resource> resourceCaptor = ArgumentCaptor.forClass(Resource.class);
        verify(authPluginService).validateIdentity(any(IdentityContext.class),
            resourceCaptor.capture());
        Resource resource = resourceCaptor.getValue();
        assertEquals(NAMESPACE_ID, resource.getNamespaceId());
        assertEquals(com.alibaba.nacos.api.common.Constants.DEFAULT_GROUP, resource.getGroup());
        assertEquals(MCP_NAME, resource.getName());
        assertEquals(SignType.AI, resource.getType());
        assertEquals(Constants.Resource.AI_TYPE_MCP,
            resource.getProperties().getProperty(Constants.Resource.AI_TYPE));
        assertEquals(ActionTypes.WRITE.toString(),
            resource.getProperties().getProperty(Constants.Resource.ACTION));
        ArgumentCaptor<Permission> permissionCaptor = ArgumentCaptor.forClass(Permission.class);
        verify(authPluginService).validateAuthority(any(IdentityContext.class),
            permissionCaptor.capture());
        assertSame(resource, permissionCaptor.getValue().getResource());
        assertEquals(ActionTypes.WRITE.toString(), permissionCaptor.getValue().getAction());
        AuthContext authContext = RequestContextHolder.getContext().getAuthContext();
        assertSame(resource, authContext.getResource());
        assertSame(authorityResult, authContext.getAuthResult());
    }
    
    @Test
    void identityFailureShouldDenyBeforeAuthorityValidation() throws Exception {
        enableAuthentication(ActionTypes.READ);
        when(authPluginService.validateIdentity(any(IdentityContext.class), any(Resource.class)))
            .thenReturn(AuthResult.failureResult(401, "invalid identity"));
        
        assertThrows(AccessException.class,
            () -> service.authorizeIdOnly(NAMESPACE_ID, MCP_NAME, null, MCP_ID,
                ActionTypes.READ));
        verify(authPluginService, never()).validateAuthority(any(), any());
    }
    
    @Test
    void authorityFailureShouldDenyCanonicalResource() throws Exception {
        enableAuthentication(ActionTypes.WRITE);
        when(authPluginService.validateIdentity(any(IdentityContext.class), any(Resource.class)))
            .thenReturn(AuthResult.successResult());
        when(authPluginService.validateAuthority(any(IdentityContext.class),
            any(Permission.class))).thenReturn(AuthResult.failureResult(403, "denied"));
        
        assertThrows(AccessException.class,
            () -> service.authorizeIdOnly(NAMESPACE_ID, MCP_NAME, null, MCP_ID,
                ActionTypes.WRITE));
    }
    
    private void enableAuthentication(ActionTypes action) {
        when(authConfig.isAuthEnabled()).thenReturn(true);
        when(authConfig.getNacosAuthSystemType()).thenReturn("mock");
        when(authPluginService.enableAuth(action, SignType.AI)).thenReturn(true);
    }
}
