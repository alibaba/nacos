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

import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.api.ai.listener.AbstractNacosAgentDiscoveryListener;
import com.alibaba.nacos.api.ai.listener.NacosAgentDiscoveryEvent;
import com.alibaba.nacos.api.ai.model.rad.AgentDiscoveryFilter;
import com.alibaba.nacos.api.ai.model.rad.AgentDiscoveryRequest;
import com.alibaba.nacos.api.ai.model.rad.AgentDiscoveryResult;
import com.alibaba.nacos.api.ai.model.rad.AgentReference;
import com.alibaba.nacos.api.ai.utils.AgentDiscoveryCanonicalizer;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.client.ai.remote.AgentClientProxy;
import com.alibaba.nacos.client.ai.utils.AgentModelUtils;
import com.alibaba.nacos.common.executor.NameThreadFactory;
import com.alibaba.nacos.common.lifecycle.Closeable;
import com.alibaba.nacos.client.utils.LogUtils;
import org.slf4j.Logger;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Transport-neutral Agent Watch manager.
 *
 * <p>The manager owns canonical identity, local capacity, complete-result cache, fingerprint,
 * pending/dirty refresh, and Listener isolation. A pluggable {@link AgentWatchTransport} owns
 * only Wire lifecycle and invalidation signals. Multiple Listener instances for one canonical
 * Discover request therefore share one Wire Intent.</p>
 *
 * @author Nacos
 */
public class AgentWatchManager implements Closeable {
    
    private static final Logger LOGGER = LogUtils.logger(AgentWatchManager.class);
    
    private static final long MAXIMUM_RETRY_DELAY_MILLIS = 60000L;
    
    private static final int CALLBACK_CORE_THREADS = 2;
    
    private static final int CALLBACK_MAX_THREADS = 4;
    
    private static final int CALLBACK_QUEUE_CAPACITY = 1024;
    
    private final String namespaceId;
    
    private final AgentClientProxy clientProxy;
    
    private final ScheduledExecutorService refreshExecutor;
    
    private final ExecutorService callbackExecutor;
    
    private final int maxSubscriptions;
    
    private final AgentWatchTransport watchTransport;
    
    private final AgentWatchRetryPolicy retryPolicy;
    
    private final Map<String, WatchIntent> intentsByKey =
        new LinkedHashMap<String, WatchIntent>();
    
    private final Map<String, WatchIntent> intentsById =
        new LinkedHashMap<String, WatchIntent>();
    
    private int subscriptionCount;
    
    private boolean closed;
    
    /**
     * Create a Watch manager with default capacity and polling compatibility transport.
     *
     * @param namespaceId SDK namespace
     * @param clientProxy authoritative Discover proxy
     */
    public AgentWatchManager(String namespaceId, AgentClientProxy clientProxy) {
        this(namespaceId, clientProxy,
            AiConstants.DEFAULT_AI_AGENT_DISCOVERY_MAX_SUBSCRIPTIONS);
    }
    
    /**
     * Create a Watch manager with configured local capacity.
     *
     * @param namespaceId SDK namespace
     * @param clientProxy authoritative Discover proxy
     * @param maxSubscriptions local Listener-record watermark
     */
    public AgentWatchManager(String namespaceId, AgentClientProxy clientProxy,
        int maxSubscriptions) {
        this(namespaceId, clientProxy, AiConstants.DEFAULT_AI_CACHE_UPDATE_INTERVAL,
            new ScheduledThreadPoolExecutor(1,
                new NameThreadFactory("com.alibaba.nacos.client.ai.agent.watch")),
            newCallbackExecutor(), maxSubscriptions);
    }
    
    /**
     * Create an injectable polling Watch manager.
     *
     * @param namespaceId SDK namespace
     * @param clientProxy authoritative Discover proxy
     * @param updateIntervalMillis polling interval
     * @param refreshExecutor refresh scheduler
     * @param callbackExecutor default Listener executor
     * @param maxSubscriptions local Listener-record watermark
     */
    protected AgentWatchManager(String namespaceId, AgentClientProxy clientProxy,
        long updateIntervalMillis, ScheduledExecutorService refreshExecutor,
        ExecutorService callbackExecutor, int maxSubscriptions) {
        this(namespaceId, clientProxy, refreshExecutor, callbackExecutor, maxSubscriptions,
            new PollingAgentWatchTransport(refreshExecutor, updateIntervalMillis),
            new AgentWatchRetryPolicy.Jittered(Math.max(1L, updateIntervalMillis),
                Math.max(updateIntervalMillis, MAXIMUM_RETRY_DELAY_MILLIS)));
    }
    
