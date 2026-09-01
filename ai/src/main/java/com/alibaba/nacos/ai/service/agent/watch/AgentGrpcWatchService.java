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
import com.alibaba.nacos.ai.service.agent.AgentDiscoveryApplicationService;
import com.alibaba.nacos.api.ai.model.rad.AgentDiscoveryRequest;
import com.alibaba.nacos.api.ai.model.rad.AgentDiscoveryResult;
import com.alibaba.nacos.api.ai.model.rad.AgentWatchEventType;
import com.alibaba.nacos.api.ai.remote.request.AgentDiscoveryNotifyRequest;
import com.alibaba.nacos.api.ai.remote.request.AgentSubscribeRpcRequest;
import com.alibaba.nacos.api.ai.remote.response.AgentSubscribeRpcResponse;
import com.alibaba.nacos.api.ai.utils.AgentDiscoveryCanonicalizer;
import com.alibaba.nacos.api.ai.utils.AgentWatchLogUtils;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.remote.PushCallBack;
import com.alibaba.nacos.common.utils.LogRateLimiter;
import com.alibaba.nacos.common.utils.ThreadUtils;
import com.alibaba.nacos.core.remote.ClientConnectionEventListener;
import com.alibaba.nacos.core.remote.Connection;
import com.alibaba.nacos.core.remote.ConnectionManager;
import com.alibaba.nacos.core.remote.RpcPushService;
import com.alibaba.nacos.core.utils.GlobalExecutor;
import com.alibaba.nacos.sys.env.EnvUtil;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * Connection-scoped gRPC Watch admission and fingerprint-hint delivery.
 *
 * <p>No Agent business snapshot or credential is retained or pushed by this service.</p>
 *
 * @author Nacos
 */
