/*
 * Copyright 1999-2021 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.ai.service;

import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.api.ai.model.mcp.McpServerDetailInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpServerRemoteServiceConfig;
import com.alibaba.nacos.api.ai.model.mcp.McpTool;
import com.alibaba.nacos.api.ai.model.mcp.McpToolSpecification;
import com.alibaba.nacos.api.ai.model.mcp.registry.McpRegistryServer;
import com.alibaba.nacos.api.ai.model.mcp.registry.McpRegistryServerDetail;
import com.alibaba.nacos.api.ai.model.mcp.registry.McpRegistryServerList;
import com.alibaba.nacos.api.ai.model.mcp.registry.Package;
import com.alibaba.nacos.api.ai.model.mcp.registry.Argument;
import com.alibaba.nacos.api.ai.model.mcp.registry.NamedArgument;
import com.alibaba.nacos.api.ai.model.mcp.registry.PositionalArgument;
import com.alibaba.nacos.api.ai.model.mcp.registry.Remote;
import com.alibaba.nacos.api.ai.model.mcp.registry.Repository;
import com.alibaba.nacos.api.ai.model.mcp.registry.ServerVersionDetail;
import com.alibaba.nacos.common.utils.StringUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * MCP Server Transform Service.
 *
 * @author nacos
 */
@Service
public class McpServerTransformService {
    
    private static final String SERVERS_FIELD = "servers";
    
    private static final String TOTAL_COUNT_FIELD = "total_count";
    
    private static final String REPOSITORY_FIELD = "repository";
    
    private static final String VERSION_DETAIL_FIELD = "version_detail";
    
    private static final String COMMAND_FIELD = "command";
    
    private static final String URL_FIELD = "url";
    
    private static final String ENDPOINT_FIELD = "endpoint";
    
    private static final String BASE_URL_FIELD = "baseUrl";
    
    private static final String SERVICE_FIELD = "service";
    
    private static final String SERVICE_NAME_FIELD = "serviceName";
    
    private static final String HTTP_PREFIX = "http://";
    
    private static final String HTTPS_PREFIX = "https://";
    
    private static final String PROTOCOL_JAVASCRIPT = "javascript:";
    
    private static final String PROTOCOL_DATA = "data:";
    
    private static final String PROTOCOL_FILE = "file:";
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    /**
     * Transform external format to Nacos MCP server format.
     *
     * @param importData import data string
     * @param importType import type (file, url, json)
     * @return list of MCP server detail info
     * @throws Exception if transformation fails
     */
    public List<McpServerDetailInfo> transformToNacosFormat(String importData, String importType) throws Exception {
        List<McpServerDetailInfo> servers = new ArrayList<>();
        
        switch (importType) {
            case "file":
            case "json":
                servers = parseJsonData(importData);
                break;
            case "url":
                servers = parseUrlData(importData);
                break;
            default:
                throw new IllegalArgumentException("Unsupported import type: " + importType);
        }
        
        // Generate IDs for servers without IDs
        servers.forEach(server -> {
            if (StringUtils.isBlank(server.getId())) {
                server.setId(generateServerId(server.getName()));
            }
        });
        
        return servers;
    }
    
    /**
     * Parse JSON data to MCP servers.
     *
     * @param jsonData JSON data string
     * @return list of MCP server detail info
     * @throws Exception if parsing fails
     */
    private List<McpServerDetailInfo> parseJsonData(String jsonData) throws Exception {
        JsonNode rootNode = objectMapper.readTree(jsonData);
        
        // First, try to parse as MCP Registry format
        if (isMcpRegistryFormat(rootNode)) {
            return parseRegistryData(jsonData);
        }
        
        // Fallback to original parsing logic
        List<McpServerDetailInfo> servers = new ArrayList<>();
        
        if (rootNode.isArray()) {
            // Direct array of servers
            for (JsonNode serverNode : rootNode) {
                McpServerDetailInfo server = transformJsonNodeToServer(serverNode);
                if (server != null) {
                    servers.add(server);
                }
            }
        } else if (rootNode.has(SERVERS_FIELD)) {
            // Wrapped in servers field
            JsonNode serversNode = rootNode.get(SERVERS_FIELD);
            if (serversNode.isArray()) {
                for (JsonNode serverNode : serversNode) {
                    McpServerDetailInfo server = transformJsonNodeToServer(serverNode);
                    if (server != null) {
                        servers.add(server);
                    }
                }
            }
        } else {
            // Single server object
            McpServerDetailInfo server = transformJsonNodeToServer(rootNode);
            if (server != null) {
                servers.add(server);
            }
        }
        
        return servers;
    }
    
