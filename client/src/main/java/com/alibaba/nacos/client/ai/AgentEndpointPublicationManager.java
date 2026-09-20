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

import com.alibaba.nacos.api.ai.model.ClientLivenessInfo;
import com.alibaba.nacos.api.ai.model.a2a.AgentEndpoint;
import com.alibaba.nacos.api.ai.utils.AgentValidationUtils;
import com.alibaba.nacos.client.ai.remote.redo.AgentEndpointPublicationRedoData;
import java.util.Collection;
import com.alibaba.nacos.api.ai.model.agent.Endpoint;
import com.alibaba.nacos.api.ai.model.agent.AgentEndpointRegistrationBatch;
import com.alibaba.nacos.api.ai.utils.EndpointNaturalKey;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.client.ai.remote.AgentTransportType;
import com.alibaba.nacos.client.ai.remote.AgentTransportRouter;
import com.alibaba.nacos.client.ai.utils.AgentModelUtils;
import com.alibaba.nacos.client.utils.LogUtils;
import com.alibaba.nacos.common.lifecycle.Closeable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Stores complete Agent Endpoint publication intent and owns HTTP heartbeat/redo.
 *
 * <p>The manager never resolves transport configuration. It asks the transport router once for a
 * new publication owner and keeps that owner with the publication's in-memory intent.</p>
 *
 * @author Nacos
 */
class AgentEndpointPublicationManager implements Closeable, AiHttpPublicationParticipant {
    
    private static final Logger LOGGER =
        LogUtils.logger(AgentEndpointPublicationManager.class);
    
    private final AgentTransportRouter transportRouter;
    
    private final AiHttpPublicationCoordinator coordinator;
    
    private final boolean ownsCoordinator;
    
    private final int maxPublications;
    
    private final Map<PublicationKey, PublicationState> publications =
        new HashMap<PublicationKey, PublicationState>();
    
    private boolean closed;
    
    AgentEndpointPublicationManager(AgentTransportRouter transportRouter) {
        this(transportRouter, new AiHttpPublicationCoordinator(),
            com.alibaba.nacos.api.ai.constant.AiConstants.DEFAULT_AI_AGENT_ENDPOINT_MAX_PUBLICATIONS,
            true);
    }
    
    AgentEndpointPublicationManager(AgentTransportRouter transportRouter,
        int maxPublications) {
        this(transportRouter, new AiHttpPublicationCoordinator(), maxPublications, true);
    }
    
    AgentEndpointPublicationManager(AgentTransportRouter transportRouter,
        ScheduledExecutorService executor) {
        this(transportRouter, new AiHttpPublicationCoordinator(executor),
            com.alibaba.nacos.api.ai.constant.AiConstants.DEFAULT_AI_AGENT_ENDPOINT_MAX_PUBLICATIONS,
            true);
    }
    
    AgentEndpointPublicationManager(AgentTransportRouter transportRouter,
        ScheduledExecutorService executor, int maxPublications) {
        this(transportRouter, new AiHttpPublicationCoordinator(executor), maxPublications, true);
    }
    
    AgentEndpointPublicationManager(AgentTransportRouter transportRouter,
        AiHttpPublicationCoordinator coordinator, int maxPublications) {
        this(transportRouter, coordinator, maxPublications, false);
    }
    
    private AgentEndpointPublicationManager(AgentTransportRouter transportRouter,
        AiHttpPublicationCoordinator coordinator, int maxPublications,
        boolean ownsCoordinator) {
        if (maxPublications < 1) {
            throw new IllegalArgumentException("maxPublications must be greater than 0");
        }
        this.transportRouter = transportRouter;
        this.coordinator = coordinator;
        this.ownsCoordinator = ownsCoordinator;
        this.maxPublications = maxPublications;
        coordinator.register(this);
    }
    
    synchronized void register(String namespaceId, AgentEndpointRegistrationBatch batch)
        throws NacosException {
        checkOpen();
        PublicationKey key = PublicationKey.of(namespaceId, batch);
        PublicationState previous = publications.get(key);
        checkSource(previous, false);
        write(key, previous, batch, null);
    }
    