    /**
     * Create a Watch manager with a configured Wire transport.
     *
     * @param namespaceId SDK namespace
     * @param clientProxy authoritative Discover proxy
     * @param maxSubscriptions local Listener-record watermark
     * @param watchTransport Wire Watch transport router
     */
    public AgentWatchManager(String namespaceId, AgentClientProxy clientProxy,
        int maxSubscriptions, AgentWatchTransport watchTransport) {
        this(namespaceId, clientProxy,
            new ScheduledThreadPoolExecutor(1,
                new NameThreadFactory("com.alibaba.nacos.client.ai.agent.watch")),
            newCallbackExecutor(), maxSubscriptions, watchTransport,
            new AgentWatchRetryPolicy.Jittered(
                AiConstants.DEFAULT_AI_CACHE_UPDATE_INTERVAL, MAXIMUM_RETRY_DELAY_MILLIS));
    }
    
    AgentWatchManager(String namespaceId, AgentClientProxy clientProxy,
        ScheduledExecutorService refreshExecutor, ExecutorService callbackExecutor,
        int maxSubscriptions, AgentWatchTransport watchTransport,
        AgentWatchRetryPolicy retryPolicy) {
        if (maxSubscriptions < 1) {
            throw new IllegalArgumentException("maxSubscriptions must be greater than 0");
        }
        this.namespaceId = namespaceId;
        this.clientProxy = clientProxy;
        this.refreshExecutor = refreshExecutor;
        this.callbackExecutor = callbackExecutor;
        this.maxSubscriptions = maxSubscriptions;
        this.watchTransport = watchTransport;
        this.retryPolicy = retryPolicy;
    }
    
    /**
     * Subscribe one Listener to a canonical Agent discovery Watch.
     *
     * @param reference Agent reference
     * @param filter optional filter
     * @param listener listener identity
     * @return current complete snapshot, or {@code null} while locally pending
     * @throws NacosException when validation, initial Discover, capacity, or activation fails
     */
    public AgentDiscoveryResult subscribe(AgentReference reference, AgentDiscoveryFilter filter,
        AbstractNacosAgentDiscoveryListener listener) throws NacosException {
        if (listener == null) {
            throw new NacosApiException(NacosException.INVALID_PARAM,
                ErrorCode.PARAMETER_VALIDATE_ERROR,
                "AbstractNacosAgentDiscoveryListener must not be null.");
        }
        AgentDiscoveryRequest request = canonicalRequest(reference, filter);
        String requestKey = AgentDiscoveryCanonicalizer.canonicalRequestKey(request);
        WatchIntent initialization = null;
        WatchIntent waitingActivation = null;
        ActivationBarrier waitingBarrier = null;
        boolean waitingListenerAdded = false;
        synchronized (this) {
            ensureOpen();
            WatchIntent existing = intentsByKey.get(requestKey);
            if (existing != null) {
                if (!existing.listeners.containsKey(listener)) {
                    ensureCapacity(1);
                    existing.addListener(listener);
                    subscriptionCount++;
                    waitingListenerAdded = true;
                }
                if (existing.state == WatchState.ACTIVATING) {
                    waitingActivation = existing;
                    waitingBarrier = existing.activationBarrier;
                } else {
                    return copy(existing.current);
                }
            } else {
                ensureCapacity(1);
                WatchIntent intent = new WatchIntent(requestKey, request);
                intent.addListener(listener);
                intent.beginActivation();
                intentsByKey.put(requestKey, intent);
                intentsById.put(intent.clientWatchId, intent);
                subscriptionCount++;
                initialization = intent;
            }
        }
        if (waitingActivation != null) {
            return awaitActivation(waitingActivation, waitingBarrier, listener,
                waitingListenerAdded);
        }
        return initializeSynchronously(initialization);
    }
    
    private AgentDiscoveryResult initializeSynchronously(WatchIntent intent)
        throws NacosException {
        DiscoveryAttempt initial = discover(intent.request);
        if (initial.result == null) {
            return initializePending(intent, initial.error);
        }
        synchronized (this) {
            if (closed || intentsById.get(intent.clientWatchId) != intent
                || intent.state != WatchState.ACTIVATING) {
                return copy(initial.result);
            }
            intent.current = initial.result;
            intent.fingerprint = fingerprint(initial.result);
        }
        return activateSynchronously(intent) ? copy(initial.result) : null;
    }
    
