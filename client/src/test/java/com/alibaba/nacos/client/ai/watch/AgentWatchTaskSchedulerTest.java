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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentWatchTaskSchedulerTest {
    
    private final AtomicLong clock = new AtomicLong();
    
    private final AtomicReference<Runnable> tick = new AtomicReference<Runnable>();
    
    private ScheduledExecutorService driver;
    
    private ScheduledExecutorService target;
    
    private AgentWatchTaskScheduler scheduler;
    
    @BeforeEach
    void setUp() {
        driver = mock(ScheduledExecutorService.class);
        target = mock(ScheduledExecutorService.class);
        when(driver.scheduleWithFixedDelay(any(Runnable.class), anyLong(), anyLong(), any()))
            .thenAnswer(invocation -> {
                tick.set(invocation.getArgument(0));
                return mock(ScheduledFuture.class);
            });
        scheduler = new AgentWatchTaskScheduler(driver, clock::get, (count, key) -> 1000L);
    }
    
    @AfterEach
    void tearDown() {
        scheduler.shutdown();
    }
    
    @Test
    void rejectedScheduledWorkRetriesWithoutExecutingOnRecoveryThread() throws Exception {
        List<Runnable> submitted = new ArrayList<Runnable>();
        Runnable command = mock(Runnable.class);
        when(target.schedule(any(Runnable.class), anyLong(), any()))
            .thenThrow(new RejectedExecutionException("first"))
            .thenThrow(new RejectedExecutionException("second"))
            .thenAnswer(invocation -> {
                submitted.add(invocation.getArgument(0));
                return mock(ScheduledFuture.class);
            });
        ScheduledFuture<?> result = scheduler.schedule(target, command, 5000L);
        assertEquals(5000L, result.getDelay(TimeUnit.MILLISECONDS));
        tick.get().run();
        verify(target).schedule(any(Runnable.class), anyLong(), any());
        clock.set(TimeUnit.SECONDS.toNanos(1));
        tick.get().run();
        assertFalse(result.isDone());
        clock.set(TimeUnit.SECONDS.toNanos(2));
        tick.get().run();
        assertEquals(1, submitted.size());
        verify(command, times(0)).run();
        submitted.get(0).run();
        submitted.get(0).run();
        result.get();
        verify(command).run();
        tick.get().run();
        verify(target, times(3)).schedule(any(Runnable.class), anyLong(), any());
        verify(driver).scheduleWithFixedDelay(any(Runnable.class), anyLong(), anyLong(), any());
    }
    
    @Test
    void rejectedExecuteWorkResumesOnOriginalExecutor() throws Exception {
        ExecutorService executor = mock(ExecutorService.class);
        List<Runnable> submitted = new ArrayList<Runnable>();
        Runnable command = mock(Runnable.class);
        doThrow(new RejectedExecutionException("busy")).doAnswer(invocation -> {
            submitted.add(invocation.getArgument(0));
            return null;
        }).when(executor).execute(any(Runnable.class));
        Future<?> result = scheduler.execute(executor, command);
        clock.set(TimeUnit.SECONDS.toNanos(1));
        tick.get().run();
        verify(command, times(0)).run();
        submitted.get(0).run();
        result.get();
        verify(command).run();
    }
    
    @Test
    void canceledAndClosedWaitingTasksCannotBeResubmitted() {
        when(target.schedule(any(Runnable.class), anyLong(), any()))
            .thenThrow(new RejectedExecutionException("busy"));
        Runnable command = mock(Runnable.class);
        Future<?> canceled = scheduler.schedule(target, command, 1000L);
        Future<?> closed = scheduler.schedule(target, command, 1000L);
        assertTrue(canceled.cancel(false));
        scheduler.shutdown();
        assertTrue(closed.isCancelled());
        clock.set(TimeUnit.SECONDS.toNanos(60));
        tick.get().run();
        verify(target, times(2)).schedule(any(Runnable.class), anyLong(), any());
        assertTrue(scheduler.schedule(target, command, 0L).isCancelled());
        verify(command, times(0)).run();
    }
    
    @Test
    void normalTasksPreserveDelayCancelDelegateAndRunOnce() throws Exception {
        List<Runnable> submitted = new ArrayList<Runnable>();
        ScheduledFuture<?> delegate = mock(ScheduledFuture.class);
        when(target.schedule(any(Runnable.class), anyLong(), any())).thenAnswer(invocation -> {
            submitted.add(invocation.getArgument(0));
            return delegate;
        });
        Runnable first = mock(Runnable.class);
        Runnable second = mock(Runnable.class);
        ScheduledFuture<?> a = scheduler.schedule(target, first, 100L);
        ScheduledFuture<?> b = scheduler.schedule(target, second, 200L);
        assertTrue(a.compareTo(b) < 0);
        assertEquals(0, a.compareTo(a));
        assertTrue(b.compareTo(a) > 0);
        a.cancel(true);
        submitted.get(0).run();
        submitted.get(1).run();
        b.get();
        verify(delegate).cancel(true);
        verify(first, times(0)).run();
        verify(second).run();
        verify(driver, times(0)).scheduleWithFixedDelay(any(Runnable.class), anyLong(), anyLong(),
            any());
    }
    
    @Test
    void normalExecuteDoesNotStartRecovery() throws Exception {
        ExecutorService executor = mock(ExecutorService.class);
        doAnswer(invocation -> {
            ((Runnable) invocation.getArgument(0)).run();
            return null;
        }).when(executor).execute(any(Runnable.class));
        Runnable command = mock(Runnable.class);
        scheduler.execute(executor, command).get();
        verify(command).run();
        verify(driver, times(0)).scheduleWithFixedDelay(any(Runnable.class), anyLong(), anyLong(),
            any());
    }
    
    @Test
    void prolongedRejectionKeepsOneTaskPerSubscriptionAndDoesNotBlockHealthyWork()
        throws Exception {
        List<Integer> failureCounts = new ArrayList<Integer>();
        AgentWatchRetryPolicy bounded = new AgentWatchRetryPolicy.Jittered(1000L, 30000L);
        scheduler.shutdown();
        scheduler = new AgentWatchTaskScheduler(driver, clock::get, (count, key) -> {
            failureCounts.add(count);
            long delay = bounded.nextDelayMillis(count, key);
            assertTrue(delay > 0L && delay <= 30000L);
            return delay;
        });
        List<Runnable> accepted = new ArrayList<Runnable>();
        java.util.concurrent.atomic.AtomicBoolean rejecting =
            new java.util.concurrent.atomic.AtomicBoolean(true);
        when(target.schedule(any(Runnable.class), anyLong(), any())).thenAnswer(invocation -> {
            if (rejecting.get()) {
                throw new RejectedExecutionException("temporarily full");
            }
            accepted.add(invocation.getArgument(0));
            return mock(ScheduledFuture.class);
        });
        List<ScheduledFuture<?>> tasks = new ArrayList<ScheduledFuture<?>>();
        Runnable command = mock(Runnable.class);
        for (int i = 0; i < 100; i++) {
            tasks.add(scheduler.schedule(target, command, 0L));
        }
        for (int i = 0; i < 35; i++) {
            clock.addAndGet(TimeUnit.SECONDS.toNanos(30));
            tick.get().run();
        }
        assertEquals(3600, failureCounts.size());
        assertEquals(30, java.util.Collections.max(failureCounts));
        ExecutorService healthy = mock(ExecutorService.class);
        doAnswer(invocation -> {
            ((Runnable) invocation.getArgument(0)).run();
            return null;
        }).when(healthy).execute(any(Runnable.class));
        Runnable healthyCommand = mock(Runnable.class);
        scheduler.execute(healthy, healthyCommand).get();
        verify(healthyCommand).run();
        for (int i = 0; i < 50; i++) {
            tasks.get(i).cancel(false);
        }
        rejecting.set(false);
        clock.addAndGet(TimeUnit.SECONDS.toNanos(30));
        tick.get().run();
        assertEquals(50, accepted.size());
        for (Runnable task : accepted) {
            task.run();
        }
        tick.get().run();
        assertEquals(50, accepted.size());
        verify(command, times(50)).run();
        verify(driver).scheduleWithFixedDelay(any(Runnable.class), anyLong(), anyLong(), any());
    }
}
