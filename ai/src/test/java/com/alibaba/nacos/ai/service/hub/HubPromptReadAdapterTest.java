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
import com.alibaba.nacos.ai.model.AiResourceVersion;
import com.alibaba.nacos.ai.model.hub.HubResourceReference;
import com.alibaba.nacos.ai.model.hub.HubResourceType;
import com.alibaba.nacos.ai.service.resource.AiResourceManager;
import com.alibaba.nacos.ai.utils.HubResourceReferenceJsonUtil;
import com.alibaba.nacos.api.ai.model.prompt.PromptVersionInfo;
import com.alibaba.nacos.api.ai.model.prompt.PromptVersionSummary;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.Page;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.lenient;

/** Prompt shared reads retain their own version and metric semantics. */
class HubPromptReadAdapterTest {
    
    private final HubResourcePublicationService publications =
        mock(HubResourcePublicationService.class);
    private final HubResourceDirectoryService directory = mock(HubResourceDirectoryService.class);
    private final HubResourceDiscoveryProjector projector =
        mock(HubResourceDiscoveryProjector.class);
    private final HubResourceContentService contents = mock(HubResourceContentService.class);
    private final AiResourceManager manager = mock(AiResourceManager.class);
    private final HubResourceDownloadRecorder downloads = mock(HubResourceDownloadRecorder.class);
    private final HubPromptReadAdapter adapter =
        new HubPromptReadAdapter(publications, directory, projector, contents, manager, downloads);
    private final AiResourceVersion version = new AiResourceVersion();
    
    @BeforeEach
    void setUp() throws Exception {
        HubResourceReference reference =
            HubResourceReferenceJsonUtil.build("source", 101L, "same-name", 201L, "1.0.0");
        AiResource hub = new AiResource();
        hub.setId(501L);
        hub.setName("same-name-101");
        version.setId(201L);
        version.setStatus("online");
        version.setAuthor("author");
        version.setDesc("published content");
        lenient().when(manager.findVersion("source", "same-name", "prompt", "1.0.0"))
            .thenReturn(version);
        lenient()
            .when(publications.inspectPinned(eq(HubResourceType.PROMPT), eq(501L), any(), any()))
            .thenAnswer(call -> {
                HubResourcePublicationService.PinnedPublicationReader<?> reader =
                    call.getArgument(3);
                return reader.read(hub, new AiResource(), reference);
            });
        PromptVersionInfo body = new PromptVersionInfo();
        body.setTemplate("Hello {{name}}");
        body.setMd5("unchanged-md5");
        body.setPublishPipelineInfo("must-not-leak");
        lenient().when(contents.readPrompt(501L, "1.0.0")).thenReturn(body);
    }
    
    @Test
    void sharedVersionRetainsSourceReviewResult() throws Exception {
        version.setPublishPipelineInfo(
            "{\"executionId\":\"review-1\",\"status\":\"APPROVED\",\"pipeline\":[]}");
        assertEquals(version.getPublishPipelineInfo(),
            adapter.getVersion("501", "1.0.0").getPublishPipelineInfo());
    }
    
    @Test
    void explicitVersionWinsOverLabelAndDoesNotCountAsAnExplicitDownload() throws Exception {
        PromptVersionInfo result = adapter.query("501", "1.0.0", "other-label");
        assertEquals("1.0.0", result.getVersion());
        assertEquals("Hello {{name}}", result.getTemplate());
        assertEquals("unchanged-md5", result.getMd5());
        assertEquals("501", result.getPromptKey());
        assertNull(result.getPublishPipelineInfo());
        verify(publications).inspectPinned(eq(HubResourceType.PROMPT), eq(501L), eq("1.0.0"),
            any());
        verifyNoInteractions(downloads);
    }
    
    @Test
    void unknownLabelFallsBackToTheOnlySharedLatest() throws Exception {
        assertEquals("1.0.0", adapter.query("501", null, "unknown-label").getVersion());
        verify(publications).inspectPinned(eq(HubResourceType.PROMPT), eq(501L), isNull(), any());
    }
    
    @Test
    void explicitDownloadCountsHubOnly() throws Exception {
        assertEquals("1.0.0", adapter.download("501", null).getVersion());
        verify(downloads).record(HubResourceType.PROMPT, 501L, "1.0.0");
    }
    
    @Test
    void versionPaginationNeverExposesOtherSourceVersionsOrLoadsContent() throws Exception {
        Page<PromptVersionSummary> second = adapter.versions("501", 2, 1);
        assertEquals(1, second.getTotalCount());
        assertEquals(0, second.getPageItems().size());
        verifyNoInteractions(contents, downloads);
    }
    
    @Test
    void replacedSourceVersionCannotReturnSharedMetadata() {
        version.setId(999L);
        assertThrows(NacosException.class, () -> adapter.getMeta("501"));
        verifyNoInteractions(contents, downloads);
    }
    
    @Test
    void ambiguousNamesAndInvalidPaginationFailBeforeIo() {
        assertThrows(NacosException.class, () -> adapter.getVersion("same-name", null));
        assertThrows(NacosException.class, () -> adapter.versions("501", 0, 20));
        verifyNoInteractions(publications, contents, manager);
    }
}
