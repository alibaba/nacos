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
import com.alibaba.nacos.api.ai.model.agent.AgentDiscoveryFilter;
import com.alibaba.nacos.api.ai.model.agent.AgentDiscoveryRequest;
import com.alibaba.nacos.api.ai.model.agent.AgentDiscoveryResult;
import com.alibaba.nacos.api.ai.model.agent.AgentReference;
import com.alibaba.nacos.api.ai.utils.AgentDiscoveryCanonicalizer;
import com.alibaba.nacos.api.ai.utils.AgentWatchLogUtils;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.client.ai.remote.AgentClientProxy;
import com.alibaba.nacos.client.ai.watch.AgentWatchListenerDispatcher.ListenerRegistration;
import com.alibaba.nacos.client.ai.watch.AgentWatchSubscription.ActivationBarrier;
import com.alibaba.nacos.client.ai.watch.AgentWatchSubscription.WatchState;
import com.alibaba.nacos.client.ai.utils.AgentModelUtils;
import com.alibaba.nacos.common.executor.NameThreadFactory;
import com.alibaba.nacos.common.lifecycle.Closeable;
import com.alibaba.nacos.common.utils.LogRateLimiter;
import com.alibaba.nacos.client.utils.LogUtils;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;

/**
 * Transport-neutral Agent Watch manager.
 *
 * <p>The manager owns canonical identity, local capacity, complete-result cache, fingerprint,
 * pending/dirty refresh. It composes per-Watch state and a latest-state Listener dispatcher;
 * all three share this manager's monitor. A pluggable {@link AgentWatchTransport} owns
 * only Wire lifecycle and invalidation signals. Multiple Listener instances for one canonical
 * Discover request therefore share one Wire Intent.</p>
 *
 * @author Nacos
 */
public class AgentWatchManager implements Closeable {
    
    private static final Logger LOGGER = LogUtils.logger(AgentWatchManager.class);
    
    private static final LogRateLimiter BACKGROUND_LOG_LIMITER = new LogRateLimiter(60000L);
    
    private static final LogRateLimiter BINDING_LOG_LIMITER = new LogRateLimiter(60000L);
    
    private static final long MAXIMUM_RETRY_DELAY_MILLIS = 60000L;
    
    private final String namespaceId;
    
    private final AgentClientProxy clientProxy;
    
    private final ScheduledExecutorService refreshExecutor;
    
    private final AgentWatchListenerDispatcher listenerDispatcher;
    
    private final AgentWatchTaskScheduler taskScheduler;
    
    private final int maxSubscriptions;
    
    private final AgentWatchTransport watchTransport;
    
    private final AgentWatchRetryPolicy retryPolicy;
    
    private final Map<String, AgentWatchSubscription> intentsByKey =
        new LinkedHashMap<String, AgentWatchSubscription>();
    
    private final Map<String, AgentWatchSubscription> intentsById =
        new LinkedHashMap<String, AgentWatchSubscription>();
    
    private int subscriptionCount;
    
