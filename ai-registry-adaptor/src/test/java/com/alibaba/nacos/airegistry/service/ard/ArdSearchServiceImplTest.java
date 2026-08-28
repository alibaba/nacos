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

package com.alibaba.nacos.airegistry.service.ard;

import com.alibaba.nacos.ai.constant.AiResourceConstants;
import com.alibaba.nacos.ai.constant.Constants;
import com.alibaba.nacos.ai.model.AiResource;
import com.alibaba.nacos.ai.model.AiResourceVersion;
import com.alibaba.nacos.ai.model.search.AiResourceSearchDocument;
import com.alibaba.nacos.ai.model.search.AiResourceSearchHit;
import com.alibaba.nacos.ai.service.mcp.McpLifecycleOperationService;
import com.alibaba.nacos.ai.service.search.AiResourceEmbeddingService;
import com.alibaba.nacos.ai.service.search.AiResourceSearchConstants;
import com.alibaba.nacos.ai.service.search.AiResourceSearchRepository;
import com.alibaba.nacos.ai.service.search.AiResourceSearchService;
import com.alibaba.nacos.ai.service.search.AiResourceSearchTypeHandler;
import com.alibaba.nacos.ai.service.search.AiResourceSearchTypeHandlerRegistry;
import com.alibaba.nacos.ai.service.search.AiResourceIndexProjection;
import com.alibaba.nacos.ai.service.search.AiResourceIndexSourcePage;
import com.alibaba.nacos.ai.service.resource.AiResourceManager;
import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.api.ai.model.mcp.McpServerDetailInfo;
import com.alibaba.nacos.api.ai.model.mcp.registry.ServerVersionDetail;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.airegistry.constant.ArdProtocolConstants;
import com.alibaba.nacos.airegistry.model.ard.ArdCatalog;
import com.alibaba.nacos.airegistry.model.ard.ArdExploreRequest;
import com.alibaba.nacos.airegistry.model.ard.ArdExploreResponse;
import com.alibaba.nacos.airegistry.model.ard.ArdExploreResultType;
import com.alibaba.nacos.airegistry.model.ard.ArdFacetRequest;
import com.alibaba.nacos.airegistry.model.ard.ArdListResponse;
import com.alibaba.nacos.airegistry.model.ard.ArdSearchFilter;
import com.alibaba.nacos.airegistry.model.ard.ArdSearchQuery;
import com.alibaba.nacos.airegistry.model.ard.ArdSearchRequest;
import com.alibaba.nacos.airegistry.model.ard.ArdSearchResponse;
import com.alibaba.nacos.airegistry.model.ard.ArdSearchResult;
import com.alibaba.nacos.plugin.ai.vector.AiResourceVectorHit;
import com.alibaba.nacos.plugin.ai.vector.spi.AiResourceVectorIndex;
import com.alibaba.nacos.plugin.visibility.constant.VisibilityConstants;
import com.alibaba.nacos.sys.env.EnvUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.networknt.schema.Error;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.sql.Timestamp;
import java.io.InputStream;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link ArdSearchServiceImpl}.
 *
 * @author nacos
 */
@ExtendWith(MockitoExtension.class)
class ArdSearchServiceImplTest {
    
    private static final String RANKING_ENABLED_KEY =
        "nacos.ai.resource.search.ranking.enhanced.enabled";
    
    private static final String CATALOG_BASE_URL_KEY = "nacos.ai.ard.catalog.base-url";
    
    private static final String CATALOG_TRUST_IDENTITY_KEY =
        "nacos.ai.ard.catalog.trust.identity";
    
    private static final String CATALOG_TRUST_IDENTITY_TYPE_KEY =
        "nacos.ai.ard.catalog.trust.identity-type";
    
    @Mock
    private AiResourceManager resourceManager;
    
    @Mock
    private McpLifecycleOperationService mcpServerOperationService;
    
    @Mock
    private AiResourceSearchRepository repository;
    
    @Mock
    private AiResourceEmbeddingService embeddingService;
    
    @Mock
    private AiResourceVectorIndex vectorIndex;
    
    @AfterEach
    void tearDown() {
        System.clearProperty(RANKING_ENABLED_KEY);
        System.clearProperty(CATALOG_BASE_URL_KEY);
        System.clearProperty(ArdProtocolConstants.KEY_CATALOG_HOST_IDENTIFIER);
        System.clearProperty(CATALOG_TRUST_IDENTITY_KEY);
        System.clearProperty(CATALOG_TRUST_IDENTITY_TYPE_KEY);
        EnvUtil.setContextPath(null);
        RequestContextHolder.resetRequestAttributes();
    }
    
    @Test
    void searchShouldReturnPersistedLatestSkillEntry() throws Exception {
        ArdSearchServiceImpl service = service();
        MockHttpServletRequest servletRequest =
            new MockHttpServletRequest("POST", "/nacos/v3/ai/ard/search");
        servletRequest.setScheme("http");
        servletRequest.setServerName("localhost");
        servletRequest.setServerPort(8850);
        servletRequest.setContextPath("/nacos");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(servletRequest));
        when(vectorIndex.available()).thenReturn(false);
        when(repository.searchChunks(eq("public"), eq("api"), eq(List.of("skill")),
            eq(10001)))
            .thenReturn(List.of(hit(100L, 1.0D)));
        when(repository.findEntriesByIds(anyCollection())).thenReturn(List.of(entry()));
        when(resourceManager.findMeta("public", "api-helper", Constants.Skills.RESOURCE_TYPE_SKILL))
            .thenReturn(meta("1.0.0"));
        when(resourceManager.findVersion("public", "api-helper",
            Constants.Skills.RESOURCE_TYPE_SKILL, "1.0.0")).thenReturn(onlineVersion("1.0.0"));
        
        ArdSearchResponse response = service.search(request("api",
            Map.of("type", (Object) List.of(ArdProtocolConstants.MEDIA_TYPE_SKILL_PACKAGE))));
        
