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

package com.alibaba.nacos.mcpregistry.controller;

import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.mcpregistry.service.SkillsRegistryService;
import com.alibaba.nacos.mcpregistry.service.SkillsRegistryService.SkillSearchItem;
import com.alibaba.nacos.mcpregistry.service.SkillsRegistryService.SkillSearchResult;
import com.alibaba.nacos.mcpregistry.service.SkillsRegistryService.WellKnownIndex;
import com.alibaba.nacos.mcpregistry.service.SkillsRegistryService.WellKnownSkillEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Unit tests for SkillsRegistryController.
 *
 * @author nacos
 */
@ExtendWith(MockitoExtension.class)
class SkillsRegistryControllerTest {
    
    @InjectMocks
    private SkillsRegistryController skillsRegistryController;
    
    @Mock
    private SkillsRegistryService skillsRegistryService;
    
    private MockMvc mockMvc;
    
    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(skillsRegistryController).build();
    }
    
    @Test
    void testSearchSkillsWithQuery() throws Exception {
        SkillSearchResult mockResult = createMockSearchResult(3);
        when(skillsRegistryService.searchSkills(eq("typescript"), eq(10), isNull(), anyString()))
                .thenReturn(mockResult);
        
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.get("/api/search")
                .param("q", "typescript");
        String responseContent = mockMvc.perform(builder)
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        
        SkillSearchResult response = JacksonUtils.toObj(responseContent, SkillSearchResult.class);
        assertNotNull(response);
        assertNotNull(response.getSkills());
        assertEquals(3, response.getSkills().size());
    }
    
    @Test
    void testSearchSkillsWithLimit() throws Exception {
        SkillSearchResult mockResult = createMockSearchResult(5);
        when(skillsRegistryService.searchSkills(eq("mcp"), eq(5), isNull(), anyString()))
                .thenReturn(mockResult);
        
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.get("/api/search")
                .param("q", "mcp")
                .param("limit", "5");
        String responseContent = mockMvc.perform(builder)
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        
        SkillSearchResult response = JacksonUtils.toObj(responseContent, SkillSearchResult.class);
        assertNotNull(response);
        assertEquals(5, response.getSkills().size());
    }
    
    @Test
    void testSearchSkillsEmptyResult() throws Exception {
        SkillSearchResult mockResult = new SkillSearchResult(Collections.emptyList());
        when(skillsRegistryService.searchSkills(eq("nonexistent"), eq(10), isNull(), anyString()))
                .thenReturn(mockResult);
        
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.get("/api/search")
                .param("q", "nonexistent");
        String responseContent = mockMvc.perform(builder)
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        
        SkillSearchResult response = JacksonUtils.toObj(responseContent, SkillSearchResult.class);
        assertNotNull(response);
        assertNotNull(response.getSkills());
        assertEquals(0, response.getSkills().size());
    }
    
    @Test
    void testSearchSkillsWithNamespaceId() throws Exception {
        SkillSearchResult mockResult = createMockSearchResult(2);
        when(skillsRegistryService.searchSkills(eq("test"), eq(10), eq("my-namespace"), anyString()))
                .thenReturn(mockResult);
        
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.get("/api/search")
                .param("q", "test")
                .param("namespaceId", "my-namespace");
        String responseContent = mockMvc.perform(builder)
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        
        SkillSearchResult response = JacksonUtils.toObj(responseContent, SkillSearchResult.class);
        assertNotNull(response);
        assertEquals(2, response.getSkills().size());
    }
    
    @Test
    void testGetSkillsIndex() throws Exception {
        WellKnownIndex mockIndex = createMockIndex(3);
        when(skillsRegistryService.getSkillsIndex(isNull())).thenReturn(mockIndex);
        
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.get("/.well-known/skills/index.json");
        String responseContent = mockMvc.perform(builder)
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        
        WellKnownIndex response = JacksonUtils.toObj(responseContent, WellKnownIndex.class);
        assertNotNull(response);
        assertNotNull(response.getSkills());
        assertEquals(3, response.getSkills().size());
        for (WellKnownSkillEntry entry : response.getSkills()) {
            assertNotNull(entry.getName());
            assertNotNull(entry.getDescription());
            assertTrue(entry.getFiles().contains("SKILL.md"));
        }
    }
    
    @Test
    void testGetSkillsIndexWithNamespace() throws Exception {
        WellKnownIndex mockIndex = createMockIndex(1);
        when(skillsRegistryService.getSkillsIndex(eq("production"))).thenReturn(mockIndex);
        
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.get("/.well-known/skills/index.json")
                .param("namespaceId", "production");
        String responseContent = mockMvc.perform(builder)
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        
        WellKnownIndex response = JacksonUtils.toObj(responseContent, WellKnownIndex.class);
        assertNotNull(response);
        assertEquals(1, response.getSkills().size());
    }
    
    @Test
    void testGetSkillMdSuccess() throws Exception {
        String mockSkillMd = "---\nname: my-server\ndescription: Test server\n---\n\n# My Server\n";
        when(skillsRegistryService.getSkillMd(eq("my-server"), isNull())).thenReturn(mockSkillMd);
        
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.get("/.well-known/skills/my-server/SKILL.md");
        String responseContent = mockMvc.perform(builder)
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        
        assertTrue(responseContent.contains("name: my-server"));
        assertTrue(responseContent.contains("description: Test server"));
    }
    
    @Test
    void testGetSkillMdNotFound() throws Exception {
        when(skillsRegistryService.getSkillMd(eq("nonexistent"), isNull())).thenReturn(null);
        
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.get("/.well-known/skills/nonexistent/SKILL.md");
        mockMvc.perform(builder)
                .andExpect(status().isNotFound());
    }
    
    @Test
    void testGetSkillMdWithNamespace() throws Exception {
        String mockSkillMd = "---\nname: my-server\ndescription: Test\n---\n\n# My Server\n";
        when(skillsRegistryService.getSkillMd(eq("my-server"), eq("dev"))).thenReturn(mockSkillMd);
        
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.get("/.well-known/skills/my-server/SKILL.md")
                .param("namespaceId", "dev");
        mockMvc.perform(builder)
                .andExpect(status().isOk());
    }
    
    private SkillSearchResult createMockSearchResult(int count) {
        List<SkillSearchItem> items = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            SkillSearchItem item = new SkillSearchItem();
            item.setId("server-" + i);
            item.setName("server-" + i);
            item.setSource("http://localhost:9080");
            item.setInstalls(0);
            items.add(item);
        }
        return new SkillSearchResult(items);
    }
    
    private WellKnownIndex createMockIndex(int count) {
        List<WellKnownSkillEntry> entries = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            WellKnownSkillEntry entry = new WellKnownSkillEntry();
            entry.setName("server-" + i);
            entry.setDescription("Description for server " + i);
            entry.setFiles(Collections.singletonList("SKILL.md"));
            entries.add(entry);
        }
        return new WellKnownIndex(entries);
    }
}