    private long notificationSequence;
    
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
            AgentWatchListenerDispatcher.newCallbackExecutor(), maxSubscriptions);
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
            AgentWatchListenerDispatcher.newCallbackExecutor(), maxSubscriptions, watchTransport,
            new AgentWatchRetryPolicy.Jittered(
                AiConstants.DEFAULT_AI_CACHE_UPDATE_INTERVAL, MAXIMUM_RETRY_DELAY_MILLIS));
    }
    
    AgentWatchManager(String namespaceId, AgentClientProxy clientProxy,
        ScheduledExecutorService refreshExecutor, ExecutorService callbackExecutor,
        int maxSubscriptions, AgentWatchTransport watchTransport,
        AgentWatchRetryPolicy retryPolicy) {
        this(namespaceId, clientProxy, refreshExecutor, callbackExecutor, maxSubscriptions,
            watchTransport, retryPolicy, new AgentWatchTaskScheduler());
    }
    
    AgentWatchManager(String namespaceId, AgentClientProxy clientProxy,
        ScheduledExecutorService refreshExecutor, ExecutorService callbackExecutor,
        int maxSubscriptions, AgentWatchTransport watchTransport,
        AgentWatchRetryPolicy retryPolicy, AgentWatchTaskScheduler taskScheduler) {
        this.taskScheduler = taskScheduler;
        if (maxSubscriptions < 1) {
            throw new IllegalArgumentException("maxSubscriptions must be greater than 0");
        }
        this.namespaceId = namespaceId;
        this.clientProxy = clientProxy;
        this.refreshExecutor = refreshExecutor;
        this.listenerDispatcher = new AgentWatchListenerDispatcher(this, intentsById.values(),
            refreshExecutor, callbackExecutor, taskScheduler);
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
        LOGGER.info("[RAD-WATCH] Client subscribe requested: {}",
            AgentWatchLogUtils.describeRequest(request));
        String requestKey = AgentDiscoveryCanonicalizer.canonicalRequestKey(request);
        AgentWatchSubscription initialization = null;
        AgentWatchSubscription waitingActivation = null;
        ActivationBarrier waitingBarrier = null;
        boolean waitingListenerAdded = false;
        synchronized (this) {
            ensureOpen();
            AgentWatchSubscription existing = intentsByKey.get(requestKey);
            if (existing != null) {
                if (!existing.listeners.containsKey(listener)) {
                    ensureCapacity(1);
                    existing.listeners.put(listener,
                        listenerDispatcher.register(existing, listener));
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
                AgentWatchSubscription intent = newSubscription(requestKey, request);
                intent.listeners.put(listener, listenerDispatcher.register(intent, listener));
                intent.beginActivation();
                intentsByKey.put(requestKey, intent);
                intentsById.put(intent.clientWatchId, intent);
                AgentWatchClientMetrics.intentAdded();
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
    
    /**
     * Accept a native subscription and supply its current state through the listener.
     *
     * @param reference Agent reference
     * @param filter optional filter
     * @param listener listener identity
     * @throws NacosException when the initial subscription is rejected
     */
    public void subscribeAndNotify(AgentReference reference, AgentDiscoveryFilter filter,
        AbstractNacosAgentDiscoveryListener listener) throws NacosException {
        subscribe(reference, filter, listener);
        notifyCurrent(reference, filter, listener);
    }
    
    /**
     * Initialize a listener only if no state notification has already been registered for it.
     * Native subscriptions and the legacy Card bridge share this serialized dispatcher.
     */
    void notifyCurrent(AgentReference reference, AgentDiscoveryFilter filter,
        AbstractNacosAgentDiscoveryListener listener) throws NacosException {
        String key =
            AgentDiscoveryCanonicalizer.canonicalRequestKey(canonicalRequest(reference, filter));
        ListenerRegistration registration;
        synchronized (this) {
            AgentWatchSubscription intent = intentsByKey.get(key);
            if (closed || intent == null || intent.state == WatchState.ACTIVATING) {
                return;
            }
            registration = intent.listeners.get(listener);
            if (!listenerDispatcher.prepareCurrent(registration)) {
                return;
            }
        }
        listenerDispatcher.dispatch(registration);
    }
    
    boolean containsListener(AgentDiscoveryRequest request,
        AbstractNacosAgentDiscoveryListener listener) {
        String key = AgentDiscoveryCanonicalizer.canonicalRequestKey(request);
        synchronized (this) {
            AgentWatchSubscription intent = intentsByKey.get(key);
            return !closed && intent != null && intent.listeners.containsKey(listener);
        }
    }
    
    private AgentDiscoveryResult initializeSynchronously(AgentWatchSubscription intent)
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
            intent.stateSequence = ++notificationSequence;
            intent.fingerprint = fingerprint(initial.result);
        }
        boolean activated = activateSynchronously(intent);
        if (activated) {
            LOGGER.info("[RAD-WATCH] Client Watch active: clientWatchId={}, fingerprint={}, {}",
                AgentWatchLogUtils.token(intent.clientWatchId),
                AgentWatchLogUtils.fingerprint(intent.fingerprint),
                AgentWatchLogUtils.describeResult(initial.result));
        }
        return activated ? copy(initial.result) : null;
    }
    
    private AgentDiscoveryResult initializePending(AgentWatchSubscription intent,
        NacosException exception)
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
        List<ListenerRegistration> notifications;
        synchronized (this) {
            if (closed || intentsById.get(intent.clientWatchId) != intent
                || intent.state != WatchState.ACTIVATING) {
                return null;
            }
            intent.setState(WatchState.LOCAL_PENDING);
            intent.unavailableNotified = true;
            intent.pendingFailureCount = 1;
            schedulePending(intent);
            intent.completeActivation(null);
            notifications = stateChanged(intent);
        }
        listenerDispatcher.dispatch(notifications);
        LOGGER.info("[RAD-WATCH] Client Watch pending: clientWatchId={}, errorCode={}, {}",
            AgentWatchLogUtils.token(intent.clientWatchId), exception.getErrCode(),
            AgentWatchLogUtils.describeRequest(intent.request));
        return null;
    }
    
    private AgentDiscoveryResult awaitActivation(AgentWatchSubscription intent,
        ActivationBarrier barrier,
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
    
    private synchronized void removeListener(AgentWatchSubscription intent,
        AbstractNacosAgentDiscoveryListener listener) {
        ListenerRegistration removed = intent.listeners.remove(listener);
        if (removed == null) {
            return;
        }
        listenerDispatcher.remove(removed);
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
        LOGGER.info("[RAD-WATCH] Client unsubscribe requested: {}",
            AgentWatchLogUtils.describeRequest(request));
        String requestKey = AgentDiscoveryCanonicalizer.canonicalRequestKey(request);
        synchronized (this) {
            AgentWatchSubscription intent = intentsByKey.get(requestKey);
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
        AgentWatchSubscription intent = intentsById.get(clientWatchId);
        if (closed || intent == null || intent.state == WatchState.CLOSED) {
            return false;
        }
        LOGGER.info("[RAD-WATCH] Client invalidation received: clientWatchId={}, "
            + "observedFingerprint={}, forceRefresh={}, {}",
            AgentWatchLogUtils.token(clientWatchId),
            AgentWatchLogUtils.fingerprint(observedFingerprint), forceRefresh,
            AgentWatchLogUtils.describeRequest(intent.request));
        if (!forceRefresh && observedFingerprint != null && intent.fingerprint != null
            && observedFingerprint.equals(intent.fingerprint)) {
            return true;
        }
        if (observedFingerprint != null && intent.fingerprint != null
            && !observedFingerprint.equals(intent.fingerprint)) {
            AgentWatchClientMetrics.record(AgentWatchClientMetrics.Event.FINGERPRINT_MISMATCH,
                AgentWatchClientMetrics.Result.MISMATCH);
        }
        intent.setDirty(true);
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
     * @param terminal whether the remote binding ended; local intent is retained
     */
    public void markUnavailable(String clientWatchId, int errorCode, String errorMessage,
        boolean terminal) {
        synchronized (this) {
            AgentWatchSubscription intent = intentsById.get(clientWatchId);
            if (closed || intent == null) {
                return;
            }
            handleActivationFailure(intent, new NacosException(errorCode, errorMessage));
        }
    }
    
    private boolean activateSynchronously(AgentWatchSubscription intent) throws NacosException {
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
                stopTransport(intent.clientWatchId);
                return true;
            }
            intent.setState(WatchState.ACTIVE);
            intent.transportActive = true;
            intent.completeActivation(null);
            if (intent.dirty) {
                scheduleRefresh(intent, 0L);
            }
        }
        return true;
    }
    
    private void executePending(String clientWatchId) {
        AgentWatchSubscription intent;
        AgentDiscoveryRequest request;
        synchronized (this) {
            intent = intentsById.get(clientWatchId);
            if (closed || intent == null || intent.state != WatchState.LOCAL_PENDING) {
                return;
            }
            intent.pendingFuture = null;
            intent.pendingDiscoverRunning = true;
            request = intent.request;
        }
        DiscoveryAttempt attempt = discover(request);
        if (attempt.result == null) {
            handlePendingFailure(intent, attempt.error);
            return;
        }
        List<ListenerRegistration> notifications = new ArrayList<ListenerRegistration>();
        synchronized (this) {
            intent.pendingDiscoverRunning = false;
            if (closed || intentsById.get(clientWatchId) != intent
                || intent.state != WatchState.LOCAL_PENDING) {
                return;
            }
            String latestFingerprint = fingerprint(attempt.result);
            boolean changed = intent.unavailableNotified
                || !latestFingerprint.equals(intent.fingerprint);
            intent.current = attempt.result;
            intent.fingerprint = latestFingerprint;
            intent.beginActivation();
            intent.unavailableNotified = false;
            if (changed) {
                notifications.addAll(stateChanged(intent));
            }
        }
        // A successful authorized Discover is a data fact even when binding recovery fails.
        listenerDispatcher.dispatch(notifications);
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
        synchronized (this) {
            if (closed || intentsById.get(clientWatchId) != intent
                || intent.state != WatchState.ACTIVATING) {
                intent.completeActivation(new NacosException(NacosException.CLIENT_DISCONNECT,
                    "Agent Watch activation was canceled."));
                stopTransport(clientWatchId);
                return;
            }
            intent.setState(WatchState.ACTIVE);
            intent.transportActive = true;
            intent.completeActivation(null);
            recovered("BINDING", intent, intent.pendingFailureCount);
            intent.pendingFailureCount = 0;
            if (intent.dirty) {
                scheduleRefresh(intent, 0L);
            }
        }
    }
    
    private void handlePendingFailure(AgentWatchSubscription intent, NacosException exception) {
        List<ListenerRegistration> notifications = new ArrayList<ListenerRegistration>();
        synchronized (this) {
            intent.pendingDiscoverRunning = false;
            if (closed || intentsById.get(intent.clientWatchId) != intent
                || intent.state != WatchState.LOCAL_PENDING) {
                return;
            }
            if (isNotFound(exception)) {
                intent.current = null;
                intent.fingerprint = null;
                if (!intent.unavailableNotified) {
                    intent.unavailableNotified = true;
                    notifications.addAll(stateChanged(intent));
                }
            }
            intent.pendingFailureCount = Math.min(30, intent.pendingFailureCount + 1);
            schedulePending(intent);
            warn("PENDING_DISCOVER", intent, exception, intent.pendingFailureCount,
                intent.pendingDelayMillis);
        }
        listenerDispatcher.dispatch(notifications);
    }
    
    private void handleActivationFailure(AgentWatchSubscription intent, NacosException exception) {
        synchronized (this) {
            if (closed || intentsById.get(intent.clientWatchId) != intent) {
                intent.completeActivation(new NacosException(NacosException.CLIENT_DISCONNECT,
                    "Agent Watch activation was canceled."));
                return;
            }
            intent.completeActivation(null);
            stopTransport(intent.clientWatchId);
            intent.transportActive = false;
            intent.refreshing = false;
            cancel(intent.refreshFuture);
            intent.refreshFuture = null;
            intent.setState(WatchState.LOCAL_PENDING);
            intent.pendingFailureCount = Math.min(30, intent.pendingFailureCount + 1);
            schedulePending(intent);
            warn("BINDING", intent, exception, intent.pendingFailureCount,
                intent.pendingDelayMillis);
        }
    }
    
    private void executeRefresh(String clientWatchId) {
        AgentWatchSubscription intent;
        AgentDiscoveryRequest request;
        ActivationBarrier activation;
        synchronized (this) {
            intent = intentsById.get(clientWatchId);
            if (closed || intent == null || intent.state != WatchState.ACTIVE
                || !intent.dirty) {
                return;
            }
            intent.refreshFuture = null;
            intent.refreshing = true;
            intent.setDirty(false);
            request = intent.request;
            activation = intent.activationBarrier;
        }
        DiscoveryAttempt attempt = discover(request);
        if (attempt.result != null) {
            handleRefreshSuccess(intent, attempt.result, activation);
        } else {
            handleRefreshFailure(intent, attempt.error, activation);
        }
    }
    
    private void handleRefreshSuccess(AgentWatchSubscription intent, AgentDiscoveryResult latest,
        ActivationBarrier activation) {
        List<ListenerRegistration> notifications = new ArrayList<ListenerRegistration>();
        synchronized (this) {
            if (closed || intentsById.get(intent.clientWatchId) != intent
                || intent.state != WatchState.ACTIVE || intent.activationBarrier != activation) {
                return;
            }
            String latestFingerprint = fingerprint(latest);
            boolean changed = intent.current == null
                || !latestFingerprint.equals(intent.fingerprint);
            AgentWatchClientMetrics.record(AgentWatchClientMetrics.Event.DISCOVER_REFRESH,
                changed ? AgentWatchClientMetrics.Result.SUCCESS
                    : AgentWatchClientMetrics.Result.UNCHANGED);
            intent.current = latest;
            intent.fingerprint = latestFingerprint;
            recovered("DISCOVER_REFRESH", intent, intent.refreshFailureCount);
            intent.refreshFailureCount = 0;
            intent.unavailableNotified = false;
            try {
                watchTransport.update(registration(intent));
            } catch (RuntimeException e) {
                handleActivationFailure(intent, new NacosException(NacosException.CLIENT_ERROR,
                    "Agent Watch transport state update failed.", e));
            }
            if (changed) {
                LOGGER.info("[RAD-WATCH] Client Discover refresh completed: clientWatchId={}, "
                    + "changed=true, fingerprint={}, listenerCount={}, {}",
                    AgentWatchLogUtils.token(intent.clientWatchId),
                    AgentWatchLogUtils.fingerprint(latestFingerprint), intent.listeners.size(),
                    AgentWatchLogUtils.describeResult(latest));
                notifications.addAll(stateChanged(intent));
            } else {
                LOGGER.debug("[RAD-WATCH] Client Discover refresh completed: clientWatchId={}, "
                    + "changed=false, fingerprint={}, {}",
                    AgentWatchLogUtils.token(intent.clientWatchId),
                    AgentWatchLogUtils.fingerprint(latestFingerprint),
                    AgentWatchLogUtils.describeRequest(intent.request));
            }
            intent.refreshing = false;
            if (intent.dirty && intent.state == WatchState.ACTIVE) {
                scheduleRefresh(intent, 0L);
            }
        }
        listenerDispatcher.dispatch(notifications);
    }
    
    private void handleRefreshFailure(AgentWatchSubscription intent, NacosException exception,
        ActivationBarrier activation) {
        List<ListenerRegistration> notifications = new ArrayList<ListenerRegistration>();
        synchronized (this) {
            if (closed || intentsById.get(intent.clientWatchId) != intent
                || intent.state != WatchState.ACTIVE || intent.activationBarrier != activation) {
                return;
            }
            intent.refreshing = false;
            AgentWatchClientMetrics.record(AgentWatchClientMetrics.Event.DISCOVER_REFRESH,
                AgentWatchClientMetrics.Result.FAILED);
            if (isNotFound(exception)) {
                notifications.addAll(enterPending(intent, exception));
            } else {
                intent.setDirty(true);
                intent.refreshFailureCount = Math.min(30, intent.refreshFailureCount + 1);
                long delay = retryPolicy.nextDelayMillis(intent.refreshFailureCount,
                    intent.requestKey);
                scheduleRefresh(intent, delay);
                warn("DISCOVER_REFRESH", intent, exception,
                    intent.refreshFailureCount, delay);
            }
        }
        listenerDispatcher.dispatch(notifications);
    }
    
    private List<ListenerRegistration> enterPending(AgentWatchSubscription intent,
        NacosException exception) {
        if (intent.transportActive) {
            stopTransport(intent.clientWatchId);
            intent.transportActive = false;
        }
        intent.setState(WatchState.LOCAL_PENDING);
        intent.current = null;
        intent.fingerprint = null;
        intent.setDirty(false);
        intent.refreshFailureCount = 0;
        intent.pendingFailureCount = 1;
        cancel(intent.refreshFuture);
        intent.refreshFuture = null;
        boolean notifyUnavailable = !intent.unavailableNotified;
        intent.unavailableNotified = true;
        schedulePending(intent);
        List<ListenerRegistration> result = new ArrayList<ListenerRegistration>();
        if (notifyUnavailable) {
            result.addAll(stateChanged(intent));
        }
        return result;
    }
    
    private boolean schedulePending(final AgentWatchSubscription intent) {
        if (intent.pendingFuture != null || intent.pendingDiscoverRunning) {
            return true;
        }
        long delay = retryPolicy.nextDelayMillis(
            Math.max(1, intent.pendingFailureCount), intent.requestKey);
        intent.pendingDelayMillis = delay;
        intent.pendingFuture = taskScheduler.schedule(refreshExecutor,
            () -> executePending(intent.clientWatchId), delay);
        AgentWatchClientMetrics.record(AgentWatchClientMetrics.Event.RETRY,
            AgentWatchClientMetrics.Result.SCHEDULED);
        return true;
    }
    
    private boolean scheduleRefresh(final AgentWatchSubscription intent, long delayMillis) {
        intent.refreshFuture = taskScheduler.schedule(refreshExecutor,
            () -> executeRefresh(intent.clientWatchId), delayMillis);
        if (delayMillis > 0L) {
            AgentWatchClientMetrics.record(AgentWatchClientMetrics.Event.RETRY,
                AgentWatchClientMetrics.Result.SCHEDULED);
        }
        return true;
    }
    
    private List<ListenerRegistration> stateChanged(AgentWatchSubscription intent) {
        intent.stateSequence = ++notificationSequence;
        return listenerDispatcher.prepareNotifications(intent);
    }
    
    private void stopTransport(String clientWatchId) {
        try {
            watchTransport.stop(clientWatchId);
        } catch (RuntimeException e) {
            if (BINDING_LOG_LIMITER.tryAcquire()) {
                LOGGER.warn("[RAD-WATCH] Binding cleanup failed: clientWatchId={}",
                    AgentWatchLogUtils.token(clientWatchId), e);
            }
        }
    }
    
    private synchronized void removeIntent(AgentWatchSubscription intent) {
        intent.completeActivation(new NacosException(NacosException.CLIENT_DISCONNECT,
            "Agent Watch was closed before activation completed."));
        boolean tracked = intentsById.remove(intent.clientWatchId) == intent;
        intentsByKey.remove(intent.requestKey);
        intent.setState(WatchState.CLOSED);
        intent.setDirty(false);
        if (tracked) {
            AgentWatchClientMetrics.intentRemoved();
        }
        cancel(intent.pendingFuture);
        cancel(intent.refreshFuture);
        intent.pendingFuture = null;
        intent.refreshFuture = null;
        stopTransport(intent.clientWatchId);
        intent.transportActive = false;
        subscriptionCount -= intent.listeners.size();
        for (ListenerRegistration listener : intent.listeners.values()) {
            listenerDispatcher.remove(listener);
        }
        intent.listeners.clear();
        intent.current = null;
        listenerDispatcher.stopCheckingIfEmpty();
        LOGGER.info("[RAD-WATCH] Client Watch removed: clientWatchId={}, {}",
            AgentWatchLogUtils.token(intent.clientWatchId),
            AgentWatchLogUtils.describeRequest(intent.request));
    }
    
    private AgentWatchSubscription newSubscription(String key, AgentDiscoveryRequest request) {
        String clientWatchId = UUID.randomUUID().toString();
        AgentWatchTransportCallback callback = new AgentWatchTransportCallback() {
            
            @Override
            public boolean invalidate(String observedFingerprint, boolean forceRefresh) {
                return markDirty(clientWatchId, observedFingerprint, forceRefresh);
            }
            
            @Override
            public void unavailable(int errorCode, String errorMessage, boolean terminal) {
                markUnavailable(clientWatchId, errorCode, errorMessage, terminal);
            }
        };
        return new AgentWatchSubscription(key, clientWatchId, request, callback);
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
                    new NacosApiException(NacosException.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
                        "Agent reference does not exist."));
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
        } catch (RuntimeException e) {
            return DiscoveryAttempt.failed(new NacosException(NacosException.SERVER_ERROR,
                "Agent Discover returned an invalid complete result.", e));
        }
    }
    
    private AgentWatchRegistration registration(AgentWatchSubscription intent) {
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
        return exception instanceof NacosApiException
            && ((NacosApiException) exception).getDetailErrCode() == ErrorCode.RESOURCE_NOT_FOUND
                .getCode();
    }
    
    private void ensureCapacity(int additions) throws NacosApiException {
        if (subscriptionCount >= maxSubscriptions && additions > 0) {
            AgentWatchClientMetrics.record(AgentWatchClientMetrics.Event.CAPACITY_REJECTION,
                AgentWatchClientMetrics.Result.REJECTED);
            LOGGER.warn("[RAD-WATCH] Client subscribe rejected: reason=CAPACITY, current={}, "
                + "limit={}, namespace={}", subscriptionCount, maxSubscriptions,
                namespaceId);
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
    
    private static void cancel(ScheduledFuture<?> future) {
        if (future != null) {
            future.cancel(false);
        }
    }
    
    private void warn(String operation, AgentWatchSubscription intent,
        Throwable failure, int failures, long retryDelayMillis) {
        LogRateLimiter limiter = "BINDING".equals(operation)
            ? BINDING_LOG_LIMITER : BACKGROUND_LOG_LIMITER;
        if (limiter.tryAcquire()) {
            int code = failure instanceof NacosException
                ? ((NacosException) failure).getErrCode() : NacosException.CLIENT_ERROR;
            Integer detail = failure instanceof NacosApiException
                ? ((NacosApiException) failure).getDetailErrCode() : null;
            LOGGER.warn("[RAD-WATCH] Operation failed: operation={}, clientWatchId={}, "
                + "listener={}, errorCode={}, detailCode={}, failures={}, retryDelayMillis={}, {}",
                operation, AgentWatchLogUtils.token(intent.clientWatchId),
                "none", code, detail, failures,
                retryDelayMillis, AgentWatchLogUtils.describeRequest(intent.request), failure);
        }
    }
    
    private void recovered(String operation, AgentWatchSubscription intent, int failures) {
        if (failures > 0) {
            LOGGER.info("[RAD-WATCH] Background operation recovered: operation={}, "
                + "clientWatchId={}, failures={}, {}", operation,
                AgentWatchLogUtils.token(intent.clientWatchId), failures,
                AgentWatchLogUtils.describeRequest(intent.request));
        }
    }
    
    @Override
    public synchronized void shutdown() {
        if (closed) {
            return;
        }
        closed = true;
        List<AgentWatchSubscription> intents =
            new ArrayList<AgentWatchSubscription>(intentsById.values());
        for (AgentWatchSubscription intent : intents) {
            removeIntent(intent);
        }
        watchTransport.shutdown();
        taskScheduler.shutdown();
        refreshExecutor.shutdownNow();
        listenerDispatcher.shutdown();
    }
    
    synchronized int intentCount() {
        return intentsById.size();
    }
    
    synchronized int subscriptionCount() {
        return subscriptionCount;
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
