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
import com.alibaba.nacos.ai.service.SyncEffectService;
import com.alibaba.nacos.ai.utils.McpConfigUtils;
import com.alibaba.nacos.api.ai.model.mcp.McpServerVersionInfo;
import com.alibaba.nacos.api.ai.utils.AgentValidationUtils;
import com.alibaba.nacos.api.config.ConfigType;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.runtime.NacosDeserializationException;
import com.alibaba.nacos.api.exception.runtime.NacosSerializationException;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.config.server.model.ConfigRequestInfo;
import com.alibaba.nacos.config.server.model.form.ConfigFormV3;
import com.alibaba.nacos.config.server.service.ConfigOperationService;
import com.alibaba.nacos.config.server.service.query.ConfigQueryChainService;
import com.alibaba.nacos.config.server.service.query.model.ConfigQueryChainRequest;
import com.alibaba.nacos.config.server.service.query.model.ConfigQueryChainResponse;
import org.springframework.stereotype.Service;

/**
 * Owns Config access for the historical MCP serving Manifest.
 *
 * <p>The Manifest remains a serving compatibility index. Resource identity resolution must use
 * {@code ai_resource}, never this storage.</p>
 *
 * @author Nacos
 */
@Service
public class McpServingManifestStorage {
    
    private final ConfigQueryChainService configQueryChainService;
    
    private final ConfigOperationService configOperationService;
    
    private final SyncEffectService syncEffectService;
    
    public McpServingManifestStorage(ConfigQueryChainService configQueryChainService,
        ConfigOperationService configOperationService, SyncEffectService syncEffectService) {
        this.configQueryChainService = configQueryChainService;
        this.configOperationService = configOperationService;
        this.syncEffectService = syncEffectService;
    }
    
    /**
     * Load one historical serving Manifest from its existing Config coordinate.
     *
     * @param namespaceId namespace identifier
     * @param mcpId MCP compatibility alias
     * @return decoded Manifest, or {@code null} when it does not exist
     * @throws NacosException when Config query or decoding fails
     */
    public McpServerVersionInfo get(String namespaceId, String mcpId) throws NacosException {
        validateCoordinate(namespaceId, mcpId);
        ConfigQueryChainRequest request = ConfigQueryChainRequest.buildConfigQueryChainRequest(
            McpConfigUtils.formatServerVersionInfoDataId(mcpId),
            Constants.MCP_SERVER_VERSIONS_GROUP, namespaceId);
        ConfigQueryChainResponse response = configQueryChainService.handle(request);
        if (response == null) {
            throw storageFailure("MCP serving Manifest query returned no response", null);
        }
        if (McpConfigUtils.isConfigNotFound(response.getStatus())) {
            return null;
        }
        if (!McpConfigUtils.isConfigFound(response.getStatus())) {
            throw storageFailure("MCP serving Manifest cannot be read: " + response.getMessage(),
                null);
        }
        final McpServerVersionInfo result;
        try {
            result = JacksonUtils.toObj(response.getContent(), McpServerVersionInfo.class);
        } catch (NacosDeserializationException e) {
            throw storageFailure("MCP serving Manifest cannot be decoded", e);
        }
        try {
            validateManifest(result);
        } catch (IllegalArgumentException e) {
            throw storageFailure("MCP serving Manifest has invalid identity fields", e);
        }
        if (!mcpId.equals(result.getId())) {
            throw storageFailure("MCP serving Manifest id does not match its Config coordinate",
                null);
        }
        return result;
    }
    
    /**
     * Publish one serving Manifest at the existing Config coordinate and wait for local effect.
     *
     * @param namespaceId namespace identifier
     * @param manifest serving Manifest
     * @throws NacosException when Config publication fails
     */
    public void publish(String namespaceId, McpServerVersionInfo manifest) throws NacosException {
        AgentValidationUtils.validateNamespaceId(namespaceId);
        validateManifest(manifest);
        ConfigFormV3 form = buildForm(namespaceId, manifest);
        long startTimeStamp = System.currentTimeMillis();
        configOperationService.publishConfig(form, new ConfigRequestInfo(), null);
        if (syncEffectService != null) {
            syncEffectService.toSync(form, startTimeStamp);
        }
    }
    
    /**
     * Delete one serving Manifest from the existing Config coordinate.
     *
     * @param namespaceId namespace identifier
     * @param mcpId MCP compatibility alias
     * @throws NacosException when Config deletion fails
     */
    public void delete(String namespaceId, String mcpId) throws NacosException {
        validateCoordinate(namespaceId, mcpId);
        configOperationService.deleteConfig(
            McpConfigUtils.formatServerVersionInfoDataId(mcpId),
            Constants.MCP_SERVER_VERSIONS_GROUP, namespaceId, null, null, "nacos", null);
    }
    
    private ConfigFormV3 buildForm(String namespaceId, McpServerVersionInfo manifest)
        throws NacosException {
        final String content;
        try {
            content = JacksonUtils.toJson(manifest);
        } catch (NacosSerializationException e) {
            throw storageFailure("MCP serving Manifest cannot be encoded", e);
        }
        ConfigFormV3 result = new ConfigFormV3();
        result.setGroupName(Constants.MCP_SERVER_VERSIONS_GROUP);
        result.setGroup(Constants.MCP_SERVER_VERSIONS_GROUP);
        result.setNamespaceId(namespaceId);
        result.setDataId(McpConfigUtils.formatServerVersionInfoDataId(manifest.getId()));
        result.setContent(content);
        result.setType(ConfigType.JSON.getType());
        result.setAppName(manifest.getName());
        result.setSrcUser("nacos");
        result.setConfigTags(McpConfigUtils.buildMcpServerVersionConfigTags(manifest.getName()));
        return result;
    }
    
    private void validateCoordinate(String namespaceId, String mcpId) {
        AgentValidationUtils.validateNamespaceId(namespaceId);
        McpResourceExtSerializer.validateMcpId(mcpId);
    }
    
    private void validateManifest(McpServerVersionInfo manifest) {
        if (manifest == null) {
            throw new IllegalArgumentException("MCP serving Manifest must not be null");
        }
        McpResourceExtSerializer.validateMcpId(manifest.getId());
        if (StringUtils.isBlank(manifest.getName())) {
            throw new IllegalArgumentException("MCP serving Manifest name must not be blank");
        }
    }
    
    private static NacosException storageFailure(String message, Throwable cause) {
        return cause == null ? new NacosException(NacosException.SERVER_ERROR, message)
            : new NacosException(NacosException.SERVER_ERROR, message, cause);
    }
}
