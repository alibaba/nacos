package com.alibaba.nacos.api.ai.model.mcp.registry;

public class McpRegistryServer {
    private String id;
    private String name;
    private String description;
    private Repository repository;
    private ServerVersionDetail version_detail;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Repository getRepository() {
        return repository;
    }

    public void setRepository(Repository repository) {
        this.repository = repository;
    }

    public ServerVersionDetail getVersion_detail() {
        return version_detail;
    }

    public void setVersion_detail(ServerVersionDetail version_detail) {
        this.version_detail = version_detail;
    }
}
