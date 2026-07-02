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

package com.alibaba.nacos.ai.service.ard;

import com.alibaba.nacos.ai.constant.AiResourceConstants;
import com.alibaba.nacos.ai.constant.Constants;
import com.alibaba.nacos.ai.model.AiResource;
import com.alibaba.nacos.ai.model.AiResourceVersion;
import com.alibaba.nacos.ai.model.ard.ArdEntry;
import com.alibaba.nacos.ai.model.ard.ArdSearchHit;
import com.alibaba.nacos.ai.service.McpServerOperationService;
import com.alibaba.nacos.ai.service.ard.vector.AiResourceVectorIndex;
import com.alibaba.nacos.ai.service.resource.AiResourceManager;
import com.alibaba.nacos.api.ai.model.ard.ArdSearchQuery;
import com.alibaba.nacos.api.ai.model.ard.ArdSearchRequest;
import com.alibaba.nacos.api.ai.model.ard.ArdSearchResponse;
import com.alibaba.nacos.api.ai.model.ard.ArdSearchResult;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.plugin.visibility.constant.VisibilityConstants;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link ArdSearchServiceImpl}.
 *
 * @author nacos
 */
@ExtendWith(MockitoExtension.class)
class ArdSearchServiceImplTest {
    
    private static final String RANKING_ENABLED_KEY =
        "nacos.ai.ard.search.ranking.enhanced.enabled";
    
    @Mock
    private AiResourceManager resourceManager;
    
    @Mock
    private McpServerOperationService mcpServerOperationService;
    
    @Mock
    private ArdIndexRepository repository;
    
    @Mock
    private ArdEmbeddingService embeddingService;
    
    @Mock
    private AiResourceVectorIndex vectorIndex;
    
    @AfterEach
    void tearDown() {
        System.clearProperty(RANKING_ENABLED_KEY);
    }
    
