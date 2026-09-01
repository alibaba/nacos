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

import com.alibaba.nacos.api.ai.model.rad.AgentDiscoveryRequest;
import com.alibaba.nacos.api.ai.model.rad.AgentReference;
import com.alibaba.nacos.api.exception.NacosException;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PollingAgentWatchTransportTest {
    
    @Test
    void registrationIsDefensiveAndPollingLifecycleIsIdempotent() throws Exception {
        ScheduledExecutorService executor = mock(ScheduledExecutorService.class);
        List<Runnable> tasks = new ArrayList<Runnable>();
        List<ScheduledFuture<?>> futures = new ArrayList<ScheduledFuture<?>>();
        when(executor.schedule(any(Runnable.class), eq(10L), any())).thenAnswer(invocation -> {
            tasks.add(invocation.getArgument(0));
            ScheduledFuture<?> future = mock(ScheduledFuture.class);
            futures.add(future);
            return future;
        });
        PollingAgentWatchTransport transport = new PollingAgentWatchTransport(executor, 10L);
        TestCallback callback = new TestCallback();
        AgentWatchRegistration registration = registration("watch-a", "fingerprint-a");
        
        transport.start(registration, callback);
        transport.start(registration, callback);
        transport.update(registration("watch-a", "fingerprint-b"));
        assertNotSame(registration.getDiscoveryRequest(), registration.getDiscoveryRequest());
        assertEquals("agent-a",
            registration.getDiscoveryRequest().getReference().getAgentName());
        assertEquals("fingerprint-a", registration.getMaterializedFingerprint());
        
        tasks.get(0).run();
        assertEquals(1, callback.invalidations);
        verify(executor, times(2)).schedule(any(Runnable.class), eq(10L), any());
        transport.stop("unknown");
        transport.stop("watch-a");
        verify(futures.get(1)).cancel(false);
        tasks.get(1).run();
        verify(executor, times(2)).schedule(any(Runnable.class), eq(10L), any());
        
        transport.shutdown();
        transport.shutdown();
        NacosException closed = assertThrows(NacosException.class,
            () -> transport.start(registration, callback));
        assertEquals(NacosException.CLIENT_DISCONNECT, closed.getErrCode());
        assertEquals(1, callback.invalidations);
    }
    
    @Test
    void rejectedScheduleDoesNotRetainPollingTask() throws Exception {
        ScheduledExecutorService executor = mock(ScheduledExecutorService.class);
        when(executor.schedule(any(Runnable.class), anyLong(), any()))
            .thenThrow(new RejectedExecutionException("closed"));
        PollingAgentWatchTransport transport = new PollingAgentWatchTransport(executor, 10L);
        AgentWatchRegistration registration = registration("watch-a", null);
        
        assertThrows(NacosException.class,
            () -> transport.start(registration, new TestCallback()));
        assertThrows(NacosException.class,
            () -> transport.start(registration, new TestCallback()));
        verify(executor, times(2)).schedule(any(Runnable.class), eq(10L), any());
        transport.shutdown();
    }
    
    @Test
    void stoppedOrRejectedFollowUpPollingNeverLeaksStaleInvalidation() throws Exception {
        ScheduledExecutorService executor = mock(ScheduledExecutorService.class);
        List<Runnable> tasks = new ArrayList<Runnable>();
        ScheduledFuture<?> future = mock(ScheduledFuture.class);
        when(executor.schedule(any(Runnable.class), eq(10L), any()))
            .thenAnswer(invocation -> {
                tasks.add(invocation.getArgument(0));
                return future;
            }).thenThrow(new RejectedExecutionException("closed"));
        PollingAgentWatchTransport transport = new PollingAgentWatchTransport(executor, 10L);
        TestCallback callback = new TestCallback();
        transport.start(registration("watch-a", null), callback);
        
        tasks.get(0).run();
        
        assertEquals(1, callback.invalidations);
        assertEquals(1, callback.unavailable);
        assertTrue(callback.terminal);
        transport.stop("watch-a");
        
        PollingAgentWatchTransport stopped = new PollingAgentWatchTransport(executor, 10L);
        TestCallback stoppingCallback = new TestCallback();
        stoppingCallback.onInvalidate = () -> stopped.stop("watch-b");
        when(executor.schedule(any(Runnable.class), eq(10L), any())).thenAnswer(invocation -> {
            tasks.add(invocation.getArgument(0));
            return future;
        });
        stopped.start(registration("watch-b", null), stoppingCallback);
        tasks.get(1).run();
        assertEquals(1, stoppingCallback.invalidations);
        assertEquals(0, stoppingCallback.unavailable);
        verify(future).cancel(false);
        stopped.shutdown();
    }
    
    @Test
    void shutdownCancelsEveryActivePollingTask() throws Exception {
        ScheduledExecutorService executor = mock(ScheduledExecutorService.class);
        ScheduledFuture<?> first = mock(ScheduledFuture.class);
        ScheduledFuture<?> second = mock(ScheduledFuture.class);
        List<ScheduledFuture<?>> futures = new ArrayList<ScheduledFuture<?>>();
        futures.add(first);
        futures.add(second);
        when(executor.schedule(any(Runnable.class), eq(10L), any()))
            .thenAnswer(invocation -> futures.remove(0));
        PollingAgentWatchTransport transport = new PollingAgentWatchTransport(executor, 10L);
        transport.start(registration("watch-a", null), new TestCallback());
        transport.start(registration("watch-b", null), new TestCallback());
        
        transport.shutdown();
        
        verify(first).cancel(false);
        verify(second).cancel(false);
    }
    
    @Test
    void jitteredRetryRemainsWithinBoundsAndRejectsInvalidBounds() {
        AgentWatchRetryPolicy policy = new AgentWatchRetryPolicy.Jittered(100L, 1000L);
        for (int attempt = 1; attempt < 30; attempt++) {
            long delay = policy.nextDelayMillis(attempt, "watch");
            assertTrue(delay >= 80L);
            assertTrue(delay <= 1000L);
        }
        AgentWatchRetryPolicy fixed = new AgentWatchRetryPolicy.Jittered(100L, 100L);
        assertEquals(100L, fixed.nextDelayMillis(1, "watch"));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
            () -> new AgentWatchRetryPolicy.Jittered(0L, 1L));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
            () -> new AgentWatchRetryPolicy.Jittered(2L, 1L));
    }
    
    private AgentWatchRegistration registration(String watchId, String fingerprint) {
        AgentDiscoveryRequest request = new AgentDiscoveryRequest();
        request.setNamespaceId("public");
        AgentReference reference = new AgentReference();
        reference.setAgentName("agent-a");
        request.setReference(reference);
        return new AgentWatchRegistration(watchId, request, fingerprint);
    }
    
    private static final class TestCallback implements AgentWatchTransportCallback {
        
        private int invalidations;
        
        private int unavailable;
        
        private boolean terminal;
        
        private Runnable onInvalidate;
        
        @Override
        public boolean invalidate(String observedFingerprint, boolean forceRefresh) {
            invalidations++;
            if (onInvalidate != null) {
                onInvalidate.run();
            }
            return true;
        }
        
        @Override
        public void unavailable(int errorCode, String errorMessage, boolean terminal) {
            unavailable++;
            this.terminal = terminal;
        }
    }
}
