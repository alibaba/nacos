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

import com.alibaba.nacos.ai.service.McpOperationService;
import com.alibaba.nacos.api.ai.model.mcp.McpEndpointSpec;
import com.alibaba.nacos.api.ai.model.mcp.McpServerBasicInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpServerDetailInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpTool;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.console.handler.ai.McpHandler;
import com.alibaba.nacos.console.handler.impl.inner.EnabledInnerHandler;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Inner implementation of Mcp handler.
 *
 * @author xiweng.yy
 */
@Service
@EnabledInnerHandler
public class McpInnerHandler implements McpHandler {
    
    private final McpOperationService mcpOperationService;
    
    public McpInnerHandler(McpOperationService mcpOperationService) {
        this.mcpOperationService = mcpOperationService;
    }
    
    @Override
    public Page<McpServerBasicInfo> listMcpServers(String namespaceId, String mcpName, String search, int pageNo,
            int pageSize) {
        return mcpOperationService.listMcpServer(namespaceId, mcpName, search, pageNo, pageSize);
    }
    
    @Override
    public McpServerDetailInfo getMcpServer(String namespaceId, String mcpName) throws NacosException {
        return mcpOperationService.getMcpServer(namespaceId, mcpName);
    }
    
    @Override
    public void createMcpServer(String namespaceId, String mcpName, McpServerBasicInfo serverSpecification,
            List<McpTool> toolSpecification, McpEndpointSpec endpointSpecification) throws NacosException {
        mcpOperationService.createMcpServer(namespaceId, mcpName, serverSpecification, toolSpecification,
                endpointSpecification);
    }
    
    @Override
    public void updateMcpServer(String namespaceId, String mcpName, McpServerBasicInfo serverSpecification,
            List<McpTool> toolSpecification, McpEndpointSpec endpointSpecification) throws NacosException {
        mcpOperationService.updateMcpServer(namespaceId, mcpName, serverSpecification, toolSpecification,
                endpointSpecification);
    }
    
    @Override
    public void deleteMcpServer(String namespaceId, String mcpName) throws NacosException {
        mcpOperationService.deleteMcpServer(namespaceId, mcpName);
    }
}
