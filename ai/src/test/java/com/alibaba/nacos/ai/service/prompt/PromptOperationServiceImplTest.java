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

package com.alibaba.nacos.ai.service.prompt;

import com.alibaba.nacos.ai.config.PromptDataMigrationTask;
import com.alibaba.nacos.ai.event.PromptDownloadEvent;
import com.alibaba.nacos.ai.model.AiResource;
import com.alibaba.nacos.ai.model.AiResourceVersion;
import com.alibaba.nacos.ai.pipeline.PublishPipelineExecutor;
import com.alibaba.nacos.ai.pipeline.PublishPipelineManager;
import com.alibaba.nacos.ai.pipeline.config.PipelineConfigProvider;
import com.alibaba.nacos.ai.pipeline.model.PipelineCallback;
import com.alibaba.nacos.ai.pipeline.model.PipelineConfig;
import com.alibaba.nacos.ai.pipeline.model.PipelineExecutionResult;
import com.alibaba.nacos.ai.pipeline.model.PipelineExecutionStatus;
import com.alibaba.nacos.ai.pipeline.repository.PipelineExecutionRepository;
import com.alibaba.nacos.ai.service.repository.AiResourcePersistService;
import com.alibaba.nacos.ai.service.repository.AiResourceVersionPersistService;
import com.alibaba.nacos.ai.service.resource.AiResourceManager;
import com.alibaba.nacos.api.ai.model.prompt.PromptMetaInfo;
import com.alibaba.nacos.api.ai.model.prompt.PromptMetaSummary;
import com.alibaba.nacos.api.ai.model.prompt.PromptVersionInfo;
import com.alibaba.nacos.api.ai.model.prompt.PromptVersionSummary;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.config.server.service.ConfigOperationService;
import com.alibaba.nacos.plugin.ai.storage.AiResourceStorageRouter;
import com.alibaba.nacos.plugin.ai.storage.model.StorageKey;
import com.alibaba.nacos.plugin.ai.storage.spi.AiResourceStorage;
import com.alibaba.nacos.plugin.visibility.spi.VisibilityPluginManager;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.StandardEnvironment;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Test for PromptOperationServiceImpl.
 *
 * @author nacos
 */
@ExtendWith(MockitoExtension.class)
class PromptOperationServiceImplTest {
    
    private static final String NS = "public";
    
    private static final String PROMPT_KEY = "test-prompt";
    
    private static final String PROMPT_TYPE = "prompt";
    
    @Mock
    private AiResourceStorage storage;
    
    @Mock
    private AiResourcePersistService aiResourcePersistService;
    
    @Mock
    private AiResourceVersionPersistService aiResourceVersionPersistService;
    
    @Mock
    private PipelineConfigProvider pipelineConfigProvider;
    
    @Mock
    private PipelineExecutionRepository pipelineExecutionRepository;
    
    @Mock
    private ConfigOperationService configOperationService;
    
    @Mock
    private PromptDataMigrationTask promptDataMigrationTask;
    
    private PromptOperationServiceImpl service;
    
    private static final org.springframework.core.env.ConfigurableEnvironment CACHED_ENVIRONMENT =
        EnvUtil.getEnvironment();
    
    private MockedStatic<VisibilityPluginManager> visibilityManagerStatic;
    
    private VisibilityPluginManager mockVisibilityManager;
    
    @BeforeEach
    void setUp() {
        EnvUtil.setEnvironment(new StandardEnvironment());
        AiResourceStorageRouter.reset();
        lenient().when(storage.type()).thenReturn("nacos_config");
        AiResourceStorageRouter.join(storage);
        PipelineConfig disabledConfig = new PipelineConfig();
        disabledConfig.setEnabled(false);
        lenient().when(pipelineConfigProvider.getConfig()).thenReturn(disabledConfig);
        PublishPipelineExecutor publishPipelineExecutor =
            new PublishPipelineExecutor(new PublishPipelineManager(),
                pipelineConfigProvider, pipelineExecutionRepository,
                Executors.newSingleThreadExecutor());
        AiResourceManager resourceManager =
            new AiResourceManager(aiResourcePersistService, aiResourceVersionPersistService,
                pipelineExecutionRepository);
        service =
            new PromptOperationServiceImpl(publishPipelineExecutor, configOperationService,
                resourceManager, promptDataMigrationTask);
        mockVisibilityManager = mock(VisibilityPluginManager.class);
        lenient().when(mockVisibilityManager.findVisibilityService(anyString()))
            .thenReturn(Optional.empty());
        visibilityManagerStatic = mockStatic(VisibilityPluginManager.class);
        visibilityManagerStatic.when(VisibilityPluginManager::getInstance)
            .thenReturn(mockVisibilityManager);
    }
    
    @AfterEach
    void tearDown() {
        if (visibilityManagerStatic != null) {
            visibilityManagerStatic.close();
        }
        EnvUtil.setEnvironment(CACHED_ENVIRONMENT);
    }
    
    // ========== createDraft ==========
    
    @Test
    void testCreateDraftNewPromptWithDefaultVersion() throws NacosException {
        when(aiResourcePersistService.find(NS, PROMPT_KEY, PROMPT_TYPE)).thenReturn(null);
        
        String version = service.createDraft(NS, PROMPT_KEY, null, null, "Hello {{name}}", null,
            null, "desc", null);
        
        assertEquals("0.0.1", version);
        verify(aiResourcePersistService).insert(any(AiResource.class));
        verify(aiResourceVersionPersistService).insert(any(AiResourceVersion.class));
        verify(storage).save(any(StorageKey.class), any(byte[].class));
    }
    
    @Test
    void testCreateDraftNewPromptShouldUseVisibilityDefaultScope() throws NacosException {
        when(aiResourcePersistService.find(NS, PROMPT_KEY, PROMPT_TYPE)).thenReturn(null);
        com.alibaba.nacos.plugin.visibility.spi.VisibilityService visibilityService =
            mock(com.alibaba.nacos.plugin.visibility.spi.VisibilityService.class);
        when(visibilityService.resolveDefaultScopeForCreate(anyString(), anyString(),
            eq(PROMPT_TYPE)))
            .thenReturn("public");
        when(mockVisibilityManager.findVisibilityService(anyString()))
            .thenReturn(Optional.of(visibilityService));
        
        service.createDraft(NS, PROMPT_KEY, null, null, "Hello {{name}}", null, null, "desc", null);
        
        ArgumentCaptor<AiResource> metaCaptor = ArgumentCaptor.forClass(AiResource.class);
        verify(aiResourcePersistService).insert(metaCaptor.capture());
        assertEquals("PUBLIC", metaCaptor.getValue().getScope());
    }
    
    @Test
    void testCreateDraftNewPromptWithSpecifiedVersion() throws NacosException {
        when(aiResourcePersistService.find(NS, PROMPT_KEY, PROMPT_TYPE)).thenReturn(null);
        
        String version =
            service.createDraft(NS, PROMPT_KEY, null, "1.0.0", "Hello", null, null, null, null);
        
        assertEquals("1.0.0", version);
    }
    
