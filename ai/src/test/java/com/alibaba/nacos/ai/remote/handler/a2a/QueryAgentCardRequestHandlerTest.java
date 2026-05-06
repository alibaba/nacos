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

import com.alibaba.nacos.ai.service.a2a.A2aServerOperationService;
import com.alibaba.nacos.api.ai.model.a2a.AgentCardDetailInfo;
import com.alibaba.nacos.api.ai.remote.request.QueryAgentCardRequest;
import com.alibaba.nacos.api.ai.remote.response.QueryAgentCardResponse;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QueryAgentCardRequestHandlerTest {
    
    @Mock
    private A2aServerOperationService a2aServerOperationService;
    
    @Mock
    private RequestMeta meta;
    
    private QueryAgentCardRequestHandler requestHandler;
    
    @BeforeEach
    void setUp() {
        requestHandler = new QueryAgentCardRequestHandler(a2aServerOperationService);
    }
    
    @AfterEach
    void tearDown() {
    }
    
    @Test
    void handleWithInvalidAgentName() throws NacosException {
        QueryAgentCardRequest request = new QueryAgentCardRequest();
        QueryAgentCardResponse response = requestHandler.handle(request, meta);
        assertEquals(ResponseCode.FAIL.getCode(), response.getResultCode());
        assertEquals(NacosException.INVALID_PARAM, response.getErrorCode());
        assertEquals("parameters `agentName` can't be empty or null", response.getMessage());
    }
    
    @Test
    void handleWithValidParameters() throws NacosException {
        QueryAgentCardRequest request = new QueryAgentCardRequest();
        request.setAgentName("test");
        request.setNamespaceId("public");
        AgentCardDetailInfo mockAgentCard = new AgentCardDetailInfo();
        mockAgentCard.setName("test");
        when(a2aServerOperationService.getAgentCard("public", "test", null, null)).thenReturn(mockAgentCard);
        QueryAgentCardResponse response = requestHandler.handle(request, meta);
        assertEquals(mockAgentCard, response.getAgentCardDetailInfo());
        assertNull(response.getMessage());
    }
    
    @Test
    void handleWithException() throws NacosException {
        QueryAgentCardRequest request = new QueryAgentCardRequest();
        request.setAgentName("test");
        request.setNamespaceId("public");
        when(a2aServerOperationService.getAgentCard("public", "test", null, null)).thenThrow(
                new NacosApiException(NacosException.SERVER_ERROR, ErrorCode.SERVER_ERROR, "test error"));
        QueryAgentCardResponse response = requestHandler.handle(request, meta);
        assertEquals(ResponseCode.FAIL.getCode(), response.getResultCode());
        assertEquals(NacosException.SERVER_ERROR, response.getErrorCode());
        assertEquals("test error", response.getMessage());
    }
}