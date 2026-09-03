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

import com.alibaba.nacos.core.monitor.NacosMeterRegistryCenter;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.ImmutableTag;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.DoubleAdder;

/**
 * Temporary low-cardinality observability for migrating Nacos 3.0-3.2 A2A data.
 *
 * <p>TODO(remove in 4.0): remove after the historical A2A migration window closes.</p>
 *
 * <p>All labels are closed enums. Resource names, Namespaces, Client ids, AgentCards, and
 * Endpoint metadata are deliberately excluded.</p>
 *
 * @author Nacos
 */
public final class A2aMigrationMetrics {
    
    static final String STATE_METRIC = "nacos_ai_a2a_migration_state";
    
    static final String PENDING_METRIC = "nacos_ai_a2a_migration_pending";
    
    static final String EVENT_METRIC = "nacos_ai_a2a_migration_events_total";
    
    static final String RECONCILIATION_ITEM_METRIC =
        "nacos_ai_a2a_migration_reconciliation_items_total";
    
    static final String RECONCILIATION_DURATION_METRIC =
        "nacos_ai_a2a_migration_reconciliation_seconds";
    
    static final String ENDPOINT_WRITE_METRIC =
        "nacos_ai_a2a_migration_endpoint_write_seconds";
    
    private static final String REGISTRY = NacosMeterRegistryCenter.CORE_STABLE_REGISTRY;
    
    private static final AtomicInteger[] STATES = createStates();
    
    private static final AtomicInteger PENDING_ENDPOINT_RETRIES = new AtomicInteger();
    
    private static final DoubleAdder[][] EVENT_COUNTS = createEventCounts();
    
    private static final DoubleAdder[] RECONCILIATION_ITEM_COUNTS =
        createReconciliationItemCounts();
    
    private static final DoubleAdder[][][][] ENDPOINT_WRITE_COUNTS =
        createEndpointWriteCounts();
    
    static {
        for (State state : State.values()) {
            registerGauge(STATE_METRIC, "state", state.value, STATES[state.ordinal()]);
        }
        registerGauge(PENDING_METRIC, "kind", "endpoint_retry", PENDING_ENDPOINT_RETRIES);
    }
    
    private A2aMigrationMetrics() {
    }
    
    static void setState(A2aMigrationState state) {
        for (AtomicInteger each : STATES) {
            each.set(0);
        }
        State metricState = state == null ? State.INACTIVE
            : State.valueOf(state.name());
        STATES[metricState.ordinal()].set(1);
    }
    
    static void setPendingEndpointRetries(int count) {
        PENDING_ENDPOINT_RETRIES.set(Math.max(0, count));
    }
    
    static void adjustPendingEndpointRetries(int delta) {
        PENDING_ENDPOINT_RETRIES.updateAndGet(current -> Math.max(0, current + delta));
    }
    
    static void record(Event event, Result result) {
        record(event, result, 1D);
    }
    
    static void record(Event event, Result result, double amount) {
        if (amount <= 0D) {
            return;
        }
        EVENT_COUNTS[event.ordinal()][result.ordinal()].add(amount);
        try {
            Counter counter = NacosMeterRegistryCenter.counter(REGISTRY, EVENT_METRIC, "event",
                event.value, "result", result.value);
            if (counter != null) {
                counter.increment(amount);
            }
        } catch (RuntimeException ignored) {
            // Metrics must never change migration availability or authority.
        }
    }
    
    static void recordReconciliation(long scanned, long migrated, long conflicts, long failed,
        long durationNanos) {
        recordReconciliationItems(ReconciliationItem.SCANNED, scanned);
        recordReconciliationItems(ReconciliationItem.MIGRATED, migrated);
        recordReconciliationItems(ReconciliationItem.CONFLICT, conflicts);
        recordReconciliationItems(ReconciliationItem.FAILED, failed);
        Result result = failed > 0 ? Result.FAILED
            : conflicts > 0 ? Result.BLOCKED : Result.SUCCESS;
        record(Event.RECONCILIATION, result);
        recordReconciliationTimer(result, durationNanos);
    }
    
    static void recordEndpointWrite(Role role, Target target, Operation operation,
        Result result, long durationNanos) {
        ENDPOINT_WRITE_COUNTS[role.ordinal()][target.ordinal()][operation.ordinal()][result
            .ordinal()].add(1D);
        try {
            Timer timer = NacosMeterRegistryCenter.timer(REGISTRY, ENDPOINT_WRITE_METRIC,
                "role", role.value, "target", target.value, "operation", operation.value,
                "result", result.value);
            if (timer != null) {
                timer.record(Math.max(0L, durationNanos), TimeUnit.NANOSECONDS);
            }
        } catch (RuntimeException ignored) {
            // Metrics must never change Endpoint publication behavior.
        }
    }
    
    static int stateValue(State state) {
        return STATES[state.ordinal()].get();
    }
    
    static int pendingEndpointRetries() {
        return PENDING_ENDPOINT_RETRIES.get();
    }
    
