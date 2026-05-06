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

import com.alibaba.nacos.ai.service.McpServerImportService;
import com.alibaba.nacos.ai.service.McpServerOperationService;
import com.alibaba.nacos.api.ai.model.mcp.McpEndpointSpec;
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
import com.alibaba.nacos.console.handler.impl.ConditionFunctionEnabled;
import com.alibaba.nacos.console.handler.impl.inner.EnabledInnerHandler;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Service;

/**
 * Inner implementation of Mcp handler.
 *
 * @author xiweng.yy
 */
@Service
@EnabledInnerHandler
@EnabledAiHandler
@Conditional(ConditionFunctionEnabled.ConditionAiEnabled.class)
public class McpInnerHandler implements McpHandler {
    
    private final McpServerOperationService mcpServerOperationService;
    
    private final McpServerImportService mcpServerImportService;
    
    public McpInnerHandler(McpServerOperationService mcpServerOperationService,
                          McpServerImportService mcpServerImportService) {
        this.mcpServerOperationService = mcpServerOperationService;
        this.mcpServerImportService = mcpServerImportService;
    }
    
    @Override
    public Page<McpServerBasicInfo> listMcpServers(String namespaceId, String mcpName, String search, int pageNo,
            int pageSize) {
        return mcpServerOperationService.listMcpServerWithPage(namespaceId, mcpName, search, pageNo, pageSize);
    }
    
    @Override
    public McpServerDetailInfo getMcpServer(String namespaceId, String mcpName, String mcpServerId, String version) throws NacosException {
        return mcpServerOperationService.getMcpServerDetail(namespaceId, mcpServerId, mcpName, version);
    }
    
    @Override
    public String createMcpServer(String namespaceId, McpServerBasicInfo serverSpecification,
            McpToolSpecification toolSpecification, McpEndpointSpec endpointSpecification) throws NacosException {
        return mcpServerOperationService.createMcpServer(namespaceId, serverSpecification, toolSpecification,
                endpointSpecification);
    }
    
    @Override
    public void updateMcpServer(String namespaceId, boolean isPublish, McpServerBasicInfo serverSpecification,
            McpToolSpecification toolSpecification, McpEndpointSpec endpointSpecification, boolean overrideExisting) throws NacosException {
        mcpServerOperationService.updateMcpServer(namespaceId, isPublish, serverSpecification, toolSpecification,
                endpointSpecification, overrideExisting);
    }
    
    @Override
    public void deleteMcpServer(String namespaceId, String mcpName, String mcpServerId, String version) throws NacosException {
        mcpServerOperationService.deleteMcpServer(namespaceId, mcpName, mcpServerId, version);
    }
    
    @Override
    public McpServerImportValidationResult validateImport(String namespaceId, McpServerImportRequest request) throws NacosException {
        return mcpServerImportService.validateImport(namespaceId, request);
    }
    
    @Override
    public McpServerImportResponse executeImport(String namespaceId, McpServerImportRequest request) throws NacosException {
        return mcpServerImportService.executeImport(namespaceId, request);
    }
}
