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

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Request-scoped indexes and atomic capacity admission for HTTP Agent Watch waiters.
 *
 * @author Nacos
 */
final class AgentHttpWatchRegistry {
    
    private final Map<String, AgentHttpWatchWaiter> waiters =
        new LinkedHashMap<String, AgentHttpWatchWaiter>();
    
    private final Map<AgentHttpWatchOwnerKey, String> ownerIndex =
        new HashMap<AgentHttpWatchOwnerKey, String>();
    
    private final Map<AgentProjectionKey, Set<String>> projectionIndex =
        new HashMap<AgentProjectionKey, Set<String>>();
    
    private long activeBytes;
    
    synchronized Registration register(AgentHttpWatchWaiter waiter, int maxItemsPerClient,
        int maxWaitersPerNode, long maxActiveBytesPerNode) throws NacosApiException {
        String existingId = ownerIndex.get(waiter.getOwnerKey());
        AgentHttpWatchWaiter existing = existingId == null ? null : waiters.get(existingId);
        if (existing != null && waiter.getGeneration() < existing.getGeneration()) {
            return Registration.stale();
        }
        int existingItems = existing == null ? 0 : existing.getItemCount();
        if (waiter.getItemCount() > existingItems && existingItems >= maxItemsPerClient) {
            throw capacity("Agent HTTP Watch limit of " + maxItemsPerClient
                + " reached for this client.");
        }
        int projectedWaiters = waiters.size() - (existing == null ? 0 : 1) + 1;
        if (projectedWaiters > maxWaitersPerNode) {
            throw capacity("Agent HTTP Watch waiter capacity reached on this server.");
        }
        long projectedBytes = activeBytes - (existing == null ? 0 : existing.getPayloadBytes())
            + waiter.getPayloadBytes();
        if (projectedBytes > maxActiveBytesPerNode) {
            throw capacity("Agent HTTP Watch active request-byte capacity reached on this server.");
        }
        if (existing != null) {
            removeInternal(existing);
        }
        add(waiter);
        return Registration.accepted(existing);
    }
    
    synchronized AgentHttpWatchWaiter remove(String waiterId) {
        AgentHttpWatchWaiter waiter = waiters.get(waiterId);
        if (waiter != null) {
            removeInternal(waiter);
        }
        return waiter;
    }
    
    synchronized List<AgentHttpWatchWaiter> findByProjection(AgentProjectionKey key) {
        Set<String> ids = projectionIndex.get(key);
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        List<AgentHttpWatchWaiter> result = new ArrayList<AgentHttpWatchWaiter>(ids.size());
        for (String each : ids) {
            AgentHttpWatchWaiter waiter = waiters.get(each);
            if (waiter != null) {
                result.add(waiter);
            }
        }
        return result;
    }
    
    synchronized List<AgentHttpWatchWaiter> clear() {
        List<AgentHttpWatchWaiter> result = new ArrayList<AgentHttpWatchWaiter>(waiters.values());
        waiters.clear();
        ownerIndex.clear();
        projectionIndex.clear();
        activeBytes = 0L;
        updateMetrics();
        return result;
    }
    
    synchronized int size() {
        return waiters.size();
    }
    
    synchronized long activeBytes() {
        return activeBytes;
    }
    
    private void add(AgentHttpWatchWaiter waiter) {
        waiters.put(waiter.getWaiterId(), waiter);
        ownerIndex.put(waiter.getOwnerKey(), waiter.getWaiterId());
        activeBytes += waiter.getPayloadBytes();
        for (AgentProjectionKey key : waiter.getProjectionKeys()) {
            Set<String> ids = projectionIndex.get(key);
            if (ids == null) {
                ids = new LinkedHashSet<String>();
                projectionIndex.put(key, ids);
            }
            ids.add(waiter.getWaiterId());
        }
        updateMetrics();
    }
    
    private void removeInternal(AgentHttpWatchWaiter waiter) {
        waiters.remove(waiter.getWaiterId());
        if (waiter.getWaiterId().equals(ownerIndex.get(waiter.getOwnerKey()))) {
            ownerIndex.remove(waiter.getOwnerKey());
        }
        activeBytes -= waiter.getPayloadBytes();
        for (AgentProjectionKey key : waiter.getProjectionKeys()) {
            Set<String> ids = projectionIndex.get(key);
            if (ids == null) {
                continue;
            }
            ids.remove(waiter.getWaiterId());
            if (ids.isEmpty()) {
                projectionIndex.remove(key);
            }
        }
        updateMetrics();
    }
    
    private NacosApiException capacity(String message) {
        AgentWatchMetrics.record(AgentWatchMetrics.Event.CAPACITY_REJECTION,
            AgentWatchMetrics.Result.REJECTED);
        return new NacosApiException(NacosException.OVER_THRESHOLD,
            ErrorCode.AGENT_DISCOVERY_SUBSCRIPTION_OVER_LIMIT, message);
    }
    
    private void updateMetrics() {
        AgentWatchMetrics.setActiveHttpWaiters(waiters.size(), activeBytes);
    }
    
    static final class Registration {
        
        private final boolean stale;
        
        private final AgentHttpWatchWaiter replaced;
        
        private Registration(boolean stale, AgentHttpWatchWaiter replaced) {
            this.stale = stale;
            this.replaced = replaced;
        }
        
        static Registration stale() {
            return new Registration(true, null);
        }
        
        static Registration accepted(AgentHttpWatchWaiter replaced) {
            return new Registration(false, replaced);
        }
        
        boolean isStale() {
            return stale;
        }
        
        AgentHttpWatchWaiter getReplaced() {
            return replaced;
        }
    }
}
