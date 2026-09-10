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

package com.alibaba.nacos.client.ai.remote;

import com.alibaba.nacos.api.ai.AgentTransportMode;
import com.alibaba.nacos.api.ai.model.agent.ClientLivenessInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpServerBasicInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpEndpointSpec;
import com.alibaba.nacos.api.ai.model.mcp.McpResourceSpecification;
import com.alibaba.nacos.api.ai.model.mcp.McpToolSpecification;
import com.alibaba.nacos.api.ai.model.mcp.McpServerDetailInfo;
import com.alibaba.nacos.api.exception.NacosException;
import io.grpc.Status;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class McpTransportRouterTest {
    
    @Mock
    private AgentGrpcTransport sharedGrpcTransport;
    
    @Mock
    private McpGrpcTransport grpcTransport;
    
    @Mock
    private McpHttpTransport httpTransport;
    
    private McpTransportRouter router;
    
    @BeforeEach
    void setUp() {
        router = new McpTransportRouter(AgentTransportMode.AUTO, sharedGrpcTransport, grpcTransport,
            httpTransport);
        lenient().when(grpcTransport.getType()).thenReturn(AgentTransportType.GRPC);
        lenient().when(httpTransport.getType()).thenReturn(AgentTransportType.HTTP);
    }
    
    @Test
    void explicitModesSelectOnlyConfiguredTransport() throws Exception {
        McpServerDetailInfo grpcResult = new McpServerDetailInfo();
        McpServerDetailInfo httpResult = new McpServerDetailInfo();
        when(grpcTransport.queryMcpServer("mcp", null)).thenReturn(grpcResult);
        when(httpTransport.queryMcpServer("mcp", null)).thenReturn(httpResult);
        router = new McpTransportRouter(AgentTransportMode.GRPC, sharedGrpcTransport, grpcTransport,
            httpTransport);
        assertSame(grpcResult, router.queryMcpServer("mcp", null));
        router = new McpTransportRouter(AgentTransportMode.HTTP, sharedGrpcTransport, grpcTransport,
            httpTransport);
        assertSame(httpResult, router.queryMcpServer("mcp", null));
        verify(sharedGrpcTransport).recordHttpSuccess(AgentGrpcTransport.Resource.MCP);
    }
    
    @Test
    void autoFallsBackForConnectionFailureOnly() throws Exception {
        McpServerDetailInfo expected = new McpServerDetailInfo();
        router = new McpTransportRouter(AgentTransportMode.AUTO, sharedGrpcTransport, grpcTransport,
            httpTransport);
        when(sharedGrpcTransport.isAvailable(AgentGrpcTransport.Resource.MCP)).thenReturn(true);
        when(grpcTransport.queryMcpServer("mcp", "1.0.0"))
            .thenThrow(new NacosException(NacosException.CLIENT_DISCONNECT, "disconnected"));
        when(httpTransport.queryMcpServer("mcp", "1.0.0")).thenReturn(expected);
        
        assertSame(expected, router.queryMcpServer("mcp", "1.0.0"));
        verify(sharedGrpcTransport).recordHttpSuccess(AgentGrpcTransport.Resource.MCP);
    }
    
    @Test
    void autoRecognizesGrpcUnavailableCause() throws Exception {
        McpServerDetailInfo expected = new McpServerDetailInfo();
        router = new McpTransportRouter(AgentTransportMode.AUTO, sharedGrpcTransport, grpcTransport,
            httpTransport);
        when(sharedGrpcTransport.isAvailable(AgentGrpcTransport.Resource.MCP)).thenReturn(true);
        NacosException unavailable = new NacosException(NacosException.SERVER_ERROR,
            "unavailable", Status.UNAVAILABLE.asRuntimeException());
        when(grpcTransport.queryMcpServer("mcp", null)).thenThrow(unavailable);
        when(httpTransport.queryMcpServer("mcp", null)).thenReturn(expected);
        
        assertSame(expected, router.queryMcpServer("mcp", null));
    }
    
    @Test
    void autoNeverFallsBackForBusinessFailureOrRelease() throws Exception {
        router = new McpTransportRouter(AgentTransportMode.AUTO, sharedGrpcTransport, grpcTransport,
            httpTransport);
        when(sharedGrpcTransport.isAvailable(AgentGrpcTransport.Resource.MCP)).thenReturn(true);
        NacosException invalid = new NacosException(NacosException.INVALID_PARAM, "invalid");
        when(grpcTransport.queryMcpServer("mcp", null)).thenThrow(invalid);
        assertSame(invalid,
            assertThrows(NacosException.class, () -> router.queryMcpServer("mcp", null)));
        verify(httpTransport, never()).queryMcpServer(anyString(), isNull());
        
        when(grpcTransport.releaseMcpServer(any(McpServerBasicInfo.class), isNull(), isNull(),
            isNull(), anyBoolean()))
            .thenThrow(new NacosException(NacosException.CLIENT_DISCONNECT, "disconnect"));
        assertThrows(NacosException.class,
            () -> router.releaseMcpServer(new McpServerBasicInfo(), null, null, null, false));
        verify(httpTransport, never()).releaseMcpServer(any(), any(), any(), any(), anyBoolean());
    }
    
    @Test
    void endpointOwnerRemainsStickyAndHeartbeatAlwaysUsesHttp() throws Exception {
        ClientLivenessInfo expected = new ClientLivenessInfo();
        when(httpTransport.registerMcpServerEndpoint("mcp", "127.0.0.1", 8080, null))
            .thenReturn(expected);
        when(httpTransport.heartbeatMcpServerEndpoints()).thenReturn(expected);
        assertSame(expected, router.registerMcpServerEndpoint("mcp", "127.0.0.1", 8080, null,
            AgentTransportType.HTTP));
        router.deregisterMcpServerEndpoint("mcp", "127.0.0.1", 8080,
            AgentTransportType.HTTP);
        assertSame(expected, router.heartbeatMcpServerEndpoints());
        verify(httpTransport).deregisterMcpServerEndpoint("mcp", "127.0.0.1", 8080);
        verify(grpcTransport, never()).registerMcpServerEndpoint(anyString(), anyString(), anyInt(),
            any());
        assertEquals(AgentTransportType.HTTP, httpTransport.getType());
    }
    
    @Test
    void businessErrorsWinOverConcurrentDisconnectAndUnavailableCause() throws Exception {
        router = new McpTransportRouter(AgentTransportMode.AUTO, sharedGrpcTransport, grpcTransport,
            httpTransport);
        when(sharedGrpcTransport.isAvailable(AgentGrpcTransport.Resource.MCP)).thenReturn(true);
        
        int[] codes = {401, NacosException.NO_RIGHT, NacosException.INVALID_PARAM,
            NacosException.NOT_FOUND, NacosException.CONFLICT, NacosException.OVER_THRESHOLD,
            NacosException.CLIENT_OVER_THRESHOLD, NacosException.SERVER_NOT_IMPLEMENTED};
        for (int code : codes) {
            NacosException failure = new NacosException(code, "business failure",
                Status.UNAVAILABLE.asRuntimeException());
            org.mockito.Mockito.doThrow(failure).when(grpcTransport).queryMcpServer("mcp", null);
            assertSame(failure,
                assertThrows(NacosException.class, () -> router.queryMcpServer("mcp", null)));
        }
        verify(httpTransport, never()).queryMcpServer("mcp", null);
    }
    
    @Test
    void autoFallsBackWhenConnectionDisappearsBeforeCapabilityCheck() throws Exception {
        when(sharedGrpcTransport.isAvailable(AgentGrpcTransport.Resource.MCP)).thenReturn(true);
        com.alibaba.nacos.api.exception.runtime.NacosRuntimeException failure =
            new com.alibaba.nacos.api.exception.runtime.NacosRuntimeException(
                NacosException.SERVER_ERROR,
                "connection unavailable",
                new NacosException(NacosException.CLIENT_DISCONNECT, "disconnected"));
        when(grpcTransport.queryMcpServer("mcp", null)).thenThrow(failure);
        McpServerDetailInfo expected = new McpServerDetailInfo();
        when(httpTransport.queryMcpServer("mcp", null)).thenReturn(expected);
        assertSame(expected, router.queryMcpServer("mcp", null));
    }
    
    @Test
    void explicitGrpcReadFailureNeverFallsBackOrRecordsHttpSuccess() throws Exception {
        router = new McpTransportRouter(AgentTransportMode.GRPC, sharedGrpcTransport, grpcTransport,
            httpTransport);
        NacosException failure =
            new NacosException(NacosException.CLIENT_DISCONNECT, "disconnected");
        when(grpcTransport.queryMcpServer("mcp", "1.0.0")).thenThrow(failure);
        
        assertSame(failure,
            assertThrows(NacosException.class, () -> router.queryMcpServer("mcp", "1.0.0")));
        verifyNoInteractions(httpTransport, sharedGrpcTransport);
    }
    
    @ParameterizedTest
    @EnumSource(value = AgentTransportMode.class, names = {"HTTP", "AUTO"})
    void httpReadFailureIsNotRetriedOverGrpc(AgentTransportMode mode) throws Exception {
        router = new McpTransportRouter(mode, sharedGrpcTransport, grpcTransport, httpTransport);
        NacosException failure =
            new NacosException(NacosException.CLIENT_DISCONNECT, "HTTP failed");
        when(httpTransport.queryMcpServer("mcp", "1.0.0")).thenThrow(failure);
        
        assertSame(failure,
            assertThrows(NacosException.class, () -> router.queryMcpServer("mcp", "1.0.0")));
        verifyNoInteractions(grpcTransport);
        verify(sharedGrpcTransport, never()).recordHttpSuccess(AgentGrpcTransport.Resource.MCP);
    }
    
    @ParameterizedTest
    @EnumSource(value = AgentTransportMode.class, names = {"HTTP", "GRPC"})
    void releasePreservesCompleteContentAndSelectedTransport(AgentTransportMode mode)
        throws Exception {
        router = new McpTransportRouter(mode, sharedGrpcTransport, grpcTransport, httpTransport);
        McpTransport selected = mode == AgentTransportMode.HTTP ? httpTransport : grpcTransport;
        McpServerBasicInfo server = new McpServerBasicInfo();
        McpToolSpecification tools = new McpToolSpecification();
        McpResourceSpecification resources = new McpResourceSpecification();
        McpEndpointSpec endpoint = new McpEndpointSpec();
        when(selected.releaseMcpServer(server, tools, resources, endpoint, true))
            .thenReturn("draft-id");
        
        assertEquals("draft-id", router.releaseMcpServer(server, tools, resources, endpoint, true));
        if (mode == AgentTransportMode.HTTP) {
            verify(sharedGrpcTransport).recordHttpSuccess(AgentGrpcTransport.Resource.MCP);
            verifyNoInteractions(grpcTransport);
        } else {
            verifyNoInteractions(httpTransport, sharedGrpcTransport);
        }
    }
    
    @Test
    void autoSelectionChangesDoNotMoveExistingGrpcPublication() throws Exception {
        when(sharedGrpcTransport.isAvailable(AgentGrpcTransport.Resource.MCP)).thenReturn(true,
            false);
        assertEquals(AgentTransportType.GRPC, router.selectPublicationTransport());
        assertEquals(AgentTransportType.HTTP, router.selectPublicationTransport());
        
        ClientLivenessInfo result = new ClientLivenessInfo();
        when(grpcTransport.registerMcpServerEndpoint("mcp", "127.0.0.1", 8080, "1.0.0"))
            .thenReturn(result);
        assertSame(result, router.registerMcpServerEndpoint("mcp", "127.0.0.1", 8080, "1.0.0",
            AgentTransportType.GRPC));
        router.deregisterMcpServerEndpoint("mcp", "127.0.0.1", 8080, AgentTransportType.GRPC);
        
        verify(grpcTransport).deregisterMcpServerEndpoint("mcp", "127.0.0.1", 8080);
        verify(sharedGrpcTransport, never()).recordHttpSuccess(AgentGrpcTransport.Resource.MCP);
        verify(httpTransport, never()).registerMcpServerEndpoint(anyString(), anyString(), anyInt(),
            any());
        verify(httpTransport, never()).deregisterMcpServerEndpoint(anyString(), anyString(),
            anyInt());
    }
}
