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
import com.alibaba.nacos.common.task.AbstractDelayTask;
import com.alibaba.nacos.common.task.AbstractExecuteTask;
import com.alibaba.nacos.common.task.NacosTask;
import com.alibaba.nacos.common.task.NacosTaskProcessor;
import com.alibaba.nacos.common.task.engine.NacosDelayTaskExecuteEngine;
import com.alibaba.nacos.common.task.engine.NacosExecuteTaskExecuteEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Bounded merge and striped delivery admission for gRPC Agent Watch hints.
 *
 * @author Nacos
 */
final class AgentGrpcWatchTaskEngine {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(AgentGrpcWatchTaskEngine.class);
    
    private static final int INITIAL_CAPACITY = 128;
    
    private static final long PROCESS_INTERVAL_MILLIS = 20L;
    
    private final long retryDelayMillis;
    
    private final DeliveryExecutor deliveryExecutor;
    
    private final WatchDelayEngine delayEngine;
    
    private final NacosExecuteTaskExecuteEngine executeEngine;
    
    private final AtomicBoolean closed = new AtomicBoolean();
    
    AgentGrpcWatchTaskEngine(long retryDelayMillis, int workerCount,
        DeliveryExecutor deliveryExecutor) {
        this(retryDelayMillis, deliveryExecutor,
            new NacosExecuteTaskExecuteEngine("AgentGrpcWatchDelivery", LOGGER, workerCount),
            PROCESS_INTERVAL_MILLIS);
    }
    
    AgentGrpcWatchTaskEngine(long retryDelayMillis, DeliveryExecutor deliveryExecutor,
        NacosExecuteTaskExecuteEngine executeEngine, long processIntervalMillis) {
        this.retryDelayMillis = retryDelayMillis;
        this.deliveryExecutor = deliveryExecutor;
        this.executeEngine = executeEngine;
        delayEngine = new WatchDelayEngine(processIntervalMillis);
    }
    
    void schedule(String watchKey) {
        add(watchKey, 0L);
    }
    
    void retry(String watchKey) {
        add(watchKey, retryDelayMillis);
    }
    
    int pendingTaskCount() {
        return delayEngine.size() + executeEngine.size();
    }
    
    void shutdown() throws NacosException {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        delayEngine.shutdown();
        executeEngine.shutdown();
    }
    
    private void add(String watchKey, long delayMillis) {
        if (!closed.get()) {
            delayEngine.addTask(watchKey, new WatchDelayTask(watchKey, delayMillis));
        }
    }
    
    interface DeliveryExecutor {
        
        void execute(String watchKey);
    }
    
    private class WatchDelayEngine extends NacosDelayTaskExecuteEngine {
        
        WatchDelayEngine(long processIntervalMillis) {
            super("AgentGrpcWatchDelay", INITIAL_CAPACITY, LOGGER, processIntervalMillis);
            setDefaultTaskProcessor(new WatchDelayProcessor());
        }
    }
    
    private class WatchDelayProcessor implements NacosTaskProcessor {
        
        @Override
        public boolean process(NacosTask task) {
            WatchDelayTask delayTask = (WatchDelayTask) task;
            return executeEngine.tryAddTask(delayTask.watchKey,
                new WatchExecuteTask(delayTask.watchKey));
        }
    }
    
    private class WatchExecuteTask extends AbstractExecuteTask {
        
        private final String watchKey;
        
        WatchExecuteTask(String watchKey) {
            this.watchKey = watchKey;
        }
        
        @Override
        public void run() {
            if (closed.get()) {
                return;
            }
            try {
                deliveryExecutor.execute(watchKey);
            } catch (Throwable e) {
                LOGGER.warn("Agent gRPC Watch delivery failed for {}", watchKey, e);
                retry(watchKey);
            }
        }
    }
    
    private static class WatchDelayTask extends AbstractDelayTask {
        
        private final String watchKey;
        
        WatchDelayTask(String watchKey, long delayMillis) {
            this.watchKey = watchKey;
            setTaskInterval(delayMillis);
            setLastProcessTime(System.currentTimeMillis());
        }
        
        @Override
        public void merge(AbstractDelayTask task) {
            if (task instanceof WatchDelayTask) {
                WatchDelayTask previous = (WatchDelayTask) task;
                setLastProcessTime(Math.min(getLastProcessTime(), previous.getLastProcessTime()));
                setTaskInterval(Math.min(getTaskInterval(), previous.getTaskInterval()));
            }
        }
    }
}
