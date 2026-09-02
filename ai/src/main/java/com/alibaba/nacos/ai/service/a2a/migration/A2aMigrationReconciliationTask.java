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

import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.api.model.response.Namespace;
import com.alibaba.nacos.ai.service.a2a.migration.A2aMigrationControlStore.VersionedValue;
import com.alibaba.nacos.common.executor.ExecutorFactory;
import com.alibaba.nacos.common.utils.ThreadFactoryBuilder;
import com.alibaba.nacos.core.service.NamespaceOperationService;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Temporary compatibility support for migrating Nacos 3.0-3.2 A2A data.
 *
 * <p>TODO(remove in 4.0): remove after the historical A2A migration window closes.</p>
 *
 * @author Nacos
 */
@Component
public class A2aMigrationReconciliationTask
    implements ApplicationListener<ApplicationReadyEvent>, DisposableBean {
    
    public static final String RECONCILIATION_INTERVAL_SECONDS_PROPERTY =
        "nacos.ai.a2a.migration.reconciliation.interval-seconds";
    
    public static final String RECONCILIATION_PAGE_SIZE_PROPERTY =
        "nacos.ai.a2a.migration.reconciliation.page-size";
    
    public static final String LEASE_DURATION_SECONDS_PROPERTY =
        "nacos.ai.a2a.migration.lease-duration-seconds";
    
    private static final Logger LOGGER =
        LoggerFactory.getLogger(A2aMigrationReconciliationTask.class);
    
    private static final long DEFAULT_INTERVAL_SECONDS = 300L;
    
    private static final int DEFAULT_PAGE_SIZE = 100;
    
    private static final int MAX_PAGE_SIZE = 500;
    
    private static final long DEFAULT_LEASE_DURATION_SECONDS = 10 * 60L;
    
    private final ScheduledExecutorService executor =
        ExecutorFactory.Managed.newSingleScheduledExecutorService(
            A2aMigrationReconciliationTask.class.getCanonicalName(),
            new ThreadFactoryBuilder().daemon(true)
                .nameFormat("nacos-ai-a2a-migration-reconcile-%d").build());
    
    private final NamespaceOperationService namespaceOperationService;
    
    private final A2aHistoricalDefinitionScanner scanner;
    
    private final A2aHistoricalDefinitionReconciler reconciler;
    
    private final A2aMigrationTargetStore targetStore;
    
    private final A2aMigrationStateService stateService;
    
    private final A2aMigrationCutoverCoordinator cutoverCoordinator;
    
    private final String leaseOwner = UUID.randomUUID().toString();
    
    private final Map<String, Integer> orphanConfirmations = new HashMap<String, Integer>();
    
    private final AtomicBoolean initialized = new AtomicBoolean(false);
    
    private volatile ScheduledFuture<?> scheduledFuture;
    
    public A2aMigrationReconciliationTask(NamespaceOperationService namespaceOperationService,
        A2aHistoricalDefinitionScanner scanner,
        A2aHistoricalDefinitionReconciler reconciler,
        A2aMigrationTargetStore targetStore,
        A2aMigrationStateService stateService,
        A2aMigrationCutoverCoordinator cutoverCoordinator) {
        this.namespaceOperationService = namespaceOperationService;
        this.scanner = scanner;
        this.reconciler = reconciler;
        this.targetStore = targetStore;
        this.stateService = stateService;
        this.cutoverCoordinator = cutoverCoordinator;
    }
    
    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        if (event.getApplicationContext().getParent() != null
            || !initialized.compareAndSet(false, true)) {
            return;
        }
        A2aMigrationState state = stateService.resolveConfigured();
        if (state == null || A2aMigrationState.CANONICAL == state) {
            LOGGER.info("Historical A2A reconciliation is inactive in state {}", state);
            return;
        }
        long interval = positiveLong(RECONCILIATION_INTERVAL_SECONDS_PROPERTY,
            DEFAULT_INTERVAL_SECONDS);
        scheduledFuture = executor.scheduleWithFixedDelay(this::safeExecuteReconciliation, 0L,
            interval, TimeUnit.SECONDS);
    }
    
    void safeExecuteReconciliation() {
        try {
            executeReconciliation();
        } catch (Exception e) {
            // TODO(remove in 4.0): a transient control-store failure must not cancel the
            // temporary periodic migration task permanently.
            LOGGER.error("Historical A2A reconciliation cycle failed before scanning", e);
        }
    }
    
    void executeReconciliation() {
        A2aMigrationState state = stateService.resolveConfigured();
        if (A2aMigrationState.CANONICAL == state) {
            stopScheduling();
            return;
        }
        if (A2aMigrationState.SYNCING != state) {
            return;
        }
        VersionedValue<A2aMigrationMarker> marker = stateService.currentMarker();
        if (marker == null || A2aMigrationState.SYNCING != marker.getValue().getState()) {
            return;
        }
        A2aMigrationLease lease = stateService.tryAcquireLease(leaseOwner,
            configuredLeaseDurationMillis());
        if (lease == null) {
            LOGGER.debug("Skip historical A2A reconciliation because another node owns lease");
            return;
        }
        ScanStats stats = new ScanStats();
        long startedAt = System.nanoTime();
        stats.generation = UUID.randomUUID().toString();
        stats.lease = lease;
        try {
            List<Namespace> namespaces = namespaceOperationService.getNamespaceList();
            if (namespaces == null) {
                throw new IllegalStateException("Namespace listing is unavailable");
            }
            int pageSize = configuredPageSize();
            Map<String, Set<String>> sourceNames = new HashMap<String, Set<String>>();
            boolean sourceScanComplete = true;
            for (Namespace namespace : namespaces) {
                lease.assertOwned();
                if (!lease.renew()) {
                    throw new IllegalStateException("Historical A2A reconciliation lease lost");
                }
                String namespaceId = namespace.getNamespace();
                try {
                    Set<String> names = reconcileNamespace(namespaceId, pageSize, stats);
                    sourceNames.put(namespaceId, names);
                } catch (Exception e) {
                    sourceScanComplete = false;
                    stats.recordFailure(e);
                    clearOrphanConfirmations(namespaceId);
                    LOGGER.warn("Failed to scan historical A2A namespace {}", namespaceId, e);
                }
            }
            if (sourceScanComplete) {
                reconcileOrphans(sourceNames, stats);
            }
            stats.sourceScanComplete = sourceScanComplete;
            cutoverCoordinator.afterSyncingRound(marker, lease, stats.isZeroDifference(),
                sourceNames, pageSize);
        } catch (Exception e) {
            stats.recordFailure(e);
            LOGGER.error("Historical A2A reconciliation failed", e);
        } finally {
            persistProgress(stats);
            lease.close();
            A2aMigrationMetrics.recordReconciliation(stats.scanned, stats.migrated,
                stats.conflicts, stats.failed, System.nanoTime() - startedAt);
            LOGGER.info("Historical A2A reconciliation completed: generation={}, scanned={}, "
                + "migrated={}, conflicts={}, failed={}", stats.generation, stats.scanned,
                stats.migrated, stats.conflicts, stats.failed);
        }
    }
    
    private Set<String> reconcileNamespace(String namespaceId, int pageSize, ScanStats stats) {
        Set<String> sourceNames = new HashSet<String>();
        int pageNo = 1;
        int pages = 1;
        while (pageNo <= pages) {
            stats.lease.assertOwned();
            if (!stats.lease.renew()) {
                throw new IllegalStateException("Historical A2A reconciliation lease lost");
            }
            Page<A2aHistoricalDefinitionSnapshot> page = scanner.scanPage(namespaceId, pageNo,
                pageSize);
            pages = page.getPagesAvailable();
            if (pages == 0 && page.getTotalCount() > 0) {
                pages = (int) Math.ceil((double) page.getTotalCount() / pageSize);
            }
            for (A2aHistoricalDefinitionSnapshot snapshot : page.getPageItems()) {
                stats.scanned++;
                sourceNames.add(snapshot.getSummary().getName());
                try {
                    A2aMigrationTargetStore.Result result = reconciler.reconcile(snapshot,
                        () -> scanner.isCurrent(snapshot));
                    if (A2aMigrationTargetStore.Result.CREATED == result
                        || A2aMigrationTargetStore.Result.REPAIRED == result) {
                        stats.migrated++;
                    }
                } catch (NacosApiException e) {
                    stats.conflicts++;
                    stats.lastError = e.getMessage();
                    LOGGER.warn("Historical A2A target conflict for namespace={}, agentHash={}",
                        namespaceId,
                        Integer.toHexString(snapshot.getSummary().getName().hashCode()),
                        e);
                } catch (Exception e) {
                    stats.recordFailure(e);
                    LOGGER.warn("Failed to reconcile historical A2A namespace={}, agentHash={}",
                        namespaceId,
                        Integer.toHexString(snapshot.getSummary().getName().hashCode()),
                        e);
                }
            }
            pageNo++;
        }
        return sourceNames;
    }
    
    private void reconcileOrphans(Map<String, Set<String>> sourceNames, ScanStats stats)
        throws Exception {
        Set<String> observedKeys = new HashSet<String>();
        for (Map.Entry<String, Set<String>> namespace : sourceNames.entrySet()) {
            Set<String> migratedNames = targetStore.listMigratedAgentNames(namespace.getKey());
            for (String migratedName : migratedNames) {
                String key = orphanKey(namespace.getKey(), migratedName);
                observedKeys.add(key);
                if (namespace.getValue().contains(migratedName)) {
                    orphanConfirmations.remove(key);
                    continue;
                }
                int confirmations = orphanConfirmations.getOrDefault(key, 0) + 1;
                if (confirmations >= 2) {
                    if (targetStore.deleteConfirmedOrphan(namespace.getKey(), migratedName)) {
                        stats.migrated++;
                    } else {
                        stats.pendingDeletes = true;
                    }
                    orphanConfirmations.remove(key);
                } else {
                    orphanConfirmations.put(key, confirmations);
                    stats.pendingDeletes = true;
                }
            }
        }
        orphanConfirmations.keySet().retainAll(observedKeys);
    }
    
    private void persistProgress(ScanStats stats) {
        A2aMigrationProgress progress = new A2aMigrationProgress();
        progress.setState(A2aMigrationState.SYNCING);
        progress.setGeneration(stats.generation);
        progress.setUpdatedAt(System.currentTimeMillis());
        progress.setScanned(stats.scanned);
        progress.setMigrated(stats.migrated);
        progress.setConflicts(stats.conflicts);
        progress.setFailed(stats.failed);
        progress.setLastError(stats.lastError);
        stateService.persistProgress(progress);
    }
    
    private void clearOrphanConfirmations(String namespaceId) {
        List<String> keys = new ArrayList<String>(orphanConfirmations.keySet());
        String prefix = namespaceId + '\u0000';
        for (String key : keys) {
            if (key.startsWith(prefix)) {
                orphanConfirmations.remove(key);
            }
        }
    }
    
    private String orphanKey(String namespaceId, String agentName) {
        return namespaceId + '\u0000' + agentName;
    }
    
    private int configuredPageSize() {
        int configured = Integer.parseInt(EnvUtil.getProperty(
            RECONCILIATION_PAGE_SIZE_PROPERTY, String.valueOf(DEFAULT_PAGE_SIZE)));
        if (configured < 1) {
            throw new IllegalArgumentException(
                "Historical A2A migration page size must be positive");
        }
        return Math.min(configured, MAX_PAGE_SIZE);
    }
    
    private long positiveLong(String key, long defaultValue) {
        long configured = Long.parseLong(EnvUtil.getProperty(key, String.valueOf(defaultValue)));
        if (configured < 1) {
            throw new IllegalArgumentException(key + " must be positive");
        }
        return configured;
    }
    
    static long configuredLeaseDurationMillis() {
        long configured = Long.parseLong(EnvUtil.getProperty(LEASE_DURATION_SECONDS_PROPERTY,
            String.valueOf(DEFAULT_LEASE_DURATION_SECONDS)));
        if (configured < 1 || configured > Long.MAX_VALUE / 1000L) {
            throw new IllegalArgumentException(
                LEASE_DURATION_SECONDS_PROPERTY + " must be a positive number of seconds");
        }
        return configured * 1000L;
    }
    
    @Override
    public void destroy() {
        executor.shutdownNow();
    }
    
    private void stopScheduling() {
        ScheduledFuture<?> future = scheduledFuture;
        if (future != null) {
            future.cancel(false);
        }
    }
    
    private static final class ScanStats {
        
        private String generation;
        
        private long scanned;
        
        private long migrated;
        
        private long conflicts;
        
        private long failed;
        
        private String lastError;
        
        private boolean sourceScanComplete;
        
        private boolean pendingDeletes;
        
        private A2aMigrationLease lease;
        
        private boolean isZeroDifference() {
            return sourceScanComplete && !pendingDeletes && migrated == 0 && conflicts == 0
                && failed == 0;
        }
        
        private void recordFailure(Exception e) {
            failed++;
            lastError = e.getMessage();
        }
    }
}
