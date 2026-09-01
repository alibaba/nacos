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

import com.alibaba.nacos.ai.constant.Constants;
import com.alibaba.nacos.ai.service.VisibilityHelper;
import com.alibaba.nacos.ai.service.agent.runtime.AgentHttpClientLifecycleService;
import com.alibaba.nacos.api.ai.model.rad.AgentDiscoveryRequest;
import com.alibaba.nacos.api.ai.model.rad.AgentWatchBatchItem;
import com.alibaba.nacos.api.ai.model.rad.AgentWatchBatchRequest;
import com.alibaba.nacos.api.ai.model.rad.AgentWatchBatchResponse;
import com.alibaba.nacos.api.ai.utils.AgentWatchLogUtils;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.common.utils.LogRateLimiter;
import com.alibaba.nacos.core.utils.GlobalExecutor;
import com.alibaba.nacos.sys.env.EnvUtil;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.unit.DataSize;
import org.springframework.web.context.request.async.DeferredResult;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Request-scoped HTTP Batch Long Poll transport for Agent Watch fingerprints.
 *
 * <p>The service stores only this request's opaque ids, canonical projection keys, submitted
 * fingerprints, and async response handle. Complete Agent data remains available only through
 * the ordinary authorized Discover API.</p>
 *
 * @author Nacos
 */
@Service
public class AgentHttpWatchService implements AgentProjectionUpdateListener {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(AgentHttpWatchService.class);
    
    private static final LogRateLimiter WARN_LOG_LIMITER = new LogRateLimiter(60000L);
    
    private final AgentProjectionService projectionService;
    
    private final AgentHttpClientLifecycleService clientLifecycleService;
    
    private final AgentHttpWatchRegistry registry;
    
    private final int maxItemsPerClient;
    
    private final int maxWaitersPerNode;
    
    private final long maxActiveBytesPerNode;
    
    private final long maxRequestBytes;
    
    private final Executor notificationExecutor;
    
    private final AtomicBoolean closed = new AtomicBoolean();
    
    private final Object lifecycleLock = new Object();
    
    @Autowired
    public AgentHttpWatchService(AgentProjectionService projectionService,
        AgentHttpClientLifecycleService clientLifecycleService,
        @Value("${server.tomcat.max-http-form-post-size:2MB}") DataSize maxRequestBytes) {
        this(projectionService, clientLifecycleService, new AgentHttpWatchRegistry(),
            resolveMaxItemsPerClient(), resolveMaxWaitersPerNode(),
            resolveMaxActiveBytesPerNode(),
            Math.min(resolveMaxRequestBytes(), maxRequestBytes.toBytes()),
            GlobalExecutor::executeByCommon);
    }
    
    AgentHttpWatchService(AgentProjectionService projectionService,
        AgentHttpClientLifecycleService clientLifecycleService, AgentHttpWatchRegistry registry,
        int maxItemsPerClient, int maxWaitersPerNode, long maxActiveBytesPerNode,
        long maxRequestBytes, Executor notificationExecutor) {
        if (maxItemsPerClient < 1 || maxWaitersPerNode < 1 || maxActiveBytesPerNode < 1L
            || maxRequestBytes < 1L) {
            throw new IllegalArgumentException("Agent HTTP Watch capacity limits must be positive");
        }
        this.projectionService = projectionService;
        this.clientLifecycleService = clientLifecycleService;
        this.registry = registry;
        this.maxItemsPerClient = maxItemsPerClient;
        this.maxWaitersPerNode = maxWaitersPerNode;
        this.maxActiveBytesPerNode = maxActiveBytesPerNode;
        this.maxRequestBytes = maxRequestBytes;
        this.notificationExecutor = notificationExecutor;
    }
    
    /** Register as a shared Projection listener after dependencies are ready. */
    @PostConstruct
    public void start() {
        projectionService.addUpdateListener(this);
    }
    
