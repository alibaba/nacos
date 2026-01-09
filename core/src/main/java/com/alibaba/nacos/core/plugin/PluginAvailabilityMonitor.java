/*
 * Copyright 1999-2024 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.core.plugin;

import com.alibaba.nacos.api.common.NodeState;
import com.alibaba.nacos.core.cluster.Member;
import com.alibaba.nacos.core.cluster.ServerMemberManager;
import com.alibaba.nacos.core.cluster.remote.ClusterRpcClientProxy;
import com.alibaba.nacos.core.cluster.remote.request.PluginAvailabilityRequest;
import com.alibaba.nacos.core.cluster.remote.response.PluginAvailabilityResponse;
import com.alibaba.nacos.core.utils.GlobalExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Plugin availability monitor.
 * Periodically checks plugin availability across cluster nodes.
 *
 * @author WangzJi
 * @since 3.2.0
 */
@Component
public class PluginAvailabilityMonitor {

    private static final Logger LOGGER = LoggerFactory.getLogger(PluginAvailabilityMonitor.class);

    /**
     * Check interval in milliseconds (30 seconds).
     */
    private static final long CHECK_INTERVAL_MS = 30_000L;

    /**
     * Initial delay in milliseconds (10 seconds).
     */
    private static final long INITIAL_DELAY_MS = 10_000L;

    private final PluginManager pluginManager;

    private final ServerMemberManager memberManager;

    private final ClusterRpcClientProxy rpcClientProxy;

    private volatile boolean shutdown = false;

    @Autowired
    public PluginAvailabilityMonitor(PluginManager pluginManager,
                                     ServerMemberManager memberManager,
                                     ClusterRpcClientProxy rpcClientProxy) {
        this.pluginManager = pluginManager;
        this.memberManager = memberManager;
        this.rpcClientProxy = rpcClientProxy;
    }

    /**
     * Initialize and start the availability monitor.
     */
    @PostConstruct
    public void init() {
        // Start periodic monitoring
        GlobalExecutor.scheduleByCommon(new AvailabilityCheckTask(), INITIAL_DELAY_MS);
        LOGGER.info("[PluginAvailabilityMonitor] Started with interval {}ms", CHECK_INTERVAL_MS);
    }

    /**
     * Shutdown the availability monitor.
     */
    @PreDestroy
    public void shutdown() {
        shutdown = true;
        LOGGER.info("[PluginAvailabilityMonitor] Shutdown");
    }

    /**
     * Availability check task.
     * Periodically checks plugin availability across all cluster nodes.
     */
    private class AvailabilityCheckTask implements Runnable {

        @Override
        public void run() {
            if (shutdown) {
                return;
            }

            try {
                checkClusterAvailability();
            } catch (Exception e) {
                LOGGER.error("[PluginAvailabilityMonitor] Availability check failed", e);
            } finally {
                if (!shutdown) {
                    // Reschedule
                    GlobalExecutor.scheduleByCommon(this, CHECK_INTERVAL_MS);
                }
            }
        }

        private void checkClusterAvailability() {
            // Get all plugin IDs
            Set<String> pluginIds = pluginManager.getLocalPluginIds();
            if (pluginIds.isEmpty()) {
                return;
            }

            // Get all cluster members (excluding self)
            Collection<Member> members = memberManager.allMembersWithoutSelf();

            // Query each plugin availability
            // Local availability
            // Remote availability
            // Update availability map
            pluginIds.forEach(pluginId -> {
                int initialCapacity = 1 + members.size();
                Map<String, Boolean> nodeAvailability = new ConcurrentHashMap<>(initialCapacity);
                String localAddress = memberManager.getSelf().getAddress();
                nodeAvailability.put(localAddress, pluginManager.isPluginAvailable(pluginId));
                members.forEach(member -> {
                    
                    if (!NodeState.UP.equals(member.getState())) {
                        nodeAvailability.put(member.getAddress(), false);
                        return;
                    }
                    
                    try {
                        PluginAvailabilityRequest request = new PluginAvailabilityRequest();
                        request.setPluginId(pluginId);
                        
                        PluginAvailabilityResponse response = (PluginAvailabilityResponse) rpcClientProxy.sendRequest(
                                member, request);
                        
                        nodeAvailability.put(member.getAddress(), response.isAvailable());
                    } catch (Exception e) {
                        LOGGER.warn("[PluginAvailabilityMonitor] Failed to check plugin {} availability on {}: {}",
                                pluginId, member.getAddress(), e.getMessage());
                        nodeAvailability.put(member.getAddress(), false);
                    }
                    
                });
                pluginManager.updateNodeAvailability(pluginId, nodeAvailability);
            });
        }
    }
}
