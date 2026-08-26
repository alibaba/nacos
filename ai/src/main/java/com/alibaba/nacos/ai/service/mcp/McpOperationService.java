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

/**
 * Complete operation contract exposed by the historical MCP management and client surfaces.
 *
 * <p>Routing is always performed for this complete contract. Read and write operations must not
 * be selected independently because that would allow one request path to observe a different
 * authority from the path that mutates it.</p>
 *
 * @author Nacos
 */
public interface McpOperationService {
    
    /**
     * List MCP servers.
     */
    Page<McpServerBasicInfo> listMcpServerWithPage(String namespaceId, String mcpName,
        String search, int pageNo, int pageSize) throws NacosException;
    
    /**
     * Get one MCP server detail by name, deprecated id, or a matching pair.
     */
    McpServerDetailInfo getMcpServerDetail(String namespaceId, String mcpServerId,
        String mcpServerName, String version) throws NacosException;
    
    /**
     * Create the first MCP Version without a Resources document.
     */
    default String createMcpServer(String namespaceId,
        McpServerBasicInfo serverSpecification, McpToolSpecification toolSpecification,
        McpEndpointSpec endpointSpecification) throws NacosException {
        return createMcpServer(namespaceId, serverSpecification, toolSpecification, null,
            endpointSpecification);
    }
    
    /**
     * Create the first MCP Version.
     */
    String createMcpServer(String namespaceId, McpServerBasicInfo serverSpecification,
        McpToolSpecification toolSpecification, McpResourceSpecification resourceSpecification,
        McpEndpointSpec endpointSpecification) throws NacosException;
    
    /**
     * Update one MCP Version without a Resources document.
     */
    default void updateMcpServer(String namespaceId, boolean isPublish,
        McpServerBasicInfo serverSpecification, McpToolSpecification toolSpecification,
        McpEndpointSpec endpointSpecification, boolean overrideExisting) throws NacosException {
        updateMcpServer(namespaceId, isPublish, serverSpecification, toolSpecification, null,
            endpointSpecification, overrideExisting);
    }
    
    /**
     * Add or overwrite one MCP Version through the historical compatibility contract.
     */
    void updateMcpServer(String namespaceId, boolean isPublish,
        McpServerBasicInfo serverSpecification, McpToolSpecification toolSpecification,
        McpResourceSpecification resourceSpecification, McpEndpointSpec endpointSpecification,
        boolean overrideExisting) throws NacosException;
    
    /**
     * Delete one MCP Version, or the complete MCP resource when Version is blank.
     */
    void deleteMcpServer(String namespaceId, String mcpName, String mcpServerId, String version)
        throws NacosException;
}