        assertEquals(1, response.getResults().size());
        ArdSearchResult result = response.getResults().get(0);
        assertEquals("api-helper", result.getDisplayName());
        assertEquals(ArdProtocolConstants.MEDIA_TYPE_SKILL_PACKAGE, result.getType());
        assertEquals("http://localhost:8850/nacos/v3/ai/ard/artifacts"
            + "?namespaceId=public&resourceType=skill&resourceName=api-helper&version=1.0.0",
            result.getUrl());
        assertEquals("urn:air:nacos:n1_cHVibGlj:n1_c2tpbGw:n1_YXBpLWhlbHBlcg",
            result.getIdentifier());
        assertTrue(URI.create(result.getSource()).isAbsolute());
        assertEquals("skill", result.getMetadata().get("resourceType"));
        assertEquals("SKILL.md", result.getMetadata().get("entrypoint"));
        assertNull(result.getTrustManifest());
        JsonNode serialized = JacksonUtils.toObj(JacksonUtils.toJson(result), JsonNode.class);
        assertTrue(serialized.get("score").isIntegralNumber());
        assertTrue(response.getReferrals().isEmpty());
    }
    
    @Test
    void searchShouldUseVectorRecallWhenAvailable() throws Exception {
        ArdSearchServiceImpl service = service();
        double[] vector = new double[] {1.0D};
        when(vectorIndex.available()).thenReturn(true);
        when(embeddingService.model()).thenReturn("test-model");
        when(embeddingService.embed("api")).thenReturn(vector);
        when(vectorIndex.search(eq("public"), eq("test-model"), eq(vector), eq(List.of("skill")),
            eq(10001)))
            .thenReturn(List.of(vectorHit(100L, 0.9D)));
        when(repository.searchChunks(eq("public"), eq("api"), eq(List.of("skill")),
            eq(10001)))
            .thenReturn(List.of());
        when(repository.findEntriesByIds(anyCollection())).thenReturn(List.of(entry()));
        when(resourceManager.findMeta("public", "api-helper", Constants.Skills.RESOURCE_TYPE_SKILL))
            .thenReturn(meta("1.0.0"));
        when(resourceManager.findVersion("public", "api-helper",
            Constants.Skills.RESOURCE_TYPE_SKILL, "1.0.0")).thenReturn(onlineVersion("1.0.0"));
        
        ArdSearchResponse response = service.search(request("api",
            Map.of("metadata.resourceType", (Object) "skill")));
        
        assertEquals(1, response.getResults().size());
        assertEquals(100, response.getResults().get(0).getScore());
    }
    
    @Test
    void searchShouldPreferHighValueChunkTypeOverContentChunk() throws Exception {
        ArdSearchServiceImpl service = service();
        when(vectorIndex.available()).thenReturn(false);
        when(repository.searchChunks(eq("public"), eq("avatar"), eq(List.of("skill")),
            eq(10001)))
            .thenReturn(List.of(
                hit(101L, 1.0D, "generic-video",
                    AiResourceSearchConstants.CHUNK_TYPE_SKILL_CONTENT),
                hit(102L, 0.8D, "avatar-tool",
                    AiResourceSearchConstants.CHUNK_TYPE_SEARCH_INTENT)));
        when(repository.findEntriesByIds(anyCollection()))
            .thenReturn(List.of(entry(101L, "generic-video"), entry(102L, "avatar-tool")));
        when(resourceManager.findMeta("public", "generic-video",
            Constants.Skills.RESOURCE_TYPE_SKILL)).thenReturn(meta("generic-video", "1.0.0"));
        when(resourceManager.findMeta("public", "avatar-tool",
            Constants.Skills.RESOURCE_TYPE_SKILL)).thenReturn(meta("avatar-tool", "1.0.0"));
        when(resourceManager.findVersion(eq("public"), any(),
            eq(Constants.Skills.RESOURCE_TYPE_SKILL),
            eq("1.0.0"))).thenReturn(onlineVersion("1.0.0"));
        
        ArdSearchResponse response = service.search(request("avatar",
            Map.of("type", (Object) List.of(ArdProtocolConstants.MEDIA_TYPE_SKILL_PACKAGE))));
        
        assertEquals(2, response.getResults().size());
        assertEquals("avatar-tool", response.getResults().get(0).getDisplayName());
    }
    
    @Test
    void searchShouldUseMaxScoreWhenEnhancedRankingDisabled() throws Exception {
        System.setProperty(RANKING_ENABLED_KEY, "false");
        ArdSearchServiceImpl service = service();
        when(vectorIndex.available()).thenReturn(false);
        when(repository.searchChunks(eq("public"), eq("api"), eq(List.of("skill")),
            eq(10001)))
            .thenReturn(List.of(hit(100L, 0.4D), hit(100L, 0.9D)));
        when(repository.findEntriesByIds(anyCollection())).thenReturn(List.of(entry()));
        when(resourceManager.findMeta("public", "api-helper", Constants.Skills.RESOURCE_TYPE_SKILL))
            .thenReturn(meta("1.0.0"));
        when(resourceManager.findVersion("public", "api-helper",
            Constants.Skills.RESOURCE_TYPE_SKILL, "1.0.0")).thenReturn(onlineVersion("1.0.0"));
        
        ArdSearchResponse response = service.search(request("api",
            Map.of("type", (Object) List.of(ArdProtocolConstants.MEDIA_TYPE_SKILL_PACKAGE))));
        
        assertEquals(1, response.getResults().size());
        assertEquals(100, response.getResults().get(0).getScore());
    }
    
    @Test
    void searchShouldApplyStructuredMetadataFilters() throws Exception {
        ArdSearchServiceImpl service = service();
        when(vectorIndex.available()).thenReturn(false);
        when(repository.searchChunks(eq("public"), eq("api"),
            eq(List.of("agent", "skill", "prompt", "mcp")), eq(10001)))
            .thenReturn(List.of(hit(100L, 1.0D)));
        when(repository.findEntriesByIds(anyCollection())).thenReturn(List.of(entry()));
        when(resourceManager.findMeta("public", "api-helper", Constants.Skills.RESOURCE_TYPE_SKILL))
            .thenReturn(meta("1.0.0"));
        when(resourceManager.findVersion("public", "api-helper",
            Constants.Skills.RESOURCE_TYPE_SKILL, "1.0.0")).thenReturn(onlineVersion("1.0.0"));
        
        ArdSearchResponse response = service.search(request("api",
            Map.of("metadata.inputTypes", (Object) List.of("json"),
                "metadata.riskLevel", (Object) "low")));
        
        assertEquals(1, response.getResults().size());
    }
    
    @Test
    void searchShouldApplyFieldPathFilters() throws Exception {
        ArdSearchServiceImpl service = service();
        when(vectorIndex.available()).thenReturn(false);
        when(repository.searchChunks(eq("public"), eq("api"), eq(List.of("skill")),
            eq(10001)))
            .thenReturn(List.of(hit(100L, 1.0D)));
        when(repository.findEntriesByIds(anyCollection())).thenReturn(List.of(entry()));
        when(resourceManager.findMeta("public", "api-helper", Constants.Skills.RESOURCE_TYPE_SKILL))
            .thenReturn(meta("1.0.0"));
        when(resourceManager.findVersion("public", "api-helper",
            Constants.Skills.RESOURCE_TYPE_SKILL, "1.0.0")).thenReturn(onlineVersion("1.0.0"));
        
        ArdSearchResponse response = service.search(requestWithFilters("api",
            List.of(filter("metadata.resourceType", List.of("skill")),
                filter("metadata.inputTypes", "json"), filter("version", "1.0.0"),
                filter("displayName", "api"))));
        
        assertEquals(1, response.getResults().size());
    }
    
    @Test
    void searchShouldPageResultsWithPageToken() throws Exception {
        ArdSearchServiceImpl service = service();
        when(vectorIndex.available()).thenReturn(false);
        when(repository.searchChunks(eq("public"), eq("api"), eq(List.of("skill")),
            eq(10001)))
            .thenReturn(List.of(hit(101L, 1.0D, "api-one"), hit(102L, 0.9D, "api-two"),
                hit(103L, 0.8D, "api-three")));
        when(repository.findEntriesByIds(anyCollection()))
            .thenReturn(List.of(entry(101L, "api-one"), entry(102L, "api-two"),
                entry(103L, "api-three")));
        when(resourceManager.findMeta("public", "api-one", Constants.Skills.RESOURCE_TYPE_SKILL))
            .thenReturn(meta("api-one", "1.0.0"));
        when(resourceManager.findMeta("public", "api-two", Constants.Skills.RESOURCE_TYPE_SKILL))
            .thenReturn(meta("api-two", "1.0.0"));
        when(resourceManager.findMeta("public", "api-three", Constants.Skills.RESOURCE_TYPE_SKILL))
            .thenReturn(meta("api-three", "1.0.0"));
        when(resourceManager.findVersion(eq("public"), any(),
            eq(Constants.Skills.RESOURCE_TYPE_SKILL),
            eq("1.0.0"))).thenReturn(onlineVersion("1.0.0"));
        
        ArdSearchRequest firstRequest = request("api",
            Map.of("type", (Object) List.of(ArdProtocolConstants.MEDIA_TYPE_SKILL_PACKAGE)));
        firstRequest.setPageSize(2);
        ArdSearchResponse firstPage = service.search(firstRequest);
        
        assertEquals(2, firstPage.getResults().size());
        assertEquals("api-one", firstPage.getResults().get(0).getDisplayName());
        assertEquals("api-two", firstPage.getResults().get(1).getDisplayName());
        assertNotNull(firstPage.getPageToken());
        
        ArdSearchRequest secondRequest = request("api",
            Map.of("type", (Object) List.of(ArdProtocolConstants.MEDIA_TYPE_SKILL_PACKAGE)));
        secondRequest.setPageSize(2);
        secondRequest.setPageToken(firstPage.getPageToken());
        ArdSearchResponse secondPage = service.search(secondRequest);
        
        assertEquals(1, secondPage.getResults().size());
        assertEquals("api-three", secondPage.getResults().get(0).getDisplayName());
        assertNull(secondPage.getPageToken());
    }
    
    @Test
    void searchShouldRejectInvalidPageToken() {
        ArdSearchServiceImpl service = service();
        ArdSearchRequest request = request("api",
            Map.of("type", (Object) List.of(ArdProtocolConstants.MEDIA_TYPE_SKILL_PACKAGE)));
        request.setPageToken("broken-token");
        
        assertThrows(NacosException.class, () -> service.search(request));
    }
    
    @Test
    void searchShouldSelectDeterministicAgentRepresentationWithoutDuplicatingIdentifier()
        throws Exception {
        ArdSearchServiceImpl service = serviceWithAllResourcesCurrent();
        AiResourceSearchDocument agent = agentEntry(400L, "research-agent",
            List.of("a2a-agent-card", "nacos-agent"), "nacos-agent");
        when(vectorIndex.available()).thenReturn(false);
        when(repository.searchChunks(eq("public"), eq("research"), anyList(), eq(10001)))
            .thenReturn(List.of(hit(400L, 1.0D, "research-agent")));
        when(repository.findEntriesByIds(anyCollection())).thenReturn(List.of(agent));
        
        ArdSearchResponse defaultResponse = service.search(request("research", Map.of()));
        ArdSearchResponse a2aResponse = service.search(request("research",
            Map.of("type", (Object) ArdProtocolConstants.MEDIA_TYPE_A2A_AGENT_CARD)));
        ArdSearchResponse nacosResponse = service.search(request("research",
            Map.of("type", (Object) ArdProtocolConstants.MEDIA_TYPE_NACOS_AGENT)));
        
        assertEquals(1, defaultResponse.getResults().size());
        assertEquals(ArdProtocolConstants.MEDIA_TYPE_NACOS_AGENT,
            defaultResponse.getResults().get(0).getType());
        assertEquals(ArdProtocolConstants.MEDIA_TYPE_A2A_AGENT_CARD,
            a2aResponse.getResults().get(0).getType());
        assertEquals(ArdProtocolConstants.MEDIA_TYPE_NACOS_AGENT,
            nacosResponse.getResults().get(0).getType());
        assertEquals(defaultResponse.getResults().get(0).getIdentifier(),
            a2aResponse.getResults().get(0).getIdentifier());
        assertTrue(defaultResponse.getResults().get(0).getUrl().contains(
            "contentDigest=sha256%3Aresearch-agent"));
        assertTrue(defaultResponse.getResults().get(0).getUrl().endsWith(
            "representation=nacos-agent"));
        assertTrue(a2aResponse.getResults().get(0).getUrl().endsWith(
            "representation=a2a-agent-card"));
    }
    
    @Test
    void searchShouldPreferA2aForPureA2aAndFilterUnavailableRepresentation() throws Exception {
        ArdSearchServiceImpl service = serviceWithAllResourcesCurrent();
        AiResourceSearchDocument pureA2a = agentEntry(401L, "pure-a2a",
            List.of("a2a-agent-card", "nacos-agent"), "a2a-agent-card");
        AiResourceSearchDocument custom = agentEntry(402L, "custom-agent",
            List.of("nacos-agent"), "nacos-agent");
        when(vectorIndex.available()).thenReturn(false);
        when(repository.searchChunks(eq("public"), eq("agent"), eq(List.of("agent")),
            eq(10001))).thenReturn(List.of(hit(401L, 1.0D), hit(402L, 0.9D)));
        when(repository.findEntriesByIds(anyCollection())).thenReturn(List.of(pureA2a, custom));
        
        ArdSearchResponse defaultResponse = service.search(request("agent",
            Map.of("metadata.resourceType", (Object) "agent")));
        ArdSearchResponse a2aResponse = service.search(request("agent",
            Map.of("type", (Object) ArdProtocolConstants.MEDIA_TYPE_A2A_AGENT_CARD)));
        
        assertEquals(2, defaultResponse.getResults().size());
        assertEquals(ArdProtocolConstants.MEDIA_TYPE_A2A_AGENT_CARD,
            resultByName(defaultResponse, "pure-a2a").getType());
        assertEquals(ArdProtocolConstants.MEDIA_TYPE_NACOS_AGENT,
            resultByName(defaultResponse, "custom-agent").getType());
        assertEquals(1, a2aResponse.getResults().size());
        assertEquals("pure-a2a", a2aResponse.getResults().get(0).getDisplayName());
    }
    
    @Test
    void searchShouldKeepNonAgentResourcesWhenMediaTypeFilterIsMixed() throws Exception {
        ArdSearchServiceImpl service = serviceWithAllResourcesCurrent();
        AiResourceSearchDocument agent = agentEntry(403L, "a2a-agent",
            List.of("a2a-agent-card", "nacos-agent"), "a2a-agent-card");
        AiResourceSearchDocument skill = entry(404L, "research-skill");
        when(vectorIndex.available()).thenReturn(false);
        when(repository.searchChunks(eq("public"), eq("research"),
            eq(List.of("agent", "skill")), eq(10001)))
            .thenReturn(List.of(hit(403L, 1.0D), hit(404L, 0.9D)));
        when(repository.findEntriesByIds(anyCollection())).thenReturn(List.of(agent, skill));
        
        ArdSearchResponse response = service.search(request("research", Map.of("type",
            (Object) List.of(ArdProtocolConstants.MEDIA_TYPE_A2A_AGENT_CARD,
                ArdProtocolConstants.MEDIA_TYPE_SKILL_PACKAGE))));
        
        assertEquals(2, response.getResults().size());
        assertTrue(response.getResults().stream().map(ArdSearchResult::getType)
            .anyMatch(ArdProtocolConstants.MEDIA_TYPE_A2A_AGENT_CARD::equals));
        assertTrue(response.getResults().stream().map(ArdSearchResult::getType)
            .anyMatch(ArdProtocolConstants.MEDIA_TYPE_SKILL_PACKAGE::equals));
    }
    
    @Test
    void searchShouldResolveLegacyAgentRepresentationMetadata() throws Exception {
        ArdSearchServiceImpl service = serviceWithAllResourcesCurrent();
        AiResourceSearchDocument pureA2a = agentEntry(405L, "legacy-a2a",
            Map.of("artifactKinds", List.of("a2a-agent-card", "nacos-agent"),
                "latestProtocols", "A2A"));
        AiResourceSearchDocument custom = agentEntry(406L, "legacy-custom",
            Map.of("artifactKinds", List.of("nacos-agent")));
        AiResourceSearchDocument invalidPrimary = agentEntry(407L, "legacy-primary",
            Map.of("artifactKinds", List.of("nacos-agent"),
                "primaryArtifactKind", "a2a-agent-card"));
        when(vectorIndex.available()).thenReturn(false);
        when(repository.searchChunks(eq("public"), eq("legacy"), anyList(), eq(10001)))
            .thenReturn(List.of(hit(405L, 1.0D), hit(406L, 0.9D), hit(407L, 0.8D)));
        when(repository.findEntriesByIds(anyCollection()))
            .thenReturn(List.of(pureA2a, custom, invalidPrimary));
        
        ArdSearchResponse response = service.search(request("legacy", Map.of()));
        
        assertEquals(ArdProtocolConstants.MEDIA_TYPE_A2A_AGENT_CARD,
            resultByName(response, "legacy-a2a").getType());
        assertEquals(ArdProtocolConstants.MEDIA_TYPE_NACOS_AGENT,
            resultByName(response, "legacy-custom").getType());
        assertEquals(ArdProtocolConstants.MEDIA_TYPE_NACOS_AGENT,
            resultByName(response, "legacy-primary").getType());
    }
    
    @Test
    void searchShouldRejectAgentWithoutArtifactRepresentation() {
        ArdSearchServiceImpl service = serviceWithAllResourcesCurrent();
        AiResourceSearchDocument agent = agentEntry(408L, "invalid-agent",
            Map.of("artifactKinds", List.of(), "primaryArtifactKind", "nacos-agent"));
        when(vectorIndex.available()).thenReturn(false);
        when(repository.searchChunks(eq("public"), eq("invalid"), anyList(), eq(10001)))
            .thenReturn(List.of(hit(408L, 1.0D)));
        when(repository.findEntriesByIds(anyCollection())).thenReturn(List.of(agent));
        
        assertThrows(IllegalStateException.class,
            () -> service.search(request("invalid", Map.of())));
    }
    
    @Test
    void searchShouldSkipEntryWhenVersionIsNotLatest() throws Exception {
        ArdSearchServiceImpl service = service();
        when(vectorIndex.available()).thenReturn(false);
        when(repository.searchChunks(eq("public"), eq("api"), eq(List.of("skill")),
            eq(10001)))
            .thenReturn(List.of(hit(100L, 1.0D)));
        when(repository.findEntriesByIds(any(Collection.class))).thenReturn(List.of(entry()));
        when(resourceManager.findMeta("public", "api-helper", Constants.Skills.RESOURCE_TYPE_SKILL))
            .thenReturn(meta("1.0.1"));
        
        ArdSearchResponse response = service.search(request("api",
            Map.of("type", (Object) List.of(ArdProtocolConstants.MEDIA_TYPE_SKILL_PACKAGE))));
        
        assertTrue(response.getResults().isEmpty());
    }
    
    @Test
    void searchShouldAcceptAllStandardFederationModesWithLocalFallback() throws Exception {
        ArdSearchServiceImpl service = service();
        for (String federation : List.of(ArdProtocolConstants.FEDERATION_AUTO,
            ArdProtocolConstants.FEDERATION_REFERRALS, ArdProtocolConstants.FEDERATION_NONE)) {
            ArdSearchRequest request = request("api", Map.of("type", (Object) "unsupported"));
            request.setFederation(federation);
            ArdSearchResponse response = service.search(request);
            assertTrue(response.getResults().isEmpty());
            assertTrue(response.getReferrals().isEmpty());
        }
        ArdSearchRequest omitted = request("api", Map.of("type", (Object) "unsupported"));
        omitted.setFederation(null);
        assertTrue(service.search(omitted).getResults().isEmpty());
    }
    
    @Test
    void searchShouldRejectUnknownFederation() {
        ArdSearchServiceImpl service = service();
        ArdSearchRequest request = request("api", Map.of("type", (Object) "unsupported"));
        request.setFederation("upstream-only");
        
        assertThrows(NacosException.class, () -> service.search(request));
    }
    
    @Test
    void searchShouldAcceptPinnedOpenApiNestedFieldPathExample() throws Exception {
        ArdSearchServiceImpl service = service();
        ArdSearchRequest request = request("api",
            Map.of("type", (Object) List.of(ArdProtocolConstants.MEDIA_TYPE_MCP),
                "trustManifest.attestations.type", (Object) List.of("SOC2-Type2")));
        
        ArdSearchResponse response = service.search(request);
        
        assertTrue(response.getResults().isEmpty());
        verifyNoInteractions(repository, resourceManager, mcpServerOperationService);
    }
    
    @Test
    void searchShouldAcceptExtensionFieldPathSegments() throws Exception {
        ArdSearchServiceImpl service = service();
        ArdSearchRequest request = request("api",
            Map.of("vendor_metadata.2026-认证", (Object) List.of("verified")));
        
        ArdSearchResponse response = service.search(request);
        
        assertTrue(response.getResults().isEmpty());
    }
    
    @Test
    void searchShouldRejectMalformedFilterKey() {
        ArdSearchServiceImpl service = service();
        ArdSearchRequest request = request("api",
            Map.of("metadata..unknown", (Object) List.of("x")));
        
        assertThrows(NacosException.class, () -> service.search(request));
    }
    
    @Test
    void searchShouldRejectBlankFieldPathFilter() {
        ArdSearchServiceImpl service = service();
        ArdSearchRequest request = requestWithFilters("api",
            List.of(filter(null, List.of("skill"))));
        
        assertThrows(NacosException.class, () -> service.search(request));
    }
    
    @Test
    void exploreShouldReturnFacetBuckets() throws Exception {
        ArdSearchServiceImpl service = service();
        when(repository.scanEnabledEntries("public", List.of("skill"), 0L, 500))
            .thenReturn(List.of(entry(101L, "api-one"), entry(102L, "api-two")));
        when(resourceManager.findMeta("public", "api-one", Constants.Skills.RESOURCE_TYPE_SKILL))
            .thenReturn(meta("api-one", "1.0.0"));
        when(resourceManager.findMeta("public", "api-two", Constants.Skills.RESOURCE_TYPE_SKILL))
            .thenReturn(meta("api-two", "1.0.0"));
        when(resourceManager.findVersion(eq("public"), any(),
            eq(Constants.Skills.RESOURCE_TYPE_SKILL),
            eq("1.0.0"))).thenReturn(onlineVersion("1.0.0"));
        
        ArdExploreResponse response = service.explore(exploreRequest("public",
            Map.of("type", (Object) ArdProtocolConstants.MEDIA_TYPE_SKILL_PACKAGE),
            List.of(facet("tags", 10, 1))));
        
        assertEquals(1, response.getFacets().size());
        assertEquals("api", response.getFacets().get("tags").getBuckets().get(0).getValue());
        assertEquals(2, response.getFacets().get("tags").getBuckets().get(0).getCount());
        JsonNode serialized =
            JacksonUtils.toObj(JacksonUtils.toJson(response), JsonNode.class);
        assertEquals("facets", serialized.get("resultType").asText());
        assertTrue(serialized.has("facets"));
    }
    
    @Test
    void exploreShouldCountAgentBySelectedPrimaryRepresentation() throws Exception {
        ArdSearchServiceImpl service = serviceWithAllResourcesCurrent();
        AiResourceSearchDocument pureA2a = agentEntry(410L, "pure-a2a",
            List.of("a2a-agent-card", "nacos-agent"), "a2a-agent-card");
        AiResourceSearchDocument multiProtocol = agentEntry(411L, "multi-agent",
            List.of("a2a-agent-card", "nacos-agent"), "nacos-agent");
        AiResourceSearchDocument skill = entry(412L, "helper-skill");
        when(repository.scanEnabledEntries("public", List.of("agent", "skill", "prompt", "mcp"),
            0L, 500)).thenReturn(List.of(pureA2a, multiProtocol, skill));
        
        ArdExploreResponse response = service.explore(exploreRequest("public", Map.of(),
            List.of(facet("type", 10, 1))));
        
        Map<String, Integer> counts = response.getFacets().get("type").getBuckets().stream()
            .collect(java.util.stream.Collectors.toMap(
                ArdExploreResponse.FacetBucket::getValue,
                ArdExploreResponse.FacetBucket::getCount));
        assertEquals(1, counts.get(ArdProtocolConstants.MEDIA_TYPE_A2A_AGENT_CARD));
        assertEquals(1, counts.get(ArdProtocolConstants.MEDIA_TYPE_NACOS_AGENT));
        assertEquals(1, counts.get(ArdProtocolConstants.MEDIA_TYPE_SKILL_PACKAGE));
    }
    
    @Test
    void exploreShouldCountAllMatchingAgentsAsExplicitA2aRepresentation() throws Exception {
        ArdSearchServiceImpl service = serviceWithAllResourcesCurrent();
        AiResourceSearchDocument pureA2a = agentEntry(413L, "pure-a2a",
            List.of("a2a-agent-card", "nacos-agent"), "a2a-agent-card");
        AiResourceSearchDocument multiProtocol = agentEntry(414L, "multi-agent",
            List.of("a2a-agent-card", "nacos-agent"), "nacos-agent");
        when(repository.scanEnabledEntries("public", List.of("agent"), 0L, 500))
            .thenReturn(List.of(pureA2a, multiProtocol));
        
        ArdExploreResponse response = service.explore(exploreRequest("public",
            Map.of("type", (Object) ArdProtocolConstants.MEDIA_TYPE_A2A_AGENT_CARD),
            List.of(facet("type", 10, 1))));
        
        assertEquals(1, response.getFacets().get("type").getBuckets().size());
        assertEquals(ArdProtocolConstants.MEDIA_TYPE_A2A_AGENT_CARD,
            response.getFacets().get("type").getBuckets().get(0).getValue());
        assertEquals(2, response.getFacets().get("type").getBuckets().get(0).getCount());
    }
    
    @Test
    void exploreShouldFallbackUnknownAgentRepresentationAndLimitTiedTypeFacet()
        throws Exception {
        ArdSearchServiceImpl service = serviceWithAllResourcesCurrent();
        AiResourceSearchDocument missingPrimary = agentEntry(415L, "missing-primary",
            Map.of("artifactKinds", List.of("nacos-agent")));
        AiResourceSearchDocument unknownPrimary = agentEntry(416L, "unknown-primary",
            Map.of("artifactKinds", List.of("nacos-agent"),
                "primaryArtifactKind", "unknown"));
        AiResourceSearchDocument skillOne = entry(417L, "skill-one");
        AiResourceSearchDocument skillTwo = entry(418L, "skill-two");
        AiResourceSearchDocument prompt = entry(419L, "prompt-one");
        prompt.setResourceType(AiResourceConstants.RESOURCE_TYPE_PROMPT);
        prompt.setMetadata(JacksonUtils.toJson(Map.of("resourceType", "prompt")));
        when(repository.scanEnabledEntries("public",
            List.of("agent", "skill", "prompt", "mcp"), 0L, 500))
            .thenReturn(List.of(missingPrimary, unknownPrimary, skillOne, skillTwo, prompt));
        
        ArdExploreResponse response = service.explore(exploreRequest("public", Map.of(),
            List.of(facet("type", 1, 1), facet("publisher", 10, 1))));
        
        assertEquals(1, response.getFacets().get("type").getBuckets().size());
        assertEquals(2, response.getFacets().get("type").getBuckets().get(0).getCount());
        assertEquals(3, response.getFacets().get("type").getOtherCount());
        assertEquals(1, response.getFacets().get("publisher").getBuckets().size());
        assertEquals(5, response.getFacets().get("publisher").getBuckets().get(0).getCount());
    }
    
    @Test
    void listShouldFilterOrderAndPageEntries() throws Exception {
        ArdSearchServiceImpl service = service();
        when(repository.scanEnabledEntries("public", List.of("skill"), 0L, 500))
            .thenReturn(List.of(entry(101L, "api-two"), entry(102L, "api-one")));
        when(resourceManager.findMeta("public", "api-one", Constants.Skills.RESOURCE_TYPE_SKILL))
            .thenReturn(meta("api-one", "1.0.0"));
        when(resourceManager.findMeta("public", "api-two", Constants.Skills.RESOURCE_TYPE_SKILL))
            .thenReturn(meta("api-two", "1.0.0"));
        when(resourceManager.findVersion(eq("public"), any(),
            eq(Constants.Skills.RESOURCE_TYPE_SKILL),
            eq("1.0.0"))).thenReturn(onlineVersion("1.0.0"));
        
        ArdListResponse response = service.list("public",
            "type = '" + ArdProtocolConstants.MEDIA_TYPE_SKILL_PACKAGE
                + "' AND createdAfter > '2026-01-01'"
                + " AND updatedAfter > '2026-01-01T00:00:00Z' AND displayName = 'api'",
            "displayName ASC", 1, null);
        
        assertEquals(1, response.getItems().size());
        assertEquals("api-one", response.getItems().get(0).getDisplayName());
        assertNotNull(response.getPageToken());
        JsonNode serialized = JacksonUtils.toObj(JacksonUtils.toJson(response), JsonNode.class);
        assertTrue(serialized.has("items"));
        assertTrue(!serialized.has("results"));
    }
    
    @Test
    void listShouldRejectMalformedFilterInsteadOfSilentlyMisparsing() {
        ArdSearchServiceImpl service = service();
        
        assertThrows(NacosException.class, () -> service.list("public",
            "type=" + ArdProtocolConstants.MEDIA_TYPE_SKILL_PACKAGE + ";displayName=api",
            null, 10, null));
        assertThrows(NacosException.class, () -> service.list("public",
            "displayName > 'api'", null, 10, null));
        assertThrows(NacosException.class, () -> service.list("public",
            "createdAfter = '2026-01-01T00:00:00Z'", null, 10, null));
    }
    
    @Test
    void catalogShouldExposeRegistryAndLocalEntries() throws Exception {
        ArdSearchServiceImpl service = service();
        when(repository.scanEnabledEntries("public",
            List.of("agent", "skill", "prompt", "mcp"), 0L, 500))
            .thenReturn(List.of(entry()));
        when(resourceManager.findMeta("public", "api-helper", Constants.Skills.RESOURCE_TYPE_SKILL))
            .thenReturn(meta("1.0.0"));
        when(resourceManager.findVersion("public", "api-helper",
            Constants.Skills.RESOURCE_TYPE_SKILL, "1.0.0")).thenReturn(onlineVersion("1.0.0"));
        
        ArdCatalog catalog = service.catalog("public");
        
        assertEquals("1.0", catalog.getSpecVersion());
        assertEquals("Nacos AI Registry", catalog.getHost().getDisplayName());
        assertEquals("urn:air:nacos:registry:nacos",
            catalog.getEntries().get(0).getIdentifier());
        assertEquals("api-helper", catalog.getEntries().get(1).getDisplayName());
    }
    
    @Test
    void catalogShouldExposeOneEntryForEachLogicalAgent() throws Exception {
        ArdSearchServiceImpl service = serviceWithAllResourcesCurrent();
        AiResourceSearchDocument agent = agentEntry(420L, "research-agent",
            List.of("a2a-agent-card", "nacos-agent"), "nacos-agent");
        when(repository.scanEnabledEntries("public", List.of("agent", "skill", "prompt", "mcp"),
            0L, 500)).thenReturn(List.of(agent));
        
        ArdCatalog catalog = service.catalog("public");
        
        assertEquals(2, catalog.getEntries().size());
        assertEquals(ArdProtocolConstants.MEDIA_TYPE_NACOS_AGENT,
            catalog.getEntries().get(1).getType());
        assertTrue(catalog.getEntries().get(1).getUrl().contains(
            "representation=nacos-agent"));
        assertCatalogSchemaValid(catalog);
    }
    
    @Test
    void catalogShouldNotTruncateAfterOneHundredEntries() throws Exception {
        List<AiResourceSearchDocument> documents = new ArrayList<>();
        for (long id = 1L; id <= 101L; id++) {
            documents.add(entry(id, "skill-" + id));
        }
        when(repository.scanEnabledEntries("public",
            List.of("agent", "skill", "prompt", "mcp"), 0L, 500))
            .thenReturn(documents);
        when(repository.findEntriesByIds(anyCollection())).thenAnswer(invocation -> {
            Collection<Long> ids = invocation.getArgument(0);
            List<AiResourceSearchDocument> matched = new ArrayList<>();
            for (AiResourceSearchDocument document : documents) {
                if (ids.contains(document.getId())) {
                    matched.add(document);
                }
            }
            return matched;
        });
        when(resourceManager.findMeta(eq("public"), any(),
            eq(Constants.Skills.RESOURCE_TYPE_SKILL)))
            .thenAnswer(invocation -> meta(invocation.getArgument(1), "1.0.0"));
        when(resourceManager.findVersion(eq("public"), any(),
            eq(Constants.Skills.RESOURCE_TYPE_SKILL), eq("1.0.0")))
            .thenReturn(onlineVersion("1.0.0"));
        
        ArdCatalog catalog = service().catalog("public");
        
        assertEquals(102, catalog.getEntries().size());
    }
    
    @Test
    void hostCatalogShouldExposeOnlyRegistryEntry() {
        ArdSearchServiceImpl service = service();
        
        ArdCatalog catalog = service.hostCatalog();
        
        assertEquals("1.0", catalog.getSpecVersion());
        assertEquals("Nacos AI Registry", catalog.getHost().getDisplayName());
        assertEquals(1, catalog.getEntries().size());
        assertEquals("urn:air:nacos:registry:nacos",
            catalog.getEntries().get(0).getIdentifier());
        assertEquals("application/ai-registry+json", catalog.getEntries().get(0).getType());
        assertEquals("/v3/ai/ard", catalog.getEntries().get(0).getUrl());
        verifyNoInteractions(repository, resourceManager, mcpServerOperationService);
    }
    
    @Test
    void catalogShouldUseConfiguredBaseUrlAndHostIdentifier() throws Exception {
        System.setProperty(CATALOG_BASE_URL_KEY, "https://nacos.example.com");
        System.setProperty(ArdProtocolConstants.KEY_CATALOG_HOST_IDENTIFIER, "nacos.example.com");
        EnvUtil.setContextPath("/nacos");
        ArdSearchServiceImpl service = service();
        when(repository.scanEnabledEntries("public",
            List.of("agent", "skill", "prompt", "mcp"), 0L, 500))
            .thenReturn(List.of(entry()));
        when(resourceManager.findMeta("public", "api-helper", Constants.Skills.RESOURCE_TYPE_SKILL))
            .thenReturn(meta("1.0.0"));
        when(resourceManager.findVersion("public", "api-helper",
            Constants.Skills.RESOURCE_TYPE_SKILL, "1.0.0")).thenReturn(onlineVersion("1.0.0"));
        
        ArdCatalog catalog = service.catalog("public");
        
        assertEquals("nacos.example.com", catalog.getHost().getIdentifier());
        assertEquals("urn:air:nacos.example.com:registry:nacos",
            catalog.getEntries().get(0).getIdentifier());
        assertEquals("https://nacos.example.com/v3/ai/ard",
            catalog.getEntries().get(0).getUrl());
        assertEquals("urn:air:nacos.example.com:n1_cHVibGlj:n1_c2tpbGw:"
            + "n1_YXBpLWhlbHBlcg",
            catalog.getEntries().get(1).getIdentifier());
        assertEquals("https://nacos.example.com/v3/ai/ard/artifacts"
            + "?namespaceId=public&resourceType=skill&resourceName=api-helper&version=1.0.0",
            catalog.getEntries().get(1).getUrl());
        assertCatalogSchemaValid(catalog);
    }
    
    @Test
    void catalogShouldEncodePublisherIdentifierWhenHostIdentifierIsNotUrnSafe() throws Exception {
        System.setProperty(ArdProtocolConstants.KEY_CATALOG_HOST_IDENTIFIER,
            "did:web:nacos.example.com");
        
        ArdCatalog catalog = service().hostCatalog();
        
        assertEquals("did:web:nacos.example.com", catalog.getHost().getIdentifier());
        assertEquals("urn:air:n1-ZGlkOndlYjpuYWNvcy5leGFtcGxlLmNvbQ:registry:nacos",
            catalog.getEntries().get(0).getIdentifier());
        assertCatalogSchemaValid(catalog);
    }
    
    @Test
    void catalogShouldIncludeConfiguredTrustIdentity() {
        System.setProperty(CATALOG_TRUST_IDENTITY_KEY, "spiffe://example.com/nacos/ard");
        System.setProperty(CATALOG_TRUST_IDENTITY_TYPE_KEY, "spiffe");
        ArdSearchServiceImpl service = service();
        
        ArdCatalog catalog = service.hostCatalog();
        
        assertEquals("spiffe://example.com/nacos/ard",
            catalog.getHost().getTrustManifest().get("identity"));
        assertEquals("spiffe", catalog.getHost().getTrustManifest().get("identityType"));
    }
    
    @Test
    void catalogIdentifiersShouldBeSchemaValidAndCollisionSafe() throws Exception {
        when(repository.scanEnabledEntries("public",
            List.of("agent", "skill", "prompt", "mcp"), 0L, 500))
            .thenReturn(List.of(entry(201L, "a/b"), entry(202L, "a?b"),
                entry(203L, "技能 名称")));
        when(
            resourceManager.findMeta(eq("public"), any(), eq(Constants.Skills.RESOURCE_TYPE_SKILL)))
            .thenAnswer(invocation -> meta(invocation.getArgument(1), "1.0.0"));
        when(resourceManager.findVersion(eq("public"), any(),
            eq(Constants.Skills.RESOURCE_TYPE_SKILL), eq("1.0.0")))
            .thenReturn(onlineVersion("1.0.0"));
        
        ArdCatalog catalog = service().catalog("public");
        
        assertEquals(3, catalog.getEntries().subList(1, 4).stream()
            .map(entry -> entry.getIdentifier()).distinct().count());
        assertCatalogSchemaValid(catalog);
    }
    
    @Test
    void catalogShouldAssemblePromptAndMcpProtocolFields() throws Exception {
        AiResourceSearchDocument prompt = entry(200L, "avatar prompt");
        prompt.setResourceType(AiResourceConstants.RESOURCE_TYPE_PROMPT);
        prompt.setMetadata(JacksonUtils.toJson(Map.of("resourceType", "prompt")));
        AiResourceSearchDocument mcp = entry(300L, "avatar-server");
        mcp.setResourceType(AiResourceConstants.RESOURCE_TYPE_MCP);
        mcp.setMetadata(JacksonUtils.toJson(Map.of("resourceType", "mcp", "mcpName",
            "avatar-server", "mcpServerId", "mcp/avatar server")));
        when(repository.scanEnabledEntries("public",
            List.of("agent", "skill", "prompt", "mcp"), 0L, 500))
            .thenReturn(List.of(prompt, mcp));
        when(resourceManager.findMeta("public", "avatar prompt",
            AiResourceConstants.RESOURCE_TYPE_PROMPT)).thenReturn(meta("avatar prompt",
                AiResourceConstants.RESOURCE_TYPE_PROMPT, "1.0.0"));
        when(resourceManager.findVersion("public", "avatar prompt",
            AiResourceConstants.RESOURCE_TYPE_PROMPT, "1.0.0")).thenReturn(onlineVersion("1.0.0"));
        McpServerDetailInfo mcpDetail = new McpServerDetailInfo();
        mcpDetail.setName("avatar-server");
        mcpDetail.setEnabled(true);
        mcpDetail.setStatus(AiConstants.Mcp.MCP_STATUS_ACTIVE);
        ServerVersionDetail versionDetail = new ServerVersionDetail();
        versionDetail.setIs_latest(true);
        mcpDetail.setVersionDetail(versionDetail);
        when(mcpServerOperationService.getMcpServerDetail("public", null, "avatar-server",
            "1.0.0")).thenReturn(mcpDetail);
        
        ArdSearchServiceImpl service = service();
        ArdCatalog catalog = service.catalog("public");
        
        assertEquals(ArdProtocolConstants.MEDIA_TYPE_PROMPT,
            catalog.getEntries().get(1).getType());
        assertEquals("/v3/ai/ard/artifacts?namespaceId=public&resourceType=prompt"
            + "&resourceName=avatar+prompt&version=1.0.0",
            catalog.getEntries().get(1).getUrl());
        assertEquals(ArdProtocolConstants.MEDIA_TYPE_MCP,
            catalog.getEntries().get(2).getType());
        assertEquals("/v3/ai/ard/artifacts?namespaceId=public&resourceType=mcp"
            + "&resourceName=avatar-server&version=1.0.0&mcpName=avatar-server",
            catalog.getEntries().get(2).getUrl());
        assertCatalogSchemaValid(catalog);
    }
    
    private ArdSearchServiceImpl service() {
        AiResourceSearchService searchService = new AiResourceSearchService(
            resourceManager, mcpServerOperationService, repository, embeddingService,
            vectorIndex);
        return new ArdSearchServiceImpl(searchService);
    }
    
    private ArdSearchServiceImpl serviceWithAllResourcesCurrent() {
        AiResourceSearchTypeHandlerRegistry registry =
            new AiResourceSearchTypeHandlerRegistry(List.of(new AlwaysCurrentHandler()));
        AiResourceSearchService searchService = new AiResourceSearchService(registry, repository,
            embeddingService, vectorIndex);
        return new ArdSearchServiceImpl(searchService);
    }
    
    private void assertCatalogSchemaValid(ArdCatalog catalog) throws Exception {
        String path =
            "/ard-spec/5fa2f5aef790b478319f6a3b43adf4661b0ed0e0/ai-catalog.schema.json";
        try (InputStream input = getClass().getResourceAsStream(path)) {
            assertNotNull(input);
            Schema schema = SchemaRegistry
                .withDefaultDialect(SpecificationVersion.DRAFT_2020_12).getSchema(input);
            JsonNode document =
                JacksonUtils.toObj(JacksonUtils.toJson(catalog), JsonNode.class);
            List<Error> errors = schema.validate(document);
            assertTrue(errors.isEmpty(), errors.toString());
        }
    }
    
    private ArdSearchRequest request(String text, Map<String, Object> filter) {
        ArdSearchQuery query = new ArdSearchQuery();
        query.setText(text);
        query.setFilter(filter);
        ArdSearchRequest request = new ArdSearchRequest();
        request.setNamespaceId("public");
        request.setQuery(query);
        request.setFederation("none");
        request.setPageSize(10);
        return request;
    }
    
    private ArdSearchRequest requestWithFilters(String text, List<ArdSearchFilter> filters) {
        ArdSearchQuery query = new ArdSearchQuery();
        query.setText(text);
        query.setFilters(filters);
        ArdSearchRequest request = new ArdSearchRequest();
        request.setNamespaceId("public");
        request.setQuery(query);
        request.setFederation("none");
        request.setPageSize(10);
        return request;
    }
    
    private ArdExploreRequest exploreRequest(String namespaceId, Map<String, Object> filter,
        List<ArdFacetRequest> facets) {
        ArdSearchQuery query = new ArdSearchQuery();
        query.setFilter(filter);
        ArdExploreResultType resultType = new ArdExploreResultType();
        resultType.setFacets(facets);
        ArdExploreRequest request = new ArdExploreRequest();
        request.setNamespaceId(namespaceId);
        request.setQuery(query);
        request.setResultType(resultType);
        return request;
    }
    
    private ArdFacetRequest facet(String field, int limit, int minCount) {
        ArdFacetRequest request = new ArdFacetRequest();
        request.setField(field);
        request.setLimit(limit);
        request.setMinCount(minCount);
        return request;
    }
    
    private ArdSearchFilter filter(String fieldPath, Object values) {
        ArdSearchFilter filter = new ArdSearchFilter();
        filter.setFieldPath(fieldPath);
        filter.setValues(values);
        return filter;
    }
    
    private AiResourceSearchHit hit(Long documentId, double score) {
        return hit(documentId, score, "api-helper");
    }
    
    private AiResourceSearchHit hit(Long documentId, double score, String resourceName) {
        return hit(documentId, score, resourceName, null);
    }
    
    private AiResourceSearchHit hit(Long documentId, double score, String resourceName,
        String chunkType) {
        AiResourceSearchHit hit = new AiResourceSearchHit();
        hit.setDocumentId(documentId);
        hit.setChunkId(200L);
        hit.setResourceType(Constants.Skills.RESOURCE_TYPE_SKILL);
        hit.setResourceName(resourceName);
        hit.setResourceVersion("1.0.0");
        hit.setChunkType(chunkType);
        hit.setScore(score);
        return hit;
    }
    
    private AiResourceVectorHit vectorHit(Long documentId, double score) {
        AiResourceVectorHit hit = new AiResourceVectorHit();
        hit.setDocumentId(documentId);
        hit.setChunkId(200L);
        hit.setResourceType(Constants.Skills.RESOURCE_TYPE_SKILL);
        hit.setResourceName("api-helper");
        hit.setResourceVersion("1.0.0");
        hit.setScore(score);
        return hit;
    }
    
    private AiResourceSearchDocument entry() {
        return entry(100L, "api-helper");
    }
    
    private AiResourceSearchDocument entry(long id, String resourceName) {
        AiResourceSearchDocument entry = new AiResourceSearchDocument();
        entry.setId(id);
        entry.setNamespaceId("public");
        entry.setResourceType(Constants.Skills.RESOURCE_TYPE_SKILL);
        entry.setResourceName(resourceName);
        entry.setResourceVersion("1.0.0");
        entry.setDisplayName(resourceName);
        entry.setDescription("Generate API parameter tables");
        entry.setTags(JacksonUtils.toJson(List.of("documentation", "api")));
        entry.setCapabilities(JacksonUtils.toJson(List.of("skill", "documentation")));
        entry.setRepresentativeQueries(JacksonUtils.toJson(List.of("api helper")));
        entry.setMetadata(JacksonUtils.toJson(Map.of("namespaceId", "public",
            "resourceType", "skill", "resourceName", resourceName, "resourceVersion", "1.0.0",
            "entrypoint", "SKILL.md", "inputTypes", List.of("json"), "outputTypes",
            List.of("markdown"), "riskLevel", "low")));
        entry.setStatus(AiResourceSearchConstants.STATUS_ENABLED);
        entry.setGmtCreate(Timestamp.from(Instant.parse("2026-06-28T01:00:00Z")));
        entry.setGmtModified(Timestamp.from(Instant.parse("2026-06-29T01:00:00Z")));
        return entry;
    }
    
    private AiResourceSearchDocument agentEntry(long id, String resourceName,
        List<String> artifactKinds, String primaryArtifactKind) {
        return agentEntry(id, resourceName, Map.of("artifactKinds", artifactKinds,
            "primaryArtifactKind", primaryArtifactKind));
    }
    
    private AiResourceSearchDocument agentEntry(long id, String resourceName,
        Map<String, Object> representationMetadata) {
        AiResourceSearchDocument entry = entry(id, resourceName);
        entry.setResourceType(Constants.Agent.RESOURCE_TYPE_AGENT);
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("namespaceId", "public");
        metadata.put("resourceType", "agent");
        metadata.put("resourceName", resourceName);
        metadata.put("resourceVersion", "1.0.0");
        metadata.put("contentDigest", "sha256:" + resourceName);
        metadata.putAll(representationMetadata);
        entry.setMetadata(JacksonUtils.toJson(metadata));
        return entry;
    }
    
    private ArdSearchResult resultByName(ArdSearchResponse response, String displayName) {
        return response.getResults().stream()
            .filter(result -> displayName.equals(result.getDisplayName())).findFirst()
            .orElseThrow();
    }
    
    private AiResource meta(String latestVersion) {
        return meta("api-helper", latestVersion);
    }
    
    private AiResource meta(String name, String latestVersion) {
        return meta(name, Constants.Skills.RESOURCE_TYPE_SKILL, latestVersion);
    }
    
    private AiResource meta(String name, String resourceType, String latestVersion) {
        AiResource meta = new AiResource();
        meta.setNamespaceId("public");
        meta.setName(name);
        meta.setType(resourceType);
        meta.setStatus(AiResourceConstants.META_STATUS_ENABLE);
        meta.setScope(VisibilityConstants.SCOPE_PUBLIC);
        meta.setVersionInfo(JacksonUtils.toJson(Map.of("labels", Map.of("latest", latestVersion))));
        return meta;
    }
    
    private AiResourceVersion onlineVersion(String versionValue) {
        AiResourceVersion version = new AiResourceVersion();
        version.setNamespaceId("public");
        version.setName("api-helper");
        version.setType(Constants.Skills.RESOURCE_TYPE_SKILL);
        version.setVersion(versionValue);
        version.setStatus(AiResourceConstants.VERSION_STATUS_ONLINE);
        return version;
    }
    
    private static final class AlwaysCurrentHandler implements AiResourceSearchTypeHandler {
        
        @Override
        public Collection<String> resourceTypes() {
            return List.of("agent", "skill", "prompt", "mcp");
        }
        
        @Override
        public AiResourceIndexProjection project(String namespaceId, String resourceType,
            String resourceName, String version) {
            return null;
        }
        
        @Override
        public AiResourceIndexSourcePage scan(String namespaceId, String resourceType, int pageNo,
            int pageSize) {
            return null;
        }
        
        @Override
        public boolean isCurrent(AiResourceSearchDocument document) {
            return true;
        }
        
        @Override
        public boolean exists(String namespaceId, String resourceType, String resourceName) {
            return true;
        }
    }
}