    private AgentDiscoveryResult initializePending(WatchIntent intent, NacosException exception)
        throws NacosException {
        if (!isNotFound(exception)) {
            synchronized (this) {
                if (intentsById.get(intent.clientWatchId) == intent) {
                    intent.completeActivation(exception);
                    removeIntent(intent);
                }
            }
            throw exception;
        }
        List<ListenerNotification> notifications;
        NacosException schedulingFailure = null;
        synchronized (this) {
            if (closed || intentsById.get(intent.clientWatchId) != intent
                || intent.state != WatchState.ACTIVATING) {
                return null;
            }
            intent.state = WatchState.LOCAL_PENDING;
            intent.unavailableNotified = true;
            intent.pendingFailureCount = 1;
            if (!schedulePending(intent)) {
                schedulingFailure = schedulingRejected();
                intent.completeActivation(schedulingFailure);
                removeIntent(intent);
                notifications = new ArrayList<ListenerNotification>();
            } else {
                intent.completeActivation(null);
                notifications = unavailableNotifications(intent, exception, true);
            }
        }
        if (schedulingFailure != null) {
            throw schedulingFailure;
        }
        dispatch(notifications);
        return null;
    }
    
    private AgentDiscoveryResult awaitActivation(WatchIntent intent, ActivationBarrier barrier,
        AbstractNacosAgentDiscoveryListener listener, boolean listenerAdded)
        throws NacosException {
        try {
            barrier.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            if (listenerAdded) {
                removeListener(intent, listener);
            }
            throw new NacosException(NacosException.CLIENT_ERROR,
                "Interrupted while waiting for Agent Watch activation.", e);
        }
        synchronized (this) {
            if (barrier.failure != null) {
                throw barrier.failure;
            }
            return copy(intent.current);
        }
    }
    
    private synchronized void removeListener(WatchIntent intent,
        AbstractNacosAgentDiscoveryListener listener) {
        ListenerRegistration removed = intent.listeners.remove(listener);
        if (removed == null) {
            return;
        }
        removed.active = false;
        subscriptionCount--;
        if (intent.listeners.isEmpty()) {
            removeIntent(intent);
        }
    }
    
    /**
     * Remove one exact local Listener subscription.
     *
     * @param reference Agent reference used to subscribe
     * @param filter filter used to subscribe
     * @param listener listener instance used to subscribe
     * @throws NacosException when the reference or filter is invalid
     */
    public void unsubscribe(AgentReference reference, AgentDiscoveryFilter filter,
        AbstractNacosAgentDiscoveryListener listener) throws NacosException {
        AgentDiscoveryRequest request = canonicalRequest(reference, filter);
        if (listener == null) {
            return;
        }
        String requestKey = AgentDiscoveryCanonicalizer.canonicalRequestKey(request);
        synchronized (this) {
            WatchIntent intent = intentsByKey.get(requestKey);
            if (intent == null) {
                return;
            }
            removeListener(intent, listener);
        }
    }
    
    /**
     * Accept one transport invalidation after it is represented by refresh state.
     *
     * @param clientWatchId client Watch identifier
     * @param observedFingerprint optional observed fingerprint
     * @param forceRefresh whether equality optimization must be skipped
     * @return whether the signal is known and safely represented
     */
    public synchronized boolean markDirty(String clientWatchId, String observedFingerprint,
        boolean forceRefresh) {
        WatchIntent intent = intentsById.get(clientWatchId);
        if (closed || intent == null || intent.state == WatchState.CLOSED) {
            return false;
        }
        if (!forceRefresh && observedFingerprint != null && intent.fingerprint != null
            && observedFingerprint.equals(intent.fingerprint)) {
            return true;
        }
        intent.dirty = true;
        if (intent.state == WatchState.ACTIVATING || intent.refreshing
            || intent.refreshFuture != null) {
            return true;
        }
        if (intent.state != WatchState.ACTIVE) {
            return false;
        }
        return scheduleRefresh(intent, 0L);
    }
    
    /**
     * Report an unavailable signal for an active client Watch.
     *
     * @param clientWatchId client Watch identifier
     * @param errorCode Nacos error code
     * @param errorMessage error description
     * @param terminal whether the complete local intent must be removed
     */
    public void markUnavailable(String clientWatchId, int errorCode, String errorMessage,
        boolean terminal) {
        List<ListenerNotification> notifications = new ArrayList<ListenerNotification>();
        synchronized (this) {
            WatchIntent intent = intentsById.get(clientWatchId);
            if (closed || intent == null) {
                return;
            }
            NacosException exception = new NacosException(errorCode, errorMessage);
            if (terminal) {
                intent.completeActivation(exception);
                notifications.addAll(terminalNotifications(intent, exception));
                removeIntent(intent);
            } else if (isNotFound(exception)) {
                intent.completeActivation(null);
                notifications.addAll(enterPending(intent, exception));
            }
        }
        dispatch(notifications);
    }
    
