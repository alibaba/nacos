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

import com.alibaba.nacos.api.ai.model.rad.AgentWatchBatchItem;
import com.alibaba.nacos.api.ai.model.rad.AgentWatchBatchRequest;
import com.alibaba.nacos.api.ai.model.rad.AgentWatchBatchResponse;
import com.alibaba.nacos.api.ai.utils.AgentWatchLogUtils;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.client.ai.remote.AgentHttpWatchClient;
import com.alibaba.nacos.client.utils.LogUtils;
import com.alibaba.nacos.common.executor.NameThreadFactory;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * One request-scoped HTTP batch long-poll coordinator for an Agent SDK namespace.
 *
 * <p>The transport owns only Wire registrations, local generation, and request cancellation.
 * Changed ids are handed to {@link AgentWatchManager} callbacks, which perform authoritative
 * Discover refreshes outside the HTTP request thread.</p>
 *
 * @author Nacos
 */
public final class HttpAgentWatchTransport implements AgentWatchTransport {
    
    static final long DEFAULT_LONG_POLL_TIMEOUT_MILLIS = 30000L;
    
    private static final long DEFAULT_RETRY_DELAY_MILLIS = 1000L;
    
    private static final int DEFAULT_FAILURES_BEFORE_FALLBACK = 3;
    
    private static final Logger LOGGER = LogUtils.logger(HttpAgentWatchTransport.class);
    
    private final AgentHttpWatchClient watchClient;
    
    private final ExecutorService requestExecutor;
    
    private final ScheduledExecutorService retryExecutor;
    
    private final long longPollTimeoutMillis;
    
    private final long retryDelayMillis;
    
    private final int failuresBeforeFallback;
    
    private final Map<String, WireWatch> watches = new LinkedHashMap<String, WireWatch>();
    
    private final Set<String> awaitingRefresh = new HashSet<String>();
    
    private final Set<String> pendingAdditions = new LinkedHashSet<String>();
    
    private WireLifecycleListener lifecycleListener;
    
    private Future<?> currentRequest;
    
    private ScheduledFuture<?> retryFuture;
    
    private long generation;
    
    private int consecutiveFailures;
    
    private boolean available = true;
    
    private boolean closed;
    
    /**
     * Create an HTTP Watch coordinator with bounded request and retry executors.
     *
     * @param watchClient HTTP Watch binding
     */
    public HttpAgentWatchTransport(AgentHttpWatchClient watchClient) {
        this(watchClient, createRequestExecutor(),
            new ScheduledThreadPoolExecutor(1,
                new NameThreadFactory("com.alibaba.nacos.client.ai.agent.watch.http.retry")),
            DEFAULT_LONG_POLL_TIMEOUT_MILLIS, DEFAULT_RETRY_DELAY_MILLIS,
            DEFAULT_FAILURES_BEFORE_FALLBACK);
    }
    
    HttpAgentWatchTransport(AgentHttpWatchClient watchClient, ExecutorService requestExecutor,
        ScheduledExecutorService retryExecutor, long longPollTimeoutMillis,
        long retryDelayMillis, int failuresBeforeFallback) {
        if (longPollTimeoutMillis < 1000L || longPollTimeoutMillis > 60000L) {
            throw new IllegalArgumentException(
                "longPollTimeoutMillis must be between 1000 and 60000");
        }
        if (retryDelayMillis < 0L) {
            throw new IllegalArgumentException("retryDelayMillis must not be negative");
        }
        if (failuresBeforeFallback < 1) {
            throw new IllegalArgumentException("failuresBeforeFallback must be greater than 0");
        }
        this.watchClient = watchClient;
        this.requestExecutor = requestExecutor;
        this.retryExecutor = retryExecutor;
        this.longPollTimeoutMillis = longPollTimeoutMillis;
        this.retryDelayMillis = retryDelayMillis;
        this.failuresBeforeFallback = failuresBeforeFallback;
    }
    
    void setLifecycleListener(WireLifecycleListener lifecycleListener) {
        this.lifecycleListener = lifecycleListener;
    }
    
