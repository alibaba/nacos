package com.alibaba.nacos.api.ai.model.mcp.registry;

import java.util.List;

public class McpRegistryServerList {
    private List<McpRegistryServer> servers;
    private int total_count;
    private String next;

    public List<McpRegistryServer> getServers() {
        return servers;
    }

    public void setServers(List<McpRegistryServer> servers) {
        this.servers = servers;
    }

    public int getTotal_count() {
        return total_count;
    }

    public void setTotal_count(int total_count) {
        this.total_count = total_count;
    }

    public String getNext() {
        return next;
    }

    public void setNext(String next) {
        this.next = next;
    }
}
