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
import com.alibaba.nacos.ai.service.skills.SkillClientOperationService;
import com.alibaba.nacos.ai.service.skills.SkillQueryResult;
import com.alibaba.nacos.api.ai.model.skills.Skill;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.sys.env.EnvUtil;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

/**
 * Test for {@link SkillClientController}.
 *
 * @author nacos
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = MockServletContext.class)
@WebAppConfiguration
class SkillClientControllerTest {
    
    private static final String SKILL_CLIENT_PATH = Constants.Skills.CLIENT_PATH;
    
    private SkillClientController skillClientController;
    
    private MockMvc mockMvc;
    
    private ConfigurableEnvironment cachedEnvironment;
    
    @MockitoBean
    private SkillClientOperationService skillClientOperationService;
    
    @BeforeEach
    void setUp() {
        cachedEnvironment = EnvUtil.getEnvironment();
        EnvUtil.setEnvironment(new StandardEnvironment());
        skillClientController = new SkillClientController(skillClientOperationService);
        mockMvc = MockMvcBuilders.standaloneSetup(skillClientController).build();
    }
    
    @AfterEach
    void tearDown() {
        EnvUtil.setEnvironment(cachedEnvironment);
    }
    
    @Test
    void testGetSkillWithoutName() throws Throwable {
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.get(SKILL_CLIENT_PATH);
        assertServletException(NacosApiException.class, () -> mockMvc.perform(builder).andReturn(),
            "Skill name is required");
    }
    
    @Test
    void testGetSkillByNameSuccess() throws Exception {
        when(skillClientOperationService.querySkill(eq("public"), eq("test-skill"), isNull(),
            isNull(), isNull()))
            .thenReturn(new SkillQueryResult(newSkill(), "md5-1", "v1"));
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.get(SKILL_CLIENT_PATH)
            .param("name", "test-skill");
        MockHttpServletResponse response = mockMvc.perform(builder).andReturn().getResponse();
        assertEquals(200, response.getStatus());
        // Response is a ZIP file
        assertEquals("application/zip", response.getContentType());
    }
    
    @Test
    void testGetSkillByLabelSuccess() throws Exception {
        when(skillClientOperationService.querySkill(eq("public"), eq("test-skill"), isNull(),
            eq("stable"), isNull()))
            .thenReturn(new SkillQueryResult(newSkill(), "md5-2", "v1"));
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.get(SKILL_CLIENT_PATH)
            .param("name", "test-skill").param("label", "stable");
        MockHttpServletResponse response = mockMvc.perform(builder).andReturn().getResponse();
        assertEquals(200, response.getStatus());
    }
    
    @Test
    void testGetSkillByVersionSuccess() throws Exception {
        when(skillClientOperationService.querySkill(eq("public"), eq("test-skill"), eq("v2"),
            isNull(), isNull()))
            .thenReturn(new SkillQueryResult(newSkill(), "md5-3", "v2"));
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.get(SKILL_CLIENT_PATH)
            .param("name", "test-skill").param("version", "v2");
        MockHttpServletResponse response = mockMvc.perform(builder).andReturn().getResponse();
        assertEquals(200, response.getStatus());
    }
    
    @Test
    void testGetSkillWithNamespaceId() throws Exception {
        when(skillClientOperationService.querySkill(eq("custom-ns"), eq("test-skill"), isNull(),
            isNull(), isNull()))
            .thenReturn(new SkillQueryResult(newSkill(), "md5-4", "v1"));
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.get(SKILL_CLIENT_PATH)
            .param("name", "test-skill").param("namespaceId", "custom-ns");
        MockHttpServletResponse response = mockMvc.perform(builder).andReturn().getResponse();
        assertEquals(200, response.getStatus());
    }
    
    @Test
    void testGetSkillNotModified() throws Exception {
        when(skillClientOperationService.querySkill(eq("public"), eq("test-skill"), isNull(),
            isNull(), eq("md5-cached")))
            .thenReturn(SkillQueryResult.notModified("md5-cached", "v1"));
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.get(SKILL_CLIENT_PATH)
            .param("name", "test-skill").param("md5", "md5-cached");
        MockHttpServletResponse response = mockMvc.perform(builder).andReturn().getResponse();
        assertEquals(304, response.getStatus());
    }
    
    private static Skill newSkill() {
        Skill skill = new Skill();
        skill.setName("test-skill");
        skill.setDescription("desc");
        skill.setSkillMd("---\nname: test-skill\ndescription: desc\n---\n\ninstruction");
        return skill;
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
                assertTrue(e.getCause().getMessage().contains(expectedMessage),
                    "Expected message containing '" + expectedMessage + "', got: "
                        + e.getCause().getMessage());
            }
        }
    }
}
