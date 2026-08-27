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

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Connection, client id, and Projection indexes for active gRPC Agent Watches.
 *
 * @author Nacos
 */
final class AgentGrpcWatchRegistry {
    
    private final Map<String, AgentGrpcWatch> watches =
        new HashMap<String, AgentGrpcWatch>();
    
    private final Map<String, Map<String, String>> clientWatchIndex =
        new HashMap<String, Map<String, String>>();
    
    private final Map<String, Set<String>> connectionIndex =
        new HashMap<String, Set<String>>();
    
    private final Map<AgentProjectionKey, Set<String>> projectionIndex =
        new HashMap<AgentProjectionKey, Set<String>>();
    
    synchronized Registration register(String connectionId, String clientWatchId,
        AgentProjectionKey projectionKey, AgentWatchOwnerContext owner, int maxPerConnection)
        throws NacosApiException {
        Map<String, String> clientWatches = clientWatchIndex.get(connectionId);
        String existingKey = clientWatches == null ? null : clientWatches.get(clientWatchId);
        if (existingKey != null) {
            AgentGrpcWatch existing = watches.get(existingKey);
            if (existing != null && existing.getProjectionKey().equals(projectionKey)) {
                return new Registration(existing, false);
            }
            throw new NacosApiException(NacosException.CONFLICT, ErrorCode.RESOURCE_CONFLICT,
                "clientWatchId is already bound to another Agent discovery intent.");
        }
        int current = connectionIndex.containsKey(connectionId)
            ? connectionIndex.get(connectionId).size() : 0;
        if (current >= maxPerConnection) {
            throw new NacosApiException(NacosException.OVER_THRESHOLD,
                ErrorCode.AGENT_DISCOVERY_SUBSCRIPTION_OVER_LIMIT,
                "Agent discovery Watch limit of " + maxPerConnection
                    + " reached for this connection.");
        }
        String watchKey = UUID.randomUUID().toString();
        AgentGrpcWatch watch = new AgentGrpcWatch(watchKey, connectionId, clientWatchId,
            projectionKey, owner);
        watches.put(watchKey, watch);
        if (clientWatches == null) {
            clientWatches = new LinkedHashMap<String, String>();
            clientWatchIndex.put(connectionId, clientWatches);
        }
        clientWatches.put(clientWatchId, watchKey);
        addIndex(connectionIndex, connectionId, watchKey);
        addIndex(projectionIndex, projectionKey, watchKey);
        return new Registration(watch, true);
    }
    
    synchronized AgentGrpcWatch findByClientWatchId(String connectionId,
        String clientWatchId) {
        Map<String, String> connectionWatches = clientWatchIndex.get(connectionId);
        return connectionWatches == null ? null
            : watches.get(connectionWatches.get(clientWatchId));
    }
    
    synchronized AgentGrpcWatch findOwned(String connectionId, String watchKey) {
        AgentGrpcWatch watch = watches.get(watchKey);
        return watch != null && watch.getConnectionId().equals(connectionId) ? watch : null;
    }
    
    synchronized AgentGrpcWatch findByWatchKey(String watchKey) {
        return watches.get(watchKey);
    }
    
    synchronized List<AgentGrpcWatch> findByProjection(AgentProjectionKey key) {
        Set<String> keys = projectionIndex.get(key);
        if (keys == null || keys.isEmpty()) {
            return Collections.emptyList();
        }
        List<AgentGrpcWatch> result = new ArrayList<AgentGrpcWatch>(keys.size());
        for (String watchKey : keys) {
            AgentGrpcWatch watch = watches.get(watchKey);
            if (watch != null) {
                result.add(watch);
            }
        }
        return result;
    }
    
    synchronized AgentGrpcWatch removeOwned(String connectionId, String watchKey) {
        AgentGrpcWatch watch = findOwned(connectionId, watchKey);
        return watch == null ? null : remove(watchKey);
    }
    
    synchronized AgentGrpcWatch remove(String watchKey) {
        AgentGrpcWatch watch = watches.remove(watchKey);
        if (watch == null) {
            return null;
        }
        watch.close();
        removeClientWatchIndex(watch);
        removeIndex(connectionIndex, watch.getConnectionId(), watchKey);
        removeIndex(projectionIndex, watch.getProjectionKey(), watchKey);
        return watch;
    }
    
    synchronized List<AgentGrpcWatch> removeConnection(String connectionId) {
        Set<String> keys = connectionIndex.get(connectionId);
        if (keys == null || keys.isEmpty()) {
            return Collections.emptyList();
        }
        List<AgentGrpcWatch> result = new ArrayList<AgentGrpcWatch>(keys.size());
        for (String watchKey : new ArrayList<String>(keys)) {
            AgentGrpcWatch removed = remove(watchKey);
            if (removed != null) {
                result.add(removed);
            }
        }
        return result;
    }
    
    synchronized List<AgentGrpcWatch> clear() {
        List<AgentGrpcWatch> result = new ArrayList<AgentGrpcWatch>(watches.values());
        for (AgentGrpcWatch watch : result) {
            watch.close();
        }
        watches.clear();
        clientWatchIndex.clear();
        connectionIndex.clear();
        projectionIndex.clear();
        return result;
    }
    
    synchronized int size() {
        return watches.size();
    }
    
    synchronized int connectionSize(String connectionId) {
        Set<String> keys = connectionIndex.get(connectionId);
        return keys == null ? 0 : keys.size();
    }
    
    private void removeClientWatchIndex(AgentGrpcWatch watch) {
        Map<String, String> clientWatches = clientWatchIndex.get(watch.getConnectionId());
        clientWatches.remove(watch.getClientWatchId());
        if (clientWatches.isEmpty()) {
            clientWatchIndex.remove(watch.getConnectionId());
        }
    }
    
    private <K> void addIndex(Map<K, Set<String>> index, K key, String watchKey) {
        Set<String> keys = index.get(key);
        if (keys == null) {
            keys = new LinkedHashSet<String>();
            index.put(key, keys);
        }
        keys.add(watchKey);
    }
    
    private <K> void removeIndex(Map<K, Set<String>> index, K key, String watchKey) {
        Set<String> keys = index.get(key);
        keys.remove(watchKey);
        if (keys.isEmpty()) {
            index.remove(key);
        }
    }
    
    static final class Registration {
        
        private final AgentGrpcWatch watch;
        
        private final boolean created;
        
        Registration(AgentGrpcWatch watch, boolean created) {
            this.watch = watch;
            this.created = created;
        }
        
        AgentGrpcWatch getWatch() {
            return watch;
        }
        
        boolean isCreated() {
            return created;
        }
    }
}
