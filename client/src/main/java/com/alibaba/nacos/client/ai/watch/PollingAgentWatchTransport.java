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
import com.alibaba.nacos.client.utils.LogUtils;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Compatibility Watch transport that emits bounded periodic Discover invalidations.
 *
 * @author Nacos
 */
final class PollingAgentWatchTransport implements AgentWatchTransport {
    
    private static final Logger LOGGER = LogUtils.logger(PollingAgentWatchTransport.class);
    
    private final ScheduledExecutorService executor;
    
    private final long intervalMillis;
    
    private final Map<String, PollingTask> tasks = new HashMap<String, PollingTask>();
    
    private boolean closed;
    
    PollingAgentWatchTransport(ScheduledExecutorService executor, long intervalMillis) {
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
        if (schedule(task) == ScheduleResult.REJECTED) {
            throw new NacosException(NacosException.CLIENT_ERROR,
                "Agent Watch polling scheduling was rejected.");
        }
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
    }
    
    private synchronized ScheduleResult schedule(PollingTask task) {
        if (closed || tasks.get(task.clientWatchId) != task) {
            return ScheduleResult.STALE;
        }
        try {
            task.future = executor.schedule(task, intervalMillis, TimeUnit.MILLISECONDS);
            return ScheduleResult.SCHEDULED;
        } catch (RejectedExecutionException e) {
            tasks.remove(task.clientWatchId);
            LOGGER.warn("Agent Watch polling scheduling was rejected.", e);
            return ScheduleResult.REJECTED;
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
            callback.invalidate(null, true);
            if (schedule(this) == ScheduleResult.REJECTED) {
                callback.unavailable(NacosException.CLIENT_ERROR,
                    "Agent Watch polling scheduling was rejected.", true);
            }
        }
        
        private void cancel() {
            if (future != null) {
                future.cancel(false);
            }
        }
    }
    
    private enum ScheduleResult {
        SCHEDULED,
        STALE,
        REJECTED
    }
}
