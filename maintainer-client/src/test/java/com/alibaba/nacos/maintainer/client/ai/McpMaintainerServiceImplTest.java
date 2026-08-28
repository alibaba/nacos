/*
 * Copyright 1999-2026 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.maintainer.client.ai;

import com.alibaba.nacos.api.ai.model.mcp.McpEndpointSpec;
import com.alibaba.nacos.api.ai.model.mcp.McpLifecycleDraftRequest;
import com.alibaba.nacos.api.ai.model.mcp.McpLifecycleLabelsUpdateRequest;
import com.alibaba.nacos.api.ai.model.mcp.McpLifecycleVersionCommand;
import com.alibaba.nacos.api.ai.model.mcp.McpLifecycleVersionDetail;
import com.alibaba.nacos.api.ai.model.mcp.McpLifecycleVersionSummary;
import com.alibaba.nacos.api.ai.model.mcp.McpResourceSpecification;
import com.alibaba.nacos.api.ai.model.mcp.McpServerBasicInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpToolSpecification;
import com.alibaba.nacos.api.ai.model.mcp.registry.ServerVersionDetail;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.api.utils.json.JsonUtils;
import com.alibaba.nacos.common.http.HttpRestResult;
import com.alibaba.nacos.common.utils.HttpMethod;
import com.alibaba.nacos.maintainer.client.constants.Constants;
import com.alibaba.nacos.maintainer.client.model.HttpRequest;
import com.alibaba.nacos.maintainer.client.remote.ClientHttpProxy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link McpMaintainerServiceImpl} lifecycle transport.
 *
 * @author Nacos
 */
@ExtendWith(MockitoExtension.class)
class McpMaintainerServiceImplTest {
    
    private static final String NAMESPACE_ID = "test-namespace";
    
    private static final String MCP_NAME = "test-mcp";
    
    private static final String VERSION = "1.0.0";
    
    @Mock
    private ClientHttpProxy clientHttpProxy;
    
    private McpMaintainerService service;
    
    @BeforeEach
    void setUp() {
        service = new McpMaintainerServiceImpl(new AiMaintainerHttpContext(clientHttpProxy));
    }
    
    @Test
    void testLifecycleReadsUseFormQueryParameters() throws NacosException {
        Page<McpLifecycleVersionSummary> page = new Page<>();
        page.setPageItems(Collections.singletonList(summary()));
        when(clientHttpProxy.executeSyncHttpRequest(any(HttpRequest.class)))
            .thenReturn(response(page), response(detail()));
        
        Page<McpLifecycleVersionSummary> actual = service.listLifecycleVersions(NAMESPACE_ID,
            MCP_NAME, "draft", 2, 20);
        McpLifecycleVersionDetail detail =
            service.getLifecycleVersion(NAMESPACE_ID, MCP_NAME, VERSION);
        
        assertEquals(VERSION, actual.getPageItems().get(0).getVersion());
        assertEquals(MCP_NAME, detail.getMcpName());
        List<HttpRequest> requests = captureRequests(2);
        assertRequest(requests.get(0), HttpMethod.GET, rootPath() + "/versions");
        assertEquals("draft", requests.get(0).getParamValues().get("status"));
        assertEquals("2", requests.get(0).getParamValues().get("pageNo"));
        assertRequest(requests.get(1), HttpMethod.GET, rootPath() + "/version");
        assertEquals(VERSION, requests.get(1).getParamValues().get("version"));
    }
    
