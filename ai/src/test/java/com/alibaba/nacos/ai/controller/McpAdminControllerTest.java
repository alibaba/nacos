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

package com.alibaba.nacos.ai.controller;

import com.alibaba.nacos.ai.constant.Constants;
import com.alibaba.nacos.ai.service.McpServerOperationService;
import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.api.ai.model.mcp.McpServerBasicInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpServerDetailInfo;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.sys.env.EnvUtil;
import com.fasterxml.jackson.core.type.TypeReference;
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
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = MockServletContext.class)
@WebAppConfiguration
class McpAdminControllerTest {
    
    private static final String MCP_SERVER_SPEC =
            "{\"protocol\":\"stdio\",\"frontProtocol\":\"stdio\",\"name\":\"nacos-mcp-server\","
                    + "\"id\":\"\",\"description\":\"nacos local mcp server(test version)\",\"versionDetail\":{\"version\":\"1.0.0\"},"
                    + "\"enabled\":true,\"localServerConfig\":{}}'";
    
    private McpAdminController mcpAdminController;
    
    private MockMvc mockMvc;
    
    private ConfigurableEnvironment cachedEnvironment;
    
    @Mock
    private McpServerOperationService mcpServerOperationService;
    
    @BeforeEach
    void setUp() {
        cachedEnvironment = EnvUtil.getEnvironment();
        EnvUtil.setEnvironment(new StandardEnvironment());
        mcpAdminController = new McpAdminController(mcpServerOperationService);
        mockMvc = MockMvcBuilders.standaloneSetup(mcpAdminController).build();
    }
    
    @AfterEach
    void tearDown() {
        EnvUtil.setEnvironment(cachedEnvironment);
    }
    
    @Test
    void listMcpServersWithIllegalSearch() throws Throwable {
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.get(Constants.MCP_ADMIN_PATH + "/list")
                .param("search", "illegal");
        assertServletException(NacosApiException.class, () -> mockMvc.perform(builder).andReturn(),
                "ErrCode:400, ErrMsg:Request parameter `search` should be `accurate` or `blur`.");
    }
    
    @Test
    void listMcpServersWithIllegalPage() throws Throwable {
        final MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.get(Constants.MCP_ADMIN_PATH + "/list")
                .param("search", "blur").param("pageNo", "-1");
        assertServletException(NacosApiException.class, () -> mockMvc.perform(builder).andReturn(),
                "ErrCode:400, ErrMsg:Required parameter 'pageNo' should be positive integer, current is -1");
        final MockHttpServletRequestBuilder builder2 = MockMvcRequestBuilders.get(Constants.MCP_ADMIN_PATH + "/list")
                .param("search", "blur").param("pageNo", "1").param("pageSize", "0");
        assertServletException(NacosApiException.class, () -> mockMvc.perform(builder2).andReturn(),
                "ErrCode:400, ErrMsg:Required parameter 'pageSize' should be positive integer, current is 0");
    }
    
    @Test
    void listMcpServersSuccess() throws Throwable {
        when(mcpServerOperationService.listMcpServerWithPage(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE, null,
                Constants.MCP_LIST_SEARCH_ACCURATE, 1, 100)).thenReturn(new Page<>());
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.get(Constants.MCP_ADMIN_PATH + "/list")
                .param("pageNo", "1").param("pageSize", "100");
        MockHttpServletResponse response = mockMvc.perform(builder).andReturn().getResponse();
        assertEquals(200, response.getStatus());
        Result<Page<McpServerBasicInfo>> result = JacksonUtils.toObj(response.getContentAsString(),
                new TypeReference<>() {
                });
        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertInstanceOf(Page.class, result.getData());
    }
    
    @Test
    void getMcpServerWithoutMcpIdAndMcpName() throws Throwable {
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.get(Constants.MCP_ADMIN_PATH);
        assertServletException(NacosApiException.class, () -> mockMvc.perform(builder).andReturn(),
                "ErrCode:400, ErrMsg:Required parameter 'mcpId' or 'mcpName' type String at lease one is not present");
    }
    
