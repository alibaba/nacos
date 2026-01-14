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

import com.alibaba.nacos.api.ai.model.a2a.AgentEndpoint;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.client.ai.remote.AiGrpcClient;
import com.alibaba.nacos.client.naming.remote.gprc.redo.data.NamingRedoData;
import com.alibaba.nacos.client.redo.data.RedoData;
import com.alibaba.nacos.client.redo.service.AbstractRedoTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Nacos AI module redo task.
 *
 * @author xiweng.yy
 */
public class AiRedoScheduledTask extends AbstractRedoTask<AiGrpcRedoService> {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(AiRedoScheduledTask.class);
    
    private final AiGrpcClient aiGrpcClient;
    
    public AiRedoScheduledTask(AiGrpcRedoService redoService, AiGrpcClient aiGrpcClient) {
        super(LOGGER, redoService);
        this.aiGrpcClient = aiGrpcClient;
    }
    
    @Override
    protected void redoData() throws NacosException {
        try {
            redoForMcpSeverEndpoint();
            redoForAgentEndpoint();
        } catch (Exception e) {
            LOGGER.warn("Redo task run with unexpected exception: ", e);
        }
    }
    
    private void redoForAgentEndpoint() {
        for (RedoData<AgentEndpointWrapper> each : getRedoService().findAgentEndpointRedoData()) {
            AgentEndpointRedoData redoData = (AgentEndpointRedoData) each;
            try {
                redoForAgentEndpoint(redoData);
            } catch (NacosException e) {
                LOGGER.error("Redo agent endpoint operation {} for {}} failed. ", each.getRedoType(),
                        redoData.getAgentName(), e);
            }
        }
    }
    
    private void redoForAgentEndpoint(AgentEndpointRedoData redoData) throws NacosException {
        NamingRedoData.RedoType redoType = redoData.getRedoType();
        String agentName = redoData.getAgentName();
        LOGGER.info("Redo agent endpoint operation {} for {}.", redoType, agentName);
        AgentEndpointWrapper wrapper = redoData.get();
        switch (redoType) {
            case REGISTER:
                if (!aiGrpcClient.isEnable()) {
                    return;
                }
                if (wrapper.isBatch()) {
                    aiGrpcClient.doRegisterAgentEndpoint(agentName, wrapper.getBatchData());
                } else {
                    aiGrpcClient.doRegisterAgentEndpoint(agentName, wrapper.getData());
                }
                break;
            case UNREGISTER:
                if (!aiGrpcClient.isEnable()) {
                    return;
                }
                AgentEndpoint endpoint = wrapper.isBatch() ? wrapper.getBatchData().stream().findFirst().get()
                        : wrapper.getData();
                aiGrpcClient.doDeregisterAgentEndpoint(agentName, endpoint);
                break;
            case REMOVE:
                getRedoService().removeAgentEndpointForRedo(agentName);
                break;
            default:
        }
    }
    
    private void redoForMcpSeverEndpoint() {
        for (RedoData<McpServerEndpoint> each : getRedoService().findMcpServerEndpointRedoData()) {
            McpServerEndpointRedoData redoData = (McpServerEndpointRedoData) each;
            try {
                redoForMcpServerEndpoint(redoData);
            } catch (NacosException e) {
                LOGGER.error("Redo mcp server endpoint operation {} for {}} failed. ", each.getRedoType(),
                        redoData.getMcpName(), e);
            }
        }
    }
    
    private void redoForMcpServerEndpoint(McpServerEndpointRedoData redoData) throws NacosException {
        NamingRedoData.RedoType redoType = redoData.getRedoType();
        String mcpName = redoData.getMcpName();
        LOGGER.info("Redo mcp server endpoint operation {} for {}.", redoType, mcpName);
        McpServerEndpoint endpoint = redoData.get();
        switch (redoType) {
            case REGISTER:
                if (!aiGrpcClient.isEnable()) {
                    return;
                }
                aiGrpcClient.doRegisterMcpServerEndpoint(mcpName, endpoint.getAddress(), endpoint.getPort(),
                        endpoint.getVersion());
                break;
            case UNREGISTER:
                if (!aiGrpcClient.isEnable()) {
                    return;
                }
                aiGrpcClient.doDeregisterMcpServerEndpoint(mcpName, endpoint.getAddress(), endpoint.getPort());
                break;
            case REMOVE:
                getRedoService().removeMcpServerEndpointForRedo(mcpName);
                break;
            default:
        }
    }
}