    synchronized boolean isAvailable() {
        return available && !closed;
    }
    
    @Override
    public void start(AgentWatchRegistration registration,
        AgentWatchTransportCallback callback) throws NacosException {
        synchronized (this) {
            ensureOpen();
            if (!available) {
                throw new NacosException(NacosException.SERVER_NOT_IMPLEMENTED,
                    "Agent HTTP Watch binding is unavailable.");
            }
            WireWatch existing = watches.get(registration.getClientWatchId());
            if (existing == null) {
                watches.put(registration.getClientWatchId(),
                    new WireWatch(registration, callback));
                pendingAdditions.add(registration.getClientWatchId());
            } else {
                existing.registration = registration;
            }
            awaitingRefresh.remove(registration.getClientWatchId());
            LOGGER.info("[RAD-WATCH] Client HTTP Watch registered: clientWatchId={}, {}",
                AgentWatchLogUtils.token(registration.getClientWatchId()),
                AgentWatchLogUtils.describeRequest(registration.getDiscoveryRequest()));
            advanceGenerationAndDispatch();
        }
    }
    
    @Override
    public synchronized void update(AgentWatchRegistration registration) {
        WireWatch watch = watches.get(registration.getClientWatchId());
        if (closed || !available || watch == null) {
            return;
        }
        watch.registration = registration;
        awaitingRefresh.remove(registration.getClientWatchId());
        advanceGenerationAndDispatch();
    }
    
    @Override
    public synchronized void stop(String clientWatchId) {
        if (watches.remove(clientWatchId) == null) {
            return;
        }
        awaitingRefresh.remove(clientWatchId);
        pendingAdditions.remove(clientWatchId);
        LOGGER.info("[RAD-WATCH] Client HTTP Watch stopped: clientWatchId={}",
            AgentWatchLogUtils.token(clientWatchId));
        advanceGenerationAndDispatch();
    }
    
    @Override
    public void shutdown() {
        synchronized (this) {
            if (closed) {
                return;
            }
            closed = true;
            available = false;
            watches.clear();
            awaitingRefresh.clear();
            pendingAdditions.clear();
            cancelRequest(currentRequest);
            cancel(retryFuture);
            currentRequest = null;
            retryFuture = null;
        }
        requestExecutor.shutdownNow();
        retryExecutor.shutdownNow();
    }
    
    private void advanceGenerationAndDispatch() {
        generation++;
        consecutiveFailures = 0;
        cancelRequest(currentRequest);
        cancel(retryFuture);
        currentRequest = null;
        retryFuture = null;
        dispatch(generation);
    }
    
    private synchronized void dispatch(long expectedGeneration) {
        if (closed || !available || generation != expectedGeneration
            || currentRequest != null) {
            return;
        }
        AgentWatchBatchRequest request = buildRequest(expectedGeneration);
        if (request.getWatches().isEmpty()) {
            return;
        }
        try {
            LOGGER.debug("[RAD-WATCH] Client HTTP long poll started: generation={}, "
                + "watchCount={}, watchIds={}", expectedGeneration,
                request.getWatches().size(), watchIds(request));
            currentRequest = requestExecutor.submit(new Runnable() {
                
                @Override
                public void run() {
                    execute(request);
                }
            });
        } catch (RejectedExecutionException e) {
            handleFailure(expectedGeneration, new NacosException(NacosException.CLIENT_ERROR,
                "Agent HTTP Watch request executor rejected the generation.", e));
        }
    }
    
    private AgentWatchBatchRequest buildRequest(long expectedGeneration) {
        List<AgentWatchBatchItem> items = new ArrayList<AgentWatchBatchItem>(watches.size());
        for (WireWatch watch : watches.values()) {
            if (awaitingRefresh.contains(watch.registration.getClientWatchId())) {
                continue;
            }
            AgentWatchBatchItem item = new AgentWatchBatchItem();
            item.setClientWatchId(watch.registration.getClientWatchId());
            item.setDiscoveryRequest(watch.registration.getDiscoveryRequest());
            item.setMaterializedFingerprint(watch.registration.getMaterializedFingerprint());
            items.add(item);
        }
        AgentWatchBatchRequest result = new AgentWatchBatchRequest();
        result.setGeneration(expectedGeneration);
        result.setTimeoutMillis(longPollTimeoutMillis);
        result.setWatches(items);
        return result;
    }
    
