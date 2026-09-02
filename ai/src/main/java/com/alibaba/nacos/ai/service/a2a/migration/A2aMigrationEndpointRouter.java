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

package com.alibaba.nacos.ai.service.a2a.migration;

import com.alibaba.nacos.ai.service.a2a.CanonicalA2aEndpointOperationService;
import com.alibaba.nacos.ai.service.a2a.LegacyA2aEndpointOperationService;
import com.alibaba.nacos.ai.service.agent.runtime.AgentRuntimePublicationCapacityGate;
import com.alibaba.nacos.api.ai.model.a2a.AgentEndpoint;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.api.remote.RemoteConstants;
import com.alibaba.nacos.common.executor.ExecutorFactory;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.common.utils.ThreadFactoryBuilder;
import com.alibaba.nacos.core.remote.ClientConnectionEventListener;
import com.alibaba.nacos.core.remote.Connection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Temporary Runtime router for Nacos 3.0-3.2 A2A upgrade migration.
 *
 * <p>TODO(remove in 4.0): remove after the historical A2A migration window closes.</p>

 * <p>The removal includes the dual-materialization router, logical publication cache, and
 * legacy shadow retry.</p>
 *
 * @author Nacos
 */
@Component
public class A2aMigrationEndpointRouter extends ClientConnectionEventListener
    implements DisposableBean {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(A2aMigrationEndpointRouter.class);
    
    private static final int DEFAULT_MAX_PENDING_PER_CONNECTION = 1024;
    
    private static final long DEFAULT_RETRY_INTERVAL_MILLIS = 1000L;
    
    private final A2aMigrationStateService stateService;
    
    private final LegacyA2aEndpointOperationService legacyService;
    
    private final CanonicalA2aEndpointOperationService canonicalService;
    
    private final AgentRuntimePublicationCapacityGate publicationCapacityGate;
    
    private final ScheduledExecutorService executor;
    
    private final int maxPendingPerConnection;
    
    private final long retryIntervalMillis;
    
    private final ConcurrentMap<String, ConnectionState> connectionStates =
        new ConcurrentHashMap<String, ConnectionState>();
    
    private final AtomicBoolean retryScheduled = new AtomicBoolean(false);
    
    private final AtomicInteger pendingRetryTotal = new AtomicInteger();
    
    private volatile boolean destroyed;
    
    @Autowired
    public A2aMigrationEndpointRouter(A2aMigrationStateService stateService,
        LegacyA2aEndpointOperationService legacyService,
        CanonicalA2aEndpointOperationService canonicalService,
        AgentRuntimePublicationCapacityGate publicationCapacityGate) {
        this(stateService, legacyService, canonicalService, publicationCapacityGate,
            ExecutorFactory.Managed.newSingleScheduledExecutorService(
                A2aMigrationEndpointRouter.class.getCanonicalName(),
                new ThreadFactoryBuilder().daemon(true)
                    .nameFormat("nacos-ai-a2a-migration-endpoint-%d").build()),
            DEFAULT_MAX_PENDING_PER_CONNECTION, DEFAULT_RETRY_INTERVAL_MILLIS);
    }
    
    A2aMigrationEndpointRouter(A2aMigrationStateService stateService,
        LegacyA2aEndpointOperationService legacyService,
        CanonicalA2aEndpointOperationService canonicalService,
        AgentRuntimePublicationCapacityGate publicationCapacityGate,
        ScheduledExecutorService executor, int maxPendingPerConnection,
        long retryIntervalMillis) {
        if (maxPendingPerConnection < 1 || retryIntervalMillis < 1) {
            throw new IllegalArgumentException(
                "A2A migration Endpoint retry capacity and interval must be positive");
        }
        this.stateService = stateService;
        this.legacyService = legacyService;
        this.canonicalService = canonicalService;
        this.publicationCapacityGate = publicationCapacityGate;
        this.executor = executor;
        this.maxPendingPerConnection = maxPendingPerConnection;
        this.retryIntervalMillis = retryIntervalMillis;
    }
    
    /**
     * Resolve the temporary Runtime migration state for one request.
     *
     * @return migration state, or {@code null} for an explicit static compatibility mode
     */
    public A2aMigrationState resolveState() {
        return stateService.resolveConfigured();
    }
    
    /**
     * Register one legacy single-Endpoint publication through the migration route.
     */
    public void register(String parentClientId, String namespaceId, String agentName,
        AgentEndpoint endpoint, String sourceIp, A2aMigrationState state) throws NacosException {
        requireEndpoint(endpoint);
        doRegister(parentClientId, namespaceId, agentName,
            new PublicationSnapshot(Collections.singletonList(copy(endpoint)), true, sourceIp),
            route(state));
    }
    
    /**
     * Register one complete legacy Endpoint Batch through the migration route.
     */
    public void register(String parentClientId, String namespaceId, String agentName,
        Collection<AgentEndpoint> endpoints, String sourceIp, A2aMigrationState state)
        throws NacosException {
        requireEndpoints(endpoints);
        doRegister(parentClientId, namespaceId, agentName,
            new PublicationSnapshot(copy(endpoints), false, sourceIp), route(state));
    }
    
    /**
     * Deregister one exact-Version publication through the migration route.
     */
    public void deregister(String parentClientId, String namespaceId, String agentName,
        AgentEndpoint endpoint, String sourceIp, A2aMigrationState state) throws NacosException {
        requireEndpoint(endpoint);
        AgentEndpoint copiedEndpoint = copy(endpoint);
        Route route = route(state);
        validateDeregistration(route, copiedEndpoint);
        PublicationKey publicationKey = new PublicationKey(namespaceId, agentName,
            copiedEndpoint.getVersion());
        ConnectionState connectionState = connectionState(parentClientId);
        synchronized (connectionState) {
            ensureRetryCapacity(connectionState, publicationKey,
                retryTarget(connectionState, publicationKey, route));
            publicationCapacityGate.deregisterLogical(parentClientId,
                logicalPublicationKey(publicationKey),
                () -> applyDeregister(route.primary, A2aMigrationMetrics.Role.PRIMARY,
                    parentClientId, publicationKey, copiedEndpoint, sourceIp));
            markNotMaterialized(connectionState, publicationKey, route.primary);
            connectionState.publications.remove(publicationKey);
            clearPendingForTarget(connectionState, publicationKey, route.primary);
            if (route.secondary == null) {
                clearAllPending(connectionState, publicationKey);
            } else {
                try {
                    applyDeregister(route.secondary, A2aMigrationMetrics.Role.SECONDARY,
                        parentClientId, publicationKey, copiedEndpoint, sourceIp);
                    markNotMaterialized(connectionState, publicationKey, route.secondary);
                    clearPendingForTarget(connectionState, publicationKey, route.secondary);
                } catch (Exception e) {
                    queueRetry(connectionState, RetryCommand.deregister(route.secondary,
                        publicationKey, copiedEndpoint, sourceIp), e, parentClientId);
                }
            }
            cleanupStaleTarget(connectionState, parentClientId, publicationKey,
                route.cleanup);
            removeEmptyState(parentClientId, connectionState);
        }
    }
    
    /**
     * Return whether any physical mirror or shadow retry still blocks Runtime convergence.
     *
     * @return {@code true} when at least one connection has pending retry state
     */
    public boolean hasPendingRetries() {
        for (ConnectionState state : connectionStates.values()) {
            synchronized (state) {
                if (!state.pending.isEmpty()) {
                    return true;
                }
            }
        }
        return false;
    }
    
    @Override
    public void clientConnected(Connection connect) {
    }
    
    @Override
    public void clientDisConnected(Connection connect) {
        if (!RemoteConstants.LABEL_MODULE_AI
            .equals(connect.getMetaInfo().getLabel(RemoteConstants.LABEL_MODULE))) {
            return;
        }
        String connectionId = connect.getMetaInfo().getConnectionId();
        publicationCapacityGate.clearLogicalPublications(connectionId);
        ConnectionState removed = connectionStates.remove(connectionId);
        if (removed != null) {
            synchronized (removed) {
                removed.attached = false;
                updatePendingRetryTotal(-removed.pending.size());
            }
        }
    }
    
    @Override
    public void destroy() {
        destroyed = true;
        executor.shutdownNow();
        for (String clientId : connectionStates.keySet()) {
            publicationCapacityGate.clearLogicalPublications(clientId);
        }
        for (ConnectionState state : connectionStates.values()) {
            synchronized (state) {
                state.attached = false;
            }
        }
        connectionStates.clear();
        int removedPending = pendingRetryTotal.getAndSet(0);
        A2aMigrationMetrics.adjustPendingEndpointRetries(-removedPending);
    }
    
    void retryPendingNow() {
        List<Map.Entry<String, ConnectionState>> states;
        states = new ArrayList<Map.Entry<String, ConnectionState>>(
            connectionStates.entrySet());
        for (Map.Entry<String, ConnectionState> entry : states) {
            retryConnection(entry.getKey(), entry.getValue());
        }
    }
    
    int publicationCount(String parentClientId) {
        ConnectionState state = connectionStates.get(parentClientId);
        if (state == null) {
            return 0;
        }
        synchronized (state) {
            return countPublications(state);
        }
    }
    
    int pendingRetryCount(String parentClientId) {
        ConnectionState state = connectionStates.get(parentClientId);
        if (state == null) {
            return 0;
        }
        synchronized (state) {
            return state.pending.size();
        }
    }
    
    private void doRegister(String parentClientId, String namespaceId, String agentName,
        PublicationSnapshot snapshot, Route route) throws NacosException {
        validateRegistration(route, namespaceId, agentName, snapshot);
        PublicationKey publicationKey = new PublicationKey(namespaceId, agentName,
            snapshot.version());
        ConnectionState connectionState = connectionState(parentClientId);
        synchronized (connectionState) {
            ensureRetryCapacity(connectionState, publicationKey,
                retryTarget(connectionState, publicationKey, route));
            publicationCapacityGate.registerLogical(parentClientId,
                logicalPublicationKey(publicationKey), snapshot.endpoints.size(),
                () -> applyRegister(route.primary, A2aMigrationMetrics.Role.PRIMARY,
                    parentClientId, publicationKey, snapshot));
            markMaterialized(connectionState, publicationKey, route.primary, snapshot);
            connectionState.publications.put(publicationKey, snapshot);
            clearPendingForTarget(connectionState, publicationKey, route.primary);
            if (route.secondary == null) {
                clearAllPending(connectionState, publicationKey);
            } else {
                try {
                    applyRegister(route.secondary, A2aMigrationMetrics.Role.SECONDARY,
                        parentClientId, publicationKey, snapshot);
                    markMaterialized(connectionState, publicationKey, route.secondary,
                        snapshot);
                    clearPendingForTarget(connectionState, publicationKey, route.secondary);
                } catch (Exception e) {
                    queueRetry(connectionState,
                        RetryCommand.register(route.secondary, publicationKey, snapshot), e,
                        parentClientId);
                }
            }
            cleanupStaleTarget(connectionState, parentClientId, publicationKey,
                route.cleanup);
        }
    }
    
    private Target retryTarget(ConnectionState state, PublicationKey key, Route route) {
        if (route.secondary != null) {
            return route.secondary;
        }
        return route.cleanup != null && isMaterialized(state, key, route.cleanup)
            ? route.cleanup : null;
    }
    
    private void cleanupStaleTarget(ConnectionState state, String parentClientId,
        PublicationKey key, Target target) {
        PublicationSnapshot materialized = materializedSnapshot(state, key, target);
        if (materialized == null) {
            return;
        }
        clearPendingForTarget(state, key, target);
        try {
            applyDeregister(target, A2aMigrationMetrics.Role.SECONDARY, parentClientId, key,
                materialized.endpoints.get(0), materialized.sourceIp);
            markNotMaterialized(state, key, target);
        } catch (Exception e) {
            queueRetry(state, RetryCommand.deregister(target, key,
                materialized.endpoints.get(0), materialized.sourceIp), e, parentClientId);
        }
    }
    
    private void validateRegistration(Route route, String namespaceId, String agentName,
        PublicationSnapshot snapshot) throws NacosException {
        if (route.includes(Target.LEGACY)) {
            if (snapshot.single) {
                legacyService.validate(snapshot.endpoints.get(0));
            } else {
                legacyService.validate(snapshot.endpoints);
            }
        }
        if (route.includes(Target.CANONICAL)) {
            canonicalService.validate(namespaceId, agentName, snapshot.endpoints);
        }
    }
    
    private void validateDeregistration(Route route, AgentEndpoint endpoint)
        throws NacosException {
        if (route.includes(Target.LEGACY)) {
            legacyService.validate(endpoint);
        }
    }
    
    private void applyRegister(Target target, A2aMigrationMetrics.Role role,
        String parentClientId, PublicationKey publicationKey, PublicationSnapshot snapshot)
        throws NacosException {
        long start = System.nanoTime();
        try {
            if (Target.CANONICAL == target) {
                canonicalService.register(parentClientId, publicationKey.namespaceId,
                    publicationKey.agentName, snapshot.endpoints);
            } else if (snapshot.single) {
                legacyService.registerChild(parentClientId, publicationKey.namespaceId,
                    publicationKey.agentName, snapshot.endpoints.get(0), snapshot.sourceIp);
            } else {
                legacyService.registerChild(parentClientId, publicationKey.namespaceId,
                    publicationKey.agentName, snapshot.endpoints, snapshot.sourceIp);
            }
            recordEndpointWrite(role, target, A2aMigrationMetrics.Operation.REGISTER,
                A2aMigrationMetrics.Result.SUCCESS, start);
        } catch (NacosException | RuntimeException e) {
            recordEndpointWrite(role, target, A2aMigrationMetrics.Operation.REGISTER,
                A2aMigrationMetrics.Result.FAILED, start);
            throw e;
        }
    }
    
    private void applyDeregister(Target target, A2aMigrationMetrics.Role role,
        String parentClientId, PublicationKey publicationKey, AgentEndpoint endpoint,
        String sourceIp) throws NacosException {
        long start = System.nanoTime();
        try {
            if (Target.CANONICAL == target) {
                canonicalService.deregister(parentClientId, publicationKey.namespaceId,
                    publicationKey.agentName, publicationKey.version);
            } else {
                legacyService.deregisterChild(parentClientId, publicationKey.namespaceId,
                    publicationKey.agentName, endpoint, sourceIp);
            }
            recordEndpointWrite(role, target, A2aMigrationMetrics.Operation.DEREGISTER,
                A2aMigrationMetrics.Result.SUCCESS, start);
        } catch (NacosException | RuntimeException e) {
            recordEndpointWrite(role, target, A2aMigrationMetrics.Operation.DEREGISTER,
                A2aMigrationMetrics.Result.FAILED, start);
            throw e;
        }
    }
    
    private void recordEndpointWrite(A2aMigrationMetrics.Role role, Target target,
        A2aMigrationMetrics.Operation operation, A2aMigrationMetrics.Result result,
        long startNanos) {
        A2aMigrationMetrics.recordEndpointWrite(role,
            A2aMigrationMetrics.Target.valueOf(target.name()), operation, result,
            System.nanoTime() - startNanos);
    }
    
    private void ensureRetryCapacity(ConnectionState state, PublicationKey key,
        Target secondary) throws NacosApiException {
        if (secondary == null || state.pending.containsKey(new RetryKey(key, secondary))) {
            return;
        }
        if (state.pending.size() >= maxPendingPerConnection) {
            throw capacityExceeded("physical retry queue limit of " + maxPendingPerConnection
                + " reached for this Client");
        }
    }
    
    private NacosApiException capacityExceeded(String message) {
        return new NacosApiException(NacosException.OVER_THRESHOLD,
            ErrorCode.AGENT_ENDPOINT_PUBLICATION_OVER_LIMIT,
            "Agent Endpoint " + message + '.');
    }
    
    private void requireEndpoints(Collection<AgentEndpoint> endpoints)
        throws NacosApiException {
        if (endpoints == null || endpoints.isEmpty()) {
            throw invalidEndpoint("Legacy A2A Endpoint batch must not be empty");
        }
        for (AgentEndpoint endpoint : endpoints) {
            requireEndpoint(endpoint);
        }
    }
    
    private void requireEndpoint(AgentEndpoint endpoint) throws NacosApiException {
        if (endpoint == null) {
            throw invalidEndpoint("Legacy A2A Endpoint must not be null");
        }
        if (StringUtils.isBlank(endpoint.getVersion())) {
            throw invalidEndpoint("Legacy A2A Endpoint Version must not be empty");
        }
    }
    
    private NacosApiException invalidEndpoint(String message) {
        return new NacosApiException(NacosException.INVALID_PARAM,
            ErrorCode.PARAMETER_VALIDATE_ERROR, message);
    }
    
    private int countPublications(ConnectionState state) {
        int result = 0;
        for (PublicationSnapshot snapshot : state.publications.values()) {
            result += snapshot.endpoints.size();
        }
        return result;
    }
    
    private String logicalPublicationKey(PublicationKey key) {
        return component(key.namespaceId) + component(key.agentName) + component(key.version);
    }
    
    private void queueRetry(ConnectionState state, RetryCommand command, Exception failure,
        String parentClientId) {
        RetryCommand previous = state.pending.put(command.retryKey, command);
        if (previous == null && state.attached) {
            updatePendingRetryTotal(1);
        }
        A2aMigrationMetrics.record(A2aMigrationMetrics.Event.ENDPOINT_RETRY,
            A2aMigrationMetrics.Result.SCHEDULED);
        LOGGER.warn("Historical A2A physical Endpoint write queued for retry: clientHash={}, "
            + "namespaceHash={}, agentHash={}, versionHash={}, target={}, operation={}, reason={}",
            hash(parentClientId), hash(command.retryKey.publicationKey.namespaceId),
            hash(command.retryKey.publicationKey.agentName),
            hash(command.retryKey.publicationKey.version), command.retryKey.target,
            command.snapshot == null ? "deregister" : "register",
            failure.getClass().getSimpleName());
        scheduleRetry();
    }
    
    private void retryConnection(String parentClientId, ConnectionState state) {
        synchronized (state) {
            // TODO(remove in 4.0): a retry snapshot can outlive its connection-map entry.
            // Never recreate a physical migration shadow after disconnect cleanup won the race.
            if (!state.attached || connectionStates.get(parentClientId) != state) {
                return;
            }
            List<RetryCommand> commands =
                new ArrayList<RetryCommand>(state.pending.values());
            for (RetryCommand command : commands) {
                try {
                    if (command.snapshot == null) {
                        applyDeregister(command.retryKey.target,
                            A2aMigrationMetrics.Role.RETRY, parentClientId,
                            command.retryKey.publicationKey,
                            command.deregistrationEndpoint, command.sourceIp);
                        markNotMaterialized(state, command.retryKey.publicationKey,
                            command.retryKey.target);
                    } else {
                        applyRegister(command.retryKey.target,
                            A2aMigrationMetrics.Role.RETRY, parentClientId,
                            command.retryKey.publicationKey, command.snapshot);
                        markMaterialized(state, command.retryKey.publicationKey,
                            command.retryKey.target, command.snapshot);
                    }
                    if (state.pending.remove(command.retryKey, command) && state.attached) {
                        updatePendingRetryTotal(-1);
                    }
                    A2aMigrationMetrics.record(A2aMigrationMetrics.Event.ENDPOINT_RETRY,
                        A2aMigrationMetrics.Result.SUCCESS);
                } catch (Exception e) {
                    A2aMigrationMetrics.record(A2aMigrationMetrics.Event.ENDPOINT_RETRY,
                        A2aMigrationMetrics.Result.FAILED);
                    LOGGER.debug("Historical A2A physical Endpoint retry is still pending: "
                        + "clientHash={}, namespaceHash={}, agentHash={}, versionHash={}, "
                        + "target={}", hash(parentClientId),
                        hash(command.retryKey.publicationKey.namespaceId),
                        hash(command.retryKey.publicationKey.agentName),
                        hash(command.retryKey.publicationKey.version), command.retryKey.target);
                }
            }
            removeEmptyState(parentClientId, state);
        }
    }
    
    private void scheduleRetry() {
        if (destroyed || !retryScheduled.compareAndSet(false, true)) {
            return;
        }
        try {
            executor.schedule(() -> {
                try {
                    retryPendingNow();
                } finally {
                    retryScheduled.set(false);
                    if (hasPendingRetries()) {
                        scheduleRetry();
                    }
                }
            }, retryIntervalMillis, TimeUnit.MILLISECONDS);
        } catch (RuntimeException e) {
            retryScheduled.set(false);
            if (!destroyed) {
                LOGGER.warn("Failed to schedule historical A2A physical Endpoint retry", e);
            }
        }
    }
    
    private void clearAllPending(ConnectionState state, PublicationKey publicationKey) {
        for (Target target : Target.values()) {
            clearPendingForTarget(state, publicationKey, target);
        }
    }
    
    private void clearPendingForTarget(ConnectionState state, PublicationKey publicationKey,
        Target target) {
        if (state.pending.remove(new RetryKey(publicationKey, target)) != null
            && state.attached) {
            updatePendingRetryTotal(-1);
        }
    }
    
    private void updatePendingRetryTotal(int delta) {
        pendingRetryTotal.addAndGet(delta);
        A2aMigrationMetrics.adjustPendingEndpointRetries(delta);
    }
    
    private boolean isMaterialized(ConnectionState state, PublicationKey key, Target target) {
        return materializedSnapshot(state, key, target) != null;
    }
    
    private PublicationSnapshot materializedSnapshot(ConnectionState state, PublicationKey key,
        Target target) {
        if (target == null) {
            return null;
        }
        Map<Target, PublicationSnapshot> targets = state.materializedTargets.get(key);
        return targets == null ? null : targets.get(target);
    }
    
    private void markMaterialized(ConnectionState state, PublicationKey key, Target target,
        PublicationSnapshot snapshot) {
        state.materializedTargets.computeIfAbsent(key,
            ignored -> new EnumMap<Target, PublicationSnapshot>(Target.class))
            .put(target, snapshot);
    }
    
    private void markNotMaterialized(ConnectionState state, PublicationKey key, Target target) {
        Map<Target, PublicationSnapshot> targets = state.materializedTargets.get(key);
        if (targets == null) {
            return;
        }
        targets.remove(target);
        if (targets.isEmpty()) {
            state.materializedTargets.remove(key);
        }
    }
    
    private ConnectionState connectionState(String parentClientId) {
        return connectionStates.computeIfAbsent(parentClientId, key -> new ConnectionState());
    }
    
    private void removeEmptyState(String parentClientId, ConnectionState state) {
        if (!state.publications.isEmpty() || !state.pending.isEmpty()
            || !state.materializedTargets.isEmpty()) {
            return;
        }
        if (connectionStates.remove(parentClientId, state)) {
            state.attached = false;
        }
    }
    
    private Route route(A2aMigrationState state) {
        if (state == null) {
            throw new IllegalArgumentException("A2A migration Endpoint state must not be null");
        }
        if (A2aMigrationState.CANONICAL == state) {
            return stateService.isLegacyNamingShadowEnabled()
                ? Route.CANONICAL_WITH_SHADOW : Route.CANONICAL_ONLY;
        }
        return Route.SYNCING;
    }
    
    private static List<AgentEndpoint> copy(Collection<AgentEndpoint> source) {
        List<AgentEndpoint> result = new ArrayList<AgentEndpoint>(source.size());
        for (AgentEndpoint endpoint : source) {
            result.add(copy(endpoint));
        }
        return Collections.unmodifiableList(result);
    }
    
    private static AgentEndpoint copy(AgentEndpoint source) {
        AgentEndpoint result = new AgentEndpoint();
        result.setTransport(source.getTransport());
        result.setAddress(source.getAddress());
        result.setPort(source.getPort());
        result.setPath(source.getPath());
        result.setSupportTls(source.isSupportTls());
        result.setVersion(source.getVersion());
        result.setProtocolVersion(source.getProtocolVersion());
        result.setTenant(source.getTenant());
        result.setProtocol(source.getProtocol());
        result.setQuery(source.getQuery());
        return result;
    }
    
    private static String hash(String value) {
        return value == null ? "null" : Integer.toHexString(value.hashCode());
    }
    
    private String component(String value) {
        String normalized = value == null ? "" : value;
        return normalized.length() + ":" + normalized;
    }
    
    private enum Target {
        LEGACY,
        CANONICAL
    }
    
    private enum Route {
        
        SYNCING(Target.LEGACY, Target.CANONICAL, null),
        CANONICAL_ONLY(Target.CANONICAL, null, Target.LEGACY),
        CANONICAL_WITH_SHADOW(Target.CANONICAL, Target.LEGACY, null);
        
        private final Target primary;
        
        private final Target secondary;
        
        private final Target cleanup;
        
        Route(Target primary, Target secondary, Target cleanup) {
            this.primary = primary;
            this.secondary = secondary;
            this.cleanup = cleanup;
        }
        
        private boolean includes(Target target) {
            return primary == target || secondary == target;
        }
    }
    
    private static final class ConnectionState {
        
        private final Map<PublicationKey, PublicationSnapshot> publications =
            new LinkedHashMap<PublicationKey, PublicationSnapshot>();
        
        private final Map<RetryKey, RetryCommand> pending =
            new LinkedHashMap<RetryKey, RetryCommand>();
        
        private final Map<PublicationKey, Map<Target, PublicationSnapshot>> materializedTargets =
            new LinkedHashMap<PublicationKey, Map<Target, PublicationSnapshot>>();
        
        private boolean attached = true;
    }
    
    private static final class PublicationKey {
        
        private final String namespaceId;
        
        private final String agentName;
        
        private final String version;
        
        private PublicationKey(String namespaceId, String agentName, String version) {
            this.namespaceId = namespaceId;
            this.agentName = agentName;
            this.version = version;
        }
        
        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            PublicationKey that = (PublicationKey) o;
            return Objects.equals(namespaceId, that.namespaceId)
                && Objects.equals(agentName, that.agentName)
                && Objects.equals(version, that.version);
        }
        
        @Override
        public int hashCode() {
            return Objects.hash(namespaceId, agentName, version);
        }
    }
    
    private static final class PublicationSnapshot {
        
        private final List<AgentEndpoint> endpoints;
        
        private final boolean single;
        
        private final String sourceIp;
        
        private PublicationSnapshot(List<AgentEndpoint> endpoints, boolean single,
            String sourceIp) {
            this.endpoints = endpoints;
            this.single = single;
            this.sourceIp = sourceIp;
        }
        
        private String version() {
            return endpoints.get(0).getVersion();
        }
    }
    
    private static final class RetryKey {
        
        private final PublicationKey publicationKey;
        
        private final Target target;
        
        private RetryKey(PublicationKey publicationKey, Target target) {
            this.publicationKey = publicationKey;
            this.target = target;
        }
        
        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            RetryKey retryKey = (RetryKey) o;
            return Objects.equals(publicationKey, retryKey.publicationKey)
                && target == retryKey.target;
        }
        
        @Override
        public int hashCode() {
            return Objects.hash(publicationKey, target);
        }
    }
    
    private static final class RetryCommand {
        
        private final RetryKey retryKey;
        
        private final PublicationSnapshot snapshot;
        
        private final AgentEndpoint deregistrationEndpoint;
        
        private final String sourceIp;
        
        private RetryCommand(RetryKey retryKey, PublicationSnapshot snapshot,
            AgentEndpoint deregistrationEndpoint, String sourceIp) {
            this.retryKey = retryKey;
            this.snapshot = snapshot;
            this.deregistrationEndpoint = deregistrationEndpoint;
            this.sourceIp = sourceIp;
        }
        
        private static RetryCommand register(Target target, PublicationKey key,
            PublicationSnapshot snapshot) {
            return new RetryCommand(new RetryKey(key, target), snapshot, null,
                snapshot.sourceIp);
        }
        
        private static RetryCommand deregister(Target target, PublicationKey key,
            AgentEndpoint endpoint, String sourceIp) {
            return new RetryCommand(new RetryKey(key, target), null, endpoint, sourceIp);
        }
    }
}
