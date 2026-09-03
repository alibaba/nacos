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

import com.alibaba.nacos.ai.service.a2a.migration.A2aMigrationControlStore.VersionedValue;
import com.alibaba.nacos.api.ai.utils.AgentValidationUtils;
import com.alibaba.nacos.common.executor.ExecutorFactory;
import com.alibaba.nacos.common.utils.ThreadFactoryBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Temporary write-after reconciliation queue for Nacos 3.0-3.2 A2A definitions.
 *
 * <p>TODO(remove in 4.0): remove after the historical A2A migration window closes.</p>
 *
 * @author Nacos
 */
@Component
public class A2aMigrationDefinitionHintReconciler implements DisposableBean {
    
    private static final Logger LOGGER =
        LoggerFactory.getLogger(A2aMigrationDefinitionHintReconciler.class);
    
    private static final int DEFAULT_CAPACITY = 1024;
    
    private final A2aHistoricalDefinitionScanner scanner;
    
    private final A2aHistoricalDefinitionReconciler reconciler;
    
    private final A2aMigrationStateService stateService;
    
    private final ExecutorService executor;
    
    private final int capacity;
    
    private final Map<String, HintKey> pending = new LinkedHashMap<String, HintKey>();
    
    private final AtomicBoolean workerScheduled = new AtomicBoolean(false);
    
    @Autowired
    public A2aMigrationDefinitionHintReconciler(A2aHistoricalDefinitionScanner scanner,
        A2aHistoricalDefinitionReconciler reconciler, A2aMigrationStateService stateService) {
        this(scanner, reconciler, stateService,
            ExecutorFactory.Managed.newSingleExecutorService(
                A2aMigrationDefinitionHintReconciler.class.getCanonicalName(),
                new ThreadFactoryBuilder().daemon(true)
                    .nameFormat("nacos-ai-a2a-migration-hint-%d").build()),
            DEFAULT_CAPACITY);
    }
    
    A2aMigrationDefinitionHintReconciler(A2aHistoricalDefinitionScanner scanner,
        A2aHistoricalDefinitionReconciler reconciler, A2aMigrationStateService stateService,
        ExecutorService executor, int capacity) {
        if (capacity < 1) {
            throw new IllegalArgumentException("Historical A2A hint capacity must be positive");
        }
        this.scanner = scanner;
        this.reconciler = reconciler;
        this.stateService = stateService;
        this.executor = executor;
        this.capacity = capacity;
    }
    
    /**
     * Submit one best-effort reconciliation hint after the historical write has succeeded.
     *
     * @param namespaceId namespace identifier
     * @param agentName public Agent name
     * @return whether the hint was accepted or already coalesced
     */
    public boolean submit(String namespaceId, String agentName) {
        AgentValidationUtils.validateNamespaceId(namespaceId);
        AgentValidationUtils.validateAgentName(agentName);
        if (A2aMigrationState.SYNCING != stateService.resolveConfigured()) {
            return false;
        }
        HintKey key = new HintKey(namespaceId, agentName);
        String identity = identity(namespaceId, agentName);
        synchronized (pending) {
            if (pending.containsKey(identity)) {
                return true;
            }
            if (pending.size() >= capacity) {
                LOGGER.warn("Historical A2A write-after hint queue is full: namespaceHash={}, "
                    + "agentHash={}, capacity={}", hash(namespaceId), hash(agentName),
                    capacity);
                return false;
            }
            pending.put(identity, key);
        }
        return scheduleWorker();
    }
    
    private boolean scheduleWorker() {
        if (!workerScheduled.compareAndSet(false, true)) {
            return true;
        }
        try {
            executor.execute(this::drain);
            return true;
        } catch (RuntimeException e) {
            workerScheduled.set(false);
            synchronized (pending) {
                // The scheduling failure may race with later submissions that observed the
                // worker flag. Drop the whole best-effort batch so no unscheduled hint remains
                // stranded; the authoritative periodic scan repairs every dropped identity.
                pending.clear();
            }
            LOGGER.warn("Failed to schedule historical A2A write-after reconciliation", e);
            return false;
        }
    }
    
    private void drain() {
        HintKey key;
        while ((key = poll()) != null) {
            reconcile(key);
        }
    }
    
    private HintKey poll() {
        synchronized (pending) {
            if (pending.isEmpty()) {
                // Keep the empty observation and worker release atomic with submissions. A
                // later submit either remains visible to this worker or starts the next one.
                workerScheduled.set(false);
                return null;
            }
            String identity = pending.keySet().iterator().next();
            return pending.remove(identity);
        }
    }
    
    private void reconcile(HintKey key) {
        if (A2aMigrationState.SYNCING != stateService.resolveConfigured()) {
            return;
        }
        VersionedValue<A2aMigrationMarker> marker = stateService.currentMarker();
        if (marker == null || A2aMigrationState.SYNCING != marker.getValue().getState()
            || !stateService.isLocalPolicyCompatible(marker.getValue())) {
            return;
        }
        try {
            Optional<A2aHistoricalDefinitionSnapshot> snapshot = scanner.scanOne(
                key.namespaceId, key.agentName);
            if (!snapshot.isPresent()) {
                // A write-after delete cannot prove a complete source scan. The periodic
                // reconciliation task performs the required consecutive orphan confirmation.
                return;
            }
            reconciler.reconcile(snapshot.get(), () -> scanner.isCurrent(snapshot.get()));
        } catch (Exception e) {
            LOGGER.warn("Historical A2A write-after reconciliation failed: namespaceHash={}, "
                + "agentHash={}; periodic scan will retry", hash(key.namespaceId),
                hash(key.agentName), e);
        }
    }
    
    int pendingCount() {
        synchronized (pending) {
            return pending.size();
        }
    }
    
    private static String hash(String value) {
        return Integer.toHexString(value.hashCode());
    }
    
    private static String identity(String namespaceId, String agentName) {
        return namespaceId.length() + ":" + namespaceId + agentName;
    }
    
    @Override
    public void destroy() {
        executor.shutdownNow();
    }
    
    private static final class HintKey {
        
        private final String namespaceId;
        
        private final String agentName;
        
        private HintKey(String namespaceId, String agentName) {
            this.namespaceId = namespaceId;
            this.agentName = agentName;
        }
        
    }
}
