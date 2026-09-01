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
import com.alibaba.nacos.api.ai.model.mcp.McpServerDetailInfo;
import com.alibaba.nacos.api.exception.NacosException;
import io.grpc.Status;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
        router = new McpTransportRouter(sharedGrpcTransport, grpcTransport, httpTransport);
        lenient().when(grpcTransport.getType()).thenReturn(AgentTransportType.GRPC);
        lenient().when(httpTransport.getType()).thenReturn(AgentTransportType.HTTP);
    }
    
    @Test
    void explicitModesSelectOnlyConfiguredTransport() throws Exception {
        McpServerDetailInfo grpcResult = new McpServerDetailInfo();
        McpServerDetailInfo httpResult = new McpServerDetailInfo();
        when(grpcTransport.queryMcpServer("mcp", null)).thenReturn(grpcResult);
        when(httpTransport.queryMcpServer("mcp", null)).thenReturn(httpResult);
        when(sharedGrpcTransport.getMode()).thenReturn(AgentTransportMode.GRPC);
        assertSame(grpcResult, router.queryMcpServer("mcp", null));
        when(sharedGrpcTransport.getMode()).thenReturn(AgentTransportMode.HTTP);
        assertSame(httpResult, router.queryMcpServer("mcp", null));
        verify(sharedGrpcTransport).recordHttpSuccess();
    }
    
    @Test
    void autoFallsBackForConnectionFailureOnly() throws Exception {
        McpServerDetailInfo expected = new McpServerDetailInfo();
        when(sharedGrpcTransport.getMode()).thenReturn(AgentTransportMode.AUTO);
        when(sharedGrpcTransport.isMcpAvailable()).thenReturn(true);
        when(sharedGrpcTransport.isConnected()).thenReturn(true);
        when(grpcTransport.queryMcpServer("mcp", "1.0.0"))
            .thenThrow(new NacosException(NacosException.CLIENT_DISCONNECT, "disconnected"));
        when(httpTransport.queryMcpServer("mcp", "1.0.0")).thenReturn(expected);
        
        assertSame(expected, router.queryMcpServer("mcp", "1.0.0"));
        verify(sharedGrpcTransport).recordHttpSuccess();
    }
    
    @Test
    void autoRecognizesGrpcUnavailableCause() throws Exception {
        McpServerDetailInfo expected = new McpServerDetailInfo();
        when(sharedGrpcTransport.getMode()).thenReturn(AgentTransportMode.AUTO);
        when(sharedGrpcTransport.isMcpAvailable()).thenReturn(true);
        when(sharedGrpcTransport.isConnected()).thenReturn(true);
        NacosException unavailable = new NacosException(NacosException.SERVER_ERROR,
            "unavailable", Status.UNAVAILABLE.asRuntimeException());
        when(grpcTransport.queryMcpServer("mcp", null)).thenThrow(unavailable);
        when(httpTransport.queryMcpServer("mcp", null)).thenReturn(expected);
        
        assertSame(expected, router.queryMcpServer("mcp", null));
    }
    
    @Test
    void autoNeverFallsBackForBusinessFailureOrRelease() throws Exception {
        when(sharedGrpcTransport.getMode()).thenReturn(AgentTransportMode.AUTO);
        when(sharedGrpcTransport.isMcpAvailable()).thenReturn(true);
        when(sharedGrpcTransport.isConnected()).thenReturn(true);
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
}
