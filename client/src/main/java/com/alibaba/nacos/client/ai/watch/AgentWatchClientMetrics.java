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

import com.alibaba.nacos.client.monitor.MetricsMonitor;

import java.util.Locale;

/**
 * Process-wide low-cardinality metrics for Agent Watch client state.
 *
 * <p>All labels are closed enums. Client id, namespace, Agent name, Watch id, and business
 * content are intentionally excluded.</p>
 *
 * @author Nacos
 */
final class AgentWatchClientMetrics {
    
    private AgentWatchClientMetrics() {
    }
    
    static void intentAdded() {
        MetricsMonitor.agentWatchIntentCount().increment();
    }
    
    static void intentRemoved() {
        MetricsMonitor.agentWatchIntentCount().decrement();
    }
    
    static void pendingAdded() {
        MetricsMonitor.agentWatchPendingCount().increment();
    }
    
    static void pendingRemoved() {
        MetricsMonitor.agentWatchPendingCount().decrement();
    }
    
    static void dirtyAdded() {
        MetricsMonitor.agentWatchDirtyCount().increment();
    }
    
    static void dirtyRemoved() {
        MetricsMonitor.agentWatchDirtyCount().decrement();
    }
    
    static void record(Event event, Result result) {
        MetricsMonitor.recordAgentWatchEvent(event.value, result.value);
    }
    
    static double intentCount() {
        return MetricsMonitor.agentWatchIntentCount().get();
    }
    
    static double pendingCount() {
        return MetricsMonitor.agentWatchPendingCount().get();
    }
    
    static double dirtyCount() {
        return MetricsMonitor.agentWatchDirtyCount().get();
    }
    
    static double eventCount(Event event, Result result) {
        return MetricsMonitor.getAgentWatchEventCount(event.value, result.value);
    }
    
    enum Event {
        
        DISCOVER_REFRESH,
        
        FINGERPRINT_MISMATCH,
        
        RETRY,
        
        CAPACITY_REJECTION,
        
        LISTENER_CALLBACK;
        
        private final String value = name().toLowerCase(Locale.ROOT);
    }
    
    enum Result {
        
        SUCCESS,
        
        UNCHANGED,
        
        MISMATCH,
        
        SCHEDULED,
        
        REJECTED,
        
        FAILED;
        
        private final String value = name().toLowerCase(Locale.ROOT);
    }
}
