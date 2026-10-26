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

import com.alibaba.nacos.api.ai.listener.AbstractNacosAgentDiscoveryListener;
import com.alibaba.nacos.api.ai.listener.NacosAgentDiscoveryEvent;
import com.alibaba.nacos.api.ai.utils.AgentWatchLogUtils;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.client.ai.utils.AgentModelUtils;
import com.alibaba.nacos.client.ai.watch.AgentWatchSubscription.WatchState;
import com.alibaba.nacos.client.utils.LogUtils;
import com.alibaba.nacos.common.executor.NameThreadFactory;
import com.alibaba.nacos.common.utils.LogRateLimiter;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Delivers each Watch's latest state with bounded per-listener progress.
 *
 * <p>The manager owns the subscription collection and calls lifecycle methods under
 * {@code stateLock}. Delivery and checking share that same monitor; user executor lookup,
 * callback submission and callbacks happen outside it. No event queue or copy of the Watch
 * cache is kept here. Queued delivery reads the subscription's current state when invoked.</p>
 *
 * @author Nacos
 */
final class AgentWatchListenerDispatcher {
    
    private static final Logger LOGGER = LogUtils.logger(AgentWatchListenerDispatcher.class);
    
    private static final LogRateLimiter CALLBACK_LOG_LIMITER = new LogRateLimiter(60000L);
    
    private static final LogRateLimiter LISTENER_LOG_LIMITER = new LogRateLimiter(60000L);
    
    private static final LogRateLimiter SLOW_LOG_LIMITER = new LogRateLimiter(60000L);
    
    static final long LISTENER_CHECK_INTERVAL_MILLIS = 1000L;
    
    private static final long SLOW_LISTENER_MILLIS = 10000L;
    
    private static final int CALLBACK_CORE_THREADS = 2;
    
    private static final int CALLBACK_MAX_THREADS = 4;
    
    private static final int CALLBACK_QUEUE_CAPACITY = 1024;
    
    private final Object stateLock;
    
    private final Collection<AgentWatchSubscription> subscriptions;
    
    private final ScheduledExecutorService refreshExecutor;
    
    private final ExecutorService callbackExecutor;
    
    private final AgentWatchTaskScheduler taskScheduler;
    
    private ScheduledFuture<?> listenerCheckFuture;
    
    private long listenerCheckGeneration;
    
    private boolean closed;
    
    AgentWatchListenerDispatcher(Object stateLock,
        Collection<AgentWatchSubscription> subscriptions,
        ScheduledExecutorService refreshExecutor, ExecutorService callbackExecutor,
        AgentWatchTaskScheduler taskScheduler) {
        this.stateLock = stateLock;
        this.subscriptions = subscriptions;
        this.refreshExecutor = refreshExecutor;
        this.callbackExecutor = callbackExecutor;
        this.taskScheduler = taskScheduler;
    }
    
    ListenerRegistration register(AgentWatchSubscription subscription,
        AbstractNacosAgentDiscoveryListener listener) {
        return new ListenerRegistration(subscription, listener);
    }
    
    boolean prepareCurrent(ListenerRegistration registration) {
        if (registration == null || registration.notificationRegistered
            || registration.intent.stateSequence == 0L) {
            return false;
        }
        registration.notificationRegistered = true;
        return true;
    }
    
    List<ListenerRegistration> prepareNotifications(AgentWatchSubscription subscription) {
        List<ListenerRegistration> listeners =
            new ArrayList<ListenerRegistration>(subscription.listeners.values());
        for (ListenerRegistration listener : listeners) {
            listener.notificationRegistered = true;
        }
        return listeners;
    }
    
    void dispatch(ListenerRegistration registration) {
        registration.dispatchLatest();
    }
    
    void dispatch(List<ListenerRegistration> listeners) {
        for (ListenerRegistration listener : listeners) {
            listener.dispatchLatest();
        }
    }
    
    void remove(ListenerRegistration registration) {
        registration.active = false;
    }
    
    void stopCheckingIfEmpty() {
        if (subscriptions.isEmpty()) {
            listenerCheckGeneration++;
            if (listenerCheckFuture != null) {
                listenerCheckFuture.cancel(false);
                listenerCheckFuture = null;
            }
        }
    }
    
