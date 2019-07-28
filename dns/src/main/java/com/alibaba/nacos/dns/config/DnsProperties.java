package com.alibaba.nacos.dns.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.io.Serializable;


/**
 * @author paderlol
 */
@Component
@ConfigurationProperties(prefix = "nacos.dns")
public class DnsProperties implements Serializable {

    /**
     * @description The Default cache time.
     */
    private int defaultCacheTime = 10;
    /**
     * @description The Upstream servers for domain suffix map.
     */
    private String upstreamServersForDomainSuffixMap="null";
    /**
     * @description The Default upstream server.
     */
    private String defaultUpstreamServer="null";
    /**
     * @description The Edns enabled.
     */
    private boolean ednsEnabled = false;

    /**
     * @return the default cache time
     * @description default cache time TODO(do something).
     * @author zhanglong
     * @date 2019年07月28日, 16:14:53
     */
    public int getDefaultCacheTime() {
        return defaultCacheTime;
    }

    /**
     * @param defaultCacheTime the default cache time
     * @description Sets default cache time TODO(do something).
     * @author zhanglong
     * @date 2019年07月28日, 16:14:53
     */
    public void setDefaultCacheTime(int defaultCacheTime) {
        this.defaultCacheTime = defaultCacheTime;
    }

    /**
     * @return the upstream servers for domain suffix map
     * @description upstream servers for domain suffix map TODO(do something).
     * @author zhanglong
     * @date 2019年07月28日, 16:14:53
     */
    public String getUpstreamServersForDomainSuffixMap() {
        return upstreamServersForDomainSuffixMap;
    }

    /**
     * @param upstreamServersForDomainSuffixMap the upstream servers for domain suffix map
     * @description Sets upstream servers for domain suffix map TODO(do something).
     * @author zhanglong
     * @date 2019年07月28日, 16:14:53
     */
    public void setUpstreamServersForDomainSuffixMap(String upstreamServersForDomainSuffixMap) {
        this.upstreamServersForDomainSuffixMap = upstreamServersForDomainSuffixMap;
    }

    /**
     * @return the default upstream server
     * @description default upstream server TODO(do something).
     * @author zhanglong
     * @date 2019年07月28日, 16:14:53
     */
    public String getDefaultUpstreamServer() {
        return defaultUpstreamServer;
    }

    /**
     * @param defaultUpstreamServer the default upstream server
     * @description Sets default upstream server TODO(do something).
     * @author zhanglong
     * @date 2019年07月28日, 16:14:53
     */
    public void setDefaultUpstreamServer(String defaultUpstreamServer) {
        this.defaultUpstreamServer = defaultUpstreamServer;
    }

    /**
     * Is edns enabled boolean.
     *
     * @return the boolean
     * @description TODO(do something)
     * @author zhanglong
     * @date 2019年07月28日, 16:14:53
     */
    public boolean isEdnsEnabled() {
        return ednsEnabled;
    }

    /**
     * @param ednsEnabled the edns enabled
     * @description Sets edns enabled TODO(do something).
     * @author zhanglong
     * @date 2019年07月28日, 16:14:53
     */
    public void setEdnsEnabled(boolean ednsEnabled) {
        this.ednsEnabled = ednsEnabled;
    }
}
