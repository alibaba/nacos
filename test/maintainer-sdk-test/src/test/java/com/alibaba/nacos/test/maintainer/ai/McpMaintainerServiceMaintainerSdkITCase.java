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

package com.alibaba.nacos.test.maintainer.ai;

import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.api.ai.model.mcp.McpEndpointInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpEndpointSpec;
import com.alibaba.nacos.api.ai.model.mcp.McpLifecycleDraftRequest;
import com.alibaba.nacos.api.ai.model.mcp.McpLifecycleLabelsUpdateRequest;
import com.alibaba.nacos.api.ai.model.mcp.McpLifecycleVersionCommand;
import com.alibaba.nacos.api.ai.model.mcp.McpLifecycleVersionDetail;
import com.alibaba.nacos.api.ai.model.mcp.McpLifecycleVersionSummary;
import com.alibaba.nacos.api.ai.model.mcp.McpResourceSpecification;
import com.alibaba.nacos.api.ai.model.mcp.McpServerBasicInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpServerDetailInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpServerRemoteServiceConfig;
import com.alibaba.nacos.api.ai.model.mcp.McpTool;
import com.alibaba.nacos.api.ai.model.mcp.McpToolSpecification;
import com.alibaba.nacos.api.ai.model.mcp.registry.ServerVersionDetail;
import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.maintainer.client.ai.McpMaintainerService;
import com.alibaba.nacos.test.maintainer.MaintainerSdkBaseITCase;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for the typed MCP lifecycle surface of {@link McpMaintainerService}.
 *
 * <p>Scenario coverage:
 * <ul>
 *     <li>Expected capability: default-public and explicit-namespace draft creation, exact and
 *     bounded Version reads, full draft replacement, no-Pipeline submit, force-publish,
 *     offline/online, custom labels, draft deletion, and resource cleanup work through the
 *     Maintainer SDK.</li>
 *     <li>Compatibility: STDIO content and a Direct remote endpoint remain readable through the
 *     historical MCP detail API after lifecycle publication, while lifecycle reads do not expose
 *     the internal MCP ID.</li>
 *     <li>Startup convergence: the test waits through the controlled conflict returned while the
 *     asynchronous historical reconciliation completes managed cutover.</li>
 *     <li>Boundary/error handling: absent Versions and invalid publish state map to controlled
 *     SDK exceptions.</li>
 *     <li>Known standalone limitation: reviewed-state publish/redraft success requires an MCP
 *     review Pipeline plugin, so this class verifies direct publication without a Pipeline and
 *     the controlled illegal-state publish path.</li>
 * </ul>
 *
 * @author xiweng.yy
 */
@SuppressWarnings("deprecation")
class McpMaintainerServiceMaintainerSdkITCase extends MaintainerSdkBaseITCase {
    
    private static final String INITIAL_VERSION = "1.0.0";
    
    private static final String SECOND_VERSION = "1.1.0";
    
    private static final String STATUS_DRAFT = "draft";
    
    private static final String STATUS_ONLINE = "online";
    
    private static final String STATUS_OFFLINE = "offline";
    
    private static final long LIFECYCLE_CUTOVER_TIMEOUT_MILLIS = 180000L;
    
