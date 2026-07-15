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

package com.alibaba.nacos.ai.service.agentspecs;

import com.alibaba.nacos.ai.constant.AiResourceConstants;
import com.alibaba.nacos.ai.model.AiResource;
import com.alibaba.nacos.ai.model.AiResourceVersion;
import com.alibaba.nacos.ai.pipeline.PublishPipelineExecutor;
import com.alibaba.nacos.ai.pipeline.PublishPipelineManager;
import com.alibaba.nacos.ai.pipeline.config.PipelineConfigProvider;
import com.alibaba.nacos.ai.pipeline.model.PipelineConfig;
import com.alibaba.nacos.ai.pipeline.repository.PipelineExecutionRepository;
import com.alibaba.nacos.ai.service.repository.AiResourcePersistService;
import com.alibaba.nacos.ai.service.repository.AiResourceVersionPersistService;
import com.alibaba.nacos.ai.service.resource.AiResourceManager;
import com.alibaba.nacos.api.ai.model.agentspecs.AgentSpec;
import com.alibaba.nacos.api.ai.model.agentspecs.AgentSpecBasicInfo;
import com.alibaba.nacos.api.ai.model.agentspecs.AgentSpecMeta;
import com.alibaba.nacos.api.ai.model.agentspecs.AgentSpecSummary;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.plugin.ai.storage.AiResourceStorageRouter;
import com.alibaba.nacos.plugin.ai.storage.model.StorageKey;
import com.alibaba.nacos.plugin.ai.storage.spi.AiResourceStorage;
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
import org.springframework.core.env.StandardEnvironment;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import com.alibaba.nacos.api.model.Page;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentSpecOperationServiceImplTest {
    
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
    
    private AgentSpecOperationServiceImpl service;
    
    private MockedStatic<VisibilityPluginManager> visibilityManagerStatic;
    
    private VisibilityPluginManager mockVisibilityManager;
    
    private static final org.springframework.core.env.ConfigurableEnvironment CACHED_ENVIRONMENT =
        EnvUtil.getEnvironment();
    
    @BeforeEach
    void setUp() {
        EnvUtil.setEnvironment(new StandardEnvironment());
        AiResourceStorageRouter.reset();
        lenient().when(storage.type()).thenReturn("nacos_config");
        AiResourceStorageRouter.join(storage);
        PipelineConfig disabledConfig = new PipelineConfig();
        disabledConfig.setEnabled(false);
        lenient().when(pipelineConfigProvider.getConfig()).thenReturn(disabledConfig);
        PublishPipelineExecutor publishPipelineExecutor = new PublishPipelineExecutor(
            new PublishPipelineManager(), pipelineConfigProvider, pipelineExecutionRepository,
            Executors.newSingleThreadExecutor());
        service = new AgentSpecOperationServiceImpl(aiResourcePersistService,
            aiResourceVersionPersistService,
            publishPipelineExecutor,
            new AiResourceManager(aiResourcePersistService, aiResourceVersionPersistService,
                pipelineExecutionRepository));
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
        AiResourceStorageRouter.reset();
        EnvUtil.setEnvironment(CACHED_ENVIRONMENT);
    }
    
    @Test
    void createDraftShouldCreateV1ForBrandNewAgentSpec() throws NacosException {
        String namespaceId = "public";
        String agentSpecName = "brand-new-agentspec";
        
        when(aiResourcePersistService.find(eq(namespaceId), eq(agentSpecName), anyString()))
            .thenReturn(null);
        
        String version = service.createDraft(namespaceId, agentSpecName, null, null);
        
        assertEquals("0.0.1", version);
        
        ArgumentCaptor<AiResourceVersion> versionCaptor =
            ArgumentCaptor.forClass(AiResourceVersion.class);
        verify(aiResourceVersionPersistService).insert(versionCaptor.capture());
        AiResourceVersion insertedVersion = versionCaptor.getValue();
        assertEquals(agentSpecName, insertedVersion.getName());
        assertEquals("0.0.1", insertedVersion.getVersion());
        assertEquals("draft", insertedVersion.getStatus());
        
        ArgumentCaptor<AiResource> metaCaptor = ArgumentCaptor.forClass(AiResource.class);
        verify(aiResourcePersistService).insert(metaCaptor.capture());
        AiResource insertedMeta = metaCaptor.getValue();
        assertEquals(agentSpecName, insertedMeta.getName());
        assertEquals("enable", insertedMeta.getStatus());
        Map<?, ?> versionInfo = JacksonUtils.toObj(insertedMeta.getVersionInfo(), Map.class);
        assertEquals("0.0.1", versionInfo.get("editingVersion"));
        assertEquals(0, ((Number) versionInfo.get("onlineCnt")).intValue());
        
        verify(storage, times(1)).save(any(StorageKey.class), any(byte[].class));
    }
    
    @Test
    void bootstrapAgentSpecFromZipShouldCreateOnlineV1WhenMetaMissing()
        throws NacosException, IOException {
        String namespaceId = "public";
        byte[] zipBytes = createValidZipBytes();
        when(aiResourcePersistService.find(eq(namespaceId), eq("测试坐席"), anyString()))
            .thenReturn(null);
        
        service.bootstrapAgentSpecFromZip(namespaceId, zipBytes);
        
        ArgumentCaptor<AiResourceVersion> versionCaptor =
            ArgumentCaptor.forClass(AiResourceVersion.class);
        verify(aiResourceVersionPersistService).insert(versionCaptor.capture());
        AiResourceVersion insertedVersion = versionCaptor.getValue();
        assertEquals("测试坐席", insertedVersion.getName());
        assertEquals("0.0.1", insertedVersion.getVersion());
        assertEquals("online", insertedVersion.getStatus());
        assertEquals("Test agentspec description", insertedVersion.getDesc());
        
        ArgumentCaptor<AiResource> metaCaptor = ArgumentCaptor.forClass(AiResource.class);
        verify(aiResourcePersistService).insert(metaCaptor.capture());
        AiResource insertedMeta = metaCaptor.getValue();
        assertEquals("测试坐席", insertedMeta.getName());
        assertEquals("enable", insertedMeta.getStatus());
        assertEquals("Test agentspec description", insertedMeta.getDesc());
        assertEquals("[\"design\",\"ux\"]", insertedMeta.getBizTags());
        Map<?, ?> versionInfo = JacksonUtils.toObj(insertedMeta.getVersionInfo(), Map.class);
        assertEquals(1, ((Number) versionInfo.get("onlineCnt")).intValue());
        assertEquals("0.0.1", ((Map<?, ?>) versionInfo.get("labels")).get("latest"));
        
        verify(storage, times(1)).save(any(StorageKey.class), any(byte[].class));
    }
    
    @Test
    void bootstrapAgentSpecFromZipShouldSkipWhenMetaExists() throws NacosException, IOException {
        String namespaceId = "public";
        byte[] zipBytes = createValidZipBytes();
        AiResource meta = new AiResource();
        meta.setName("测试坐席");
        meta.setType("agentspec");
        when(aiResourcePersistService.find(eq(namespaceId), eq("测试坐席"), anyString()))
            .thenReturn(meta);
        
        service.bootstrapAgentSpecFromZip(namespaceId, zipBytes);
        
        verify(aiResourceVersionPersistService, never()).insert(any(AiResourceVersion.class));
        verify(aiResourcePersistService, never()).insert(any(AiResource.class));
    }
    
    @Test
    void bootstrapAgentSpecFromZipShouldRepairBuiltInWhenLatestContentMissing()
        throws NacosException, IOException {
        String namespaceId = "public";
        AiResource meta = new AiResource();
        meta.setNamespaceId(namespaceId);
        meta.setName("测试坐席");
        meta.setType("agentspec");
        meta.setOwner("nacos");
        meta.setStatus("enable");
        meta.setDesc("old");
        meta.setBizTags("[\"legacy\"]");
        meta.setMetaVersion(5L);
        meta.setVersionInfo("{\"labels\":{\"latest\":\"v1\"},\"onlineCnt\":1}");
        AiResourceVersion onlineVersion = new AiResourceVersion();
        onlineVersion.setVersion("v1");
        onlineVersion.setStatus("online");
        onlineVersion.setAuthor("nacos");
        
        when(aiResourcePersistService.find(eq(namespaceId), eq("测试坐席"), anyString()))
            .thenReturn(meta);
        when(aiResourceVersionPersistService.find(eq(namespaceId), eq("测试坐席"), anyString(),
            eq("v1")))
            .thenReturn(onlineVersion);
        when(aiResourcePersistService.updateMetaCas(eq(namespaceId), eq("测试坐席"), eq("agentspec"),
            eq(5L), any()))
            .thenReturn(true);
        
        byte[] zipBytes = createValidZipBytesWithAgents();
        service.bootstrapAgentSpecFromZip(namespaceId, zipBytes);
        
        verify(aiResourceVersionPersistService, never()).insert(any(AiResourceVersion.class));
        verify(aiResourcePersistService, never()).insert(any(AiResource.class));
        verify(aiResourceVersionPersistService).updateStorageAndDesc(eq(namespaceId), eq("测试坐席"),
            eq("agentspec"), eq("v1"), anyString(), eq("Test agentspec description"));
        verify(aiResourcePersistService).updateMetaCas(eq(namespaceId), eq("测试坐席"), eq("agentspec"),
            eq(5L),
            argThat(resource -> resource != null
                && "Test agentspec description".equals(resource.getDesc())
                && "[\"design\",\"ux\"]".equals(resource.getBizTags())));
        verify(storage, times(2)).save(any(StorageKey.class), any(byte[].class));
    }
    
    @Test
    void createDraftShouldRejectBasedOnVersionForBrandNewAgentSpec() {
        String namespaceId = "public";
        String agentSpecName = "brand-new-agentspec";
        
        when(aiResourcePersistService.find(eq(namespaceId), eq(agentSpecName), anyString()))
            .thenReturn(null);
        
        NacosApiException exception = assertThrows(NacosApiException.class,
            () -> service.createDraft(namespaceId, agentSpecName, "v7", null));
        
        assertEquals(NacosException.NOT_FOUND, exception.getErrCode());
        assertTrue(
            exception.getErrMsg().contains("cannot use basedOnVersion for a brand-new agentspec"));
    }
    
    @Test
    void createDraftShouldCreateEmptyDraftWhenMetaExistsWithoutAnyBaseVersion()
        throws NacosException {
        String namespaceId = "public";
        String agentSpecName = "existing-agentspec";
        AiResource meta = new AiResource();
        meta.setNamespaceId(namespaceId);
        meta.setName(agentSpecName);
        meta.setType("agentspec");
        meta.setStatus("enable");
        meta.setVersionInfo("{\"labels\":{},\"onlineCnt\":0}");
        meta.setMetaVersion(1L);
        
        com.alibaba.nacos.api.model.Page<AiResourceVersion> emptyPage =
            new com.alibaba.nacos.api.model.Page<>();
        when(aiResourcePersistService.find(eq(namespaceId), eq(agentSpecName), anyString()))
            .thenReturn(meta);
        when(aiResourceVersionPersistService.list(eq(namespaceId), eq(agentSpecName),
            eq("agentspec"), isNull(),
            anyInt(), anyInt())).thenReturn(emptyPage);
        when(aiResourcePersistService.updateMetaCas(eq(namespaceId), eq(agentSpecName), anyString(),
            eq(1L), any()))
            .thenReturn(true);
        
        String version = service.createDraft(namespaceId, agentSpecName, null, null);
        
        assertEquals("0.0.1", version);
        verify(aiResourceVersionPersistService).insert(any(AiResourceVersion.class));
        verify(storage, times(1)).save(any(StorageKey.class), any(byte[].class));
    }
    
    @Test
    void uploadAgentSpecFromZipWithOverwriteUpdatesExistingDraft()
        throws NacosException, IOException {
        String namespaceId = "public";
        AiResource meta = new AiResource();
        meta.setNamespaceId(namespaceId);
        meta.setName("测试坐席");
        meta.setType("agentspec");
        meta.setStatus("enable");
        meta.setDesc("old");
        meta.setBizTags("[\"legacy\"]");
        meta.setMetaVersion(2L);
        meta.setVersionInfo("{\"editingVersion\":\"v2\",\"labels\":{},\"onlineCnt\":1}");
        AiResourceVersion version = new AiResourceVersion();
        version.setVersion("v2");
        version.setStatus("draft");
        when(aiResourcePersistService.find(eq(namespaceId), eq("测试坐席"), anyString()))
            .thenReturn(meta);
        when(aiResourceVersionPersistService.find(eq(namespaceId), eq("测试坐席"), anyString(),
            eq("v2")))
            .thenReturn(version);
        when(aiResourcePersistService.updateMetaCas(eq(namespaceId), eq("测试坐席"), eq("agentspec"),
            eq(2L), any()))
            .thenReturn(true);
        
        byte[] zipBytes = createValidZipBytes();
        String result = service.uploadAgentSpecFromZip(namespaceId, zipBytes, true);
        
        assertEquals("测试坐席", result);
        verify(aiResourceVersionPersistService).updateStorageAndDesc(eq(namespaceId), eq("测试坐席"),
            anyString(), eq("v2"), anyString(), eq("Test agentspec description"));
        verify(aiResourcePersistService).updateMetaCas(eq(namespaceId), eq("测试坐席"), eq("agentspec"),
            eq(2L),
            argThat(resource -> resource != null
                && "Test agentspec description".equals(resource.getDesc())
                && "[\"design\",\"ux\"]".equals(resource.getBizTags())));
        verify(aiResourceVersionPersistService, never()).insert(argThat(inserted -> inserted != null
            && "测试坐席".equals(inserted.getName()) && "v2".equals(inserted.getVersion())));
    }
    
    @Test
    void uploadAgentSpecFromZipWithOverwriteCreatesDraftWhenNoEditingDraftExists()
        throws NacosException,
        IOException {
        String namespaceId = "public";
        AiResource meta = new AiResource();
        meta.setNamespaceId(namespaceId);
        meta.setName("测试坐席");
        meta.setType("agentspec");
        meta.setStatus("enable");
        meta.setDesc("old");
        meta.setBizTags("[\"legacy\"]");
        meta.setMetaVersion(3L);
        meta.setVersionInfo("{\"reviewingVersion\":\"v2\",\"labels\":{},\"onlineCnt\":1}");
        Page<AiResourceVersion> versions = new Page<>();
        AiResourceVersion v2 = new AiResourceVersion();
        v2.setVersion("v2");
        versions.setPageItems(java.util.List.of(v2));
        when(aiResourcePersistService.find(eq(namespaceId), eq("测试坐席"), anyString()))
            .thenReturn(meta);
        when(aiResourceVersionPersistService.list(eq(namespaceId), eq("测试坐席"), eq("agentspec"),
            isNull(),
            anyInt(), anyInt())).thenReturn(versions);
        when(aiResourcePersistService.updateMetaCas(eq(namespaceId), eq("测试坐席"), anyString(),
            eq(3L), any()))
            .thenReturn(true);
        
        byte[] zipBytes = createValidZipBytes();
        String result = service.uploadAgentSpecFromZip(namespaceId, zipBytes, true);
        
        assertEquals("测试坐席", result);
        verify(aiResourceVersionPersistService).insert(argThat(inserted -> inserted != null
            && "测试坐席".equals(inserted.getName()) && "v3".equals(inserted.getVersion())));
    }
    
    @Test
    void uploadAgentSpecFromArchiveShouldImportAllAgentSpecs() throws NacosException, IOException {
        String namespaceId = "public";
        byte[] zipBytes = createArchiveZipBytes("坐席一", "坐席二");
        when(aiResourcePersistService.find(eq(namespaceId), anyString(), anyString()))
            .thenAnswer(invocation -> null);
        
        String result = service.uploadAgentSpecFromZip(namespaceId, zipBytes, false);
        
        assertEquals("Imported 2 agentspecs: 坐席一, 坐席二", result);
        verify(aiResourceVersionPersistService, times(2)).insert(any(AiResourceVersion.class));
        verify(aiResourcePersistService, times(2)).insert(any(AiResource.class));
        verify(storage, times(2)).save(any(StorageKey.class), any(byte[].class));
    }
    
    @Test
    void testUpdateScopeSuccess() throws NacosException {
        String namespaceId = "test-ns";
        String agentSpecName = "my-agentspec";
        AiResource meta = new AiResource();
        meta.setName(agentSpecName);
        meta.setType("agentspec");
        meta.setNamespaceId(namespaceId);
        when(aiResourcePersistService.find(eq(namespaceId), eq(agentSpecName), anyString()))
            .thenReturn(meta);
        when(aiResourcePersistService.updateScope(eq(namespaceId), eq(agentSpecName),
            eq("agentspec"), eq("PUBLIC")))
            .thenReturn(true);
        
        service.updateScope(namespaceId, agentSpecName, "PUBLIC");
        verify(aiResourcePersistService).updateScope(namespaceId, agentSpecName, "agentspec",
            "PUBLIC");
    }
    
    @Test
    void testUpdateScopeNotFound() {
        String namespaceId = "test-ns";
        String agentSpecName = "nonexistent";
        when(aiResourcePersistService.find(eq(namespaceId), eq(agentSpecName), anyString()))
            .thenReturn(null);
        
        NacosApiException ex = assertThrows(NacosApiException.class,
            () -> service.updateScope(namespaceId, agentSpecName, "PUBLIC"));
        assertEquals(NacosException.NOT_FOUND, ex.getErrCode());
    }
    
    @Test
    void testUpdateScopeFailed() {
        String namespaceId = "test-ns";
        String agentSpecName = "my-agentspec";
        AiResource meta = new AiResource();
        meta.setName(agentSpecName);
        meta.setType("agentspec");
        meta.setNamespaceId(namespaceId);
        when(aiResourcePersistService.find(eq(namespaceId), eq(agentSpecName), anyString()))
            .thenReturn(meta);
        when(aiResourcePersistService.updateScope(eq(namespaceId), eq(agentSpecName),
            eq("agentspec"), eq("PRIVATE")))
            .thenReturn(false);
        
        NacosApiException ex = assertThrows(NacosApiException.class,
            () -> service.updateScope(namespaceId, agentSpecName, "PRIVATE"));
        assertEquals(NacosException.SERVER_ERROR, ex.getErrCode());
        verify(aiResourcePersistService).updateScope(namespaceId, agentSpecName, "agentspec",
            "PRIVATE");
    }
    
    @Test
    void testUpdateBizTagsSuccess() throws NacosException {
        String namespaceId = "test-ns";
        String agentSpecName = "my-agentspec";
        AiResource meta = new AiResource();
        meta.setName(agentSpecName);
        meta.setType("agentspec");
        meta.setNamespaceId(namespaceId);
        meta.setStatus("enable");
        meta.setDesc("desc");
        meta.setVersionInfo("{\"labels\":{},\"onlineCnt\":1}");
        meta.setMetaVersion(1L);
        when(aiResourcePersistService.find(eq(namespaceId), eq(agentSpecName), anyString()))
            .thenReturn(meta);
        when(aiResourcePersistService.updateMetaCas(eq(namespaceId), eq(agentSpecName),
            eq("agentspec"), eq(1L), any()))
            .thenReturn(true);
        
        service.updateBizTags(namespaceId, agentSpecName, "[\"finance\"]");
        
        verify(aiResourcePersistService).updateMetaCas(eq(namespaceId), eq(agentSpecName),
            eq("agentspec"), eq(1L),
            argThat(resource -> resource != null && "[\"finance\"]".equals(resource.getBizTags())));
    }
    
    @Test
    void testGetAgentSpecDetailShouldContainBizTags() throws NacosException {
        String namespaceId = "public";
        String agentSpecName = "test-agentspec";
        AiResource meta = new AiResource();
        meta.setNamespaceId(namespaceId);
        meta.setName(agentSpecName);
        meta.setType("agentspec");
        meta.setStatus("enable");
        meta.setBizTags("[\"finance\"]");
        meta.setVersionInfo("{\"labels\":{\"latest\":\"v1\"},\"onlineCnt\":1}");
        Page<AiResourceVersion> versions = new Page<>();
        versions.setPageItems(List.of());
        when(aiResourcePersistService.find(eq(namespaceId), eq(agentSpecName), anyString()))
            .thenReturn(meta);
        when(aiResourceVersionPersistService.list(eq(namespaceId), eq(agentSpecName),
            eq("agentspec"), isNull(),
            anyInt(), anyInt())).thenReturn(versions);
        
        AgentSpecMeta result = service.getAgentSpecDetail(namespaceId, agentSpecName);
        
        assertNotNull(result);
        assertEquals("[\"finance\"]", result.getBizTags());
    }
    
    @Test
    void testListAgentSpecsShouldContainBizTags() throws NacosException {
        String namespaceId = "public";
        AiResource meta = new AiResource();
        meta.setNamespaceId(namespaceId);
        meta.setName("test-agentspec");
        meta.setType("agentspec");
        meta.setDesc("desc");
        meta.setBizTags("[\"iot\"]");
        Page<AiResource> metaPage = new Page<>();
        metaPage.setPageItems(List.of(meta));
        metaPage.setTotalCount(1);
        metaPage.setPagesAvailable(1);
        metaPage.setPageNumber(1);
        when(aiResourcePersistService.list(any(), eq(1), eq(10)))
            .thenReturn(metaPage);
        
        Page<AgentSpecSummary> result = service.listAgentSpecs(namespaceId, null, null, 1, 10);
        
        assertNotNull(result);
        assertEquals(1, result.getPageItems().size());
        assertEquals("[\"iot\"]", result.getPageItems().get(0).getBizTags());
    }
    
    @Test
    void testForcePublishSuccess() throws NacosException {
        String namespaceId = "test-ns";
        String agentSpecName = "my-agentspec";
        String version = "v1";
        
        AiResource meta = new AiResource();
        meta.setName(agentSpecName);
        meta.setType("agentspec");
        meta.setNamespaceId(namespaceId);
        meta.setStatus("enable");
        meta.setMetaVersion(1L);
        meta.setVersionInfo("{\"reviewingVersion\":\"v1\",\"labels\":{},\"onlineCnt\":1}");
        when(aiResourcePersistService.find(eq(namespaceId), eq(agentSpecName), anyString()))
            .thenReturn(meta);
        
        AiResourceVersion v = new AiResourceVersion();
        v.setVersion(version);
        v.setStatus("reviewed");
        when(aiResourceVersionPersistService.find(eq(namespaceId), eq(agentSpecName), anyString(),
            eq(version)))
            .thenReturn(v);
        when(aiResourcePersistService.updateMetaCas(eq(namespaceId), eq(agentSpecName),
            eq("agentspec"), eq(1L), any()))
            .thenReturn(true);
        
        service.forcePublish(namespaceId, agentSpecName, version, false);
        
        verify(aiResourceVersionPersistService).updateStatus(eq(namespaceId), eq(agentSpecName),
            anyString(),
            eq(version), eq("online"));
        verify(aiResourcePersistService).updateMetaCas(eq(namespaceId), eq(agentSpecName),
            eq("agentspec"), eq(1L),
            argThat(resource -> {
                Map<?, ?> info = JacksonUtils.toObj(resource.getVersionInfo(), Map.class);
                Map<?, ?> labels = (Map<?, ?>) info.get("labels");
                return version.equals(labels.get(AiResourceConstants.LABEL_LATEST));
            }));
    }
    
    @Test
    void testForcePublishVersionNotFound() {
        String namespaceId = "test-ns";
        String agentSpecName = "my-agentspec";
        String version = "v99";
        
        AiResource meta = new AiResource();
        meta.setName(agentSpecName);
        meta.setType("agentspec");
        meta.setNamespaceId(namespaceId);
        meta.setStatus("enable");
        meta.setVersionInfo("{\"labels\":{},\"onlineCnt\":1}");
        when(aiResourcePersistService.find(eq(namespaceId), eq(agentSpecName), anyString()))
            .thenReturn(meta);
        when(aiResourceVersionPersistService.find(eq(namespaceId), eq(agentSpecName), anyString(),
            eq(version)))
            .thenReturn(null);
        
        NacosApiException ex = assertThrows(NacosApiException.class,
            () -> service.forcePublish(namespaceId, agentSpecName, version, true));
        assertEquals(NacosException.NOT_FOUND, ex.getErrCode());
    }
    
    @Test
    void testForcePublishVersionAlreadyOnline() {
        String namespaceId = "test-ns";
        String agentSpecName = "my-agentspec";
        String version = "v1";
        
        AiResource meta = new AiResource();
        meta.setName(agentSpecName);
        meta.setType("agentspec");
        meta.setNamespaceId(namespaceId);
        meta.setStatus("enable");
        meta.setVersionInfo("{\"labels\":{},\"onlineCnt\":1}");
        when(aiResourcePersistService.find(eq(namespaceId), eq(agentSpecName), anyString()))
            .thenReturn(meta);
        
        AiResourceVersion v = new AiResourceVersion();
        v.setVersion(version);
        v.setStatus("online");
        when(aiResourceVersionPersistService.find(eq(namespaceId), eq(agentSpecName), anyString(),
            eq(version)))
            .thenReturn(v);
        
        NacosApiException ex = assertThrows(NacosApiException.class,
            () -> service.forcePublish(namespaceId, agentSpecName, version, true));
        assertEquals(NacosException.INVALID_PARAM, ex.getErrCode());
    }
    
    @Test
    void testPublishShouldBeIdempotentWhenVersionAlreadyOnline() throws NacosException {
        String namespaceId = "test-ns";
        String agentSpecName = "my-agentspec";
        String version = "v1";
        
        AiResource meta = new AiResource();
        meta.setName(agentSpecName);
        meta.setType("agentspec");
        meta.setNamespaceId(namespaceId);
        meta.setStatus("enable");
        meta.setMetaVersion(1L);
        meta.setVersionInfo("{\"reviewingVersion\":\"v1\",\"labels\":{},\"onlineCnt\":2}");
        when(aiResourcePersistService.find(eq(namespaceId), eq(agentSpecName), anyString()))
            .thenReturn(meta);
        when(aiResourcePersistService.updateMetaCas(eq(namespaceId), eq(agentSpecName),
            eq("agentspec"), eq(1L), any()))
            .thenReturn(true);
        
        AiResourceVersion v = new AiResourceVersion();
        v.setVersion(version);
        v.setStatus("online");
        when(aiResourceVersionPersistService.find(eq(namespaceId), eq(agentSpecName), anyString(),
            eq(version)))
            .thenReturn(v);
        
        service.publish(namespaceId, agentSpecName, version, true);
        
        // Should NOT call updateStatus since already online
        verify(aiResourceVersionPersistService, never()).updateStatus(anyString(), anyString(),
            anyString(),
            anyString(), anyString());
        // onlineCnt should remain 2 (not incremented)
        verify(aiResourcePersistService).updateMetaCas(eq(namespaceId), eq(agentSpecName),
            eq("agentspec"), eq(1L),
            argThat(resource -> {
                Map<?, ?> info = JacksonUtils.toObj(resource.getVersionInfo(), Map.class);
                return ((Number) info.get("onlineCnt")).intValue() == 2;
            }));
    }
    
    @Test
    void testChangeOnlineStatusShouldSkipWhenAlreadyOnline() throws NacosException {
        String namespaceId = "test-ns";
        String agentSpecName = "my-agentspec";
        String version = "v1";
        
        AiResource meta = new AiResource();
        meta.setName(agentSpecName);
        meta.setType("agentspec");
        meta.setNamespaceId(namespaceId);
        meta.setStatus("enable");
        meta.setMetaVersion(1L);
        meta.setVersionInfo("{\"labels\":{},\"onlineCnt\":1}");
        when(aiResourcePersistService.find(eq(namespaceId), eq(agentSpecName), anyString()))
            .thenReturn(meta);
        
        AiResourceVersion v = new AiResourceVersion();
        v.setVersion(version);
        v.setStatus("online");
        when(aiResourceVersionPersistService.find(eq(namespaceId), eq(agentSpecName), anyString(),
            eq(version)))
            .thenReturn(v);
        
        service.changeOnlineStatus(namespaceId, agentSpecName, "version", version, true);
        
        // Should NOT call updateStatus or updateMetaCas
        verify(aiResourceVersionPersistService, never()).updateStatus(anyString(), anyString(),
            anyString(),
            anyString(), anyString());
        verify(aiResourcePersistService, never()).updateMetaCas(anyString(), anyString(),
            anyString(), anyLong(), any());
    }
    
    @Test
    void testChangeOnlineStatusShouldSkipWhenAlreadyOffline() throws NacosException {
        String namespaceId = "test-ns";
        String agentSpecName = "my-agentspec";
        String version = "v1";
        
        AiResource meta = new AiResource();
        meta.setName(agentSpecName);
        meta.setType("agentspec");
        meta.setNamespaceId(namespaceId);
        meta.setStatus("enable");
        meta.setMetaVersion(1L);
        meta.setVersionInfo("{\"labels\":{},\"onlineCnt\":0}");
        when(aiResourcePersistService.find(eq(namespaceId), eq(agentSpecName), anyString()))
            .thenReturn(meta);
        
        AiResourceVersion v = new AiResourceVersion();
        v.setVersion(version);
        v.setStatus("offline");
        when(aiResourceVersionPersistService.find(eq(namespaceId), eq(agentSpecName), anyString(),
            eq(version)))
            .thenReturn(v);
        
        service.changeOnlineStatus(namespaceId, agentSpecName, "version", version, false);
        
        verify(aiResourceVersionPersistService, never()).updateStatus(anyString(), anyString(),
            anyString(),
            anyString(), anyString());
        verify(aiResourcePersistService, never()).updateMetaCas(anyString(), anyString(),
            anyString(), anyLong(), any());
    }
    
    @Test
    void testVersionOfflineShouldRemoveLatestWhenNoOnlineVersionRemains()
        throws NacosException {
        String namespaceId = "test-ns";
        String agentSpecName = "my-agentspec";
        String version = "v1";
        AiResource meta = new AiResource();
        meta.setName(agentSpecName);
        meta.setType("agentspec");
        meta.setNamespaceId(namespaceId);
        meta.setStatus("enable");
        meta.setMetaVersion(1L);
        meta.setVersionInfo("{\"labels\":{\"latest\":\"v1\"},\"onlineCnt\":1}");
        when(aiResourcePersistService.find(eq(namespaceId), eq(agentSpecName), anyString()))
            .thenReturn(meta);
        AiResourceVersion v = new AiResourceVersion();
        v.setVersion(version);
        v.setStatus("online");
        when(aiResourceVersionPersistService.find(eq(namespaceId), eq(agentSpecName), anyString(),
            eq(version))).thenReturn(v);
        Page<AiResourceVersion> emptyOnlinePage = new Page<>();
        emptyOnlinePage.setPageItems(List.of());
        when(aiResourceVersionPersistService.list(eq(namespaceId), eq(agentSpecName), anyString(),
            eq("online"), eq(1), eq(500))).thenReturn(emptyOnlinePage);
        when(aiResourcePersistService.updateMetaCas(eq(namespaceId), eq(agentSpecName),
            eq("agentspec"), eq(1L), any())).thenReturn(true);
        
        service.changeOnlineStatus(namespaceId, agentSpecName, "version", version, false);
        
        ArgumentCaptor<AiResource> captor = ArgumentCaptor.forClass(AiResource.class);
        verify(aiResourcePersistService).updateMetaCas(eq(namespaceId), eq(agentSpecName),
            eq("agentspec"), eq(1L), captor.capture());
        Map<?, ?> info = JacksonUtils.toObj(captor.getValue().getVersionInfo(), Map.class);
        Map<?, ?> labels = (Map<?, ?>) info.get("labels");
        assertTrue(!labels.containsKey("latest"));
    }
    
    @Test
    void testGetAgentSpecVersionDetailSuccess() throws NacosException {
        String namespaceId = "test-ns";
        String name = "my-agentspec";
        AiResource meta = new AiResource();
        meta.setName(name);
        meta.setType("agentspec");
        meta.setStatus("enable");
        when(aiResourcePersistService.find(eq(namespaceId), eq(name), anyString()))
            .thenReturn(meta);
        AiResourceVersion vRow = new AiResourceVersion();
        vRow.setVersion("v1");
        vRow.setStatus("online");
        when(aiResourceVersionPersistService.find(eq(namespaceId), eq(name), anyString(), eq("v1")))
            .thenReturn(vRow);
        String mainJson = "{\"name\":\"my-agentspec\",\"description\":\"desc\",\"resources\":[]}";
        when(storage.get(any(StorageKey.class)))
            .thenReturn(mainJson.getBytes(StandardCharsets.UTF_8));
        AgentSpec result = service.getAgentSpecVersionDetail(namespaceId, name, "v1");
        assertNotNull(result);
        assertEquals("my-agentspec", result.getName());
    }
    
    @Test
    void testGetAgentSpecVersionDetailBlankVersion() {
        String namespaceId = "test-ns";
        String name = "my-agentspec";
        AiResource meta = new AiResource();
        meta.setName(name);
        meta.setType("agentspec");
        meta.setStatus("enable");
        when(aiResourcePersistService.find(eq(namespaceId), eq(name), anyString()))
            .thenReturn(meta);
        NacosApiException ex = assertThrows(NacosApiException.class,
            () -> service.getAgentSpecVersionDetail(namespaceId, name, ""));
        assertEquals(NacosException.INVALID_PARAM, ex.getErrCode());
    }
    
    @Test
    void testGetAgentSpecVersionDetailVersionNotFound() {
        String namespaceId = "test-ns";
        String name = "my-agentspec";
        AiResource meta = new AiResource();
        meta.setName(name);
        meta.setType("agentspec");
        meta.setStatus("enable");
        when(aiResourcePersistService.find(eq(namespaceId), eq(name), anyString()))
            .thenReturn(meta);
        when(
            aiResourceVersionPersistService.find(eq(namespaceId), eq(name), anyString(), eq("v99")))
            .thenReturn(null);
        NacosApiException ex = assertThrows(NacosApiException.class,
            () -> service.getAgentSpecVersionDetail(namespaceId, name, "v99"));
        assertEquals(NacosException.NOT_FOUND, ex.getErrCode());
    }
    
    @Test
    void testGetAgentSpecVersionMetaSuccess() throws NacosException {
        String namespaceId = "test-ns";
        String name = "my-agentspec";
        AiResource meta = new AiResource();
        meta.setName(name);
        meta.setType("agentspec");
        meta.setStatus("enable");
        when(aiResourcePersistService.find(eq(namespaceId), eq(name), anyString()))
            .thenReturn(meta);
        AiResourceVersion vRow = new AiResourceVersion();
        vRow.setVersion("v1");
        vRow.setStatus("online");
        when(aiResourceVersionPersistService.find(eq(namespaceId), eq(name), anyString(), eq("v1")))
            .thenReturn(vRow);
        String mainJson = "{\"name\":\"my-agentspec\",\"description\":\"desc\","
            + "\"resources\":[{\"name\":\"config/SOUL.md\",\"type\":\"config\"},{\"name\":\"run.sh\",\"type\":\"other\"}]}";
        when(storage.get(any(StorageKey.class)))
            .thenReturn(mainJson.getBytes(StandardCharsets.UTF_8));
        AgentSpec result = service.getAgentSpecVersionMeta(namespaceId, name, "v1");
        assertNotNull(result);
        assertEquals("my-agentspec", result.getName());
        assertEquals("desc", result.getDescription());
        assertNotNull(result.getResource());
        assertEquals(2, result.getResource().size());
        for (Map.Entry<String, com.alibaba.nacos.api.ai.model.agentspecs.AgentSpecResource> entry : result
            .getResource().entrySet()) {
            assertNotNull(entry.getValue().getName());
            assertNotNull(entry.getValue().getType());
            assertEquals(null, entry.getValue().getContent());
            assertEquals(null, entry.getValue().getMetadata());
        }
        // Only one storage read for main config, no resource file reads
        verify(storage, times(1)).get(any(StorageKey.class));
    }
    
    @Test
    void testGetAgentSpecVersionMetaBlankVersion() {
        String namespaceId = "test-ns";
        String name = "my-agentspec";
        AiResource meta = new AiResource();
        meta.setName(name);
        meta.setType("agentspec");
        meta.setStatus("enable");
        when(aiResourcePersistService.find(eq(namespaceId), eq(name), anyString()))
            .thenReturn(meta);
        NacosApiException ex = assertThrows(NacosApiException.class,
            () -> service.getAgentSpecVersionMeta(namespaceId, name, ""));
        assertEquals(NacosException.INVALID_PARAM, ex.getErrCode());
    }
    
    @Test
    void testGetAgentSpecVersionMetaVersionNotFound() {
        String namespaceId = "test-ns";
        String name = "my-agentspec";
        AiResource meta = new AiResource();
        meta.setName(name);
        meta.setType("agentspec");
        meta.setStatus("enable");
        when(aiResourcePersistService.find(eq(namespaceId), eq(name), anyString()))
            .thenReturn(meta);
        when(
            aiResourceVersionPersistService.find(eq(namespaceId), eq(name), anyString(), eq("v99")))
            .thenReturn(null);
        NacosApiException ex = assertThrows(NacosApiException.class,
            () -> service.getAgentSpecVersionMeta(namespaceId, name, "v99"));
        assertEquals(NacosException.NOT_FOUND, ex.getErrCode());
    }
    
    @Test
    void testGetAgentSpecVersionMetaNotFound() {
        String namespaceId = "test-ns";
        String name = "nonexistent";
        when(aiResourcePersistService.find(eq(namespaceId), eq(name), anyString()))
            .thenReturn(null);
        NacosApiException ex = assertThrows(NacosApiException.class,
            () -> service.getAgentSpecVersionMeta(namespaceId, name, "v1"));
        assertEquals(NacosException.NOT_FOUND, ex.getErrCode());
    }
    
    @Test
    void testDeleteAgentSpecSuccess() throws NacosException {
        String namespaceId = "test-ns";
        String name = "my-agentspec";
        AiResource meta = new AiResource();
        meta.setName(name);
        meta.setType("agentspec");
        meta.setNamespaceId(namespaceId);
        meta.setStatus("enable");
        when(aiResourcePersistService.find(eq(namespaceId), eq(name), anyString()))
            .thenReturn(meta);
        AiResourceVersion v1 = new AiResourceVersion();
        v1.setVersion("v1");
        v1.setStatus("online");
        Page<AiResourceVersion> vPage = new Page<>();
        vPage.setPageItems(List.of(v1));
        when(aiResourceVersionPersistService.list(eq(namespaceId), eq(name), eq("agentspec"),
            isNull(),
            anyInt(), anyInt())).thenReturn(vPage);
        service.deleteAgentSpec(namespaceId, name);
        verify(aiResourceVersionPersistService).deleteByNameAndType(eq(namespaceId), eq(name),
            eq("agentspec"));
        verify(aiResourcePersistService).delete(eq(namespaceId), eq(name), eq("agentspec"));
    }
    
    @Test
    void testDeleteAgentSpecMetaNull() throws NacosException {
        String namespaceId = "test-ns";
        String name = "nonexistent";
        when(aiResourcePersistService.find(eq(namespaceId), eq(name), anyString()))
            .thenReturn(null);
        service.deleteAgentSpec(namespaceId, name);
        verify(aiResourcePersistService, never()).delete(anyString(), anyString(), anyString());
    }
    
    @Test
    void testSearchAgentSpecsSuccess() throws NacosException {
        String namespaceId = "test-ns";
        Page<AiResource> metaPage = new Page<>();
        AiResource meta = new AiResource();
        meta.setName("my-agentspec");
        meta.setStatus("enable");
        meta.setDesc("desc");
        meta.setVersionInfo("{\"labels\":{\"latest\":\"v1\"},\"onlineCnt\":1}");
        metaPage.setPageItems(List.of(meta));
        metaPage.setTotalCount(1);
        metaPage.setPagesAvailable(1);
        when(aiResourcePersistService.list(any(), eq(1), eq(10))).thenReturn(metaPage);
        Page<AgentSpecBasicInfo> result = service.searchAgentSpecs(namespaceId, "my", 1, 10);
        assertNotNull(result);
        assertEquals(1, result.getPageItems().size());
        assertEquals("my-agentspec", result.getPageItems().get(0).getName());
    }
    
    @Test
    void testSearchAgentSpecsExcludesDisabledAndNoOnline() throws NacosException {
        String namespaceId = "test-ns";
        Page<AiResource> metaPage = new Page<>();
        AiResource disabled = new AiResource();
        disabled.setName("disabled");
        disabled.setStatus("disable");
        disabled.setVersionInfo("{\"onlineCnt\":1}");
        AiResource noOnline = new AiResource();
        noOnline.setName("no-online");
        noOnline.setStatus("enable");
        noOnline.setVersionInfo("{\"onlineCnt\":0}");
        metaPage.setPageItems(List.of(disabled, noOnline));
        metaPage.setTotalCount(2);
        metaPage.setPagesAvailable(1);
        when(aiResourcePersistService.list(any(), eq(1), eq(10))).thenReturn(metaPage);
        Page<AgentSpecBasicInfo> result = service.searchAgentSpecs(namespaceId, null, 1, 10);
        assertTrue(result.getPageItems().isEmpty());
    }
    
    @Test
    void testQueryAgentSpecSuccess() throws NacosException {
        String namespaceId = "test-ns";
        String name = "my-agentspec";
        AiResource meta = new AiResource();
        meta.setName(name);
        meta.setType("agentspec");
        meta.setStatus("enable");
        meta.setVersionInfo("{\"labels\":{\"latest\":\"v1\"},\"onlineCnt\":1}");
        when(aiResourcePersistService.find(eq(namespaceId), eq(name), anyString()))
            .thenReturn(meta);
        AiResourceVersion vRow = new AiResourceVersion();
        vRow.setVersion("v1");
        vRow.setStatus("online");
        when(aiResourceVersionPersistService.find(eq(namespaceId), eq(name), anyString(), eq("v1")))
            .thenReturn(vRow);
        String mainJson = "{\"name\":\"my-agentspec\",\"description\":\"desc\",\"resources\":[]}";
        when(storage.get(any(StorageKey.class)))
            .thenReturn(mainJson.getBytes(StandardCharsets.UTF_8));
        AgentSpec result = service.queryAgentSpec(namespaceId, name, null, null);
        assertNotNull(result);
        assertEquals("my-agentspec", result.getName());
    }
    
    @Test
    void testQueryAgentSpecNotFound() {
        String namespaceId = "test-ns";
        String name = "nonexistent";
        when(aiResourcePersistService.find(eq(namespaceId), eq(name), anyString()))
            .thenReturn(null);
        NacosApiException ex = assertThrows(NacosApiException.class,
            () -> service.queryAgentSpec(namespaceId, name, null, null));
        assertEquals(NacosException.NOT_FOUND, ex.getErrCode());
    }
    
    @Test
    void testQueryAgentSpecDisabled() {
        String namespaceId = "test-ns";
        String name = "my-agentspec";
        AiResource meta = new AiResource();
        meta.setName(name);
        meta.setType("agentspec");
        meta.setStatus("disable");
        meta.setVersionInfo("{\"labels\":{},\"onlineCnt\":0}");
        when(aiResourcePersistService.find(eq(namespaceId), eq(name), anyString()))
            .thenReturn(meta);
        NacosApiException ex = assertThrows(NacosApiException.class,
            () -> service.queryAgentSpec(namespaceId, name, null, null));
        assertEquals(NacosException.NOT_FOUND, ex.getErrCode());
    }
    
    @Test
    void testQueryAgentSpecVersionNotOnline() {
        String namespaceId = "test-ns";
        String name = "my-agentspec";
        AiResource meta = new AiResource();
        meta.setName(name);
        meta.setType("agentspec");
        meta.setStatus("enable");
        meta.setVersionInfo("{\"labels\":{\"latest\":\"v1\"},\"onlineCnt\":1}");
        when(aiResourcePersistService.find(eq(namespaceId), eq(name), anyString()))
            .thenReturn(meta);
        AiResourceVersion vRow = new AiResourceVersion();
        vRow.setVersion("v1");
        vRow.setStatus("draft");
        when(aiResourceVersionPersistService.find(eq(namespaceId), eq(name), anyString(), eq("v1")))
            .thenReturn(vRow);
        NacosApiException ex = assertThrows(NacosApiException.class,
            () -> service.queryAgentSpec(namespaceId, name, null, null));
        assertEquals(NacosException.NOT_FOUND, ex.getErrCode());
    }
    
    @Test
    void testUpdateDraftSuccess() throws NacosException {
        String namespaceId = "test-ns";
        String name = "my-agentspec";
        AiResource meta = new AiResource();
        meta.setName(name);
        meta.setType("agentspec");
        meta.setNamespaceId(namespaceId);
        meta.setStatus("enable");
        meta.setMetaVersion(1L);
        meta.setVersionInfo("{\"editingVersion\":\"v2\",\"labels\":{},\"onlineCnt\":1}");
        when(aiResourcePersistService.find(eq(namespaceId), eq(name), anyString()))
            .thenReturn(meta);
        AiResourceVersion vRow = new AiResourceVersion();
        vRow.setVersion("v2");
        vRow.setStatus("draft");
        when(aiResourceVersionPersistService.find(eq(namespaceId), eq(name), anyString(), eq("v2")))
            .thenReturn(vRow);
        when(aiResourcePersistService.updateMetaCas(eq(namespaceId), eq(name), eq("agentspec"),
            eq(1L), any()))
            .thenReturn(true);
        AgentSpec draft = new AgentSpec();
        draft.setName(name);
        draft.setDescription("updated desc");
        service.updateDraft(namespaceId, draft);
        verify(aiResourceVersionPersistService).updateStorageAndDesc(eq(namespaceId), eq(name),
            eq("agentspec"), eq("v2"), anyString(), eq("updated desc"));
    }
    
    @Test
    void testUpdateDraftNullAgentSpec() {
        NacosApiException ex = assertThrows(NacosApiException.class,
            () -> service.updateDraft("test-ns", null));
        assertEquals(NacosException.INVALID_PARAM, ex.getErrCode());
    }
    
    @Test
    void testUpdateDraftNoEditing() {
        String namespaceId = "test-ns";
        String name = "my-agentspec";
        AiResource meta = new AiResource();
        meta.setName(name);
        meta.setType("agentspec");
        meta.setNamespaceId(namespaceId);
        meta.setStatus("enable");
        meta.setMetaVersion(1L);
        meta.setVersionInfo("{\"labels\":{},\"onlineCnt\":1}");
        when(aiResourcePersistService.find(eq(namespaceId), eq(name), anyString()))
            .thenReturn(meta);
        AgentSpec draft = new AgentSpec();
        draft.setName(name);
        NacosApiException ex = assertThrows(NacosApiException.class,
            () -> service.updateDraft(namespaceId, draft));
        assertEquals(NacosException.NOT_FOUND, ex.getErrCode());
    }
    
    @Test
    void testDeleteDraftSuccess() throws NacosException {
        String namespaceId = "test-ns";
        String name = "my-agentspec";
        AiResource meta = new AiResource();
        meta.setName(name);
        meta.setType("agentspec");
        meta.setNamespaceId(namespaceId);
        meta.setStatus("enable");
        meta.setMetaVersion(1L);
        meta.setVersionInfo("{\"editingVersion\":\"v2\",\"labels\":{},\"onlineCnt\":1}");
        when(aiResourcePersistService.find(eq(namespaceId), eq(name), anyString()))
            .thenReturn(meta);
        AiResourceVersion vRow = new AiResourceVersion();
        vRow.setVersion("v2");
        vRow.setStatus("draft");
        when(aiResourceVersionPersistService.find(eq(namespaceId), eq(name), anyString(), eq("v2")))
            .thenReturn(vRow);
        when(aiResourcePersistService.updateMetaCas(eq(namespaceId), eq(name), eq("agentspec"),
            eq(1L), any()))
            .thenReturn(true);
        service.deleteDraft(namespaceId, name);
        verify(aiResourceVersionPersistService).delete(eq(namespaceId), eq(name), eq("agentspec"),
            eq("v2"));
    }
    
    @Test
    void testDeleteDraftNoEditingReturnsEarly() throws NacosException {
        String namespaceId = "test-ns";
        String name = "my-agentspec";
        AiResource meta = new AiResource();
        meta.setName(name);
        meta.setType("agentspec");
        meta.setNamespaceId(namespaceId);
        meta.setStatus("enable");
        meta.setMetaVersion(1L);
        meta.setVersionInfo("{\"labels\":{},\"onlineCnt\":0}");
        when(aiResourcePersistService.find(eq(namespaceId), eq(name), anyString()))
            .thenReturn(meta);
        service.deleteDraft(namespaceId, name);
        verify(aiResourceVersionPersistService, never()).delete(anyString(), anyString(),
            anyString(), anyString());
    }
    
    @Test
    void testSubmitDirectPublishWhenNoPipeline() throws NacosException {
        String namespaceId = "test-ns";
        String name = "my-agentspec";
        AiResource meta = new AiResource();
        meta.setName(name);
        meta.setType("agentspec");
        meta.setNamespaceId(namespaceId);
        meta.setStatus("enable");
        meta.setMetaVersion(1L);
        meta.setVersionInfo("{\"editingVersion\":\"v1\",\"labels\":{},\"onlineCnt\":0}");
        when(aiResourcePersistService.find(eq(namespaceId), eq(name), anyString()))
            .thenReturn(meta);
        AiResourceVersion vRow = new AiResourceVersion();
        vRow.setVersion("v1");
        vRow.setStatus("draft");
        when(aiResourceVersionPersistService.find(eq(namespaceId), eq(name), anyString(), eq("v1")))
            .thenReturn(vRow);
        when(aiResourcePersistService.updateMetaCas(eq(namespaceId), eq(name), eq("agentspec"),
            eq(1L), any()))
            .thenReturn(true);
        String result = service.submit(namespaceId, name, null);
        assertEquals("v1", result);
        verify(aiResourceVersionPersistService).updateStatus(eq(namespaceId), eq(name), anyString(),
            eq("v1"), eq("online"));
    }
    
    @Test
    void testUpdateLabelsSuccess() throws NacosException {
        String namespaceId = "test-ns";
        String name = "my-agentspec";
        AiResource meta = new AiResource();
        meta.setName(name);
        meta.setType("agentspec");
        meta.setNamespaceId(namespaceId);
        meta.setStatus("enable");
        meta.setMetaVersion(1L);
        meta.setVersionInfo("{\"labels\":{},\"onlineCnt\":1}");
        when(aiResourcePersistService.find(eq(namespaceId), eq(name), anyString()))
            .thenReturn(meta);
        when(aiResourcePersistService.updateMetaCas(eq(namespaceId), eq(name), eq("agentspec"),
            eq(1L), any()))
            .thenReturn(true);
        Map<String, String> labels = Map.of("stable", "v2");
        service.updateLabels(namespaceId, name, labels);
        verify(aiResourcePersistService).updateMetaCas(eq(namespaceId), eq(name), eq("agentspec"),
            eq(1L), any());
    }
    
    @Test
    void testChangeOnlineStatusAgentSpecScopeEnable() throws NacosException {
        String namespaceId = "test-ns";
        String name = "my-agentspec";
        AiResource meta = new AiResource();
        meta.setName(name);
        meta.setType("agentspec");
        meta.setNamespaceId(namespaceId);
        meta.setStatus("disable");
        meta.setMetaVersion(1L);
        meta.setVersionInfo("{\"labels\":{},\"onlineCnt\":1}");
        when(aiResourcePersistService.find(eq(namespaceId), eq(name), anyString()))
            .thenReturn(meta);
        when(aiResourcePersistService.updateMetaCas(eq(namespaceId), eq(name), eq("agentspec"),
            eq(1L), any()))
            .thenReturn(true);
        service.changeOnlineStatus(namespaceId, name, "agentspec", null, true);
        verify(aiResourcePersistService).updateMetaCas(eq(namespaceId), eq(name), eq("agentspec"),
            eq(1L),
            argThat(resource -> "enable".equals(resource.getStatus())));
    }
    
    @Test
    void testChangeOnlineStatusAgentSpecScopeDisable() throws NacosException {
        String namespaceId = "test-ns";
        String name = "my-agentspec";
        AiResource meta = new AiResource();
        meta.setName(name);
        meta.setType("agentspec");
        meta.setNamespaceId(namespaceId);
        meta.setStatus("enable");
        meta.setMetaVersion(1L);
        meta.setVersionInfo("{\"labels\":{},\"onlineCnt\":1}");
        when(aiResourcePersistService.find(eq(namespaceId), eq(name), anyString()))
            .thenReturn(meta);
        when(aiResourcePersistService.updateMetaCas(eq(namespaceId), eq(name), eq("agentspec"),
            eq(1L), any()))
            .thenReturn(true);
        service.changeOnlineStatus(namespaceId, name, "agentspec", null, false);
        verify(aiResourcePersistService).updateMetaCas(eq(namespaceId), eq(name), eq("agentspec"),
            eq(1L),
            argThat(resource -> "disable".equals(resource.getStatus())));
    }
    
    // ===== Semver versioning and targetVersion tests =====
    
    @Test
    void createDraftWithTargetVersionShouldUseSpecifiedVersion() throws NacosException {
        String namespaceId = "public";
        String agentSpecName = "new-agentspec";
        
        when(aiResourcePersistService.find(eq(namespaceId), eq(agentSpecName), anyString()))
            .thenReturn(null);
        
        String version = service.createDraft(namespaceId, agentSpecName, null, "1.0.0");
        
        assertEquals("1.0.0", version);
        verify(aiResourceVersionPersistService)
            .insert(argThat(v -> "1.0.0".equals(v.getVersion())));
    }
    
    @Test
    void createDraftWithInvalidTargetVersionShouldReject() {
        String namespaceId = "public";
        String agentSpecName = "new-agentspec";
        
        when(aiResourcePersistService.find(eq(namespaceId), eq(agentSpecName), anyString()))
            .thenReturn(null);
        
        NacosApiException ex = assertThrows(NacosApiException.class,
            () -> service.createDraft(namespaceId, agentSpecName, null, "bad-version"));
        
        assertTrue(ex.getErrMsg().contains("Invalid targetVersion format"));
    }
    
    @Test
    void createDraftWithDuplicateTargetVersionShouldReject() {
        String namespaceId = "public";
        String agentSpecName = "existing-agentspec";
        AiResource meta = new AiResource();
        meta.setNamespaceId(namespaceId);
        meta.setName(agentSpecName);
        meta.setType("agentspec");
        meta.setStatus("enable");
        meta.setVersionInfo("{\"labels\":{\"latest\":\"0.0.1\"},\"onlineCnt\":1}");
        meta.setMetaVersion(1L);
        
        Page<AiResourceVersion> versionPage = new Page<>();
        AiResourceVersion existing = new AiResourceVersion();
        existing.setVersion("0.0.1");
        versionPage.setPageItems(List.of(existing));
        
        when(aiResourcePersistService.find(eq(namespaceId), eq(agentSpecName), anyString()))
            .thenReturn(meta);
        when(aiResourceVersionPersistService.list(eq(namespaceId), eq(agentSpecName),
            eq("agentspec"), isNull(),
            anyInt(), anyInt())).thenReturn(versionPage);
        
        NacosApiException ex = assertThrows(NacosApiException.class,
            () -> service.createDraft(namespaceId, agentSpecName, null, "0.0.1"));
        
        assertTrue(ex.getErrMsg().contains("targetVersion already exists"));
    }
    
    @Test
    void createDraftWithTargetVersionSmallerThanBaseShouldReject() {
        String namespaceId = "public";
        String agentSpecName = "existing-agentspec";
        AiResource meta = new AiResource();
        meta.setNamespaceId(namespaceId);
        meta.setName(agentSpecName);
        meta.setType("agentspec");
        meta.setStatus("enable");
        meta.setVersionInfo("{\"labels\":{\"latest\":\"0.0.2\"},\"onlineCnt\":1}");
        meta.setMetaVersion(1L);
        
        AiResourceVersion v2 = new AiResourceVersion();
        v2.setVersion("0.0.2");
        v2.setStatus("online");
        when(aiResourcePersistService.find(eq(namespaceId), eq(agentSpecName), anyString()))
            .thenReturn(meta);
        
        Page<AiResourceVersion> versionPage = new Page<>();
        versionPage.setPageItems(List.of(v2));
        when(aiResourceVersionPersistService.list(eq(namespaceId), eq(agentSpecName),
            eq("agentspec"), isNull(),
            anyInt(), anyInt())).thenReturn(versionPage);
        
        NacosApiException ex = assertThrows(NacosApiException.class,
            () -> service.createDraft(namespaceId, agentSpecName, "0.0.2", "0.0.1"));
        
        assertTrue(ex.getErrMsg().contains("targetVersion must be greater than basedOnVersion"));
    }
    
    @Test
    void createDraftAutoIncrementShouldUseSemverWhenSemverExists() throws NacosException {
        String namespaceId = "public";
        String agentSpecName = "existing-agentspec";
        AiResource meta = new AiResource();
        meta.setNamespaceId(namespaceId);
        meta.setName(agentSpecName);
        meta.setType("agentspec");
        meta.setStatus("enable");
        meta.setVersionInfo("{\"labels\":{},\"onlineCnt\":0}");
        meta.setMetaVersion(1L);
        
        Page<AiResourceVersion> emptyPage = new Page<>();
        Page<AiResourceVersion> versionPage = new Page<>();
        AiResourceVersion v1 = new AiResourceVersion();
        v1.setVersion("0.0.1");
        AiResourceVersion v2 = new AiResourceVersion();
        v2.setVersion("0.0.2");
        versionPage.setPageItems(List.of(v1, v2));
        
        when(aiResourcePersistService.find(eq(namespaceId), eq(agentSpecName), anyString()))
            .thenReturn(meta);
        // 1st call: resolveBaseVersion → listExistingVersions → returns empty (no base found)
        // 2nd call: nextVersion → listExistingVersions → returns versions for auto-increment
        when(aiResourceVersionPersistService.list(eq(namespaceId), eq(agentSpecName),
            eq("agentspec"), isNull(),
            anyInt(), anyInt())).thenReturn(emptyPage).thenReturn(versionPage);
        when(aiResourcePersistService.updateMetaCas(eq(namespaceId), eq(agentSpecName), anyString(),
            eq(1L), any()))
            .thenReturn(true);
        
        String version = service.createDraft(namespaceId, agentSpecName, null, null);
        
        assertEquals("0.0.3", version);
    }
    
    @Test
    void createDraftAutoIncrementShouldFallbackToVnWhenOnlyLegacyExists() throws NacosException {
        String namespaceId = "public";
        String agentSpecName = "legacy-agentspec";
        AiResource meta = new AiResource();
        meta.setNamespaceId(namespaceId);
        meta.setName(agentSpecName);
        meta.setType("agentspec");
        meta.setStatus("enable");
        meta.setVersionInfo("{\"labels\":{},\"onlineCnt\":0}");
        meta.setMetaVersion(1L);
        
        Page<AiResourceVersion> emptyPage = new Page<>();
        Page<AiResourceVersion> versionPage = new Page<>();
        AiResourceVersion v1 = new AiResourceVersion();
        v1.setVersion("v1");
        AiResourceVersion v2 = new AiResourceVersion();
        v2.setVersion("v2");
        versionPage.setPageItems(List.of(v1, v2));
        
        when(aiResourcePersistService.find(eq(namespaceId), eq(agentSpecName), anyString()))
            .thenReturn(meta);
        // 1st call: resolveBaseVersion → listExistingVersions → returns empty (no base found)
        // 2nd call: nextVersion → listExistingVersions → returns versions for auto-increment
        when(aiResourceVersionPersistService.list(eq(namespaceId), eq(agentSpecName),
            eq("agentspec"), isNull(),
            anyInt(), anyInt())).thenReturn(emptyPage).thenReturn(versionPage);
        when(aiResourcePersistService.updateMetaCas(eq(namespaceId), eq(agentSpecName), anyString(),
            eq(1L), any()))
            .thenReturn(true);
        
        String version = service.createDraft(namespaceId, agentSpecName, null, null);
        
        assertEquals("v3", version);
    }
    
    @Test
    void createDraftAutoIncrementShouldReturn001WhenNoVersionsExist() throws NacosException {
        String namespaceId = "public";
        String agentSpecName = "brand-new";
        
        when(aiResourcePersistService.find(eq(namespaceId), eq(agentSpecName), anyString()))
            .thenReturn(null);
        
        String version = service.createDraft(namespaceId, agentSpecName, null, null);
        
        assertEquals("0.0.1", version);
    }
    
    private byte[] createValidZipBytes() throws IOException {
        String manifest = "{\"version\":\"1.0\",\"description\":\"Test agentspec description\","
            + "\"tags\":[\"design\",\"ux\"],"
            + "\"worker\":{\"suggested_name\":\"测试坐席\"}}";
        return createZipBytes("manifest.json", manifest);
    }
    
    private byte[] createValidZipBytesWithAgents() throws IOException {
        String manifest = "{\"version\":\"1.0\",\"description\":\"Test agentspec description\","
            + "\"tags\":[\"design\",\"ux\"],"
            + "\"worker\":{\"suggested_name\":\"测试坐席\"}}";
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos, StandardCharsets.UTF_8)) {
            addZipEntry(zos, "manifest.json", manifest);
            addZipEntry(zos, "config/AGENTS.md", "# 测试坐席\n");
        }
        return baos.toByteArray();
    }
    
    private byte[] createArchiveZipBytes(String... names) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos, StandardCharsets.UTF_8)) {
            for (String each : names) {
                ZipEntry manifestEntry = new ZipEntry(each + "/manifest.json");
                zos.putNextEntry(manifestEntry);
                String manifest = "{\"version\":\"1.0\",\"tags\":[\"archive\"],"
                    + "\"worker\":{\"suggested_name\":\"" + each + "\"}}";
                zos.write(manifest.getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }
        }
        return baos.toByteArray();
    }
    
    private byte[] createZipBytes(String path, String content) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos, StandardCharsets.UTF_8)) {
            addZipEntry(zos, path, content);
        }
        return baos.toByteArray();
    }
    
    private void addZipEntry(ZipOutputStream zos, String path, String content) throws IOException {
        ZipEntry manifestEntry = new ZipEntry(path);
        zos.putNextEntry(manifestEntry);
        zos.write(content.getBytes(StandardCharsets.UTF_8));
        zos.closeEntry();
    }
}
