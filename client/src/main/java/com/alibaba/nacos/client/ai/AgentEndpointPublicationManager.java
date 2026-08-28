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

package com.alibaba.nacos.client.ai;

import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.api.ai.model.agent.ClientLivenessInfo;
import com.alibaba.nacos.api.ai.model.agent.Endpoint;
import com.alibaba.nacos.api.ai.model.rad.AgentEndpointDeregistrationBatch;
import com.alibaba.nacos.api.ai.model.rad.AgentEndpointRegistrationBatch;
import com.alibaba.nacos.api.ai.utils.EndpointNaturalKey;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.client.ai.remote.AgentTransportType;
import com.alibaba.nacos.client.ai.remote.AgentTransportRouter;
import com.alibaba.nacos.client.ai.utils.AgentModelUtils;
import com.alibaba.nacos.client.utils.LogUtils;
import com.alibaba.nacos.common.executor.NameThreadFactory;
import com.alibaba.nacos.common.lifecycle.Closeable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Stores complete Agent Endpoint publication intent and owns HTTP heartbeat/redo.
 *
 * <p>The manager never resolves transport configuration. It asks the transport router once for a
 * new publication owner and keeps that owner with the publication's in-memory intent.</p>
 *
 * @author Nacos
 */
class AgentEndpointPublicationManager implements Closeable {
    
    private static final Logger LOGGER =
        LogUtils.logger(AgentEndpointPublicationManager.class);
    
    private final AgentTransportRouter transportRouter;
    
    private final ScheduledExecutorService executor;
    
    private final int maxPublications;
    
    private final Map<PublicationKey, PublicationState> publications =
        new HashMap<PublicationKey, PublicationState>();
    
    private long heartbeatIntervalMillis = AiConstants.DEFAULT_AI_CACHE_UPDATE_INTERVAL;
    
    private ScheduledFuture<?> maintenanceFuture;
    
    private boolean closed;
    
    AgentEndpointPublicationManager(AgentTransportRouter transportRouter) {
        this(transportRouter,
            AiConstants.DEFAULT_AI_AGENT_ENDPOINT_MAX_PUBLICATIONS);
    }
    
    AgentEndpointPublicationManager(AgentTransportRouter transportRouter,
        int maxPublications) {
        this(transportRouter, new ScheduledThreadPoolExecutor(1,
            new NameThreadFactory("com.alibaba.nacos.client.ai.agent.endpoint")),
            maxPublications);
    }
    
    AgentEndpointPublicationManager(AgentTransportRouter transportRouter,
        ScheduledExecutorService executor) {
        this(transportRouter, executor,
            AiConstants.DEFAULT_AI_AGENT_ENDPOINT_MAX_PUBLICATIONS);
    }
    
    AgentEndpointPublicationManager(AgentTransportRouter transportRouter,
        ScheduledExecutorService executor, int maxPublications) {
        if (maxPublications < 1) {
            throw new IllegalArgumentException("maxPublications must be greater than 0");
        }
        this.transportRouter = transportRouter;
        this.executor = executor;
        this.maxPublications = maxPublications;
    }
    
    synchronized void register(AgentEndpointRegistrationBatch batch) throws NacosException {
        PublicationKey key = PublicationKey.of(batch);
        PublicationState previous = publications.get(key);
        AgentTransportType ownerTransport = previous == null
            ? transportRouter.selectPublicationTransport() : previous.ownerTransport;
        int previousPublicationCount = countPublicationEntries(previous);
        if (countPublicationEntries() >= maxPublications
            && batch.getEndpoints().size() > previousPublicationCount) {
            throw new NacosApiException(NacosException.CLIENT_OVER_THRESHOLD,
                ErrorCode.AGENT_ENDPOINT_PUBLICATION_OVER_LIMIT,
                "Agent Endpoint publication limit of " + maxPublications
                    + " reached for this SDK Client.");
        }
        PublicationState desired =
            new PublicationState(AgentModelUtils.copyRegistrationBatch(batch), ownerTransport,
                true);
        publications.put(key, desired);
        try {
            ClientLivenessInfo liveness = transportRouter.registerAgentEndpoints(desired.batch,
                desired.ownerTransport);
            desired.dirty = false;
            desired.rollback = null;
            updateLiveness(liveness, desired.ownerTransport);
            scheduleMaintenanceIfRequired();
        } catch (NacosException e) {
            handleWriteFailure(key, previous, desired, e);
            throw e;
        }
    }
    
    private int countPublicationEntries() {
        int result = 0;
        for (PublicationState state : publications.values()) {
            result += countPublicationEntries(state);
        }
        return result;
    }
    
    private int countPublicationEntries(PublicationState state) {
        return state == null || state.batch == null ? 0 : state.batch.getEndpoints().size();
    }
    
