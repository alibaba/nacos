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

import com.alibaba.nacos.client.utils.LogUtils;
import com.alibaba.nacos.common.executor.NameThreadFactory;
import com.alibaba.nacos.common.utils.LogRateLimiter;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Delayed;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

/**
 * Retains rejected background submissions independently of the rejecting executor.
 * The recovery thread only submits work; it never runs network or Listener code.
 *
 * @author Nacos
 */
final class AgentWatchTaskScheduler {
    
    private static final Logger LOGGER = LogUtils.logger(AgentWatchTaskScheduler.class);
    
    private static final LogRateLimiter WARN_LOG_LIMITER = new LogRateLimiter(60000L);
    
    private final ScheduledExecutorService recoveryExecutor;
    
    private final LongSupplier nanoTime;
    
    private final AgentWatchRetryPolicy retryPolicy;
    
    private final Set<Task> waiting = new HashSet<Task>();
    
    private ScheduledFuture<?> recovery;
    
    private boolean closed;
    
    AgentWatchTaskScheduler() {
        this(new ScheduledThreadPoolExecutor(1,
            new NameThreadFactory("com.alibaba.nacos.client.ai.watch.submission")),
            System::nanoTime, new AgentWatchRetryPolicy.Jittered(1000L, 30000L));
    }
    
    AgentWatchTaskScheduler(ScheduledExecutorService recoveryExecutor, LongSupplier nanoTime,
        AgentWatchRetryPolicy retryPolicy) {
        this.recoveryExecutor = recoveryExecutor;
        this.nanoTime = nanoTime;
        this.retryPolicy = retryPolicy;
    }
    
    synchronized ScheduledFuture<?> schedule(ScheduledExecutorService executor,
        Runnable command, long delayMillis) {
        Task task = new Task(executor, command, delayMillis, true);
        submit(task);
        return task;
    }
    
    synchronized Future<?> execute(ExecutorService executor, Runnable command) {
        Task task = new Task(executor, command, 0L, false);
        submit(task);
        return task;
    }
    
    long nanoTime() {
        return nanoTime.getAsLong();
    }
    
    private synchronized void submit(Task task) {
        if (closed || task.isCancelled()) {
            task.cancel(false);
            return;
        }
        try {
            if (task.scheduled) {
                task.delegate = ((ScheduledExecutorService) task.executor).schedule(task,
                    task.failures == 0 ? task.delayMillis
                        : Math.max(0L, task.getDelay(TimeUnit.MILLISECONDS)),
                    TimeUnit.MILLISECONDS);
            } else {
                task.executor.execute(task);
            }
            waiting.remove(task);
            if (task.failures > 0) {
                LOGGER.info("[RAD-WATCH] Background task submission recovered: task={}, "
                    + "executor={}, failures={}, waitingCount={}", task.commandType,
                    task.executor.getClass().getSimpleName(), task.failures, waiting.size());
            }
        } catch (RejectedExecutionException e) {
            task.failures = Math.min(30, task.failures + 1);
            long delay = retryPolicy.nextDelayMillis(task.failures, "submission");
            task.retryAt = nanoTime.getAsLong() + TimeUnit.MILLISECONDS.toNanos(delay);
            waiting.add(task);
            if (WARN_LOG_LIMITER.tryAcquire()) {
                LOGGER.warn("[RAD-WATCH] Background task rejected; retaining work: task={}, "
                    + "executor={}, executorShutdown={}, failures={}, retryDelayMillis={}, "
                    + "waitingCount={}", task.commandType, task.executor.getClass().getSimpleName(),
                    task.executor.isShutdown(), task.failures, delay, waiting.size(), e);
            }
            if (recovery == null) {
                recovery = recoveryExecutor.scheduleWithFixedDelay(this::retryWaiting,
                    1000L, 1000L, TimeUnit.MILLISECONDS);
            }
        }
    }
    
    private synchronized void retryWaiting() {
        for (Task task : new ArrayList<Task>(waiting)) {
            if (nanoTime.getAsLong() - task.retryAt >= 0L) {
                submit(task);
            }
        }
    }
    
    synchronized void shutdown() {
        closed = true;
        for (Task task : new ArrayList<Task>(waiting)) {
            task.cancel(false);
        }
        if (recovery != null) {
            recovery.cancel(false);
        }
        recoveryExecutor.shutdownNow();
    }
    
    private final class Task extends FutureTask<Void> implements ScheduledFuture<Void> {
        
        private final ExecutorService executor;
        
        private final String commandType;
        
        private final boolean scheduled;
        
        private final long deadline;
        
        private final long delayMillis;
        
        private Future<?> delegate;
        
        private int failures;
        
        private long retryAt;
        
        private Task(ExecutorService executor, Runnable command, long delayMillis,
            boolean scheduled) {
            super(command, null);
            this.executor = executor;
            this.commandType = command.getClass().getName();
            this.scheduled = scheduled;
            this.delayMillis = delayMillis;
            this.deadline = nanoTime.getAsLong() + TimeUnit.MILLISECONDS.toNanos(delayMillis);
        }
        
        @Override
        public long getDelay(TimeUnit unit) {
            return unit.convert(deadline - nanoTime.getAsLong(), TimeUnit.NANOSECONDS);
        }
        
        @Override
        public int compareTo(Delayed other) {
            return Long.compare(getDelay(TimeUnit.NANOSECONDS),
                other.getDelay(TimeUnit.NANOSECONDS));
        }
        
        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            synchronized (AgentWatchTaskScheduler.this) {
                waiting.remove(this);
                boolean canceled = super.cancel(mayInterruptIfRunning);
                if (delegate != null) {
                    delegate.cancel(mayInterruptIfRunning);
                }
                return canceled;
            }
        }
    }
}