    @Test
    void testCreateDraftShouldThrowWhenTemplateMissingForNewPrompt() {
        when(aiResourcePersistService.find(NS, PROMPT_KEY, PROMPT_TYPE)).thenReturn(null);
        
        assertThrows(NacosApiException.class,
            () -> service.createDraft(NS, PROMPT_KEY, null, null, null, null, null, null, null));
    }
    
    @Test
    void testCreateDraftForkFromBasedOnVersion() throws NacosException {
        AiResource meta = createMeta(PROMPT_KEY, 1L, "{\"labels\":{},\"onlineCnt\":1}");
        when(aiResourcePersistService.find(NS, PROMPT_KEY, PROMPT_TYPE)).thenReturn(meta);
        
        AiResourceVersion baseRow = createVersionRow("0.0.1", "online");
        when(aiResourceVersionPersistService.find(NS, PROMPT_KEY, PROMPT_TYPE, "0.0.1"))
            .thenReturn(baseRow);
        // No existing version for 0.0.2
        when(aiResourceVersionPersistService.find(NS, PROMPT_KEY, PROMPT_TYPE, "0.0.2"))
            .thenReturn(null);
        
        PromptVersionInfo baseContent = new PromptVersionInfo();
        baseContent.setTemplate("base template");
        mockStorageGet(JacksonUtils.toJson(baseContent).getBytes(StandardCharsets.UTF_8));
        
        when(aiResourcePersistService.updateMetaCas(eq(NS), eq(PROMPT_KEY), eq(PROMPT_TYPE), eq(1L),
            any(AiResource.class))).thenReturn(true);
        
        String version =
            service.createDraft(NS, PROMPT_KEY, "0.0.1", null, null, null, null, null, null);
        
        assertEquals("0.0.2", version);
        verify(aiResourceVersionPersistService).insert(any(AiResourceVersion.class));
    }
    
    @Test
    void testCreateDraftShouldThrowWhenBasedOnVersionNotFound() {
        AiResource meta = createMeta(PROMPT_KEY, 1L, "{\"labels\":{},\"onlineCnt\":1}");
        when(aiResourcePersistService.find(NS, PROMPT_KEY, PROMPT_TYPE)).thenReturn(meta);
        when(aiResourceVersionPersistService.find(NS, PROMPT_KEY, PROMPT_TYPE, "9.9.9"))
            .thenReturn(null);
        
        assertThrows(NacosApiException.class,
            () -> service.createDraft(NS, PROMPT_KEY, "9.9.9", null, null, null, null, null, null));
    }
    
    @Test
    void testCreateDraftShouldThrowWhenWorkingVersionExists() {
        AiResource meta = createMeta(PROMPT_KEY, 1L,
            "{\"labels\":{},\"editingVersion\":\"0.0.2\",\"onlineCnt\":1}");
        when(aiResourcePersistService.find(NS, PROMPT_KEY, PROMPT_TYPE)).thenReturn(meta);
        
        assertThrows(NacosApiException.class,
            () -> service.createDraft(NS, PROMPT_KEY, "0.0.1", null, null, null, null, null, null));
    }
    
    @Test
    void testCreateDraftShouldThrowWhenTargetVersionExists() {
        AiResource meta = createMeta(PROMPT_KEY, 1L, "{\"labels\":{},\"onlineCnt\":1}");
        when(aiResourcePersistService.find(NS, PROMPT_KEY, PROMPT_TYPE)).thenReturn(meta);
        when(aiResourceVersionPersistService.find(NS, PROMPT_KEY, PROMPT_TYPE, "0.0.1"))
            .thenReturn(createVersionRow("0.0.1", "online"));
        
        assertThrows(NacosApiException.class,
            () -> service.createDraft(NS, PROMPT_KEY, null, "0.0.1", "template", null, null, null,
                null));
    }
    
    // ========== updateDraft ==========
    
    @Test
    void testUpdateDraftSuccessfully() throws NacosException {
        AiResource meta =
            createMeta(PROMPT_KEY, 1L, "{\"labels\":{},\"editingVersion\":\"0.0.1\"}");
        when(aiResourcePersistService.find(NS, PROMPT_KEY, PROMPT_TYPE)).thenReturn(meta);
        when(aiResourceVersionPersistService.find(NS, PROMPT_KEY, PROMPT_TYPE, "0.0.1"))
            .thenReturn(createVersionRow("0.0.1", "draft"));
        
        service.updateDraft(NS, PROMPT_KEY, "updated template", null, "update msg");
        
        verify(storage).save(any(StorageKey.class), any(byte[].class));
    }
    
    @Test
    void testUpdateDraftShouldThrowWhenNoEditingVersion() {
        AiResource meta = createMeta(PROMPT_KEY, 1L, "{\"labels\":{}}");
        when(aiResourcePersistService.find(NS, PROMPT_KEY, PROMPT_TYPE)).thenReturn(meta);
        
        assertThrows(NacosApiException.class,
            () -> service.updateDraft(NS, PROMPT_KEY, "template", null, null));
    }
    
    @Test
    void testUpdateDraftShouldThrowWhenVersionNotDraft() {
        AiResource meta =
            createMeta(PROMPT_KEY, 1L, "{\"labels\":{},\"editingVersion\":\"0.0.1\"}");
        when(aiResourcePersistService.find(NS, PROMPT_KEY, PROMPT_TYPE)).thenReturn(meta);
        when(aiResourceVersionPersistService.find(NS, PROMPT_KEY, PROMPT_TYPE, "0.0.1"))
            .thenReturn(createVersionRow("0.0.1", "online"));
        
        assertThrows(NacosApiException.class,
            () -> service.updateDraft(NS, PROMPT_KEY, "template", null, null));
    }
    
    // ========== deleteDraft ==========
    
    @Test
    void testDeleteDraftSuccessfully() throws NacosException {
        AiResource meta =
            createMeta(PROMPT_KEY, 1L, "{\"labels\":{},\"editingVersion\":\"0.0.1\"}");
        when(aiResourcePersistService.find(NS, PROMPT_KEY, PROMPT_TYPE)).thenReturn(meta);
        when(aiResourceVersionPersistService.find(NS, PROMPT_KEY, PROMPT_TYPE, "0.0.1"))
            .thenReturn(createVersionRow("0.0.1", "draft"));
        when(aiResourcePersistService.updateMetaCas(eq(NS), eq(PROMPT_KEY), eq(PROMPT_TYPE), eq(1L),
            any(AiResource.class))).thenReturn(true);
        
        service.deleteDraft(NS, PROMPT_KEY);
        
        verify(aiResourceVersionPersistService).delete(NS, PROMPT_KEY, PROMPT_TYPE, "0.0.1");
        verify(storage).delete(any(StorageKey.class));
    }
    
    @Test
    void testDeleteDraftShouldDoNothingWhenNoEditing() throws NacosException {
        AiResource meta = createMeta(PROMPT_KEY, 1L, "{\"labels\":{}}");
        when(aiResourcePersistService.find(NS, PROMPT_KEY, PROMPT_TYPE)).thenReturn(meta);
        
        service.deleteDraft(NS, PROMPT_KEY);
        
        verify(aiResourceVersionPersistService, never()).delete(anyString(), anyString(),
            anyString(), anyString());
    }
    
