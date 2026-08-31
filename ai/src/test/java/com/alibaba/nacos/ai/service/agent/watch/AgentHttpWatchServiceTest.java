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

package com.alibaba.nacos.ai.service.agent.watch;

import com.alibaba.nacos.ai.service.VisibilityHelper;
import com.alibaba.nacos.ai.service.agent.runtime.AgentHttpClientLifecycleService;
import com.alibaba.nacos.api.ai.model.rad.AgentDiscoveryRequest;
import com.alibaba.nacos.api.ai.model.rad.AgentReference;
import com.alibaba.nacos.api.ai.model.rad.AgentWatchBatchItem;
import com.alibaba.nacos.api.ai.model.rad.AgentWatchBatchRequest;
import com.alibaba.nacos.api.ai.model.rad.AgentWatchBatchResponse;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.sys.env.EnvUtil;
import com.alibaba.nacos.api.model.v2.Result;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.util.unit.DataSize;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.web.context.request.async.DeferredResult;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.RejectedExecutionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentHttpWatchServiceTest {
    
    private AgentProjectionService projectionService;
    
    private AgentHttpClientLifecycleService lifecycleService;
    
    private AgentHttpWatchRegistry registry;
    
    private AgentHttpWatchService service;
    
    private Map<AgentProjectionKey, AgentProjectionState> states;
    
    private MockedStatic<VisibilityHelper> visibilityHelper;
    
    @BeforeEach
    void setUp() {
        projectionService = mock(AgentProjectionService.class);
        lifecycleService = mock(AgentHttpClientLifecycleService.class);
        registry = new AgentHttpWatchRegistry();
        states = new LinkedHashMap<>();
        service = service(Runnable::run);
        when(projectionService.retain(any(AgentDiscoveryRequest.class)))
            .thenAnswer(invocation -> AgentProjectionKey.of(invocation.getArgument(0)));
        when(projectionService.refreshNow(any(AgentProjectionKey.class)))
            .thenAnswer(invocation -> states.get(invocation.getArgument(0)));
        when(projectionService.getState(any(AgentProjectionKey.class)))
            .thenAnswer(invocation -> Optional.ofNullable(states.get(invocation.getArgument(0))));
        visibilityHelper = mockStatic(VisibilityHelper.class);
        visibilityHelper.when(VisibilityHelper::resolveCurrentIdentity).thenReturn("alice");
    }
    
    @AfterEach
    void tearDown() {
        visibilityHelper.close();
    }
    
    @Test
    void testUnchangedWaitsThenProjectionUpdateReturnsChangedId() throws Exception {
        double bytesBefore = AgentWatchMetrics.byteCount(AgentWatchMetrics.Transport.HTTP);
        double acceptedBefore = AgentWatchMetrics.eventCount(
            AgentWatchMetrics.Event.HTTP_LONG_POLL, AgentWatchMetrics.Result.ACCEPTED);
        AgentWatchBatchRequest request = request(1L, item("watch", "agent", "fingerprint-a"));
        AgentProjectionKey key = key(request, 0);
        states.put(key, available("fingerprint-a"));
        service.start();
        
        DeferredResult<Result<AgentWatchBatchResponse>> deferred =
            service.watch("client", "AI", request, 100);
        assertFalse(deferred.hasResult());
        assertEquals(1, service.size());
        assertEquals(100L, service.activeBytes());
        assertEquals(bytesBefore + 100D,
            AgentWatchMetrics.byteCount(AgentWatchMetrics.Transport.HTTP));
        assertEquals(acceptedBefore + 1D, AgentWatchMetrics.eventCount(
            AgentWatchMetrics.Event.HTTP_LONG_POLL, AgentWatchMetrics.Result.ACCEPTED));
        verify(lifecycleService).renewForWatch("client", "AI", "public");
        
        AgentProjectionState changed = available("fingerprint-b");
        states.put(key, changed);
        service.onProjectionUpdate(update(key, changed));
        AgentWatchBatchResponse response = result(deferred);
        assertTrue(response.isChanged());
        assertEquals(1L, response.getGeneration());
        assertEquals(Collections.singletonList("watch"), response.getChangedClientWatchIds());
        assertEquals(0, service.size());
        assertEquals(0L, service.activeBytes());
        verify(projectionService).release(key);
        verify(projectionService).addUpdateListener(service);
    }
    
    @Test
    void testImmediateMismatchAndUnavailableReturnOnlyIds() throws Exception {
        AgentWatchBatchRequest request = request(2L,
            item("changed", "agent-a", "old"), item("same", "agent-b", "same"));
        states.put(key(request, 0), available("new"));
        states.put(key(request, 1), available("same"));
        DeferredResult<Result<AgentWatchBatchResponse>> deferred =
            service.watch("client", "AI", request, 120);
        AgentWatchBatchResponse response = result(deferred);
        assertEquals(Collections.singletonList("changed"), response.getChangedClientWatchIds());
        assertEquals(0, service.size());
        
        AgentWatchBatchRequest unavailable = request(3L,
            item("missing", "agent-c", "old"));
        AgentProjectionKey missingKey = key(unavailable, 0);
        states.put(missingKey, AgentProjectionState.failure(AgentProjectionStatus.NOT_FOUND,
            404, "must-not-leak", 1L));
        response = result(service.watch("client", "AI", unavailable, 80));
        assertEquals(Collections.singletonList("missing"), response.getChangedClientWatchIds());
    }
    
    @Test
    void testTimeoutCancelAndReplacementCleanExactlyOnce() throws Exception {
        AgentWatchBatchRequest first = request(1L, item("watch", "agent", "same"));
        AgentProjectionKey key = key(first, 0);
        states.put(key, available("same"));
        DeferredResult<Result<AgentWatchBatchResponse>> firstDeferred =
            service.watch("client", "AI", first, 40);
        AgentHttpWatchWaiter firstWaiter = registry.findByProjection(key).get(0);
        assertTrue(firstWaiter.cancel());
        assertEquals(0, service.size());
        assertFalse(firstDeferred.hasResult());
        
        DeferredResult<Result<AgentWatchBatchResponse>> current =
            service.watch("client", "AI", first, 40);
        AgentWatchBatchRequest replacement = request(2L, item("watch", "agent", "same"));
        DeferredResult<Result<AgentWatchBatchResponse>> next =
            service.watch("client", "AI", replacement, 50);
        assertFalse(next.hasResult());
        AgentWatchBatchResponse superseded = result(current);
        assertFalse(superseded.isChanged());
        assertEquals(1L, superseded.getGeneration());
        registry.findByProjection(key).get(0).timeout();
        assertFalse(result(next).isChanged());
        verify(projectionService, times(3)).release(key);
    }
    
    @Test
    void testRejectedNotificationExecutorFallsBackSynchronously() throws Exception {
        service = service(command -> {
            throw new RejectedExecutionException("rejected");
        });
        AgentWatchBatchRequest request = request(1L, item("watch", "agent", "same"));
        AgentProjectionKey key = key(request, 0);
        states.put(key, available("same"));
        DeferredResult<Result<AgentWatchBatchResponse>> deferred =
            service.watch("client", "AI", request, 40);
        AgentProjectionState changed = available("new");
        service.onProjectionUpdate(update(key, changed));
        assertTrue(result(deferred).isChanged());
        assertEquals(0, service.size());
    }
    
    @Test
    void testRequestByteAndNodeCapacityFailuresHaveNoPartialState() throws Exception {
        AgentWatchBatchRequest request = request(1L, item("watch", "agent", "same"));
        NacosApiException tooLarge = assertThrows(NacosApiException.class,
            () -> service.watch("client", "AI", request, 1001));
        assertEquals(ErrorCode.AGENT_DISCOVERY_SUBSCRIPTION_OVER_LIMIT.getCode(),
            tooLarge.getDetailErrCode());
        verify(lifecycleService, never()).renewForWatch(any(), any(), any());
        assertEquals(0, service.size());
        
        states.put(key(request, 0), available("same"));
        service.watch("client-one", "AI", request, 600);
        AgentWatchBatchRequest other = request(1L, item("other", "other-agent", "same"));
        states.put(key(other, 0), available("same"));
        NacosApiException nodeBytes = assertThrows(NacosApiException.class,
            () -> service.watch("client-two", "AI", other, 500));
        assertEquals(ErrorCode.AGENT_DISCOVERY_SUBSCRIPTION_OVER_LIMIT.getCode(),
            nodeBytes.getDetailErrCode());
        assertEquals(1, service.size());
        assertEquals(600L, service.activeBytes());
    }
    
    @Test
    void testRefreshAndRetainFailuresReleaseProjectionReferences() {
        AgentWatchBatchRequest request = request(1L, item("watch", "agent", "same"));
        AgentProjectionKey key = key(request, 0);
        when(projectionService.refreshNow(key)).thenThrow(new IllegalStateException("failed"));
        assertThrows(IllegalStateException.class,
            () -> service.watch("client", "AI", request, 40));
        verify(projectionService).release(key);
        assertEquals(0, service.size());
        
        AgentWatchBatchRequest two = request(2L, item("one", "a", "same"),
            item("two", "b", "same"));
        AgentProjectionKey first = key(two, 0);
        when(projectionService.retain(any(AgentDiscoveryRequest.class)))
            .thenReturn(first).thenThrow(new IllegalStateException("retain failed"));
        assertThrows(IllegalStateException.class,
            () -> service.watch("other", "AI", two, 40));
        verify(projectionService).release(first);
    }
    
    @Test
    void testShutdownCompletesWaitersAndRejectsNewRequests() throws Exception {
        AgentWatchBatchRequest request = request(1L, item("watch", "agent", "same"));
        states.put(key(request, 0), available("same"));
        DeferredResult<Result<AgentWatchBatchResponse>> deferred =
            service.watch("client", "AI", request, 40);
        service.start();
        service.shutdown();
        service.shutdown();
        assertFalse(result(deferred).isChanged());
        assertEquals(0, service.size());
        verify(projectionService).removeUpdateListener(service);
        assertThrows(IllegalStateException.class,
            () -> service.watch("client", "AI", request, 40));
        service.onProjectionUpdate(update(key(request, 0), available("new")));
    }
    
    @Test
    void testPublicConstructorAndInvalidRequestGuard() {
        org.springframework.core.env.ConfigurableEnvironment previous = EnvUtil.getEnvironment();
        try {
            EnvUtil.setEnvironment(new MockEnvironment());
            AgentHttpWatchService configured = new AgentHttpWatchService(projectionService,
                lifecycleService, DataSize.ofKilobytes(2));
            configured.start();
            configured.shutdown();
        } finally {
            EnvUtil.setEnvironment(previous);
        }
        
        assertThrows(IllegalArgumentException.class,
            () -> service.watch("client", "AI", null, 1));
        AgentWatchBatchRequest empty = new AgentWatchBatchRequest();
        empty.setWatches(Collections.emptyList());
        assertThrows(IllegalArgumentException.class,
            () -> service.watch("client", "AI", empty, 1));
    }
    
    @Test
    void testStaleGenerationReturnsTimeoutWithoutReplacingCurrent() throws Exception {
        AgentWatchBatchRequest current = request(2L, item("watch", "agent", "same"));
        AgentProjectionKey key = key(current, 0);
        states.put(key, available("same"));
        DeferredResult<Result<AgentWatchBatchResponse>> active =
            service.watch("client", "AI", current, 40);
        
        AgentWatchBatchRequest stale = request(1L, item("watch", "agent", "same"));
        AgentWatchBatchResponse response = result(service.watch("client", "AI", stale, 20));
        assertFalse(response.isChanged());
        assertEquals(1L, response.getGeneration());
        assertEquals(1, service.size());
        assertFalse(active.hasResult());
        registry.findByProjection(key).get(0).timeout();
    }
    
    @Test
    void testUnexpectedCanonicalKeyReleasesActualReference() {
        AgentWatchBatchRequest request = request(1L, item("watch", "agent", "same"));
        AgentProjectionKey actual = AgentProjectionKey.of(
            item("other", "other-agent", "same").getDiscoveryRequest());
        when(projectionService.retain(any(AgentDiscoveryRequest.class))).thenReturn(actual);
        
        assertThrows(IllegalStateException.class,
            () -> service.watch("client", "AI", request, 40));
        verify(projectionService).release(actual);
        assertEquals(0, service.size());
    }
    
    @Test
    void testConstructorRejectsInvalidLimits() {
        assertThrows(IllegalArgumentException.class,
            () -> new AgentHttpWatchService(projectionService, lifecycleService, registry,
                0, 1, 1L, 1L, Runnable::run));
        assertThrows(IllegalArgumentException.class,
            () -> new AgentHttpWatchService(projectionService, lifecycleService, registry,
                1, 0, 1L, 1L, Runnable::run));
        assertThrows(IllegalArgumentException.class,
            () -> new AgentHttpWatchService(projectionService, lifecycleService, registry,
                1, 1, 0L, 1L, Runnable::run));
        assertThrows(IllegalArgumentException.class,
            () -> new AgentHttpWatchService(projectionService, lifecycleService, registry,
                1, 1, 1L, 0L, Runnable::run));
    }
    
    private AgentHttpWatchService service(java.util.concurrent.Executor executor) {
        return new AgentHttpWatchService(projectionService, lifecycleService, registry,
            3, 2, 1000L, 1000L, executor);
    }
    
    private AgentProjectionUpdate update(AgentProjectionKey key, AgentProjectionState current) {
        return new AgentProjectionUpdate(key, null, current,
            EnumSet.of(AgentProjectionChangeReason.RUNTIME));
    }
    
    private AgentProjectionState available(String fingerprint) {
        return AgentProjectionState.available(fingerprint, Collections.emptySet(), 1L);
    }
    
    private AgentProjectionKey key(AgentWatchBatchRequest request, int index) {
        return AgentProjectionKey.of(request.getWatches().get(index).getDiscoveryRequest());
    }
    
    private AgentWatchBatchRequest request(long generation, AgentWatchBatchItem... items) {
        AgentWatchBatchRequest result = new AgentWatchBatchRequest();
        result.setGeneration(generation);
        result.setTimeoutMillis(1000L);
        result.setWatches(Arrays.asList(items));
        return result;
    }
    
    private AgentWatchBatchItem item(String id, String agentName, String fingerprint) {
        AgentReference reference = new AgentReference();
        reference.setAgentName(agentName);
        AgentDiscoveryRequest discoveryRequest = new AgentDiscoveryRequest();
        discoveryRequest.setNamespaceId("public");
        discoveryRequest.setReference(reference);
        AgentWatchBatchItem result = new AgentWatchBatchItem();
        result.setClientWatchId(id);
        result.setDiscoveryRequest(discoveryRequest);
        result.setMaterializedFingerprint(fingerprint);
        return result;
    }
    
    @SuppressWarnings("unchecked")
    private AgentWatchBatchResponse result(
        DeferredResult<Result<AgentWatchBatchResponse>> deferred) {
        return ((Result<AgentWatchBatchResponse>) deferred.getResult()).getData();
    }
}