    @Test
    void getMcpServerWithMcpName() throws Exception {
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.get(Constants.MCP_ADMIN_PATH)
                .param("mcpName", "testName");
        when(mcpServerOperationService.getMcpServerDetail(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE, null, "testName",
                null)).thenReturn(new McpServerDetailInfo());
        MockHttpServletResponse response = mockMvc.perform(builder).andReturn().getResponse();
        assertEquals(200, response.getStatus());
        Result<McpServerDetailInfo> result = JacksonUtils.toObj(response.getContentAsString(), new TypeReference<>() {
        });
        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertInstanceOf(McpServerDetailInfo.class, result.getData());
    }
    
    @Test
    void getMcpServerWithMcpId() throws Exception {
        String id = UUID.randomUUID().toString();
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.get(Constants.MCP_ADMIN_PATH).param("mcpId", id);
        when(mcpServerOperationService.getMcpServerDetail(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE, id, null,
                null)).thenReturn(new McpServerDetailInfo());
        MockHttpServletResponse response = mockMvc.perform(builder).andReturn().getResponse();
        assertEquals(200, response.getStatus());
        Result<McpServerDetailInfo> result = JacksonUtils.toObj(response.getContentAsString(), new TypeReference<>() {
        });
        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertInstanceOf(McpServerDetailInfo.class, result.getData());
    }
    
    @Test
    void getMcpServerWithVersion() throws Exception {
        String id = UUID.randomUUID().toString();
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.get(Constants.MCP_ADMIN_PATH).param("mcpId", id)
                .param("namespaceId", "testNs").param("version", "1.0.0");
        when(mcpServerOperationService.getMcpServerDetail("testNs", id, null, "1.0.0")).thenReturn(
                new McpServerDetailInfo());
        MockHttpServletResponse response = mockMvc.perform(builder).andReturn().getResponse();
        assertEquals(200, response.getStatus());
        Result<McpServerDetailInfo> result = JacksonUtils.toObj(response.getContentAsString(), new TypeReference<>() {
        });
        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertInstanceOf(McpServerDetailInfo.class, result.getData());
    }
    
    @Test
    void createMcpServerWithoutSpec() throws Throwable {
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.post(Constants.MCP_ADMIN_PATH);
        assertServletException(NacosApiException.class, () -> mockMvc.perform(builder).andReturn(),
                "ErrCode:400, ErrMsg:Required parameter 'serverSpecification' type McpServerBasicInfo is not present");
    }
    
    @Test
    void createMcpServerWithSpec() throws Exception {
        String mcpId = UUID.randomUUID().toString();
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.post(Constants.MCP_ADMIN_PATH)
                .param("serverSpecification", MCP_SERVER_SPEC);
        when(mcpServerOperationService.createMcpServer(eq(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE),
                any(McpServerBasicInfo.class), any(), any())).thenReturn(mcpId);
        MockHttpServletResponse response = mockMvc.perform(builder).andReturn().getResponse();
        assertEquals(200, response.getStatus());

        Result<String> result = JacksonUtils.toObj(response.getContentAsString(), new TypeReference<>() {
        });

        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertEquals(mcpId, result.getData());
        verify(mcpServerOperationService).createMcpServer(eq(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE),
                any(McpServerBasicInfo.class), isNull(), isNull());
    }
    
    @Test
    void updateMcpServerWithoutSpec() throws Throwable {
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.put(Constants.MCP_ADMIN_PATH);
        assertServletException(NacosApiException.class, () -> mockMvc.perform(builder).andReturn(),
                "ErrCode:400, ErrMsg:Required parameter 'serverSpecification' type McpServerBasicInfo is not present");
    }
    