    @Test
    void testDeleteDraftShouldThrowWhenMetaNotFound() {
        when(aiResourcePersistService.find(NS, PROMPT_KEY, PROMPT_TYPE)).thenReturn(null);
        
        assertThrows(NacosApiException.class, () -> service.deleteDraft(NS, PROMPT_KEY));
    }
    
    // ========== submit ==========
    
    @Test
    void testSubmitShouldAutoPublishWhenNoPipeline() throws NacosException {
        AiResource meta =
            createMeta(PROMPT_KEY, 1L, "{\"labels\":{},\"editingVersion\":\"0.0.1\"}");
        when(aiResourcePersistService.find(NS, PROMPT_KEY, PROMPT_TYPE)).thenReturn(meta);
        // submit reads "draft", then publish re-reads and expects "reviewing"
        AiResourceVersion draftRow = createVersionRow("0.0.1", "draft");
        AiResourceVersion reviewingRow = createVersionRow("0.0.1", "reviewing");
        when(aiResourceVersionPersistService.find(NS, PROMPT_KEY, PROMPT_TYPE, "0.0.1"))
            .thenReturn(draftRow).thenReturn(reviewingRow);
        when(aiResourcePersistService.updateMetaCas(eq(NS), eq(PROMPT_KEY), eq(PROMPT_TYPE),
            anyLong(),
            any(AiResource.class))).thenReturn(true);
        
        PromptVersionInfo content = new PromptVersionInfo();
        content.setTemplate("hello");
        mockStorageGet(JacksonUtils.toJson(content).getBytes(StandardCharsets.UTF_8));
        
        String result = service.submit(NS, PROMPT_KEY, null);
        
        assertEquals("0.0.1", result);
        // Should have moved to reviewing then auto-published (online)
        verify(aiResourceVersionPersistService).updateStatus(NS, PROMPT_KEY, PROMPT_TYPE, "0.0.1",
            "reviewing");
        verify(aiResourceVersionPersistService).updateStatus(NS, PROMPT_KEY, PROMPT_TYPE, "0.0.1",
            "online");
    }
    
    @Test
    void testSubmitShouldUseEditingVersionWhenVersionNotSpecified() throws NacosException {
        AiResource meta =
            createMeta(PROMPT_KEY, 1L, "{\"labels\":{},\"editingVersion\":\"0.0.2\"}");
        when(aiResourcePersistService.find(NS, PROMPT_KEY, PROMPT_TYPE)).thenReturn(meta);
        // submit reads "draft", then publish re-reads and expects "reviewing"
        when(aiResourceVersionPersistService.find(NS, PROMPT_KEY, PROMPT_TYPE, "0.0.2"))
            .thenReturn(createVersionRow("0.0.2", "draft"))
            .thenReturn(createVersionRow("0.0.2", "reviewing"));
        when(aiResourcePersistService.updateMetaCas(eq(NS), eq(PROMPT_KEY), eq(PROMPT_TYPE),
            anyLong(),
            any(AiResource.class))).thenReturn(true);
        
        PromptVersionInfo content = new PromptVersionInfo();
        content.setTemplate("hello");
        mockStorageGet(JacksonUtils.toJson(content).getBytes(StandardCharsets.UTF_8));
        
        String result = service.submit(NS, PROMPT_KEY, null);
        
        assertEquals("0.0.2", result);
    }
    
    @Test
    void testSubmitPipelineApprovedShouldMoveVersionToReviewed() throws NacosException {
        assertSubmitPipelineCompletionMovesVersionToReviewed(PipelineExecutionStatus.APPROVED);
    }
    
    @Test
    void testSubmitPipelineRejectedShouldMoveVersionToReviewed() throws NacosException {
        assertSubmitPipelineCompletionMovesVersionToReviewed(PipelineExecutionStatus.REJECTED);
    }
    
    @Test
    void testSubmitShouldThrowWhenNoDraftToSubmit() {
        AiResource meta = createMeta(PROMPT_KEY, 1L, "{\"labels\":{}}");
        when(aiResourcePersistService.find(NS, PROMPT_KEY, PROMPT_TYPE)).thenReturn(meta);
        
        assertThrows(NacosApiException.class, () -> service.submit(NS, PROMPT_KEY, null));
    }
    
    @Test
    void testSubmitShouldRejectNonDraftVersion() {
        AiResource meta =
            createMeta(PROMPT_KEY, 1L, "{\"labels\":{},\"editingVersion\":\"0.0.1\"}");
        when(aiResourcePersistService.find(NS, PROMPT_KEY, PROMPT_TYPE)).thenReturn(meta);
        // Target version exists but is already online (formal version).
        when(aiResourceVersionPersistService.find(NS, PROMPT_KEY, PROMPT_TYPE, "0.0.1"))
            .thenReturn(createVersionRow("0.0.1", "online"));
        
        NacosApiException ex = assertThrows(NacosApiException.class,
            () -> service.submit(NS, PROMPT_KEY, "0.0.1"));
        assertEquals(NacosException.INVALID_PARAM, ex.getErrCode());
        // Status must NOT be flipped to reviewing.
        verify(aiResourceVersionPersistService, never()).updateStatus(NS, PROMPT_KEY, PROMPT_TYPE,
            "0.0.1", "reviewing");
    }
    
    // ========== publish ==========
    
    @Test
    void testPublishShouldUpdateStatusToOnline() throws NacosException {
        AiResource meta = createMeta(PROMPT_KEY, 1L,
            "{\"labels\":{},\"reviewingVersion\":\"0.0.1\",\"onlineCnt\":0}");
        when(aiResourcePersistService.find(NS, PROMPT_KEY, PROMPT_TYPE)).thenReturn(meta);
        when(aiResourceVersionPersistService.find(NS, PROMPT_KEY, PROMPT_TYPE, "0.0.1"))
            .thenReturn(createVersionRow("0.0.1", "reviewing"));
        when(aiResourcePersistService.updateMetaCas(eq(NS), eq(PROMPT_KEY), eq(PROMPT_TYPE), eq(1L),
            any(AiResource.class))).thenReturn(true);
        
        service.publish(NS, PROMPT_KEY, "0.0.1", false);
        
        verify(aiResourceVersionPersistService).updateStatus(NS, PROMPT_KEY, PROMPT_TYPE, "0.0.1",
            "online");
    }
    
    @Test
    void testPublishShouldUpdateLatestLabelAndRefreshMirrorEvenWhenFlagFalse()
        throws NacosException {
        AiResource meta = createMeta(PROMPT_KEY, 1L,
            "{\"labels\":{},\"reviewingVersion\":\"0.0.1\",\"onlineCnt\":0}");
        AiResource updatedMeta = createMeta(PROMPT_KEY, 2L,
            "{\"labels\":{\"latest\":\"0.0.1\"},\"onlineCnt\":1}");
        when(aiResourcePersistService.find(NS, PROMPT_KEY, PROMPT_TYPE)).thenReturn(meta,
            updatedMeta);
        when(aiResourceVersionPersistService.find(NS, PROMPT_KEY, PROMPT_TYPE, "0.0.1"))
            .thenReturn(createVersionRow("0.0.1", "reviewing"));
        when(aiResourcePersistService.updateMetaCas(eq(NS), eq(PROMPT_KEY), eq(PROMPT_TYPE),
            anyLong(),
            any(AiResource.class))).thenReturn(true);
        
        PromptVersionInfo content = new PromptVersionInfo();
        content.setTemplate("hello");
        mockStorageGet(JacksonUtils.toJson(content).getBytes(StandardCharsets.UTF_8));
        
        service.publish(NS, PROMPT_KEY, "0.0.1", false);
        
        verify(configOperationService).publishConfig(any(), any(), any());
    }
    
