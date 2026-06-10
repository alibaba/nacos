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

package com.alibaba.nacos.ai.service.skills;

import com.alibaba.nacos.ai.constant.AiResourceConstants;
import com.alibaba.nacos.ai.model.AiResource;
import com.alibaba.nacos.ai.pipeline.PublishPipelineExecutor;
import com.alibaba.nacos.ai.pipeline.PublishPipelineManager;
import com.alibaba.nacos.ai.pipeline.config.PipelineConfigProvider;
import com.alibaba.nacos.ai.pipeline.model.PipelineCallback;
import com.alibaba.nacos.ai.pipeline.model.PipelineConfig;
import com.alibaba.nacos.ai.pipeline.model.PipelineExecution;
import com.alibaba.nacos.ai.pipeline.model.PipelineExecutionResult;
import com.alibaba.nacos.ai.pipeline.model.PipelineExecutionStatus;
import com.alibaba.nacos.ai.pipeline.repository.PipelineExecutionRepository;
import com.alibaba.nacos.ai.service.repository.AiResourcePersistService;
import com.alibaba.nacos.ai.service.repository.AiResourceVersionPersistService;
import com.alibaba.nacos.ai.service.resource.AiResourceManager;
import com.alibaba.nacos.api.ai.model.skills.Skill;
import com.alibaba.nacos.api.ai.model.skills.SkillBasicInfo;
import com.alibaba.nacos.api.ai.model.skills.SkillMeta;
import com.alibaba.nacos.api.ai.model.skills.SkillResource;
import com.alibaba.nacos.api.ai.model.skills.SkillSummary;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.core.context.RequestContext;
import com.alibaba.nacos.core.context.RequestContextHolder;
import com.alibaba.nacos.core.context.addition.AuthContext;
import com.alibaba.nacos.plugin.ai.storage.AiResourceStorageRouter;
import com.alibaba.nacos.plugin.ai.storage.model.StorageKey;
import com.alibaba.nacos.plugin.ai.storage.spi.AiResourceStorage;
import com.alibaba.nacos.plugin.auth.api.IdentityContext;
import com.alibaba.nacos.plugin.auth.constant.Constants;
import com.alibaba.nacos.plugin.visibility.constant.VisibilityConstants;
import com.alibaba.nacos.plugin.visibility.model.BaseVisibilityPredicate;
import com.alibaba.nacos.plugin.visibility.spi.QueryAdvisor;
import com.alibaba.nacos.plugin.visibility.spi.ValidationResult;
import com.alibaba.nacos.plugin.visibility.spi.VisibilityPluginManager;
import com.alibaba.nacos.plugin.visibility.spi.VisibilityService;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.springframework.core.env.StandardEnvironment;

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
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

/**
 * Test for SkillOperationServiceImpl.
 *
 * @author nacos
 */
@ExtendWith(MockitoExtension.class)
class SkillOperationServiceImplTest {
    
    private static final String AUTO_PUBLISH_AFTER_REVIEW_ENABLED_KEY =
        "nacos.ai.skill.auto-publish-after-review.enabled";
    
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
    private SkillIndexManifestService manifestService;
    
    private SkillOperationServiceImpl skillOperationService;
    
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
        PublishPipelineExecutor publishPipelineExecutor = new PublishPipelineExecutor(
            new PublishPipelineManager(), pipelineConfigProvider, pipelineExecutionRepository,
            Executors.newSingleThreadExecutor());
        skillOperationService =
            new SkillOperationServiceImpl(aiResourcePersistService, aiResourceVersionPersistService,
                publishPipelineExecutor, manifestService,
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
        System.clearProperty(AUTO_PUBLISH_AFTER_REVIEW_ENABLED_KEY);
        EnvUtil.setEnvironment(CACHED_ENVIRONMENT);
    }
    
    @Test
    void testGetSkillDetailSuccessfully() throws NacosException {
        // Given
        String namespaceId = "test-namespace";
        String skillName = "test-skill";
        com.alibaba.nacos.ai.model.AiResource meta = new com.alibaba.nacos.ai.model.AiResource();
        meta.setName(skillName);
        meta.setType("skill");
        meta.setStatus("enable");
        meta.setScope(VisibilityConstants.SCOPE_PUBLIC);
        meta.setBizTags("[\"retail\"]");
        meta.setVersionInfo("{\"labels\":{\"latest\":\"v1\"},\"onlineCnt\":1}");
        when(aiResourcePersistService.find(eq(namespaceId), eq(skillName), anyString()))
            .thenReturn(meta);
        Page<com.alibaba.nacos.ai.model.AiResourceVersion> vPage = new Page<>();
        vPage.setPageItems(List.of());
        when(aiResourceVersionPersistService.list(eq(namespaceId), eq(skillName), anyString(),
            any(), anyInt(), anyInt())).thenReturn(vPage);
        
        // When
        SkillMeta skillDetail = skillOperationService.getSkillDetail(namespaceId, skillName);
        
        // Then
        assertNotNull(skillDetail);
        assertTrue(skillDetail.isEnable());
        assertEquals(1, skillDetail.getOnlineCnt());
        assertEquals("v1", skillDetail.getLabels().get("latest"));
        assertEquals("[\"retail\"]", skillDetail.getBizTags());
        assertEquals(VisibilityConstants.SCOPE_PUBLIC, skillDetail.getScope());
        assertNotNull(skillDetail.getVersions());
    }
    
    @Test
    void testGetSkillDetailNotFound() throws NacosException {
        // Given
        String namespaceId = "test-namespace";
        String skillName = "non-existent-skill";
        when(aiResourcePersistService.find(eq(namespaceId), eq(skillName), anyString()))
            .thenReturn(null);
        
        // When & Then
        NacosApiException exception = assertThrows(NacosApiException.class,
            () -> skillOperationService.getSkillDetail(namespaceId, skillName));
        assertEquals(NacosException.NOT_FOUND, exception.getErrCode());
    }
    
    @Test
    void testDeleteSkillSuccessfully() throws NacosException {
        // Given
        String namespaceId = "test-namespace";
        String skillName = "test-skill";
        com.alibaba.nacos.ai.model.AiResource meta = new com.alibaba.nacos.ai.model.AiResource();
        meta.setName(skillName);
        meta.setType("skill");
        meta.setStatus("enable");
        when(aiResourcePersistService.find(eq(namespaceId), eq(skillName), anyString()))
            .thenReturn(meta);
        Page<com.alibaba.nacos.ai.model.AiResourceVersion> vPage = new Page<>();
        com.alibaba.nacos.ai.model.AiResourceVersion v1 =
            new com.alibaba.nacos.ai.model.AiResourceVersion();
        v1.setVersion("v1");
        vPage.setPageItems(List.of(v1));
        when(aiResourceVersionPersistService.list(eq(namespaceId), eq(skillName), anyString(),
            isNull(), anyInt(), anyInt())).thenReturn(vPage);
        
        // When
        skillOperationService.deleteSkill(namespaceId, skillName);
        
        // Then
        verify(manifestService).delete(anyString(), anyString());
        verify(aiResourcePersistService).delete(eq(namespaceId), eq(skillName), anyString());
    }
    
    @Test
    void testDeleteSkillAlreadyDeleted() throws NacosException {
        // Given
        String namespaceId = "test-namespace";
        String skillName = "test-skill";
        when(aiResourcePersistService.find(eq(namespaceId), eq(skillName), anyString()))
            .thenReturn(null);
        
        // When
        skillOperationService.deleteSkill(namespaceId, skillName);
        
        // Then
        verify(storage, never()).delete(any(StorageKey.class));
    }
    
    @Test
    void testListSkillsSuccessfully() throws NacosException {
        // Given
        String namespaceId = "test-namespace";
        Page<com.alibaba.nacos.ai.model.AiResource> metaPage = new Page<>();
        com.alibaba.nacos.ai.model.AiResource meta = new com.alibaba.nacos.ai.model.AiResource();
        meta.setName("test-skill");
        meta.setDesc("Test description");
        meta.setBizTags("[\"ops\"]");
        metaPage.setPageItems(List.of(meta));
        metaPage.setTotalCount(1);
        metaPage.setPageNumber(1);
        metaPage.setPagesAvailable(1);
        when(aiResourcePersistService.list(any(), eq(1), eq(10))).thenReturn(metaPage);
        
        // When
        Page<SkillSummary> result =
            skillOperationService.listSkills(namespaceId, null, null, 1, 10);
        
        // Then
        assertNotNull(result);
        assertEquals(1, result.getPageNumber());
        assertEquals(1, result.getPageItems().size());
        assertEquals("[\"ops\"]", result.getPageItems().get(0).getBizTags());
        assertEquals(VisibilityConstants.SCOPE_PRIVATE, result.getPageItems().get(0).getScope());
    }
    
    @Test
    void testUploadSkillFromZip() throws NacosException, IOException {
        // Given
        String namespaceId = "test-namespace";
        byte[] zipBytes = createValidZipBytes();
        when(aiResourcePersistService.find(eq(namespaceId), anyString(), anyString()))
            .thenReturn(null);
        
        // When
        String result = uploadSkill(namespaceId, zipBytes);
        
        // Then
        assertNotNull(result);
        verify(storage, times(1)).save(any(StorageKey.class), any(byte[].class));
        verify(aiResourceVersionPersistService).insert(argThat(inserted -> inserted != null
            && "test-skill".equals(inserted.getName()) && "3.0.6".equals(inserted.getVersion())));
    }
    
    @Test
    void testUploadSkillFromZipWithCommitMsgCreatesDraftDesc()
        throws NacosException, IOException {
        String namespaceId = "test-namespace";
        byte[] zipBytes = createValidZipBytes();
        when(aiResourcePersistService.find(eq(namespaceId), anyString(), anyString()))
            .thenReturn(null);
        
        String result = uploadSkill(namespaceId, zipBytes, false, null, "initial upload");
        
        assertEquals("test-skill", result);
        verify(aiResourceVersionPersistService).insert(argThat(inserted -> inserted != null
            && "test-skill".equals(inserted.getName()) && "3.0.6".equals(inserted.getVersion())
            && "initial upload".equals(inserted.getDesc())));
    }
    
    @Test
    void testUploadSkillFromZipWithInvalidSkillNameShouldBeRejected() throws IOException {
        String namespaceId = "test-namespace";
        byte[] zipBytes = createZipBytesWithSkillNameAndVersion("Test_Skill", "3.0.6");
        
        NacosApiException exception = assertThrows(NacosApiException.class,
            () -> uploadSkill(namespaceId, zipBytes));
        
        assertEquals(NacosException.INVALID_PARAM, exception.getErrCode());
        assertEquals(
            "Skill name may only contain lowercase letters, numbers, and hyphens, and must not start or end with a hyphen",
            exception.getErrMsg());
        verify(aiResourcePersistService, never()).insert(any());
        verify(aiResourceVersionPersistService, never()).insert(any());
    }
    
    @Test
    void testUploadSkillFromZipWithOverwriteUpdatesExistingDraft()
        throws NacosException, IOException {
        String namespaceId = "test-namespace";
        byte[] zipBytes = createValidZipBytes();
        AiResource meta = new AiResource();
        meta.setNamespaceId(namespaceId);
        meta.setName("test-skill");
        meta.setType("skill");
        meta.setStatus("enable");
        meta.setMetaVersion(1L);
        meta.setVersionInfo("{\"editingVersion\":\"v3\",\"labels\":{},\"onlineCnt\":1}");
        com.alibaba.nacos.ai.model.AiResourceVersion version =
            new com.alibaba.nacos.ai.model.AiResourceVersion();
        version.setVersion("v3");
        version.setStatus("draft");
        when(aiResourcePersistService.find(eq(namespaceId), eq("test-skill"), anyString()))
            .thenReturn(meta);
        when(aiResourceVersionPersistService.find(eq(namespaceId), eq("test-skill"), anyString(),
            eq("v3")))
            .thenReturn(version);
        
        String result = uploadSkill(namespaceId, zipBytes, true);
        
        assertEquals("test-skill", result);
        verify(aiResourceVersionPersistService).updateStorage(eq(namespaceId), eq("test-skill"),
            anyString(),
            eq("v3"), anyString());
        verify(aiResourceVersionPersistService, never()).insert(argThat(inserted -> inserted != null
            && "test-skill".equals(inserted.getName()) && "v3".equals(inserted.getVersion())));
    }
    
