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

import com.alibaba.nacos.api.ai.model.mcp.McpServerVersionDetail;
import com.alibaba.nacos.api.ai.model.mcp.McpServerVersionSummary;
import com.alibaba.nacos.api.ai.model.mcp.McpServerBasicInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpServerDetailInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpServerCloneItem;
import com.alibaba.nacos.api.ai.model.mcp.McpServerImportResponse;
import com.alibaba.nacos.api.ai.model.mcp.McpServerImportValidationResult;
import com.alibaba.nacos.api.ai.model.mcp.McpEndpointInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpEndpointSpec;
import com.alibaba.nacos.api.ai.model.mcp.McpServerRemoteServiceConfig;
import com.alibaba.nacos.api.ai.model.mcp.McpServiceRef;
import com.alibaba.nacos.api.config.model.SameConfigPolicy;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.console.config.McpEndpointAccessValidator;
import com.alibaba.nacos.console.proxy.ai.McpProxy;
import com.alibaba.nacos.core.controller.compatibility.CompatibilityHelper;
import com.alibaba.nacos.core.exception.NacosApiExceptionHandler;
import com.alibaba.nacos.sys.env.EnvUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Map;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConsoleMcpControllerTest {
    
    @Mock
    private McpProxy mcpProxy;
    
    private MockMvc mockMvc;
    
    private MockEnvironment environment;
    
    @BeforeEach
    void setUp() {
        environment = new MockEnvironment();
        EnvUtil.setEnvironment(environment);
        mockMvc = MockMvcBuilders.standaloneSetup(
            new ConsoleMcpController(mcpProxy, new McpEndpointAccessValidator()))
            .setControllerAdvice(new NacosApiExceptionHandler()).build();
    }
    
    @AfterEach
    void tearDown() {
        EnvUtil.setEnvironment(null);
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
    void testExportMcpServers() throws Exception {
        McpServerDetailInfo first = new McpServerDetailInfo();
        first.setName("first");
        first.setVersion("1.0.0");
        McpServerDetailInfo second = new McpServerDetailInfo();
        second.setName("second");
        second.setVersion("2.0.0");
        when(mcpProxy.getMcpServer("nacos-default-mcp", "first", null, null)).thenReturn(first);
        when(mcpProxy.getMcpServer("nacos-default-mcp", "second", null, null)).thenReturn(second);

        MockHttpServletResponse response = mockMvc.perform(
            MockMvcRequestBuilders.get("/v3/console/ai/mcp/export")
                .param("namespaceId", "nacos-default-mcp")
                .param("mcpNames", "first,second"))
            .andReturn().getResponse();

        assertEquals(HttpStatus.OK.value(), response.getStatus());
        assertTrue(response.getContentType().contains("application/json"));
        List<McpServerDetailInfo> result = JacksonUtils.toObj(response.getContentAsString(),
            new TypeReference<>() {
            });
        assertEquals(2, result.size());
        assertEquals("first", result.get(0).getName());
        assertTrue(response.getHeader("Content-Disposition").contains("mcp-servers.json"));
    }

    @Test
    void testCloneMcpServersWithDefaultNamesAndPublish() throws Exception {
        McpServerDetailInfo source = new McpServerDetailInfo();
        source.setName("source");
        source.setProtocol("stdio");
        source.setVersion("1.0.0");
        McpServerVersionDetail sourceVersion = new McpServerVersionDetail();
        sourceVersion.setLabels(Map.of("stable", "1.0.0"));
        McpServerVersionDetail created = new McpServerVersionDetail();
        created.setVersion("1.0.0");
        when(mcpProxy.getMcpServer("source-ns", "source-copy", null, null)).thenReturn(null);
        when(mcpProxy.getMcpServer("source-ns", "source", null, null)).thenReturn(source);
        when(mcpProxy.getMcpServerVersion("source-ns", "source", "1.0.0"))
            .thenReturn(sourceVersion);
        when(mcpProxy.createMcpServerDraft(eq("source-ns"), any(McpServerBasicInfo.class),
            isNull(), isNull(), isNull())).thenReturn(created);

        McpServerCloneItem item = new McpServerCloneItem();
        item.setSourceName("source");
        item.setTargetName("source-copy");
        MockHttpServletResponse response = mockMvc.perform(
            MockMvcRequestBuilders.post("/v3/console/ai/mcp/clone")
                .param("namespaceId", "source-ns")
                .contentType("application/json")
                .content(JacksonUtils.toJson(List.of(item))))
            .andReturn().getResponse();

        Result<Map<String, Object>> result = JacksonUtils.toObj(response.getContentAsString(),
            new TypeReference<>() {
            });
        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertEquals(1, result.getData().get("successCount"));
        verify(mcpProxy).forcePublishMcpServerVersion("source-ns", "source-copy", "1.0.0");
        verify(mcpProxy).updateMcpServerLabels("source-ns", "source-copy",
            Map.of("stable", "1.0.0"));
    }

    @Test
    void testCloneMcpServersSkipsExistingTarget() throws Exception {
        McpServerDetailInfo existing = new McpServerDetailInfo();
        when(mcpProxy.getMcpServer("source-ns", "source-copy", null, null)).thenReturn(existing);
        McpServerCloneItem item = new McpServerCloneItem();
        item.setSourceName("source");
        item.setTargetName("source-copy");

        MockHttpServletResponse response = mockMvc.perform(
            MockMvcRequestBuilders.post("/v3/console/ai/mcp/clone")
                .param("namespaceId", "source-ns")
                .param("policy", "SKIP")
                .contentType("application/json")
                .content(JacksonUtils.toJson(List.of(item))))
            .andReturn().getResponse();

        Result<Map<String, Object>> result = JacksonUtils.toObj(response.getContentAsString(),
            new TypeReference<>() {
            });
        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertEquals(1, result.getData().get("skippedCount"));
        verifyNoInteractionsExceptTargetLookup();
    }

    @Test
    void testCloneMcpServerRebuildsDirectEndpoint() throws Exception {
        McpServerDetailInfo source = sourceWithEndpoint("mcp-streamable", "source::1.0.0");
        source.getRemoteServerConfig().getServiceRef().setGroupName("mcp-endpoints");
        McpEndpointInfo endpoint = new McpEndpointInfo();
        endpoint.setAddress("127.0.0.1");
        endpoint.setPort(8080);
        endpoint.setProtocol("http");
        source.setBackendEndpoints(List.of(endpoint));
        McpServerVersionDetail created = new McpServerVersionDetail();
        created.setVersion("1.0.0");
        when(mcpProxy.getMcpServer("source-ns", "source-copy", null, null)).thenReturn(null);
        when(mcpProxy.getMcpServer("source-ns", "source", null, null)).thenReturn(source);
        when(mcpProxy.getMcpServerVersion("source-ns", "source", "1.0.0"))
            .thenReturn(new McpServerVersionDetail());
        ArgumentCaptor<McpEndpointSpec> endpointCaptor = ArgumentCaptor.forClass(
            McpEndpointSpec.class);
        when(mcpProxy.createMcpServerDraft(eq("source-ns"), any(McpServerBasicInfo.class),
            isNull(), isNull(), endpointCaptor.capture())).thenReturn(created);

        MockHttpServletResponse response = mockMvc.perform(
            MockMvcRequestBuilders.post("/v3/console/ai/mcp/clone")
                .param("namespaceId", "source-ns")
                .contentType("application/json")
                .content(JacksonUtils.toJson(List.of(cloneItem("source", "source-copy")))))
            .andReturn().getResponse();

        Result<Map<String, Object>> result = JacksonUtils.toObj(response.getContentAsString(),
            new TypeReference<>() {
            });
        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertEquals("DIRECT", endpointCaptor.getValue().getType());
        assertEquals("127.0.0.1", endpointCaptor.getValue().getData().get("address"));
        assertEquals("8080", endpointCaptor.getValue().getData().get("port"));
        assertEquals("http", endpointCaptor.getValue().getData().get("transportProtocol"));
    }

    @Test
    void testCloneMcpServerRebuildsRefEndpoint() throws Exception {
        McpServerDetailInfo source = sourceWithEndpoint("mcp-streamable", "backend-service");
        McpServiceRef serviceRef = source.getRemoteServerConfig().getServiceRef();
        serviceRef.setNamespaceId("backend-ns");
        serviceRef.setGroupName("backend-group");
        serviceRef.setTransportProtocol("https");
        McpServerVersionDetail created = new McpServerVersionDetail();
        created.setVersion("1.0.0");
        when(mcpProxy.getMcpServer("source-ns", "source-copy", null, null)).thenReturn(null);
        when(mcpProxy.getMcpServer("source-ns", "source", null, null)).thenReturn(source);
        when(mcpProxy.getMcpServerVersion("source-ns", "source", "1.0.0"))
            .thenReturn(new McpServerVersionDetail());
        ArgumentCaptor<McpEndpointSpec> endpointCaptor = ArgumentCaptor.forClass(
            McpEndpointSpec.class);
        when(mcpProxy.createMcpServerDraft(eq("source-ns"), any(McpServerBasicInfo.class),
            isNull(), isNull(), endpointCaptor.capture())).thenReturn(created);

        MockHttpServletResponse response = mockMvc.perform(
            MockMvcRequestBuilders.post("/v3/console/ai/mcp/clone")
                .param("namespaceId", "source-ns")
                .contentType("application/json")
                .content(JacksonUtils.toJson(List.of(cloneItem("source", "source-copy")))))
            .andReturn().getResponse();

        Result<Map<String, Object>> result = JacksonUtils.toObj(response.getContentAsString(),
            new TypeReference<>() {
            });
        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertEquals("REF", endpointCaptor.getValue().getType());
        assertEquals("backend-ns", endpointCaptor.getValue().getData().get("namespaceId"));
        assertEquals("backend-group", endpointCaptor.getValue().getData().get("groupName"));
        assertEquals("backend-service", endpointCaptor.getValue().getData().get("serviceName"));
        assertEquals("https", endpointCaptor.getValue().getData().get("transportProtocol"));
    }

    @Test
    void testCloneMcpServerDoesNotMisclassifyRefEndpointWithVersionLikeName() throws Exception {
        McpServerDetailInfo source = sourceWithEndpoint("mcp-streamable", "source::1.0.0");
        source.getRemoteServerConfig().getServiceRef().setGroupName("backend-group");
        McpServerVersionDetail created = new McpServerVersionDetail();
        created.setVersion("1.0.0");
        when(mcpProxy.getMcpServer("source-ns", "source-copy", null, null)).thenReturn(null);
        when(mcpProxy.getMcpServer("source-ns", "source", null, null)).thenReturn(source);
        when(mcpProxy.getMcpServerVersion("source-ns", "source", "1.0.0"))
            .thenReturn(new McpServerVersionDetail());
        ArgumentCaptor<McpEndpointSpec> endpointCaptor = ArgumentCaptor.forClass(
            McpEndpointSpec.class);
        when(mcpProxy.createMcpServerDraft(eq("source-ns"), any(McpServerBasicInfo.class),
            isNull(), isNull(), endpointCaptor.capture())).thenReturn(created);

        MockHttpServletResponse response = mockMvc.perform(
            MockMvcRequestBuilders.post("/v3/console/ai/mcp/clone")
                .param("namespaceId", "source-ns")
                .contentType("application/json")
                .content(JacksonUtils.toJson(List.of(cloneItem("source", "source-copy")))))
            .andReturn().getResponse();

        Result<Map<String, Object>> result = JacksonUtils.toObj(response.getContentAsString(),
            new TypeReference<>() {
            });
        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertEquals("REF", endpointCaptor.getValue().getType());
    }

    @Test
    void testCloneMcpServerReadsSourceBeforeOverwrite() throws Exception {
        McpServerDetailInfo source = sourceWithEndpoint("stdio", null);
        McpServerVersionDetail created = new McpServerVersionDetail();
        created.setVersion("1.0.0");
        when(mcpProxy.getMcpServer("source-ns", "source", null, null))
            .thenReturn(source, source);
        when(mcpProxy.getMcpServerVersion("source-ns", "source", "1.0.0"))
            .thenReturn(new McpServerVersionDetail());
        when(mcpProxy.createMcpServerDraft(eq("source-ns"), any(McpServerBasicInfo.class),
            isNull(), isNull(), isNull())).thenReturn(created);

        MockHttpServletResponse response = mockMvc.perform(
            MockMvcRequestBuilders.post("/v3/console/ai/mcp/clone")
                .param("namespaceId", "source-ns")
                .param("policy", SameConfigPolicy.OVERWRITE.name())
                .contentType("application/json")
                .content(JacksonUtils.toJson(List.of(cloneItem("source", null)))))
            .andReturn().getResponse();

        Result<Map<String, Object>> result = JacksonUtils.toObj(response.getContentAsString(),
            new TypeReference<>() {
            });
        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        InOrder order = inOrder(mcpProxy);
        order.verify(mcpProxy, times(2)).getMcpServer("source-ns", "source", null, null);
        order.verify(mcpProxy).getMcpServerVersion("source-ns", "source", "1.0.0");
        order.verify(mcpProxy).deleteMcpServer("source-ns", "source", null, null);
    }

    private McpServerCloneItem cloneItem(String sourceName, String targetName) {
        McpServerCloneItem result = new McpServerCloneItem();
        result.setSourceName(sourceName);
        result.setTargetName(targetName);
        return result;
    }

    private McpServerDetailInfo sourceWithEndpoint(String protocol, String serviceName) {
        McpServerDetailInfo result = new McpServerDetailInfo();
        result.setName("source");
        result.setProtocol(protocol);
        result.setVersion("1.0.0");
        if (serviceName != null) {
            McpServerRemoteServiceConfig remote = new McpServerRemoteServiceConfig();
            McpServiceRef serviceRef = new McpServiceRef();
            serviceRef.setNamespaceId("source-ns");
            serviceRef.setGroupName("DEFAULT_GROUP");
            serviceRef.setServiceName(serviceName);
            remote.setServiceRef(serviceRef);
            result.setRemoteServerConfig(remote);
        }
        return result;
    }

    private void verifyNoInteractionsExceptTargetLookup() throws Exception {
        verify(mcpProxy).getMcpServer("source-ns", "source-copy", null, null);
    }
    
    @Test
    void testStandardLifecycleApisDelegateByNameAndExactVersion() throws Exception {
        McpServerVersionDetail detail = new McpServerVersionDetail();
        McpServerVersionSummary summary = new McpServerVersionSummary();
        when(mcpProxy.listMcpServerVersions("nacos-default-mcp", "test", "draft", 1, 10))
            .thenReturn(new Page<>());
        when(mcpProxy.getMcpServerVersion("nacos-default-mcp", "test", "1.0.0"))
            .thenReturn(detail);
        when(mcpProxy.createMcpServerDraft(eq("nacos-default-mcp"),
            any(McpServerBasicInfo.class), isNull(), isNull(), isNull())).thenReturn(detail);
        when(mcpProxy.updateMcpServerDraft(eq("nacos-default-mcp"),
            any(McpServerBasicInfo.class), isNull(), isNull(), isNull())).thenReturn(detail);
        when(mcpProxy.submitMcpServerVersion("nacos-default-mcp", "test", "1.0.0"))
            .thenReturn(summary);
        when(mcpProxy.publishMcpServerVersion("nacos-default-mcp", "test", "1.0.0"))
            .thenReturn(summary);
        when(mcpProxy.forcePublishMcpServerVersion("nacos-default-mcp", "test", "1.0.0"))
            .thenReturn(summary);
        when(mcpProxy.redraftMcpServerVersion("nacos-default-mcp", "test", "1.0.0"))
            .thenReturn(summary);
        when(mcpProxy.onlineMcpServerVersion("nacos-default-mcp", "test", "1.0.0"))
            .thenReturn(summary);
        when(mcpProxy.offlineMcpServerVersion("nacos-default-mcp", "test", "1.0.0"))
            .thenReturn(summary);
        when(mcpProxy.updateMcpServerLabels("nacos-default-mcp", "test", Map.of()))
            .thenReturn(Map.of());
        
        assertEquals(200, mockMvc.perform(MockMvcRequestBuilders.get(
            "/v3/console/ai/mcp/versions").param("namespaceId", "nacos-default-mcp")
            .param("mcpName", "test").param("status", "draft").param("pageNo", "1")
            .param("pageSize", "10")).andReturn().getResponse().getStatus());
        assertEquals(200, lifecycleVersionRequest(MockMvcRequestBuilders.get(
            "/v3/console/ai/mcp/version")).andReturn().getResponse().getStatus());
        assertEquals(200, lifecycleDraftRequest(MockMvcRequestBuilders.post(
            "/v3/console/ai/mcp/draft")).andReturn().getResponse().getStatus());
        assertEquals(200, lifecycleDraftRequest(MockMvcRequestBuilders.put(
            "/v3/console/ai/mcp/draft")).andReturn().getResponse().getStatus());
        assertEquals(200, lifecycleVersionRequest(MockMvcRequestBuilders.delete(
            "/v3/console/ai/mcp/draft")).andReturn().getResponse().getStatus());
        assertEquals(200, lifecycleVersionRequest(MockMvcRequestBuilders.post(
            "/v3/console/ai/mcp/submit")).andReturn().getResponse().getStatus());
        assertEquals(200, lifecycleVersionRequest(MockMvcRequestBuilders.post(
            "/v3/console/ai/mcp/publish")).andReturn().getResponse().getStatus());
        assertEquals(200, lifecycleVersionRequest(MockMvcRequestBuilders.post(
            "/v3/console/ai/mcp/force-publish")).andReturn().getResponse().getStatus());
        assertEquals(200, lifecycleVersionRequest(MockMvcRequestBuilders.post(
            "/v3/console/ai/mcp/redraft")).andReturn().getResponse().getStatus());
        assertEquals(200, lifecycleVersionRequest(MockMvcRequestBuilders.post(
            "/v3/console/ai/mcp/online")).andReturn().getResponse().getStatus());
        assertEquals(200, lifecycleVersionRequest(MockMvcRequestBuilders.post(
            "/v3/console/ai/mcp/offline")).andReturn().getResponse().getStatus());
        assertEquals(200, mockMvc.perform(MockMvcRequestBuilders.put(
            "/v3/console/ai/mcp/labels").param("namespaceId", "nacos-default-mcp")
            .param("mcpName", "test")).andReturn().getResponse().getStatus());
        
        verify(mcpProxy).deleteMcpServerDraft("nacos-default-mcp", "test", "1.0.0");
        verify(mcpProxy).updateMcpServerLabels("nacos-default-mcp", "test", Map.of());
    }
    
    @Test
    void testImportToolsDisabledByOperatorSwitch() throws Exception {
        environment.setProperty(McpEndpointAccessValidator.IMPORT_ENABLED_PROPERTY, "false");
        MockHttpServletResponse response = mockMvc.perform(
            MockMvcRequestBuilders.get("/v3/console/ai/mcp/importToolsFromMcp")
                .param("transportType", "mcp-streamable")
                .param("baseUrl", "http://127.0.0.1:8080")
                .param("endpoint", "/mcp"))
            .andReturn().getResponse();
        
        Result<Object> result = JacksonUtils.toObj(response.getContentAsString(),
            new TypeReference<>() {
            });
        assertEquals(HttpStatus.OK.value(), response.getStatus());
        assertEquals(ErrorCode.ACCESS_DENIED.getCode(), result.getCode());
        assertTrue(result.getMessage().contains("disabled"));
        assertTrue(result.getMessage().contains(
            McpEndpointAccessValidator.IMPORT_ENABLED_PROPERTY));
    }
    
    @Test
    void testImportToolsRejectsPrivateAddressByDefault() throws Exception {
        MockHttpServletResponse response = mockMvc.perform(
            MockMvcRequestBuilders.get("/v3/console/ai/mcp/importToolsFromMcp")
                .param("transportType", "mcp-streamable")
                .param("baseUrl", "http://127.0.0.1:8080")
                .param("endpoint", "/mcp"))
            .andReturn().getResponse();
        
        Result<Object> result = JacksonUtils.toObj(response.getContentAsString(),
            new TypeReference<>() {
            });
        assertEquals(HttpStatus.OK.value(), response.getStatus());
        assertEquals(ErrorCode.ACCESS_DENIED.getCode(), result.getCode());
        assertTrue(result.getMessage().contains("127.0.0.1"));
        assertTrue(result.getMessage().contains("private or local"));
        assertTrue(result.getMessage().contains(
            McpEndpointAccessValidator.ALLOWED_PRIVATE_ADDRESSES_PROPERTY));
    }
    
    @Test
    void testImportToolsRejectsEndpointAuthorityOverride() throws Exception {
        MockHttpServletResponse response = mockMvc.perform(
            MockMvcRequestBuilders.get("/v3/console/ai/mcp/importToolsFromMcp")
                .param("transportType", "mcp-sse")
                .param("baseUrl", "http://127.0.0.1:8080")
                .param("endpoint", "//192.0.2.1/mcp"))
            .andReturn().getResponse();
        
        Result<Object> result = JacksonUtils.toObj(response.getContentAsString(),
            new TypeReference<>() {
            });
        assertEquals(HttpStatus.OK.value(), response.getStatus());
        assertEquals(ErrorCode.PARAMETER_VALIDATE_ERROR.getCode(), result.getCode());
        assertTrue(result.getMessage().contains("must not override"));
    }
    
    @Test
    void testImportToolsUnsupportedTransportIsRejectedBeforeEndpointValidation() throws Exception {
        MockHttpServletResponse response = mockMvc.perform(
            MockMvcRequestBuilders.get("/v3/console/ai/mcp/importToolsFromMcp")
                .param("transportType", "stdio")
                .param("baseUrl", "http://127.0.0.1:8080")
                .param("endpoint", "/mcp"))
            .andReturn().getResponse();
        
        Result<Object> result = JacksonUtils.toObj(response.getContentAsString(),
            new TypeReference<>() {
            });
        assertEquals(HttpStatus.OK.value(), response.getStatus());
        assertEquals(ErrorCode.SERVER_ERROR.getCode(), result.getCode());
        assertTrue(result.getMessage().contains("Unsupported transport type"));
    }
    
    @Test
    void testValidateImportDisabledByDefault() throws Exception {
        MockHttpServletResponse response = mockMvc.perform(
            MockMvcRequestBuilders.post("/v3/console/ai/mcp/import/validate")
                .param("namespaceId", "nacos-default-mcp")
                .param("importType", "json")
                .param("data", "[{\"name\":\"test-server\"}]"))
            .andReturn().getResponse();
        
        assertDeprecated(response, "POST /v3/console/ai/import/validate");
        verifyNoInteractions(mcpProxy);
    }
    
    @Test
    void testValidateImportWhenCompatibilityEnabled() throws Exception {
        environment.setProperty(CompatibilityHelper.API_COMPATIBILITY_ENABLED_KEY, "true");
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
    void testExecuteImportDisabledByDefault() throws Exception {
        MockHttpServletResponse response = mockMvc.perform(
            MockMvcRequestBuilders.post("/v3/console/ai/mcp/import/execute")
                .param("namespaceId", "nacos-default-mcp")
                .param("importType", "json")
                .param("data", "[{\"name\":\"test-server\"}]"))
            .andReturn().getResponse();
        
        assertDeprecated(response, "POST /v3/console/ai/import/execute");
        verifyNoInteractions(mcpProxy);
    }
    
    @Test
    void testExecuteImportWhenCompatibilityEnabled() throws Exception {
        environment.setProperty(CompatibilityHelper.API_COMPATIBILITY_ENABLED_KEY, "true");
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
    
    private void assertDeprecated(MockHttpServletResponse response, String alternative)
        throws Exception {
        assertEquals(HttpStatus.GONE.value(), response.getStatus());
        Result<String> result = JacksonUtils.toObj(response.getContentAsString(),
            new TypeReference<>() {
            });
        assertEquals(ErrorCode.API_DEPRECATED.getCode(), result.getCode());
        assertTrue(result.getData().contains(alternative));
    }
    
    private org.springframework.test.web.servlet.ResultActions lifecycleVersionRequest(
        MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request.param("namespaceId", "nacos-default-mcp")
            .param("mcpName", "test").param("version", "1.0.0"));
    }
    
    private org.springframework.test.web.servlet.ResultActions lifecycleDraftRequest(
        MockHttpServletRequestBuilder request) throws Exception {
        return lifecycleVersionRequest(request.param("serverSpecification",
            "{\"protocol\":\"stdio\",\"name\":\"test\","
                + "\"versionDetail\":{\"version\":\"1.0.0\"},\"enabled\":true}"));
    }
}