    synchronized void registerA2a(String namespaceId, String agentName,
        Collection<AgentEndpoint> endpoints) throws NacosException {
        checkOpen();
        PublicationKey key = new PublicationKey(namespaceId, agentName, "a2a");
        PublicationState previous = publications.get(key);
        checkSource(previous, true);
        A2aEndpointIntent intent = A2aEndpointIntent.replace(namespaceId, agentName,
            previous == null ? null : previous.a2aIntent, endpoints);
        write(key, previous, intent.batch(), intent);
    }
    
    synchronized void deregisterA2a(String namespaceId, String agentName, String version)
        throws NacosException {
        checkOpen();
        try {
            AgentValidationUtils.validateNamespaceId(namespaceId);
            AgentValidationUtils.validateAgentName(agentName);
            AgentValidationUtils.validateVersion(version);
        } catch (IllegalArgumentException e) {
            throw new NacosException(NacosException.INVALID_PARAM, e.getMessage());
        }
        PublicationKey key = new PublicationKey(namespaceId, agentName, "a2a");
        PublicationState previous = publications.get(key);
        checkSource(previous, true);
        if (previous == null || previous.batch == null) {
            return;
        }
        A2aEndpointIntent intent = previous.a2aIntent.remove(namespaceId, agentName, version);
        if (intent != previous.a2aIntent) {
            write(key, previous, intent.batch(), intent);
        }
    }
    
    private void checkOpen() throws NacosException {
        if (closed) {
            throw new NacosException(NacosException.CLIENT_DISCONNECT,
                "Agent publication manager is closed.");
        }
    }
    
    private void checkSource(PublicationState previous, boolean a2a) throws NacosException {
        if (previous != null && (previous.a2aIntent != null) != a2a) {
            throw new NacosException(NacosException.CONFLICT,
                "A2A and native RAD cannot write the same live Agent/protocol publication "
                    + "in one SDK instance. Deregister its current contribution first.");
        }
    }
    
    private void write(PublicationKey key, PublicationState previous,
        AgentEndpointRegistrationBatch batch, A2aEndpointIntent intent) throws NacosException {
        AgentTransportType ownerTransport = previous == null
            ? transportRouter.selectPublicationTransport() : previous.ownerTransport;
        int nextCount = batch == null ? 0 : batch.getEndpoints().size();
        if (countPublicationEntries() >= maxPublications
            && nextCount > countPublicationEntries(previous)) {
            throw new NacosApiException(NacosException.CLIENT_OVER_THRESHOLD,
                ErrorCode.AGENT_ENDPOINT_PUBLICATION_OVER_LIMIT,
                "Agent Endpoint publication limit of " + maxPublications
                    + " reached for this SDK Client.");
        }
        PublicationState desired = new PublicationState(batch == null ? null
            : AgentModelUtils.copyRegistrationBatch(batch), ownerTransport, true, intent);
        desired.redoBatch = batch == null ? previous.redoBatch : desired.batch;
        publications.put(key, desired);
        try {
            ClientLivenessInfo liveness = send(key, desired);
            notifyCoordinator(desired.ownerTransport == AgentTransportType.HTTP ? liveness : null);
        } catch (NacosException e) {
            handleWriteFailure(key, previous, desired, e);
            throw e;
        }
    }
    
