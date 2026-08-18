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

import com.alibaba.nacos.ai.constant.AiResourceConstants;
import com.alibaba.nacos.ai.model.AiResource;
import com.alibaba.nacos.ai.model.AiResourceVersion;
import com.alibaba.nacos.ai.model.search.AiResourceSearchDocument;
import com.alibaba.nacos.ai.service.resource.AiResourceManager;
import com.alibaba.nacos.ai.storage.NacosConfigAiResourceStorage;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.common.utils.JacksonUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link StoredAiResourceSearchTypeHandler}.
 *
 * @author nacos
 */
@ExtendWith(MockitoExtension.class)
class StoredAiResourceSearchTypeHandlerTest {
    
    @Mock
    private AiResourceManager resourceManager;
    
    @Mock
    private AiResourceIndexContentLoader contentLoader;
    
    @Test
    void projectShouldBuildLatestSkillProjection() throws Exception {
        AiResource resource = resource(AiResourceConstants.RESOURCE_TYPE_SKILL, "skill", "1.0.0");
        resource.setNamespaceId(null);
        resource.setType(null);
        AiResourceVersion version = version("skill", AiResourceConstants.RESOURCE_TYPE_SKILL,
            "1.0.0", AiResourceConstants.VERSION_STATUS_ONLINE);
        when(resourceManager.findMeta("public", "skill", AiResourceConstants.RESOURCE_TYPE_SKILL))
            .thenReturn(resource);
        when(resourceManager.findVersion("public", "skill",
            AiResourceConstants.RESOURCE_TYPE_SKILL, "1.0.0")).thenReturn(version);
        when(contentLoader.load(any(), any())).thenReturn(List.of(
            new AiResourceIndexEnhancementContent("SKILL.md", "Research with citations")));
        StoredAiResourceSearchTypeHandler handler = handler();
        
        AiResourceIndexProjection projection = handler.project("public",
            AiResourceConstants.RESOURCE_TYPE_SKILL, "skill", null);
        
        assertEquals("1.0.0", projection.getDocument().getResourceVersion());
        assertEquals("public", resource.getNamespaceId());
        assertEquals(AiResourceConstants.RESOURCE_TYPE_SKILL, resource.getType());
        assertEquals("public", projection.getFacets().get("scope"));
        assertTrue(projection.getChunks().stream().anyMatch(
            chunk -> AiResourceSearchConstants.CHUNK_TYPE_SKILL_CONTENT
                .equals(chunk.getChunkType())));
        assertEquals(1, projection.getEnhancementContents().size());
    }
    
    @Test
    void projectShouldBuildExactPromptAndTolerateContentFailure() throws Exception {
        String resourceType = NacosConfigAiResourceStorage.RESOURCE_TYPE_PROMPT;
        AiResource resource = resource(resourceType, "prompt", "2.0.0");
        AiResourceVersion version = version("prompt", resourceType, "1.0.0",
            AiResourceConstants.VERSION_STATUS_ONLINE);
        when(resourceManager.findMeta("public", "prompt", resourceType)).thenReturn(resource);
        when(resourceManager.findVersion("public", "prompt", resourceType, "1.0.0"))
            .thenReturn(version);
        when(contentLoader.load(any(), any())).thenThrow(new IllegalStateException("storage"));
        
        AiResourceIndexProjection projection = handler().project("public", resourceType,
            "prompt", "1.0.0");
        
        assertEquals("1.0.0", projection.getDocument().getResourceVersion());
        assertTrue(projection.getEnhancementContents().isEmpty());
        assertFalse(projection.getChunks().stream().anyMatch(
            chunk -> AiResourceSearchConstants.CHUNK_TYPE_PROMPT_CONTENT
                .equals(chunk.getChunkType())));
    }
    
