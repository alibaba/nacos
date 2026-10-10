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
import com.alibaba.nacos.ai.service.VisibilityHelper;
import com.alibaba.nacos.api.common.ApiType;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
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
import com.alibaba.nacos.plugin.visibility.constant.VisibilityConstants;
import com.alibaba.nacos.plugin.visibility.spi.ValidationResult;
import com.alibaba.nacos.plugin.visibility.spi.VisibilityService;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Tests the real Nacos authentication and visibility bridge, including bypass prevention.
 */
class HubSkillPermissionGuardTest {
    
    private MockedStatic<RequestContextHolder> requests;
    
    private MockedStatic<NacosAuthConfigHolder> configs;
    
    private MockedStatic<VisibilityHelper> visibility;
    
    private MockedStatic<AuthPluginManager> plugins;
    
    private VisibilityService visibilityService;
    
    private NacosAuthConfig config;
    
    private AuthContext auth;
    
    private IdentityContext identity;
    
    private NacosAuthConfigHolder configHolder;
    
    private AuthPluginManager pluginManager;
    
    private final HubSkillPermissionGuard guard = new HubSkillPermissionGuard();
    
    @BeforeEach
    void setUp() {
        requests = mockStatic(RequestContextHolder.class);
        configs = mockStatic(NacosAuthConfigHolder.class);
        visibility = mockStatic(VisibilityHelper.class);
        plugins = mockStatic(AuthPluginManager.class);
        RequestContext request = mock(RequestContext.class);
        auth = new AuthContext();
        identity = new IdentityContext();
        identity.setParameter(Constants.Identity.IDENTITY_ID, "alice");
        auth.setApiType(ApiType.CONSOLE_API.name());
        auth.setIdentityContext(identity);
        auth.setAuthResult(AuthResult.successResult());
        when(request.getAuthContext()).thenReturn(auth);
        requests.when(RequestContextHolder::getContext).thenReturn(request);
        configHolder = mock(NacosAuthConfigHolder.class);
        config = mock(NacosAuthConfig.class);
        when(config.isAuthEnabled()).thenReturn(true);
        when(config.getNacosAuthSystemType()).thenReturn("nacos");
        when(configHolder.getNacosAuthConfigByScope(ApiType.CONSOLE_API.name()))
            .thenReturn(config);
        configs.when(NacosAuthConfigHolder::getInstance).thenReturn(configHolder);
        visibilityService = mock(VisibilityService.class);
        visibility.when(VisibilityHelper::findVisibilityService)
            .thenReturn(Optional.of(visibilityService));
        pluginManager = mock(AuthPluginManager.class);
        plugins.when(AuthPluginManager::getInstance).thenReturn(pluginManager);
    }
    
    @AfterEach
    void tearDown() {
        plugins.close();
        visibility.close();
        configs.close();
        requests.close();
    }
    
    @Test
    void acceptsAuthenticatedSourceWriteWithVisibilityPlugin() throws Exception {
        AiResource source = sourceSkill();
        when(visibilityService.validateVisibility("alice", VisibilityConstants.ACTION_WRITE,
            ApiType.CONSOLE_API.name(), source)).thenReturn(ValidationResult.allow());
        guard.requireSourceWrite(source);
        verify(visibilityService).validateVisibility("alice", VisibilityConstants.ACTION_WRITE,
            ApiType.CONSOLE_API.name(), source);
    }
    
    @Test
    void sourceReadFollowsExistingVisibilityPermission() throws Exception {
        AiResource source = sourceSkill();
        when(visibilityService.validateVisibility("alice", VisibilityConstants.ACTION_READ,
            ApiType.CONSOLE_API.name(), source)).thenReturn(ValidationResult.allow());
        guard.requireSourceRead(source);
        verify(visibilityService).validateVisibility("alice", VisibilityConstants.ACTION_READ,
            ApiType.CONSOLE_API.name(), source);
    }
    
    @Test
    void deniesReadWithoutVisibilityReadGrant() {
        NacosApiException ex = assertThrows(NacosApiException.class,
            () -> guard.requireSourceRead(sourceSkill()));
        assertEquals(NacosException.NO_RIGHT, ex.getErrCode());
    }
    
