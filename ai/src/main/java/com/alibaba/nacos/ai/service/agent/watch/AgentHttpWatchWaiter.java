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

import com.alibaba.nacos.api.ai.model.rad.AgentWatchBatchItem;
import com.alibaba.nacos.api.ai.model.rad.AgentWatchBatchResponse;
import com.alibaba.nacos.api.ai.utils.AgentWatchLogUtils;
import com.alibaba.nacos.api.model.v2.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.context.request.async.DeferredResult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * One request-scoped HTTP Batch Long Poll waiter.
 *
 * @author Nacos
 */
final class AgentHttpWatchWaiter {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(AgentHttpWatchWaiter.class);
    
    private final String waiterId = UUID.randomUUID().toString();
    
    private final AgentHttpWatchOwnerKey ownerKey;
    
    private final long generation;
    
    private final int itemCount;
    
    private final int payloadBytes;
    
    private final Map<AgentProjectionKey, List<Observation>> observations;
    
    private final DeferredResult<Result<AgentWatchBatchResponse>> deferredResult;
    
    private final Consumer<AgentHttpWatchWaiter> cleanup;
    
    private final AtomicBoolean completed = new AtomicBoolean();
    
    private final long startedNanos = System.nanoTime();
    
    AgentHttpWatchWaiter(AgentHttpWatchOwnerKey ownerKey, long generation, long timeoutMillis,
        List<AgentWatchBatchItem> items, int payloadBytes,
        Consumer<AgentHttpWatchWaiter> cleanup) {
        this.ownerKey = ownerKey;
        this.generation = generation;
        this.itemCount = items.size();
        this.payloadBytes = payloadBytes;
        this.cleanup = cleanup;
        this.observations = index(items);
        this.deferredResult = new DeferredResult<Result<AgentWatchBatchResponse>>(timeoutMillis);
        deferredResult.onTimeout(this::timeout);
        deferredResult.onError(throwable -> cancel());
        deferredResult.onCompletion(this::cancel);
    }
    
    boolean completeIfChanged(AgentProjectionKey key, AgentProjectionState state) {
        return completeIfChanged(key, state, "CHANGE_FANOUT");
    }
    
    boolean completeIfChanged(AgentProjectionKey key, AgentProjectionState state,
        String trigger) {
        List<Observation> candidates = observations.get(key);
        if (candidates == null) {
            return false;
        }
        List<String> changedIds = new ArrayList<String>(candidates.size());
        addChangedIds(candidates, state, changedIds);
        return completeChanged(changedIds, trigger);
    }
    
    boolean completeIfChanged(Map<AgentProjectionKey, AgentProjectionState> states) {
        return completeIfChanged(states, "INITIAL_SUBSCRIBE");
    }
    
    boolean completeIfChanged(Map<AgentProjectionKey, AgentProjectionState> states,
        String trigger) {
        List<String> changedIds = new ArrayList<String>(itemCount);
        for (Map.Entry<AgentProjectionKey, List<Observation>> entry : observations.entrySet()) {
            addChangedIds(entry.getValue(), states.get(entry.getKey()), changedIds);
        }
        return completeChanged(changedIds, trigger);
    }
    
    boolean timeout() {
        AgentWatchBatchResponse response = new AgentWatchBatchResponse();
        response.setGeneration(generation);
        response.setChanged(false);
        boolean result = complete(response);
        if (result) {
            AgentWatchMetrics.record(AgentWatchMetrics.Event.HTTP_LONG_POLL,
                AgentWatchMetrics.Result.TIMEOUT);
            LOGGER.debug("[RAD-WATCH] Server HTTP long poll completed: clientId={}, namespace={}, "
                + "generation={}, result=TIMEOUT, durationMillis={}",
                ownerKey.getClientId(), ownerKey.getNamespaceId(), generation,
                elapsedMillis());
        }
        return result;
    }
    
