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

package com.alibaba.nacos.ai.remote.handler.a2a;

import com.alibaba.nacos.ai.service.a2a.A2aCompatibilityMode;
import com.alibaba.nacos.ai.service.a2a.A2aCompatibilityModeResolver;
import com.alibaba.nacos.ai.service.a2a.CanonicalA2aEndpointOperationService;
import com.alibaba.nacos.ai.service.a2a.LegacyA2aEndpointOperationService;
import com.alibaba.nacos.ai.service.a2a.migration.A2aMigrationEndpointRouter;
import com.alibaba.nacos.ai.service.a2a.migration.A2aMigrationState;
import com.alibaba.nacos.api.ai.model.a2a.AgentEndpoint;
import com.alibaba.nacos.api.ai.remote.AiRemoteConstants;
import com.alibaba.nacos.api.ai.remote.request.AgentEndpointRequest;
import com.alibaba.nacos.api.ai.remote.response.AgentEndpointResponse;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.api.remote.request.RequestMeta;
import com.alibaba.nacos.api.remote.response.ResponseCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentEndpointRequestHandlerTest {
    
    private static final String CLIENT_ID = "client-1";
    
    private static final String SOURCE_IP = "127.0.0.1";
    
    @Mock
    private LegacyA2aEndpointOperationService legacyService;
    
    @Mock
    private A2aCompatibilityModeResolver compatibilityModeResolver;
    
    @Mock
    private CanonicalA2aEndpointOperationService canonicalService;
    
    @Mock
    private A2aMigrationEndpointRouter migrationRouter;
    
    @Mock
    private RequestMeta meta;
    
    private AgentEndpointRequestHandler handler;
    
    @BeforeEach
    void setUp() {
        handler = new AgentEndpointRequestHandler(legacyService, compatibilityModeResolver,
            canonicalService, migrationRouter);
    }
    
    @Test
    void shouldRejectInvalidRequestBeforeRouting() throws NacosException {
        assertError(handler.handle(new AgentEndpointRequest(), meta),
            "Required parameter `agentName` can't be empty or null");
        
        AgentEndpointRequest missingEndpoint = new AgentEndpointRequest();
        missingEndpoint.setAgentName("demo");
        assertError(handler.handle(missingEndpoint, meta),
            "Required parameter `endpoint` can't be null");
        
        AgentEndpointRequest missingVersion = new AgentEndpointRequest();
        missingVersion.setAgentName("demo");
        missingVersion.setEndpoint(new AgentEndpoint());
        assertError(handler.handle(missingVersion, meta),
            "Required parameter `endpoint.version` can't be empty or null");
        verifyNoInteractions(migrationRouter, compatibilityModeResolver, legacyService,
            canonicalService);
    }
    
    @Test
    void shouldKeepExplicitLegacyRegisterAndDeregister() throws NacosException {
        prepareMeta();
        when(compatibilityModeResolver.resolve()).thenReturn(A2aCompatibilityMode.LEGACY);
        AgentEndpointRequest request = request(AiRemoteConstants.REGISTER_ENDPOINT);
        
        AgentEndpointResponse registered = handler.handle(request, meta);
        
        assertEquals(ResponseCode.SUCCESS.getCode(), registered.getResultCode());
        verify(legacyService).register(CLIENT_ID, "public", "demo", request.getEndpoint(),
            SOURCE_IP);
        
        request.setType(AiRemoteConstants.DE_REGISTER_ENDPOINT);
        AgentEndpointResponse deregistered = handler.handle(request, meta);
        
        assertEquals(ResponseCode.SUCCESS.getCode(), deregistered.getResultCode());
        verify(legacyService).deregister(CLIENT_ID, "public", "demo", request.getEndpoint(),
            SOURCE_IP);
        verifyNoInteractions(canonicalService);
    }
    
    @Test
    void shouldKeepExplicitCanonicalRegisterAndDeregister() throws NacosException {
        prepareMeta();
        when(compatibilityModeResolver.resolve()).thenReturn(A2aCompatibilityMode.CANONICAL);
        AgentEndpointRequest request = request(AiRemoteConstants.REGISTER_ENDPOINT);
        
        assertEquals(ResponseCode.SUCCESS.getCode(), handler.handle(request, meta).getResultCode());
        verify(canonicalService).register(CLIENT_ID, "public", "demo",
            Collections.singletonList(request.getEndpoint()));
        
        request.setType(AiRemoteConstants.DE_REGISTER_ENDPOINT);
        assertEquals(ResponseCode.SUCCESS.getCode(), handler.handle(request, meta).getResultCode());
        verify(canonicalService).deregister(CLIENT_ID, "public", "demo", "1.0.0");
        verifyNoInteractions(legacyService);
    }
    
    @Test
    void shouldRouteMigrationRegisterAndDeregisterBeforeStaticMode() throws NacosException {
        prepareMeta();
        when(migrationRouter.resolveState()).thenReturn(A2aMigrationState.SYNCING);
        AgentEndpointRequest request = request(AiRemoteConstants.REGISTER_ENDPOINT);
        
        assertEquals(ResponseCode.SUCCESS.getCode(), handler.handle(request, meta).getResultCode());
        verify(migrationRouter).register(CLIENT_ID, "public", "demo", request.getEndpoint(),
            SOURCE_IP, A2aMigrationState.SYNCING);
        
        request.setType(AiRemoteConstants.DE_REGISTER_ENDPOINT);
        assertEquals(ResponseCode.SUCCESS.getCode(), handler.handle(request, meta).getResultCode());
        verify(migrationRouter).deregister(CLIENT_ID, "public", "demo", request.getEndpoint(),
            SOURCE_IP, A2aMigrationState.SYNCING);
        verifyNoInteractions(compatibilityModeResolver, legacyService, canonicalService);
    }
    
    @Test
    void shouldMapValidationFailureAndPropagateRuntimeFailure() throws NacosException {
        prepareMeta();
        when(migrationRouter.resolveState()).thenReturn(A2aMigrationState.QUIESCING);
        AgentEndpointRequest request = request(AiRemoteConstants.REGISTER_ENDPOINT);
        doThrow(new NacosApiException(NacosException.INVALID_PARAM,
            ErrorCode.PARAMETER_VALIDATE_ERROR, "invalid endpoint"))
            .when(migrationRouter).register(CLIENT_ID, "public", "demo", request.getEndpoint(),
                SOURCE_IP, A2aMigrationState.QUIESCING);
        
        assertError(handler.handle(request, meta), "invalid endpoint");
        
        doThrow(new NacosException(NacosException.SERVER_ERROR, "server failed"))
            .when(migrationRouter).register(CLIENT_ID, "public", "demo", request.getEndpoint(),
                SOURCE_IP, A2aMigrationState.QUIESCING);
        assertThrows(NacosException.class, () -> handler.handle(request, meta));
    }
    
    @Test
    void shouldRejectInvalidTypeOnStaticAndMigrationPaths() throws NacosException {
        AgentEndpointRequest request = request("invalid");
        when(compatibilityModeResolver.resolve()).thenReturn(A2aCompatibilityMode.LEGACY);
        assertError(handler.handle(request, meta),
            "parameter `type` should be registerEndpoint or deregisterEndpoint, but was invalid");
        
        when(compatibilityModeResolver.resolve()).thenReturn(A2aCompatibilityMode.CANONICAL);
        assertError(handler.handle(request, meta),
            "parameter `type` should be registerEndpoint or deregisterEndpoint, but was invalid");
        
        when(migrationRouter.resolveState()).thenReturn(A2aMigrationState.SYNCING);
        assertError(handler.handle(request, meta),
            "parameter `type` should be registerEndpoint or deregisterEndpoint, but was invalid");
    }
    
    private void prepareMeta() {
        when(meta.getConnectionId()).thenReturn(CLIENT_ID);
        lenient().when(meta.getClientIp()).thenReturn(SOURCE_IP);
    }
    
    private AgentEndpointRequest request(String type) {
        AgentEndpointRequest result = new AgentEndpointRequest();
        result.setNamespaceId("public");
        result.setAgentName("demo");
        AgentEndpoint endpoint = new AgentEndpoint();
        endpoint.setAddress("127.0.0.1");
        endpoint.setPort(8080);
        endpoint.setVersion("1.0.0");
        result.setEndpoint(endpoint);
        result.setType(type);
        return result;
    }
    
    private void assertError(AgentEndpointResponse response, String message) {
        assertEquals(ResponseCode.FAIL.getCode(), response.getResultCode());
        assertEquals(NacosException.INVALID_PARAM, response.getErrorCode());
        assertEquals(message, response.getMessage());
    }
}
