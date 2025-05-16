package com.alibaba.nacos.api.ai.model.mcp.registry;


public class ServerVersionDetail {
    private String version;
    private String release_date;
    private Boolean is_latest;

    public String getRelease_date() {
        return release_date;
    }

    public String getVersion() {
        return version;
    }

    public void setRelease_date(String releaseDate) {
        this.release_date = releaseDate;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public void setIs_latest(Boolean is_latest) {
        this.is_latest = is_latest;
    }

    public Boolean getIs_latest() {
        return is_latest;
    }
}
