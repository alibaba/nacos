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

import com.alibaba.nacos.api.ai.model.rad.AgentWatchEventType;
import com.alibaba.nacos.api.ai.remote.request.AgentDiscoveryNotifyRequest;

import java.util.Objects;

/**
 * Mutable delivery state for one connection-owned gRPC Watch.
 *
 * @author Nacos
 */
final class AgentGrpcWatch {
    
    private enum State {
        SUBSCRIBING,
        ACTIVE,
        CLOSED
    }
    
    private final String watchKey;
    
    private final String connectionId;
    
    private final String clientWatchId;
    
    private final AgentProjectionKey projectionKey;
    
    private final AgentWatchOwnerContext owner;
    
    private State state = State.SUBSCRIBING;
    
    private String lastAcceptedFingerprint;
    
    private boolean dirty;
    
    private boolean deliveryScheduled;
    
    private boolean delivering;
    
    AgentGrpcWatch(String watchKey, String connectionId, String clientWatchId,
        AgentProjectionKey projectionKey, AgentWatchOwnerContext owner) {
        this.watchKey = watchKey;
        this.connectionId = connectionId;
        this.clientWatchId = clientWatchId;
        this.projectionKey = projectionKey;
        this.owner = owner;
    }
    
    String getWatchKey() {
        return watchKey;
    }
    
    String getConnectionId() {
        return connectionId;
    }
    
    String getClientWatchId() {
        return clientWatchId;
    }
    
    AgentProjectionKey getProjectionKey() {
        return projectionKey;
    }
    
    AgentWatchOwnerContext getOwner() {
        return owner;
    }
    
    synchronized boolean activate(String observedFingerprint) {
        if (state == State.CLOSED) {
            return false;
        }
        lastAcceptedFingerprint = observedFingerprint;
        state = State.ACTIVE;
        return scheduleIfNeeded();
    }
    
    synchronized boolean markDirty() {
        if (state == State.CLOSED) {
            return false;
        }
        dirty = true;
        return scheduleIfNeeded();
    }
    
    synchronized boolean beginDelivery() {
        if (state != State.ACTIVE || !deliveryScheduled || delivering) {
            return false;
        }
        deliveryScheduled = false;
        delivering = true;
        dirty = false;
        return true;
    }
    
    synchronized boolean shouldInvalidate(String fingerprint) {
        return !Objects.equals(lastAcceptedFingerprint, fingerprint);
    }
    
    synchronized boolean completeDelivery(AgentDiscoveryNotifyRequest delivered,
        boolean success) {
        delivering = false;
        if (state == State.CLOSED) {
            return false;
        }
        if (success && delivered != null
            && delivered.getEventType() == AgentWatchEventType.INVALIDATE) {
            lastAcceptedFingerprint = delivered.getObservedFingerprint();
        }
        if (!success) {
            dirty = true;
        }
        return scheduleIfNeeded();
    }
    
    synchronized void close() {
        state = State.CLOSED;
        dirty = false;
        deliveryScheduled = false;
    }
    
    synchronized boolean isClosed() {
        return state == State.CLOSED;
    }
    
    synchronized String getLastAcceptedFingerprint() {
        return lastAcceptedFingerprint;
    }
    
    private boolean scheduleIfNeeded() {
        if (state == State.ACTIVE && dirty && !deliveryScheduled && !delivering) {
            deliveryScheduled = true;
            return true;
        }
        return false;
    }
}
