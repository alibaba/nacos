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

package com.alibaba.nacos.maintainer.client.ai;

import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.ai.model.a2a.AgentCard;
import com.alibaba.nacos.api.ai.model.a2a.AgentCardDetailInfo;
import com.alibaba.nacos.api.ai.model.a2a.AgentCardVersionInfo;
import com.alibaba.nacos.api.ai.model.a2a.AgentVersionDetail;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.common.http.HttpRestResult;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.maintainer.client.model.HttpRequest;
import com.alibaba.nacos.maintainer.client.remote.ClientHttpProxy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Unit tests for A2aMaintainerService interface default methods.
 * Tests the convenience methods that provide simplified parameter signatures.
 *
 * @author nacos
 */
@ExtendWith(MockitoExtension.class)
class A2aMaintainerServiceDefaultMethodsTest {
    
    @Mock
    private ClientHttpProxy clientHttpProxy;
    
    private A2aMaintainerService a2aService;
    
    @BeforeEach
    void setUp() throws NacosException, NoSuchFieldException, IllegalAccessException {
        Properties properties = new Properties();
        properties.put(PropertyKeyConst.SERVER_ADDR, "127.0.0.1:8848");
        AiMaintainerService aiMaintainerService =
            AiMaintainerFactory.createAiMaintainerService(properties);
        
        Field a2aServiceField =
            NacosAiMaintainerServiceImpl.class.getDeclaredField("a2aMaintainerService");
        a2aServiceField.setAccessible(true);
        a2aService = (A2aMaintainerService) a2aServiceField.get(aiMaintainerService);
        
        injectMockClientHttpProxy(a2aService);
    }
    
    private void injectMockClientHttpProxy(Object service)
        throws NoSuchFieldException, IllegalAccessException {
        Field contextField = AbstractAiDelegateMaintainerService.class.getDeclaredField("context");
        contextField.setAccessible(true);
        Object context = contextField.get(service);
        Field clientHttpProxyField =
            AiMaintainerHttpContext.class.getDeclaredField("clientHttpProxy");
        clientHttpProxyField.setAccessible(true);
        clientHttpProxyField.set(context, clientHttpProxy);
    }
    
    // ========== registerAgent default method tests ==========
    
    @Test
    @DisplayName("registerAgent(agentCard) should use default namespace")
    void testRegisterAgentWithDefaultNamespace() throws NacosException {
        AgentCard card = new AgentCard();
        card.setName("testAgent");
        card.setVersion("1.0");
        
        HttpRestResult<String> mockResult = new HttpRestResult<>();
        mockResult.setData(JacksonUtils.toJson(Result.success("ok")));
        when(clientHttpProxy.executeSyncHttpRequest(any(HttpRequest.class))).thenReturn(mockResult);
        
        assertTrue(a2aService.registerAgent(card));
    }
    
    @Test
    @DisplayName("registerAgent(agentCard, namespaceId) should use default registration type")
    void testRegisterAgentWithNamespace() throws NacosException {
        AgentCard card = new AgentCard();
        card.setName("testAgent");
        card.setVersion("1.0");
        
        HttpRestResult<String> mockResult = new HttpRestResult<>();
        mockResult.setData(JacksonUtils.toJson(Result.success("ok")));
        when(clientHttpProxy.executeSyncHttpRequest(any(HttpRequest.class))).thenReturn(mockResult);
        
        assertTrue(a2aService.registerAgent(card, "public"));
    }
    
    // ========== getAgentCard default method tests ==========
    
    @Test
    @DisplayName("getAgentCard(agentName) should use default namespace")
    void testGetAgentCardWithDefaultNamespace() throws NacosException {
        AgentCardDetailInfo detail = new AgentCardDetailInfo();
        detail.setName("testAgent");
        
        HttpRestResult<String> mockResult = new HttpRestResult<>();
        mockResult.setData(JacksonUtils.toJson(Result.success(detail)));
        when(clientHttpProxy.executeSyncHttpRequest(any(HttpRequest.class))).thenReturn(mockResult);
        
        AgentCardDetailInfo result = a2aService.getAgentCard("testAgent");
        assertNotNull(result);
        assertEquals("testAgent", result.getName());
    }
    
