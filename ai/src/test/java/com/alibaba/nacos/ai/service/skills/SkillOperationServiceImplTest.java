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

import com.alibaba.nacos.ai.pipeline.PublishPipelineExecutor;
import com.alibaba.nacos.ai.pipeline.repository.PipelineExecutionRepository;
import com.alibaba.nacos.ai.service.repository.AiResourcePersistService;
import com.alibaba.nacos.ai.service.repository.AiResourceVersionPersistService;
import com.alibaba.nacos.api.ai.model.skills.Skill;
import com.alibaba.nacos.api.ai.model.skills.SkillBasicInfo;
import com.alibaba.nacos.api.ai.model.skills.SkillResource;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.plugin.ai.storage.AiResourceStorageRouter;
import com.alibaba.nacos.plugin.ai.storage.model.StorageKey;
import com.alibaba.nacos.plugin.ai.storage.spi.AiResourceStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

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
    private PublishPipelineExecutor publishPipelineExecutor;
    
    @Mock
    private PipelineExecutionRepository pipelineExecutionRepository;

    private SkillOperationServiceImpl skillOperationService;

    @BeforeEach
    void setUp() {
        AiResourceStorageRouter.reset();
        lenient().when(storage.type()).thenReturn("nacos_config");
        AiResourceStorageRouter.join(storage);
        skillOperationService = new SkillOperationServiceImpl(aiResourcePersistService, aiResourceVersionPersistService,
                publishPipelineExecutor, pipelineExecutionRepository);
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
        when(storage.get(any(StorageKey.class))).thenReturn(createMainConfigJson(skillName).getBytes());
        
        // When
        Skill skill = skillOperationService.getSkillDetail(namespaceId, skillName);
        
        // Then
        assertNotNull(skill);
        assertEquals(skillName, skill.getName());
        assertEquals("Test description", skill.getDescription());
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
        when(storage.get(any(StorageKey.class))).thenReturn(createMainConfigJson(skillName).getBytes());
        
        // When
        skillOperationService.deleteSkill(namespaceId, skillName);
        
        // Then
        verify(storage, times(1)).delete(any(StorageKey.class));
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
        Page<SkillBasicInfo> result = skillOperationService.listSkills(namespaceId, null, null, 1, 10);
        
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
}
