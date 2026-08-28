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

package com.alibaba.nacos.console.handler.impl.remote.ai;

import com.alibaba.nacos.ai.constant.Constants;
import com.alibaba.nacos.api.ai.model.mcp.McpEndpointSpec;
import com.alibaba.nacos.api.ai.model.mcp.McpServerDraftRequest;
import com.alibaba.nacos.api.ai.model.mcp.McpServerLabelsUpdateRequest;
import com.alibaba.nacos.api.ai.model.mcp.McpServerVersionCommand;
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
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.console.handler.ai.EnabledAiHandler;
import com.alibaba.nacos.console.handler.ai.McpHandler;
import com.alibaba.nacos.console.handler.impl.remote.EnabledRemoteHandler;
import com.alibaba.nacos.console.handler.impl.remote.NacosMaintainerClientHolder;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Remote implementation of Mcp handler.
 *
 * @author xiweng.yy
 */
@Service
@EnabledRemoteHandler
@EnabledAiHandler
public class McpRemoteHandler implements McpHandler {
    
    private final NacosMaintainerClientHolder clientHolder;
    
    public McpRemoteHandler(NacosMaintainerClientHolder clientHolder) {
        this.clientHolder = clientHolder;
    }
    
    @Override
    public Page<McpServerBasicInfo> listMcpServers(String namespaceId, String mcpName,
        String search, int pageNo,
        int pageSize) throws NacosException {
        if (Constants.MCP_LIST_SEARCH_ACCURATE.equalsIgnoreCase(search)) {
            return clientHolder.getAiMaintainerService().mcp().listMcpServer(namespaceId, mcpName,
                pageNo, pageSize);
        } else {
            return clientHolder.getAiMaintainerService().mcp().searchMcpServer(namespaceId, mcpName,
                pageNo, pageSize);
        }
    }
    
    @Override
    public McpServerDetailInfo getMcpServer(String namespaceId, String mcpName, String mcpId,
        String version)
        throws NacosException {
        return clientHolder.getAiMaintainerService().mcp().getMcpServerDetail(namespaceId, mcpName,
            mcpId, version);
    }
    
    @Override
    public String createMcpServer(String namespaceId, McpServerBasicInfo serverSpecification,
        McpToolSpecification toolSpecification, McpEndpointSpec endpointSpecification)
        throws NacosException {
        return clientHolder.getAiMaintainerService().mcp()
            .createMcpServer(namespaceId, serverSpecification.getName(), serverSpecification,
                toolSpecification,
                endpointSpecification);
    }
    
    @Override
    public void updateMcpServer(String namespaceId, boolean isPublish,
        McpServerBasicInfo serverSpecification,
        McpToolSpecification toolSpecification, McpEndpointSpec endpointSpecification,
        boolean overrideExisting) throws NacosException {
        clientHolder.getAiMaintainerService().mcp()
            .updateMcpServer(namespaceId, serverSpecification.getName(), isPublish,
                serverSpecification,
                toolSpecification, endpointSpecification, overrideExisting);
    }
    
    @Override
    public void deleteMcpServer(String namespaceId, String mcpName, String mcpId, String version)
        throws NacosException {
        clientHolder.getAiMaintainerService().mcp().deleteMcpServer(namespaceId, mcpName, mcpId,
            version);
    }
    
    @Override
    public Page<McpServerVersionSummary> listMcpServerVersions(String namespaceId,
        String mcpName, String status, int pageNo, int pageSize) throws NacosException {
        return clientHolder.getAiMaintainerService().mcp().listMcpServerVersions(namespaceId,
            mcpName, status, pageNo, pageSize);
    }
    
    @Override
    public McpServerVersionDetail getMcpServerVersion(String namespaceId, String mcpName,
        String version) throws NacosException {
        return clientHolder.getAiMaintainerService().mcp().getMcpServerVersion(namespaceId,
            mcpName, version);
    }
    
    @Override
    public McpServerVersionDetail createMcpServerDraft(String namespaceId,
        McpServerBasicInfo serverSpecification, McpToolSpecification toolSpecification,
        McpResourceSpecification resourceSpecification,
        McpEndpointSpec endpointSpecification) throws NacosException {
        return clientHolder.getAiMaintainerService().mcp().createMcpServer(namespaceId,
            draftRequest(serverSpecification, toolSpecification, resourceSpecification,
                endpointSpecification));
    }
    
