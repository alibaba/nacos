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
import com.alibaba.nacos.api.ai.model.mcp.McpServerBasicInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpServerDetailInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpTool;
import com.alibaba.nacos.api.config.ConfigType;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.config.server.model.ConfigInfo;
import com.alibaba.nacos.config.server.model.ConfigRequestInfo;
import com.alibaba.nacos.config.server.model.form.ConfigForm;
import com.alibaba.nacos.config.server.model.form.ConfigFormV3;
import com.alibaba.nacos.config.server.service.ConfigDetailService;
import com.alibaba.nacos.config.server.service.ConfigOperationService;
import com.alibaba.nacos.config.server.service.query.ConfigQueryChainService;
import com.alibaba.nacos.config.server.service.query.model.ConfigQueryChainRequest;
import com.alibaba.nacos.config.server.service.query.model.ConfigQueryChainResponse;
import com.alibaba.nacos.naming.core.InstanceOperator;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

/**
 * Nacos AI MCP operation service.
 *
 * @author xiweng.yy
 */
@Service
public class McpOperationService {
    
    private final ConfigQueryChainService configQueryChainService;
    
    private final ConfigOperationService configOperationService;
    
    private final ConfigDetailService configDetailService;
    
    private final InstanceOperator instanceOperator;
    
    public McpOperationService(ConfigQueryChainService configQueryChainService,
            ConfigOperationService configOperationService, ConfigDetailService configDetailService,
            InstanceOperator instanceOperator) {
        this.configQueryChainService = configQueryChainService;
        this.configOperationService = configOperationService;
        this.configDetailService = configDetailService;
        this.instanceOperator = instanceOperator;
    }
    
    /**
     * List mcp server.
     *
     * @param namespaceId namespace id of mcp servers
     * @param mcpName     mcp name pattern, if null or empty, filter all mcp servers.
     * @param search      search type `blur` or `accurate`, means whether to search by fuzzy or exact match by
     *                    `mcpName`.
     * @param pageNo      page number, start from 1
     * @param pageSize    page size each page
     * @return list of {@link McpServerBasicInfo} matched input parameters.
     */
    public Page<McpServerBasicInfo> listMcpServer(String namespaceId, String mcpName, String search, int pageNo,
            int pageSize) {
        String targetMcpName;
        if (StringUtils.isBlank(mcpName)) {
            targetMcpName = Constants.ALL_PATTERN + Constants.MCP_SPECIFICATION_DATA_ID_SUFFIX;
            search = Constants.MCP_LIST_SEARCH_BLUR;
        } else {
            targetMcpName = mcpName + Constants.MCP_SPECIFICATION_DATA_ID_SUFFIX;
        }
        Page<ConfigInfo> mcpServerPage = configDetailService.findConfigInfoPage(search, pageNo, pageSize, targetMcpName,
                Constants.MCP_SERVER_GROUP, namespaceId, Collections.emptyMap());
        Page<McpServerBasicInfo> result = new Page<>();
        result.setTotalCount(mcpServerPage.getTotalCount());
        result.setPageNumber(mcpServerPage.getPageNumber());
        result.setPagesAvailable(mcpServerPage.getPagesAvailable());
        for (ConfigInfo each : mcpServerPage.getPageItems()) {
            result.getPageItems().add(transferToMcpServerBasicInfo(each));
        }
        return result;
    }
    
    /**
     * Get specified mcp server detail info.
     *
     * @param namespaceId namespace id of mcp server
     * @param mcpName     name of mcp server
     * @return detail info with {@link McpServerDetailInfo}
     * @throws NacosApiException any exception during handling
     */
    public McpServerDetailInfo getMcpServer(String namespaceId, String mcpName) throws NacosApiException {
        ConfigQueryChainRequest request = buildQueryMcpServerRequest(namespaceId, mcpName);
        ConfigQueryChainResponse response = configQueryChainService.handle(request);
        if (ConfigQueryChainResponse.ConfigQueryStatus.CONFIG_NOT_FOUND == response.getStatus()) {
            throw new NacosApiException(NacosApiException.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
                    String.format("mcp server `%s` not found", mcpName));
        }
        McpServerDetailInfo result = JacksonUtils.toObj(response.getContent(), McpServerDetailInfo.class);
        // TODO get tool info and endpoint service info.
        return result;
    }
    
