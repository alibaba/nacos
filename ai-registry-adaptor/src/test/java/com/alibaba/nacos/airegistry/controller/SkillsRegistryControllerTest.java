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

package com.alibaba.nacos.airegistry.controller;

import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.airegistry.model.skills.SkillsSearchItem;
import com.alibaba.nacos.airegistry.model.skills.SkillsSearchResponse;
import com.alibaba.nacos.airegistry.model.skills.WellKnownSkillEntry;
import com.alibaba.nacos.airegistry.model.skills.WellKnownSkillsIndex;
import com.alibaba.nacos.airegistry.service.NacosSkillsRegistryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.HandlerMapping;

import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Unit tests for {@link SkillsRegistryController}.
 *
 * @author nacos
 */
@ExtendWith(MockitoExtension.class)
@ContextConfiguration(classes = org.springframework.mock.web.MockServletContext.class)
@WebAppConfiguration
class SkillsRegistryControllerTest {
    
    @InjectMocks
    private SkillsRegistryController skillsRegistryController;
    
    @Mock
    private NacosSkillsRegistryService nacosSkillsRegistryService;
    
    private MockMvc mockMvc;
    
    private static final String SCHEMA_0_2 =
        "https://schemas.agentskills.io/discovery/0.2.0/schema.json";
    
    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(skillsRegistryController).build();
    }
    
    @Test
    void testGetAgentSkillsIndex() throws Exception {
        WellKnownSkillEntry entry = new WellKnownSkillEntry();
        entry.setName("demo-skill");
        entry.setDescription("demo");
        entry.setType("archive");
        entry.setUrl("demo-skill.zip");
        entry.setDigest("sha256:0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");
        WellKnownSkillsIndex index = new WellKnownSkillsIndex();
        index.setSchema(SCHEMA_0_2);
        index.setSkills(List.of(entry));
        when(nacosSkillsRegistryService.buildAgentSkillsIndex("public")).thenReturn(index);
        
        String content = mockMvc.perform(
            MockMvcRequestBuilders.get("/registry/public/.well-known/agent-skills/index.json"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        
        WellKnownSkillsIndex response = JacksonUtils.toObj(content, WellKnownSkillsIndex.class);
        assertEquals(SCHEMA_0_2, response.getSchema());
        assertEquals(1, response.getSkills().size());
        assertEquals("demo-skill", response.getSkills().get(0).getName());
        assertEquals("archive", response.getSkills().get(0).getType());
        assertEquals("demo-skill.zip", response.getSkills().get(0).getUrl());
    }
    
    @Test
    void testGetLegacySkillsIndex() throws Exception {
        WellKnownSkillEntry entry = new WellKnownSkillEntry();
        entry.setName("demo-skill");
        entry.setDescription("demo");
        entry.setFiles(List.of("SKILL.md", "docs/guide.md"));
        WellKnownSkillsIndex index = new WellKnownSkillsIndex();
        index.setSkills(List.of(entry));
        when(nacosSkillsRegistryService.buildLegacySkillsIndex("public")).thenReturn(index);
        
        String content = mockMvc.perform(
            MockMvcRequestBuilders.get("/registry/public/.well-known/skills/index.json"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        
        WellKnownSkillsIndex response = JacksonUtils.toObj(content, WellKnownSkillsIndex.class);
        assertEquals(1, response.getSkills().size());
        assertEquals(List.of("SKILL.md", "docs/guide.md"),
            response.getSkills().get(0).getFiles());
    }
    
    @Test
    void testSearch() throws Exception {
        SkillsSearchItem item = new SkillsSearchItem();
        item.setId("demo-skill");
        item.setName("demo-skill");
        item.setInstalls(12);
        item.setSource("http://localhost/registry/public");
        SkillsSearchResponse response = new SkillsSearchResponse();
        response.setSkills(List.of(item));
        when(nacosSkillsRegistryService.search(eq("public"), eq("demo"), eq(10),
            eq("http://localhost/registry/public"))).thenReturn(response);
        
        String content = mockMvc.perform(MockMvcRequestBuilders.get("/registry/public/api/search")
            .param("q", "demo"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        
        SkillsSearchResponse actual = JacksonUtils.toObj(content, SkillsSearchResponse.class);
        assertEquals(1, actual.getSkills().size());
        assertEquals("demo-skill", actual.getSkills().get(0).getId());
    }
    
    @Test
    void testGetSkillMarkdown() throws Exception {
        when(nacosSkillsRegistryService.getSkillFileContent("public", "demo-skill", "SKILL.md"))
            .thenReturn("---\nname: demo-skill\n---\n");
        
        String content = mockMvc.perform(
            MockMvcRequestBuilders
                .get("/registry/public/.well-known/agent-skills/demo-skill/SKILL.md"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        
        assertTrue(content.contains("demo-skill"));
    }
    
    @Test
    void testGetSkillMarkdownNotFound() throws Exception {
        when(nacosSkillsRegistryService.getSkillFileContent("public", "missing-skill", "SKILL.md"))
            .thenReturn(null);
        
        mockMvc.perform(
            MockMvcRequestBuilders
                .get("/registry/public/.well-known/agent-skills/missing-skill/SKILL.md"))
            .andExpect(status().isNotFound());
    }
    
    @Test
    void testGetSkillArchive() throws Exception {
        when(nacosSkillsRegistryService.getSkillArchiveContent("public", "demo-skill"))
            .thenReturn("zip".getBytes());
        
        byte[] content = mockMvc.perform(
            MockMvcRequestBuilders
                .get("/registry/public/.well-known/agent-skills/demo-skill.zip"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsByteArray();
        
        assertEquals("zip", new String(content, StandardCharsets.UTF_8));
    }
    
    @Test
    void testGetSkillArchiveNotFound() throws Exception {
        when(nacosSkillsRegistryService.getSkillArchiveContent("public", "missing-skill"))
            .thenReturn(null);
        
        mockMvc.perform(
            MockMvcRequestBuilders
                .get("/registry/public/.well-known/agent-skills/missing-skill.zip"))
            .andExpect(status().isNotFound());
    }
    
    @Test
    void testGetNestedFile() throws Exception {
        when(
            nacosSkillsRegistryService.getSkillFileContent("public", "demo-skill", "docs/guide.md"))
            .thenReturn("guide");
        
        String content = mockMvc.perform(
            MockMvcRequestBuilders
                .get("/registry/public/.well-known/skills/demo-skill/docs/guide.md"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        
        assertEquals("guide", content);
    }
    
    @Test
    void testGetFileNotFound() throws Exception {
        when(nacosSkillsRegistryService.getSkillFileContent("public", "demo-skill",
            "docs/missing.md"))
            .thenReturn(null);
        
        mockMvc.perform(
            MockMvcRequestBuilders
                .get("/registry/public/.well-known/agent-skills/demo-skill/docs/missing.md"))
            .andExpect(status().isNotFound());
    }
    
    @Test
    void testExtractFilePathBoundaryCases() throws Exception {
        Method extractFilePath =
            SkillsRegistryController.class.getDeclaredMethod("extractFilePath",
                HttpServletRequest.class);
        extractFilePath.setAccessible(true);
        
        HttpServletRequest blankRequest = mock(HttpServletRequest.class);
        when(blankRequest.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE))
            .thenReturn("/registry/public/.well-known/skills/demo-skill/");
        when(blankRequest.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE))
            .thenReturn("/registry/{namespaceId}/.well-known/skills/{skillName}/**");
        assertEquals("", extractFilePath.invoke(skillsRegistryController, blankRequest));
        
        HttpServletRequest slashRequest = mock(HttpServletRequest.class);
        when(slashRequest.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE))
            .thenReturn("/docs/guide.md");
        when(slashRequest.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE))
            .thenReturn("/**");
        assertEquals("docs/guide.md", extractFilePath.invoke(skillsRegistryController,
            slashRequest));
    }
}
