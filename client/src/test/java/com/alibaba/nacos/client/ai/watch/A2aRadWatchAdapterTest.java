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

package com.alibaba.nacos.client.ai.watch;

import com.alibaba.nacos.api.ai.listener.AbstractNacosAgentCardListener;
import com.alibaba.nacos.api.ai.listener.AbstractNacosAgentDiscoveryListener;
import com.alibaba.nacos.api.ai.listener.NacosAgentCardEvent;
import com.alibaba.nacos.api.ai.model.a2a.AgentCard;
import com.alibaba.nacos.api.ai.model.a2a.AgentCardDetailInfo;
import com.alibaba.nacos.api.ai.model.agent.AgentCallInterface;
import com.alibaba.nacos.api.ai.model.agent.AgentDiscoveryRequest;
import com.alibaba.nacos.api.ai.model.agent.AgentDiscoveryResult;
import com.alibaba.nacos.api.ai.model.agent.Endpoint;
import com.alibaba.nacos.api.ai.model.agent.EndpointSet;
import com.alibaba.nacos.api.ai.model.agent.EndpointSource;
import com.alibaba.nacos.api.ai.model.agent.client.AgentPublishRequest;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.utils.json.JsonUtils;
import com.alibaba.nacos.client.ai.remote.AgentClientProxy;
import com.alibaba.nacos.client.ai.utils.A2aRadConverter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class A2aRadWatchAdapterTest {
    
    private AgentClientProxy proxy;
    private AgentWatchTransport transport;
    private AgentWatchManager manager;
    private A2aRadWatchAdapter adapter;
    private final List<Runnable> scheduled = new ArrayList<>();
    private final List<Runnable> callbacks = new ArrayList<>();
    private final List<AgentWatchRegistration> registrations = new ArrayList<>();
    
    @BeforeEach
    void setUp() throws Exception {
        proxy = mock(AgentClientProxy.class);
        transport = mock(AgentWatchTransport.class);
        ScheduledExecutorService refresh = mock(ScheduledExecutorService.class);
        ExecutorService delivery = mock(ExecutorService.class);
        when(refresh.schedule(any(Runnable.class), anyLong(), any(TimeUnit.class)))
            .thenAnswer(call -> {
                scheduled.add(call.getArgument(0));
                return mock(ScheduledFuture.class);
            });
        doAnswer(call -> {
            callbacks.add(call.getArgument(0));
            return null;
        })
            .when(delivery).execute(any(Runnable.class));
        doAnswer(call -> {
            registrations.add(call.getArgument(0));
            return null;
        })
            .when(transport)
            .start(any(AgentWatchRegistration.class), any(AgentWatchTransportCallback.class));
        manager = new AgentWatchManager("public", proxy, refresh, delivery, 300, transport,
            (count, key) -> 1L);
        adapter = new A2aRadWatchAdapter("public", manager);
    }
    
    @AfterEach
    void tearDown() {
        adapter.shutdown();
        manager.shutdown();
    }
    
    @Test
    void initialCallbackAndDuplicateSubscribeUseOneWireAndIndependentCopies() throws Exception {
        when(proxy.discoverAgent(any())).thenReturn(snapshot("SERVICE"));
        CardListener listener = new CardListener();
        AgentCardDetailInfo result = adapter.subscribe("demo", null, listener);
        result.setDescription("caller mutation");
        adapter.subscribe("demo", "", listener);
        drain();
        assertEquals(1, listener.cards.size());
        assertEquals("full description", listener.cards.get(0).getDescription());
        listener.cards.get(0).setDescription("listener mutation");
        adapter.subscribe("demo", null, listener);
        drain();
        assertEquals(1, listener.cards.size());
        assertEquals(1, registrations.size());
        assertTrue(listener.cards.get(0).isLatestVersion());
        verify(proxy, times(1)).discoverAgent(any());
    }
    
    @Test
    void latestAndExactHaveDifferentRequestsAndExactFlagIsNull() throws Exception {
        when(proxy.discoverAgent(any())).thenReturn(snapshot("SERVICE"));
        CardListener latest = new CardListener();
        CardListener exact = new CardListener();
        adapter.subscribe("demo", null, latest);
        adapter.subscribe("demo", "1.0.0", exact);
        drain();
        assertTrue(latest.cards.get(0).isLatestVersion());
        assertNull(exact.cards.get(0).isLatestVersion());
        assertEquals(2, registrations.size());
        ArgumentCaptor<AgentDiscoveryRequest> requests =
            ArgumentCaptor.forClass(AgentDiscoveryRequest.class);
        verify(proxy, times(2)).discoverAgent(requests.capture());
        assertEquals("latest", requests.getAllValues().get(0).getReference().getLabel());
        assertEquals("1.0.0", requests.getAllValues().get(1).getReference().getVersion());
        for (AgentDiscoveryRequest request : requests.getAllValues()) {
            assertEquals("public", request.getNamespaceId());
            assertEquals(Collections.singletonList("a2a"), request.getFilter().getProtocols());
            assertNull(request.getFilter().getEndpointSources());
        }
    }
    
    @Test
    void initialMissingTargetRecoversWithoutFabricatingCard() throws Exception {
        when(proxy.discoverAgent(any()))
            .thenThrow(new NacosException(NacosException.NOT_FOUND, "missing"))
            .thenReturn(snapshot("URL"));
        CardListener listener = new CardListener();
        assertNull(adapter.subscribe("demo", null, listener));
        drain();
        assertTrue(listener.cards.isEmpty());
        scheduled.remove(0).run();
        drain();
        assertEquals(1, listener.cards.size());
    }
    
    @Test
    void missingNativeCardKeepsWatchForValidDefinition() throws Exception {
        AgentDiscoveryResult invalid = snapshot("URL");
        invalid.getCallInterfaces().get(0).setNativeDescriptor(Collections.emptyMap());
        when(proxy.discoverAgent(any())).thenReturn(invalid, snapshot("URL"));
        CardListener listener = new CardListener();
        assertNull(adapter.subscribe("demo", null, listener));
        drain();
        assertTrue(listener.cards.isEmpty());
        refresh();
        assertEquals(1, listener.cards.size());
    }
    
    @ParameterizedTest
    @ValueSource(strings = {"tenant", "protocolVersion", "description", "preference"})
    void completeProjectionIncludesFieldsBeyondCardEquality(String change) throws Exception {
        AgentDiscoveryResult first = snapshot("SERVICE");
        Endpoint endpoint = endpoint("https://runtime.example/rpc", "JSONRPC", 0);
        endpoint.setMetadata(new LinkedHashMap<>());
        endpoint.getMetadata().put("zone", "one");
        runtime(first).setEndpoints(Collections.singletonList(endpoint));
        AgentDiscoveryResult second =
            JsonUtils.toObj(JsonUtils.toJson(first), AgentDiscoveryResult.class);
        if ("tenant".equals(change) || "protocolVersion".equals(change)) {
            runtime(second).getEndpoints().get(0).getMetadata()
                .put("__nacos.agent.endpoint." + change + "__",
                    "tenant".equals(change) ? "team" : "1.1");
        } else if ("preference".equals(change)) {
            Collections.reverse(second.getCallInterfaces().get(0).getEndpointSets());
        } else {
            ((java.util.Map<String, Object>) second.getCallInterfaces().get(0)
                .getNativeDescriptor())
                .put("description", "changed description");
        }
        when(proxy.discoverAgent(any())).thenReturn(first, second);
        CardListener listener = new CardListener();
        adapter.subscribe("demo", null, listener);
        drain();
        refresh();
        assertEquals(2, listener.cards.size());
        AgentCardDetailInfo expected = A2aRadConverter.project(
            com.alibaba.nacos.api.ai.utils.AgentDiscoveryCanonicalizer.canonicalizeResult(second),
            A2aRadConverter.discoveryRequest("public", "demo", null), null);
        assertEquals(JsonUtils.toObj(JsonUtils.toJson(expected), java.util.Map.class),
            JsonUtils.toObj(JsonUtils.toJson(listener.cards.get(1)), java.util.Map.class));
    }
    
    @Test
    void preferenceOnlyChangeIsNotLostByLegacyCardEquality() throws Exception {
        AgentDiscoveryResult first = snapshot("URL");
        AgentDiscoveryResult second = snapshot("SERVICE");
        when(proxy.discoverAgent(any())).thenReturn(first, second);
        CardListener listener = new CardListener();
        adapter.subscribe("demo", null, listener);
        drain();
        refresh();
        assertEquals(2, listener.cards.size());
        assertEquals(listener.cards.get(0), listener.cards.get(1),
            "the legacy equals method ignores the compatibility registrationType field");
        assertEquals("URL", listener.cards.get(0).getRegistrationType());
        assertEquals("SERVICE", listener.cards.get(1).getRegistrationType());
    }
    
    @Test
    void runtimeHealthWeightAndRevisionWithoutCardChangeDoNotNotify() throws Exception {
        AgentDiscoveryResult first = snapshot("SERVICE");
        Endpoint endpoint = endpoint("https://runtime.example/rpc", "JSONRPC", 0);
        runtime(first).setEndpoints(Collections.singletonList(endpoint));
        AgentDiscoveryResult second =
            JsonUtils.toObj(JsonUtils.toJson(first), AgentDiscoveryResult.class);
        runtime(second).getEndpoints().get(0).setHealthy(false);
        runtime(second).getEndpoints().get(0).setWeight(2D);
        runtime(second).setSourceRevision(
            "murmur3-x64-128-v1:cccccccccccccccccccccccccccccccc");
        when(proxy.discoverAgent(any())).thenReturn(first, second);
        CardListener listener = new CardListener();
        adapter.subscribe("demo", null, listener);
        drain();
        refresh();
        assertEquals(1, listener.cards.size());
    }
    
    @Test
    void unavailableClearsDedupeAndRecoveryReemitsIdenticalCard() throws Exception {
        when(proxy.discoverAgent(any())).thenReturn(snapshot("URL"))
            .thenThrow(new NacosException(NacosException.NOT_FOUND, "removed"))
            .thenReturn(snapshot("URL"));
        CardListener listener = new CardListener();
        adapter.subscribe("demo", null, listener);
        drain();
        refresh();
        assertEquals(1, listener.cards.size());
        scheduled.remove(0).run();
        drain();
        assertEquals(2, listener.cards.size());
    }
    
    @Test
    void cancellationBeforeCustomExecutorDeliverySuppressesQueuedCard() throws Exception {
        when(proxy.discoverAgent(any())).thenReturn(snapshot("URL"));
        CardListener listener = new CardListener();
        List<Runnable> held = new ArrayList<>();
        listener.executor = held::add;
        adapter.subscribe("demo", null, listener);
        drain();
        assertEquals(1, held.size());
        adapter.unsubscribe("demo", null, listener);
        held.remove(0).run();
        assertTrue(listener.cards.isEmpty());
        verify(transport).stop(registrations.get(0).getClientWatchId());
        adapter.unsubscribe("demo", null, listener);
    }
    
    @Test
    void listenerFailureAndCancellationDoNotAffectOtherOrNativeListeners() throws Exception {
        when(proxy.discoverAgent(any())).thenReturn(snapshot("URL"));
        CardListener broken = new CardListener();
        broken.failure = true;
        CardListener other = new CardListener();
        AgentDiscoveryRequest request = A2aRadConverter.discoveryRequest("public", "demo", null);
        AbstractNacosAgentDiscoveryListener nativeListener =
            mock(AbstractNacosAgentDiscoveryListener.class);
        manager.subscribe(request.getReference(), request.getFilter(), nativeListener);
        adapter.subscribe("demo", null, broken);
        adapter.subscribe("demo", null, other);
        drain();
        assertEquals(1, other.cards.size());
        adapter.unsubscribe("demo", null, broken);
        adapter.shutdown();
        adapter.shutdown();
        verify(transport, never()).stop(any());
        verify(transport, never()).shutdown();
        manager.unsubscribe(request.getReference(), request.getFilter(), nativeListener);
        verify(transport).stop(registrations.get(0).getClientWatchId());
    }
    
    @Test
    void callbackMayCancelItselfAndSubscribeAgain() throws Exception {
        when(proxy.discoverAgent(any())).thenReturn(snapshot("URL"));
        CardListener listener = new CardListener();
        listener.after = () -> {
            try {
                adapter.unsubscribe("demo", null, listener);
            } catch (NacosException e) {
                throw new AssertionError(e);
            }
        };
        adapter.subscribe("demo", null, listener);
        drain();
        listener.after = null;
        adapter.subscribe("demo", null, listener);
        drain();
        assertEquals(2, listener.cards.size());
        assertEquals(2, registrations.size());
    }
    
    @ParameterizedTest
    @ValueSource(ints = {400, 403, 500})
    void rejectedInitialReadPropagatesAndDoesNotRetainListener(int code) throws Exception {
        when(proxy.discoverAgent(any())).thenThrow(new NacosException(code, "rejected"))
            .thenReturn(snapshot("URL"));
        CardListener listener = new CardListener();
        assertEquals(code, assertThrows(NacosException.class,
            () -> adapter.subscribe("demo", null, listener)).getErrCode());
        assertNotNull(adapter.subscribe("demo", null, listener));
        drain();
        assertEquals(1, listener.cards.size());
    }
    
    @Test
    void cancellationDuringActivationCannotResurrectBridge() throws Exception {
        CardListener listener = new CardListener();
        when(proxy.discoverAgent(any())).thenAnswer(call -> {
            adapter.unsubscribe("demo", null, listener);
            return snapshot("URL");
        });
        assertNull(adapter.subscribe("demo", null, listener));
        drain();
        assertTrue(listener.cards.isEmpty());
        assertTrue(registrations.isEmpty());
    }
    
    @Test
    void closedOrInvalidSubscriptionDoesNoRemoteWork() throws Exception {
        assertEquals(NacosException.INVALID_PARAM, assertThrows(NacosException.class,
            () -> adapter.subscribe("demo", null, null)).getErrCode());
        adapter.shutdown();
        assertEquals(NacosException.CLIENT_DISCONNECT, assertThrows(NacosException.class,
            () -> adapter.subscribe("demo", null, new CardListener())).getErrCode());
        verify(proxy, never()).discoverAgent(any());
    }
    
    @Test
    void terminalUnavailableReleasesBridgeUntilExplicitResubscribe() throws Exception {
        when(proxy.discoverAgent(any())).thenReturn(snapshot("URL"));
        CardListener listener = new CardListener();
        adapter.subscribe("demo", null, listener);
        drain();
        manager.markUnavailable(registrations.get(0).getClientWatchId(), NacosException.NO_RIGHT,
            "revoked", true);
        drain();
        assertTrue(scheduled.isEmpty());
        assertEquals(1, listener.cards.size());
        adapter.subscribe("demo", null, listener);
        drain();
        assertEquals(2, listener.cards.size());
        assertEquals(2, registrations.size());
    }
    
    @Test
    void queuedInitialCannotRunAfterShutdownAndNativeManagerRemainsUsable() throws Exception {
        when(proxy.discoverAgent(any())).thenReturn(snapshot("URL"));
        CardListener listener = new CardListener();
        adapter.subscribe("demo", null, listener);
        adapter.shutdown();
        drain();
        assertTrue(listener.cards.isEmpty());
        AgentDiscoveryRequest request = A2aRadConverter.discoveryRequest("public", "demo", null);
        assertNotNull(manager.subscribe(request.getReference(), request.getFilter(),
            mock(AbstractNacosAgentDiscoveryListener.class)));
    }
    
    private void refresh() {
        assertTrue(manager.markDirty(registrations.get(0).getClientWatchId(), "different", false));
        scheduled.remove(0).run();
        drain();
    }
    
    private void drain() {
        while (!callbacks.isEmpty()) {
            callbacks.remove(0).run();
        }
    }
    
    private static class CardListener extends AbstractNacosAgentCardListener {
        
        private final List<AgentCardDetailInfo> cards = new ArrayList<>();
        private Executor executor;
        private boolean failure;
        private Runnable after;
        
        @Override
        public void onEvent(NacosAgentCardEvent event) {
            cards.add(event.getAgentCard());
            if (after != null) {
                after.run();
            }
            if (failure) {
                throw new IllegalStateException("listener failure");
            }
        }
        
        @Override
        public Executor getExecutor() {
            return executor;
        }
    }
    
    private AgentDiscoveryResult snapshot(String type) throws Exception {
        AgentPublishRequest publish = A2aRadConverter.publishRequest("public", card(), type, false);
        AgentCallInterface call = publish.getCallInterfaces().get(0);
        EndpointSet runtime = new EndpointSet();
        runtime.setSource(EndpointSource.RUNTIME);
        runtime.setEndpoints(new ArrayList<Endpoint>());
        EndpointSet declared = call.getEndpointSets().get(0);
        call.setEndpointSets("URL".equals(type) ? Arrays.asList(declared, runtime)
            : Arrays.asList(runtime, declared));
        call.setEndpointSourceOrder(null);
        AgentDiscoveryResult result = new AgentDiscoveryResult();
        result.setNamespaceId("public");
        result.setAgentName("demo");
        result.setVersion("1.0.0");
        String digest = "sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
        result.setContentDigest(digest);
        declared.setSourceRevision(digest);
        runtime.setSourceRevision(
            "murmur3-x64-128-v1:bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb");
        result.setCallInterfaces(Collections.singletonList(call));
        return result;
    }
    
    private EndpointSet runtime(AgentDiscoveryResult snapshot) {
        return snapshot.getCallInterfaces().get(0).getEndpointSets().stream()
            .filter(set -> set.getSource() == EndpointSource.RUNTIME).findFirst().get();
    }
    
    private Endpoint endpoint(String uri, String transport, int priority) {
        Endpoint endpoint = new Endpoint();
        endpoint.setUri(uri);
        endpoint.setTransport(transport);
        endpoint.setPriority(priority);
        com.alibaba.nacos.api.ai.model.agent.RuntimeVersionBinding binding =
            new com.alibaba.nacos.api.ai.model.agent.RuntimeVersionBinding();
        binding.setRuntimeVersion("1.0.0");
        binding.setVersionRange("[1.0.0]");
        endpoint.setBindings(Collections.singletonList(binding));
        return endpoint;
    }
    
    private AgentCard card() {
        return JsonUtils.toObj("{\"name\":\"demo\",\"version\":\"1.0.0\","
            + "\"protocolVersion\":\"0.3\",\"url\":\"https://declared.example/rpc\","
            + "\"preferredTransport\":\"JSONRPC\",\"description\":\"full description\","
            + "\"capabilities\":{\"streaming\":true,\"extendedAgentCard\":true},"
            + "\"skills\":[{\"id\":\"s\",\"name\":\"skill\",\"tags\":[\"tag\"]}],"
            + "\"provider\":{\"organization\":\"Nacos\",\"url\":\"https://nacos.io\"},"
            + "\"securitySchemes\":{\"token\":{\"type\":\"http\",\"scheme\":\"bearer\"}},"
            + "\"security\":[{\"token\":[]}],\"securityRequirements\":[{\"token\":[]}],"
            + "\"signatures\":[{\"protected\":\"header\",\"signature\":\"signature\"}],"
            + "\"documentationUrl\":\"https://docs.example\","
            + "\"defaultInputModes\":[\"text/plain\"],\"defaultOutputModes\":[\"application/json\"]}",
            AgentCard.class);
    }
}