    /**
     * Check if the JSON data is in MCP Registry format.
     *
     * @param rootNode JSON root node
     * @return true if it's MCP Registry format
     */
    private boolean isMcpRegistryFormat(JsonNode rootNode) {
        // Check for MCP Registry API response structure
        if (rootNode.has(SERVERS_FIELD) && rootNode.has(TOTAL_COUNT_FIELD)) {
            return true;
        }
        
        // Check for single server with registry-specific fields
        if (rootNode.has(REPOSITORY_FIELD) && rootNode.has(VERSION_DETAIL_FIELD)) {
            return true;
        }
        
        // Check for array of registry servers
        if (rootNode.isArray() && rootNode.size() > 0) {
            JsonNode firstServer = rootNode.get(0);
            return firstServer.has(REPOSITORY_FIELD) && firstServer.has(VERSION_DETAIL_FIELD);
        }
        
        return false;
    }
    
    /**
     * Parse MCP Registry JSON data to MCP servers.
     *
     * @param jsonData MCP Registry JSON data string
     * @return list of MCP server detail info
     * @throws Exception if parsing fails
     */
    private List<McpServerDetailInfo> parseRegistryData(String jsonData) throws Exception {
        JsonNode rootNode = objectMapper.readTree(jsonData);
        List<McpServerDetailInfo> servers = new ArrayList<>();
        
        if (rootNode.has(SERVERS_FIELD) && rootNode.has(TOTAL_COUNT_FIELD)) {
            // Parse as McpRegistryServerList
            McpRegistryServerList registryList = objectMapper.treeToValue(rootNode, McpRegistryServerList.class);
            if (registryList.getServers() != null) {
                for (McpRegistryServer registryServer : registryList.getServers()) {
                    McpServerDetailInfo server = transformRegistryServerToNacos(registryServer);
                    if (server != null) {
                        servers.add(server);
                    }
                }
            }
        } else if (rootNode.isArray()) {
            // Array of registry servers
            for (JsonNode serverNode : rootNode) {
                McpRegistryServer registryServer = objectMapper.treeToValue(serverNode, McpRegistryServer.class);
                McpServerDetailInfo server = transformRegistryServerToNacos(registryServer);
                if (server != null) {
                    servers.add(server);
                }
            }
        } else {
            // Single registry server
            McpRegistryServer registryServer = objectMapper.treeToValue(rootNode, McpRegistryServer.class);
            McpServerDetailInfo server = transformRegistryServerToNacos(registryServer);
            if (server != null) {
                servers.add(server);
            }
        }
        
        return servers;
    }
    
    /**
     * Transform MCP Registry Server to Nacos MCP Server format.
     *
     * @param registryServer MCP Registry Server
     * @return Nacos MCP Server Detail Info
     */
    private McpServerDetailInfo transformRegistryServerToNacos(McpRegistryServer registryServer) {
        if (registryServer == null) {
            return null;
        }
        
        try {
            McpServerDetailInfo server = new McpServerDetailInfo();
            
            // Basic server info from registry
            server.setId(registryServer.getId());
            server.setName(registryServer.getName());
            server.setDescription(registryServer.getDescription());
            
            // Repository information
            if (registryServer.getRepository() != null) {
                server.setRepository(registryServer.getRepository());
            }
            
            // Version information
            if (registryServer.getVersion_detail() != null) {
                server.setVersionDetail(registryServer.getVersion_detail());
            }
            
            // Handle packages to infer protocol and remote configuration
            if (registryServer.getPackages() != null && !registryServer.getPackages().isEmpty()) {
                Package firstPackage = registryServer.getPackages().get(0);
                
                // Infer protocol from package registry type
                String protocol = inferProtocolFromPackage(firstPackage);
                server.setProtocol(protocol);
                server.setFrontProtocol(protocol);
                
                // Set up remote service config based on package info
                McpServerRemoteServiceConfig remoteConfig = new McpServerRemoteServiceConfig();
                configureRemoteServiceFromPackage(remoteConfig, firstPackage, protocol);
                server.setRemoteServerConfig(remoteConfig);
            }
            
            // Handle remotes for detailed registry servers (backward compatibility)
            if (registryServer instanceof McpRegistryServerDetail) {
                McpRegistryServerDetail detailServer = (McpRegistryServerDetail) registryServer;
                if (detailServer.getRemotes() != null && !detailServer.getRemotes().isEmpty() 
                        && server.getRemoteServerConfig() == null) {
                    Remote firstRemote = detailServer.getRemotes().get(0);
                    
                    // Infer protocol from remote configuration
                    String protocol = inferProtocolFromRemote(firstRemote);
                    server.setProtocol(protocol);
                    server.setFrontProtocol(protocol);
                    
                    // Set up remote service config based on protocol
                    McpServerRemoteServiceConfig remoteConfig = new McpServerRemoteServiceConfig();
                    configureRemoteService(remoteConfig, firstRemote, protocol);
                    server.setRemoteServerConfig(remoteConfig);
                }
            }
            
            // Default protocol if not set
            if (StringUtils.isBlank(server.getProtocol())) {
                server.setProtocol(AiConstants.Mcp.MCP_PROTOCOL_STDIO);
                server.setFrontProtocol(AiConstants.Mcp.MCP_PROTOCOL_STDIO);
            }
            
            return server;
        } catch (Exception e) {
            // Log error and skip invalid server
            return null;
        }
    }
    
