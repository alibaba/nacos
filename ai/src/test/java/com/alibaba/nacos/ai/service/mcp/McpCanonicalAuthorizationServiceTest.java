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

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.auth.config.NacosAuthConfig;
import com.alibaba.nacos.core.context.RequestContextHolder;
import com.alibaba.nacos.core.context.addition.AuthContext;
import com.alibaba.nacos.plugin.auth.api.AuthResult;
import com.alibaba.nacos.plugin.auth.api.IdentityContext;
import com.alibaba.nacos.plugin.auth.api.Permission;
import com.alibaba.nacos.plugin.auth.api.Resource;
import com.alibaba.nacos.plugin.auth.constant.ActionTypes;
import com.alibaba.nacos.plugin.auth.constant.SignType;
import com.alibaba.nacos.plugin.auth.exception.AccessException;
import com.alibaba.nacos.plugin.auth.spi.server.AuthPluginService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static com.alibaba.nacos.plugin.auth.constant.Constants.Resource.AI_TYPE;
import static com.alibaba.nacos.plugin.auth.constant.Constants.Resource.AI_TYPE_MCP;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class McpCanonicalAuthorizationServiceTest {
    
    private static final String AUTH_SCOPE = "server";
    
    private static final String AUTH_SYSTEM = "nacos";
    
    private static final String NAMESPACE_ID = "public";
    
    private static final String MCP_NAME = "demo-mcp";
    
    private McpCanonicalAuthorizationService authorizationService;
    
    private NacosAuthConfig authConfig;
    
    private AuthPluginService authPluginService;
    
    private IdentityContext identityContext;
    
    private Optional<AuthPluginService> authPlugin;
    
    private int pluginLookupCount;
    
    private String requestedAuthSystem;
    
    @BeforeEach
    void setUp() {
        authConfig = mock(NacosAuthConfig.class);
        authPluginService = mock(AuthPluginService.class);
        identityContext = new IdentityContext();
        authPlugin = Optional.empty();
        authorizationService = new McpCanonicalAuthorizationService(scope -> authConfig,
            authSystem -> {
                pluginLookupCount++;
                requestedAuthSystem = authSystem;
                return authPlugin;
            });
        AuthContext authContext = RequestContextHolder.getContext().getAuthContext();
        authContext.setApiType(AUTH_SCOPE);
        authContext.setIdentityContext(identityContext);
    }
    
    @AfterEach
    void tearDown() {
        RequestContextHolder.removeContext();
    }
    
    @Test
    void testProductionConstructor() {
        assertNotNull(new McpCanonicalAuthorizationService());
    }
    
    @Test
    void testMissingAuthConfigReturnsWithoutPluginLookup() throws Exception {
        authorizationService = new McpCanonicalAuthorizationService(scope -> null,
            authSystem -> {
                pluginLookupCount++;
                return authPlugin;
            });
        
        authorizationService.authorizeRead(NAMESPACE_ID, MCP_NAME);
        
        assertEquals(0, pluginLookupCount);
        assertNull(RequestContextHolder.getContext().getAuthContext().getResource());
    }
    
    @Test
    void testDisabledAuthReturnsWithoutPluginLookup() throws Exception {
        when(authConfig.isAuthEnabled()).thenReturn(false);
        
        authorizationService.authorizeRead(NAMESPACE_ID, MCP_NAME);
        
        assertEquals(0, pluginLookupCount);
    }
    
    @Test
    void testMissingOrDisabledPluginReturnsWithoutAuthorityValidation() throws Exception {
        enableAuth();
        authorizationService.authorizeRead(NAMESPACE_ID, MCP_NAME);
        
        authPlugin = Optional.of(authPluginService);
        when(authPluginService.enableAuth(ActionTypes.READ, SignType.AI)).thenReturn(false);
        authorizationService.authorizeRead(NAMESPACE_ID, MCP_NAME);
        
        assertEquals(2, pluginLookupCount);
        assertEquals(AUTH_SYSTEM, requestedAuthSystem);
        verify(authPluginService, never()).validateAuthority(any(), any());
    }
    
    @Test
    void testMissingIdentityIsDenied() throws Exception {
        enableAuthWithPlugin();
        RequestContextHolder.getContext().getAuthContext().setIdentityContext(null);
        
        NacosApiException exception = assertThrows(NacosApiException.class,
            () -> authorizationService.authorizeRead(NAMESPACE_ID, MCP_NAME));
        
        assertEquals(NacosException.NO_RIGHT, exception.getErrCode());
        verify(authPluginService, never()).validateAuthority(any(), any());
    }
    
    @Test
    void testSuccessfulAuthorizationUsesCanonicalMcpResource() throws Exception {
        enableAuthWithPlugin();
        when(authPluginService.validateAuthority(any(), any()))
            .thenReturn(AuthResult.successResult());
        
        authorizationService.authorizeRead(NAMESPACE_ID, MCP_NAME);
        
        ArgumentCaptor<Permission> permissionCaptor = ArgumentCaptor.forClass(Permission.class);
        verify(authPluginService).validateAuthority(any(), permissionCaptor.capture());
        Permission permission = permissionCaptor.getValue();
        Resource resource = permission.getResource();
        assertEquals(ActionTypes.READ.toString(), permission.getAction());
        assertEquals(NAMESPACE_ID, resource.getNamespaceId());
        assertEquals(com.alibaba.nacos.api.common.Constants.DEFAULT_GROUP, resource.getGroup());
        assertEquals(MCP_NAME, resource.getName());
        assertEquals(SignType.AI, resource.getType());
        assertEquals(AI_TYPE_MCP, resource.getProperties().getProperty(AI_TYPE));
        assertSame(resource, RequestContextHolder.getContext().getAuthContext().getResource());
    }
    
    @Test
    void testFailedAuthorizationResultIsDenied() throws Exception {
        enableAuthWithPlugin();
        when(authPluginService.validateAuthority(any(), any()))
            .thenReturn(AuthResult.failureResult(403, "denied"));
        
        NacosApiException exception = assertThrows(NacosApiException.class,
            () -> authorizationService.authorizeRead(NAMESPACE_ID, MCP_NAME));
        
        assertEquals(NacosException.NO_RIGHT, exception.getErrCode());
        assertEquals("Code: 403, Message: denied.", exception.getErrMsg());
        assertNull(RequestContextHolder.getContext().getAuthContext().getResource());
    }
    
    @Test
    void testPluginAccessExceptionIsDenied() throws Exception {
        enableAuthWithPlugin();
        doThrow(new AccessException("plugin denied")).when(authPluginService)
            .validateAuthority(any(), any());
        
        NacosApiException exception = assertThrows(NacosApiException.class,
            () -> authorizationService.authorizeRead(NAMESPACE_ID, MCP_NAME));
        
        assertEquals(NacosException.NO_RIGHT, exception.getErrCode());
        assertEquals("plugin denied", exception.getErrMsg());
        assertNull(RequestContextHolder.getContext().getAuthContext().getResource());
    }
    
    private void enableAuth() {
        when(authConfig.isAuthEnabled()).thenReturn(true);
        when(authConfig.getNacosAuthSystemType()).thenReturn(AUTH_SYSTEM);
    }
    
    private void enableAuthWithPlugin() {
        enableAuth();
        authPlugin = Optional.of(authPluginService);
        when(authPluginService.enableAuth(ActionTypes.READ, SignType.AI)).thenReturn(true);
    }
}