    @Test
    void testPublishShouldAllowReviewedVersion() throws NacosException {
        AiResource meta = createMeta(PROMPT_KEY, 1L,
            "{\"labels\":{},\"reviewingVersion\":\"0.0.1\",\"onlineCnt\":0}");
        when(aiResourcePersistService.find(NS, PROMPT_KEY, PROMPT_TYPE)).thenReturn(meta);
        when(aiResourceVersionPersistService.find(NS, PROMPT_KEY, PROMPT_TYPE, "0.0.1"))
            .thenReturn(createVersionRow("0.0.1", "reviewed"));
        when(aiResourcePersistService.updateMetaCas(eq(NS), eq(PROMPT_KEY), eq(PROMPT_TYPE), eq(1L),
            any(AiResource.class))).thenReturn(true);
        
        service.publish(NS, PROMPT_KEY, "0.0.1", false);
        
        verify(aiResourceVersionPersistService).updateStatus(NS, PROMPT_KEY, PROMPT_TYPE, "0.0.1",
            "online");
    }
    
    @Test
    void testPublishShouldThrowWhenVersionNotFound() {
        AiResource meta = createMeta(PROMPT_KEY, 1L, "{\"labels\":{}}");
        when(aiResourcePersistService.find(NS, PROMPT_KEY, PROMPT_TYPE)).thenReturn(meta);
        when(aiResourceVersionPersistService.find(NS, PROMPT_KEY, PROMPT_TYPE, "0.0.1"))
            .thenReturn(null);
        
        assertThrows(NacosApiException.class,
            () -> service.publish(NS, PROMPT_KEY, "0.0.1", false));
    }
    
    @Test
    void testPublishShouldThrowWhenVersionNotReviewingReviewedOrOnline() {
        AiResource meta = createMeta(PROMPT_KEY, 1L, "{\"labels\":{}}");
        when(aiResourcePersistService.find(NS, PROMPT_KEY, PROMPT_TYPE)).thenReturn(meta);
        when(aiResourceVersionPersistService.find(NS, PROMPT_KEY, PROMPT_TYPE, "0.0.1"))
            .thenReturn(createVersionRow("0.0.1", "draft"));
        
        assertThrows(NacosApiException.class,
            () -> service.publish(NS, PROMPT_KEY, "0.0.1", false));
    }
    
    // ========== forcePublish ==========
    
    @Test
    void testForcePublishShouldUpdateLatestLabelAndRefreshMirrorEvenWhenFlagFalse()
        throws NacosException {
        AiResource meta = createMeta(PROMPT_KEY, 1L,
            "{\"labels\":{},\"editingVersion\":\"0.0.1\",\"onlineCnt\":0}");
        AiResource updatedMeta = createMeta(PROMPT_KEY, 2L,
            "{\"labels\":{\"latest\":\"0.0.1\"},\"onlineCnt\":1}");
        when(aiResourcePersistService.find(NS, PROMPT_KEY, PROMPT_TYPE)).thenReturn(meta,
            updatedMeta);
        when(aiResourceVersionPersistService.find(NS, PROMPT_KEY, PROMPT_TYPE, "0.0.1"))
            .thenReturn(createVersionRow("0.0.1", "draft"));
        when(aiResourcePersistService.updateMetaCas(eq(NS), eq(PROMPT_KEY), eq(PROMPT_TYPE),
            anyLong(),
            any(AiResource.class))).thenReturn(true);
        
        PromptVersionInfo content = new PromptVersionInfo();
        content.setTemplate("hello");
        mockStorageGet(JacksonUtils.toJson(content).getBytes(StandardCharsets.UTF_8));
        
        service.forcePublish(NS, PROMPT_KEY, "0.0.1", false);
        
        verify(configOperationService).publishConfig(any(), any(), any());
    }
    
    @Test
    void testForcePublishFromDraftStatus() throws NacosException {
        AiResource meta = createMeta(PROMPT_KEY, 1L,
            "{\"labels\":{},\"editingVersion\":\"0.0.1\",\"onlineCnt\":0}");
        when(aiResourcePersistService.find(NS, PROMPT_KEY, PROMPT_TYPE)).thenReturn(meta);
        when(aiResourceVersionPersistService.find(NS, PROMPT_KEY, PROMPT_TYPE, "0.0.1"))
            .thenReturn(createVersionRow("0.0.1", "draft"));
        when(aiResourcePersistService.updateMetaCas(eq(NS), eq(PROMPT_KEY), eq(PROMPT_TYPE), eq(1L),
            any(AiResource.class))).thenReturn(true);
        
        service.forcePublish(NS, PROMPT_KEY, "0.0.1", false);
        
        verify(aiResourceVersionPersistService).updateStatus(NS, PROMPT_KEY, PROMPT_TYPE, "0.0.1",
            "online");
    }
    
    @Test
    void testForcePublishShouldClearWorkingPointers() throws NacosException {
        AiResource meta = createMeta(PROMPT_KEY, 1L,
            "{\"labels\":{},\"editingVersion\":\"0.0.1\",\"reviewingVersion\":\"0.0.1\",\"onlineCnt\":0}");
        when(aiResourcePersistService.find(NS, PROMPT_KEY, PROMPT_TYPE)).thenReturn(meta);
        when(aiResourceVersionPersistService.find(NS, PROMPT_KEY, PROMPT_TYPE, "0.0.1"))
            .thenReturn(createVersionRow("0.0.1", "reviewing"));
        when(aiResourcePersistService.updateMetaCas(eq(NS), eq(PROMPT_KEY), eq(PROMPT_TYPE), eq(1L),
            any(AiResource.class))).thenReturn(true);
        
        service.forcePublish(NS, PROMPT_KEY, "0.0.1", false);
        
        verify(aiResourcePersistService).updateMetaCas(eq(NS), eq(PROMPT_KEY), eq(PROMPT_TYPE),
            eq(1L),
            any(AiResource.class));
    }
    
    @Test
    void testForcePublishShouldThrowWhenVersionNotFound() {
        AiResource meta = createMeta(PROMPT_KEY, 1L, "{\"labels\":{}}");
        when(aiResourcePersistService.find(NS, PROMPT_KEY, PROMPT_TYPE)).thenReturn(meta);
        when(aiResourceVersionPersistService.find(NS, PROMPT_KEY, PROMPT_TYPE, "0.0.1"))
            .thenReturn(null);
        
        assertThrows(NacosApiException.class,
            () -> service.forcePublish(NS, PROMPT_KEY, "0.0.1", false));
    }
    
