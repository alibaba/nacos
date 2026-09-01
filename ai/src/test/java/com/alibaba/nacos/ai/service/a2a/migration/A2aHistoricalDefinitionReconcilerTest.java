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

package com.alibaba.nacos.ai.service.a2a.migration;

import com.alibaba.nacos.ai.service.a2a.A2aCanonicalDefinitionConverter;
import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.api.ai.model.a2a.AgentCardDetailInfo;
import com.alibaba.nacos.api.ai.model.a2a.AgentCardVersionInfo;
import com.alibaba.nacos.api.ai.model.a2a.AgentInterface;
import com.alibaba.nacos.api.ai.model.a2a.AgentProvider;
import com.alibaba.nacos.api.ai.model.agent.EndpointSource;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.plugin.visibility.constant.VisibilityConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class A2aHistoricalDefinitionReconcilerTest {
    
    private static final String NAMESPACE_ID = "tenant-a";
    
    private static final String AGENT_NAME = "research-agent";
    
    @Mock
    private A2aMigrationTargetStore targetStore;
    
    private A2aHistoricalDefinitionReconciler reconciler;
    
    @BeforeEach
    void setUp() {
        reconciler = new A2aHistoricalDefinitionReconciler(
            new A2aCanonicalDefinitionConverter(), targetStore);
    }
    
    @Test
    void shouldMapAllVersionsAndLatestMetadataExactly() throws NacosException {
        A2aHistoricalDefinitionSnapshot snapshot = snapshot("2.0.0",
            card("1.0.0", "URL", "Old"), card("2.0.0", "SERVICE", "Latest"));
        BooleanSupplier fence = () -> true;
        when(targetStore.reconcile(any(), any()))
            .thenReturn(A2aMigrationTargetStore.Result.CREATED);
        
        assertEquals(A2aMigrationTargetStore.Result.CREATED,
            reconciler.reconcile(snapshot, fence));
        
        ArgumentCaptor<A2aMigrationDefinition> definitionCaptor =
            ArgumentCaptor.forClass(A2aMigrationDefinition.class);
        ArgumentCaptor<BooleanSupplier> fenceCaptor = ArgumentCaptor.forClass(
            BooleanSupplier.class);
        verify(targetStore).reconcile(definitionCaptor.capture(), fenceCaptor.capture());
        A2aMigrationDefinition definition = definitionCaptor.getValue();
        assertSame(fence, fenceCaptor.getValue());
        assertEquals("2.0.0", definition.getLatestVersion());
        assertEquals(2, definition.getVersions().size());
        assertEquals(AiConstants.Agent.VERSION_STATUS_ONLINE,
            definition.getVersions().get(0).getStatus());
        assertEquals("nacos", definition.getVersions().get(0).getAuthor());
        assertEquals(Arrays.asList(EndpointSource.DECLARED, EndpointSource.RUNTIME),
            definition.getVersions().get(0).getCallInterfaces().get(0)
                .getEndpointSourceOrder());
        assertEquals(Arrays.asList(EndpointSource.RUNTIME, EndpointSource.DECLARED),
            definition.getVersions().get(1).getCallInterfaces().get(0)
                .getEndpointSourceOrder());
        assertEquals("Latest", definition.getAgent().getDescription());
        assertEquals("Latest Organization", definition.getAgent().getProvider().getName());
        assertEquals(AiConstants.Agent.RESOURCE_STATUS_ENABLE,
            definition.getAgent().getStatus());
        assertEquals("nacos", definition.getAgent().getOwner());
        assertEquals(VisibilityConstants.SCOPE_PUBLIC, definition.getAgent().getScope());
    }
    
    @Test
    void shouldAllowLatestCardWithoutOptionalProvider() throws NacosException {
        AgentCardDetailInfo latest = card("1.0.0", "URL", "Latest");
        latest.setProvider(null);
        A2aMigrationDefinition definition = reconciler.convert(snapshot("1.0.0", latest));
        
        assertNull(definition.getAgent().getProvider());
        assertEquals("Latest", definition.getAgent().getDescription());
    }
    
    @Test
    void shouldRejectIncompleteInputsAndMissingLatestContent() {
        assertThrows(IllegalArgumentException.class,
            () -> reconciler.reconcile(null, () -> true));
        assertThrows(IllegalArgumentException.class,
            () -> reconciler.reconcile(snapshot("1.0.0", card("1.0.0", "URL", "x")), null));
        A2aHistoricalDefinitionSnapshot missingLatest = snapshot("2.0.0",
            card("1.0.0", "URL", "x"));
        assertThrows(IllegalStateException.class, () -> reconciler.convert(missingLatest));
    }
    
    private A2aHistoricalDefinitionSnapshot snapshot(String latest,
        AgentCardDetailInfo... cards) {
        AgentCardVersionInfo summary = new AgentCardVersionInfo();
        summary.setName(AGENT_NAME);
        summary.setLatestPublishedVersion(latest);
        summary.setRegistrationType("URL");
        java.util.List<com.alibaba.nacos.api.ai.model.a2a.AgentVersionDetail> details =
            new java.util.ArrayList<>();
        Map<String, A2aHistoricalDefinitionSnapshot.VersionSnapshot> versions =
            new LinkedHashMap<>();
        for (AgentCardDetailInfo card : cards) {
            com.alibaba.nacos.api.ai.model.a2a.AgentVersionDetail detail =
                new com.alibaba.nacos.api.ai.model.a2a.AgentVersionDetail();
            detail.setVersion(card.getVersion());
            detail.setLatest(card.getVersion().equals(latest));
            details.add(detail);
            String content = JacksonUtils.toJson(card);
            versions.put(card.getVersion(),
                new A2aHistoricalDefinitionSnapshot.VersionSnapshot(
                    "opaque-" + card.getVersion(), content, "md5-" + card.getVersion(), card));
        }
        summary.setVersionDetails(details);
        return new A2aHistoricalDefinitionSnapshot(NAMESPACE_ID, "opaque",
            JacksonUtils.toJson(summary), "summary-md5", summary, versions, "fingerprint");
    }
    
    private AgentCardDetailInfo card(String version, String registrationType,
        String description) {
        AgentCardDetailInfo result = new AgentCardDetailInfo();
        result.setName(AGENT_NAME);
        result.setVersion(version);
        result.setDescription(description);
        result.setRegistrationType(registrationType);
        AgentProvider provider = new AgentProvider();
        provider.setOrganization(description + " Organization");
        provider.setUrl("https://example.com/provider");
        result.setProvider(provider);
        AgentInterface agentInterface = new AgentInterface();
        agentInterface.setUrl("https://example.com/" + version);
        agentInterface.setProtocolBinding("HTTP+JSON");
        agentInterface.setProtocolVersion("0.3");
        result.setSupportedInterfaces(java.util.Collections.singletonList(agentInterface));
        return result;
    }
}
