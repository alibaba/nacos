package com.alibaba.nacos.ai.index;

import com.alibaba.nacos.ai.constant.Constants;
import com.alibaba.nacos.ai.model.mcp.McpServerIndexData;
import com.alibaba.nacos.api.ai.model.mcp.McpServerDetailInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpServerVersionInfo;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.api.model.response.Namespace;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.config.server.Config;
import com.alibaba.nacos.config.server.model.ConfigInfo;
import com.alibaba.nacos.config.server.service.ConfigDetailService;
import com.alibaba.nacos.core.service.NamespaceOperationService;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class McpServerMemoryIndex implements McpServerIndex {
    
    private NamespaceOperationService namespaceOperationService;
    
    private ConfigDetailService configDetailService;
    
    private Map<String, McpServerIndexData> id2InfoCache;
    
    public McpServerMemoryIndex(NamespaceOperationService namespaceOperationService, ConfigDetailService configDetailService) {
        this.id2InfoCache = new ConcurrentHashMap<>();
        this.namespaceOperationService = namespaceOperationService;
        this.configDetailService = configDetailService;
        initIndex();
    }
    
    public void initIndex() {
        List<Namespace> namespaceList = namespaceOperationService.getNamespaceList();
        for (Namespace namespace : namespaceList) {
            Page<ConfigInfo> configInfoPage = configDetailService.findConfigInfoPage("blur", 1, 10000, 
                    "", Constants.MCP_SERVER_VERSIONS_GROUP, namespace.getNamespace(), Collections.emptyMap());
            for (ConfigInfo pageItem : configInfoPage.getPageItems()) {
                McpServerVersionInfo versionInfo = JacksonUtils.toObj(pageItem.getContent(), McpServerVersionInfo.class);
                McpServerIndexData data = new McpServerIndexData();
                data.setName(versionInfo.getName());
                data.setNamespaceId(namespace.getNamespace());
                data.setId(versionInfo.getId());
                id2InfoCache.put(versionInfo.getId(), data);
            }
        }
        System.out.println("Init index " + JacksonUtils.toJson(id2InfoCache));
    }
    
    /**
     * Search Mcp by name
     *
     * @param name mcp server name
     * @return MCP Server Summery
     */
    @Override
    public Page<McpServerIndexData> searchMcpServerByName(String namespaceId, String name,String search, int offset, int limit) {
        List<McpServerIndexData> allServers = id2InfoCache.values().stream().filter((indexData) -> {
            if (StringUtils.isNotEmpty(namespaceId)) {
                return namespaceId.equals(indexData.getNamespaceId());
            }
            return true;
        }).filter((indexData) -> {
            if ("blur".equals(search)) {
                if (StringUtils.isEmpty(name)) {
                    return true;
                }
                return indexData.getName().contains(name);
            } else {
                return indexData.getName().equals(name);
            }
        }).sorted(Comparator.comparing(McpServerIndexData::getId)).toList();
        
        Page<McpServerIndexData> result = new Page<>();
        int end = Math.min(limit, allServers.size());
        if (offset <= allServers.size()) {
            result.setPageItems(allServers.subList(offset, end));
            result.setTotalCount(allServers.size());
            result.setPageNumber(offset / limit + 1);
            result.setPagesAvailable(allServers.size() / limit + 1);
        }
        return result;
    }
    

    @Override
    public McpServerIndexData getMcpServerById(String id) {
        return id2InfoCache.get(id);
    }

    @Override
    public boolean addIndex(String id, McpServerIndexData data) {
        System.out.println("Add index " + JacksonUtils.toJson(data));
        return id2InfoCache.putIfAbsent(id, data) == null;
    }

    @Override
    public void updateIndex(String id, McpServerIndexData data) {
        id2InfoCache.put(id, data);
    }

    @Override
    public void deleteIndex(String id) {
        id2InfoCache.remove(id);
    }
}