    synchronized void deregister(AgentEndpointDeregistrationBatch batch)
        throws NacosException {
        PublicationKey key = PublicationKey.of(batch);
        PublicationState previous = publications.get(key);
        if (previous == null || previous.batch == null) {
            return;
        }
        Set<EndpointNaturalKey> removals = naturalKeys(batch.getNamespaceId(),
            batch.getAgentName(), batch.getProtocol(), batch.getEndpoints());
        AgentEndpointRegistrationBatch remainder = removeEndpoints(previous.batch, removals);
        if (remainder.getEndpoints().size() == previous.batch.getEndpoints().size()) {
            return;
        }
        PublicationState desired = new PublicationState(
            remainder.getEndpoints().isEmpty() ? null : remainder, previous.ownerTransport,
            true);
        publications.put(key, desired);
        try {
            if (desired.batch == null) {
                transportRouter.deregisterAgentEndpoints(key.namespaceId, key.agentName,
                    key.protocol, desired.ownerTransport);
                publications.remove(key);
            } else {
                ClientLivenessInfo liveness =
                    transportRouter.registerAgentEndpoints(desired.batch,
                        desired.ownerTransport);
                desired.dirty = false;
                desired.rollback = null;
                updateLiveness(liveness, desired.ownerTransport);
            }
            scheduleMaintenanceIfRequired();
        } catch (NacosException e) {
            handleWriteFailure(key, previous, desired, e);
            throw e;
        }
    }
    
    private void handleWriteFailure(PublicationKey key, PublicationState previous,
        PublicationState desired, NacosException exception) {
        if (isCapacityRejected(exception)) {
            publications.remove(key);
        } else if (isRetryable(exception)) {
            desired.dirty = true;
            desired.rollback = previous;
            scheduleMaintenanceIfRequired();
        } else if (previous == null) {
            publications.remove(key);
        } else {
            publications.put(key, previous);
        }
    }
    
    private boolean isRetryable(NacosException exception) {
        return !isCapacityRejected(exception)
            && (exception.getErrCode() >= NacosException.SERVER_ERROR
                || exception.getErrCode() == NacosException.HTTP_CLIENT_ERROR_CODE);
    }
    
    private boolean isCapacityRejected(NacosException exception) {
        return exception instanceof NacosApiException
            && ((NacosApiException) exception)
                .getDetailErrCode() == ErrorCode.AGENT_ENDPOINT_PUBLICATION_OVER_LIMIT.getCode();
    }
    
    synchronized void discardAfterRemoteCapacityRejection(
        AgentEndpointRegistrationBatch batch) {
        publications.remove(PublicationKey.of(batch));
        scheduleMaintenanceIfRequired();
    }
    
    private Set<EndpointNaturalKey> naturalKeys(String namespaceId, String agentName,
        String protocol, List<Endpoint> endpoints) {
        Set<EndpointNaturalKey> result = new HashSet<EndpointNaturalKey>();
        for (Endpoint endpoint : endpoints) {
            result.add(EndpointNaturalKey.of(namespaceId, agentName, protocol, endpoint));
        }
        return result;
    }
    
    private AgentEndpointRegistrationBatch removeEndpoints(
        AgentEndpointRegistrationBatch current, Set<EndpointNaturalKey> removals) {
        AgentEndpointRegistrationBatch result =
            AgentModelUtils.copyRegistrationBatch(current);
        List<Endpoint> retained = new ArrayList<Endpoint>();
        for (Endpoint endpoint : current.getEndpoints()) {
            EndpointNaturalKey key = EndpointNaturalKey.of(current.getNamespaceId(),
                current.getAgentName(), current.getProtocol(), endpoint);
            if (!removals.contains(key)) {
                retained.add(endpoint);
            }
        }
        result.setEndpoints(retained);
        return result;
    }
    
    private void updateLiveness(ClientLivenessInfo liveness,
        AgentTransportType ownerTransport) {
        if (ownerTransport == AgentTransportType.HTTP && liveness != null
            && liveness.getHeartbeatIntervalMillis() > 0) {
            heartbeatIntervalMillis = liveness.getHeartbeatIntervalMillis();
        }
    }
    
    private void scheduleMaintenanceIfRequired() {
        if (closed || !hasHttpPublication()) {
            cancelMaintenance();
            return;
        }
        if (maintenanceFuture == null || maintenanceFuture.isDone()) {
            maintenanceFuture = executor.schedule(new Runnable() {
                
                @Override
                public void run() {
                    maintainHttpPublications();
                }
            }, heartbeatIntervalMillis, TimeUnit.MILLISECONDS);
        }
    }
    
    private synchronized void maintainHttpPublications() {
        maintenanceFuture = null;
        if (closed || publications.isEmpty()) {
            return;
        }
        redoDirtyPublications();
        if (hasRegisteredHttpPublication()) {
            try {
                updateLiveness(
                    transportRouter.heartbeatAgentEndpoints(AgentTransportType.HTTP),
                    AgentTransportType.HTTP);
            } catch (NacosException e) {
                if (e.getErrCode() == ErrorCode.HTTP_CLIENT_NOT_FOUND.getCode()) {
                    markRegistrationsDirty();
                    redoDirtyPublications();
                } else {
                    LOGGER.warn("Agent Endpoint HTTP heartbeat failed.", e);
                }
            }
        }
        scheduleMaintenanceIfRequired();
    }
    
