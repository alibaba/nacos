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
import com.alibaba.nacos.ai.model.hub.HubResourceType;
import com.alibaba.nacos.ai.service.VisibilityHelper;
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
import com.alibaba.nacos.plugin.auth.spi.server.AuthPluginManager;
import com.alibaba.nacos.plugin.auth.spi.server.AuthPluginService;
import com.alibaba.nacos.plugin.visibility.spi.ValidationResult;
import com.alibaba.nacos.plugin.visibility.spi.VisibilityService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Source and target authority remain independent for every supported resource type. */
class HubResourcePermissionGuardTest {
    
    private final HubSkillReadPermissionGuard admission = mock(HubSkillReadPermissionGuard.class);
    private final HubSkillPermissionGuard governance = mock(HubSkillPermissionGuard.class);
    private final HubResourcePermissionGuard guard =
        new HubResourcePermissionGuard(admission, governance);
    private final AuthContext auth = new AuthContext();
    private final IdentityContext identity = new IdentityContext();
    private final AuthPluginService plugin = mock(AuthPluginService.class);
    private final VisibilityService visibility = mock(VisibilityService.class);
    private final NacosAuthConfig config = mock(NacosAuthConfig.class);
    private MockedStatic<RequestContextHolder> requests;
    private MockedStatic<NacosAuthConfigHolder> configs;
    private MockedStatic<AuthPluginManager> plugins;
    private MockedStatic<VisibilityHelper> visibilityLookup;
    
    @BeforeEach
    void open() throws Exception {
        requests = mockStatic(RequestContextHolder.class);
        configs = mockStatic(NacosAuthConfigHolder.class);
        plugins = mockStatic(AuthPluginManager.class);
        visibilityLookup = mockStatic(VisibilityHelper.class);
        RequestContext request = mock(RequestContext.class);
        auth.setApiType(ApiType.CONSOLE_API.name());
        auth.setIdentityContext(identity);
        auth.setAuthResult(AuthResult.successResult());
        identity.setParameter(Constants.Identity.IDENTITY_ID, "alice");
        when(request.getAuthContext()).thenReturn(auth);
        requests.when(RequestContextHolder::getContext).thenReturn(request);
        NacosAuthConfigHolder holder = mock(NacosAuthConfigHolder.class);
        configs.when(NacosAuthConfigHolder::getInstance).thenReturn(holder);
        when(holder.getNacosAuthConfigByScope(ApiType.CONSOLE_API.name())).thenReturn(config);
        when(config.isAuthEnabled()).thenReturn(true);
        when(config.getNacosAuthSystemType()).thenReturn("nacos");
        AuthPluginManager manager = mock(AuthPluginManager.class);
        plugins.when(AuthPluginManager::getInstance).thenReturn(manager);
        when(manager.findAuthServiceSpiImpl("nacos")).thenReturn(Optional.of(plugin));
        when(plugin.validateAuthority(any(), any(Permission.class)))
            .thenReturn(AuthResult.successResult());
        visibilityLookup.when(VisibilityHelper::findVisibilityService)
            .thenReturn(Optional.of(visibility));
        ValidationResult allowed = mock(ValidationResult.class);
        when(allowed.isAllowed()).thenReturn(true);
        when(visibility.validateVisibility(anyString(), anyString(), anyString(), any()))
            .thenReturn(allowed);
    }
    
    @AfterEach
    void close() {
        visibilityLookup.close();
        plugins.close();
        configs.close();
        requests.close();
    }
    
    @ParameterizedTest
    @EnumSource(HubResourceType.class)
    void targetWriteUsesExactTypeNamespaceAndName(HubResourceType type) throws Exception {
        guard.requireTargetWrite(type, "target", "new-name");
        ArgumentCaptor<Permission> captured = ArgumentCaptor.forClass(Permission.class);
        verify(plugin).validateAuthority(any(), captured.capture());
        assertEquals("target", captured.getValue().getResource().getNamespaceId());
        assertEquals("new-name", captured.getValue().getResource().getName());
        assertEquals(type == HubResourceType.AGENTSPEC ? "agentSpec" : type.resourceType(),
            captured.getValue().getResource().getProperties()
                .getProperty(Constants.Resource.AI_TYPE));
        assertEquals("w", captured.getValue().getAction());
        verifyNoInteractions(visibility, governance);
    }
    
