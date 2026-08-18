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

package com.alibaba.nacos.ai.service.search;

import com.alibaba.nacos.ai.model.search.AiResourceSearchDocument;
import com.alibaba.nacos.api.ai.model.a2a.AgentCapabilities;
import com.alibaba.nacos.api.ai.model.a2a.AgentCard;
import com.alibaba.nacos.api.ai.model.a2a.AgentExtension;
import com.alibaba.nacos.api.ai.model.a2a.AgentSkill;
import com.alibaba.nacos.api.ai.model.agent.Agent;
import com.alibaba.nacos.api.ai.model.agent.AgentCallInterface;
import com.alibaba.nacos.api.ai.model.agent.AgentProvider;
import com.alibaba.nacos.api.ai.model.agent.AgentVersionCatalog;
import com.alibaba.nacos.api.ai.model.agent.AgentVersionCatalogEntry;
import com.alibaba.nacos.api.ai.model.agent.AgentVersionDetail;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;

class AgentSearchIndexProjectorTest {
    
    private static final TypeReference<Map<String, Object>> MAP_TYPE =
        new TypeReference<Map<String, Object>>() {
        };
    
    private static final TypeReference<List<String>> LIST_TYPE =
        new TypeReference<List<String>>() {
        };
    
    private final AgentSearchIndexProjector projector = new AgentSearchIndexProjector();
    
    @Test
    void shouldProjectMultiVersionA2aAndCustomDefinition() {
        Agent agent = agent();
        AgentVersionDetail latest = latest();
        AgentCard card = a2aCard();
        AgentCallInterface a2a = callInterface("a2a", card);
        Map<String, Object> customDescriptor = new LinkedHashMap<>();
        customDescriptor.put("z", "tail");
        customDescriptor.put("a", "head");
        AgentCallInterface custom = callInterface("acme-rpc", customDescriptor);
        latest.setCallInterfaces(Arrays.asList(a2a, null, custom));
        
        AiResourceIndexProjection projection = projector.project(agent, latest);
        AiResourceSearchDocument document = projection.getDocument();
        Map<String, Object> metadata = JacksonUtils.toObj(document.getMetadata(), MAP_TYPE);
        final List<String> capabilities =
            JacksonUtils.toObj(document.getCapabilities(), LIST_TYPE);
        final List<String> queries =
            JacksonUtils.toObj(document.getRepresentativeQueries(), LIST_TYPE);
        
        assertEquals("public", document.getNamespaceId());
        assertEquals("agent", document.getResourceType());
        assertEquals("research-agent", document.getResourceName());
        assertEquals("2.0.0", document.getResourceVersion());
        assertEquals("Research Agent", document.getDisplayName());
        assertEquals("Find research", document.getDescription());
        assertEquals(2000L, document.getGmtModified().getTime());
        assertEquals(64, document.getSourceDigest().length());
        assertEquals(List.of("a2a", "acme-rpc"), metadata.get("protocols"));
        assertEquals(List.of("a2a-agent-card", "nacos-agent"),
            metadata.get("artifactKinds"));
        assertEquals(1, metadata.get("projectionVersion"));
        assertTrue(metadata.containsKey("provider"));
        assertTrue(capabilities.containsAll(List.of("a2a", "acme-rpc", "streaming",
            "push-notifications", "state-transition-history", "extended-agent-card",
            "urn:extension", "research", "Research Skill", "academic")));
        assertTrue(queries.containsAll(List.of("research-agent", "Research Agent",
            "Find research", "Research Skill", "Search journals")));
        assertEquals(2, projection.getEnhancementContents().size());
        assertTrue(projection.getEnhancementContents().get(0).getText()
            .contains("Search journals"));
        assertEquals("{\"a\":\"head\",\"z\":\"tail\"}",
            projection.getEnhancementContents().get(1).getText());
        assertTrue(projection.getChunks().stream()
            .anyMatch(chunk -> AiResourceSearchConstants.CHUNK_TYPE_AGENT_CONTENT
                .equals(chunk.getChunkType())));
    }
    
    @Test
    void shouldKeepNacosArtifactWhenA2aIsInvalidOrOnlyOldVersionSupportsIt() {
        Agent agent = agent();
        agent.setDisplayName(" ");
        agent.setTags(null);
        agent.setIconUrl(null);
        agent.setProvider(null);
        agent.setOwner(null);
        agent.setScope(null);
        AgentVersionDetail latest = latest();
        latest.setUpdateTime(null);
        latest.setCallInterfaces(List.of(callInterface("a2a", a2aCard("other", "2.0.0"))));
        
        AiResourceIndexProjection projection = projector.project(agent, latest);
        Map<String, Object> metadata = JacksonUtils.toObj(
            projection.getDocument().getMetadata(), MAP_TYPE);
        
        assertEquals("research-agent", projection.getDocument().getDisplayName());
        assertEquals(1000L, projection.getDocument().getGmtModified().getTime());
        assertEquals(List.of("nacos-agent"), metadata.get("artifactKinds"));
        assertEquals(Collections.emptyList(), projection.getEnhancementContents());
        assertFalse(metadata.containsKey("provider"));
        assertFalse(metadata.containsKey("owner"));
    }
    