    @Test
    void testForcePublishShouldThrowWhenVersionAlreadyOnline() {
        AiResource meta = createMeta(PROMPT_KEY, 1L, "{\"labels\":{},\"onlineCnt\":1}");
        when(aiResourcePersistService.find(NS, PROMPT_KEY, PROMPT_TYPE)).thenReturn(meta);
        when(aiResourceVersionPersistService.find(NS, PROMPT_KEY, PROMPT_TYPE, "0.0.1"))
            .thenReturn(createVersionRow("0.0.1", "online"));
        
        NacosApiException ex = assertThrows(NacosApiException.class,
            () -> service.forcePublish(NS, PROMPT_KEY, "0.0.1", false));
        
        assertEquals(NacosException.INVALID_PARAM, ex.getErrCode());
    }
    
    @Test
    void testForcePublishShouldThrowWhenVersionOffline() {
        AiResource meta = createMeta(PROMPT_KEY, 1L, "{\"labels\":{},\"onlineCnt\":0}");
        when(aiResourcePersistService.find(NS, PROMPT_KEY, PROMPT_TYPE)).thenReturn(meta);
        when(aiResourceVersionPersistService.find(NS, PROMPT_KEY, PROMPT_TYPE, "0.0.1"))
            .thenReturn(createVersionRow("0.0.1", "offline"));
        
        NacosApiException ex = assertThrows(NacosApiException.class,
            () -> service.forcePublish(NS, PROMPT_KEY, "0.0.1", false));
        
        assertEquals(NacosException.INVALID_PARAM, ex.getErrCode());
    }
    
    // ========== changeOnlineStatus ==========
    
    @Test
    void testChangeOnlineStatusToOffline() throws NacosException {
        AiResource meta = createMeta(PROMPT_KEY, 1L, "{\"labels\":{},\"onlineCnt\":1}");
        when(aiResourcePersistService.find(NS, PROMPT_KEY, PROMPT_TYPE)).thenReturn(meta);
        when(aiResourceVersionPersistService.find(NS, PROMPT_KEY, PROMPT_TYPE, "0.0.1"))
            .thenReturn(createVersionRow("0.0.1", "online"));
        when(aiResourcePersistService.updateMetaCas(eq(NS), eq(PROMPT_KEY), eq(PROMPT_TYPE), eq(1L),
            any(AiResource.class))).thenReturn(true);
        
        service.changeOnlineStatus(NS, PROMPT_KEY, "0.0.1", false);
        
        verify(aiResourceVersionPersistService).updateStatus(NS, PROMPT_KEY, PROMPT_TYPE, "0.0.1",
            "offline");
    }
    
    @Test
    void testChangeOnlineStatusShouldRemoveLatestAndLegacyMirrorWhenNoOnlineVersionRemains()
        throws NacosException {
        AiResource meta =
            createMeta(PROMPT_KEY, 1L, "{\"labels\":{\"latest\":\"0.0.1\"},\"onlineCnt\":1}");
        when(aiResourcePersistService.find(NS, PROMPT_KEY, PROMPT_TYPE)).thenReturn(meta);
        when(aiResourceVersionPersistService.find(NS, PROMPT_KEY, PROMPT_TYPE, "0.0.1"))
            .thenReturn(createVersionRow("0.0.1", "online"));
        Page<AiResourceVersion> emptyOnlinePage = new Page<>();
        emptyOnlinePage.setPageItems(Collections.emptyList());
        when(aiResourceVersionPersistService.list(NS, PROMPT_KEY, PROMPT_TYPE, "online", 1, 500))
            .thenReturn(emptyOnlinePage);
        when(aiResourcePersistService.updateMetaCas(eq(NS), eq(PROMPT_KEY), eq(PROMPT_TYPE), eq(1L),
            any(AiResource.class))).thenReturn(true);
        
        service.changeOnlineStatus(NS, PROMPT_KEY, "0.0.1", false);
        
        ArgumentCaptor<AiResource> captor = ArgumentCaptor.forClass(AiResource.class);
        verify(aiResourcePersistService).updateMetaCas(eq(NS), eq(PROMPT_KEY), eq(PROMPT_TYPE),
            eq(1L), captor.capture());
        Map<?, ?> info = JacksonUtils.toObj(captor.getValue().getVersionInfo(), Map.class);
        Map<?, ?> labels = (Map<?, ?>) info.get("labels");
        assertEquals(false, labels.containsKey("latest"));
        verify(configOperationService).deleteConfig(eq(PROMPT_KEY + ".json"),
            eq("nacos-ai-prompt"), eq(NS), any(), any(), eq("nacos"), any());
    }
    
    @Test
    void testChangeOnlineStatusShouldThrowWhenVersionNotFound() {
        AiResource meta = createMeta(PROMPT_KEY, 1L, "{\"labels\":{}}");
        when(aiResourcePersistService.find(NS, PROMPT_KEY, PROMPT_TYPE)).thenReturn(meta);
        when(aiResourceVersionPersistService.find(NS, PROMPT_KEY, PROMPT_TYPE, "0.0.1"))
            .thenReturn(null);
        
        assertThrows(NacosApiException.class,
            () -> service.changeOnlineStatus(NS, PROMPT_KEY, "0.0.1", false));
    }
    
    // ========== updateLabels ==========
    
    @Test
    void testUpdateLabelsShouldIgnoreProvidedLatestLabel() throws NacosException {
        AiResource meta =
            createMeta(PROMPT_KEY, 1L, "{\"labels\":{\"latest\":\"0.0.1\"},\"onlineCnt\":1}");
        when(aiResourcePersistService.find(NS, PROMPT_KEY, PROMPT_TYPE)).thenReturn(meta);
        when(aiResourcePersistService.updateMetaCas(eq(NS), eq(PROMPT_KEY), eq(PROMPT_TYPE),
            anyLong(),
            any(AiResource.class))).thenReturn(true);
        
        Map<String, String> labels = new HashMap<>();
        labels.put("latest", "0.0.2");
        labels.put("stable", "0.0.1");
        
        service.updateLabels(NS, PROMPT_KEY, labels);
        
        ArgumentCaptor<AiResource> captor = ArgumentCaptor.forClass(AiResource.class);
        verify(aiResourcePersistService).updateMetaCas(eq(NS), eq(PROMPT_KEY), eq(PROMPT_TYPE),
            anyLong(), captor.capture());
        Map<String, Object> versionInfo =
            JacksonUtils.toObj(captor.getValue().getVersionInfo(), Map.class);
        Map<String, String> effectiveLabels =
            (Map<String, String>) versionInfo.get("labels");
        assertEquals("0.0.1", effectiveLabels.get("stable"));
        assertEquals("0.0.1", effectiveLabels.get("latest"));
    }
    
