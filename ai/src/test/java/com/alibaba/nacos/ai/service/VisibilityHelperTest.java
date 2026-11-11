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

package com.alibaba.nacos.ai.service;

import com.alibaba.nacos.plugin.visibility.constant.VisibilityConstants;
import com.alibaba.nacos.core.plugin.visibility.VisibilityPluginManager;
import com.alibaba.nacos.plugin.visibility.model.VisibilityResource;
import com.alibaba.nacos.plugin.visibility.spi.ValidationResult;
import com.alibaba.nacos.plugin.visibility.spi.VisibilityService;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.StandardEnvironment;
import com.alibaba.nacos.ai.form.AiResourceFilterableForm;
import com.alibaba.nacos.ai.form.agent.admin.AgentScopeForm;
import com.alibaba.nacos.ai.form.agentspecs.admin.AgentSpecScopeForm;
import com.alibaba.nacos.ai.form.skills.admin.SkillScopeForm;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.plugin.visibility.model.VisibilityScopeDefinition;

import java.lang.reflect.Field;
import java.util.Optional;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertThrows;

class VisibilityHelperTest {
    
    private static final ConfigurableEnvironment CACHED_ENVIRONMENT = EnvUtil.getEnvironment();
    
    private static final String[] RESOURCE_TYPES =
        {"agent", "mcp", "skill", "prompt", "agentspec", "unknown"};
    
    private MockedStatic<VisibilityPluginManager> visibilityManagerStatic;
    
    private VisibilityPluginManager visibilityPluginManager;
    
    @BeforeEach
    void setUp() throws Exception {
        EnvUtil.setEnvironment(new StandardEnvironment());
        resetCachedVisibilityServiceName();
        visibilityPluginManager = mock(VisibilityPluginManager.class);
        visibilityManagerStatic = mockStatic(VisibilityPluginManager.class);
        visibilityManagerStatic.when(VisibilityPluginManager::getInstance)
            .thenReturn(visibilityPluginManager);
    }
    
    @AfterEach
    void tearDown() throws Exception {
        if (visibilityManagerStatic != null) {
            visibilityManagerStatic.close();
        }
        EnvUtil.setEnvironment(CACHED_ENVIRONMENT);
        resetCachedVisibilityServiceName();
    }
    
    @Test
    void resolveDefaultScopeForCreateShouldFallbackToPrivateWhenPluginAbsent() {
        when(visibilityPluginManager.findVisibilityService(anyString()))
            .thenReturn(Optional.empty());
        for (String resourceType : RESOURCE_TYPES) {
            assertEquals(VisibilityConstants.SCOPE_PRIVATE,
                VisibilityHelper.resolveDefaultScopeForCreate(resourceType));
        }
    }
    
    @Test
    void resolveDefaultScopeForCreateShouldUsePluginScopeAndNormalizeUppercase() {
        VisibilityService visibilityService = mock(VisibilityService.class);
        when(visibilityPluginManager.findVisibilityService(anyString()))
            .thenReturn(Optional.of(visibilityService));
        for (String scope : new String[] {"public", "private"}) {
            when(visibilityService.resolveDefaultScopeForCreate(anyString(), anyString(),
                anyString())).thenReturn(scope);
            for (String resourceType : RESOURCE_TYPES) {
                assertEquals(scope.toUpperCase(java.util.Locale.ROOT),
                    VisibilityHelper.resolveDefaultScopeForCreate(resourceType));
            }
        }
    }
    
    @Test
    void resolveDefaultScopeForCreateShouldFallbackToPrivateWhenPluginReturnsBlank() {
        VisibilityService visibilityService = mock(VisibilityService.class);
        when(visibilityPluginManager.findVisibilityService(anyString()))
            .thenReturn(Optional.of(visibilityService));
        for (String scope : new String[] {null, "", "  "}) {
            when(visibilityService.resolveDefaultScopeForCreate(anyString(), anyString(),
                anyString())).thenReturn(scope);
            for (String resourceType : RESOURCE_TYPES) {
                assertEquals(VisibilityConstants.SCOPE_PRIVATE,
                    VisibilityHelper.resolveDefaultScopeForCreate(resourceType));
            }
        }
    }
    
    @Test
    void canReadResourceWithCapturedOwnerShouldAllowWhenPluginAbsent() {
        when(visibilityPluginManager.findVisibilityService(anyString()))
            .thenReturn(Optional.empty());
        
        assertTrue(VisibilityHelper.canReadResource(mock(VisibilityResource.class)));
        assertTrue(VisibilityHelper.canReadResource("identity", "open-api",
            mock(VisibilityResource.class)));
    }
    
    @Test
    void canReadResourceWithCapturedOwnerShouldDelegateExactContext() {
        VisibilityService visibilityService = mock(VisibilityService.class);
        VisibilityResource resource = mock(VisibilityResource.class);
        when(visibilityPluginManager.findVisibilityService(anyString()))
            .thenReturn(Optional.of(visibilityService));
        when(visibilityService.validateVisibility("identity", VisibilityConstants.ACTION_READ,
            "open-api", resource)).thenReturn(ValidationResult.deny("private"));
        
        assertFalse(VisibilityHelper.canReadResource("identity", "open-api", resource));
        verify(visibilityService).validateVisibility("identity",
            VisibilityConstants.ACTION_READ, "open-api", resource);
        verify(visibilityService).validateVisibility(anyString(), anyString(), anyString(),
            any(VisibilityResource.class));
    }
    
    private static void resetCachedVisibilityServiceName() throws Exception {
        Field field = VisibilityHelper.class.getDeclaredField("cachedVisibilityServiceName");
        field.setAccessible(true);
        field.set(null, null);
    }
    
    @Test
    void scopeDeclarationsFollowSelectedPluginAndFormsShareValidation() throws Exception {
        VisibilityService service = mock(VisibilityService.class);
        when(service.getAdditionalScopes()).thenReturn(List.of(
            new VisibilityScopeDefinition("TEAM_EDIT", true)));
        when(visibilityPluginManager.findVisibilityService(anyString()))
            .thenReturn(Optional.of(service));
        assertEquals(Set.of("PUBLIC", "PRIVATE", "TEAM_EDIT"),
            VisibilityHelper.scopeRegistry().getScopeNames());
        SkillScopeForm skill = new SkillScopeForm();
        skill.setSkillName("my-skill");
        skill.setScope("team_edit");
        skill.validate();
        AgentSpecScopeForm agentSpec = new AgentSpecScopeForm();
        agentSpec.setAgentSpecName("my-agent");
        agentSpec.setScope("TEAM_EDIT");
        agentSpec.validate();
        AiResourceFilterableForm filter = new AiResourceFilterableForm();
        filter.setScope("TEAM_EDIT");
        filter.validate();
        AgentScopeForm agent = new AgentScopeForm();
        agent.setAgentName("scope-extension");
        agent.setScope("team_edit");
        agent.validate();
        when(visibilityPluginManager.findVisibilityService(anyString()))
            .thenReturn(Optional.empty());
        assertThrows(NacosApiException.class, skill::validate);
        assertThrows(NacosApiException.class, agentSpec::validate);
        assertThrows(NacosApiException.class, filter::validate);
        assertThrows(NacosApiException.class, agent::validate);
        filter.setScope(null);
        filter.validate();
        assertEquals(Set.of("PUBLIC", "PRIVATE"), VisibilityHelper.scopeRegistry().getScopeNames());
    }
    
}
