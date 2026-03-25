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

package com.alibaba.nacos.mcpregistry.service;

import com.alibaba.nacos.api.ai.model.mcp.McpServerBasicInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpServerDetailInfo;
import com.alibaba.nacos.ai.service.McpServerOperationService;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.api.model.response.Namespace;
import com.alibaba.nacos.api.ai.model.mcp.registry.ServerVersionDetail;
import com.alibaba.nacos.core.service.NamespaceOperationService;
import com.alibaba.nacos.mcpregistry.service.SkillsRegistryService.SkillSearchResult;
import com.alibaba.nacos.mcpregistry.service.SkillsRegistryService.WellKnownIndex;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

/**
 * Unit tests for SkillsRegistryService.
 *
 * @author nacos
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SkillsRegistryServiceTest {
    
    @Mock
    private McpServerOperationService mcpServerOperationService;
    
    @Mock
    private NamespaceOperationService namespaceOperationService;
    
    private SkillsRegistryService skillsRegistryService;
    
    @BeforeEach
    void setUp() {
        skillsRegistryService = new SkillsRegistryService(mcpServerOperationService, namespaceOperationService);
    }
    
    @Test
    void testSearchSkillsWithResults() {
        mockNamespaces("public");
        mockServersInNamespace("public", "test", createServerList("test-server-1", "test-server-2"));
        
        SkillSearchResult result = skillsRegistryService.searchSkills("test", 10, null, "http://localhost:9080");
        
        assertNotNull(result);
        assertNotNull(result.getSkills());
        assertEquals(2, result.getSkills().size());
        assertEquals("test-server-1", result.getSkills().get(0).getId());
        assertEquals("http://localhost:9080", result.getSkills().get(0).getSource());
    }
    
    @Test
    void testSearchSkillsWithNamespace() {
        mockServersInNamespace("my-ns", "query", createServerList("server-a"));
        
        SkillSearchResult result = skillsRegistryService.searchSkills("query", 10, "my-ns", "http://localhost:9080");
        
        assertNotNull(result);
        assertEquals(1, result.getSkills().size());
        assertEquals("server-a", result.getSkills().get(0).getName());
    }
    
    @Test
    void testSearchSkillsEmptyResult() {
        mockNamespaces("public");
        mockServersInNamespace("public", "nothing", createEmptyServerList());
        
        SkillSearchResult result = skillsRegistryService.searchSkills("nothing", 10, null, "http://localhost:9080");
        
        assertNotNull(result);
        assertNotNull(result.getSkills());
        assertEquals(0, result.getSkills().size());
    }
    
    @Test
    void testGetSkillsIndex() {
        mockNamespaces("public");
        mockServersInNamespace("public", null, createServerList("server-1", "server-2", "server-3"));
        
        WellKnownIndex index = skillsRegistryService.getSkillsIndex(null);
        
        assertNotNull(index);
        assertNotNull(index.getSkills());
        assertEquals(3, index.getSkills().size());
        for (SkillsRegistryService.WellKnownSkillEntry entry : index.getSkills()) {
            assertNotNull(entry.getName());
            assertNotNull(entry.getDescription());
            assertTrue(entry.getFiles().contains("SKILL.md"));
        }
    }
    
    @Test
    void testGetSkillMdSuccess() throws NacosException {
        McpServerDetailInfo detail = createMockDetail("my-server", "A test MCP server");
        when(mcpServerOperationService.getMcpServerDetail(isNull(), isNull(), eq("my-server"), isNull()))
                .thenReturn(detail);
        
        String skillMd = skillsRegistryService.getSkillMd("my-server", null);
        
        assertNotNull(skillMd);
        assertTrue(skillMd.contains("name: my-server"));
        assertTrue(skillMd.contains("description: A test MCP server"));
        assertTrue(skillMd.contains("# my-server"));
    }
    
    @Test
    void testGetSkillMdNotFound() throws NacosException {
        when(mcpServerOperationService.getMcpServerDetail(isNull(), isNull(), eq("nonexistent"), isNull()))
                .thenThrow(new NacosException(NacosException.NOT_FOUND, "Not found"));
        
        String skillMd = skillsRegistryService.getSkillMd("nonexistent", null);
        
        assertNull(skillMd);
    }
    
    @Test
    void testNormalizeSkillName() {
        assertEquals("my-server", SkillsRegistryService.normalizeSkillName("My-Server"));
        assertEquals("com-example-server", SkillsRegistryService.normalizeSkillName("com.example/server"));
        assertEquals("simple", SkillsRegistryService.normalizeSkillName("simple"));
        assertEquals("test-server-1", SkillsRegistryService.normalizeSkillName("test_server_1"));
        assertEquals("unknown", SkillsRegistryService.normalizeSkillName(""));
        assertEquals("unknown", SkillsRegistryService.normalizeSkillName(null));
    }
    
    @Test
    void testSearchSkillsNameNormalization() {
        mockNamespaces("public");
        mockServersInNamespace("public", "test", createServerList("com.example/TestServer"));
        
        SkillSearchResult result = skillsRegistryService.searchSkills("test", 10, null, "http://localhost:9080");
        
        assertNotNull(result);
        assertEquals(1, result.getSkills().size());
        assertEquals("com-example-testserver", result.getSkills().get(0).getId());
    }
    
    private void mockNamespaces(String... namespaceIds) {
        List<Namespace> namespaces = new ArrayList<>();
        for (String id : namespaceIds) {
            Namespace ns = new Namespace();
            ns.setNamespace(id);
            namespaces.add(ns);
        }
        when(namespaceOperationService.getNamespaceList()).thenReturn(namespaces);
    }
    
    private void mockServersInNamespace(String namespaceId, String keyword, Page<McpServerBasicInfo> page) {
        when(mcpServerOperationService.listMcpServerWithPage(
                eq(namespaceId), eq(keyword), anyString(), anyInt(), anyInt()))
                .thenReturn(page);
    }
    
    private Page<McpServerBasicInfo> createServerList(String... names) {
        Page<McpServerBasicInfo> page = new Page<>();
        List<McpServerBasicInfo> items = new ArrayList<>();
        for (String name : names) {
            McpServerBasicInfo server = new McpServerBasicInfo();
            server.setName(name);
            server.setDescription("Description for " + name);
            items.add(server);
        }
        page.setPageItems(items);
        page.setTotalCount(items.size());
        return page;
    }
    
    private Page<McpServerBasicInfo> createEmptyServerList() {
        Page<McpServerBasicInfo> page = new Page<>();
        page.setPageItems(Collections.emptyList());
        page.setTotalCount(0);
        return page;
    }
    
    private McpServerDetailInfo createMockDetail(String name, String description) {
        McpServerDetailInfo detail = new McpServerDetailInfo();
        detail.setName(name);
        detail.setDescription(description);
        detail.setFrontProtocol("sse");
        ServerVersionDetail versionDetail = new ServerVersionDetail();
        versionDetail.setVersion("1.0.0");
        detail.setVersionDetail(versionDetail);
        return detail;
    }
}
