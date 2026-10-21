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

import com.alibaba.nacos.api.ai.listener.AbstractNacosAgentDiscoveryListener;
import com.alibaba.nacos.api.ai.model.agent.AgentDiscoveryRequest;
import com.alibaba.nacos.api.ai.model.agent.AgentDiscoveryResult;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.client.ai.watch.AgentWatchListenerDispatcher.ListenerRegistration;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledFuture;

/**
 * One canonical Watch's materialized state and recovery progress.
 *
 * <p>All mutable fields and transitions are guarded by the owning manager's monitor.
 * The listener dispatcher shares that monitor; it never retains another snapshot cache.
 * Each activation barrier also identifies the refresh epoch, so old queries cannot
 * overwrite a reactivated subscription.</p>
 *
 * @author Nacos
 */
final class AgentWatchSubscription {
    
    final String requestKey;
    
    final String clientWatchId;
    
    final AgentDiscoveryRequest request;
    
    final Map<AbstractNacosAgentDiscoveryListener, ListenerRegistration> listeners =
        new IdentityHashMap<AbstractNacosAgentDiscoveryListener, ListenerRegistration>();
    
    final AgentWatchTransportCallback callback;
    
    AgentDiscoveryResult current;
    
    String fingerprint;
    
    WatchState state;
    
    boolean transportActive;
    
    boolean dirty;
    
    boolean refreshing;
    
    boolean pendingDiscoverRunning;
    
    boolean unavailableNotified;
    
    long stateSequence;
    
    int pendingFailureCount;
    
    long pendingDelayMillis;
    
    int refreshFailureCount;
    
    ScheduledFuture<?> pendingFuture;
    
    ScheduledFuture<?> refreshFuture;
    
    ActivationBarrier activationBarrier;
    
    AgentWatchSubscription(String requestKey, String clientWatchId,
        AgentDiscoveryRequest request, AgentWatchTransportCallback callback) {
        this.requestKey = requestKey;
        this.clientWatchId = clientWatchId;
        this.request = request;
        this.callback = callback;
    }
    
    void beginActivation() {
        setState(WatchState.ACTIVATING);
        activationBarrier = new ActivationBarrier();
    }
    
    void completeActivation(NacosException failure) {
        if (activationBarrier != null) {
            activationBarrier.complete(failure);
        }
    }
    
    void setState(WatchState state) {
        if (this.state == state) {
            return;
        }
        if (this.state == WatchState.LOCAL_PENDING) {
            AgentWatchClientMetrics.pendingRemoved();
        }
        this.state = state;
        if (state == WatchState.LOCAL_PENDING) {
            AgentWatchClientMetrics.pendingAdded();
        }
    }
    
    void setDirty(boolean dirty) {
        if (this.dirty == dirty) {
            return;
        }
        this.dirty = dirty;
        if (dirty) {
            AgentWatchClientMetrics.dirtyAdded();
        } else {
            AgentWatchClientMetrics.dirtyRemoved();
        }
    }
    
    enum WatchState {
        LOCAL_PENDING,
        ACTIVATING,
        ACTIVE,
        CLOSED
    }
    
    static final class ActivationBarrier {
        
        private final CountDownLatch latch = new CountDownLatch(1);
        
        NacosException failure;
        
        private boolean completed;
        
        private void complete(NacosException failure) {
            if (!completed) {
                completed = true;
                this.failure = failure;
                latch.countDown();
            }
        }
        
        void await() throws InterruptedException {
            latch.await();
        }
    }
    
}