    private void execute(AgentWatchBatchRequest request) {
        long startedNanos = System.nanoTime();
        try {
            AgentWatchBatchResponse response = watchClient.watchAgents(request);
            handleResponse(request.getGeneration(), response, elapsedMillis(startedNanos));
        } catch (NacosException e) {
            handleFailure(request.getGeneration(), e);
        } catch (RuntimeException e) {
            handleFailure(request.getGeneration(), new NacosException(NacosException.SERVER_ERROR,
                "Agent HTTP Watch request failed.", e));
        }
    }
    
    private void handleResponse(long requestGeneration, AgentWatchBatchResponse response,
        long durationMillis) {
        List<WireWatch> changed = new ArrayList<WireWatch>();
        NacosException invalidResponse = null;
        synchronized (this) {
            if (!acceptResponse(requestGeneration)) {
                return;
            }
            currentRequest = null;
            if (response == null || response.getGeneration() != requestGeneration) {
                invalidResponse = new NacosException(NacosException.SERVER_ERROR,
                    "Agent HTTP Watch returned a mismatched generation.");
            } else if (response.isChanged() && response.getChangedClientWatchIds() != null) {
                consecutiveFailures = 0;
                pendingAdditions.clear();
                for (String clientWatchId : response.getChangedClientWatchIds()) {
                    WireWatch watch = watches.get(clientWatchId);
                    if (watch != null && awaitingRefresh.add(clientWatchId)) {
                        changed.add(watch);
                    }
                }
            } else {
                consecutiveFailures = 0;
                pendingAdditions.clear();
            }
        }
        if (invalidResponse != null) {
            handleFailure(requestGeneration, invalidResponse);
            return;
        }
        if (response.isChanged()) {
            LOGGER.info("[RAD-WATCH] Client HTTP invalidation received: generation={}, "
                + "changedWatchIds={}, durationMillis={}", requestGeneration,
                AgentWatchLogUtils.tokens(response.getChangedClientWatchIds()), durationMillis);
        } else {
            LOGGER.debug("[RAD-WATCH] Client HTTP long poll completed: generation={}, "
                + "result=TIMEOUT, durationMillis={}", requestGeneration, durationMillis);
        }
        for (WireWatch watch : changed) {
            boolean accepted = watch.callback.invalidate(null, true);
            if (!accepted) {
                synchronized (this) {
                    awaitingRefresh.remove(watch.registration.getClientWatchId());
                }
            }
        }
        synchronized (this) {
            if (acceptResponse(requestGeneration)) {
                dispatch(requestGeneration);
            }
        }
    }
    
    private void handleFailure(long requestGeneration, NacosException exception) {
        WireLifecycleListener listener = null;
        WireWatch rejectedAddition = null;
        synchronized (this) {
            if (!acceptResponse(requestGeneration)) {
                return;
            }
            currentRequest = null;
            if (isCapacity(exception) && !pendingAdditions.isEmpty()) {
                String rejectedId = latestPendingAddition();
                pendingAdditions.remove(rejectedId);
                awaitingRefresh.remove(rejectedId);
                rejectedAddition = watches.remove(rejectedId);
                advanceGenerationAndDispatch();
            } else if (requiresFallback(exception)
                || ++consecutiveFailures >= failuresBeforeFallback) {
                available = false;
                cancel(retryFuture);
                retryFuture = null;
                listener = lifecycleListener;
            } else {
                scheduleRetry(requestGeneration, exception);
            }
        }
        LOGGER.warn("[RAD-WATCH] Client HTTP long poll failed: generation={}, errorCode={}, "
            + "errorType={}, consecutiveFailures={}, fallback={}", requestGeneration,
            exception.getErrCode(), exception.getClass().getSimpleName(), consecutiveFailures,
            listener != null);
        if (rejectedAddition != null) {
            rejectedAddition.callback.unavailable(exception.getErrCode(),
                exception.getErrMsg(), true);
        }
        if (listener != null) {
            listener.onWireUnavailable(exception);
        }
    }
    
