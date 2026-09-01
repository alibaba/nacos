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
import com.alibaba.nacos.ai.service.a2a.A2aEndpointChildPublisherManager.ChildPublisher;
import com.alibaba.nacos.ai.service.a2a.identity.AgentIdCodecHolder;
import com.alibaba.nacos.api.ai.model.a2a.AgentEndpoint;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import com.alibaba.nacos.naming.core.v2.service.impl.EphemeralClientOperationServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LegacyA2aEndpointOperationServiceTest {
    
    private static final String PARENT = "parent";
    
    private static final String CHILD = "child";
    
    @Mock
    private EphemeralClientOperationServiceImpl clientOperationService;
    
    @Mock
    private AgentIdCodecHolder codecHolder;
    
    @Mock
    private A2aEndpointChildPublisherManager childManager;
    
    @Mock
    private ChildPublisher childPublisher;
    
    private LegacyA2aEndpointOperationService service;
    
    @BeforeEach
    void setUp() {
        service = new LegacyA2aEndpointOperationService(clientOperationService, codecHolder,
            childManager);
    }
    
    @Test
    void shouldValidateAndRegisterSingleHistoricalEndpoint() throws NacosException {
        AgentEndpoint endpoint = endpoint("1.0.0", "127.0.0.1", 8080);
        when(codecHolder.encode("demo")).thenReturn("encoded");
        
        service.validate(endpoint);
        service.register(PARENT, "public", "demo", endpoint, "10.0.0.1");
        
        ArgumentCaptor<Service> serviceCaptor = ArgumentCaptor.forClass(Service.class);
        ArgumentCaptor<Instance> instanceCaptor = ArgumentCaptor.forClass(Instance.class);
        verify(clientOperationService).registerInstance(serviceCaptor.capture(),
            instanceCaptor.capture(), eq(PARENT));
        assertEquals(Constants.Agent.AGENT_ENDPOINT_GROUP,
            serviceCaptor.getValue().getGroup());
        assertEquals("encoded::1.0.0", serviceCaptor.getValue().getName());
        assertEquals("127.0.0.1", instanceCaptor.getValue().getIp());
        assertEquals(8080, instanceCaptor.getValue().getPort());
    }
    
    @Test
    void shouldValidateAndReplaceHistoricalBatch() throws NacosException {
        List<AgentEndpoint> endpoints = Arrays.asList(
            endpoint("1.0.0", "127.0.0.1", 8080),
            endpoint("1.0.0", "127.0.0.2", 8081));
        when(codecHolder.encode("demo")).thenReturn("encoded");
        
        service.validate(endpoints);
        service.register(PARENT, "public", "demo", endpoints, "10.0.0.1");
        
        ArgumentCaptor<List<Instance>> instances = ArgumentCaptor.forClass(List.class);
        verify(clientOperationService).batchRegisterInstance(any(Service.class),
            instances.capture(), eq(PARENT));
        assertEquals(2, instances.getValue().size());
        assertEquals("127.0.0.2", instances.getValue().get(1).getIp());
    }
    
    @Test
    void shouldDeregisterHistoricalEndpoint() throws NacosException {
        AgentEndpoint endpoint = endpoint("1.0.0", "127.0.0.1", 8080);
        when(codecHolder.encode("demo")).thenReturn("encoded");
        
        service.deregister(PARENT, "public", "demo", endpoint, "10.0.0.1");
        
        verify(clientOperationService).deregisterInstance(any(Service.class),
            any(Instance.class), eq(PARENT));
    }
    
    @Test
    void shouldUseAndCleanMigrationChildForSingleAndBatch() throws NacosException {
        AgentEndpoint endpoint = endpoint("1.0.0", "127.0.0.1", 8080);
        List<AgentEndpoint> endpoints = Collections.singletonList(endpoint);
        prepareChild(true);
        when(codecHolder.encode("demo")).thenReturn("encoded");
        
        service.registerChild(PARENT, "public", "demo", endpoint, "10.0.0.1");
        service.registerChild(PARENT, "public", "demo", endpoints, "10.0.0.1");
        
        verify(clientOperationService).registerInstance(any(Service.class), any(Instance.class),
            eq(CHILD));
        verify(clientOperationService).batchRegisterInstance(any(Service.class), anyList(),
            eq(CHILD));
        
        when(childManager.findChild(PARENT, "public", "demo", "1.0.0",
            LegacyA2aEndpointOperationService.CHILD_LAYOUT)).thenReturn(CHILD, null);
        service.deregisterChild(PARENT, "public", "demo", endpoint, "10.0.0.1");
        service.deregisterChild(PARENT, "public", "demo", endpoint, "10.0.0.1");
        verify(clientOperationService).deregisterInstance(any(Service.class),
            any(Instance.class), eq(CHILD));
        verify(childManager).disconnectChild(PARENT, CHILD);
    }
    
    @Test
    void shouldCleanOnlyNewChildAfterPhysicalFailure() throws NacosException {
        AgentEndpoint endpoint = endpoint("1.0.0", "127.0.0.1", 8080);
        prepareChild(true);
        when(codecHolder.encode("demo")).thenReturn("encoded");
        doThrow(new NacosException(NacosException.SERVER_ERROR, "failed"))
            .when(clientOperationService).registerInstance(any(Service.class),
                any(Instance.class), eq(CHILD));
        
        assertThrows(NacosException.class,
            () -> service.registerChild(PARENT, "public", "demo", endpoint, "source"));
        verify(childManager).disconnectChild(PARENT, CHILD);
        
        prepareChild(false);
        doThrow(new IllegalStateException("failed"))
            .when(clientOperationService).batchRegisterInstance(any(Service.class), anyList(),
                eq(CHILD));
        assertThrows(IllegalStateException.class,
            () -> service.registerChild(PARENT, "public", "demo",
                Collections.singletonList(endpoint), "source"));
        verify(childManager, times(1)).disconnectChild(PARENT, CHILD);
    }
    
    @Test
    void shouldRejectInvalidEndpointMapping() throws NacosException {
        AgentEndpoint endpoint = endpoint("1.0.0", null, 8080);
        assertThrows(NacosException.class, () -> service.validate(endpoint));
        assertThrows(NacosException.class,
            () -> service.validate(Collections.singletonList(endpoint)));
        verify(clientOperationService, never()).registerInstance(any(Service.class),
            any(Instance.class), any(String.class));
    }
    
    private void prepareChild(boolean created) {
        when(childManager.ensureChild(PARENT, "public", "demo", "1.0.0",
            LegacyA2aEndpointOperationService.CHILD_LAYOUT)).thenReturn(childPublisher);
        when(childPublisher.getClientId()).thenReturn(CHILD);
        lenient().when(childPublisher.isCreated()).thenReturn(created);
    }
    
    private AgentEndpoint endpoint(String version, String address, int port) {
        AgentEndpoint result = new AgentEndpoint();
        result.setVersion(version);
        result.setAddress(address);
        result.setPort(port);
        result.setPath("/rpc");
        result.setTransport("HTTP+JSON");
        return result;
    }
}
