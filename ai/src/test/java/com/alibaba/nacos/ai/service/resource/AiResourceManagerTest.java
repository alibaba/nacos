/*
 * Copyright 1999-2025 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.ai.service.resource;

import com.alibaba.nacos.ai.constant.AiResourceConstants;
import com.alibaba.nacos.ai.model.AiResource;
import com.alibaba.nacos.ai.model.AiResourceVersion;
import com.alibaba.nacos.ai.pipeline.PublishPipelineExecutor;
import com.alibaba.nacos.ai.pipeline.model.PipelineExecution;
import com.alibaba.nacos.ai.pipeline.model.PipelineExecutionResult;
import com.alibaba.nacos.ai.pipeline.model.PipelineExecutionStatus;
import com.alibaba.nacos.ai.service.repository.AiResourcePersistService;
import com.alibaba.nacos.ai.service.repository.AiResourceVersionPersistService;
import com.alibaba.nacos.ai.service.repository.QueryCondition;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.plugin.ai.pipeline.model.ResourceFilesPipelineContext;
import com.alibaba.nacos.plugin.visibility.constant.VisibilityConstants;
import com.alibaba.nacos.plugin.visibility.spi.VisibilityPluginManager;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.StandardEnvironment;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link AiResourceManager}.
 *
 * @author nacos
 */
@ExtendWith(MockitoExtension.class)
class AiResourceManagerTest {
    
    private static final String NAMESPACE_ID = "public";
    
    private static final String RESOURCE_TYPE = "skill";
    
    @Mock
    private AiResourcePersistService aiResourcePersistService;
    
    @Mock
    private AiResourceVersionPersistService aiResourceVersionPersistService;
    
    @Mock
    private com.alibaba.nacos.ai.pipeline.repository.PipelineExecutionRepository pipelineExecutionRepository;
    
    private AiResourceManager manager;
    
    private MockedStatic<VisibilityPluginManager> visibilityManagerStatic;
    
    private VisibilityPluginManager mockVisibilityManager;
    
    private static final ConfigurableEnvironment CACHED_ENVIRONMENT = EnvUtil.getEnvironment();
    
