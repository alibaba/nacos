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

package com.alibaba.nacos.ai.service.a2a;

import com.alibaba.nacos.ai.service.a2a.migration.A2aMigrationDefinitionWriteAfterHook;
import com.alibaba.nacos.ai.service.a2a.migration.A2aMigrationLegacyMutationGuard;
import com.alibaba.nacos.api.ai.model.a2a.AgentCard;
import com.alibaba.nacos.api.ai.model.a2a.AgentCardDetailInfo;
import com.alibaba.nacos.api.ai.model.a2a.AgentCardVersionInfo;
import com.alibaba.nacos.api.ai.model.a2a.AgentVersionDetail;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.Page;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class A2aCompatibilityOperationServiceTest {
    
    @Mock
    private A2aCompatibilityModeResolver modeResolver;
    
    @Mock
    private A2aServerOperationService canonicalService;
    
    @Mock
    private LegacyA2aOperationService legacyService;
    
    @Mock
    private A2aMigrationDefinitionWriteAfterHook migrationWriteAfterHook;
    
    @Mock
    private A2aMigrationLegacyMutationGuard migrationMutationGuard;
    
    private A2aCompatibilityOperationService service;
    
    @BeforeEach
    void setUp() {
        service = new A2aCompatibilityOperationService(modeResolver, canonicalService,
            legacyService, migrationWriteAfterHook, migrationMutationGuard);
    }
    
    @Test
    void shouldRouteEveryOperationToCanonical() throws NacosException {
        when(modeResolver.resolve()).thenReturn(A2aCompatibilityMode.CANONICAL);
        exerciseAndVerify(canonicalService);
    }
    
    @Test
    void shouldRouteEveryOperationToLegacy() throws NacosException {
        when(modeResolver.resolve()).thenReturn(A2aCompatibilityMode.LEGACY);
        exerciseAndVerify(legacyService);
    }
    
    private void exerciseAndVerify(A2aOperationService selected) throws NacosException {
        AgentCard card = new AgentCard();
        card.setName("agent");
        AgentCardDetailInfo detail = new AgentCardDetailInfo();
        Page<AgentCardVersionInfo> page = new Page<>();
        List<AgentVersionDetail> versions = Collections.singletonList(new AgentVersionDetail());
        when(selected.getAgentCard("ns", "agent", "1.0.0", "URL")).thenReturn(detail);
        when(selected.getAgentCardForClient("ns", "agent", "1.0.0", "URL"))
            .thenReturn(detail);
        when(selected.listAgents("ns", "agent", "blur", 1, 10)).thenReturn(page);
        when(selected.listAgentVersions("ns", "agent")).thenReturn(versions);
        
        service.registerAgent(card, "ns", "URL");
        service.releaseAgent(card, "ns", "SERVICE", true);
        service.updateAgentCard(card, "ns", "URL", false);
        service.deleteAgent("ns", "agent", "1.0.0");
        assertSame(detail, service.getAgentCard("ns", "agent", "1.0.0", "URL"));
        assertSame(detail, service.getAgentCardForClient("ns", "agent", "1.0.0", "URL"));
        assertSame(page, service.listAgents("ns", "agent", "blur", 1, 10));
        assertSame(versions, service.listAgentVersions("ns", "agent"));
        
        verify(selected).registerAgent(card, "ns", "URL");
        verify(selected).releaseAgent(card, "ns", "SERVICE", true);
        verify(selected).updateAgentCard(card, "ns", "URL", false);
        verify(selected).deleteAgent("ns", "agent", "1.0.0");
        verify(migrationMutationGuard, times(4)).checkMutable();
        if (selected == legacyService) {
            verify(migrationWriteAfterHook, times(4)).afterSuccessfulMutation("ns", "agent");
        } else {
            verifyNoInteractions(migrationWriteAfterHook);
        }
    }
}
