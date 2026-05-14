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

package com.alibaba.nacos.console.controller.v3.ai;

import com.alibaba.nacos.api.ai.model.mcp.McpServerBasicInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpServerDetailInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpServerImportResponse;
import com.alibaba.nacos.api.ai.model.mcp.McpServerImportValidationResult;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.console.proxy.ai.McpProxy;
import com.alibaba.nacos.sys.env.EnvUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConsoleMcpControllerTest {
    
    @Mock
    private McpProxy mcpProxy;
    
    private MockMvc mockMvc;
    
    @BeforeEach
    void setUp() {
        EnvUtil.setEnvironment(new StandardEnvironment());
        mockMvc = MockMvcBuilders.standaloneSetup(
            new ConsoleMcpController(mcpProxy)).build();
    }
    
    @Test
    void testListMcpServers() throws Exception {
        Page<McpServerBasicInfo> mockPage = new Page<>();
        when(mcpProxy.listMcpServers("nacos-default-mcp", "test", "blur", 1, 10))
            .thenReturn(mockPage);
        MockHttpServletRequestBuilder builder =
            MockMvcRequestBuilders.get("/v3/console/ai/mcp/list")
                .param("namespaceId", "nacos-default-mcp").param("mcpName", "test")
                .param("search", "blur")
                .param("pageNo", "1").param("pageSize", "10");
        MockHttpServletResponse response = mockMvc.perform(builder).andReturn().getResponse();
        Result<Page<McpServerBasicInfo>> result =
            JacksonUtils.toObj(response.getContentAsString(), new TypeReference<>() {
            });
        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
    }
    
    @Test
    void testGetMcpServer() throws Exception {
        McpServerDetailInfo mock = new McpServerDetailInfo();
        when(mcpProxy.getMcpServer("nacos-default-mcp", "test", "id", "version"))
            .thenReturn(mock);
        MockHttpServletRequestBuilder builder =
            MockMvcRequestBuilders.get("/v3/console/ai/mcp")
                .param("namespaceId", "nacos-default-mcp").param("mcpName", "test")
                .param("mcpId", "id").param("version", "version")
                .param("publish", "true");
        MockHttpServletResponse response = mockMvc.perform(builder).andReturn().getResponse();
        Result<McpServerDetailInfo> result = JacksonUtils.toObj(
            response.getContentAsString(), new TypeReference<>() {
            });
        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
    }
    
    @Test
    void testCreateMcpServer() throws Exception {
        String mcpId = UUID.randomUUID().toString();
        MockHttpServletRequestBuilder builder =
            MockMvcRequestBuilders.post("/v3/console/ai/mcp")
                .param("namespaceId", "nacos-default-mcp").param("mcpName", "test")
                .param("serverSpecification",
                    "{\"id\":\"" + mcpId + "\",\"protocol\":\"stdio\"}");
        when(mcpProxy.createMcpServer(any(), any(McpServerBasicInfo.class), any(), any()))
            .thenReturn(mcpId);
        MockHttpServletResponse response = mockMvc.perform(builder).andReturn().getResponse();
        Result<String> result = JacksonUtils.toObj(
            response.getContentAsString(), new TypeReference<>() {
            });
        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertEquals(mcpId, result.getData());
    }
    
    @Test
    void testUpdateMcpServer() throws Exception {
        MockHttpServletRequestBuilder builder =
            MockMvcRequestBuilders.put("/v3/console/ai/mcp")
                .param("namespaceId", "nacos-default-mcp").param("mcpName", "test")
                .param("mcpId", "id").param("version", "version")
                .param("serverSpecification", "{\"protocol\":\"stdio\"}")
                .param("latest", "true");
        MockHttpServletResponse response = mockMvc.perform(builder).andReturn().getResponse();
        Result<String> result = JacksonUtils.toObj(
            response.getContentAsString(), new TypeReference<>() {
            });
        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertEquals("ok", result.getData());
    }
    
    @Test
    void testDeleteMcpServer() throws Exception {
        MockHttpServletRequestBuilder builder =
            MockMvcRequestBuilders.delete("/v3/console/ai/mcp")
                .param("namespaceId", "nacos-default-mcp").param("mcpName", "test");
        MockHttpServletResponse response = mockMvc.perform(builder).andReturn().getResponse();
        Result<String> result = JacksonUtils.toObj(
            response.getContentAsString(), new TypeReference<>() {
            });
        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertEquals("ok", result.getData());
    }
    
    @Test
    void testValidateImport() throws Exception {
        McpServerImportValidationResult validationResult =
            new McpServerImportValidationResult();
        when(mcpProxy.validateImport(anyString(), any())).thenReturn(validationResult);
        
        MockHttpServletResponse response = mockMvc.perform(
            MockMvcRequestBuilders.post("/v3/console/ai/mcp/import/validate")
                .param("namespaceId", "nacos-default-mcp")
                .param("mcpName", "test")
                .param("importType", "json")
                .param("data", "[{\"name\":\"test-server\"}]"))
            .andReturn().getResponse();
        
        assertEquals(200, response.getStatus());
        Result<McpServerImportValidationResult> result = JacksonUtils.toObj(
            response.getContentAsString(), new TypeReference<>() {
            });
        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertNotNull(result.getData());
    }
    
    @Test
    void testExecuteImport() throws Exception {
        McpServerImportResponse importResponse = new McpServerImportResponse();
        when(mcpProxy.executeImport(anyString(), any())).thenReturn(importResponse);
        
        MockHttpServletResponse response = mockMvc.perform(
            MockMvcRequestBuilders.post("/v3/console/ai/mcp/import/execute")
                .param("namespaceId", "nacos-default-mcp")
                .param("mcpName", "test")
                .param("importType", "json")
                .param("data", "[{\"name\":\"test-server\"}]"))
            .andReturn().getResponse();
        
        assertEquals(200, response.getStatus());
        Result<McpServerImportResponse> result = JacksonUtils.toObj(
            response.getContentAsString(), new TypeReference<>() {
            });
        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertNotNull(result.getData());
    }
}