    @Test
    @DisplayName("getAgentCard(agentName, namespaceId) should use empty registration type")
    void testGetAgentCardWithNamespace() throws NacosException {
        AgentCardDetailInfo detail = new AgentCardDetailInfo();
        detail.setName("testAgent");
        
        HttpRestResult<String> mockResult = new HttpRestResult<>();
        mockResult.setData(JacksonUtils.toJson(Result.success(detail)));
        when(clientHttpProxy.executeSyncHttpRequest(any(HttpRequest.class))).thenReturn(mockResult);
        
        AgentCardDetailInfo result = a2aService.getAgentCard("testAgent", "public");
        assertNotNull(result);
        assertEquals("testAgent", result.getName());
    }
    
    @Test
    @DisplayName("getAgentCard(agentName, namespaceId, type) should use empty version")
    void testGetAgentCardWithNamespaceAndType() throws NacosException {
        AgentCardDetailInfo detail = new AgentCardDetailInfo();
        detail.setName("testAgent");
        
        HttpRestResult<String> mockResult = new HttpRestResult<>();
        mockResult.setData(JacksonUtils.toJson(Result.success(detail)));
        when(clientHttpProxy.executeSyncHttpRequest(any(HttpRequest.class))).thenReturn(mockResult);
        
        AgentCardDetailInfo result = a2aService.getAgentCard("testAgent", "public", "url");
        assertNotNull(result);
        assertEquals("testAgent", result.getName());
    }
    
    // ========== updateAgentCard default method tests ==========
    
    @Test
    @DisplayName("updateAgentCard(agentCard) should use default namespace")
    void testUpdateAgentCardWithDefaultNamespace() throws NacosException {
        AgentCard card = new AgentCard();
        card.setName("testAgent");
        
        HttpRestResult<String> mockResult = new HttpRestResult<>();
        mockResult.setData(JacksonUtils.toJson(Result.success("ok")));
        when(clientHttpProxy.executeSyncHttpRequest(any(HttpRequest.class))).thenReturn(mockResult);
        
        assertTrue(a2aService.updateAgentCard(card));
    }
    
    @Test
    @DisplayName("updateAgentCard(agentCard, namespaceId) should set as latest")
    void testUpdateAgentCardWithNamespace() throws NacosException {
        AgentCard card = new AgentCard();
        card.setName("testAgent");
        
        HttpRestResult<String> mockResult = new HttpRestResult<>();
        mockResult.setData(JacksonUtils.toJson(Result.success("ok")));
        when(clientHttpProxy.executeSyncHttpRequest(any(HttpRequest.class))).thenReturn(mockResult);
        
        assertTrue(a2aService.updateAgentCard(card, "public"));
    }
    
    @Test
    @DisplayName("updateAgentCard(agentCard, namespaceId, setAsLatest) should use empty registration type")
    void testUpdateAgentCardWithNamespaceAndLatestFlag() throws NacosException {
        AgentCard card = new AgentCard();
        card.setName("testAgent");
        
        HttpRestResult<String> mockResult = new HttpRestResult<>();
        mockResult.setData(JacksonUtils.toJson(Result.success("ok")));
        when(clientHttpProxy.executeSyncHttpRequest(any(HttpRequest.class))).thenReturn(mockResult);
        
        assertTrue(a2aService.updateAgentCard(card, "public", false));
    }
    
    // ========== deleteAgent default method tests ==========
    
    @Test
    @DisplayName("deleteAgent(agentName) should use default namespace")
    void testDeleteAgentWithDefaultNamespace() throws NacosException {
        HttpRestResult<String> mockResult = new HttpRestResult<>();
        mockResult.setData(JacksonUtils.toJson(Result.success("ok")));
        when(clientHttpProxy.executeSyncHttpRequest(any(HttpRequest.class))).thenReturn(mockResult);
        
        assertTrue(a2aService.deleteAgent("testAgent"));
    }
    
    @Test
    @DisplayName("deleteAgent(agentName, namespaceId) should use empty version")
    void testDeleteAgentWithNamespace() throws NacosException {
        HttpRestResult<String> mockResult = new HttpRestResult<>();
        mockResult.setData(JacksonUtils.toJson(Result.success("ok")));
        when(clientHttpProxy.executeSyncHttpRequest(any(HttpRequest.class))).thenReturn(mockResult);
        
        assertTrue(a2aService.deleteAgent("testAgent", "public"));
    }
    