    @Test
    void projectShouldIgnoreUnsupportedMissingOrOfflineResources() {
        StoredAiResourceSearchTypeHandler handler = handler();
        assertNull(handler.project("public", "agent", "agent", null));
        assertNull(handler.project("public", AiResourceConstants.RESOURCE_TYPE_SKILL,
            "missing", null));
        
        AiResource withoutLatest = resource(AiResourceConstants.RESOURCE_TYPE_SKILL,
            "without-latest", null);
        when(resourceManager.findMeta("public", "without-latest",
            AiResourceConstants.RESOURCE_TYPE_SKILL)).thenReturn(withoutLatest);
        assertNull(handler.project("public", AiResourceConstants.RESOURCE_TYPE_SKILL,
            "without-latest", null));
        
        AiResource offline = resource(AiResourceConstants.RESOURCE_TYPE_SKILL, "offline", "1.0.0");
        when(resourceManager.findMeta("public", "offline",
            AiResourceConstants.RESOURCE_TYPE_SKILL)).thenReturn(offline);
        when(resourceManager.findVersion("public", "offline",
            AiResourceConstants.RESOURCE_TYPE_SKILL, "1.0.0"))
            .thenReturn(version("offline", AiResourceConstants.RESOURCE_TYPE_SKILL, "1.0.0",
                AiResourceConstants.VERSION_STATUS_DRAFT));
        assertNull(handler.project("public", AiResourceConstants.RESOURCE_TYPE_SKILL,
            "offline", null));
    }
    
    @Test
    void scanShouldReturnBoundedSourcesAndCaptureOneProjectionFailure() {
        AiResource valid = resource(AiResourceConstants.RESOURCE_TYPE_SKILL, "valid", "1.0.0");
        AiResource broken = resource(AiResourceConstants.RESOURCE_TYPE_SKILL, "broken", "1.0.0");
        when(resourceManager.listMetaByType("public", AiResourceConstants.RESOURCE_TYPE_SKILL,
            null, null, 1, 2)).thenReturn(page(List.of(valid, broken)));
        when(resourceManager.findVersion("public", "valid",
            AiResourceConstants.RESOURCE_TYPE_SKILL, "1.0.0"))
            .thenReturn(version("valid", AiResourceConstants.RESOURCE_TYPE_SKILL, "1.0.0",
                AiResourceConstants.VERSION_STATUS_ONLINE));
        when(resourceManager.findVersion("public", "broken",
            AiResourceConstants.RESOURCE_TYPE_SKILL, "1.0.0"))
            .thenThrow(new IllegalStateException("broken source"));
        
        AiResourceIndexSourcePage result = handler().scan("public",
            AiResourceConstants.RESOURCE_TYPE_SKILL, 1, 2);
        
        assertTrue(result.hasMore());
        assertEquals(2, result.getItems().size());
        assertTrue(result.getItems().get(0).getProjection() != null);
        assertEquals("broken source", result.getItems().get(1).getFailure().getMessage());
        assertTrue(handler().scan("public", "agent", 1, 2).getItems().isEmpty());
        assertTrue(handler().scan("public", NacosConfigAiResourceStorage.RESOURCE_TYPE_PROMPT,
            1, 2).getItems().isEmpty());
    }
    
    @Test
    void isCurrentShouldValidateVisibilityLatestAndOnlineVersion() throws Exception {
        AiResource resource = resource(AiResourceConstants.RESOURCE_TYPE_SKILL, "skill", "1.0.0");
        AiResourceVersion version = version("skill", AiResourceConstants.RESOURCE_TYPE_SKILL,
            "1.0.0", AiResourceConstants.VERSION_STATUS_ONLINE);
        AiResourceSearchDocument document = document("skill",
            AiResourceConstants.RESOURCE_TYPE_SKILL, "1.0.0");
        when(resourceManager.findMeta("public", "skill", AiResourceConstants.RESOURCE_TYPE_SKILL))
            .thenReturn(resource);
        when(resourceManager.findVersion("public", "skill",
            AiResourceConstants.RESOURCE_TYPE_SKILL, "1.0.0")).thenReturn(version);
        
        assertTrue(handler().isCurrent(document));
        verify(resourceManager).ensureReadableOrNotFound(resource, "skill not found: skill");
        document.setResourceVersion("0.9.0");
        assertFalse(handler().isCurrent(document));
        document.setResourceType("agent");
        assertFalse(handler().isCurrent(document));
        assertFalse(handler().isCurrent(null));
    }
    
