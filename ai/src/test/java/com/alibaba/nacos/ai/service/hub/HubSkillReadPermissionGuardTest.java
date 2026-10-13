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
import com.alibaba.nacos.ai.controller.SkillAdminController;
import com.alibaba.nacos.ai.form.skills.admin.SkillForm;
import com.alibaba.nacos.ai.service.VisibilityHelper;
import com.alibaba.nacos.api.common.ApiType;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.auth.config.NacosAuthConfig;
import com.alibaba.nacos.auth.config.NacosAuthConfigHolder;
import com.alibaba.nacos.auth.annotation.Secured;
import com.alibaba.nacos.auth.parser.http.AiHttpResourceParser;
import com.alibaba.nacos.core.context.RequestContext;
import com.alibaba.nacos.core.context.RequestContextHolder;
import com.alibaba.nacos.core.context.addition.AuthContext;
import com.alibaba.nacos.plugin.auth.api.AuthResult;
import com.alibaba.nacos.plugin.auth.api.IdentityContext;
import com.alibaba.nacos.plugin.auth.api.Permission;
import com.alibaba.nacos.plugin.auth.api.Resource;
import com.alibaba.nacos.plugin.auth.constant.Constants;
import com.alibaba.nacos.plugin.auth.exception.AccessException;
import com.alibaba.nacos.plugin.auth.spi.server.AuthPluginManager;
import com.alibaba.nacos.plugin.auth.spi.server.AuthPluginService;
import com.alibaba.nacos.plugin.visibility.constant.VisibilityConstants;
import com.alibaba.nacos.plugin.visibility.spi.ValidationResult;
import com.alibaba.nacos.plugin.visibility.spi.VisibilityService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.ArgumentCaptor;
import jakarta.servlet.http.HttpServletRequest;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Verified identity, distinct Hub read and optional source-link permission. */
class HubSkillReadPermissionGuardTest {
    
    private MockedStatic<RequestContextHolder> requests;
    
    private MockedStatic<NacosAuthConfigHolder> authConfigs;
    
    private MockedStatic<VisibilityHelper> visibility;
    
    private MockedStatic<AuthPluginManager> authPlugins;
    
    private AuthPluginManager pluginManager;
    
    private AuthPluginService authService;
    
    private final HubSkillReadPermissionGuard guard = new HubSkillReadPermissionGuard();
    
    private AuthContext context;
    
    private IdentityContext identity;
    
    private NacosAuthConfig config;
    
    private NacosAuthConfigHolder holder;
    
    private VisibilityService visibilityService;
    
    @BeforeEach
    void setUp() throws Exception {
        requests = mockStatic(RequestContextHolder.class);
        authConfigs = mockStatic(NacosAuthConfigHolder.class);
        visibility = mockStatic(VisibilityHelper.class);
        authPlugins = mockStatic(AuthPluginManager.class);
        RequestContext request = mock(RequestContext.class);
        context = new AuthContext();
        identity = new IdentityContext();
        identity.setParameter(Constants.Identity.IDENTITY_ID, "alice");
        context.setApiType(ApiType.CONSOLE_API.name());
        context.setIdentityContext(identity);
        context.setAuthResult(AuthResult.successResult());
        when(request.getAuthContext()).thenReturn(context);
        requests.when(RequestContextHolder::getContext).thenReturn(request);
        holder = mock(NacosAuthConfigHolder.class);
        config = mock(NacosAuthConfig.class);
        when(config.isAuthEnabled()).thenReturn(true);
        when(config.getNacosAuthSystemType()).thenReturn("nacos");
        when(holder.getNacosAuthConfigByScope(ApiType.CONSOLE_API.name())).thenReturn(config);
        when(holder.getNacosAuthConfigByScope(ApiType.ADMIN_API.name())).thenReturn(config);
        when(holder.getNacosAuthConfigByScope(ApiType.OPEN_API.name())).thenReturn(config);
        authConfigs.when(NacosAuthConfigHolder::getInstance).thenReturn(holder);
        visibilityService = mock(VisibilityService.class);
        visibility.when(VisibilityHelper::findVisibilityService)
            .thenReturn(Optional.of(visibilityService));
        pluginManager = mock(AuthPluginManager.class);
        authService = mock(AuthPluginService.class);
        authPlugins.when(AuthPluginManager::getInstance).thenReturn(pluginManager);
        when(pluginManager.findAuthServiceSpiImpl("nacos")).thenReturn(Optional.of(authService));
        when(authService.validateAuthority(eq(identity), any(Permission.class)))
            .thenReturn(AuthResult.successResult());
    }
    
