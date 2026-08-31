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

import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.core.monitor.NacosMeterRegistryCenter;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.ImmutableTag;
import io.micrometer.core.instrument.Tag;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.DoubleAdder;

/**
 * Low-cardinality Server metrics for RAD Projection and Watch lifecycles.
 *
 * <p>Metric labels are closed enums. Namespace, Agent name, Watch id, Descriptor, Endpoint
 * metadata, and credentials are intentionally excluded.</p>
 *
 * @author Nacos
 */
public final class AgentWatchMetrics {
    
    static final String ACTIVE_METRIC = "nacos_ai_agent_watch_active";
    
    static final String PENDING_METRIC = "nacos_ai_agent_watch_pending";
    
    static final String HTTP_ACTIVE_BYTES_METRIC = "nacos_ai_agent_watch_http_active_bytes";
    
    static final String BYTES_METRIC = "nacos_ai_agent_watch_bytes_total";
    
    static final String RECONCILIATION_LAG_METRIC =
        "nacos_ai_agent_watch_reconciliation_lag_millis";
    
    static final String EVENT_METRIC = "nacos_ai_agent_watch_events_total";
    
    private static final String REGISTRY = NacosMeterRegistryCenter.CORE_STABLE_REGISTRY;
    
    private static final AtomicInteger ACTIVE_PROJECTIONS = new AtomicInteger();
    
    private static final AtomicInteger ACTIVE_GRPC_WATCHES = new AtomicInteger();
    
    private static final AtomicInteger ACTIVE_HTTP_WAITERS = new AtomicInteger();
    
    private static final AtomicInteger PENDING_PROJECTION_TASKS = new AtomicInteger();
    
    private static final AtomicLong HTTP_ACTIVE_BYTES = new AtomicLong();
    
    private static final AtomicLong RECONCILIATION_LAG_MILLIS = new AtomicLong();
    
    private static final DoubleAdder[][] EVENT_COUNTS = createEventCounts();
    
    private static final DoubleAdder[] BYTE_COUNTS = createByteCounts();
    
    static {
        registerGauge(ACTIVE_METRIC, "kind", "projection", ACTIVE_PROJECTIONS);
        registerGauge(ACTIVE_METRIC, "kind", "grpc", ACTIVE_GRPC_WATCHES);
        registerGauge(ACTIVE_METRIC, "kind", "http", ACTIVE_HTTP_WAITERS);
        registerGauge(PENDING_METRIC, "kind", "projection", PENDING_PROJECTION_TASKS);
        registerGauge(HTTP_ACTIVE_BYTES_METRIC, "kind", "http", HTTP_ACTIVE_BYTES);
        registerGauge(RECONCILIATION_LAG_METRIC, "kind", "projection",
            RECONCILIATION_LAG_MILLIS);
    }
    
    private AgentWatchMetrics() {
    }
    
    /**
     * Record delivery of one Agent resource cluster change hint.
     *
     * <p>This narrow facade lets the protocol-neutral AI resource notifier preserve the Agent
     * Watch metric without exposing the remaining Agent Watch metric model.</p>
     *
     * @param success whether delivery succeeded
     */
    public static void recordClusterHint(boolean success) {
        record(Event.CLUSTER_HINT, success ? Result.SUCCESS : Result.FAILED);
    }
    
    static void setActiveProjections(int count) {
        ACTIVE_PROJECTIONS.set(count);
    }
    
    static void setActiveGrpcWatches(int count) {
        ACTIVE_GRPC_WATCHES.set(count);
    }
    
    static void setActiveHttpWaiters(int count, long bytes) {
        ACTIVE_HTTP_WAITERS.set(count);
        HTTP_ACTIVE_BYTES.set(bytes);
    }
    
    static void setPendingProjectionTasks(int count) {
        PENDING_PROJECTION_TASKS.set(count);
    }
    
    static void setReconciliationLagMillis(long millis) {
        RECONCILIATION_LAG_MILLIS.set(Math.max(0L, millis));
    }
    
