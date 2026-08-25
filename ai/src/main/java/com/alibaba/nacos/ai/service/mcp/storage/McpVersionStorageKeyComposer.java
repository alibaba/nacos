/*
 * Copyright 1999-2026 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.ai.service.mcp.storage;

import com.alibaba.nacos.ai.constant.Constants;
import com.alibaba.nacos.ai.model.mcp.McpServerStorageInfo;
import com.alibaba.nacos.ai.model.mcp.McpVersionStorageDescriptor;
import com.alibaba.nacos.ai.utils.McpConfigUtils;
import com.alibaba.nacos.api.ai.utils.AgentValidationUtils;
import com.alibaba.nacos.common.utils.StringUtils;

/**
 * Composes stable logical storage keys for the three existing MCP Config objects.
 *
 * @author Nacos
 */
public final class McpVersionStorageKeyComposer {
    
    private static final int MAX_VERSION_LENGTH = 64;
    
    private McpVersionStorageKeyComposer() {
    }
    
    /**
     * Compose a descriptor for a new MCP Version using deterministic existing data ids.
     *
     * @param namespaceId namespace identifier
     * @param mcpId historical UUID-shaped compatibility id
     * @param version exact MCP Version
     * @param includeTools whether the Version has Tools content
     * @param includeResources whether the Version has Resources content
     * @return validated storage descriptor
     */
    public static McpVersionStorageDescriptor compose(String namespaceId, String mcpId,
        String version, boolean includeTools, boolean includeResources) {
        validateIdentity(namespaceId, mcpId, version);
        String toolDataId = includeTools
            ? McpConfigUtils.formatServerToolSpecDataId(mcpId, version) : null;
        String resourceDataId = includeResources
            ? McpConfigUtils.formatServerResourceSpecDataId(mcpId, version) : null;
        return build(namespaceId, McpConfigUtils.formatServerSpecInfoDataId(mcpId, version),
            toolDataId, resourceDataId);
    }
    
    /**
     * Build a zero-copy descriptor for historical MCP content.
     *
     * <p>The optional Tools and Resources data ids are taken from the historical Server object
     * rather than inferred, so reconciliation never points to content that the old object did not
     * reference.</p>
     *
     * @param namespaceId namespace identifier
     * @param mcpId historical UUID-shaped compatibility id
     * @param version exact MCP Version
     * @param legacyStorageInfo historical Server storage object
     * @return validated descriptor pointing to the existing Config coordinates
     */
    public static McpVersionStorageDescriptor fromLegacy(String namespaceId, String mcpId,
        String version, McpServerStorageInfo legacyStorageInfo) {
        if (legacyStorageInfo == null) {
            throw new IllegalArgumentException("Legacy MCP Server storage info must not be null");
        }
        validateIdentity(namespaceId, mcpId, version);
        return build(namespaceId, McpConfigUtils.formatServerSpecInfoDataId(mcpId, version),
            blankToNull(legacyStorageInfo.getToolsDescriptionRef()),
            blankToNull(legacyStorageInfo.getResourceDescriptionRef()));
    }
    
    private static McpVersionStorageDescriptor build(String namespaceId, String serverDataId,
        String toolDataId, String resourceDataId) {
        McpVersionStorageDescriptor result = new McpVersionStorageDescriptor();
        result.setProvider(McpVersionStorageDescriptor.PROVIDER);
        result.setKeyFormat(McpVersionStorageDescriptor.KEY_FORMAT);
        result.setServerKey(key(namespaceId, Constants.MCP_SERVER_GROUP, serverDataId));
        if (toolDataId != null) {
            result.setToolKey(key(namespaceId, Constants.MCP_SERVER_TOOL_GROUP, toolDataId));
        }
        if (resourceDataId != null) {
            result.setResourceKey(key(namespaceId, Constants.MCP_SERVER_RESOURCE_GROUP,
                resourceDataId));
        }
        result.setSchemaVersion(McpVersionStorageDescriptor.SCHEMA_VERSION);
        McpVersionStorageDescriptorSerializer.validate(result);
        return result;
    }
    
    private static String key(String namespaceId, String group, String dataId) {
        return namespaceId + ':' + group + ':' + dataId;
    }
    
    private static void validateIdentity(String namespaceId, String mcpId, String version) {
        AgentValidationUtils.validateNamespaceId(namespaceId);
        McpResourceExtSerializer.validateMcpId(mcpId);
        if (version == null || version.isEmpty() || version.length() > MAX_VERSION_LENGTH) {
            throw new IllegalArgumentException("Invalid MCP Version: " + version);
        }
    }
    
    private static String blankToNull(String value) {
        return StringUtils.isBlank(value) ? null : value;
    }
}
