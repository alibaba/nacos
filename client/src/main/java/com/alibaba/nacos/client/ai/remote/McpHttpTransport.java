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

package com.alibaba.nacos.client.ai.remote;

import com.alibaba.nacos.api.ai.model.agent.ClientLivenessInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpEndpointSpec;
import com.alibaba.nacos.api.ai.model.mcp.McpResourceSpecification;
import com.alibaba.nacos.api.ai.model.mcp.McpServerBasicInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpServerDetailInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpToolSpecification;
import com.alibaba.nacos.api.exception.NacosException;

/**
 * HTTP MCP Client transport.
 *
 * @author Nacos
 */
public class McpHttpTransport implements McpTransport {
    
    private final AiHttpClientProxy clientProxy;
    
    public McpHttpTransport(AiHttpClientProxy clientProxy) {
        this.clientProxy = clientProxy;
    }
    
    @Override
    public AgentTransportType getType() {
        return AgentTransportType.HTTP;
    }
    
    @Override
    public McpServerDetailInfo queryMcpServer(String mcpName, String version)
        throws NacosException {
        return clientProxy.queryMcpServer(mcpName, version);
    }
    
    @Override
    public String releaseMcpServer(McpServerBasicInfo serverSpecification,
        McpToolSpecification toolSpecification,
        McpResourceSpecification resourceSpecification, McpEndpointSpec endpointSpecification,
        boolean createDraft) throws NacosException {
        return clientProxy.releaseMcpServer(serverSpecification, toolSpecification,
            resourceSpecification, endpointSpecification, createDraft);
    }
    
    @Override
    public ClientLivenessInfo registerMcpServerEndpoint(String mcpName, String address, int port,
        String version) throws NacosException {
        return clientProxy.registerMcpServerEndpoint(mcpName, address, port, version);
    }
    
    @Override
    public void deregisterMcpServerEndpoint(String mcpName, String address, int port)
        throws NacosException {
        clientProxy.deregisterMcpServerEndpoint(mcpName, address, port);
    }
    
    @Override
    public ClientLivenessInfo heartbeatMcpServerEndpoints() throws NacosException {
        return clientProxy.heartbeatMcpServerEndpoints();
    }
}
