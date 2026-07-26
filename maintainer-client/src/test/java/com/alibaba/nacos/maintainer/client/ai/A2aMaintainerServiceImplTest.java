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
import com.alibaba.nacos.api.ai.model.a2a.AgentInterface;
import com.alibaba.nacos.api.ai.model.a2a.AgentVersionDetail;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.common.http.HttpRestResult;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.maintainer.client.model.HttpRequest;
import com.alibaba.nacos.maintainer.client.remote.ClientHttpProxy;
import org.junit.jupiter.api.BeforeEach;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class A2aMaintainerServiceImplTest {
    
    @Mock
    private ClientHttpProxy clientHttpProxy;
    
    private A2aMaintainerService a2aService;
    
    @BeforeEach
    void setUp() throws Exception {
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
    
    private void injectMockClientHttpProxy(Object service) throws Exception {
        Field contextField = AbstractAiDelegateMaintainerService.class.getDeclaredField("context");
        contextField.setAccessible(true);
        Object context = contextField.get(service);
        Field clientHttpProxyField =
            AiMaintainerHttpContext.class.getDeclaredField("clientHttpProxy");
        clientHttpProxyField.setAccessible(true);
        clientHttpProxyField.set(context, clientHttpProxy);
    }
    
    @Test
    void testRegisterAgentSuccess() throws NacosException {
        HttpRestResult<String> mockResult = buildSuccessResult();
        when(clientHttpProxy.executeSyncHttpRequest(any(HttpRequest.class))).thenReturn(mockResult);
        
        assertTrue(a2aService.registerAgent(buildLegacyAgentCard(), "public", "url"));
        verify(clientHttpProxy, times(1)).executeSyncHttpRequest(any(HttpRequest.class));
    }
    
    @Test
    void testRegisterAgentV1Success() throws NacosException {
        HttpRestResult<String> mockResult = buildSuccessResult();
        when(clientHttpProxy.executeSyncHttpRequest(any(HttpRequest.class))).thenReturn(mockResult);
        
        assertTrue(a2aService.registerAgent(buildV1AgentCard(), "public", "url"));
        verify(clientHttpProxy, times(1)).executeSyncHttpRequest(any(HttpRequest.class));
    }
    
    @Test
    void testRegisterAgentV1ShouldFallbackOnOldServer() throws NacosException {
        NacosException legacyValidationError = new NacosException(NacosException.INVALID_PARAM,
            "Required parameter `agentCard.protocolVersion` not present");
        HttpRestResult<String> successResult = buildSuccessResult();
        when(clientHttpProxy.executeSyncHttpRequest(any(HttpRequest.class)))
            .thenThrow(legacyValidationError)
            .thenReturn(successResult);
        
        assertTrue(a2aService.registerAgent(buildV1AgentCard(), "public", "url"));
        verify(clientHttpProxy, times(2)).executeSyncHttpRequest(any(HttpRequest.class));
    }
    
    @Test
    void testRegisterAgentShouldNotFallbackOnOtherErrors() throws NacosException {
        NacosException otherError =
            new NacosException(NacosException.SERVER_ERROR, "Internal server error");
        when(clientHttpProxy.executeSyncHttpRequest(any(HttpRequest.class))).thenThrow(otherError);
        
        NacosException thrown = assertThrows(NacosException.class,
            () -> a2aService.registerAgent(buildV1AgentCard(), "public", "url"));
        assertEquals(NacosException.SERVER_ERROR, thrown.getErrCode());
        verify(clientHttpProxy, times(1)).executeSyncHttpRequest(any(HttpRequest.class));
    }
    
    @Test
    void testUpdateAgentCardSuccess() throws NacosException {
        HttpRestResult<String> mockResult = buildSuccessResult();
        when(clientHttpProxy.executeSyncHttpRequest(any(HttpRequest.class))).thenReturn(mockResult);
        
        assertTrue(a2aService.updateAgentCard(buildLegacyAgentCard(), "public", true, "url"));
        verify(clientHttpProxy, times(1)).executeSyncHttpRequest(any(HttpRequest.class));
    }
    
    @Test
    void testUpdateAgentCardV1ShouldFallbackOnOldServer() throws NacosException {
        NacosException legacyValidationError = new NacosException(NacosException.INVALID_PARAM,
            "Required parameter `agentCard.preferredTransport` not present");
        HttpRestResult<String> successResult = buildSuccessResult();
        when(clientHttpProxy.executeSyncHttpRequest(any(HttpRequest.class)))
            .thenThrow(legacyValidationError)
            .thenReturn(successResult);
        
        assertTrue(a2aService.updateAgentCard(buildV1AgentCard(), "public", true, "url"));
        verify(clientHttpProxy, times(2)).executeSyncHttpRequest(any(HttpRequest.class));
    }
    
    @Test
    void testUpdateAgentCardShouldNotFallbackOnOtherErrors() throws NacosException {
        NacosException conflictError =
            new NacosException(NacosException.CONFLICT, "Agent already exists");
        when(clientHttpProxy.executeSyncHttpRequest(any(HttpRequest.class)))
            .thenThrow(conflictError);
        
        NacosException thrown = assertThrows(NacosException.class,
            () -> a2aService.updateAgentCard(buildV1AgentCard(), "public", true, "url"));
        assertEquals(NacosException.CONFLICT, thrown.getErrCode());
        verify(clientHttpProxy, times(1)).executeSyncHttpRequest(any(HttpRequest.class));
    }
    
    @Test
    void testGetAgentCardSuccess() throws NacosException {
        AgentCardDetailInfo detailInfo = new AgentCardDetailInfo();
        detailInfo.setName("test-agent");
        detailInfo.setVersion("1.0");
        HttpRestResult<String> mockResult = new HttpRestResult<>();
        mockResult.setData(JacksonUtils.toJson(Result.success(detailInfo)));
        when(clientHttpProxy.executeSyncHttpRequest(any(HttpRequest.class))).thenReturn(mockResult);
        
        AgentCardDetailInfo result = a2aService.getAgentCard("test-agent", "public", "url", "1.0");
        assertNotNull(result);
        assertEquals("test-agent", result.getName());
    }
    
    @Test
    void testDeleteAgentSuccess() throws NacosException {
        HttpRestResult<String> mockResult = buildSuccessResult();
        when(clientHttpProxy.executeSyncHttpRequest(any(HttpRequest.class))).thenReturn(mockResult);
        
        assertTrue(a2aService.deleteAgent("test-agent", "public", "1.0"));
    }
    
    @Test
    void testListAllVersionOfAgent() throws NacosException {
        AgentVersionDetail detail = new AgentVersionDetail();
        detail.setVersion("1.0");
        detail.setLatest(true);
        HttpRestResult<String> mockResult = new HttpRestResult<>();
        mockResult.setData(JacksonUtils.toJson(Result.success(Collections.singletonList(detail))));
        when(clientHttpProxy.executeSyncHttpRequest(any(HttpRequest.class))).thenReturn(mockResult);
        
        List<AgentVersionDetail> versions =
            a2aService.listAllVersionOfAgent("test-agent", "public");
        assertNotNull(versions);
        assertEquals(1, versions.size());
    }
    
    @Test
    void testSearchAgentCardsByName() throws NacosException {
        Page<AgentCardVersionInfo> page = new Page<>();
        page.setTotalCount(1);
        page.setPageItems(Collections.singletonList(new AgentCardVersionInfo()));
        HttpRestResult<String> mockResult = new HttpRestResult<>();
        mockResult.setData(JacksonUtils.toJson(Result.success(page)));
        when(clientHttpProxy.executeSyncHttpRequest(any(HttpRequest.class))).thenReturn(mockResult);
        
        Page<AgentCardVersionInfo> result =
            a2aService.searchAgentCardsByName("public", "test", 1, 10);
        assertNotNull(result);
        assertEquals(1, result.getTotalCount());
    }
    
    @Test
    void testListAgentCards() throws NacosException {
        Page<AgentCardVersionInfo> page = new Page<>();
        page.setTotalCount(0);
        page.setPageItems(Collections.emptyList());
        HttpRestResult<String> mockResult = new HttpRestResult<>();
        mockResult.setData(JacksonUtils.toJson(Result.success(page)));
        when(clientHttpProxy.executeSyncHttpRequest(any(HttpRequest.class))).thenReturn(mockResult);
        
        Page<AgentCardVersionInfo> result = a2aService.listAgentCards("public", "test", 1, 10);
        assertNotNull(result);
        assertEquals(0, result.getTotalCount());
    }
    
    private AgentCard buildLegacyAgentCard() {
        AgentCard card = new AgentCard();
        card.setName("test-agent");
        card.setVersion("1.0");
        card.setProtocolVersion("0.2.6");
        card.setPreferredTransport("JSONRPC");
        card.setUrl("http://localhost:8080");
        return card;
    }
    
    private AgentCard buildV1AgentCard() {
        AgentCard card = new AgentCard();
        card.setName("test-agent");
        card.setVersion("1.0");
        AgentInterface iface = new AgentInterface();
        iface.setUrl("http://localhost:8080");
        iface.setProtocolBinding("JSONRPC");
        iface.setProtocolVersion("1.0.0");
        card.setSupportedInterfaces(Collections.singletonList(iface));
        return card;
    }
    
    private HttpRestResult<String> buildSuccessResult() {
        HttpRestResult<String> result = new HttpRestResult<>();
        result.setData(JacksonUtils.toJson(new Result<>(ErrorCode.SUCCESS.getCode(), "ok")));
        return result;
    }
}