    /**
     * Infer protocol from remote configuration.
     *
     * @param remote Remote configuration
     * @return inferred protocol
     */
    private String inferProtocolFromRemote(Remote remote) {
        if (remote == null) {
            return AiConstants.Mcp.MCP_PROTOCOL_STDIO;
        }
        
        String transportType = remote.getTransportType();
        if (StringUtils.isNotBlank(transportType)) {
            switch (transportType.toLowerCase()) {
                case "http":
                case "https":
                    return AiConstants.Mcp.MCP_PROTOCOL_HTTP;
                case "stdio":
                    return AiConstants.Mcp.MCP_PROTOCOL_STDIO;
                case "dubbo":
                    return AiConstants.Mcp.MCP_PROTOCOL_DUBBO;
                default:
                    break;
            }
        }
        
        // Infer from URL if transport type is not clear
        String url = remote.getUrl();
        if (StringUtils.isNotBlank(url)) {
            if (url.startsWith(HTTP_PREFIX) || url.startsWith(HTTPS_PREFIX)) {
                return AiConstants.Mcp.MCP_PROTOCOL_HTTP;
            }
        }
        
        // Default to stdio
        return AiConstants.Mcp.MCP_PROTOCOL_STDIO;
    }
    
    /**
     * Configure remote service based on protocol and remote info.
     *
     * @param remoteConfig Remote service config to configure
     * @param remote Remote information
     * @param protocol Protocol type
     */
    private void configureRemoteService(McpServerRemoteServiceConfig remoteConfig, Remote remote, String protocol) {
        if (remote == null || remoteConfig == null) {
            return;
        }
        
        String url = remote.getUrl();
        if (StringUtils.isNotBlank(url)) {
            // Validate URL security and validity
            if (!isValidUrl(url, protocol)) {
                throw new IllegalArgumentException("Invalid URL: " + url);
            }
            switch (protocol) {
                case AiConstants.Mcp.MCP_PROTOCOL_HTTP:
                    // For HTTP protocol, set the base URL
                    remoteConfig.setExportPath(url);
                    break;
                case AiConstants.Mcp.MCP_PROTOCOL_STDIO:
                    // For stdio protocol, treat URL as command or export path
                    remoteConfig.setExportPath(url);
                    break;
                case AiConstants.Mcp.MCP_PROTOCOL_DUBBO:
                    // For Dubbo protocol, configure service details
                    remoteConfig.setExportPath(url);
                    break;
                default:
                    remoteConfig.setExportPath(url);
                    break;
            }
        }
    }
    
    /**
     * Infer protocol from MCP package information.
     *
     * @param mcpPackage MCP package information
     * @return inferred protocol
     */
    private String inferProtocolFromPackage(Package mcpPackage) {
        if (mcpPackage == null) {
            return AiConstants.Mcp.MCP_PROTOCOL_STDIO;
        }
        
        String registryName = mcpPackage.getRegistryName();
        if (StringUtils.isNotBlank(registryName)) {
            switch (registryName.toLowerCase()) {
                case "npm":
                case "pypi":
                    // Package managers typically use stdio protocol
                    return AiConstants.Mcp.MCP_PROTOCOL_STDIO;
                case "docker":
                    // Docker containers might use HTTP
                    return AiConstants.Mcp.MCP_PROTOCOL_HTTP;
                default:
                    break;
            }
        }
        
        // Default to stdio for package-based servers
        return AiConstants.Mcp.MCP_PROTOCOL_STDIO;
    }
    