    @Test
    void deniesSourceWriteWhenAuthScopeDisabled() {
        when(config.isAuthEnabled()).thenReturn(false);
        NacosApiException ex = assertThrows(NacosApiException.class,
            () -> guard.requireSourceWrite(sourceSkill()));
        assertEquals(NacosException.NO_RIGHT, ex.getErrCode());
    }
    
    @Test
    void deniesMissingValidatedAuthResult() {
        auth.setAuthResult(null);
        NacosApiException ex = assertThrows(NacosApiException.class,
            () -> guard.requireSourceWrite(sourceSkill()));
        assertEquals(NacosException.NO_RIGHT, ex.getErrCode());
    }
    
    @Test
    void deniesAnonymousIdentityEvenWhenAuthResultIsSuccessful() {
        identity.setParameter(Constants.Identity.IDENTITY_ID, "anonymous");
        NacosApiException ex = assertThrows(NacosApiException.class,
            () -> guard.requireSourceWrite(sourceSkill()));
        assertEquals(NacosException.NO_RIGHT, ex.getErrCode());
    }
    
    @Test
    void deniesServerIdentityBypass() {
        identity.setParameter(Constants.Identity.SERVER_IDENTITY, Boolean.TRUE);
        NacosApiException ex = assertThrows(NacosApiException.class,
            () -> guard.requireSourceWrite(sourceSkill()));
        assertEquals(NacosException.NO_RIGHT, ex.getErrCode());
    }
    
    @Test
    void rejectsClientApiForHubMutation() {
        auth.setApiType(ApiType.OPEN_API.name());
        NacosApiException ex = assertThrows(NacosApiException.class,
            () -> guard.requireSourceWrite(sourceSkill()));
        assertEquals(NacosException.NO_RIGHT, ex.getErrCode());
    }
    
    @Test
    void deniesMissingVisibilityPluginInsteadOfFailingOpen() {
        visibility.when(VisibilityHelper::findVisibilityService).thenReturn(Optional.empty());
        NacosApiException ex = assertThrows(NacosApiException.class,
            () -> guard.requireSourceWrite(sourceSkill()));
        assertEquals(NacosException.NO_RIGHT, ex.getErrCode());
    }
    
    @Test
    void rejectsHubMetadataAsSourceSkill() {
        AiResource hub = sourceSkill();
        hub.setNamespaceId("__nacos_ai_hub__");
        NacosApiException ex = assertThrows(NacosApiException.class,
            () -> guard.requireSourceWrite(hub));
        assertEquals(NacosException.NO_RIGHT, ex.getErrCode());
    }
    
    @Test
    void forceUnshareUsesSpecificGovernanceRbacPermission() throws Exception {
        AuthPluginService plugin = mock(AuthPluginService.class);
        when(pluginManager.findAuthServiceSpiImpl("nacos")).thenReturn(Optional.of(plugin));
        when(plugin.validateAuthority(eq(identity), any(Permission.class)))
            .thenReturn(AuthResult.successResult());
        
        guard.requireHubManage();
        
        ArgumentCaptor<Permission> permission = ArgumentCaptor.forClass(Permission.class);
        org.mockito.Mockito.verify(plugin).validateAuthority(eq(identity), permission.capture());
        assertEquals("ai-hub/manage", HubSkillPermissionGuard.HUB_MANAGE_RESOURCE);
        assertEquals(HubSkillPermissionGuard.HUB_MANAGE_RESOURCE,
            permission.getValue().getResource().getName());
        assertEquals(SignType.SPECIFIED, permission.getValue().getResource().getType());
        assertEquals("w", permission.getValue().getAction());
    }
    
    @Test
    void forceUnshareDeniesUnprivilegedCaller() throws Exception {
        AuthPluginService plugin = mock(AuthPluginService.class);
        when(pluginManager.findAuthServiceSpiImpl("nacos")).thenReturn(Optional.of(plugin));
        when(plugin.validateAuthority(eq(identity), any(Permission.class)))
            .thenReturn(AuthResult.failureResult(403, "no grant"));
        
        NacosApiException ex = assertThrows(NacosApiException.class, guard::requireHubManage);
        assertEquals(NacosException.NO_RIGHT, ex.getErrCode());
    }
    
