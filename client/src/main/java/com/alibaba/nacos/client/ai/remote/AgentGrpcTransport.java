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

/**
 * gRPC implementation of the protocol-neutral Agent transport.
 *
 * <p>The transport owns shared gRPC connection startup and the Agent AUTO probe state while
 * delegating wire requests to {@link AiGrpcClient}.</p>
 *
 * @author Nacos
 */
public class AgentGrpcTransport implements AgentTransport {
    
    private static final Logger LOGGER = LogUtils.logger(AgentGrpcTransport.class);
    
    private final AgentTransportMode mode;
    
    private final AiGrpcClient clientProxy;
    
    private final NacosMcpServerCacheHolder mcpServerCacheHolder;
    
    private final NacosAgentCardCacheHolder agentCardCacheHolder;
    
    private final int autoFailureThreshold;
    
    private boolean started;
    
    private boolean requiredByNonAgentFeature;
    
    private boolean agentHttpSucceeded;
    
    private boolean autoHttpStable;
    
    public AgentGrpcTransport(AgentTransportMode mode, AiGrpcClient clientProxy,
        NacosMcpServerCacheHolder mcpServerCacheHolder,
        NacosAgentCardCacheHolder agentCardCacheHolder) {
        this.mode = mode;
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
        if (mode != AgentTransportMode.HTTP) {
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
    
    private void startIfNecessary() throws NacosException {
        if (started) {
            return;
        }
        clientProxy.start(mcpServerCacheHolder, agentCardCacheHolder);
        started = true;
    }
    
    /**
     * Check whether Agent AUTO may use the complete RAD v1 gRPC contract.
     *
     * @return {@code true} when gRPC is selected or connected with RAD v1 support
     */
    public synchronized boolean isAvailable() {
        if (mode == AgentTransportMode.GRPC) {
            return true;
        }
        return mode == AgentTransportMode.AUTO && !autoHttpStable && clientProxy.isEnable()
            && clientProxy.isAbilitySupportedByServer(AbilityKey.SERVER_RAD_V1);
    }
    
    /**
     * Record one successful Agent HTTP operation and settle AUTO when its probe is exhausted.
     */
    public synchronized void recordHttpSuccess() {
        if (mode != AgentTransportMode.AUTO || autoHttpStable) {
            return;
        }
        agentHttpSucceeded = true;
        settleAutoIfRequired(clientProxy.getInitialConnectionFailureCount());
    }
    
    private synchronized void onInitialConnectionFailure(int failureCount) {
        settleAutoIfRequired(failureCount);
    }
    
    private void settleAutoIfRequired(int failureCount) {
        if (mode != AgentTransportMode.AUTO || autoHttpStable || requiredByNonAgentFeature
            || !agentHttpSucceeded || failureCount < autoFailureThreshold) {
            return;
        }
        if (clientProxy.suspendInitialReconnect()) {
            autoHttpStable = true;
            LOGGER.info(
                "Agent AUTO transport settled on HTTP after {} initial gRPC reconnect failures.",
                failureCount);
        }
    }
    
    /**
     * Return the configured Agent transport mode.
     *
     * @return configured mode
     */
    public AgentTransportMode getMode() {
        return mode;
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
