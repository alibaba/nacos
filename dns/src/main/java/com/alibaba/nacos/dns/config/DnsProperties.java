package com.alibaba.nacos.dns.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.io.Serializable;

@Component
@ConfigurationProperties(prefix = "nacos.dns")
public class DnsProperties implements Serializable {

    private int defaultCacheTime = 10;
    private String upstreamServersForDomainSuffixMap;
    private String defaultUpstreamServer;
    private boolean ednsEnabled = false;

    public int getDefaultCacheTime() {
        return defaultCacheTime;
    }

    public void setDefaultCacheTime(int defaultCacheTime) {
        this.defaultCacheTime = defaultCacheTime;
    }

    public String getUpstreamServersForDomainSuffixMap() {
        return upstreamServersForDomainSuffixMap;
    }

    public void setUpstreamServersForDomainSuffixMap(String upstreamServersForDomainSuffixMap) {
        this.upstreamServersForDomainSuffixMap = upstreamServersForDomainSuffixMap;
    }

    public String getDefaultUpstreamServer() {
        return defaultUpstreamServer;
    }

    public void setDefaultUpstreamServer(String defaultUpstreamServer) {
        this.defaultUpstreamServer = defaultUpstreamServer;
    }

    public boolean isEdnsEnabled() {
        return ednsEnabled;
    }

    public void setEdnsEnabled(boolean ednsEnabled) {
        this.ednsEnabled = ednsEnabled;
    }
}