    @Test
    void testUploadSkillFromZipWithOverwriteAndCommitMsgUpdatesExistingDraftDesc()
        throws NacosException, IOException {
        String namespaceId = "test-namespace";
        byte[] zipBytes = createValidZipBytes();
        AiResource meta = new AiResource();
        meta.setNamespaceId(namespaceId);
        meta.setName("test-skill");
        meta.setType("skill");
        meta.setStatus("enable");
        meta.setMetaVersion(1L);
        meta.setVersionInfo("{\"editingVersion\":\"v3\",\"labels\":{},\"onlineCnt\":1}");
        com.alibaba.nacos.ai.model.AiResourceVersion version =
            new com.alibaba.nacos.ai.model.AiResourceVersion();
        version.setVersion("v3");
        version.setStatus("draft");
        when(aiResourcePersistService.find(eq(namespaceId), eq("test-skill"), anyString()))
            .thenReturn(meta);
        when(aiResourceVersionPersistService.find(eq(namespaceId), eq("test-skill"), anyString(),
            eq("v3")))
            .thenReturn(version);
        
        String result = uploadSkill(namespaceId, zipBytes, true, null, "refresh draft");
        
        assertEquals("test-skill", result);
        verify(aiResourceVersionPersistService).updateStorageAndDesc(eq(namespaceId),
            eq("test-skill"), anyString(), eq("v3"), anyString(), eq("refresh draft"));
        verify(aiResourceVersionPersistService, never()).updateStorage(eq(namespaceId),
            eq("test-skill"), anyString(), eq("v3"), anyString());
    }
    
    @Test
    void testUploadSkillFromZipWithOverwriteCreatesDraftForExistingSkillWithoutEditing()
        throws NacosException,
        IOException {
        String namespaceId = "test-namespace";
        byte[] zipBytes = createValidZipBytes();
        AiResource meta = new AiResource();
        meta.setNamespaceId(namespaceId);
        meta.setName("test-skill");
        meta.setType("skill");
        meta.setStatus("enable");
        meta.setMetaVersion(2L);
        meta.setVersionInfo("{\"reviewingVersion\":\"v2\",\"labels\":{},\"onlineCnt\":1}");
        Page<com.alibaba.nacos.ai.model.AiResourceVersion> versions = new Page<>();
        com.alibaba.nacos.ai.model.AiResourceVersion v1 =
            new com.alibaba.nacos.ai.model.AiResourceVersion();
        v1.setVersion("v2");
        versions.setPageItems(List.of(v1));
        when(aiResourcePersistService.find(eq(namespaceId), eq("test-skill"), anyString()))
            .thenReturn(meta);
        when(aiResourceVersionPersistService.list(eq(namespaceId), eq("test-skill"), anyString(),
            isNull(), anyInt(), anyInt()))
            .thenReturn(versions);
        when(aiResourcePersistService.updateMetaCas(eq(namespaceId), eq("test-skill"), anyString(),
            eq(2L), any()))
            .thenReturn(true);
        
        String result = uploadSkill(namespaceId, zipBytes, true);
        
        assertEquals("test-skill", result);
        verify(aiResourceVersionPersistService).insert(argThat(inserted -> inserted != null
            && "test-skill".equals(inserted.getName()) && "3.0.6".equals(inserted.getVersion())));
    }
    
    @Test
    void testUploadSkillFromZipShouldSyncMetaDescriptionForExistingSkill()
        throws NacosException, IOException {
        String namespaceId = "test-namespace";
        final byte[] zipBytes = createValidZipBytes();
        AiResource meta = new AiResource();
        meta.setNamespaceId(namespaceId);
        meta.setName("test-skill");
        meta.setType("skill");
        meta.setStatus("enable");
        meta.setDesc("Old description");
        meta.setMetaVersion(2L);
        meta.setVersionInfo("{\"labels\":{\"latest\":\"3.0.5\"},\"onlineCnt\":1}");
        Page<com.alibaba.nacos.ai.model.AiResourceVersion> versions = new Page<>();
        com.alibaba.nacos.ai.model.AiResourceVersion v1 =
            new com.alibaba.nacos.ai.model.AiResourceVersion();
        v1.setVersion("3.0.5");
        versions.setPageItems(List.of(v1));
        when(aiResourcePersistService.find(eq(namespaceId), eq("test-skill"), anyString()))
            .thenReturn(meta);
        when(aiResourceVersionPersistService.list(eq(namespaceId), eq("test-skill"), anyString(),
            isNull(), anyInt(), anyInt()))
            .thenReturn(versions);
        when(aiResourcePersistService.updateMetaCas(eq(namespaceId), eq("test-skill"), anyString(),
            eq(2L), any()))
            .thenReturn(true);
        
        String result = uploadSkill(namespaceId, zipBytes, false);
        
        assertEquals("test-skill", result);
        ArgumentCaptor<AiResource> metaCaptor = ArgumentCaptor.forClass(AiResource.class);
        verify(aiResourcePersistService).updateMetaCas(eq(namespaceId), eq("test-skill"),
            anyString(),
            eq(2L), metaCaptor.capture());
        assertEquals("Test skill description", metaCaptor.getValue().getDesc());
    }
    
    @Test
    void testUploadSkillFromZipUsesMetaJsonVersionWhenMetadataMissing()
        throws NacosException, IOException {
        String namespaceId = "test-namespace";
        byte[] zipBytes = createZipBytesWithoutVersionWithMeta("1.1.3");
        when(aiResourcePersistService.find(eq(namespaceId), anyString(), anyString()))
            .thenReturn(null);
        
        String result = uploadSkill(namespaceId, zipBytes, false);
        
        assertEquals("test-skill", result);
        verify(aiResourceVersionPersistService).insert(argThat(inserted -> inserted != null
            && "1.1.3".equals(inserted.getVersion())));
    }
    
    @Test
    void testUploadSkillFromZipUsesFrontmatterMetadataVersion() throws NacosException, IOException {
        String namespaceId = "test-namespace";
        byte[] zipBytes = createZipBytesWithNestedMetadataVersion("1.0.0");
        when(aiResourcePersistService.find(eq(namespaceId), anyString(), anyString()))
            .thenReturn(null);
        
        String result = uploadSkill(namespaceId, zipBytes, false);
        
        assertEquals("test-skill", result);
        verify(aiResourceVersionPersistService).insert(argThat(inserted -> inserted != null
            && "1.0.0".equals(inserted.getVersion())));
    }
    
    @Test
    void testUploadSkillFromZipRejectsInvalidFrontmatterMetadataVersion() throws IOException {
        String namespaceId = "test-namespace";
        byte[] zipBytes = createZipBytesWithNestedMetadataVersion("latest");
        
        NacosApiException exception = assertThrows(NacosApiException.class,
            () -> uploadSkill(namespaceId, zipBytes, false));
        assertTrue(exception.getErrMsg().contains("SKILL.md frontmatter"),
            "error should identify the frontmatter as the source");
        assertTrue(exception.getErrMsg().contains("latest"),
            "error should include the offending value");
    }
    
    @Test
    void testUploadSkillFromZipRejectsInvalidTargetVersion() throws IOException {
        String namespaceId = "test-namespace";
        byte[] zipBytes = createZipBytesWithoutVersion();
        
        NacosApiException exception = assertThrows(NacosApiException.class,
            () -> uploadSkill(namespaceId, zipBytes, false, "not-a-version"));
        assertTrue(exception.getErrMsg().contains("targetVersion"),
            "error should identify targetVersion as the source");
    }
    
    @Test
    void testUploadSkillFromZipUsesMetadataVersionWhenBothPresent()
        throws NacosException, IOException {
        String namespaceId = "test-namespace";
        byte[] zipBytes = createValidZipBytes();
        when(aiResourcePersistService.find(eq(namespaceId), anyString(), anyString()))
            .thenReturn(null);
        
        String result = uploadSkill(namespaceId, zipBytes, false);
        
        assertEquals("test-skill", result);
        verify(aiResourceVersionPersistService).insert(argThat(inserted -> inserted != null
            && "3.0.6".equals(inserted.getVersion())));
    }
    
    @Test
    void testUploadSkillFromZipUsesDefaultVersionWhenCannotInfer()
        throws NacosException, IOException {
        String namespaceId = "test-namespace";
        byte[] zipBytes = createZipBytesWithoutVersion();
        when(aiResourcePersistService.find(eq(namespaceId), anyString(), anyString()))
            .thenReturn(null);
        
        String result = uploadSkill(namespaceId, zipBytes, false);
        
        assertEquals("test-skill", result);
        verify(aiResourceVersionPersistService).insert(argThat(inserted -> inserted != null
            && "0.0.1".equals(inserted.getVersion())));
    }
    
    @Test
    void testUploadSkillFromZipUsesTargetVersionWhenCannotInfer()
        throws NacosException, IOException {
        String namespaceId = "test-namespace";
        byte[] zipBytes = createZipBytesWithoutVersion();
        when(aiResourcePersistService.find(eq(namespaceId), anyString(), anyString()))
            .thenReturn(null);
        
        String result =
            uploadSkill(namespaceId, zipBytes, false, "2.0.0");
        
        assertEquals("test-skill", result);
        verify(aiResourceVersionPersistService).insert(argThat(inserted -> inserted != null
            && "2.0.0".equals(inserted.getVersion())));
    }
    
    @Test
    void testUploadSkillFromZipBumpsPatchWhenCandidateVersionAlreadyExists()
        throws NacosException, IOException {
        String namespaceId = "test-namespace";
        final byte[] zipBytes = createValidZipBytes(); // metadata version = 3.0.6
        AiResource meta = new AiResource();
        meta.setNamespaceId(namespaceId);
        meta.setName("test-skill");
        meta.setType("skill");
        meta.setStatus("enable");
        meta.setMetaVersion(2L);
        meta.setVersionInfo("{\"labels\":{\"latest\":\"v1\"},\"onlineCnt\":1}");
        Page<com.alibaba.nacos.ai.model.AiResourceVersion> versions = new Page<>();
        com.alibaba.nacos.ai.model.AiResourceVersion v1 =
            new com.alibaba.nacos.ai.model.AiResourceVersion();
        v1.setVersion("3.0.6");
        com.alibaba.nacos.ai.model.AiResourceVersion v2 =
            new com.alibaba.nacos.ai.model.AiResourceVersion();
        v2.setVersion("3.0.7");
        versions.setPageItems(List.of(v1, v2));
        when(aiResourcePersistService.find(eq(namespaceId), eq("test-skill"), anyString()))
            .thenReturn(meta);
        when(aiResourceVersionPersistService.list(eq(namespaceId), eq("test-skill"), anyString(),
            isNull(), anyInt(), anyInt()))
            .thenReturn(versions);
        when(aiResourcePersistService.updateMetaCas(eq(namespaceId), eq("test-skill"), anyString(),
            eq(2L), any()))
            .thenReturn(true);
        
        String result = uploadSkill(namespaceId, zipBytes, false);
        
        assertEquals("test-skill", result);
        verify(aiResourceVersionPersistService).insert(argThat(inserted -> inserted != null
            && "3.0.8".equals(inserted.getVersion())));
    }
    
    @Test
    void testUploadSkillFromZipBumpsPatchWhenCandidateNotGreaterThanMax()
        throws NacosException, IOException {
        String namespaceId = "test-namespace";
        byte[] zipBytes = createZipBytes("3.0.5");
        AiResource meta = new AiResource();
        meta.setNamespaceId(namespaceId);
        meta.setName("test-skill");
        meta.setType("skill");
        meta.setStatus("enable");
        meta.setMetaVersion(2L);
        meta.setVersionInfo("{\"labels\":{\"latest\":\"v1\"},\"onlineCnt\":1}");
        Page<com.alibaba.nacos.ai.model.AiResourceVersion> versions = new Page<>();
        com.alibaba.nacos.ai.model.AiResourceVersion v1 =
            new com.alibaba.nacos.ai.model.AiResourceVersion();
        v1.setVersion("3.0.7");
        versions.setPageItems(List.of(v1));
        when(aiResourcePersistService.find(eq(namespaceId), eq("test-skill"), anyString()))
            .thenReturn(meta);
        when(aiResourceVersionPersistService.list(eq(namespaceId), eq("test-skill"), anyString(),
            isNull(), anyInt(), anyInt()))
            .thenReturn(versions);
        when(aiResourcePersistService.updateMetaCas(eq(namespaceId), eq("test-skill"), anyString(),
            eq(2L), any()))
            .thenReturn(true);
        
        String result = uploadSkill(namespaceId, zipBytes, false);
        
        assertEquals("test-skill", result);
        verify(aiResourceVersionPersistService).insert(argThat(inserted -> inserted != null
            && "3.0.5".equals(inserted.getVersion())));
    }
    