    @Test
    void isCurrentShouldHideDisabledUnreadableAndOfflineResources() throws Exception {
        AiResource disabled = resource(AiResourceConstants.RESOURCE_TYPE_SKILL,
            "disabled", "1.0.0");
        disabled.setStatus(AiResourceConstants.META_STATUS_DISABLE);
        when(resourceManager.findMeta("public", "disabled",
            AiResourceConstants.RESOURCE_TYPE_SKILL)).thenReturn(disabled);
        assertFalse(handler().isCurrent(document("disabled",
            AiResourceConstants.RESOURCE_TYPE_SKILL, "1.0.0")));
        
        AiResource unreadable = resource(AiResourceConstants.RESOURCE_TYPE_SKILL,
            "unreadable", "1.0.0");
        when(resourceManager.findMeta("public", "unreadable",
            AiResourceConstants.RESOURCE_TYPE_SKILL)).thenReturn(unreadable);
        doThrow(new NacosException(NacosException.NOT_FOUND, "hidden"))
            .when(resourceManager).ensureReadableOrNotFound(unreadable,
                "skill not found: unreadable");
        assertFalse(handler().isCurrent(document("unreadable",
            AiResourceConstants.RESOURCE_TYPE_SKILL, "1.0.0")));
        
        AiResource offline = resource(AiResourceConstants.RESOURCE_TYPE_SKILL, "offline", "1.0.0");
        when(resourceManager.findMeta("public", "offline",
            AiResourceConstants.RESOURCE_TYPE_SKILL)).thenReturn(offline);
        when(resourceManager.findVersion("public", "offline",
            AiResourceConstants.RESOURCE_TYPE_SKILL, "1.0.0"))
            .thenReturn(version("offline", AiResourceConstants.RESOURCE_TYPE_SKILL, "1.0.0",
                AiResourceConstants.VERSION_STATUS_DRAFT));
        assertFalse(handler().isCurrent(document("offline",
            AiResourceConstants.RESOURCE_TYPE_SKILL, "1.0.0")));
    }
    
    @Test
    void existsShouldOnlyInspectOwnedResourceTypes() {
        when(resourceManager.findMeta("public", "skill", AiResourceConstants.RESOURCE_TYPE_SKILL))
            .thenReturn(resource(AiResourceConstants.RESOURCE_TYPE_SKILL, "skill", "1.0.0"));
        assertTrue(handler().exists("public", AiResourceConstants.RESOURCE_TYPE_SKILL, "skill"));
        assertFalse(handler().exists("public", "agent", "skill"));
        assertEquals(List.of(AiResourceConstants.RESOURCE_TYPE_SKILL,
            NacosConfigAiResourceStorage.RESOURCE_TYPE_PROMPT),
            List.copyOf(handler().resourceTypes()));
    }
    
    private StoredAiResourceSearchTypeHandler handler() {
        return new StoredAiResourceSearchTypeHandler(resourceManager, contentLoader);
    }
    
    private AiResource resource(String type, String name, String latestVersion) {
        AiResource resource = new AiResource();
        resource.setNamespaceId("public");
        resource.setType(type);
        resource.setName(name);
        resource.setDesc("description");
        resource.setScope("public");
        resource.setStatus(AiResourceConstants.META_STATUS_ENABLE);
        resource.setVersionInfo(latestVersion == null ? null
            : JacksonUtils.toJson(Map.of("labels", Map.of("latest", latestVersion))));
        return resource;
    }
    
    private AiResourceVersion version(String name, String type, String value, String status) {
        AiResourceVersion version = new AiResourceVersion();
        version.setNamespaceId("public");
        version.setName(name);
        version.setType(type);
        version.setVersion(value);
        version.setStatus(status);
        return version;
    }
    
    private AiResourceSearchDocument document(String name, String type, String version) {
        AiResourceSearchDocument document = new AiResourceSearchDocument();
        document.setNamespaceId("public");
        document.setResourceType(type);
        document.setResourceName(name);
        document.setResourceVersion(version);
        return document;
    }
    
    private Page<AiResource> page(List<AiResource> resources) {
        Page<AiResource> page = new Page<>();
        page.setPageItems(resources);
        return page;
    }
}
