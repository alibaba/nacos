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

package com.alibaba.nacos.maintainer.client.ai;

import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.ai.model.skills.Skill;
import com.alibaba.nacos.api.ai.model.skills.SkillMeta;
import com.alibaba.nacos.api.ai.model.skills.SkillSummary;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.common.http.HttpRestResult;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.maintainer.client.model.HttpRequest;
import com.alibaba.nacos.maintainer.client.remote.ClientHttpProxy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Unit tests for SkillMaintainerService interface default methods.
 * Tests the convenience methods that provide simplified parameter signatures.
 *
 * @author nacos
 */
@ExtendWith(MockitoExtension.class)
class SkillMaintainerServiceDefaultMethodsTest {
    
    @Mock
    private ClientHttpProxy clientHttpProxy;
    
    private SkillMaintainerService skillService;
    
    @BeforeEach
    void setUp() throws NacosException, NoSuchFieldException, IllegalAccessException {
        Properties properties = new Properties();
        properties.put(PropertyKeyConst.SERVER_ADDR, "127.0.0.1:8848");
        AiMaintainerService aiMaintainerService =
            AiMaintainerFactory.createAiMaintainerService(properties);
        
        Field skillServiceField =
            NacosAiMaintainerServiceImpl.class.getDeclaredField("skillMaintainerService");
        skillServiceField.setAccessible(true);
        skillService = (SkillMaintainerService) skillServiceField.get(aiMaintainerService);
        
        injectMockClientHttpProxy(skillService);
    }
    
    private void injectMockClientHttpProxy(Object service)
        throws NoSuchFieldException, IllegalAccessException {
        Field contextField = AbstractAiDelegateMaintainerService.class.getDeclaredField("context");
        contextField.setAccessible(true);
        Object context = contextField.get(service);
        Field clientHttpProxyField =
            AiMaintainerHttpContext.class.getDeclaredField("clientHttpProxy");
        clientHttpProxyField.setAccessible(true);
        clientHttpProxyField.set(context, clientHttpProxy);
    }
    
    // ========== getSkillMeta default method tests ==========
    
    @Test
    @DisplayName("getSkillMeta(skillName) should use default namespace")
    void testGetSkillMetaWithDefaultNamespace() throws NacosException {
        SkillMeta meta = new SkillMeta();
        meta.setEditingVersion("v1");
        
        HttpRestResult<String> mockResult = new HttpRestResult<>();
        mockResult.setData(JacksonUtils.toJson(Result.success(meta)));
        when(clientHttpProxy.executeSyncHttpRequest(any(HttpRequest.class))).thenReturn(mockResult);
        
        SkillMeta result = skillService.getSkillMeta("testSkill");
        assertNotNull(result);
        assertEquals("v1", result.getEditingVersion());
    }
    
    // ========== getSkillVersionDetail default method tests ==========
    
    @Test
    @DisplayName("getSkillVersionDetail(skillName, version) should use default namespace")
    void testGetSkillVersionDetailWithDefaultNamespace() throws NacosException {
        Skill skill = new Skill();
        skill.setName("testSkill");
        
        HttpRestResult<String> mockResult = new HttpRestResult<>();
        mockResult.setData(JacksonUtils.toJson(Result.success(skill)));
        when(clientHttpProxy.executeSyncHttpRequest(any(HttpRequest.class))).thenReturn(mockResult);
        
        Skill result = skillService.getSkillVersionDetail("testSkill", "v1");
        assertNotNull(result);
        assertEquals("testSkill", result.getName());
    }
    
    // ========== deleteSkill default method tests ==========
    
    @Test
    @DisplayName("deleteSkill(skillName) should use default namespace")
    void testDeleteSkillWithDefaultNamespace() throws NacosException {
        HttpRestResult<String> mockResult = new HttpRestResult<>();
        mockResult.setData(JacksonUtils.toJson(Result.success("ok")));
        when(clientHttpProxy.executeSyncHttpRequest(any(HttpRequest.class))).thenReturn(mockResult);
        
        boolean result = skillService.deleteSkill("testSkill");
        assertTrue(result);
    }
    
    // ========== listSkills default method tests ==========
    
    @Test
    @DisplayName("listSkills(skillName, pageNo, pageSize) should use default namespace and blur search")
    void testListSkillsWithDefaults() throws NacosException {
        Page<SkillSummary> page = new Page<>();
        page.setTotalCount(1);
        page.setPageItems(Collections.singletonList(new SkillSummary()));
        
        HttpRestResult<String> mockResult = new HttpRestResult<>();
        mockResult.setData(JacksonUtils.toJson(Result.success(page)));
        when(clientHttpProxy.executeSyncHttpRequest(any(HttpRequest.class))).thenReturn(mockResult);
        
        Page<SkillSummary> result = skillService.listSkills("testSkill", 1, 10);
        assertNotNull(result);
        assertEquals(1, result.getTotalCount());
    }
    