    private boolean activateSynchronously(WatchIntent intent) throws NacosException {
        try {
            watchTransport.start(registration(intent), intent.callback);
        } catch (NacosException e) {
            if (isNotFound(e)) {
                handleActivationFailure(intent, e);
                return false;
            }
            synchronized (this) {
                if (intentsById.get(intent.clientWatchId) == intent) {
                    intent.completeActivation(e);
                    removeIntent(intent);
                }
            }
            throw e;
        } catch (RuntimeException e) {
            NacosException failure = new NacosException(NacosException.CLIENT_ERROR,
                "Failed to activate Agent Watch transport.", e);
            synchronized (this) {
                if (intentsById.get(intent.clientWatchId) == intent) {
                    intent.completeActivation(failure);
                    removeIntent(intent);
                }
            }
            throw failure;
        }
        synchronized (this) {
            if (closed || intentsById.get(intent.clientWatchId) != intent
                || intent.state != WatchState.ACTIVATING) {
                intent.completeActivation(new NacosException(NacosException.CLIENT_DISCONNECT,
                    "Agent Watch activation was canceled."));
                watchTransport.stop(intent.clientWatchId);
                return true;
            }
            intent.state = WatchState.ACTIVE;
            intent.transportActive = true;
            intent.completeActivation(null);
            if (intent.dirty) {
                scheduleRefresh(intent, 0L);
            }
        }
        return true;
    }
    
    private void executePending(String clientWatchId) {
        WatchIntent intent;
        AgentDiscoveryRequest request;
        synchronized (this) {
            intent = intentsById.get(clientWatchId);
            if (closed || intent == null || intent.state != WatchState.LOCAL_PENDING) {
                return;
            }
            intent.pendingFuture = null;
            request = intent.request;
        }
        DiscoveryAttempt attempt = discover(request);
        if (attempt.result == null) {
            handlePendingFailure(intent, attempt.error);
            return;
        }
        synchronized (this) {
            if (closed || intentsById.get(clientWatchId) != intent
                || intent.state != WatchState.LOCAL_PENDING) {
                return;
            }
            intent.current = attempt.result;
            intent.fingerprint = fingerprint(attempt.result);
            intent.beginActivation();
            intent.pendingFailureCount = 0;
            intent.unavailableNotified = false;
        }
        boolean activated = false;
        try {
            watchTransport.start(registration(intent), intent.callback);
            activated = true;
        } catch (NacosException e) {
            handleActivationFailure(intent, e);
        } catch (RuntimeException e) {
            handleActivationFailure(intent, new NacosException(NacosException.CLIENT_ERROR,
                "Failed to activate Agent Watch transport.", e));
        }
        if (!activated) {
            return;
        }
        List<ListenerNotification> notifications = new ArrayList<ListenerNotification>();
        synchronized (this) {
            if (closed || intentsById.get(clientWatchId) != intent
                || intent.state != WatchState.ACTIVATING) {
                intent.completeActivation(new NacosException(NacosException.CLIENT_DISCONNECT,
                    "Agent Watch activation was canceled."));
                watchTransport.stop(clientWatchId);
                return;
            }
            intent.state = WatchState.ACTIVE;
            intent.transportActive = true;
            intent.completeActivation(null);
            notifications.addAll(snapshotNotifications(intent));
            if (intent.dirty) {
                scheduleRefresh(intent, 0L);
            }
        }
        dispatch(notifications);
    }
    
    private void handlePendingFailure(WatchIntent intent, NacosException exception) {
        List<ListenerNotification> notifications = new ArrayList<ListenerNotification>();
        synchronized (this) {
            if (closed || intentsById.get(intent.clientWatchId) != intent
                || intent.state != WatchState.LOCAL_PENDING) {
                return;
            }
            if (isTerminal(exception)) {
                notifications.addAll(terminalNotifications(intent, exception));
                removeIntent(intent);
            } else {
                if (isNotFound(exception) && !intent.unavailableNotified) {
                    intent.unavailableNotified = true;
                    notifications.addAll(unavailableNotifications(intent, exception, true));
                }
                intent.pendingFailureCount++;
                if (!schedulePending(intent)) {
                    notifications.clear();
                    notifications.addAll(terminalNotifications(intent, schedulingRejected()));
                    removeIntent(intent);
                }
            }
        }
        dispatch(notifications);
    }
    
