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
import com.alibaba.nacos.ai.pipeline.repository.PipelineExecutionRepository;
import com.alibaba.nacos.ai.service.agent.AgentOperationService;
import com.alibaba.nacos.ai.service.agent.AgentPersistenceService;
import com.alibaba.nacos.ai.pipeline.PublishPipelineExecutor;
import com.alibaba.nacos.ai.service.repository.AiResourcePersistService;
import com.alibaba.nacos.ai.service.repository.AiResourceVersionPersistService;
import com.alibaba.nacos.ai.service.repository.HubEntryStorage;
import com.alibaba.nacos.ai.service.resource.AiResourceManager;
import com.alibaba.nacos.ai.service.trace.AiResourceTraceService;
import com.alibaba.nacos.ai.utils.HubResourceNameUtils;
import com.alibaba.nacos.ai.utils.HubResourceReferenceJsonUtil;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.ai.service.VisibilityHelper;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Original Agent scope operation integrated with real common Hub revocation fences. */
class HubAgentScopeCompatibilityTest {
    
    @Test
    void privateTransitionWithdrawsBeforePersistingWithoutTouchingVersions() throws Exception {
        Fixture f = new Fixture();
        try (MockedStatic<VisibilityHelper> visibility = mockStatic(VisibilityHelper.class);
            MockedStatic<AiResourceTraceService> trace = mockStatic(AiResourceTraceService.class)) {
            f.service.updateScope("source", "same-name", "private");
            assertEquals("PRIVATE", f.source.getScope());
            assertEquals("disable", f.entry.getStatus());
            assertEquals("alice", f.source.getOwner());
            assertEquals("enable", f.source.getStatus());
            assertEquals("unchanged-version-state", f.source.getVersionInfo());
            verifyNoInteractions(f.versions, f.agentPersistence);
        }
    }
    
    @Test
    void deniedWriteCannotWithdrawPublication() throws Exception {
        Fixture f = new Fixture();
        try (MockedStatic<VisibilityHelper> visibility = mockStatic(VisibilityHelper.class)) {
            visibility.when(() -> VisibilityHelper.checkWritableResource(f.source))
                .thenThrow(new NacosException(403, "denied"));
            assertThrows(NacosException.class,
                () -> f.service.updateScope("source", "same-name", "PRIVATE"));
            assertEquals("enable", f.entry.getStatus());
            verifyNoInteractions(f.storage, f.versions, f.agentPersistence);
            verify(f.persistence, never()).updateScope(anyString(), anyString(), anyString(),
                anyString());
        }
    }
    
    @Test
    void makingPublicDoesNotRepublishOrChangeVersions() throws Exception {
        Fixture f = new Fixture();
        f.source.setScope("PRIVATE");
        f.entry.setStatus("disable");
        try (MockedStatic<VisibilityHelper> visibility = mockStatic(VisibilityHelper.class);
            MockedStatic<AiResourceTraceService> trace = mockStatic(AiResourceTraceService.class)) {
            f.service.updateScope("source", "same-name", "PUBLIC");
            assertEquals("PUBLIC", f.source.getScope());
            assertEquals("disable", f.entry.getStatus());
            verifyNoInteractions(f.storage, f.versions, f.agentPersistence);
        }
    }
    
    @Test
    void internalNamespaceCannotChangeScope() throws Exception {
        Fixture f = new Fixture();
        assertThrows(NacosException.class,
            () -> f.service.updateScope("__nacos_ai_hub__", "501", "PRIVATE"));
        verifyNoInteractions(f.persistence, f.storage, f.versions, f.agentPersistence);
    }
    
    private static final class Fixture {
        
        private final AiResourcePersistService persistence = mock(AiResourcePersistService.class);
        private final AiResourceVersionPersistService versions =
            mock(AiResourceVersionPersistService.class);
        private final AgentPersistenceService agentPersistence =
            mock(AgentPersistenceService.class);
        private final HubEntryStorage storage = mock(HubEntryStorage.class);
        private final AiResource source = new AiResource();
        private final AiResource entry = new AiResource();
        private final AgentOperationService service;
        
        private Fixture() throws Exception {
            source.setId(101L);
            source.setNamespaceId("source");
            source.setName("same-name");
            source.setType("agent");
            source.setOwner("alice");
            source.setStatus("enable");
            source.setScope("PUBLIC");
            source.setVersionInfo("unchanged-version-state");
            entry.setId(501L);
            entry.setStatus("enable");
            entry.setMetaVersion(1L);
            entry.setExt(HubResourceReferenceJsonUtil.toJson(HubResourceReferenceJsonUtil.build(
                "source", 101L, "same-name", 201L, "1.0.0")));
            when(persistence.find("source", "same-name", "agent")).thenReturn(source);
            when(storage.findByInternalName(HubResourceType.AGENT,
                HubResourceNameUtils.buildInternalName("same-name", 101L))).thenReturn(entry);
            when(storage.updateMetaCas(eq(HubResourceType.AGENT), any(), anyLong(), any()))
                .thenAnswer(call -> {
                    entry.setStatus("disable");
                    return true;
                });
            when(persistence.updateScope(eq("source"), eq("same-name"), eq("agent"), anyString()))
                .thenAnswer(call -> {
                    String scope = call.getArgument(3);
                    if ("PRIVATE".equals(scope)) {
                        assertEquals("disable", entry.getStatus());
                    }
                    source.setScope(scope);
                    return true;
                });
            AiResourceManager manager = new AiResourceManager(persistence, versions,
                mock(PipelineExecutionRepository.class));
            manager.setHubResourceLifecycleRevoker(new HubResourceLifecycleRevoker(storage));
            service = new AgentOperationService(agentPersistence, manager,
                mock(PublishPipelineExecutor.class));
        }
    }
}
