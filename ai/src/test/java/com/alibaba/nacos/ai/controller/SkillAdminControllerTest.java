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

package com.alibaba.nacos.ai.controller;

import com.alibaba.nacos.ai.constant.Constants;
import com.alibaba.nacos.ai.form.AiResourceFilterableForm;
import com.alibaba.nacos.ai.form.skills.admin.SkillListForm;
import com.alibaba.nacos.ai.param.SkillListHttpParamExtractor;
import com.alibaba.nacos.ai.service.skills.SkillOperationService;
import com.alibaba.nacos.api.ai.model.skills.Skill;
import com.alibaba.nacos.api.ai.model.skills.SkillMeta;
import com.alibaba.nacos.api.ai.model.skills.SkillSummary;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.core.model.form.PageForm;
import com.alibaba.nacos.core.paramcheck.ExtractorManager;
import com.alibaba.nacos.sys.env.EnvUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Test for {@link SkillAdminController}.
 *
 * @author nacos
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = MockServletContext.class)
@WebAppConfiguration
class SkillAdminControllerTest {
    
    private static final String SKILL_ADMIN_PATH = Constants.Skills.ADMIN_PATH;
    
    private SkillAdminController skillAdminController;
    
    private MockMvc mockMvc;
    
    private ConfigurableEnvironment cachedEnvironment;
    
    @MockitoBean
    private SkillOperationService skillOperationService;
    
    @BeforeEach
    void setUp() {
        cachedEnvironment = EnvUtil.getEnvironment();
        EnvUtil.setEnvironment(new StandardEnvironment());
        skillAdminController = new SkillAdminController(skillOperationService);
        mockMvc = MockMvcBuilders.standaloneSetup(skillAdminController).build();
    }
    
    @AfterEach
    void tearDown() {
        EnvUtil.setEnvironment(cachedEnvironment);
    }
    
    @Test
    void testGetSkillWithoutSkillName() throws Throwable {
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.get(SKILL_ADMIN_PATH);
        assertServletException(NacosApiException.class, () -> mockMvc.perform(builder).andReturn(),
            "Required parameter 'skillName' type String is not present");
    }
    
    @Test
    void testGetSkillSuccess() throws Exception {
        SkillMeta detail = new SkillMeta();
        detail.setEnable(true);
        detail.setOnlineCnt(2);
        when(skillOperationService.getSkillDetail(eq("public"), eq("test-skill")))
            .thenReturn(detail);
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.get(SKILL_ADMIN_PATH)
            .param("skillName", "test-skill");
        MockHttpServletResponse response = mockMvc.perform(builder).andReturn().getResponse();
        assertEquals(200, response.getStatus());
        Result<SkillMeta> result =
            JacksonUtils.toObj(response.getContentAsString(), new TypeReference<>() {
            });
        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertNotNull(result.getData());
    }
    
    @Test
    void testGetSkillVersionSuccess() throws Exception {
        Skill skill = new Skill();
        skill.setName("test-skill");
        skill.setSkillMd("---\nname: test-skill\ndescription: d\n---\n\nhello");
        when(skillOperationService.getSkillVersionDetail(eq("public"), eq("test-skill"), eq("v1")))
            .thenReturn(skill);
        MockHttpServletRequestBuilder builder =
            MockMvcRequestBuilders.get(SKILL_ADMIN_PATH + "/version")
                .param("skillName", "test-skill").param("version", "v1");
        MockHttpServletResponse response = mockMvc.perform(builder).andReturn().getResponse();
        assertEquals(200, response.getStatus());
        Result<Skill> result =
            JacksonUtils.toObj(response.getContentAsString(), new TypeReference<>() {
            });
        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertEquals("test-skill", result.getData().getName());
    }
    
    @Test
    void testDeleteSkillSuccess() throws Exception {
        doNothing().when(skillOperationService).deleteSkill(eq("public"), eq("test-skill"));
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.delete(SKILL_ADMIN_PATH)
            .param("skillName", "test-skill");
        MockHttpServletResponse response = mockMvc.perform(builder).andReturn().getResponse();
        assertEquals(200, response.getStatus());
        verify(skillOperationService).deleteSkill("public", "test-skill");
    }
    
    @Test
    void testDeleteSkillWithoutSkillName() throws Throwable {
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.delete(SKILL_ADMIN_PATH);
        assertServletException(NacosApiException.class, () -> mockMvc.perform(builder).andReturn(),
            "Required parameter 'skillName' type String is not present");
    }
    
