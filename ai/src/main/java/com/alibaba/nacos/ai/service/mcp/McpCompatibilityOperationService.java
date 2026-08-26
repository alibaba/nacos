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
import com.alibaba.nacos.api.ai.model.mcp.McpResourceSpecification;
import com.alibaba.nacos.api.ai.model.mcp.McpServerBasicInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpServerDetailInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpToolSpecification;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.Page;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

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
    
    private McpOperationService current() {
        McpCompatibilityMode mode = modeResolver.resolve();
        return McpCompatibilityMode.LIFECYCLE_MANAGED == mode ? lifecycleService : legacyService;
    }
}
