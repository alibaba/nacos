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
import com.alibaba.nacos.ai.pipeline.model.PipelineExecutionResult;
import com.alibaba.nacos.ai.pipeline.model.PipelineExecutionStatus;
import com.alibaba.nacos.ai.service.repository.AiResourcePersistService;
import com.alibaba.nacos.ai.service.repository.AiResourceVersionPersistService;
import com.alibaba.nacos.ai.service.repository.QueryCondition;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.common.utils.JacksonUtils;
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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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
        lenient().when(mockVisibilityManager.findVisibilityService(anyString())).thenReturn(Optional.empty());
        visibilityManagerStatic = org.mockito.Mockito.mockStatic(VisibilityPluginManager.class);
        visibilityManagerStatic.when(VisibilityPluginManager::getInstance).thenReturn(mockVisibilityManager);
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
        String json = "{\"editingVersion\":\"v2\",\"reviewingVersion\":\"v1\",\"onlineCnt\":3,\"labels\":{\"latest\":\"v1\"}}";
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
        when(aiResourcePersistService.find(NAMESPACE_ID, "test-resource", RESOURCE_TYPE)).thenReturn(meta);
        
        AiResource result = manager.requireMeta(NAMESPACE_ID, "test-resource", RESOURCE_TYPE);
        assertEquals(meta, result);
    }
    
    @Test
    void requireMetaShouldThrowNotFoundWhenMissing() {
        when(aiResourcePersistService.find(NAMESPACE_ID, "missing", RESOURCE_TYPE)).thenReturn(null);
        
        NacosApiException ex = assertThrows(NacosApiException.class,
                () -> manager.requireMeta(NAMESPACE_ID, "missing", RESOURCE_TYPE));
        assertEquals(NacosException.NOT_FOUND, ex.getErrCode());
        assertTrue(ex.getErrMsg().contains("missing"));
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
        when(aiResourcePersistService.updateMetaCas(eq(NAMESPACE_ID), eq("res"), eq(RESOURCE_TYPE), eq(1L), any()))
                .thenReturn(true);
        
        ResourceVersionInfo info = new ResourceVersionInfo();
        info.setEditingVersion("v2");
        manager.updateVersionInfoCas(NAMESPACE_ID, meta, info);
        
        verify(aiResourcePersistService).updateMetaCas(eq(NAMESPACE_ID), eq("res"), eq(RESOURCE_TYPE), eq(1L), any());
    }
    
    @Test
    void updateVersionInfoCasShouldRetryAndSucceed() throws NacosException {
        AiResource meta = buildMeta("res");
        AiResource updated = buildMeta("res");
        updated.setMetaVersion(2L);
        
        when(aiResourcePersistService.updateMetaCas(eq(NAMESPACE_ID), eq("res"), eq(RESOURCE_TYPE), eq(1L), any()))
                .thenReturn(false);
        when(aiResourcePersistService.find(NAMESPACE_ID, "res", RESOURCE_TYPE)).thenReturn(updated);
        when(aiResourcePersistService.updateMetaCas(eq(NAMESPACE_ID), eq("res"), eq(RESOURCE_TYPE), eq(2L), any()))
                .thenReturn(true);
        
        manager.updateVersionInfoCas(NAMESPACE_ID, meta, new ResourceVersionInfo());
    }
    
    @Test
    void updateVersionInfoCasShouldThrowConflictAfterMaxRetries() {
        AiResource meta = buildMeta("res");
        when(aiResourcePersistService.updateMetaCas(eq(NAMESPACE_ID), eq("res"), eq(RESOURCE_TYPE), anyLong(), any()))
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
        when(aiResourcePersistService.updateMetaCas(eq(NAMESPACE_ID), eq("res"), eq(RESOURCE_TYPE), eq(1L), any()))
                .thenReturn(false);
        when(aiResourcePersistService.find(NAMESPACE_ID, "res", RESOURCE_TYPE)).thenReturn(null);
        
        NacosApiException ex = assertThrows(NacosApiException.class,
                () -> manager.updateVersionInfoCas(NAMESPACE_ID, meta, new ResourceVersionInfo()));
        assertEquals(NacosException.SERVER_ERROR, ex.getErrCode());
    }
    
    // ---- updateBizTagsCas ----
    
    @Test
    void updateBizTagsCasShouldSucceedOnFirstAttempt() throws NacosException {
        AiResource meta = buildMeta("res");
        when(aiResourcePersistService.updateMetaCas(eq(NAMESPACE_ID), eq("res"), eq(RESOURCE_TYPE), eq(1L), any()))
                .thenReturn(true);
        
        manager.updateBizTagsCas(NAMESPACE_ID, meta, "[\"tag1\"]");
        
        verify(aiResourcePersistService).updateMetaCas(eq(NAMESPACE_ID), eq("res"), eq(RESOURCE_TYPE), eq(1L), any());
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
        
        when(aiResourcePersistService.updateMetaCas(eq(NAMESPACE_ID), eq("res"), eq(RESOURCE_TYPE), eq(1L), any()))
                .thenReturn(false);
        when(aiResourcePersistService.find(NAMESPACE_ID, "res", RESOURCE_TYPE)).thenReturn(latestMeta);
        when(aiResourcePersistService.updateMetaCas(eq(NAMESPACE_ID), eq("res"), eq(RESOURCE_TYPE), eq(2L), any()))
                .thenReturn(true);
        
        manager.updateBizTagsCas(NAMESPACE_ID, meta, "[\"newTag\"]");
        
        ArgumentCaptor<AiResource> captor = ArgumentCaptor.forClass(AiResource.class);
        verify(aiResourcePersistService).updateMetaCas(eq(NAMESPACE_ID), eq("res"), eq(RESOURCE_TYPE), eq(2L),
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
        when(aiResourcePersistService.updateMetaCas(eq(NAMESPACE_ID), eq("res"), eq(RESOURCE_TYPE), eq(1L), any()))
                .thenReturn(false);
        when(aiResourcePersistService.find(NAMESPACE_ID, "res", RESOURCE_TYPE)).thenReturn(null);
        
        NacosApiException ex = assertThrows(NacosApiException.class,
                () -> manager.updateBizTagsCas(NAMESPACE_ID, meta, "[\"tag\"]"));
        assertEquals(NacosException.SERVER_ERROR, ex.getErrCode());
    }
    
    @Test
    void updateBizTagsCasShouldThrowConflictAfterMaxRetries() {
        AiResource meta = buildMeta("res");
        when(aiResourcePersistService.updateMetaCas(eq(NAMESPACE_ID), eq("res"), eq(RESOURCE_TYPE), anyLong(), any()))
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
        when(aiResourcePersistService.updateMetaCas(eq(NAMESPACE_ID), eq("res"), eq(RESOURCE_TYPE), eq(1L), any()))
                .thenReturn(true);
        
        manager.metaEnableDisable(NAMESPACE_ID, meta, true);
        
        ArgumentCaptor<AiResource> captor = ArgumentCaptor.forClass(AiResource.class);
        verify(aiResourcePersistService).updateMetaCas(eq(NAMESPACE_ID), eq("res"), eq(RESOURCE_TYPE), eq(1L),
                captor.capture());
        assertEquals(AiResourceConstants.META_STATUS_ENABLE, captor.getValue().getStatus());
    }
    
    @Test
    void metaEnableDisableShouldSetDisableStatus() throws NacosException {
        AiResource meta = buildMeta("res");
        meta.setStatus(AiResourceConstants.META_STATUS_ENABLE);
        when(aiResourcePersistService.updateMetaCas(eq(NAMESPACE_ID), eq("res"), eq(RESOURCE_TYPE), eq(1L), any()))
                .thenReturn(true);
        
        manager.metaEnableDisable(NAMESPACE_ID, meta, false);
        
        ArgumentCaptor<AiResource> captor = ArgumentCaptor.forClass(AiResource.class);
        verify(aiResourcePersistService).updateMetaCas(eq(NAMESPACE_ID), eq("res"), eq(RESOURCE_TYPE), eq(1L),
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
        
        when(aiResourcePersistService.updateMetaCas(eq(NAMESPACE_ID), eq("res"), eq(RESOURCE_TYPE), eq(1L), any()))
                .thenReturn(false);
        when(aiResourcePersistService.find(NAMESPACE_ID, "res", RESOURCE_TYPE)).thenReturn(latestMeta);
        when(aiResourcePersistService.updateMetaCas(eq(NAMESPACE_ID), eq("res"), eq(RESOURCE_TYPE), eq(2L), any()))
                .thenReturn(true);
        
        manager.metaEnableDisable(NAMESPACE_ID, meta, true);
        
        ArgumentCaptor<AiResource> captor = ArgumentCaptor.forClass(AiResource.class);
        verify(aiResourcePersistService).updateMetaCas(eq(NAMESPACE_ID), eq("res"), eq(RESOURCE_TYPE), eq(2L),
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
        when(aiResourcePersistService.updateMetaCas(eq(NAMESPACE_ID), eq("res"), eq(RESOURCE_TYPE), eq(1L), any()))
                .thenReturn(false);
        when(aiResourcePersistService.find(NAMESPACE_ID, "res", RESOURCE_TYPE)).thenReturn(null);
        
        NacosApiException ex = assertThrows(NacosApiException.class,
                () -> manager.metaEnableDisable(NAMESPACE_ID, meta, true));
        assertEquals(NacosException.SERVER_ERROR, ex.getErrCode());
    }
    
    @Test
    void metaEnableDisableShouldThrowConflictAfterMaxRetries() {
        AiResource meta = buildMeta("res");
        when(aiResourcePersistService.updateMetaCas(eq(NAMESPACE_ID), eq("res"), eq(RESOURCE_TYPE), anyLong(), any()))
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
        verify(aiResourcePersistService, never()).updateMetaCas(anyString(), anyString(), anyString(), anyLong(), any());
    }
    
    @Test
    void bumpMetaDescriptionShouldDoNothingForNullMetaVersion() {
        AiResource meta = new AiResource();
        meta.setName("res");
        manager.bumpMetaDescription(NAMESPACE_ID, meta, "desc");
        verify(aiResourcePersistService, never()).updateMetaCas(anyString(), anyString(), anyString(), anyLong(), any());
    }
    
    @Test
    void bumpMetaDescriptionShouldSucceed() {
        AiResource meta = buildMeta("res");
        when(aiResourcePersistService.updateMetaCas(eq(NAMESPACE_ID), eq("res"), eq(RESOURCE_TYPE), eq(1L), any()))
                .thenReturn(true);
        
        manager.bumpMetaDescription(NAMESPACE_ID, meta, "new description");
        
        ArgumentCaptor<AiResource> captor = ArgumentCaptor.forClass(AiResource.class);
        verify(aiResourcePersistService).updateMetaCas(eq(NAMESPACE_ID), eq("res"), eq(RESOURCE_TYPE), eq(1L),
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
        
        when(aiResourcePersistService.updateMetaCas(eq(NAMESPACE_ID), eq("res"), eq(RESOURCE_TYPE), eq(1L), any()))
                .thenReturn(false);
        when(aiResourcePersistService.find(NAMESPACE_ID, "res", RESOURCE_TYPE)).thenReturn(latestMeta);
        when(aiResourcePersistService.updateMetaCas(eq(NAMESPACE_ID), eq("res"), eq(RESOURCE_TYPE), eq(2L), any()))
                .thenReturn(true);
        
        manager.bumpMetaDescription(NAMESPACE_ID, meta, "bumped");
        
        ArgumentCaptor<AiResource> captor = ArgumentCaptor.forClass(AiResource.class);
        verify(aiResourcePersistService).updateMetaCas(eq(NAMESPACE_ID), eq("res"), eq(RESOURCE_TYPE), eq(2L),
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
        when(aiResourcePersistService.updateMetaCas(eq(NAMESPACE_ID), eq("res"), eq(RESOURCE_TYPE), eq(1L), any()))
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
        
        when(aiResourcePersistService.updateMetaCas(eq(NAMESPACE_ID), eq("res"), eq(RESOURCE_TYPE), eq(1L), any()))
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
        verify(aiResourcePersistService, never()).updateMetaCas(anyString(), anyString(), anyString(), anyLong(), any());
    }
    
    @Test
    void syncImportedMetaShouldUseExistingValuesForBlankArgs() {
        AiResource meta = buildMeta("res");
        meta.setDesc("original-desc");
        meta.setBizTags("[\"original\"]");
        when(aiResourcePersistService.updateMetaCas(eq(NAMESPACE_ID), eq("res"), eq(RESOURCE_TYPE), eq(1L), any()))
                .thenReturn(true);
        
        manager.syncImportedMeta(NAMESPACE_ID, meta, "", "");
        
        ArgumentCaptor<AiResource> captor = ArgumentCaptor.forClass(AiResource.class);
        verify(aiResourcePersistService).updateMetaCas(eq(NAMESPACE_ID), eq("res"), eq(RESOURCE_TYPE), eq(1L),
                captor.capture());
        assertEquals("original-desc", captor.getValue().getDesc());
        assertEquals("[\"original\"]", captor.getValue().getBizTags());
    }
    
    @Test
    void syncImportedMetaShouldUseProvidedValuesWhenNotBlank() {
        AiResource meta = buildMeta("res");
        when(aiResourcePersistService.updateMetaCas(eq(NAMESPACE_ID), eq("res"), eq(RESOURCE_TYPE), eq(1L), any()))
                .thenReturn(true);
        
        manager.syncImportedMeta(NAMESPACE_ID, meta, "imported-desc", "[\"imported\"]");
        
        ArgumentCaptor<AiResource> captor = ArgumentCaptor.forClass(AiResource.class);
        verify(aiResourcePersistService).updateMetaCas(eq(NAMESPACE_ID), eq("res"), eq(RESOURCE_TYPE), eq(1L),
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
        when(aiResourcePersistService.updateMetaCas(eq(NAMESPACE_ID), eq("res"), eq(RESOURCE_TYPE), eq(1L), any()))
                .thenReturn(false);
        // find returns updated meta
        when(aiResourcePersistService.find(NAMESPACE_ID, "res", RESOURCE_TYPE)).thenReturn(latestMeta);
        // Second CAS attempt succeeds with new metaVersion
        when(aiResourcePersistService.updateMetaCas(eq(NAMESPACE_ID), eq("res"), eq(RESOURCE_TYPE), eq(2L), any()))
                .thenReturn(true);
        
        manager.syncImportedMeta(NAMESPACE_ID, meta, "new-desc", "[\"new\"]");
        
        // Verify the second CAS call carries refreshed fields from latestMeta
        ArgumentCaptor<AiResource> captor = ArgumentCaptor.forClass(AiResource.class);
        verify(aiResourcePersistService).updateMetaCas(eq(NAMESPACE_ID), eq("res"), eq(RESOURCE_TYPE), eq(2L),
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
        when(aiResourcePersistService.updateMetaCas(eq(NAMESPACE_ID), eq("res"), eq(RESOURCE_TYPE), eq(1L), any()))
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
        
        when(aiResourcePersistService.updateMetaCas(eq(NAMESPACE_ID), eq("res"), eq(RESOURCE_TYPE), eq(1L), any()))
                .thenReturn(false);
        when(aiResourcePersistService.find(NAMESPACE_ID, "res", RESOURCE_TYPE)).thenReturn(stale);
        
        manager.syncImportedMeta(NAMESPACE_ID, meta, "desc", "tags");
        
        verify(aiResourcePersistService, times(1))
                .updateMetaCas(anyString(), anyString(), anyString(), anyLong(), any());
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
        when(mockService.validateVisibility(anyString(), anyString(), anyString(), any())).thenReturn(denied);
        when(mockVisibilityManager.findVisibilityService(anyString())).thenReturn(Optional.of(mockService));
        
        assertThrows(NacosApiException.class, () -> manager.ensureReadableOrNotFound(meta, "resource not found"));
    }
    
    // ---- buildQueryCondition ----
    
    @Test
    void buildQueryConditionShouldReturnConditionWithCorrectFields() {
        QueryCondition condition = manager.buildQueryCondition(NAMESPACE_ID, RESOURCE_TYPE, "name%", "tag%",
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
        
        verify(aiResourceVersionPersistService).updatePublishPipelineInfo(eq(NAMESPACE_ID), eq("res"), eq(RESOURCE_TYPE),
                eq("v1"), anyString());
        // Approved -> should NOT rollback to draft
        verify(aiResourceVersionPersistService, never()).updateStatus(anyString(), anyString(), anyString(), anyString(),
                eq(AiResourceConstants.VERSION_STATUS_DRAFT));
    }
    
    @Test
    void onPipelineCompleteShouldRollbackOnRejection() {
        PipelineExecutionResult result = new PipelineExecutionResult();
        result.setExecutionId("exec-2");
        result.setStatus(PipelineExecutionStatus.REJECTED);
        result.setPipeline(new ArrayList<>());
        
        AiResource meta = buildMeta("res");
        ResourceVersionInfo vInfo = new ResourceVersionInfo();
        vInfo.setReviewingVersion("v1");
        vInfo.setLabels(new HashMap<>());
        meta.setVersionInfo(JacksonUtils.toJson(vInfo));
        
        when(aiResourcePersistService.find(NAMESPACE_ID, "res", RESOURCE_TYPE)).thenReturn(meta);
        when(aiResourcePersistService.updateMetaCas(eq(NAMESPACE_ID), eq("res"), eq(RESOURCE_TYPE), eq(1L), any()))
                .thenReturn(true);
        
        manager.onPipelineComplete(NAMESPACE_ID, "res", RESOURCE_TYPE, "v1", result);
        
        verify(aiResourceVersionPersistService).updateStatus(NAMESPACE_ID, "res", RESOURCE_TYPE, "v1",
                AiResourceConstants.VERSION_STATUS_DRAFT);
    }
    
    @Test
    void onPipelineCompleteShouldRollbackOnNullResult() {
        AiResource meta = buildMeta("res");
        ResourceVersionInfo vInfo = new ResourceVersionInfo();
        vInfo.setReviewingVersion("v1");
        vInfo.setLabels(new HashMap<>());
        meta.setVersionInfo(JacksonUtils.toJson(vInfo));
        
        when(aiResourcePersistService.find(NAMESPACE_ID, "res", RESOURCE_TYPE)).thenReturn(meta);
        when(aiResourcePersistService.updateMetaCas(eq(NAMESPACE_ID), eq("res"), eq(RESOURCE_TYPE), eq(1L), any()))
                .thenReturn(true);
        
        manager.onPipelineComplete(NAMESPACE_ID, "res", RESOURCE_TYPE, "v1", null);
        
        verify(aiResourceVersionPersistService).updateStatus(NAMESPACE_ID, "res", RESOURCE_TYPE, "v1",
                AiResourceConstants.VERSION_STATUS_DRAFT);
    }
    
    @Test
    void onPipelineCompleteShouldNotCrashWhenMetaNotFound() {
        PipelineExecutionResult result = new PipelineExecutionResult();
        result.setExecutionId("exec-3");
        result.setStatus(PipelineExecutionStatus.REJECTED);
        result.setPipeline(new ArrayList<>());
        
        when(aiResourcePersistService.find(NAMESPACE_ID, "res", RESOURCE_TYPE)).thenReturn(null);
        
        // Should not throw
        manager.onPipelineComplete(NAMESPACE_ID, "res", RESOURCE_TYPE, "v1", result);
    }
    
    @Test
    void onPipelineCompleteShouldCatchInnerExceptionFromUpdateVersionInfoCas() {
        PipelineExecutionResult result = new PipelineExecutionResult();
        result.setExecutionId("exec-4");
        result.setStatus(PipelineExecutionStatus.REJECTED);
        result.setPipeline(new ArrayList<>());
        
        AiResource meta = buildMeta("res");
        ResourceVersionInfo vInfo = new ResourceVersionInfo();
        vInfo.setReviewingVersion("v1");
        vInfo.setLabels(new HashMap<>());
        meta.setVersionInfo(JacksonUtils.toJson(vInfo));
        
        when(aiResourcePersistService.find(NAMESPACE_ID, "res", RESOURCE_TYPE)).thenReturn(meta);
        // Make updateVersionInfoCas fail: CAS returns false, then find returns null -> throws SERVER_ERROR
        when(aiResourcePersistService.updateMetaCas(eq(NAMESPACE_ID), eq("res"), eq(RESOURCE_TYPE), eq(1L), any()))
                .thenReturn(false);
        // On retry find returns null, causing META_LOST -> inner catch
        when(aiResourcePersistService.find(NAMESPACE_ID, "res", RESOURCE_TYPE))
                .thenReturn(meta)   // first call: in onPipelineComplete to get meta
                .thenReturn(null);  // second call: inside doCasLoop retry -> META_LOST
        
        // Should not throw - inner exception is caught and logged as warn
        manager.onPipelineComplete(NAMESPACE_ID, "res", RESOURCE_TYPE, "v1", result);
        
        verify(aiResourceVersionPersistService).updateStatus(NAMESPACE_ID, "res", RESOURCE_TYPE, "v1",
                AiResourceConstants.VERSION_STATUS_DRAFT);
    }
    
    @Test
    void onPipelineCompleteShouldCatchOuterThrowable() {
        PipelineExecutionResult result = new PipelineExecutionResult();
        result.setExecutionId("exec-5");
        result.setStatus(PipelineExecutionStatus.REJECTED);
        result.setPipeline(new ArrayList<>());
        
        // Make updatePublishPipelineInfo throw to trigger the outer catch
        doThrow(new RuntimeException("db error")).when(aiResourceVersionPersistService)
                .updatePublishPipelineInfo(anyString(), anyString(), anyString(), anyString(), anyString());
        
        // Should not throw - outer exception is caught and logged as error
        manager.onPipelineComplete(NAMESPACE_ID, "res", RESOURCE_TYPE, "v1", result);
        
        // Verify no further interactions after the exception
        verify(aiResourceVersionPersistService, never()).updateStatus(anyString(), anyString(), anyString(),
                anyString(), anyString());
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
