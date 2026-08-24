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

package com.alibaba.nacos.client.ai.cache;

import com.alibaba.nacos.api.ai.listener.AbstractNacosAgentDiscoveryListener;
import com.alibaba.nacos.api.ai.listener.NacosAgentDiscoveryEvent;
import com.alibaba.nacos.api.ai.model.agent.EndpointSource;
import com.alibaba.nacos.api.ai.model.rad.AgentDiscoveryCallInterface;
import com.alibaba.nacos.api.ai.model.rad.AgentDiscoveryFilter;
import com.alibaba.nacos.api.ai.model.rad.AgentDiscoveryRequest;
import com.alibaba.nacos.api.ai.model.rad.AgentDiscoveryResult;
import com.alibaba.nacos.api.ai.model.rad.AgentReference;
import com.alibaba.nacos.api.ai.model.rad.EndpointSet;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.client.ai.remote.AiClientProxy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NacosAgentDiscoveryCacheHolderTest {
    
    @Mock
    private AiClientProxy clientProxy;
    
    @Mock
    private ScheduledExecutorService pollingExecutor;
    
    @Mock
    private ExecutorService callbackExecutor;
    
    @Mock
    private ScheduledFuture<?> future;
    
    private NacosAgentDiscoveryCacheHolder cacheHolder;
    
    @BeforeEach
    void setUp() {
        lenient().doReturn(future).when(pollingExecutor)
            .schedule(any(Runnable.class), anyLong(), any());
        cacheHolder = new NacosAgentDiscoveryCacheHolder("public", clientProxy, 10,
            pollingExecutor, callbackExecutor);
    }
    
    @Test
    void subscribeExistingAndDuplicateKeepOnePollingRecord() throws NacosException {
        AgentDiscoveryResult initial = result("1.0.0", "digest-a", "revision-a");
        when(clientProxy.discoverAgent(any(AgentDiscoveryRequest.class))).thenReturn(initial);
        TestListener listener = new TestListener(null);
        AgentReference reference = reference();
        
        AgentDiscoveryResult first = cacheHolder.subscribe(reference, null, listener);
        AgentDiscoveryResult duplicate = cacheHolder.subscribe(reference, null, listener);
        
        assertNotSame(initial, first);
        assertNotSame(first, duplicate);
        assertEquals("1.0.0", first.getVersion());
        verify(clientProxy).discoverAgent(any(AgentDiscoveryRequest.class));
        verify(pollingExecutor).schedule(any(Runnable.class), eq(10L), any());
        
        reference.setAgentName("changed");
        assertEquals("agent-a", capturedRequest().getReference().getAgentName());
    }
    
    @Test
    void configuredSubscriptionCapacityRejectsBeforeDiscoverAndReusesReleasedSlot()
        throws NacosException {
        NacosAgentDiscoveryCacheHolder limited = new NacosAgentDiscoveryCacheHolder("public",
            clientProxy, 10, pollingExecutor, callbackExecutor, 1);
        when(clientProxy.discoverAgent(any(AgentDiscoveryRequest.class))).thenReturn(null);
        TestListener firstListener = new TestListener(null);
        AgentReference first = reference();
        AgentReference second = reference();
        second.setAgentName("agent-b");
        
        limited.subscribe(first, null, firstListener);
        limited.subscribe(first, null, firstListener);
        NacosApiException exception = assertThrows(NacosApiException.class,
            () -> limited.subscribe(second, null, new TestListener(null)));
        assertEquals(NacosException.CLIENT_OVER_THRESHOLD, exception.getErrCode());
        assertEquals(ErrorCode.AGENT_DISCOVERY_SUBSCRIPTION_OVER_LIMIT.getCode(),
            exception.getDetailErrCode());
        verify(clientProxy).discoverAgent(any(AgentDiscoveryRequest.class));
        
        limited.unsubscribe(first, null, firstListener);
        limited.subscribe(second, null, new TestListener(null));
        verify(clientProxy, times(2)).discoverAgent(any(AgentDiscoveryRequest.class));
        limited.shutdown();
    }
    
    @Test
    void invalidSubscriptionCapacityIsRejected() {
        assertThrows(IllegalArgumentException.class,
            () -> new NacosAgentDiscoveryCacheHolder("public", clientProxy, 10,
                pollingExecutor, callbackExecutor, 0));
    }
    
    @Test
    void initialAbsentThenAppearsAndUnchangedPollDoesNotDuplicateCallback()
        throws NacosException {
        when(clientProxy.discoverAgent(any(AgentDiscoveryRequest.class)))
            .thenThrow(new NacosException(NacosException.NOT_FOUND, "missing"))
            .thenThrow(new NacosException(NacosException.NOT_FOUND, "missing"))
            .thenReturn(result("1.0.0", "digest-a", "revision-a"))
            .thenReturn(result("1.0.0", "digest-a", "revision-a"));
        TestListener listener = new TestListener(null);
        
        assertNull(cacheHolder.subscribe(reference(), null, listener));
        runScheduled(0);
        runScheduled(1);
        runCallback(0);
        runScheduled(2);
        
        assertEquals(1, listener.count);
        verify(callbackExecutor).execute(any(Runnable.class));
    }
    
    @Test
    void versionDigestAndSourceRevisionChangesDispatchCompleteSnapshots()
        throws NacosException {
        when(clientProxy.discoverAgent(any(AgentDiscoveryRequest.class)))
            .thenReturn(result("1.0.0", "digest-a", "revision-a"))
            .thenReturn(result("2.0.0", "digest-a", "revision-a"))
            .thenReturn(result("2.0.0", "digest-b", "revision-a"))
            .thenReturn(result("2.0.0", "digest-b", "revision-b"));
        TestListener listener = new TestListener(null);
        
        cacheHolder.subscribe(reference(), null, listener);
        for (int i = 0; i < 3; i++) {
            runScheduled(i);
            runCallback(i);
        }
        
        assertEquals(3, listener.count);
        assertEquals("2.0.0", listener.last.getAgentDiscoveryResult().getVersion());
    }
    
    @Test
    void filtersAreCanonicalForSubscriptionIdentity() throws NacosException {
        when(clientProxy.discoverAgent(any(AgentDiscoveryRequest.class)))
            .thenReturn(result("1.0.0", "digest-a", null));
        AgentDiscoveryFilter first = filter(Arrays.asList("a2a", "mcp"),
            Arrays.asList(EndpointSource.RUNTIME, EndpointSource.DECLARED));
        AgentDiscoveryFilter reordered = filter(Arrays.asList("mcp", "a2a"),
            Arrays.asList(EndpointSource.DECLARED, EndpointSource.RUNTIME));
        TestListener listener = new TestListener(null);
        
        cacheHolder.subscribe(reference(), first, listener);
        cacheHolder.subscribe(reference(), reordered, listener);
        
        verify(clientProxy).discoverAgent(any(AgentDiscoveryRequest.class));
        verify(pollingExecutor).schedule(any(Runnable.class), anyLong(), any());
    }
    
    @Test
    void omittedAndExplicitLatestSelectorsHaveIndependentPollingRecords()
        throws NacosException {
        when(clientProxy.discoverAgent(any(AgentDiscoveryRequest.class)))
            .thenReturn(result("2.0.0", "digest-a", "all-online"))
            .thenReturn(result("2.0.0", "digest-a", "latest-only"));
        TestListener listener = new TestListener(null);
        AgentReference omitted = reference();
        AgentReference explicitLatest = reference();
        explicitLatest.setLabel("latest");
        
        cacheHolder.subscribe(omitted, null, listener);
        cacheHolder.subscribe(explicitLatest, null, listener);
        
        ArgumentCaptor<AgentDiscoveryRequest> requests =
            ArgumentCaptor.forClass(AgentDiscoveryRequest.class);
        verify(clientProxy, times(2)).discoverAgent(requests.capture());
        assertNull(requests.getAllValues().get(0).getReference().getLabel());
        assertEquals("latest", requests.getAllValues().get(1).getReference().getLabel());
        verify(pollingExecutor, times(2)).schedule(any(Runnable.class), eq(10L), any());
    }
    
    @Test
    void nullFilterCollectionsUseCanonicalEmptyValues() throws NacosException {
        when(clientProxy.discoverAgent(any(AgentDiscoveryRequest.class)))
            .thenReturn(result("1.0.0", "digest-a", null));
        
        cacheHolder.subscribe(reference(), new AgentDiscoveryFilter(), new TestListener(null));
        
        verify(clientProxy).discoverAgent(any(AgentDiscoveryRequest.class));
    }
    
    @Test
    void staleScheduledPollStopsAfterUnsubscribe() throws NacosException {
        when(clientProxy.discoverAgent(any(AgentDiscoveryRequest.class)))
            .thenReturn(result("1.0.0", "digest-a", null))
            .thenReturn(result("2.0.0", "digest-b", null));
        TestListener listener = new TestListener(null);
        AgentReference reference = reference();
        cacheHolder.subscribe(reference, null, listener);
        cacheHolder.unsubscribe(reference, null, listener);
        
        runScheduled(0);
        
        assertEquals(0, listener.count);
        verify(pollingExecutor).schedule(any(Runnable.class), anyLong(), any());
    }
    
    @Test
    void subscriptionKeyEqualityHandlesIdentityAndForeignTypes() throws Exception {
        Class<?> keyType = Class.forName(
            NacosAgentDiscoveryCacheHolder.class.getName() + "$SubscriptionKey");
        Constructor<?> constructor = keyType.getDeclaredConstructor(String.class,
            AbstractNacosAgentDiscoveryListener.class);
        constructor.setAccessible(true);
        Object key = constructor.newInstance("request", new TestListener(null));
        
        assertEquals(key, key);
        assertNotEquals(key, "request");
    }
    
    @Test
    void differentListenersAreIndependentAndExactUnsubscribeCancelsOnlyOne()
        throws NacosException {
        when(clientProxy.discoverAgent(any(AgentDiscoveryRequest.class)))
            .thenReturn(result("1.0.0", "digest-a", null));
        TestListener first = new TestListener(null);
        TestListener second = new TestListener(null);
        AgentReference reference = reference();
        
        cacheHolder.subscribe(reference, null, first);
        cacheHolder.subscribe(reference, null, second);
        cacheHolder.unsubscribe(reference, null, first);
        cacheHolder.unsubscribe(reference, null, new TestListener(null));
        cacheHolder.unsubscribe(reference, null, null);
        
        verify(future).cancel(false);
        cacheHolder.shutdown();
        verify(future, times(2)).cancel(false);
    }
    
    @Test
    void customExecutorAndListenerFailureDoNotStopPolling() throws NacosException {
        Executor customExecutor = org.mockito.Mockito.mock(Executor.class);
        when(clientProxy.discoverAgent(any(AgentDiscoveryRequest.class)))
            .thenReturn(result("1.0.0", "digest-a", null))
            .thenReturn(result("2.0.0", "digest-a", null));
        TestListener listener = new TestListener(customExecutor);
        listener.fail = true;
        doAnswer(invocation -> {
            ((Runnable) invocation.getArgument(0)).run();
            return null;
        }).when(customExecutor).execute(any(Runnable.class));
        
        cacheHolder.subscribe(reference(), null, listener);
        runScheduled(0);
        
        assertEquals(1, listener.count);
        verify(customExecutor).execute(any(Runnable.class));
        verify(callbackExecutor, never()).execute(any(Runnable.class));
        verify(pollingExecutor, times(2)).schedule(any(Runnable.class), anyLong(), any());
    }
    
    @Test
    void callbackRejectionAndShutdownSuppressQueuedCallback() throws NacosException {
        when(clientProxy.discoverAgent(any(AgentDiscoveryRequest.class)))
            .thenReturn(result("1.0.0", "digest-a", null))
            .thenReturn(result("2.0.0", "digest-a", null));
        doThrow(new RejectedExecutionException("closed")).when(callbackExecutor)
            .execute(any(Runnable.class));
        TestListener rejected = new TestListener(null);
        cacheHolder.subscribe(reference(), null, rejected);
        runScheduled(0);
        assertEquals(0, rejected.count);
        
        cacheHolder.shutdown();
        cacheHolder.shutdown();
        verify(pollingExecutor).shutdownNow();
        verify(callbackExecutor).shutdownNow();
    }
    
    @Test
    void pollFailureReschedulesAndInitialFailurePropagates() throws NacosException {
        when(clientProxy.discoverAgent(any(AgentDiscoveryRequest.class)))
            .thenReturn(result("1.0.0", "digest-a", null))
            .thenThrow(new NacosException(NacosException.SERVER_ERROR, "failed"));
        cacheHolder.subscribe(reference(), null, new TestListener(null));
        runScheduled(0);
        verify(pollingExecutor, times(2)).schedule(any(Runnable.class), anyLong(), any());
        
        NacosAgentDiscoveryCacheHolder failing = new NacosAgentDiscoveryCacheHolder("public",
            clientProxy, 10, pollingExecutor, callbackExecutor);
        assertThrows(NacosException.class,
            () -> failing.subscribe(reference(), null, new TestListener(null)));
    }
    
    @Test
    void invalidListenerAndInvalidUnsubscribeAreRejected() {
        assertThrows(NacosException.class,
            () -> cacheHolder.subscribe(reference(), null, null));
        AgentReference invalid = reference();
        invalid.setVersion("1.0.0");
        invalid.setLabel("stable");
        assertThrows(NacosException.class,
            () -> cacheHolder.unsubscribe(invalid, null, null));
    }
    
    @Test
    void defaultConstructorShutsDownOwnedExecutors() throws NacosException {
        NacosAgentDiscoveryCacheHolder owned =
            new NacosAgentDiscoveryCacheHolder("public", clientProxy);
        owned.shutdown();
    }
    
    private AgentDiscoveryRequest capturedRequest() throws NacosException {
        ArgumentCaptor<AgentDiscoveryRequest> captor =
            ArgumentCaptor.forClass(AgentDiscoveryRequest.class);
        verify(clientProxy).discoverAgent(captor.capture());
        return captor.getValue();
    }
    
    private void runScheduled(int index) {
        ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
        verify(pollingExecutor, times(index + 1)).schedule(captor.capture(), anyLong(), any());
        captor.getAllValues().get(index).run();
    }
    
    private void runCallback(int index) {
        ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
        verify(callbackExecutor, times(index + 1)).execute(captor.capture());
        captor.getAllValues().get(index).run();
    }
    
    private AgentReference reference() {
        AgentReference result = new AgentReference();
        result.setAgentName("agent-a");
        return result;
    }
    
    private AgentDiscoveryFilter filter(java.util.List<String> protocols,
        java.util.List<EndpointSource> sources) {
        AgentDiscoveryFilter result = new AgentDiscoveryFilter();
        result.setProtocols(protocols);
        result.setTransports(Arrays.asList("http", "grpc"));
        result.setEndpointSources(sources);
        result.setProtocolVersion("1.0.0");
        Map<String, String> selector = new HashMap<String, String>();
        selector.put("zone", "east");
        result.setMetadataSelector(selector);
        return result;
    }
    
    private AgentDiscoveryResult result(String version, String digest, String revision) {
        AgentDiscoveryResult result = new AgentDiscoveryResult();
        result.setNamespaceId("public");
        result.setAgentName("agent-a");
        result.setVersion(version);
        result.setContentDigest(digest);
        AgentDiscoveryCallInterface callInterface = new AgentDiscoveryCallInterface();
        callInterface.setProtocol("a2a");
        if (revision == null) {
            callInterface.setEndpointSets(null);
        } else {
            EndpointSet endpointSet = new EndpointSet();
            endpointSet.setSource(EndpointSource.RUNTIME);
            endpointSet.setSourceRevision(revision);
            callInterface.setEndpointSets(Collections.singletonList(endpointSet));
        }
        result.setCallInterfaces(Collections.singletonList(callInterface));
        return result;
    }
    
    private static final class TestListener extends AbstractNacosAgentDiscoveryListener {
        
        private final Executor executor;
        
        private int count;
        
        private boolean fail;
        
        private NacosAgentDiscoveryEvent last;
        
        private TestListener(Executor executor) {
            this.executor = executor;
        }
        
        @Override
        public void onEvent(NacosAgentDiscoveryEvent event) {
            count++;
            last = event;
            if (fail) {
                throw new IllegalStateException("listener failure");
            }
        }
        
        @Override
        public Executor getExecutor() {
            return executor;
        }
    }
}
