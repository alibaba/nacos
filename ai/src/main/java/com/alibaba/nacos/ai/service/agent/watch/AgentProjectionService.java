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
import com.alibaba.nacos.common.executor.ExecutorFactory;
import com.alibaba.nacos.common.executor.NameThreadFactory;
import com.alibaba.nacos.common.utils.ThreadUtils;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.stereotype.Component;

/**
 * Shared Server Projection Core for Agent Watch transports.
 *
 * <p>This component owns only canonical projection state and current-fact scheduling. It does not
 * own connection Watches, HTTP waiters, Owner admission, credentials, or business snapshots.</p>
 *
 * @author Nacos
 */
@Component
public class AgentProjectionService {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(AgentProjectionService.class);
    
    private static final long DEFAULT_CHANGE_DELAY_MILLIS = 100L;
    
    private static final long DEFAULT_RETRY_DELAY_MILLIS = 1000L;
    
    private static final long DEFAULT_RECONCILIATION_INTERVAL_MILLIS = 30000L;
    
    private static final int DEFAULT_RECONCILIATION_BATCH_SIZE = 100;
    
    private final AgentProjectionProjector projector;
    
    private final AgentProjectionRegistry registry;
    
    private final AgentProjectionTaskEngine taskEngine;
    
    private final List<AgentProjectionUpdateListener> updateListeners =
        new CopyOnWriteArrayList<AgentProjectionUpdateListener>();
    
    private final ScheduledExecutorService reconciliationExecutor;
    
    private final int reconciliationBatchSize;
    
    private final AtomicBoolean closed = new AtomicBoolean();
    
    private int reconciliationCursor;
    
    @Autowired
    public AgentProjectionService(AgentProjectionProjector projector) {
        this(projector, new AgentProjectionRegistry(), DEFAULT_CHANGE_DELAY_MILLIS,
            DEFAULT_RETRY_DELAY_MILLIS, DEFAULT_RECONCILIATION_INTERVAL_MILLIS,
            DEFAULT_RECONCILIATION_BATCH_SIZE,
            Math.max(2, ThreadUtils.getSuitableThreadCount(1)));
    }
    
    AgentProjectionService(AgentProjectionProjector projector, AgentProjectionRegistry registry,
        long changeDelayMillis, long retryDelayMillis, long reconciliationIntervalMillis,
        int reconciliationBatchSize, int workerCount) {
        this.projector = projector;
        this.registry = registry;
        this.reconciliationBatchSize = reconciliationBatchSize;
        this.taskEngine = new AgentProjectionTaskEngine(changeDelayMillis, retryDelayMillis,
            workerCount, this::executeProjection);
        if (reconciliationIntervalMillis > 0) {
            reconciliationExecutor = ExecutorFactory.newSingleScheduledExecutorService(
                new NameThreadFactory("AgentProjectionReconciliation"));
            reconciliationExecutor.scheduleWithFixedDelay(this::reconcileBatch,
                reconciliationIntervalMillis, reconciliationIntervalMillis,
                TimeUnit.MILLISECONDS);
        } else {
            reconciliationExecutor = null;
        }
    }
    
    /**
     * Retain one active shared Projection and schedule its initial current-fact computation.
     *
     * @param request Discover request
     * @return canonical shared key
     */
    public AgentProjectionKey retain(
        com.alibaba.nacos.api.ai.model.rad.AgentDiscoveryRequest request) {
        ensureOpen();
        AgentProjectionKey key = AgentProjectionKey.of(request);
        if (registry.retain(key)) {
            taskEngine.markDirty(key, AgentProjectionChangeReason.INITIAL);
        }
        return key;
    }
    
    /**
     * Release one active Projection reference.
     *
     * @param key projection key
     * @return whether the final shared state was removed
     */
    public boolean release(AgentProjectionKey key) {
        return registry.release(key);
    }
    
    /**
     * Synchronously recompute an active Projection from current facts.
     *
     * @param key active projection key
     * @return applied current state
     */
    public AgentProjectionState refreshNow(AgentProjectionKey key) {
        ensureOpen();
        if (!registry.isActive(key)) {
            throw new IllegalStateException("Agent Projection is not active: " + key);
        }
        boolean completed = executeProjection(key,
            Collections.singleton(AgentProjectionChangeReason.INITIAL));
        if (!completed && registry.isActive(key)) {
            taskEngine.retry(key, AgentProjectionChangeReason.RETRY);
        }
        return registry.getState(key)
            .orElseThrow(() -> new IllegalStateException(
                "Agent Projection was released while refreshing: " + key));
    }
    
