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

import com.alibaba.nacos.ai.constant.Constants;
import com.alibaba.nacos.ai.model.mcp.McpServerIndexData;
import com.alibaba.nacos.ai.utils.McpConfigUtils;
import com.alibaba.nacos.api.ai.model.mcp.McpServerVersionInfo;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.api.model.response.Namespace;
import com.alibaba.nacos.common.utils.CollectionUtils;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.config.server.model.ConfigInfo;
import com.alibaba.nacos.config.server.service.ConfigDetailService;
import com.alibaba.nacos.config.server.service.query.ConfigQueryChainService;
import com.alibaba.nacos.config.server.service.query.model.ConfigQueryChainRequest;
import com.alibaba.nacos.config.server.service.query.model.ConfigQueryChainResponse;
import com.alibaba.nacos.core.service.NamespaceOperationService;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

/**
 * Plain Mcp server index implementation. This is empty index implementation so the performance is not well. this should
 * be implemented by memory index or db index.
 *
 * @author xinluo
 */
@Service
public class PlainMcpServerIndex implements McpServerIndex {
    
    private final ConfigDetailService configDetailService;
    
    private final NamespaceOperationService namespaceOperationService;
    
    private final ConfigQueryChainService configQueryChainService;
    
    public PlainMcpServerIndex(NamespaceOperationService namespaceOperationService,
            ConfigDetailService configDetailService, ConfigQueryChainService configQueryChainService) {
        this.namespaceOperationService = namespaceOperationService;
        this.configDetailService = configDetailService;
        this.configQueryChainService = configQueryChainService;
    }
    
    /**
     * Search Mcp server by name and namespaceId. If namespaceId is empty, we search all the namespace.
     *
     * @param namespaceId namespaceId empty for all namespaces
     * @param name        mcp server name, empty for all servers
     * @param search      search mode
     * @param offset      offset
     * @param limit       limit
     * @return MCP Server Summery
     */
    @Override
    public Page<McpServerIndexData> searchMcpServerByName(String namespaceId, String name, String search, int offset,
            int limit) {
        return searchMcpServerByName0(namespaceId, name, search, offset, limit);
    }
    
    private Page<McpServerIndexData> searchMcpServerByName0(String namespaceId, String name, String search, int offset,
            int limit) {
        Page<ConfigInfo> serverInfos = searchMcpServers(namespaceId, name, search, limit);
        List<McpServerIndexData> indexDataList = serverInfos.getPageItems().stream().skip(offset)
                .map(this::mapMcpServerVersionConfigToIndexData).toList();
        Page<McpServerIndexData> result = new Page<>();
        result.setPageItems(indexDataList);
        result.setTotalCount(serverInfos.getTotalCount());
        result.setPagesAvailable((int) Math.ceil((double) serverInfos.getTotalCount() / (double) limit));
        result.setPageNumber(offset / limit + 1);
        return result;
    }
    
    private McpServerIndexData mapMcpServerVersionConfigToIndexData(ConfigInfo configInfo) {
        McpServerIndexData data = new McpServerIndexData();
        McpServerVersionInfo versionInfo = JacksonUtils.toObj(configInfo.getContent(), McpServerVersionInfo.class);
        data.setId(versionInfo.getId());
        data.setNamespaceId(configInfo.getTenant());
        return data;
    }
    
    private Page<ConfigInfo> searchMcpServers(String namespace, String serverName, String search, int limit) {
        HashMap<String, Object> advanceInfo = new HashMap<>(1);
        if (Objects.isNull(serverName)) {
            serverName = StringUtils.EMPTY;
        }
        
        String dataId = Constants.ALL_PATTERN;
        if (Constants.MCP_LIST_SEARCH_BLUR.equals(search) || StringUtils.isEmpty(serverName)) {
            String nameTag = McpConfigUtils.formatServerNameTagBlurSearchValue(serverName);
            advanceInfo.put(Constants.CONFIG_TAGS_NAME, nameTag);
            search = Constants.MCP_LIST_SEARCH_BLUR;
        } else {
            advanceInfo.put(Constants.CONFIG_TAGS_NAME,
                    McpConfigUtils.formatServerNameTagAccurateSearchValue(serverName));
            dataId = null;
        }
        
        return configDetailService.findConfigInfoPage(search, 1, limit, dataId, Constants.MCP_SERVER_VERSIONS_GROUP,
                namespace, advanceInfo);
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
     *
     * @param namespaceId namespaceId
     * @param name        servername
     * @return {@link McpServerIndexData}
     */
    @Override
    public McpServerIndexData getMcpServerByName(String namespaceId, String name) {
        Page<McpServerIndexData> indexDataPage = searchMcpServerByName(namespaceId, name,
                Constants.MCP_LIST_SEARCH_ACCURATE, 0, 1);
        if (CollectionUtils.isNotEmpty(indexDataPage.getPageItems())) {
            return indexDataPage.getPageItems().get(0);
        }
        return null;
    }
    
    private List<String> fetchOrderedNamespaceList() {
        return namespaceOperationService.getNamespaceList().stream()
                .sorted(Comparator.comparing(Namespace::getNamespace)).map(Namespace::getNamespace).toList();
    }
}
