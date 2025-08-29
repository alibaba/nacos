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
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.auth.config.NacosAuthConfig;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.console.proxy.ai.McpProxy;
import com.alibaba.nacos.core.auth.AuthFilter;
import com.alibaba.nacos.sys.env.EnvUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConsoleMcpControllerTest {
    
    @Mock
    McpProxy mcpProxy;
    
    @Mock
    private NacosAuthConfig authConfig;
    
    @InjectMocks
    private AuthFilter authFilter;
    
    private MockMvc mockmvc;
    
    ConsoleMcpController consoleMcpController;
    
    @BeforeEach
    void setUp() {
        EnvUtil.setEnvironment(new StandardEnvironment());
        consoleMcpController = new ConsoleMcpController(mcpProxy);
        mockmvc = MockMvcBuilders.standaloneSetup(consoleMcpController).addFilter(authFilter).build();
        when(authConfig.isAuthEnabled()).thenReturn(false);
    }
    
    @AfterEach
    void tearDown() {
    }
    
    @Test
    void listMcpServers() throws Exception {
        Page<McpServerBasicInfo> mockPage = new Page<>();
        when(mcpProxy.listMcpServers("nacos-default-mcp", "test", "blur", 1, 10)).thenReturn(mockPage);
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.get("/v3/console/ai/mcp/list")
                .param("namespaceId", "nacos-default-mcp").param("mcpName", "test").param("search", "blur")
                .param("pageNo", "1").param("pageSize", "10");
        MockHttpServletResponse response = mockmvc.perform(builder).andReturn().getResponse();
        String actualValue = response.getContentAsString();
        Result<Page<McpServerBasicInfo>> result = JacksonUtils.toObj(actualValue, new TypeReference<>() {
        });
        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
    }
    
    @Test
    void getMcpServer() throws Exception {
        McpServerDetailInfo mock = new McpServerDetailInfo();
        when(mcpProxy.getMcpServer("nacos-default-mcp", "test", "id", "version")).thenReturn(mock);
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.get("/v3/console/ai/mcp")
                .param("namespaceId", "nacos-default-mcp").param("mcpName", "test").param("mcpId", "id")
                .param("version", "version").param("publish", "true");
        MockHttpServletResponse response = mockmvc.perform(builder).andReturn().getResponse();
        String actualValue = response.getContentAsString();
        Result<McpServerDetailInfo> result = JacksonUtils.toObj(actualValue, new TypeReference<>() {
        });
        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
    }
    
    @Test
    void createMcpServer() throws Exception {
        String mcpId = UUID.randomUUID().toString();
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.post("/v3/console/ai/mcp")
                .param("namespaceId", "nacos-default-mcp").param("mcpName", "test")
                .param("serverSpecification", "{\"id\":\"" + mcpId + "\",\"protocol\":\"stdio\"}");
        when(mcpProxy.createMcpServer(any(),
                any(McpServerBasicInfo.class), any(), any())).thenReturn(mcpId);
        MockHttpServletResponse response = mockmvc.perform(builder).andReturn().getResponse();
        String actualValue = response.getContentAsString();
        Result<String> result = JacksonUtils.toObj(actualValue, new TypeReference<>() {
        });
        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertEquals(mcpId, result.getData());
    }
    
    @Test
    void updateMcpServer() throws Exception {
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.put("/v3/console/ai/mcp")
                .param("namespaceId", "nacos-default-mcp").param("mcpName", "test").param("mcpId", "id")
                .param("version", "version").param("serverSpecification", "{\"protocol\":\"stdio\"}")
                .param("latest", "true");
        MockHttpServletResponse response = mockmvc.perform(builder).andReturn().getResponse();
        String actualValue = response.getContentAsString();
        Result<String> result = JacksonUtils.toObj(actualValue, new TypeReference<>() {
        });
        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertEquals("ok", result.getData());
    }
    
    @Test
    void deleteMcpServer() throws Exception {
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.delete("/v3/console/ai/mcp")
                .param("namespaceId", "nacos-default-mcp").param("mcpName", "test");
        MockHttpServletResponse response = mockmvc.perform(builder).andReturn().getResponse();
        String actualValue = response.getContentAsString();
        Result<String> result = JacksonUtils.toObj(actualValue, new TypeReference<>() {
        });
        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertEquals("ok", result.getData());
    }
}