    @Test
    void testUpdateLabelsShouldPreserveLatestLabel() throws NacosException {
        AiResource meta =
            createMeta(PROMPT_KEY, 1L, "{\"labels\":{\"latest\":\"0.0.1\"},\"onlineCnt\":1}");
        when(aiResourcePersistService.find(NS, PROMPT_KEY, PROMPT_TYPE)).thenReturn(meta);
        when(aiResourcePersistService.updateMetaCas(eq(NS), eq(PROMPT_KEY), eq(PROMPT_TYPE),
            anyLong(),
            any(AiResource.class))).thenReturn(true);
        
        Map<String, String> labels = new HashMap<>();
        labels.put("stable", "0.0.1");
        
        service.updateLabels(NS, PROMPT_KEY, labels);
        
        ArgumentCaptor<AiResource> captor = ArgumentCaptor.forClass(AiResource.class);
        verify(aiResourcePersistService).updateMetaCas(eq(NS), eq(PROMPT_KEY), eq(PROMPT_TYPE),
            anyLong(), captor.capture());
        Map<String, Object> versionInfo =
            JacksonUtils.toObj(captor.getValue().getVersionInfo(), Map.class);
        Map<String, String> effectiveLabels =
            (Map<String, String>) versionInfo.get("labels");
        assertEquals("0.0.1", effectiveLabels.get("stable"));
        assertEquals("0.0.1", effectiveLabels.get("latest"));
    }
    
    @Test
    void testUpdateLabelsShouldRejectDraftVersion() {
        AiResource meta = createMeta(PROMPT_KEY, 1L,
            "{\"labels\":{\"latest\":\"0.0.1\"},\"editingVersion\":\"0.0.2\",\"onlineCnt\":1}");
        when(aiResourcePersistService.find(NS, PROMPT_KEY, PROMPT_TYPE)).thenReturn(meta);
        Map<String, String> labels = new HashMap<>();
        labels.put("stable", "0.0.2");
        
        assertThrows(NacosApiException.class,
            () -> service.updateLabels(NS, PROMPT_KEY, labels));
        verify(aiResourcePersistService, never()).updateMetaCas(anyString(), anyString(),
            anyString(), anyLong(), any());
    }
    
    @Test
    void testBindLabelShouldPreserveLatestWithoutSubmittingReservedLabel() throws NacosException {
        AiResource meta =
            createMeta(PROMPT_KEY, 1L, "{\"labels\":{\"latest\":\"0.0.1\"},\"onlineCnt\":1}");
        when(aiResourcePersistService.find(NS, PROMPT_KEY, PROMPT_TYPE)).thenReturn(meta);
        Page<AiResourceVersion> versionPage = new Page<>();
        versionPage.setPageItems(Collections.singletonList(createVersionRow("0.0.1", "online")));
        when(aiResourceVersionPersistService.list(eq(NS), eq(PROMPT_KEY), eq(PROMPT_TYPE), any(),
            eq(1), eq(200))).thenReturn(versionPage);
        when(aiResourcePersistService.updateMetaCas(eq(NS), eq(PROMPT_KEY), eq(PROMPT_TYPE),
            eq(1L), any(AiResource.class))).thenReturn(true);
        
        service.bindLabel(NS, PROMPT_KEY, "legacy", "0.0.1");
        
        ArgumentCaptor<AiResource> captor = ArgumentCaptor.forClass(AiResource.class);
        verify(aiResourcePersistService).updateMetaCas(eq(NS), eq(PROMPT_KEY), eq(PROMPT_TYPE),
            eq(1L), captor.capture());
        Map<?, ?> info = JacksonUtils.toObj(captor.getValue().getVersionInfo(), Map.class);
        Map<?, ?> labels = (Map<?, ?>) info.get("labels");
        assertEquals("0.0.1", labels.get("legacy"));
        assertEquals("0.0.1", labels.get("latest"));
    }
    
    // ========== updateBizTags / updateDescription ==========
    
    @Test
    void testUpdateBizTagsViaCas() throws NacosException {
        AiResource meta = createMeta(PROMPT_KEY, 1L, "{\"labels\":{}}");
        when(aiResourcePersistService.find(NS, PROMPT_KEY, PROMPT_TYPE)).thenReturn(meta);
        when(aiResourcePersistService.updateMetaCas(eq(NS), eq(PROMPT_KEY), eq(PROMPT_TYPE), eq(1L),
            any(AiResource.class))).thenReturn(true);
        
        service.updateBizTags(NS, PROMPT_KEY, "[\"tag1\"]");
        
        verify(aiResourcePersistService).updateMetaCas(eq(NS), eq(PROMPT_KEY), eq(PROMPT_TYPE),
            eq(1L),
            any(AiResource.class));
    }
    
    @Test
    void testUpdateDescriptionViaCas() throws NacosException {
        AiResource meta = createMeta(PROMPT_KEY, 1L, "{\"labels\":{}}");
        when(aiResourcePersistService.find(NS, PROMPT_KEY, PROMPT_TYPE)).thenReturn(meta);
        when(aiResourcePersistService.updateMetaCas(eq(NS), eq(PROMPT_KEY), eq(PROMPT_TYPE), eq(1L),
            any(AiResource.class))).thenReturn(true);
        
        service.updateDescription(NS, PROMPT_KEY, "new desc");
        
        verify(aiResourcePersistService).updateMetaCas(eq(NS), eq(PROMPT_KEY), eq(PROMPT_TYPE),
            eq(1L),
            any(AiResource.class));
    }
    
    // ========== deletePrompt ==========
    
    @Test
    void testDeletePromptSuccessfully() throws NacosException {
        AiResource meta = createMeta(PROMPT_KEY, 1L, "{\"labels\":{}}");
        when(aiResourcePersistService.find(NS, PROMPT_KEY, PROMPT_TYPE)).thenReturn(meta);
        
        Page<AiResourceVersion> vPage = new Page<>();
        AiResourceVersion v1 = createVersionRow("0.0.1", "online");
        vPage.setPageItems(Collections.singletonList(v1));
        when(aiResourceVersionPersistService.list(eq(NS), eq(PROMPT_KEY), eq(PROMPT_TYPE), any(),
            eq(1), eq(200))).thenReturn(vPage);
        
        service.deletePrompt(NS, PROMPT_KEY);
        
        verify(aiResourceVersionPersistService).deleteByNameAndType(NS, PROMPT_KEY, PROMPT_TYPE);
        verify(aiResourcePersistService).delete(NS, PROMPT_KEY, PROMPT_TYPE);
    }
    
    @Test
    void testDeletePromptShouldSkipWhenMetaNotFound() throws NacosException {
        when(aiResourcePersistService.find(NS, PROMPT_KEY, PROMPT_TYPE)).thenReturn(null);
        Page<AiResourceVersion> emptyPage = new Page<>();
        emptyPage.setPageItems(new ArrayList<>());
        when(aiResourceVersionPersistService.list(eq(NS), eq(PROMPT_KEY), eq(PROMPT_TYPE), any(),
            eq(1), eq(200))).thenReturn(emptyPage);
        
        service.deletePrompt(NS, PROMPT_KEY);
        
        verify(aiResourceVersionPersistService).deleteByNameAndType(NS, PROMPT_KEY, PROMPT_TYPE);
        verify(aiResourcePersistService).delete(NS, PROMPT_KEY, PROMPT_TYPE);
    }
    
