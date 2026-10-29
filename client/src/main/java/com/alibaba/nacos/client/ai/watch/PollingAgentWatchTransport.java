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

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.ai.utils.AgentWatchLogUtils;
import com.alibaba.nacos.client.utils.LogUtils;
import com.alibaba.nacos.common.utils.LogRateLimiter;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;

/**
 * Compatibility Watch transport that emits bounded periodic Discover invalidations.
 *
 * <p>This is a client-side timer, not HTTP long polling. It does not call a Wire Watch
 * endpoint or choose Discover's transport. The manager performs ordinary Discover through
 * its query proxy after each tick; routing can still use HTTP or gRPC.</p>
 *
 * <p>{@link AgentWatchTransportRouter} selects this fallback when the configured server
 * Watch binding is unavailable, or a binding failure permits fallback. For example, a
 * gRPC-only client can still Discover when the server advertises RAD support but disables
 * its Watch ability. The standard SDK wiring uses a ten-second interval. Discover support
 * is still required; polling does not make a server without RAD compatible.</p>
 *
 * <p>The timer is an alternative owner, not a permanent companion to gRPC Watch. A later
 * gRPC availability event may upgrade the binding. The router stops this timer after the
 * new binding starts; cancellation prevents further ticks but does not interrupt a refresh
 * already triggered through the manager. Brief overlap during handover is therefore
 * possible, with both signals sharing the same refresh and fingerprint comparison.</p>
 *
 * @author Nacos
 */
final class PollingAgentWatchTransport implements AgentWatchTransport {
    
    private static final Logger LOGGER = LogUtils.logger(PollingAgentWatchTransport.class);
    
    private static final LogRateLimiter WARN_LOG_LIMITER = new LogRateLimiter(60000L);
    
    private final ScheduledExecutorService executor;
    
    private final long intervalMillis;
    
    private final AgentWatchTaskScheduler taskScheduler;
    
    private final Map<String, PollingTask> tasks = new HashMap<String, PollingTask>();
    
    private boolean closed;
    
    PollingAgentWatchTransport(ScheduledExecutorService executor, long intervalMillis) {
        this(executor, intervalMillis, new AgentWatchTaskScheduler());
    }
    
    PollingAgentWatchTransport(ScheduledExecutorService executor, long intervalMillis,
        AgentWatchTaskScheduler taskScheduler) {
        this.taskScheduler = taskScheduler;
        this.executor = executor;
        this.intervalMillis = intervalMillis;
    }
    
    @Override
    public synchronized void start(AgentWatchRegistration registration,
        AgentWatchTransportCallback callback) throws NacosException {
        if (closed) {
            throw new NacosException(NacosException.CLIENT_DISCONNECT,
                "Agent Watch polling transport has been shut down.");
        }
        if (tasks.containsKey(registration.getClientWatchId())) {
            return;
        }
        PollingTask task = new PollingTask(registration.getClientWatchId(), callback);
        tasks.put(task.clientWatchId, task);
        schedule(task);
    }
    
    @Override
    public void update(AgentWatchRegistration registration) {
        // Periodic polling carries no materialized state.
    }
    
    @Override
    public synchronized void stop(String clientWatchId) {
        PollingTask removed = tasks.remove(clientWatchId);
        if (removed != null) {
            removed.cancel();
        }
    }
    
    @Override
    public synchronized void shutdown() {
        if (closed) {
            return;
        }
        closed = true;
        for (PollingTask task : tasks.values()) {
            task.cancel();
        }
        tasks.clear();
        taskScheduler.shutdown();
    }
    
    private synchronized void schedule(PollingTask task) {
        if (!closed && tasks.get(task.clientWatchId) == task) {
            task.future = taskScheduler.schedule(executor, task, intervalMillis);
        }
    }
    
    private synchronized boolean isActive(PollingTask task) {
        return !closed && tasks.get(task.clientWatchId) == task;
    }
    
    private final class PollingTask implements Runnable {
        
        private final String clientWatchId;
        
        private final AgentWatchTransportCallback callback;
        
        private ScheduledFuture<?> future;
        
        private PollingTask(String clientWatchId, AgentWatchTransportCallback callback) {
            this.clientWatchId = clientWatchId;
            this.callback = callback;
        }
        
        @Override
        public void run() {
            if (!isActive(this)) {
                return;
            }
            try {
                callback.invalidate(null, true);
            } catch (RuntimeException e) {
                if (WARN_LOG_LIMITER.tryAcquire()) {
                    LOGGER.warn("[RAD-WATCH] Polling invalidation failed: clientWatchId={}, "
                        + "retryDelayMillis={}", AgentWatchLogUtils.token(clientWatchId),
                        intervalMillis, e);
                }
            } finally {
                schedule(this);
            }
        }
        
        private void cancel() {
            if (future != null) {
                future.cancel(false);
            }
        }
    }
    
}
