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

import com.alibaba.nacos.ai.model.AiResource;
import com.alibaba.nacos.ai.model.skills.SkillAdminDetail;
import com.alibaba.nacos.ai.model.skills.SkillAdminListItem;
import com.alibaba.nacos.ai.pipeline.PublishPipelineExecutor;
import com.alibaba.nacos.ai.pipeline.PublishPipelineManager;
import com.alibaba.nacos.ai.pipeline.config.PipelineConfigProvider;
import com.alibaba.nacos.ai.pipeline.model.PipelineConfig;
import com.alibaba.nacos.ai.pipeline.repository.PipelineExecutionRepository;
import com.alibaba.nacos.ai.service.repository.AiResourcePersistService;
import com.alibaba.nacos.ai.service.repository.AiResourceVersionPersistService;
import com.alibaba.nacos.api.ai.model.skills.Skill;
import com.alibaba.nacos.api.ai.model.skills.SkillResource;
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
import com.alibaba.nacos.plugin.datafilter.constant.DataFilterConstants;
import com.alibaba.nacos.plugin.datafilter.spi.DataFilterPluginManager;
import com.alibaba.nacos.plugin.datafilter.spi.DataFilterService;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Collections;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Test for SkillOperationServiceImpl.
 *
 * @author nacos
 */
@ExtendWith(MockitoExtension.class)
class SkillOperationServiceImplTest {
    
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

    private static final org.springframework.core.env.ConfigurableEnvironment CACHED_ENVIRONMENT = EnvUtil.getEnvironment();

    private MockedStatic<DataFilterPluginManager> dataFilterManagerStatic;

