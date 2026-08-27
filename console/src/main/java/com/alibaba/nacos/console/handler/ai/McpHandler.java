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

package com.alibaba.nacos.console.handler.ai;

import com.alibaba.nacos.api.ai.model.mcp.McpEndpointSpec;
import com.alibaba.nacos.api.ai.model.mcp.McpLifecycleVersionDetail;
import com.alibaba.nacos.api.ai.model.mcp.McpLifecycleVersionSummary;
import com.alibaba.nacos.api.ai.model.mcp.McpResourceSpecification;
import com.alibaba.nacos.api.ai.model.mcp.McpServerBasicInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpServerDetailInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpServerImportRequest;
import com.alibaba.nacos.api.ai.model.mcp.McpServerImportResponse;
import com.alibaba.nacos.api.ai.model.mcp.McpServerImportValidationResult;
import com.alibaba.nacos.api.ai.model.mcp.McpTool;
import com.alibaba.nacos.api.ai.model.mcp.McpToolSpecification;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.Page;

import java.util.Map;

/**
 * Actual Handler class for handling AI MCP operations.
 *
 * @author xiweng.yy
 */
public interface McpHandler {
    
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
     * @throws NacosException any exception during handling
     */
    Page<McpServerBasicInfo> listMcpServers(String namespaceId, String mcpName, String search,
        int pageNo, int pageSize)
        throws NacosException;
    
    /**
     * Get specified mcp server detail info.
     *
     * @param namespaceId namespace id of mcp server
     * @param mcpName     name of mcp server
     * @param mcpId       id of mcp server
     * @param version     version of the mcp server
     * @return detail info with {@link McpServerDetailInfo}
     * @throws NacosException any exception during handling
     */
    McpServerDetailInfo getMcpServer(String namespaceId, String mcpName, String mcpId,
        String version) throws NacosException;
    
    /**
     * Create new mcp server.
     *
     * @param namespaceId           namespace id of mcp server
     * @param serverSpecification   mcp server specification, see {@link McpServerBasicInfo}
     * @param toolSpecification     mcp server included tools, see {@link McpTool}, optional
     * @param endpointSpecification mcp server endpoint specification, see {@link McpEndpointSpec}, optional
     * @return mcp server id of the new mcp server
     * @throws NacosException any exception during handling
     */
    String createMcpServer(String namespaceId, McpServerBasicInfo serverSpecification,
        McpToolSpecification toolSpecification, McpEndpointSpec endpointSpecification)
        throws NacosException;
    
    /**
     * Update existed mcp server.
     *
     * <p>
     * `namespaceId` and `mcpName` can't be changed.
     * </p>
     *
     * @param namespaceId           namespace id of mcp server, used to mark which mcp server to update
     * @param isPublish             publish the current version to latest
     * @param serverSpecification   mcp server specification, see {@link McpServerBasicInfo}
     * @param toolSpecification     mcp server included tools, see {@link McpTool}, optional
     * @param endpointSpecification mcp server endpoint specification, see {@link McpEndpointSpec}, optional
     * @param overrideExisting      if replace all the instances when update the mcp server
     * @throws NacosException any exception during handling
     */
    void updateMcpServer(String namespaceId, boolean isPublish,
        McpServerBasicInfo serverSpecification,
        McpToolSpecification toolSpecification, McpEndpointSpec endpointSpecification,
        boolean overrideExisting) throws NacosException;
    
    /**
     * Delete existed mcp server.
     *
     * @param namespaceId namespace id of mcp server
     * @param mcpId       id of the mcp server
     * @param version     version of the mcp server
     * @param mcpName     name of mcp server
     * @throws NacosException any exception during handling
     */
    void deleteMcpServer(String namespaceId, String mcpName, String mcpId, String version)
        throws NacosException;
    
    /**
     * Page standard lifecycle Version summaries.
     */
    Page<McpLifecycleVersionSummary> listLifecycleVersions(String namespaceId, String mcpName,
        String status, int pageNo, int pageSize) throws NacosException;
    
    /**
     * Read one exact standard lifecycle Version.
     */
    McpLifecycleVersionDetail getLifecycleVersion(String namespaceId, String mcpName,
        String version) throws NacosException;
    
    /**
     * Create one standard MCP draft.
     */
    McpLifecycleVersionDetail createLifecycleDraft(String namespaceId,
        McpServerBasicInfo serverSpecification, McpToolSpecification toolSpecification,
        McpResourceSpecification resourceSpecification,
        McpEndpointSpec endpointSpecification) throws NacosException;
    
    /**
     * Replace one exact standard MCP draft.
     */
    McpLifecycleVersionDetail updateLifecycleDraft(String namespaceId,
        McpServerBasicInfo serverSpecification, McpToolSpecification toolSpecification,
        McpResourceSpecification resourceSpecification,
        McpEndpointSpec endpointSpecification) throws NacosException;
    
    /**
     * Delete one exact standard MCP draft.
     */
    void deleteLifecycleDraft(String namespaceId, String mcpName, String version)
        throws NacosException;
    
    /**
     * Submit one standard MCP Version.
     */
    McpLifecycleVersionSummary submitLifecycleVersion(String namespaceId, String mcpName,
        String version) throws NacosException;
    
    /**
     * Publish one standard MCP Version.
     */
    McpLifecycleVersionSummary publishLifecycleVersion(String namespaceId, String mcpName,
        String version) throws NacosException;
    
    /**
     * Force-publish one standard MCP Version.
     */
    McpLifecycleVersionSummary forcePublishLifecycleVersion(String namespaceId, String mcpName,
        String version) throws NacosException;
    
    /**
     * Return one reviewed standard MCP Version to draft.
     */
    McpLifecycleVersionSummary redraftLifecycleVersion(String namespaceId, String mcpName,
        String version) throws NacosException;
    
    /**
     * Bring one standard MCP Version online.
     */
    McpLifecycleVersionSummary onlineLifecycleVersion(String namespaceId, String mcpName,
        String version) throws NacosException;
    
    /**
     * Take one standard MCP Version offline.
     */
    McpLifecycleVersionSummary offlineLifecycleVersion(String namespaceId, String mcpName,
        String version) throws NacosException;
    
    /**
     * Replace custom MCP labels.
     */
    Map<String, String> updateLifecycleLabels(String namespaceId, String mcpName,
        Map<String, String> labels) throws NacosException;
    
    /**
     * Validate MCP server import request.
     *
     * @param namespaceId namespace id for mcp servers
     * @param request     import request containing data and settings
     * @return validation result with details about potential issues
     * @throws NacosException any exception during validation
     * @deprecated use the unified AI resource import validation API instead. Planned for removal
     *     in Nacos 3.4.0.
     */
    @Deprecated
    McpServerImportValidationResult validateImport(String namespaceId,
        McpServerImportRequest request) throws NacosException;
    
    /**
     * Execute MCP server import operation.
     *
     * @param namespaceId namespace id for mcp servers
     * @param request     import request containing data and settings
     * @return import response with results and statistics
     * @throws NacosException any exception during import execution
     * @deprecated use the unified AI resource import execute API instead. Planned for removal in
     *     Nacos 3.4.0.
     */
    @Deprecated
    McpServerImportResponse executeImport(String namespaceId, McpServerImportRequest request)
        throws NacosException;
}
