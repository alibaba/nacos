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

import com.alibaba.nacos.ai.constant.Constants;
import com.alibaba.nacos.api.ai.model.mcp.McpToolSpecification;
import com.alibaba.nacos.api.ai.model.mcp.registry.McpRegistryServerDetail;
import com.alibaba.nacos.api.ai.model.mcp.registry.McpRegistryServerList;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.mcpregistry.form.GetServerForm;
import com.alibaba.nacos.mcpregistry.form.ListServerForm;
import com.alibaba.nacos.mcpregistry.service.NacosMcpRegistryService;
import com.alibaba.nacos.sys.env.EnvUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.mockito.Mock;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = MockServletContext.class)
@WebAppConfiguration
class McpRegistryControllerTest {
    
    @Mock
    private NacosMcpRegistryService nacosMcpRegistryService;
    
    private ObjectMapper mapper = new ObjectMapper();
    
    private MockMvc mockMvc;
    
    private ConfigurableEnvironment cachedEnvironment;
    
    McpRegistryController mcpRegistryController;
    
    @BeforeEach
    void setUp() {
        cachedEnvironment = EnvUtil.getEnvironment();
        EnvUtil.setEnvironment(new StandardEnvironment());
        mcpRegistryController = new McpRegistryController(nacosMcpRegistryService);
        mockMvc = MockMvcBuilders.standaloneSetup(mcpRegistryController).build();
    }
    
    @AfterEach
    void tearDown() {
        EnvUtil.setEnvironment(cachedEnvironment);
    }
    
    @Test
    void listMcpServersInvalidOffset() throws Throwable {
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.get("/v0/servers").param("offset", "-1");
        assertServletException(NacosApiException.class, () -> mockMvc.perform(builder).andReturn(),
                "ErrCode:400, ErrMsg:Parameter 'offset' must >= 0");
    }
    
    @Test
    void listMcpServersInvalidLimit() throws Throwable {
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.get("/v0/servers").param("offset", "0")
                .param("limit", "1000");
        assertServletException(NacosApiException.class, () -> mockMvc.perform(builder).andReturn(),
                "ErrCode:400, ErrMsg:Parameter 'limit' must <= 100");
    }
    
    @Test
    void listMcpServersInvalidSearchMode() throws Throwable {
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.get("/v0/servers").param("offset", "0")
                .param("limit", "100").param("searchMode", "invalid");
        assertServletException(NacosApiException.class, () -> mockMvc.perform(builder).andReturn(),
                "ErrCode:400, ErrMsg:Parameter 'searchMode' must be " + Constants.MCP_LIST_SEARCH_BLUR + " or "
                        + Constants.MCP_LIST_SEARCH_ACCURATE);
    }
    
    @Test
    void listMcpServers() throws Throwable {
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.get("/v0/servers").param("offset", "0")
                .param("limit", "100").param("searchMode", Constants.MCP_LIST_SEARCH_BLUR);
        McpRegistryServerList mcpRegistryServerList = new McpRegistryServerList();
        when(nacosMcpRegistryService.listMcpServers(any(ListServerForm.class))).thenReturn(mcpRegistryServerList);
        assertEquals(mapper.writeValueAsString(mcpRegistryServerList),
                mockMvc.perform(builder).andReturn().getResponse().getContentAsString());
    }
    
    @Test
    void listMcpServersWithoutSearchMode() throws Throwable {
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.get("/v0/servers").param("offset", "0")
                .param("limit", "100");
        McpRegistryServerList mcpRegistryServerList = new McpRegistryServerList();
        when(nacosMcpRegistryService.listMcpServers(any(ListServerForm.class))).thenReturn(mcpRegistryServerList);
        assertEquals(mapper.writeValueAsString(mcpRegistryServerList),
                mockMvc.perform(builder).andReturn().getResponse().getContentAsString());
    }
    
    @Test
    void getServer() throws Exception {
        String id = UUID.randomUUID().toString();
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.get("/v0/servers/" + id).param("version", "");
        when(nacosMcpRegistryService.getServer(eq(id), any(GetServerForm.class))).thenReturn(new McpRegistryServerDetail());
        MockHttpServletResponse response = mockMvc.perform(builder).andReturn().getResponse();
        assertEquals(200, response.getStatus());
        assertEquals(mapper.writeValueAsString(new McpRegistryServerDetail()), response.getContentAsString());
    }
    
    @Test
    void getServerNotFound() throws Exception {
        String id = UUID.randomUUID().toString();
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.get("/v0/servers/" + id).param("version", "");
        MockHttpServletResponse response = mockMvc.perform(builder).andReturn().getResponse();
        assertEquals(404, response.getStatus());
        assertEquals("{\"error\":\"Server not found\"}", response.getContentAsString());
    }
    
    @Test
    void getMcpServerToolsInfo() throws Exception {
        String id = UUID.randomUUID().toString();
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.get("/v0/servers/" + id + "/tools")
                .param("version", "");
        when(nacosMcpRegistryService.getTools(id, "")).thenReturn(new McpToolSpecification());
        assertEquals(mapper.writeValueAsString(new McpToolSpecification()),
                mockMvc.perform(builder).andReturn().getResponse().getContentAsString());
    }
    
    private static <T extends Throwable> void assertServletException(Class<T> expectedCause, Executable executable,
            String expectedMsg) throws Throwable {
        try {
            executable.execute();
        } catch (ServletException e) {
            Throwable caused = e.getCause();
            assertInstanceOf(expectedCause, caused);
            assertEquals(expectedMsg, caused.toString());
        }
    }
}