    /**
     * Create new mcp server.
     *
     * @param namespaceId         namespace id of mcp server
     * @param mcpName             name of mcp server
     * @param serverSpecification mcp server specification, see {@link McpServerBasicInfo}
     * @param toolSpecification   mcp server included tools, see {@link McpTool}, optional
     * @throws NacosException any exception during handling
     */
    public void createMcpServer(String namespaceId, String mcpName, McpServerBasicInfo serverSpecification,
            List<McpTool> toolSpecification) throws NacosException {
        ConfigQueryChainRequest request = buildQueryMcpServerRequest(namespaceId, mcpName);
        ConfigQueryChainResponse response = configQueryChainService.handle(request);
        if (ConfigQueryChainResponse.ConfigQueryStatus.CONFIG_NOT_FOUND != response.getStatus()) {
            throw new NacosApiException(NacosApiException.CONFLICT, ErrorCode.RESOURCE_CONFLICT,
                    String.format("mcp server `%s` has existed, please update it rather than create.", mcpName));
        }
        ConfigForm configForm = buildMcpConfigForm(namespaceId, mcpName, serverSpecification);
        ConfigRequestInfo configRequestInfo = new ConfigRequestInfo();
        configRequestInfo.setUpdateForExist(false);
        configOperationService.publishConfig(configForm, configRequestInfo, null);
        if (null != toolSpecification && !toolSpecification.isEmpty()) {
            // TODO create tool specification.
        }
    }
    
    /**
     * Update existed mcp server.
     *
     * <p>
     * `namespaceId` and `mcpName` can't be changed.
     * </p>
     *
     * @param namespaceId         namespace id of mcp server, used to mark which mcp server to update
     * @param mcpName             name of mcp server, used to mark which mcp server to update
     * @param serverSpecification mcp server specification, see {@link McpServerBasicInfo}
     * @param toolSpecification   mcp server included tools, see {@link McpTool}, optional
     * @throws NacosException any exception during handling
     */
    public void updateMcpServer(String namespaceId, String mcpName, McpServerBasicInfo serverSpecification,
            List<McpTool> toolSpecification) throws NacosException {
        ConfigQueryChainRequest request = buildQueryMcpServerRequest(namespaceId, mcpName);
        ConfigQueryChainResponse response = configQueryChainService.handle(request);
        if (ConfigQueryChainResponse.ConfigQueryStatus.CONFIG_NOT_FOUND == response.getStatus()) {
            throw new NacosApiException(NacosApiException.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
                    String.format("mcp server `%s` not found", mcpName));
        }
        ConfigForm configForm = buildMcpConfigForm(namespaceId, mcpName, serverSpecification);
        configOperationService.publishConfig(configForm, new ConfigRequestInfo(), null);
        if (null != toolSpecification && !toolSpecification.isEmpty()) {
            // TODO create tool specification.
        }
    }
    
    /**
     * Delete existed mcp server.
     *
     * @param namespaceId namespace id of mcp server
     * @param mcpName     name of mcp server
     * @throws NacosException any exception during handling
     */
    public void deleteMcpServer(String namespaceId, String mcpName) throws NacosException {
        configOperationService.deleteConfig(mcpName + Constants.MCP_SPECIFICATION_DATA_ID_SUFFIX,
                Constants.MCP_SERVER_GROUP, namespaceId, null, null, "nacos", null);
    }
    
    private ConfigFormV3 buildMcpConfigForm(String namespaceId, String mcpName,
            McpServerBasicInfo serverSpecification) {
        ConfigFormV3 configFormV3 = new ConfigFormV3();
        configFormV3.setGroupName(Constants.MCP_SERVER_GROUP);
        configFormV3.setGroup(Constants.MCP_SERVER_GROUP);
        configFormV3.setNamespaceId(namespaceId);
        configFormV3.setDataId(mcpName + Constants.MCP_SPECIFICATION_DATA_ID_SUFFIX);
        configFormV3.setContent(JacksonUtils.toJson(serverSpecification));
        configFormV3.setType(ConfigType.JSON.getType());
        configFormV3.setAppName(mcpName);
        configFormV3.setSrcUser("nacos");
        return configFormV3;
    }
    
    private ConfigQueryChainRequest buildQueryMcpServerRequest(String namespaceId, String mcpName) {
        ConfigQueryChainRequest request = new ConfigQueryChainRequest();
        request.setDataId(mcpName + Constants.MCP_SPECIFICATION_DATA_ID_SUFFIX);
        request.setGroup(Constants.MCP_SERVER_GROUP);
        request.setTenant(namespaceId);
        return request;
    }
    
    private McpServerBasicInfo transferToMcpServerBasicInfo(ConfigInfo configInfo) {
        return JacksonUtils.toObj(configInfo.getContent(), McpServerBasicInfo.class);
    }
}