    @Test
    void shouldProjectPureA2aAgentWithoutInventingOtherProtocols() {
        Agent agent = agent();
        AgentVersionCatalogEntry latestEntry = new AgentVersionCatalogEntry();
        latestEntry.setVersion("2.0.0");
        latestEntry.setLabels(Arrays.asList("latest", "stable"));
        latestEntry.setProtocols(List.of("a2a"));
        agent.getVersionCatalog().setOnlineVersions(List.of(latestEntry));
        AgentVersionDetail latest = latest();
        latest.setCallInterfaces(List.of(callInterface("a2a", a2aCard())));
        
        AiResourceIndexProjection projection = projector.project(agent, latest);
        Map<String, Object> metadata = JacksonUtils.toObj(
            projection.getDocument().getMetadata(), MAP_TYPE);
        
        assertEquals(List.of("a2a"), metadata.get("protocols"));
        assertEquals(List.of("a2a-agent-card", "nacos-agent"),
            metadata.get("artifactKinds"));
        assertEquals(1, projection.getEnhancementContents().size());
    }
    
    @Test
    void shouldSkipBrokenA2aAndDescriptorButContinueToValidContent() {
        Agent agent = agent();
        AgentVersionDetail latest = latest();
        AgentCallInterface brokenA2a = callInterface("a2a", null);
        AgentCallInterface validA2a = callInterface("a2a", a2aCard());
        AgentCallInterface brokenCustom = callInterface("custom", new FailingDescriptor());
        AgentCallInterface emptyCustom = callInterface("empty", null);
        latest.setCallInterfaces(Arrays.asList(brokenA2a, validA2a, brokenCustom, emptyCustom));
        
        AiResourceIndexProjection projection = projector.project(agent, latest);
        Map<String, Object> metadata = JacksonUtils.toObj(
            projection.getDocument().getMetadata(), MAP_TYPE);
        
        assertEquals(List.of("a2a-agent-card", "nacos-agent"),
            metadata.get("artifactKinds"));
        assertEquals(1, projection.getEnhancementContents().size());
    }
    
    @Test
    void shouldBoundCustomDescriptorContent() {
        Agent agent = agent();
        AgentVersionDetail latest = latest();
        latest.setCallInterfaces(List.of(callInterface("custom",
            Collections.singletonMap("content", "x".repeat(1024 * 1024)))));
        
        AiResourceIndexProjection projection = projector.project(agent, latest);
        
        assertEquals(12000, projection.getEnhancementContents().get(0).getText().length());
    }
    
    @Test
    void shouldHandleMissingCatalogEntriesAndCallInterfaces() {
        Agent agent = agent();
        agent.setVersionCatalog(null);
        AgentVersionDetail latest = latest();
        latest.setCallInterfaces(null);
        AiResourceIndexProjection withoutCatalog = projector.project(agent, latest);
        Map<String, Object> metadata = JacksonUtils.toObj(
            withoutCatalog.getDocument().getMetadata(), MAP_TYPE);
        assertEquals(Collections.emptyList(), metadata.get("protocols"));
        assertEquals(List.of("nacos-agent"), metadata.get("artifactKinds"));
        assertTrue(withoutCatalog.getEnhancementContents().isEmpty());
        
        AgentVersionCatalog catalog = catalog();
        AgentVersionCatalogEntry withoutProtocols = new AgentVersionCatalogEntry();
        withoutProtocols.setVersion("2.0.0");
        catalog.setOnlineVersions(Arrays.asList(null, withoutProtocols));
        agent.setVersionCatalog(catalog);
        AiResourceIndexProjection withoutProtocolsProjection = projector.project(agent, latest);
        Map<String, Object> withoutProtocolsMetadata = JacksonUtils.toObj(
            withoutProtocolsProjection.getDocument().getMetadata(), MAP_TYPE);
        assertEquals(Collections.emptyList(), withoutProtocolsMetadata.get("protocols"));
    }
    