    private void handleActivationFailure(WatchIntent intent, NacosException exception) {
        List<ListenerNotification> notifications = new ArrayList<ListenerNotification>();
        synchronized (this) {
            if (closed || intentsById.get(intent.clientWatchId) != intent) {
                intent.completeActivation(new NacosException(NacosException.CLIENT_DISCONNECT,
                    "Agent Watch activation was canceled."));
                return;
            }
            if (isTerminal(exception)) {
                intent.completeActivation(exception);
                notifications.addAll(terminalNotifications(intent, exception));
                removeIntent(intent);
            } else {
                intent.completeActivation(null);
                watchTransport.stop(intent.clientWatchId);
                intent.transportActive = false;
                intent.current = null;
                intent.fingerprint = null;
                intent.state = WatchState.LOCAL_PENDING;
                intent.pendingFailureCount++;
                if (isNotFound(exception) && !intent.unavailableNotified) {
                    intent.unavailableNotified = true;
                    notifications.addAll(unavailableNotifications(intent, exception, true));
                }
                if (!schedulePending(intent)) {
                    notifications.addAll(terminalNotifications(intent, schedulingRejected()));
                    removeIntent(intent);
                }
            }
        }
        dispatch(notifications);
    }
    
    private void executeRefresh(String clientWatchId) {
        WatchIntent intent;
        AgentDiscoveryRequest request;
        synchronized (this) {
            intent = intentsById.get(clientWatchId);
            if (closed || intent == null || intent.state != WatchState.ACTIVE
                || !intent.dirty) {
                return;
            }
            intent.refreshFuture = null;
            intent.refreshing = true;
            intent.dirty = false;
            request = intent.request;
        }
        DiscoveryAttempt attempt = discover(request);
        if (attempt.result != null) {
            handleRefreshSuccess(intent, attempt.result);
        } else {
            handleRefreshFailure(intent, attempt.error);
        }
    }
    
    private void handleRefreshSuccess(WatchIntent intent, AgentDiscoveryResult latest) {
        List<ListenerNotification> notifications = new ArrayList<ListenerNotification>();
        synchronized (this) {
            if (closed || intentsById.get(intent.clientWatchId) != intent
                || intent.state != WatchState.ACTIVE) {
                return;
            }
            intent.refreshing = false;
            String latestFingerprint = fingerprint(latest);
            boolean changed = intent.current == null
                || !latestFingerprint.equals(intent.fingerprint);
            intent.current = latest;
            intent.fingerprint = latestFingerprint;
            intent.refreshFailureCount = 0;
            intent.unavailableNotified = false;
            try {
                watchTransport.update(registration(intent));
            } catch (RuntimeException e) {
                LOGGER.warn("Agent Watch transport state update failed.", e);
            }
            if (changed) {
                notifications.addAll(snapshotNotifications(intent));
            }
            if (intent.dirty) {
                scheduleRefresh(intent, 0L);
            }
        }
        dispatch(notifications);
    }
    
    private void handleRefreshFailure(WatchIntent intent, NacosException exception) {
        List<ListenerNotification> notifications = new ArrayList<ListenerNotification>();
        synchronized (this) {
            if (closed || intentsById.get(intent.clientWatchId) != intent
                || intent.state != WatchState.ACTIVE) {
                return;
            }
            intent.refreshing = false;
            if (isNotFound(exception)) {
                notifications.addAll(enterPending(intent, exception));
            } else if (isTerminal(exception)) {
                notifications.addAll(terminalNotifications(intent, exception));
                removeIntent(intent);
            } else {
                intent.dirty = true;
                intent.refreshFailureCount++;
                long delay = retryPolicy.nextDelayMillis(intent.refreshFailureCount,
                    intent.requestKey);
                if (!scheduleRefresh(intent, delay)) {
                    notifications.addAll(terminalNotifications(intent, schedulingRejected()));
                    removeIntent(intent);
                } else {
                    LOGGER.warn("Agent Watch Discover refresh failed; retaining dirty state.",
                        exception);
                }
            }
        }
        dispatch(notifications);
    }
    
    private List<ListenerNotification> enterPending(WatchIntent intent,
        NacosException exception) {
        if (intent.transportActive) {
            watchTransport.stop(intent.clientWatchId);
            intent.transportActive = false;
        }
        intent.state = WatchState.LOCAL_PENDING;
        intent.current = null;
        intent.fingerprint = null;
        intent.dirty = false;
        intent.refreshFailureCount = 0;
        intent.pendingFailureCount = 1;
        cancel(intent.refreshFuture);
        intent.refreshFuture = null;
        boolean notifyUnavailable = !intent.unavailableNotified;
        intent.unavailableNotified = true;
        if (!schedulePending(intent)) {
            List<ListenerNotification> result =
                terminalNotifications(intent, schedulingRejected());
            removeIntent(intent);
            return result;
        }
        List<ListenerNotification> result = new ArrayList<ListenerNotification>();
        if (notifyUnavailable) {
            result.addAll(unavailableNotifications(intent, exception, true));
        }
        return result;
    }
    