    private void scheduleRetry(long requestGeneration, NacosException exception) {
        if (closed || !available || generation != requestGeneration || retryFuture != null) {
            return;
        }
        LOGGER.debug("Agent HTTP Watch generation {} will retry after a transport failure.",
            requestGeneration, exception);
        try {
            retryFuture = retryExecutor.schedule(new Runnable() {
                
                @Override
                public void run() {
                    synchronized (HttpAgentWatchTransport.this) {
                        retryFuture = null;
                    }
                    dispatch(requestGeneration);
                }
            }, retryDelayMillis, TimeUnit.MILLISECONDS);
        } catch (RejectedExecutionException e) {
            available = false;
            WireLifecycleListener listener = lifecycleListener;
            if (listener != null) {
                listener.onWireUnavailable(new NacosException(NacosException.CLIENT_ERROR,
                    "Agent HTTP Watch retry executor rejected the generation.", e));
            }
        }
    }
    
    private boolean acceptResponse(long requestGeneration) {
        return !closed && available && generation == requestGeneration;
    }
    
    private boolean requiresFallback(NacosException exception) {
        int code = exception.getErrCode();
        return code == NacosException.NOT_FOUND || code == NacosException.RESOURCE_NOT_FOUND
            || code == NacosException.SERVER_NOT_IMPLEMENTED || code == NacosException.NO_HANDLER
            || code == NacosException.NO_RIGHT || code == NacosException.INVALID_PARAM
            || code == NacosException.CLIENT_INVALID_PARAM || isCapacity(exception);
    }
    
    private boolean isCapacity(NacosException exception) {
        return exception instanceof NacosApiException
            && ((NacosApiException) exception)
                .getDetailErrCode() == ErrorCode.AGENT_DISCOVERY_SUBSCRIPTION_OVER_LIMIT.getCode();
    }
    
    private void ensureOpen() throws NacosException {
        if (closed) {
            throw new NacosException(NacosException.CLIENT_DISCONNECT,
                "Agent HTTP Watch transport has been shut down.");
        }
    }
    
    private String latestPendingAddition() {
        String result = null;
        for (String each : pendingAdditions) {
            result = each;
        }
        return result;
    }
    
    private String watchIds(AgentWatchBatchRequest request) {
        List<String> ids = new ArrayList<String>(request.getWatches().size());
        for (AgentWatchBatchItem each : request.getWatches()) {
            ids.add(each.getClientWatchId());
        }
        return AgentWatchLogUtils.tokens(ids);
    }
    
    private long elapsedMillis(long startedNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos);
    }
    
    private void cancelRequest(Future<?> future) {
        cancel(future);
        if (requestExecutor instanceof ThreadPoolExecutor) {
            ((ThreadPoolExecutor) requestExecutor).purge();
        }
    }
    
    private static ExecutorService createRequestExecutor() {
        return new ThreadPoolExecutor(2, 2, 0L, TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<Runnable>(1),
            new NameThreadFactory("com.alibaba.nacos.client.ai.agent.watch.http"));
    }
    
    private static void cancel(Future<?> future) {
        if (future != null) {
            future.cancel(true);
        }
    }
    
    /** Receives one binding-wide HTTP Watch availability transition. */
    interface WireLifecycleListener {
        
        /**
         * Handle an unsupported, rejected, or persistently failing HTTP Watch binding.
         *
         * @param exception last transport failure
         */
        void onWireUnavailable(NacosException exception);
    }
    
    private static final class WireWatch {
        
        private AgentWatchRegistration registration;
        
        private final AgentWatchTransportCallback callback;
        
        private WireWatch(AgentWatchRegistration registration,
            AgentWatchTransportCallback callback) {
            this.registration = registration;
            this.callback = callback;
        }
    }
}