    @Test
    void testDraftTransportSerializesAllOptionalContent() throws NacosException {
        when(clientHttpProxy.executeSyncHttpRequest(any(HttpRequest.class)))
            .thenReturn(response(detail()), response(detail()), response(null));
        McpLifecycleDraftRequest request = draftRequest();
        
        service.createLifecycleDraft(NAMESPACE_ID, request);
        service.updateLifecycleDraft(NAMESPACE_ID, request);
        service.deleteLifecycleDraft(NAMESPACE_ID, versionCommand());
        
        List<HttpRequest> requests = captureRequests(3);
        assertRequest(requests.get(0), HttpMethod.POST, rootPath() + "/draft");
        assertEquals(VERSION, requests.get(0).getParamValues().get("version"));
        assertEquals(MCP_NAME, JsonUtils.toObj(
            requests.get(0).getParamValues().get("serverSpecification"),
            McpServerBasicInfo.class).getName());
        assertEquals(McpToolSpecification.class, JsonUtils.toObj(
            requests.get(0).getParamValues().get("toolSpecification"),
            McpToolSpecification.class).getClass());
        assertEquals(McpResourceSpecification.class, JsonUtils.toObj(
            requests.get(0).getParamValues().get("resourceSpecification"),
            McpResourceSpecification.class).getClass());
        assertEquals(McpEndpointSpec.class, JsonUtils.toObj(
            requests.get(0).getParamValues().get("endpointSpecification"),
            McpEndpointSpec.class).getClass());
        assertRequest(requests.get(1), HttpMethod.PUT, rootPath() + "/draft");
        assertEquals(VERSION, requests.get(1).getParamValues().get("version"));
        assertRequest(requests.get(2), HttpMethod.DELETE, rootPath() + "/draft");
    }
    
    @Test
    void testDraftTransportFallsBackToLegacyVersionField() throws NacosException {
        when(clientHttpProxy.executeSyncHttpRequest(any(HttpRequest.class)))
            .thenReturn(response(detail()));
        McpLifecycleDraftRequest request = draftRequest();
        request.getServerSpecification().setVersionDetail(null);
        request.getServerSpecification().setVersion(VERSION);
        
        service.createLifecycleDraft(NAMESPACE_ID, request);
        
        HttpRequest actual = captureRequests(1).get(0);
        assertEquals(VERSION, actual.getParamValues().get("version"));
    }
    
    @Test
    void testLifecycleCommandsUsePostFormRequests() throws NacosException {
        when(clientHttpProxy.executeSyncHttpRequest(any(HttpRequest.class)))
            .thenReturn(response(summary()), response(summary()), response(summary()),
                response(summary()), response(summary()), response(summary()));
        McpLifecycleVersionCommand command = versionCommand();
        
        service.submitLifecycleVersion(NAMESPACE_ID, command);
        service.publishLifecycleVersion(NAMESPACE_ID, command);
        service.forcePublishLifecycleVersion(NAMESPACE_ID, command);
        service.redraftLifecycleVersion(NAMESPACE_ID, command);
        service.onlineLifecycleVersion(NAMESPACE_ID, command);
        service.offlineLifecycleVersion(NAMESPACE_ID, command);
        
        List<HttpRequest> requests = captureRequests(6);
        List<String> paths = Arrays.asList("/submit", "/publish", "/force-publish", "/redraft",
            "/online", "/offline");
        for (int i = 0; i < paths.size(); i++) {
            assertRequest(requests.get(i), HttpMethod.POST, rootPath() + paths.get(i));
            assertEquals(MCP_NAME, requests.get(i).getParamValues().get("mcpName"));
            assertEquals(VERSION, requests.get(i).getParamValues().get("version"));
        }
    }
    
    @Test
    void testLabelsAndDefaultNamespaceOverloads() throws NacosException {
        Map<String, String> labels = Collections.singletonMap("stable", VERSION);
        when(clientHttpProxy.executeSyncHttpRequest(any(HttpRequest.class)))
            .thenReturn(response(labels), response(summary()), response(new Page<>()));
        McpLifecycleLabelsUpdateRequest request = new McpLifecycleLabelsUpdateRequest();
        request.setMcpName(MCP_NAME);
        request.setLabels(labels);
        
        assertEquals(labels, service.updateLifecycleLabels(request));
        service.submitLifecycleVersion(versionCommand());
        service.listLifecycleVersions(MCP_NAME, null, 1, 100);
        
        List<HttpRequest> requests = captureRequests(3);
        for (HttpRequest httpRequest : requests) {
            assertEquals(com.alibaba.nacos.api.common.Constants.DEFAULT_NAMESPACE_ID,
                httpRequest.getParamValues().get("namespaceId"));
        }
        assertEquals(VERSION, JsonUtils.toObj(
            requests.get(0).getParamValues().get("labels"), Map.class).get("stable"));
        assertFalse(requests.get(2).getParamValues().containsKey("status"));
    }
    
