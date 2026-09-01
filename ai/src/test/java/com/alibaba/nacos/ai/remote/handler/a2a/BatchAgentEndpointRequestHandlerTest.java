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
import com.alibaba.nacos.api.ai.remote.request.BatchAgentEndpointRequest;
import com.alibaba.nacos.api.ai.remote.response.AgentEndpointResponse;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.remote.request.RequestMeta;
import com.alibaba.nacos.api.remote.response.ResponseCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BatchAgentEndpointRequestHandlerTest {
    
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
    
    private BatchAgentEndpointRequestHandler handler;
    
    @BeforeEach
    void setUp() {
        handler = new BatchAgentEndpointRequestHandler(legacyService,
            compatibilityModeResolver, canonicalService, migrationRouter);
    }
    
    @Test
    void shouldRejectMissingBatchBeforeRouting() throws NacosException {
        assertError(handler.handle(new BatchAgentEndpointRequest(), meta),
            "Required parameter `agentName` can't be empty or null");
        
        BatchAgentEndpointRequest missing = new BatchAgentEndpointRequest();
        missing.setAgentName("demo");
        assertError(handler.handle(missing, meta),
            "Required parameter `endpoints` can't be empty or null, if want to deregister, please use deregister API.");
        missing.setEndpoints(Collections.emptyList());
        assertError(handler.handle(missing, meta),
            "Required parameter `endpoints` can't be empty or null, if want to deregister, please use deregister API.");
        verifyNoInteractions(migrationRouter, compatibilityModeResolver, legacyService,
            canonicalService);
    }
    
    @Test
    void shouldRejectNullEmptyAndMixedVersions() throws NacosException {
        BatchAgentEndpointRequest request = request();
        request.setEndpoints(Collections.singletonList(null));
        assertError(handler.handle(request, meta),
            "Required parameter `endpoint.version` can't be empty or null.");
        
        request.setEndpoints(Collections.singletonList(endpoint(null, 8080)));
        assertError(handler.handle(request, meta),
            "Required parameter `endpoint.version` can't be empty or null.");
        
        request.setEndpoints(Arrays.asList(endpoint("1.0.0", 8080),
            endpoint("2.0.0", 8081)));
        AgentEndpointResponse response = handler.handle(request, meta);
        assertEquals(ResponseCode.FAIL.getCode(), response.getResultCode());
        assertEquals(NacosException.INVALID_PARAM, response.getErrorCode());
        assertTrue(response.getMessage().startsWith(
            "Required parameter `endpoint.version` can't be different, current includes:"));
    }
    
    @Test
    void shouldKeepStaticLegacyAndCanonicalPaths() throws NacosException {
        prepareMeta();
        BatchAgentEndpointRequest request = request();
        when(compatibilityModeResolver.resolve()).thenReturn(A2aCompatibilityMode.LEGACY,
            A2aCompatibilityMode.CANONICAL);
        
        assertEquals(ResponseCode.SUCCESS.getCode(), handler.handle(request, meta).getResultCode());
        verify(legacyService).register(CLIENT_ID, "public", "demo", request.getEndpoints(),
            SOURCE_IP);
        
        assertEquals(ResponseCode.SUCCESS.getCode(), handler.handle(request, meta).getResultCode());
        verify(canonicalService).register(CLIENT_ID, "public", "demo", request.getEndpoints());
    }
    
    @Test
    void shouldRouteMigrationBatchBeforeStaticMode() throws NacosException {
        prepareMeta();
        BatchAgentEndpointRequest request = request();
        when(migrationRouter.resolveState()).thenReturn(A2aMigrationState.QUIESCING);
        
        AgentEndpointResponse response = handler.handle(request, meta);
        
        assertEquals(ResponseCode.SUCCESS.getCode(), response.getResultCode());
        verify(migrationRouter).register(CLIENT_ID, "public", "demo", request.getEndpoints(),
            SOURCE_IP, A2aMigrationState.QUIESCING);
        verifyNoInteractions(compatibilityModeResolver, legacyService, canonicalService);
    }
    
    private void prepareMeta() {
        when(meta.getConnectionId()).thenReturn(CLIENT_ID);
        when(meta.getClientIp()).thenReturn(SOURCE_IP);
    }
    
    private BatchAgentEndpointRequest request() {
        BatchAgentEndpointRequest result = new BatchAgentEndpointRequest();
        result.setNamespaceId("public");
        result.setAgentName("demo");
        result.setEndpoints(Arrays.asList(endpoint("1.0.0", 8080),
            endpoint("1.0.0", 8081)));
        return result;
    }
    
    private AgentEndpoint endpoint(String version, int port) {
        AgentEndpoint result = new AgentEndpoint();
        result.setAddress("127.0.0.1");
        result.setPort(port);
        result.setVersion(version);
        return result;
    }
    
    private void assertError(AgentEndpointResponse response, String message) {
        assertEquals(ResponseCode.FAIL.getCode(), response.getResultCode());
        assertEquals(NacosException.INVALID_PARAM, response.getErrorCode());
        assertEquals(message, response.getMessage());
    }
}
