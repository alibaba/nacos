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

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

import com.alibaba.nacos.ai.constant.Constants;
import com.alibaba.nacos.ai.model.mcp.McpServerIndexData;
import com.alibaba.nacos.ai.utils.McpConfigUtils;
import com.alibaba.nacos.api.ai.model.mcp.McpServerVersionInfo;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.api.model.response.Namespace;
import com.alibaba.nacos.api.utils.StringUtils;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.config.server.model.ConfigInfo;
import com.alibaba.nacos.config.server.service.ConfigDetailService;
import com.alibaba.nacos.core.service.NamespaceOperationService;

/**
 * Abstract base class for MCP server index implementations.
 *
 * @author xinluo
 */
public abstract class AbstractMcpServerIndex implements McpServerIndex {

    private final NamespaceOperationService namespaceOperationService;

    protected final ConfigDetailService configDetailService;

    public AbstractMcpServerIndex(NamespaceOperationService namespaceOperationService, ConfigDetailService configDetailService) {
        this.namespaceOperationService = namespaceOperationService;
        this.configDetailService = configDetailService;
    }

    protected List<String> fetchOrderedNamespaceList() {
        return namespaceOperationService.getNamespaceList().stream()
                .sorted(Comparator.comparing(Namespace::getNamespace)).map(Namespace::getNamespace).toList();
    }

    protected McpServerIndexData getFirstMcpServerByName(String name) {
        return fetchOrderedNamespaceList()
            .stream()
            .filter(namespaceId -> !StringUtils.isEmpty(namespaceId))
            .map(namespaceId -> getMcpServerByName(namespaceId, name))
            .filter(index -> Objects.nonNull(index))
            .findFirst()
            .orElse(null);
    }

    @Override
    public Page<McpServerIndexData> searchMcpServerByNameWithPage(String namespaceId, String name, String search,
            int pageNo, int limit) {
        Page<ConfigInfo> serverInfos = searchMcpServers(namespaceId, name, search, pageNo, limit);
        List<McpServerIndexData> indexDataList = serverInfos.getPageItems().stream()
                .map((configInfo) -> {
                    configInfo.setTenant(namespaceId);
                    return configInfo;
                })
                .map(this::mapToMcpServerVersionInfo)
                .map(this::mcpToIndexAndUpdateToCache)
                .toList();
        Page<McpServerIndexData> result = new Page<>();
        result.setPageItems(indexDataList);
        result.setTotalCount(serverInfos.getTotalCount());
        result.setPagesAvailable((int) Math.ceil((double) serverInfos.getTotalCount() / (double) limit));
        result.setPageNumber(pageNo);
        return result;
    }

    /**
     * Callback after search operation. Subclasses can implement this to perform additional operations.
     *
     * @param searchResult the search results
     * @param name the search name
     */
    protected abstract void afterSearch(McpServerIndexData searchResult, String name);

    /**
     * Search MCP servers.
     */
    protected Page<ConfigInfo> searchMcpServers(String namespace, String serverName, String search, int pageNo,
            int limit) {
        HashMap<String, Object> advanceInfo = new HashMap<>(1);
        if (Objects.isNull(serverName)) {
            serverName = "";
        }
        String dataId = Constants.ALL_PATTERN;
        if (Constants.MCP_LIST_SEARCH_BLUR.equals(search) || serverName.isEmpty()) {
            String nameTag = McpConfigUtils.formatServerNameTagBlurSearchValue(serverName);
            advanceInfo.put(Constants.CONFIG_TAGS_NAME, nameTag);
            search = Constants.MCP_LIST_SEARCH_BLUR;
        } else {
            advanceInfo.put(Constants.CONFIG_TAGS_NAME,
                    McpConfigUtils.formatServerNameTagAccurateSearchValue(serverName));
            dataId = null;
        }
        return configDetailService.findConfigInfoPage(search, pageNo, limit, dataId,
                Constants.MCP_SERVER_VERSIONS_GROUP, namespace, advanceInfo);
    }

    protected McpServerVersionInfo mapToMcpServerVersionInfo(ConfigInfo configInfo) {
        McpServerVersionInfo obj = JacksonUtils.toObj(configInfo.getContent(), McpServerVersionInfo.class);
        obj.setNamespaceId(configInfo.getTenant());
        return obj;
    }

    protected McpServerIndexData mcpToIndexAndUpdateToCache(McpServerVersionInfo versionInfo) {
        McpServerIndexData data = new McpServerIndexData();
        data.setId(versionInfo.getId());
        data.setNamespaceId(versionInfo.getNamespaceId());
        afterSearch(data, versionInfo.getName());
        return data;
    }
}