    private ClientLivenessInfo send(PublicationKey key, PublicationState state)
        throws NacosException {
        if (state.batch == null) {
            transportRouter.deregisterAgentEndpoints(key.namespaceId, key.agentName,
                key.protocol, state.ownerTransport);
            publications.remove(key);
            return null;
        }
        ClientLivenessInfo result = transportRouter.registerAgentEndpoints(key.namespaceId,
            state.batch, state.ownerTransport);
        state.dirty = false;
        state.rollback = null;
        return result;
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
    
    synchronized void deregister(String namespaceId, String agentName, String protocol,
        List<Endpoint> endpoints)
        throws NacosException {
        checkOpen();
        PublicationKey key = new PublicationKey(namespaceId, agentName, protocol);
        PublicationState previous = publications.get(key);
        checkSource(previous, false);
        if (previous == null || previous.batch == null) {
            return;
        }
        Set<EndpointNaturalKey> removals = naturalKeys(namespaceId, agentName, protocol, endpoints);
        AgentEndpointRegistrationBatch remainder =
            removeEndpoints(namespaceId, previous.batch, removals);
        if (remainder.getEndpoints().size() != previous.batch.getEndpoints().size()) {
            write(key, previous, remainder.getEndpoints().isEmpty() ? null : remainder, null);
        }
    }
    
    private void handleWriteFailure(PublicationKey key, PublicationState previous,
        PublicationState desired, NacosException exception) {
        if (isCapacityRejected(exception)) {
            publications.remove(key);
        } else if (isRetryable(exception)) {
            desired.dirty = true;
            desired.rollback = previous;
        } else if (previous == null) {
            publications.remove(key);
        } else {
            publications.put(key, previous);
        }
        notifyCoordinator(null);
    }
    
    private boolean isRetryable(NacosException exception) {
        return exception.getErrCode() != ErrorCode.AGENT_MIGRATION_IN_PROGRESS.getCode()
            && exception.getErrCode() != NacosException.SERVER_NOT_IMPLEMENTED
            && !isCapacityRejected(exception)
            && (exception.getErrCode() >= NacosException.SERVER_ERROR
                || exception.getErrCode() == NacosException.HTTP_CLIENT_ERROR_CODE);
    }
    
    private boolean isCapacityRejected(NacosException exception) {
        return exception instanceof NacosApiException
            && ((NacosApiException) exception)
                .getDetailErrCode() == ErrorCode.AGENT_ENDPOINT_PUBLICATION_OVER_LIMIT.getCode();
    }
    
    synchronized void discardAfterRemoteCapacityRejection(String namespaceId,
        AgentEndpointRegistrationBatch batch) {
        publications.remove(PublicationKey.of(namespaceId, batch));
        notifyCoordinator(null);
    }
    
    private Set<EndpointNaturalKey> naturalKeys(String namespaceId, String agentName,
        String protocol, List<Endpoint> endpoints) {
        Set<EndpointNaturalKey> result = new HashSet<EndpointNaturalKey>();
        for (Endpoint endpoint : endpoints) {
            result.add(EndpointNaturalKey.of(namespaceId, agentName, protocol, endpoint));
        }
        return result;
    }
    
    private AgentEndpointRegistrationBatch removeEndpoints(String namespaceId,
        AgentEndpointRegistrationBatch current, Set<EndpointNaturalKey> removals) {
        AgentEndpointRegistrationBatch result =
            AgentModelUtils.copyRegistrationBatch(current);
        List<Endpoint> retained = new ArrayList<Endpoint>();
        for (Endpoint endpoint : current.getEndpoints()) {
            EndpointNaturalKey key = EndpointNaturalKey.of(namespaceId,
                current.getAgentName(), current.getProtocol(), endpoint);
            if (!removals.contains(key)) {
                retained.add(endpoint);
            }
        }
        result.setEndpoints(retained);
        return result;
    }
    
    private void notifyCoordinator(ClientLivenessInfo liveness) {
        coordinator.stateChanged(this, liveness, hasHttpPublication());
    }
    
    @Override
    public synchronized void redoDirtyHttpPublications() {
        ClientLivenessInfo liveness = null;
        List<Map.Entry<PublicationKey, PublicationState>> entries =
            new ArrayList<Map.Entry<PublicationKey, PublicationState>>(publications.entrySet());
        for (Map.Entry<PublicationKey, PublicationState> entry : entries) {
            PublicationState state = entry.getValue();
            if (!state.dirty || state.ownerTransport != AgentTransportType.HTTP) {
                continue;
            }
            try {
                liveness = send(entry.getKey(), state);
            } catch (NacosException e) {
                if (isCapacityRejected(e)) {
                    publications.remove(entry.getKey());
                } else if (!isRetryable(e)) {
                    restorePrevious(entry.getKey(), state);
                }
                LOGGER.warn("Redo Agent Endpoint HTTP publication failed.", e);
            }
        }
        notifyCoordinator(liveness);
    }
    
    synchronized void redoGrpcPublication(AgentEndpointPublicationRedoData redoData,
        java.util.function.Consumer<AgentEndpointPublicationRedoData> reconcileRedo) {
        AgentEndpointRegistrationBatch expected = redoData.get();
        PublicationKey key = PublicationKey.of(redoData.getNamespaceId(), expected);
        PublicationState state = publications.get(key);
        if (closed || state == null) {
            reconcileRedo.accept(null);
            return;
        }
        if (state.ownerTransport != AgentTransportType.GRPC || state.redoBatch != expected
            || redoData
                .getRedoType() == com.alibaba.nacos.client.redo.data.RedoData.RedoType.NONE) {
            return;
        }
        try {
            send(key, state);
            if (state.batch == null) {
                reconcileRedo.accept(null);
            }
        } catch (NacosException e) {
            if (isCapacityRejected(e)) {
                publications.remove(key);
                reconcileRedo.accept(null);
            } else if (!isRetryable(e)) {
                if (state.dirty) {
                    restorePrevious(key, state);
                }
                reconcileRedo.accept(redoSnapshot(key, publications.get(key)));
            }
            LOGGER.warn("Redo Agent Endpoint gRPC publication failed.", e);
        }
    }
    
    private AgentEndpointPublicationRedoData redoSnapshot(PublicationKey key,
        PublicationState state) {
        if (state == null) {
            return null;
        }
        AgentEndpointPublicationRedoData result =
            new AgentEndpointPublicationRedoData(key.namespaceId,
                state.redoBatch);
        result.setExpectedRegistered(state.batch != null);
        result.setRegistered(!state.dirty || state.batch == null);
        result.setUnregistering(state.batch == null);
        return result;
    }
    
    private void restorePrevious(PublicationKey key, PublicationState state) {
        if (state.rollback == null) {
            publications.remove(key);
        } else {
            publications.put(key, state.rollback);
        }
    }
    
    @Override
    public synchronized void markHttpPublicationsDirty() {
        for (PublicationState state : publications.values()) {
            if (state.batch != null && state.ownerTransport == AgentTransportType.HTTP) {
                state.dirty = true;
            }
        }
    }
    
    @Override
    public synchronized boolean hasRegisteredHttpPublication() {
        for (PublicationState state : publications.values()) {
            if (state.ownerTransport == AgentTransportType.HTTP && state.batch != null
                && !state.dirty) {
                return true;
            }
        }
        return false;
    }
    
    @Override
    public synchronized boolean hasHttpPublication() {
        for (PublicationState state : publications.values()) {
            if (state.ownerTransport == AgentTransportType.HTTP) {
                return true;
            }
        }
        return false;
    }
    
    @Override
    public ClientLivenessInfo heartbeat() throws NacosException {
        return transportRouter.heartbeatAgentEndpoints(AgentTransportType.HTTP);
    }
    
    @Override
    public String getPublicationModuleName() {
        return "Agent";
    }
    
    @Override
    public synchronized void shutdown() {
        if (closed) {
            return;
        }
        closed = true;
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
        notifyCoordinator(null);
        if (ownsCoordinator) {
            coordinator.shutdown();
        }
    }
    
    private static final class PublicationState {
        
        private final AgentEndpointRegistrationBatch batch;
        
        private final AgentTransportType ownerTransport;
        
        private final A2aEndpointIntent a2aIntent;
        
        private AgentEndpointRegistrationBatch redoBatch;
        
        private boolean dirty;
        
        private PublicationState rollback;
        
        private PublicationState(AgentEndpointRegistrationBatch batch,
            AgentTransportType ownerTransport, boolean dirty, A2aEndpointIntent a2aIntent) {
            this.batch = batch;
            this.a2aIntent = a2aIntent;
            this.redoBatch = batch;
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
        
        private static PublicationKey of(String namespaceId, AgentEndpointRegistrationBatch batch) {
            return new PublicationKey(namespaceId, batch.getAgentName(),
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