    void shutdown() {
        closed = true;
        stopCheckingIfEmpty();
        callbackExecutor.shutdownNow();
    }
    
    private void scheduleListenerCheck() {
        if (closed || subscriptions.isEmpty() || listenerCheckFuture != null) {
            return;
        }
        long generation = ++listenerCheckGeneration;
        listenerCheckFuture = taskScheduler.schedule(refreshExecutor,
            () -> checkListeners(generation), LISTENER_CHECK_INTERVAL_MILLIS);
    }
    
    private void checkListeners(long generation) {
        List<ListenerRegistration> listeners = new ArrayList<ListenerRegistration>();
        synchronized (stateLock) {
            if (closed || generation != listenerCheckGeneration) {
                return;
            }
            listenerCheckFuture = null;
            for (AgentWatchSubscription intent : subscriptions) {
                listeners.addAll(intent.listeners.values());
            }
        }
        try {
            for (ListenerRegistration listener : listeners) {
                listener.checkSlowDelivery();
                listener.dispatchLatest();
            }
        } finally {
            synchronized (stateLock) {
                scheduleListenerCheck();
            }
        }
    }
    
    private NacosAgentDiscoveryEvent unavailable() {
        return NacosAgentDiscoveryEvent.unavailable(NacosException.NOT_FOUND,
            "The subscribed Agent reference does not exist.");
    }
    
    private void warn(String operation, AgentWatchSubscription intent,
        ListenerRegistration listener,
        Throwable failure, int failures, long retryDelayMillis) {
        LogRateLimiter limiter = CALLBACK_LOG_LIMITER;
        if ("LISTENER_CALLBACK".equals(operation)) {
            limiter = LISTENER_LOG_LIMITER;
        }
        if (limiter.tryAcquire()) {
            int code = failure instanceof NacosException
                ? ((NacosException) failure).getErrCode() : NacosException.CLIENT_ERROR;
            Integer detail = failure instanceof NacosApiException
                ? ((NacosApiException) failure).getDetailErrCode() : null;
            LOGGER.warn("[RAD-WATCH] Operation failed: operation={}, clientWatchId={}, "
                + "listener={}, errorCode={}, detailCode={}, failures={}, retryDelayMillis={}, {}",
                operation, AgentWatchLogUtils.token(intent.clientWatchId),
                listener.listenerId(), code, detail, failures,
                retryDelayMillis, AgentWatchLogUtils.describeRequest(intent.request), failure);
        }
    }
    
    static ExecutorService newCallbackExecutor() {
        return new ThreadPoolExecutor(CALLBACK_CORE_THREADS, CALLBACK_MAX_THREADS, 60L,
            TimeUnit.SECONDS, new ArrayBlockingQueue<Runnable>(CALLBACK_QUEUE_CAPACITY),
            new NameThreadFactory("com.alibaba.nacos.client.ai.agent.listener"),
            new ThreadPoolExecutor.AbortPolicy());
    }
    
    final class ListenerRegistration {
        
        private final AgentWatchSubscription intent;
        
        private final AbstractNacosAgentDiscoveryListener listener;
        
        private boolean active = true;
        
        private boolean dispatching;
        
        private long lastDeliveredSequence;
        
        private boolean notificationRegistered;
        
        private long deliveryGeneration;
        
        private long deliveryStartedNanos;
        
        private int submissionFailures;
        
        private long retryAfterNanos;
        
        private ListenerRegistration(AgentWatchSubscription intent,
            AbstractNacosAgentDiscoveryListener listener) {
            this.intent = intent;
            this.listener = listener;
        }
        
        private boolean isActive() {
            return !closed && active && intent.state != WatchState.CLOSED;
        }
        
        private void dispatchLatest() {
            final long generation;
            synchronized (stateLock) {
                if (!isActive() || !notificationRegistered) {
                    return;
                }
                scheduleListenerCheck();
                if (dispatching || intent.stateSequence <= lastDeliveredSequence
                    || submissionFailures > 0
                        && taskScheduler.nanoTime() - retryAfterNanos < 0L) {
                    return;
                }
                dispatching = true;
                generation = ++deliveryGeneration;
                deliveryStartedNanos = taskScheduler.nanoTime();
            }
            try {
                callbackExecutor.execute(() -> dispatchToListener(generation));
            } catch (Throwable e) {
                rejected(generation, "CALLBACK_SUBMIT", e);
            }
        }
        
