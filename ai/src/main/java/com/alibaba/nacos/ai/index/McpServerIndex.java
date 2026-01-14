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

package com.alibaba.nacos.ai.index;

import com.alibaba.nacos.ai.model.mcp.McpServerIndexData;
import com.alibaba.nacos.api.model.Page;

/**
 * Server info index interface. We should know the relation between the mcp server id and namespaceId + mcpServerName.
 *
 * @author xinluo
 */
public interface McpServerIndex {
    
    /**
     * Search Mcp server by name and namespaceId with pagination.
     *
     * @param namespaceId namespace ID
     * @param name        mcp server name
     * @param search      search mode
     * @param pageNo      page number
     * @param limit       page size limit
     * @return MCP Server Index Data page
     */
    Page<McpServerIndexData> searchMcpServerByNameWithPage(String namespaceId, String name, String search, int pageNo,
            int limit);
    /**
     * Get mcp server by id.
     *
     * @param id mcp server id
     * @return {@link McpServerIndexData}
     */
    McpServerIndexData getMcpServerById(String id);
    
    /**
     * Get mcp server by namespaceId and servername.
     *
     * @param namespaceId namespaceId
     * @param name        servername
     * @return {@link McpServerIndexData}
     */
    McpServerIndexData getMcpServerByName(String namespaceId, String name);
    
    /**
     * Remove cache entry by namespace ID and MCP server name.
     *
     * @param namespaceId namespace ID
     * @param mcpName     MCP server name
     */
    void removeMcpServerByName(String namespaceId, String mcpName);
    
    /**
     * Remove cache entry by MCP server ID.
     *
     * @param mcpId MCP server ID
     */
    void removeMcpServerById(String mcpId);
}
