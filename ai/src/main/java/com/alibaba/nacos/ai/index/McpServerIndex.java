package com.alibaba.nacos.ai.index;

import com.alibaba.nacos.ai.model.mcp.McpServerIndexData;
import com.alibaba.nacos.api.model.Page;

public interface McpServerIndex {

    /**
     * Search Mcp by name
     * @param name mcp server name
     * @return MCP Server Summery
     */
    Page<McpServerIndexData> searchMcpServerByName(String namespaceId, String name, String search, int offset, int limit);

    McpServerIndexData getMcpServerById(String id);
    
    
    boolean addIndex(String id, McpServerIndexData data);
    
    void updateIndex(String id, McpServerIndexData data);
    
    void deleteIndex(String id);
}
