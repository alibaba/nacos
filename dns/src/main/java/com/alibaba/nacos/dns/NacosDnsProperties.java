/*
 * Copyright 1999-2025 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alibaba.nacos.dns;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration properties for Nacos DNS server.
 *
 * @author Nacos
 */
@ConfigurationProperties(prefix = "nacos.naming.dns")
public class NacosDnsProperties {
    
    /**
     * Whether DNS server is enabled.
     */
    private boolean enabled = false;
    
    /**
     * DNS server listen port. Default is 5353 to avoid conflict with system DNS port 53.
     */
    private int port = 5353;
    
    /**
     * DNS domain suffix for Nacos services.
     * Example: service-name.group-name.nacos
     */
    private String domainSuffix = "nacos";
    
    /**
     * Default group name when not specified in domain.
     */
    private String defaultGroup = "DEFAULT_GROUP";
    
    /**
     * Default namespace when not specified.
     */
    private String namespace = "";
    
    /**
     * DNS TTL (time to live) for A records in seconds.
     */
    private long ttl = 60;
    
    /**
     * Whether to forward queries that don't match the Nacos domain suffix to upstream DNS servers.
     */
    private boolean forwardEnabled = false;
    
    /**
     * Upstream DNS server addresses for forwarding (e.g., "8.8.8.8", "114.114.114.114").
     */
    private List<String> forwardServers = new ArrayList<>();
    
    /**
     * Timeout in milliseconds for forwarded DNS queries.
     */
    private int forwardTimeoutMs = 3000;
    
    public boolean isEnabled() {
        return enabled;
    }
    
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
    
    public int getPort() {
        return port;
    }
    
    public void setPort(int port) {
        this.port = port;
    }
    
    public String getDomainSuffix() {
        return domainSuffix;
    }
    
    public void setDomainSuffix(String domainSuffix) {
        this.domainSuffix = domainSuffix;
    }
    
    public String getDefaultGroup() {
        return defaultGroup;
    }
    
    public void setDefaultGroup(String defaultGroup) {
        this.defaultGroup = defaultGroup;
    }
    
    public String getNamespace() {
        return namespace;
    }
    
    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }
    
    public long getTtl() {
        return ttl;
    }
    
    public void setTtl(long ttl) {
        this.ttl = ttl;
    }
    
    public boolean isForwardEnabled() {
        return forwardEnabled;
    }
    
    public void setForwardEnabled(boolean forwardEnabled) {
        this.forwardEnabled = forwardEnabled;
    }
    
    public List<String> getForwardServers() {
        return forwardServers;
    }
    
    public void setForwardServers(List<String> forwardServers) {
        this.forwardServers = forwardServers;
    }
    
    public int getForwardTimeoutMs() {
        return forwardTimeoutMs;
    }
    
    public void setForwardTimeoutMs(int forwardTimeoutMs) {
        this.forwardTimeoutMs = forwardTimeoutMs;
    }
}