    @Test
    void updateMcpServerWithSpec() throws Exception {
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.put(Constants.MCP_ADMIN_PATH)
                .param("serverSpecification", MCP_SERVER_SPEC);
        MockHttpServletResponse response = mockMvc.perform(builder).andReturn().getResponse();
        assertEquals(200, response.getStatus());
        Result<String> result = JacksonUtils.toObj(response.getContentAsString(), new TypeReference<>() {
        });
        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertEquals("ok", result.getData());
        verify(mcpServerOperationService).updateMcpServer(eq(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE), eq(true),
                any(McpServerBasicInfo.class), isNull(), isNull(), eq(false));
    }

    @Test
    void updateMcpServerWithOverrideExisting() throws Exception {
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.put(Constants.MCP_ADMIN_PATH)
                .param("serverSpecification", MCP_SERVER_SPEC).param("overrideExisting", "true");
        MockHttpServletResponse response = mockMvc.perform(builder).andReturn().getResponse();
        assertEquals(200, response.getStatus());
        Result<String> result = JacksonUtils.toObj(response.getContentAsString(), new TypeReference<>() {
        });
        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertEquals("ok", result.getData());
        verify(mcpServerOperationService).updateMcpServer(eq(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE), eq(true),
                any(McpServerBasicInfo.class), isNull(), isNull(), eq(true));
    }
    
    @Test
    void updateMcpServerWithoutLatest() throws Exception {
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.put(Constants.MCP_ADMIN_PATH)
                .param("serverSpecification", MCP_SERVER_SPEC).param("latest", "false");
        MockHttpServletResponse response = mockMvc.perform(builder).andReturn().getResponse();
        assertEquals(200, response.getStatus());
        Result<String> result = JacksonUtils.toObj(response.getContentAsString(), new TypeReference<>() {
        });
        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertEquals("ok", result.getData());
        verify(mcpServerOperationService).updateMcpServer(eq(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE), eq(false),
                any(McpServerBasicInfo.class), isNull(), isNull(), eq(false));
    }
    
    @Test
    void deleteMcpServerWithoutMcpIdAndMcpName() throws Throwable {
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.delete(Constants.MCP_ADMIN_PATH);
        assertServletException(NacosApiException.class, () -> mockMvc.perform(builder).andReturn(),
                "ErrCode:400, ErrMsg:Required parameter 'mcpId' or 'mcpName' type String at lease one is not present");
    }
    
    @Test
    void deleteMcpServerWithMcpName() throws Exception {
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.delete(Constants.MCP_ADMIN_PATH)
                .param("mcpName", "testName");
        MockHttpServletResponse response = mockMvc.perform(builder).andReturn().getResponse();
        assertEquals(200, response.getStatus());
        Result<String> result = JacksonUtils.toObj(response.getContentAsString(), new TypeReference<>() {
        });
        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertEquals("ok", result.getData());
        verify(mcpServerOperationService).deleteMcpServer(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE, "testName", null,
                null);
    }
    
    @Test
    void deleteMcpServerWithMcpId() throws Exception {
        String id = UUID.randomUUID().toString();
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.delete(Constants.MCP_ADMIN_PATH)
                .param("mcpId", id);
        MockHttpServletResponse response = mockMvc.perform(builder).andReturn().getResponse();
        assertEquals(200, response.getStatus());
        Result<String> result = JacksonUtils.toObj(response.getContentAsString(), new TypeReference<>() {
        });
        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertEquals("ok", result.getData());
        verify(mcpServerOperationService).deleteMcpServer(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE, null, id, null);
    }
    
    @Test
    void deleteMcpServerWithVersion() throws Exception {
        String id = UUID.randomUUID().toString();
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.delete(Constants.MCP_ADMIN_PATH)
                .param("mcpId", id).param("namespaceId", "testNs").param("version", "1.0.0");
        MockHttpServletResponse response = mockMvc.perform(builder).andReturn().getResponse();
        assertEquals(200, response.getStatus());
        Result<String> result = JacksonUtils.toObj(response.getContentAsString(), new TypeReference<>() {
        });
        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertEquals("ok", result.getData());
        verify(mcpServerOperationService).deleteMcpServer("testNs", null, id, "1.0.0");
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