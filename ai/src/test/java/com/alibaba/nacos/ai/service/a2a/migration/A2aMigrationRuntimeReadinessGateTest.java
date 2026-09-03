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

package com.alibaba.nacos.ai.service.a2a.migration;

import com.alibaba.nacos.ai.constant.Constants;
import com.alibaba.nacos.ai.service.a2a.identity.AgentIdCodecHolder;
import com.alibaba.nacos.ai.service.agent.identity.RadServiceNameComposer;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.naming.pojo.ServiceInfo;
import com.alibaba.nacos.naming.core.v2.ServiceManager;
import com.alibaba.nacos.naming.core.v2.index.ServiceStorage;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class A2aMigrationRuntimeReadinessGateTest {
    
    @Mock
    private AgentIdCodecHolder codec;
    
    @Mock
    private A2aRuntimeSnapshotComparator comparator;
    
    @Mock
    private A2aMigrationEndpointRouter endpointRouter;
    
    private Service historical;
    
    private Service canonical;
    
    private Instance historicalInstance;
    
    private Instance canonicalInstance;
    
    @BeforeEach
    void setUp() {
        historical = Service.newService("public", Constants.Agent.AGENT_ENDPOINT_GROUP,
            "encoded::1.0.0");
        canonical = Service.newService("public", Constants.Agent.AGENT_ENDPOINT_GROUP,
            RadServiceNameComposer.compose("agent", "a2a"));
        historicalInstance = new Instance();
        canonicalInstance = new Instance();
    }
    
    @Test
    void shouldFailClosedForRetryOrUnavailableNamingViews() {
        when(endpointRouter.hasPendingRetries()).thenReturn(true);
        assertFalse(gate(Collections.singleton("public"), namespace -> Collections.emptySet(),
            service -> service, service -> Collections.emptyList()).isReady());
        assertFalse(gate(Collections.singleton("public"), namespace -> Collections.emptySet(),
            service -> service, service -> Collections.emptyList()).isLocalMirrorReady());
        when(endpointRouter.hasPendingRetries()).thenReturn(false);
        assertTrue(gate(Collections.emptySet(), namespace -> Collections.emptySet(),
            service -> service, service -> Collections.emptyList()).isLocalMirrorReady());
        assertFalse(gate(null, namespace -> Collections.emptySet(), service -> service,
            service -> Collections.emptyList()).isReady());
        assertFalse(gate(Collections.singleton("public"), namespace -> null,
            service -> service, service -> Collections.emptyList()).isReady());
    }
    
    @Test
    void shouldIgnoreInactiveAndUnrelatedServices() {
        Service unrelated = Service.newService("public", "other", "encoded::1.0.0");
        Set<Service> services = Set.of(unrelated, historical);
        assertTrue(gate(Collections.singleton("public"), namespace -> services,
            service -> service, service -> Collections.emptyList()).isReady());
        assertTrue(gate(Collections.emptySet(), namespace -> Collections.emptySet(),
            service -> service, service -> Collections.emptyList()).isReady());
    }
    
    @Test
    void shouldCompareEveryActiveHistoricalSnapshot() {
        when(codec.decode("encoded")).thenReturn("agent");
        when(codec.encode("agent")).thenReturn("encoded");
        when(comparator.equivalent(Collections.singletonList(historicalInstance),
            Collections.singletonList(canonicalInstance), "1.0.0")).thenReturn(true);
        Function<Service, List<Instance>> loader = service -> service.equals(historical)
            ? Collections.singletonList(historicalInstance)
            : Collections.singletonList(canonicalInstance);
        A2aMigrationRuntimeReadinessGate gate = gate(Collections.singleton("public"),
            namespace -> Collections.singleton(historical), service -> canonical, loader);
        assertTrue(gate.isReady());
        verify(comparator).equivalent(Collections.singletonList(historicalInstance),
            Collections.singletonList(canonicalInstance), "1.0.0");
        when(comparator.equivalent(any(), any(), any())).thenReturn(false);
        assertFalse(gate.isReady());
    }
    
    @Test
    void shouldRejectMalformedIdentityRevisionRaceAndLateRetry() {
        Function<Service, List<Instance>> active =
            service -> Collections.singletonList(historicalInstance);
        assertFalse(gate(Collections.singleton("public"),
            namespace -> Collections.singleton(historical), service -> canonical, active)
            .isReady());
        when(codec.decode("encoded")).thenReturn("agent");
        when(codec.encode("agent")).thenReturn("encoded");
        AtomicBoolean changed = new AtomicBoolean();
        Function<Service, List<Instance>> changing = service -> {
            if (service.equals(historical) && changed.compareAndSet(false, true)) {
                historical.incrementRevision();
            }
            return Collections.singletonList(historicalInstance);
        };
        assertFalse(gate(Collections.singleton("public"),
            namespace -> Collections.singleton(historical), service -> canonical, changing)
            .isReady());
        when(comparator.equivalent(any(), any(), any())).thenReturn(true);
        when(endpointRouter.hasPendingRetries()).thenReturn(false, true);
        assertFalse(gate(Collections.singleton("public"),
            namespace -> Collections.singleton(historical), service -> canonical,
            service -> Collections.singletonList(historicalInstance)).isReady());
        
        when(endpointRouter.hasPendingRetries()).thenReturn(false);
        Service truncated = Service.newService("public", Constants.Agent.AGENT_ENDPOINT_GROUP,
            "encoded::");
        assertFalse(gate(Collections.singleton("public"),
            namespace -> Collections.singleton(truncated), service -> canonical, active)
            .isReady());
        when(codec.decode("encoded")).thenReturn("agent");
        when(codec.encode("agent")).thenReturn("different");
        assertFalse(gate(Collections.singleton("public"),
            namespace -> Collections.singleton(historical), service -> canonical, active)
            .isReady());
    }
    
    @Test
    void publicAdapterShouldPreserveServiceInfoHosts() {
        ServiceStorage serviceStorage = mock(ServiceStorage.class);
        A2aMigrationRuntimeReadinessGate gate = new A2aMigrationRuntimeReadinessGate(codec,
            comparator, endpointRouter, serviceStorage);
        assertTrue(A2aMigrationRuntimeReadinessGate.instances(null).isEmpty());
        ServiceInfo empty = new ServiceInfo();
        assertTrue(A2aMigrationRuntimeReadinessGate.instances(empty).isEmpty());
        ServiceInfo populated = new ServiceInfo();
        populated.setHosts(Collections.singletonList(historicalInstance));
        assertEquals(Collections.singletonList(historicalInstance),
            A2aMigrationRuntimeReadinessGate.instances(populated));
        Service managed = Service.newService("runtime-readiness-test",
            Constants.Agent.AGENT_ENDPOINT_GROUP, "managed");
        ServiceManager.getInstance().getSingleton(managed);
        try {
            when(serviceStorage.getPushData(managed)).thenReturn(populated);
            assertEquals(managed, singletonResolver(gate).apply(managed));
            assertEquals(Collections.singletonList(historicalInstance),
                instanceLoader(gate).apply(managed));
        } finally {
            ServiceManager.getInstance().removeSingleton(managed);
        }
    }
    
    private A2aMigrationRuntimeReadinessGate gate(Set<String> namespaces,
        Function<String, Set<Service>> services, Function<Service, Service> singleton,
        Function<Service, List<Instance>> loader) {
        return new A2aMigrationRuntimeReadinessGate(codec, comparator, endpointRouter,
            () -> namespaces, services, singleton, loader);
    }
    
    @SuppressWarnings("unchecked")
    private Function<Service, Service> singletonResolver(A2aMigrationRuntimeReadinessGate gate) {
        return (Function<Service, Service>) ReflectionTestUtils.getField(gate,
            "singletonResolver");
    }
    
    @SuppressWarnings("unchecked")
    private Function<Service, List<Instance>> instanceLoader(
        A2aMigrationRuntimeReadinessGate gate) {
        return (Function<Service, List<Instance>>) ReflectionTestUtils.getField(gate,
            "instanceLoader");
    }
}
