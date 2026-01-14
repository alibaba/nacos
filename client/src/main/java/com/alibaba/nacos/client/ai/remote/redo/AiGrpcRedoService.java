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

package com.alibaba.nacos.client.ai.remote.redo;

import com.alibaba.nacos.api.remote.RemoteConstants;
import com.alibaba.nacos.client.ai.remote.AiGrpcClient;
import com.alibaba.nacos.client.env.NacosClientProperties;
import com.alibaba.nacos.client.redo.data.RedoData;
import com.alibaba.nacos.client.redo.service.AbstractRedoService;
import com.alibaba.nacos.client.redo.service.AbstractRedoTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

/**
 * Nacos AI module redo service.
 *
 * @author xiweng.yy
 */
public class AiGrpcRedoService extends AbstractRedoService {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(AiGrpcRedoService.class);
    
    private final AiGrpcClient aiGrpcClient;
    
    public AiGrpcRedoService(NacosClientProperties properties, AiGrpcClient aiGrpcClient) {
        super(LOGGER, properties, RemoteConstants.LABEL_MODULE_AI);
        this.aiGrpcClient = aiGrpcClient;
        startRedoTask();
    }
    
    @Override
    protected AbstractRedoTask buildRedoTask() {
        return new AiRedoScheduledTask(this, aiGrpcClient);
    }
    
    public void cachedMcpServerEndpointForRedo(String mcpName, String address, int port, String version) {
        RedoData<McpServerEndpoint> redoData = buildMcpServerEndpointRedoData(mcpName, address, port, version);
        super.cachedRedoData(mcpName, redoData, McpServerEndpoint.class);
    }
    
    public void removeMcpServerEndpointForRedo(String mcpName) {
        super.removeRedoData(mcpName, McpServerEndpoint.class);
    }
    
    public void mcpServerEndpointRegistered(String mcpName) {
        super.dataRegistered(mcpName, McpServerEndpoint.class);
    }
    
    public void mcpServerEndpointDeregister(String mcpName) {
        super.dataDeregister(mcpName, McpServerEndpoint.class);
    }
    
    public void mcpServerEndpointDeregistered(String mcpName) {
        super.dataDeregistered(mcpName, McpServerEndpoint.class);
    }
    
    public boolean isMcpServerEndpointRegistered(String mcpName) {
        return super.isDataRegistered(mcpName, McpServerEndpoint.class);
    }
    
    public Set<RedoData<McpServerEndpoint>> findMcpServerEndpointRedoData() {
        return super.findRedoData(McpServerEndpoint.class);
    }
    
    public McpServerEndpoint getMcpServerEndpoint(String mcpName) {
        RedoData<McpServerEndpoint> redoData = super.getRedoData(mcpName, McpServerEndpoint.class);
        return redoData == null ? null : redoData.get();
    }
    
    private RedoData<McpServerEndpoint> buildMcpServerEndpointRedoData(String mcpName, String address, int port,
            String version) {
        McpServerEndpoint mcpServerEndpoint = new McpServerEndpoint(address, port, version);
        McpServerEndpointRedoData result = new McpServerEndpointRedoData(mcpName);
        result.set(mcpServerEndpoint);
        return result;
    }
    
    public void cachedAgentEndpointForRedo(String agentName, AgentEndpointWrapper wrapper) {
        AgentEndpointRedoData redoData = new AgentEndpointRedoData(agentName, wrapper);
        super.cachedRedoData(agentName, redoData, AgentEndpointWrapper.class);
    }
    
    public void removeAgentEndpointForRedo(String agentName) {
        super.removeRedoData(agentName, AgentEndpointWrapper.class);
    }
    
    public void agentEndpointRegistered(String agentName) {
        super.dataRegistered(agentName, AgentEndpointWrapper.class);
    }
    
    public void agentEndpointDeregister(String agentName) {
        super.dataDeregister(agentName, AgentEndpointWrapper.class);
    }
    
    public void agentEndpointDeregistered(String agentName) {
        super.dataDeregistered(agentName, AgentEndpointWrapper.class);
    }
    
    public boolean isAgentEndpointRegistered(String agentName) {
        return super.isDataRegistered(agentName, AgentEndpointWrapper.class);
    }
    
    public Set<RedoData<AgentEndpointWrapper>> findAgentEndpointRedoData() {
        return super.findRedoData(AgentEndpointWrapper.class);
    }
    
    public AgentEndpointWrapper getAgentEndpoint(String agentName) {
        RedoData<AgentEndpointWrapper> redoData = super.getRedoData(agentName, AgentEndpointWrapper.class);
        return redoData == null ? null : redoData.get();
    }
}