    @Test
    void forceUnshareDeniesMissingAuthPlugin() {
        when(pluginManager.findAuthServiceSpiImpl("nacos")).thenReturn(Optional.empty());
        NacosApiException ex = assertThrows(NacosApiException.class, guard::requireHubManage);
        assertEquals(NacosException.NO_RIGHT, ex.getErrCode());
    }
    
    @Test
    void forceUnshareDeniesDisabledAuthWithoutConsultingAuthPlugin() {
        when(config.isAuthEnabled()).thenReturn(false);
        NacosApiException ex = assertThrows(NacosApiException.class, guard::requireHubManage);
        assertEquals(NacosException.NO_RIGHT, ex.getErrCode());
        verifyNoInteractions(pluginManager);
    }
    
    @ParameterizedTest
    @ValueSource(strings = {"r", "w"})
    void pluginDisappearingAfterLookupMustNotBypassDenial(String action) {
        AiResource source = sourceSkill();
        visibility.when(VisibilityHelper::findVisibilityService)
            .thenReturn(Optional.of(visibilityService), Optional.empty());
        when(visibilityService.validateVisibility("alice", action, ApiType.CONSOLE_API.name(),
            source))
            .thenReturn(ValidationResult.deny("No source grant"));
        NacosApiException ex = assertThrows(NacosApiException.class,
            () -> requireVisibility(source, action));
        assertEquals(NacosException.NO_RIGHT, ex.getErrCode());
        visibility.verify(VisibilityHelper::findVisibilityService, times(1));
        verify(visibilityService).validateVisibility("alice", action, ApiType.CONSOLE_API.name(),
            source);
        visibility.verifyNoMoreInteractions();
    }
    
    @ParameterizedTest
    @ValueSource(strings = {"r", "w"})
    void nullVisibilityResultMustDeny(String action) {
        NacosApiException ex = assertThrows(NacosApiException.class,
            () -> requireVisibility(sourceSkill(), action));
        assertEquals(NacosException.NO_RIGHT, ex.getErrCode());
    }
    
    @ParameterizedTest
    @ValueSource(strings = {"r", "w"})
    void visibilityFailureMustDeny(String action) {
        AiResource source = sourceSkill();
        when(visibilityService.validateVisibility("alice", action, ApiType.CONSOLE_API.name(),
            source))
            .thenThrow(new IllegalStateException("Plugin unavailable"));
        NacosApiException ex = assertThrows(NacosApiException.class,
            () -> requireVisibility(source, action));
        assertEquals(NacosException.NO_RIGHT, ex.getErrCode());
    }
    
    @Test
    void visibilityUsesAuthenticatedPrincipalAndApiTypeSnapshot() throws Exception {
        AiResource source = sourceSkill();
        visibility.when(VisibilityHelper::findVisibilityService).thenAnswer(invocation -> {
            identity.setParameter(Constants.Identity.IDENTITY_ID, "bob");
            auth.setApiType(ApiType.ADMIN_API.name());
            return Optional.of(visibilityService);
        });
        when(visibilityService.validateVisibility("alice", VisibilityConstants.ACTION_WRITE,
            ApiType.CONSOLE_API.name(), source)).thenReturn(ValidationResult.allow());
        guard.requireSourceWrite(source);
        verify(visibilityService).validateVisibility("alice", VisibilityConstants.ACTION_WRITE,
            ApiType.CONSOLE_API.name(), source);
    }
    
    @Test
    void forceUnshareRejectsAnonymousBeforeConsultingPlugin() {
        identity.setParameter(Constants.Identity.IDENTITY_ID, "anonymous");
        assertThrows(NacosApiException.class, guard::requireHubManage);
        verifyNoInteractions(pluginManager);
    }
    
    private void requireVisibility(AiResource source, String action) throws NacosException {
        if (VisibilityConstants.ACTION_READ.equals(action)) {
            guard.requireSourceRead(source);
        } else {
            guard.requireSourceWrite(source);
        }
    }
    
    private AiResource sourceSkill() {
        AiResource source = new AiResource();
        source.setId(101L);
        source.setNamespaceId("team-a");
        source.setName("find-skills");
        source.setType("skill");
        assertTrue(source.getId() > 0);
        return source;
    }
}