    @Test
    void testUploadSkillFromZipKeepsLowerVersionWhenNotExisting()
        throws NacosException, IOException {
        String namespaceId = "test-namespace";
        byte[] zipBytes = createZipBytes("3.0.6");
        AiResource meta = new AiResource();
        meta.setNamespaceId(namespaceId);
        meta.setName("test-skill");
        meta.setType("skill");
        meta.setStatus("enable");
        meta.setMetaVersion(2L);
        meta.setVersionInfo("{\"labels\":{\"latest\":\"v1\"},\"onlineCnt\":1}");
        Page<com.alibaba.nacos.ai.model.AiResourceVersion> versions = new Page<>();
        com.alibaba.nacos.ai.model.AiResourceVersion v1 =
            new com.alibaba.nacos.ai.model.AiResourceVersion();
        v1.setVersion("3.0.7");
        versions.setPageItems(List.of(v1));
        when(aiResourcePersistService.find(eq(namespaceId), eq("test-skill"), anyString()))
            .thenReturn(meta);
        when(aiResourceVersionPersistService.list(eq(namespaceId), eq("test-skill"), anyString(),
            isNull(), anyInt(), anyInt()))
            .thenReturn(versions);
        when(aiResourcePersistService.updateMetaCas(eq(namespaceId), eq("test-skill"), anyString(),
            eq(2L), any()))
            .thenReturn(true);
        
        String result = uploadSkill(namespaceId, zipBytes, false);
        
        assertEquals("test-skill", result);
        verify(aiResourceVersionPersistService).insert(argThat(inserted -> inserted != null
            && "3.0.6".equals(inserted.getVersion())));
    }
    
    @Test
    void testBootstrapSkillFromZipUsesMetadataVersion() throws NacosException, IOException {
        String namespaceId = "test-namespace";
        byte[] zipBytes = createValidZipBytes(); // metadata version = 3.0.6
        when(aiResourcePersistService.find(eq(namespaceId), eq("test-skill"), anyString()))
            .thenReturn(null);
        
        skillOperationService.bootstrapSkillFromZip(namespaceId, zipBytes);
        
        verify(aiResourceVersionPersistService).insert(argThat(inserted -> inserted != null
            && "test-skill".equals(inserted.getName()) && "3.0.6".equals(inserted.getVersion())
            && "online".equals(inserted.getStatus())));
    }
    
    /**
     * Create a valid skill for testing.
     */
    private Skill createValidSkill() {
        Skill skill = new Skill();
        skill.setName("test-skill");
        skill.setDescription("Test description");
        skill.setSkillMd(
            "---\nname: test-skill\ndescription: Test description\n---\n\nTest instruction");
        return skill;
    }
    
    /**
     * Create a skill with resources for testing.
     */
    private Skill createValidSkillWithResources() {
        Skill skill = createValidSkill();
        Map<String, SkillResource> resources = new HashMap<>();
        SkillResource resource = new SkillResource();
        resource.setName("test.sh");
        resource.setType("script");
        resource.setContent("#!/bin/bash");
        resources.put("test", resource);
        skill.setResource(resources);
        return skill;
    }
    
    private String createMainConfigJson(String skillName) {
        Map<String, Object> mainConfig = new HashMap<>();
        mainConfig.put("name", skillName);
        mainConfig.put("description", "Test description");
        mainConfig.put("instruction", "Test instruction");
        mainConfig.put("resources", List.of());
        return JacksonUtils.toJson(mainConfig);
    }
    
    /**
     * Create valid zip bytes for testing.
     */
    private byte[] createValidZipBytes() throws IOException {
        return createZipBytes("3.0.6");
    }
    
    private byte[] createZipBytesWithoutVersion() throws IOException {
        return createZipBytes(null);
    }
    
    private byte[] createZipBytesWithoutVersionWithMeta(String metaVersion) throws IOException {
        return createZipBytesWithMeta(null, metaVersion);
    }
    
    private byte[] createZipBytes(String version) throws IOException {
        return createZipBytesWithMeta(version, null);
    }
    
