package com.alibaba.nacos.api.ai.model.mcp;

import com.alibaba.nacos.api.ai.model.mcp.registry.ServerVersionDetail;

import java.util.List;

public class McpServerVersionInfo extends McpServerBasicInfo {
    private String latestPublishedVersion;
    private List<ServerVersionDetail> versions;

    public String getLatestPublishedVersion() {
        return latestPublishedVersion;
    }

    public void setLatestPublishedVersion(String latestPublishedVersion) {
        this.latestPublishedVersion = latestPublishedVersion;
    }

    public List<ServerVersionDetail> getVersionDetails() {
        return versions;
    }

    public void setVersionDetails(List<ServerVersionDetail> versionDetails) {
        this.versions = versionDetails;
    }
}