    @Test
    void testDeletePromptShouldCleanLegacyLatestMirror() throws NacosException {
        AiResource meta = createMeta(PROMPT_KEY, 1L, "{\"labels\":{\"latest\":\"0.0.1\"}}");
        when(aiResourcePersistService.find(NS, PROMPT_KEY, PROMPT_TYPE)).thenReturn(meta);
        
        Page<AiResourceVersion> vPage = new Page<>();
        vPage.setPageItems(Collections.singletonList(createVersionRow("0.0.1", "online")));
        when(aiResourceVersionPersistService.list(eq(NS), eq(PROMPT_KEY), eq(PROMPT_TYPE), any(),
            eq(1), eq(200))).thenReturn(vPage);
        
        service.deletePrompt(NS, PROMPT_KEY);
        
        // Should delete legacy mirror: dataId=test-prompt.json, group=nacos-ai-prompt
        verify(configOperationService).deleteConfig(eq(PROMPT_KEY + ".json"), eq("nacos-ai-prompt"),
            eq(NS),
            any(), any(), eq("nacos"), any());
    }
    
    @Test
    void testDeletePromptShouldNotFailWhenLegacyMirrorDeleteThrows() throws NacosException {
        AiResource meta = createMeta(PROMPT_KEY, 1L, "{\"labels\":{}}");
        when(aiResourcePersistService.find(NS, PROMPT_KEY, PROMPT_TYPE)).thenReturn(meta);
        
        Page<AiResourceVersion> emptyPage = new Page<>();
        emptyPage.setPageItems(new ArrayList<>());
        when(aiResourceVersionPersistService.list(eq(NS), eq(PROMPT_KEY), eq(PROMPT_TYPE), any(),
            eq(1), eq(200))).thenReturn(emptyPage);
        
        when(configOperationService.deleteConfig(anyString(), anyString(), anyString(), any(),
            any(), anyString(),
            any())).thenThrow(new RuntimeException("simulated failure"));
        
        // Should NOT throw despite legacy mirror delete failure
        service.deletePrompt(NS, PROMPT_KEY);
        
        verify(aiResourceVersionPersistService).deleteByNameAndType(NS, PROMPT_KEY, PROMPT_TYPE);
        verify(aiResourcePersistService).delete(NS, PROMPT_KEY, PROMPT_TYPE);
    }
    
    // ========== getPromptDetail / getPromptVersionDetail ==========
    
    @Test
    void testGetPromptDetailSuccessfully() throws NacosException {
        AiResource meta = createMeta(PROMPT_KEY, 1L,
            "{\"labels\":{\"latest\":\"0.0.1\"},\"editingVersion\":\"0.0.2\",\"onlineCnt\":1}");
        when(aiResourcePersistService.find(NS, PROMPT_KEY, PROMPT_TYPE)).thenReturn(meta);
        
        Page<AiResourceVersion> vPage = new Page<>();
        vPage.setPageItems(Collections.singletonList(createVersionRow("0.0.1", "online")));
        when(aiResourceVersionPersistService.list(eq(NS), eq(PROMPT_KEY), eq(PROMPT_TYPE), any(),
            eq(1), eq(200))).thenReturn(vPage);
        
        PromptMetaInfo detail = service.getPromptDetail(NS, PROMPT_KEY);
        
        assertNotNull(detail);
        assertEquals(PROMPT_KEY, detail.getPromptKey());
        assertEquals("0.0.1", detail.getLatestVersion());
        assertEquals("0.0.2", detail.getEditingVersion());
        assertEquals(1, detail.getOnlineCnt());
    }
    
    @Test
    void testGetPromptVersionDetailSuccessfully() throws NacosException {
        AiResource meta = createMeta(PROMPT_KEY, 1L, "{\"labels\":{}}");
        when(aiResourcePersistService.find(NS, PROMPT_KEY, PROMPT_TYPE)).thenReturn(meta);
        when(aiResourceVersionPersistService.find(NS, PROMPT_KEY, PROMPT_TYPE, "0.0.1"))
            .thenReturn(createVersionRow("0.0.1", "online"));
        
        PromptVersionInfo content = new PromptVersionInfo();
        content.setTemplate("hello");
        mockStorageGet(JacksonUtils.toJson(content).getBytes(StandardCharsets.UTF_8));
        
        PromptVersionInfo result = service.getPromptVersionDetail(NS, PROMPT_KEY, "0.0.1");
        
        assertNotNull(result);
        assertEquals(PROMPT_KEY, result.getPromptKey());
        assertEquals("0.0.1", result.getVersion());
    }
    
    // ========== listPrompts / listPromptVersions ==========
    
    @Test
    void testListPromptsSuccessfully() throws NacosException {
        Page<AiResource> metaPage = new Page<>();
        metaPage.setTotalCount(1);
        metaPage.setPagesAvailable(1);
        AiResource r =
            createMeta(PROMPT_KEY, 1L, "{\"labels\":{\"latest\":\"0.0.1\"},\"onlineCnt\":1}");
        metaPage.setPageItems(Collections.singletonList(r));
        when(aiResourcePersistService.list(eq(NS), eq(PROMPT_TYPE), any(), any(), eq(1), eq(10)))
            .thenReturn(metaPage);
        
        Page<PromptMetaSummary> result = service.listPrompts(NS, null, null, null, 1, 10);
        
        assertNotNull(result);
        assertEquals(1, result.getTotalCount());
        assertEquals(1, result.getPageItems().size());
        assertEquals(PROMPT_KEY, result.getPageItems().get(0).getPromptKey());
    }
    
    @Test
    void testListPromptVersionsSuccessfully() throws NacosException {
        AiResource meta = createMeta(PROMPT_KEY, 1L, "{\"labels\":{}}");
        when(aiResourcePersistService.find(NS, PROMPT_KEY, PROMPT_TYPE)).thenReturn(meta);
        
        Page<AiResourceVersion> vPage = new Page<>();
        vPage.setTotalCount(1);
        vPage.setPagesAvailable(1);
        vPage.setPageItems(Collections.singletonList(createVersionRow("0.0.1", "online")));
        when(aiResourceVersionPersistService.list(eq(NS), eq(PROMPT_KEY), eq(PROMPT_TYPE), any(),
            eq(1), eq(10)))
            .thenReturn(vPage);
        
        Page<PromptVersionSummary> result = service.listPromptVersions(NS, PROMPT_KEY, 1, 10);
        
        assertNotNull(result);
        assertEquals(1, result.getTotalCount());
        assertEquals("0.0.1", result.getPageItems().get(0).getVersion());
    }
    
    // ========== queryPrompt (Client) ==========
    
    @Test
    void testQueryPromptByExplicitVersion() throws NacosException {
        AiResource meta = createMeta(PROMPT_KEY, 1L, "{\"labels\":{\"latest\":\"0.0.1\"}}");
        when(aiResourcePersistService.find(NS, PROMPT_KEY, PROMPT_TYPE)).thenReturn(meta);
        when(aiResourceVersionPersistService.find(NS, PROMPT_KEY, PROMPT_TYPE, "0.0.1"))
            .thenReturn(createVersionRow("0.0.1", "online"));
        
        PromptVersionInfo content = new PromptVersionInfo();
        content.setTemplate("hello");
        mockStorageGet(JacksonUtils.toJson(content).getBytes(StandardCharsets.UTF_8));
        
        PromptVersionInfo result = service.queryPrompt(NS, PROMPT_KEY, "0.0.1", null);
        
        assertNotNull(result);
        assertEquals(PROMPT_KEY, result.getPromptKey());
    }
    