        private void dispatchToListener(long generation) {
            synchronized (stateLock) {
                if (!isActive() || !dispatching || generation != deliveryGeneration) {
                    return;
                }
            }
            Executor executor;
            try {
                executor = listener.getExecutor();
            } catch (Throwable e) {
                rejected(generation, "LISTENER_EXECUTOR_LOOKUP", e);
                return;
            }
            try {
                if (executor == null) {
                    deliverLatest(generation);
                } else {
                    executor.execute(() -> deliverLatest(generation));
                }
            } catch (Throwable e) {
                rejected(generation, "LISTENER_EXECUTOR_SUBMIT", e);
            }
        }
        
        private void deliverLatest(long generation) {
            final long sequence;
            final NacosAgentDiscoveryEvent event;
            try {
                synchronized (stateLock) {
                    if (!isActive() || !dispatching || generation != deliveryGeneration) {
                        return;
                    }
                    sequence = intent.stateSequence;
                    event = intent.current == null ? unavailable()
                        : new NacosAgentDiscoveryEvent(
                            AgentModelUtils.copyDiscoveryResult(intent.current));
                    if (submissionFailures > 0) {
                        LOGGER.info("[RAD-WATCH] Listener delivery recovered: clientWatchId={}, "
                            + "listener={}, failures={}, sequence={}",
                            AgentWatchLogUtils.token(intent.clientWatchId), listenerId(),
                            submissionFailures, sequence);
                        submissionFailures = 0;
                    }
                }
            } catch (Throwable e) {
                rejected(generation, "LISTENER_SNAPSHOT_PREPARE", e);
                return;
            }
            try {
                listener.onEvent(event);
                AgentWatchClientMetrics.record(AgentWatchClientMetrics.Event.LISTENER_CALLBACK,
                    AgentWatchClientMetrics.Result.SUCCESS);
            } catch (Throwable e) {
                AgentWatchClientMetrics.record(AgentWatchClientMetrics.Event.LISTENER_CALLBACK,
                    AgentWatchClientMetrics.Result.FAILED);
                warn("LISTENER_CALLBACK", intent, this, e, 1, 0L);
            } finally {
                synchronized (stateLock) {
                    lastDeliveredSequence = sequence;
                    dispatching = false;
                }
                dispatchLatest();
            }
        }
        
        private void rejected(long generation, String operation, Throwable failure) {
            int failures;
            synchronized (stateLock) {
                // A custom Executor may run the task before throwing. Its late failure
                // must not reset a completed or newer delivery.
                if (!isActive() || !dispatching || generation != deliveryGeneration) {
                    return;
                }
                dispatching = false;
                submissionFailures = Math.min(30, submissionFailures + 1);
                retryAfterNanos = taskScheduler.nanoTime()
                    + TimeUnit.MILLISECONDS.toNanos(LISTENER_CHECK_INTERVAL_MILLIS);
                failures = submissionFailures;
            }
            warn(operation, intent, this, failure, failures, LISTENER_CHECK_INTERVAL_MILLIS);
        }
        
        private void checkSlowDelivery() {
            synchronized (stateLock) {
                long elapsed = TimeUnit.NANOSECONDS.toMillis(
                    taskScheduler.nanoTime() - deliveryStartedNanos);
                if (isActive() && dispatching && elapsed >= SLOW_LISTENER_MILLIS
                    && SLOW_LOG_LIMITER.tryAcquire()) {
                    LOGGER.warn("[RAD-WATCH] Listener delivery is still queued or running: "
                        + "clientWatchId={}, listener={}, elapsedMillis={}, "
                        + "latestSequence={}, handledSequence={}, {}",
                        AgentWatchLogUtils.token(intent.clientWatchId), listenerId(), elapsed,
                        intent.stateSequence, lastDeliveredSequence,
                        AgentWatchLogUtils.describeRequest(intent.request));
                }
            }
        }
        
        private String listenerId() {
            return listener.getClass().getName() + "@"
                + Integer.toHexString(System.identityHashCode(listener));
        }
    }
    
}
