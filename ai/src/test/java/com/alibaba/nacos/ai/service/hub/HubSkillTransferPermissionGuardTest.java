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

package com.alibaba.nacos.ai.service.hub;

import com.alibaba.nacos.ai.model.AiResource;
import com.alibaba.nacos.api.common.ApiType;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.auth.config.NacosAuthConfig;
import com.alibaba.nacos.auth.config.NacosAuthConfigHolder;
import com.alibaba.nacos.core.context.RequestContext;
import com.alibaba.nacos.core.context.RequestContextHolder;
import com.alibaba.nacos.core.context.addition.AuthContext;
import com.alibaba.nacos.plugin.auth.api.AuthResult;
import com.alibaba.nacos.plugin.auth.api.IdentityContext;
import com.alibaba.nacos.plugin.auth.api.Permission;
import com.alibaba.nacos.plugin.auth.constant.Constants;
import com.alibaba.nacos.plugin.auth.constant.SignType;
import com.alibaba.nacos.plugin.auth.spi.server.AuthPluginManager;
import com.alibaba.nacos.plugin.auth.spi.server.AuthPluginService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** The target is nonexistent; never treat a guessed owner as sufficient for WRITE. */
class HubSkillTransferPermissionGuardTest {
    
    private final HubSkillReadPermissionGuard admission = mock(HubSkillReadPermissionGuard.class);
    private final HubSkillPermissionGuard sourceGuard = mock(HubSkillPermissionGuard.class);
    private final HubSkillTransferPermissionGuard guard =
        new HubSkillTransferPermissionGuard(admission, sourceGuard);
    
    private MockedStatic<RequestContextHolder> requests;
    private MockedStatic<NacosAuthConfigHolder> configs;
    private MockedStatic<AuthPluginManager> plugins;
    private AuthContext auth;
    private IdentityContext user;
    private NacosAuthConfig config;
    private AuthPluginManager pluginManager;
    private AuthPluginService plugin;
    
    @BeforeEach
    void setUp() throws Exception {
        requests = mockStatic(RequestContextHolder.class);
        configs = mockStatic(NacosAuthConfigHolder.class);
        plugins = mockStatic(AuthPluginManager.class);
        RequestContext request = mock(RequestContext.class);
        auth = new AuthContext();
        user = new IdentityContext();
        user.setParameter(Constants.Identity.IDENTITY_ID, "alice");
        auth.setApiType(ApiType.CONSOLE_API.name());
        auth.setIdentityContext(user);
        auth.setAuthResult(AuthResult.successResult());
        when(request.getAuthContext()).thenReturn(auth);
        requests.when(RequestContextHolder::getContext).thenReturn(request);
        NacosAuthConfigHolder holder = mock(NacosAuthConfigHolder.class);
        config = mock(NacosAuthConfig.class);
        when(config.isAuthEnabled()).thenReturn(true);
        when(config.getNacosAuthSystemType()).thenReturn("nacos");
        when(holder.getNacosAuthConfigByScope(ApiType.CONSOLE_API.name())).thenReturn(config);
        configs.when(NacosAuthConfigHolder::getInstance).thenReturn(holder);
        pluginManager = mock(AuthPluginManager.class);
        plugins.when(AuthPluginManager::getInstance).thenReturn(pluginManager);
        plugin = mock(AuthPluginService.class);
        when(pluginManager.findAuthServiceSpiImpl("nacos")).thenReturn(Optional.of(plugin));
    }
    
    @AfterEach
    void close() {
        plugins.close();
        configs.close();
        requests.close();
    }
    
    @Test
    void requiresExplicitTargetWriteAndUsesTargetNamespaceName() throws Exception {
        when(plugin.validateAuthority(any(), any(Permission.class)))
            .thenReturn(AuthResult.successResult());
        guard.requireTargetWrite("target-ns", "new-skill");
        ArgumentCaptor<Permission> argument = ArgumentCaptor.forClass(Permission.class);
        verify(plugin).validateAuthority(any(), argument.capture());
        assertEquals("target-ns", argument.getValue().getResource().getNamespaceId());
        assertEquals("new-skill", argument.getValue().getResource().getName());
        assertEquals(SignType.AI, argument.getValue().getResource().getType());
        assertEquals("w", argument.getValue().getAction());
    }
    
    @Test
    void noTargetWriteIs403() throws Exception {
        when(plugin.validateAuthority(any(), any(Permission.class)))
            .thenReturn(AuthResult.failureResult(403, "denied"));
        assertEquals(NacosException.NO_RIGHT, assertThrows(NacosException.class,
            () -> guard.requireTargetWrite("target-ns", "new-skill")).getErrCode());
    }
    
    @Test
    void missingPluginAndDisabledAuthFailClosed() {
        when(pluginManager.findAuthServiceSpiImpl("nacos")).thenReturn(Optional.empty());
        assertThrows(NacosException.class,
            () -> guard.requireTargetWrite("target-ns", "new-skill"));
        when(config.isAuthEnabled()).thenReturn(false);
        assertThrows(NacosException.class,
            () -> guard.requireTargetWrite("target-ns", "new-skill"));
    }
    
    @Test
    void serverIdentityMustNotGainTargetWrite() {
        user.setParameter(Constants.Identity.SERVER_IDENTITY, true);
        assertThrows(NacosException.class,
            () -> guard.requireTargetWrite("target-ns", "new-skill"));
    }
    
    @Test
    void directCopyDelegatesSourceReadButHubImportDoesNot() throws Exception {
        AiResource source = validSource();
        when(plugin.validateAuthority(any(), any(Permission.class)))
            .thenReturn(AuthResult.successResult());
        guard.requireDirectSourceRead(source);
        verify(sourceGuard).requireSourceRead(source);
    }
    
    @Test
    void publicSourceStillRequiresOrdinaryReadAuthority() throws Exception {
        AiResource source = validSource();
        source.setScope("PUBLIC");
        when(plugin.validateAuthority(any(), any(Permission.class)))
            .thenReturn(AuthResult.failureResult(403, "denied"));
        assertEquals(403, assertThrows(NacosException.class,
            () -> guard.requireDirectSourceRead(source)).getErrCode());
        ArgumentCaptor<Permission> captor = ArgumentCaptor.forClass(Permission.class);
        verify(plugin).validateAuthority(any(), captor.capture());
        assertEquals("source-ns", captor.getValue().getResource().getNamespaceId());
        assertEquals("source-skill", captor.getValue().getResource().getName());
        assertEquals("r", captor.getValue().getAction());
        verifyNoInteractions(sourceGuard);
    }
    
    @Test
    void nullAuthorityAndStringServerIdentityFailClosed() throws Exception {
        when(plugin.validateAuthority(any(), any(Permission.class))).thenReturn(null);
        assertThrows(NacosException.class,
            () -> guard.requireTargetWrite("target-ns", "new-skill"));
        user.setParameter(Constants.Identity.SERVER_IDENTITY, "true");
        assertThrows(NacosException.class,
            () -> guard.requireTargetWrite("target-ns", "new-skill"));
    }
    
    private AiResource validSource() {
        AiResource source = new AiResource();
        source.setId(123L);
        source.setNamespaceId("source-ns");
        source.setName("source-skill");
        source.setType("skill");
        return source;
    }
    
}
