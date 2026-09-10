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

import com.alibaba.nacos.api.ability.constant.AbilityKey;
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
import com.alibaba.nacos.client.ai.cache.NacosAgentCardCacheHolder;
import com.alibaba.nacos.client.ai.cache.NacosMcpServerCacheHolder;
import com.alibaba.nacos.client.utils.LogUtils;
import com.alibaba.nacos.common.remote.client.InitialConnectionFailureListener;
import org.slf4j.Logger;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * gRPC implementation of the protocol-neutral Agent transport.
 *
 * <p>The transport owns shared gRPC connection startup and independent resource AUTO probe state while
 * delegating wire requests to {@link AiGrpcClient}.</p>
 *
 * @author Nacos
 */
public class AgentGrpcTransport implements AgentTransport {
    
    private static final Logger LOGGER = LogUtils.logger(AgentGrpcTransport.class);
    
    /** AI resources sharing this connection and supporting both transports. */
    public enum Resource {
        AGENT, MCP, PROMPT
    }
    
    private final Map<Resource, AgentTransportMode> modes = new EnumMap<>(Resource.class);
    
    private final Set<Resource> usedAutoResources = EnumSet.noneOf(Resource.class);
    
    private final Set<Resource> httpSucceededResources = EnumSet.noneOf(Resource.class);
    
    private final Set<Resource> httpStableResources = EnumSet.noneOf(Resource.class);
    
    private final AiGrpcClient clientProxy;
    
    private final NacosMcpServerCacheHolder mcpServerCacheHolder;
    
    private final NacosAgentCardCacheHolder agentCardCacheHolder;
    
    private final int autoFailureThreshold;
    
    private boolean started;
    
    private boolean requiredByNonAgentFeature;
    
    private int probeFailureBaseline;
    
    private boolean autoHttpStable;
    
    public AgentGrpcTransport(AgentTransportMode agentMode, AgentTransportMode mcpMode,
        AgentTransportMode promptMode, AiGrpcClient clientProxy,
        NacosMcpServerCacheHolder mcpServerCacheHolder,
        NacosAgentCardCacheHolder agentCardCacheHolder) {
        modes.put(Resource.AGENT, agentMode);
        modes.put(Resource.MCP, mcpMode);
        modes.put(Resource.PROMPT, promptMode);
        this.clientProxy = clientProxy;
        this.mcpServerCacheHolder = mcpServerCacheHolder;
        this.agentCardCacheHolder = agentCardCacheHolder;
        this.autoFailureThreshold = Math.max(1, clientProxy.getRetryTimes());
        clientProxy.registerInitialConnectionFailureListener(
            new InitialConnectionFailureListener() {
                
                @Override
                public void onFailure(int failureCount) {
                    onInitialConnectionFailure(failureCount);
                }
            });
    }
    
    /**
     * Start gRPC synchronously for GRPC and AUTO; explicit HTTP remains lazy.
     *
     * @throws NacosException when transport initialization fails
     */
    public synchronized void startConfiguredTransport() throws NacosException {
        if (modes.containsValue(AgentTransportMode.GRPC)
            || modes.containsValue(AgentTransportMode.AUTO)) {
            startIfNecessary();
        }
    }
    
    /**
     * Acquire gRPC for an existing AI feature that has no Agent HTTP routing contract.
     *
     * @return initialized shared gRPC client
     * @throws NacosException when transport initialization fails
     */
    public synchronized AiGrpcClient requireGrpcClient() throws NacosException {
        requiredByNonAgentFeature = true;
        startIfNecessary();
        clientProxy.resumeInitialReconnect();
        return clientProxy;
    }
    
    /**
     * Acquire gRPC for a protocol-neutral Agent or MCP operation without pinning AUTO reconnect.
     *
     * @return initialized shared gRPC client
     * @throws NacosException when transport initialization fails
     */
    public synchronized AiGrpcClient acquireProtocolNeutralClient() throws NacosException {
        startIfNecessary();
        return clientProxy;
    }
    
    private void startIfNecessary() throws NacosException {
        if (started) {
            return;
        }
        clientProxy.start(mcpServerCacheHolder, agentCardCacheHolder);
        started = true;
    }
    