    @AfterEach
    void tearDown() {
        authPlugins.close();
        visibility.close();
        authConfigs.close();
        requests.close();
    }
    
    @Test
    void authenticatedConsoleViewerDoesNotNeedSourceReadGrant() throws Exception {
        assertDoesNotThrow(guard::requireHubRead);
        assertFalse(guard.canReadSource(sourceSkill()));
    }
    
    @Test
    void adminAndOpenApiReadersUseTheirOwnEnabledAuthScope() throws Exception {
        context.setApiType(ApiType.ADMIN_API.name());
        guard.requireHubRead();
        context.setApiType(ApiType.OPEN_API.name());
        guard.requireHubRead();
    }
    
    @Test
    void missingAuthConfigMustFailClosed() {
        context.setApiType(ApiType.OPEN_API.name());
        when(holder.getNacosAuthConfigByScope(ApiType.OPEN_API.name())).thenReturn(null);
        assertDenied();
    }
    
    @Test
    void authDisabledMustNotGrantHubRead() {
        when(config.isAuthEnabled()).thenReturn(false);
        assertDenied();
    }
    
    @Test
    void absentAuthResultMustNotGrantHubRead() {
        context.setAuthResult(null);
        assertDenied();
    }
    
    @Test
    void rejectedAuthResultMustNotGrantHubRead() {
        context.setAuthResult(AuthResult.failureResult(401, "bad credentials"));
        assertDenied();
    }
    
    @Test
    void anonymousIdentityIsNotGrantedByPublicSource() {
        identity.setParameter(Constants.Identity.IDENTITY_ID, "anonymous");
        assertDenied();
    }
    
    @Test
    void whitespaceAnonymousIdentityIsRejected() {
        identity.setParameter(Constants.Identity.IDENTITY_ID, " anonymous ");
        assertDenied();
    }
    
    @Test
    void missingPrincipalIsRejected() {
        identity.setParameter(Constants.Identity.IDENTITY_ID, null);
        assertDenied();
    }
    
    @Test
    void serverIdentityIsNotAUserGrant() {
        identity.setParameter(Constants.Identity.SERVER_IDENTITY, Boolean.TRUE);
        assertDenied();
    }
    
    @Test
    void serverIdentityStringFlagIsAlsoRejected() {
        identity.setParameter(Constants.Identity.SERVER_IDENTITY, "true");
        assertDenied();
    }
    
    @Test
    void innerApiIsNotAUserReadEndpoint() {
        context.setApiType(ApiType.INNER_API.name());
        assertDenied();
    }
    
    @Test
    void missingRequestContextMustFailClosed() {
        requests.when(RequestContextHolder::getContext).thenReturn(null);
        assertDenied();
    }
    
    @Test
    void missingVisibilityPluginMustNotGrantHubRead() {
        visibility.when(VisibilityHelper::findVisibilityService).thenReturn(Optional.empty());
        assertDenied();
    }
    
    @Test
    void failingVisibilityPluginLookupMustNotGrantHubRead() {
        visibility.when(VisibilityHelper::findVisibilityService)
            .thenThrow(new IllegalStateException("visibility SPI unavailable"));
        assertDenied();
    }
    
