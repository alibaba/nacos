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

package com.alibaba.nacos.console.handler.impl.remote.ai;

import com.alibaba.nacos.ai.constant.Constants;
import com.alibaba.nacos.api.ai.model.mcp.McpEndpointSpec;
import com.alibaba.nacos.api.ai.model.mcp.McpServerBasicInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpServerDetailInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpToolSpecification;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.console.handler.ai.McpHandler;
import com.alibaba.nacos.console.handler.impl.remote.EnabledRemoteHandler;
import com.alibaba.nacos.console.handler.impl.remote.NacosMaintainerClientHolder;
import org.springframework.stereotype.Service;

/**
 * Remote implementation of Mcp handler.
 *
 * @author xiweng.yy
 */
@Service
@EnabledRemoteHandler
public class McpRemoteHandler implements McpHandler {
    
    private final NacosMaintainerClientHolder clientHolder;
    
    public McpRemoteHandler(NacosMaintainerClientHolder clientHolder) {
        this.clientHolder = clientHolder;
    }
    
    @Override
    public Page<McpServerBasicInfo> listMcpServers(String namespaceId, String mcpName, String search, int pageNo,
            int pageSize) throws NacosException {
        if (Constants.MCP_LIST_SEARCH_ACCURATE.equalsIgnoreCase(search)) {
            return clientHolder.getAiMaintainerService().listMcpServer(mcpName, pageNo, pageSize);
        } else {
            return clientHolder.getAiMaintainerService().searchMcpServer(mcpName, pageNo, pageSize);
        }
    }
    
    @Override
    public McpServerDetailInfo getMcpServer(String namespaceId, String mcpName, String version) throws NacosException {
        return clientHolder.getAiMaintainerService().getMcpServerDetail(mcpName);
    }
    
    @Override
    public void createMcpServer(String namespaceId, String mcpName, McpServerBasicInfo serverSpecification,
            McpToolSpecification toolSpecification, McpEndpointSpec endpointSpecification) throws NacosException {
        clientHolder.getAiMaintainerService()
                .createMcpServer(mcpName, serverSpecification, toolSpecification, endpointSpecification);
    }
    
    @Override
    public void updateMcpServer(String namespaceId, String mcpName, boolean isPublish, McpServerBasicInfo serverSpecification,
            McpToolSpecification toolSpecification, McpEndpointSpec endpointSpecification) throws NacosException {
        clientHolder.getAiMaintainerService()
                .updateMcpServer(mcpName, serverSpecification, toolSpecification, endpointSpecification);
    }
    
    @Override
    public void deleteMcpServer(String namespaceId, String mcpName, String version) throws NacosException {
        clientHolder.getAiMaintainerService().deleteMcpServer(mcpName);
    }
}
