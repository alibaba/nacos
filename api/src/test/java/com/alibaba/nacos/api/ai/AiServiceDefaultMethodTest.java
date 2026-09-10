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

import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.api.ai.listener.AbstractNacosAgentCardListener;
import com.alibaba.nacos.api.ai.listener.AbstractNacosAgentSpecListener;
import com.alibaba.nacos.api.ai.listener.AbstractNacosMcpServerListener;
import com.alibaba.nacos.api.ai.listener.AbstractNacosPromptListener;
import com.alibaba.nacos.api.ai.listener.AbstractNacosSkillListener;
import com.alibaba.nacos.api.ai.model.a2a.AgentCard;
import com.alibaba.nacos.api.ai.model.a2a.AgentCardDetailInfo;
import com.alibaba.nacos.api.ai.model.a2a.AgentEndpoint;
import com.alibaba.nacos.api.ai.model.agentspecs.AgentSpec;
import com.alibaba.nacos.api.ai.model.prompt.Prompt;
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
import org.mockito.ArgumentCaptor;

import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AiServiceDefaultMethodTest {
    
    private AtomicBoolean invokeMark;
    
    AiService aiService;
    
    private AiService facade;
    
    private McpService mcp;
    
    private AgentService agent;
    
    private SkillService skill;
    
    private AgentSpecService spec;
    
    private PromptService prompt;
    
    @BeforeEach
    void setUp() throws NacosException {
        mcp = mock(McpService.class);
        agent = mock(AgentService.class);
        skill = mock(SkillService.class);
        spec = mock(AgentSpecService.class);
        prompt = mock(PromptService.class);
        facade = mock(AiService.class, CALLS_REAL_METHODS);
        doReturn(mcp).when(facade).mcp();
        doReturn(agent).when(facade).agent();
        doReturn(skill).when(facade).skill();
        doReturn(spec).when(facade).agentSpec();
        doReturn(prompt).when(facade).prompt();
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
        assertTrue(invokeMark.get());
    }
    
    @Test
    void unsubscribeMcpServer() throws NacosException {
        aiService.unsubscribeMcpServer("", null);
        assertTrue(invokeMark.get());
    }
    
    @Test
    void publishAgentDefaultsToNotImplemented() {
        NacosException exception = assertThrows(NacosException.class,
            () -> mock(AgentService.class, CALLS_REAL_METHODS)
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
        facade.getMcpServer("mcp");
        facade.getAgentCard("agent");
        facade.downloadSkillZip("skill");
        facade.loadAgentSpec("spec");
        facade.getPromptByLabel("prompt", "stable");
        verify(mcp).getMcpServer("mcp", null);
        verify(agent).getAgentCard("agent", "", "");
        verify(skill).downloadSkillZip("skill");
        verify(spec).loadAgentSpec("spec");
        verify(prompt).getPromptByLabel("prompt", "stable");
        assertFalse(AgentDiscoveryService.class.isAssignableFrom(AiService.class));
        assertTrue(AgentDiscoveryService.class.isAssignableFrom(AgentService.class));
        assertTrue(A2aService.class.isAssignableFrom(AgentService.class));
    }
    
    @Test
    void mcpReadsAndSubscriptionsPreserveResultsAndListeners() throws Exception {
        McpServerDetailInfo detail = new McpServerDetailInfo();
        AbstractNacosMcpServerListener listener = mock(AbstractNacosMcpServerListener.class);
        when(mcp.getMcpServer("mcp", "1.0.0")).thenReturn(detail);
        when(mcp.subscribeMcpServer("mcp", null, listener)).thenReturn(detail);
        when(mcp.subscribeMcpServer("mcp", "1.0.0", listener)).thenReturn(detail);
        
        assertSame(detail, facade.getMcpServer("mcp", "1.0.0"));
        assertSame(detail, facade.subscribeMcpServer("mcp", listener));
        assertSame(detail, facade.subscribeMcpServer("mcp", "1.0.0", listener));
        facade.unsubscribeMcpServer("mcp", listener);
        facade.unsubscribeMcpServer("mcp", "1.0.0", listener);
        
        verify(mcp).unsubscribeMcpServer("mcp", null, listener);
        verify(mcp).unsubscribeMcpServer("mcp", "1.0.0", listener);
        verifyNoInteractions(agent, skill, spec, prompt);
    }
    
    @Test
    void mcpReleaseOverloadsPreserveOptionalContentAndResult() throws Exception {
        McpServerBasicInfo server = new McpServerBasicInfo();
        McpToolSpecification tools = new McpToolSpecification();
        McpResourceSpecification resources = new McpResourceSpecification();
        McpEndpointSpec endpoint = new McpEndpointSpec();
        when(mcp.releaseMcpServer(server, tools, (McpEndpointSpec) null)).thenReturn("minimal");
        when(mcp.releaseMcpServer(server, tools, endpoint)).thenReturn("endpoint");
        when(mcp.releaseMcpServer(server, tools, resources, null)).thenReturn("resources");
        when(mcp.releaseMcpServer(server, tools, resources, endpoint)).thenReturn("complete");
        when(mcp.releaseMcpServer(server, tools, null, null)).thenReturn("online");
        
        assertEquals("minimal", facade.releaseMcpServer(server, tools));
        assertEquals("endpoint", facade.releaseMcpServer(server, tools, endpoint));
        assertEquals("resources", facade.releaseMcpServer(server, tools, resources));
        assertEquals("complete", facade.releaseMcpServer(server, tools, resources, endpoint));
        assertEquals("online", facade.releaseMcpServer(server, tools, false));
        verify(mcp).releaseMcpServer(server, tools, null, null);
        verifyNoInteractions(agent, skill, spec, prompt);
    }
    
    @Test
    void mcpDraftConvenienceMethodNeverDirectPublishes() {
        NacosException failure = assertThrows(NacosException.class,
            () -> facade.releaseMcpServer(new McpServerBasicInfo(), null, true));
        assertEquals(NacosException.SERVER_NOT_IMPLEMENTED, failure.getErrCode());
        verifyNoInteractions(mcp);
    }
    
    @Test
    void mcpEndpointDelegatesPreserveVersionAndAddress() throws Exception {
        facade.registerMcpServerEndpoint("mcp", "127.0.0.1", 8080);
        facade.registerMcpServerEndpoint("mcp", "127.0.0.2", 8081, "1.0.0");
        facade.deregisterMcpServerEndpoint("mcp", "127.0.0.2", 8081);
        
        verify(mcp).registerMcpServerEndpoint("mcp", "127.0.0.1", 8080, null);
        verify(mcp).registerMcpServerEndpoint("mcp", "127.0.0.2", 8081, "1.0.0");
        verify(mcp).deregisterMcpServerEndpoint("mcp", "127.0.0.2", 8081);
        verifyNoInteractions(agent, skill, spec, prompt);
    }
    
    @Test
    void agentCardDelegatesPreserveSelectorsAndReleaseFlags() throws Exception {
        AgentCardDetailInfo detail = new AgentCardDetailInfo();
        AgentCard card = new AgentCard();
        when(agent.getAgentCard("agent", "", "")).thenReturn(detail);
        when(agent.getAgentCard("agent", "1.0.0", "")).thenReturn(detail);
        when(agent.getAgentCard("agent", "1.0.0", "URL")).thenReturn(detail);
        
        assertSame(detail, facade.getAgentCard("agent"));
        assertSame(detail, facade.getAgentCard("agent", "1.0.0"));
        assertSame(detail, facade.getAgentCard("agent", "1.0.0", "URL"));
        facade.releaseAgentCard(card);
        facade.releaseAgentCard(card, "URL");
        facade.releaseAgentCard(card, "URL", true);
        
        verify(agent).releaseAgentCard(card, AiConstants.A2a.A2A_ENDPOINT_TYPE_SERVICE, false);
        verify(agent).releaseAgentCard(card, "URL", false);
        verify(agent).releaseAgentCard(card, "URL", true);
        verifyNoInteractions(mcp, skill, spec, prompt);
    }
    
    @Test
    void agentEndpointOverloadsPreserveDefaultsAndExplicitFields() throws Exception {
        facade.registerAgentEndpoint("agent", "1.0.0", "127.0.0.1", 8080);
        facade.registerAgentEndpoint("agent", "1.0.0", "127.0.0.1", 8080, "GRPC");
        facade.registerAgentEndpoint("agent", "1.0.0", "127.0.0.1", 8080, "GRPC", "/a2a");
        facade.registerAgentEndpoint("agent", "1.0.0", "127.0.0.1", 8080, "GRPC", "/a2a", true);
        
        ArgumentCaptor<AgentEndpoint> captor = ArgumentCaptor.forClass(AgentEndpoint.class);
        verify(agent, times(4)).registerAgentEndpoint(eq("agent"), captor.capture());
        List<AgentEndpoint> endpoints = captor.getAllValues();
        for (int i = 0; i < endpoints.size(); i++) {
            AgentEndpoint endpoint = endpoints.get(i);
            assertEquals("1.0.0", endpoint.getVersion());
            assertEquals("127.0.0.1", endpoint.getAddress());
            assertEquals(8080, endpoint.getPort());
            assertEquals(i == 0 ? AiConstants.A2a.A2A_ENDPOINT_DEFAULT_TRANSPORT : "GRPC",
                endpoint.getTransport());
            assertEquals(i < 2 ? "" : "/a2a", endpoint.getPath());
            assertEquals(i == 3, endpoint.isSupportTls());
        }
        verifyNoInteractions(mcp, skill, spec, prompt);
    }
    
    @Test
    void agentEndpointObjectsAndBatchesReachTheSameResource() throws Exception {
        AgentEndpoint endpoint = new AgentEndpoint();
        List<AgentEndpoint> endpoints = Collections.singletonList(endpoint);
        facade.registerAgentEndpoint("agent", endpoint);
        facade.registerAgentEndpoint("agent", endpoints);
        facade.deregisterAgentEndpoint("agent", endpoint);
        facade.deregisterAgentEndpoint("agent", "1.0.0", "127.0.0.1", 8080);
        
        verify(agent).registerAgentEndpoint("agent", endpoint);
        verify(agent).registerAgentEndpoint("agent", endpoints);
        ArgumentCaptor<AgentEndpoint> captor = ArgumentCaptor.forClass(AgentEndpoint.class);
        verify(agent, times(2)).deregisterAgentEndpoint(eq("agent"), captor.capture());
        assertSame(endpoint, captor.getAllValues().get(0));
        AgentEndpoint removed = captor.getAllValues().get(1);
        assertEquals("1.0.0", removed.getVersion());
        assertEquals("127.0.0.1", removed.getAddress());
        assertEquals(8080, removed.getPort());
    }
    
    @Test
    void agentSubscriptionsPreserveLatestAndExactVersionListeners() throws Exception {
        AgentCardDetailInfo detail = new AgentCardDetailInfo();
        AbstractNacosAgentCardListener listener = mock(AbstractNacosAgentCardListener.class);
        when(agent.subscribeAgentCard("agent", "", listener)).thenReturn(detail);
        when(agent.subscribeAgentCard("agent", "1.0.0", listener)).thenReturn(detail);
        
        assertSame(detail, facade.subscribeAgentCard("agent", listener));
        assertSame(detail, facade.subscribeAgentCard("agent", "1.0.0", listener));
        facade.unsubscribeAgentCard("agent", listener);
        facade.unsubscribeAgentCard("agent", "1.0.0", listener);
        
        verify(agent).unsubscribeAgentCard("agent", "", listener);
        verify(agent).unsubscribeAgentCard("agent", "1.0.0", listener);
    }
    
    @Test
    void skillDelegatesPreserveZipResultsAndSubscriptionSelectors() throws Exception {
        byte[] latest = {1};
        byte[] version = {2};
        byte[] label = {3};
        AbstractNacosSkillListener listener = mock(AbstractNacosSkillListener.class);
        when(skill.downloadSkillZip("skill")).thenReturn(latest);
        when(skill.downloadSkillZipByVersion("skill", "1.0.0")).thenReturn(version);
        when(skill.downloadSkillZipByLabel("skill", "stable")).thenReturn(label);
        when(skill.subscribeSkill("skill", "1.0.0", "stable", listener)).thenReturn(version);
        
        assertSame(latest, facade.downloadSkillZip("skill"));
        assertSame(version, facade.downloadSkillZipByVersion("skill", "1.0.0"));
        assertSame(label, facade.downloadSkillZipByLabel("skill", "stable"));
        assertSame(version, facade.subscribeSkill("skill", "1.0.0", "stable", listener));
        facade.unsubscribeSkill("skill", "1.0.0", "stable", listener);
        
        verify(skill).unsubscribeSkill("skill", "1.0.0", "stable", listener);
        verifyNoInteractions(mcp, agent, spec, prompt);
    }
    
    @Test
    void agentSpecDelegatesPreserveResultAndListener() throws Exception {
        AgentSpec result = new AgentSpec();
        AbstractNacosAgentSpecListener listener = mock(AbstractNacosAgentSpecListener.class);
        when(spec.loadAgentSpec("spec")).thenReturn(result);
        when(spec.subscribeAgentSpec("spec", listener)).thenReturn(result);
        
        assertSame(result, facade.loadAgentSpec("spec"));
        assertSame(result, facade.subscribeAgentSpec("spec", listener));
        facade.unsubscribeAgentSpec("spec", listener);
        
        verify(spec).unsubscribeAgentSpec("spec", listener);
        verifyNoInteractions(mcp, agent, skill, prompt);
    }
    
    @Test
    void promptDelegatesPreserveSelectorsAndListener() throws Exception {
        Prompt latest = new Prompt();
        Prompt version = new Prompt();
        Prompt label = new Prompt();
        AbstractNacosPromptListener listener = mock(AbstractNacosPromptListener.class);
        when(prompt.getPrompt("prompt")).thenReturn(latest);
        when(prompt.getPromptByVersion("prompt", "1.0.0")).thenReturn(version);
        when(prompt.getPromptByLabel("prompt", "stable")).thenReturn(label);
        when(prompt.subscribePrompt("prompt", "1.0.0", "stable", listener)).thenReturn(version);
        
        assertSame(latest, facade.getPrompt("prompt"));
        assertSame(version, facade.getPromptByVersion("prompt", "1.0.0"));
        assertSame(label, facade.getPromptByLabel("prompt", "stable"));
        assertSame(version, facade.subscribePrompt("prompt", "1.0.0", "stable", listener));
        facade.unsubscribePrompt("prompt", "1.0.0", "stable", listener);
        
        verify(prompt).unsubscribePrompt("prompt", "1.0.0", "stable", listener);
        verifyNoInteractions(mcp, agent, skill, spec);
    }
    
    @Test
    void delegatesPreserveTypedFailuresFromEveryResource() throws Exception {
        NacosException failure = new NacosException(NacosException.NO_RIGHT, "denied");
        when(mcp.getMcpServer("mcp", null)).thenThrow(failure);
        when(agent.getAgentCard("agent", "", "")).thenThrow(failure);
        when(skill.downloadSkillZip("skill")).thenThrow(failure);
        when(spec.loadAgentSpec("spec")).thenThrow(failure);
        when(prompt.getPrompt("prompt")).thenThrow(failure);
        
        assertSame(failure, assertThrows(NacosException.class, () -> facade.getMcpServer("mcp")));
        assertSame(failure, assertThrows(NacosException.class, () -> facade.getAgentCard("agent")));
        assertSame(failure,
            assertThrows(NacosException.class, () -> facade.downloadSkillZip("skill")));
        assertSame(failure, assertThrows(NacosException.class, () -> facade.loadAgentSpec("spec")));
        assertSame(failure, assertThrows(NacosException.class, () -> facade.getPrompt("prompt")));
    }
    
    @Test
    void delegatesPreserveAbsentResourceResults() throws Exception {
        assertNull(facade.getMcpServer("missing"));
        assertNull(facade.getAgentCard("missing"));
        assertNull(facade.downloadSkillZip("missing"));
        assertNull(facade.loadAgentSpec("missing"));
        assertNull(facade.getPrompt("missing"));
        verify(mcp).getMcpServer("missing", null);
        verify(agent).getAgentCard("missing", "", "");
        verify(skill).downloadSkillZip("missing");
        verify(spec).loadAgentSpec("missing");
        verify(prompt).getPrompt("missing");
    }
}
