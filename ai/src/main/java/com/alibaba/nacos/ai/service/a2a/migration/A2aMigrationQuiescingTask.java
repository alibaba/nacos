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
import com.alibaba.nacos.common.executor.ExecutorFactory;
import com.alibaba.nacos.common.utils.ThreadFactoryBuilder;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

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
public class A2aMigrationQuiescingTask
    implements ApplicationListener<ApplicationReadyEvent>, DisposableBean {
    
    public static final String QUIESCING_TIMEOUT_SECONDS_PROPERTY =
        "nacos.ai.a2a.migration.quiescing-timeout-seconds";
    
    public static final String QUIESCING_CHECK_INTERVAL_SECONDS_PROPERTY =
        "nacos.ai.a2a.migration.quiescing-check-interval-seconds";
    
    private static final Logger LOGGER =
        LoggerFactory.getLogger(A2aMigrationQuiescingTask.class);
    
    private static final long DEFAULT_QUIESCING_TIMEOUT_SECONDS = 120L;
    
    private static final long DEFAULT_CHECK_INTERVAL_SECONDS = 3L;
    
    private static final int DEFAULT_PAGE_SIZE = 100;
    
    private static final int MAX_PAGE_SIZE = 500;
    
    private final ScheduledExecutorService executor =
        ExecutorFactory.Managed.newSingleScheduledExecutorService(
            A2aMigrationQuiescingTask.class.getCanonicalName(),
            new ThreadFactoryBuilder().daemon(true)
                .nameFormat("nacos-ai-a2a-migration-quiescing-%d").build());
    
    private final A2aMigrationStateService stateService;
    
    private final A2aMigrationDefinitionReadinessValidator definitionValidator;
    
    private final A2aMigrationSearchReadinessGate searchReadinessGate;
    
    private final A2aMigrationRuntimeReadinessGate runtimeReadinessGate;
    
    private final String leaseOwner;
    
    private final AtomicBoolean initialized = new AtomicBoolean(false);
    
    private volatile ScheduledFuture<?> scheduledFuture;
    
    private String locallyVerifiedGeneration;
    
    @Autowired
    public A2aMigrationQuiescingTask(A2aMigrationStateService stateService,
        A2aMigrationDefinitionReadinessValidator definitionValidator,
        A2aMigrationSearchReadinessGate searchReadinessGate,
        A2aMigrationRuntimeReadinessGate runtimeReadinessGate) {
        this(stateService, definitionValidator, searchReadinessGate, runtimeReadinessGate,
            "quiescing-" + stateService.newGeneration());
    }
    
    A2aMigrationQuiescingTask(A2aMigrationStateService stateService,
        A2aMigrationDefinitionReadinessValidator definitionValidator,
        A2aMigrationSearchReadinessGate searchReadinessGate,
        A2aMigrationRuntimeReadinessGate runtimeReadinessGate, String leaseOwner) {
        this.stateService = stateService;
        this.definitionValidator = definitionValidator;
        this.searchReadinessGate = searchReadinessGate;
        this.runtimeReadinessGate = runtimeReadinessGate;
        this.leaseOwner = leaseOwner;
    }
    
    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        if (event.getApplicationContext().getParent() != null
            || !initialized.compareAndSet(false, true)) {
            return;
        }
        A2aMigrationState state = stateService.resolveConfigured();
        if (state == null || A2aMigrationState.CANONICAL == state) {
            return;
        }
        scheduledFuture = executor.scheduleWithFixedDelay(this::safeExecuteCheck, 0L,
            positiveLong(QUIESCING_CHECK_INTERVAL_SECONDS_PROPERTY,
                DEFAULT_CHECK_INTERVAL_SECONDS),
            TimeUnit.SECONDS);
    }
    
    void safeExecuteCheck() {
        try {
            executeCheck();
        } catch (Exception e) {
            LOGGER.error("Historical A2A quiescing check failed", e);
        }
    }
    
    void executeCheck() {
        VersionedValue<A2aMigrationMarker> current = stateService.currentMarker();
        if (current == null || A2aMigrationState.QUIESCING != current.getValue().getState()) {
            clearLocalAck();
            if (current != null
                && A2aMigrationState.CANONICAL == current.getValue().getState()) {
                stopScheduling();
            }
            return;
        }
        A2aMigrationMarker marker = current.getValue();
        A2aMigrationMemberView memberView = stateService.readyMemberView(marker);
        if (memberView == null
            || !A2aMigrationQuiescingGeneration.matches(marker.getGeneration(), memberView)) {
            rollback(current, "member-or-policy-change");
            return;
        }
        if (timedOut(marker)) {
            rollback(current, "timeout");
            return;
        }
        if (!runtimeReadinessGate.isLocalMirrorReady()) {
            clearLocalAck();
            return;
        }
        if (!marker.getGeneration().equals(locallyVerifiedGeneration)) {
            A2aMigrationDefinitionReadinessValidator.Result local =
                definitionValidator.validate(configuredPageSize(), null);
            if (!local.isReady() || !runtimeReadinessGate.isLocalMirrorReady()
                || !stateService.advertiseLocalAck(marker)) {
                clearLocalAck();
                return;
            }
            locallyVerifiedGeneration = marker.getGeneration();
            A2aMigrationMetrics.record(A2aMigrationMetrics.Event.MEMBER_ACK,
                A2aMigrationMetrics.Result.SUCCESS);
            LOGGER.info("Historical A2A migration member is READY: generationHash={}, "
                + "definitions={}", hash(marker.getGeneration()), local.getScanned());
        }
        if (!stateService.allMembersAcknowledged(marker, memberView)) {
            return;
        }
        completeOrRollback(current, memberView);
    }
    
    private void completeOrRollback(VersionedValue<A2aMigrationMarker> expected,
        A2aMigrationMemberView expectedView) {
        A2aMigrationLease lease = stateService.tryAcquireLease(leaseOwner,
            A2aMigrationReconciliationTask.configuredLeaseDurationMillis());
        if (lease == null) {
            return;
        }
        try {
            VersionedValue<A2aMigrationMarker> current = stateService.currentMarker();
            if (!sameQuiescingGeneration(expected, current)) {
                return;
            }
            if (!expectedView.equals(stateService.readyMemberView(current.getValue()))
                || !stateService.allMembersAcknowledged(current.getValue(), expectedView)) {
                rollback(current, "member-or-ack-change");
                return;
            }
            A2aMigrationDefinitionReadinessValidator.Result definitions =
                definitionValidator.validate(configuredPageSize(), lease);
            boolean ready = definitions.isReady()
                && searchReadinessGate.isReady(definitions.getSourceNames())
                && runtimeReadinessGate.isReady();
            if (!ready) {
                rollback(current, "final-gate-difference");
                return;
            }
            if (!lease.renew()) {
                rollback(current, "lease-lost");
                return;
            }
            VersionedValue<A2aMigrationMarker> verified = stateService.currentMarker();
            A2aMigrationMemberView verifiedView = verified == null ? null
                : stateService.readyMemberView(verified.getValue());
            if (!sameQuiescingGeneration(current, verified)
                || !expectedView.equals(verifiedView)
                || !stateService.allMembersAcknowledged(verified.getValue(), expectedView)
                || !runtimeReadinessGate.isReady()) {
                if (sameQuiescingGeneration(current, verified)) {
                    rollback(verified, "final-state-changed");
                }
                return;
            }
            VersionedValue<A2aMigrationMarker> canonical = stateService.transition(verified,
                A2aMigrationState.CANONICAL, verified.getValue().getGeneration());
            if (canonical != null) {
                A2aMigrationMetrics.record(A2aMigrationMetrics.Event.CUTOVER,
                    A2aMigrationMetrics.Result.SUCCESS);
                LOGGER.info("Historical A2A migration is permanently CANONICAL: "
                    + "generationHash={}, members={}, definitions={}",
                    hash(canonical.getValue().getGeneration()), expectedView.getMemberCount(),
                    definitions.getScanned());
                clearLocalAck();
                stopScheduling();
            } else {
                A2aMigrationMetrics.record(A2aMigrationMetrics.Event.CUTOVER,
                    A2aMigrationMetrics.Result.FAILED);
                VersionedValue<A2aMigrationMarker> unresolved = stateService.currentMarker();
                if (sameQuiescingGeneration(verified, unresolved)) {
                    rollback(unresolved, "canonical-cas-conflict");
                }
            }
        } finally {
            lease.close();
        }
    }
    
    private void rollback(VersionedValue<A2aMigrationMarker> expected, String reason) {
        if (expected == null || A2aMigrationState.QUIESCING != expected.getValue().getState()) {
            return;
        }
        VersionedValue<A2aMigrationMarker> syncing = stateService.transition(expected,
            A2aMigrationState.SYNCING, stateService.newGeneration());
        stateService.clearLocalAck();
        locallyVerifiedGeneration = null;
        if (syncing != null) {
            A2aMigrationMetrics.record(A2aMigrationMetrics.Event.ROLLBACK,
                A2aMigrationMetrics.Result.SUCCESS);
            LOGGER.warn("Historical A2A migration returned to SYNCING: reason={}, "
                + "previousGenerationHash={}", reason, hash(expected.getValue().getGeneration()));
        } else {
            A2aMigrationMetrics.record(A2aMigrationMetrics.Event.ROLLBACK,
                A2aMigrationMetrics.Result.FAILED);
        }
    }
    
    private boolean timedOut(A2aMigrationMarker marker) {
        long timeoutMillis = positiveLong(QUIESCING_TIMEOUT_SECONDS_PROPERTY,
            DEFAULT_QUIESCING_TIMEOUT_SECONDS) * 1000L;
        return stateService.currentTimeMillis() - marker.getUpdatedAt() >= timeoutMillis;
    }
    
    private boolean sameQuiescingGeneration(VersionedValue<A2aMigrationMarker> expected,
        VersionedValue<A2aMigrationMarker> actual) {
        return expected != null && actual != null
            && A2aMigrationState.QUIESCING == actual.getValue().getState()
            && expected.getValue().getGeneration().equals(actual.getValue().getGeneration());
    }
    
    private void clearLocalAck() {
        if (locallyVerifiedGeneration != null) {
            stateService.clearLocalAck();
            locallyVerifiedGeneration = null;
        }
    }
    
    private int configuredPageSize() {
        int configured = Integer.parseInt(EnvUtil.getProperty(
            A2aMigrationReconciliationTask.RECONCILIATION_PAGE_SIZE_PROPERTY,
            String.valueOf(DEFAULT_PAGE_SIZE)));
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
    
    private String hash(String value) {
        return Integer.toHexString(value.hashCode());
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
}
