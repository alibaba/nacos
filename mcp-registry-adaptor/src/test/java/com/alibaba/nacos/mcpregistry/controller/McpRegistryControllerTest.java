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

import com.alibaba.nacos.api.ai.model.mcp.registry.McpRegistryServerDetail;
import com.alibaba.nacos.api.ai.model.mcp.registry.McpRegistryServerList;
import com.alibaba.nacos.api.ai.model.mcp.registry.ServerResponse;
import com.alibaba.nacos.api.ai.model.mcp.registry.OfficialMeta;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.mcpregistry.form.ListServersNacosForm;
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
    void setUp() throws Exception {
        cachedEnvironment = EnvUtil.getEnvironment();
        EnvUtil.setEnvironment(new StandardEnvironment());
        
        // Create a real controller instance without Spring initialization
        mcpRegistryController = new McpRegistryController(nacosMcpRegistryService);
        mockMvc = MockMvcBuilders.standaloneSetup(mcpRegistryController).build();
    }

    @AfterEach
    void tearDown() {
        EnvUtil.setEnvironment(cachedEnvironment);
    }

    @Test
    void listMcpServersInvalidCursor() throws Throwable {
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.get("/v0/servers")
                .param("cursor", "-1");
        assertServletException(NacosApiException.class, () -> mockMvc.perform(builder).andReturn(),
                "ErrCode:400, ErrMsg:cursor must be >= 0");
    }

    @Test
    void listMcpServersInvalidLimit() throws Throwable {
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.get("/v0/servers")
                .param("limit", "1000");
        assertServletException(NacosApiException.class, () -> mockMvc.perform(builder).andReturn(),
                "ErrCode:400, ErrMsg:limit must <= 100");
    }

    @Test
    void listMcpServersFirstPageWithNextCursor() throws Exception {
        ServerResponse sr1 = serverResponse("id-1", "2025-06-10T02:29:17Z", "2025-06-10T02:29:17Z");
        ServerResponse sr2 = serverResponse("id-2", "2025-06-11T02:29:17Z", "2025-06-12T02:29:17Z");
        McpRegistryServerList internal = new McpRegistryServerList();
        internal.setServers(java.util.List.of(sr1, sr2));

        // Mock the service to return our test data
        when(nacosMcpRegistryService.listMcpServers(any())).thenReturn(internal);

        // Test the controller directly instead of via MockMvc
        ListServersNacosForm form = new ListServersNacosForm();
        form.setLimit(2);
        form.validate();
        McpRegistryServerList result = mcpRegistryController.listMcpServers(form);

        assertEquals(2, result.getServers().size());
        assertEquals("2", result.getMetadata().getNextCursor());
    }

    @Test
    void listMcpServersLastPageNoNextCursor() throws Exception {
        ServerResponse sr1 = serverResponse("id-3", "2025-06-10T02:29:17Z", "2025-06-10T02:29:17Z");
        McpRegistryServerList internal = new McpRegistryServerList();
        internal.setServers(java.util.List.of(sr1));

        when(nacosMcpRegistryService.listMcpServers(any())).thenReturn(internal);

        // Test the controller directly
        ListServersNacosForm form = new ListServersNacosForm();
        form.setLimit(10);
        form.validate();
        McpRegistryServerList result = mcpRegistryController.listMcpServers(form);

        assertEquals(1, result.getServers().size());
        // next_cursor should be offset + returned -> "1"
        assertEquals("1", result.getMetadata().getNextCursor());
    }

    @Test
    void getServer() throws Exception {
        String serverName = "test-server";
        String version = "1.0.0";
        ServerResponse serverResponse = new ServerResponse();
        serverResponse.setServer(new McpRegistryServerDetail());
        when(nacosMcpRegistryService.getServer(eq(serverName), any(String.class), eq(version)))
                .thenReturn(serverResponse);
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders
                .get("/v0/servers/" + serverName + "/versions/" + version)
                .param("version", version);
        MockHttpServletResponse response = mockMvc.perform(builder).andReturn().getResponse();
        assertEquals(200, response.getStatus());
        assertEquals(mapper.writeValueAsString(serverResponse), response.getContentAsString());
    }

    @Test
    void getServerNotFound() throws Exception {
        String serverName = "nonexistent-server";
        String version = "1.0.0";
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders
                .get("/v0/servers/" + serverName + "/versions/" + version)
                .param("version", version);
        MockHttpServletResponse response = mockMvc.perform(builder).andReturn().getResponse();
        assertEquals(404, response.getStatus());
        assertEquals("{\"error\":\"Server not found\"}", response.getContentAsString());
    }

    private ServerResponse serverResponse(String id, String publishedAt, String updatedAt) {
        McpRegistryServerDetail server = new McpRegistryServerDetail();
        server.setName(id + "-name");
        server.setDescription("desc-" + id);

        ServerResponse response = new ServerResponse();
        response.setServer(server);

        ServerResponse.Meta meta = new ServerResponse.Meta();
        OfficialMeta official = new OfficialMeta();
        official.setPublishedAt(publishedAt);
        official.setUpdatedAt(updatedAt);
        meta.setOfficial(official);
        response.setMeta(meta);

        return response;
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