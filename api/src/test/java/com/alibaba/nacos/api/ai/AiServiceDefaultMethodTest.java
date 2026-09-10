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

import com.alibaba.nacos.api.ai.listener.AbstractNacosMcpServerListener;
import com.alibaba.nacos.api.ai.model.mcp.McpEndpointSpec;
import com.alibaba.nacos.api.ai.model.agent.AgentPublishRequest;
import com.alibaba.nacos.api.ai.model.mcp.McpResourceSpecification;
import com.alibaba.nacos.api.ai.model.mcp.McpServerBasicInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpServerDetailInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpToolSpecification;
import com.alibaba.nacos.client.ai.NacosAiService;
import com.alibaba.nacos.api.exception.NacosException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AiServiceDefaultMethodTest {
    
    private AtomicBoolean invokeMark;
    
    AiService aiService;
    
    @BeforeEach
    void setUp() throws NacosException {
        invokeMark = new AtomicBoolean(false);
        NacosAiService.IS_THROW_EXCEPTION.set(false);
        aiService = new NacosAiService(new Properties()) {
            
            @Override
            public McpServerDetailInfo getMcpServer(String mcpName, String version)
                throws NacosException {
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
            public String releaseMcpServer(McpServerBasicInfo serverSpecification,
                McpToolSpecification toolSpecification,
                McpResourceSpecification resourceSpecification,
                McpEndpointSpec endpointSpecification) throws NacosException {
                invokeMark.set(true);
                return "";
            }
            
            @Override
            public void registerMcpServerEndpoint(String mcpName, String address, int port,
                String version)
                throws NacosException {
                invokeMark.set(true);
            }
            
            @Override
            public void deregisterMcpServerEndpoint(String mcpName, String address, int port)
                throws NacosException {
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
    void releaseMcpServerWithResourceSpecification() throws NacosException {
        McpServerBasicInfo serverSpecification = new McpServerBasicInfo();
        aiService.releaseMcpServer(serverSpecification, null, new McpResourceSpecification());
        assertTrue(invokeMark.get());
    }
    
    @Test
    void releaseMcpServerWithFalseDraftFlagPreservesLegacyDelegate() throws NacosException {
        aiService.releaseMcpServer(new McpServerBasicInfo(), null, null, null, false);
        assertTrue(invokeMark.get());
    }
    
    @Test
    void releaseMcpServerWithTrueDraftFlagRequiresImplementation() {
        NacosException exception = assertThrows(NacosException.class,
            () -> aiService.releaseMcpServer(new McpServerBasicInfo(), null, null, null, true));
        assertEquals(NacosException.SERVER_NOT_IMPLEMENTED, exception.getErrCode());
    }
    
    @Test
    void subscribeMcpServer() throws NacosException {
        aiService.subscribeMcpServer("", null);
    }
    
    @Test
    void unsubscribeMcpServer() throws NacosException {
        aiService.unsubscribeMcpServer("", null);
    }
    
    @Test
    void publishAgentDefaultsToNotImplemented() {
        NacosException exception = assertThrows(NacosException.class,
            () -> org.mockito.Mockito
                .mock(AgentService.class, org.mockito.Mockito.CALLS_REAL_METHODS)
                .publishAgent(new AgentPublishRequest()));
        assertEquals(NacosException.SERVER_NOT_IMPLEMENTED, exception.getErrCode());
    }
    
    @Test
    void resourceAccessorsDefaultToUnsupportedWithoutChangingLegacyOverrides() throws Exception {
        assertThrows(UnsupportedOperationException.class, aiService::mcp);
        assertThrows(UnsupportedOperationException.class, aiService::agent);
        assertThrows(UnsupportedOperationException.class, aiService::skill);
        assertThrows(UnsupportedOperationException.class, aiService::agentSpec);
        assertThrows(UnsupportedOperationException.class, aiService::prompt);
        aiService.getMcpServer("legacy");
        assertTrue(invokeMark.get());
    }
    
    @Test
    void coreDefaultsDelegateToResourceServices() throws Exception {
        McpService mcp = org.mockito.Mockito.mock(McpService.class);
        AgentService agent = org.mockito.Mockito.mock(AgentService.class);
        SkillService skill = org.mockito.Mockito.mock(SkillService.class);
        AgentSpecService spec = org.mockito.Mockito.mock(AgentSpecService.class);
        PromptService prompt = org.mockito.Mockito.mock(PromptService.class);
        AiService facade = new AiService() {
            
            @Override
            public McpService mcp() {
                return mcp;
            }
            
            @Override
            public AgentService agent() {
                return agent;
            }
            
            @Override
            public SkillService skill() {
                return skill;
            }
            
            @Override
            public AgentSpecService agentSpec() {
                return spec;
            }
            
            @Override
            public PromptService prompt() {
                return prompt;
            }
            
            @Override
            public void shutdown() {
            }
        };
        facade.getMcpServer("mcp");
        facade.getAgentCard("agent");
        facade.downloadSkillZip("skill");
        facade.loadAgentSpec("spec");
        facade.getPromptByLabel("prompt", "stable");
        org.mockito.Mockito.verify(mcp).getMcpServer("mcp", null);
        org.mockito.Mockito.verify(agent).getAgentCard("agent", "", "");
        org.mockito.Mockito.verify(skill).downloadSkillZip("skill");
        org.mockito.Mockito.verify(spec).loadAgentSpec("spec");
        org.mockito.Mockito.verify(prompt).getPromptByLabel("prompt", "stable");
        org.junit.jupiter.api.Assertions
            .assertFalse(AgentDiscoveryService.class.isAssignableFrom(AiService.class));
        assertTrue(AgentDiscoveryService.class.isAssignableFrom(AgentService.class));
        assertTrue(A2aService.class.isAssignableFrom(AgentService.class));
    }
}