@Service
public class AgentGrpcWatchService extends ClientConnectionEventListener
    implements AgentProjectionUpdateListener {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(AgentGrpcWatchService.class);
    
    private static final LogRateLimiter DELIVERY_WARN_LOG_LIMITER =
        new LogRateLimiter(60000L);
    
    private static final LogRateLimiter REVALIDATE_WARN_LOG_LIMITER =
        new LogRateLimiter(60000L);
    
    private static final long DEFAULT_PUSH_TIMEOUT_MILLIS = 3000L;
    
    private static final long DEFAULT_RETRY_DELAY_MILLIS = 1000L;
    
    private static final Pattern CLIENT_WATCH_ID_PATTERN =
        Pattern.compile("[A-Za-z0-9._:-]{1,128}");
    
    private static final Pattern WATCH_KEY_PATTERN =
        Pattern.compile("[A-Za-z0-9._:-]{1,256}");
    
    private static final Pattern FINGERPRINT_PATTERN = Pattern.compile(
        Pattern.quote(AgentDiscoveryCanonicalizer.ALGORITHM_ID) + ":[0-9a-f]{64}");
    
    private final AgentProjectionService projectionService;
    
    private final AgentDiscoveryApplicationService discoveryService;
    
    private final AgentWatchOwnerEligibilityChecker ownerEligibilityChecker;
    
    private final RpcPushService rpcPushService;
    
    private final ConnectionManager connectionManager;
    
    private final AgentGrpcWatchRegistry registry;
    
    private final AgentGrpcWatchTaskEngine taskEngine;
    
    private final Executor callbackExecutor;
    
    private final int maxWatchesPerConnection;
    
    private final Object lifecycleLock = new Object();
    
    @Autowired
    public AgentGrpcWatchService(AgentProjectionService projectionService,
        AgentDiscoveryApplicationService discoveryService,
        AgentWatchOwnerEligibilityChecker ownerEligibilityChecker,
        RpcPushService rpcPushService, ConnectionManager connectionManager) {
        this(projectionService, discoveryService, ownerEligibilityChecker, rpcPushService,
            connectionManager, new AgentGrpcWatchRegistry(), resolveMaxWatchesPerConnection(),
            DEFAULT_RETRY_DELAY_MILLIS, Math.max(2, ThreadUtils.getSuitableThreadCount(1)),
            GlobalExecutor::executeByCommon);
    }
    
    AgentGrpcWatchService(AgentProjectionService projectionService,
        AgentDiscoveryApplicationService discoveryService,
        AgentWatchOwnerEligibilityChecker ownerEligibilityChecker,
        RpcPushService rpcPushService, ConnectionManager connectionManager,
        AgentGrpcWatchRegistry registry, int maxWatchesPerConnection, long retryDelayMillis,
        int workerCount, Executor callbackExecutor) {
        if (maxWatchesPerConnection < 1) {
            throw new IllegalArgumentException(Constants.Agent.MAX_WATCHES_PER_CLIENT_CONFIG_KEY
                + " must be greater than 0");
        }
        this.projectionService = projectionService;
        this.discoveryService = discoveryService;
        this.ownerEligibilityChecker = ownerEligibilityChecker;
        this.rpcPushService = rpcPushService;
        this.connectionManager = connectionManager;
        this.registry = registry;
        this.maxWatchesPerConnection = maxWatchesPerConnection;
        this.callbackExecutor = callbackExecutor;
        taskEngine = new AgentGrpcWatchTaskEngine(retryDelayMillis, workerCount,
            this::deliverCurrentFact);
    }
    
    /** Register as a Projection update listener after dependencies are ready. */
    @PostConstruct
    public void start() {
        projectionService.addUpdateListener(this);
    }
    
    /**
     * Install one exact connection-owned Watch after the request passed gRPC authorization.
     *
     * @param connectionId current gRPC connection id
     * @param request subscribe binding
     * @return opaque Watch admission result
     * @throws NacosException when validation, initial Discover, or admission fails
     */
    public AgentSubscribeRpcResponse subscribe(String connectionId,
        AgentSubscribeRpcRequest request) throws NacosException {
        validateSubscribeRequest(request);
        AgentDiscoveryRequest canonical =
            AgentDiscoveryCanonicalizer.canonicalizeRequest(request.getDiscoveryRequest());
        AgentProjectionKey projectionKey = AgentProjectionKey.of(canonical);
        LOGGER.info("[RAD-WATCH] Server gRPC Watch received: connectionId={}, clientIp={}, "
            + "clientWatchId={}, materializedFingerprint={}, {}", connectionId,
            clientIp(connectionId), AgentWatchLogUtils.token(request.getClientWatchId()),
            AgentWatchLogUtils.fingerprint(request.getMaterializedFingerprint()),
            AgentWatchLogUtils.describeRequest(canonical));
        AgentGrpcWatch existing;
        synchronized (lifecycleLock) {
            existing = registry.findByClientWatchId(connectionId, request.getClientWatchId());
            if (existing != null) {
                requireSameProjection(existing, projectionKey);
                AgentSubscribeRpcResponse duplicate =
                    response(existing, request.getMaterializedFingerprint());
                logAdmission(existing, duplicate, true);
                return duplicate;
            }
        }
        
        AgentWatchOwnerContext owner = new AgentWatchOwnerContext(
            VisibilityHelper.resolveCurrentIdentity(), VisibilityHelper.resolveCurrentApiType());
        AgentDiscoveryResult initial = discoveryService.discover(canonical);
        String initialFingerprint = AgentDiscoveryCanonicalizer.fingerprint(initial);
        synchronized (lifecycleLock) {
            if (!connectionManager.checkValid(connectionId)) {
                throw new NacosException(NacosException.CLIENT_DISCONNECT,
                    "gRPC connection closed before Agent Watch admission.");
            }
            AgentGrpcWatchRegistry.Registration registration;
            try {
                registration = registry.register(connectionId, request.getClientWatchId(),
                    projectionKey, owner, maxWatchesPerConnection);
            } catch (NacosApiException e) {
                LOGGER.warn("[RAD-WATCH] Server gRPC Watch rejected: connectionId={}, "
                    + "clientIp={}, clientWatchId={}, reason={}, current={}, limit={}, {}",
                    connectionId, clientIp(connectionId),
                    AgentWatchLogUtils.token(request.getClientWatchId()), e.getErrMsg(),
                    registry.connectionSize(connectionId), maxWatchesPerConnection,
                    AgentWatchLogUtils.describeRequest(canonical));
                throw e;
            }
            AgentGrpcWatch watch = registration.getWatch();
            if (!registration.isCreated()) {
                AgentSubscribeRpcResponse duplicate =
                    response(watch, request.getMaterializedFingerprint());
                logAdmission(watch, duplicate, true);
                return duplicate;
            }
            boolean retained = false;
            try {
                AgentProjectionKey retainedKey = projectionService.retain(canonical);
                retained = true;
                if (!projectionKey.equals(retainedKey)) {
                    throw new IllegalStateException("Agent Projection canonical key changed");
                }
                // Establish the current Projection and its physical reverse indexes before the
                // Subscribe acknowledgement. Otherwise a Runtime change can race the delayed
                // initial projection and be lost until polling or reconciliation repairs it.
                projectionService.refreshNow(projectionKey);
                markCurrentStateIfDifferent(watch, initialFingerprint);
                if (watch.activate(initialFingerprint)) {
                    taskEngine.schedule(watch.getWatchKey());
                }
                AgentSubscribeRpcResponse response =
                    response(watch, request.getMaterializedFingerprint());
                logAdmission(watch, response, false);
                return response;
            } catch (RuntimeException e) {
                registry.remove(watch.getWatchKey());
                if (retained) {
                    projectionService.release(projectionKey);
                }
                throw e;
            }
        }
    }
    
    /**
     * Remove one Watch only when it belongs to the current connection.
     *
     * @param connectionId current connection id
     * @param watchKey opaque server Watch key
     */
    public void unsubscribe(String connectionId, String watchKey) {
        validateWatchKey(watchKey);
        synchronized (lifecycleLock) {
            AgentGrpcWatch removed = registry.removeOwned(connectionId, watchKey);
            release(removed);
            if (removed != null) {
                LOGGER.info("[RAD-WATCH] Server gRPC Watch unsubscribed: connectionId={}, "
                    + "clientIp={}, clientWatchId={}, watchKey={}, {}", connectionId,
                    clientIp(connectionId), AgentWatchLogUtils.token(removed.getClientWatchId()),
                    AgentWatchLogUtils.token(watchKey),
                    AgentWatchLogUtils.describeRequest(removed.getProjectionKey().getRequest()));
            }
        }
    }
    
    @Override
    public void onProjectionUpdate(AgentProjectionUpdate update) {
        List<AgentGrpcWatch> watches = registry.findByProjection(update.getKey());
        LOGGER.debug("Agent Projection update invalidates {} gRPC Watches: reasons={}",
            watches.size(), update.getReasons());
        if (!watches.isEmpty()) {
            LOGGER.info("[RAD-WATCH] Server gRPC change fanout triggered: projection={}, "
                + "reasons={}, watchCount={}",
                AgentWatchLogUtils.token(update.getKey().getValue()), update.getReasons(),
                watches.size());
        }
        for (AgentGrpcWatch watch : watches) {
            if (watch.markDirty("CHANGE_FANOUT:" + update.getReasons())) {
                taskEngine.schedule(watch.getWatchKey());
            }
        }
    }
    
    @Override
    public void clientConnected(Connection connect) {
        // Watch state is created only by an authorized Subscribe request.
    }
    
    @Override
    public void clientDisConnected(Connection connect) {
        removeConnection(connect.getMetaInfo().getConnectionId());
    }
    
    int size() {
        return registry.size();
    }
    
    int connectionSize(String connectionId) {
        return registry.connectionSize(connectionId);
    }
    
    int pendingTaskCount() {
        return taskEngine.pendingTaskCount();
    }
    
    /** Stop delivery and release all Projection references. */
    @PreDestroy
    public void shutdown() throws NacosException {
        projectionService.removeUpdateListener(this);
        synchronized (lifecycleLock) {
            for (AgentGrpcWatch watch : registry.clear()) {
                projectionService.release(watch.getProjectionKey());
            }
        }
        taskEngine.shutdown();
    }
    
    private void deliverCurrentFact(String watchKey) {
        AgentGrpcWatch watch = registry.findByWatchKey(watchKey);
        if (watch == null || !watch.beginDelivery()) {
            return;
        }
        try {
            if (!connectionManager.checkValid(watch.getConnectionId())) {
                removeConnection(watch.getConnectionId());
                return;
            }
            Optional<AgentProjectionState> current =
                projectionService.getState(watch.getProjectionKey());
            if (!current.isPresent()) {
                retry(watch, null);
                return;
            }
            AgentDiscoveryNotifyRequest notifyRequest = buildNotifyRequest(watch, current.get());
            if (notifyRequest == null) {
                scheduleAfterCompletion(watch, null, true, false);
                return;
            }
            LOGGER.info("[RAD-WATCH] Server gRPC hint push started: connectionId={}, clientIp={}, "
                + "clientWatchId={}, watchKey={}, trigger={}, eventType={}, fingerprint={}",
                watch.getConnectionId(), clientIp(watch.getConnectionId()),
                AgentWatchLogUtils.token(watch.getClientWatchId()),
                AgentWatchLogUtils.token(watch.getWatchKey()), watch.getDeliveryTrigger(),
                notifyRequest.getEventType(),
                AgentWatchLogUtils.fingerprint(notifyRequest.getObservedFingerprint()));
            AgentWatchMetrics.record(AgentWatchMetrics.Event.GRPC_PUSH,
                AgentWatchMetrics.Result.SCHEDULED);
            AgentWatchMetrics.recordJsonBytes(AgentWatchMetrics.Transport.GRPC, notifyRequest);
            rpcPushService.pushWithCallback(watch.getConnectionId(), notifyRequest,
                new WatchPushCallback(watch, notifyRequest), callbackExecutor);
        } catch (RuntimeException e) {
            AgentWatchMetrics.record(AgentWatchMetrics.Event.GRPC_PUSH,
                AgentWatchMetrics.Result.FAILED);
            if (DELIVERY_WARN_LOG_LIMITER.tryAcquire()) {
                LOGGER.warn("[RAD-WATCH] Server gRPC hint push failed before ACK registration: "
                    + "connectionId={}, clientIp={}, clientWatchId={}, watchKey={}, "
                    + "errorType={}", watch.getConnectionId(),
                    clientIp(watch.getConnectionId()),
                    AgentWatchLogUtils.token(watch.getClientWatchId()),
                    AgentWatchLogUtils.token(watch.getWatchKey()),
                    e.getClass().getSimpleName());
            }
            retry(watch, null);
        }
    }
    
    private AgentDiscoveryNotifyRequest buildNotifyRequest(AgentGrpcWatch watch,
        AgentProjectionState state) {
        AgentWatchOwnerEligibility eligibility =
            ownerEligibilityChecker.evaluate(watch.getOwner(), watch.getProjectionKey());
        if (eligibility == AgentWatchOwnerEligibility.DENIED) {
            return terminated(watch.getWatchKey(), NacosException.RESOURCE_NOT_FOUND);
        }
        if (eligibility == AgentWatchOwnerEligibility.UNCERTAIN) {
            return revalidate(watch.getWatchKey());
        }
        switch (state.getStatus()) {
            case AVAILABLE:
                if (!watch.shouldInvalidate(state.getFingerprint())) {
                    return null;
                }
                AgentDiscoveryNotifyRequest result = base(watch.getWatchKey(),
                    AgentWatchEventType.INVALIDATE);
                result.setObservedFingerprint(state.getFingerprint());
                return result;
            case NOT_FOUND:
                return terminated(watch.getWatchKey(), state.getErrorCode());
            case ACCESS_UNCERTAIN:
            case CONFLICT:
            case TRANSIENT_FAILURE:
            default:
                return revalidate(watch.getWatchKey());
        }
    }
    
    private void markCurrentStateIfDifferent(AgentGrpcWatch watch, String initialFingerprint) {
        Optional<AgentProjectionState> current = projectionService.getState(
            watch.getProjectionKey());
        if (current.isPresent() && (!current.get().isAvailable()
            || !Objects.equals(initialFingerprint, current.get().getFingerprint()))) {
            watch.markDirty("INITIAL_SUBSCRIBE");
        }
    }
    
    private AgentSubscribeRpcResponse response(AgentGrpcWatch watch,
        String materializedFingerprint) {
        String observedFingerprint = watch.getLastAcceptedFingerprint();
        Optional<AgentProjectionState> current =
            projectionService.getState(watch.getProjectionKey());
        if (current.isPresent() && current.get().isAvailable()) {
            observedFingerprint = current.get().getFingerprint();
        }
        AgentSubscribeRpcResponse result = new AgentSubscribeRpcResponse();
        result.setWatchKey(watch.getWatchKey());
        result.setObservedFingerprint(observedFingerprint);
        result.setRefreshRequired(observedFingerprint == null
            || !observedFingerprint.equals(materializedFingerprint));
        return result;
    }
    
    private void requireSameProjection(AgentGrpcWatch watch, AgentProjectionKey expected)
        throws NacosException {
        if (!watch.getProjectionKey().equals(expected)) {
            throw new com.alibaba.nacos.api.exception.api.NacosApiException(
                NacosException.CONFLICT, com.alibaba.nacos.api.model.v2.ErrorCode.RESOURCE_CONFLICT,
                "clientWatchId is already bound to another Agent discovery intent.");
        }
    }
    
    private void validateSubscribeRequest(AgentSubscribeRpcRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("AgentSubscribeRpcRequest must not be null");
        }
        if (request.getClientWatchId() == null
            || !CLIENT_WATCH_ID_PATTERN.matcher(request.getClientWatchId()).matches()) {
            throw new IllegalArgumentException(
                "clientWatchId must match [A-Za-z0-9._:-]+ and contain 1 to 128 characters");
        }
        if (request.getDiscoveryRequest() == null) {
            throw new IllegalArgumentException("discoveryRequest must not be null");
        }
        String fingerprint = request.getMaterializedFingerprint();
        if (fingerprint != null && !FINGERPRINT_PATTERN.matcher(fingerprint).matches()) {
            throw new IllegalArgumentException("materializedFingerprint is invalid");
        }
    }
    
    private void validateWatchKey(String watchKey) {
        if (watchKey == null || !WATCH_KEY_PATTERN.matcher(watchKey).matches()) {
            throw new IllegalArgumentException(
                "watchKey must match [A-Za-z0-9._:-]+ and contain 1 to 256 characters");
        }
    }
    
    private AgentDiscoveryNotifyRequest revalidate(String watchKey) {
        return base(watchKey, AgentWatchEventType.REVALIDATE);
    }
    
    private AgentDiscoveryNotifyRequest terminated(String watchKey, int errorCode) {
        AgentDiscoveryNotifyRequest result = base(watchKey, AgentWatchEventType.TERMINATED);
        result.setErrorCode(errorCode);
        return result;
    }
    
    private AgentDiscoveryNotifyRequest base(String watchKey, AgentWatchEventType eventType) {
        AgentDiscoveryNotifyRequest result = new AgentDiscoveryNotifyRequest();
        result.setWatchKey(watchKey);
        result.setEventType(eventType);
        return result;
    }
    
    private void retry(AgentGrpcWatch watch, AgentDiscoveryNotifyRequest delivered) {
        if (watch.isClosed()) {
            return;
        }
        try {
            projectionService.revalidate(watch.getProjectionKey());
        } catch (RuntimeException e) {
            if (REVALIDATE_WARN_LOG_LIMITER.tryAcquire()) {
                LOGGER.warn("Failed to request current RAD projection for a Watch: {}",
                    e.getClass().getSimpleName());
            }
        } finally {
            scheduleAfterCompletion(watch, delivered, false, true);
        }
    }
    
    private void scheduleAfterCompletion(AgentGrpcWatch watch,
        AgentDiscoveryNotifyRequest delivered, boolean success, boolean delayed) {
        if (watch.completeDelivery(delivered, success)) {
            if (delayed) {
                taskEngine.retry(watch.getWatchKey());
            } else {
                taskEngine.schedule(watch.getWatchKey());
            }
        }
    }
    
    private void removeConnection(String connectionId) {
        synchronized (lifecycleLock) {
            List<AgentGrpcWatch> removed = registry.removeConnection(connectionId);
            for (AgentGrpcWatch watch : removed) {
                projectionService.release(watch.getProjectionKey());
            }
            if (!removed.isEmpty()) {
                LOGGER.info("[RAD-WATCH] Server gRPC connection cleanup: connectionId={}, "
                    + "removedWatchCount={}", connectionId, removed.size());
            }
        }
    }
    
    private void removeWatch(String watchKey) {
        synchronized (lifecycleLock) {
            release(registry.remove(watchKey));
        }
    }
    
    private void release(AgentGrpcWatch watch) {
        if (watch != null) {
            projectionService.release(watch.getProjectionKey());
        }
    }
    
    private static int resolveMaxWatchesPerConnection() {
        return EnvUtil.getProperty(Constants.Agent.MAX_WATCHES_PER_CLIENT_CONFIG_KEY,
            Integer.class, Constants.Agent.DEFAULT_MAX_WATCHES_PER_CLIENT);
    }
    
    private void logAdmission(AgentGrpcWatch watch, AgentSubscribeRpcResponse response,
        boolean duplicate) {
        LOGGER.info("[RAD-WATCH] Server gRPC Watch admitted: connectionId={}, clientIp={}, "
            + "clientWatchId={}, watchKey={}, duplicate={}, refreshRequired={}, "
            + "observedFingerprint={}, {}", watch.getConnectionId(),
            clientIp(watch.getConnectionId()), AgentWatchLogUtils.token(watch.getClientWatchId()),
            AgentWatchLogUtils.token(watch.getWatchKey()), duplicate,
            response.isRefreshRequired(),
            AgentWatchLogUtils.fingerprint(response.getObservedFingerprint()),
            AgentWatchLogUtils.describeRequest(watch.getProjectionKey().getRequest()));
    }
    
    private String clientIp(String connectionId) {
        Connection connection = connectionManager.getConnection(connectionId);
        return connection == null || connection.getMetaInfo() == null
            ? "-" : connection.getMetaInfo().getClientIp();
    }
    
    private class WatchPushCallback implements PushCallBack {
        
        private final AgentGrpcWatch watch;
        
        private final AgentDiscoveryNotifyRequest request;
        
        private final long startedNanos = System.nanoTime();
        
        WatchPushCallback(AgentGrpcWatch watch, AgentDiscoveryNotifyRequest request) {
            this.watch = watch;
            this.request = request;
        }
        
        @Override
        public long getTimeout() {
            return DEFAULT_PUSH_TIMEOUT_MILLIS;
        }
        
        @Override
        public void onSuccess() {
            AgentWatchMetrics.record(AgentWatchMetrics.Event.GRPC_ACK,
                AgentWatchMetrics.Result.SUCCESS);
            LOGGER.info("[RAD-WATCH] Server gRPC hint ACK succeeded: connectionId={}, clientIp={}, "
                + "clientWatchId={}, watchKey={}, eventType={}, durationMillis={}",
                watch.getConnectionId(), clientIp(watch.getConnectionId()),
                AgentWatchLogUtils.token(watch.getClientWatchId()),
                AgentWatchLogUtils.token(watch.getWatchKey()), request.getEventType(),
                elapsedMillis());
            if (request.getEventType() == AgentWatchEventType.TERMINATED) {
                removeWatch(watch.getWatchKey());
            } else {
                scheduleAfterCompletion(watch, request, true, false);
            }
        }
        
        @Override
        public void onFail(Throwable e) {
            AgentWatchMetrics.record(AgentWatchMetrics.Event.GRPC_ACK,
                AgentWatchMetrics.Result.FAILED);
            if (DELIVERY_WARN_LOG_LIMITER.tryAcquire()) {
                LOGGER.warn("[RAD-WATCH] Server gRPC hint ACK failed: connectionId={}, "
                    + "clientIp={}, clientWatchId={}, watchKey={}, eventType={}, "
                    + "durationMillis={}, errorType={}", watch.getConnectionId(),
                    clientIp(watch.getConnectionId()),
                    AgentWatchLogUtils.token(watch.getClientWatchId()),
                    AgentWatchLogUtils.token(watch.getWatchKey()), request.getEventType(),
                    elapsedMillis(), e == null ? "Unknown" : e.getClass().getSimpleName());
            }
            if (!connectionManager.checkValid(watch.getConnectionId())) {
                removeConnection(watch.getConnectionId());
                return;
            }
            retry(watch, request);
        }
        
        private long elapsedMillis() {
            return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos);
        }
    }
}
