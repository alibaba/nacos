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

import com.alibaba.nacos.api.ai.model.rad.AgentEndpointRegistrationBatch;
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
    
    /**
     * Cache MCP server endpoint for redo.
     *
     * @param mcpName the MCP name
     * @param address the address
     * @param port the port
     * @param version the version
     */
    public void cachedMcpServerEndpointForRedo(String mcpName, String address, int port,
        String version) {
        RedoData<McpServerEndpoint> redoData =
            buildMcpServerEndpointRedoData(mcpName, address, port, version);
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
    
    private RedoData<McpServerEndpoint> buildMcpServerEndpointRedoData(String mcpName,
        String address, int port,
        String version) {
        McpServerEndpoint mcpServerEndpoint = new McpServerEndpoint(address, port, version);
        McpServerEndpointRedoData result = new McpServerEndpointRedoData(mcpName);
        result.set(mcpServerEndpoint);
        return result;
    }
    
    public void cachedAgentEndpointForRedo(String agentName, AgentEndpointWrapper wrapper) {
        AgentEndpointRedoData redoData = new AgentEndpointRedoData(agentName, wrapper);
        super.cachedRedoData(redoData.getKey(), redoData, AgentEndpointWrapper.class);
    }
    
    public void removeAgentEndpointForRedo(String key) {
        super.removeRedoData(key, AgentEndpointWrapper.class);
    }
    
    public void agentEndpointRegistered(String agentName, String version) {
        super.dataRegistered(AgentEndpointRedoData.keyOf(agentName, version),
            AgentEndpointWrapper.class);
    }
    
    public void agentEndpointDeregister(String agentName, String version) {
        super.dataDeregister(AgentEndpointRedoData.keyOf(agentName, version),
            AgentEndpointWrapper.class);
    }
    
    public void agentEndpointDeregistered(String agentName, String version) {
        super.dataDeregistered(AgentEndpointRedoData.keyOf(agentName, version),
            AgentEndpointWrapper.class);
    }
    
    public boolean isAgentEndpointRegistered(String agentName, String version) {
        return super.isDataRegistered(AgentEndpointRedoData.keyOf(agentName, version),
            AgentEndpointWrapper.class);
    }
    
    public Set<RedoData<AgentEndpointWrapper>> findAgentEndpointRedoData() {
        return super.findRedoData(AgentEndpointWrapper.class);
    }
    
    public AgentEndpointWrapper getAgentEndpoint(String agentName, String version) {
        RedoData<AgentEndpointWrapper> redoData =
            super.getRedoData(AgentEndpointRedoData.keyOf(agentName, version),
                AgentEndpointWrapper.class);
        return redoData == null ? null : redoData.get();
    }
    
    /**
     * Cache one complete RAD Agent Endpoint batch for reconnect redo.
     *
     * @param batch complete registration batch
     */
    public void cacheAgentEndpointPublication(AgentEndpointRegistrationBatch batch) {
        AgentEndpointPublicationRedoData redoData =
            new AgentEndpointPublicationRedoData(batch);
        super.cachedRedoData(redoData.getKey(), redoData, AgentEndpointRegistrationBatch.class);
    }
    
    /**
     * Mark a complete RAD Agent Endpoint publication registered.
     *
     * @param key publication key
     */
    public void agentEndpointPublicationRegistered(String key) {
        super.dataRegistered(key, AgentEndpointRegistrationBatch.class);
    }
    
    /**
     * Mark a complete RAD Agent Endpoint publication for deregistration.
     *
     * @param key publication key
     */
    public void agentEndpointPublicationDeregistering(String key) {
        super.dataDeregister(key, AgentEndpointRegistrationBatch.class);
    }
    
    /**
     * Mark a complete RAD Agent Endpoint publication deregistered.
     *
     * @param key publication key
     */
    public void agentEndpointPublicationDeregistered(String key) {
        super.dataDeregistered(key, AgentEndpointRegistrationBatch.class);
    }
    
    /**
     * Remove a completed RAD Agent Endpoint publication redo record.
     *
     * @param key publication key
     */
    public void removeAgentEndpointPublication(String key) {
        super.removeRedoData(key, AgentEndpointRegistrationBatch.class);
    }
    
    /**
     * Find complete RAD Agent Endpoint publications that need redo.
     *
     * @return pending redo records
     */
    public Set<RedoData<AgentEndpointRegistrationBatch>> findAgentEndpointPublicationRedoData() {
        return super.findRedoData(AgentEndpointRegistrationBatch.class);
    }
    
    /**
     * Return one cached complete RAD Agent Endpoint publication.
     *
     * @param key publication key
     * @return cached batch, or {@code null}
     */
    public AgentEndpointRegistrationBatch getAgentEndpointPublication(String key) {
        RedoData<AgentEndpointRegistrationBatch> redoData =
            super.getRedoData(key, AgentEndpointRegistrationBatch.class);
        return redoData == null ? null : redoData.get();
    }
    
    /**
     * Whether one RAD Agent Endpoint publication is registered.
     *
     * @param key publication key
     * @return registered state
     */
    public boolean isAgentEndpointPublicationRegistered(String key) {
        return super.isDataRegistered(key, AgentEndpointRegistrationBatch.class);
    }
    
    /**
     * Discard a non-retryable RAD Agent Endpoint publication intent.
     *
     * @param key publication key
     */
    public void discardAgentEndpointPublication(String key) {
        super.dataDeregister(key, AgentEndpointRegistrationBatch.class);
        super.dataDeregistered(key, AgentEndpointRegistrationBatch.class);
        super.removeRedoData(key, AgentEndpointRegistrationBatch.class);
    }
}
