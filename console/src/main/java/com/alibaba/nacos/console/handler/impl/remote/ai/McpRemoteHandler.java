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
import com.alibaba.nacos.api.ai.model.mcp.McpLifecycleVersionDetail;
import com.alibaba.nacos.api.ai.model.mcp.McpLifecycleVersionSummary;
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
    
    private static final String LIFECYCLE_REMOTE_MESSAGE =
        "Remote MCP lifecycle management requires the typed Maintainer SDK lifecycle API";
    
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
    public Page<McpLifecycleVersionSummary> listLifecycleVersions(String namespaceId,
        String mcpName, String status, int pageNo, int pageSize) throws NacosException {
        throw lifecycleDisabled();
    }
    
    @Override
    public McpLifecycleVersionDetail getLifecycleVersion(String namespaceId, String mcpName,
        String version) throws NacosException {
        throw lifecycleDisabled();
    }
    
    @Override
    public McpLifecycleVersionDetail createLifecycleDraft(String namespaceId,
        McpServerBasicInfo serverSpecification, McpToolSpecification toolSpecification,
        McpResourceSpecification resourceSpecification,
        McpEndpointSpec endpointSpecification) throws NacosException {
        throw lifecycleDisabled();
    }
    
    @Override
    public McpLifecycleVersionDetail updateLifecycleDraft(String namespaceId,
        McpServerBasicInfo serverSpecification, McpToolSpecification toolSpecification,
        McpResourceSpecification resourceSpecification,
        McpEndpointSpec endpointSpecification) throws NacosException {
        throw lifecycleDisabled();
    }
    
    @Override
    public void deleteLifecycleDraft(String namespaceId, String mcpName, String version)
        throws NacosException {
        throw lifecycleDisabled();
    }
    
    @Override
    public McpLifecycleVersionSummary submitLifecycleVersion(String namespaceId, String mcpName,
        String version) throws NacosException {
        throw lifecycleDisabled();
    }
    
    @Override
    public McpLifecycleVersionSummary publishLifecycleVersion(String namespaceId, String mcpName,
        String version) throws NacosException {
        throw lifecycleDisabled();
    }
    
    @Override
    public McpLifecycleVersionSummary forcePublishLifecycleVersion(String namespaceId,
        String mcpName, String version) throws NacosException {
        throw lifecycleDisabled();
    }
    
    @Override
    public McpLifecycleVersionSummary redraftLifecycleVersion(String namespaceId, String mcpName,
        String version) throws NacosException {
        throw lifecycleDisabled();
    }
    
    @Override
    public McpLifecycleVersionSummary onlineLifecycleVersion(String namespaceId, String mcpName,
        String version) throws NacosException {
        throw lifecycleDisabled();
    }
    
    @Override
    public McpLifecycleVersionSummary offlineLifecycleVersion(String namespaceId, String mcpName,
        String version) throws NacosException {
        throw lifecycleDisabled();
    }
    
    @Override
    public Map<String, String> updateLifecycleLabels(String namespaceId, String mcpName,
        Map<String, String> labels) throws NacosException {
        throw lifecycleDisabled();
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
    
    private NacosApiException lifecycleDisabled() {
        return new NacosApiException(NacosException.SERVER_NOT_IMPLEMENTED,
            ErrorCode.API_FUNCTION_DISABLED, LIFECYCLE_REMOTE_MESSAGE);
    }
}
