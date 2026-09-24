/*
 * Copyright 1999-2026 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.ai.service.agent;

import com.alibaba.nacos.api.ai.model.agent.client.AgentPublishRequest;
import com.alibaba.nacos.api.ai.model.agent.AgentVersionDetail;
import com.alibaba.nacos.api.ai.model.agent.AgentCallInterface;
import com.alibaba.nacos.api.ai.model.agent.EndpointSource;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.ai.remote.handler.agent.AgentPublishRpcRequestHandler;
import com.alibaba.nacos.api.ai.remote.request.AgentPublishRpcRequest;
import com.alibaba.nacos.api.ai.remote.response.AgentPublishRpcResponse;
import com.alibaba.nacos.api.remote.request.RequestMeta;
import com.alibaba.nacos.api.utils.json.JsonUtils;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.Arrays;
import java.util.Collections;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoMoreInteractions;

class AgentPublishApplicationServiceTest {
    
    private static final String NAMESPACE_ID = "team";
    private static final String AGENT_NAME = "demo-agent";
    private static final String VERSION = "1.0.0";
    private AgentOperationService operationService;
    private AgentPublishApplicationService service;
    
    @BeforeEach
    void setUp() {
        operationService = mock(AgentOperationService.class);
        service = new AgentPublishApplicationService(operationService);
    }
    
    @Test
    void testRejectsNullRequest() {
        assertThrows(IllegalArgumentException.class, () -> service.publish(NAMESPACE_ID, null));
    }
    
    @Test
    void testRawRpcRejectsIncompleteSourceOrderBeforeWrites() throws Exception {
        AgentPublishRpcRequestHandler handler = new AgentPublishRpcRequestHandler(service,
            mock(AgentClientMigrationGuard.class));
        for (String order : new String[] {"null", "[]", "[\"RUNTIME\"]", "[\"DECLARED\"]",
            "[\"RUNTIME\",\"RUNTIME\"]"}) {
            String json = "{\"namespaceId\":\"team\",\"publishRequest\":{\"agentName\":\"demo\","
                + "\"version\":\"1.0.0\",\"callInterfaces\":[{\"protocol\":\"custom\","
                + "\"descriptorMediaType\":\"application/json\",\"nativeDescriptor\":{},"
                + "\"endpointSourceOrder\":" + order + "}]}}";
            AgentPublishRpcResponse response = handler.handle(JsonUtils.toObj(json,
                AgentPublishRpcRequest.class), mock(RequestMeta.class));
            assertEquals(ErrorCode.PARAMETER_VALIDATE_ERROR.getCode(), response.getErrorCode());
        }
        verify(operationService, never()).writeDraftFromPublication(
            org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any());
        verify(operationService, never()).submit(org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
    }
    
    @ParameterizedTest
    @CsvSource({"true,false", "true,true", "false,true"})
    void testFirstVersionOrExplicitFlagUsesOrdinarySubmit(boolean first, boolean autoSubmit)
        throws Exception {
        AgentPublishRequest request = request(autoSubmit);
        AgentVersionDetail draft = detail("draft");
        AgentVersionDetail reviewing = detail("reviewing");
        when(operationService.writeDraftFromPublication(NAMESPACE_ID, request))
            .thenReturn(new AgentOperationService.PublicationResult(draft, first));
        when(operationService.getVersion(NAMESPACE_ID, AGENT_NAME, VERSION)).thenReturn(reviewing);
        assertSame(reviewing, service.publish(NAMESPACE_ID, request));
        assertEquals(autoSubmit, request.isAutoSubmit());
        verify(operationService).writeDraftFromPublication(NAMESPACE_ID, request);
        verify(operationService).submit(NAMESPACE_ID, AGENT_NAME, VERSION);
        verify(operationService).getVersion(NAMESPACE_ID, AGENT_NAME, VERSION);
        verifyNoMoreInteractions(operationService);
    }
    
    @Test
    void testExistingOrLaterDraftRemainsDraftWithoutAutoSubmit() throws Exception {
        AgentPublishRequest request = request(false);
        AgentVersionDetail draft = detail("draft");
        when(operationService.writeDraftFromPublication(NAMESPACE_ID, request))
            .thenReturn(new AgentOperationService.PublicationResult(draft, false));
        assertSame(draft, service.publish(NAMESPACE_ID, request));
        verify(operationService).writeDraftFromPublication(NAMESPACE_ID, request);
        verifyNoMoreInteractions(operationService);
    }
    
    @ParameterizedTest
    @CsvSource({"reviewing,false", "reviewing,true", "reviewed,false", "reviewed,true",
        "online,false", "online,true", "offline,false", "offline,true"})
    void testNonDraftIsNoopWithEitherFlag(String status, boolean autoSubmit) throws Exception {
        AgentPublishRequest request = request(autoSubmit);
        AgentVersionDetail current = detail(status);
        when(operationService.writeDraftFromPublication(NAMESPACE_ID, request))
            .thenReturn(new AgentOperationService.PublicationResult(current, false));
        assertSame(current, service.publish(NAMESPACE_ID, request));
        verify(operationService).writeDraftFromPublication(NAMESPACE_ID, request);
        verifyNoMoreInteractions(operationService);
    }
    
    @Test
    void testDefinitionFailureIsReturnedWithoutRecoveryOrSubmit() throws Exception {
        AgentPublishRequest request = request(true);
        NacosException failure = new NacosException(500, "uncertain definition write");
        when(operationService.writeDraftFromPublication(NAMESPACE_ID, request)).thenThrow(failure);
        assertSame(failure,
            assertThrows(NacosException.class, () -> service.publish(NAMESPACE_ID, request)));
        verify(operationService).writeDraftFromPublication(NAMESPACE_ID, request);
        verifyNoMoreInteractions(operationService);
    }
    
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testSubmitFailureIsReturnedWithoutRecovery(boolean first) throws Exception {
        AgentPublishRequest request = request(!first);
        when(operationService.writeDraftFromPublication(NAMESPACE_ID, request))
            .thenReturn(new AgentOperationService.PublicationResult(detail("draft"), first));
        NacosException failure = new NacosException(500, "uncertain submit");
        when(operationService.submit(NAMESPACE_ID, AGENT_NAME, VERSION)).thenThrow(failure);
        assertSame(failure,
            assertThrows(NacosException.class, () -> service.publish(NAMESPACE_ID, request)));
        verify(operationService).writeDraftFromPublication(NAMESPACE_ID, request);
        verify(operationService).submit(NAMESPACE_ID, AGENT_NAME, VERSION);
        verifyNoMoreInteractions(operationService);
    }
    
    private AgentPublishRequest request(boolean autoSubmit) {
        AgentCallInterface callInterface = new AgentCallInterface();
        callInterface.setProtocol("custom");
        callInterface.setDescriptorMediaType("application/json");
        callInterface.setNativeDescriptor(Collections.singletonMap("name", AGENT_NAME));
        callInterface
            .setEndpointSourceOrder(Arrays.asList(EndpointSource.RUNTIME, EndpointSource.DECLARED));
        AgentPublishRequest request = new AgentPublishRequest();
        request.setAgentName(AGENT_NAME);
        request.setVersion(VERSION);
        request.setAutoSubmit(autoSubmit);
        request.setCallInterfaces(Collections.singletonList(callInterface));
        return request;
    }
    
    private AgentVersionDetail detail(String status) {
        AgentVersionDetail result = new AgentVersionDetail();
        result.setAgentName(AGENT_NAME);
        result.setVersion(VERSION);
        result.setStatus(status);
        return result;
    }
}
