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

package com.alibaba.nacos.ai.service.mcp;

import com.alibaba.nacos.api.ai.model.mcp.McpEndpointSpec;
import com.alibaba.nacos.api.ai.model.mcp.McpServerVersionDetail;
import com.alibaba.nacos.api.ai.model.mcp.McpServerVersionSummary;
import com.alibaba.nacos.api.ai.model.mcp.McpResourceSpecification;
import com.alibaba.nacos.api.ai.model.mcp.McpServerBasicInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpServerDetailInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpToolSpecification;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Routes each complete historical MCP operation to exactly one authority.
 *
 * @author Nacos
 */
@Component
@Primary
public class McpCompatibilityOperationService implements McpOperationService {
    
    private final McpCompatibilityModeResolver modeResolver;
    
    private final LegacyMcpOperationService legacyService;
    
    private final McpLifecycleOperationService lifecycleService;
    
    public McpCompatibilityOperationService(McpCompatibilityModeResolver modeResolver,
        LegacyMcpOperationService legacyService, McpLifecycleOperationService lifecycleService) {
        this.modeResolver = modeResolver;
        this.legacyService = legacyService;
        this.lifecycleService = lifecycleService;
    }
    
    @Override
    public Page<McpServerBasicInfo> listMcpServerWithPage(String namespaceId, String mcpName,
        String search, int pageNo, int pageSize) throws NacosException {
        return current().listMcpServerWithPage(namespaceId, mcpName, search, pageNo, pageSize);
    }
    
    @Override
    public McpServerDetailInfo getMcpServerDetail(String namespaceId, String mcpServerId,
        String mcpServerName, String version) throws NacosException {
        return current().getMcpServerDetail(namespaceId, mcpServerId, mcpServerName, version);
    }
    
    @Override
    public String createMcpServer(String namespaceId, McpServerBasicInfo serverSpecification,
        McpToolSpecification toolSpecification, McpResourceSpecification resourceSpecification,
        McpEndpointSpec endpointSpecification) throws NacosException {
        return current().createMcpServer(namespaceId, serverSpecification, toolSpecification,
            resourceSpecification, endpointSpecification);
    }
    
    @Override
    public void updateMcpServer(String namespaceId, boolean isPublish,
        McpServerBasicInfo serverSpecification, McpToolSpecification toolSpecification,
        McpResourceSpecification resourceSpecification, McpEndpointSpec endpointSpecification,
        boolean overrideExisting) throws NacosException {
        current().updateMcpServer(namespaceId, isPublish, serverSpecification, toolSpecification,
            resourceSpecification, endpointSpecification, overrideExisting);
    }
    
    @Override
    public void deleteMcpServer(String namespaceId, String mcpName, String mcpServerId,
        String version) throws NacosException {
        current().deleteMcpServer(namespaceId, mcpName, mcpServerId, version);
    }
    
    /**
     * Page standard lifecycle Version summaries after the managed cutover.
     */
    public Page<McpServerVersionSummary> listMcpServerVersions(String namespaceId,
        String mcpName, String status, int pageNo, int pageSize) throws NacosException {
        return managed().listMcpServerVersions(namespaceId, mcpName, status, pageNo, pageSize);
    }
    
    /**
     * Read one exact standard lifecycle Version after the managed cutover.
     */
    public McpServerVersionDetail getMcpServerVersion(String namespaceId, String mcpName,
        String version) throws NacosException {
        return managed().getMcpServerVersion(namespaceId, mcpName, version);
    }
    
    /**
     * Create one standard MCP draft after the managed cutover.
     */
    public McpServerVersionDetail createMcpServerDraft(String namespaceId,
        McpServerBasicInfo serverSpecification, McpToolSpecification toolSpecification,
        McpResourceSpecification resourceSpecification,
        McpEndpointSpec endpointSpecification) throws NacosException {
        return managed().createMcpServerDraft(namespaceId, serverSpecification,
            toolSpecification, resourceSpecification, endpointSpecification);
    }
    
