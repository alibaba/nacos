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

package com.alibaba.nacos.ai.service.a2a;

import com.alibaba.nacos.ai.constant.Constants;
import com.alibaba.nacos.ai.remote.manager.AiConnectionBasedClientManager;
import com.alibaba.nacos.api.ai.model.a2a.AgentEndpoint;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.exception.runtime.NacosRuntimeException;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.remote.RemoteConstants;
import com.alibaba.nacos.core.remote.Connection;
import com.alibaba.nacos.core.remote.ConnectionMeta;
import com.alibaba.nacos.naming.core.v2.client.ClientAttributes;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import com.alibaba.nacos.naming.core.v2.service.impl.EphemeralClientOperationServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CanonicalA2aEndpointOperationServiceTest {
    
    private static final String PARENT_CLIENT_ID = "parent-client";
    
    @Mock
    private AiConnectionBasedClientManager clientManager;
    
    @Mock
    private EphemeralClientOperationServiceImpl clientOperationService;
    
    @Mock
    private Connection connection;
    
    @Mock
    private ConnectionMeta connectionMeta;
    
    private CanonicalA2aEndpointOperationService service;
    
    @BeforeEach
    void setUp() {
        service =
            new CanonicalA2aEndpointOperationService(clientManager, clientOperationService);
    }
    
    @Test
    void shouldRegisterIndependentExactVersionPublishers() throws NacosException {
        AtomicBoolean childExists = connectedClientState();
        AgentEndpoint first = endpoint("1.0.0", "2001:db8::1", 8080, "rpc", true);
        first.setQuery("tenant=nacos");
        first.setProtocolVersion("0.3");
        first.setTenant("tenant-a");
        AgentEndpoint second = endpoint("2.0.0", "127.0.0.2", 8081, "/rpc", false);
        
        service.register(PARENT_CLIENT_ID, "public", "demo-agent",
            Collections.singletonList(first));
        childExists.set(false);
        service.register(PARENT_CLIENT_ID, "public", "demo-agent",
            Collections.singletonList(second));
        
        ArgumentCaptor<Service> serviceCaptor = ArgumentCaptor.forClass(Service.class);
        ArgumentCaptor<List<Instance>> instancesCaptor = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<String> clientCaptor = ArgumentCaptor.forClass(String.class);
        verify(clientOperationService, times(2)).batchRegisterInstance(serviceCaptor.capture(),
            instancesCaptor.capture(), clientCaptor.capture());
        assertEquals("agent-endpoints", serviceCaptor.getAllValues().get(0).getGroup());
        assertEquals("rad-demo-agent-a2a", serviceCaptor.getAllValues().get(0).getName());
        assertNotEquals(clientCaptor.getAllValues().get(0), clientCaptor.getAllValues().get(1));
        assertTrue(clientCaptor.getAllValues().get(0)
            .startsWith(CanonicalA2aEndpointOperationService.CHILD_CLIENT_ID_PREFIX));
        Instance firstInstance = instancesCaptor.getAllValues().get(0).get(0);
        assertEquals("2001:db8::1", firstInstance.getIp());
        assertEquals("https", firstInstance.getMetadata().get(
            Constants.Agent.AGENT_ENDPOINT_PROTOCOL_KEY));
        assertEquals("/rpc", firstInstance.getMetadata().get(
            Constants.Agent.AGENT_ENDPOINT_PATH_KEY));
        assertEquals("tenant=nacos", firstInstance.getMetadata().get(
            Constants.Agent.AGENT_ENDPOINT_QUERY_KEY));
        assertEquals("0.3", firstInstance.getMetadata().get(
            Constants.Agent.AGENT_ENDPOINT_PROTOCOL_VERSION_KEY));
        assertEquals("tenant-a", firstInstance.getMetadata().get(
            Constants.Agent.AGENT_ENDPOINT_TENANT_KEY));
        assertEquals("[1.0.0]", firstInstance.getMetadata().get(
            Constants.Agent.AGENT_ENDPOINT_VERSION_RANGE_KEY));
    }
    
    @Test
    void shouldReuseChildPublisherAndDeregisterIt() throws NacosException {
        AtomicBoolean childExists = connectedClientState();
        AgentEndpoint endpoint = endpoint("1.0.0", "127.0.0.1", 8080, "/rpc", false);
        service.register(PARENT_CLIENT_ID, "public", "demo-agent",
            Collections.singletonList(endpoint));
        childExists.set(true);
        service.register(PARENT_CLIENT_ID, "public", "demo-agent",
            Collections.singletonList(endpoint));
        
        ArgumentCaptor<String> childCaptor = ArgumentCaptor.forClass(String.class);
        verify(clientManager).clientConnected(childCaptor.capture(), any(ClientAttributes.class));
        verify(clientOperationService, times(2)).batchRegisterInstance(any(Service.class),
            anyList(), eq(childCaptor.getValue()));
        
        service.deregister(PARENT_CLIENT_ID, "public", "demo-agent", "1.0.0");
        verify(clientOperationService).deregisterInstance(any(Service.class), any(Instance.class),
            eq(childCaptor.getValue()));
        verify(clientManager).clientDisconnected(childCaptor.getValue());
        childExists.set(false);
        service.deregister(PARENT_CLIENT_ID, "public", "demo-agent", "1.0.0");
        verify(clientOperationService).deregisterInstance(any(Service.class), any(Instance.class),
            eq(childCaptor.getValue()));
    }
    
    @Test
    void shouldValidateBatchAndParentConnection() {
        assertThrows(NacosApiException.class,
            () -> service.register(PARENT_CLIENT_ID, "public", "demo-agent", null));
        assertThrows(NacosApiException.class,
            () -> service.register(PARENT_CLIENT_ID, "public", "demo-agent",
                Collections.emptyList()));
        List<AgentEndpoint> oversized = new ArrayList<AgentEndpoint>(1001);
        for (int i = 0; i < 1001; i++) {
            oversized.add(endpoint("1.0.0", "127.0.0.1", 8080, "/rpc", false));
        }
        NacosException overThreshold = assertThrows(NacosException.class,
            () -> service.register(PARENT_CLIENT_ID, "public", "demo-agent", oversized));
        assertEquals(NacosException.OVER_THRESHOLD, overThreshold.getErrCode());
        assertThrows(NacosApiException.class,
            () -> service.register(PARENT_CLIENT_ID, "public", "demo-agent",
                Arrays.asList(endpoint("1.0.0", "127.0.0.1", 8080, "/rpc", false),
                    endpoint("2.0.0", "127.0.0.2", 8081, "/rpc", false))));
        assertThrows(NacosApiException.class,
            () -> service.register(PARENT_CLIENT_ID, "public", "demo-agent",
                Collections.singletonList(null)));
        AgentEndpoint invalid = endpoint("1.0.0", null, 8080, "/rpc", false);
        assertThrows(NacosApiException.class,
            () -> service.register(PARENT_CLIENT_ID, "public", "demo-agent",
                Collections.singletonList(invalid)));
        
        when(clientManager.contains(anyString())).thenReturn(false);
        assertThrows(NacosRuntimeException.class,
            () -> service.register(PARENT_CLIENT_ID, "public", "demo-agent",
                Collections.singletonList(
                    endpoint("1.0.0", "127.0.0.1", 8080, "/rpc", false))));
    }
    
    @Test
    void shouldCleanNewChildWhenRegistrationFails() {
        connectedClientState();
        doThrow(new IllegalStateException("failed")).when(clientOperationService)
            .batchRegisterInstance(any(Service.class), anyList(), anyString());
        
        assertThrows(IllegalStateException.class,
            () -> service.register(PARENT_CLIENT_ID, "public", "demo-agent",
                Collections.singletonList(
                    endpoint("1.0.0", "127.0.0.1", 8080, "/rpc", false))));
        
        verify(clientManager).clientDisconnected(anyString());
    }
    
    @Test
    void shouldKeepExistingChildWhenReplacementFails() {
        when(clientManager.contains(anyString())).thenReturn(true);
        doThrow(new IllegalStateException("failed")).when(clientOperationService)
            .batchRegisterInstance(any(Service.class), anyList(), anyString());
        
        assertThrows(IllegalStateException.class,
            () -> service.register(PARENT_CLIENT_ID, "public", "demo-agent",
                Collections.singletonList(
                    endpoint("1.0.0", "127.0.0.1", 8080, "/rpc", false))));
        
        verify(clientManager, never()).clientConnected(anyString(), any(ClientAttributes.class));
        verify(clientManager, never()).clientDisconnected(anyString());
    }
    
    @Test
    void shouldCleanChildWhenParentDisconnectsDuringCreation() {
        AtomicBoolean firstParentCheck = new AtomicBoolean(true);
        when(clientManager.contains(anyString())).thenAnswer(invocation -> {
            String clientId = invocation.getArgument(0);
            return PARENT_CLIENT_ID.equals(clientId) && firstParentCheck.getAndSet(false);
        });
        
        assertThrows(NacosRuntimeException.class,
            () -> service.register(PARENT_CLIENT_ID, "public", "demo-agent",
                Collections.singletonList(
                    endpoint("1.0.0", "127.0.0.1", 8080, "/rpc", false))));
        
        verify(clientManager).clientConnected(anyString(), any(ClientAttributes.class));
        verify(clientManager).clientDisconnected(anyString());
        verify(clientOperationService, never()).batchRegisterInstance(any(Service.class),
            anyList(), anyString());
    }
    
    @Test
    void shouldReleaseChildrenOnlyForDisconnectedAiConnections() throws NacosException {
        connectedClientState();
        service.clientConnected(connection);
        when(connection.getMetaInfo()).thenReturn(connectionMeta);
        when(connectionMeta.getConnectionId()).thenReturn(PARENT_CLIENT_ID);
        when(connectionMeta.getLabel(RemoteConstants.LABEL_MODULE)).thenReturn("naming");
        service.clientDisConnected(connection);
        verify(clientManager, never()).clientDisconnected(anyString());
        
        when(connectionMeta.getLabel(RemoteConstants.LABEL_MODULE))
            .thenReturn(RemoteConstants.LABEL_MODULE_AI);
        service.clientDisConnected(connection);
        verify(clientManager, never()).clientDisconnected(anyString());
        
        service.register(PARENT_CLIENT_ID, "public", "demo-agent",
            Collections.singletonList(
                endpoint("1.0.0", "127.0.0.1", 8080, "/rpc", false)));
        service.clientDisConnected(connection);
        verify(clientManager).clientDisconnected(anyString());
        service.clientDisConnected(connection);
        verify(clientManager).clientDisconnected(anyString());
    }
    
    private AtomicBoolean connectedClientState() {
        AtomicBoolean childExists = new AtomicBoolean(false);
        when(clientManager.contains(anyString())).thenAnswer(invocation -> {
            String clientId = invocation.getArgument(0);
            return PARENT_CLIENT_ID.equals(clientId) || childExists.get();
        });
        doAnswer(invocation -> {
            childExists.set(true);
            return true;
        }).when(clientManager).clientConnected(anyString(), any(ClientAttributes.class));
        return childExists;
    }
    
    private AgentEndpoint endpoint(String version, String address, int port, String path,
        boolean tls) {
        AgentEndpoint result = new AgentEndpoint();
        result.setVersion(version);
        result.setAddress(address);
        result.setPort(port);
        result.setPath(path);
        result.setSupportTls(tls);
        result.setTransport("HTTP+JSON");
        return result;
    }
}
