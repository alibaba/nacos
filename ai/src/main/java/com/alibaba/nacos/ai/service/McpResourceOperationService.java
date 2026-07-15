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

package com.alibaba.nacos.ai.service;

import com.alibaba.nacos.ai.constant.Constants;
import com.alibaba.nacos.ai.utils.McpConfigUtils;
import com.alibaba.nacos.api.ai.model.mcp.McpResourceSpecification;
import com.alibaba.nacos.api.ai.model.mcp.McpServerBasicInfo;
import com.alibaba.nacos.api.config.ConfigType;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.config.server.model.ConfigRequestInfo;
import com.alibaba.nacos.config.server.model.form.ConfigFormV3;
import com.alibaba.nacos.config.server.service.ConfigOperationService;
import com.alibaba.nacos.config.server.service.query.ConfigQueryChainService;
import com.alibaba.nacos.config.server.service.query.model.ConfigQueryChainRequest;
import com.alibaba.nacos.config.server.service.query.model.ConfigQueryChainResponse;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.stereotype.Service;

/**
 * Nacos AI MCP resource operation service.
 *
 * @author xiweng.yy
 */
@Service
public class McpResourceOperationService {
    
    private final ConfigQueryChainService configQueryChainService;
    
    private final ConfigOperationService configOperationService;
    
    public McpResourceOperationService(ConfigQueryChainService configQueryChainService,
        ConfigOperationService configOperationService) {
        this.configQueryChainService = configQueryChainService;
        this.configOperationService = configOperationService;
    }
    
    /**
     * Create or update mcp server resources. If mcp server resources already exist, will fully replace it.
     *
     * @param namespaceId namespace id of mcp server
     * @param serverBasicInfo mcp server basic info
     * @param resourceSpecification mcp server resource specification
     * @throws NacosException any exception during handling
     */
    public void refreshMcpResource(String namespaceId, McpServerBasicInfo serverBasicInfo,
        McpResourceSpecification resourceSpecification) throws NacosException {
        ConfigRequestInfo configRequestInfo = new ConfigRequestInfo();
        ConfigFormV3 resourceConfigForm =
            buildMcpResourceConfigForm(namespaceId, serverBasicInfo, resourceSpecification);
        configOperationService.publishConfig(resourceConfigForm, configRequestInfo, null);
    }
    
    public McpResourceSpecification getMcpResource(String namespaceId,
        String resourceDescriptionRef) {
        ConfigQueryChainRequest request =
            buildQueryMcpResourceRequest(namespaceId, resourceDescriptionRef);
        ConfigQueryChainResponse response = configQueryChainService.handle(request);
        if (ConfigQueryChainResponse.ConfigQueryStatus.CONFIG_NOT_FOUND == response.getStatus()) {
            return null;
        }
        return transferToMcpServerResource(response);
    }
    
    /** Delete MCP resource specification. */
    public void deleteMcpResource(String namespaceId, String mcpServerId, String version)
        throws NacosException {
        configOperationService.deleteConfig(
            McpConfigUtils.formatServerResourceSpecDataId(mcpServerId, version),
            Constants.MCP_SERVER_RESOURCE_GROUP, namespaceId, null, null, "nacos", null);
    }
    
    private ConfigFormV3 buildMcpResourceConfigForm(String namespaceId,
        McpServerBasicInfo mcpServerBasicInfo,
        McpResourceSpecification resourceSpecification) {
        ConfigFormV3 configFormV3 = new ConfigFormV3();
        configFormV3.setGroupName(Constants.MCP_SERVER_RESOURCE_GROUP);
        configFormV3.setGroup(Constants.MCP_SERVER_RESOURCE_GROUP);
        configFormV3.setNamespaceId(namespaceId);
        String resourceSpecDataId =
            McpConfigUtils.formatServerResourceSpecDataId(mcpServerBasicInfo.getId(),
                mcpServerBasicInfo.getVersionDetail().getVersion());
        configFormV3.setDataId(resourceSpecDataId);
        configFormV3.setContent(JacksonUtils.toJson(resourceSpecification));
        configFormV3.setType(ConfigType.JSON.getType());
        configFormV3.setAppName(mcpServerBasicInfo.getName());
        configFormV3.setSrcUser("nacos");
        configFormV3.setConfigTags(Constants.MCP_SERVER_CONFIG_MARK);
        return configFormV3;
    }
    
    private ConfigQueryChainRequest buildQueryMcpResourceRequest(String namespaceId,
        String resourceDescriptionRef) {
        ConfigQueryChainRequest request = new ConfigQueryChainRequest();
        request.setDataId(resourceDescriptionRef);
        request.setGroup(Constants.MCP_SERVER_RESOURCE_GROUP);
        request.setTenant(namespaceId);
        return request;
    }
    
    private McpResourceSpecification transferToMcpServerResource(
        ConfigQueryChainResponse response) {
        return JacksonUtils.toObj(response.getContent(), new TypeReference<>() {
        });
    }
}
