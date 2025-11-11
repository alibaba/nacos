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

package com.alibaba.nacos.api.ai.model.mcp;

import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.api.ai.model.mcp.registry.Icon;
import com.alibaba.nacos.api.ai.model.mcp.registry.Package;
import com.alibaba.nacos.api.ai.model.mcp.registry.Repository;
import com.alibaba.nacos.api.ai.model.mcp.registry.ServerVersionDetail;

import java.util.List;
import java.util.Map;

/**
 * AI Mcp server spec in nacos.
 *
 * @author xiweng.yy
 */
public class McpServerBasicInfo {
    
    private String namespaceId;
    
    private String id;
    
    private String name;
    
    /**
     * It should be {@link AiConstants.Mcp#MCP_PROTOCOL_STDIO}, {@link AiConstants.Mcp#MCP_PROTOCOL_SSE}, 
     * {@link AiConstants.Mcp#MCP_PROTOCOL_STREAMABLE}, {@link AiConstants.Mcp#MCP_PROTOCOL_HTTP} or {@link AiConstants.Mcp#MCP_PROTOCOL_DUBBO}.
     */
    private String protocol;
    
    private String frontProtocol;
    
    private String description;
    
    private Repository repository;
    
    private List<Package> packages;

    private List<Icon> icons;

    private String websiteUrl;
    
    private ServerVersionDetail versionDetail;
    
    /**
     * Please use {@link #versionDetail} replaced.
     */
    private String version;
    
    /**
     * Should be set when `type` is not {@link AiConstants.Mcp#MCP_PROTOCOL_STDIO}.
     */
    private McpServerRemoteServiceConfig remoteServerConfig;
    
    /**
     * Should be set when `type` is {@link AiConstants.Mcp#MCP_PROTOCOL_STDIO}.
     */
    private Map<String, Object> localServerConfig;
    
    private boolean enabled = true;
    
    /**
     * Current lifecycle status of MCP server, should be one of
     * {@link AiConstants.Mcp#MCP_STATUS_ACTIVE} or {@link AiConstants.Mcp#MCP_STATUS_DEPRECATED}.
     * Default is {@link AiConstants.Mcp#MCP_STATUS_ACTIVE}.
     */
    private String status = AiConstants.Mcp.MCP_STATUS_ACTIVE;
    
    /**
     * Auto discovery capabilities by Nacos. No need to set when create or update Mcp server.
     */
    private List<McpCapability> capabilities;
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public String getProtocol() {
        return protocol;
    }
    
    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    public McpServerRemoteServiceConfig getRemoteServerConfig() {
        return remoteServerConfig;
    }
    
    public void setRemoteServerConfig(McpServerRemoteServiceConfig remoteServerConfig) {
        this.remoteServerConfig = remoteServerConfig;
    }
    
    public Map<String, Object> getLocalServerConfig() {
        return localServerConfig;
    }
    
    public void setLocalServerConfig(Map<String, Object> localServerConfig) {
        this.localServerConfig = localServerConfig;
    }

    public String getFrontProtocol() {
        return frontProtocol;
    }

    public void setFrontProtocol(String frontProtocol) {
        this.frontProtocol = frontProtocol;
    }

    public boolean isEnabled() {
        return enabled;
    }
    
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
    
    public List<McpCapability> getCapabilities() {
        return capabilities;
    }
    
    public void setCapabilities(List<McpCapability> capabilities) {
        this.capabilities = capabilities;
    }

    public ServerVersionDetail getVersionDetail() {
        return versionDetail;
    }

    public void setVersionDetail(ServerVersionDetail versionDetail) {
        this.versionDetail = versionDetail;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Repository getRepository() {
        return repository;
    }

    public void setRepository(Repository repository) {
        this.repository = repository;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public List<Package> getPackages() {
        return packages;
    }

    public void setPackages(List<Package> packages) {
        this.packages = packages;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public List<Icon> getIcons() {
        return icons;
    }

    public void setIcons(List<Icon> icons) {
        this.icons = icons;
    }

    public String getWebsiteUrl() {
        return websiteUrl;
    }

    public void setWebsiteUrl(String websiteUrl) {
        this.websiteUrl = websiteUrl;
    }

    public String getNamespaceId() {
        return namespaceId;
    }

    public void setNamespaceId(String namespaceId) {
        this.namespaceId = namespaceId;
    }
}