    private boolean schedulePending(final WatchIntent intent) {
        if (intent.pendingFuture != null) {
            return true;
        }
        long delay = retryPolicy.nextDelayMillis(
            Math.max(1, intent.pendingFailureCount), intent.requestKey);
        try {
            intent.pendingFuture = refreshExecutor.schedule(new Runnable() {
                
                @Override
                public void run() {
                    executePending(intent.clientWatchId);
                }
            }, delay, TimeUnit.MILLISECONDS);
            return true;
        } catch (RejectedExecutionException e) {
            LOGGER.warn("Agent Watch pending scheduling was rejected.", e);
            return false;
        }
    }
    
    private boolean scheduleRefresh(final WatchIntent intent, long delayMillis) {
        try {
            intent.refreshFuture = refreshExecutor.schedule(new Runnable() {
                
                @Override
                public void run() {
                    executeRefresh(intent.clientWatchId);
                }
            }, delayMillis, TimeUnit.MILLISECONDS);
            return true;
        } catch (RejectedExecutionException e) {
            LOGGER.warn("Agent Watch refresh scheduling was rejected.", e);
            return false;
        }
    }
    
    private List<ListenerNotification> snapshotNotifications(WatchIntent intent) {
        List<ListenerNotification> result = new ArrayList<ListenerNotification>();
        for (ListenerRegistration listener : intent.listeners.values()) {
            result.add(new ListenerNotification(intent, listener,
                new NacosAgentDiscoveryEvent(copy(intent.current)), true));
        }
        return result;
    }
    
    private List<ListenerNotification> unavailableNotifications(WatchIntent intent,
        NacosException exception, boolean requireActive) {
        List<ListenerNotification> result = new ArrayList<ListenerNotification>();
        NacosAgentDiscoveryEvent event = unavailable(exception);
        for (ListenerRegistration listener : intent.listeners.values()) {
            result.add(new ListenerNotification(intent, listener, event, requireActive));
        }
        return result;
    }
    
    private List<ListenerNotification> terminalNotifications(WatchIntent intent,
        NacosException exception) {
        return unavailableNotifications(intent, exception, false);
    }
    
    private void dispatch(List<ListenerNotification> notifications) {
        for (ListenerNotification notification : notifications) {
            dispatch(notification);
        }
    }
    
    private void dispatch(final ListenerNotification notification) {
        try {
            notification.listener.enqueue(notification);
        } catch (RuntimeException e) {
            LOGGER.warn("Agent Watch listener dispatch failed.", e);
        }
    }
    
    private void invoke(ListenerNotification notification) {
        synchronized (this) {
            if (closed) {
                return;
            }
            if (notification.requireActive
                && (!notification.listener.active
                    || intentsById.get(notification.intent.clientWatchId) != notification.intent)) {
                return;
            }
        }
        try {
            notification.listener.listener.onEvent(notification.event);
        } catch (Throwable throwable) {
            LOGGER.warn("Agent Watch listener failed.", throwable);
        }
    }
    
    private synchronized void removeIntent(WatchIntent intent) {
        intent.completeActivation(new NacosException(NacosException.CLIENT_DISCONNECT,
            "Agent Watch was closed before activation completed."));
        intentsById.remove(intent.clientWatchId);
        intentsByKey.remove(intent.requestKey);
        intent.state = WatchState.CLOSED;
        intent.dirty = false;
        cancel(intent.pendingFuture);
        cancel(intent.refreshFuture);
        intent.pendingFuture = null;
        intent.refreshFuture = null;
        watchTransport.stop(intent.clientWatchId);
        intent.transportActive = false;
        subscriptionCount -= intent.listeners.size();
        for (ListenerRegistration listener : intent.listeners.values()) {
            listener.active = false;
        }
        intent.listeners.clear();
    }
    
    private AgentDiscoveryRequest canonicalRequest(AgentReference reference,
        AgentDiscoveryFilter filter) throws NacosException {
        AgentDiscoveryRequest copy =
            AgentModelUtils.copyDiscoveryRequest(reference, filter, namespaceId);
        return AgentDiscoveryCanonicalizer.canonicalizeRequest(copy);
    }
    
    private DiscoveryAttempt discover(AgentDiscoveryRequest request) {
        try {
            AgentDiscoveryResult result = clientProxy.discoverAgent(request);
            if (result == null) {
                return DiscoveryAttempt.failed(
                    new NacosException(NacosException.NOT_FOUND, "Agent does not exist."));
            }
            AgentDiscoveryResult canonical =
                AgentDiscoveryCanonicalizer.canonicalizeResult(result);
            if (!Objects.equals(request.getNamespaceId(), canonical.getNamespaceId())
                || !Objects.equals(request.getReference().getAgentName(),
                    canonical.getAgentName())) {
                throw new IllegalArgumentException(
                    "Agent Discover result does not match the requested Agent identity");
            }
            return DiscoveryAttempt.success(canonical);
        } catch (NacosException e) {
            return DiscoveryAttempt.failed(e);
        } catch (IllegalArgumentException e) {
            return DiscoveryAttempt.failed(new NacosException(NacosException.SERVER_ERROR,
                "Agent Discover returned an invalid complete result.", e));
        }
    }
    
