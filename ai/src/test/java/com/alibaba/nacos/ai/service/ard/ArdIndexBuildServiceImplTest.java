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
import com.alibaba.nacos.ai.model.ard.ArdChunk;
import com.alibaba.nacos.ai.model.ard.ArdEntry;
import com.alibaba.nacos.ai.service.ard.vector.AiResourceVectorDocument;
import com.alibaba.nacos.ai.service.ard.vector.AiResourceVectorIndex;
import com.alibaba.nacos.ai.service.resource.AiResourceManager;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.plugin.visibility.constant.VisibilityConstants;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link ArdIndexBuildServiceImpl}.
 *
 * @author nacos
 */
@ExtendWith(MockitoExtension.class)
class ArdIndexBuildServiceImplTest {
    
    @Mock
    private AiResourceManager resourceManager;
    
    @Mock
    private ArdIndexRepository repository;
    
    @Mock
    private ArdEmbeddingService embeddingService;
    
    @Mock
    private AiResourceVectorIndex vectorIndex;
    
    @Test
    void rebuildAiResourceShouldPersistEntryChunksAndVectors() throws Exception {
        ArdIndexBuildServiceImpl service = service();
        AiResource meta = meta();
        AiResourceVersion version = version(AiResourceConstants.VERSION_STATUS_ONLINE);
        when(resourceManager.findMeta("public", "api-helper", Constants.Skills.RESOURCE_TYPE_SKILL))
            .thenReturn(meta);
        when(resourceManager.findVersion("public", "api-helper",
            Constants.Skills.RESOURCE_TYPE_SKILL, "1.0.0")).thenReturn(version);
        when(vectorIndex.available()).thenReturn(true);
        when(embeddingService.model()).thenReturn("test-model");
        when(embeddingService.embed(any())).thenReturn(new double[] {1.0D});
        when(repository.replaceEntry(any(ArdEntry.class), anyList())).thenAnswer(invocation -> {
            List<ArdChunk> chunks = invocation.getArgument(1);
            long id = 1L;
            for (ArdChunk chunk : chunks) {
                chunk.setId(id++);
            }
            return chunks;
        });
        
        service.rebuildAiResource("public", Constants.Skills.RESOURCE_TYPE_SKILL, "api-helper",
            "1.0.0");
        
        ArgumentCaptor<ArdEntry> entryCaptor = ArgumentCaptor.forClass(ArdEntry.class);
        ArgumentCaptor<List<ArdChunk>> chunksCaptor = ArgumentCaptor.forClass(List.class);
        verify(repository).replaceEntry(entryCaptor.capture(), chunksCaptor.capture());
        ArdEntry entry = entryCaptor.getValue();
        assertEquals("urn:air:nacos.local:public:skill:api-helper", entry.getIdentifier());
        assertEquals(ArdIndexConstants.MEDIA_TYPE_SKILL, entry.getType());
        assertFalse(chunksCaptor.getValue().isEmpty());
        ArgumentCaptor<Collection<AiResourceVectorDocument>> vectorCaptor =
            ArgumentCaptor.forClass(Collection.class);
        verify(vectorIndex).replaceResourceVersion(eq("public"),
            eq(Constants.Skills.RESOURCE_TYPE_SKILL), eq("api-helper"), eq("1.0.0"),
            vectorCaptor.capture());
        assertFalse(vectorCaptor.getValue().isEmpty());
    }
    
    @Test
    void rebuildAiResourceShouldDeleteOfflineVersion() throws Exception {
        ArdIndexBuildServiceImpl service = service();
        when(resourceManager.findMeta("public", "api-helper", Constants.Skills.RESOURCE_TYPE_SKILL))
            .thenReturn(meta());
        when(resourceManager.findVersion("public", "api-helper",
            Constants.Skills.RESOURCE_TYPE_SKILL, "1.0.0"))
            .thenReturn(version(AiResourceConstants.VERSION_STATUS_DRAFT));
        
        service.rebuildAiResource("public", Constants.Skills.RESOURCE_TYPE_SKILL, "api-helper",
            "1.0.0");
        
        verify(repository).deleteByResourceVersion("public", Constants.Skills.RESOURCE_TYPE_SKILL,
            "api-helper", "1.0.0");
        verify(repository, never()).replaceEntry(any(), anyList());
    }
    
    private ArdIndexBuildServiceImpl service() {
        return new ArdIndexBuildServiceImpl(resourceManager, repository, embeddingService,
            vectorIndex);
    }
    
    private AiResource meta() {
        AiResource meta = new AiResource();
        meta.setNamespaceId("public");
        meta.setName("api-helper");
        meta.setType(Constants.Skills.RESOURCE_TYPE_SKILL);
        meta.setDesc("Generate API parameter tables");
        meta.setStatus(AiResourceConstants.META_STATUS_ENABLE);
        meta.setScope(VisibilityConstants.SCOPE_PUBLIC);
        meta.setBizTags(JacksonUtils.toJson(List.of("api", "documentation")));
        meta.setExt(JacksonUtils.toJson(Map.of("inputTypes", List.of("json"),
            "outputTypes", List.of("markdown"), "riskLevel", "low")));
        meta.setVersionInfo(JacksonUtils.toJson(Map.of("labels", Map.of("latest", "1.0.0"))));
        return meta;
    }
    
    private AiResourceVersion version(String status) {
        AiResourceVersion version = new AiResourceVersion();
        version.setNamespaceId("public");
        version.setName("api-helper");
        version.setType(Constants.Skills.RESOURCE_TYPE_SKILL);
        version.setVersion("1.0.0");
        version.setDesc("Extract API parameters");
        version.setStatus(status);
        return version;
    }
}
