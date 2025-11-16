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

import java.util.List;

import com.alibaba.nacos.ai.constant.Constants;
import com.alibaba.nacos.ai.model.mcp.McpServerIndexData;
import com.alibaba.nacos.ai.utils.McpConfigUtils;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.common.utils.CollectionUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.config.server.service.ConfigDetailService;
import com.alibaba.nacos.config.server.service.query.ConfigQueryChainService;
import com.alibaba.nacos.config.server.service.query.model.ConfigQueryChainRequest;
import com.alibaba.nacos.config.server.service.query.model.ConfigQueryChainResponse;
import com.alibaba.nacos.core.service.NamespaceOperationService;

/**
 * Plain Mcp server index implementation. This is empty index implementation so the performance is not well. this should
 * be implemented by memory index or db index.
 *
 * @author xinluo
 */
public class PlainMcpServerIndex extends AbstractMcpServerIndex {
    
    private final ConfigQueryChainService configQueryChainService;
    
    public PlainMcpServerIndex(NamespaceOperationService namespaceOperationService,
            ConfigDetailService configDetailService, ConfigQueryChainService configQueryChainService) {
        super(namespaceOperationService, configDetailService);
        this.configQueryChainService = configQueryChainService;
    }
    
    /**
     * Get mcp server by id.
     *
     * @param id mcp server id
     * @return {@link McpServerIndexData} return null if server not found
     */
    @Override
    public McpServerIndexData getMcpServerById(String id) {
        if (StringUtils.isEmpty(id)) {
            return null;
        }
        
        List<String> namespaceList = fetchOrderedNamespaceList();
        for (String namespaceId : namespaceList) {
            McpServerIndexData result = getMcpServerById(namespaceId, id);
            if (result != null) {
                return result;
            }
        }
        return null;
    }
    
    public McpServerIndexData getMcpServerById(String namespaceId, String id) {
        ConfigQueryChainRequest request = buildConfigQueryChainRequest(namespaceId, id);
        ConfigQueryChainResponse response = configQueryChainService.handle(request);
        if (McpConfigUtils.isConfigFound(response.getStatus())) {
            return McpServerIndexData.newIndexData(id, namespaceId);
        }
        return null;
    }
    
    private ConfigQueryChainRequest buildConfigQueryChainRequest(String namespaceId, String serverId) {
        ConfigQueryChainRequest request = new ConfigQueryChainRequest();
        request.setTenant(namespaceId);
        request.setDataId(McpConfigUtils.formatServerVersionInfoDataId(serverId));
        request.setGroup(Constants.MCP_SERVER_VERSIONS_GROUP);
        return request;
    }
    
    /**
     * Get mcp server by namespaceId and servername.
     * If namespaceId is empty, we search all namespaces and return the first found server.
     * @param namespaceId namespaceId
     * @param name        servername
     * @return {@link McpServerIndexData}
     */
    @Override
    public McpServerIndexData getMcpServerByName(String namespaceId, String name) {
        if (StringUtils.isEmpty(namespaceId)) {
            return getFirstMcpServerByName(name);
        }

        Page<McpServerIndexData> indexDataPage = searchMcpServerByNameWithPage(namespaceId, name,
                Constants.MCP_LIST_SEARCH_ACCURATE, 1, 1);
        if (CollectionUtils.isNotEmpty(indexDataPage.getPageItems())) {
            return indexDataPage.getPageItems().get(0);
        }
        return null;
    }
    
    /**
     * Remove cache entry by namespace ID and MCP server name. This is a no-op implementation since PlainMcpServerIndex
     * doesn't use cache.
     *
     * @param namespaceId namespace ID
     * @param mcpName     MCP server name
     */
    @Override
    public void removeMcpServerByName(String namespaceId, String mcpName) {
        // No-op implementation since PlainMcpServerIndex doesn't use cache
    }
    
    /**
     * Remove cache entry by MCP server ID. This is a no-op implementation since PlainMcpServerIndex doesn't use cache.
     *
     * @param mcpId MCP server ID
     */
    @Override
    public void removeMcpServerById(String mcpId) {
        // No-op implementation since PlainMcpServerIndex doesn't use cache
    }

    @Override
    protected void afterSearch(List<McpServerIndexData> searchResult, String name) {
    }
}