    @Test
    void testAllDefaultNamespaceOverloads() throws NacosException {
        Page<McpLifecycleVersionSummary> page = new Page<>();
        page.setPageItems(Collections.singletonList(summary()));
        Map<String, String> labels = Collections.singletonMap("stable", VERSION);
        when(clientHttpProxy.executeSyncHttpRequest(any(HttpRequest.class))).thenReturn(
            response(page), response(detail()), response(detail()), response(detail()),
            response(null), response(summary()), response(summary()), response(summary()),
            response(summary()), response(summary()), response(summary()), response(labels));
        McpLifecycleLabelsUpdateRequest labelsRequest = new McpLifecycleLabelsUpdateRequest();
        labelsRequest.setMcpName(MCP_NAME);
        labelsRequest.setLabels(labels);
        
        service.listLifecycleVersions(MCP_NAME, "draft", 1, 10);
        service.getLifecycleVersion(MCP_NAME, VERSION);
        service.createLifecycleDraft(draftRequest());
        service.updateLifecycleDraft(draftRequest());
        service.deleteLifecycleDraft(versionCommand());
        service.submitLifecycleVersion(versionCommand());
        service.publishLifecycleVersion(versionCommand());
        service.forcePublishLifecycleVersion(versionCommand());
        service.redraftLifecycleVersion(versionCommand());
        service.onlineLifecycleVersion(versionCommand());
        service.offlineLifecycleVersion(versionCommand());
        assertEquals(labels, service.updateLifecycleLabels(labelsRequest));
        
        for (HttpRequest request : captureRequests(12)) {
            assertEquals(com.alibaba.nacos.api.common.Constants.DEFAULT_NAMESPACE_ID,
                request.getParamValues().get("namespaceId"));
        }
    }
    
    @Test
    void testInvalidDraftRequestsAreRejectedLocally() {
        assertThrows(IllegalArgumentException.class,
            () -> service.createLifecycleDraft(NAMESPACE_ID, null));
        McpLifecycleDraftRequest request = new McpLifecycleDraftRequest();
        assertThrows(IllegalArgumentException.class,
            () -> service.updateLifecycleDraft(NAMESPACE_ID, request));
        assertThrows(IllegalArgumentException.class,
            () -> service.submitLifecycleVersion(NAMESPACE_ID, null));
    }
    
    private List<HttpRequest> captureRequests(int count) throws NacosException {
        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(clientHttpProxy, times(count)).executeSyncHttpRequest(captor.capture());
        return captor.getAllValues();
    }
    
    private void assertRequest(HttpRequest request, String method, String path) {
        assertEquals(method, request.getHttpMethod());
        assertEquals(path, request.getPath());
        assertNull(request.getBody());
    }
    
    private String rootPath() {
        return Constants.AdminApiPath.AI_MCP_ADMIN_PATH;
    }
    
    private McpLifecycleDraftRequest draftRequest() {
        McpLifecycleDraftRequest result = new McpLifecycleDraftRequest();
        McpServerBasicInfo server = new McpServerBasicInfo();
        server.setName(MCP_NAME);
        ServerVersionDetail versionDetail = new ServerVersionDetail();
        versionDetail.setVersion(VERSION);
        server.setVersionDetail(versionDetail);
        result.setServerSpecification(server);
        result.setToolSpecification(new McpToolSpecification());
        result.setResourceSpecification(new McpResourceSpecification());
        result.setEndpointSpecification(new McpEndpointSpec());
        return result;
    }
    
    private McpLifecycleVersionCommand versionCommand() {
        McpLifecycleVersionCommand result = new McpLifecycleVersionCommand();
        result.setMcpName(MCP_NAME);
        result.setVersion(VERSION);
        return result;
    }
    
    private McpLifecycleVersionDetail detail() {
        McpLifecycleVersionDetail result = new McpLifecycleVersionDetail();
        result.setMcpName(MCP_NAME);
        result.setVersion(VERSION);
        return result;
    }
    
    private McpLifecycleVersionSummary summary() {
        McpLifecycleVersionSummary result = new McpLifecycleVersionSummary();
        result.setVersion(VERSION);
        return result;
    }
    
    private HttpRestResult<String> response(Object data) {
        HttpRestResult<String> result = new HttpRestResult<>();
        result.setData(JsonUtils.toJson(Result.success(data)));
        return result;
    }
}
