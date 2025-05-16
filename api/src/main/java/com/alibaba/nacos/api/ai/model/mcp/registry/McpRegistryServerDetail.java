package com.alibaba.nacos.api.ai.model.mcp.registry;

import java.util.List;

public class McpRegistryServerDetail extends McpRegistryServer {
    private List<Remote> remotes;

    
    public List<Remote> getRemotes() {
        return remotes;
    }

    public void setRemotes(List<Remote> remotes) {
        this.remotes = remotes;
    }
}