    public Optional<AgentProjectionState> getState(AgentProjectionKey key) {
        return registry.getState(key);
    }
    
    public int getReferenceCount(AgentProjectionKey key) {
        return registry.getReferenceCount(key);
    }
    
    public int size() {
        return registry.size();
    }
    
    /**
     * Mark every active Projection for one logical Agent dirty.
     *
     * @param namespaceId namespace identifier
     * @param agentName Agent name
     */
    public void onAgentChanged(String namespaceId, String agentName) {
        markDirty(registry.findByAgent(namespaceId, agentName),
            AgentProjectionChangeReason.DEFINITION);
    }
    
    /**
     * Mark every active Projection depending on one physical Naming Service dirty.
     *
     * @param service physical Naming Service
     */
    public void onRuntimeServiceChanged(Service service) {
        markDirty(registry.findByService(service), AgentProjectionChangeReason.RUNTIME);
    }
    
    public void addUpdateListener(AgentProjectionUpdateListener listener) {
        updateListeners.add(listener);
    }
    
    public void removeUpdateListener(AgentProjectionUpdateListener listener) {
        updateListeners.remove(listener);
    }
    
    /**
     * Schedule a current-fact revalidation for one active Projection.
     *
     * @param key active projection key
     */
    public void revalidate(AgentProjectionKey key) {
        if (!closed.get() && registry.isActive(key)) {
            taskEngine.retry(key, AgentProjectionChangeReason.RETRY);
        }
    }
    
    /**
     * Schedule one bounded reconciliation slice over active Projections only.
     */
    void reconcileBatch() {
        if (closed.get()) {
            return;
        }
        List<AgentProjectionKey> keys = registry.activeKeys();
        if (keys.isEmpty()) {
            reconciliationCursor = 0;
            return;
        }
        int count = Math.min(reconciliationBatchSize, keys.size());
        int start = Math.floorMod(reconciliationCursor, keys.size());
        for (int offset = 0; offset < count; offset++) {
            AgentProjectionKey key = keys.get((start + offset) % keys.size());
            taskEngine.markDirty(key, AgentProjectionChangeReason.RECONCILIATION);
        }
        reconciliationCursor = (start + count) % keys.size();
    }
    
    int pendingTaskCount() {
        return taskEngine.pendingDelayTaskCount();
    }
    
    /**
     * Stop projection scheduling and release its internal executors.
     *
     * @throws NacosException when an executor cannot be stopped
     */
    @PreDestroy
    public void shutdown() throws NacosException {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        if (reconciliationExecutor != null) {
            reconciliationExecutor.shutdownNow();
        }
        taskEngine.shutdown();
        updateListeners.clear();
    }
    
    private boolean executeProjection(AgentProjectionKey key,
        Set<AgentProjectionChangeReason> reasons) {
        if (!registry.isActive(key)) {
            return true;
        }
        AgentProjectionState computed = projector.project(key);
        Optional<AgentProjectionUpdate> applied = registry.apply(key, computed, reasons);
        if (!applied.isPresent()) {
            return true;
        }
        notifyUpdate(applied.get());
        return !applied.get().getCurrent().requiresRetry();
    }
    
    private void notifyUpdate(AgentProjectionUpdate update) {
        for (AgentProjectionUpdateListener listener : updateListeners) {
            try {
                listener.onProjectionUpdate(update);
            } catch (RuntimeException e) {
                LOGGER.warn("Agent Projection listener failed for {}", update.getKey(), e);
            }
        }
    }
    
    private void markDirty(Set<AgentProjectionKey> keys,
        AgentProjectionChangeReason reason) {
        for (AgentProjectionKey key : keys) {
            taskEngine.markDirty(key, reason);
        }
    }
    
    private void ensureOpen() {
        if (closed.get()) {
            throw new IllegalStateException("Agent Projection service is closed");
        }
    }
}
