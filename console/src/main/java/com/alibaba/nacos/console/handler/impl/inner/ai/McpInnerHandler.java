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

package com.alibaba.nacos.console.handler.impl.inner.ai;

import com.alibaba.nacos.ai.service.McpLegacyImportAdapter;
import com.alibaba.nacos.ai.service.mcp.McpCompatibilityOperationService;
import com.alibaba.nacos.ai.service.mcp.McpOperationService;
import com.alibaba.nacos.api.ai.model.mcp.McpEndpointSpec;
import com.alibaba.nacos.api.ai.model.mcp.McpServerVersionDetail;
import com.alibaba.nacos.api.ai.model.mcp.McpServerVersionSummary;
import com.alibaba.nacos.api.ai.model.mcp.McpResourceSpecification;
import com.alibaba.nacos.api.ai.model.mcp.McpServerBasicInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpServerDetailInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpServerImportRequest;
import com.alibaba.nacos.api.ai.model.mcp.McpServerImportResponse;
import com.alibaba.nacos.api.ai.model.mcp.McpServerImportValidationResult;
import com.alibaba.nacos.api.ai.model.mcp.McpToolSpecification;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.console.handler.ai.EnabledAiHandler;
import com.alibaba.nacos.console.handler.ai.McpHandler;
import com.alibaba.nacos.console.handler.impl.inner.EnabledInnerHandler;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Inner implementation of Mcp handler.
 *
 * @author xiweng.yy
 */
@Service
@EnabledInnerHandler
@EnabledAiHandler
public class McpInnerHandler implements McpHandler {
    
    private final McpOperationService mcpServerOperationService;
    
    private final McpLegacyImportAdapter mcpLegacyImportAdapter;
    
    private final McpCompatibilityOperationService lifecycleOperationService;
    
    public McpInnerHandler(McpOperationService mcpServerOperationService,
        McpLegacyImportAdapter mcpLegacyImportAdapter,
        McpCompatibilityOperationService lifecycleOperationService) {
        this.mcpServerOperationService = mcpServerOperationService;
        this.mcpLegacyImportAdapter = mcpLegacyImportAdapter;
        this.lifecycleOperationService = lifecycleOperationService;
    }
    
    @Override
    public Page<McpServerBasicInfo> listMcpServers(String namespaceId, String mcpName,
        String search, int pageNo,
        int pageSize) throws NacosException {
        return mcpServerOperationService.listMcpServerWithPage(namespaceId, mcpName, search, pageNo,
            pageSize);
    }
    
    @Override
    public McpServerDetailInfo getMcpServer(String namespaceId, String mcpName, String mcpServerId,
        String version) throws NacosException {
        return mcpServerOperationService.getMcpServerDetail(namespaceId, mcpServerId, mcpName,
            version);
    }
    
    @Override
    public String createMcpServer(String namespaceId, McpServerBasicInfo serverSpecification,
        McpToolSpecification toolSpecification, McpEndpointSpec endpointSpecification)
        throws NacosException {
        return mcpServerOperationService.createMcpServer(namespaceId, serverSpecification,
            toolSpecification,
            endpointSpecification);
    }
    
    @Override
    public void updateMcpServer(String namespaceId, boolean isPublish,
        McpServerBasicInfo serverSpecification,
        McpToolSpecification toolSpecification, McpEndpointSpec endpointSpecification,
        boolean overrideExisting) throws NacosException {
        mcpServerOperationService.updateMcpServer(namespaceId, isPublish, serverSpecification,
            toolSpecification,
            endpointSpecification, overrideExisting);
    }
    
    @Override
    public void deleteMcpServer(String namespaceId, String mcpName, String mcpServerId,
        String version) throws NacosException {
        mcpServerOperationService.deleteMcpServer(namespaceId, mcpName, mcpServerId, version);
    }
    
    @Override
    public Page<McpServerVersionSummary> listMcpServerVersions(String namespaceId,
        String mcpName, String status, int pageNo, int pageSize) throws NacosException {
        return lifecycleOperationService.listMcpServerVersions(namespaceId, mcpName, status,
            pageNo, pageSize);
    }
    
    @Override
    public McpServerVersionDetail getMcpServerVersion(String namespaceId, String mcpName,
        String version) throws NacosException {
        return lifecycleOperationService.getMcpServerVersion(namespaceId, mcpName, version);
    }
    
    @Override
    public McpServerVersionDetail createMcpServerDraft(String namespaceId,
        McpServerBasicInfo serverSpecification, McpToolSpecification toolSpecification,
        McpResourceSpecification resourceSpecification,
        McpEndpointSpec endpointSpecification) throws NacosException {
        return lifecycleOperationService.createMcpServerDraft(namespaceId, serverSpecification,
            toolSpecification, resourceSpecification, endpointSpecification);
    }
    
    @Override
    public McpServerVersionDetail updateMcpServerDraft(String namespaceId,
        McpServerBasicInfo serverSpecification, McpToolSpecification toolSpecification,
        McpResourceSpecification resourceSpecification,
        McpEndpointSpec endpointSpecification) throws NacosException {
        return lifecycleOperationService.updateMcpServerDraft(namespaceId, serverSpecification,
            toolSpecification, resourceSpecification, endpointSpecification);
    }
    
    @Override
    public void deleteMcpServerDraft(String namespaceId, String mcpName, String version)
        throws NacosException {
        lifecycleOperationService.deleteMcpServerDraft(namespaceId, mcpName, version);
    }
    
    @Override
    public McpServerVersionSummary submitMcpServerVersion(String namespaceId, String mcpName,
        String version) throws NacosException {
        return lifecycleOperationService.submitMcpServerVersion(namespaceId, mcpName, version);
    }
    
    @Override
    public McpServerVersionSummary publishMcpServerVersion(String namespaceId, String mcpName,
        String version) throws NacosException {
        return lifecycleOperationService.publishMcpServerVersion(namespaceId, mcpName, version);
    }
    
    @Override
    public McpServerVersionSummary forcePublishMcpServerVersion(String namespaceId,
        String mcpName, String version) throws NacosException {
        return lifecycleOperationService.forcePublishMcpServerVersion(namespaceId, mcpName,
            version);
    }
    
    @Override
    public McpServerVersionSummary redraftMcpServerVersion(String namespaceId, String mcpName,
        String version) throws NacosException {
        return lifecycleOperationService.redraftMcpServerVersion(namespaceId, mcpName, version);
    }
    
    @Override
    public McpServerVersionSummary onlineMcpServerVersion(String namespaceId, String mcpName,
        String version) throws NacosException {
        return lifecycleOperationService.onlineMcpServerVersion(namespaceId, mcpName, version);
    }
    
    @Override
    public McpServerVersionSummary offlineMcpServerVersion(String namespaceId, String mcpName,
        String version) throws NacosException {
        return lifecycleOperationService.offlineMcpServerVersion(namespaceId, mcpName, version);
    }
    
    @Override
    public Map<String, String> updateMcpServerLabels(String namespaceId, String mcpName,
        Map<String, String> labels) throws NacosException {
        return lifecycleOperationService.updateMcpServerLabels(namespaceId, mcpName, labels);
    }
    
    @Deprecated
    @Override
    public McpServerImportValidationResult validateImport(String namespaceId,
        McpServerImportRequest request) throws NacosException {
        return mcpLegacyImportAdapter.validateImport(namespaceId, request);
    }
    
    @Deprecated
    @Override
    public McpServerImportResponse executeImport(String namespaceId, McpServerImportRequest request)
        throws NacosException {
        return mcpLegacyImportAdapter.executeImport(namespaceId, request);
    }
}