    static double eventCount(Event event, Result result) {
        return EVENT_COUNTS[event.ordinal()][result.ordinal()].sum();
    }
    
    static double reconciliationItemCount(ReconciliationItem item) {
        return RECONCILIATION_ITEM_COUNTS[item.ordinal()].sum();
    }
    
    static double endpointWriteCount(Role role, Target target, Operation operation,
        Result result) {
        return ENDPOINT_WRITE_COUNTS[role.ordinal()][target.ordinal()][operation.ordinal()][result
            .ordinal()].sum();
    }
    
    static void resetGaugesForTest() {
        setState(null);
        setPendingEndpointRetries(0);
    }
    
    private static void recordReconciliationItems(ReconciliationItem item, double amount) {
        if (amount <= 0D) {
            return;
        }
        RECONCILIATION_ITEM_COUNTS[item.ordinal()].add(amount);
        try {
            Counter counter = NacosMeterRegistryCenter.counter(REGISTRY,
                RECONCILIATION_ITEM_METRIC, "result", item.value);
            if (counter != null) {
                counter.increment(amount);
            }
        } catch (RuntimeException ignored) {
            // Metrics must never change reconciliation behavior.
        }
    }
    
    private static void recordReconciliationTimer(Result result, long durationNanos) {
        try {
            Timer timer = NacosMeterRegistryCenter.timer(REGISTRY,
                RECONCILIATION_DURATION_METRIC, "result", result.value);
            if (timer != null) {
                timer.record(Math.max(0L, durationNanos), TimeUnit.NANOSECONDS);
            }
        } catch (RuntimeException ignored) {
            // Metrics must never change reconciliation behavior.
        }
    }
    
    private static AtomicInteger[] createStates() {
        AtomicInteger[] result = new AtomicInteger[State.values().length];
        for (int index = 0; index < result.length; index++) {
            result[index] = new AtomicInteger();
        }
        return result;
    }
    
    private static DoubleAdder[][] createEventCounts() {
        DoubleAdder[][] result = new DoubleAdder[Event.values().length][Result.values().length];
        for (int event = 0; event < result.length; event++) {
            for (int outcome = 0; outcome < result[event].length; outcome++) {
                result[event][outcome] = new DoubleAdder();
            }
        }
        return result;
    }
    
    private static DoubleAdder[] createReconciliationItemCounts() {
        DoubleAdder[] result = new DoubleAdder[ReconciliationItem.values().length];
        for (int index = 0; index < result.length; index++) {
            result[index] = new DoubleAdder();
        }
        return result;
    }
    
    private static DoubleAdder[][][][] createEndpointWriteCounts() {
        int roles = Role.values().length;
        int targets = Target.values().length;
        int operations = Operation.values().length;
        int outcomes = Result.values().length;
        DoubleAdder[][][][] result = new DoubleAdder[roles][targets][operations][outcomes];
        for (int role = 0; role < result.length; role++) {
            for (int target = 0; target < result[role].length; target++) {
                for (int operation = 0; operation < result[role][target].length; operation++) {
                    for (int outcome =
                        0; outcome < result[role][target][operation].length; outcome++) {
                        result[role][target][operation][outcome] = new DoubleAdder();
                    }
                }
            }
        }
        return result;
    }
    
    private static void registerGauge(String name, String tagName, String tagValue,
        Number number) {
        try {
            List<Tag> tags = new ArrayList<Tag>(1);
            tags.add(new ImmutableTag(tagName, tagValue));
            NacosMeterRegistryCenter.gauge(REGISTRY, name, tags, number);
        } catch (RuntimeException ignored) {
            // Metrics initialization must never prevent server startup.
        }
    }
    
    enum State {
        
        INACTIVE,
        
        SYNCING,
        
        QUIESCING,
        
        CANONICAL;
        
        private final String value = name().toLowerCase(Locale.ROOT);
    }
    
    enum Event {
        
        RECONCILIATION,
        
        CUTOVER_GATE,
        
        ENTER_QUIESCING,
        
        MEMBER_ACK,
        
        CUTOVER,
        
        ROLLBACK,
        
        ENDPOINT_RETRY;
        
        private final String value = name().toLowerCase(Locale.ROOT);
    }
    
    enum Result {
        
        SUCCESS,
        
        FAILED,
        
        BLOCKED,
        
        SCHEDULED;
        
        private final String value = name().toLowerCase(Locale.ROOT);
    }
    
    enum ReconciliationItem {
        
        SCANNED,
        
        MIGRATED,
        
        CONFLICT,
        
        FAILED;
        
        private final String value = name().toLowerCase(Locale.ROOT);
    }
    
    enum Role {
        
        PRIMARY,
        
        SECONDARY,
        
        RETRY;
        
        private final String value = name().toLowerCase(Locale.ROOT);
    }
    
    enum Target {
        
        LEGACY,
        
        CANONICAL;
        
        private final String value = name().toLowerCase(Locale.ROOT);
    }
    
    enum Operation {
        
        REGISTER,
        
        DEREGISTER;
        
        private final String value = name().toLowerCase(Locale.ROOT);
    }
}
