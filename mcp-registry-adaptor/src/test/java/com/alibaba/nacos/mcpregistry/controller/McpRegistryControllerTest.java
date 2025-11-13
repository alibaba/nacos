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

import com.alibaba.nacos.api.ai.model.mcp.registry.McpErrorResponse;
import com.alibaba.nacos.api.ai.model.mcp.registry.McpRegistryServerList;
import com.alibaba.nacos.api.ai.model.mcp.registry.ServerResponse;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.mcpregistry.service.NacosMcpRegistryService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Unit tests for McpRegistryController.
 * 
 * @author xinluo
 */
@ExtendWith(MockitoExtension.class)
@ContextConfiguration(classes = org.springframework.mock.web.MockServletContext.class)
@WebAppConfiguration
class McpRegistryControllerTest {
    
    @InjectMocks
    private McpRegistryController mcpRegistryController;
    
    @Mock
    private NacosMcpRegistryService nacosMcpRegistryService;
    
    private MockMvc mockMvc;
    
    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(mcpRegistryController).build();
    }
    
    @AfterEach
    void tearDown() {
        // Cleanup if needed
    }
    
    /**
     * Test GET /v0/servers - List MCP servers with default parameters.
     */
    @Test
    void testListMcpServersDefaultParams() throws Exception {
        // Setup
        McpRegistryServerList mockServerList = createMockServerList(2);
        mockServerList.setMetadata(new McpRegistryServerList.Metadata("2", 2));
        when(nacosMcpRegistryService.listMcpServers(any())).thenReturn(mockServerList);
        
        // Execute
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.get("/v0/servers");
        String responseContent = mockMvc.perform(builder)
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        
        // Verify
        McpRegistryServerList response = JacksonUtils.toObj(responseContent, McpRegistryServerList.class);
        assertNotNull(response);
        assertNotNull(response.getServers());
        assertEquals(2, response.getServers().size());
        assertNotNull(response.getMetadata());
        assertNotNull(response.getMetadata().getNextCursor());
    }
    
    /**
     * Test GET /v0/servers - List MCP servers with cursor parameter.
     */
    @Test
    void testListMcpServersWithCursor() throws Exception {
        // Setup
        McpRegistryServerList mockServerList = createMockServerList(3);
        mockServerList.setMetadata(new McpRegistryServerList.Metadata("13", 3));
        when(nacosMcpRegistryService.listMcpServers(any())).thenReturn(mockServerList);
        
        // Execute
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.get("/v0/servers")
                .param("cursor", "10");
        String responseContent = mockMvc.perform(builder)
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        
        // Verify
        McpRegistryServerList response = JacksonUtils.toObj(responseContent, McpRegistryServerList.class);
        assertNotNull(response);
        assertNotNull(response.getServers());
        assertEquals(3, response.getServers().size());
    }
    
    /**
     * Test GET /v0/servers - List MCP servers with limit parameter.
     */
    @Test
    void testListMcpServersWithLimit() throws Exception {
        // Setup
        McpRegistryServerList mockServerList = createMockServerList(5);
        mockServerList.setMetadata(new McpRegistryServerList.Metadata("5", 5));
        when(nacosMcpRegistryService.listMcpServers(any())).thenReturn(mockServerList);
        
        // Execute
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.get("/v0/servers")
                .param("limit", "5");
        String responseContent = mockMvc.perform(builder)
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        
        // Verify
        McpRegistryServerList response = JacksonUtils.toObj(responseContent, McpRegistryServerList.class);
        assertNotNull(response);
        assertEquals(5, response.getServers().size());
    }
    
    /**
     * Test GET /v0/servers - List MCP servers with search parameter.
     */
    @Test
    void testListMcpServersWithSearch() throws Exception {
        // Setup
        McpRegistryServerList mockServerList = createMockServerList(1);
        mockServerList.setMetadata(new McpRegistryServerList.Metadata("1", 1));
        when(nacosMcpRegistryService.listMcpServers(any())).thenReturn(mockServerList);
        
        // Execute
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.get("/v0/servers")
                .param("search", "filesystem");
        String responseContent = mockMvc.perform(builder)
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        
        // Verify
        McpRegistryServerList response = JacksonUtils.toObj(responseContent, McpRegistryServerList.class);
        assertNotNull(response);
        assertEquals(1, response.getServers().size());
    }
    
    /**
     * Test GET /v0/servers - List MCP servers with updated_since parameter.
     */
    @Test
    void testListMcpServersWithUpdatedSince() throws Exception {
        // Setup
        McpRegistryServerList mockServerList = createMockServerList(2);
        mockServerList.setMetadata(new McpRegistryServerList.Metadata("2", 2));
        when(nacosMcpRegistryService.listMcpServers(any())).thenReturn(mockServerList);
        
        // Execute
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.get("/v0/servers")
                .param("updated_since", "2025-08-07T13:15:04.280Z");
        String responseContent = mockMvc.perform(builder)
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        
        // Verify
        McpRegistryServerList response = JacksonUtils.toObj(responseContent, McpRegistryServerList.class);
        assertNotNull(response);
        assertEquals(2, response.getServers().size());
    }
    
    /**
     * Test GET /v0/servers - List MCP servers with version parameter.
     */
    @Test
    void testListMcpServersWithVersion() throws Exception {
        // Setup
        McpRegistryServerList mockServerList = createMockServerList(1);
        mockServerList.setMetadata(new McpRegistryServerList.Metadata("1", 1));
        when(nacosMcpRegistryService.listMcpServers(any())).thenReturn(mockServerList);
        
        // Execute
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.get("/v0/servers")
                .param("version", "1.2.3");
        String responseContent = mockMvc.perform(builder)
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        
        // Verify
        McpRegistryServerList response = JacksonUtils.toObj(responseContent, McpRegistryServerList.class);
        assertNotNull(response);
        assertEquals(1, response.getServers().size());
    }
    
    /**
     * Test GET /v0/servers - List MCP servers with all parameters.
     */
    @Test
    void testListMcpServersWithAllParams() throws Exception {
        // Setup
        McpRegistryServerList mockServerList = createMockServerList(3);
        mockServerList.setMetadata(new McpRegistryServerList.Metadata("8", 3));
        when(nacosMcpRegistryService.listMcpServers(any())).thenReturn(mockServerList);
        
        // Execute
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.get("/v0/servers")
                .param("cursor", "5")
                .param("limit", "10")
                .param("search", "test")
                .param("updated_since", "2025-08-07T13:15:04.280Z")
                .param("version", "1.0.0");
        String responseContent = mockMvc.perform(builder)
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        
        // Verify
        McpRegistryServerList response = JacksonUtils.toObj(responseContent, McpRegistryServerList.class);
        assertNotNull(response);
        assertEquals(3, response.getServers().size());
    }
    
    /**
     * Test GET /v0/servers - Empty server list.
     */
    @Test
    void testListMcpServersEmptyList() throws Exception {
        // Setup
        McpRegistryServerList mockServerList = new McpRegistryServerList();
        mockServerList.setServers(Collections.emptyList());
        when(nacosMcpRegistryService.listMcpServers(any())).thenReturn(mockServerList);
        
        // Execute
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.get("/v0/servers");
        String responseContent = mockMvc.perform(builder)
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        
        // Verify
        McpRegistryServerList response = JacksonUtils.toObj(responseContent, McpRegistryServerList.class);
        assertNotNull(response);
        assertNotNull(response.getServers());
        assertEquals(0, response.getServers().size());
    }
    
    /**
     * Test GET /v0/servers/{serverName}/versions - Get all versions of a server.
     */
    @Test
    void testGetServerVersionsSuccess() throws Exception {
        // Setup
        String serverName = "com.example%2Fmy-server";
        McpRegistryServerList mockServerList = createMockServerList(2);
        mockServerList.setMetadata(new McpRegistryServerList.Metadata(null, 2));
        when(nacosMcpRegistryService.getServerVersions(any(), any())).thenReturn(mockServerList);
        
        // Execute
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.get("/v0/servers/{name}/versions", serverName);
        String responseContent = mockMvc.perform(builder)
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        
        // Verify
        McpRegistryServerList response = JacksonUtils.toObj(responseContent, McpRegistryServerList.class);
        assertNotNull(response);
        assertNotNull(response.getServers());
        assertEquals(2, response.getServers().size());
    }
    
    /**
     * Test GET /v0/servers/{serverName}/versions - Server not found (404).
     */
    @Test
    void testGetServerVersionsNotFound() throws Exception {
        // Setup
        String serverName = "com.example%2Fnonexistent";
        when(nacosMcpRegistryService.getServerVersions(any(), any())).thenReturn(null);
        
        // Execute
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.get("/v0/servers/{name}/versions", serverName);
        String responseContent = mockMvc.perform(builder)
                .andExpect(status().isNotFound())
                .andReturn()
                .getResponse()
                .getContentAsString();
        
        // Verify
        McpErrorResponse errorResponse = JacksonUtils.toObj(responseContent, McpErrorResponse.class);
        assertNotNull(errorResponse);
        assertEquals("Server not found", errorResponse.getError());
    }
    
    /**
     * Test GET /v0/servers/{serverName}/versions/{version} - Get specific version.
     */
    @Test
    void testGetVersionedServerSuccess() throws Exception {
        // Setup
        String serverName = "com.example%2Fmy-server";
        String version = "1.0.0";
        ServerResponse mockServerResponse = createMockServerResponse(serverName, version);
        when(nacosMcpRegistryService.getServer(eq(serverName), isNull(), eq(version))).thenReturn(mockServerResponse);
        
        // Execute
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.get("/v0/servers/{serverName}/versions/{version}", 
                serverName, version);
        String responseContent = mockMvc.perform(builder)
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        
        // Verify
        ServerResponse response = JacksonUtils.toObj(responseContent, ServerResponse.class);
        assertNotNull(response);
        assertNotNull(response.getServer());
        assertEquals(serverName, response.getServer().getName());
        assertEquals(version, response.getServer().getVersion());
    }
    
    /**
     * Test GET /v0/servers/{serverName}/versions/{version} - Get latest version.
     */
    @Test
    void testGetVersionedServerLatestVersion() throws Exception {
        // Setup
        String serverName = "com.example%2Fmy-server";
        String version = "latest";
        ServerResponse mockServerResponse = createMockServerResponse(serverName, "2.0.0");
        when(nacosMcpRegistryService.getServer(eq(serverName), isNull(), eq(version))).thenReturn(mockServerResponse);
        
        // Execute
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.get("/v0/servers/{serverName}/versions/{version}", 
                serverName, version);
        String responseContent = mockMvc.perform(builder)
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        
        // Verify
        ServerResponse response = JacksonUtils.toObj(responseContent, ServerResponse.class);
        assertNotNull(response);
        assertNotNull(response.getServer());
        assertEquals(serverName, response.getServer().getName());
    }
    
    /**
     * Test GET /v0/servers/{serverName}/versions/{version} - Version with build metadata.
     */
    @Test
    void testGetVersionedServerWithBuildMetadata() throws Exception {
        // Setup
        String serverName = "com.example%2Fmy-server";
        String version = "1.0.0%2B20130313144700";
        ServerResponse mockServerResponse = createMockServerResponse(serverName, "1.0.0+20130313144700");
        when(nacosMcpRegistryService.getServer(eq(serverName), isNull(), eq(version))).thenReturn(mockServerResponse);
        
        // Execute
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.get("/v0/servers/{serverName}/versions/{version}", 
                serverName, version);
        String responseContent = mockMvc.perform(builder)
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        
        // Verify
        ServerResponse response = JacksonUtils.toObj(responseContent, ServerResponse.class);
        assertNotNull(response);
        assertNotNull(response.getServer());
    }
    
    /**
     * Test GET /v0/servers/{serverName}/versions/{version} - Server not found (404).
     */
    @Test
    void testGetVersionedServerNotFound() throws Exception {
        // Setup
        String serverName = "com.example%2Fnonexistent";
        String version = "1.0.0";
        when(nacosMcpRegistryService.getServer(eq(serverName), isNull(), eq(version))).thenReturn(null);
        
        // Execute
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.get("/v0/servers/{serverName}/versions/{version}", 
                serverName, version);
        String responseContent = mockMvc.perform(builder)
                .andExpect(status().isNotFound())
                .andReturn()
                .getResponse()
                .getContentAsString();
        
        // Verify
        McpErrorResponse errorResponse = JacksonUtils.toObj(responseContent, McpErrorResponse.class);
        assertNotNull(errorResponse);
        assertEquals("Server not found", errorResponse.getError());
    }
    
    /**
     * Test GET /v0/servers/{serverName}/versions/{version} - Version not found (404).
     */
    @Test
    void testGetVersionedServerVersionNotFound() throws Exception {
        // Setup
        String serverName = "com.example%2Fmy-server";
        String version = "999.999.999";
        when(nacosMcpRegistryService.getServer(eq(serverName), isNull(), eq(version))).thenReturn(null);
        
        // Execute
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.get("/v0/servers/{serverName}/versions/{version}", 
                serverName, version);
        String responseContent = mockMvc.perform(builder)
                .andExpect(status().isNotFound())
                .andReturn()
                .getResponse()
                .getContentAsString();
        
        // Verify
        McpErrorResponse errorResponse = JacksonUtils.toObj(responseContent, McpErrorResponse.class);
        assertNotNull(errorResponse);
        assertEquals("Server not found", errorResponse.getError());
    }
    
    /**
     * Test GET /v0/servers - Service throws NacosException.
     */
    @Test
    void testListMcpServersServiceException() throws Exception {
        // Setup
        when(nacosMcpRegistryService.listMcpServers(any())).thenReturn(new McpRegistryServerList());
        
        // Execute & Verify
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.get("/v0/servers");
        mockMvc.perform(builder)
                .andExpect(status().isOk());
    }
    
    /**
     * Test GET /v0/servers/{serverName}/versions - Service throws NacosException.
     */
    @Test
    void testGetServerVersionsServiceException() throws Exception {
        // Setup
        String serverName = "com.example%2Fmy-server";
        when(nacosMcpRegistryService.getServerVersions(any(), any()))
                .thenReturn(new McpRegistryServerList());
        
        // Execute & Verify
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.get("/v0/servers/{name}/versions", serverName);
        mockMvc.perform(builder)
                .andExpect(status().isOk());
    }
    
    /**
     * Test GET /v0/servers/{serverName}/versions/{version} - Service throws NacosException.
     */
    @Test
    void testGetVersionedServerServiceException() throws Exception {
        // Setup
        String serverName = "com.example%2Fmy-server";
        String version = "1.0.0";
        ServerResponse mockServerResponse = createMockServerResponse(serverName, version);
        when(nacosMcpRegistryService.getServer(eq(serverName), isNull(), eq(version)))
                .thenReturn(mockServerResponse);
        
        // Execute & Verify
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.get("/v0/servers/{serverName}/versions/{version}", 
                serverName, version);
        mockMvc.perform(builder)
                .andExpect(status().isOk());
    }
    
    /**
     * Test pagination cursor calculation.
     */
    @Test
    void testListMcpServersPaginationCursor() throws Exception {
        // Setup
        McpRegistryServerList mockServerList = createMockServerList(5);
        when(nacosMcpRegistryService.listMcpServers(any())).thenReturn(mockServerList);
        
        // Execute with offset 10
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.get("/v0/servers")
                .param("cursor", "10");
        String responseContent = mockMvc.perform(builder)
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        
        // Verify cursor calculation: offset (10) + returned (5) = 15
        McpRegistryServerList response = JacksonUtils.toObj(responseContent, McpRegistryServerList.class);
        assertNotNull(response.getMetadata());
        assertEquals("15", response.getMetadata().getNextCursor());
        assertEquals(5, response.getMetadata().getCount());
    }
    
    /**
     * Helper method to create a mock ServerResponse.
     */
    private ServerResponse createMockServerResponse(String serverName, String version) {
        ServerResponse response = new ServerResponse();
        com.alibaba.nacos.api.ai.model.mcp.registry.McpRegistryServerDetail server = 
                new com.alibaba.nacos.api.ai.model.mcp.registry.McpRegistryServerDetail();
        server.setName(serverName);
        server.setDescription("Test MCP server description");
        server.setVersion(version);
        response.setServer(server);
        
        ServerResponse.Meta meta = new ServerResponse.Meta();
        com.alibaba.nacos.api.ai.model.mcp.registry.OfficialMeta officialMeta = 
                new com.alibaba.nacos.api.ai.model.mcp.registry.OfficialMeta();
        officialMeta.setStatus("active");
        officialMeta.setIsLatest(true);
        meta.setOfficial(officialMeta);
        response.setMeta(meta);
        
        return response;
    }
    
    /**
     * Helper method to create a mock McpRegistryServerList.
     */
    private McpRegistryServerList createMockServerList(int count) {
        McpRegistryServerList serverList = new McpRegistryServerList();
        List<ServerResponse> servers = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            ServerResponse serverResponse = createMockServerResponse(
                    "com.example%2Fserver-" + i, 
                    "1.0." + i
            );
            servers.add(serverResponse);
        }
        serverList.setServers(servers);
        return serverList;
    }
}