    private AgentWatchRegistration registration(WatchIntent intent) {
        return new AgentWatchRegistration(intent.clientWatchId, intent.request,
            intent.fingerprint);
    }
    
    private String fingerprint(AgentDiscoveryResult result) {
        return AgentDiscoveryCanonicalizer.fingerprint(result);
    }
    
    private AgentDiscoveryResult copy(AgentDiscoveryResult result) {
        return AgentModelUtils.copyDiscoveryResult(result);
    }
    
    private boolean isNotFound(NacosException exception) {
        return exception.getErrCode() == NacosException.NOT_FOUND
            || exception.getErrCode() == NacosException.RESOURCE_NOT_FOUND;
    }
    
    private boolean isTerminal(NacosException exception) {
        int code = exception.getErrCode();
        return code == NacosException.NO_RIGHT || code == NacosException.OVER_THRESHOLD
            || code == NacosException.CLIENT_OVER_THRESHOLD
            || code == NacosException.INVALID_PARAM
            || code == NacosException.CLIENT_INVALID_PARAM;
    }
    
    private NacosAgentDiscoveryEvent unavailable(NacosException exception) {
        return NacosAgentDiscoveryEvent.unavailable(exception.getErrCode(),
            exception.getErrMsg());
    }
    
    private void ensureCapacity(int additions) throws NacosApiException {
        if (subscriptionCount >= maxSubscriptions && additions > 0) {
            throw new NacosApiException(NacosException.CLIENT_OVER_THRESHOLD,
                ErrorCode.AGENT_DISCOVERY_SUBSCRIPTION_OVER_LIMIT,
                "Agent discovery subscription limit of " + maxSubscriptions
                    + " reached for this SDK Client.");
        }
    }
    
    private void ensureOpen() throws NacosException {
        if (closed) {
            throw new NacosException(NacosException.CLIENT_DISCONNECT,
                "Agent Watch manager has been shut down.");
        }
    }
    
    private NacosException schedulingRejected() {
        return new NacosException(NacosException.CLIENT_ERROR,
            "Agent Watch refresh scheduling was rejected.");
    }
    
    private static void cancel(ScheduledFuture<?> future) {
        if (future != null) {
            future.cancel(false);
        }
    }
    
    private static ExecutorService newCallbackExecutor() {
        return new ThreadPoolExecutor(CALLBACK_CORE_THREADS, CALLBACK_MAX_THREADS, 60L,
            TimeUnit.SECONDS, new ArrayBlockingQueue<Runnable>(CALLBACK_QUEUE_CAPACITY),
            new NameThreadFactory("com.alibaba.nacos.client.ai.agent.listener"),
            new ThreadPoolExecutor.AbortPolicy());
    }
    
    @Override
    public synchronized void shutdown() {
        if (closed) {
            return;
        }
        closed = true;
        List<WatchIntent> intents = new ArrayList<WatchIntent>(intentsById.values());
        for (WatchIntent intent : intents) {
            removeIntent(intent);
        }
        watchTransport.shutdown();
        refreshExecutor.shutdownNow();
        callbackExecutor.shutdownNow();
    }
    
    synchronized int intentCount() {
        return intentsById.size();
    }
    
    synchronized int subscriptionCount() {
        return subscriptionCount;
    }
    
    private enum WatchState {
        LOCAL_PENDING,
        ACTIVATING,
        ACTIVE,
        CLOSED
    }
    
    private final class WatchIntent {
        
        private final String requestKey;
        
        private final String clientWatchId = UUID.randomUUID().toString();
        
        private final AgentDiscoveryRequest request;
        
        private final Map<AbstractNacosAgentDiscoveryListener, ListenerRegistration> listeners =
            new IdentityHashMap<AbstractNacosAgentDiscoveryListener, ListenerRegistration>();
        
        private final AgentWatchTransportCallback callback = new AgentWatchTransportCallback() {
            
            @Override
            public boolean invalidate(String observedFingerprint, boolean forceRefresh) {
                return markDirty(clientWatchId, observedFingerprint, forceRefresh);
            }
            
            @Override
            public void unavailable(int errorCode, String errorMessage, boolean terminal) {
                markUnavailable(clientWatchId, errorCode, errorMessage, terminal);
            }
        };
        
