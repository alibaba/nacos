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

package com.alibaba.nacos.console.proxy.ai;

import com.alibaba.nacos.api.ai.model.mcp.McpEndpointSpec;
import com.alibaba.nacos.api.ai.model.mcp.McpServerBasicInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpServerDetailInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpServerImportRequest;
import com.alibaba.nacos.api.ai.model.mcp.McpServerImportResponse;
import com.alibaba.nacos.api.ai.model.mcp.McpServerImportValidationResult;
import com.alibaba.nacos.api.ai.model.mcp.McpToolSpecification;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.console.handler.ai.McpHandler;
import org.springframework.stereotype.Service;

/**
 * Proxy class for handling AI MCP operations.
 *
 * @author xiweng.yy
 */
@Service
public class McpProxy {
    
    private final McpHandler mcpHandler;
    
    public McpProxy(McpHandler mcpHandler) {
        this.mcpHandler = mcpHandler;
    }
    
    /**
     * List mcp server.
     *
     * @param namespaceId namespace id of mcp servers
     * @param mcpName     mcp name pattern, if null or empty, filter all mcp servers.
     * @param search      search type `blur` or `accurate`, means whether to search by fuzzy or exact match by
     *                    `mcpName`.
     * @param pageNo      page number, start from 1
     * @param pageSize    page size each page
     * @return list of {@link McpServerBasicInfo} matched input parameters.
     */
    public Page<McpServerBasicInfo> listMcpServers(String namespaceId, String mcpName, String search, int pageNo,
            int pageSize) throws NacosException {
        return mcpHandler.listMcpServers(namespaceId, mcpName, search, pageNo, pageSize);
    }
    
    /**
     * Get specified mcp server detail info.
     *
     * @param namespaceId namespace id of mcp server
     * @param mcpName     name of mcp server
     * @return detail info with {@link McpServerDetailInfo}
     * @throws NacosException any exception during handling
     */
    public McpServerDetailInfo getMcpServer(String namespaceId, String mcpName, String mcpId, String version) throws NacosException {
        return mcpHandler.getMcpServer(namespaceId, mcpName, mcpId, version);
    }
    
    /**
     * Create new mcp server.
     *
     * @param namespaceId           namespace id of mcp server
     * @param serverSpecification   mcp server specification, see {@link McpServerBasicInfo}
     * @param toolSpecification     mcp server included tools, see {@link McpToolSpecification}, optional
     * @param endpointSpecification mcp server endpoint specification, see {@link McpEndpointSpec}, optional
     * @return mcp server id of the new mcp server
     * @throws NacosException any exception during handling
     */
    public String createMcpServer(String namespaceId, McpServerBasicInfo serverSpecification,
            McpToolSpecification toolSpecification, McpEndpointSpec endpointSpecification) throws NacosException {
        return mcpHandler.createMcpServer(namespaceId, serverSpecification, toolSpecification, endpointSpecification);
    }
    
    /**
     * Update existed mcp server.
     *
     * <p>
     * `namespaceId` and `mcpName` can't be changed.
     * </p>
     *
     * @param namespaceId           namespace id of mcp server, used to mark which mcp server to update
     * @param isPublish             if publish the mcp server or just save the mcp
     * @param serverSpecification   mcp server specification, see {@link McpServerBasicInfo}
     * @param toolSpecification     mcp server included tools, see {@link McpToolSpecification}, optional
     * @param endpointSpecification mcp server endpoint specification, see {@link McpEndpointSpec}, optional
     * @throws NacosException any exception during handling
     */
    public void updateMcpServer(String namespaceId, boolean isPublish, McpServerBasicInfo serverSpecification,
            McpToolSpecification toolSpecification, McpEndpointSpec endpointSpecification) throws NacosException {
        mcpHandler.updateMcpServer(namespaceId, isPublish, serverSpecification, toolSpecification, endpointSpecification);
    }
    
    /**
     * Delete existed mcp server.
     *
     * @param namespaceId namespace id of mcp server
     * @param mcpServerId     name of mcp server
     * @param version     version of the mcp server
     * @throws NacosException any exception during handling
     */
    public void deleteMcpServer(String namespaceId, String mcpName, String mcpServerId, String version) throws NacosException {
        mcpHandler.deleteMcpServer(namespaceId, mcpName, mcpServerId, version);
    }
    
    /**
     * Validate MCP server import request.
     *
     * @param namespaceId namespace id for mcp servers
     * @param request     import request containing data and settings
     * @return validation result with details about potential issues
     * @throws NacosException any exception during validation
     */
    public McpServerImportValidationResult validateImport(String namespaceId, McpServerImportRequest request) throws NacosException {
        return mcpHandler.validateImport(namespaceId, request);
    }
    
    /**
     * Execute MCP server import operation.
     *
     * @param namespaceId namespace id for mcp servers
     * @param request     import request containing data and settings
     * @return import response with results and statistics
     * @throws NacosException any exception during import execution
     */
    public McpServerImportResponse executeImport(String namespaceId, McpServerImportRequest request) throws NacosException {
        return mcpHandler.executeImport(namespaceId, request);
    }
}
