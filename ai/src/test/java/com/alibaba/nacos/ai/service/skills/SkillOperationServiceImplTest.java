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

import com.alibaba.nacos.ai.service.SyncEffectService;
import com.alibaba.nacos.api.ai.model.skills.Skill;
import com.alibaba.nacos.api.ai.model.skills.SkillBasicInfo;
import com.alibaba.nacos.api.ai.model.skills.SkillResource;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.config.server.exception.ConfigAlreadyExistsException;
import com.alibaba.nacos.config.server.model.ConfigInfo;
import com.alibaba.nacos.config.server.model.ConfigRequestInfo;
import com.alibaba.nacos.config.server.model.form.ConfigForm;
import com.alibaba.nacos.config.server.service.ConfigOperationService;
import com.alibaba.nacos.config.server.service.repository.ConfigInfoPersistService;
import com.alibaba.nacos.config.server.service.query.ConfigQueryChainService;
import com.alibaba.nacos.config.server.service.query.model.ConfigQueryChainRequest;
import com.alibaba.nacos.config.server.service.query.model.ConfigQueryChainResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
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
    private ConfigQueryChainService configQueryChainService;
    
    @Mock
    private ConfigOperationService configOperationService;
    
    @Mock
    private ConfigInfoPersistService configInfoPersistService;
    
    @Mock
    private SyncEffectService syncEffectService;
    
    private SkillOperationServiceImpl skillOperationService;
    
    @BeforeEach
    void setUp() {
        skillOperationService = new SkillOperationServiceImpl(
                configQueryChainService,
                configOperationService,
                configInfoPersistService,
                syncEffectService);
    }
    
    @Test
    void testRegisterSkillSuccessfully() throws NacosException {
        // Given
        Skill skill = createValidSkill();
        String namespaceId = "test-namespace";
        when(configOperationService.publishConfig(any(ConfigForm.class),
                any(ConfigRequestInfo.class), isNull()))
                .thenReturn(Boolean.TRUE);
        
        // When
        String result = skillOperationService.registerSkill(skill, namespaceId);
        
        // Then
        assertEquals(skill.getName(), result);
        verify(configOperationService, times(1)).publishConfig(any(ConfigForm.class),
                any(ConfigRequestInfo.class), isNull());
        verify(syncEffectService, times(1)).toSync(any(ConfigForm.class), any(Long.class));
    }
    
    @Test
    void testRegisterSkillWithResources() throws NacosException {
        // Given
        Skill skill = createValidSkillWithResources();
        String namespaceId = "test-namespace";
        when(configOperationService.publishConfig(any(ConfigForm.class),
                any(ConfigRequestInfo.class), isNull()))
                .thenReturn(Boolean.TRUE);
        
        // When
        String result = skillOperationService.registerSkill(skill, namespaceId);
        
        // Then
        assertEquals(skill.getName(), result);
        // Should publish main config + resource configs
        verify(configOperationService, times(2)).publishConfig(any(ConfigForm.class),
                any(ConfigRequestInfo.class), isNull());
    }
    
    @Test
    void testRegisterSkillWithBlankName() {
        // Given
        Skill skill = createValidSkill();
        skill.setName("");
        String namespaceId = "test-namespace";
        
        // When & Then
        NacosApiException exception = assertThrows(NacosApiException.class,
                () -> skillOperationService.registerSkill(skill, namespaceId));
        assertEquals("Skill name is required", exception.getMessage());
    }
    
    @Test
    void testRegisterSkillWithInvalidName() {
        // Given
        Skill skill = createValidSkill();
        skill.setName("invalid-name-123"); // Contains numbers
        String namespaceId = "test-namespace";
        
        // When & Then
        assertThrows(NacosApiException.class,
                () -> skillOperationService.registerSkill(skill, namespaceId));
    }
    
    @Test
    void testRegisterSkillWithDoubleUnderscore() throws NacosException {
        // Given: skill name and resource names may contain double underscores
        Skill skill = createValidSkill();
        skill.setName("test__skill"); // Contains double underscore
        String namespaceId = "test-namespace";
        when(configOperationService.publishConfig(any(ConfigForm.class),
                any(ConfigRequestInfo.class), isNull()))
                .thenReturn(Boolean.TRUE);
        
        // When
        String result = skillOperationService.registerSkill(skill, namespaceId);
        
        // Then
        assertEquals("test__skill", result);
    }
    
    @Test
    void testRegisterSkillAlreadyExists() throws NacosException {
        // Given
        Skill skill = createValidSkill();
        String namespaceId = "test-namespace";
        when(configOperationService.publishConfig(any(ConfigForm.class),
                any(ConfigRequestInfo.class), isNull()))
                .thenThrow(new ConfigAlreadyExistsException("Config already exists"));
        
        // When & Then
        NacosApiException exception = assertThrows(NacosApiException.class,
                () -> skillOperationService.registerSkill(skill, namespaceId));
        assertEquals(NacosException.CONFLICT, exception.getErrCode());
    }
    
    @Test
    void testGetSkillDetailSuccessfully() throws NacosException {
        // Given
        String namespaceId = "test-namespace";
        String skillName = "test-skill";
        ConfigQueryChainResponse response = createMockConfigResponse();
        when(configQueryChainService.handle(any(ConfigQueryChainRequest.class)))
                .thenReturn(response);
        
        // When
        Skill skill = skillOperationService.getSkillDetail(namespaceId, skillName);
        
        // Then
        assertNotNull(skill);
        assertEquals(skillName, skill.getName());
        assertEquals("Test description", skill.getDescription());
    }
    
    @Test
    void testGetSkillDetailNotFound() {
        // Given
        String namespaceId = "test-namespace";
        String skillName = "non-existent-skill";
        ConfigQueryChainResponse response = new ConfigQueryChainResponse();
        response.setStatus(ConfigQueryChainResponse.ConfigQueryStatus.CONFIG_NOT_FOUND);
        when(configQueryChainService.handle(any(ConfigQueryChainRequest.class)))
                .thenReturn(response);
        
        // When & Then
        NacosApiException exception = assertThrows(NacosApiException.class,
                () -> skillOperationService.getSkillDetail(namespaceId, skillName));
        assertEquals(NacosException.NOT_FOUND, exception.getErrCode());
    }
    
    @Test
    void testUpdateSkillSuccessfully() throws NacosException {
        // Given
        Skill skill = createValidSkill();
        String namespaceId = "test-namespace";
        ConfigQueryChainResponse response = createMockConfigResponse();
        when(configQueryChainService.handle(any(ConfigQueryChainRequest.class)))
                .thenReturn(response);
        when(configOperationService.publishConfig(any(ConfigForm.class),
                any(ConfigRequestInfo.class), isNull()))
                .thenReturn(Boolean.TRUE);
        
        // When
        skillOperationService.updateSkill(skill, namespaceId);
        
        // Then
        ArgumentCaptor<ConfigRequestInfo> requestInfoCaptor = ArgumentCaptor.forClass(ConfigRequestInfo.class);
        verify(configOperationService, times(1)).publishConfig(any(ConfigForm.class),
                requestInfoCaptor.capture(), isNull());
        assertEquals(Boolean.TRUE, requestInfoCaptor.getValue().getUpdateForExist());
    }
    
    @Test
    void testUpdateSkillNotFound() {
        // Given
        Skill skill = createValidSkill();
        String namespaceId = "test-namespace";
        ConfigQueryChainResponse response = new ConfigQueryChainResponse();
        response.setStatus(ConfigQueryChainResponse.ConfigQueryStatus.CONFIG_NOT_FOUND);
        when(configQueryChainService.handle(any(ConfigQueryChainRequest.class)))
                .thenReturn(response);
        
        // When & Then
        assertThrows(NacosApiException.class,
                () -> skillOperationService.updateSkill(skill, namespaceId));
    }
    
    @Test
    void testDeleteSkillSuccessfully() throws NacosException {
        // Given
        String namespaceId = "test-namespace";
        String skillName = "test-skill";
        ConfigQueryChainResponse response = createMockConfigResponse();
        when(configQueryChainService.handle(any(ConfigQueryChainRequest.class)))
                .thenReturn(response);
        
        // When
        skillOperationService.deleteSkill(namespaceId, skillName);
        
        // Then
        verify(configOperationService, times(1)).deleteConfig(eq("skill.json"),
                eq("skill_" + skillName), eq(namespaceId), isNull(), isNull(), eq("nacos"), isNull());
    }
    
    @Test
    void testDeleteSkillAlreadyDeleted() throws NacosException {
        // Given
        String namespaceId = "test-namespace";
        String skillName = "test-skill";
        ConfigQueryChainResponse response = new ConfigQueryChainResponse();
        response.setStatus(ConfigQueryChainResponse.ConfigQueryStatus.CONFIG_NOT_FOUND);
        when(configQueryChainService.handle(any(ConfigQueryChainRequest.class)))
                .thenReturn(response);
        
        // When
        skillOperationService.deleteSkill(namespaceId, skillName);
        
        // Then
        verify(configOperationService, never()).deleteConfig(anyString(), anyString(),
                anyString(), isNull(), isNull(), anyString(), isNull());
    }
    
    @Test
    void testListSkillsSuccessfully() throws NacosException {
        // Given
        String namespaceId = "test-namespace";
        Page<ConfigInfo> configInfoPage = createMockConfigInfoPage();
        when(configInfoPersistService.findConfigInfoLike4Page(eq(1), eq(10), eq("skill.json"),
                anyString(), eq(namespaceId), isNull()))
                .thenReturn(configInfoPage);
        
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
        when(configOperationService.publishConfig(any(ConfigForm.class),
                any(ConfigRequestInfo.class), isNull()))
                .thenReturn(Boolean.TRUE);
        
        // When
        String result = skillOperationService.uploadSkillFromZip(namespaceId, zipBytes);
        
        // Then
        assertNotNull(result);
        verify(configOperationService, times(1)).publishConfig(any(ConfigForm.class),
                any(ConfigRequestInfo.class), isNull());
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
    
    /**
     * Create a mock config response.
     */
    private ConfigQueryChainResponse createMockConfigResponse() {
        ConfigQueryChainResponse response = new ConfigQueryChainResponse();
        response.setStatus(ConfigQueryChainResponse.ConfigQueryStatus.CONFIG_FOUND_FORMAL);
        Map<String, Object> mainConfig = new HashMap<>();
        mainConfig.put("name", "test-skill");
        mainConfig.put("description", "Test description");
        mainConfig.put("instruction", "Test instruction");
        mainConfig.put("resource", new HashMap<>());
        response.setContent(JacksonUtils.toJson(mainConfig));
        return response;
    }
    
    /**
     * Create a mock config info page.
     */
    private Page<ConfigInfo> createMockConfigInfoPage() {
        Page<ConfigInfo> page = new Page<>();
        ConfigInfo configInfo = new ConfigInfo();
        configInfo.setDataId("skill.json");
        configInfo.setGroup("skill_test-skill");
        Map<String, Object> mainConfig = new HashMap<>();
        mainConfig.put("name", "test-skill");
        mainConfig.put("description", "Test description");
        mainConfig.put("instruction", "Test instruction");
        mainConfig.put("resource", new HashMap<>());
        configInfo.setContent(JacksonUtils.toJson(mainConfig));
        page.setPageItems(List.of(configInfo));
        page.setTotalCount(1);
        page.setPageNumber(1);
        page.setPagesAvailable(1);
        return page;
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