    /**
     * Replace one exact standard MCP draft after the managed cutover.
     */
    public McpServerVersionDetail updateMcpServerDraft(String namespaceId,
        McpServerBasicInfo serverSpecification, McpToolSpecification toolSpecification,
        McpResourceSpecification resourceSpecification,
        McpEndpointSpec endpointSpecification) throws NacosException {
        return managed().updateMcpServerDraft(namespaceId, serverSpecification,
            toolSpecification, resourceSpecification, endpointSpecification);
    }
    
    /**
     * Delete one exact standard MCP draft after the managed cutover.
     */
    public void deleteMcpServerDraft(String namespaceId, String mcpName, String version)
        throws NacosException {
        managed().deleteMcpServerDraft(namespaceId, mcpName, version);
    }
    
    /**
     * Submit one standard MCP Version after the managed cutover.
     */
    public McpServerVersionSummary submitMcpServerVersion(String namespaceId, String mcpName,
        String version) throws NacosException {
        return managed().submitMcpServerVersion(namespaceId, mcpName, version);
    }
    
    /**
     * Publish one standard MCP Version after the managed cutover.
     */
    public McpServerVersionSummary publishMcpServerVersion(String namespaceId, String mcpName,
        String version) throws NacosException {
        return managed().publishMcpServerVersion(namespaceId, mcpName, version);
    }
    
    /**
     * Force-publish one standard MCP Version after the managed cutover.
     */
    public McpServerVersionSummary forcePublishMcpServerVersion(String namespaceId,
        String mcpName, String version) throws NacosException {
        return managed().forcePublishMcpServerVersion(namespaceId, mcpName, version);
    }
    
    /**
     * Return one reviewed standard MCP Version to draft after the managed cutover.
     */
    public McpServerVersionSummary redraftMcpServerVersion(String namespaceId, String mcpName,
        String version) throws NacosException {
        return managed().redraftMcpServerVersion(namespaceId, mcpName, version);
    }
    
    /**
     * Bring one standard MCP Version online after the managed cutover.
     */
    public McpServerVersionSummary onlineMcpServerVersion(String namespaceId, String mcpName,
        String version) throws NacosException {
        return managed().onlineLifecycleVersion(namespaceId, mcpName, version);
    }
    
    /**
     * Take one standard MCP Version offline after the managed cutover.
     */
    public McpServerVersionSummary offlineMcpServerVersion(String namespaceId, String mcpName,
        String version) throws NacosException {
        return managed().offlineLifecycleVersion(namespaceId, mcpName, version);
    }
    
    /**
     * Replace custom MCP labels after the managed cutover.
     */
    public Map<String, String> updateMcpServerLabels(String namespaceId, String mcpName,
        Map<String, String> labels) throws NacosException {
        return managed().updateMcpServerLabels(namespaceId, mcpName, labels);
    }
    
    private McpOperationService current() throws NacosException {
        McpCompatibilityMode mode = modeResolver.resolve();
        if (McpCompatibilityMode.LIFECYCLE_MANAGED != mode) {
            return legacyService;
        }
        if (!modeResolver.localMemberSupportsManagedLifecycle()) {
            throw new NacosApiException(NacosException.SERVER_ERROR, ErrorCode.SERVER_ERROR,
                "This Nacos member cannot process MCP lifecycle-managed requests");
        }
        return lifecycleService;
    }
    
    private McpLifecycleOperationService managed() throws NacosException {
        if (McpCompatibilityMode.LIFECYCLE_MANAGED != modeResolver.resolve()) {
            throw new NacosApiException(NacosException.CONFLICT, ErrorCode.RESOURCE_CONFLICT,
                "MCP standard lifecycle APIs are unavailable before LIFECYCLE_MANAGED cutover");
        }
        if (!modeResolver.localMemberSupportsManagedLifecycle()) {
            throw new NacosApiException(NacosException.SERVER_ERROR, ErrorCode.SERVER_ERROR,
                "This Nacos member cannot process MCP lifecycle-managed requests");
        }
        return lifecycleService;
    }
}