    @Test
    @DisplayName("listSkills with orderBy/owner/scope should delegate to base version")
    void testListSkillsWithFilters() throws NacosException {
        Page<SkillSummary> page = new Page<>();
        page.setTotalCount(0);
        
        HttpRestResult<String> mockResult = new HttpRestResult<>();
        mockResult.setData(JacksonUtils.toJson(Result.success(page)));
        when(clientHttpProxy.executeSyncHttpRequest(any(HttpRequest.class))).thenReturn(mockResult);
        
        Page<SkillSummary> result = skillService.listSkills("public", "test", "blur",
            "download_count", "alice", "PUBLIC", 1, 10);
        assertNotNull(result);
    }
    
    @Test
    @DisplayName("listSkills with bizTag should delegate to base version")
    void testListSkillsWithBizTag() throws NacosException {
        Page<SkillSummary> page = new Page<>();
        page.setTotalCount(0);
        
        HttpRestResult<String> mockResult = new HttpRestResult<>();
        mockResult.setData(JacksonUtils.toJson(Result.success(page)));
        when(clientHttpProxy.executeSyncHttpRequest(any(HttpRequest.class))).thenReturn(mockResult);
        
        Page<SkillSummary> result =
            skillService.listSkills("public", "test", "blur", null, null, null, "retail", 1, 10);
        assertNotNull(result);
    }
    
    // ========== uploadSkillFromZip default method tests ==========
    
    @Test
    @DisplayName("uploadSkillFromZip(zipBytes) should use default namespace and not overwrite")
    void testUploadSkillFromZipWithDefaults() throws NacosException {
        byte[] zipBytes = "test zip content".getBytes();
        
        HttpRestResult<String> mockResult = new HttpRestResult<>();
        mockResult.setData(JacksonUtils.toJson(Result.success("uploadedSkill")));
        when(clientHttpProxy.executeSyncHttpRequest(any(HttpRequest.class))).thenReturn(mockResult);
        
        String result = skillService.uploadSkillFromZip(zipBytes);
        assertEquals("uploadedSkill", result);
    }
    
    @Test
    @DisplayName("uploadSkillFromZip(namespaceId, zipBytes) should not overwrite")
    void testUploadSkillFromZipWithNamespace() throws NacosException {
        byte[] zipBytes = "test zip content".getBytes();
        
        HttpRestResult<String> mockResult = new HttpRestResult<>();
        mockResult.setData(JacksonUtils.toJson(Result.success("uploadedSkill")));
        when(clientHttpProxy.executeSyncHttpRequest(any(HttpRequest.class))).thenReturn(mockResult);
        
        String result = skillService.uploadSkillFromZip("public", zipBytes);
        assertEquals("uploadedSkill", result);
    }
    
    // ========== createDraft default method tests ==========
    
    @Test
    @DisplayName("createDraft(namespaceId, skillCard) should create new skill")
    void testCreateDraftWithSkillCard() throws NacosException {
        HttpRestResult<String> mockResult = new HttpRestResult<>();
        mockResult.setData(JacksonUtils.toJson(Result.success("v1")));
        when(clientHttpProxy.executeSyncHttpRequest(any(HttpRequest.class))).thenReturn(mockResult);
        
        String result = skillService.createDraft("public", "{}");
        assertEquals("v1", result);
    }
    
    @Test
    @DisplayName("createDraft(namespaceId, skillName, basedOnVersion) should fork")
    void testCreateDraftFork() throws NacosException {
        HttpRestResult<String> mockResult = new HttpRestResult<>();
        mockResult.setData(JacksonUtils.toJson(Result.success("v2")));
        when(clientHttpProxy.executeSyncHttpRequest(any(HttpRequest.class))).thenReturn(mockResult);
        
        String result = skillService.createDraft("public", "testSkill", "v1");
        assertEquals("v2", result);
    }
    
    @Test
    @DisplayName("createDraft(namespaceId, skillName, basedOnVersion, targetVersion) should fork with target")
    void testCreateDraftForkWithTarget() throws NacosException {
        HttpRestResult<String> mockResult = new HttpRestResult<>();
        mockResult.setData(JacksonUtils.toJson(Result.success("v2")));
        when(clientHttpProxy.executeSyncHttpRequest(any(HttpRequest.class))).thenReturn(mockResult);
        
        String result = skillService.createDraft("public", "testSkill", "v1", "v2");
        assertEquals("v2", result);
    }
}