    /**
     * Check the selected resource's connection and negotiated capability for AUTO.
     *
     * @param resource resource selecting a transport
     * @return whether gRPC is available for this resource
     */
    public synchronized boolean isAvailable(Resource resource) {
        AgentTransportMode mode = modes.get(resource);
        if (mode == AgentTransportMode.GRPC) {
            return true;
        }
        if (mode != AgentTransportMode.AUTO) {
            return false;
        }
        useAutoResource(resource);
        if (httpStableResources.contains(resource) || !clientProxy.isEnable()) {
            return false;
        }
        switch (resource) {
            case AGENT:
                return clientProxy.isAbilitySupportedByServer(AbilityKey.SERVER_RAD_V1);
            case MCP:
                return clientProxy.isAbilitySupportedByServer(AbilityKey.SERVER_MCP_REGISTRY);
            default:
                // Prompt has no dedicated server ability bit.
                return true;
        }
    }
    
    private void useAutoResource(Resource resource) {
        if (usedAutoResources.add(resource) && autoHttpStable) {
            // A previously unused resource gets its own full initial probe budget.
            probeFailureBaseline = clientProxy.getInitialConnectionFailureCount();
            autoHttpStable = false;
            clientProxy.resumeInitialReconnect();
        }
    }
    
    /**
     * Record successful HTTP use for one resource without settling other resources.
     *
     * @param resource resource whose HTTP operation succeeded
     */
    public synchronized void recordHttpSuccess(Resource resource) {
        if (modes.get(resource) != AgentTransportMode.AUTO) {
            return;
        }
        useAutoResource(resource);
        httpSucceededResources.add(resource);
        settleAutoIfRequired(clientProxy.getInitialConnectionFailureCount());
    }
    
    private synchronized void onInitialConnectionFailure(int failureCount) {
        settleAutoIfRequired(failureCount);
    }
    
    private void settleAutoIfRequired(int failureCount) {
        if (autoHttpStable || requiredByNonAgentFeature
            || modes.containsValue(AgentTransportMode.GRPC)
            || usedAutoResources.isEmpty() || !httpSucceededResources.containsAll(usedAutoResources)
            || failureCount - probeFailureBaseline < autoFailureThreshold) {
            return;
        }
        if (clientProxy.suspendInitialReconnect()) {
            autoHttpStable = true;
            httpStableResources.addAll(usedAutoResources);
            LOGGER.info(
                "AI AUTO resources {} settled on HTTP after {} initial gRPC reconnect failures.",
                usedAutoResources, failureCount);
        }
    }
    
    /**
     * Check whether the shared gRPC connection is currently running.
     *
     * @return {@code true} when gRPC is connected
     */
    public boolean isConnected() {
        return clientProxy.isEnable();
    }
    
    /**
     * Return an AI proxy that acquires the shared gRPC transport before every operation.
     *
     * @return lifecycle-aware proxy
     */
    public AiClientProxy requiredProxy() {
        return new RequiredAiGrpcClientProxy(this);
    }
    
    @Override
    public AgentTransportType getType() {
        return AgentTransportType.GRPC;
    }
    
    @Override
    public AgentVersionDetail publishAgent(AgentPublishRequest request) throws NacosException {
        return clientProxy.publishAgent(request);
    }
    
    @Override
    public Page<AgentCatalogEntry> searchAgents(AgentSearchRequest request)
        throws NacosException {
        return clientProxy.searchAgents(request);
    }
    
    @Override
    public AgentDiscoveryResult discoverAgent(AgentDiscoveryRequest request)
        throws NacosException {
        return clientProxy.discoverAgent(request);
    }
    
    @Override
    public ClientLivenessInfo registerAgentEndpoints(AgentEndpointRegistrationBatch batch)
        throws NacosException {
        return clientProxy.registerAgentEndpoints(batch);
    }
    
    @Override
    public void deregisterAgentEndpoints(String namespaceId, String agentName, String protocol)
        throws NacosException {
        clientProxy.deregisterAgentEndpoints(namespaceId, agentName, protocol);
    }
    
    @Override
    public ClientLivenessInfo heartbeatAgentEndpoints() throws NacosException {
        return clientProxy.heartbeatAgentEndpoints();
    }
    
    synchronized boolean isAutoHttpStable() {
        return autoHttpStable;
    }
}