    private byte[] createZipBytesWithSkillNameAndVersion(String skillName, String version)
        throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            ZipEntry entry = new ZipEntry("SKILL.md");
            zos.putNextEntry(entry);
            String skillMd = "---\n"
                + "name: " + skillName + "\n"
                + "description: Test skill description\n"
                + (version == null ? "" : "version: " + version + "\n")
                + "---\n\n"
                + "This is a test instruction";
            zos.write(skillMd.getBytes());
            zos.closeEntry();
        }
        return baos.toByteArray();
    }
    
    private byte[] createZipBytesWithMeta(String version, String metaVersion) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            // Add SKILL.md
            ZipEntry entry = new ZipEntry("SKILL.md");
            zos.putNextEntry(entry);
            String skillMd = "---\n"
                + "name: test-skill\n"
                + "description: Test skill description\n"
                + (version == null ? "" : "version: " + version + "\n")
                + "---\n\n"
                + "This is a test instruction";
            zos.write(skillMd.getBytes());
            zos.closeEntry();
            if (metaVersion != null) {
                ZipEntry metaEntry = new ZipEntry("_meta.json");
                zos.putNextEntry(metaEntry);
                String metaJson = "{\n"
                    + "  \"ownerId\": \"kn7akgt520t01vgs2tzx7yk6m180kt26\",\n"
                    + "  \"slug\": \"baidu-search\",\n"
                    + "  \"version\": \"" + metaVersion + "\",\n"
                    + "  \"publishedAt\": 1773828934466\n"
                    + "}";
                zos.write(metaJson.getBytes());
                zos.closeEntry();
            }
        }
        return baos.toByteArray();
    }
    
    private byte[] createZipBytesWithNestedMetadataVersion(String metadataVersion)
        throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            ZipEntry entry = new ZipEntry("SKILL.md");
            zos.putNextEntry(entry);
            String skillMd = "---\n"
                + "name: test-skill\n"
                + "description: Test skill description\n"
                + "metadata:\n"
                + "  author: example-org\n"
                + "  version: \"" + metadataVersion + "\"\n"
                + "---\n\n"
                + "This is a test instruction";
            zos.write(skillMd.getBytes());
            zos.closeEntry();
        }
        return baos.toByteArray();
    }
    
    // ========== Data filter integration tests ==========
    
    private void setupRequestContext(String username) {
        RequestContext requestContext = RequestContextHolder.getContext();
        AuthContext authContext = requestContext.getAuthContext();
        IdentityContext identityContext = new IdentityContext();
        identityContext.setParameter(Constants.Identity.IDENTITY_ID, username);
        authContext.setIdentityContext(identityContext);
        authContext.setApiType("ADMIN_API");
    }
    
    @Test
    void testListSkillsFilteredByReadFilter() throws NacosException {
        String namespaceId = "test-ns";
        AiResource meta1 = new AiResource();
        meta1.setName("skill-public");
        meta1.setNamespaceId(namespaceId);
        meta1.setType("skill");
        meta1.setScope(VisibilityConstants.SCOPE_PUBLIC);
        meta1.setOwner("userA");
        AiResource meta2 = new AiResource();
        meta2.setName("skill-private");
        meta2.setNamespaceId(namespaceId);
        meta2.setType("skill");
        meta2.setScope(VisibilityConstants.SCOPE_PRIVATE);
        meta2.setOwner("userA");
        
        QueryAdvisor advisor = new QueryAdvisor();
        advisor.setBasePredicate(BaseVisibilityPredicate.PUBLIC);
        VisibilityService mockFilter = mock(VisibilityService.class);
        lenient().when(mockFilter.adviseQuery(anyString(), eq(VisibilityConstants.ACTION_READ),
            anyString(), any())).thenReturn(
                advisor);
        lenient().when(mockVisibilityManager.findVisibilityService(anyString()))
            .thenReturn(Optional.of(mockFilter));
        setupRequestContext("userB");
        
        Page<AiResource> metaPage = new Page<>();
        metaPage.setPageItems(List.of(meta1));
        metaPage.setTotalCount(1);
        metaPage.setPagesAvailable(1);
        when(aiResourcePersistService.list(any(), eq(1), eq(10))).thenReturn(metaPage);
        
        Page<SkillSummary> result =
            skillOperationService.listSkills(namespaceId, null, null, 1, 10);
        assertEquals(1, result.getPageItems().size());
        assertEquals("skill-public", result.getPageItems().get(0).getName());
        assertEquals(1, result.getTotalCount());
    }
    
    @Test
    void testGetSkillDetailDeniedByReadFilter() {
        String namespaceId = "test-ns";
        String skillName = "private-skill";
        AiResource meta = new AiResource();
        meta.setName(skillName);
        meta.setType("skill");
        meta.setNamespaceId(namespaceId);
        meta.setScope(VisibilityConstants.SCOPE_PRIVATE);
        meta.setOwner("ownerUser");
        when(aiResourcePersistService.find(eq(namespaceId), eq(skillName), anyString()))
            .thenReturn(meta);
        
        VisibilityService mockFilter = mock(VisibilityService.class);
        when(mockFilter.validateVisibility(anyString(), eq(VisibilityConstants.ACTION_READ),
            anyString(), any()))
            .thenReturn(ValidationResult.deny("denied"));
        when(mockVisibilityManager.findVisibilityService(anyString()))
            .thenReturn(Optional.of(mockFilter));
        
        setupRequestContext("otherUser");
        NacosApiException ex = assertThrows(NacosApiException.class,
            () -> skillOperationService.getSkillDetail(namespaceId, skillName));
        assertEquals(NacosException.NOT_FOUND, ex.getErrCode());
    }
    
    @Test
    void testQuerySkillDeniedByReadFilterShouldReturnNotFound() {
        String namespaceId = "test-ns";
        String skillName = "private-skill";
        AiResource meta = new AiResource();
        meta.setName(skillName);
        meta.setType("skill");
        meta.setNamespaceId(namespaceId);
        meta.setScope(VisibilityConstants.SCOPE_PRIVATE);
        meta.setOwner("ownerUser");
        when(aiResourcePersistService.find(eq(namespaceId), eq(skillName), anyString()))
            .thenReturn(meta);
        
        VisibilityService mockFilter = mock(VisibilityService.class);
        when(mockFilter.validateVisibility(anyString(), eq(VisibilityConstants.ACTION_READ),
            anyString(), any()))
            .thenReturn(ValidationResult.deny("denied"));
        when(mockVisibilityManager.findVisibilityService(anyString()))
            .thenReturn(Optional.of(mockFilter));
        
        setupRequestContext("otherUser");
        NacosApiException ex = assertThrows(NacosApiException.class,
            () -> skillOperationService.querySkill(namespaceId, skillName, null, null));
        assertEquals(NacosException.NOT_FOUND, ex.getErrCode());
        verify(manifestService, never()).query(namespaceId, skillName);
    }
    
    @Test
    void testGetSkillVersionDetailDeniedByReadFilterShouldReturnNotFound() {
        String namespaceId = "test-ns";
        String skillName = "private-skill";
        String version = "v1";
        AiResource meta = new AiResource();
        meta.setName(skillName);
        meta.setType("skill");
        meta.setNamespaceId(namespaceId);
        meta.setScope(VisibilityConstants.SCOPE_PRIVATE);
        meta.setOwner("ownerUser");
        when(aiResourcePersistService.find(eq(namespaceId), eq(skillName), anyString()))
            .thenReturn(meta);
        
        VisibilityService mockFilter = mock(VisibilityService.class);
        when(mockFilter.validateVisibility(anyString(), eq(VisibilityConstants.ACTION_READ),
            anyString(), any()))
            .thenReturn(ValidationResult.deny("denied"));
        when(mockVisibilityManager.findVisibilityService(anyString()))
            .thenReturn(Optional.of(mockFilter));
        
        setupRequestContext("otherUser");
        NacosApiException ex = assertThrows(NacosApiException.class,
            () -> skillOperationService.getSkillVersionDetail(namespaceId, skillName, version));
        assertEquals(NacosException.NOT_FOUND, ex.getErrCode());
        verify(aiResourceVersionPersistService, never()).find(anyString(), anyString(), anyString(),
            anyString());
    }
    
    @Test
    void testDeleteSkillDeniedByWriteFilter() {
        String namespaceId = "test-ns";
        String skillName = "protected-skill";
        AiResource meta = new AiResource();
        meta.setName(skillName);
        meta.setType("skill");
        meta.setNamespaceId(namespaceId);
        meta.setScope(VisibilityConstants.SCOPE_PRIVATE);
        meta.setOwner("ownerUser");
        when(aiResourcePersistService.find(eq(namespaceId), eq(skillName), anyString()))
            .thenReturn(meta);
        
        VisibilityService mockFilter = mock(VisibilityService.class);
        when(mockFilter.validateVisibility(anyString(), eq(VisibilityConstants.ACTION_WRITE),
            anyString(), any()))
            .thenReturn(ValidationResult.deny("denied"));
        when(mockVisibilityManager.findVisibilityService(anyString()))
            .thenReturn(Optional.of(mockFilter));
        
        setupRequestContext("attackerUser");
        NacosApiException ex = assertThrows(NacosApiException.class,
            () -> skillOperationService.deleteSkill(namespaceId, skillName));
        assertEquals(NacosException.NO_RIGHT, ex.getErrCode());
        verify(aiResourcePersistService, never()).delete(anyString(), anyString(), anyString());
    }
    
    @Test
    void testUploadSkillSetsOwnerOnCreation() throws NacosException, IOException {
        String namespaceId = "test-ns";
        byte[] zipBytes = createValidZipBytes();
        when(aiResourcePersistService.find(eq(namespaceId), anyString(), anyString()))
            .thenReturn(null);
        setupRequestContext("creatorUser");
        
        uploadSkill(namespaceId, zipBytes);
        
        org.mockito.ArgumentCaptor<AiResource> captor =
            org.mockito.ArgumentCaptor.forClass(AiResource.class);
        verify(aiResourcePersistService).insert(captor.capture());
        assertEquals("creatorUser", captor.getValue().getOwner());
    }
    
    @Test
    void testListSkillsNoFilterServiceAvailable() throws NacosException {
        String namespaceId = "test-ns";
        AiResource meta = new AiResource();
        meta.setName("my-skill");
        meta.setType("skill");
        meta.setNamespaceId(namespaceId);
        Page<AiResource> metaPage = new Page<>();
        metaPage.setPageItems(List.of(meta));
        metaPage.setTotalCount(1);
        metaPage.setPagesAvailable(1);
        when(aiResourcePersistService.list(any(), eq(1), eq(10))).thenReturn(metaPage);
        
        Page<SkillSummary> result =
            skillOperationService.listSkills(namespaceId, null, null, 1, 10);
        assertEquals(1, result.getPageItems().size());
    }
    
    @Test
    void testCreateDraftOnExistingSkillDeniedByWriteFilter() {
        String namespaceId = "test-ns";
        String skillName = "protected-skill";
        AiResource meta = new AiResource();
        meta.setName(skillName);
        meta.setType("skill");
        meta.setNamespaceId(namespaceId);
        meta.setScope(VisibilityConstants.SCOPE_PRIVATE);
        meta.setOwner("ownerUser");
        meta.setVersionInfo("{\"labels\":{},\"onlineCnt\":0}");
        meta.setMetaVersion(1L);
        when(aiResourcePersistService.find(eq(namespaceId), eq(skillName), anyString()))
            .thenReturn(meta);
        
        VisibilityService mockFilter = mock(VisibilityService.class);
        when(mockFilter.validateVisibility(anyString(), eq(VisibilityConstants.ACTION_WRITE),
            anyString(), any()))
            .thenReturn(ValidationResult.deny("denied"));
        when(mockVisibilityManager.findVisibilityService(anyString()))
            .thenReturn(Optional.of(mockFilter));
        
        setupRequestContext("attackerUser");
        NacosApiException ex = assertThrows(NacosApiException.class,
            () -> skillOperationService.createDraft(namespaceId, skillName, null, null, null,
                null));
        assertEquals(NacosException.NO_RIGHT, ex.getErrCode());
    }
    
    @Test
    void testCreateDraftAuthorIsCurrentUser() throws NacosException {
        String namespaceId = "test-ns";
        String skillName = "brand-new-skill";
        when(aiResourcePersistService.find(eq(namespaceId), eq(skillName), anyString()))
            .thenReturn(null);
        setupRequestContext("myUser");
        
        Skill initial = new Skill();
        initial.setName(skillName);
        initial.setDescription("desc");
        initial.setSkillMd("---\nname: " + skillName + "\ndescription: desc\n---\n\ninst");
        initial.setNamespaceId(namespaceId);
        String version =
            skillOperationService.createDraft(namespaceId, skillName, null, null, initial, null);
        assertEquals("0.0.1", version);
        
        org.mockito.ArgumentCaptor<com.alibaba.nacos.ai.model.AiResourceVersion> vCaptor =
            org.mockito.ArgumentCaptor.forClass(com.alibaba.nacos.ai.model.AiResourceVersion.class);
        verify(aiResourceVersionPersistService).insert(vCaptor.capture());
        assertEquals("myUser", vCaptor.getValue().getAuthor());
        assertEquals("", vCaptor.getValue().getDesc());
    }
    
    @Test
    void testCreateDraftUsesMetadataVersionForBrandNewSkill() throws NacosException {
        String namespaceId = "test-ns";
        String skillName = "brand-new-skill";
        when(aiResourcePersistService.find(eq(namespaceId), eq(skillName), anyString()))
            .thenReturn(null);
        
        Skill initial = new Skill();
        initial.setName(skillName);
        initial.setDescription("desc");
        initial.setSkillMd(
            "---\nname: " + skillName + "\ndescription: desc\nversion: 2.1.3\n---\n\ninst");
        initial.setNamespaceId(namespaceId);
        
        String version =
            skillOperationService.createDraft(namespaceId, skillName, null, null, initial, null);
        
        assertEquals("2.1.3", version);
        verify(aiResourceVersionPersistService)
            .insert(argThat(v -> v != null && "2.1.3".equals(v.getVersion())
                && "".equals(v.getDesc())));
    }
    
    @Test
    void testCreateDraftDefaultsToMaxPatchIncrement() throws NacosException {
        String namespaceId = "test-ns";
        String skillName = "my-skill";
        AiResource meta = new AiResource();
        meta.setName(skillName);
        meta.setType("skill");
        meta.setNamespaceId(namespaceId);
        meta.setScope(VisibilityConstants.SCOPE_PUBLIC);
        meta.setOwner("ownerUser");
        meta.setMetaVersion(2L);
        meta.setVersionInfo("{\"labels\":{\"latest\":\"1.1.3\"},\"onlineCnt\":1}");
        when(aiResourcePersistService.find(eq(namespaceId), eq(skillName), anyString()))
            .thenReturn(meta);
        
        Page<com.alibaba.nacos.ai.model.AiResourceVersion> versions = new Page<>();
        com.alibaba.nacos.ai.model.AiResourceVersion v =
            new com.alibaba.nacos.ai.model.AiResourceVersion();
        v.setVersion("1.1.3");
        com.alibaba.nacos.ai.model.AiResourceVersion v2 =
            new com.alibaba.nacos.ai.model.AiResourceVersion();
        v2.setVersion("1.2.0");
        versions.setPageItems(List.of(v, v2));
        when(aiResourceVersionPersistService.list(eq(namespaceId), eq(skillName), anyString(),
            isNull(), anyInt(), anyInt()))
            .thenReturn(versions);
        
        com.alibaba.nacos.ai.model.AiResourceVersion baseVersion =
            new com.alibaba.nacos.ai.model.AiResourceVersion();
        baseVersion.setVersion("1.1.3");
        baseVersion.setStorage(
            "{\"provider\":\"nacos_config\",\"scope\":\"test-ns:my-skill:1.1.3\",\"files\":[\"SKILL.md\"]}");
        when(aiResourceVersionPersistService.find(eq(namespaceId), eq(skillName), anyString(),
            eq("1.1.3")))
            .thenReturn(baseVersion);
        when(storage.get(any(StorageKey.class))).thenReturn(
            ("---\nname: my-skill\ndescription: Test skill description\nversion: 1.1.3\n---\n\nbody")
                .getBytes());
        when(aiResourcePersistService.updateMetaCas(eq(namespaceId), eq(skillName), eq("skill"),
            eq(2L), any()))
            .thenReturn(true);
        
        String version =
            skillOperationService.createDraft(namespaceId, skillName, null, null, null, null);
        
        assertEquals("1.2.1", version);
        verify(aiResourceVersionPersistService).insert(argThat(inserted -> inserted != null
            && "1.2.1".equals(inserted.getVersion()) && "".equals(inserted.getDesc())));
    }
    
    @Test
    void testCreateDraftWithSpecifiedTargetVersion() throws NacosException {
        String namespaceId = "test-ns";
        String skillName = "my-skill";
        AiResource meta = new AiResource();
        meta.setName(skillName);
        meta.setType("skill");
        meta.setNamespaceId(namespaceId);
        meta.setScope(VisibilityConstants.SCOPE_PUBLIC);
        meta.setOwner("ownerUser");
        meta.setMetaVersion(2L);
        meta.setVersionInfo("{\"labels\":{\"latest\":\"1.1.3\"},\"onlineCnt\":1}");
        when(aiResourcePersistService.find(eq(namespaceId), eq(skillName), anyString()))
            .thenReturn(meta);
        
        Page<com.alibaba.nacos.ai.model.AiResourceVersion> versions = new Page<>();
        com.alibaba.nacos.ai.model.AiResourceVersion v =
            new com.alibaba.nacos.ai.model.AiResourceVersion();
        v.setVersion("1.1.3");
        versions.setPageItems(List.of(v));
        when(aiResourceVersionPersistService.list(eq(namespaceId), eq(skillName), anyString(),
            isNull(), anyInt(), anyInt()))
            .thenReturn(versions);
        
        com.alibaba.nacos.ai.model.AiResourceVersion baseVersion =
            new com.alibaba.nacos.ai.model.AiResourceVersion();
        baseVersion.setVersion("1.1.3");
        baseVersion.setStorage(
            "{\"provider\":\"nacos_config\",\"scope\":\"test-ns:my-skill:1.1.3\",\"files\":[\"SKILL.md\"]}");
        when(aiResourceVersionPersistService.find(eq(namespaceId), eq(skillName), anyString(),
            eq("1.1.3")))
            .thenReturn(baseVersion);
        when(storage.get(any(StorageKey.class))).thenReturn(
            ("---\nname: my-skill\ndescription: Test skill description\nversion: 1.1.3\n---\n\nbody")
                .getBytes());
        when(aiResourcePersistService.updateMetaCas(eq(namespaceId), eq(skillName), eq("skill"),
            eq(2L), any()))
            .thenReturn(true);
        
        String version =
            skillOperationService.createDraft(namespaceId, skillName, "1.1.3", "1.1.4", null, null);
        
        assertEquals("1.1.4", version);
        verify(aiResourceVersionPersistService).insert(argThat(inserted -> inserted != null
            && "1.1.4".equals(inserted.getVersion()) && "".equals(inserted.getDesc())));
    }
    
    @Test
    void testCreateDraftWithDuplicateTargetVersionThrowsConflict() throws NacosException {
        String namespaceId = "test-ns";
        String skillName = "my-skill";
        AiResource meta = new AiResource();
        meta.setName(skillName);
        meta.setType("skill");
        meta.setNamespaceId(namespaceId);
        meta.setScope(VisibilityConstants.SCOPE_PUBLIC);
        meta.setOwner("ownerUser");
        meta.setMetaVersion(2L);
        meta.setVersionInfo("{\"labels\":{\"latest\":\"1.1.3\"},\"onlineCnt\":1}");
        when(aiResourcePersistService.find(eq(namespaceId), eq(skillName), anyString()))
            .thenReturn(meta);
        
        Page<com.alibaba.nacos.ai.model.AiResourceVersion> versions = new Page<>();
        com.alibaba.nacos.ai.model.AiResourceVersion v1 =
            new com.alibaba.nacos.ai.model.AiResourceVersion();
        v1.setVersion("1.1.3");
        com.alibaba.nacos.ai.model.AiResourceVersion v2 =
            new com.alibaba.nacos.ai.model.AiResourceVersion();
        v2.setVersion("1.1.4");
        versions.setPageItems(List.of(v1, v2));
        when(aiResourceVersionPersistService.list(eq(namespaceId), eq(skillName), anyString(),
            isNull(), anyInt(), anyInt()))
            .thenReturn(versions);
        
        NacosApiException ex = assertThrows(NacosApiException.class,
            () -> skillOperationService.createDraft(namespaceId, skillName, null, "1.1.4", null,
                null));
        assertEquals(NacosException.CONFLICT, ex.getErrCode());
    }
    
    @Test
    void testCreateDraftWithTargetVersionNotGreaterThanBaseThrowsInvalidParam()
        throws NacosException {
        String namespaceId = "test-ns";
        String skillName = "my-skill";
        AiResource meta = new AiResource();
        meta.setName(skillName);
        meta.setType("skill");
        meta.setNamespaceId(namespaceId);
        meta.setScope(VisibilityConstants.SCOPE_PUBLIC);
        meta.setOwner("ownerUser");
        meta.setMetaVersion(2L);
        meta.setVersionInfo("{\"labels\":{\"latest\":\"1.1.3\"},\"onlineCnt\":1}");
        when(aiResourcePersistService.find(eq(namespaceId), eq(skillName), anyString()))
            .thenReturn(meta);
        
        Page<com.alibaba.nacos.ai.model.AiResourceVersion> versions = new Page<>();
        com.alibaba.nacos.ai.model.AiResourceVersion v1 =
            new com.alibaba.nacos.ai.model.AiResourceVersion();
        v1.setVersion("1.1.3");
        versions.setPageItems(List.of(v1));
        when(aiResourceVersionPersistService.list(eq(namespaceId), eq(skillName), anyString(),
            isNull(), anyInt(), anyInt()))
            .thenReturn(versions);
        
        NacosApiException ex = assertThrows(NacosApiException.class,
            () -> skillOperationService.createDraft(namespaceId, skillName, "1.1.3", "1.1.2", null,
                null));
        assertEquals(NacosException.INVALID_PARAM, ex.getErrCode());
    }
    
    @Test
    void testUpdateScopeSuccess() throws NacosException {
        String namespaceId = "test-ns";
        String skillName = "my-skill";
        AiResource meta = new AiResource();
        meta.setName(skillName);
        meta.setType("skill");
        meta.setNamespaceId(namespaceId);
        meta.setScope(VisibilityConstants.SCOPE_PRIVATE);
        meta.setOwner("ownerUser");
        when(aiResourcePersistService.find(eq(namespaceId), eq(skillName), anyString()))
            .thenReturn(meta);
        when(aiResourcePersistService.updateScope(eq(namespaceId), eq(skillName), eq("skill"),
            eq("PUBLIC")))
            .thenReturn(true);
        
        skillOperationService.updateScope(namespaceId, skillName, "PUBLIC");
        verify(aiResourcePersistService).updateScope(namespaceId, skillName, "skill", "PUBLIC");
    }
    
    @Test
    void testUpdateScopeDeniedByWriteFilter() {
        String namespaceId = "test-ns";
        String skillName = "protected-skill";
        AiResource meta = new AiResource();
        meta.setName(skillName);
        meta.setType("skill");
        meta.setNamespaceId(namespaceId);
        meta.setScope(VisibilityConstants.SCOPE_PRIVATE);
        meta.setOwner("ownerUser");
        when(aiResourcePersistService.find(eq(namespaceId), eq(skillName), anyString()))
            .thenReturn(meta);
        
        VisibilityService mockFilter = mock(VisibilityService.class);
        when(mockFilter.validateVisibility(anyString(), eq(VisibilityConstants.ACTION_WRITE),
            anyString(), any()))
            .thenReturn(ValidationResult.deny("denied"));
        when(mockVisibilityManager.findVisibilityService(anyString()))
            .thenReturn(Optional.of(mockFilter));
        
        setupRequestContext("attackerUser");
        NacosApiException ex = assertThrows(NacosApiException.class,
            () -> skillOperationService.updateScope(namespaceId, skillName, "PUBLIC"));
        assertEquals(NacosException.NO_RIGHT, ex.getErrCode());
        verify(aiResourcePersistService, never()).updateScope(anyString(), anyString(), anyString(),
            anyString());
    }
    
    @Test
    void testUpdateScopeNotFound() {
        String namespaceId = "test-ns";
        String skillName = "nonexistent";
        when(aiResourcePersistService.find(eq(namespaceId), eq(skillName), anyString()))
            .thenReturn(null);
        
        NacosApiException ex = assertThrows(NacosApiException.class,
            () -> skillOperationService.updateScope(namespaceId, skillName, "PUBLIC"));
        assertEquals(NacosException.NOT_FOUND, ex.getErrCode());
    }
    
    @Test
    void testListSkillsWithBizTagFilter() throws NacosException {
        String namespaceId = "test-namespace";
        Page<AiResource> metaPage = new Page<>();
        AiResource meta = new AiResource();
        meta.setName("tagged-skill");
        meta.setDesc("Skill with tag");
        meta.setBizTags("[\"retail\"]");
        metaPage.setPageItems(List.of(meta));
        metaPage.setTotalCount(1);
        metaPage.setPageNumber(1);
        metaPage.setPagesAvailable(1);
        when(aiResourcePersistService.list(any(), eq(1), eq(10))).thenReturn(metaPage);
        when(aiResourcePersistService.generateLikeArgument(anyString())).thenReturn("%retail%");
        
        Page<SkillSummary> result =
            skillOperationService.listSkills(namespaceId, null, null, null, null, null,
                "retail", 1, 10);
        
        assertNotNull(result);
        assertEquals(1, result.getPageItems().size());
        assertEquals("[\"retail\"]", result.getPageItems().get(0).getBizTags());
        verify(aiResourcePersistService).generateLikeArgument("*retail*");
    }
    
    @Test
    void testListSkillsWithOwnerAndScopeAndBizTagFilters() throws NacosException {
        String namespaceId = "test-namespace";
        Page<AiResource> metaPage = new Page<>();
        AiResource meta = new AiResource();
        meta.setName("filtered-skill");
        meta.setDesc("Filtered skill");
        meta.setBizTags("[\"ops\"]");
        meta.setOwner("alice");
        meta.setScope(VisibilityConstants.SCOPE_PUBLIC);
        metaPage.setPageItems(List.of(meta));
        metaPage.setTotalCount(1);
        metaPage.setPageNumber(1);
        metaPage.setPagesAvailable(1);
        when(aiResourcePersistService.list(any(), eq(1), eq(10))).thenReturn(metaPage);
        when(aiResourcePersistService.generateLikeArgument(anyString())).thenReturn("%ops%");
        
        Page<SkillSummary> result =
            skillOperationService.listSkills(namespaceId, null, null, "name", "alice",
                "PUBLIC", "ops", 1, 10);
        
        assertNotNull(result);
        assertEquals(1, result.getPageItems().size());
        assertEquals("filtered-skill", result.getPageItems().get(0).getName());
    }
    
    @Test
    void testUpdateBizTagsSuccess() throws NacosException {
        String namespaceId = "test-ns";
        String skillName = "my-skill";
        AiResource meta = new AiResource();
        meta.setName(skillName);
        meta.setType("skill");
        meta.setNamespaceId(namespaceId);
        meta.setStatus("enable");
        meta.setDesc("desc");
        meta.setVersionInfo("{\"labels\":{},\"onlineCnt\":1}");
        meta.setMetaVersion(1L);
        meta.setOwner("ownerUser");
        when(aiResourcePersistService.find(eq(namespaceId), eq(skillName), anyString()))
            .thenReturn(meta);
        when(aiResourcePersistService.updateMetaCas(eq(namespaceId), eq(skillName), eq("skill"),
            eq(1L), any()))
            .thenReturn(true);
        
        skillOperationService.updateBizTags(namespaceId, skillName, "[\"retail\"]");
        
        verify(aiResourcePersistService).updateMetaCas(eq(namespaceId), eq(skillName), eq("skill"),
            eq(1L),
            argThat(resource -> resource != null && "[\"retail\"]".equals(resource.getBizTags())));
    }
    
    @Test
    void testForcePublishSuccess() throws NacosException {
        String namespaceId = "test-ns";
        String skillName = "my-skill";
        String version = "v1";
        
        AiResource meta = new AiResource();
        meta.setName(skillName);
        meta.setType("skill");
        meta.setNamespaceId(namespaceId);
        meta.setStatus("enable");
        meta.setMetaVersion(1L);
        meta.setVersionInfo("{\"reviewingVersion\":\"v1\",\"labels\":{},\"onlineCnt\":1}");
        when(aiResourcePersistService.find(eq(namespaceId), eq(skillName), anyString()))
            .thenReturn(meta);
        
        com.alibaba.nacos.ai.model.AiResourceVersion v =
            new com.alibaba.nacos.ai.model.AiResourceVersion();
        v.setVersion(version);
        v.setStatus("reviewed");
        v.setStorage(
            "{\"provider\":\"nacos_config\",\"scope\":\"test-ns:my-skill:v1\",\"files\":[\"SKILL.md\"]}");
        when(aiResourceVersionPersistService.find(eq(namespaceId), eq(skillName), anyString(),
            eq(version)))
            .thenReturn(v);
        when(aiResourcePersistService.updateMetaCas(eq(namespaceId), eq(skillName), eq("skill"),
            eq(1L), any()))
            .thenReturn(true);
        
        com.alibaba.nacos.ai.model.skills.SkillIndexManifest manifest =
            new com.alibaba.nacos.ai.model.skills.SkillIndexManifest();
        manifest.setVersions(new HashMap<>());
        manifest.setLabels(new HashMap<>());
        when(manifestService.loadForUpdate(eq(namespaceId), eq(skillName))).thenReturn(manifest);
        
        skillOperationService.forcePublish(namespaceId, skillName, version, false);
        
        verify(aiResourceVersionPersistService).updateStatus(eq(namespaceId), eq(skillName),
            anyString(),
            eq(version), eq("online"));
        verify(manifestService).write(eq(namespaceId), eq(skillName), argThat(
            written -> version.equals(written.getLabels().get(AiResourceConstants.LABEL_LATEST))));
    }
    
    @Test
    void testForcePublishVersionNotFound() {
        String namespaceId = "test-ns";
        String skillName = "my-skill";
        String version = "v99";
        
        AiResource meta = new AiResource();
        meta.setName(skillName);
        meta.setType("skill");
        meta.setNamespaceId(namespaceId);
        meta.setStatus("enable");
        meta.setVersionInfo("{\"labels\":{},\"onlineCnt\":1}");
        when(aiResourcePersistService.find(eq(namespaceId), eq(skillName), anyString()))
            .thenReturn(meta);
        when(aiResourceVersionPersistService.find(eq(namespaceId), eq(skillName), anyString(),
            eq(version)))
            .thenReturn(null);
        
        NacosApiException ex = assertThrows(NacosApiException.class,
            () -> skillOperationService.forcePublish(namespaceId, skillName, version, true));
        assertEquals(NacosException.NOT_FOUND, ex.getErrCode());
    }
    
    @Test
    void testForcePublishVersionAlreadyOnline() {
        String namespaceId = "test-ns";
        String skillName = "my-skill";
        String version = "v1";
        
        AiResource meta = new AiResource();
        meta.setName(skillName);
        meta.setType("skill");
        meta.setNamespaceId(namespaceId);
        meta.setStatus("enable");
        meta.setVersionInfo("{\"labels\":{},\"onlineCnt\":1}");
        when(aiResourcePersistService.find(eq(namespaceId), eq(skillName), anyString()))
            .thenReturn(meta);
        
        com.alibaba.nacos.ai.model.AiResourceVersion v =
            new com.alibaba.nacos.ai.model.AiResourceVersion();
        v.setVersion(version);
        v.setStatus("online");
        when(aiResourceVersionPersistService.find(eq(namespaceId), eq(skillName), anyString(),
            eq(version)))
            .thenReturn(v);
        
        NacosApiException ex = assertThrows(NacosApiException.class,
            () -> skillOperationService.forcePublish(namespaceId, skillName, version, true));
        assertEquals(NacosException.INVALID_PARAM, ex.getErrCode());
    }
    
    @Test
    void testPublishShouldBeIdempotentWhenVersionAlreadyOnline() throws NacosException {
        String namespaceId = "test-ns";
        String skillName = "my-skill";
        String version = "v1";
        
        AiResource meta = new AiResource();
        meta.setName(skillName);
        meta.setType("skill");
        meta.setNamespaceId(namespaceId);
        meta.setStatus("enable");
        meta.setMetaVersion(1L);
        meta.setVersionInfo("{\"reviewingVersion\":\"v1\",\"labels\":{},\"onlineCnt\":2}");
        when(aiResourcePersistService.find(eq(namespaceId), eq(skillName), anyString()))
            .thenReturn(meta);
        when(aiResourcePersistService.updateMetaCas(eq(namespaceId), eq(skillName), eq("skill"),
            eq(1L), any()))
            .thenReturn(true);
        
        com.alibaba.nacos.ai.model.AiResourceVersion v =
            new com.alibaba.nacos.ai.model.AiResourceVersion();
        v.setVersion(version);
        v.setStatus("online");
        v.setStorage(
            "{\"provider\":\"nacos_config\",\"scope\":\"test-ns:my-skill:v1\",\"files\":[\"SKILL.md\"]}");
        when(aiResourceVersionPersistService.find(eq(namespaceId), eq(skillName), anyString(),
            eq(version)))
            .thenReturn(v);
        
        com.alibaba.nacos.ai.model.skills.SkillIndexManifest manifest =
            new com.alibaba.nacos.ai.model.skills.SkillIndexManifest();
        manifest.setVersions(new HashMap<>());
        manifest.setLabels(new HashMap<>());
        when(manifestService.loadForUpdate(eq(namespaceId), eq(skillName))).thenReturn(manifest);
        
        skillOperationService.publish(namespaceId, skillName, version, true);
        
        // Should NOT call updateStatus since already online
        verify(aiResourceVersionPersistService, never()).updateStatus(anyString(), anyString(),
            anyString(),
            anyString(), anyString());
        // onlineCnt should remain 2 (not incremented)
        verify(aiResourcePersistService).updateMetaCas(eq(namespaceId), eq(skillName), eq("skill"),
            eq(1L),
            argThat(resource -> {
                Map<?, ?> info = JacksonUtils.toObj(resource.getVersionInfo(), Map.class);
                return ((Number) info.get("onlineCnt")).intValue() == 2;
            }));
    }
    
    @Test
    void testChangeOnlineStatusShouldSkipWhenVersionAlreadyOnline() throws NacosException {
        String namespaceId = "test-ns";
        String skillName = "my-skill";
        String version = "v1";
        
        AiResource meta = new AiResource();
        meta.setName(skillName);
        meta.setType("skill");
        meta.setNamespaceId(namespaceId);
        meta.setStatus("enable");
        meta.setMetaVersion(1L);
        meta.setVersionInfo("{\"labels\":{},\"onlineCnt\":1}");
        when(aiResourcePersistService.find(eq(namespaceId), eq(skillName), anyString()))
            .thenReturn(meta);
        
        com.alibaba.nacos.ai.model.AiResourceVersion v =
            new com.alibaba.nacos.ai.model.AiResourceVersion();
        v.setVersion(version);
        v.setStatus("online");
        when(aiResourceVersionPersistService.find(eq(namespaceId), eq(skillName), anyString(),
            eq(version)))
            .thenReturn(v);
        
        skillOperationService.changeOnlineStatus(namespaceId, skillName, "version", version, true);
        
        // Should NOT call updateStatus or updateMetaCas since already in target status
        verify(aiResourceVersionPersistService, never()).updateStatus(anyString(), anyString(),
            anyString(),
            anyString(), anyString());
        verify(aiResourcePersistService, never()).updateMetaCas(anyString(), anyString(),
            anyString(), anyLong(), any());
    }
    
    @Test
    void testChangeOnlineStatusShouldSkipWhenVersionAlreadyOffline() throws NacosException {
        String namespaceId = "test-ns";
        String skillName = "my-skill";
        String version = "v1";
        
        AiResource meta = new AiResource();
        meta.setName(skillName);
        meta.setType("skill");
        meta.setNamespaceId(namespaceId);
        meta.setStatus("enable");
        meta.setMetaVersion(1L);
        meta.setVersionInfo("{\"labels\":{},\"onlineCnt\":0}");
        when(aiResourcePersistService.find(eq(namespaceId), eq(skillName), anyString()))
            .thenReturn(meta);
        
        com.alibaba.nacos.ai.model.AiResourceVersion v =
            new com.alibaba.nacos.ai.model.AiResourceVersion();
        v.setVersion(version);
        v.setStatus("offline");
        when(aiResourceVersionPersistService.find(eq(namespaceId), eq(skillName), anyString(),
            eq(version)))
            .thenReturn(v);
        
        skillOperationService.changeOnlineStatus(namespaceId, skillName, "version", version, false);
        
        verify(aiResourceVersionPersistService, never()).updateStatus(anyString(), anyString(),
            anyString(),
            anyString(), anyString());
        verify(aiResourcePersistService, never()).updateMetaCas(anyString(), anyString(),
            anyString(), anyLong(), any());
    }
    
    // ========== Coverage gap tests ==========
    
    @Test
    void testGetSkillVersionDetailSuccess() throws NacosException {
        String namespaceId = "test-ns";
        String skillName = "test-skill";
        String version = "v1";
        AiResource meta = new AiResource();
        meta.setName(skillName);
        meta.setType("skill");
        meta.setStatus("enable");
        meta.setScope(VisibilityConstants.SCOPE_PUBLIC);
        when(aiResourcePersistService.find(eq(namespaceId), eq(skillName), anyString()))
            .thenReturn(meta);
        com.alibaba.nacos.ai.model.AiResourceVersion vRow =
            new com.alibaba.nacos.ai.model.AiResourceVersion();
        vRow.setVersion(version);
        vRow.setStorage(
            "{\"provider\":\"nacos_config\",\"scope\":\"test-ns:test-skill:v1\",\"files\":[\"SKILL.md\"]}");
        when(aiResourceVersionPersistService.find(eq(namespaceId), eq(skillName), anyString(),
            eq(version)))
            .thenReturn(vRow);
        when(storage.get(any(StorageKey.class))).thenReturn(
            ("---\nname: test-skill\ndescription: desc\n---\n\nbody").getBytes());
        Skill result = skillOperationService.getSkillVersionDetail(namespaceId, skillName, version);
        assertNotNull(result);
        assertEquals("test-skill", result.getName());
    }
    
    @Test
    void testGetSkillVersionDetailBlankVersionThrows() {
        String namespaceId = "test-ns";
        String skillName = "test-skill";
        AiResource meta = new AiResource();
        meta.setName(skillName);
        meta.setType("skill");
        meta.setStatus("enable");
        when(aiResourcePersistService.find(eq(namespaceId), eq(skillName), anyString()))
            .thenReturn(meta);
        NacosApiException ex = assertThrows(NacosApiException.class,
            () -> skillOperationService.getSkillVersionDetail(namespaceId, skillName, ""));
        assertEquals(NacosException.INVALID_PARAM, ex.getErrCode());
    }
    
    @Test
    void testGetSkillVersionDetailVersionNotFound() {
        String namespaceId = "test-ns";
        String skillName = "test-skill";
        AiResource meta = new AiResource();
        meta.setName(skillName);
        meta.setType("skill");
        meta.setStatus("enable");
        when(aiResourcePersistService.find(eq(namespaceId), eq(skillName), anyString()))
            .thenReturn(meta);
        when(aiResourceVersionPersistService.find(eq(namespaceId), eq(skillName), anyString(),
            eq("v99")))
            .thenReturn(null);
        NacosApiException ex = assertThrows(NacosApiException.class,
            () -> skillOperationService.getSkillVersionDetail(namespaceId, skillName, "v99"));
        assertEquals(NacosException.NOT_FOUND, ex.getErrCode());
    }
    
    @Test
    void testDownloadSkillVersionSuccess() throws NacosException {
        String namespaceId = "test-ns";
        String skillName = "test-skill";
        String version = "v1";
        AiResource meta = new AiResource();
        meta.setName(skillName);
        meta.setType("skill");
        meta.setStatus("enable");
        meta.setScope(VisibilityConstants.SCOPE_PUBLIC);
        when(aiResourcePersistService.find(eq(namespaceId), eq(skillName), anyString()))
            .thenReturn(meta);
        com.alibaba.nacos.ai.model.AiResourceVersion vRow =
            new com.alibaba.nacos.ai.model.AiResourceVersion();
        vRow.setVersion(version);
        vRow.setStorage(
            "{\"provider\":\"nacos_config\",\"scope\":\"test-ns:test-skill:v1\",\"files\":[\"SKILL.md\"]}");
        when(aiResourceVersionPersistService.find(eq(namespaceId), eq(skillName), anyString(),
            eq(version)))
            .thenReturn(vRow);
        when(storage.get(any(StorageKey.class))).thenReturn(
            ("---\nname: test-skill\ndescription: desc\n---\n\nbody").getBytes());
        Skill result = skillOperationService.downloadSkillVersion(namespaceId, skillName, version);
        assertNotNull(result);
        assertEquals("test-skill", result.getName());
    }
    
    @Test
    void testUpdateDraftSuccess() throws NacosException {
        String namespaceId = "test-ns";
        String skillName = "my-skill";
        AiResource meta = new AiResource();
        meta.setName(skillName);
        meta.setType("skill");
        meta.setNamespaceId(namespaceId);
        meta.setStatus("enable");
        meta.setMetaVersion(1L);
        meta.setVersionInfo("{\"editingVersion\":\"v1\",\"labels\":{},\"onlineCnt\":0}");
        when(aiResourcePersistService.find(eq(namespaceId), eq(skillName), anyString()))
            .thenReturn(meta);
        com.alibaba.nacos.ai.model.AiResourceVersion vRow =
            new com.alibaba.nacos.ai.model.AiResourceVersion();
        vRow.setVersion("v1");
        vRow.setStatus("draft");
        when(aiResourceVersionPersistService.find(eq(namespaceId), eq(skillName), anyString(),
            eq("v1")))
            .thenReturn(vRow);
        when(aiResourcePersistService.updateMetaCas(eq(namespaceId), eq(skillName), eq("skill"),
            eq(1L), any()))
            .thenReturn(true);
        
        Skill draft = new Skill();
        draft.setName(skillName);
        draft.setDescription("updated desc");
        draft.setSkillMd("---\nname: my-skill\ndescription: updated desc\n---\n\nbody");
        skillOperationService.updateDraft(namespaceId, draft, null);
        verify(aiResourceVersionPersistService).updateStorage(eq(namespaceId), eq(skillName),
            anyString(),
            eq("v1"), anyString());
    }
    
    @Test
    void testUpdateDraftNullSkillThrows() {
        NacosApiException ex = assertThrows(NacosApiException.class,
            () -> skillOperationService.updateDraft("ns", null, null));
        assertEquals(NacosException.INVALID_PARAM, ex.getErrCode());
    }
    
    @Test
    void testUpdateDraftRejectsFrontmatterOnlyMarkdown() {
        Skill draft = new Skill();
        draft.setName("my-skill");
        draft.setDescription("desc");
        draft.setSkillMd("---\nname: my-skill\ndescription: desc\n---\n\n  ");
        NacosApiException ex = assertThrows(NacosApiException.class,
            () -> skillOperationService.updateDraft("ns", draft, null));
        assertEquals(NacosException.INVALID_PARAM, ex.getErrCode());
    }
    
    @Test
    void testUpdateDraftNoEditingThrows() {
        String namespaceId = "test-ns";
        String skillName = "my-skill";
        AiResource meta = new AiResource();
        meta.setName(skillName);
        meta.setType("skill");
        meta.setNamespaceId(namespaceId);
        meta.setStatus("enable");
        meta.setMetaVersion(1L);
        meta.setVersionInfo("{\"labels\":{},\"onlineCnt\":0}");
        when(aiResourcePersistService.find(eq(namespaceId), eq(skillName), anyString()))
            .thenReturn(meta);
        Skill draft = new Skill();
        draft.setName(skillName);
        draft.setDescription("desc");
        draft.setSkillMd("---\nname: my-skill\n---\n\nbody");
        NacosApiException ex = assertThrows(NacosApiException.class,
            () -> skillOperationService.updateDraft(namespaceId, draft, null));
        assertEquals(NacosException.NOT_FOUND, ex.getErrCode());
    }
    
    @Test
    void testDeleteDraftSuccess() throws NacosException {
        String namespaceId = "test-ns";
        String skillName = "my-skill";
        AiResource meta = new AiResource();
        meta.setName(skillName);
        meta.setType("skill");
        meta.setNamespaceId(namespaceId);
        meta.setStatus("enable");
        meta.setMetaVersion(1L);
        meta.setVersionInfo("{\"editingVersion\":\"v1\",\"labels\":{},\"onlineCnt\":0}");
        when(aiResourcePersistService.find(eq(namespaceId), eq(skillName), anyString()))
            .thenReturn(meta);
        com.alibaba.nacos.ai.model.AiResourceVersion vRow =
            new com.alibaba.nacos.ai.model.AiResourceVersion();
        vRow.setVersion("v1");
        vRow.setStatus("draft");
        vRow.setStorage(
            "{\"provider\":\"nacos_config\",\"scope\":\"ns:s:v1\",\"files\":[\"SKILL.md\"]}");
        when(aiResourceVersionPersistService.find(eq(namespaceId), eq(skillName), anyString(),
            eq("v1")))
            .thenReturn(vRow);
        when(aiResourcePersistService.updateMetaCas(eq(namespaceId), eq(skillName), eq("skill"),
            eq(1L), any()))
            .thenReturn(true);
        
        skillOperationService.deleteDraft(namespaceId, skillName);
        verify(aiResourceVersionPersistService).delete(eq(namespaceId), eq(skillName), anyString(),
            eq("v1"));
    }
    
    @Test
    void testDeleteDraftNoEditingShouldReturn() throws NacosException {
        String namespaceId = "test-ns";
        String skillName = "my-skill";
        AiResource meta = new AiResource();
        meta.setName(skillName);
        meta.setType("skill");
        meta.setNamespaceId(namespaceId);
        meta.setStatus("enable");
        meta.setMetaVersion(1L);
        meta.setVersionInfo("{\"labels\":{},\"onlineCnt\":0}");
        when(aiResourcePersistService.find(eq(namespaceId), eq(skillName), anyString()))
            .thenReturn(meta);
        skillOperationService.deleteDraft(namespaceId, skillName);
        verify(aiResourceVersionPersistService, never()).delete(anyString(), anyString(),
            anyString(), anyString());
    }
    
    @Test
    void testSubmitDirectPublishWhenNoPipeline() throws NacosException {
        String namespaceId = "test-ns";
        String skillName = "my-skill";
        AiResource meta = new AiResource();
        meta.setName(skillName);
        meta.setType("skill");
        meta.setNamespaceId(namespaceId);
        meta.setStatus("enable");
        meta.setMetaVersion(1L);
        meta.setVersionInfo("{\"editingVersion\":\"v1\",\"labels\":{},\"onlineCnt\":0}");
        when(aiResourcePersistService.find(eq(namespaceId), eq(skillName), anyString()))
            .thenReturn(meta);
        com.alibaba.nacos.ai.model.AiResourceVersion vRow =
            new com.alibaba.nacos.ai.model.AiResourceVersion();
        vRow.setVersion("v1");
        vRow.setStatus("draft");
        vRow.setStorage(
            "{\"provider\":\"nacos_config\",\"scope\":\"ns:s:v1\",\"files\":[\"SKILL.md\"]}");
        when(aiResourceVersionPersistService.find(eq(namespaceId), eq(skillName), anyString(),
            eq("v1")))
            .thenReturn(vRow);
        when(storage.get(any(StorageKey.class))).thenReturn(
            ("---\nname: my-skill\ndescription: desc\n---\n\nbody").getBytes());
        when(aiResourcePersistService.updateMetaCas(eq(namespaceId), eq(skillName), eq("skill"),
            eq(1L), any()))
            .thenReturn(true);
        doAnswer(inv -> {
            vRow.setStatus("reviewing");
            return null;
        }).when(aiResourceVersionPersistService).updateStatus(eq(namespaceId), eq(skillName),
            anyString(),
            eq("v1"), eq("reviewing"));
        com.alibaba.nacos.ai.model.skills.SkillIndexManifest manifest =
            new com.alibaba.nacos.ai.model.skills.SkillIndexManifest();
        manifest.setVersions(new HashMap<>());
        manifest.setLabels(new HashMap<>());
        when(manifestService.loadForUpdate(eq(namespaceId), eq(skillName))).thenReturn(manifest);
        
        String result = skillOperationService.submit(namespaceId, skillName, null);
        assertEquals("v1", result);
        verify(aiResourceVersionPersistService).updateStatus(eq(namespaceId), eq(skillName),
            anyString(),
            eq("v1"), eq("online"));
    }
    
    @Test
    void testSubmitAutoPublishWhenPipelineApproved() throws NacosException {
        System.setProperty(AUTO_PUBLISH_AFTER_REVIEW_ENABLED_KEY, "true");
        VisibilityService visibilityService = mock(VisibilityService.class);
        when(visibilityService.validateVisibility(anyString(), anyString(), anyString(), any()))
            .thenReturn(ValidationResult.allow());
        when(mockVisibilityManager.findVisibilityService(anyString()))
            .thenReturn(Optional.of(visibilityService));
        String namespaceId = "test-ns";
        String skillName = "my-skill";
        AiResource meta = new AiResource();
        meta.setName(skillName);
        meta.setType("skill");
        meta.setNamespaceId(namespaceId);
        meta.setStatus("enable");
        meta.setMetaVersion(1L);
        meta.setVersionInfo("{\"editingVersion\":\"v1\",\"labels\":{},\"onlineCnt\":0}");
        when(aiResourcePersistService.find(eq(namespaceId), eq(skillName), anyString()))
            .thenReturn(meta);
        com.alibaba.nacos.ai.model.AiResourceVersion vRow =
            new com.alibaba.nacos.ai.model.AiResourceVersion();
        vRow.setVersion("v1");
        vRow.setStatus("draft");
        vRow.setStorage(
            "{\"provider\":\"nacos_config\",\"scope\":\"ns:s:v1\",\"files\":[\"SKILL.md\"]}");
        when(aiResourceVersionPersistService.find(eq(namespaceId), eq(skillName), anyString(),
            eq("v1")))
            .thenReturn(vRow);
        when(storage.get(any(StorageKey.class))).thenReturn(
            ("---\nname: my-skill\ndescription: desc\n---\n\nbody").getBytes());
        when(aiResourcePersistService.updateMetaCas(eq(namespaceId), eq(skillName), eq("skill"),
            anyLong(), any()))
            .thenReturn(true);
        doAnswer(invocation -> {
            vRow.setStatus(invocation.getArgument(4));
            return null;
        }).when(aiResourceVersionPersistService).updateStatus(eq(namespaceId), eq(skillName),
            anyString(),
            eq("v1"), anyString());
        doAnswer(invocation -> {
            vRow.setPublishPipelineInfo(invocation.getArgument(4));
            return null;
        }).when(aiResourceVersionPersistService).updatePublishPipelineInfo(eq(namespaceId),
            eq(skillName),
            anyString(), eq("v1"), anyString());
        
        PipelineExecution execution = new PipelineExecution();
        execution.setExecutionId("exec-1");
        execution.setStatus(PipelineExecutionStatus.APPROVED);
        when(pipelineExecutionRepository.findById("exec-1")).thenReturn(execution);
        com.alibaba.nacos.ai.model.skills.SkillIndexManifest manifest =
            new com.alibaba.nacos.ai.model.skills.SkillIndexManifest();
        manifest.setVersions(new HashMap<>());
        manifest.setLabels(new HashMap<>());
        when(manifestService.loadForUpdate(eq(namespaceId), eq(skillName))).thenReturn(manifest);
        
        PublishPipelineExecutor pipelineExecutor = mock(PublishPipelineExecutor.class);
        when(pipelineExecutor.isPipelineAvailable(any())).thenReturn(true);
        doAnswer(invocation -> {
            PipelineCallback callback = invocation.getArgument(1);
            PipelineExecutionResult result = new PipelineExecutionResult();
            result.setExecutionId("exec-1");
            result.setStatus(PipelineExecutionStatus.APPROVED);
            result.setPipeline(List.of());
            callback.onComplete(result);
            return "exec-1";
        }).when(pipelineExecutor).execute(any(), any(), anyString());
        SkillOperationServiceImpl autoPublishService =
            new SkillOperationServiceImpl(aiResourcePersistService,
                aiResourceVersionPersistService, pipelineExecutor, manifestService,
                new AiResourceManager(aiResourcePersistService, aiResourceVersionPersistService,
                    pipelineExecutionRepository));
        
        String result = autoPublishService.submit(namespaceId, skillName, null);
        
        assertEquals("v1", result);
        verify(aiResourceVersionPersistService).updateStatus(eq(namespaceId), eq(skillName),
            anyString(),
            eq("v1"), eq("online"));
        verify(manifestService).write(eq(namespaceId), eq(skillName), any());
        verify(visibilityService, times(1)).validateVisibility(anyString(), anyString(),
            anyString(), any());
    }
    
    @Test
    void testSubmitShouldNotAutoPublishByDefaultWhenPipelineApproved() throws NacosException {
        String namespaceId = "test-ns";
        String skillName = "my-skill";
        AiResource meta = new AiResource();
        meta.setName(skillName);
        meta.setType("skill");
        meta.setNamespaceId(namespaceId);
        meta.setStatus("enable");
        meta.setMetaVersion(1L);
        meta.setVersionInfo("{\"editingVersion\":\"v1\",\"labels\":{},\"onlineCnt\":0}");
        when(aiResourcePersistService.find(eq(namespaceId), eq(skillName), anyString()))
            .thenReturn(meta);
        com.alibaba.nacos.ai.model.AiResourceVersion vRow =
            new com.alibaba.nacos.ai.model.AiResourceVersion();
        vRow.setVersion("v1");
        vRow.setStatus("draft");
        vRow.setStorage(
            "{\"provider\":\"nacos_config\",\"scope\":\"ns:s:v1\",\"files\":[\"SKILL.md\"]}");
        when(aiResourceVersionPersistService.find(eq(namespaceId), eq(skillName), anyString(),
            eq("v1")))
            .thenReturn(vRow);
        when(storage.get(any(StorageKey.class))).thenReturn(
            ("---\nname: my-skill\ndescription: desc\n---\n\nbody").getBytes());
        when(aiResourcePersistService.updateMetaCas(eq(namespaceId), eq(skillName), eq("skill"),
            anyLong(), any()))
            .thenReturn(true);
        doAnswer(invocation -> {
            vRow.setStatus(invocation.getArgument(4));
            return null;
        }).when(aiResourceVersionPersistService).updateStatus(eq(namespaceId), eq(skillName),
            anyString(),
            eq("v1"), anyString());
        doAnswer(invocation -> {
            vRow.setPublishPipelineInfo(invocation.getArgument(4));
            return null;
        }).when(aiResourceVersionPersistService).updatePublishPipelineInfo(eq(namespaceId),
            eq(skillName),
            anyString(), eq("v1"), anyString());
        
        PublishPipelineExecutor pipelineExecutor = mock(PublishPipelineExecutor.class);
        when(pipelineExecutor.isPipelineAvailable(any())).thenReturn(true);
        doAnswer(invocation -> {
            PipelineCallback callback = invocation.getArgument(1);
            PipelineExecutionResult result = new PipelineExecutionResult();
            result.setExecutionId("exec-1");
            result.setStatus(PipelineExecutionStatus.APPROVED);
            result.setPipeline(List.of());
            callback.onComplete(result);
            return "exec-1";
        }).when(pipelineExecutor).execute(any(), any(), anyString());
        SkillOperationServiceImpl autoPublishService =
            new SkillOperationServiceImpl(aiResourcePersistService,
                aiResourceVersionPersistService, pipelineExecutor, manifestService,
                new AiResourceManager(aiResourcePersistService, aiResourceVersionPersistService,
                    pipelineExecutionRepository));
        
        String result = autoPublishService.submit(namespaceId, skillName, null);
        
        assertEquals("v1", result);
        verify(aiResourceVersionPersistService, never()).updateStatus(eq(namespaceId),
            eq(skillName), anyString(),
            eq("v1"), eq("online"));
        verify(manifestService, never()).write(eq(namespaceId), eq(skillName), any());
    }
    
    @Test
    void testQuerySkillSuccess() throws NacosException {
        String namespaceId = "test-ns";
        String skillName = "my-skill";
        AiResource meta = new AiResource();
        meta.setName(skillName);
        meta.setType("skill");
        meta.setStatus("enable");
        meta.setScope(VisibilityConstants.SCOPE_PUBLIC);
        meta.setVersionInfo("{\"labels\":{\"latest\":\"v1\"},\"onlineCnt\":1}");
        when(aiResourcePersistService.find(eq(namespaceId), eq(skillName), anyString()))
            .thenReturn(meta);
        com.alibaba.nacos.ai.model.skills.SkillIndexManifest manifest =
            new com.alibaba.nacos.ai.model.skills.SkillIndexManifest();
        manifest.setVersions(new HashMap<>(Map.of("v1", List.of("SKILL.md"))));
        manifest.setLabels(new HashMap<>(Map.of("latest", "v1")));
        when(manifestService.query(eq(namespaceId), eq(skillName))).thenReturn(manifest);
        when(storage.get(any(StorageKey.class))).thenReturn(
            ("---\nname: my-skill\ndescription: desc\n---\n\nbody").getBytes());
        Skill result = skillOperationService.querySkill(namespaceId, skillName, null, null);
        assertNotNull(result);
        assertEquals("my-skill", result.getName());
    }
    
    @Test
    void testQuerySkillNotFoundWhenNoManifest() {
        String namespaceId = "test-ns";
        String skillName = "my-skill";
        AiResource meta = new AiResource();
        meta.setName(skillName);
        meta.setType("skill");
        meta.setStatus("enable");
        when(aiResourcePersistService.find(eq(namespaceId), eq(skillName), anyString()))
            .thenReturn(meta);
        when(manifestService.query(eq(namespaceId), eq(skillName))).thenReturn(null);
        NacosApiException ex = assertThrows(NacosApiException.class,
            () -> skillOperationService.querySkill(namespaceId, skillName, null, null));
        assertEquals(NacosException.NOT_FOUND, ex.getErrCode());
    }
    
    @Test
    void testSearchSkillsSuccess() throws NacosException {
        String namespaceId = "test-ns";
        Page<AiResource> metaPage = new Page<>();
        AiResource meta = new AiResource();
        meta.setName("my-skill");
        meta.setStatus("enable");
        meta.setDesc("desc");
        meta.setVersionInfo("{\"labels\":{\"latest\":\"v1\"},\"onlineCnt\":1}");
        metaPage.setPageItems(List.of(meta));
        metaPage.setTotalCount(1);
        metaPage.setPagesAvailable(1);
        when(aiResourcePersistService.list(any(), eq(1), eq(10))).thenReturn(metaPage);
        Page<SkillBasicInfo> result = skillOperationService.searchSkills(namespaceId, "my", 1, 10);
        assertNotNull(result);
        assertEquals(1, result.getPageItems().size());
        assertEquals("my-skill", result.getPageItems().get(0).getName());
    }
    
    @Test
    void testSearchSkillsExcludesDisabledAndNoOnline() throws NacosException {
        String namespaceId = "test-ns";
        Page<AiResource> metaPage = new Page<>();
        AiResource disabled = new AiResource();
        disabled.setName("disabled-skill");
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
        Page<SkillBasicInfo> result = skillOperationService.searchSkills(namespaceId, null, 1, 10);
        assertTrue(result.getPageItems().isEmpty());
    }
    
    @Test
    void testUpdateLabelsSuccess() throws NacosException {
        String namespaceId = "test-ns";
        String skillName = "my-skill";
        AiResource meta = new AiResource();
        meta.setName(skillName);
        meta.setType("skill");
        meta.setNamespaceId(namespaceId);
        meta.setStatus("enable");
        meta.setMetaVersion(1L);
        meta.setVersionInfo("{\"labels\":{\"latest\":\"v1\"},\"onlineCnt\":1}");
        when(aiResourcePersistService.find(eq(namespaceId), eq(skillName), anyString()))
            .thenReturn(meta);
        when(aiResourcePersistService.updateMetaCas(eq(namespaceId), eq(skillName), eq("skill"),
            eq(1L), any()))
            .thenReturn(true);
        com.alibaba.nacos.ai.model.skills.SkillIndexManifest manifest =
            new com.alibaba.nacos.ai.model.skills.SkillIndexManifest();
        manifest.setLabels(new HashMap<>());
        manifest.setVersions(new HashMap<>());
        when(manifestService.query(eq(namespaceId), eq(skillName))).thenReturn(manifest);
        Map<String, String> labels = Map.of("stable", "v1");
        skillOperationService.updateLabels(namespaceId, skillName, labels);
        ArgumentCaptor<com.alibaba.nacos.ai.model.skills.SkillIndexManifest> manifestCaptor =
            ArgumentCaptor.forClass(com.alibaba.nacos.ai.model.skills.SkillIndexManifest.class);
        verify(manifestService).write(eq(namespaceId), eq(skillName), manifestCaptor.capture());
        assertEquals("v1", manifestCaptor.getValue().getLabels().get("stable"));
        assertEquals("v1", manifestCaptor.getValue().getLabels().get("latest"));
    }
    
    @Test
    void testVersionOfflineShouldRemoveLatestFromManifestWhenNoOnlineVersionRemains()
        throws NacosException {
        String namespaceId = "test-ns";
        String skillName = "my-skill";
        String version = "v1";
        AiResource meta = new AiResource();
        meta.setName(skillName);
        meta.setType("skill");
        meta.setNamespaceId(namespaceId);
        meta.setStatus("enable");
        meta.setMetaVersion(1L);
        meta.setVersionInfo("{\"labels\":{\"latest\":\"v1\"},\"onlineCnt\":1}");
        when(aiResourcePersistService.find(eq(namespaceId), eq(skillName), anyString()))
            .thenReturn(meta);
        when(aiResourcePersistService.updateMetaCas(eq(namespaceId), eq(skillName), eq("skill"),
            eq(1L), any())).thenReturn(true);
        com.alibaba.nacos.ai.model.AiResourceVersion v =
            new com.alibaba.nacos.ai.model.AiResourceVersion();
        v.setVersion(version);
        v.setStatus("online");
        v.setStorage(
            "{\"provider\":\"nacos_config\",\"scope\":\"test-ns:my-skill:v1\",\"files\":[\"SKILL.md\"]}");
        when(aiResourceVersionPersistService.find(eq(namespaceId), eq(skillName), anyString(),
            eq(version))).thenReturn(v);
        Page<com.alibaba.nacos.ai.model.AiResourceVersion> emptyOnlinePage = new Page<>();
        emptyOnlinePage.setPageItems(List.of());
        when(aiResourceVersionPersistService.list(eq(namespaceId), eq(skillName), anyString(),
            eq("online"), eq(1), eq(500))).thenReturn(emptyOnlinePage);
        com.alibaba.nacos.ai.model.skills.SkillIndexManifest manifest =
            new com.alibaba.nacos.ai.model.skills.SkillIndexManifest();
        manifest.setLabels(new HashMap<>(Map.of("latest", version)));
        manifest.setVersions(new HashMap<>(Map.of(version, List.of("SKILL.md"))));
        when(manifestService.query(eq(namespaceId), eq(skillName))).thenReturn(manifest);
        
        skillOperationService.changeOnlineStatus(namespaceId, skillName, "version", version,
            false);
        
        ArgumentCaptor<com.alibaba.nacos.ai.model.skills.SkillIndexManifest> manifestCaptor =
            ArgumentCaptor.forClass(com.alibaba.nacos.ai.model.skills.SkillIndexManifest.class);
        verify(manifestService).write(eq(namespaceId), eq(skillName), manifestCaptor.capture());
        assertTrue(!manifestCaptor.getValue().getLabels().containsKey("latest"));
        assertTrue(!manifestCaptor.getValue().getVersions().containsKey(version));
    }
    
    @Test
    void testChangeOnlineStatusSkillScopeEnable() throws NacosException {
        String namespaceId = "test-ns";
        String skillName = "my-skill";
        AiResource meta = new AiResource();
        meta.setName(skillName);
        meta.setType("skill");
        meta.setNamespaceId(namespaceId);
        meta.setStatus("disable");
        meta.setMetaVersion(1L);
        meta.setVersionInfo("{\"labels\":{\"latest\":\"v1\"},\"onlineCnt\":1}");
        when(aiResourcePersistService.find(eq(namespaceId), eq(skillName), anyString()))
            .thenReturn(meta);
        when(aiResourcePersistService.updateMetaCas(eq(namespaceId), eq(skillName), eq("skill"),
            eq(1L), any()))
            .thenReturn(true);
        com.alibaba.nacos.ai.model.AiResourceVersion onlineV =
            new com.alibaba.nacos.ai.model.AiResourceVersion();
        onlineV.setVersion("v1");
        onlineV.setStatus("online");
        onlineV.setStorage(
            "{\"provider\":\"nacos_config\",\"scope\":\"ns:s:v1\",\"files\":[\"SKILL.md\"]}");
        Page<com.alibaba.nacos.ai.model.AiResourceVersion> vPage = new Page<>();
        vPage.setPageItems(List.of(onlineV));
        when(aiResourceVersionPersistService.list(eq(namespaceId), eq(skillName), anyString(),
            any(), anyInt(), anyInt()))
            .thenReturn(vPage);
        
        skillOperationService.changeOnlineStatus(namespaceId, skillName, "skill", null, true);
        verify(aiResourcePersistService).updateMetaCas(eq(namespaceId), eq(skillName), eq("skill"),
            eq(1L),
            argThat(resource -> "enable".equals(resource.getStatus())));
    }
    
    @Test
    void testChangeOnlineStatusSkillScopeDisable() throws NacosException {
        String namespaceId = "test-ns";
        String skillName = "my-skill";
        AiResource meta = new AiResource();
        meta.setName(skillName);
        meta.setType("skill");
        meta.setNamespaceId(namespaceId);
        meta.setStatus("enable");
        meta.setMetaVersion(1L);
        meta.setVersionInfo("{\"labels\":{},\"onlineCnt\":1}");
        when(aiResourcePersistService.find(eq(namespaceId), eq(skillName), anyString()))
            .thenReturn(meta);
        when(aiResourcePersistService.updateMetaCas(eq(namespaceId), eq(skillName), eq("skill"),
            eq(1L), any()))
            .thenReturn(true);
        
        skillOperationService.changeOnlineStatus(namespaceId, skillName, "skill", null, false);
        verify(manifestService).delete(eq(namespaceId), eq(skillName));
    }
    
    private String uploadSkill(String namespaceId, byte[] zipBytes) throws NacosException {
        return uploadSkill(namespaceId, zipBytes, false);
    }
    
    private String uploadSkill(String namespaceId, byte[] zipBytes, boolean overwrite)
        throws NacosException {
        return uploadSkill(namespaceId, zipBytes, overwrite, null);
    }
    
    private String uploadSkill(String namespaceId, byte[] zipBytes, boolean overwrite,
        String targetVersion)
        throws NacosException {
        return uploadSkill(namespaceId, zipBytes, overwrite, targetVersion, null);
    }
    
    private String uploadSkill(String namespaceId, byte[] zipBytes, boolean overwrite,
        String targetVersion, String commitMsg)
        throws NacosException {
        SkillUploadRequest request = SkillUploadRequest.builder()
            .namespaceId(namespaceId)
            .zipBytes(zipBytes)
            .overwrite(overwrite)
            .targetVersion(targetVersion)
            .commitMsg(commitMsg)
            .build();
        return skillOperationService.uploadSkillFromZip(request);
    }
}
