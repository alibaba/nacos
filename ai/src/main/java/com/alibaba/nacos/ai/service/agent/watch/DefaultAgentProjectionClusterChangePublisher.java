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

import com.alibaba.nacos.api.ai.remote.request.cluster.AgentProjectionChangeClusterRequest;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.common.executor.ExecutorFactory;
import com.alibaba.nacos.common.executor.NameThreadFactory;
import com.alibaba.nacos.common.utils.LogRateLimiter;
import com.alibaba.nacos.core.cluster.Member;
import com.alibaba.nacos.core.cluster.ServerMemberManager;
import com.alibaba.nacos.core.cluster.remote.ClusterRpcClientProxy;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Coalescing asynchronous publisher for Agent Projection cluster hints.
 *
 * <p>The publisher keeps at most one pending entry per logical Agent. Failures are observable but
 * never roll back the durable Agent operation; active Projection reconciliation remains the repair
 * path for a lost hint.</p>
 *
 * @author Nacos
 */
@Component
public class DefaultAgentProjectionClusterChangePublisher
    implements AgentProjectionClusterChangePublisher {
    
    private static final Logger LOGGER =
        LoggerFactory.getLogger(DefaultAgentProjectionClusterChangePublisher.class);
    
    private static final LogRateLimiter WARN_LOG_LIMITER = new LogRateLimiter(60000L);
    
    private final ServerMemberManager memberManager;
    
    private final ClusterRpcClientProxy clusterRpcClientProxy;
    
    private final ExecutorService executor;
    
    private final Set<AgentLogicalKey> pending = ConcurrentHashMap.newKeySet();
    
    private final AtomicBoolean drainScheduled = new AtomicBoolean();
    
    private final AtomicBoolean closed = new AtomicBoolean();
    
    @Autowired
    public DefaultAgentProjectionClusterChangePublisher(ServerMemberManager memberManager,
        ClusterRpcClientProxy clusterRpcClientProxy) {
        this(memberManager, clusterRpcClientProxy,
            ExecutorFactory.newSingleExecutorService(
                new NameThreadFactory("AgentProjectionClusterHint")));
    }
    
    DefaultAgentProjectionClusterChangePublisher(ServerMemberManager memberManager,
        ClusterRpcClientProxy clusterRpcClientProxy, ExecutorService executor) {
        this.memberManager = memberManager;
        this.clusterRpcClientProxy = clusterRpcClientProxy;
        this.executor = executor;
    }
    
    @Override
    public void publish(String namespaceId, String agentName) {
        if (closed.get()) {
            return;
        }
        pending.add(new AgentLogicalKey(namespaceId, agentName));
        scheduleDrain();
    }
    
    /**
     * Stop cluster hint delivery and discard changes that reconciliation can repair later.
     */
    @PreDestroy
    public void shutdown() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        pending.clear();
        executor.shutdownNow();
    }
    
    /**
     * Return the number of coalesced logical changes waiting for delivery.
     *
     * @return pending logical Agent count
     */
    int pendingCount() {
        return pending.size();
    }
    
    private void scheduleDrain() {
        if (!drainScheduled.compareAndSet(false, true)) {
            return;
        }
        try {
            executor.execute(this::drain);
        } catch (RejectedExecutionException e) {
            drainScheduled.set(false);
            recordFailure(e);
        }
    }
    
    private void drain() {
        try {
            while (!closed.get()) {
                List<AgentLogicalKey> batch = new ArrayList<AgentLogicalKey>(pending);
                if (batch.isEmpty()) {
                    return;
                }
                pending.removeAll(batch);
                for (AgentLogicalKey key : batch) {
                    publishToPeers(key);
                }
            }
        } finally {
            completeDrain();
        }
    }
    
    /**
     * Hand off changes that arrived while the current drain observed an empty queue.
     */
    void completeDrain() {
        drainScheduled.set(false);
        if (!closed.get() && !pending.isEmpty()) {
            scheduleDrain();
        }
    }
    
    private void publishToPeers(AgentLogicalKey key) {
        AgentProjectionChangeClusterRequest request =
            new AgentProjectionChangeClusterRequest();
        request.setNamespaceId(key.getNamespaceId());
        request.setAgentName(key.getAgentName());
        for (Member member : memberManager.allMembersWithoutSelf()) {
            try {
                clusterRpcClientProxy.sendRequest(member, request);
                AgentWatchMetrics.record(AgentWatchMetrics.Event.CLUSTER_HINT,
                    AgentWatchMetrics.Result.SUCCESS);
            } catch (NacosException | RuntimeException e) {
                recordFailure(e);
            }
        }
    }
    
    private void recordFailure(Throwable failure) {
        AgentWatchMetrics.record(AgentWatchMetrics.Event.CLUSTER_HINT,
            AgentWatchMetrics.Result.FAILED);
        if (WARN_LOG_LIMITER.tryAcquire()) {
            LOGGER.warn("Agent Projection cluster hint delivery failed: {}",
                failure.getClass().getSimpleName());
        }
    }
}