    @Test
    void shouldUseStableFactsInsteadOfTimestampsForDigest() {
        Agent firstAgent = agent();
        AgentVersionDetail firstLatest = latest();
        String first = projector.project(firstAgent, firstLatest).getDocument().getSourceDigest();
        firstAgent.setUpdateTime(9999L);
        firstLatest.setUpdateTime(8888L);
        String timestampOnly = projector.project(firstAgent, firstLatest).getDocument()
            .getSourceDigest();
        firstLatest.setContentDigest("changed");
        String changedContent = projector.project(firstAgent, firstLatest).getDocument()
            .getSourceDigest();
        firstLatest.setCallInterfaces(List.of(callInterface("a2a", a2aCard())));
        String changedArtifact = projector.project(firstAgent, firstLatest).getDocument()
            .getSourceDigest();
        firstAgent.setDescription("changed description");
        String changedMetadata = projector.project(firstAgent, firstLatest).getDocument()
            .getSourceDigest();
        firstAgent.getVersionCatalog().getOnlineVersions().get(0).setLabels(List.of("stable"));
        String changedCatalog = projector.project(firstAgent, firstLatest).getDocument()
            .getSourceDigest();
        
        assertEquals(first, timestampOnly);
        assertNotEquals(timestampOnly, changedContent);
        assertNotEquals(changedContent, changedArtifact);
        assertNotEquals(changedArtifact, changedMetadata);
        assertNotEquals(changedMetadata, changedCatalog);
    }
    
    @Test
    void shouldRejectMissingProjectionFactsAndWrapMissingSha256() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> projector.project(null, latest()));
        assertThrows(IllegalArgumentException.class, () -> projector.project(agent(), null));
        try (MockedStatic<MessageDigest> digest = mockStatic(MessageDigest.class)) {
            digest.when(() -> MessageDigest.getInstance(eq("SHA-256")))
                .thenThrow(new NoSuchAlgorithmException("missing"));
            assertThrows(IllegalStateException.class,
                () -> projector.project(agent(), latest()));
        }
    }
    
    private Agent agent() {
        Agent result = new Agent();
        result.setNamespaceId("public");
        result.setAgentName("research-agent");
        result.setDisplayName("Research Agent");
        result.setDescription("Find research");
        result.setIconUrl("https://example.com/icon.png");
        AgentProvider provider = new AgentProvider();
        provider.setName("Nacos");
        provider.setUrl("https://nacos.io");
        result.setProvider(provider);
        result.setTags(List.of("research", "assistant"));
        result.setStatus("enable");
        result.setOwner("alice");
        result.setScope("PUBLIC");
        result.setVersionCatalog(catalog());
        result.setUpdateTime(1000L);
        return result;
    }
    
    private AgentVersionCatalog catalog() {
        AgentVersionCatalog result = new AgentVersionCatalog();
        result.setLatestVersion("2.0.0");
        AgentVersionCatalogEntry latest = new AgentVersionCatalogEntry();
        latest.setVersion("2.0.0");
        latest.setLabels(List.of("latest"));
        latest.setProtocols(Arrays.asList("a2a", "acme-rpc", "a2a", null, ""));
        AgentVersionCatalogEntry old = new AgentVersionCatalogEntry();
        old.setVersion("1.0.0");
        old.setLabels(Collections.emptyList());
        old.setProtocols(List.of("a2a"));
        result.setOnlineVersions(Arrays.asList(latest, null, old));
        return result;
    }
    
    private AgentVersionDetail latest() {
        AgentVersionDetail result = new AgentVersionDetail();
        result.setNamespaceId("public");
        result.setAgentName("research-agent");
        result.setVersion("2.0.0");
        result.setStatus("online");
        result.setCallInterfaces(Collections.emptyList());
        result.setContentDigest("sha256:content");
        result.setUpdateTime(2000L);
        return result;
    }
    
    private AgentCard a2aCard() {
        return a2aCard("research-agent", "2.0.0");
    }
    
    private AgentCard a2aCard(String name, String version) {
        AgentCard result = new AgentCard();
        result.setName(name);
        result.setVersion(version);
        result.setDescription("Research card");
        result.setUrl("https://example.com/a2a");
        result.setPreferredTransport("HTTP+JSON");
        result.setProtocolVersion("0.3.0");
        AgentCapabilities capabilities = new AgentCapabilities();
        capabilities.setStreaming(true);
        capabilities.setPushNotifications(true);
        capabilities.setStateTransitionHistory(true);
        capabilities.setExtendedAgentCard(true);
        AgentExtension extension = new AgentExtension();
        extension.setUri("urn:extension");
        capabilities.setExtensions(Arrays.asList(extension, null));
        result.setCapabilities(capabilities);
        AgentSkill skill = new AgentSkill();
        skill.setId("research");
        skill.setName("Research Skill");
        skill.setDescription("Search publications");
        skill.setTags(Arrays.asList("academic", null));
        skill.setExamples(List.of("Search journals"));
        result.setSkills(Arrays.asList(skill, null));
        return result;
    }
    
    private AgentCallInterface callInterface(String protocol, Object descriptor) {
        AgentCallInterface result = new AgentCallInterface();
        result.setProtocol(protocol);
        result.setNativeDescriptor(descriptor);
        return result;
    }
    
    public static class FailingDescriptor {
        
        public String getValue() {
            throw new IllegalStateException("cannot serialize");
        }
    }
}
