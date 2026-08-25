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

package com.alibaba.nacos.ai.service.agent.runtime;

import com.alibaba.nacos.ai.constant.Constants;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.naming.core.v2.client.Client;
import com.alibaba.nacos.naming.core.v2.client.manager.ClientManager;
import com.alibaba.nacos.naming.core.v2.client.manager.ClientManagerDelegate;
import com.alibaba.nacos.naming.core.v2.pojo.BatchInstancePublishInfo;
import com.alibaba.nacos.naming.core.v2.pojo.InstancePublishInfo;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.mock.env.MockEnvironment;

import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentRuntimePublicationCapacityGateTest {
    
    @Mock
    private ClientManager clientManager;
    
    @Mock
    private Client client;
    
    @AfterEach
    void tearDown() {
        EnvUtil.setEnvironment(new MockEnvironment());
    }
    
    @Test
    void testDefaultConstructorReadsConfiguredLimit() throws NacosApiException {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty(Constants.Agent.MAX_PUBLICATIONS_PER_CLIENT_CONFIG_KEY, "1");
        EnvUtil.setEnvironment(environment);
        ClientManagerDelegate configuredManager = mock(ClientManagerDelegate.class);
        when(configuredManager.getClient("client-1")).thenReturn(client);
        Service old = agent("old");
        Service target = agent("new");
        when(client.getAllPublishedService()).thenReturn(Collections.singletonList(old));
        when(client.getInstancePublishInfo(old)).thenReturn(singlePublication());
        when(client.getInstancePublishInfo(target)).thenReturn(null);
        AtomicBoolean invoked = new AtomicBoolean();
        AgentRuntimePublicationCapacityGate gate =
            new AgentRuntimePublicationCapacityGate(configuredManager);
        NacosApiException exception = assertThrows(NacosApiException.class,
            () -> gate.register("client-1", target, 1, () -> invoked.set(true)));
        assertEquals(ErrorCode.AGENT_ENDPOINT_PUBLICATION_OVER_LIMIT.getCode(),
            exception.getDetailErrCode());
    }
    
    @Test
    void testSpringConstructorInjection() {
        ClientManagerDelegate configuredManager = mock(ClientManagerDelegate.class);
        try (AnnotationConfigApplicationContext context =
            new AnnotationConfigApplicationContext()) {
            context.getBeanFactory().registerSingleton("clientManagerDelegateMock",
                configuredManager);
            context.register(AgentRuntimePublicationCapacityGate.class);
            context.refresh();
            assertTrue(context.getBean(
                AgentRuntimePublicationCapacityGate.class) instanceof AgentRuntimePublicationCapacityGate);
        }
    }
    
    @Test
    void testMissingClientDelegatesToNamingValidation() throws NacosApiException {
        AgentRuntimePublicationCapacityGate gate = gate(1);
        AtomicBoolean invoked = new AtomicBoolean();
        gate.register("missing", agent("new"), 1, () -> invoked.set(true));
        assertTrue(invoked.get());
    }
    
    @Test
    void testWholeBatchMayCrossLimitFromBelowAndNonAgentServicesDoNotConsumeQuota()
        throws NacosApiException {
        when(clientManager.getClient("client-1")).thenReturn(client);
        Service old = agent("old");
        Service target = agent("new");
        Service ordinary = Service.newService("public", "DEFAULT_GROUP", "ordinary");
        when(client.getAllPublishedService()).thenReturn(Arrays.asList(old, ordinary));
        when(client.getInstancePublishInfo(old)).thenReturn(singlePublication());
        when(client.getInstancePublishInfo(target)).thenReturn(null);
        AgentRuntimePublicationCapacityGate gate = gate(2);
        AtomicBoolean invoked = new AtomicBoolean();
        gate.register("client-1", target, 3, () -> invoked.set(true));
        assertTrue(invoked.get());
    }
    
    @Test
    void testReplacementAllowedAtLimit() throws NacosApiException {
        Service replacement = agent("old");
        when(clientManager.getClient("client-1")).thenReturn(client);
        when(client.getAllPublishedService()).thenReturn(Collections.singletonList(replacement));
        when(client.getInstancePublishInfo(replacement)).thenReturn(singlePublication());
        AtomicBoolean invoked = new AtomicBoolean();
        gate(1).register("client-1", replacement, 1, () -> invoked.set(true));
        assertTrue(invoked.get());
    }
    
    @Test
    void testEmptyBatchPublicationConsumesNoCapacity() throws NacosApiException {
        Service empty = agent("empty");
        Service target = agent("new");
        when(clientManager.getClient("client-1")).thenReturn(client);
        when(client.getAllPublishedService()).thenReturn(Collections.singletonList(empty));
        when(client.getInstancePublishInfo(empty)).thenReturn(new BatchInstancePublishInfo());
        when(client.getInstancePublishInfo(target)).thenReturn(null);
        AtomicBoolean invoked = new AtomicBoolean();
        
        gate(1).register("client-1", target, 1, () -> invoked.set(true));
        
        assertTrue(invoked.get());
    }
    
    @Test
    void testAtCapacityReplacementMayStayEqualOrShrinkButCannotGrow()
        throws NacosApiException {
        Service replacement = agent("old");
        when(clientManager.getClient("client-1")).thenReturn(client);
        when(client.getAllPublishedService()).thenReturn(Collections.singletonList(replacement));
        when(client.getInstancePublishInfo(replacement)).thenReturn(batchPublication(3));
        AgentRuntimePublicationCapacityGate gate = gate(2);
        AtomicBoolean invoked = new AtomicBoolean();
        
        gate.register("client-1", replacement, 3, () -> invoked.set(true));
        gate.register("client-1", replacement, 2, () -> invoked.set(true));
        invoked.set(false);
        NacosApiException exception = assertThrows(NacosApiException.class,
            () -> gate.register("client-1", replacement, 4, () -> invoked.set(true)));
        
        assertEquals(ErrorCode.AGENT_ENDPOINT_PUBLICATION_OVER_LIMIT.getCode(),
            exception.getDetailErrCode());
        assertFalse(invoked.get());
    }
    
    @Test
    void testNewPublicationRejectedAtLimitWithoutMutation() {
        when(clientManager.getClient("client-1")).thenReturn(client);
        Service old = agent("old");
        Service target = agent("new");
        when(client.getAllPublishedService()).thenReturn(Collections.singletonList(old));
        when(client.getInstancePublishInfo(old)).thenReturn(singlePublication());
        when(client.getInstancePublishInfo(target)).thenReturn(null);
        AtomicBoolean invoked = new AtomicBoolean();
        NacosApiException exception = assertThrows(NacosApiException.class,
            () -> gate(1).register("client-1", target, 1, () -> invoked.set(true)));
        assertEquals(NacosException.OVER_THRESHOLD, exception.getErrCode());
        assertEquals(ErrorCode.AGENT_ENDPOINT_PUBLICATION_OVER_LIMIT.getCode(),
            exception.getDetailErrCode());
        assertFalse(invoked.get());
    }
    
    @Test
    void testRejectInvalidConfiguredLimit() {
        assertThrows(IllegalArgumentException.class,
            () -> new AgentRuntimePublicationCapacityGate(clientManager, 0));
    }
    
    private AgentRuntimePublicationCapacityGate gate(int limit) {
        return new AgentRuntimePublicationCapacityGate(clientManager, limit);
    }
    
    private Service agent(String name) {
        return Service.newService("public", Constants.Agent.AGENT_ENDPOINT_GROUP, name);
    }
    
    private InstancePublishInfo singlePublication() {
        return mock(InstancePublishInfo.class);
    }
    
    private BatchInstancePublishInfo batchPublication(int count) {
        BatchInstancePublishInfo result = new BatchInstancePublishInfo();
        result.setInstancePublishInfos(Collections.nCopies(count, singlePublication()));
        return result;
    }
    
}
