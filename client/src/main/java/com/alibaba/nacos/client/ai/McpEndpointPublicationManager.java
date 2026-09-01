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

import com.alibaba.nacos.api.ai.model.agent.ClientLivenessInfo;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.client.ai.remote.AgentTransportType;
import com.alibaba.nacos.client.ai.remote.McpTransportRouter;
import com.alibaba.nacos.client.utils.LogUtils;
import com.alibaba.nacos.common.lifecycle.Closeable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * Stores MCP Runtime Endpoint intent and participates in shared HTTP heartbeat and redo.
 *
 * @author Nacos
 */
class McpEndpointPublicationManager implements Closeable, AiHttpPublicationParticipant {
    
    private static final Logger LOGGER =
        LogUtils.logger(McpEndpointPublicationManager.class);
    
    private final McpTransportRouter transportRouter;
    
    private final AiHttpPublicationCoordinator coordinator;
    
    private final Map<String, PublicationState> publications =
        new HashMap<String, PublicationState>();
    
    private boolean closed;
    
    McpEndpointPublicationManager(McpTransportRouter transportRouter,
        AiHttpPublicationCoordinator coordinator) {
        this.transportRouter = transportRouter;
        this.coordinator = coordinator;
        coordinator.register(this);
    }
    
    synchronized void register(String mcpName, String address, int port, String version)
        throws NacosException {
        PublicationState previous = publications.get(mcpName);
        AgentTransportType owner = previous == null
            ? transportRouter.selectPublicationTransport() : previous.ownerTransport;
        PublicationState desired = new PublicationState(mcpName, address, port, version, owner,
            false, true);
        publications.put(mcpName, desired);
        try {
            ClientLivenessInfo liveness = transportRouter.registerMcpServerEndpoint(mcpName,
                address, port, version, owner);
            desired.dirty = false;
            desired.rollback = null;
            notifyCoordinator(owner == AgentTransportType.HTTP ? liveness : null);
        } catch (NacosException e) {
            handleFailure(mcpName, previous, desired, e);
            throw e;
        }
    }
    
    synchronized void deregister(String mcpName, String address, int port)
        throws NacosException {
        PublicationState previous = publications.get(mcpName);
        AgentTransportType owner = previous == null
            ? transportRouter.selectPublicationTransport() : previous.ownerTransport;
        PublicationState desired = new PublicationState(mcpName, address, port, null, owner, true,
            true);
        desired.rollback = previous;
        publications.put(mcpName, desired);
        try {
            transportRouter.deregisterMcpServerEndpoint(mcpName, address, port, owner);
            publications.remove(mcpName);
            notifyCoordinator(null);
        } catch (NacosException e) {
            if (owner != AgentTransportType.HTTP || !isRetryable(e)) {
                if (previous == null) {
                    publications.remove(mcpName);
                } else {
                    publications.put(mcpName, previous);
                }
            }
            notifyCoordinator(null);
            throw e;
        }
    }
    
    private void handleFailure(String mcpName, PublicationState previous,
        PublicationState desired, NacosException exception) {
        if (desired.ownerTransport == AgentTransportType.HTTP && isRetryable(exception)) {
            desired.dirty = true;
            desired.rollback = previous;
        } else if (previous == null) {
            publications.remove(mcpName);
        } else {
            publications.put(mcpName, previous);
        }
        notifyCoordinator(null);
    }
    
    private boolean isRetryable(NacosException exception) {
        return exception.getErrCode() >= NacosException.SERVER_ERROR
            || exception.getErrCode() == NacosException.HTTP_CLIENT_ERROR_CODE;
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
    public synchronized boolean hasRegisteredHttpPublication() {
        for (PublicationState state : publications.values()) {
            if (state.ownerTransport == AgentTransportType.HTTP && !state.deregister
                && !state.dirty) {
                return true;
            }
        }
        return false;
    }
    
    @Override
    public ClientLivenessInfo heartbeat() throws NacosException {
        return transportRouter.heartbeatMcpServerEndpoints();
    }
    
    @Override
    public synchronized void markHttpPublicationsDirty() {
        for (PublicationState state : publications.values()) {
            if (state.ownerTransport == AgentTransportType.HTTP) {
                state.dirty = true;
            }
        }
    }
    
    @Override
    public synchronized void redoDirtyHttpPublications() {
        ClientLivenessInfo liveness = null;
        for (Map.Entry<String, PublicationState> entry : new ArrayList<Map.Entry<String, PublicationState>>(
            publications.entrySet())) {
            PublicationState state = entry.getValue();
            if (!state.dirty || state.ownerTransport != AgentTransportType.HTTP) {
                continue;
            }
            try {
                if (state.deregister) {
                    transportRouter.deregisterMcpServerEndpoint(state.mcpName, state.address,
                        state.port, state.ownerTransport);
                    publications.remove(entry.getKey());
                } else {
                    liveness = transportRouter.registerMcpServerEndpoint(
                        state.mcpName, state.address, state.port, state.version,
                        state.ownerTransport);
                    state.dirty = false;
                    state.rollback = null;
                }
            } catch (NacosException e) {
                if (!isRetryable(e)) {
                    if (state.rollback == null) {
                        publications.remove(entry.getKey());
                    } else {
                        publications.put(entry.getKey(), state.rollback);
                    }
                }
                LOGGER.warn("Redo MCP Endpoint HTTP publication failed.", e);
            }
        }
        notifyCoordinator(liveness);
    }
    
    @Override
    public String getPublicationModuleName() {
        return "MCP";
    }
    
    @Override
    public synchronized void shutdown() {
        if (closed) {
            return;
        }
        closed = true;
        for (PublicationState state : new ArrayList<PublicationState>(publications.values())) {
            try {
                transportRouter.deregisterMcpServerEndpoint(state.mcpName, state.address,
                    state.port, state.ownerTransport);
            } catch (NacosException e) {
                LOGGER.warn("Best-effort MCP Endpoint deregistration during shutdown failed.", e);
            }
        }
        publications.clear();
        notifyCoordinator(null);
    }
    
    private void notifyCoordinator(ClientLivenessInfo liveness) {
        coordinator.stateChanged(this, liveness, hasHttpPublication());
    }
    
    private static final class PublicationState {
        
        private final String mcpName;
        
        private final String address;
        
        private final int port;
        
        private final String version;
        
        private final AgentTransportType ownerTransport;
        
        private final boolean deregister;
        
        private boolean dirty;
        
        private PublicationState rollback;
        
        private PublicationState(String mcpName, String address, int port, String version,
            AgentTransportType ownerTransport, boolean deregister, boolean dirty) {
            this.mcpName = mcpName;
            this.address = address;
            this.port = port;
            this.version = version;
            this.ownerTransport = ownerTransport;
            this.deregister = deregister;
            this.dirty = dirty;
        }
    }
}
