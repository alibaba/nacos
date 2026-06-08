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

package com.alibaba.nacos.console.controller.v3.ai;

import com.alibaba.nacos.ai.constant.Constants;
import com.alibaba.nacos.ai.form.AiResourceFilterableForm;
import com.alibaba.nacos.ai.form.skills.admin.SkillListForm;
import com.alibaba.nacos.ai.form.skills.admin.SkillPublishForm;
import com.alibaba.nacos.ai.param.SkillListHttpParamExtractor;
import com.alibaba.nacos.api.ai.model.skills.Skill;
import com.alibaba.nacos.api.ai.model.skills.SkillMeta;
import com.alibaba.nacos.api.ai.model.skills.SkillSummary;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.console.proxy.ai.SkillProxy;
import com.alibaba.nacos.core.model.form.PageForm;
import com.alibaba.nacos.core.paramcheck.ExtractorManager;
import com.alibaba.nacos.sys.env.EnvUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConsoleSkillControllerTest {
    
    private static final String NS = "public";
    
    private static final String SKILL_NAME = "test-skill";
    
    private static final String VERSION = "v1";
    
    private static final String BASE_PATH = Constants.Skills.CONSOLE_PATH;
    
    @Mock
    private SkillProxy skillProxy;
    
    private MockMvc mockMvc;
    
    @BeforeEach
    void setUp() {
        EnvUtil.setEnvironment(new StandardEnvironment());
        mockMvc = MockMvcBuilders.standaloneSetup(
            new ConsoleSkillController(skillProxy)).build();
    }
    
    @Test
    void testGetSkill() throws Exception {
        SkillMeta meta = new SkillMeta();
        meta.setName(SKILL_NAME);
        when(skillProxy.getSkill(any())).thenReturn(meta);
        
        MockHttpServletResponse response = mockMvc.perform(
            MockMvcRequestBuilders.get(BASE_PATH)
                .param("namespaceId", NS).param("skillName", SKILL_NAME))
            .andReturn().getResponse();
        
        assertEquals(200, response.getStatus());
        Result<SkillMeta> result = JacksonUtils.toObj(
            response.getContentAsString(), new TypeReference<>() {
            });
        assertEquals(SKILL_NAME, result.getData().getName());
    }
    
    @Test
    void testGetSkillVersion() throws Exception {
        Skill skill = new Skill();
        skill.setName(SKILL_NAME);
        when(skillProxy.getSkillVersion(any())).thenReturn(skill);
        
        MockHttpServletResponse response = mockMvc.perform(
            MockMvcRequestBuilders.get(BASE_PATH + "/version")
                .param("namespaceId", NS).param("skillName", SKILL_NAME)
                .param("version", VERSION))
            .andReturn().getResponse();
        
        assertEquals(200, response.getStatus());
        Result<Skill> result = JacksonUtils.toObj(
            response.getContentAsString(), new TypeReference<>() {
            });
        assertEquals(SKILL_NAME, result.getData().getName());
    }
    
    @Test
    void testDownloadSkillVersion() throws Exception {
        Skill skill = new Skill();
        skill.setName(SKILL_NAME);
        when(skillProxy.downloadSkillVersion(any())).thenReturn(skill);
        
        MockHttpServletResponse response = mockMvc.perform(
            MockMvcRequestBuilders.get(BASE_PATH + "/version/download")
                .param("namespaceId", NS).param("skillName", SKILL_NAME)
                .param("version", VERSION))
            .andReturn().getResponse();
        
        assertEquals(200, response.getStatus());
        assertNotNull(response.getContentType());
    }
    
    @Test
    void testDeleteSkill() throws Exception {
        doNothing().when(skillProxy).deleteSkill(any());
        
        MockHttpServletResponse response = mockMvc.perform(
            MockMvcRequestBuilders.delete(BASE_PATH)
                .param("namespaceId", NS).param("skillName", SKILL_NAME))
            .andReturn().getResponse();
        
        assertEquals(200, response.getStatus());
        Result<String> result = JacksonUtils.toObj(
            response.getContentAsString(), new TypeReference<>() {
            });
        assertEquals("ok", result.getData());
    }
    
    @Test
    void testRedraftSuccess() throws Exception {
        doNothing().when(skillProxy).redraft(any(SkillPublishForm.class));
        
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.post(
            Constants.Skills.CONSOLE_PATH + "/redraft").param("namespaceId", "test-ns")
            .param("skillName", "test-skill").param("version", "v1");
        
        MockHttpServletResponse response = mockMvc.perform(builder).andReturn().getResponse();
        String content = response.getContentAsString();
        Result<String> result = JacksonUtils.toObj(content, new TypeReference<>() {
        });
        
        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertEquals("ok", result.getData());
        verify(skillProxy).redraft(any(SkillPublishForm.class));
    }
    
    @Test
    void testListSkillsSuccess() throws Exception {
        Page<SkillSummary> page = new Page<>();
        page.setTotalCount(1);
        page.setPagesAvailable(1);
        SkillSummary item = new SkillSummary();
        item.setName(SKILL_NAME);
        page.setPageItems(List.of(item));
        when(skillProxy.listSkills(any(), any(), any())).thenReturn(page);
        
        MockHttpServletResponse response = mockMvc.perform(
            MockMvcRequestBuilders.get(BASE_PATH + "/list")
                .param("pageNo", "1").param("pageSize", "10"))
            .andReturn().getResponse();
        
        Result<Page<SkillSummary>> result = JacksonUtils.toObj(
            response.getContentAsString(), new TypeReference<>() {
            });
        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertEquals(1, result.getData().getTotalCount());
    }
    
    @Test
    void testListSkillsUsesListParamExtractor() throws Exception {
        ExtractorManager.Extractor extractor = ConsoleSkillController.class
            .getMethod("listSkills", SkillListForm.class,
                AiResourceFilterableForm.class, PageForm.class)
            .getAnnotation(ExtractorManager.Extractor.class);
        
        assertNotNull(extractor);
        assertEquals(SkillListHttpParamExtractor.class, extractor.httpExtractor());
    }
    
    @Test
    void testListSkillsWithOwnerFilter() throws Exception {
        Page<SkillSummary> page = new Page<>();
        page.setTotalCount(0);
        page.setPageItems(Collections.emptyList());
        when(skillProxy.listSkills(any(), any(), any())).thenReturn(page);
        
        MockHttpServletResponse response = mockMvc.perform(
            MockMvcRequestBuilders.get(BASE_PATH + "/list")
                .param("owner", "alice").param("pageNo", "1")
                .param("pageSize", "10"))
            .andReturn().getResponse();
        
        assertEquals(200, response.getStatus());
        verify(skillProxy).listSkills(any(), any(), any());
    }
    
    @Test
    void testUploadSkill() throws Exception {
        when(skillProxy.uploadSkillFromZip(argThat(request -> request != null
            && NS.equals(request.getNamespaceId()) && !request.isOverwrite()
            && request.getTargetVersion() == null
            && "upload commit".equals(request.getCommitMsg())
            && request.getZipBytes() != null))).thenReturn(SKILL_NAME);
        
        MockMultipartFile file = new MockMultipartFile("file", "skill.zip",
            "application/zip", new byte[] {0x50, 0x4B, 0x03, 0x04, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0});
        
        MockHttpServletResponse response = mockMvc.perform(
            MockMvcRequestBuilders.multipart(BASE_PATH + "/upload").file(file)
                .param("namespaceId", NS).param("overwrite", "false")
                .param("commitMsg", "upload commit"))
            .andReturn().getResponse();
        
        assertEquals(200, response.getStatus());
        Result<String> result = JacksonUtils.toObj(
            response.getContentAsString(), new TypeReference<>() {
            });
        assertEquals(SKILL_NAME, result.getData());
    }
    
    @Test
    void testCreateDraft() throws Exception {
        when(skillProxy.createDraft(any())).thenReturn("v1-draft");
        
        MockHttpServletResponse response = mockMvc.perform(
            MockMvcRequestBuilders.post(BASE_PATH + "/draft")
                .param("namespaceId", NS).param("skillName", SKILL_NAME)
                .param("basedOnVersion", "v1"))
            .andReturn().getResponse();
        
        assertEquals(200, response.getStatus());
        Result<String> result = JacksonUtils.toObj(
            response.getContentAsString(), new TypeReference<>() {
            });
        assertEquals("v1-draft", result.getData());
    }
    
    @Test
    void testUpdateDraft() throws Exception {
        doNothing().when(skillProxy).updateDraft(any());
        
        MockHttpServletResponse response = mockMvc.perform(
            MockMvcRequestBuilders.put(BASE_PATH + "/draft")
                .param("namespaceId", NS).param("skillName", SKILL_NAME)
                .param("version", VERSION)
                .param("skillCard", "{\"name\":\"test-skill\"}"))
            .andReturn().getResponse();
        
        assertEquals(200, response.getStatus());
        Result<String> result = JacksonUtils.toObj(
            response.getContentAsString(), new TypeReference<>() {
            });
        assertEquals("ok", result.getData());
    }
    
    @Test
    void testDeleteDraft() throws Exception {
        doNothing().when(skillProxy).deleteDraft(any());
        
        MockHttpServletResponse response = mockMvc.perform(
            MockMvcRequestBuilders.delete(BASE_PATH + "/draft")
                .param("namespaceId", NS).param("skillName", SKILL_NAME))
            .andReturn().getResponse();
        
        assertEquals(200, response.getStatus());
        Result<String> result = JacksonUtils.toObj(
            response.getContentAsString(), new TypeReference<>() {
            });
        assertEquals("ok", result.getData());
    }
    
    @Test
    void testSubmit() throws Exception {
        when(skillProxy.submit(any())).thenReturn("reviewing");
        
        MockHttpServletResponse response = mockMvc.perform(
            MockMvcRequestBuilders.post(BASE_PATH + "/submit")
                .param("namespaceId", NS).param("skillName", SKILL_NAME)
                .param("version", VERSION))
            .andReturn().getResponse();
        
        assertEquals(200, response.getStatus());
        Result<String> result = JacksonUtils.toObj(
            response.getContentAsString(), new TypeReference<>() {
            });
        assertEquals("reviewing", result.getData());
    }
    
    @Test
    void testPublish() throws Exception {
        doNothing().when(skillProxy).publish(any());
        
        MockHttpServletResponse response = mockMvc.perform(
            MockMvcRequestBuilders.post(BASE_PATH + "/publish")
                .param("namespaceId", NS).param("skillName", SKILL_NAME)
                .param("version", VERSION))
            .andReturn().getResponse();
        
        assertEquals(200, response.getStatus());
        verify(skillProxy).publish(any());
    }
    
    @Test
    void testForcePublishSuccess() throws Exception {
        doNothing().when(skillProxy).forcePublish(any());
        
        MockHttpServletResponse response = mockMvc.perform(
            MockMvcRequestBuilders.post(BASE_PATH + "/force-publish")
                .param("namespaceId", NS).param("skillName", SKILL_NAME)
                .param("version", VERSION))
            .andReturn().getResponse();
        
        Result<String> result = JacksonUtils.toObj(
            response.getContentAsString(), new TypeReference<>() {
            });
        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertEquals("ok", result.getData());
    }
    
    @Test
    void testUpdateLabels() throws Exception {
        doNothing().when(skillProxy).updateLabels(any());
        
        MockHttpServletResponse response = mockMvc.perform(
            MockMvcRequestBuilders.put(BASE_PATH + "/labels")
                .param("namespaceId", NS).param("skillName", SKILL_NAME)
                .param("labels", "{\"env\":\"prod\"}"))
            .andReturn().getResponse();
        
        assertEquals(200, response.getStatus());
        verify(skillProxy).updateLabels(any());
    }
    
    @Test
    void testUpdateBizTags() throws Exception {
        doNothing().when(skillProxy).updateBizTags(any());
        
        MockHttpServletResponse response = mockMvc.perform(
            MockMvcRequestBuilders.put(BASE_PATH + "/biz-tags")
                .param("namespaceId", NS).param("skillName", SKILL_NAME)
                .param("bizTags", "tag1,tag2"))
            .andReturn().getResponse();
        
        assertEquals(200, response.getStatus());
        verify(skillProxy).updateBizTags(any());
    }
    
    @Test
    void testOnline() throws Exception {
        doNothing().when(skillProxy).online(any());
        
        MockHttpServletResponse response = mockMvc.perform(
            MockMvcRequestBuilders.post(BASE_PATH + "/online")
                .param("namespaceId", NS).param("skillName", SKILL_NAME)
                .param("version", VERSION))
            .andReturn().getResponse();
        
        assertEquals(200, response.getStatus());
        verify(skillProxy).online(any());
    }
    
    @Test
    void testOffline() throws Exception {
        doNothing().when(skillProxy).offline(any());
        
        MockHttpServletResponse response = mockMvc.perform(
            MockMvcRequestBuilders.post(BASE_PATH + "/offline")
                .param("namespaceId", NS).param("skillName", SKILL_NAME)
                .param("version", VERSION))
            .andReturn().getResponse();
        
        assertEquals(200, response.getStatus());
        verify(skillProxy).offline(any());
    }
    
    @Test
    void testUpdateScope() throws Exception {
        doNothing().when(skillProxy).updateScope(any());
        
        MockHttpServletResponse response = mockMvc.perform(
            MockMvcRequestBuilders.put(BASE_PATH + "/scope")
                .param("namespaceId", NS).param("skillName", SKILL_NAME)
                .param("scope", "PUBLIC"))
            .andReturn().getResponse();
        
        assertEquals(200, response.getStatus());
        verify(skillProxy).updateScope(any());
    }
}
