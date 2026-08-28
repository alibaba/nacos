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
 * Historical MCP server identity index.
 *
 * <p>This index is retained only for the complete legacy operation strategy while lifecycle
 * reconciliation is in {@code SYNCING}. Lifecycle-managed CRUD, identity resolution, Import,
 * and Search must use canonical AI Resource rows instead.</p>
 *
 * @author xinluo
 * @deprecated compatibility-only during MCP lifecycle reconciliation; planned for removal in
 *     Nacos 4.0.0
 */
@Deprecated
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
    Page<McpServerIndexData> searchMcpServerByNameWithPage(String namespaceId, String name,
        String search, int pageNo,
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
