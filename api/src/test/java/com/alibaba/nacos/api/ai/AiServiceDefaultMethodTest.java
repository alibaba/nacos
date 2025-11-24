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

package com.alibaba.nacos.api.ai;

import com.alibaba.nacos.api.ai.listener.AbstractNacosAgentCardListener;
import com.alibaba.nacos.api.ai.listener.AbstractNacosMcpServerListener;
import com.alibaba.nacos.api.ai.model.a2a.AgentCard;
import com.alibaba.nacos.api.ai.model.a2a.AgentCardDetailInfo;
import com.alibaba.nacos.api.ai.model.a2a.AgentEndpoint;
import com.alibaba.nacos.api.ai.model.mcp.McpEndpointSpec;
import com.alibaba.nacos.api.ai.model.mcp.McpServerBasicInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpServerDetailInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpToolSpecification;
import com.alibaba.nacos.api.exception.NacosException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AiServiceDefaultMethodTest {
    
    private AtomicBoolean invokeMark;
    
    AiService aiService;
    
    @BeforeEach
    void setUp() {
        invokeMark = new AtomicBoolean(false);
        aiService = new AiService() {
            @Override
            public McpServerDetailInfo getMcpServer(String mcpName, String version) throws NacosException {
                invokeMark.set(true);
                return null;
            }
            
            @Override
            public String releaseMcpServer(McpServerBasicInfo serverSpecification,
                    McpToolSpecification toolSpecification, McpEndpointSpec endpointSpecification)
                    throws NacosException {
                invokeMark.set(true);
                return "";
            }
            
            @Override
            public void registerMcpServerEndpoint(String mcpName, String address, int port, String version)
                    throws NacosException {
                invokeMark.set(true);
            }
            
            @Override
            public void deregisterMcpServerEndpoint(String mcpName, String address, int port) throws NacosException {
            }
            
            @Override
            public McpServerDetailInfo subscribeMcpServer(String mcpName, String version,
                    AbstractNacosMcpServerListener mcpServerListener) throws NacosException {
                invokeMark.set(true);
                return null;
            }
            
            @Override
            public void unsubscribeMcpServer(String mcpName, String version,
                    AbstractNacosMcpServerListener mcpServerListener) throws NacosException {
                invokeMark.set(true);
            }
            
            @Override
            public AgentCardDetailInfo getAgentCard(String agentName, String version, String registrationType)
                    throws NacosException {
                return null;
            }
            
            @Override
            public void releaseAgentCard(AgentCard agentCard, String registrationType, boolean setAsLatest)
                    throws NacosException {
                
            }
            
            @Override
            public void registerAgentEndpoint(String agentName, AgentEndpoint endpoint) throws NacosException {
                
            }
            
            @Override
            public void registerAgentEndpoint(String agentName, Collection<AgentEndpoint> endpoints)
                    throws NacosException {
                
            }
            
            @Override
            public void deregisterAgentEndpoint(String agentName, AgentEndpoint endpoint) throws NacosException {
                
            }
            
            @Override
            public AgentCardDetailInfo subscribeAgentCard(String agentName, String version,
                    AbstractNacosAgentCardListener agentCardListener) throws NacosException {
                return null;
            }
            
            @Override
            public void unsubscribeAgentCard(String agentName, String version,
                    AbstractNacosAgentCardListener agentCardListener) throws NacosException {
            }
            
            @Override
            public void shutdown() throws NacosException {
            }
        };
    }
    
    @Test
    void getMcpServer() throws NacosException {
        aiService.getMcpServer("");
        assertTrue(invokeMark.get());
    }
    
    @Test
    void registerMcpServerEndpoint() throws NacosException {
        aiService.registerMcpServerEndpoint("", "", 0);
        assertTrue(invokeMark.get());
    }
    
    @Test
    void releaseMcpServer() throws NacosException {
        McpServerBasicInfo serverSpecification = new McpServerBasicInfo();
        aiService.releaseMcpServer(serverSpecification, null);
        assertTrue(invokeMark.get());
    }
    
    @Test
    void subscribeMcpServer() throws NacosException {
        aiService.subscribeMcpServer("", null);
    }
    
    @Test
    void unsubscribeMcpServer() throws NacosException {
        aiService.unsubscribeMcpServer("", null);
    }
}