    @Test
    void testListSkillsSuccess() throws Exception {
        Page<SkillSummary> page = new Page<>();
        page.setTotalCount(1);
        page.setPagesAvailable(1);
        SkillSummary item = new SkillSummary();
        item.setName("test-skill");
        page.setPageItems(Collections.singletonList(item));
        when(skillOperationService.listSkills(eq("public"), isNull(), isNull(), isNull(), isNull(),
            isNull(), isNull(), eq(1),
            eq(10))).thenReturn(page);
        MockHttpServletRequestBuilder builder =
            MockMvcRequestBuilders.get(SKILL_ADMIN_PATH + "/list")
                .param("pageNo", "1").param("pageSize", "10");
        MockHttpServletResponse response = mockMvc.perform(builder).andReturn().getResponse();
        assertEquals(200, response.getStatus());
        Result<Page<SkillSummary>> result =
            JacksonUtils.toObj(response.getContentAsString(), new TypeReference<>() {
            });
        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertEquals(1, result.getData().getTotalCount());
    }
    
    @Test
    void testListSkillsUsesListParamExtractor() throws Exception {
        ExtractorManager.Extractor extractor = SkillAdminController.class
            .getMethod("listSkills", SkillListForm.class,
                AiResourceFilterableForm.class, PageForm.class)
            .getAnnotation(ExtractorManager.Extractor.class);
        
        assertNotNull(extractor);
        assertEquals(SkillListHttpParamExtractor.class, extractor.httpExtractor());
    }
    
    @Test
    void testListSkillsWithOwnerFilter() throws Exception {
        Page<SkillSummary> page = new Page<>();
        page.setTotalCount(1);
        page.setPagesAvailable(1);
        SkillSummary item = new SkillSummary();
        item.setName("test-skill");
        page.setPageItems(Collections.singletonList(item));
        when(skillOperationService.listSkills(eq("public"), isNull(), isNull(), isNull(),
            eq("alice"), isNull(), isNull(), eq(1),
            eq(10))).thenReturn(page);
        MockHttpServletRequestBuilder builder =
            MockMvcRequestBuilders.get(SKILL_ADMIN_PATH + "/list")
                .param("owner", "alice").param("pageNo", "1").param("pageSize", "10");
        MockHttpServletResponse response = mockMvc.perform(builder).andReturn().getResponse();
        assertEquals(200, response.getStatus());
        verify(skillOperationService).listSkills("public", null, null, null, "alice", null, null, 1,
            10);
    }
    
    @Test
    void testListSkillsWithScopeFilter() throws Exception {
        Page<SkillSummary> page = new Page<>();
        page.setTotalCount(0);
        page.setPageItems(Collections.emptyList());
        when(skillOperationService.listSkills(eq("public"), isNull(), isNull(), isNull(), isNull(),
            eq("PRIVATE"),
            isNull(), eq(1), eq(10))).thenReturn(page);
        MockHttpServletRequestBuilder builder =
            MockMvcRequestBuilders.get(SKILL_ADMIN_PATH + "/list")
                .param("scope", "PRIVATE").param("pageNo", "1").param("pageSize", "10");
        MockHttpServletResponse response = mockMvc.perform(builder).andReturn().getResponse();
        assertEquals(200, response.getStatus());
        verify(skillOperationService).listSkills("public", null, null, null, null, "PRIVATE", null,
            1, 10);
    }
    
    @Test
    void testListSkillsWithInvalidScope() throws Throwable {
        MockHttpServletRequestBuilder builder =
            MockMvcRequestBuilders.get(SKILL_ADMIN_PATH + "/list")
                .param("scope", "INVALID").param("pageNo", "1").param("pageSize", "10");
        assertServletException(NacosApiException.class, () -> mockMvc.perform(builder).andReturn(),
            "must be PUBLIC or PRIVATE");
    }
    
    @Test
    void testListSkillsWithIllegalSearch() throws Throwable {
        MockHttpServletRequestBuilder builder =
            MockMvcRequestBuilders.get(SKILL_ADMIN_PATH + "/list")
                .param("search", "illegal").param("pageNo", "1").param("pageSize", "10");
        assertServletException(NacosApiException.class, () -> mockMvc.perform(builder).andReturn(),
            "Request parameter `search` should be `accurate` or `blur`.");
    }
    
    @Test
    void testListSkillsWithIllegalPage() throws Throwable {
        MockHttpServletRequestBuilder builder =
            MockMvcRequestBuilders.get(SKILL_ADMIN_PATH + "/list")
                .param("pageNo", "-1").param("pageSize", "10");
        assertServletException(NacosApiException.class, () -> mockMvc.perform(builder).andReturn(),
            "pageNo");
    }
    
