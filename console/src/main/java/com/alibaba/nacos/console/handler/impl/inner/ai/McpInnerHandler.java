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

import com.alibaba.nacos.ai.service.McpServerOperationService;
import com.alibaba.nacos.api.ai.model.mcp.McpEndpointSpec;
import com.alibaba.nacos.api.ai.model.mcp.McpServerBasicInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpServerDetailInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpToolSpecification;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.console.handler.ai.McpHandler;
import com.alibaba.nacos.console.handler.impl.inner.EnabledInnerHandler;
import org.springframework.stereotype.Service;

/**
 * Inner implementation of Mcp handler.
 *
 * @author xiweng.yy
 */
@Service
@EnabledInnerHandler
public class McpInnerHandler implements McpHandler {
    
    private final McpServerOperationService mcpServerOperationService;
    
    public McpInnerHandler(McpServerOperationService mcpServerOperationService) {
        this.mcpServerOperationService = mcpServerOperationService;
    }
    
    @Override
    public Page<McpServerBasicInfo> listMcpServers(String namespaceId, String mcpName, String search, int pageNo,
            int pageSize) {
        return mcpServerOperationService.listMcpServerWithPage(namespaceId, mcpName, search, pageNo, pageSize);
    }
    
    @Override
    public McpServerDetailInfo getMcpServer(String namespaceId, String mcpServerId, String version) throws NacosException {
        return mcpServerOperationService.getMcpServerDetail(namespaceId, mcpServerId, StringUtils.EMPTY, version);
    }
    
    @Override
    public void createMcpServer(String namespaceId, McpServerBasicInfo serverSpecification,
            McpToolSpecification toolSpecification, McpEndpointSpec endpointSpecification) throws NacosException {
        mcpServerOperationService.createMcpServer(namespaceId, serverSpecification, toolSpecification,
                endpointSpecification);
    }
    
    @Override
    public void updateMcpServer(String namespaceId, String mcpServerId, boolean isPublish, McpServerBasicInfo serverSpecification,
            McpToolSpecification toolSpecification, McpEndpointSpec endpointSpecification) throws NacosException {
        mcpServerOperationService.updateMcpServer(namespaceId, mcpServerId, isPublish, serverSpecification, toolSpecification,
                endpointSpecification);
    }
    
    @Override
    public void deleteMcpServer(String namespaceId, String mcpServerId, String version) throws NacosException {
        mcpServerOperationService.deleteMcpServer(namespaceId, mcpServerId, version);
    }
}