    static void record(Event event, Result result) {
        EVENT_COUNTS[event.ordinal()][result.ordinal()].add(1D);
        Counter counter = counter(event, result);
        if (counter != null) {
            counter.increment();
        }
    }
    
    static void record(Event event, Result result, double amount) {
        if (amount > 0D) {
            EVENT_COUNTS[event.ordinal()][result.ordinal()].add(amount);
        }
        Counter counter = counter(event, result);
        if (counter != null && amount > 0D) {
            counter.increment(amount);
        }
    }
    
    static void recordBytes(Transport transport, double amount) {
        if (amount <= 0D) {
            return;
        }
        BYTE_COUNTS[transport.ordinal()].add(amount);
        Counter counter = NacosMeterRegistryCenter.counter(REGISTRY, BYTES_METRIC, "transport",
            transport.value);
        if (counter != null) {
            counter.increment(amount);
        }
    }
    
    static void recordJsonBytes(Transport transport, Object payload) {
        try {
            recordBytes(transport, JacksonUtils.toJsonBytes(payload).length);
        } catch (RuntimeException ignored) {
            // Metrics must never change Watch delivery behavior.
        }
    }
    
    static int activeProjections() {
        return ACTIVE_PROJECTIONS.get();
    }
    
    static int activeGrpcWatches() {
        return ACTIVE_GRPC_WATCHES.get();
    }
    
    static int activeHttpWaiters() {
        return ACTIVE_HTTP_WAITERS.get();
    }
    
    static int pendingProjectionTasks() {
        return PENDING_PROJECTION_TASKS.get();
    }
    
    static long httpActiveBytes() {
        return HTTP_ACTIVE_BYTES.get();
    }
    
    static long reconciliationLagMillis() {
        return RECONCILIATION_LAG_MILLIS.get();
    }
    
    static double eventCount(Event event, Result result) {
        return EVENT_COUNTS[event.ordinal()][result.ordinal()].sum();
    }
    
    static double byteCount(Transport transport) {
        return BYTE_COUNTS[transport.ordinal()].sum();
    }
    
    static void resetGaugesForTest() {
        setActiveProjections(0);
        setActiveGrpcWatches(0);
        setActiveHttpWaiters(0, 0L);
        setPendingProjectionTasks(0);
        setReconciliationLagMillis(0L);
    }
    
    private static Counter counter(Event event, Result result) {
        return NacosMeterRegistryCenter.counter(REGISTRY, EVENT_METRIC, "event",
            event.value, "result", result.value);
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
    
    private static DoubleAdder[] createByteCounts() {
        DoubleAdder[] result = new DoubleAdder[Transport.values().length];
        for (int index = 0; index < result.length; index++) {
            result[index] = new DoubleAdder();
        }
        return result;
    }
    
    private static void registerGauge(String name, String tagName, String tagValue,
        Number number) {
        List<Tag> tags = new ArrayList<Tag>(1);
        tags.add(new ImmutableTag(tagName, tagValue));
        NacosMeterRegistryCenter.gauge(REGISTRY, name, tags, number);
    }
    
    enum Event {
        
        PROJECTION_RECOMPUTE,
        
        PROJECTION_COALESCE,
        
        PROJECTION_RETRY,
        
        RECONCILIATION,
        
        GRPC_PUSH,
        
        GRPC_ACK,
        
        HTTP_LONG_POLL,
        
        CAPACITY_REJECTION,
        
        CLUSTER_HINT;
        
        private final String value = name().toLowerCase(Locale.ROOT);
    }
    
    enum Transport {
        
        GRPC,
        
        HTTP;
        
        private final String value = name().toLowerCase(Locale.ROOT);
    }
    
    enum Result {
        
        SUCCESS,
        
        NOT_FOUND,
        
        RETRY,
        
        SCHEDULED,
        
        CHANGED,
        
        TIMEOUT,
        
        CANCELED,
        
        ACCEPTED,
        
        REJECTED,
        
        FAILED;
        
        private final String value = name().toLowerCase(Locale.ROOT);
    }
}