    @Test
    void testCreateDraftSuccess() throws Exception {
        when(skillOperationService.createDraft(eq("public"), eq("test-skill"), isNull(), isNull(),
            any(Skill.class), isNull())).thenReturn("v1");
        String skillCard =
            "{\"name\":\"test-skill\",\"description\":\"d\",\"skillMd\":\"---\\nname: test-skill\\ndescription: d\\n---\\n\\ni\"}";
        MockHttpServletRequestBuilder builder =
            MockMvcRequestBuilders.post(SKILL_ADMIN_PATH + "/draft")
                .param("skillCard", skillCard);
        MockHttpServletResponse response = mockMvc.perform(builder).andReturn().getResponse();
        assertEquals(200, response.getStatus());
        Result<String> result =
            JacksonUtils.toObj(response.getContentAsString(), new TypeReference<>() {
            });
        assertEquals("v1", result.getData());
    }
    
    @Test
    void testCreateDraftForkSuccess() throws Exception {
        when(skillOperationService.createDraft(eq("public"), eq("test-skill"), eq("v1"), isNull(),
            isNull(), isNull())).thenReturn("v2");
        MockHttpServletRequestBuilder builder =
            MockMvcRequestBuilders.post(SKILL_ADMIN_PATH + "/draft")
                .param("skillName", "test-skill").param("basedOnVersion", "v1");
        MockHttpServletResponse response = mockMvc.perform(builder).andReturn().getResponse();
        assertEquals(200, response.getStatus());
        Result<String> result =
            JacksonUtils.toObj(response.getContentAsString(), new TypeReference<>() {
            });
        assertEquals("v2", result.getData());
    }
    
    @Test
    void testCreateDraftRejectsSkillCardWithOnlyFrontmatter() throws Throwable {
        String skillCard = "{\"name\":\"test-skill\",\"description\":\"d\","
            + "\"skillMd\":\"---\\nname: test-skill\\ndescription: d\\n---\\n\\n  \"}";
        MockHttpServletRequestBuilder builder =
            MockMvcRequestBuilders.post(SKILL_ADMIN_PATH + "/draft")
                .param("skillCard", skillCard);
        assertServletException(NacosApiException.class, () -> mockMvc.perform(builder).andReturn(),
            "markdown body should not be empty");
    }
    
    @Test
    void testUpdateDraftRejectsSkillCardWithOnlyFrontmatter() throws Throwable {
        String skillCard = "{\"name\":\"test-skill\",\"description\":\"d\","
            + "\"skillMd\":\"---\\nname: test-skill\\ndescription: d\\n---\\n\\n\\n\"}";
        MockHttpServletRequestBuilder builder =
            MockMvcRequestBuilders.put(SKILL_ADMIN_PATH + "/draft")
                .param("skillCard", skillCard);
        assertServletException(NacosApiException.class, () -> mockMvc.perform(builder).andReturn(),
            "markdown body should not be empty");
    }
    
    @Test
    void testDeleteDraftSuccess() throws Exception {
        doNothing().when(skillOperationService).deleteDraft(eq("public"), eq("test-skill"));
        MockHttpServletRequestBuilder builder =
            MockMvcRequestBuilders.delete(SKILL_ADMIN_PATH + "/draft")
                .param("skillName", "test-skill");
        MockHttpServletResponse response = mockMvc.perform(builder).andReturn().getResponse();
        assertEquals(200, response.getStatus());
        verify(skillOperationService).deleteDraft("public", "test-skill");
    }
    
    @Test
    void testSubmitSuccess() throws Exception {
        when(skillOperationService.submit(eq("public"), eq("test-skill"), eq("v1")))
            .thenReturn("pipeline-123");
        MockHttpServletRequestBuilder builder =
            MockMvcRequestBuilders.post(SKILL_ADMIN_PATH + "/submit")
                .param("skillName", "test-skill").param("version", "v1");
        MockHttpServletResponse response = mockMvc.perform(builder).andReturn().getResponse();
        assertEquals(200, response.getStatus());
        Result<String> result =
            JacksonUtils.toObj(response.getContentAsString(), new TypeReference<>() {
            });
        assertEquals("pipeline-123", result.getData());
    }
    
    @Test
    void testUpdateBizTagsSuccess() throws Exception {
        doNothing().when(skillOperationService).updateBizTags(eq("public"), eq("test-skill"),
            eq("[\"retail\"]"));
        MockHttpServletRequestBuilder builder =
            MockMvcRequestBuilders.put(SKILL_ADMIN_PATH + "/biz-tags")
                .param("skillName", "test-skill").param("bizTags", "[\"retail\"]");
        MockHttpServletResponse response = mockMvc.perform(builder).andReturn().getResponse();
        assertEquals(200, response.getStatus());
        verify(skillOperationService).updateBizTags("public", "test-skill", "[\"retail\"]");
    }
    