    @Test
    void sourceLinkGrantUsesCurrentIdentityAndApiType() throws Exception {
        AiResource source = sourceSkill();
        when(visibilityService.validateVisibility("alice", VisibilityConstants.ACTION_READ,
            ApiType.CONSOLE_API.name(), source)).thenReturn(ValidationResult.allow());
        assertTrue(guard.canReadSource(source));
        verify(visibilityService).validateVisibility("alice", VisibilityConstants.ACTION_READ,
            ApiType.CONSOLE_API.name(), source);
    }
    
    @Test
    void sourceLinkDenialDoesNotRevokeHubGrant() throws Exception {
        AiResource source = sourceSkill();
        when(visibilityService.validateVisibility("alice", VisibilityConstants.ACTION_READ,
            ApiType.CONSOLE_API.name(), source))
            .thenReturn(ValidationResult.deny("No namespace READ"));
        assertFalse(guard.canReadSource(source));
        guard.requireHubRead();
    }
    
    @Test
    void sourceLinkMustDenyInvalidOrHubSourceWithoutCallingPlugin() throws Exception {
        assertFalse(guard.canReadSource(null));
        AiResource hub = sourceSkill();
        hub.setNamespaceId("__nacos_ai_hub__");
        assertFalse(guard.canReadSource(hub));
        verifyNoInteractions(visibilityService);
    }
    
    @Test
    void sourceLinkFailsClosedForPluginErrorsOrMissingResponse() throws Exception {
        AiResource source = sourceSkill();
        assertFalse(guard.canReadSource(source));
        when(visibilityService.validateVisibility("alice", VisibilityConstants.ACTION_READ,
            ApiType.CONSOLE_API.name(), source))
            .thenThrow(new IllegalStateException("read SPI crashed"));
        assertFalse(guard.canReadSource(source));
        guard.requireHubRead();
    }
    
    @Test
    void sourceLinkNeverAcceptsAnonymousOrServerIdentity() {
        identity.setParameter(Constants.Identity.IDENTITY_ID, "anonymous");
        assertEquals(NacosException.NO_RIGHT, assertThrows(NacosException.class,
            () -> guard.canReadSource(sourceSkill())).getErrCode());
        identity.setParameter(Constants.Identity.IDENTITY_ID, "alice");
        identity.setParameter(Constants.Identity.SERVER_IDENTITY, true);
        assertEquals(NacosException.NO_RIGHT, assertThrows(NacosException.class,
            () -> guard.canReadSource(sourceSkill())).getErrCode());
    }
    
    @Test
    void publicVisibilityDoesNotGrantAnIndependentSourceDeepLink() throws Exception {
        AiResource source = sourceSkill();
        when(visibilityService.validateVisibility("alice", VisibilityConstants.ACTION_READ,
            ApiType.CONSOLE_API.name(), source)).thenReturn(ValidationResult.allow());
        when(authService.validateAuthority(eq(identity), any(Permission.class)))
            .thenReturn(AuthResult.failureResult(403, "No source READ"));
        
        assertFalse(guard.canReadSource(source));
        guard.requireHubRead();
        verifyNoInteractions(visibilityService);
    }
    
    @Test
    void sourcePermissionMatchesTheOrdinarySkillEndpoint() throws Exception {
        AiResource source = sourceSkill();
        when(visibilityService.validateVisibility("alice", VisibilityConstants.ACTION_READ,
            ApiType.CONSOLE_API.name(), source)).thenReturn(ValidationResult.allow());
        assertTrue(guard.canReadSource(source));
        
        ArgumentCaptor<Permission> captured = ArgumentCaptor.forClass(Permission.class);
        verify(authService).validateAuthority(eq(identity), captured.capture());
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/v3/admin/ai/skills");
        when(request.getParameter("namespaceId")).thenReturn("team-a");
        when(request.getParameter("skillName")).thenReturn("find-skills");
        Secured secured = SkillAdminController.class.getMethod("getSkill", SkillForm.class)
            .getAnnotation(Secured.class);
        Resource ordinary = new AiHttpResourceParser().parse(request, secured);
        Resource actual = captured.getValue().getResource();
        assertEquals(secured.action().toString(), captured.getValue().getAction());
        assertEquals(ordinary.getNamespaceId(), actual.getNamespaceId());
        assertEquals(ordinary.getGroup(), actual.getGroup());
        assertEquals(ordinary.getName(), actual.getName());
        assertEquals(ordinary.getType(), actual.getType());
        assertEquals(ordinary.getProperties(), actual.getProperties());
    }
    