        private AgentDiscoveryResult current;
        
        private String fingerprint;
        
        private WatchState state;
        
        private boolean transportActive;
        
        private boolean dirty;
        
        private boolean refreshing;
        
        private boolean unavailableNotified;
        
        private int pendingFailureCount;
        
        private int refreshFailureCount;
        
        private ScheduledFuture<?> pendingFuture;
        
        private ScheduledFuture<?> refreshFuture;
        
        private ActivationBarrier activationBarrier;
        
        private WatchIntent(String requestKey, AgentDiscoveryRequest request) {
            this.requestKey = requestKey;
            this.request = request;
        }
        
        private void addListener(AbstractNacosAgentDiscoveryListener listener) {
            listeners.put(listener, new ListenerRegistration(listener));
        }
        
        private void beginActivation() {
            state = WatchState.ACTIVATING;
            activationBarrier = new ActivationBarrier();
        }
        
        private void completeActivation(NacosException failure) {
            if (activationBarrier != null) {
                activationBarrier.complete(failure);
            }
        }
    }
    
    private static final class ActivationBarrier {
        
        private final CountDownLatch latch = new CountDownLatch(1);
        
        private NacosException failure;
        
        private boolean completed;
        
        private void complete(NacosException failure) {
            if (!completed) {
                completed = true;
                this.failure = failure;
                latch.countDown();
            }
        }
        
        private void await() throws InterruptedException {
            latch.await();
        }
    }
    
    private final class ListenerRegistration {
        
        private final AbstractNacosAgentDiscoveryListener listener;
        
        private final ArrayDeque<ListenerNotification> notifications =
            new ArrayDeque<ListenerNotification>();
        
        private boolean active = true;
        
        private boolean dispatching;
        
        private ListenerRegistration(AbstractNacosAgentDiscoveryListener listener) {
            this.listener = listener;
        }
        
        private void enqueue(ListenerNotification notification) {
            boolean submit;
            synchronized (this) {
                notifications.add(notification);
                submit = !dispatching;
                dispatching = true;
            }
            if (submit) {
                submitDelivery(true);
            }
        }
        
        private void submitDelivery(boolean propagateFailure) {
            try {
                callbackExecutor.execute(new Runnable() {
                    
                    @Override
                    public void run() {
                        deliverNext();
                    }
                });
            } catch (RuntimeException e) {
                synchronized (this) {
                    notifications.clear();
                    dispatching = false;
                }
                if (propagateFailure) {
                    throw e;
                }
                LOGGER.warn("Agent Watch listener dispatch failed.", e);
            }
        }
        
        private void deliverNext() {
            final ListenerNotification notification;
            synchronized (this) {
                notification = notifications.element();
            }
            Executor listenerExecutor = listener.getExecutor();
            if (listenerExecutor == null) {
                try {
                    invoke(notification);
                } finally {
                    completeDelivery(notification);
                }
                return;
            }
            try {
                listenerExecutor.execute(new Runnable() {
                    
                    @Override
                    public void run() {
                        try {
                            invoke(notification);
                        } finally {
                            completeDelivery(notification);
                        }
                    }
                });
            } catch (RuntimeException e) {
                LOGGER.warn("Agent Watch listener executor rejected dispatch.", e);
                completeDelivery(notification);
            }
        }
        
        private void completeDelivery(ListenerNotification delivered) {
            boolean submit;
            synchronized (this) {
                notifications.remove(delivered);
                submit = !notifications.isEmpty();
                if (!submit) {
                    dispatching = false;
                }
            }
            if (submit) {
                submitDelivery(false);
            }
        }
    }
    
    private static final class ListenerNotification {
        
        private final WatchIntent intent;
        
        private final ListenerRegistration listener;
        
        private final NacosAgentDiscoveryEvent event;
        
        private final boolean requireActive;
        
        private ListenerNotification(WatchIntent intent, ListenerRegistration listener,
            NacosAgentDiscoveryEvent event, boolean requireActive) {
            this.intent = intent;
            this.listener = listener;
            this.event = event;
            this.requireActive = requireActive;
        }
    }
    
    private static final class DiscoveryAttempt {
        
        private final AgentDiscoveryResult result;
        
        private final NacosException error;
        
        private DiscoveryAttempt(AgentDiscoveryResult result, NacosException error) {
            this.result = result;
            this.error = error;
        }
        
        private static DiscoveryAttempt success(AgentDiscoveryResult result) {
            return new DiscoveryAttempt(result, null);
        }
        
        private static DiscoveryAttempt failed(NacosException error) {
            return new DiscoveryAttempt(null, error);
        }
    }
}