    @ParameterizedTest
    @EnumSource(HubResourceType.class)
    void sourceReadNeedsAuthorityAndVisibility(HubResourceType type) throws Exception {
        AiResource source = source(type);
        guard.requireSourceRead(type, source);
        verify(visibility).validateVisibility("alice", "r", ApiType.CONSOLE_API.name(), source);
        when(plugin.validateAuthority(any(), any(Permission.class)))
            .thenReturn(AuthResult.failureResult(403, "denied"));
        assertThrows(NacosException.class, () -> guard.requireSourceRead(type, source));
    }
    
    @ParameterizedTest
    @EnumSource(HubResourceType.class)
    void deniedVisibilityNeverBecomesAnOwnerGrant(HubResourceType type) throws Exception {
        AiResource source = source(type);
        source.setOwner("alice");
        when(visibility.validateVisibility(anyString(), anyString(), anyString(), any()))
            .thenReturn(null);
        assertThrows(NacosException.class, () -> guard.requireSourceWrite(type, source));
    }
    
    @Test
    void anonymousServerIdentityOpenApiAndDisabledAuthCannotMutate() {
        identity.setParameter(Constants.Identity.IDENTITY_ID, " anonymous ");
        assertThrows(NacosException.class,
            () -> guard.requireTargetWrite(HubResourceType.PROMPT, "target", "name"));
        identity.setParameter(Constants.Identity.IDENTITY_ID, "alice");
        identity.setParameter(Constants.Identity.SERVER_IDENTITY, "true");
        assertThrows(NacosException.class,
            () -> guard.requireTargetWrite(HubResourceType.PROMPT, "target", "name"));
        identity.setParameter(Constants.Identity.SERVER_IDENTITY, false);
        auth.setApiType(ApiType.OPEN_API.name());
        assertThrows(NacosException.class,
            () -> guard.requireTargetWrite(HubResourceType.PROMPT, "target", "name"));
        auth.setApiType(ApiType.CONSOLE_API.name());
        when(config.isAuthEnabled()).thenReturn(false);
        assertThrows(NacosException.class,
            () -> guard.requireTargetWrite(HubResourceType.PROMPT, "target", "name"));
        verifyNoInteractions(plugin, visibility);
    }
    
    @Test
    void hubReadAndGovernanceDoNotGrantSourceOrTargetWrite() throws Exception {
        guard.requireHubRead();
        guard.requireAdministrator();
        verify(admission).requireHubRead();
        verify(governance).requireAdministrator();
        verifyNoInteractions(plugin, visibility);
    }
    
    private AiResource source(HubResourceType type) {
        AiResource source = new AiResource();
        source.setId(101L);
        source.setNamespaceId("source");
        source.setName("same-name");
        source.setType(type.resourceType());
        source.setScope("PUBLIC");
        return source;
    }
    
    @Test
    void namingDependencyReadUsesExactNamingIdentityAndDoesNotInferAiAuthority() throws Exception {
        guard.requireNamingRead("target-ns", "group", "service");
        ArgumentCaptor<Permission> captured = ArgumentCaptor.forClass(Permission.class);
        verify(plugin).validateAuthority(any(), captured.capture());
        assertEquals(com.alibaba.nacos.plugin.auth.constant.SignType.NAMING,
            captured.getValue().getResource().getType());
        assertEquals("target-ns", captured.getValue().getResource().getNamespaceId());
        assertEquals("group", captured.getValue().getResource().getGroup());
        assertEquals("service", captured.getValue().getResource().getName());
        assertEquals("r", captured.getValue().getAction());
        verifyNoInteractions(visibility, governance);
    }
    
}