    /**
     * Configure remote service from package information.
     *
     * @param remoteConfig Remote service config to configure
     * @param mcpPackage MCP package information
     * @param protocol Protocol type
     */
    private void configureRemoteServiceFromPackage(McpServerRemoteServiceConfig remoteConfig, 
            Package mcpPackage, String protocol) {
        if (mcpPackage == null || remoteConfig == null) {
            return;
        }
        
        String registryName = mcpPackage.getRegistryName();
        String packageName = mcpPackage.getName();
        
        if (StringUtils.isNotBlank(registryName) && StringUtils.isNotBlank(packageName)) {
            String command = buildPackageCommand(registryName, packageName, mcpPackage);
            remoteConfig.setExportPath(command);
        }
    }
    
    /**
     * Build command from package information.
     *
     * @param registryName Package registry name
     * @param packageName Package name
     * @param mcpPackage Package details
     * @return command string
     */
    private String buildPackageCommand(String registryName, String packageName, Package mcpPackage) {
        StringBuilder command = new StringBuilder();
        
        switch (registryName.toLowerCase()) {
            case "npm":
                command.append("npx ").append(packageName);
                break;
            case "pypi":
                command.append("python -m ").append(packageName);
                break;
            default:
                command.append(packageName);
                break;
        }
        
        // Add package arguments if available
        if (mcpPackage.getPackageArguments() != null) {
            for (Argument arg : mcpPackage.getPackageArguments()) {
                String argValue = extractArgumentValue(arg);
                if (StringUtils.isNotBlank(argValue)) {
                    command.append(" ").append(argValue);
                }
            }
        }
        
        return command.toString();
    }
    
    /**
     * Extract argument value from polymorphic Argument interface.
     * 
     * @param arg Argument instance (either NamedArgument or PositionalArgument)
     * @return argument value
     */
    private String extractArgumentValue(Argument arg) {
        if (arg instanceof NamedArgument) {
            NamedArgument namedArg = (NamedArgument) arg;
            return namedArg.getValue();
        } else if (arg instanceof PositionalArgument) {
            PositionalArgument positionalArg = (PositionalArgument) arg;
            return positionalArg.getValue();
        }
        return null;
    }
    
    /**
     * Parse URL data to MCP servers.
     * 
     * <p>TODO: Implement proper URL fetching functionality to retrieve data from remote URLs
     *
     * @param urlData URL data string
     * @return list of MCP server detail info
     * @throws UnsupportedOperationException URL fetching is not implemented yet
     */
    private List<McpServerDetailInfo> parseUrlData(String urlData) throws Exception {
        // URL fetching functionality is not implemented yet
        throw new UnsupportedOperationException("URL fetching not implemented yet. Please use JSON import instead.");
    }
    
    /**
     * Transform JSON node to MCP server.
     *
     * @param serverNode JSON node
     * @return MCP server detail info
     */
    private McpServerDetailInfo transformJsonNodeToServer(JsonNode serverNode) {
        try {
            McpServerDetailInfo server = new McpServerDetailInfo();
            
            // Basic server info
            server.setId(getTextValue(serverNode, "id"));
            server.setName(getTextValue(serverNode, "name"));
            server.setDescription(getTextValue(serverNode, "description"));
            server.setProtocol(getTextValue(serverNode, "protocol"));
            server.setFrontProtocol(getTextValue(serverNode, "frontProtocol"));
            
            // Handle different protocol formats
            if (StringUtils.isBlank(server.getProtocol())) {
                server.setProtocol(inferProtocolFromConfig(serverNode));
            }
            
            // Repository info
            JsonNode repoNode = serverNode.get("repository");
            if (repoNode != null) {
                Repository repo = new Repository();
                server.setRepository(repo);
            }
            
            // Version info
            JsonNode versionNode = serverNode.get("version");
            if (versionNode != null) {
                ServerVersionDetail versionDetail = new ServerVersionDetail();
                if (versionNode.isTextual()) {
                    versionDetail.setVersion(versionNode.asText());
                } else {
                    versionDetail.setVersion(getTextValue(versionNode, "version"));
                }
                server.setVersionDetail(versionDetail);
            }
            
            // Remote server config - simplified for stdio protocol
            if (AiConstants.Mcp.MCP_PROTOCOL_STDIO.equals(server.getProtocol())) {
                McpServerRemoteServiceConfig remoteConfig = new McpServerRemoteServiceConfig();
                String exportPath = getTextValue(serverNode, "command");
                if (StringUtils.isNotBlank(exportPath)) {
                    remoteConfig.setExportPath(exportPath);
                }
                server.setRemoteServerConfig(remoteConfig);
            }
            
            // Tools - simplified structure
            JsonNode toolsNode = serverNode.get("tools");
            if (toolsNode != null && toolsNode.isArray()) {
                McpToolSpecification toolSpec = new McpToolSpecification();
                List<McpTool> tools = new ArrayList<>();
                
                for (JsonNode toolNode : toolsNode) {
                    McpTool tool = new McpTool();
                    String toolName = getTextValue(toolNode, "name");
                    String toolDesc = getTextValue(toolNode, "description");
                    
                    if (StringUtils.isNotBlank(toolName)) {
                        // Set basic tool info - actual implementation depends on McpTool structure
                        tools.add(tool);
                    }
                }
                
                if (!tools.isEmpty()) {
                    toolSpec.setTools(tools);
                    server.setToolSpec(toolSpec);
                }
            }
            
            return server;
        } catch (Exception e) {
            // Log error and skip invalid server
            return null;
        }
    }
    