    boolean cancel() {
        if (!completed.compareAndSet(false, true)) {
            return false;
        }
        cleanup.accept(this);
        AgentWatchMetrics.record(AgentWatchMetrics.Event.HTTP_LONG_POLL,
            AgentWatchMetrics.Result.CANCELED);
        LOGGER.debug("[RAD-WATCH] Server HTTP long poll canceled: clientId={}, namespace={}, "
            + "generation={}, durationMillis={}", ownerKey.getClientId(),
            ownerKey.getNamespaceId(), generation, elapsedMillis());
        return true;
    }
    
    private boolean completeChanged(List<String> changedIds, String trigger) {
        if (changedIds.isEmpty()) {
            return false;
        }
        AgentWatchBatchResponse response = new AgentWatchBatchResponse();
        response.setGeneration(generation);
        response.setChanged(true);
        response.setChangedClientWatchIds(
            Collections.unmodifiableList(new ArrayList<String>(changedIds)));
        boolean result = complete(response);
        if (result) {
            AgentWatchMetrics.record(AgentWatchMetrics.Event.HTTP_LONG_POLL,
                AgentWatchMetrics.Result.CHANGED);
            LOGGER.info("[RAD-WATCH] Server HTTP long poll completed: clientId={}, namespace={}, "
                + "generation={}, result=CHANGED, trigger={}, changedWatchIds={}, "
                + "durationMillis={}", ownerKey.getClientId(), ownerKey.getNamespaceId(),
                generation, trigger, AgentWatchLogUtils.tokens(changedIds), elapsedMillis());
        }
        return result;
    }
    
    private boolean complete(AgentWatchBatchResponse response) {
        if (!completed.compareAndSet(false, true)) {
            return false;
        }
        cleanup.accept(this);
        deferredResult.setResult(Result.success(response));
        return true;
    }
    
    private void addChangedIds(List<Observation> candidates, AgentProjectionState state,
        List<String> changedIds) {
        for (Observation each : candidates) {
            if (state == null || !state.isAvailable()
                || !each.fingerprint.equals(state.getFingerprint())) {
                changedIds.add(each.clientWatchId);
            }
        }
    }
    
    private Map<AgentProjectionKey, List<Observation>> index(List<AgentWatchBatchItem> items) {
        Map<AgentProjectionKey, List<Observation>> result =
            new LinkedHashMap<AgentProjectionKey, List<Observation>>();
        for (AgentWatchBatchItem each : items) {
            AgentProjectionKey key = AgentProjectionKey.of(each.getDiscoveryRequest());
            List<Observation> keyObservations = result.get(key);
            if (keyObservations == null) {
                keyObservations = new ArrayList<Observation>();
                result.put(key, keyObservations);
            }
            keyObservations.add(new Observation(each.getClientWatchId(),
                each.getMaterializedFingerprint()));
        }
        return result;
    }
    
    String getWaiterId() {
        return waiterId;
    }
    
    AgentHttpWatchOwnerKey getOwnerKey() {
        return ownerKey;
    }
    
    long getGeneration() {
        return generation;
    }
    
    int getItemCount() {
        return itemCount;
    }
    
    int getPayloadBytes() {
        return payloadBytes;
    }
    
    Set<AgentProjectionKey> getProjectionKeys() {
        return Collections.unmodifiableSet(observations.keySet());
    }
    
    DeferredResult<Result<AgentWatchBatchResponse>> getDeferredResult() {
        return deferredResult;
    }
    
    boolean isCompleted() {
        return completed.get();
    }
    
    private long elapsedMillis() {
        return java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(
            System.nanoTime() - startedNanos);
    }
    
    private static final class Observation {
        
        private final String clientWatchId;
        
        private final String fingerprint;
        
        Observation(String clientWatchId, String fingerprint) {
            this.clientWatchId = clientWatchId;
            this.fingerprint = fingerprint;
        }
    }
}