    @Test
    void searchShouldReturnPersistedLatestSkillEntry() throws Exception {
        ArdSearchServiceImpl service = service();
        when(vectorIndex.available()).thenReturn(false);
        when(repository.searchChunks(eq("public"), eq("api"), eq(List.of("skill")), eq(500)))
            .thenReturn(List.of(hit(100L, 1.0D)));
        when(repository.findEntriesByIds(anyCollection())).thenReturn(List.of(entry()));
        when(resourceManager.findMeta("public", "api-helper", Constants.Skills.RESOURCE_TYPE_SKILL))
            .thenReturn(meta("1.0.0"));
        when(resourceManager.findVersion("public", "api-helper",
            Constants.Skills.RESOURCE_TYPE_SKILL, "1.0.0")).thenReturn(onlineVersion("1.0.0"));
        
        ArdSearchResponse response = service.search(request("api",
            Map.of("type", (Object) List.of(ArdIndexConstants.MEDIA_TYPE_SKILL))));
        
        assertEquals(1, response.getResults().size());
        ArdSearchResult result = response.getResults().get(0);
        assertEquals("api-helper", result.getDisplayName());
        assertEquals(ArdIndexConstants.MEDIA_TYPE_SKILL, result.getType());
        assertEquals("/v3/client/ai/skills?namespaceId=public&name=api-helper&version=1.0.0",
            result.getUrl());
        assertEquals("urn:air:nacos.local:public:skill:api-helper", result.getIdentifier());
        assertEquals(ArdIndexConstants.SOURCE_NACOS_LOCAL, result.getSource());
        assertEquals("skill", result.getMetadata().get("resourceType"));
        assertEquals("skill", result.getTrustManifest().get("resourceType"));
        assertEquals(100.0D, result.getScore(), 0.001D);
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
            eq(500)))
            .thenReturn(List.of(hit(100L, 0.9D)));
        when(repository.searchChunks(eq("public"), eq("api"), eq(List.of("skill")), eq(500)))
            .thenReturn(List.of());
        when(repository.findEntriesByIds(anyCollection())).thenReturn(List.of(entry()));
        when(resourceManager.findMeta("public", "api-helper", Constants.Skills.RESOURCE_TYPE_SKILL))
            .thenReturn(meta("1.0.0"));
        when(resourceManager.findVersion("public", "api-helper",
            Constants.Skills.RESOURCE_TYPE_SKILL, "1.0.0")).thenReturn(onlineVersion("1.0.0"));
        
        ArdSearchResponse response = service.search(request("api",
            Map.of("metadata.resourceType", (Object) "skill")));
        
        assertEquals(1, response.getResults().size());
        assertEquals(100.0D, response.getResults().get(0).getScore(), 0.001D);
    }
    
    @Test
    void searchShouldPreferHighValueChunkTypeOverContentChunk() throws Exception {
        ArdSearchServiceImpl service = service();
        when(vectorIndex.available()).thenReturn(false);
        when(repository.searchChunks(eq("public"), eq("avatar"), eq(List.of("skill")), eq(500)))
            .thenReturn(List.of(
                hit(101L, 1.0D, "generic-video", ArdIndexConstants.CHUNK_TYPE_SKILL_CONTENT),
                hit(102L, 0.8D, "avatar-tool",
                    ArdIndexConstants.CHUNK_TYPE_BILINGUAL_ALIAS)));
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
            Map.of("type", (Object) List.of(ArdIndexConstants.MEDIA_TYPE_SKILL))));
        
        assertEquals(2, response.getResults().size());
        assertEquals("avatar-tool", response.getResults().get(0).getDisplayName());
    }
    
    @Test
    void searchShouldUseMaxScoreWhenEnhancedRankingDisabled() throws Exception {
        System.setProperty(RANKING_ENABLED_KEY, "false");
        ArdSearchServiceImpl service = service();
        when(vectorIndex.available()).thenReturn(false);
        when(repository.searchChunks(eq("public"), eq("api"), eq(List.of("skill")), eq(500)))
            .thenReturn(List.of(hit(100L, 0.4D), hit(100L, 0.9D)));
        when(repository.findEntriesByIds(anyCollection())).thenReturn(List.of(entry()));
        when(resourceManager.findMeta("public", "api-helper", Constants.Skills.RESOURCE_TYPE_SKILL))
            .thenReturn(meta("1.0.0"));
        when(resourceManager.findVersion("public", "api-helper",
            Constants.Skills.RESOURCE_TYPE_SKILL, "1.0.0")).thenReturn(onlineVersion("1.0.0"));
        
        ArdSearchResponse response = service.search(request("api",
            Map.of("type", (Object) List.of(ArdIndexConstants.MEDIA_TYPE_SKILL))));
        
        assertEquals(1, response.getResults().size());
        assertEquals(100.0D, response.getResults().get(0).getScore(), 0.001D);
    }
    
    @Test
    void searchShouldApplyStructuredMetadataFilters() throws Exception {
        ArdSearchServiceImpl service = service();
        when(vectorIndex.available()).thenReturn(false);
        when(repository.searchChunks(eq("public"), eq("api"), eq(List.of("skill", "prompt", "mcp")),
            eq(500)))
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
    void searchShouldPageResultsWithPageToken() throws Exception {
        ArdSearchServiceImpl service = service();
        when(vectorIndex.available()).thenReturn(false);
        when(repository.searchChunks(eq("public"), eq("api"), eq(List.of("skill")), eq(500)))
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
            Map.of("type", (Object) List.of(ArdIndexConstants.MEDIA_TYPE_SKILL)));
        firstRequest.setPageSize(2);
        ArdSearchResponse firstPage = service.search(firstRequest);
        
        assertEquals(2, firstPage.getResults().size());
        assertEquals("api-one", firstPage.getResults().get(0).getDisplayName());
        assertEquals("api-two", firstPage.getResults().get(1).getDisplayName());
        assertNotNull(firstPage.getPageToken());
        
        ArdSearchRequest secondRequest = request("api",
            Map.of("type", (Object) List.of(ArdIndexConstants.MEDIA_TYPE_SKILL)));
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
            Map.of("type", (Object) List.of(ArdIndexConstants.MEDIA_TYPE_SKILL)));
        request.setPageToken("broken-token");
        
        assertThrows(NacosException.class, () -> service.search(request));
    }
    
    @Test
    void searchShouldSkipEntryWhenVersionIsNotLatest() throws Exception {
        ArdSearchServiceImpl service = service();
        when(vectorIndex.available()).thenReturn(false);
        when(repository.searchChunks(eq("public"), eq("api"), eq(List.of("skill")), eq(500)))
            .thenReturn(List.of(hit(100L, 1.0D)));
        when(repository.findEntriesByIds(any(Collection.class))).thenReturn(List.of(entry()));
        when(resourceManager.findMeta("public", "api-helper", Constants.Skills.RESOURCE_TYPE_SKILL))
            .thenReturn(meta("1.0.1"));
        
        ArdSearchResponse response = service.search(request("api",
            Map.of("type", (Object) List.of(ArdIndexConstants.MEDIA_TYPE_SKILL))));
        
        assertTrue(response.getResults().isEmpty());
    }
    
    @Test
    void searchShouldRejectUnsupportedFederation() {
        ArdSearchServiceImpl service = service();
        ArdSearchRequest request = request("api",
            Map.of("type", (Object) List.of(ArdIndexConstants.MEDIA_TYPE_SKILL)));
        request.setFederation("referrals");
        
        assertThrows(NacosException.class, () -> service.search(request));
    }
    
    @Test
    void searchShouldRejectUnknownFilterKey() {
        ArdSearchServiceImpl service = service();
        ArdSearchRequest request = request("api",
            Map.of("metadata.unknown", (Object) List.of("x")));
        
        assertThrows(NacosException.class, () -> service.search(request));
    }
    
    private ArdSearchServiceImpl service() {
        return new ArdSearchServiceImpl(resourceManager, mcpServerOperationService, repository,
            embeddingService, vectorIndex);
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
    
    private ArdSearchHit hit(Long entryId, double score) {
        return hit(entryId, score, "api-helper");
    }
    
    private ArdSearchHit hit(Long entryId, double score, String resourceName) {
        return hit(entryId, score, resourceName, null);
    }
    
    private ArdSearchHit hit(Long entryId, double score, String resourceName,
        String chunkType) {
        ArdSearchHit hit = new ArdSearchHit();
        hit.setEntryId(entryId);
        hit.setChunkId(200L);
        hit.setIdentifier("urn:air:nacos.local:public:skill:" + resourceName);
        hit.setResourceType(Constants.Skills.RESOURCE_TYPE_SKILL);
        hit.setResourceName(resourceName);
        hit.setResourceVersion("1.0.0");
        hit.setChunkType(chunkType);
        hit.setScore(score);
        return hit;
    }
    
    private ArdEntry entry() {
        return entry(100L, "api-helper");
    }
    
    private ArdEntry entry(long id, String resourceName) {
        ArdEntry entry = new ArdEntry();
        entry.setId(id);
        entry.setNamespaceId("public");
        entry.setResourceType(Constants.Skills.RESOURCE_TYPE_SKILL);
        entry.setResourceName(resourceName);
        entry.setResourceVersion("1.0.0");
        entry.setIdentifier("urn:air:nacos.local:public:skill:" + resourceName);
        entry.setDisplayName(resourceName);
        entry.setType(ArdIndexConstants.MEDIA_TYPE_SKILL);
        entry.setUrl("/v3/client/ai/skills?namespaceId=public&name=" + resourceName
            + "&version=1.0.0");
        entry.setDescription("Generate API parameter tables");
        entry.setTags(JacksonUtils.toJson(List.of("documentation", "api")));
        entry.setCapabilities(JacksonUtils.toJson(List.of("skill", "documentation")));
        entry.setRepresentativeQueries(JacksonUtils.toJson(List.of("api helper")));
        entry.setMetadata(JacksonUtils.toJson(Map.of("namespaceId", "public",
            "resourceType", "skill", "resourceName", resourceName, "resourceVersion", "1.0.0",
            "inputTypes", List.of("json"), "outputTypes", List.of("markdown"),
            "riskLevel", "low")));
        entry.setTrustManifest(JacksonUtils.toJson(Map.of("source",
            ArdIndexConstants.SOURCE_NACOS_LOCAL, "resourceType", "skill", "federation",
            ArdSearchServiceImpl.FEDERATION_NONE)));
        entry.setStatus(ArdIndexConstants.STATUS_ENABLED);
        entry.setSource(ArdIndexConstants.SOURCE_NACOS_LOCAL);
        entry.setGmtModified(Timestamp.from(Instant.parse("2026-06-29T01:00:00Z")));
        return entry;
    }
    
    private AiResource meta(String latestVersion) {
        return meta("api-helper", latestVersion);
    }
    
    private AiResource meta(String name, String latestVersion) {
        AiResource meta = new AiResource();
        meta.setNamespaceId("public");
        meta.setName(name);
        meta.setType(Constants.Skills.RESOURCE_TYPE_SKILL);
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
}
