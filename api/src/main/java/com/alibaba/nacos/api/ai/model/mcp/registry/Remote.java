package com.alibaba.nacos.api.ai.model.mcp.registry;

import java.util.List;

public class Remote {
    private String transport_type;
    private String url;

    public String getTransport_type() {
        return transport_type;
    }

    public void setTransport_type(String transport_type) {
        this.transport_type = transport_type;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }
}
