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

package com.alibaba.nacos.client.ai.remote;

import com.alibaba.nacos.api.ai.AgentTransportMode;
import com.alibaba.nacos.api.ai.model.agent.AgentPublishRequest;
import com.alibaba.nacos.api.ai.model.agent.AgentVersionDetail;
import com.alibaba.nacos.api.ai.model.agent.ClientLivenessInfo;
import com.alibaba.nacos.api.ai.model.rad.AgentCatalogEntry;
import com.alibaba.nacos.api.ai.model.rad.AgentDiscoveryRequest;
import com.alibaba.nacos.api.ai.model.rad.AgentDiscoveryResult;
import com.alibaba.nacos.api.ai.model.rad.AgentEndpointRegistrationBatch;
import com.alibaba.nacos.api.ai.model.rad.AgentSearchRequest;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.Page;
import io.grpc.Status;
import io.grpc.StatusException;
import io.grpc.StatusRuntimeException;

/**
 * Routes protocol-neutral Agent operations without owning feature cache state.
 *
 * @author Nacos
 */
public class AgentTransportRouter implements AgentClientProxy {
    
    private final AgentGrpcTransport grpcTransport;
    
    private final AgentHttpTransport httpTransport;
    
    public AgentTransportRouter(AgentGrpcTransport grpcTransport,
        AgentHttpTransport httpTransport) {
        this.grpcTransport = grpcTransport;
        this.httpTransport = httpTransport;
    }
    
    @Override
    public AgentVersionDetail publishAgent(AgentPublishRequest request) throws NacosException {
        AgentTransport transport = select();
        AgentVersionDetail result = transport.publishAgent(request);
        recordHttpSuccess(transport);
        return result;
    }
    
    @Override
    public Page<AgentCatalogEntry> searchAgents(AgentSearchRequest request)
        throws NacosException {
        AgentTransport transport = select();
        Page<AgentCatalogEntry> result;
        try {
            result = transport.searchAgents(request);
        } catch (NacosException e) {
            if (transport.getType() != AgentTransportType.GRPC || !canFallbackRead(e)) {
                throw e;
            }
            transport = httpTransport;
            result = transport.searchAgents(request);
        }
        recordHttpSuccess(transport);
        return result;
    }
    
    @Override
    public AgentDiscoveryResult discoverAgent(AgentDiscoveryRequest request)
        throws NacosException {
        AgentTransport transport = select();
        AgentDiscoveryResult result;
        try {
            result = transport.discoverAgent(request);
        } catch (NacosException e) {
            if (transport.getType() != AgentTransportType.GRPC || !canFallbackRead(e)) {
                throw e;
            }
            transport = httpTransport;
            result = transport.discoverAgent(request);
        }
        recordHttpSuccess(transport);
        return result;
    }
    
    /**
     * Select the sticky owner for a newly observed Endpoint Publication identity.
     *
     * @return concrete transport owner
     */
    public AgentTransportType selectPublicationTransport() {
        return select().getType();
    }
    
    @Override
    public ClientLivenessInfo registerAgentEndpoints(AgentEndpointRegistrationBatch batch)
        throws NacosException {
        return registerAgentEndpoints(batch, selectPublicationTransport());
    }
    
    /**
     * Register an Endpoint Publication through its previously selected owner transport.
     *
     * @param batch complete publication batch
     * @param ownerTransport sticky owner transport
     * @return HTTP liveness information, or {@code null} for gRPC
     * @throws NacosException when registration fails
     */
    public ClientLivenessInfo registerAgentEndpoints(AgentEndpointRegistrationBatch batch,
        AgentTransportType ownerTransport) throws NacosException {
        AgentTransport transport = getTransport(ownerTransport);
        ClientLivenessInfo result = transport.registerAgentEndpoints(batch);
        recordHttpSuccess(transport);
        return result;
    }
    
    @Override
    public void deregisterAgentEndpoints(String namespaceId, String agentName, String protocol)
        throws NacosException {
        deregisterAgentEndpoints(namespaceId, agentName, protocol,
            selectPublicationTransport());
    }
    
    /**
     * Deregister an Endpoint Publication through its sticky owner transport.
     *
     * @param namespaceId effective namespace
     * @param agentName Agent name
     * @param protocol protocol token
     * @param ownerTransport sticky owner transport
     * @throws NacosException when deregistration fails
     */
    public void deregisterAgentEndpoints(String namespaceId, String agentName, String protocol,
        AgentTransportType ownerTransport) throws NacosException {
        AgentTransport transport = getTransport(ownerTransport);
        transport.deregisterAgentEndpoints(namespaceId, agentName, protocol);
        recordHttpSuccess(transport);
    }
    
    @Override
    public ClientLivenessInfo heartbeatAgentEndpoints() throws NacosException {
        return heartbeatAgentEndpoints(AgentTransportType.HTTP);
    }
    
    /**
     * Heartbeat Endpoint Publications through their sticky owner transport.
     *
     * @param ownerTransport sticky owner transport
     * @return HTTP liveness information, or {@code null} for gRPC
     * @throws NacosException when heartbeat fails
     */
    public ClientLivenessInfo heartbeatAgentEndpoints(AgentTransportType ownerTransport)
        throws NacosException {
        AgentTransport transport = getTransport(ownerTransport);
        ClientLivenessInfo result = transport.heartbeatAgentEndpoints();
        recordHttpSuccess(transport);
        return result;
    }
    
    private AgentTransport select() {
        AgentTransportMode mode = grpcTransport.getMode();
        if (mode == AgentTransportMode.HTTP) {
            return httpTransport;
        }
        if (mode == AgentTransportMode.GRPC || grpcTransport.isAvailable()) {
            return grpcTransport;
        }
        return httpTransport;
    }
    
    private AgentTransport getTransport(AgentTransportType type) {
        return type == AgentTransportType.GRPC ? grpcTransport : httpTransport;
    }
    
    private void recordHttpSuccess(AgentTransport transport) {
        if (transport.getType() == AgentTransportType.HTTP) {
            grpcTransport.recordHttpSuccess();
        }
    }
    
    private boolean canFallbackRead(NacosException exception) {
        if (grpcTransport.getMode() != AgentTransportMode.AUTO) {
            return false;
        }
        int code = exception.getErrCode();
        return !grpcTransport.isConnected()
            || code == NacosException.CLIENT_DISCONNECT || code == NacosException.UN_REGISTER
            || isGrpcUnavailable(exception);
    }
    
    private boolean isGrpcUnavailable(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof StatusRuntimeException) {
                return ((StatusRuntimeException) current).getStatus()
                    .getCode() == Status.Code.UNAVAILABLE;
            }
            if (current instanceof StatusException) {
                return ((StatusException) current).getStatus().getCode() == Status.Code.UNAVAILABLE;
            }
            current = current.getCause();
        }
        return false;
    }
}
