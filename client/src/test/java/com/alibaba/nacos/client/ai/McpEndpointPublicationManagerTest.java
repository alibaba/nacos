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

package com.alibaba.nacos.client.ai;

import com.alibaba.nacos.api.ai.model.agent.ClientLivenessInfo;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.client.ai.remote.AgentTransportType;
import com.alibaba.nacos.client.ai.remote.McpTransportRouter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class McpEndpointPublicationManagerTest {
    
    @Mock
    private McpTransportRouter transportRouter;
    
    @Mock
    private AiHttpPublicationCoordinator coordinator;
    
    private McpEndpointPublicationManager manager;
    
    @BeforeEach
    void setUp() {
        manager = new McpEndpointPublicationManager(transportRouter, coordinator);
        verify(coordinator).register(manager);
    }
    
    @Test
    void successfulHttpPublicationUsesStickyOwnerUntilDeregistered() throws Exception {
        ClientLivenessInfo liveness = new ClientLivenessInfo();
        when(transportRouter.selectPublicationTransport()).thenReturn(AgentTransportType.HTTP);
        when(transportRouter.registerMcpServerEndpoint("mcp", "127.0.0.1", 8080, "1.0.0",
            AgentTransportType.HTTP)).thenReturn(liveness);
        
        manager.register("mcp", "127.0.0.1", 8080, "1.0.0");
        assertTrue(manager.hasHttpPublication());
        assertTrue(manager.hasRegisteredHttpPublication());
        verify(coordinator).stateChanged(manager, liveness, true);
        
        manager.deregister("mcp", "127.0.0.1", 8080);
        verify(transportRouter).deregisterMcpServerEndpoint("mcp", "127.0.0.1", 8080,
            AgentTransportType.HTTP);
        assertFalse(manager.hasHttpPublication());
    }
    
    @Test
    void retryableRegisterFailureRetainsIntentForRedo() throws Exception {
        ClientLivenessInfo liveness = new ClientLivenessInfo();
        when(transportRouter.selectPublicationTransport()).thenReturn(AgentTransportType.HTTP);
        when(transportRouter.registerMcpServerEndpoint("mcp", "127.0.0.1", 8080, null,
            AgentTransportType.HTTP))
            .thenThrow(new NacosException(NacosException.SERVER_ERROR, "down"))
            .thenReturn(liveness);
        
        assertThrows(NacosException.class,
            () -> manager.register("mcp", "127.0.0.1", 8080, null));
        assertTrue(manager.hasHttpPublication());
        assertFalse(manager.hasRegisteredHttpPublication());
        manager.redoDirtyHttpPublications();
        assertTrue(manager.hasRegisteredHttpPublication());
        verify(transportRouter, times(2)).registerMcpServerEndpoint("mcp", "127.0.0.1", 8080,
            null, AgentTransportType.HTTP);
        verify(coordinator).stateChanged(manager, liveness, true);
    }
    
    @Test
    void nonRetryableRegisterFailureRollsBackIntent() throws Exception {
        when(transportRouter.selectPublicationTransport()).thenReturn(AgentTransportType.HTTP);
        when(transportRouter.registerMcpServerEndpoint("mcp", "127.0.0.1", 8080, null,
            AgentTransportType.HTTP))
            .thenThrow(new NacosException(NacosException.INVALID_PARAM, "invalid"));
        
        assertThrows(NacosException.class,
            () -> manager.register("mcp", "127.0.0.1", 8080, null));
        assertFalse(manager.hasHttpPublication());
        manager.redoDirtyHttpPublications();
        verify(transportRouter).registerMcpServerEndpoint("mcp", "127.0.0.1", 8080, null,
            AgentTransportType.HTTP);
    }
    
    @Test
    void retryableDeregisterFailureKeepsTombstoneUntilRedo() throws Exception {
        when(transportRouter.selectPublicationTransport()).thenReturn(AgentTransportType.HTTP);
        when(transportRouter.registerMcpServerEndpoint("mcp", "127.0.0.1", 8080, null,
            AgentTransportType.HTTP)).thenReturn(new ClientLivenessInfo());
        manager.register("mcp", "127.0.0.1", 8080, null);
        doThrow(new NacosException(NacosException.HTTP_CLIENT_ERROR_CODE, "down"))
            .doNothing().when(transportRouter)
            .deregisterMcpServerEndpoint("mcp", "127.0.0.1", 8080,
                AgentTransportType.HTTP);
        
        assertThrows(NacosException.class,
            () -> manager.deregister("mcp", "127.0.0.1", 8080));
        assertTrue(manager.hasHttpPublication());
        assertFalse(manager.hasRegisteredHttpPublication());
        manager.redoDirtyHttpPublications();
        assertFalse(manager.hasHttpPublication());
        verify(transportRouter, times(2)).deregisterMcpServerEndpoint("mcp", "127.0.0.1", 8080,
            AgentTransportType.HTTP);
    }
    
    @Test
    void sharedClientRecoveryMarksAndReplaysRegisteredEndpoint() throws Exception {
        ClientLivenessInfo liveness = new ClientLivenessInfo();
        when(transportRouter.selectPublicationTransport()).thenReturn(AgentTransportType.HTTP);
        when(transportRouter.registerMcpServerEndpoint("mcp", "127.0.0.1", 8080, null,
            AgentTransportType.HTTP)).thenReturn(liveness);
        manager.register("mcp", "127.0.0.1", 8080, null);
        
        manager.markHttpPublicationsDirty();
        assertFalse(manager.hasRegisteredHttpPublication());
        manager.redoDirtyHttpPublications();
        
        assertTrue(manager.hasRegisteredHttpPublication());
        verify(transportRouter, times(2)).registerMcpServerEndpoint("mcp", "127.0.0.1", 8080,
            null, AgentTransportType.HTTP);
    }
    
    @Test
    void grpcPublicationNeverParticipatesInHttpHeartbeat() throws Exception {
        when(transportRouter.selectPublicationTransport()).thenReturn(AgentTransportType.GRPC);
        manager.register("mcp", "127.0.0.1", 8080, null);
        assertFalse(manager.hasHttpPublication());
        assertFalse(manager.hasRegisteredHttpPublication());
        manager.markHttpPublicationsDirty();
        manager.redoDirtyHttpPublications();
        verify(transportRouter, never()).heartbeatMcpServerEndpoints();
    }
}
