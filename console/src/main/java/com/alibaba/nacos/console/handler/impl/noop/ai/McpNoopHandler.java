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

package com.alibaba.nacos.console.handler.impl.noop.ai;

import com.alibaba.nacos.api.ai.model.mcp.McpEndpointSpec;
import com.alibaba.nacos.api.ai.model.mcp.McpServerBasicInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpServerDetailInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpServerImportRequest;
import com.alibaba.nacos.api.ai.model.mcp.McpServerImportResponse;
import com.alibaba.nacos.api.ai.model.mcp.McpServerImportValidationResult;
import com.alibaba.nacos.api.ai.model.mcp.McpToolSpecification;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.console.handler.ai.McpHandler;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Service;

/**
 * Noop implementation of Mcp handler.
 * Used when `naming` or `config` module are not enabled.
 *
 * @author xiweng.yy
 */
@Service
@ConditionalOnMissingBean(value = McpHandler.class, ignored = McpNoopHandler.class)
public class McpNoopHandler implements McpHandler {
    
    private static final String MCP_NOT_ENABLED_MESSAGE = "Nacos AI MCP module and API required both `naming` and `config` module.";
    
    @Override
    public Page<McpServerBasicInfo> listMcpServers(String namespaceId, String mcpName, String search, int pageNo,
            int pageSize) throws NacosException {
        throw new NacosApiException(NacosException.SERVER_NOT_IMPLEMENTED, ErrorCode.API_FUNCTION_DISABLED,
                MCP_NOT_ENABLED_MESSAGE);
    }
    
    @Override
    public McpServerDetailInfo getMcpServer(String namespaceId, String mcpName, String mcpId, String version)
            throws NacosException {
        throw new NacosApiException(NacosException.SERVER_NOT_IMPLEMENTED, ErrorCode.API_FUNCTION_DISABLED,
                MCP_NOT_ENABLED_MESSAGE);
    }
    
    @Override
    public String createMcpServer(String namespaceId, McpServerBasicInfo serverSpecification,
            McpToolSpecification toolSpecification, McpEndpointSpec endpointSpecification) throws NacosException {
        throw new NacosApiException(NacosException.SERVER_NOT_IMPLEMENTED, ErrorCode.API_FUNCTION_DISABLED,
                MCP_NOT_ENABLED_MESSAGE);
    }
    
    @Override
    public void updateMcpServer(String namespaceId, boolean isPublish, McpServerBasicInfo serverSpecification,
            McpToolSpecification toolSpecification, McpEndpointSpec endpointSpecification) throws NacosException {
        throw new NacosApiException(NacosException.SERVER_NOT_IMPLEMENTED, ErrorCode.API_FUNCTION_DISABLED,
                MCP_NOT_ENABLED_MESSAGE);
    }
    
    @Override
    public void deleteMcpServer(String namespaceId, String mcpName, String mcpId, String version)
            throws NacosException {
        throw new NacosApiException(NacosException.SERVER_NOT_IMPLEMENTED, ErrorCode.API_FUNCTION_DISABLED,
                MCP_NOT_ENABLED_MESSAGE);
    }
    
    @Override
    public McpServerImportValidationResult validateImport(String namespaceId, McpServerImportRequest request) throws NacosException {
        throw new NacosApiException(NacosException.SERVER_NOT_IMPLEMENTED, ErrorCode.API_FUNCTION_DISABLED,
                MCP_NOT_ENABLED_MESSAGE);
    }
    
    @Override
    public McpServerImportResponse executeImport(String namespaceId, McpServerImportRequest request) throws NacosException {
        throw new NacosApiException(NacosException.SERVER_NOT_IMPLEMENTED, ErrorCode.API_FUNCTION_DISABLED,
                MCP_NOT_ENABLED_MESSAGE);
    }
}