    @Test
    void testQueryPromptByLabel() throws NacosException {
        AiResource meta = createMeta(PROMPT_KEY, 1L, "{\"labels\":{\"stable\":\"0.0.1\"}}");
        when(aiResourcePersistService.find(NS, PROMPT_KEY, PROMPT_TYPE)).thenReturn(meta);
        when(aiResourceVersionPersistService.find(NS, PROMPT_KEY, PROMPT_TYPE, "0.0.1"))
            .thenReturn(createVersionRow("0.0.1", "online"));
        
        PromptVersionInfo content = new PromptVersionInfo();
        content.setTemplate("hello");
        mockStorageGet(JacksonUtils.toJson(content).getBytes(StandardCharsets.UTF_8));
        
        PromptVersionInfo result = service.queryPrompt(NS, PROMPT_KEY, null, "stable");
        
        assertNotNull(result);
        assertEquals("0.0.1", result.getVersion());
    }
    
    // ========== downloadPromptVersion ==========
    
    @Test
    void testDownloadPromptVersionReturnsDetailAndPublishesEvent() throws NacosException {
        AiResource meta = createMeta(PROMPT_KEY, 1L, "{\"labels\":{}}");
        when(aiResourcePersistService.find(NS, PROMPT_KEY, PROMPT_TYPE)).thenReturn(meta);
        when(aiResourceVersionPersistService.find(NS, PROMPT_KEY, PROMPT_TYPE, "0.0.1"))
            .thenReturn(createVersionRow("0.0.1", "online"));
        
        PromptVersionInfo content = new PromptVersionInfo();
        content.setTemplate("hello");
        mockStorageGet(JacksonUtils.toJson(content).getBytes(StandardCharsets.UTF_8));
        
        try (MockedStatic<NotifyCenter> notifyCenterStatic = mockStatic(NotifyCenter.class)) {
            PromptVersionInfo result = service.downloadPromptVersion(NS, PROMPT_KEY, "0.0.1");
            
            assertNotNull(result);
            assertEquals(PROMPT_KEY, result.getPromptKey());
            assertEquals("0.0.1", result.getVersion());
            
            ArgumentCaptor<PromptDownloadEvent> captor =
                ArgumentCaptor.forClass(PromptDownloadEvent.class);
            notifyCenterStatic.verify(() -> NotifyCenter.publishEvent(captor.capture()));
            
            PromptDownloadEvent published = captor.getValue();
            assertEquals(NS, published.getNamespaceId());
            assertEquals(PROMPT_KEY, published.getName());
            assertEquals("0.0.1", published.getVersion());
        }
    }
    
    // ========== refreshLatestMirror ==========
    
    @Test
    void testRefreshLatestMirrorPublishesToLegacyConfig() throws NacosException {
        AiResource meta = createMeta(PROMPT_KEY, 1L, "{\"labels\":{\"latest\":\"0.0.1\"}}");
        when(aiResourcePersistService.find(NS, PROMPT_KEY, PROMPT_TYPE)).thenReturn(meta);
        
        PromptVersionInfo content = new PromptVersionInfo();
        content.setTemplate("hello");
        mockStorageGet(JacksonUtils.toJson(content).getBytes(StandardCharsets.UTF_8));
        
        service.refreshLatestMirror(NS, PROMPT_KEY);
        
        verify(configOperationService).publishConfig(any(), any(), any());
    }
    
    // ========== Helper methods ==========
    
    private AiResource createMeta(String name, Long metaVersion, String versionInfoJson) {
        AiResource meta = new AiResource();
        meta.setNamespaceId(NS);
        meta.setName(name);
        meta.setType(PROMPT_TYPE);
        meta.setStatus("enable");
        meta.setMetaVersion(metaVersion);
        meta.setVersionInfo(versionInfoJson);
        return meta;
    }
    
    private AiResourceVersion createVersionRow(String version, String status) {
        AiResourceVersion row = new AiResourceVersion();
        row.setNamespaceId(NS);
        row.setName(PROMPT_KEY);
        row.setType(PROMPT_TYPE);
        row.setVersion(version);
        row.setStatus(status);
        row.setAuthor("-");
        return row;
    }
    
    private void assertSubmitPipelineCompletionMovesVersionToReviewed(
        PipelineExecutionStatus status)
        throws NacosException {
        AiResource meta =
            createMeta(PROMPT_KEY, 1L, "{\"labels\":{},\"editingVersion\":\"0.0.1\"}");
        when(aiResourcePersistService.find(NS, PROMPT_KEY, PROMPT_TYPE)).thenReturn(meta);
        when(aiResourceVersionPersistService.find(NS, PROMPT_KEY, PROMPT_TYPE, "0.0.1"))
            .thenReturn(createVersionRow("0.0.1", "draft"));
        when(aiResourcePersistService.updateMetaCas(eq(NS), eq(PROMPT_KEY), eq(PROMPT_TYPE),
            anyLong(), any(AiResource.class))).thenReturn(true);
        
        PromptVersionInfo content = new PromptVersionInfo();
        content.setTemplate("hello");
        mockStorageGet(JacksonUtils.toJson(content).getBytes(StandardCharsets.UTF_8));
        
        PublishPipelineExecutor pipelineExecutor = mock(PublishPipelineExecutor.class);
        when(pipelineExecutor.isPipelineAvailable(any())).thenReturn(true);
        when(pipelineExecutor.execute(any(), any(), anyString())).thenAnswer(invocation -> {
            PipelineCallback callback = invocation.getArgument(1);
            PipelineExecutionResult pipelineResult = new PipelineExecutionResult();
            pipelineResult.setExecutionId(invocation.getArgument(2));
            pipelineResult.setStatus(status);
            pipelineResult.setPipeline(Collections.emptyList());
            callback.onComplete(pipelineResult);
            return invocation.getArgument(2);
        });
        PromptOperationServiceImpl serviceWithPipeline =
            new PromptOperationServiceImpl(pipelineExecutor, configOperationService,
                new AiResourceManager(aiResourcePersistService, aiResourceVersionPersistService,
                    pipelineExecutionRepository),
                promptDataMigrationTask);
        
        String result = serviceWithPipeline.submit(NS, PROMPT_KEY, null);
        
        assertEquals("0.0.1", result);
        ArgumentCaptor<String> statusCaptor = ArgumentCaptor.forClass(String.class);
        verify(aiResourceVersionPersistService, org.mockito.Mockito.atLeast(2))
            .updateStatus(eq(NS), eq(PROMPT_KEY), eq(PROMPT_TYPE), eq("0.0.1"),
                statusCaptor.capture());
        assertEquals("reviewed",
            statusCaptor.getAllValues().get(statusCaptor.getAllValues().size() - 1));
    }
    
    private void mockStorageGet(byte[] content) throws NacosException {
        lenient().when(storage.get(any(StorageKey.class))).thenReturn(content);
    }
}