    @Test
    void missingAuthPluginDeniesSourceLinkWithoutRevokingHubRead() throws Exception {
        when(pluginManager.findAuthServiceSpiImpl("nacos")).thenReturn(Optional.empty());
        assertFalse(guard.canReadSource(sourceSkill()));
        guard.requireHubRead();
        verifyNoInteractions(visibilityService);
    }
    
    @Test
    void nullAuthorityDecisionDeniesSourceLink() throws Exception {
        when(authService.validateAuthority(eq(identity), any(Permission.class))).thenReturn(null);
        assertFalse(guard.canReadSource(sourceSkill()));
        verifyNoInteractions(visibilityService);
    }
    
    @Test
    void authPluginErrorsDenySourceLinkWithoutRevokingHubRead() throws Exception {
        doThrow(new AccessException("No permission")).when(authService)
            .validateAuthority(eq(identity), any(Permission.class));
        assertFalse(guard.canReadSource(sourceSkill()));
        doThrow(new IllegalStateException("Plugin unavailable")).when(authService)
            .validateAuthority(eq(identity), any(Permission.class));
        assertFalse(guard.canReadSource(sourceSkill()));
        guard.requireHubRead();
        verifyNoInteractions(visibilityService);
    }
    
    @Test
    void malformedPrincipalAndUnknownApiScopeAreDenied() {
        identity.setParameter(Constants.Identity.IDENTITY_ID, 101L);
        assertDenied();
        identity.setParameter(Constants.Identity.IDENTITY_ID, " ");
        assertDenied();
        identity.setParameter(Constants.Identity.IDENTITY_ID, "alice");
        context.setApiType(null);
        assertDenied();
        context.setApiType("unknown");
        assertDenied();
    }
    
    @Test
    void absentIdentityAndAuthContextAreDenied() {
        context.setIdentityContext(null);
        assertDenied();
        RequestContext empty = mock(RequestContext.class);
        requests.when(RequestContextHolder::getContext).thenReturn(empty);
        assertDenied();
    }
    
    @Test
    void nullVisibilityLookupIsDenied() throws Exception {
        visibility.when(VisibilityHelper::findVisibilityService).thenReturn(null);
        assertDenied();
        assertFalse(guard.canReadSource(sourceSkill()));
    }
    
    @Test
    void sourceLinkUsesOneVisibilityLookupAndCapturedCaller() throws Exception {
        AiResource source = sourceSkill();
        visibility.when(VisibilityHelper::findVisibilityService).thenAnswer(invocation -> {
            context.setApiType(ApiType.OPEN_API.name());
            IdentityContext replacement = new IdentityContext();
            replacement.setParameter(Constants.Identity.IDENTITY_ID, "bob");
            context.setIdentityContext(replacement);
            return Optional.of(visibilityService);
        });
        when(visibilityService.validateVisibility("alice", VisibilityConstants.ACTION_READ,
            ApiType.CONSOLE_API.name(), source)).thenReturn(ValidationResult.allow());
        
        assertTrue(guard.canReadSource(source));
        visibility.verify(VisibilityHelper::findVisibilityService);
        verify(authService).validateAuthority(eq(identity), any(Permission.class));
        verify(visibilityService).validateVisibility("alice", VisibilityConstants.ACTION_READ,
            ApiType.CONSOLE_API.name(), source);
    }
    
    private void assertDenied() {
        assertEquals(NacosException.NO_RIGHT, assertThrows(NacosException.class,
            guard::requireHubRead).getErrCode());
    }
    
    private AiResource sourceSkill() {
        AiResource source = new AiResource();
        source.setId(101L);
        source.setName("find-skills");
        source.setNamespaceId("team-a");
        source.setType("skill");
        return source;
    }
}