    @Override
    public McpServerVersionDetail updateMcpServerDraft(String namespaceId,
        McpServerBasicInfo serverSpecification, McpToolSpecification toolSpecification,
        McpResourceSpecification resourceSpecification,
        McpEndpointSpec endpointSpecification) throws NacosException {
        return clientHolder.getAiMaintainerService().mcp().updateMcpServer(namespaceId,
            draftRequest(serverSpecification, toolSpecification, resourceSpecification,
                endpointSpecification));
    }
    
    @Override
    public void deleteMcpServerDraft(String namespaceId, String mcpName, String version)
        throws NacosException {
        clientHolder.getAiMaintainerService().mcp().deleteMcpServerDraft(namespaceId,
            versionCommand(mcpName, version));
    }
    
    @Override
    public McpServerVersionSummary submitMcpServerVersion(String namespaceId, String mcpName,
        String version) throws NacosException {
        return clientHolder.getAiMaintainerService().mcp().submitMcpServerVersion(namespaceId,
            versionCommand(mcpName, version));
    }
    
    @Override
    public McpServerVersionSummary publishMcpServerVersion(String namespaceId, String mcpName,
        String version) throws NacosException {
        return clientHolder.getAiMaintainerService().mcp().publishMcpServerVersion(namespaceId,
            versionCommand(mcpName, version));
    }
    
    @Override
    public McpServerVersionSummary forcePublishMcpServerVersion(String namespaceId,
        String mcpName, String version) throws NacosException {
        return clientHolder.getAiMaintainerService().mcp()
            .forcePublishMcpServerVersion(namespaceId, versionCommand(mcpName, version));
    }
    
    @Override
    public McpServerVersionSummary redraftMcpServerVersion(String namespaceId, String mcpName,
        String version) throws NacosException {
        return clientHolder.getAiMaintainerService().mcp().redraftMcpServerVersion(namespaceId,
            versionCommand(mcpName, version));
    }
    
    @Override
    public McpServerVersionSummary onlineMcpServerVersion(String namespaceId, String mcpName,
        String version) throws NacosException {
        return clientHolder.getAiMaintainerService().mcp().onlineMcpServerVersion(namespaceId,
            versionCommand(mcpName, version));
    }
    
    @Override
    public McpServerVersionSummary offlineMcpServerVersion(String namespaceId, String mcpName,
        String version) throws NacosException {
        return clientHolder.getAiMaintainerService().mcp().offlineMcpServerVersion(namespaceId,
            versionCommand(mcpName, version));
    }
    
    @Override
    public Map<String, String> updateMcpServerLabels(String namespaceId, String mcpName,
        Map<String, String> labels) throws NacosException {
        McpServerLabelsUpdateRequest request = new McpServerLabelsUpdateRequest();
        request.setMcpName(mcpName);
        request.setLabels(labels);
        return clientHolder.getAiMaintainerService().mcp().updateMcpServerLabels(namespaceId,
            request);
    }
    
    @Deprecated
    @Override
    public McpServerImportValidationResult validateImport(String namespaceId,
        McpServerImportRequest request)
        throws NacosException {
        throw new NacosApiException(NacosException.SERVER_NOT_IMPLEMENTED,
            ErrorCode.API_FUNCTION_DISABLED,
            "MCP import functionality is not supported in remote mode");
    }
    
    @Deprecated
    @Override
    public McpServerImportResponse executeImport(String namespaceId, McpServerImportRequest request)
        throws NacosException {
        throw new NacosApiException(NacosException.SERVER_NOT_IMPLEMENTED,
            ErrorCode.API_FUNCTION_DISABLED,
            "MCP import functionality is not supported in remote mode");
    }
    
    private McpServerDraftRequest draftRequest(McpServerBasicInfo serverSpecification,
        McpToolSpecification toolSpecification, McpResourceSpecification resourceSpecification,
        McpEndpointSpec endpointSpecification) {
        McpServerDraftRequest request = new McpServerDraftRequest();
        request.setServerSpecification(serverSpecification);
        request.setToolSpecification(toolSpecification);
        request.setResourceSpecification(resourceSpecification);
        request.setEndpointSpecification(endpointSpecification);
        return request;
    }
    
    private McpServerVersionCommand versionCommand(String mcpName, String version) {
        McpServerVersionCommand command = new McpServerVersionCommand();
        command.setMcpName(mcpName);
        command.setVersion(version);
        return command;
    }
}
