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

import com.alibaba.nacos.api.ai.model.mcp.McpServerBasicInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpServerDetailInfo;
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
 * Unit tests for AiMaintainerService interface default methods.
 * Tests the delegation methods that forward calls to mcp() and a2a() sub-services.
 *
 * @author nacos
 */
@ExtendWith(MockitoExtension.class)
class AiMaintainerServiceDefaultMethodsTest {
    
    @Mock
    private ClientHttpProxy clientHttpProxy;
    
    private AiMaintainerService aiMaintainerService;
    
    @BeforeEach
    void setUp() throws NacosException, NoSuchFieldException, IllegalAccessException {
        Properties properties = new Properties();
        properties.put(PropertyKeyConst.SERVER_ADDR, "127.0.0.1:8848");
        aiMaintainerService = AiMaintainerFactory.createAiMaintainerService(properties);
        
        // Inject mock into mcp service
        Field mcpServiceField =
            NacosAiMaintainerServiceImpl.class.getDeclaredField("mcpMaintainerService");
        mcpServiceField.setAccessible(true);
        Object mcpService = mcpServiceField.get(aiMaintainerService);
        injectMockClientHttpProxy(mcpService);
        
        // Inject mock into a2a service
        Field a2aServiceField =
            NacosAiMaintainerServiceImpl.class.getDeclaredField("a2aMaintainerService");
        a2aServiceField.setAccessible(true);
        Object a2aService = a2aServiceField.get(aiMaintainerService);
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
    
    // ========== AiMaintainerService -> mcp() delegation tests ==========
    
    @Test
    @DisplayName("AiMaintainerService.listMcpServer should delegate to mcp()")
    void testListMcpServerDelegation() throws NacosException {
        Page<McpServerBasicInfo> page = new Page<>();
        page.setTotalCount(1);
        page.setPageItems(Collections.singletonList(new McpServerBasicInfo()));
        
        HttpRestResult<String> mockResult = new HttpRestResult<>();
        mockResult.setData(JacksonUtils.toJson(Result.success(page)));
        when(clientHttpProxy.executeSyncHttpRequest(any(HttpRequest.class))).thenReturn(mockResult);
        
        Page<McpServerBasicInfo> result =
            aiMaintainerService.listMcpServer("public", "test", 1, 10);
        assertNotNull(result);
        assertEquals(1, result.getTotalCount());
    }
    
    @Test
    @DisplayName("AiMaintainerService.searchMcpServer should delegate to mcp()")
    void testSearchMcpServerDelegation() throws NacosException {
        Page<McpServerBasicInfo> page = new Page<>();
        page.setTotalCount(1);
        page.setPageItems(Collections.singletonList(new McpServerBasicInfo()));
        
        HttpRestResult<String> mockResult = new HttpRestResult<>();
        mockResult.setData(JacksonUtils.toJson(Result.success(page)));
        when(clientHttpProxy.executeSyncHttpRequest(any(HttpRequest.class))).thenReturn(mockResult);
        
        Page<McpServerBasicInfo> result =
            aiMaintainerService.searchMcpServer("public", "test", 1, 10);
        assertNotNull(result);
        assertEquals(1, result.getTotalCount());
    }
    
    @Test
    @DisplayName("AiMaintainerService.getMcpServerDetail should delegate to mcp()")
    void testGetMcpServerDetailDelegation() throws NacosException {
        McpServerDetailInfo detail = new McpServerDetailInfo();
        detail.setName("testMcp");
        
        HttpRestResult<String> mockResult = new HttpRestResult<>();
        mockResult.setData(JacksonUtils.toJson(Result.success(detail)));
        when(clientHttpProxy.executeSyncHttpRequest(any(HttpRequest.class))).thenReturn(mockResult);
        
        McpServerDetailInfo result =
            aiMaintainerService.getMcpServerDetail("public", "testMcp", "id", "1.0");
        assertNotNull(result);
        assertEquals("testMcp", result.getName());
    }
    
    @Test
    @DisplayName("AiMaintainerService.createMcpServer should delegate to mcp()")
    void testCreateMcpServerDelegation() throws NacosException {
        HttpRestResult<String> mockResult = new HttpRestResult<>();
        mockResult.setData(JacksonUtils.toJson(Result.success("mcp-id-123")));
        when(clientHttpProxy.executeSyncHttpRequest(any(HttpRequest.class))).thenReturn(mockResult);
        
        String result = aiMaintainerService.createMcpServer("public", "testMcp",
            new McpServerBasicInfo(), null, null);
        assertEquals("mcp-id-123", result);
    }
    
    @Test
    @DisplayName("AiMaintainerService.updateMcpServer should delegate to mcp()")
    void testUpdateMcpServerDelegation() throws NacosException {
        HttpRestResult<String> mockResult = new HttpRestResult<>();
        mockResult.setData(JacksonUtils.toJson(Result.success("ok")));
        when(clientHttpProxy.executeSyncHttpRequest(any(HttpRequest.class))).thenReturn(mockResult);
        
        boolean result = aiMaintainerService.updateMcpServer("public", "testMcp", true,
            new McpServerBasicInfo(), null, null, false);
        assertTrue(result);
    }
    
    @Test
    @DisplayName("AiMaintainerService.deleteMcpServer should delegate to mcp()")
    void testDeleteMcpServerDelegation() throws NacosException {
        HttpRestResult<String> mockResult = new HttpRestResult<>();
        mockResult.setData(JacksonUtils.toJson(Result.success("ok")));
        when(clientHttpProxy.executeSyncHttpRequest(any(HttpRequest.class))).thenReturn(mockResult);
        
        boolean result = aiMaintainerService.deleteMcpServer("public", "testMcp", "id", "1.0");
        assertTrue(result);
    }
    
    // ========== AiMaintainerService -> a2a() delegation tests ==========
    
    @Test
    @DisplayName("AiMaintainerService.registerAgent should delegate to a2a()")
    void testRegisterAgentDelegation() throws NacosException {
        AgentCard card = new AgentCard();
        card.setName("testAgent");
        
        HttpRestResult<String> mockResult = new HttpRestResult<>();
        mockResult.setData(JacksonUtils.toJson(Result.success("ok")));
        when(clientHttpProxy.executeSyncHttpRequest(any(HttpRequest.class))).thenReturn(mockResult);
        
        boolean result = aiMaintainerService.registerAgent(card, "public", "url");
        assertTrue(result);
    }
    
    @Test
    @DisplayName("AiMaintainerService.getAgentCard should delegate to a2a()")
    void testGetAgentCardDelegation() throws NacosException {
        AgentCardDetailInfo detail = new AgentCardDetailInfo();
        detail.setName("testAgent");
        
        HttpRestResult<String> mockResult = new HttpRestResult<>();
        mockResult.setData(JacksonUtils.toJson(Result.success(detail)));
        when(clientHttpProxy.executeSyncHttpRequest(any(HttpRequest.class))).thenReturn(mockResult);
        
        AgentCardDetailInfo result =
            aiMaintainerService.getAgentCard("testAgent", "public", "url", "1.0");
        assertNotNull(result);
        assertEquals("testAgent", result.getName());
    }
    
    @Test
    @DisplayName("AiMaintainerService.updateAgentCard should delegate to a2a()")
    void testUpdateAgentCardDelegation() throws NacosException {
        AgentCard card = new AgentCard();
        card.setName("testAgent");
        
        HttpRestResult<String> mockResult = new HttpRestResult<>();
        mockResult.setData(JacksonUtils.toJson(Result.success("ok")));
        when(clientHttpProxy.executeSyncHttpRequest(any(HttpRequest.class))).thenReturn(mockResult);
        
        boolean result = aiMaintainerService.updateAgentCard(card, "public", true, "url");
        assertTrue(result);
    }
    
    @Test
    @DisplayName("AiMaintainerService.deleteAgent should delegate to a2a()")
    void testDeleteAgentDelegation() throws NacosException {
        HttpRestResult<String> mockResult = new HttpRestResult<>();
        mockResult.setData(JacksonUtils.toJson(Result.success("ok")));
        when(clientHttpProxy.executeSyncHttpRequest(any(HttpRequest.class))).thenReturn(mockResult);
        
        boolean result = aiMaintainerService.deleteAgent("testAgent", "public", "1.0");
        assertTrue(result);
    }
    
    @Test
    @DisplayName("AiMaintainerService.listAllVersionOfAgent should delegate to a2a()")
    void testListAllVersionOfAgentDelegation() throws NacosException {
        AgentVersionDetail version = new AgentVersionDetail();
        version.setVersion("1.0");
        
        HttpRestResult<String> mockResult = new HttpRestResult<>();
        mockResult.setData(JacksonUtils.toJson(Result.success(Collections.singletonList(version))));
        when(clientHttpProxy.executeSyncHttpRequest(any(HttpRequest.class))).thenReturn(mockResult);
        
        List<AgentVersionDetail> result =
            aiMaintainerService.listAllVersionOfAgent("testAgent", "public");
        assertNotNull(result);
        assertEquals(1, result.size());
    }
    
    @Test
    @DisplayName("AiMaintainerService.searchAgentCardsByName should delegate to a2a()")
    void testSearchAgentCardsByNameDelegation() throws NacosException {
        Page<AgentCardVersionInfo> page = new Page<>();
        page.setTotalCount(1);
        
        HttpRestResult<String> mockResult = new HttpRestResult<>();
        mockResult.setData(JacksonUtils.toJson(Result.success(page)));
        when(clientHttpProxy.executeSyncHttpRequest(any(HttpRequest.class))).thenReturn(mockResult);
        
        Page<AgentCardVersionInfo> result =
            aiMaintainerService.searchAgentCardsByName("public", "test", 1, 10);
        assertNotNull(result);
        assertEquals(1, result.getTotalCount());
    }
    
    @Test
    @DisplayName("AiMaintainerService.listAgentCards should delegate to a2a()")
    void testListAgentCardsDelegation() throws NacosException {
        Page<AgentCardVersionInfo> page = new Page<>();
        page.setTotalCount(1);
        
        HttpRestResult<String> mockResult = new HttpRestResult<>();
        mockResult.setData(JacksonUtils.toJson(Result.success(page)));
        when(clientHttpProxy.executeSyncHttpRequest(any(HttpRequest.class))).thenReturn(mockResult);
        
        Page<AgentCardVersionInfo> result =
            aiMaintainerService.listAgentCards("public", "testAgent", 1, 10);
        assertNotNull(result);
        assertEquals(1, result.getTotalCount());
    }
}