    private DataFilterPluginManager mockDataFilterManager;

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
        skillOperationService = new SkillOperationServiceImpl(aiResourcePersistService, aiResourceVersionPersistService,
                publishPipelineExecutor, pipelineExecutionRepository, manifestService);
        mockDataFilterManager = mock(DataFilterPluginManager.class);
        lenient().when(mockDataFilterManager.findFilterService(anyString())).thenReturn(Optional.empty());
        dataFilterManagerStatic = org.mockito.Mockito.mockStatic(DataFilterPluginManager.class);
        dataFilterManagerStatic.when(DataFilterPluginManager::getInstance).thenReturn(mockDataFilterManager);
    }

    @AfterEach
    void tearDown() {
        if (dataFilterManagerStatic != null) {
            dataFilterManagerStatic.close();
        }
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
        meta.setVersionInfo("{\"labels\":{\"latest\":\"v1\"},\"onlineCnt\":1}");
        when(aiResourcePersistService.find(eq(namespaceId), eq(skillName), anyString())).thenReturn(meta);
        Page<com.alibaba.nacos.ai.model.AiResourceVersion> vPage = new Page<>();
        vPage.setPageItems(List.of());
        when(aiResourceVersionPersistService.listAll(eq(namespaceId), eq(skillName), anyInt(), anyInt())).thenReturn(vPage);

        // When
        SkillAdminDetail skillAdminDetail = skillOperationService.getSkillDetail(namespaceId, skillName);

        // Then
        assertNotNull(skillAdminDetail);
        assertTrue(skillAdminDetail.isEnable());
        assertEquals(1, skillAdminDetail.getOnlineCnt());
        assertEquals("v1", skillAdminDetail.getLabels().get("latest"));
        assertNotNull(skillAdminDetail.getVersions());
    }
    
    @Test
    void testGetSkillDetailNotFound() throws NacosException {
        // Given
        String namespaceId = "test-namespace";
        String skillName = "non-existent-skill";
        when(aiResourcePersistService.find(eq(namespaceId), eq(skillName), anyString())).thenReturn(null);
        
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
        when(aiResourcePersistService.find(eq(namespaceId), eq(skillName), anyString())).thenReturn(meta);
        Page<com.alibaba.nacos.ai.model.AiResourceVersion> vPage = new Page<>();
        com.alibaba.nacos.ai.model.AiResourceVersion v1 = new com.alibaba.nacos.ai.model.AiResourceVersion();
        v1.setVersion("v1");
        vPage.setPageItems(List.of(v1));
        when(aiResourceVersionPersistService.listAll(eq(namespaceId), eq(skillName), anyInt(), anyInt())).thenReturn(vPage);

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
        when(aiResourcePersistService.find(eq(namespaceId), eq(skillName), anyString())).thenReturn(null);
        
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
        metaPage.setPageItems(List.of(meta));
        metaPage.setTotalCount(1);
        metaPage.setPageNumber(1);
        metaPage.setPagesAvailable(1);
        when(aiResourcePersistService.list(eq(namespaceId), anyString(), any(), any(), eq(1), eq(10)))
                .thenReturn(metaPage);
        
        // When
        Page<SkillAdminListItem> result = skillOperationService.listSkills(namespaceId, null, null, 1, 10);
        
        // Then
        assertNotNull(result);
        assertEquals(1, result.getPageNumber());
        assertEquals(1, result.getPageItems().size());
    }
    
    @Test
    void testUploadSkillFromZip() throws NacosException, IOException {
        // Given
        String namespaceId = "test-namespace";
        byte[] zipBytes = createValidZipBytes();
        when(aiResourcePersistService.find(eq(namespaceId), anyString(), anyString())).thenReturn(null);
        
        // When
        String result = skillOperationService.uploadSkillFromZip(namespaceId, zipBytes);
        
        // Then
        assertNotNull(result);
        verify(storage, times(1)).save(any(StorageKey.class), any(byte[].class));
    }
    
    /**
     * Create a valid skill for testing.
     */
    private Skill createValidSkill() {
        Skill skill = new Skill();
        skill.setName("test-skill");
        skill.setDescription("Test description");
        skill.setInstruction("Test instruction");
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
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            // Add SKILL.md
            ZipEntry entry = new ZipEntry("SKILL.md");
            zos.putNextEntry(entry);
            String skillMd = "---\n"
                    + "name: test-skill\n"
                    + "description: Test skill description\n"
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
    }

    @Test
    void testListSkillsFilteredByReadFilter() throws NacosException {
        String namespaceId = "test-ns";
        AiResource meta1 = new AiResource();
        meta1.setName("skill-public");
        meta1.setNamespaceId(namespaceId);
        meta1.setType("skill");
        meta1.setScope(DataFilterConstants.SCOPE_PUBLIC);
        meta1.setOwner("userA");
        AiResource meta2 = new AiResource();
        meta2.setName("skill-private");
        meta2.setNamespaceId(namespaceId);
        meta2.setType("skill");
        meta2.setScope(DataFilterConstants.SCOPE_PRIVATE);
        meta2.setOwner("userA");

        Page<AiResource> metaPage = new Page<>();
        metaPage.setPageItems(List.of(meta1, meta2));
        metaPage.setTotalCount(2);
        metaPage.setPagesAvailable(1);
        when(aiResourcePersistService.list(eq(namespaceId), anyString(), any(), any(), eq(1), eq(10)))
                .thenReturn(metaPage);

        DataFilterService mockFilter = mock(DataFilterService.class);
        when(mockFilter.filter(anyString(), eq(DataFilterConstants.ACTION_READ), isNull(), anyList()))
                .thenReturn(Collections.singletonList(meta1));
        when(mockDataFilterManager.findFilterService("nacos-default-ai")).thenReturn(Optional.of(mockFilter));

        setupRequestContext("userB");
        Page<SkillAdminListItem> result = skillOperationService.listSkills(namespaceId, null, null, 1, 10);
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
        meta.setScope(DataFilterConstants.SCOPE_PRIVATE);
        meta.setOwner("ownerUser");
        when(aiResourcePersistService.find(eq(namespaceId), eq(skillName), anyString())).thenReturn(meta);

        DataFilterService mockFilter = mock(DataFilterService.class);
        when(mockFilter.filter(anyString(), eq(DataFilterConstants.ACTION_READ), isNull(), anyList()))
                .thenReturn(Collections.emptyList());
        when(mockDataFilterManager.findFilterService("nacos-default-ai")).thenReturn(Optional.of(mockFilter));

        setupRequestContext("otherUser");
        NacosApiException ex = assertThrows(NacosApiException.class,
                () -> skillOperationService.getSkillDetail(namespaceId, skillName));
        assertEquals(NacosException.NO_RIGHT, ex.getErrCode());
    }

    @Test
    void testDeleteSkillDeniedByWriteFilter() {
        String namespaceId = "test-ns";
        String skillName = "protected-skill";
        AiResource meta = new AiResource();
        meta.setName(skillName);
        meta.setType("skill");
        meta.setNamespaceId(namespaceId);
        meta.setScope(DataFilterConstants.SCOPE_PRIVATE);
        meta.setOwner("ownerUser");
        when(aiResourcePersistService.find(eq(namespaceId), eq(skillName), anyString())).thenReturn(meta);

        DataFilterService mockFilter = mock(DataFilterService.class);
        when(mockFilter.filter(anyString(), eq(DataFilterConstants.ACTION_WRITE), isNull(), anyList()))
                .thenReturn(Collections.emptyList());
        when(mockDataFilterManager.findFilterService("nacos-default-ai")).thenReturn(Optional.of(mockFilter));

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
        when(aiResourcePersistService.find(eq(namespaceId), anyString(), anyString())).thenReturn(null);
        setupRequestContext("creatorUser");

        skillOperationService.uploadSkillFromZip(namespaceId, zipBytes);

        org.mockito.ArgumentCaptor<AiResource> captor = org.mockito.ArgumentCaptor.forClass(AiResource.class);
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
        when(aiResourcePersistService.list(eq(namespaceId), anyString(), any(), any(), eq(1), eq(10)))
                .thenReturn(metaPage);

        Page<SkillAdminListItem> result = skillOperationService.listSkills(namespaceId, null, null, 1, 10);
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
        meta.setScope(DataFilterConstants.SCOPE_PRIVATE);
        meta.setOwner("ownerUser");
        meta.setVersionInfo("{\"labels\":{},\"onlineCnt\":0}");
        meta.setMetaVersion(1L);
        when(aiResourcePersistService.find(eq(namespaceId), eq(skillName), anyString())).thenReturn(meta);

        DataFilterService mockFilter = mock(DataFilterService.class);
        when(mockFilter.filter(anyString(), eq(DataFilterConstants.ACTION_WRITE), isNull(), anyList()))
                .thenReturn(Collections.emptyList());
        when(mockDataFilterManager.findFilterService("nacos-default-ai")).thenReturn(Optional.of(mockFilter));

        setupRequestContext("attackerUser");
        NacosApiException ex = assertThrows(NacosApiException.class,
                () -> skillOperationService.createDraft(namespaceId, skillName, null));
        assertEquals(NacosException.NO_RIGHT, ex.getErrCode());
    }

    @Test
    void testCreateDraftAuthorIsCurrentUser() throws NacosException {
        String namespaceId = "test-ns";
        String skillName = "brand-new-skill";
        when(aiResourcePersistService.find(eq(namespaceId), eq(skillName), anyString())).thenReturn(null);
        setupRequestContext("myUser");

        String version = skillOperationService.createDraft(namespaceId, skillName, null);
        assertEquals("v1", version);

        org.mockito.ArgumentCaptor<com.alibaba.nacos.ai.model.AiResourceVersion> vCaptor =
                org.mockito.ArgumentCaptor.forClass(com.alibaba.nacos.ai.model.AiResourceVersion.class);
        verify(aiResourceVersionPersistService).insert(vCaptor.capture());
        assertEquals("myUser", vCaptor.getValue().getAuthor());
    }

    @Test
    void testUpdateScopeSuccess() throws NacosException {
        String namespaceId = "test-ns";
        String skillName = "my-skill";
        AiResource meta = new AiResource();
        meta.setName(skillName);
        meta.setType("skill");
        meta.setNamespaceId(namespaceId);
        meta.setScope(DataFilterConstants.SCOPE_PRIVATE);
        meta.setOwner("ownerUser");
        when(aiResourcePersistService.find(eq(namespaceId), eq(skillName), anyString())).thenReturn(meta);
        when(aiResourcePersistService.updateScope(eq(namespaceId), eq(skillName), eq("skill"), eq("PUBLIC")))
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
        meta.setScope(DataFilterConstants.SCOPE_PRIVATE);
        meta.setOwner("ownerUser");
        when(aiResourcePersistService.find(eq(namespaceId), eq(skillName), anyString())).thenReturn(meta);

        DataFilterService mockFilter = mock(DataFilterService.class);
        when(mockFilter.filter(anyString(), eq(DataFilterConstants.ACTION_WRITE), isNull(), anyList()))
                .thenReturn(Collections.emptyList());
        when(mockDataFilterManager.findFilterService("nacos-default-ai")).thenReturn(Optional.of(mockFilter));

        setupRequestContext("attackerUser");
        NacosApiException ex = assertThrows(NacosApiException.class,
                () -> skillOperationService.updateScope(namespaceId, skillName, "PUBLIC"));
        assertEquals(NacosException.NO_RIGHT, ex.getErrCode());
        verify(aiResourcePersistService, never()).updateScope(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void testUpdateScopeNotFound() {
        String namespaceId = "test-ns";
        String skillName = "nonexistent";
        when(aiResourcePersistService.find(eq(namespaceId), eq(skillName), anyString())).thenReturn(null);

        NacosApiException ex = assertThrows(NacosApiException.class,
                () -> skillOperationService.updateScope(namespaceId, skillName, "PUBLIC"));
        assertEquals(NacosException.NOT_FOUND, ex.getErrCode());
    }
}