    // ========== listAllVersionOfAgent default method tests ==========
    
    @Test
    @DisplayName("listAllVersionOfAgent(agentName) should use default namespace")
    void testListAllVersionOfAgentWithDefaultNamespace() throws NacosException {
        AgentVersionDetail version = new AgentVersionDetail();
        version.setVersion("1.0");
        
        HttpRestResult<String> mockResult = new HttpRestResult<>();
        mockResult.setData(JacksonUtils.toJson(Result.success(Collections.singletonList(version))));
        when(clientHttpProxy.executeSyncHttpRequest(any(HttpRequest.class))).thenReturn(mockResult);
        
        List<AgentVersionDetail> result = a2aService.listAllVersionOfAgent("testAgent");
        assertNotNull(result);
        assertEquals(1, result.size());
    }
    
    // ========== searchAgentCardsByName default method tests ==========
    
    @Test
    @DisplayName("searchAgentCardsByName(pattern) should use default namespace and top 100")
    void testSearchAgentCardsByNameWithPatternOnly() throws NacosException {
        Page<AgentCardVersionInfo> page = new Page<>();
        page.setTotalCount(0);
        
        HttpRestResult<String> mockResult = new HttpRestResult<>();
        mockResult.setData(JacksonUtils.toJson(Result.success(page)));
        when(clientHttpProxy.executeSyncHttpRequest(any(HttpRequest.class))).thenReturn(mockResult);
        
        Page<AgentCardVersionInfo> result = a2aService.searchAgentCardsByName("test");
        assertNotNull(result);
    }
    
    @Test
    @DisplayName("searchAgentCardsByName(pattern, pageNo, pageSize) should use default namespace")
    void testSearchAgentCardsByNameWithPagination() throws NacosException {
        Page<AgentCardVersionInfo> page = new Page<>();
        page.setTotalCount(0);
        
        HttpRestResult<String> mockResult = new HttpRestResult<>();
        mockResult.setData(JacksonUtils.toJson(Result.success(page)));
        when(clientHttpProxy.executeSyncHttpRequest(any(HttpRequest.class))).thenReturn(mockResult);
        
        Page<AgentCardVersionInfo> result = a2aService.searchAgentCardsByName("test", 1, 10);
        assertNotNull(result);
    }
    
    // ========== listAgentCards default method tests ==========
    
    @Test
    @DisplayName("listAgentCards() should use default namespace and top 100")
    void testListAgentCardsWithNoArgs() throws NacosException {
        Page<AgentCardVersionInfo> page = new Page<>();
        page.setTotalCount(0);
        
        HttpRestResult<String> mockResult = new HttpRestResult<>();
        mockResult.setData(JacksonUtils.toJson(Result.success(page)));
        when(clientHttpProxy.executeSyncHttpRequest(any(HttpRequest.class))).thenReturn(mockResult);
        
        Page<AgentCardVersionInfo> result = a2aService.listAgentCards();
        assertNotNull(result);
    }
    
    @Test
    @DisplayName("listAgentCards(pageNo, pageSize) should use default namespace")
    void testListAgentCardsWithPagination() throws NacosException {
        Page<AgentCardVersionInfo> page = new Page<>();
        page.setTotalCount(0);
        
        HttpRestResult<String> mockResult = new HttpRestResult<>();
        mockResult.setData(JacksonUtils.toJson(Result.success(page)));
        when(clientHttpProxy.executeSyncHttpRequest(any(HttpRequest.class))).thenReturn(mockResult);
        
        Page<AgentCardVersionInfo> result = a2aService.listAgentCards(1, 10);
        assertNotNull(result);
    }
    
    @Test
    @DisplayName("listAgentCards(namespaceId, pageNo, pageSize) should use empty agent name")
    void testListAgentCardsWithNamespace() throws NacosException {
        Page<AgentCardVersionInfo> page = new Page<>();
        page.setTotalCount(0);
        
        HttpRestResult<String> mockResult = new HttpRestResult<>();
        mockResult.setData(JacksonUtils.toJson(Result.success(page)));
        when(clientHttpProxy.executeSyncHttpRequest(any(HttpRequest.class))).thenReturn(mockResult);
        
        Page<AgentCardVersionInfo> result = a2aService.listAgentCards("public", 1, 10);
        assertNotNull(result);
    }
}