    /**
     * Infer protocol from configuration.
     *
     * @param serverNode server JSON node
     * @return inferred protocol
     */
    private String inferProtocolFromConfig(JsonNode serverNode) {
        if (serverNode.has(COMMAND_FIELD)) {
            return AiConstants.Mcp.MCP_PROTOCOL_STDIO;
        }
        
        if (serverNode.has(URL_FIELD) || serverNode.has(ENDPOINT_FIELD) || serverNode.has(BASE_URL_FIELD)) {
            return AiConstants.Mcp.MCP_PROTOCOL_HTTP;
        }
        
        if (serverNode.has(SERVICE_NAME_FIELD) || serverNode.has(SERVICE_FIELD)) {
            return AiConstants.Mcp.MCP_PROTOCOL_DUBBO;
        }
        
        // Default to stdio
        return AiConstants.Mcp.MCP_PROTOCOL_STDIO;
    }
    
    /**
     * Get text value from JSON node.
     *
     * @param node JSON node
     * @param fieldName field name
     * @return text value or null
     */
    private String getTextValue(JsonNode node, String fieldName) {
        JsonNode fieldNode = node.get(fieldName);
        if (fieldNode != null && fieldNode.isTextual()) {
            return fieldNode.asText();
        }
        return null;
    }
    
    /**
     * Generate server ID from name.
     *
     * @param name server name
     * @return generated ID
     */
    private String generateServerId(String name) {
        if (StringUtils.isBlank(name)) {
            return UUID.randomUUID().toString().replace("-", "");
        }
        
        // Use name-based ID with random suffix
        String baseId = name.toLowerCase().replaceAll("[^a-z0-9]", "");
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        return baseId + "-" + suffix;
    }
    
    /**
     * Validate URL security and validity based on protocol.
     *
     * @param url URL to validate
     * @param protocol Protocol type
     * @return true if URL is valid
     */
    private boolean isValidUrl(String url, String protocol) {
        if (StringUtils.isBlank(url)) {
            return false;
        }
        
        // Basic security checks - prevent potential malicious URLs
        String lowerUrl = url.toLowerCase();
        if (lowerUrl.contains(PROTOCOL_JAVASCRIPT) || lowerUrl.contains(PROTOCOL_DATA) || lowerUrl.contains(PROTOCOL_FILE)) {
            return false;
        }
        
        switch (protocol) {
            case AiConstants.Mcp.MCP_PROTOCOL_HTTP:
                // HTTP protocol requires valid HTTP/HTTPS URLs
                return lowerUrl.startsWith(HTTP_PREFIX) || lowerUrl.startsWith(HTTPS_PREFIX);
            case AiConstants.Mcp.MCP_PROTOCOL_STDIO:
                // STDIO protocol allows commands and paths, more flexible validation
                return !lowerUrl.contains("..") && !lowerUrl.contains("&") && !lowerUrl.contains("|");
            case AiConstants.Mcp.MCP_PROTOCOL_DUBBO:
                // Dubbo protocol allows dubbo:// URLs or standard service URLs
                return lowerUrl.startsWith("dubbo://") || lowerUrl.startsWith(HTTP_PREFIX) || lowerUrl.startsWith(HTTPS_PREFIX);
            default:
                // For unknown protocols, apply basic validation
                return !lowerUrl.contains("..") && !lowerUrl.contains(PROTOCOL_JAVASCRIPT) && !lowerUrl.contains(PROTOCOL_DATA);
        }
    }
}