    @Test
    void shouldManageStdioLifecycleInDefaultNamespace() throws Exception {
        McpMaintainerService mcpService = createAiMaintainerService().mcp();
        waitForLifecycleManaged(mcpService);
        String mcpName = randomMaintainerName("mcp-lc-default");
        
        NacosException missing = assertThrows(NacosException.class,
            () -> mcpService.getLifecycleVersion(mcpName, INITIAL_VERSION));
        assertEquals(NacosException.NOT_FOUND, missing.getErrCode());
        
        McpLifecycleDraftRequest request = draftRequest(mcpName, INITIAL_VERSION,
            AiConstants.Mcp.MCP_PROTOCOL_STDIO, "Initial STDIO lifecycle draft");
        request.setToolSpecification(toolSpecification(mcpName));
        request.setResourceSpecification(resourceSpecification(mcpName));
        McpLifecycleVersionDetail created = mcpService.createLifecycleDraft(request);
        addCleanup(() -> mcpService.deleteMcpServer(Constants.DEFAULT_NAMESPACE_ID, mcpName, null,
            null));
        assertLifecycleDetail(created, Constants.DEFAULT_NAMESPACE_ID, mcpName, INITIAL_VERSION,
            STATUS_DRAFT, "Initial STDIO lifecycle draft");
        assertNull(created.getServerSpecification().getId());
        assertNotNull(created.getToolSpecification());
        assertNotNull(created.getResourceSpecification());
        
        request.getServerSpecification().setDescription("Updated STDIO lifecycle draft");
        McpLifecycleVersionDetail updated = mcpService.updateLifecycleDraft(request);
        assertLifecycleDetail(updated, Constants.DEFAULT_NAMESPACE_ID, mcpName, INITIAL_VERSION,
            STATUS_DRAFT, "Updated STDIO lifecycle draft");
        assertContainsVersion(mcpService.listLifecycleVersions(mcpName, STATUS_DRAFT, 1, 10),
            INITIAL_VERSION, STATUS_DRAFT);
        
        McpLifecycleVersionSummary online =
            mcpService.submitLifecycleVersion(versionCommand(mcpName, INITIAL_VERSION));
        assertEquals(STATUS_ONLINE, online.getStatus());
        assertEquals(Boolean.TRUE, online.getLatest());
        
        McpServerDetailInfo compatibilityDetail =
            mcpService.getMcpServerDetail(mcpName, INITIAL_VERSION);
        assertEquals(mcpName, compatibilityDetail.getName());
        assertEquals(AiConstants.Mcp.MCP_PROTOCOL_STDIO, compatibilityDetail.getProtocol());
        assertNotNull(compatibilityDetail.getToolSpec());
        assertNotNull(compatibilityDetail.getResourceSpec());
        
        McpLifecycleLabelsUpdateRequest labelsRequest = new McpLifecycleLabelsUpdateRequest();
        labelsRequest.setMcpName(mcpName);
        labelsRequest.setLabels(Collections.singletonMap("stable", INITIAL_VERSION));
        Map<String, String> labels = mcpService.updateLifecycleLabels(labelsRequest);
        assertEquals(INITIAL_VERSION, labels.get("latest"));
        assertEquals(INITIAL_VERSION, labels.get("stable"));
        
        McpLifecycleVersionSummary offline =
            mcpService.offlineLifecycleVersion(versionCommand(mcpName, INITIAL_VERSION));
        assertEquals(STATUS_OFFLINE, offline.getStatus());
        McpLifecycleVersionSummary onlineAgain =
            mcpService.onlineLifecycleVersion(versionCommand(mcpName, INITIAL_VERSION));
        assertEquals(STATUS_ONLINE, onlineAgain.getStatus());
        
        McpLifecycleDraftRequest secondDraft = draftRequest(mcpName, SECOND_VERSION,
            AiConstants.Mcp.MCP_PROTOCOL_STDIO, "Deletable STDIO lifecycle draft");
        assertEquals(STATUS_DRAFT, mcpService.createLifecycleDraft(secondDraft).getStatus());
        NacosException invalidPublish = assertThrows(NacosException.class,
            () -> mcpService.publishLifecycleVersion(versionCommand(mcpName, SECOND_VERSION)));
        assertEquals(NacosException.INVALID_PARAM, invalidPublish.getErrCode());
        assertFalse(String.valueOf(invalidPublish.getMessage()).isEmpty());
        mcpService.deleteLifecycleDraft(versionCommand(mcpName, SECOND_VERSION));
        NacosException deletedDraft = assertThrows(NacosException.class,
            () -> mcpService.getLifecycleVersion(mcpName, SECOND_VERSION));
        assertEquals(NacosException.NOT_FOUND, deletedDraft.getErrCode());
        
        assertTrue(mcpService.deleteMcpServer(mcpName));
        NacosException deleted = assertThrows(NacosException.class,
            () -> mcpService.getLifecycleVersion(mcpName, INITIAL_VERSION));
        assertEquals(NacosException.NOT_FOUND, deleted.getErrCode());
    }
    
    @Test
    void shouldPublishDirectEndpointInExplicitNamespace() throws Exception {
        McpMaintainerService mcpService = createAiMaintainerService().mcp();
        waitForLifecycleManaged(mcpService);
        String namespaceId = randomMaintainerName("mcp-ns");
        String mcpName = randomMaintainerName("mcp-lc-direct");
        McpLifecycleDraftRequest request = draftRequest(mcpName, INITIAL_VERSION,
            AiConstants.Mcp.MCP_PROTOCOL_STREAMABLE, "Direct lifecycle draft");
        McpServerRemoteServiceConfig remoteConfig = new McpServerRemoteServiceConfig();
        remoteConfig.setExportPath("/mcp");
        request.getServerSpecification().setRemoteServerConfig(remoteConfig);
        request.setEndpointSpecification(directEndpoint());
        
        McpLifecycleVersionDetail created = mcpService.createLifecycleDraft(namespaceId, request);
        addCleanup(() -> mcpService.deleteMcpServer(namespaceId, mcpName, null, null));
        assertLifecycleDetail(created, namespaceId, mcpName, INITIAL_VERSION, STATUS_DRAFT,
            "Direct lifecycle draft");
        
        McpLifecycleVersionSummary online = mcpService.forcePublishLifecycleVersion(namespaceId,
            versionCommand(mcpName, INITIAL_VERSION));
        assertEquals(STATUS_ONLINE, online.getStatus());
        McpServerDetailInfo compatibilityDetail =
            mcpService.getMcpServerDetail(namespaceId, mcpName, null, INITIAL_VERSION);
        assertEquals(AiConstants.Mcp.MCP_PROTOCOL_STREAMABLE, compatibilityDetail.getProtocol());
        assertEquals("/mcp", compatibilityDetail.getRemoteServerConfig().getExportPath());
        assertNotNull(compatibilityDetail.getBackendEndpoints());
        assertEquals(1, compatibilityDetail.getBackendEndpoints().size());
        McpEndpointInfo endpoint = compatibilityDetail.getBackendEndpoints().get(0);
        assertEquals("127.0.0.1", endpoint.getAddress());
        assertEquals(19090, endpoint.getPort());
        assertEquals(AiConstants.Mcp.MCP_PROTOCOL_STREAMABLE, endpoint.getProtocol());
        
        assertTrue(mcpService.deleteMcpServer(namespaceId, mcpName, null, null));
        NacosException deleted = assertThrows(NacosException.class,
            () -> mcpService.getLifecycleVersion(namespaceId, mcpName, INITIAL_VERSION));
        assertEquals(NacosException.NOT_FOUND, deleted.getErrCode());
    }
    
