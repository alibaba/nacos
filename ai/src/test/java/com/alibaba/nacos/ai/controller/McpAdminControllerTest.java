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
import com.alibaba.nacos.ai.service.mcp.McpCompatibilityOperationService;
import com.alibaba.nacos.ai.service.mcp.McpOperationService;
import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.api.ai.model.mcp.McpLifecycleVersionDetail;
import com.alibaba.nacos.api.ai.model.mcp.McpLifecycleVersionSummary;
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

import java.util.UUID;
import java.util.Map;

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
    
    private static final String MCP_RESOURCE_SPEC =
        "{\"resources\":[{\"name\":\"readme\",\"uri\":\"file:///README.md\",\"description\":\"test resource\"}]}";
    
    private McpAdminController mcpAdminController;
    
    private MockMvc mockMvc;
    
    private ConfigurableEnvironment cachedEnvironment;
    
    @MockitoBean
    private McpOperationService mcpServerOperationService;
    
    @MockitoBean
    private McpCompatibilityOperationService lifecycleOperationService;
    
    @BeforeEach
    void setUp() {
        cachedEnvironment = EnvUtil.getEnvironment();
        EnvUtil.setEnvironment(new StandardEnvironment());
        mcpAdminController = new McpAdminController(mcpServerOperationService,
            lifecycleOperationService);
        mockMvc = MockMvcBuilders.standaloneSetup(mcpAdminController).build();
    }
    
    @AfterEach
    void tearDown() {
        EnvUtil.setEnvironment(cachedEnvironment);
    }
    
    @Test
    void listMcpServersWithIllegalSearch() throws Throwable {
        MockHttpServletRequestBuilder builder =
            MockMvcRequestBuilders.get(Constants.MCP_ADMIN_PATH + "/list")
                .param("search", "illegal");
        assertServletException(NacosApiException.class, () -> mockMvc.perform(builder).andReturn(),
            "ErrCode:400, ErrMsg:Request parameter `search` should be `accurate` or `blur`.");
    }
    
    @Test
    void listMcpServersWithIllegalPage() throws Throwable {
        final MockHttpServletRequestBuilder builder =
            MockMvcRequestBuilders.get(Constants.MCP_ADMIN_PATH + "/list")
                .param("search", "blur").param("pageNo", "-1");
        assertServletException(NacosApiException.class, () -> mockMvc.perform(builder).andReturn(),
            "ErrCode:400, ErrMsg:Required parameter 'pageNo' should be positive integer, current is -1");
        final MockHttpServletRequestBuilder builder2 =
            MockMvcRequestBuilders.get(Constants.MCP_ADMIN_PATH + "/list")
                .param("search", "blur").param("pageNo", "1").param("pageSize", "0");
        assertServletException(NacosApiException.class, () -> mockMvc.perform(builder2).andReturn(),
            "ErrCode:400, ErrMsg:Required parameter 'pageSize' should be positive integer, current is 0");
    }
    
    @Test
    void listMcpServersSuccess() throws Throwable {
        when(mcpServerOperationService.listMcpServerWithPage(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE,
            null,
            Constants.MCP_LIST_SEARCH_ACCURATE, 1, 100)).thenReturn(new Page<>());
        MockHttpServletRequestBuilder builder =
            MockMvcRequestBuilders.get(Constants.MCP_ADMIN_PATH + "/list")
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
        MockHttpServletRequestBuilder builder =
            MockMvcRequestBuilders.get(Constants.MCP_ADMIN_PATH);
        assertServletException(NacosApiException.class, () -> mockMvc.perform(builder).andReturn(),
            "ErrCode:400, ErrMsg:Required parameter 'mcpId' or 'mcpName' type String at lease one is not present");
    }
    
    @Test
    void getMcpServerWithMcpName() throws Exception {
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.get(Constants.MCP_ADMIN_PATH)
            .param("mcpName", "testName");
        when(mcpServerOperationService.getMcpServerDetail(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE,
            null, "testName",
            null)).thenReturn(new McpServerDetailInfo());
        MockHttpServletResponse response = mockMvc.perform(builder).andReturn().getResponse();
        assertEquals(200, response.getStatus());
        Result<McpServerDetailInfo> result =
            JacksonUtils.toObj(response.getContentAsString(), new TypeReference<>() {
            });
        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertInstanceOf(McpServerDetailInfo.class, result.getData());
    }
    
    @Test
    void getMcpServerWithMcpId() throws Exception {
        String id = UUID.randomUUID().toString();
        MockHttpServletRequestBuilder builder =
            MockMvcRequestBuilders.get(Constants.MCP_ADMIN_PATH).param("mcpId", id);
        when(mcpServerOperationService.getMcpServerDetail(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE, id,
            null,
            null)).thenReturn(new McpServerDetailInfo());
        MockHttpServletResponse response = mockMvc.perform(builder).andReturn().getResponse();
        assertEquals(200, response.getStatus());
        Result<McpServerDetailInfo> result =
            JacksonUtils.toObj(response.getContentAsString(), new TypeReference<>() {
            });
        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertInstanceOf(McpServerDetailInfo.class, result.getData());
    }
    
    @Test
    void getMcpServerWithVersion() throws Exception {
        String id = UUID.randomUUID().toString();
        MockHttpServletRequestBuilder builder =
            MockMvcRequestBuilders.get(Constants.MCP_ADMIN_PATH).param("mcpId", id)
                .param("namespaceId", "testNs").param("version", "1.0.0");
        when(mcpServerOperationService.getMcpServerDetail("testNs", id, null, "1.0.0")).thenReturn(
            new McpServerDetailInfo());
        MockHttpServletResponse response = mockMvc.perform(builder).andReturn().getResponse();
        assertEquals(200, response.getStatus());
        Result<McpServerDetailInfo> result =
            JacksonUtils.toObj(response.getContentAsString(), new TypeReference<>() {
            });
        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertInstanceOf(McpServerDetailInfo.class, result.getData());
    }
    
    @Test
    void createMcpServerWithoutSpec() throws Throwable {
        MockHttpServletRequestBuilder builder =
            MockMvcRequestBuilders.post(Constants.MCP_ADMIN_PATH);
        assertServletException(NacosApiException.class, () -> mockMvc.perform(builder).andReturn(),
            "ErrCode:400, ErrMsg:Required parameter 'serverSpecification' type McpServerBasicInfo is not present");
    }
    
    @Test
    void createMcpServerWithSpec() throws Exception {
        String mcpId = UUID.randomUUID().toString();
        MockHttpServletRequestBuilder builder =
            MockMvcRequestBuilders.post(Constants.MCP_ADMIN_PATH)
                .param("serverSpecification", MCP_SERVER_SPEC);
        when(mcpServerOperationService.createMcpServer(eq(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE),
            any(McpServerBasicInfo.class), any(), any(), any())).thenReturn(mcpId);
        MockHttpServletResponse response = mockMvc.perform(builder).andReturn().getResponse();
        assertEquals(200, response.getStatus());
        
        Result<String> result =
            JacksonUtils.toObj(response.getContentAsString(), new TypeReference<>() {
            });
        
        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertEquals(mcpId, result.getData());
        verify(mcpServerOperationService).createMcpServer(eq(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE),
            any(McpServerBasicInfo.class), isNull(), isNull(), isNull());
    }
    
    @Test
    void createMcpServerWithResourceSpec() throws Exception {
        String mcpId = UUID.randomUUID().toString();
        MockHttpServletRequestBuilder builder =
            MockMvcRequestBuilders.post(Constants.MCP_ADMIN_PATH)
                .param("serverSpecification", MCP_SERVER_SPEC)
                .param("resourceSpecification", MCP_RESOURCE_SPEC);
        when(mcpServerOperationService.createMcpServer(eq(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE),
            any(McpServerBasicInfo.class), any(), any(), any())).thenReturn(mcpId);
        MockHttpServletResponse response = mockMvc.perform(builder).andReturn().getResponse();
        assertEquals(200, response.getStatus());
        verify(mcpServerOperationService).createMcpServer(eq(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE),
            any(McpServerBasicInfo.class), isNull(), any(), isNull());
    }
    
    @Test
    void updateMcpServerWithoutSpec() throws Throwable {
        MockHttpServletRequestBuilder builder =
            MockMvcRequestBuilders.put(Constants.MCP_ADMIN_PATH);
        assertServletException(NacosApiException.class, () -> mockMvc.perform(builder).andReturn(),
            "ErrCode:400, ErrMsg:Required parameter 'serverSpecification' type McpServerBasicInfo is not present");
    }
    
    @Test
    void updateMcpServerWithSpec() throws Exception {
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.put(Constants.MCP_ADMIN_PATH)
            .param("serverSpecification", MCP_SERVER_SPEC);
        MockHttpServletResponse response = mockMvc.perform(builder).andReturn().getResponse();
        assertEquals(200, response.getStatus());
        Result<String> result =
            JacksonUtils.toObj(response.getContentAsString(), new TypeReference<>() {
            });
        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertEquals("ok", result.getData());
        verify(mcpServerOperationService).updateMcpServer(eq(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE),
            eq(true),
            any(McpServerBasicInfo.class), isNull(), isNull(), isNull(), eq(false));
    }
    
    @Test
    void updateMcpServerWithOverrideExisting() throws Exception {
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.put(Constants.MCP_ADMIN_PATH)
            .param("serverSpecification", MCP_SERVER_SPEC).param("overrideExisting", "true");
        MockHttpServletResponse response = mockMvc.perform(builder).andReturn().getResponse();
        assertEquals(200, response.getStatus());
        Result<String> result =
            JacksonUtils.toObj(response.getContentAsString(), new TypeReference<>() {
            });
        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertEquals("ok", result.getData());
        verify(mcpServerOperationService).updateMcpServer(eq(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE),
            eq(true),
            any(McpServerBasicInfo.class), isNull(), isNull(), isNull(), eq(true));
    }
    
    @Test
    void updateMcpServerWithoutLatest() throws Exception {
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.put(Constants.MCP_ADMIN_PATH)
            .param("serverSpecification", MCP_SERVER_SPEC).param("latest", "false");
        MockHttpServletResponse response = mockMvc.perform(builder).andReturn().getResponse();
        assertEquals(200, response.getStatus());
        Result<String> result =
            JacksonUtils.toObj(response.getContentAsString(), new TypeReference<>() {
            });
        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertEquals("ok", result.getData());
        verify(mcpServerOperationService).updateMcpServer(eq(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE),
            eq(false),
            any(McpServerBasicInfo.class), isNull(), isNull(), isNull(), eq(false));
    }
    
    @Test
    void deleteMcpServerWithoutMcpIdAndMcpName() throws Throwable {
        MockHttpServletRequestBuilder builder =
            MockMvcRequestBuilders.delete(Constants.MCP_ADMIN_PATH);
        assertServletException(NacosApiException.class, () -> mockMvc.perform(builder).andReturn(),
            "ErrCode:400, ErrMsg:Required parameter 'mcpId' or 'mcpName' type String at lease one is not present");
    }
    
    @Test
    void deleteMcpServerWithMcpName() throws Exception {
        MockHttpServletRequestBuilder builder =
            MockMvcRequestBuilders.delete(Constants.MCP_ADMIN_PATH)
                .param("mcpName", "testName");
        MockHttpServletResponse response = mockMvc.perform(builder).andReturn().getResponse();
        assertEquals(200, response.getStatus());
        Result<String> result =
            JacksonUtils.toObj(response.getContentAsString(), new TypeReference<>() {
            });
        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertEquals("ok", result.getData());
        verify(mcpServerOperationService).deleteMcpServer(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE,
            "testName", null,
            null);
    }
    
    @Test
    void deleteMcpServerWithMcpId() throws Exception {
        String id = UUID.randomUUID().toString();
        MockHttpServletRequestBuilder builder =
            MockMvcRequestBuilders.delete(Constants.MCP_ADMIN_PATH)
                .param("mcpId", id);
        MockHttpServletResponse response = mockMvc.perform(builder).andReturn().getResponse();
        assertEquals(200, response.getStatus());
        Result<String> result =
            JacksonUtils.toObj(response.getContentAsString(), new TypeReference<>() {
            });
        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertEquals("ok", result.getData());
        verify(mcpServerOperationService).deleteMcpServer(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE,
            null, id, null);
    }
    
    @Test
    void deleteMcpServerWithVersion() throws Exception {
        String id = UUID.randomUUID().toString();
        MockHttpServletRequestBuilder builder =
            MockMvcRequestBuilders.delete(Constants.MCP_ADMIN_PATH)
                .param("mcpId", id).param("namespaceId", "testNs").param("version", "1.0.0");
        MockHttpServletResponse response = mockMvc.perform(builder).andReturn().getResponse();
        assertEquals(200, response.getStatus());
        Result<String> result =
            JacksonUtils.toObj(response.getContentAsString(), new TypeReference<>() {
            });
        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertEquals("ok", result.getData());
        verify(mcpServerOperationService).deleteMcpServer("testNs", null, id, "1.0.0");
    }
    
    @Test
    void standardLifecycleApisDelegateByNameAndExactVersion() throws Exception {
        McpLifecycleVersionDetail detail = new McpLifecycleVersionDetail();
        McpLifecycleVersionSummary summary = new McpLifecycleVersionSummary();
        when(lifecycleOperationService.listLifecycleVersions(
            AiConstants.Mcp.MCP_DEFAULT_NAMESPACE, "nacos-mcp-server", "draft", 1, 10))
            .thenReturn(new Page<>());
        when(lifecycleOperationService.getLifecycleVersion(
            AiConstants.Mcp.MCP_DEFAULT_NAMESPACE, "nacos-mcp-server", "1.0.0"))
            .thenReturn(detail);
        when(lifecycleOperationService.createLifecycleDraft(
            eq(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE), any(McpServerBasicInfo.class), isNull(),
            isNull(), isNull())).thenReturn(detail);
        when(lifecycleOperationService.updateLifecycleDraft(
            eq(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE), any(McpServerBasicInfo.class), isNull(),
            isNull(), isNull())).thenReturn(detail);
        when(lifecycleOperationService.submitLifecycleVersion(
            AiConstants.Mcp.MCP_DEFAULT_NAMESPACE, "nacos-mcp-server", "1.0.0"))
            .thenReturn(summary);
        when(lifecycleOperationService.publishLifecycleVersion(
            AiConstants.Mcp.MCP_DEFAULT_NAMESPACE, "nacos-mcp-server", "1.0.0"))
            .thenReturn(summary);
        when(lifecycleOperationService.forcePublishLifecycleVersion(
            AiConstants.Mcp.MCP_DEFAULT_NAMESPACE, "nacos-mcp-server", "1.0.0"))
            .thenReturn(summary);
        when(lifecycleOperationService.redraftLifecycleVersion(
            AiConstants.Mcp.MCP_DEFAULT_NAMESPACE, "nacos-mcp-server", "1.0.0"))
            .thenReturn(summary);
        when(lifecycleOperationService.onlineLifecycleVersion(
            AiConstants.Mcp.MCP_DEFAULT_NAMESPACE, "nacos-mcp-server", "1.0.0"))
            .thenReturn(summary);
        when(lifecycleOperationService.offlineLifecycleVersion(
            AiConstants.Mcp.MCP_DEFAULT_NAMESPACE, "nacos-mcp-server", "1.0.0"))
            .thenReturn(summary);
        when(lifecycleOperationService.updateLifecycleLabels(
            AiConstants.Mcp.MCP_DEFAULT_NAMESPACE, "nacos-mcp-server", Map.of()))
            .thenReturn(Map.of());
        
        assertEquals(200, mockMvc.perform(MockMvcRequestBuilders.get(
            Constants.MCP_ADMIN_PATH + "/versions").param("mcpName", "nacos-mcp-server")
            .param("status", "draft").param("pageNo", "1").param("pageSize", "10"))
            .andReturn().getResponse().getStatus());
        assertEquals(200, lifecycleVersionRequest(MockMvcRequestBuilders.get(
            Constants.MCP_ADMIN_PATH + "/version")).andReturn().getResponse().getStatus());
        assertEquals(200, lifecycleDraftRequest(MockMvcRequestBuilders.post(
            Constants.MCP_ADMIN_PATH + "/draft")).andReturn().getResponse().getStatus());
        assertEquals(200, lifecycleDraftRequest(MockMvcRequestBuilders.put(
            Constants.MCP_ADMIN_PATH + "/draft")).andReturn().getResponse().getStatus());
        assertEquals(200, lifecycleVersionRequest(MockMvcRequestBuilders.delete(
            Constants.MCP_ADMIN_PATH + "/draft")).andReturn().getResponse().getStatus());
        assertEquals(200, lifecycleVersionRequest(MockMvcRequestBuilders.post(
            Constants.MCP_ADMIN_PATH + "/submit")).andReturn().getResponse().getStatus());
        assertEquals(200, lifecycleVersionRequest(MockMvcRequestBuilders.post(
            Constants.MCP_ADMIN_PATH + "/publish")).andReturn().getResponse().getStatus());
        assertEquals(200, lifecycleVersionRequest(MockMvcRequestBuilders.post(
            Constants.MCP_ADMIN_PATH + "/force-publish")).andReturn().getResponse().getStatus());
        assertEquals(200, lifecycleVersionRequest(MockMvcRequestBuilders.post(
            Constants.MCP_ADMIN_PATH + "/redraft")).andReturn().getResponse().getStatus());
        assertEquals(200, lifecycleVersionRequest(MockMvcRequestBuilders.post(
            Constants.MCP_ADMIN_PATH + "/online")).andReturn().getResponse().getStatus());
        assertEquals(200, lifecycleVersionRequest(MockMvcRequestBuilders.post(
            Constants.MCP_ADMIN_PATH + "/offline")).andReturn().getResponse().getStatus());
        assertEquals(200, mockMvc.perform(MockMvcRequestBuilders.put(
            Constants.MCP_ADMIN_PATH + "/labels").param("mcpName", "nacos-mcp-server"))
            .andReturn().getResponse().getStatus());
        
        verify(lifecycleOperationService).deleteLifecycleDraft(
            AiConstants.Mcp.MCP_DEFAULT_NAMESPACE, "nacos-mcp-server", "1.0.0");
        verify(lifecycleOperationService).updateLifecycleLabels(
            AiConstants.Mcp.MCP_DEFAULT_NAMESPACE, "nacos-mcp-server", Map.of());
    }
    
    @Test
    void standardLifecycleApiDoesNotAcceptCompatibilityIdAsIdentity() throws Throwable {
        MockHttpServletRequestBuilder request = MockMvcRequestBuilders.get(
            Constants.MCP_ADMIN_PATH + "/version").param("mcpId", UUID.randomUUID().toString())
            .param("version", "1.0.0");
        
        assertServletException(NacosApiException.class,
            () -> mockMvc.perform(request).andReturn(),
            "ErrCode:400, ErrMsg:Required parameter 'mcpName' type String is not present");
    }
    
    private org.springframework.test.web.servlet.ResultActions lifecycleVersionRequest(
        MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request.param("mcpName", "nacos-mcp-server")
            .param("version", "1.0.0"));
    }
    
    private org.springframework.test.web.servlet.ResultActions lifecycleDraftRequest(
        MockHttpServletRequestBuilder request) throws Exception {
        return lifecycleVersionRequest(request.param("serverSpecification", MCP_SERVER_SPEC));
    }
    
    private static <T extends Throwable> void assertServletException(Class<T> expectedCause,
        Executable executable,
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