    @Test
    void testPublishSuccess() throws Exception {
        doNothing().when(skillOperationService).publish(eq("public"), eq("test-skill"), eq("v1"),
            eq(true));
        MockHttpServletRequestBuilder builder =
            MockMvcRequestBuilders.post(SKILL_ADMIN_PATH + "/publish")
                .param("skillName", "test-skill").param("version", "v1")
                .param("updateLatestLabel", "false");
        MockHttpServletResponse response = mockMvc.perform(builder).andReturn().getResponse();
        assertEquals(200, response.getStatus());
        verify(skillOperationService).publish("public", "test-skill", "v1", true);
    }
    
    @Test
    void testUpdateLabelsSuccess() throws Exception {
        doNothing().when(skillOperationService).updateLabels(eq("public"), eq("test-skill"),
            any(Map.class));
        String labelsJson = "{\"stable\":\"v2\"}";
        MockHttpServletRequestBuilder builder =
            MockMvcRequestBuilders.put(SKILL_ADMIN_PATH + "/labels")
                .param("skillName", "test-skill").param("labels", labelsJson);
        MockHttpServletResponse response = mockMvc.perform(builder).andReturn().getResponse();
        assertEquals(200, response.getStatus());
        verify(skillOperationService).updateLabels(eq("public"), eq("test-skill"), any(Map.class));
    }
    
    @Test
    void testOnlineSuccess() throws Exception {
        doNothing().when(skillOperationService)
            .changeOnlineStatus(anyString(), anyString(), anyString(), anyString(), anyBoolean());
        MockHttpServletRequestBuilder builder =
            MockMvcRequestBuilders.post(SKILL_ADMIN_PATH + "/online")
                .param("skillName", "test-skill").param("scope", "version").param("version", "v1");
        MockHttpServletResponse response = mockMvc.perform(builder).andReturn().getResponse();
        assertEquals(200, response.getStatus());
        verify(skillOperationService).changeOnlineStatus("public", "test-skill", "version", "v1",
            true);
    }
    
    @Test
    void testOfflineSuccess() throws Exception {
        doNothing().when(skillOperationService)
            .changeOnlineStatus(anyString(), anyString(), anyString(), anyString(), anyBoolean());
        MockHttpServletRequestBuilder builder =
            MockMvcRequestBuilders.post(SKILL_ADMIN_PATH + "/offline")
                .param("skillName", "test-skill").param("scope", "version").param("version", "v1");
        MockHttpServletResponse response = mockMvc.perform(builder).andReturn().getResponse();
        assertEquals(200, response.getStatus());
        verify(skillOperationService).changeOnlineStatus("public", "test-skill", "version", "v1",
            false);
    }
    
    @Test
    void testUpdateScopeSuccess() throws Exception {
        doNothing().when(skillOperationService).updateScope(anyString(), anyString(), anyString());
        MockHttpServletRequestBuilder builder =
            MockMvcRequestBuilders.put(SKILL_ADMIN_PATH + "/scope")
                .param("skillName", "test-skill").param("scope", "PUBLIC");
        MockHttpServletResponse response = mockMvc.perform(builder).andReturn().getResponse();
        assertEquals(200, response.getStatus());
        verify(skillOperationService).updateScope("public", "test-skill", "PUBLIC");
    }
    
    @Test
    void testForcePublishSuccess() throws Exception {
        doNothing().when(skillOperationService).forcePublish(eq("public"), eq("test-skill"),
            eq("v1"), eq(true));
        MockHttpServletRequestBuilder builder =
            MockMvcRequestBuilders.post(SKILL_ADMIN_PATH + "/force-publish")
                .param("skillName", "test-skill").param("version", "v1")
                .param("updateLatestLabel", "false");
        MockHttpServletResponse response = mockMvc.perform(builder).andReturn().getResponse();
        assertEquals(200, response.getStatus());
        verify(skillOperationService).forcePublish("public", "test-skill", "v1", true);
    }
    
    private void assertServletException(Class<? extends Exception> expectedException,
        Executable executable,
        String expectedMessage) throws Throwable {
        try {
            executable.execute();
        } catch (ServletException e) {
            assertInstanceOf(expectedException, e.getCause());
            if (expectedMessage != null) {
                assertNotNull(e.getCause().getMessage());
                assertEquals(true, e.getCause().getMessage().contains(expectedMessage),
                    "Expected message containing '" + expectedMessage + "', but got: "
                        + e.getCause().getMessage());
            }
        }
    }
}