    /**
     * Compare or wait for any changed item in one complete HTTP Watch generation.
     *
     * @param externalClientId mandatory HTTP Client id
     * @param requestModule mandatory AI request module
     * @param request validated complete Watch set
     * @param payloadBytes UTF-8 bytes of the JSON-valued watches form field
     * @return asynchronous wrapped invalidation response
     * @throws NacosException when header, capacity, or projection admission fails
     */
    public DeferredResult<Result<AgentWatchBatchResponse>> watch(String externalClientId,
        String requestModule, AgentWatchBatchRequest request, int payloadBytes)
        throws NacosException {
        ensureOpen();
        if (request == null || request.getWatches() == null || request.getWatches().isEmpty()) {
            throw new IllegalArgumentException("AgentWatchBatchRequest must not be empty");
        }
        if (payloadBytes < 0 || payloadBytes > maxRequestBytes) {
            LOGGER.warn(
                "[RAD-WATCH] Server HTTP Watch rejected: clientId={}, reason=REQUEST_BYTES, "
                    + "payloadBytes={}, limit={}",
                externalClientId, payloadBytes,
                maxRequestBytes);
            throw capacity("Agent HTTP Watch request exceeds the configured request-byte limit.");
        }
        String namespaceId = request.getWatches().get(0).getDiscoveryRequest().getNamespaceId();
        clientLifecycleService.renewForWatch(externalClientId, requestModule, namespaceId);
        AgentHttpWatchOwnerKey ownerKey = new AgentHttpWatchOwnerKey(externalClientId,
            VisibilityHelper.resolveCurrentIdentity(), namespaceId);
        LOGGER.info("[RAD-WATCH] Server HTTP Watch received: clientId={}, clientIp={}, "
            + "generation={}, itemCount={}, payloadBytes={}, watches={}", externalClientId,
            VisibilityHelper.resolveClientIp(), request.getGeneration(),
            request.getWatches().size(), payloadBytes, describeWatches(request));
        Map<AgentProjectionKey, AgentDiscoveryRequest> projections = projections(request);
        retain(projections);
        Map<AgentProjectionKey, AgentProjectionState> states;
        try {
            states = refresh(projections);
        } catch (RuntimeException e) {
            release(projections);
            throw e;
        }
        AgentHttpWatchWaiter waiter = new AgentHttpWatchWaiter(ownerKey,
            request.getGeneration(), request.getTimeoutMillis(), request.getWatches(), payloadBytes,
            this::cleanup);
        try {
            AgentHttpWatchRegistry.Registration registration;
            synchronized (lifecycleLock) {
                ensureOpen();
                try {
                    registration = registry.register(waiter, maxItemsPerClient, maxWaitersPerNode,
                        maxActiveBytesPerNode);
                } catch (NacosApiException e) {
                    LOGGER.warn("[RAD-WATCH] Server HTTP Watch rejected: clientId={}, "
                        + "namespace={}, generation={}, itemCount={}, reason={}, "
                        + "activeWaiters={}, activeBytes={}", externalClientId, namespaceId,
                        request.getGeneration(), request.getWatches().size(), e.getErrMsg(),
                        registry.size(), registry.activeBytes());
                    throw e;
                }
            }
            if (registration.isStale()) {
                waiter.timeout();
                return waiter.getDeferredResult();
            }
            AgentWatchMetrics.record(AgentWatchMetrics.Event.HTTP_LONG_POLL,
                AgentWatchMetrics.Result.ACCEPTED);
            AgentWatchMetrics.recordBytes(AgentWatchMetrics.Transport.HTTP, payloadBytes);
            if (registration.getReplaced() != null) {
                registration.getReplaced().timeout();
            }
            states = currentStates(projections, states);
            waiter.completeIfChanged(states, "INITIAL_SUBSCRIBE");
            return waiter.getDeferredResult();
        } catch (NacosException | RuntimeException e) {
            waiter.cancel();
            throw e;
        }
    }
    
    @Override
    public void onProjectionUpdate(AgentProjectionUpdate update) {
        if (closed.get()) {
            return;
        }
        List<AgentHttpWatchWaiter> waiters = registry.findByProjection(update.getKey());
        if (!waiters.isEmpty()) {
            LOGGER.info("[RAD-WATCH] Server HTTP change fanout triggered: projection={}, "
                + "reasons={}, waiterCount={}",
                AgentWatchLogUtils.token(update.getKey().getValue()), update.getReasons(),
                waiters.size());
        }
        for (AgentHttpWatchWaiter waiter : waiters) {
            Runnable notification =
                () -> waiter.completeIfChanged(update.getKey(), update.getCurrent(),
                    "CHANGE_FANOUT:" + update.getReasons());
            try {
                notificationExecutor.execute(notification);
            } catch (RejectedExecutionException e) {
                if (WARN_LOG_LIMITER.tryAcquire()) {
                    LOGGER.warn("Agent HTTP Watch notification executor rejected a waiter; "
                        + "completing inline.");
                }
                notification.run();
            }
        }
    }
    
    int size() {
        return registry.size();
    }
    
    long activeBytes() {
        return registry.activeBytes();
    }
    