    private void redoDirtyPublications() {
        List<Map.Entry<PublicationKey, PublicationState>> entries =
            new ArrayList<Map.Entry<PublicationKey, PublicationState>>(publications.entrySet());
        for (Map.Entry<PublicationKey, PublicationState> entry : entries) {
            PublicationState state = entry.getValue();
            if (!state.dirty || state.ownerTransport != AgentTransportType.HTTP) {
                continue;
            }
            try {
                if (state.batch == null) {
                    PublicationKey key = entry.getKey();
                    transportRouter.deregisterAgentEndpoints(key.namespaceId, key.agentName,
                        key.protocol, state.ownerTransport);
                    publications.remove(key);
                } else {
                    updateLiveness(transportRouter.registerAgentEndpoints(state.batch,
                        state.ownerTransport), state.ownerTransport);
                    state.dirty = false;
                    state.rollback = null;
                }
            } catch (NacosException e) {
                if (isCapacityRejected(e)) {
                    publications.remove(entry.getKey());
                } else if (!isRetryable(e)) {
                    restorePrevious(entry.getKey(), state);
                }
                LOGGER.warn("Redo Agent Endpoint HTTP publication failed.", e);
            }
        }
    }
    
    private void restorePrevious(PublicationKey key, PublicationState state) {
        if (state.rollback == null) {
            publications.remove(key);
        } else {
            publications.put(key, state.rollback);
        }
    }
    
    private void markRegistrationsDirty() {
        for (PublicationState state : publications.values()) {
            if (state.batch != null && state.ownerTransport == AgentTransportType.HTTP) {
                state.dirty = true;
            }
        }
    }
    
    private boolean hasRegisteredHttpPublication() {
        for (PublicationState state : publications.values()) {
            if (state.ownerTransport == AgentTransportType.HTTP && state.batch != null
                && !state.dirty) {
                return true;
            }
        }
        return false;
    }
    
    private boolean hasHttpPublication() {
        for (PublicationState state : publications.values()) {
            if (state.ownerTransport == AgentTransportType.HTTP) {
                return true;
            }
        }
        return false;
    }
    
    private void cancelMaintenance() {
        if (maintenanceFuture != null) {
            maintenanceFuture.cancel(false);
            maintenanceFuture = null;
        }
    }
    
    @Override
    public synchronized void shutdown() {
        if (closed) {
            return;
        }
        closed = true;
        cancelMaintenance();
        for (Map.Entry<PublicationKey, PublicationState> entry : new ArrayList<Map.Entry<PublicationKey, PublicationState>>(
            publications.entrySet())) {
            PublicationKey key = entry.getKey();
            try {
                transportRouter.deregisterAgentEndpoints(key.namespaceId, key.agentName,
                    key.protocol, entry.getValue().ownerTransport);
            } catch (NacosException e) {
                LOGGER.warn("Best-effort Agent Endpoint deregistration during shutdown failed.",
                    e);
            }
        }
        publications.clear();
        if (executor != null) {
            executor.shutdownNow();
        }
    }
    
    private static final class PublicationState {
        
        private final AgentEndpointRegistrationBatch batch;
        
        private final AgentTransportType ownerTransport;
        
        private boolean dirty;
        
        private PublicationState rollback;
        
        private PublicationState(AgentEndpointRegistrationBatch batch,
            AgentTransportType ownerTransport, boolean dirty) {
            this.batch = batch;
            this.ownerTransport = ownerTransport;
            this.dirty = dirty;
        }
    }
    
    private static final class PublicationKey {
        
        private final String namespaceId;
        
        private final String agentName;
        
        private final String protocol;
        
        private PublicationKey(String namespaceId, String agentName, String protocol) {
            this.namespaceId = namespaceId;
            this.agentName = agentName;
            this.protocol = protocol;
        }
        
        private static PublicationKey of(AgentEndpointRegistrationBatch batch) {
            return new PublicationKey(batch.getNamespaceId(), batch.getAgentName(),
                batch.getProtocol());
        }
        
        private static PublicationKey of(AgentEndpointDeregistrationBatch batch) {
            return new PublicationKey(batch.getNamespaceId(), batch.getAgentName(),
                batch.getProtocol());
        }
        
        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof PublicationKey)) {
                return false;
            }
            PublicationKey other = (PublicationKey) obj;
            return namespaceId.equals(other.namespaceId) && agentName.equals(other.agentName)
                && protocol.equals(other.protocol);
        }
        
        @Override
        public int hashCode() {
            int result = namespaceId.hashCode();
            result = 31 * result + agentName.hashCode();
            return 31 * result + protocol.hashCode();
        }
    }
}
