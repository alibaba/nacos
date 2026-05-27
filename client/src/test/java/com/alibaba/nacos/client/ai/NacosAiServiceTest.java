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

package com.alibaba.nacos.client.ai;

import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.ai.listener.AbstractNacosAgentCardListener;
import com.alibaba.nacos.api.ai.listener.AbstractNacosAgentSpecListener;
import com.alibaba.nacos.api.ai.listener.AbstractNacosMcpServerListener;
import com.alibaba.nacos.api.ai.listener.AbstractNacosPromptListener;
import com.alibaba.nacos.api.ai.listener.NacosAgentCardEvent;
import com.alibaba.nacos.api.ai.listener.NacosAgentSpecEvent;
import com.alibaba.nacos.api.ai.listener.NacosMcpServerEvent;
import com.alibaba.nacos.api.ai.listener.NacosPromptEvent;
import com.alibaba.nacos.api.ai.model.a2a.AgentCard;
import com.alibaba.nacos.api.ai.model.a2a.AgentCardDetailInfo;
import com.alibaba.nacos.api.ai.model.a2a.AgentEndpoint;
import com.alibaba.nacos.api.ai.model.a2a.AgentInterface;
import com.alibaba.nacos.api.ai.model.agentspecs.AgentSpec;
import com.alibaba.nacos.api.ai.model.mcp.McpServerBasicInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpServerDetailInfo;
import com.alibaba.nacos.api.ai.model.mcp.registry.ServerVersionDetail;
import com.alibaba.nacos.api.ai.model.prompt.Prompt;
import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.client.ai.cache.NacosAgentCardCacheHolder;
import com.alibaba.nacos.client.ai.cache.NacosAgentSpecCacheHolder;
import com.alibaba.nacos.client.ai.cache.NacosMcpServerCacheHolder;
import com.alibaba.nacos.client.ai.cache.NacosPromptCacheHolder;
import com.alibaba.nacos.client.ai.event.AgentCardListenerInvoker;
import com.alibaba.nacos.client.ai.event.AgentSpecListenerInvoker;
import com.alibaba.nacos.client.ai.event.AiChangeNotifier;
import com.alibaba.nacos.client.ai.event.McpServerListenerInvoker;
import com.alibaba.nacos.client.ai.event.PromptListenerInvoker;
import com.alibaba.nacos.client.ai.remote.AiClientProxy;
import com.alibaba.nacos.client.ai.remote.AiGrpcClient;
import com.alibaba.nacos.client.ai.remote.AiHttpClientProxy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Properties;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NacosAiServiceTest {
    
    @Mock
    private AiGrpcClient grpcClient;
    
    @Mock
    private NacosMcpServerCacheHolder mcpServerCacheHolder;
    
    @Mock
    private NacosAgentCardCacheHolder agentCardCacheHolder;
    
    @Mock
    private NacosPromptCacheHolder promptCacheHolder;
    
    @Mock
    private NacosAgentSpecCacheHolder agentSpecCacheHolder;
    
    @Mock
    private AiHttpClientProxy httpProxy;
    
    @Mock
    private AiClientProxy aiClientProxy;
    
    @Mock
    private AiChangeNotifier aiChangeNotifier;
    
    NacosAiService nacosAiService;
    
    @BeforeEach
    void setUp() throws NacosException {
        Properties properties = new Properties();
        properties.put(PropertyKeyConst.SERVER_ADDR, "127.0.0.1");
        nacosAiService = new NacosAiService(properties);
    }
    
    @AfterEach
    void tearDown() throws NacosException {
        if (null != nacosAiService) {
            nacosAiService.shutdown();
        }
    }
    
    @Test
    void testConstructorWithNamespace()
        throws NoSuchFieldException, IllegalAccessException, NacosException {
        Field field = NacosAiService.class.getDeclaredField("namespaceId");
        field.setAccessible(true);
        assertEquals(Constants.DEFAULT_NAMESPACE_ID, field.get(nacosAiService));
        NacosAiService aiService = null;
        try {
            Properties properties = new Properties();
            properties.put(PropertyKeyConst.SERVER_ADDR, "127.0.0.1");
            properties.put(PropertyKeyConst.NAMESPACE, "test");
            aiService = new NacosAiService(properties);
            assertEquals("test", field.get(aiService));
        } finally {
            if (null != aiService) {
                aiService.shutdown();
            }
        }
    }
    
    @Test
    void getMcpServer() throws NoSuchFieldException, IllegalAccessException, NacosException {
        injectMocks();
        when(grpcClient.queryMcpServer("testMcpName", "1.0.0"))
            .thenReturn(new McpServerDetailInfo());
        assertNotNull(nacosAiService.getMcpServer("testMcpName", "1.0.0"));
    }
    
    @Test
    void getMcpServerWithInvalidMcpName()
        throws NoSuchFieldException, IllegalAccessException, NacosException {
        assertThrows(NacosApiException.class, () -> nacosAiService.getMcpServer("", "1.0.0"));
    }
    
    @Test
    void releaseMcpServer() throws NacosException, NoSuchFieldException, IllegalAccessException {
        injectMocks();
        McpServerBasicInfo serverSpecification = new McpServerBasicInfo();
        serverSpecification.setName("testMcpName");
        serverSpecification.setVersionDetail(new ServerVersionDetail());
        serverSpecification.getVersionDetail().setVersion("1.0.0");
        String id = UUID.randomUUID().toString();
        when(grpcClient.releaseMcpServer(serverSpecification, null, null, null)).thenReturn(id);
        assertEquals(id, nacosAiService.releaseMcpServer(serverSpecification, null));
    }
    
    @Test
    void releaseMcpServerWithInvalidParameters() throws NacosException {
        assertThrows(NacosApiException.class, () -> nacosAiService.releaseMcpServer(null, null));
        McpServerBasicInfo serverSpecification = new McpServerBasicInfo();
        assertThrows(NacosApiException.class,
            () -> nacosAiService.releaseMcpServer(serverSpecification, null));
        serverSpecification.setName("testMcpName");
        assertThrows(NacosApiException.class,
            () -> nacosAiService.releaseMcpServer(serverSpecification, null));
        serverSpecification.setVersionDetail(new ServerVersionDetail());
        assertThrows(NacosApiException.class,
            () -> nacosAiService.releaseMcpServer(serverSpecification, null));
    }
    
    @Test
    void registerMcpServerEndpoint()
        throws NoSuchFieldException, IllegalAccessException, NacosException {
        injectMocks();
        nacosAiService.registerMcpServerEndpoint("testMcpName", "1.1.1.1", 8848, "1.0.0");
        verify(grpcClient).registerMcpServerEndpoint("testMcpName", "1.1.1.1", 8848, "1.0.0");
    }
    
    @Test
    void registerMcpServerEndpointWithInvalidParameters() {
        assertThrows(NacosApiException.class,
            () -> nacosAiService.registerMcpServerEndpoint("", null, -1, "1.0.0"));
        assertThrows(NacosApiException.class,
            () -> nacosAiService.registerMcpServerEndpoint("testMcpName", null, -1, "1.0.0"));
        assertThrows(NacosApiException.class,
            () -> nacosAiService.registerMcpServerEndpoint("testMcpName", "1.1.1.1", -1,
                "1.0.0"));
    }
    
    @Test
    void deregisterMcpServerEndpoint()
        throws NoSuchFieldException, IllegalAccessException, NacosException {
        injectMocks();
        nacosAiService.deregisterMcpServerEndpoint("testMcpName", "1.1.1.1", 8848);
        verify(grpcClient).deregisterMcpServerEndpoint("testMcpName", "1.1.1.1", 8848);
    }
    
    @Test
    void deregisterMcpServerEndpointWithInvalidParameters() {
        assertThrows(NacosApiException.class,
            () -> nacosAiService.deregisterMcpServerEndpoint("", null, -1));
        assertThrows(NacosApiException.class,
            () -> nacosAiService.deregisterMcpServerEndpoint("testMcpName", null, -1));
        assertThrows(NacosApiException.class,
            () -> nacosAiService.deregisterMcpServerEndpoint("testMcpName", "1.1.1.1", -1));
    }
    
    @Test
    void subscribeMcpServer() throws NoSuchFieldException, IllegalAccessException, NacosException {
        injectMocks();
        AbstractNacosMcpServerListener listener =
            Mockito.mock(AbstractNacosMcpServerListener.class);
        McpServerDetailInfo expected = new McpServerDetailInfo();
        when(grpcClient.subscribeMcpServer("testMcpName", null)).thenReturn(expected);
        McpServerDetailInfo actual = nacosAiService.subscribeMcpServer("testMcpName", listener);
        assertEquals(expected, actual);
        verify(aiChangeNotifier).registerListener(eq("testMcpName"), isNull(),
            any(McpServerListenerInvoker.class));
        verify(listener).onEvent(any(NacosMcpServerEvent.class));
    }
    
    @Test
    void subscribeMcpServerWithInvalidParameters() {
        assertThrows(NacosApiException.class, () -> nacosAiService.subscribeMcpServer("", null));
        assertThrows(NacosApiException.class,
            () -> nacosAiService.subscribeMcpServer("testMcpName", null));
    }
    
    @Test
    void unsubscribeMcpServer()
        throws NoSuchFieldException, IllegalAccessException, NacosException {
        injectMocks();
        AbstractNacosMcpServerListener listener =
            Mockito.mock(AbstractNacosMcpServerListener.class);
        nacosAiService.unsubscribeMcpServer("testMcpName", listener);
        verify(aiChangeNotifier).deregisterListener(eq("testMcpName"), isNull(),
            any(McpServerListenerInvoker.class));
        verify(grpcClient).unsubscribeMcpServer("testMcpName", null);
    }
    
    @Test
    void unsubscribeMcpServerWithOtherListener()
        throws NoSuchFieldException, IllegalAccessException, NacosException {
        injectMocks();
        when(aiChangeNotifier.isMcpServerSubscribed("testMcpName", null)).thenReturn(true);
        AbstractNacosMcpServerListener listener =
            Mockito.mock(AbstractNacosMcpServerListener.class);
        nacosAiService.unsubscribeMcpServer("testMcpName", listener);
        verify(aiChangeNotifier).deregisterListener(eq("testMcpName"), isNull(),
            any(McpServerListenerInvoker.class));
        verify(grpcClient, never()).unsubscribeMcpServer("testMcpName", null);
    }
    
    @Test
    void unsubscribeMcpServerWithNullListener()
        throws NoSuchFieldException, IllegalAccessException, NacosException {
        injectMocks();
        nacosAiService.unsubscribeMcpServer("testMcpName", null);
        verify(aiChangeNotifier, never()).deregisterListener(eq("testMcpName"), isNull(),
            any(McpServerListenerInvoker.class));
        verify(grpcClient, never()).unsubscribeMcpServer("testMcpName", null);
    }
    
    @Test
    void unsubscribeMcpServerWithInvalidParameters() {
        assertThrows(NacosApiException.class, () -> nacosAiService.unsubscribeMcpServer("", null));
    }
    
    @Test
    void releaseAgentCardShouldAcceptLegacyFields() throws Exception {
        injectMocks();
        AgentCard agentCard = new AgentCard();
        agentCard.setName("testAgent");
        agentCard.setVersion("1.0.0");
        agentCard.setProtocolVersion("1.0");
        agentCard.setPreferredTransport("JSONRPC");
        agentCard.setUrl("http://127.0.0.1:8080/agent");
        nacosAiService.releaseAgentCard(agentCard, "service", true);
        verify(grpcClient).releaseAgentCard(agentCard, "service", true);
    }
    
    @Test
    void releaseAgentCardShouldAcceptV1Interfaces() throws Exception {
        injectMocks();
        AgentCard agentCard = new AgentCard();
        agentCard.setName("testAgent");
        agentCard.setVersion("1.0.0");
        AgentInterface agentInterface = new AgentInterface();
        agentInterface.setUrl("http://127.0.0.1:8080/agent");
        agentInterface.setProtocolBinding("JSONRPC");
        agentInterface.setProtocolVersion("1.0");
        agentCard.setSupportedInterfaces(Collections.singletonList(agentInterface));
        nacosAiService.releaseAgentCard(agentCard, "service", true);
        verify(grpcClient).releaseAgentCard(agentCard, "service", true);
    }
    
    @Test
    void releaseAgentCardShouldShowUnifiedErrorWhenFormatsInvalid() {
        AgentCard agentCard = new AgentCard();
        agentCard.setName("testAgent");
        agentCard.setVersion("1.0.0");
        NacosApiException exception = assertThrows(NacosApiException.class,
            () -> nacosAiService.releaseAgentCard(agentCard, "service", true));
        assertEquals(
            "Required parameter `agentCard.supportedInterfaces` not present, and old protocol fields "
                + "(`agentCard.protocolVersion`, `agentCard.preferredTransport`, `agentCard.url`) are incomplete. "
                + "Please prefer `agentCard.supportedInterfaces` for A2A 1.0.0.",
            exception.getMessage());
    }
    
    @Test
    void registerAgentEndpointWithCollection()
        throws NoSuchFieldException, IllegalAccessException, NacosException {
        injectMocks();
        Collection<AgentEndpoint> endpoints = createTestEndpoints();
        nacosAiService.registerAgentEndpoint("testAgent", endpoints);
        verify(grpcClient).registerAgentEndpoints("testAgent", endpoints);
    }
    
    @Test
    void registerAgentEndpointWithCollectionInvalidAgentName() {
        Collection<AgentEndpoint> endpoints = createTestEndpoints();
        assertThrows(NacosApiException.class,
            () -> nacosAiService.registerAgentEndpoint("", endpoints));
    }
    
    @Test
    void registerAgentEndpointWithCollectionNullEndpoints() {
        assertThrows(NacosApiException.class, () -> nacosAiService
            .registerAgentEndpoint("testAgent", (Collection<AgentEndpoint>) null));
    }
    
    @Test
    void registerAgentEndpointWithCollectionEmptyEndpoints() {
        assertThrows(NacosApiException.class,
            () -> nacosAiService.registerAgentEndpoint("testAgent", new ArrayList<>()));
    }
    
    @Test
    void registerAgentEndpointWithCollectionNullEndpointInList() {
        Collection<AgentEndpoint> endpoints = Arrays.asList(new AgentEndpoint(), null);
        assertThrows(NacosApiException.class,
            () -> nacosAiService.registerAgentEndpoint("testAgent", endpoints));
    }
    
    @Test
    void registerAgentEndpointWithCollectionEndpointWithoutVersion() {
        AgentEndpoint endpoint = new AgentEndpoint();
        endpoint.setAddress("1.1.1.1");
        endpoint.setPort(8080);
        // No version set
        Collection<AgentEndpoint> endpoints = Arrays.asList(endpoint);
        assertThrows(NacosApiException.class,
            () -> nacosAiService.registerAgentEndpoint("testAgent", endpoints));
    }
    
    @Test
    void registerAgentEndpointWithCollectionDifferentVersions() {
        AgentEndpoint endpoint1 = new AgentEndpoint();
        endpoint1.setAddress("1.1.1.1");
        endpoint1.setPort(8080);
        endpoint1.setVersion("1.0.0");
        
        AgentEndpoint endpoint2 = new AgentEndpoint();
        endpoint2.setAddress("2.2.2.2");
        endpoint2.setPort(9090);
        endpoint2.setVersion("2.0.0");
        
        Collection<AgentEndpoint> endpoints = Arrays.asList(endpoint1, endpoint2);
        assertThrows(NacosApiException.class,
            () -> nacosAiService.registerAgentEndpoint("testAgent", endpoints));
    }
    
    @Test
    void releaseAgentCardWithInvalidInterfaceShouldUseLegacyValidation() {
        // V1 interface fails validation (missing protocolVersion), legacy fields also missing
        AgentCard agentCard = new AgentCard();
        agentCard.setName("testAgent");
        agentCard.setVersion("1.0.0");
        AgentInterface invalid = new AgentInterface();
        invalid.setUrl("http://127.0.0.1:8080");
        invalid.setProtocolBinding("JSONRPC");
        // No protocolVersion → invalid → hasValidV1Interfaces returns false (line 417)
        agentCard.setSupportedInterfaces(Collections.singletonList(invalid));
        assertThrows(NacosApiException.class,
            () -> nacosAiService.releaseAgentCard(agentCard, "service", true));
    }
    
    @Test
    void getAgentCard() throws NoSuchFieldException, IllegalAccessException, NacosException {
        injectMocks();
        AgentCardDetailInfo expected = new AgentCardDetailInfo();
        when(grpcClient.getAgentCard("agentName", "1.0.0", "service")).thenReturn(expected);
        AgentCardDetailInfo actual = nacosAiService.getAgentCard("agentName", "1.0.0", "service");
        assertEquals(expected, actual);
    }
    
    @Test
    void getAgentCardWithBlankName() {
        assertThrows(NacosApiException.class,
            () -> nacosAiService.getAgentCard("", "1.0.0", "service"));
    }
    
    @Test
    void releaseAgentCardWithoutRegistrationTypeShouldUseDefault()
        throws NoSuchFieldException, IllegalAccessException, NacosException {
        injectMocks();
        AgentCard agentCard = new AgentCard();
        agentCard.setName("testAgent");
        agentCard.setVersion("1.0.0");
        AgentInterface ai = new AgentInterface();
        ai.setUrl("http://127.0.0.1:8080");
        ai.setProtocolBinding("JSONRPC");
        ai.setProtocolVersion("1.0");
        agentCard.setSupportedInterfaces(Collections.singletonList(ai));
        nacosAiService.releaseAgentCard(agentCard, "", true);
        verify(grpcClient).releaseAgentCard(eq(agentCard), eq(
            com.alibaba.nacos.api.ai.constant.AiConstants.A2a.A2A_ENDPOINT_TYPE_SERVICE), eq(true));
    }
    
    @Test
    void releaseAgentCardWithNullAgentCard() {
        assertThrows(NacosApiException.class,
            () -> nacosAiService.releaseAgentCard(null, "service", true));
    }
    
    @Test
    void releaseAgentCardWithBlankName() {
        AgentCard agentCard = new AgentCard();
        // missing name
        assertThrows(NacosApiException.class,
            () -> nacosAiService.releaseAgentCard(agentCard, "service", true));
    }
    
    @Test
    void registerAgentEndpoint()
        throws NoSuchFieldException, IllegalAccessException, NacosException {
        injectMocks();
        AgentEndpoint endpoint = new AgentEndpoint();
        endpoint.setAddress("1.1.1.1");
        endpoint.setPort(8080);
        endpoint.setVersion("1.0.0");
        nacosAiService.registerAgentEndpoint("testAgent", endpoint);
        verify(grpcClient).registerAgentEndpoint("testAgent", endpoint);
    }
    
    @Test
    void registerAgentEndpointWithBlankAgentName() {
        AgentEndpoint endpoint = new AgentEndpoint();
        endpoint.setAddress("1.1.1.1");
        endpoint.setPort(8080);
        endpoint.setVersion("1.0.0");
        assertThrows(NacosApiException.class,
            () -> nacosAiService.registerAgentEndpoint("", endpoint));
    }
    
    @Test
    void registerAgentEndpointWithNullEndpoint() {
        assertThrows(NacosApiException.class,
            () -> nacosAiService.registerAgentEndpoint("testAgent", (AgentEndpoint) null));
    }
    
    @Test
    void deregisterAgentEndpoint()
        throws NoSuchFieldException, IllegalAccessException, NacosException {
        injectMocks();
        AgentEndpoint endpoint = new AgentEndpoint();
        endpoint.setAddress("1.1.1.1");
        endpoint.setPort(8080);
        endpoint.setVersion("1.0.0");
        nacosAiService.deregisterAgentEndpoint("testAgent", endpoint);
        verify(grpcClient).deregisterAgentEndpoint("testAgent", endpoint);
    }
    
    @Test
    void deregisterAgentEndpointWithBlankAgentName() {
        assertThrows(NacosApiException.class,
            () -> nacosAiService.deregisterAgentEndpoint("", new AgentEndpoint()));
    }
    
    @Test
    void subscribeAgentCard() throws NoSuchFieldException, IllegalAccessException, NacosException {
        injectMocks();
        AbstractNacosAgentCardListener listener =
            Mockito.mock(AbstractNacosAgentCardListener.class);
        AgentCardDetailInfo expected = new AgentCardDetailInfo();
        when(grpcClient.subscribeAgentCard("agentName", "1.0.0")).thenReturn(expected);
        AgentCardDetailInfo actual =
            nacosAiService.subscribeAgentCard("agentName", "1.0.0", listener);
        assertEquals(expected, actual);
        verify(aiChangeNotifier).registerListener(eq("agentName"), eq("1.0.0"),
            any(AgentCardListenerInvoker.class));
        verify(listener).onEvent(any(NacosAgentCardEvent.class));
    }
    
    @Test
    void subscribeAgentCardWithBlankName() {
        assertThrows(NacosApiException.class,
            () -> nacosAiService.subscribeAgentCard("", "1.0.0", null));
    }
    
    @Test
    void subscribeAgentCardWithNullListener() {
        assertThrows(NacosApiException.class,
            () -> nacosAiService.subscribeAgentCard("agentName", "1.0.0", null));
    }
    
    @Test
    void unsubscribeAgentCard()
        throws NoSuchFieldException, IllegalAccessException, NacosException {
        injectMocks();
        AbstractNacosAgentCardListener listener =
            Mockito.mock(AbstractNacosAgentCardListener.class);
        nacosAiService.unsubscribeAgentCard("agentName", "1.0.0", listener);
        verify(aiChangeNotifier).deregisterListener(eq("agentName"), eq("1.0.0"),
            any(AgentCardListenerInvoker.class));
        verify(grpcClient).unsubscribeAgentCard("agentName", "1.0.0");
    }
    
    @Test
    void unsubscribeAgentCardWithRemainingListeners()
        throws NoSuchFieldException, IllegalAccessException, NacosException {
        injectMocks();
        when(aiChangeNotifier.isAgentCardSubscribed("agentName", "1.0.0")).thenReturn(true);
        AbstractNacosAgentCardListener listener =
            Mockito.mock(AbstractNacosAgentCardListener.class);
        nacosAiService.unsubscribeAgentCard("agentName", "1.0.0", listener);
        verify(aiChangeNotifier).deregisterListener(eq("agentName"), eq("1.0.0"),
            any(AgentCardListenerInvoker.class));
        verify(grpcClient, never()).unsubscribeAgentCard("agentName", "1.0.0");
    }
    
    @Test
    void unsubscribeAgentCardWithNullListener()
        throws NoSuchFieldException, IllegalAccessException, NacosException {
        injectMocks();
        nacosAiService.unsubscribeAgentCard("agentName", "1.0.0", null);
        verify(aiChangeNotifier, never()).deregisterListener(eq("agentName"), eq("1.0.0"),
            any(AgentCardListenerInvoker.class));
        verify(grpcClient, never()).unsubscribeAgentCard("agentName", "1.0.0");
    }
    
    @Test
    void unsubscribeAgentCardWithBlankName() {
        assertThrows(NacosApiException.class,
            () -> nacosAiService.unsubscribeAgentCard("", null, null));
    }
    
    @Test
    void downloadSkillZip() throws NoSuchFieldException, IllegalAccessException, NacosException {
        injectMocks();
        byte[] zipBytes = new byte[] {1, 2, 3};
        when(httpProxy.downloadSkillZip("skillName", null, null)).thenReturn(zipBytes);
        assertEquals(zipBytes, nacosAiService.downloadSkillZip("skillName"));
    }
    
    @Test
    void downloadSkillZipWithBlankName() {
        assertThrows(NacosApiException.class, () -> nacosAiService.downloadSkillZip(""));
    }
    
    @Test
    void downloadSkillZipByVersion()
        throws NoSuchFieldException, IllegalAccessException, NacosException {
        injectMocks();
        byte[] zipBytes = new byte[] {4, 5, 6};
        when(httpProxy.downloadSkillZip("skillName", "1.0.0", null)).thenReturn(zipBytes);
        assertEquals(zipBytes, nacosAiService.downloadSkillZipByVersion("skillName", "1.0.0"));
    }
    
    @Test
    void downloadSkillZipByVersionWithBlankName() {
        assertThrows(NacosApiException.class,
            () -> nacosAiService.downloadSkillZipByVersion("", "1.0.0"));
    }
    
    @Test
    void downloadSkillZipByLabel()
        throws NoSuchFieldException, IllegalAccessException, NacosException {
        injectMocks();
        byte[] zipBytes = new byte[] {7, 8, 9};
        when(httpProxy.downloadSkillZip("skillName", null, "stable")).thenReturn(zipBytes);
        assertEquals(zipBytes, nacosAiService.downloadSkillZipByLabel("skillName", "stable"));
    }
    
    @Test
    void downloadSkillZipByLabelWithBlankName() {
        assertThrows(NacosApiException.class,
            () -> nacosAiService.downloadSkillZipByLabel("", "stable"));
    }
    
    @Test
    void loadAgentSpec() throws NoSuchFieldException, IllegalAccessException, NacosException {
        injectMocks();
        AgentSpec expected = new AgentSpec();
        when(agentSpecCacheHolder.queryAgentSpec("specName")).thenReturn(expected);
        assertEquals(expected, nacosAiService.loadAgentSpec("specName"));
    }
    
    @Test
    void loadAgentSpecWithBlankName() {
        assertThrows(NacosApiException.class, () -> nacosAiService.loadAgentSpec(""));
    }
    
    @Test
    void subscribeAgentSpec() throws NoSuchFieldException, IllegalAccessException, NacosException {
        injectMocks();
        AbstractNacosAgentSpecListener listener =
            Mockito.mock(AbstractNacosAgentSpecListener.class);
        AgentSpec expected = new AgentSpec();
        when(agentSpecCacheHolder.subscribeAgentSpec("specName")).thenReturn(expected);
        AgentSpec actual = nacosAiService.subscribeAgentSpec("specName", listener);
        assertEquals(expected, actual);
        verify(aiChangeNotifier).registerListener(eq("specName"),
            any(AgentSpecListenerInvoker.class));
        verify(listener).onEvent(any(NacosAgentSpecEvent.class));
    }
    
    @Test
    void subscribeAgentSpecWithBlankName() {
        assertThrows(NacosApiException.class,
            () -> nacosAiService.subscribeAgentSpec("", null));
    }
    
    @Test
    void subscribeAgentSpecWithNullListener() {
        assertThrows(NacosApiException.class,
            () -> nacosAiService.subscribeAgentSpec("specName", null));
    }
    
    @Test
    void unsubscribeAgentSpec()
        throws NoSuchFieldException, IllegalAccessException, NacosException {
        injectMocks();
        AbstractNacosAgentSpecListener listener =
            Mockito.mock(AbstractNacosAgentSpecListener.class);
        nacosAiService.unsubscribeAgentSpec("specName", listener);
        verify(aiChangeNotifier).deregisterListener(eq("specName"),
            any(AgentSpecListenerInvoker.class));
        verify(agentSpecCacheHolder).unsubscribeAgentSpec("specName");
    }
    
    @Test
    void unsubscribeAgentSpecWithRemainingListeners()
        throws NoSuchFieldException, IllegalAccessException, NacosException {
        injectMocks();
        when(aiChangeNotifier.isAgentSpecSubscribed("specName")).thenReturn(true);
        AbstractNacosAgentSpecListener listener =
            Mockito.mock(AbstractNacosAgentSpecListener.class);
        nacosAiService.unsubscribeAgentSpec("specName", listener);
        verify(aiChangeNotifier).deregisterListener(eq("specName"),
            any(AgentSpecListenerInvoker.class));
        verify(agentSpecCacheHolder, never()).unsubscribeAgentSpec("specName");
    }
    
    @Test
    void unsubscribeAgentSpecWithNullListener()
        throws NoSuchFieldException, IllegalAccessException, NacosException {
        injectMocks();
        nacosAiService.unsubscribeAgentSpec("specName", null);
        verify(aiChangeNotifier, never()).deregisterListener(eq("specName"),
            any(AgentSpecListenerInvoker.class));
        verify(agentSpecCacheHolder, never()).unsubscribeAgentSpec("specName");
    }
    
    @Test
    void unsubscribeAgentSpecWithBlankName() {
        assertThrows(NacosApiException.class,
            () -> nacosAiService.unsubscribeAgentSpec("", null));
    }
    
    @Test
    void getPrompt() throws NoSuchFieldException, IllegalAccessException, NacosException {
        injectMocks();
        Prompt expected = new Prompt("p1", "1.0.0", "tpl");
        when(aiClientProxy.queryPrompt("p1", null, null, null)).thenReturn(expected);
        assertEquals(expected, nacosAiService.getPrompt("p1"));
    }
    
    @Test
    void getPromptWithBlankKey() {
        assertThrows(NacosApiException.class, () -> nacosAiService.getPrompt(""));
    }
    
    @Test
    void getPromptByVersion()
        throws NoSuchFieldException, IllegalAccessException, NacosException {
        injectMocks();
        Prompt expected = new Prompt("p1", "1.0.0", "tpl");
        when(aiClientProxy.queryPrompt("p1", "1.0.0", null, null)).thenReturn(expected);
        assertEquals(expected, nacosAiService.getPromptByVersion("p1", "1.0.0"));
    }
    
    @Test
    void getPromptByVersionWithBlankVersion()
        throws NoSuchFieldException, IllegalAccessException, NacosException {
        injectMocks();
        Prompt expected = new Prompt("p1", "1.0.0", "tpl");
        when(aiClientProxy.queryPrompt("p1", null, null, null)).thenReturn(expected);
        assertEquals(expected, nacosAiService.getPromptByVersion("p1", ""));
    }
    
    @Test
    void getPromptByVersionWithBlankKey() {
        assertThrows(NacosApiException.class, () -> nacosAiService.getPromptByVersion("", "1.0.0"));
    }
    
    @Test
    void getPromptByLabel() throws NoSuchFieldException, IllegalAccessException, NacosException {
        injectMocks();
        Prompt expected = new Prompt("p1", "1.0.0", "tpl");
        when(aiClientProxy.queryPrompt("p1", null, "prod", null)).thenReturn(expected);
        assertEquals(expected, nacosAiService.getPromptByLabel("p1", "prod"));
    }
    
    @Test
    void getPromptByLabelWithBlankKey() {
        assertThrows(NacosApiException.class, () -> nacosAiService.getPromptByLabel("", "prod"));
    }
    
    @Test
    void getPromptByLabelWithBlankLabel() {
        assertThrows(NacosApiException.class, () -> nacosAiService.getPromptByLabel("p1", ""));
    }
    
    @Test
    void subscribePrompt() throws NoSuchFieldException, IllegalAccessException, NacosException {
        injectMocks();
        AbstractNacosPromptListener listener = Mockito.mock(AbstractNacosPromptListener.class);
        Prompt expected = new Prompt("p1", "1.0.0", "tpl");
        when(promptCacheHolder.subscribePrompt("p1", "1.0.0", null)).thenReturn(expected);
        Prompt actual = nacosAiService.subscribePrompt("p1", "1.0.0", null, listener);
        assertEquals(expected, actual);
        verify(aiChangeNotifier).registerListener(eq("p1"), eq("1.0.0"), isNull(),
            any(PromptListenerInvoker.class));
        verify(listener).onEvent(any(NacosPromptEvent.class));
    }
    
    @Test
    void subscribePromptWithBlankKey() {
        assertThrows(NacosApiException.class,
            () -> nacosAiService.subscribePrompt("", null, null, null));
    }
    
    @Test
    void subscribePromptWithNullListener() {
        assertThrows(NacosApiException.class,
            () -> nacosAiService.subscribePrompt("p1", null, null, null));
    }
    
    @Test
    void subscribePromptWithNullResult()
        throws NoSuchFieldException, IllegalAccessException, NacosException {
        injectMocks();
        AbstractNacosPromptListener listener = Mockito.mock(AbstractNacosPromptListener.class);
        when(promptCacheHolder.subscribePrompt("p1", "1.0.0", null)).thenReturn(null);
        Prompt actual = nacosAiService.subscribePrompt("p1", "1.0.0", null, listener);
        assertNull(actual);
        verify(listener, never()).onEvent(any(NacosPromptEvent.class));
    }
    
    @Test
    void unsubscribePrompt() throws NoSuchFieldException, IllegalAccessException, NacosException {
        injectMocks();
        AbstractNacosPromptListener listener = Mockito.mock(AbstractNacosPromptListener.class);
        nacosAiService.unsubscribePrompt("p1", "1.0.0", null, listener);
        verify(aiChangeNotifier).deregisterListener(eq("p1"), eq("1.0.0"), isNull(),
            any(PromptListenerInvoker.class));
        verify(promptCacheHolder).unsubscribePrompt("p1", "1.0.0", null);
    }
    
    @Test
    void unsubscribePromptWithRemainingListeners()
        throws NoSuchFieldException, IllegalAccessException, NacosException {
        injectMocks();
        when(aiChangeNotifier.isPromptSubscribed("p1", "1.0.0", null)).thenReturn(true);
        AbstractNacosPromptListener listener = Mockito.mock(AbstractNacosPromptListener.class);
        nacosAiService.unsubscribePrompt("p1", "1.0.0", null, listener);
        verify(aiChangeNotifier).deregisterListener(eq("p1"), eq("1.0.0"), isNull(),
            any(PromptListenerInvoker.class));
        verify(promptCacheHolder, never()).unsubscribePrompt("p1", "1.0.0", null);
    }
    
    @Test
    void unsubscribePromptWithNullListener()
        throws NoSuchFieldException, IllegalAccessException, NacosException {
        injectMocks();
        nacosAiService.unsubscribePrompt("p1", "1.0.0", null, null);
        verify(aiChangeNotifier, never()).deregisterListener(eq("p1"), eq("1.0.0"), isNull(),
            any(PromptListenerInvoker.class));
        verify(promptCacheHolder, never()).unsubscribePrompt("p1", "1.0.0", null);
    }
    
    @Test
    void unsubscribePromptWithBlankKey() {
        assertThrows(NacosApiException.class,
            () -> nacosAiService.unsubscribePrompt("", null, null, null));
    }
    
    @Test
    void shutdownInvokesAllChildShutdowns()
        throws NoSuchFieldException, IllegalAccessException, NacosException {
        injectMocks();
        nacosAiService.shutdown();
        verify(grpcClient).shutdown();
        verify(httpProxy).shutdown();
        verify(mcpServerCacheHolder).shutdown();
        verify(promptCacheHolder).shutdown();
        verify(agentSpecCacheHolder).shutdown();
        // null out so AfterEach doesn't run shutdown again
        nacosAiService = null;
    }
    
    @Test
    void constructorHttpTransportMode() throws NacosException {
        Properties properties = new Properties();
        properties.put(PropertyKeyConst.SERVER_ADDR, "127.0.0.1");
        properties.put(com.alibaba.nacos.api.ai.constant.AiConstants.AI_TRANSPORT_MODE,
            com.alibaba.nacos.api.ai.constant.AiConstants.AI_TRANSPORT_MODE_HTTP);
        NacosAiService aiService = null;
        try {
            aiService = new NacosAiService(properties);
            // Verify aiClientProxy field is set to httpProxy
            Field clientProxyField = NacosAiService.class.getDeclaredField("aiClientProxy");
            clientProxyField.setAccessible(true);
            Field httpProxyField = NacosAiService.class.getDeclaredField("httpProxy");
            httpProxyField.setAccessible(true);
            assertEquals(httpProxyField.get(aiService), clientProxyField.get(aiService));
        } catch (NoSuchFieldException | IllegalAccessException ex) {
            throw new RuntimeException(ex);
        } finally {
            if (aiService != null) {
                aiService.shutdown();
            }
        }
    }
    
    private void injectMocks() throws NoSuchFieldException, IllegalAccessException {
        Field field = NacosAiService.class.getDeclaredField("grpcClient");
        field.setAccessible(true);
        final AiGrpcClient autoBuildGrpcClient = (AiGrpcClient) field.get(nacosAiService);
        field.set(nacosAiService, grpcClient);
        field = NacosAiService.class.getDeclaredField("httpProxy");
        field.setAccessible(true);
        final AiHttpClientProxy autoBuildHttpProxy = (AiHttpClientProxy) field.get(nacosAiService);
        field.set(nacosAiService, httpProxy);
        field = NacosAiService.class.getDeclaredField("aiClientProxy");
        field.setAccessible(true);
        field.set(nacosAiService, aiClientProxy);
        field = NacosAiService.class.getDeclaredField("mcpServerCacheHolder");
        field.setAccessible(true);
        NacosMcpServerCacheHolder autoBuildCacheHolder =
            (NacosMcpServerCacheHolder) field.get(nacosAiService);
        field.set(nacosAiService, mcpServerCacheHolder);
        field = NacosAiService.class.getDeclaredField("agentCardCacheHolder");
        field.setAccessible(true);
        NacosAgentCardCacheHolder autoBuildAgentCacheHolder =
            (NacosAgentCardCacheHolder) field.get(nacosAiService);
        field.set(nacosAiService, agentCardCacheHolder);
        field = NacosAiService.class.getDeclaredField("promptCacheHolder");
        field.setAccessible(true);
        NacosPromptCacheHolder autoBuildPromptCacheHolder =
            (NacosPromptCacheHolder) field.get(nacosAiService);
        field.set(nacosAiService, promptCacheHolder);
        field = NacosAiService.class.getDeclaredField("agentSpecCacheHolder");
        field.setAccessible(true);
        NacosAgentSpecCacheHolder autoBuildAgentSpecCacheHolder =
            (NacosAgentSpecCacheHolder) field.get(nacosAiService);
        field.set(nacosAiService, agentSpecCacheHolder);
        field = NacosAiService.class.getDeclaredField("aiChangeNotifier");
        field.setAccessible(true);
        field.set(nacosAiService, aiChangeNotifier);
        try {
            autoBuildGrpcClient.shutdown();
            autoBuildHttpProxy.shutdown();
            autoBuildCacheHolder.shutdown();
            autoBuildAgentCacheHolder.shutdown();
            autoBuildPromptCacheHolder.shutdown();
            autoBuildAgentSpecCacheHolder.shutdown();
        } catch (NacosException ignored) {
        }
    }
    
    private Collection<AgentEndpoint> createTestEndpoints() {
        AgentEndpoint endpoint1 = new AgentEndpoint();
        endpoint1.setAddress("1.1.1.1");
        endpoint1.setPort(8080);
        endpoint1.setVersion("1.0.0");
        
        AgentEndpoint endpoint2 = new AgentEndpoint();
        endpoint2.setAddress("2.2.2.2");
        endpoint2.setPort(9090);
        endpoint2.setVersion("1.0.0");
        
        return Arrays.asList(endpoint1, endpoint2);
    }
}