    private McpLifecycleDraftRequest draftRequest(String mcpName, String version,
        String protocol, String description) {
        McpServerBasicInfo server = new McpServerBasicInfo();
        server.setName(mcpName);
        server.setProtocol(protocol);
        server.setDescription(description);
        ServerVersionDetail versionDetail = new ServerVersionDetail();
        versionDetail.setVersion(version);
        server.setVersionDetail(versionDetail);
        McpLifecycleDraftRequest result = new McpLifecycleDraftRequest();
        result.setServerSpecification(server);
        return result;
    }
    
    private void waitForLifecycleManaged(McpMaintainerService mcpService) throws Exception {
        String probeName = randomMaintainerName("mcp-lifecycle-readiness");
        waitUntil("MCP lifecycle management should complete asynchronous cutover",
            LIFECYCLE_CUTOVER_TIMEOUT_MILLIS, () -> {
                try {
                    mcpService.getLifecycleVersion(probeName, INITIAL_VERSION);
                    return true;
                } catch (NacosException exception) {
                    if (NacosException.NOT_FOUND == exception.getErrCode()) {
                        return true;
                    }
                    if (NacosException.CONFLICT == exception.getErrCode()) {
                        return false;
                    }
                    throw exception;
                }
            });
    }
    
    private McpToolSpecification toolSpecification(String mcpName) {
        McpTool tool = new McpTool();
        tool.setName("tool_" + mcpName.replace('-', '_'));
        tool.setDescription("Echo text for Maintainer SDK lifecycle IT");
        tool.setInputSchema(Collections.singletonMap("type", "object"));
        McpToolSpecification result = new McpToolSpecification();
        result.setTools(Collections.singletonList(tool));
        return result;
    }
    
    private McpResourceSpecification resourceSpecification(String mcpName) {
        Map<String, Object> resource = new HashMap<>();
        resource.put("uri", "nacos://" + mcpName + "/resource");
        resource.put("name", "Maintainer SDK lifecycle resource");
        McpResourceSpecification result = new McpResourceSpecification();
        result.setResources(Collections.singletonList(resource));
        return result;
    }
    
    private McpEndpointSpec directEndpoint() {
        McpEndpointSpec result = new McpEndpointSpec();
        result.setType(AiConstants.Mcp.MCP_ENDPOINT_TYPE_DIRECT);
        Map<String, String> data = new HashMap<>();
        data.put("address", "127.0.0.1");
        data.put("port", "19090");
        data.put("transportProtocol", AiConstants.Mcp.MCP_PROTOCOL_STREAMABLE);
        result.setData(data);
        return result;
    }
    
    private McpLifecycleVersionCommand versionCommand(String mcpName, String version) {
        McpLifecycleVersionCommand result = new McpLifecycleVersionCommand();
        result.setMcpName(mcpName);
        result.setVersion(version);
        return result;
    }
    
    private void assertLifecycleDetail(McpLifecycleVersionDetail detail, String namespaceId,
        String mcpName, String version, String status, String description) {
        assertNotNull(detail);
        assertEquals(namespaceId, detail.getNamespaceId());
        assertEquals(mcpName, detail.getMcpName());
        assertEquals(version, detail.getVersion());
        assertEquals(status, detail.getStatus());
        assertNotNull(detail.getServerSpecification());
        assertEquals(mcpName, detail.getServerSpecification().getName());
        assertEquals(description, detail.getServerSpecification().getDescription());
    }
    
    private void assertContainsVersion(Page<McpLifecycleVersionSummary> page, String version,
        String status) {
        assertNotNull(page);
        assertNotNull(page.getPageItems());
        assertTrue(page.getPageItems().stream().anyMatch(
            each -> version.equals(each.getVersion()) && status.equals(each.getStatus())));
    }
}
