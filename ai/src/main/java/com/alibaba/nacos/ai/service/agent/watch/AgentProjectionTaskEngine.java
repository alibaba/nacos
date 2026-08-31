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
import com.alibaba.nacos.common.utils.LogRateLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Delay/merge and striped current-fact execution for Agent Projections.
 *
 * @author Nacos
 */
final class AgentProjectionTaskEngine {
    
    private static final Logger LOGGER =
        LoggerFactory.getLogger(AgentProjectionTaskEngine.class);
    
    private static final LogRateLimiter WARN_LOG_LIMITER = new LogRateLimiter(60000L);
    
    private static final int INITIAL_CAPACITY = 128;
    
    private static final long PROCESS_INTERVAL_MILLIS = 20L;
    
    private final long changeDelayMillis;
    
    private final long retryDelayMillis;
    
    private final ProjectionExecutor projectionExecutor;
    
    private final ProjectionDelayEngine delayEngine;
    
    private final NacosExecuteTaskExecuteEngine executeEngine;
    
    private final AtomicBoolean closed = new AtomicBoolean();
    
    AgentProjectionTaskEngine(long changeDelayMillis, long retryDelayMillis, int workerCount,
        ProjectionExecutor projectionExecutor) {
        this(changeDelayMillis, retryDelayMillis, projectionExecutor,
            new NacosExecuteTaskExecuteEngine("AgentProjectionExecute", LOGGER, workerCount),
            PROCESS_INTERVAL_MILLIS);
    }
    
    AgentProjectionTaskEngine(long changeDelayMillis, long retryDelayMillis,
        ProjectionExecutor projectionExecutor, NacosExecuteTaskExecuteEngine executeEngine,
        long processIntervalMillis) {
        this.changeDelayMillis = changeDelayMillis;
        this.retryDelayMillis = retryDelayMillis;
        this.projectionExecutor = projectionExecutor;
        this.executeEngine = executeEngine;
        this.delayEngine = new ProjectionDelayEngine(processIntervalMillis);
    }
    
    void markDirty(AgentProjectionKey key, AgentProjectionChangeReason reason) {
        markDirty(key, reason, changeDelayMillis);
    }
    
    private void markDirty(AgentProjectionKey key, AgentProjectionChangeReason reason,
        long delayMillis) {
        if (closed.get()) {
            return;
        }
        delayEngine.addTask(key, new ProjectionDelayTask(key, reason, delayMillis));
        AgentWatchMetrics.setPendingProjectionTasks(delayEngine.size());
    }
    
    void retry(AgentProjectionKey key, AgentProjectionChangeReason reason) {
        if (closed.get()) {
            return;
        }
        AgentWatchMetrics.record(AgentWatchMetrics.Event.PROJECTION_RETRY,
            AgentWatchMetrics.Result.SCHEDULED);
        markDirty(key, reason, retryDelayMillis);
    }
    
    int pendingDelayTaskCount() {
        return delayEngine.size();
    }
    
    void shutdown() throws NacosException {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        delayEngine.shutdown();
        executeEngine.shutdown();
        AgentWatchMetrics.setPendingProjectionTasks(0);
    }
    
    interface ProjectionExecutor {
        
        boolean execute(AgentProjectionKey key, Set<AgentProjectionChangeReason> reasons);
    }
    
    private class ProjectionDelayEngine extends NacosDelayTaskExecuteEngine {
        
        ProjectionDelayEngine(long processIntervalMillis) {
            super("AgentProjectionDelay", INITIAL_CAPACITY, LOGGER, processIntervalMillis);
            setDefaultTaskProcessor(new ProjectionDelayProcessor());
        }
    }
    
    private class ProjectionDelayProcessor implements NacosTaskProcessor {
        
        @Override
        public boolean process(NacosTask task) {
            ProjectionDelayTask delayTask = (ProjectionDelayTask) task;
            boolean accepted = executeEngine.tryAddTask(delayTask.getKey(),
                new ProjectionExecuteTask(delayTask));
            AgentWatchMetrics.setPendingProjectionTasks(delayEngine.size());
            return accepted;
        }
    }
    
    private class ProjectionExecuteTask extends AbstractExecuteTask {
        
        private final ProjectionDelayTask source;
        
        ProjectionExecuteTask(ProjectionDelayTask source) {
            this.source = source;
        }
        
        @Override
        public void run() {
            if (closed.get()) {
                return;
            }
            boolean completed = false;
            try {
                completed = projectionExecutor.execute(source.getKey(), source.getReasons());
            } catch (Throwable e) {
                if (WARN_LOG_LIMITER.tryAcquire()) {
                    LOGGER.warn("Agent Projection current-fact execution failed: {}",
                        e.getClass().getSimpleName());
                }
            }
            if (!completed) {
                retry(source.getKey(), AgentProjectionChangeReason.RETRY);
            }
            AgentWatchMetrics.setPendingProjectionTasks(delayEngine.size());
        }
    }
    
    private static class ProjectionDelayTask extends AbstractDelayTask {
        
        private final AgentProjectionKey key;
        
        private final Set<AgentProjectionChangeReason> reasons =
            new LinkedHashSet<AgentProjectionChangeReason>();
        
        ProjectionDelayTask(AgentProjectionKey key, AgentProjectionChangeReason reason,
            long delayMillis) {
            this.key = key;
            reasons.add(reason);
            setTaskInterval(delayMillis);
            setLastProcessTime(System.currentTimeMillis());
        }
        
        AgentProjectionKey getKey() {
            return key;
        }
        
        Set<AgentProjectionChangeReason> getReasons() {
            return new LinkedHashSet<AgentProjectionChangeReason>(reasons);
        }
        
        @Override
        public void merge(AbstractDelayTask task) {
            if (!(task instanceof ProjectionDelayTask)) {
                return;
            }
            ProjectionDelayTask previous = (ProjectionDelayTask) task;
            reasons.addAll(previous.reasons);
            setLastProcessTime(Math.min(getLastProcessTime(), previous.getLastProcessTime()));
            setTaskInterval(Math.min(getTaskInterval(), previous.getTaskInterval()));
            AgentWatchMetrics.record(AgentWatchMetrics.Event.PROJECTION_COALESCE,
                AgentWatchMetrics.Result.SUCCESS);
        }
    }
}
