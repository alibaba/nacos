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

package com.alibaba.nacos.ai.remote.handler.a2a;

import com.alibaba.nacos.ai.service.a2a.A2aCompatibilityOperationService;
import com.alibaba.nacos.api.ai.model.a2a.AgentCard;
import com.alibaba.nacos.api.ai.remote.request.ReleaseAgentCardRequest;
import com.alibaba.nacos.api.ai.remote.response.ReleaseAgentCardResponse;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.api.remote.request.RequestMeta;
import com.alibaba.nacos.api.remote.response.ResponseCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ReleaseAgentCardRequestHandlerTest {
    
    @Mock
    private A2aCompatibilityOperationService a2aServerOperationService;
    
    @Mock
    private RequestMeta meta;
    
    private ReleaseAgentCardRequestHandler requestHandler;
    
    @BeforeEach
    void setUp() {
        requestHandler = new ReleaseAgentCardRequestHandler(a2aServerOperationService);
    }
    
    @AfterEach
    void tearDown() {
    }
    
    @Test
    void handleWithNullAgentCard() throws NacosException {
        ReleaseAgentCardRequest request = new ReleaseAgentCardRequest();
        ReleaseAgentCardResponse response = requestHandler.handle(request, meta);
        assertEquals(ResponseCode.FAIL.getCode(), response.getResultCode());
        assertEquals(NacosException.INVALID_PARAM, response.getErrorCode());
        assertEquals("parameters `agentCard` can't be null", response.getMessage());
    }
    
    @Test
    void handleWithValidNewAgentCard() throws NacosException {
        final ReleaseAgentCardRequest request = new ReleaseAgentCardRequest();
        AgentCard agentCard = new AgentCard();
        agentCard.setName("test");
        agentCard.setVersion("1.0.0");
        agentCard.setProtocolVersion("0.3.0");
        agentCard.setPreferredTransport("JSONRPC");
        agentCard.setUrl("https://example.com");
        request.setAgentCard(agentCard);
        request.setNamespaceId("public");
        ReleaseAgentCardResponse response = requestHandler.handle(request, meta);
        assertEquals(ResponseCode.SUCCESS.getCode(), response.getResultCode());
        assertNull(response.getMessage());
        verify(a2aServerOperationService).releaseAgent(eq(agentCard), eq("public"),
            eq("SERVICE"), eq(false));
    }
    
    @Test
    void handleWithValidNewVersionAgentCard() throws NacosException {
        final ReleaseAgentCardRequest request = new ReleaseAgentCardRequest();
        AgentCard agentCard = new AgentCard();
        agentCard.setName("test");
        agentCard.setVersion("1.0.0");
        agentCard.setProtocolVersion("0.3.0");
        agentCard.setPreferredTransport("JSONRPC");
        agentCard.setUrl("https://example.com");
        request.setAgentCard(agentCard);
        request.setNamespaceId("public");
        request.setSetAsLatest(true);
        ReleaseAgentCardResponse response = requestHandler.handle(request, meta);
        assertEquals(ResponseCode.SUCCESS.getCode(), response.getResultCode());
        assertNull(response.getMessage());
        verify(a2aServerOperationService).releaseAgent(eq(agentCard), eq("public"),
            eq("SERVICE"), eq(true));
    }
    
    @Test
    void handleWithOtherException() throws NacosException {
        final ReleaseAgentCardRequest request = new ReleaseAgentCardRequest();
        AgentCard agentCard = new AgentCard();
        agentCard.setName("test");
        agentCard.setVersion("1.0.0");
        agentCard.setProtocolVersion("0.3.0");
        agentCard.setPreferredTransport("JSONRPC");
        agentCard.setUrl("https://example.com");
        request.setAgentCard(agentCard);
        request.setNamespaceId("public");
        doThrow(new NacosApiException(NacosException.SERVER_ERROR, ErrorCode.SERVER_ERROR,
            "test error")).when(a2aServerOperationService).releaseAgent(agentCard, "public",
                "SERVICE", false);
        ReleaseAgentCardResponse response = requestHandler.handle(request, meta);
        assertEquals(ResponseCode.FAIL.getCode(), response.getResultCode());
        assertEquals(NacosException.SERVER_ERROR, response.getErrorCode());
        assertEquals("test error", response.getMessage());
        verify(a2aServerOperationService).releaseAgent(agentCard, "public", "SERVICE", false);
    }
    
    @Test
    void shouldPreserveMigrationFenceDetailCode() throws NacosException {
        ReleaseAgentCardRequest request = new ReleaseAgentCardRequest();
        AgentCard agentCard = new AgentCard();
        agentCard.setName("test");
        agentCard.setVersion("1.0.0");
        agentCard.setProtocolVersion("0.3.0");
        agentCard.setPreferredTransport("JSONRPC");
        agentCard.setUrl("https://example.com");
        request.setAgentCard(agentCard);
        request.setNamespaceId("public");
        doThrow(new NacosApiException(NacosException.CONFLICT,
            ErrorCode.AGENT_MIGRATION_IN_PROGRESS, "retry later"))
            .when(a2aServerOperationService).releaseAgent(agentCard, "public", "SERVICE", false);
        ReleaseAgentCardResponse response = requestHandler.handle(request, meta);
        assertEquals(ResponseCode.FAIL.getCode(), response.getResultCode());
        assertEquals(ErrorCode.AGENT_MIGRATION_IN_PROGRESS.getCode(), response.getErrorCode());
        assertEquals("retry later", response.getMessage());
    }
}
