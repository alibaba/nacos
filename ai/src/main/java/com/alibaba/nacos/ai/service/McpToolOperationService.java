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
import com.alibaba.nacos.api.ai.model.mcp.McpServerBasicInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpTool;
import com.alibaba.nacos.api.ai.model.mcp.McpToolSpecification;
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
 * Nacos AI MCP tool operation service.
 *
 * @author xiweng.yy
 */
@Service
public class McpToolOperationService {
    
    private final ConfigQueryChainService configQueryChainService;
    
    private final ConfigOperationService configOperationService;
    
    public McpToolOperationService(ConfigQueryChainService configQueryChainService,
            ConfigOperationService configOperationService) {
        this.configQueryChainService = configQueryChainService;
        this.configOperationService = configOperationService;
    }
    
    /**
     * Create or Update mcp server tool. If mcp server tool already exist, will full replace it.
     *
     * @param namespaceId       namespace id of mcp server
     * @param toolSpecification mcp server included tools, see {@link McpTool}, optional
     * @throws NacosException any exception during handling
     */
    public void refreshMcpTool(String namespaceId, McpServerBasicInfo serverBasicInfo, 
                               McpToolSpecification toolSpecification)
            throws NacosException {
        ConfigRequestInfo configRequestInfo = new ConfigRequestInfo();
        ConfigFormV3 toolConfigForm = buildMcpToolConfigForm(namespaceId, serverBasicInfo, toolSpecification);
        configOperationService.publishConfig(toolConfigForm, configRequestInfo, null);
    }
    
    public McpToolSpecification getMcpTool(String namespaceId, String toolsDescriptionRef) {
        ConfigQueryChainRequest request = buildQueryMcpToolRequest(namespaceId, toolsDescriptionRef);
        ConfigQueryChainResponse response = configQueryChainService.handle(request);
        if (ConfigQueryChainResponse.ConfigQueryStatus.CONFIG_NOT_FOUND == response.getStatus()) {
            return null;
        }
        return transferToMcpServerTool(response);
    }
    
    public void deleteMcpTool(String namespaceId, String mcpServerId, String version) throws NacosException {
        configOperationService.deleteConfig(McpConfigUtils.formatServerToolSpecDataId(mcpServerId, version),
                Constants.MCP_SERVER_TOOL_GROUP, namespaceId, null, null, "nacos", null);
    }
    
    private ConfigFormV3 buildMcpToolConfigForm(String namespaceId, McpServerBasicInfo mcpServerBasicInfo, 
                                                McpToolSpecification toolSpecification) {
        ConfigFormV3 configFormV3 = new ConfigFormV3();
        configFormV3.setGroupName(Constants.MCP_SERVER_TOOL_GROUP);
        configFormV3.setGroup(Constants.MCP_SERVER_TOOL_GROUP);
        configFormV3.setNamespaceId(namespaceId);

        String toolSpecDataId = McpConfigUtils.formatServerToolSpecDataId(mcpServerBasicInfo.getId(), 
                mcpServerBasicInfo.getVersionDetail().getVersion());
        configFormV3.setDataId(toolSpecDataId);
        
        configFormV3.setContent(JacksonUtils.toJson(toolSpecification));
        configFormV3.setType(ConfigType.JSON.getType());
        configFormV3.setAppName(mcpServerBasicInfo.getName());
        configFormV3.setSrcUser("nacos");
        configFormV3.setConfigTags(Constants.MCP_SERVER_CONFIG_MARK);
        return configFormV3;
    }
    
    private ConfigQueryChainRequest buildQueryMcpToolRequest(String namespaceId, String toolsDescriptionRef) {
        ConfigQueryChainRequest request = new ConfigQueryChainRequest();
        request.setDataId(toolsDescriptionRef);
        request.setGroup(Constants.MCP_SERVER_TOOL_GROUP);
        request.setTenant(namespaceId);
        return request;
    }
    
    private McpToolSpecification transferToMcpServerTool(ConfigQueryChainResponse response) {
        return JacksonUtils.toObj(response.getContent(), new TypeReference<>() {
        });
    }
}
