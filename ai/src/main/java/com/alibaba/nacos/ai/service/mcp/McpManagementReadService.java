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

import com.alibaba.nacos.ai.service.McpServerOperationService;
import com.alibaba.nacos.api.ai.model.mcp.McpServerBasicInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpServerDetailInfo;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.Page;
import org.springframework.stereotype.Service;

/**
 * Stable read facade for legacy MCP Admin, Console and Maintainer management APIs.
 *
 * <p>SYNCING deliberately delegates to the historical implementation. Only the permanent,
 * one-way lifecycle marker switches reads to canonical Resource/Version rows; therefore merging
 * the read implementation cannot expose a partially reconciled dataset.</p>
 *
 * @author Nacos
 */
@Service
public class McpManagementReadService {
    
    private final McpServerOperationService legacyReadService;
    
    private final McpLifecycleReadService lifecycleReadService;
    
    private final McpLifecycleManagementStateService managementStateService;
    
    public McpManagementReadService(McpServerOperationService legacyReadService,
        McpLifecycleReadService lifecycleReadService,
        McpLifecycleManagementStateService managementStateService) {
        this.legacyReadService = legacyReadService;
        this.lifecycleReadService = lifecycleReadService;
        this.managementStateService = managementStateService;
    }
    
    /**
     * List MCP servers through the route authorized by the migration state.
     */
    public Page<McpServerBasicInfo> listMcpServers(String namespaceId, String mcpName,
        String search, int pageNo, int pageSize) throws NacosException {
        if (managementStateService.isLifecycleManaged()) {
            return lifecycleReadService.listMcpServers(namespaceId, mcpName, search, pageNo,
                pageSize);
        }
        return legacyReadService.listMcpServerWithPage(namespaceId, mcpName, search, pageNo,
            pageSize);
    }
    
    /**
     * Get one MCP detail through the route authorized by the migration state.
     */
    public McpServerDetailInfo getMcpServer(String namespaceId, String mcpName, String mcpId,
        String version) throws NacosException {
        if (managementStateService.isLifecycleManaged()) {
            return lifecycleReadService.getMcpServer(namespaceId, mcpName, mcpId, version);
        }
        return legacyReadService.getMcpServerDetail(namespaceId, mcpId, mcpName, version);
    }
}
