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

import com.alibaba.nacos.ai.constant.McpServerValidationConstants;
import com.alibaba.nacos.ai.index.McpServerIndex;
import com.alibaba.nacos.ai.model.mcp.McpServerIndexData;
import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.api.ai.model.mcp.McpServerDetailInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpServerImportValidationResult;
import com.alibaba.nacos.api.ai.model.mcp.McpServerValidationItem;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.common.utils.CollectionUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * MCP Server Validation Service.
 *
 * @author nacos
 */
@Service
public class McpServerValidationService {
    
    @Autowired
    private McpServerIndex mcpServerIndex;
    
    /**
     * Validate MCP servers for import.
     *
     * @param namespaceId namespace ID
     * @param servers servers to validate
     * @return validation result
     * @throws NacosException if validation fails
     */
    public McpServerImportValidationResult validateServers(String namespaceId, List<McpServerDetailInfo> servers) 
            throws NacosException {
        McpServerImportValidationResult result = new McpServerImportValidationResult();
        List<McpServerValidationItem> validationItems = new ArrayList<>();
        List<String> overallErrors = new ArrayList<>();
        
        Set<String> serverNames = new HashSet<>();
        int validCount = 0;
        int invalidCount = 0;
        int duplicateCount = 0;
        
        try {
            for (McpServerDetailInfo server : servers) {
                McpServerValidationItem item = validateSingleServer(namespaceId, server, serverNames);
                validationItems.add(item);
                
                switch (item.getStatus()) {
                    case McpServerValidationConstants.STATUS_VALID:
                        validCount++;
                        break;
                    case McpServerValidationConstants.STATUS_INVALID:
                        invalidCount++;
                        break;
                    case McpServerValidationConstants.STATUS_DUPLICATE:
                        duplicateCount++;
                        break;
                    default:
                        // Handle unknown status
                        break;
                }
            }
            
            result.setValid(invalidCount == 0);
            result.setTotalCount(servers.size());
            result.setValidCount(validCount);
            result.setInvalidCount(invalidCount);
            result.setDuplicateCount(duplicateCount);
            result.setServers(validationItems);
            result.setErrors(overallErrors);
            
        } catch (Exception e) {
            overallErrors.add("Validation failed: " + e.getMessage());
            result.setValid(false);
            result.setErrors(overallErrors);
        }
        
        return result;
    }
    
    /**
     * Validate single MCP server.
     *
     * @param namespaceId namespace ID
     * @param server server to validate
     * @param existingNames existing server names in current batch
     * @return validation item
     */
    private McpServerValidationItem validateSingleServer(String namespaceId, McpServerDetailInfo server, 
            Set<String> existingNames) {
        McpServerValidationItem item = new McpServerValidationItem();
        List<String> errors = new ArrayList<>();
        
        String serverName = server.getName();
        item.setServerName(serverName);
        item.setServerId(server.getId());
        item.setServer(server);
        
        // Check required fields
        if (StringUtils.isBlank(serverName)) {
            errors.add("Server name is required");
        }
        
        if (StringUtils.isBlank(server.getProtocol())) {
            errors.add("Protocol is required");
        } else if (!isValidProtocol(server.getProtocol())) {
            errors.add("Invalid protocol: " + server.getProtocol());
        }
        
        if (StringUtils.isBlank(server.getDescription())) {
            errors.add("Description is required");
        }
        
        // Check for duplicates in current batch
        if (existingNames.contains(serverName)) {
            errors.add("Duplicate server name in import batch: " + serverName);
            item.setStatus(McpServerValidationConstants.STATUS_DUPLICATE);
        } else {
            existingNames.add(serverName);
        }
        
        // Check if server already exists
        try {
            McpServerIndexData existingServer = mcpServerIndex.getMcpServerByName(namespaceId, serverName);
            if (existingServer != null) {
                item.setExists(true);
                if (!McpServerValidationConstants.STATUS_DUPLICATE.equals(item.getStatus())) {
                    item.setStatus(McpServerValidationConstants.STATUS_DUPLICATE);
                    errors.add("Server already exists: " + serverName);
                }
            }
        } catch (Exception e) {
            errors.add("Error checking existing server: " + e.getMessage());
        }
        
        // Validate protocol-specific configurations
        validateProtocolSpecificConfig(server, errors);
        
        // Set validation status
        if (errors.isEmpty()) {
            item.setStatus(McpServerValidationConstants.STATUS_VALID);
        } else if (!McpServerValidationConstants.STATUS_DUPLICATE.equals(item.getStatus())) {
            item.setStatus(McpServerValidationConstants.STATUS_INVALID);
        }
        
        item.setErrors(errors);
        return item;
    }
    
    /**
     * Check if protocol is valid.
     *
     * @param protocol protocol to check
     * @return true if valid
     */
    private boolean isValidProtocol(String protocol) {
        return AiConstants.Mcp.MCP_PROTOCOL_STDIO.equals(protocol)
                || AiConstants.Mcp.MCP_PROTOCOL_SSE.equals(protocol)
                || AiConstants.Mcp.MCP_PROTOCOL_STREAMABLE.equals(protocol)
                || AiConstants.Mcp.MCP_PROTOCOL_HTTP.equals(protocol)
                || AiConstants.Mcp.MCP_PROTOCOL_DUBBO.equals(protocol);
    }
    
    /**
     * Validate protocol-specific configurations.
     *
     * @param server server to validate
     * @param errors error list to append to
     */
    private void validateProtocolSpecificConfig(McpServerDetailInfo server, List<String> errors) {
        String protocol = server.getProtocol();
        
        if (AiConstants.Mcp.MCP_PROTOCOL_STDIO.equals(protocol)) {
            // For stdio protocol, check if command is provided
            if (server.getLocalServerConfig() == null && CollectionUtils.isEmpty(server.getPackages())) {
                errors.add("Local server configuration or packages are required for stdio protocol");
            }
        } else {
            // For non-stdio protocols, basic validation
            if (server.getRemoteServerConfig() == null) {
                errors.add("Remote server configuration is required for " + protocol + " protocol");
            }
        }
        
        // Validate tools if present
        if (server.getToolSpec() != null) {
            if (server.getToolSpec().getTools() == null || server.getToolSpec().getTools().isEmpty()) {
                errors.add("Tool specification should contain at least one tool");
            }
        }
    }
}