    @BeforeEach
    void setUp() {
        EnvUtil.setEnvironment(new StandardEnvironment());
        manager = new AiResourceManager(aiResourcePersistService, aiResourceVersionPersistService,
            pipelineExecutionRepository);
        mockVisibilityManager = mock(VisibilityPluginManager.class);
        lenient().when(mockVisibilityManager.findVisibilityService(anyString()))
            .thenReturn(Optional.empty());
        visibilityManagerStatic = org.mockito.Mockito.mockStatic(VisibilityPluginManager.class);
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
    
    // ---- parseVersionInfo ----
    
    @Test
    void parseVersionInfoShouldReturnNullForBlankInput() {
        assertNull(AiResourceManager.parseVersionInfo(null));
        assertNull(AiResourceManager.parseVersionInfo(""));
        assertNull(AiResourceManager.parseVersionInfo("   "));
    }
    
    @Test
    void parseVersionInfoShouldReturnNullForInvalidJson() {
        assertNull(AiResourceManager.parseVersionInfo("not-json"));
    }
    
    @Test
    void parseVersionInfoShouldParseValidJson() {
        String json =
            "{\"editingVersion\":\"v2\",\"reviewingVersion\":\"v1\",\"onlineCnt\":3,\"labels\":{\"latest\":\"v1\"}}";
        ResourceVersionInfo info = AiResourceManager.parseVersionInfo(json);
        assertNotNull(info);
        assertEquals("v2", info.getEditingVersion());
        assertEquals("v1", info.getReviewingVersion());
        assertEquals(3, info.getOnlineCnt());
        assertEquals("v1", info.getLabels().get("latest"));
    }
    
    // ---- requireVersionInfo ----
    
    @Test
    void requireVersionInfoShouldReturnEmptyInfoForNullMeta() {
        ResourceVersionInfo info = AiResourceManager.requireVersionInfo(null);
        assertNotNull(info);
        assertNotNull(info.getLabels());
        assertNull(info.getEditingVersion());
    }
    
    @Test
    void requireVersionInfoShouldReturnEmptyInfoForBlankVersionInfo() {
        AiResource meta = new AiResource();
        meta.setVersionInfo("");
        ResourceVersionInfo info = AiResourceManager.requireVersionInfo(meta);
        assertNotNull(info);
        assertNotNull(info.getLabels());
    }
    
    @Test
    void requireVersionInfoShouldInitLabelsIfNull() {
        AiResource meta = new AiResource();
        meta.setVersionInfo("{\"onlineCnt\":1}");
        ResourceVersionInfo info = AiResourceManager.requireVersionInfo(meta);
        assertNotNull(info);
        assertNotNull(info.getLabels());
        assertEquals(1, info.getOnlineCnt());
    }
    
    @Test
    void requireVersionInfoShouldPreserveExistingLabels() {
        AiResource meta = new AiResource();
        meta.setVersionInfo("{\"labels\":{\"latest\":\"v3\"},\"onlineCnt\":2}");
        ResourceVersionInfo info = AiResourceManager.requireVersionInfo(meta);
        assertEquals("v3", info.getLabels().get("latest"));
        assertEquals(2, info.getOnlineCnt());
    }
    
    // ---- parsePublishPipelineInfo ----
    
    @Test
    void parsePublishPipelineInfoShouldReturnNullForBlankInput() {
        assertNull(AiResourceManager.parsePublishPipelineInfo(null));
        assertNull(AiResourceManager.parsePublishPipelineInfo(""));
    }
    
    @Test
    void parsePublishPipelineInfoShouldReturnNullForInvalidJson() {
        assertNull(AiResourceManager.parsePublishPipelineInfo("bad-json"));
    }
    
    @Test
    void parsePublishPipelineInfoShouldReturnNullWhenExecutionIdBlank() {
        assertNull(AiResourceManager.parsePublishPipelineInfo("{\"status\":\"APPROVED\"}"));
    }
    
    @Test
    void parsePublishPipelineInfoShouldParseValidJson() {
        String json = "{\"executionId\":\"exec-1\",\"status\":\"APPROVED\",\"pipeline\":[]}";
        PublishPipelineInfo info = AiResourceManager.parsePublishPipelineInfo(json);
        assertNotNull(info);
        assertEquals("exec-1", info.getExecutionId());
        assertEquals(PipelineExecutionStatus.APPROVED, info.getStatus());
        assertNotNull(info.getPipeline());
    }
    
    // ---- buildEmptyPage ----
    
    @Test
    void buildEmptyPageShouldReturnCorrectPage() {
        Page<String> page = AiResourceManager.buildEmptyPage(3);
        assertNotNull(page);
        assertNotNull(page.getPageItems());
        assertTrue(page.getPageItems().isEmpty());
        assertEquals(0, page.getTotalCount());
        assertEquals(0, page.getPagesAvailable());
        assertEquals(3, page.getPageNumber());
    }
    
    // ---- resolveScope ----
    
    @Test
    void resolveScopeShouldReturnPrivateForNullMeta() {
        assertEquals(VisibilityConstants.SCOPE_PRIVATE, AiResourceManager.resolveScope(null));
    }
    
    @Test
    void resolveScopeShouldReturnPrivateForBlankScope() {
        AiResource meta = new AiResource();
        meta.setScope("");
        assertEquals(VisibilityConstants.SCOPE_PRIVATE, AiResourceManager.resolveScope(meta));
    }
    
    @Test
    void resolveScopeShouldReturnActualScope() {
        AiResource meta = new AiResource();
        meta.setScope(VisibilityConstants.SCOPE_PUBLIC);
        assertEquals(VisibilityConstants.SCOPE_PUBLIC, AiResourceManager.resolveScope(meta));
    }
    
    // ---- resolveVersion ----
    
    @Test
    void resolveVersionShouldReturnLabelVersion() {
        AiResource meta = new AiResource();
        meta.setVersionInfo("{\"labels\":{\"stable\":\"v2\",\"latest\":\"v3\"}}");
        assertEquals("v2", AiResourceManager.resolveVersion(meta, "v1", "stable"));
    }
    
    @Test
    void resolveVersionShouldReturnExplicitVersionWhenLabelMissing() {
        AiResource meta = new AiResource();
        meta.setVersionInfo("{\"labels\":{\"latest\":\"v3\"}}");
        assertEquals("v1", AiResourceManager.resolveVersion(meta, "v1", "nonexistent"));
    }
    
    @Test
    void resolveVersionShouldReturnExplicitVersion() {
        AiResource meta = new AiResource();
        meta.setVersionInfo("{\"labels\":{\"latest\":\"v3\"}}");
        assertEquals("v1", AiResourceManager.resolveVersion(meta, "v1", null));
    }
    
    @Test
    void resolveVersionShouldFallbackToLatestLabel() {
        AiResource meta = new AiResource();
        meta.setVersionInfo("{\"labels\":{\"latest\":\"v5\"}}");
        assertEquals("v5", AiResourceManager.resolveVersion(meta, null, null));
    }
    
    @Test
    void resolveVersionShouldReturnNullWhenNoVersionResolvable() {
        AiResource meta = new AiResource();
        meta.setVersionInfo("{\"labels\":{}}");
        assertNull(AiResourceManager.resolveVersion(meta, null, null));
    }
    
    // ---- requireMeta ----
    
    @Test
    void requireMetaShouldReturnMetaWhenFound() throws NacosException {
        AiResource meta = buildMeta("test-resource");
        when(aiResourcePersistService.find(NAMESPACE_ID, "test-resource", RESOURCE_TYPE))
            .thenReturn(meta);
        
        AiResource result = manager.requireMeta(NAMESPACE_ID, "test-resource", RESOURCE_TYPE);
        assertEquals(meta, result);
    }
    
    @Test
    void requireMetaShouldThrowNotFoundWhenMissing() {
        when(aiResourcePersistService.find(NAMESPACE_ID, "missing", RESOURCE_TYPE))
            .thenReturn(null);
        
        NacosApiException ex = assertThrows(NacosApiException.class,
            () -> manager.requireMeta(NAMESPACE_ID, "missing", RESOURCE_TYPE));
        assertEquals(NacosException.NOT_FOUND, ex.getErrCode());
        assertTrue(ex.getErrMsg().contains("missing"));
    }
    
    // ---- passthrough persistence helpers ----
    
    @Test
    void findMetaShouldDelegateToPersistService() {
        AiResource meta = buildMeta("res");
        when(aiResourcePersistService.find(NAMESPACE_ID, "res", RESOURCE_TYPE)).thenReturn(meta);
        assertEquals(meta, manager.findMeta(NAMESPACE_ID, "res", RESOURCE_TYPE));
    }
    
    @Test
    void findVersionShouldDelegateToPersistService() {
        AiResourceVersion row = new AiResourceVersion();
        row.setVersion("v1");
        when(aiResourceVersionPersistService.find(NAMESPACE_ID, "res", RESOURCE_TYPE, "v1"))
            .thenReturn(row);
        assertEquals(row, manager.findVersion(NAMESPACE_ID, "res", RESOURCE_TYPE, "v1"));
    }
    
    @Test
    void updateVersionStorageAndDescShouldDelegate() {
        manager.updateVersionStorageAndDesc(NAMESPACE_ID, "res", RESOURCE_TYPE, "v1", "{\"x\":1}",
            "desc");
        verify(aiResourceVersionPersistService).updateStorageAndDesc(NAMESPACE_ID, "res",
            RESOURCE_TYPE, "v1",
            "{\"x\":1}", "desc");
    }
    
    @Test
    void updateVersionStorageShouldDelegate() {
        manager.updateVersionStorage(NAMESPACE_ID, "res", RESOURCE_TYPE, "v1", "{\"x\":1}");
        verify(aiResourceVersionPersistService).updateStorage(NAMESPACE_ID, "res", RESOURCE_TYPE,
            "v1", "{\"x\":1}");
    }
    
    @Test
    void updateVersionStatusShouldDelegate() {
        manager.updateVersionStatus(NAMESPACE_ID, "res", RESOURCE_TYPE, "v1",
            AiResourceConstants.VERSION_STATUS_ONLINE);
        verify(aiResourceVersionPersistService).updateStatus(NAMESPACE_ID, "res", RESOURCE_TYPE,
            "v1",
            AiResourceConstants.VERSION_STATUS_ONLINE);
    }
    
    @Test
    void updateVersionPublishPipelineInfoShouldDelegate() {
        manager.updateVersionPublishPipelineInfo(NAMESPACE_ID, "res", RESOURCE_TYPE, "v1",
            "{\"executionId\":\"1\"}");
        verify(aiResourceVersionPersistService).updatePublishPipelineInfo(NAMESPACE_ID, "res",
            RESOURCE_TYPE, "v1",
            "{\"executionId\":\"1\"}");
    }
    
    @Test
    void deleteVersionShouldDelegate() {
        manager.deleteVersion(NAMESPACE_ID, "res", RESOURCE_TYPE, "v1");
        verify(aiResourceVersionPersistService).delete(NAMESPACE_ID, "res", RESOURCE_TYPE, "v1");
    }
    
    @Test
    void deleteVersionsByNameAndTypeShouldDelegate() {
        manager.deleteVersionsByNameAndType(NAMESPACE_ID, "res", RESOURCE_TYPE);
        verify(aiResourceVersionPersistService).deleteByNameAndType(NAMESPACE_ID, "res",
            RESOURCE_TYPE);
    }
    
    @Test
    void deleteMetaShouldDelegate() {
        manager.deleteMeta(NAMESPACE_ID, "res", RESOURCE_TYPE);
        verify(aiResourcePersistService).delete(NAMESPACE_ID, "res", RESOURCE_TYPE);
    }
    
    @Test
    void generateLikeArgumentShouldDelegate() {
        when(aiResourcePersistService.generateLikeArgument("%abc%")).thenReturn("like-value");
        assertEquals("like-value", manager.generateLikeArgument("%abc%"));
    }
    
    @Test
    void listMetaByTypeShouldDelegate() {
        Page<AiResource> expected = new Page<>();
        when(aiResourcePersistService.list(NAMESPACE_ID, RESOURCE_TYPE, "name", "tag", 1, 10))
            .thenReturn(expected);
        assertEquals(expected,
            manager.listMetaByType(NAMESPACE_ID, RESOURCE_TYPE, "name", "tag", 1, 10));
    }
    
    @Test
    void listMetaShouldDelegate() {
        QueryCondition condition = new QueryCondition();
        Page<AiResource> expected = new Page<>();
        when(aiResourcePersistService.list(condition, 1, 10)).thenReturn(expected);
        assertEquals(expected, manager.listMeta(condition, 1, 10));
    }
    
    @Test
    void listVersionsShouldDelegate() {
        Page<AiResourceVersion> expected = new Page<>();
        when(aiResourceVersionPersistService.list(NAMESPACE_ID, "res", RESOURCE_TYPE, "online", 1,
            10))
            .thenReturn(expected);
        assertEquals(expected,
            manager.listVersions(NAMESPACE_ID, "res", RESOURCE_TYPE, "online", 1, 10));
    }
    
    // ---- updateVersionInfoCas ----
    
    @Test
    void updateVersionInfoCasShouldThrowOnNullMeta() {
        assertThrows(NacosApiException.class,
            () -> manager.updateVersionInfoCas(NAMESPACE_ID, null, new ResourceVersionInfo()));
    }
    
    @Test
    void updateVersionInfoCasShouldThrowOnNullMetaVersion() {
        AiResource meta = new AiResource();
        meta.setName("res");
        meta.setType(RESOURCE_TYPE);
        assertThrows(NacosApiException.class,
            () -> manager.updateVersionInfoCas(NAMESPACE_ID, meta, new ResourceVersionInfo()));
    }
    
    @Test
    void updateVersionInfoCasShouldSucceedOnFirstAttempt() throws NacosException {
        AiResource meta = buildMeta("res");
        when(aiResourcePersistService.updateMetaCas(eq(NAMESPACE_ID), eq("res"), eq(RESOURCE_TYPE),
            eq(1L), any()))
            .thenReturn(true);
        
        ResourceVersionInfo info = new ResourceVersionInfo();
        info.setEditingVersion("v2");
        manager.updateVersionInfoCas(NAMESPACE_ID, meta, info);
        
        verify(aiResourcePersistService).updateMetaCas(eq(NAMESPACE_ID), eq("res"),
            eq(RESOURCE_TYPE), eq(1L), any());
    }
    
    @Test
    void updateVersionInfoCasShouldRetryAndSucceed() throws NacosException {
        AiResource meta = buildMeta("res");
        AiResource updated = buildMeta("res");
        updated.setMetaVersion(2L);
        
        when(aiResourcePersistService.updateMetaCas(eq(NAMESPACE_ID), eq("res"), eq(RESOURCE_TYPE),
            eq(1L), any()))
            .thenReturn(false);
        when(aiResourcePersistService.find(NAMESPACE_ID, "res", RESOURCE_TYPE)).thenReturn(updated);
        when(aiResourcePersistService.updateMetaCas(eq(NAMESPACE_ID), eq("res"), eq(RESOURCE_TYPE),
            eq(2L), any()))
            .thenReturn(true);
        
        manager.updateVersionInfoCas(NAMESPACE_ID, meta, new ResourceVersionInfo());
    }
    
    @Test
    void updateVersionInfoCasShouldThrowConflictAfterMaxRetries() {
        AiResource meta = buildMeta("res");
        when(aiResourcePersistService.updateMetaCas(eq(NAMESPACE_ID), eq("res"), eq(RESOURCE_TYPE),
            anyLong(), any()))
            .thenReturn(false);
        AiResource latest = buildMeta("res");
        latest.setMetaVersion(99L);
        when(aiResourcePersistService.find(NAMESPACE_ID, "res", RESOURCE_TYPE)).thenReturn(latest);
        
        NacosApiException ex = assertThrows(NacosApiException.class,
            () -> manager.updateVersionInfoCas(NAMESPACE_ID, meta, new ResourceVersionInfo()));
        assertEquals(NacosException.CONFLICT, ex.getErrCode());
    }
    
    @Test
    void updateVersionInfoCasShouldThrowWhenRetryFindReturnsNull() {
        AiResource meta = buildMeta("res");
        when(aiResourcePersistService.updateMetaCas(eq(NAMESPACE_ID), eq("res"), eq(RESOURCE_TYPE),
            eq(1L), any()))
            .thenReturn(false);
        when(aiResourcePersistService.find(NAMESPACE_ID, "res", RESOURCE_TYPE)).thenReturn(null);
        
        NacosApiException ex = assertThrows(NacosApiException.class,
            () -> manager.updateVersionInfoCas(NAMESPACE_ID, meta, new ResourceVersionInfo()));
        assertEquals(NacosException.SERVER_ERROR, ex.getErrCode());
    }
    
    @Test
    void markEditingVersionCasShouldPreserveLatestLabelOnCasRetry() throws NacosException {
        AiResource meta = buildMeta("res");
        meta.setVersionInfo("{\"labels\":{\"latest\":\"v1\"},\"onlineCnt\":1}");
        AiResource latestMeta = buildMeta("res");
        latestMeta.setMetaVersion(2L);
        latestMeta.setVersionInfo(
            "{\"labels\":{\"latest\":\"v2\",\"stable\":\"v1\"},\"onlineCnt\":2}");
        
        when(aiResourcePersistService.updateMetaCas(eq(NAMESPACE_ID), eq("res"), eq(RESOURCE_TYPE),
            eq(1L), any()))
            .thenReturn(false);
        when(aiResourcePersistService.find(NAMESPACE_ID, "res", RESOURCE_TYPE))
            .thenReturn(latestMeta);
        when(aiResourcePersistService.updateMetaCas(eq(NAMESPACE_ID), eq("res"), eq(RESOURCE_TYPE),
            eq(2L), any()))
            .thenReturn(true);
        
        manager.markEditingVersionCas(NAMESPACE_ID, meta,
            AiResourceManager.requireVersionInfo(meta), "v3", "create draft");
        
        ArgumentCaptor<AiResource> retryCaptor = ArgumentCaptor.forClass(AiResource.class);
        verify(aiResourcePersistService).updateMetaCas(eq(NAMESPACE_ID), eq("res"),
            eq(RESOURCE_TYPE), eq(2L), retryCaptor.capture());
        ResourceVersionInfo savedInfo =
            JacksonUtils.toObj(retryCaptor.getValue().getVersionInfo(), ResourceVersionInfo.class);
        assertEquals("v3", savedInfo.getEditingVersion());
        assertEquals("v2", savedInfo.getLabels().get(AiResourceConstants.LABEL_LATEST));
        assertEquals("v1", savedInfo.getLabels().get("stable"));
    }
    
    @Test
    void markEditingVersionCasShouldThrowWhenWorkingVersionAppearsOnCasRetry() {
        AiResource meta = buildMeta("res");
        AiResource latestMeta = buildMeta("res");
        latestMeta.setMetaVersion(2L);
        latestMeta.setVersionInfo("{\"editingVersion\":\"other\",\"labels\":{},\"onlineCnt\":0}");
        
        when(aiResourcePersistService.updateMetaCas(eq(NAMESPACE_ID), eq("res"), eq(RESOURCE_TYPE),
            eq(1L), any()))
            .thenReturn(false);
        when(aiResourcePersistService.find(NAMESPACE_ID, "res", RESOURCE_TYPE))
            .thenReturn(latestMeta);
        
        NacosApiException ex = assertThrows(NacosApiException.class,
            () -> manager.markEditingVersionCas(NAMESPACE_ID, meta,
                AiResourceManager.requireVersionInfo(meta), "v3", "create draft"));
        assertEquals(NacosException.CONFLICT, ex.getErrCode());
        verify(aiResourcePersistService, never()).updateMetaCas(eq(NAMESPACE_ID), eq("res"),
            eq(RESOURCE_TYPE), eq(2L), any());
    }
    
    // ---- updateBizTagsCas ----
    
    @Test
    void updateBizTagsCasShouldSucceedOnFirstAttempt() throws NacosException {
        AiResource meta = buildMeta("res");
        when(aiResourcePersistService.updateMetaCas(eq(NAMESPACE_ID), eq("res"), eq(RESOURCE_TYPE),
            eq(1L), any()))
            .thenReturn(true);
        
        manager.updateBizTagsCas(NAMESPACE_ID, meta, "[\"tag1\"]");
        
        verify(aiResourcePersistService).updateMetaCas(eq(NAMESPACE_ID), eq("res"),
            eq(RESOURCE_TYPE), eq(1L), any());
    }
    
    @Test
    void updateBizTagsCasShouldThrowOnNullMeta() {
        assertThrows(NacosApiException.class,
            () -> manager.updateBizTagsCas(NAMESPACE_ID, null, "[\"tag1\"]"));
    }
    
    @Test
    void updateBizTagsCasShouldRetryAndSucceedOnCasConflict() throws NacosException {
        AiResource meta = buildMeta("res");
        meta.setDesc("d1");
        meta.setExt("e1");
        meta.setVersionInfo("{\"onlineCnt\":1}");
        
        AiResource latestMeta = buildMeta("res");
        latestMeta.setMetaVersion(2L);
        latestMeta.setStatus(AiResourceConstants.META_STATUS_DISABLE);
        latestMeta.setDesc("d2");
        latestMeta.setExt("e2");
        latestMeta.setVersionInfo("{\"onlineCnt\":2}");
        
        when(aiResourcePersistService.updateMetaCas(eq(NAMESPACE_ID), eq("res"), eq(RESOURCE_TYPE),
            eq(1L), any()))
            .thenReturn(false);
        when(aiResourcePersistService.find(NAMESPACE_ID, "res", RESOURCE_TYPE))
            .thenReturn(latestMeta);
        when(aiResourcePersistService.updateMetaCas(eq(NAMESPACE_ID), eq("res"), eq(RESOURCE_TYPE),
            eq(2L), any()))
            .thenReturn(true);
        
        manager.updateBizTagsCas(NAMESPACE_ID, meta, "[\"newTag\"]");
        
        ArgumentCaptor<AiResource> captor = ArgumentCaptor.forClass(AiResource.class);
        verify(aiResourcePersistService).updateMetaCas(eq(NAMESPACE_ID), eq("res"),
            eq(RESOURCE_TYPE), eq(2L),
            captor.capture());
        AiResource written = captor.getValue();
        assertEquals("[\"newTag\"]", written.getBizTags());
        assertEquals(AiResourceConstants.META_STATUS_DISABLE, written.getStatus());
        assertEquals("d2", written.getDesc());
        assertEquals("e2", written.getExt());
        assertEquals("{\"onlineCnt\":2}", written.getVersionInfo());
    }
    
    @Test
    void updateBizTagsCasShouldThrowWhenRetryFindReturnsNull() {
        AiResource meta = buildMeta("res");
        when(aiResourcePersistService.updateMetaCas(eq(NAMESPACE_ID), eq("res"), eq(RESOURCE_TYPE),
            eq(1L), any()))
            .thenReturn(false);
        when(aiResourcePersistService.find(NAMESPACE_ID, "res", RESOURCE_TYPE)).thenReturn(null);
        
        NacosApiException ex = assertThrows(NacosApiException.class,
            () -> manager.updateBizTagsCas(NAMESPACE_ID, meta, "[\"tag\"]"));
        assertEquals(NacosException.SERVER_ERROR, ex.getErrCode());
    }
    
    @Test
    void updateBizTagsCasShouldThrowConflictAfterMaxRetries() {
        AiResource meta = buildMeta("res");
        when(aiResourcePersistService.updateMetaCas(eq(NAMESPACE_ID), eq("res"), eq(RESOURCE_TYPE),
            anyLong(), any()))
            .thenReturn(false);
        AiResource latest = buildMeta("res");
        latest.setMetaVersion(99L);
        when(aiResourcePersistService.find(NAMESPACE_ID, "res", RESOURCE_TYPE)).thenReturn(latest);
        
        NacosApiException ex = assertThrows(NacosApiException.class,
            () -> manager.updateBizTagsCas(NAMESPACE_ID, meta, "[\"tag\"]"));
        assertEquals(NacosException.CONFLICT, ex.getErrCode());
    }
    
    // ---- metaEnableDisable ----
    
    @Test
    void metaEnableDisableShouldSetEnableStatus() throws NacosException {
        AiResource meta = buildMeta("res");
        meta.setStatus(AiResourceConstants.META_STATUS_DISABLE);
        when(aiResourcePersistService.updateMetaCas(eq(NAMESPACE_ID), eq("res"), eq(RESOURCE_TYPE),
            eq(1L), any()))
            .thenReturn(true);
        
        manager.metaEnableDisable(NAMESPACE_ID, meta, true);
        
        ArgumentCaptor<AiResource> captor = ArgumentCaptor.forClass(AiResource.class);
        verify(aiResourcePersistService).updateMetaCas(eq(NAMESPACE_ID), eq("res"),
            eq(RESOURCE_TYPE), eq(1L),
            captor.capture());
        assertEquals(AiResourceConstants.META_STATUS_ENABLE, captor.getValue().getStatus());
    }
    
    @Test
    void metaEnableDisableShouldSetDisableStatus() throws NacosException {
        AiResource meta = buildMeta("res");
        meta.setStatus(AiResourceConstants.META_STATUS_ENABLE);
        when(aiResourcePersistService.updateMetaCas(eq(NAMESPACE_ID), eq("res"), eq(RESOURCE_TYPE),
            eq(1L), any()))
            .thenReturn(true);
        
        manager.metaEnableDisable(NAMESPACE_ID, meta, false);
        
        ArgumentCaptor<AiResource> captor = ArgumentCaptor.forClass(AiResource.class);
        verify(aiResourcePersistService).updateMetaCas(eq(NAMESPACE_ID), eq("res"),
            eq(RESOURCE_TYPE), eq(1L),
            captor.capture());
        assertEquals(AiResourceConstants.META_STATUS_DISABLE, captor.getValue().getStatus());
    }
    
    @Test
    void metaEnableDisableShouldRetryAndSucceedOnCasConflict() throws NacosException {
        AiResource meta = buildMeta("res");
        meta.setDesc("d1");
        meta.setBizTags("[\"b1\"]");
        meta.setExt("e1");
        
        AiResource latestMeta = buildMeta("res");
        latestMeta.setMetaVersion(2L);
        latestMeta.setDesc("d2");
        latestMeta.setBizTags("[\"b2\"]");
        latestMeta.setExt("e2");
        
        when(aiResourcePersistService.updateMetaCas(eq(NAMESPACE_ID), eq("res"), eq(RESOURCE_TYPE),
            eq(1L), any()))
            .thenReturn(false);
        when(aiResourcePersistService.find(NAMESPACE_ID, "res", RESOURCE_TYPE))
            .thenReturn(latestMeta);
        when(aiResourcePersistService.updateMetaCas(eq(NAMESPACE_ID), eq("res"), eq(RESOURCE_TYPE),
            eq(2L), any()))
            .thenReturn(true);
        
        manager.metaEnableDisable(NAMESPACE_ID, meta, true);
        
        ArgumentCaptor<AiResource> captor = ArgumentCaptor.forClass(AiResource.class);
        verify(aiResourcePersistService).updateMetaCas(eq(NAMESPACE_ID), eq("res"),
            eq(RESOURCE_TYPE), eq(2L),
            captor.capture());
        AiResource written = captor.getValue();
        assertEquals(AiResourceConstants.META_STATUS_ENABLE, written.getStatus());
        assertEquals("d2", written.getDesc());
        assertEquals("[\"b2\"]", written.getBizTags());
        assertEquals("e2", written.getExt());
    }
    
    @Test
    void metaEnableDisableShouldThrowWhenRetryFindReturnsNull() {
        AiResource meta = buildMeta("res");
        when(aiResourcePersistService.updateMetaCas(eq(NAMESPACE_ID), eq("res"), eq(RESOURCE_TYPE),
            eq(1L), any()))
            .thenReturn(false);
        when(aiResourcePersistService.find(NAMESPACE_ID, "res", RESOURCE_TYPE)).thenReturn(null);
        
        NacosApiException ex = assertThrows(NacosApiException.class,
            () -> manager.metaEnableDisable(NAMESPACE_ID, meta, true));
        assertEquals(NacosException.SERVER_ERROR, ex.getErrCode());
    }
    
    @Test
    void metaEnableDisableShouldThrowConflictAfterMaxRetries() {
        AiResource meta = buildMeta("res");
        when(aiResourcePersistService.updateMetaCas(eq(NAMESPACE_ID), eq("res"), eq(RESOURCE_TYPE),
            anyLong(), any()))
            .thenReturn(false);
        AiResource latest = buildMeta("res");
        latest.setMetaVersion(99L);
        when(aiResourcePersistService.find(NAMESPACE_ID, "res", RESOURCE_TYPE)).thenReturn(latest);
        
        NacosApiException ex = assertThrows(NacosApiException.class,
            () -> manager.metaEnableDisable(NAMESPACE_ID, meta, false));
        assertEquals(NacosException.CONFLICT, ex.getErrCode());
    }
    
    // ---- bumpMetaDescription ----
    
    @Test
    void bumpMetaDescriptionShouldDoNothingForNullMeta() {
        manager.bumpMetaDescription(NAMESPACE_ID, null, "desc");
        verify(aiResourcePersistService, never()).updateMetaCas(anyString(), anyString(),
            anyString(), anyLong(), any());
    }
    
    @Test
    void bumpMetaDescriptionShouldDoNothingForNullMetaVersion() {
        AiResource meta = new AiResource();
        meta.setName("res");
        manager.bumpMetaDescription(NAMESPACE_ID, meta, "desc");
        verify(aiResourcePersistService, never()).updateMetaCas(anyString(), anyString(),
            anyString(), anyLong(), any());
    }
    
    @Test
    void bumpMetaDescriptionShouldSucceed() {
        AiResource meta = buildMeta("res");
        when(aiResourcePersistService.updateMetaCas(eq(NAMESPACE_ID), eq("res"), eq(RESOURCE_TYPE),
            eq(1L), any()))
            .thenReturn(true);
        
        manager.bumpMetaDescription(NAMESPACE_ID, meta, "new description");
        
        ArgumentCaptor<AiResource> captor = ArgumentCaptor.forClass(AiResource.class);
        verify(aiResourcePersistService).updateMetaCas(eq(NAMESPACE_ID), eq("res"),
            eq(RESOURCE_TYPE), eq(1L),
            captor.capture());
        assertEquals("new description", captor.getValue().getDesc());
    }
    
    @Test
    void bumpMetaDescriptionShouldRetryAndSucceedOnCasConflict() {
        AiResource meta = buildMeta("res");
        meta.setStatus("s1");
        meta.setBizTags("[\"b1\"]");
        meta.setExt("e1");
        meta.setVersionInfo("{\"onlineCnt\":1}");
        
        AiResource latestMeta = buildMeta("res");
        latestMeta.setMetaVersion(2L);
        latestMeta.setStatus("s2");
        latestMeta.setBizTags("[\"b2\"]");
        latestMeta.setExt("e2");
        latestMeta.setVersionInfo("{\"onlineCnt\":2}");
        
        when(aiResourcePersistService.updateMetaCas(eq(NAMESPACE_ID), eq("res"), eq(RESOURCE_TYPE),
            eq(1L), any()))
            .thenReturn(false);
        when(aiResourcePersistService.find(NAMESPACE_ID, "res", RESOURCE_TYPE))
            .thenReturn(latestMeta);
        when(aiResourcePersistService.updateMetaCas(eq(NAMESPACE_ID), eq("res"), eq(RESOURCE_TYPE),
            eq(2L), any()))
            .thenReturn(true);
        
        manager.bumpMetaDescription(NAMESPACE_ID, meta, "bumped");
        
        ArgumentCaptor<AiResource> captor = ArgumentCaptor.forClass(AiResource.class);
        verify(aiResourcePersistService).updateMetaCas(eq(NAMESPACE_ID), eq("res"),
            eq(RESOURCE_TYPE), eq(2L),
            captor.capture());
        AiResource written = captor.getValue();
        assertEquals("bumped", written.getDesc());
        assertEquals("s2", written.getStatus());
        assertEquals("[\"b2\"]", written.getBizTags());
        assertEquals("e2", written.getExt());
        assertEquals("{\"onlineCnt\":2}", written.getVersionInfo());
    }
    
    @Test
    void bumpMetaDescriptionShouldReturnSilentlyWhenRetryFindReturnsNull() {
        AiResource meta = buildMeta("res");
        when(aiResourcePersistService.updateMetaCas(eq(NAMESPACE_ID), eq("res"), eq(RESOURCE_TYPE),
            eq(1L), any()))
            .thenReturn(false);
        when(aiResourcePersistService.find(NAMESPACE_ID, "res", RESOURCE_TYPE)).thenReturn(null);
        
        manager.bumpMetaDescription(NAMESPACE_ID, meta, "desc");
        
        verify(aiResourcePersistService, times(1))
            .updateMetaCas(anyString(), anyString(), anyString(), anyLong(), any());
    }
    
    @Test
    void bumpMetaDescriptionShouldReturnSilentlyWhenRetryFindReturnsNullMetaVersion() {
        AiResource meta = buildMeta("res");
        AiResource stale = new AiResource();
        stale.setName("res");
        stale.setType(RESOURCE_TYPE);
        
        when(aiResourcePersistService.updateMetaCas(eq(NAMESPACE_ID), eq("res"), eq(RESOURCE_TYPE),
            eq(1L), any()))
            .thenReturn(false);
        when(aiResourcePersistService.find(NAMESPACE_ID, "res", RESOURCE_TYPE)).thenReturn(stale);
        
        manager.bumpMetaDescription(NAMESPACE_ID, meta, "desc");
        
        verify(aiResourcePersistService, times(1))
            .updateMetaCas(anyString(), anyString(), anyString(), anyLong(), any());
    }
    
    // ---- syncImportedMeta ----
    
    @Test
    void syncImportedMetaShouldDoNothingForNullMeta() {
        manager.syncImportedMeta(NAMESPACE_ID, null, "desc", "tags");
        verify(aiResourcePersistService, never()).updateMetaCas(anyString(), anyString(),
            anyString(), anyLong(), any());
    }
    
    @Test
    void syncImportedMetaShouldUseExistingValuesForBlankArgs() {
        AiResource meta = buildMeta("res");
        meta.setDesc("original-desc");
        meta.setBizTags("[\"original\"]");
        when(aiResourcePersistService.updateMetaCas(eq(NAMESPACE_ID), eq("res"), eq(RESOURCE_TYPE),
            eq(1L), any()))
            .thenReturn(true);
        
        manager.syncImportedMeta(NAMESPACE_ID, meta, "", "");
        
        ArgumentCaptor<AiResource> captor = ArgumentCaptor.forClass(AiResource.class);
        verify(aiResourcePersistService).updateMetaCas(eq(NAMESPACE_ID), eq("res"),
            eq(RESOURCE_TYPE), eq(1L),
            captor.capture());
        assertEquals("original-desc", captor.getValue().getDesc());
        assertEquals("[\"original\"]", captor.getValue().getBizTags());
    }
    
    @Test
    void syncImportedMetaShouldUseProvidedValuesWhenNotBlank() {
        AiResource meta = buildMeta("res");
        when(aiResourcePersistService.updateMetaCas(eq(NAMESPACE_ID), eq("res"), eq(RESOURCE_TYPE),
            eq(1L), any()))
            .thenReturn(true);
        
        manager.syncImportedMeta(NAMESPACE_ID, meta, "imported-desc", "[\"imported\"]");
        
        ArgumentCaptor<AiResource> captor = ArgumentCaptor.forClass(AiResource.class);
        verify(aiResourcePersistService).updateMetaCas(eq(NAMESPACE_ID), eq("res"),
            eq(RESOURCE_TYPE), eq(1L),
            captor.capture());
        assertEquals("imported-desc", captor.getValue().getDesc());
        assertEquals("[\"imported\"]", captor.getValue().getBizTags());
    }
    
    @Test
    void syncImportedMetaShouldRetryAndSucceedOnCasConflict() {
        AiResource meta = buildMeta("res");
        meta.setDesc("old-desc");
        meta.setBizTags("[\"old\"]");
        meta.setExt("ext1");
        meta.setVersionInfo("{\"onlineCnt\":1}");
        
        AiResource latestMeta = buildMeta("res");
        latestMeta.setMetaVersion(2L);
        latestMeta.setStatus(AiResourceConstants.META_STATUS_DISABLE);
        latestMeta.setExt("ext2");
        latestMeta.setVersionInfo("{\"onlineCnt\":2}");
        
        // First CAS attempt fails
        when(aiResourcePersistService.updateMetaCas(eq(NAMESPACE_ID), eq("res"), eq(RESOURCE_TYPE),
            eq(1L), any()))
            .thenReturn(false);
        // find returns updated meta
        when(aiResourcePersistService.find(NAMESPACE_ID, "res", RESOURCE_TYPE))
            .thenReturn(latestMeta);
        // Second CAS attempt succeeds with new metaVersion
        when(aiResourcePersistService.updateMetaCas(eq(NAMESPACE_ID), eq("res"), eq(RESOURCE_TYPE),
            eq(2L), any()))
            .thenReturn(true);
        
        manager.syncImportedMeta(NAMESPACE_ID, meta, "new-desc", "[\"new\"]");
        
        // Verify the second CAS call carries refreshed fields from latestMeta
        ArgumentCaptor<AiResource> captor = ArgumentCaptor.forClass(AiResource.class);
        verify(aiResourcePersistService).updateMetaCas(eq(NAMESPACE_ID), eq("res"),
            eq(RESOURCE_TYPE), eq(2L),
            captor.capture());
        AiResource written = captor.getValue();
        assertEquals("new-desc", written.getDesc());
        assertEquals("[\"new\"]", written.getBizTags());
        assertEquals(AiResourceConstants.META_STATUS_DISABLE, written.getStatus());
        assertEquals("ext2", written.getExt());
        assertEquals("{\"onlineCnt\":2}", written.getVersionInfo());
    }
    
    @Test
    void syncImportedMetaShouldReturnSilentlyWhenFindReturnsNullDuringRetry() {
        AiResource meta = buildMeta("res");
        
        // CAS fails
        when(aiResourcePersistService.updateMetaCas(eq(NAMESPACE_ID), eq("res"), eq(RESOURCE_TYPE),
            eq(1L), any()))
            .thenReturn(false);
        // find returns null
        when(aiResourcePersistService.find(NAMESPACE_ID, "res", RESOURCE_TYPE)).thenReturn(null);
        
        // Should not throw, just return silently
        manager.syncImportedMeta(NAMESPACE_ID, meta, "desc", "tags");
        
        // Verify only one CAS attempt was made (no further retries after null find)
        verify(aiResourcePersistService, times(1))
            .updateMetaCas(anyString(), anyString(), anyString(), anyLong(), any());
    }
    
    @Test
    void syncImportedMetaShouldReturnSilentlyWhenFindReturnsMetaWithNullVersion() {
        AiResource meta = buildMeta("res");
        
        AiResource stale = new AiResource();
        stale.setName("res");
        stale.setType(RESOURCE_TYPE);
        // metaVersion is null
        
        when(aiResourcePersistService.updateMetaCas(eq(NAMESPACE_ID), eq("res"), eq(RESOURCE_TYPE),
            eq(1L), any()))
            .thenReturn(false);
        when(aiResourcePersistService.find(NAMESPACE_ID, "res", RESOURCE_TYPE)).thenReturn(stale);
        
        manager.syncImportedMeta(NAMESPACE_ID, meta, "desc", "tags");
        
        verify(aiResourcePersistService, times(1))
            .updateMetaCas(anyString(), anyString(), anyString(), anyLong(), any());
    }
    
    // ---- syncImportedSource ----
    
    @Test
    void syncImportedSourceShouldDoNothingForBlankSource() {
        manager.syncImportedSource(NAMESPACE_ID, buildMeta("res"), "");
        
        verify(aiResourcePersistService, never()).updateSourceCas(anyString(), anyString(),
            anyString(), anyLong(), anyString());
    }
    
    @Test
    void syncImportedSourceShouldUpdateSource() {
        AiResource meta = buildMeta("res");
        String source =
            "https://developers.cloudflare.com/.well-known/agent-skills/cloudflare.tar.gz";
        when(aiResourcePersistService.updateSourceCas(NAMESPACE_ID, "res", RESOURCE_TYPE, 1L,
            source)).thenReturn(true);
        
        manager.syncImportedSource(NAMESPACE_ID, meta, source);
        
        verify(aiResourcePersistService).updateSourceCas(NAMESPACE_ID, "res", RESOURCE_TYPE, 1L,
            source);
    }
    
    @Test
    void syncImportedSourceShouldRetryOnCasConflict() {
        AiResource meta = buildMeta("res");
        AiResource latestMeta = buildMeta("res");
        latestMeta.setMetaVersion(2L);
        String source = "https://example.com/skill.tar.gz";
        when(aiResourcePersistService.updateSourceCas(NAMESPACE_ID, "res", RESOURCE_TYPE, 1L,
            source)).thenReturn(false);
        when(aiResourcePersistService.find(NAMESPACE_ID, "res", RESOURCE_TYPE))
            .thenReturn(latestMeta);
        when(aiResourcePersistService.updateSourceCas(NAMESPACE_ID, "res", RESOURCE_TYPE, 2L,
            source)).thenReturn(true);
        
        manager.syncImportedSource(NAMESPACE_ID, meta, source);
        
        verify(aiResourcePersistService).updateSourceCas(NAMESPACE_ID, "res", RESOURCE_TYPE, 2L,
            source);
    }
    
    // ---- ensureReadableOrNotFound ----
    
    @Test
    void ensureReadableOrNotFoundShouldPassWhenReadable() throws NacosException {
        AiResource meta = buildMeta("res");
        meta.setScope(VisibilityConstants.SCOPE_PUBLIC);
        // No visibility service -> canReadResource returns true
        manager.ensureReadableOrNotFound(meta, "not found");
    }
    
    @Test
    void ensureReadableOrNotFoundShouldThrowWhenNotReadable() {
        AiResource meta = buildMeta("res");
        meta.setScope(VisibilityConstants.SCOPE_PRIVATE);
        // Mock visibility service to deny read
        com.alibaba.nacos.plugin.visibility.spi.VisibilityService mockService =
            mock(com.alibaba.nacos.plugin.visibility.spi.VisibilityService.class);
        com.alibaba.nacos.plugin.visibility.spi.ValidationResult denied =
            com.alibaba.nacos.plugin.visibility.spi.ValidationResult.deny("denied");
        when(mockService.validateVisibility(anyString(), anyString(), anyString(), any()))
            .thenReturn(denied);
        when(mockVisibilityManager.findVisibilityService(anyString()))
            .thenReturn(Optional.of(mockService));
        
        assertThrows(NacosApiException.class,
            () -> manager.ensureReadableOrNotFound(meta, "resource not found"));
    }
    
    // ---- buildQueryCondition ----
    
    @Test
    void buildQueryConditionShouldReturnConditionWithCorrectFields() {
        QueryCondition condition =
            manager.buildQueryCondition(NAMESPACE_ID, RESOURCE_TYPE, "name%", "tag%",
                VisibilityConstants.ACTION_READ);
        assertNotNull(condition);
        assertEquals(NAMESPACE_ID, condition.getNamespaceId());
        assertEquals(RESOURCE_TYPE, condition.getType());
        assertEquals("name%", condition.getNameLike());
        assertEquals("tag%", condition.getBizTagsLike());
    }
    
    // ---- onPipelineComplete ----
    
    @Test
    void onPipelineCompleteShouldPersistApprovedResult() {
        PipelineExecutionResult result = new PipelineExecutionResult();
        result.setExecutionId("exec-1");
        result.setStatus(PipelineExecutionStatus.APPROVED);
        result.setPipeline(new ArrayList<>());
        
        manager.onPipelineComplete(NAMESPACE_ID, "res", RESOURCE_TYPE, "v1", result);
        
        verify(aiResourceVersionPersistService).updatePublishPipelineInfo(eq(NAMESPACE_ID),
            eq("res"), eq(RESOURCE_TYPE),
            eq("v1"), anyString());
        // Approved -> should NOT rollback to draft
        verify(aiResourceVersionPersistService, never()).updateStatus(anyString(), anyString(),
            anyString(), anyString(),
            eq(AiResourceConstants.VERSION_STATUS_DRAFT));
        // Approved -> should transition to reviewed
        verify(aiResourceVersionPersistService).updateStatus(NAMESPACE_ID, "res", RESOURCE_TYPE,
            "v1",
            AiResourceConstants.VERSION_STATUS_REVIEWED);
    }
    
    @Test
    void onPipelineCompleteShouldTransitionToReviewedOnRejection() {
        PipelineExecutionResult result = new PipelineExecutionResult();
        result.setExecutionId("exec-2");
        result.setStatus(PipelineExecutionStatus.REJECTED);
        result.setPipeline(new ArrayList<>());
        
        manager.onPipelineComplete(NAMESPACE_ID, "res", RESOURCE_TYPE, "v1", result);
        
        verify(aiResourceVersionPersistService).updateStatus(NAMESPACE_ID, "res", RESOURCE_TYPE,
            "v1", AiResourceConstants.VERSION_STATUS_REVIEWED);
        verify(aiResourceVersionPersistService, never()).updateStatus(anyString(), anyString(),
            anyString(), anyString(), eq(AiResourceConstants.VERSION_STATUS_DRAFT));
    }
    
    @Test
    void onPipelineCompleteShouldTransitionToReviewedOnNullResult() {
        manager.onPipelineComplete(NAMESPACE_ID, "res", RESOURCE_TYPE, "v1", null);
        
        verify(aiResourceVersionPersistService).updateStatus(NAMESPACE_ID, "res", RESOURCE_TYPE,
            "v1", AiResourceConstants.VERSION_STATUS_REVIEWED);
        verify(aiResourceVersionPersistService, never()).updateStatus(anyString(), anyString(),
            anyString(), anyString(), eq(AiResourceConstants.VERSION_STATUS_DRAFT));
    }
    
    @Test
    void onPipelineCompleteShouldNotCrashWhenMetaNotFound() {
        PipelineExecutionResult result = new PipelineExecutionResult();
        result.setExecutionId("exec-3");
        result.setStatus(PipelineExecutionStatus.REJECTED);
        result.setPipeline(new ArrayList<>());
        
        // Should not throw
        manager.onPipelineComplete(NAMESPACE_ID, "res", RESOURCE_TYPE, "v1", result);
    }
    
    @Test
    void onPipelineCompleteShouldCatchOuterThrowable() {
        PipelineExecutionResult result = new PipelineExecutionResult();
        result.setExecutionId("exec-5");
        result.setStatus(PipelineExecutionStatus.REJECTED);
        result.setPipeline(new ArrayList<>());
        
        // Make updatePublishPipelineInfo throw to trigger the outer catch
        doThrow(new RuntimeException("db error")).when(aiResourceVersionPersistService)
            .updatePublishPipelineInfo(anyString(), anyString(), anyString(), anyString(),
                anyString());
        
        // Should not throw - outer exception is caught and logged as error
        manager.onPipelineComplete(NAMESPACE_ID, "res", RESOURCE_TYPE, "v1", result);
        
        // Verify no further interactions after the exception
        verify(aiResourceVersionPersistService, never()).updateStatus(anyString(), anyString(),
            anyString(),
            anyString(), anyString());
    }
    
    // ---- doRedraft ----
    
    @Test
    void doRedraftShouldTransitionReviewedToDraft() throws NacosException {
        AiResource meta = buildMeta("res");
        ResourceVersionInfo vInfo = new ResourceVersionInfo();
        vInfo.setReviewingVersion("v1");
        vInfo.setLabels(new HashMap<>());
        meta.setVersionInfo(JacksonUtils.toJson(vInfo));
        when(aiResourcePersistService.find(NAMESPACE_ID, "res", RESOURCE_TYPE)).thenReturn(meta);
        
        AiResourceVersion v = new AiResourceVersion();
        v.setVersion("v1");
        v.setStatus(AiResourceConstants.VERSION_STATUS_REVIEWED);
        when(aiResourceVersionPersistService.find(NAMESPACE_ID, "res", RESOURCE_TYPE, "v1"))
            .thenReturn(v);
        when(aiResourcePersistService.updateMetaCas(eq(NAMESPACE_ID), eq("res"), eq(RESOURCE_TYPE),
            eq(1L), any())).thenReturn(true);
        
        manager.doRedraft(NAMESPACE_ID, "res", RESOURCE_TYPE, "v1");
        
        verify(aiResourceVersionPersistService).updateStatus(NAMESPACE_ID, "res", RESOURCE_TYPE,
            "v1", AiResourceConstants.VERSION_STATUS_DRAFT);
        // Verify meta pointers updated: reviewingVersion cleared, editingVersion set
        ArgumentCaptor<AiResource> metaCaptor = ArgumentCaptor.forClass(AiResource.class);
        verify(aiResourcePersistService).updateMetaCas(eq(NAMESPACE_ID), eq("res"),
            eq(RESOURCE_TYPE), eq(1L), metaCaptor.capture());
        String updatedVersionInfo = metaCaptor.getValue().getVersionInfo();
        assertTrue(updatedVersionInfo.contains("\"editingVersion\":\"v1\""));
        assertFalse(updatedVersionInfo.contains("\"reviewingVersion\":\"v1\""));
    }
    
    @Test
    void doRedraftShouldPreserveLatestLabelOnCasRetry() throws NacosException {
        AiResource meta = buildMeta("res");
        meta.setVersionInfo(
            "{\"reviewingVersion\":\"v1\",\"labels\":{\"latest\":\"v1\"},\"onlineCnt\":1}");
        AiResource latestMeta = buildMeta("res");
        latestMeta.setMetaVersion(2L);
        latestMeta.setVersionInfo(
            "{\"reviewingVersion\":\"v1\",\"labels\":{\"latest\":\"v2\","
                + "\"stable\":\"v1\"},\"onlineCnt\":2}");
        when(aiResourcePersistService.find(NAMESPACE_ID, "res", RESOURCE_TYPE))
            .thenReturn(meta, latestMeta);
        
        AiResourceVersion v = new AiResourceVersion();
        v.setVersion("v1");
        v.setStatus(AiResourceConstants.VERSION_STATUS_REVIEWED);
        when(aiResourceVersionPersistService.find(NAMESPACE_ID, "res", RESOURCE_TYPE, "v1"))
            .thenReturn(v);
        when(aiResourcePersistService.updateMetaCas(eq(NAMESPACE_ID), eq("res"), eq(RESOURCE_TYPE),
            eq(1L), any()))
            .thenReturn(false);
        when(aiResourcePersistService.updateMetaCas(eq(NAMESPACE_ID), eq("res"), eq(RESOURCE_TYPE),
            eq(2L), any()))
            .thenReturn(true);
        
        manager.doRedraft(NAMESPACE_ID, "res", RESOURCE_TYPE, "v1");
        
        ArgumentCaptor<AiResource> retryCaptor = ArgumentCaptor.forClass(AiResource.class);
        verify(aiResourcePersistService).updateMetaCas(eq(NAMESPACE_ID), eq("res"),
            eq(RESOURCE_TYPE), eq(2L), retryCaptor.capture());
        ResourceVersionInfo savedInfo =
            JacksonUtils.toObj(retryCaptor.getValue().getVersionInfo(), ResourceVersionInfo.class);
        assertEquals("v1", savedInfo.getEditingVersion());
        assertNull(savedInfo.getReviewingVersion());
        assertEquals("v2", savedInfo.getLabels().get(AiResourceConstants.LABEL_LATEST));
        assertEquals("v1", savedInfo.getLabels().get("stable"));
    }
    
    @Test
    void doRedraftShouldThrowWhenVersionNotReviewed() {
        AiResource meta = buildMeta("res");
        meta.setVersionInfo("{\"labels\":{},\"onlineCnt\":0}");
        when(aiResourcePersistService.find(NAMESPACE_ID, "res", RESOURCE_TYPE)).thenReturn(meta);
        
        AiResourceVersion v = new AiResourceVersion();
        v.setVersion("v1");
        v.setStatus(AiResourceConstants.VERSION_STATUS_DRAFT);
        when(aiResourceVersionPersistService.find(NAMESPACE_ID, "res", RESOURCE_TYPE, "v1"))
            .thenReturn(v);
        
        NacosApiException ex = assertThrows(NacosApiException.class,
            () -> manager.doRedraft(NAMESPACE_ID, "res", RESOURCE_TYPE, "v1"));
        assertEquals(NacosException.INVALID_PARAM, ex.getErrCode());
    }
    
    @Test
    void doRedraftShouldThrowWhenVersionNotFound() {
        AiResource meta = buildMeta("res");
        meta.setVersionInfo("{\"labels\":{},\"onlineCnt\":0}");
        when(aiResourcePersistService.find(NAMESPACE_ID, "res", RESOURCE_TYPE)).thenReturn(meta);
        when(aiResourceVersionPersistService.find(NAMESPACE_ID, "res", RESOURCE_TYPE, "v99"))
            .thenReturn(null);
        
        NacosApiException ex = assertThrows(NacosApiException.class,
            () -> manager.doRedraft(NAMESPACE_ID, "res", RESOURCE_TYPE, "v99"));
        assertEquals(NacosException.NOT_FOUND, ex.getErrCode());
    }
    
    // ---- doDeleteDraft ----
    
    @Test
    void doDeleteDraftShouldPreserveLatestLabelOnCasRetry() throws NacosException {
        AiResource meta = buildMeta("res");
        meta.setVersionInfo(
            "{\"editingVersion\":\"v1\",\"labels\":{\"latest\":\"v1\"},\"onlineCnt\":1}");
        AiResource latestMeta = buildMeta("res");
        latestMeta.setMetaVersion(2L);
        latestMeta.setVersionInfo(
            "{\"editingVersion\":\"v1\",\"labels\":{\"latest\":\"v2\","
                + "\"stable\":\"v1\"},\"onlineCnt\":2}");
        when(aiResourcePersistService.find(NAMESPACE_ID, "res", RESOURCE_TYPE))
            .thenReturn(meta, latestMeta);
        
        AiResourceVersion v = new AiResourceVersion();
        v.setVersion("v1");
        v.setStatus(AiResourceConstants.VERSION_STATUS_DRAFT);
        when(aiResourceVersionPersistService.find(NAMESPACE_ID, "res", RESOURCE_TYPE, "v1"))
            .thenReturn(v);
        when(aiResourcePersistService.updateMetaCas(eq(NAMESPACE_ID), eq("res"), eq(RESOURCE_TYPE),
            eq(1L), any()))
            .thenReturn(false);
        when(aiResourcePersistService.updateMetaCas(eq(NAMESPACE_ID), eq("res"), eq(RESOURCE_TYPE),
            eq(2L), any()))
            .thenReturn(true);
        
        AtomicReference<String> deletedVersion = new AtomicReference<>();
        manager.doDeleteDraft(NAMESPACE_ID, "res", RESOURCE_TYPE,
            version -> deletedVersion.set(version.getVersion()));
        
        ArgumentCaptor<AiResource> retryCaptor = ArgumentCaptor.forClass(AiResource.class);
        verify(aiResourcePersistService).updateMetaCas(eq(NAMESPACE_ID), eq("res"),
            eq(RESOURCE_TYPE), eq(2L), retryCaptor.capture());
        ResourceVersionInfo savedInfo =
            JacksonUtils.toObj(retryCaptor.getValue().getVersionInfo(), ResourceVersionInfo.class);
        assertNull(savedInfo.getEditingVersion());
        assertEquals("v2", savedInfo.getLabels().get(AiResourceConstants.LABEL_LATEST));
        assertEquals("v1", savedInfo.getLabels().get("stable"));
        assertEquals("v1", deletedVersion.get());
    }
    
    // ---- resolveBaseVersion ----
    
    @Test
    void resolveBaseVersionWithExplicitVersionShouldResolve() throws NacosException {
        AiResource meta = buildMeta("res");
        meta.setVersionInfo("{\"labels\":{\"latest\":\"1.0.0\"},\"onlineCnt\":1}");
        String result =
            manager.resolveBaseVersion(NAMESPACE_ID, "res", RESOURCE_TYPE, meta, "1.0.0");
        assertEquals("1.0.0", result);
    }
    
    @Test
    void resolveBaseVersionWithExplicitVersionResolvesDirectly() throws NacosException {
        AiResource meta = buildMeta("res");
        meta.setVersionInfo("{\"labels\":{},\"onlineCnt\":0}");
        // resolveVersion with explicit non-blank basedOnVersion always returns it as-is
        String result =
            manager.resolveBaseVersion(NAMESPACE_ID, "res", RESOURCE_TYPE, meta, "nonexist");
        assertEquals("nonexist", result);
    }
    
    @Test
    void resolveBaseVersionShouldFallbackToLatestLabel() throws NacosException {
        AiResource meta = buildMeta("res");
        meta.setVersionInfo("{\"labels\":{\"latest\":\"2.0.0\"},\"onlineCnt\":1}");
        String result = manager.resolveBaseVersion(NAMESPACE_ID, "res", RESOURCE_TYPE, meta, null);
        assertEquals("2.0.0", result);
    }
    
    @Test
    void resolveBaseVersionShouldFallbackToMaxSemver() throws NacosException {
        AiResource meta = buildMeta("res");
        meta.setVersionInfo("{\"labels\":{},\"onlineCnt\":0}");
        Page<AiResourceVersion> page = new Page<>();
        AiResourceVersion v1 = new AiResourceVersion();
        v1.setVersion("1.0.0");
        AiResourceVersion v2 = new AiResourceVersion();
        v2.setVersion("2.0.0");
        page.setPageItems(List.of(v1, v2));
        when(aiResourceVersionPersistService.list(eq(NAMESPACE_ID), eq("res"), eq(RESOURCE_TYPE),
            isNull(), eq(1), eq(500)))
            .thenReturn(page);
        String result = manager.resolveBaseVersion(NAMESPACE_ID, "res", RESOURCE_TYPE, meta, null);
        assertEquals("2.0.0", result);
    }
    
    @Test
    void resolveBaseVersionShouldFallbackToMaxVNumber() throws NacosException {
        AiResource meta = buildMeta("res");
        meta.setVersionInfo("{\"labels\":{},\"onlineCnt\":0}");
        Page<AiResourceVersion> page = new Page<>();
        AiResourceVersion v1 = new AiResourceVersion();
        v1.setVersion("v3");
        AiResourceVersion v2 = new AiResourceVersion();
        v2.setVersion("v5");
        page.setPageItems(List.of(v1, v2));
        when(aiResourceVersionPersistService.list(eq(NAMESPACE_ID), eq("res"), eq(RESOURCE_TYPE),
            isNull(), eq(1), eq(500)))
            .thenReturn(page);
        String result = manager.resolveBaseVersion(NAMESPACE_ID, "res", RESOURCE_TYPE, meta, null);
        assertEquals("v5", result);
    }
    
    @Test
    void resolveBaseVersionShouldReturnNullWhenNoVersionExists() throws NacosException {
        AiResource meta = buildMeta("res");
        meta.setVersionInfo("{\"labels\":{},\"onlineCnt\":0}");
        when(aiResourceVersionPersistService.list(eq(NAMESPACE_ID), eq("res"), eq(RESOURCE_TYPE),
            isNull(), eq(1), eq(500)))
            .thenReturn(null);
        String result = manager.resolveBaseVersion(NAMESPACE_ID, "res", RESOURCE_TYPE, meta, null);
        assertNull(result);
    }
    
    // ---- ensureNoWorkingVersion ----
    
    @Test
    void ensureNoWorkingVersionShouldPassWhenNothingEditing() throws NacosException {
        ResourceVersionInfo info = new ResourceVersionInfo();
        AiResourceManager.ensureNoWorkingVersion(info, "test");
    }
    
    @Test
    void ensureNoWorkingVersionShouldThrowWhenEditingExists() {
        ResourceVersionInfo info = new ResourceVersionInfo();
        info.setEditingVersion("v1");
        NacosApiException ex = assertThrows(NacosApiException.class,
            () -> AiResourceManager.ensureNoWorkingVersion(info, "upload"));
        assertEquals(NacosException.CONFLICT, ex.getErrCode());
        assertTrue(ex.getErrMsg().contains("upload"));
    }
    
    @Test
    void ensureNoWorkingVersionShouldThrowWhenReviewingExists() {
        ResourceVersionInfo info = new ResourceVersionInfo();
        info.setReviewingVersion("v2");
        NacosApiException ex = assertThrows(NacosApiException.class,
            () -> AiResourceManager.ensureNoWorkingVersion(info, "create draft"));
        assertEquals(NacosException.CONFLICT, ex.getErrCode());
    }
    
    // ---- buildPageResult ----
    
    @Test
    void buildPageResultShouldBuildCorrectPage() {
        Page<String> source = new Page<>();
        source.setTotalCount(100);
        source.setPagesAvailable(10);
        List<String> items = List.of("a", "b");
        Page<String> result = AiResourceManager.buildPageResult(items, source, 3);
        assertEquals(2, result.getPageItems().size());
        assertEquals(100, result.getTotalCount());
        assertEquals(10, result.getPagesAvailable());
        assertEquals(3, result.getPageNumber());
    }
    
    @Test
    void buildPageResultShouldHandleNullSourcePage() {
        List<String> items = List.of("x");
        Page<String> result = AiResourceManager.buildPageResult(items, null, 1);
        assertEquals(1, result.getPageItems().size());
        assertEquals(0, result.getTotalCount());
        assertEquals(0, result.getPagesAvailable());
    }
    
    // ---- listExistingVersions ----
    
    @Test
    void listExistingVersionsShouldReturnVersions() {
        Page<AiResourceVersion> page = new Page<>();
        AiResourceVersion v1 = new AiResourceVersion();
        v1.setVersion("1.0.0");
        AiResourceVersion v2 = new AiResourceVersion();
        v2.setVersion("2.0.0");
        page.setPageItems(List.of(v1, v2));
        when(aiResourceVersionPersistService.list(eq(NAMESPACE_ID), eq("res"), eq(RESOURCE_TYPE),
            isNull(), eq(1), eq(500)))
            .thenReturn(page);
        List<String> result = manager.listExistingVersions(NAMESPACE_ID, "res", RESOURCE_TYPE);
        assertEquals(2, result.size());
        assertEquals("1.0.0", result.get(0));
        assertEquals("2.0.0", result.get(1));
    }
    
    @Test
    void listExistingVersionsShouldReturnEmptyForNullPage() {
        when(aiResourceVersionPersistService.list(eq(NAMESPACE_ID), eq("res"), eq(RESOURCE_TYPE),
            isNull(), eq(1), eq(500)))
            .thenReturn(null);
        List<String> result = manager.listExistingVersions(NAMESPACE_ID, "res", RESOURCE_TYPE);
        assertTrue(result.isEmpty());
    }
    
    @Test
    void listExistingVersionsShouldSkipBlankVersions() {
        Page<AiResourceVersion> page = new Page<>();
        AiResourceVersion v1 = new AiResourceVersion();
        v1.setVersion("1.0.0");
        AiResourceVersion v2 = new AiResourceVersion();
        v2.setVersion("");
        AiResourceVersion v3 = new AiResourceVersion();
        // v3.version is null
        page.setPageItems(List.of(v1, v2, v3));
        when(aiResourceVersionPersistService.list(eq(NAMESPACE_ID), eq("res"), eq(RESOURCE_TYPE),
            isNull(), eq(1), eq(500)))
            .thenReturn(page);
        List<String> result = manager.listExistingVersions(NAMESPACE_ID, "res", RESOURCE_TYPE);
        assertEquals(1, result.size());
        assertEquals("1.0.0", result.get(0));
    }
    
    // ---- insertVersionRow ----
    
    @Test
    void insertVersionRowShouldPopulateAllFields() {
        manager.insertVersionRow(NAMESPACE_ID, "res", RESOURCE_TYPE, "author1",
            AiResourceConstants.VERSION_STATUS_DRAFT, "1.0.0", "desc", "{\"files\":[]}");
        ArgumentCaptor<AiResourceVersion> captor = ArgumentCaptor.forClass(AiResourceVersion.class);
        verify(aiResourceVersionPersistService).insert(captor.capture());
        AiResourceVersion row = captor.getValue();
        assertEquals(NAMESPACE_ID, row.getNamespaceId());
        assertEquals("res", row.getName());
        assertEquals(RESOURCE_TYPE, row.getType());
        assertEquals("author1", row.getAuthor());
        assertEquals(AiResourceConstants.VERSION_STATUS_DRAFT, row.getStatus());
        assertEquals("1.0.0", row.getVersion());
        assertEquals("desc", row.getDesc());
        assertEquals("{\"files\":[]}", row.getStorage());
    }
    
    // ---- requireDraftVersion ----
    
    @Test
    void requireDraftVersionShouldReturnWhenDraft() throws NacosException {
        AiResourceVersion v = new AiResourceVersion();
        v.setVersion("1.0.0");
        v.setStatus(AiResourceConstants.VERSION_STATUS_DRAFT);
        when(aiResourceVersionPersistService.find(NAMESPACE_ID, "res", RESOURCE_TYPE, "1.0.0"))
            .thenReturn(v);
        AiResourceVersion result =
            manager.requireDraftVersion(NAMESPACE_ID, "res", RESOURCE_TYPE, "1.0.0");
        assertEquals(v, result);
    }
    
    @Test
    void requireDraftVersionShouldThrowWhenNotFound() {
        when(aiResourceVersionPersistService.find(NAMESPACE_ID, "res", RESOURCE_TYPE, "1.0.0"))
            .thenReturn(null);
        NacosApiException ex = assertThrows(NacosApiException.class,
            () -> manager.requireDraftVersion(NAMESPACE_ID, "res", RESOURCE_TYPE, "1.0.0"));
        assertEquals(NacosException.INVALID_PARAM, ex.getErrCode());
    }
    
    @Test
    void requireDraftVersionShouldThrowWhenNotDraft() {
        AiResourceVersion v = new AiResourceVersion();
        v.setVersion("1.0.0");
        v.setStatus(AiResourceConstants.VERSION_STATUS_ONLINE);
        when(aiResourceVersionPersistService.find(NAMESPACE_ID, "res", RESOURCE_TYPE, "1.0.0"))
            .thenReturn(v);
        NacosApiException ex = assertThrows(NacosApiException.class,
            () -> manager.requireDraftVersion(NAMESPACE_ID, "res", RESOURCE_TYPE, "1.0.0"));
        assertEquals(NacosException.INVALID_PARAM, ex.getErrCode());
    }
    
    // ---- directPublishVersion ----
    
    @Test
    void directPublishVersionShouldSetOnlineAndUpdateMeta() throws NacosException {
        AiResource meta = buildMeta("res");
        ResourceVersionInfo info = new ResourceVersionInfo();
        info.setEditingVersion("1.0.0");
        info.setLabels(new HashMap<>());
        info.setOnlineCnt(0);
        when(aiResourcePersistService.updateMetaCas(eq(NAMESPACE_ID), eq("res"), eq(RESOURCE_TYPE),
            eq(1L), any()))
            .thenReturn(true);
        manager.directPublishVersion(NAMESPACE_ID, meta, info, "1.0.0", true);
        verify(aiResourceVersionPersistService).updateStatus(NAMESPACE_ID, "res", RESOURCE_TYPE,
            "1.0.0",
            AiResourceConstants.VERSION_STATUS_ONLINE);
        assertNull(info.getEditingVersion());
        assertEquals(1, info.getOnlineCnt());
        assertEquals("1.0.0", info.getLabels().get(AiResourceConstants.LABEL_LATEST));
    }
    
    @Test
    void directPublishVersionShouldUpdateLatestLabelWhenFlagFalse() throws NacosException {
        AiResource meta = buildMeta("res");
        ResourceVersionInfo info = new ResourceVersionInfo();
        info.setReviewingVersion("1.0.0");
        info.setLabels(new HashMap<>());
        info.setOnlineCnt(2);
        when(aiResourcePersistService.updateMetaCas(eq(NAMESPACE_ID), eq("res"), eq(RESOURCE_TYPE),
            eq(1L), any()))
            .thenReturn(true);
        manager.directPublishVersion(NAMESPACE_ID, meta, info, "1.0.0", false);
        assertNull(info.getReviewingVersion());
        assertEquals(3, info.getOnlineCnt());
        assertEquals("1.0.0", info.getLabels().get(AiResourceConstants.LABEL_LATEST));
    }
    
    // ---- resolveSubmitTarget ----
    
    @Test
    void resolveSubmitTargetWithExplicitVersion() throws NacosException {
        ResourceVersionInfo info = new ResourceVersionInfo();
        String result = manager.resolveSubmitTarget(info, "v2", RESOURCE_TYPE, "res");
        assertEquals("v2", result);
    }
    
    @Test
    void resolveSubmitTargetWithEditingVersion() throws NacosException {
        ResourceVersionInfo info = new ResourceVersionInfo();
        info.setEditingVersion("v3");
        String result = manager.resolveSubmitTarget(info, null, RESOURCE_TYPE, "res");
        assertEquals("v3", result);
    }
    
    @Test
    void resolveSubmitTargetShouldThrowWhenNoTarget() {
        ResourceVersionInfo info = new ResourceVersionInfo();
        NacosApiException ex = assertThrows(NacosApiException.class,
            () -> manager.resolveSubmitTarget(info, null, RESOURCE_TYPE, "res"));
        assertEquals(NacosException.NOT_FOUND, ex.getErrCode());
    }
    
    // ---- moveToReviewing ----
    
    @Test
    void moveToReviewingShouldTransitionStatus() throws NacosException {
        AiResource meta = buildMeta("res");
        ResourceVersionInfo info = new ResourceVersionInfo();
        info.setEditingVersion("v1");
        info.setLabels(new HashMap<>());
        AiResourceVersion existing = new AiResourceVersion();
        existing.setVersion("v1");
        existing.setStatus(AiResourceConstants.VERSION_STATUS_DRAFT);
        when(aiResourceVersionPersistService.find(NAMESPACE_ID, "res", RESOURCE_TYPE, "v1"))
            .thenReturn(existing);
        when(aiResourcePersistService.updateMetaCas(eq(NAMESPACE_ID), eq("res"), eq(RESOURCE_TYPE),
            eq(1L), any()))
            .thenReturn(true);
        manager.moveToReviewing(NAMESPACE_ID, "res", RESOURCE_TYPE, "v1", meta, info);
        verify(aiResourceVersionPersistService).updateStatus(NAMESPACE_ID, "res", RESOURCE_TYPE,
            "v1",
            AiResourceConstants.VERSION_STATUS_REVIEWING);
        assertNull(info.getEditingVersion());
        assertEquals("v1", info.getReviewingVersion());
    }
    
    @Test
    void moveToReviewingShouldPreserveLatestLabelOnCasRetry() throws NacosException {
        AiResource meta = buildMeta("res");
        meta.setVersionInfo(
            "{\"editingVersion\":\"v1\",\"labels\":{\"latest\":\"v1\"},\"onlineCnt\":1}");
        ResourceVersionInfo info = AiResourceManager.requireVersionInfo(meta);
        AiResource latestMeta = buildMeta("res");
        latestMeta.setMetaVersion(2L);
        latestMeta.setVersionInfo(
            "{\"editingVersion\":\"v1\",\"labels\":{\"latest\":\"v2\","
                + "\"stable\":\"v1\"},\"onlineCnt\":2}");
        AiResourceVersion existing = new AiResourceVersion();
        existing.setVersion("v1");
        existing.setStatus(AiResourceConstants.VERSION_STATUS_DRAFT);
        when(aiResourceVersionPersistService.find(NAMESPACE_ID, "res", RESOURCE_TYPE, "v1"))
            .thenReturn(existing);
        when(aiResourcePersistService.updateMetaCas(eq(NAMESPACE_ID), eq("res"), eq(RESOURCE_TYPE),
            eq(1L), any()))
            .thenReturn(false);
        when(aiResourcePersistService.find(NAMESPACE_ID, "res", RESOURCE_TYPE))
            .thenReturn(latestMeta);
        when(aiResourcePersistService.updateMetaCas(eq(NAMESPACE_ID), eq("res"), eq(RESOURCE_TYPE),
            eq(2L), any()))
            .thenReturn(true);
        
        manager.moveToReviewing(NAMESPACE_ID, "res", RESOURCE_TYPE, "v1", meta, info);
        
        ArgumentCaptor<AiResource> retryCaptor = ArgumentCaptor.forClass(AiResource.class);
        verify(aiResourcePersistService).updateMetaCas(eq(NAMESPACE_ID), eq("res"),
            eq(RESOURCE_TYPE), eq(2L), retryCaptor.capture());
        ResourceVersionInfo savedInfo =
            JacksonUtils.toObj(retryCaptor.getValue().getVersionInfo(), ResourceVersionInfo.class);
        assertNull(savedInfo.getEditingVersion());
        assertEquals("v1", savedInfo.getReviewingVersion());
        assertEquals("v2", savedInfo.getLabels().get(AiResourceConstants.LABEL_LATEST));
        assertEquals("v1", savedInfo.getLabels().get("stable"));
    }
    
    @Test
    void moveToReviewingShouldRejectNonDraftVersion() {
        AiResource meta = buildMeta("res");
        ResourceVersionInfo info = new ResourceVersionInfo();
        info.setReviewingVersion("v1");
        info.setLabels(new HashMap<>());
        AiResourceVersion existing = new AiResourceVersion();
        existing.setVersion("v1");
        existing.setStatus(AiResourceConstants.VERSION_STATUS_ONLINE);
        when(aiResourceVersionPersistService.find(NAMESPACE_ID, "res", RESOURCE_TYPE, "v1"))
            .thenReturn(existing);
        NacosApiException ex = assertThrows(NacosApiException.class,
            () -> manager.moveToReviewing(NAMESPACE_ID, "res", RESOURCE_TYPE, "v1", meta, info));
        assertEquals(NacosException.INVALID_PARAM, ex.getErrCode());
        verify(aiResourceVersionPersistService, never()).updateStatus(NAMESPACE_ID, "res",
            RESOURCE_TYPE, "v1", AiResourceConstants.VERSION_STATUS_REVIEWING);
        assertEquals("v1", info.getReviewingVersion());
    }
    
    @Test
    void moveToReviewingShouldRejectMissingVersion() {
        AiResource meta = buildMeta("res");
        ResourceVersionInfo info = new ResourceVersionInfo();
        info.setEditingVersion("v1");
        info.setLabels(new HashMap<>());
        when(aiResourceVersionPersistService.find(NAMESPACE_ID, "res", RESOURCE_TYPE, "v1"))
            .thenReturn(null);
        NacosApiException ex = assertThrows(NacosApiException.class,
            () -> manager.moveToReviewing(NAMESPACE_ID, "res", RESOURCE_TYPE, "v1", meta, info));
        assertEquals(NacosException.INVALID_PARAM, ex.getErrCode());
        verify(aiResourceVersionPersistService, never()).updateStatus(NAMESPACE_ID, "res",
            RESOURCE_TYPE, "v1", AiResourceConstants.VERSION_STATUS_REVIEWING);
    }
    
    // ---- writePipelineInfoInProgress / clearPipelineInfo ----
    
    @Test
    void writePipelineInfoInProgressShouldPersist() {
        manager.writePipelineInfoInProgress(NAMESPACE_ID, "res", RESOURCE_TYPE, "v1", "exec-1");
        verify(aiResourceVersionPersistService).updatePublishPipelineInfo(eq(NAMESPACE_ID),
            eq("res"),
            eq(RESOURCE_TYPE), eq("v1"), anyString());
    }
    
    @Test
    void clearPipelineInfoShouldSetNull() {
        manager.clearPipelineInfo(NAMESPACE_ID, "res", RESOURCE_TYPE, "v1");
        verify(aiResourceVersionPersistService).updatePublishPipelineInfo(NAMESPACE_ID, "res",
            RESOURCE_TYPE, "v1", null);
    }
    
    // ---- insertBootstrapMeta ----
    
    @Test
    void insertBootstrapMetaShouldCreateOnlineVersionAndMeta() {
        manager.insertBootstrapMeta(NAMESPACE_ID, "res", RESOURCE_TYPE, "desc", "[\"tag\"]",
            "owner", "builtin", "v1", "{}");
        ArgumentCaptor<AiResourceVersion> vCaptor =
            ArgumentCaptor.forClass(AiResourceVersion.class);
        verify(aiResourceVersionPersistService).insert(vCaptor.capture());
        assertEquals(AiResourceConstants.VERSION_STATUS_ONLINE, vCaptor.getValue().getStatus());
        assertEquals("v1", vCaptor.getValue().getVersion());
        ArgumentCaptor<AiResource> mCaptor = ArgumentCaptor.forClass(AiResource.class);
        verify(aiResourcePersistService).insert(mCaptor.capture());
        AiResource meta = mCaptor.getValue();
        assertEquals("res", meta.getName());
        assertEquals("owner", meta.getOwner());
        assertEquals("builtin", meta.getFrom());
        assertEquals(VisibilityConstants.SCOPE_PUBLIC, meta.getScope());
        assertEquals(1L, meta.getMetaVersion());
    }
    
    // ---- doPublish ----
    
    @Test
    void doPublishShouldSetVersionOnline() throws NacosException {
        AiResource meta = buildMeta("res");
        meta.setVersionInfo("{\"reviewingVersion\":\"v1\",\"labels\":{},\"onlineCnt\":0}");
        when(aiResourcePersistService.find(NAMESPACE_ID, "res", RESOURCE_TYPE)).thenReturn(meta);
        AiResourceVersion v = new AiResourceVersion();
        v.setVersion("v1");
        v.setStatus(AiResourceConstants.VERSION_STATUS_REVIEWING);
        when(aiResourceVersionPersistService.find(NAMESPACE_ID, "res", RESOURCE_TYPE, "v1"))
            .thenReturn(v);
        when(aiResourcePersistService.updateMetaCas(eq(NAMESPACE_ID), eq("res"), eq(RESOURCE_TYPE),
            eq(1L), any()))
            .thenReturn(true);
        AiResourceVersion result =
            manager.doPublish(NAMESPACE_ID, "res", RESOURCE_TYPE, "v1", true);
        assertNotNull(result);
        verify(aiResourceVersionPersistService).updateStatus(NAMESPACE_ID, "res", RESOURCE_TYPE,
            "v1",
            AiResourceConstants.VERSION_STATUS_ONLINE);
    }
    
    @Test
    void doPublishShouldThrowWhenVersionNotFound() {
        AiResource meta = buildMeta("res");
        when(aiResourcePersistService.find(NAMESPACE_ID, "res", RESOURCE_TYPE)).thenReturn(meta);
        when(aiResourceVersionPersistService.find(NAMESPACE_ID, "res", RESOURCE_TYPE, "v99"))
            .thenReturn(null);
        NacosApiException ex = assertThrows(NacosApiException.class,
            () -> manager.doPublish(NAMESPACE_ID, "res", RESOURCE_TYPE, "v99", true));
        assertEquals(NacosException.NOT_FOUND, ex.getErrCode());
    }
    
    @Test
    void doPublishShouldThrowWhenVersionIsDraft() {
        AiResource meta = buildMeta("res");
        when(aiResourcePersistService.find(NAMESPACE_ID, "res", RESOURCE_TYPE)).thenReturn(meta);
        AiResourceVersion v = new AiResourceVersion();
        v.setVersion("v1");
        v.setStatus(AiResourceConstants.VERSION_STATUS_DRAFT);
        when(aiResourceVersionPersistService.find(NAMESPACE_ID, "res", RESOURCE_TYPE, "v1"))
            .thenReturn(v);
        NacosApiException ex = assertThrows(NacosApiException.class,
            () -> manager.doPublish(NAMESPACE_ID, "res", RESOURCE_TYPE, "v1", true));
        assertEquals(NacosException.INVALID_PARAM, ex.getErrCode());
    }
    
    @Test
    void doPublishShouldThrowWhenPipelineNotApproved() {
        AiResource meta = buildMeta("res");
        when(aiResourcePersistService.find(NAMESPACE_ID, "res", RESOURCE_TYPE)).thenReturn(meta);
        AiResourceVersion v = new AiResourceVersion();
        v.setVersion("v1");
        v.setStatus(AiResourceConstants.VERSION_STATUS_REVIEWING);
        v.setPublishPipelineInfo("{\"executionId\":\"exec-1\",\"status\":\"IN_PROGRESS\"}");
        when(aiResourceVersionPersistService.find(NAMESPACE_ID, "res", RESOURCE_TYPE, "v1"))
            .thenReturn(v);
        PipelineExecution execution = new PipelineExecution();
        execution.setStatus(PipelineExecutionStatus.IN_PROGRESS);
        when(pipelineExecutionRepository.findById("exec-1")).thenReturn(execution);
        NacosApiException ex = assertThrows(NacosApiException.class,
            () -> manager.doPublish(NAMESPACE_ID, "res", RESOURCE_TYPE, "v1", true));
        assertEquals(NacosException.INVALID_PARAM, ex.getErrCode());
    }
    
    @Test
    void doPublishShouldThrowWhenPipelineExecutionNotFound() {
        AiResource meta = buildMeta("res");
        when(aiResourcePersistService.find(NAMESPACE_ID, "res", RESOURCE_TYPE)).thenReturn(meta);
        AiResourceVersion v = new AiResourceVersion();
        v.setVersion("v1");
        v.setStatus(AiResourceConstants.VERSION_STATUS_REVIEWING);
        v.setPublishPipelineInfo("{\"executionId\":\"exec-missing\",\"status\":\"IN_PROGRESS\"}");
        when(aiResourceVersionPersistService.find(NAMESPACE_ID, "res", RESOURCE_TYPE, "v1"))
            .thenReturn(v);
        when(pipelineExecutionRepository.findById("exec-missing")).thenReturn(null);
        NacosApiException ex = assertThrows(NacosApiException.class,
            () -> manager.doPublish(NAMESPACE_ID, "res", RESOURCE_TYPE, "v1", true));
        assertEquals(NacosException.INVALID_PARAM, ex.getErrCode());
    }
    
    @Test
    void doPublishShouldBeIdempotentForOnlineVersion() throws NacosException {
        AiResource meta = buildMeta("res");
        meta.setVersionInfo("{\"reviewingVersion\":\"v1\",\"labels\":{},\"onlineCnt\":2}");
        when(aiResourcePersistService.find(NAMESPACE_ID, "res", RESOURCE_TYPE)).thenReturn(meta);
        AiResourceVersion v = new AiResourceVersion();
        v.setVersion("v1");
        v.setStatus(AiResourceConstants.VERSION_STATUS_ONLINE);
        when(aiResourceVersionPersistService.find(NAMESPACE_ID, "res", RESOURCE_TYPE, "v1"))
            .thenReturn(v);
        when(aiResourcePersistService.updateMetaCas(eq(NAMESPACE_ID), eq("res"), eq(RESOURCE_TYPE),
            eq(1L), any()))
            .thenReturn(true);
        manager.doPublish(NAMESPACE_ID, "res", RESOURCE_TYPE, "v1", false);
        verify(aiResourceVersionPersistService, never()).updateStatus(anyString(), anyString(),
            anyString(),
            anyString(), anyString());
        verify(aiResourcePersistService).updateMetaCas(eq(NAMESPACE_ID), eq("res"),
            eq(RESOURCE_TYPE), eq(1L),
            argThat(resource -> {
                Map<?, ?> info = JacksonUtils.toObj(resource.getVersionInfo(), Map.class);
                Map<?, ?> labels = (Map<?, ?>) info.get("labels");
                return "v1".equals(labels.get(AiResourceConstants.LABEL_LATEST));
            }));
    }
    
    @Test
    void doPublishShouldAcceptReviewedStatus() throws NacosException {
        AiResource meta = buildMeta("res");
        meta.setVersionInfo("{\"reviewingVersion\":\"v1\",\"labels\":{},\"onlineCnt\":0}");
        when(aiResourcePersistService.find(NAMESPACE_ID, "res", RESOURCE_TYPE)).thenReturn(meta);
        AiResourceVersion v = new AiResourceVersion();
        v.setVersion("v1");
        v.setStatus(AiResourceConstants.VERSION_STATUS_REVIEWED);
        when(aiResourceVersionPersistService.find(NAMESPACE_ID, "res", RESOURCE_TYPE, "v1"))
            .thenReturn(v);
        when(aiResourcePersistService.updateMetaCas(eq(NAMESPACE_ID), eq("res"), eq(RESOURCE_TYPE),
            eq(1L), any()))
            .thenReturn(true);
        AiResourceVersion result =
            manager.doPublish(NAMESPACE_ID, "res", RESOURCE_TYPE, "v1", true);
        assertNotNull(result);
        verify(aiResourceVersionPersistService).updateStatus(NAMESPACE_ID, "res", RESOURCE_TYPE,
            "v1",
            AiResourceConstants.VERSION_STATUS_ONLINE);
    }
    
    @Test
    void doPublishShouldPreserveUserLabelsOnCasRetry() throws NacosException {
        AiResource meta = buildMeta("res");
        meta.setVersionInfo(
            "{\"reviewingVersion\":\"v2\",\"labels\":{\"latest\":\"v1\"},\"onlineCnt\":1}");
        AiResource latestMeta = buildMeta("res");
        latestMeta.setMetaVersion(2L);
        latestMeta.setVersionInfo(
            "{\"reviewingVersion\":\"v2\",\"labels\":{\"latest\":\"v1\",\"stable\":\"v1\"},\"onlineCnt\":1}");
        when(aiResourcePersistService.find(NAMESPACE_ID, "res", RESOURCE_TYPE))
            .thenReturn(meta, latestMeta);
        AiResourceVersion v = new AiResourceVersion();
        v.setVersion("v2");
        v.setStatus(AiResourceConstants.VERSION_STATUS_REVIEWING);
        when(aiResourceVersionPersistService.find(NAMESPACE_ID, "res", RESOURCE_TYPE, "v2"))
            .thenReturn(v);
        when(aiResourcePersistService.updateMetaCas(eq(NAMESPACE_ID), eq("res"), eq(RESOURCE_TYPE),
            eq(1L), any()))
            .thenReturn(false);
        when(aiResourcePersistService.updateMetaCas(eq(NAMESPACE_ID), eq("res"), eq(RESOURCE_TYPE),
            eq(2L), any()))
            .thenReturn(true);
        
        manager.doPublish(NAMESPACE_ID, "res", RESOURCE_TYPE, "v2", false);
        
        ArgumentCaptor<AiResource> retryCaptor = ArgumentCaptor.forClass(AiResource.class);
        verify(aiResourcePersistService).updateMetaCas(eq(NAMESPACE_ID), eq("res"),
            eq(RESOURCE_TYPE), eq(2L), retryCaptor.capture());
        ResourceVersionInfo savedInfo =
            JacksonUtils.toObj(retryCaptor.getValue().getVersionInfo(), ResourceVersionInfo.class);
        assertEquals("v1", savedInfo.getLabels().get("stable"));
        assertEquals("v2", savedInfo.getLabels().get(AiResourceConstants.LABEL_LATEST));
    }
    
    // ---- doForcePublish ----
    
    @Test
    void doForcePublishShouldSetVersionOnline() throws NacosException {
        AiResource meta = buildMeta("res");
        meta.setVersionInfo("{\"reviewingVersion\":\"v1\",\"labels\":{},\"onlineCnt\":0}");
        when(aiResourcePersistService.find(NAMESPACE_ID, "res", RESOURCE_TYPE)).thenReturn(meta);
        AiResourceVersion v = new AiResourceVersion();
        v.setVersion("v1");
        v.setStatus(AiResourceConstants.VERSION_STATUS_REVIEWED);
        when(aiResourceVersionPersistService.find(NAMESPACE_ID, "res", RESOURCE_TYPE, "v1"))
            .thenReturn(v);
        when(aiResourcePersistService.updateMetaCas(eq(NAMESPACE_ID), eq("res"), eq(RESOURCE_TYPE),
            eq(1L), any()))
            .thenReturn(true);
        AiResourceVersion result =
            manager.doForcePublish(NAMESPACE_ID, "res", RESOURCE_TYPE, "v1", true);
        assertNotNull(result);
        verify(aiResourceVersionPersistService).updateStatus(NAMESPACE_ID, "res", RESOURCE_TYPE,
            "v1",
            AiResourceConstants.VERSION_STATUS_ONLINE);
    }
    
    @Test
    void doForcePublishShouldThrowWhenVersionNotFound() {
        AiResource meta = buildMeta("res");
        when(aiResourcePersistService.find(NAMESPACE_ID, "res", RESOURCE_TYPE)).thenReturn(meta);
        when(aiResourceVersionPersistService.find(NAMESPACE_ID, "res", RESOURCE_TYPE, "v99"))
            .thenReturn(null);
        NacosApiException ex = assertThrows(NacosApiException.class,
            () -> manager.doForcePublish(NAMESPACE_ID, "res", RESOURCE_TYPE, "v99", true));
        assertEquals(NacosException.NOT_FOUND, ex.getErrCode());
    }
    
    @Test
    void doForcePublishShouldThrowWhenAlreadyOnline() {
        AiResource meta = buildMeta("res");
        when(aiResourcePersistService.find(NAMESPACE_ID, "res", RESOURCE_TYPE)).thenReturn(meta);
        AiResourceVersion v = new AiResourceVersion();
        v.setVersion("v1");
        v.setStatus(AiResourceConstants.VERSION_STATUS_ONLINE);
        when(aiResourceVersionPersistService.find(NAMESPACE_ID, "res", RESOURCE_TYPE, "v1"))
            .thenReturn(v);
        NacosApiException ex = assertThrows(NacosApiException.class,
            () -> manager.doForcePublish(NAMESPACE_ID, "res", RESOURCE_TYPE, "v1", true));
        assertEquals(NacosException.INVALID_PARAM, ex.getErrCode());
    }
    
    @Test
    void doForcePublishShouldThrowWhenOffline() {
        AiResource meta = buildMeta("res");
        when(aiResourcePersistService.find(NAMESPACE_ID, "res", RESOURCE_TYPE)).thenReturn(meta);
        AiResourceVersion v = new AiResourceVersion();
        v.setVersion("v1");
        v.setStatus(AiResourceConstants.VERSION_STATUS_OFFLINE);
        when(aiResourceVersionPersistService.find(NAMESPACE_ID, "res", RESOURCE_TYPE, "v1"))
            .thenReturn(v);
        NacosApiException ex = assertThrows(NacosApiException.class,
            () -> manager.doForcePublish(NAMESPACE_ID, "res", RESOURCE_TYPE, "v1", true));
        assertEquals(NacosException.INVALID_PARAM, ex.getErrCode());
    }
    
    @Test
    void doForcePublishShouldAllowDraft() throws NacosException {
        AiResource meta = buildMeta("res");
        meta.setVersionInfo("{\"editingVersion\":\"v1\",\"labels\":{},\"onlineCnt\":0}");
        when(aiResourcePersistService.find(NAMESPACE_ID, "res", RESOURCE_TYPE)).thenReturn(meta);
        AiResourceVersion v = new AiResourceVersion();
        v.setVersion("v1");
        v.setStatus(AiResourceConstants.VERSION_STATUS_DRAFT);
        when(aiResourceVersionPersistService.find(NAMESPACE_ID, "res", RESOURCE_TYPE, "v1"))
            .thenReturn(v);
        when(aiResourcePersistService.updateMetaCas(eq(NAMESPACE_ID), eq("res"), eq(RESOURCE_TYPE),
            eq(1L), any()))
            .thenReturn(true);
        AiResourceVersion result =
            manager.doForcePublish(NAMESPACE_ID, "res", RESOURCE_TYPE, "v1", true);
        assertNotNull(result);
        verify(aiResourceVersionPersistService).updateStatus(NAMESPACE_ID, "res", RESOURCE_TYPE,
            "v1", AiResourceConstants.VERSION_STATUS_ONLINE);
    }
    
    @Test
    void doForcePublishShouldAllowDraftWithHistoricalPipeline() throws NacosException {
        AiResource meta = buildMeta("res");
        meta.setVersionInfo("{\"editingVersion\":\"v1\",\"labels\":{},\"onlineCnt\":0}");
        when(aiResourcePersistService.find(NAMESPACE_ID, "res", RESOURCE_TYPE)).thenReturn(meta);
        AiResourceVersion v = new AiResourceVersion();
        v.setVersion("v1");
        v.setStatus(AiResourceConstants.VERSION_STATUS_DRAFT);
        v.setPublishPipelineInfo(
            "{\"executionId\":\"e1\",\"status\":\"REJECTED\",\"pipeline\":[],\"historical\":true}");
        when(aiResourceVersionPersistService.find(NAMESPACE_ID, "res", RESOURCE_TYPE, "v1"))
            .thenReturn(v);
        when(aiResourcePersistService.updateMetaCas(eq(NAMESPACE_ID), eq("res"), eq(RESOURCE_TYPE),
            eq(1L), any()))
            .thenReturn(true);
        AiResourceVersion result =
            manager.doForcePublish(NAMESPACE_ID, "res", RESOURCE_TYPE, "v1", true);
        assertNotNull(result);
        verify(aiResourceVersionPersistService).updateStatus(NAMESPACE_ID, "res", RESOURCE_TYPE,
            "v1", AiResourceConstants.VERSION_STATUS_ONLINE);
    }
    
    // ---- validateAndUpdateLabels ----
    
    @Test
    void validateAndUpdateLabelsShouldSucceed() throws NacosException {
        AiResource meta = buildMeta("res");
        meta.setVersionInfo("{\"labels\":{},\"onlineCnt\":1}");
        when(aiResourcePersistService.find(NAMESPACE_ID, "res", RESOURCE_TYPE)).thenReturn(meta);
        when(aiResourcePersistService.updateMetaCas(eq(NAMESPACE_ID), eq("res"), eq(RESOURCE_TYPE),
            eq(1L), any()))
            .thenReturn(true);
        Map<String, String> labels = new LinkedHashMap<>();
        labels.put("stable", "v2");
        Map<String, String> effectiveLabels =
            manager.validateAndUpdateLabels(NAMESPACE_ID, "res", RESOURCE_TYPE, labels);
        assertEquals("v2", effectiveLabels.get("stable"));
        verify(aiResourcePersistService).updateMetaCas(eq(NAMESPACE_ID), eq("res"),
            eq(RESOURCE_TYPE), eq(1L), any());
    }
    
    @Test
    void validateAndUpdateLabelsShouldPreserveLatestLabel() throws NacosException {
        AiResource meta = buildMeta("res");
        meta.setVersionInfo("{\"labels\":{\"latest\":\"v1\"},\"onlineCnt\":1}");
        when(aiResourcePersistService.find(NAMESPACE_ID, "res", RESOURCE_TYPE)).thenReturn(meta);
        when(aiResourcePersistService.updateMetaCas(eq(NAMESPACE_ID), eq("res"), eq(RESOURCE_TYPE),
            eq(1L), any()))
            .thenReturn(true);
        Map<String, String> labels = new LinkedHashMap<>();
        labels.put("stable", "v1");
        Map<String, String> effectiveLabels =
            manager.validateAndUpdateLabels(NAMESPACE_ID, "res", RESOURCE_TYPE, labels);
        assertEquals("v1", effectiveLabels.get("stable"));
        assertEquals("v1", effectiveLabels.get(AiResourceConstants.LABEL_LATEST));
    }
    
    @Test
    void validateAndUpdateLabelsShouldIgnoreProvidedLatestLabel() throws NacosException {
        AiResource meta = buildMeta("res");
        meta.setVersionInfo("{\"labels\":{\"latest\":\"v1\"},\"onlineCnt\":1}");
        when(aiResourcePersistService.find(NAMESPACE_ID, "res", RESOURCE_TYPE)).thenReturn(meta);
        when(aiResourcePersistService.updateMetaCas(eq(NAMESPACE_ID), eq("res"), eq(RESOURCE_TYPE),
            eq(1L), any()))
            .thenReturn(true);
        Map<String, String> labels = new LinkedHashMap<>();
        labels.put("latest", "v2");
        labels.put("LATEST", "v3");
        labels.put("stable", "v1");
        Map<String, String> effectiveLabels =
            manager.validateAndUpdateLabels(NAMESPACE_ID, "res", RESOURCE_TYPE, labels);
        assertEquals("v1", effectiveLabels.get("stable"));
        assertEquals("v1", effectiveLabels.get(AiResourceConstants.LABEL_LATEST));
        assertFalse(effectiveLabels.containsKey("LATEST"));
    }
    
    @Test
    void validateAndUpdateLabelsShouldPreserveLatestLabelOnCasRetry() throws NacosException {
        AiResource meta = buildMeta("res");
        meta.setVersionInfo("{\"labels\":{\"latest\":\"v1\"},\"onlineCnt\":1}");
        
        AiResource latestMeta = buildMeta("res");
        latestMeta.setMetaVersion(2L);
        latestMeta.setVersionInfo("{\"labels\":{\"latest\":\"v2\"},\"onlineCnt\":2}");
        
        when(aiResourcePersistService.find(NAMESPACE_ID, "res", RESOURCE_TYPE))
            .thenReturn(meta, latestMeta);
        when(aiResourcePersistService.updateMetaCas(eq(NAMESPACE_ID), eq("res"), eq(RESOURCE_TYPE),
            eq(1L), any()))
            .thenReturn(false);
        when(aiResourcePersistService.updateMetaCas(eq(NAMESPACE_ID), eq("res"), eq(RESOURCE_TYPE),
            eq(2L), any()))
            .thenReturn(true);
        
        Map<String, String> labels = new LinkedHashMap<>();
        labels.put("stable", "v1");
        Map<String, String> effectiveLabels =
            manager.validateAndUpdateLabels(NAMESPACE_ID, "res", RESOURCE_TYPE, labels);
        
        assertEquals("v1", effectiveLabels.get("stable"));
        assertEquals("v2", effectiveLabels.get(AiResourceConstants.LABEL_LATEST));
        ArgumentCaptor<AiResource> retryCaptor = ArgumentCaptor.forClass(AiResource.class);
        verify(aiResourcePersistService).updateMetaCas(eq(NAMESPACE_ID), eq("res"),
            eq(RESOURCE_TYPE), eq(2L), retryCaptor.capture());
        ResourceVersionInfo savedInfo =
            JacksonUtils.toObj(retryCaptor.getValue().getVersionInfo(), ResourceVersionInfo.class);
        assertEquals("v1", savedInfo.getLabels().get("stable"));
        assertEquals("v2", savedInfo.getLabels().get(AiResourceConstants.LABEL_LATEST));
    }
    
    @Test
    void validateAndUpdateLabelsShouldThrowWhenPointingToEditing() {
        AiResource meta = buildMeta("res");
        meta.setVersionInfo("{\"editingVersion\":\"v2\",\"labels\":{},\"onlineCnt\":0}");
        when(aiResourcePersistService.find(NAMESPACE_ID, "res", RESOURCE_TYPE)).thenReturn(meta);
        Map<String, String> labels = Map.of("stable", "v2");
        NacosApiException ex = assertThrows(NacosApiException.class,
            () -> manager.validateAndUpdateLabels(NAMESPACE_ID, "res", RESOURCE_TYPE, labels));
        assertEquals(NacosException.INVALID_PARAM, ex.getErrCode());
        assertTrue(ex.getErrMsg().contains("draft"));
    }
    
    @Test
    void validateAndUpdateLabelsShouldThrowWhenPointingToReviewing() {
        AiResource meta = buildMeta("res");
        meta.setVersionInfo("{\"reviewingVersion\":\"v3\",\"labels\":{},\"onlineCnt\":0}");
        when(aiResourcePersistService.find(NAMESPACE_ID, "res", RESOURCE_TYPE)).thenReturn(meta);
        Map<String, String> labels = Map.of("stable", "v3");
        NacosApiException ex = assertThrows(NacosApiException.class,
            () -> manager.validateAndUpdateLabels(NAMESPACE_ID, "res", RESOURCE_TYPE, labels));
        assertEquals(NacosException.INVALID_PARAM, ex.getErrCode());
        assertTrue(ex.getErrMsg().contains("reviewing"));
    }
    
    @Test
    void validateAndUpdateLabelsShouldHandleNullLabels() throws NacosException {
        AiResource meta = buildMeta("res");
        meta.setVersionInfo("{\"labels\":{\"latest\":\"v1\"},\"onlineCnt\":1}");
        when(aiResourcePersistService.find(NAMESPACE_ID, "res", RESOURCE_TYPE)).thenReturn(meta);
        when(aiResourcePersistService.updateMetaCas(eq(NAMESPACE_ID), eq("res"), eq(RESOURCE_TYPE),
            eq(1L), any()))
            .thenReturn(true);
        Map<String, String> effectiveLabels =
            manager.validateAndUpdateLabels(NAMESPACE_ID, "res", RESOURCE_TYPE, null);
        assertEquals("v1", effectiveLabels.get(AiResourceConstants.LABEL_LATEST));
        verify(aiResourcePersistService).updateMetaCas(eq(NAMESPACE_ID), eq("res"),
            eq(RESOURCE_TYPE), eq(1L), any());
    }
    
    // ---- doUpdateScope ----
    
    @Test
    void doUpdateScopeShouldSucceed() throws NacosException {
        AiResource meta = buildMeta("res");
        when(aiResourcePersistService.find(NAMESPACE_ID, "res", RESOURCE_TYPE)).thenReturn(meta);
        when(aiResourcePersistService.updateScope(NAMESPACE_ID, "res", RESOURCE_TYPE, "PUBLIC"))
            .thenReturn(true);
        manager.doUpdateScope(NAMESPACE_ID, "res", RESOURCE_TYPE, "public");
        verify(aiResourcePersistService).updateScope(NAMESPACE_ID, "res", RESOURCE_TYPE, "PUBLIC");
    }
    
    @Test
    void doUpdateScopeShouldThrowOnFailure() {
        AiResource meta = buildMeta("res");
        when(aiResourcePersistService.find(NAMESPACE_ID, "res", RESOURCE_TYPE)).thenReturn(meta);
        when(aiResourcePersistService.updateScope(NAMESPACE_ID, "res", RESOURCE_TYPE, "PRIVATE"))
            .thenReturn(false);
        NacosApiException ex = assertThrows(NacosApiException.class,
            () -> manager.doUpdateScope(NAMESPACE_ID, "res", RESOURCE_TYPE, "private"));
        assertEquals(NacosException.SERVER_ERROR, ex.getErrCode());
    }
    
    // ---- toggleVersionOnlineStatus ----
    
    @Test
    void toggleVersionOnlineStatusShouldSetOnline() throws NacosException {
        AiResource meta = buildMeta("res");
        ResourceVersionInfo info = new ResourceVersionInfo();
        info.setOnlineCnt(1);
        info.setLabels(new HashMap<>());
        AiResourceVersion v = new AiResourceVersion();
        v.setVersion("v1");
        v.setStatus(AiResourceConstants.VERSION_STATUS_OFFLINE);
        when(aiResourceVersionPersistService.find(NAMESPACE_ID, "res", RESOURCE_TYPE, "v1"))
            .thenReturn(v);
        Page<AiResourceVersion> onlineVersions = new Page<>();
        onlineVersions.setPageItems(List.of(v));
        when(aiResourceVersionPersistService.list(NAMESPACE_ID, "res", RESOURCE_TYPE,
            AiResourceConstants.VERSION_STATUS_ONLINE, 1, 500)).thenReturn(onlineVersions);
        when(aiResourcePersistService.updateMetaCas(eq(NAMESPACE_ID), eq("res"), eq(RESOURCE_TYPE),
            eq(1L), any()))
            .thenReturn(true);
        AiResourceVersion result =
            manager.toggleVersionOnlineStatus(NAMESPACE_ID, meta, info, "v1", true);
        assertNotNull(result);
        verify(aiResourceVersionPersistService).updateStatus(NAMESPACE_ID, "res", RESOURCE_TYPE,
            "v1",
            AiResourceConstants.VERSION_STATUS_ONLINE);
        assertEquals(2, info.getOnlineCnt());
        assertEquals("v1", info.getLabels().get(AiResourceConstants.LABEL_LATEST));
    }
    
    @Test
    void toggleVersionOnlineStatusShouldSetOffline() throws NacosException {
        AiResource meta = buildMeta("res");
        ResourceVersionInfo info = new ResourceVersionInfo();
        info.setOnlineCnt(3);
        info.setLabels(new HashMap<>());
        AiResourceVersion v = new AiResourceVersion();
        v.setVersion("v1");
        v.setStatus(AiResourceConstants.VERSION_STATUS_ONLINE);
        when(aiResourceVersionPersistService.find(NAMESPACE_ID, "res", RESOURCE_TYPE, "v1"))
            .thenReturn(v);
        when(aiResourcePersistService.updateMetaCas(eq(NAMESPACE_ID), eq("res"), eq(RESOURCE_TYPE),
            eq(1L), any()))
            .thenReturn(true);
        AiResourceVersion result =
            manager.toggleVersionOnlineStatus(NAMESPACE_ID, meta, info, "v1", false);
        assertNotNull(result);
        verify(aiResourceVersionPersistService).updateStatus(NAMESPACE_ID, "res", RESOURCE_TYPE,
            "v1",
            AiResourceConstants.VERSION_STATUS_OFFLINE);
        assertEquals(2, info.getOnlineCnt());
    }
    
    @Test
    void toggleVersionOnlineStatusShouldMoveLatestWhenCurrentLatestOffline()
        throws NacosException {
        AiResource meta = buildMeta("res");
        ResourceVersionInfo info = new ResourceVersionInfo();
        info.setOnlineCnt(2);
        info.setLabels(new HashMap<>(Map.of(AiResourceConstants.LABEL_LATEST, "2.0.0")));
        AiResourceVersion v = new AiResourceVersion();
        v.setVersion("2.0.0");
        v.setStatus(AiResourceConstants.VERSION_STATUS_ONLINE);
        when(aiResourceVersionPersistService.find(NAMESPACE_ID, "res", RESOURCE_TYPE, "2.0.0"))
            .thenReturn(v);
        AiResourceVersion fallback = new AiResourceVersion();
        fallback.setVersion("1.1.0");
        fallback.setStatus(AiResourceConstants.VERSION_STATUS_ONLINE);
        Page<AiResourceVersion> onlineVersions = new Page<>();
        onlineVersions.setPageItems(List.of(fallback));
        when(aiResourceVersionPersistService.list(NAMESPACE_ID, "res", RESOURCE_TYPE,
            AiResourceConstants.VERSION_STATUS_ONLINE, 1, 500)).thenReturn(onlineVersions);
        when(aiResourcePersistService.updateMetaCas(eq(NAMESPACE_ID), eq("res"), eq(RESOURCE_TYPE),
            eq(1L), any())).thenReturn(true);
        
        manager.toggleVersionOnlineStatus(NAMESPACE_ID, meta, info, "2.0.0", false);
        
        assertEquals(1, info.getOnlineCnt());
        assertEquals("1.1.0", info.getLabels().get(AiResourceConstants.LABEL_LATEST));
    }
    
    @Test
    void toggleVersionOnlineStatusShouldScanAllOnlineVersionPagesForLatest()
        throws NacosException {
        final AiResource meta = buildMeta("res");
        ResourceVersionInfo info = new ResourceVersionInfo();
        info.setOnlineCnt(502);
        info.setLabels(new HashMap<>(Map.of(AiResourceConstants.LABEL_LATEST, "1.0.0")));
        AiResourceVersion v = new AiResourceVersion();
        v.setVersion("1.0.0");
        v.setStatus(AiResourceConstants.VERSION_STATUS_ONLINE);
        when(aiResourceVersionPersistService.find(NAMESPACE_ID, "res", RESOURCE_TYPE, "1.0.0"))
            .thenReturn(v);
        
        List<AiResourceVersion> firstPageItems = new ArrayList<>();
        for (int i = 0; i < 500; i++) {
            AiResourceVersion item = new AiResourceVersion();
            item.setVersion("0.0." + i);
            item.setStatus(AiResourceConstants.VERSION_STATUS_ONLINE);
            firstPageItems.add(item);
        }
        Page<AiResourceVersion> firstPage = new Page<>();
        firstPage.setPageItems(firstPageItems);
        AiResourceVersion maxVersion = new AiResourceVersion();
        maxVersion.setVersion("2.0.0");
        maxVersion.setStatus(AiResourceConstants.VERSION_STATUS_ONLINE);
        Page<AiResourceVersion> secondPage = new Page<>();
        secondPage.setPageItems(List.of(maxVersion));
        when(aiResourceVersionPersistService.list(NAMESPACE_ID, "res", RESOURCE_TYPE,
            AiResourceConstants.VERSION_STATUS_ONLINE, 1, 500)).thenReturn(firstPage);
        when(aiResourceVersionPersistService.list(NAMESPACE_ID, "res", RESOURCE_TYPE,
            AiResourceConstants.VERSION_STATUS_ONLINE, 2, 500)).thenReturn(secondPage);
        when(aiResourcePersistService.updateMetaCas(eq(NAMESPACE_ID), eq("res"), eq(RESOURCE_TYPE),
            eq(1L), any())).thenReturn(true);
        
        manager.toggleVersionOnlineStatus(NAMESPACE_ID, meta, info, "1.0.0", false);
        
        assertEquals("2.0.0", info.getLabels().get(AiResourceConstants.LABEL_LATEST));
        verify(aiResourceVersionPersistService).list(NAMESPACE_ID, "res", RESOURCE_TYPE,
            AiResourceConstants.VERSION_STATUS_ONLINE, 1, 500);
        verify(aiResourceVersionPersistService).list(NAMESPACE_ID, "res", RESOURCE_TYPE,
            AiResourceConstants.VERSION_STATUS_ONLINE, 2, 500);
    }
    
    @Test
    void toggleVersionOnlineStatusShouldMoveLatestBackToMaxVersionWhenReOnline()
        throws NacosException {
        final AiResource meta = buildMeta("res");
        ResourceVersionInfo info = new ResourceVersionInfo();
        info.setOnlineCnt(1);
        info.setLabels(new HashMap<>(Map.of(AiResourceConstants.LABEL_LATEST, "1.1.0")));
        AiResourceVersion v = new AiResourceVersion();
        v.setVersion("2.0.0");
        v.setStatus(AiResourceConstants.VERSION_STATUS_OFFLINE);
        when(aiResourceVersionPersistService.find(NAMESPACE_ID, "res", RESOURCE_TYPE, "2.0.0"))
            .thenReturn(v);
        AiResourceVersion fallback = new AiResourceVersion();
        fallback.setVersion("1.1.0");
        fallback.setStatus(AiResourceConstants.VERSION_STATUS_ONLINE);
        AiResourceVersion restored = new AiResourceVersion();
        restored.setVersion("2.0.0");
        restored.setStatus(AiResourceConstants.VERSION_STATUS_ONLINE);
        Page<AiResourceVersion> onlineVersions = new Page<>();
        onlineVersions.setPageItems(List.of(fallback, restored));
        when(aiResourceVersionPersistService.list(NAMESPACE_ID, "res", RESOURCE_TYPE,
            AiResourceConstants.VERSION_STATUS_ONLINE, 1, 500)).thenReturn(onlineVersions);
        when(aiResourcePersistService.updateMetaCas(eq(NAMESPACE_ID), eq("res"), eq(RESOURCE_TYPE),
            eq(1L), any())).thenReturn(true);
        
        manager.toggleVersionOnlineStatus(NAMESPACE_ID, meta, info, "2.0.0", true);
        
        assertEquals(2, info.getOnlineCnt());
        assertEquals("2.0.0", info.getLabels().get(AiResourceConstants.LABEL_LATEST));
    }
    
    @Test
    void toggleVersionOnlineStatusShouldRemoveLatestWhenNoOnlineVersionRemains()
        throws NacosException {
        AiResource meta = buildMeta("res");
        ResourceVersionInfo info = new ResourceVersionInfo();
        info.setOnlineCnt(1);
        info.setLabels(new HashMap<>(Map.of(AiResourceConstants.LABEL_LATEST, "v1")));
        AiResourceVersion v = new AiResourceVersion();
        v.setVersion("v1");
        v.setStatus(AiResourceConstants.VERSION_STATUS_ONLINE);
        when(aiResourceVersionPersistService.find(NAMESPACE_ID, "res", RESOURCE_TYPE, "v1"))
            .thenReturn(v);
        Page<AiResourceVersion> onlineVersions = new Page<>();
        onlineVersions.setPageItems(List.of());
        when(aiResourceVersionPersistService.list(NAMESPACE_ID, "res", RESOURCE_TYPE,
            AiResourceConstants.VERSION_STATUS_ONLINE, 1, 500)).thenReturn(onlineVersions);
        when(aiResourcePersistService.updateMetaCas(eq(NAMESPACE_ID), eq("res"), eq(RESOURCE_TYPE),
            eq(1L), any())).thenReturn(true);
        
        manager.toggleVersionOnlineStatus(NAMESPACE_ID, meta, info, "v1", false);
        
        assertEquals(0, info.getOnlineCnt());
        assertFalse(info.getLabels().containsKey(AiResourceConstants.LABEL_LATEST));
    }
    
    @Test
    void toggleVersionOnlineStatusShouldReturnNullWhenAlreadyInTargetStatus()
        throws NacosException {
        AiResource meta = buildMeta("res");
        ResourceVersionInfo info = new ResourceVersionInfo();
        info.setOnlineCnt(1);
        AiResourceVersion v = new AiResourceVersion();
        v.setVersion("v1");
        v.setStatus(AiResourceConstants.VERSION_STATUS_ONLINE);
        when(aiResourceVersionPersistService.find(NAMESPACE_ID, "res", RESOURCE_TYPE, "v1"))
            .thenReturn(v);
        AiResourceVersion result =
            manager.toggleVersionOnlineStatus(NAMESPACE_ID, meta, info, "v1", true);
        assertNull(result);
        verify(aiResourceVersionPersistService, never()).updateStatus(anyString(), anyString(),
            anyString(),
            anyString(), anyString());
    }
    
    @Test
    void toggleVersionOnlineStatusShouldThrowWhenNotFound() {
        AiResource meta = buildMeta("res");
        ResourceVersionInfo info = new ResourceVersionInfo();
        when(aiResourceVersionPersistService.find(NAMESPACE_ID, "res", RESOURCE_TYPE, "v99"))
            .thenReturn(null);
        NacosApiException ex = assertThrows(NacosApiException.class,
            () -> manager.toggleVersionOnlineStatus(NAMESPACE_ID, meta, info, "v99", true));
        assertEquals(NacosException.NOT_FOUND, ex.getErrCode());
    }
    
    // ---- initOrUpdateMetaForDraft ----
    
    @Test
    void initOrUpdateMetaForDraftShouldCreateNewMeta() throws NacosException {
        manager.initOrUpdateMetaForDraft(NAMESPACE_ID, "res", RESOURCE_TYPE, "desc", "[\"tag\"]",
            "v1", null, true);
        ArgumentCaptor<AiResource> captor = ArgumentCaptor.forClass(AiResource.class);
        verify(aiResourcePersistService).insert(captor.capture());
        AiResource meta = captor.getValue();
        assertEquals("res", meta.getName());
        assertEquals(RESOURCE_TYPE, meta.getType());
        assertEquals(AiResourceConstants.META_STATUS_ENABLE, meta.getStatus());
        assertEquals("desc", meta.getDesc());
        assertEquals("[\"tag\"]", meta.getBizTags());
        assertEquals(VisibilityConstants.SCOPE_PRIVATE, meta.getScope());
        assertEquals(1L, meta.getMetaVersion());
    }
    
    @Test
    void initOrUpdateMetaForDraftShouldUsePluginDefaultScopeWhenCreateNewMeta()
        throws NacosException {
        com.alibaba.nacos.plugin.visibility.spi.VisibilityService mockVisibilityService =
            mock(com.alibaba.nacos.plugin.visibility.spi.VisibilityService.class);
        when(mockVisibilityService.resolveDefaultScopeForCreate(anyString(), anyString(),
            anyString()))
            .thenReturn(VisibilityConstants.SCOPE_PUBLIC);
        when(mockVisibilityManager.findVisibilityService(anyString()))
            .thenReturn(Optional.of(mockVisibilityService));
        manager.initOrUpdateMetaForDraft(NAMESPACE_ID, "res", RESOURCE_TYPE, "desc", "[\"tag\"]",
            "v1", null, true);
        ArgumentCaptor<AiResource> captor = ArgumentCaptor.forClass(AiResource.class);
        verify(aiResourcePersistService).insert(captor.capture());
        assertEquals(VisibilityConstants.SCOPE_PUBLIC, captor.getValue().getScope());
    }
    
    @Test
    void initOrUpdateMetaForDraftShouldUpdateExistingMeta() throws NacosException {
        AiResource existedMeta = buildMeta("res");
        when(aiResourcePersistService.updateMetaCas(eq(NAMESPACE_ID), eq("res"), eq(RESOURCE_TYPE),
            eq(1L), any()))
            .thenReturn(true);
        manager.initOrUpdateMetaForDraft(NAMESPACE_ID, "res", RESOURCE_TYPE, "desc", null,
            "v2", existedMeta, false);
        verify(aiResourcePersistService, never()).insert(any());
        verify(aiResourcePersistService).updateMetaCas(eq(NAMESPACE_ID), eq("res"),
            eq(RESOURCE_TYPE), eq(1L), any());
    }
    
    @Test
    void initOrUpdateMetaForDraftShouldPreserveLatestDescriptionWhenDescriptionBlankOnRetry()
        throws NacosException {
        AiResource existedMeta = buildMeta("res");
        existedMeta.setDesc("old-desc");
        AiResource latestMeta = buildMeta("res");
        latestMeta.setDesc("new-desc");
        latestMeta.setMetaVersion(2L);
        latestMeta.setVersionInfo(
            "{\"labels\":{\"latest\":\"v1\",\"stable\":\"v1\"},\"onlineCnt\":1}");
        when(aiResourcePersistService.updateMetaCas(eq(NAMESPACE_ID), eq("res"), eq(RESOURCE_TYPE),
            eq(1L), any()))
            .thenReturn(false);
        when(aiResourcePersistService.find(NAMESPACE_ID, "res", RESOURCE_TYPE))
            .thenReturn(latestMeta);
        when(aiResourcePersistService.updateMetaCas(eq(NAMESPACE_ID), eq("res"), eq(RESOURCE_TYPE),
            eq(2L), any()))
            .thenReturn(true);
        
        manager.initOrUpdateMetaForDraft(NAMESPACE_ID, "res", RESOURCE_TYPE, "", null, "v2",
            existedMeta, false);
        
        ArgumentCaptor<AiResource> captor = ArgumentCaptor.forClass(AiResource.class);
        verify(aiResourcePersistService).updateMetaCas(eq(NAMESPACE_ID), eq("res"),
            eq(RESOURCE_TYPE), eq(2L), captor.capture());
        assertEquals("new-desc", captor.getValue().getDesc());
        ResourceVersionInfo savedInfo =
            JacksonUtils.toObj(captor.getValue().getVersionInfo(), ResourceVersionInfo.class);
        assertEquals("v2", savedInfo.getEditingVersion());
        assertEquals("v1", savedInfo.getLabels().get(AiResourceConstants.LABEL_LATEST));
        assertEquals("v1", savedInfo.getLabels().get("stable"));
    }
    
    // ---- deleteResourceWithVersions ----
    
    @Test
    void deleteResourceWithVersionsShouldDeleteAll() throws NacosException {
        Page<AiResourceVersion> page = new Page<>();
        AiResourceVersion v1 = new AiResourceVersion();
        v1.setVersion("v1");
        AiResourceVersion v2 = new AiResourceVersion();
        v2.setVersion("v2");
        page.setPageItems(List.of(v1, v2));
        when(aiResourceVersionPersistService.list(eq(NAMESPACE_ID), eq("res"), eq(RESOURCE_TYPE),
            isNull(), eq(1), eq(200)))
            .thenReturn(page);
        AiResourceManager.VersionStorageDeleter deleter =
            mock(AiResourceManager.VersionStorageDeleter.class);
        manager.deleteResourceWithVersions(NAMESPACE_ID, "res", RESOURCE_TYPE, deleter);
        verify(aiResourcePersistService).delete(NAMESPACE_ID, "res", RESOURCE_TYPE);
        verify(aiResourceVersionPersistService).deleteByNameAndType(NAMESPACE_ID, "res",
            RESOURCE_TYPE);
        verify(deleter, times(2)).deleteStorage(any());
    }
    
    @Test
    void deleteResourceWithVersionsShouldHandleNullPage() throws NacosException {
        when(aiResourceVersionPersistService.list(eq(NAMESPACE_ID), eq("res"), eq(RESOURCE_TYPE),
            isNull(), eq(1), eq(200)))
            .thenReturn(null);
        AiResourceManager.VersionStorageDeleter deleter =
            mock(AiResourceManager.VersionStorageDeleter.class);
        manager.deleteResourceWithVersions(NAMESPACE_ID, "res", RESOURCE_TYPE, deleter);
        verify(aiResourcePersistService).delete(NAMESPACE_ID, "res", RESOURCE_TYPE);
        verify(deleter, never()).deleteStorage(any());
    }
    
    // ---- runPipelineExecution ----
    
    @Test
    void runPipelineExecutionShouldReturnFalseWhenPipelineFallsThrough() {
        PublishPipelineExecutor executor = mock(PublishPipelineExecutor.class);
        ResourceFilesPipelineContext ctx = mock(ResourceFilesPipelineContext.class);
        when(executor.execute(eq(ctx), any(), anyString())).thenReturn(null);
        boolean result =
            manager.runPipelineExecution(NAMESPACE_ID, "res", RESOURCE_TYPE, "v1", ctx, executor);
        assertFalse(result);
        // Should write then clear pipeline info
        verify(aiResourceVersionPersistService, times(2)).updatePublishPipelineInfo(
            eq(NAMESPACE_ID), eq("res"),
            eq(RESOURCE_TYPE), eq("v1"), any());
    }
    
    @Test
    void runPipelineExecutionShouldReturnTrueWhenPipelineIsAsync() {
        PublishPipelineExecutor executor = mock(PublishPipelineExecutor.class);
        ResourceFilesPipelineContext ctx = mock(ResourceFilesPipelineContext.class);
        when(executor.execute(eq(ctx), any(), anyString())).thenReturn("exec-async");
        boolean result =
            manager.runPipelineExecution(NAMESPACE_ID, "res", RESOURCE_TYPE, "v1", ctx, executor);
        assertTrue(result);
        // Should only write pipeline info (not clear)
        verify(aiResourceVersionPersistService, times(1)).updatePublishPipelineInfo(
            eq(NAMESPACE_ID), eq("res"),
            eq(RESOURCE_TYPE), eq("v1"), anyString());
    }
    
    // ---- Helper ----
    
    private static AiResource buildMeta(String name) {
        AiResource meta = new AiResource();
        meta.setNamespaceId(NAMESPACE_ID);
        meta.setName(name);
        meta.setType(RESOURCE_TYPE);
        meta.setStatus(AiResourceConstants.META_STATUS_ENABLE);
        meta.setDesc("test " + name);
        meta.setMetaVersion(1L);
        meta.setVersionInfo("{\"labels\":{},\"onlineCnt\":0}");
        return meta;
    }
}