    /** Stop accepting requests and release every request-scoped Projection reference. */
    @PreDestroy
    public void shutdown() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        projectionService.removeUpdateListener(this);
        List<AgentHttpWatchWaiter> waiters;
        synchronized (lifecycleLock) {
            waiters = registry.clear();
        }
        for (AgentHttpWatchWaiter waiter : waiters) {
            waiter.timeout();
        }
    }
    
    private Map<AgentProjectionKey, AgentDiscoveryRequest> projections(
        AgentWatchBatchRequest request) {
        Map<AgentProjectionKey, AgentDiscoveryRequest> result =
            new LinkedHashMap<AgentProjectionKey, AgentDiscoveryRequest>();
        for (AgentWatchBatchItem each : request.getWatches()) {
            AgentProjectionKey key = AgentProjectionKey.of(each.getDiscoveryRequest());
            result.put(key, each.getDiscoveryRequest());
        }
        return result;
    }
    
    private void retain(Map<AgentProjectionKey, AgentDiscoveryRequest> projections) {
        List<AgentProjectionKey> retained = new ArrayList<AgentProjectionKey>(projections.size());
        try {
            for (Map.Entry<AgentProjectionKey, AgentDiscoveryRequest> entry : projections
                .entrySet()) {
                AgentProjectionKey actual = projectionService.retain(entry.getValue());
                retained.add(actual);
                if (!entry.getKey().equals(actual)) {
                    throw new IllegalStateException("Agent Projection canonical key changed");
                }
            }
        } catch (RuntimeException e) {
            for (AgentProjectionKey key : retained) {
                projectionService.release(key);
            }
            throw e;
        }
    }
    
    private Map<AgentProjectionKey, AgentProjectionState> refresh(
        Map<AgentProjectionKey, AgentDiscoveryRequest> projections) {
        Map<AgentProjectionKey, AgentProjectionState> result =
            new LinkedHashMap<AgentProjectionKey, AgentProjectionState>();
        for (AgentProjectionKey key : projections.keySet()) {
            result.put(key, projectionService.refreshNow(key));
        }
        return result;
    }
    
    private Map<AgentProjectionKey, AgentProjectionState> currentStates(
        Map<AgentProjectionKey, AgentDiscoveryRequest> projections,
        Map<AgentProjectionKey, AgentProjectionState> fallback) {
        Map<AgentProjectionKey, AgentProjectionState> result =
            new LinkedHashMap<AgentProjectionKey, AgentProjectionState>();
        for (AgentProjectionKey key : projections.keySet()) {
            result.put(key, projectionService.getState(key).orElse(fallback.get(key)));
        }
        return result;
    }
    
    private void cleanup(AgentHttpWatchWaiter waiter) {
        registry.remove(waiter.getWaiterId());
        for (AgentProjectionKey key : waiter.getProjectionKeys()) {
            projectionService.release(key);
        }
    }
    
    private void release(Map<AgentProjectionKey, AgentDiscoveryRequest> projections) {
        for (AgentProjectionKey key : projections.keySet()) {
            projectionService.release(key);
        }
    }
    
    private void ensureOpen() {
        if (closed.get()) {
            throw new IllegalStateException("Agent HTTP Watch service is closed");
        }
    }
    
    private NacosApiException capacity(String message) {
        AgentWatchMetrics.record(AgentWatchMetrics.Event.CAPACITY_REJECTION,
            AgentWatchMetrics.Result.REJECTED);
        return new NacosApiException(NacosException.OVER_THRESHOLD,
            ErrorCode.AGENT_DISCOVERY_SUBSCRIPTION_OVER_LIMIT, message);
    }
    
    private String describeWatches(AgentWatchBatchRequest request) {
        List<String> result = new ArrayList<String>(request.getWatches().size());
        for (AgentWatchBatchItem each : request.getWatches()) {
            result.add(AgentWatchLogUtils.token(each.getClientWatchId()) + '{'
                + AgentWatchLogUtils.describeRequest(each.getDiscoveryRequest())
                + ", materializedFingerprint="
                + AgentWatchLogUtils.fingerprint(each.getMaterializedFingerprint()) + '}');
        }
        return result.toString();
    }
    
    private static int resolveMaxItemsPerClient() {
        return EnvUtil.getProperty(Constants.Agent.MAX_WATCHES_PER_CLIENT_CONFIG_KEY,
            Integer.class, Constants.Agent.DEFAULT_MAX_WATCHES_PER_CLIENT);
    }
    
    private static int resolveMaxWaitersPerNode() {
        return EnvUtil.getProperty(Constants.Agent.MAX_HTTP_WATCH_WAITERS_PER_NODE_CONFIG_KEY,
            Integer.class, Constants.Agent.DEFAULT_MAX_HTTP_WATCH_WAITERS_PER_NODE);
    }
    
    private static long resolveMaxActiveBytesPerNode() {
        return EnvUtil.getProperty(Constants.Agent.MAX_HTTP_WATCH_ACTIVE_BYTES_PER_NODE_CONFIG_KEY,
            Long.class, Constants.Agent.DEFAULT_MAX_HTTP_WATCH_ACTIVE_BYTES_PER_NODE);
    }
    
    private static long resolveMaxRequestBytes() {
        return EnvUtil.getProperty(Constants.Agent.MAX_HTTP_WATCH_REQUEST_BYTES_CONFIG_KEY,
            Long.class, Constants.Agent.DEFAULT_MAX_HTTP_WATCH_REQUEST_BYTES);
    }
}
