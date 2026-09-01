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

package com.alibaba.nacos.client.ai.cache;

import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.client.ai.remote.AgentClientProxy;
import com.alibaba.nacos.client.ai.watch.AgentWatchManager;
import com.alibaba.nacos.client.ai.watch.AgentWatchTransport;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Compatibility name for the transport-neutral {@link AgentWatchManager}.
 *
 * <p>The earlier polling holder API remains source compatible while all identity, cache,
 * Listener, pending, and dirty-refresh state now belongs to the Watch manager.</p>
 *
 * @author Nacos
 */
public class NacosAgentDiscoveryCacheHolder extends AgentWatchManager {
    
    public NacosAgentDiscoveryCacheHolder(String namespaceId, AgentClientProxy clientProxy) {
        super(namespaceId, clientProxy);
    }
    
    public NacosAgentDiscoveryCacheHolder(String namespaceId, AgentClientProxy clientProxy,
        int maxSubscriptions) {
        super(namespaceId, clientProxy, maxSubscriptions);
    }
    
    /**
     * Create a compatibility holder with an explicit Watch transport.
     *
     * @param namespaceId SDK namespace
     * @param clientProxy authoritative Discover proxy
     * @param maxSubscriptions local Listener-record watermark
     * @param watchTransport Wire Watch transport router
     */
    public NacosAgentDiscoveryCacheHolder(String namespaceId, AgentClientProxy clientProxy,
        int maxSubscriptions, AgentWatchTransport watchTransport) {
        super(namespaceId, clientProxy, maxSubscriptions, watchTransport);
    }
    
    NacosAgentDiscoveryCacheHolder(String namespaceId, AgentClientProxy clientProxy,
        long updateIntervalMillis, ScheduledExecutorService pollingExecutor,
        ExecutorService callbackExecutor) {
        this(namespaceId, clientProxy, updateIntervalMillis, pollingExecutor, callbackExecutor,
            AiConstants.DEFAULT_AI_AGENT_DISCOVERY_MAX_SUBSCRIPTIONS);
    }
    
    NacosAgentDiscoveryCacheHolder(String namespaceId, AgentClientProxy clientProxy,
        long updateIntervalMillis, ScheduledExecutorService pollingExecutor,
        ExecutorService callbackExecutor, int maxSubscriptions) {
        super(namespaceId, clientProxy, updateIntervalMillis, pollingExecutor, callbackExecutor,
            maxSubscriptions);
    }
}
