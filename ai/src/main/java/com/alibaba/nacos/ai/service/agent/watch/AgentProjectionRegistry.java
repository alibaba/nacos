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

import com.alibaba.nacos.naming.core.v2.pojo.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * In-memory shared Projection state and its logical/physical reverse indexes.
 *
 * <p>The registry counts only active Projection users. It never stores Owner admission,
 * credentials, Watch state, or an Agent business snapshot.</p>
 *
 * @author Nacos
 */
public class AgentProjectionRegistry {
    
    private final Map<AgentProjectionKey, Entry> entries =
        new HashMap<AgentProjectionKey, Entry>();
    
    private final Map<AgentLogicalKey, Set<AgentProjectionKey>> logicalIndex =
        new HashMap<AgentLogicalKey, Set<AgentProjectionKey>>();
    
    private final Map<Service, Set<AgentProjectionKey>> physicalIndex =
        new HashMap<Service, Set<AgentProjectionKey>>();
    
    /**
     * Retain one shared Projection.
     *
     * @param key projection key
     * @return {@code true} when a new shared Projection was created
     */
    public synchronized boolean retain(AgentProjectionKey key) {
        Entry entry = entries.get(key);
        if (entry != null) {
            entry.references++;
            return false;
        }
        entries.put(key, new Entry());
        addIndex(logicalIndex, logicalKey(key), key);
        AgentWatchMetrics.setActiveProjections(entries.size());
        return true;
    }
    
    /**
     * Release one Projection reference.
     *
     * @param key projection key
     * @return {@code true} when the final reference and all dependency state were removed
     */
    public synchronized boolean release(AgentProjectionKey key) {
        Entry entry = entries.get(key);
        if (entry == null) {
            return false;
        }
        entry.references--;
        if (entry.references > 0) {
            return false;
        }
        entries.remove(key);
        AgentWatchMetrics.setActiveProjections(entries.size());
        removeIndex(logicalIndex, logicalKey(key), key);
        if (entry.state != null) {
            for (Service dependency : entry.state.getPhysicalDependencies()) {
                removeIndex(physicalIndex, dependency, key);
            }
        }
        return true;
    }
    
    /**
     * Apply a current-fact computation only while its Projection remains active.
     *
     * <p>Successful and not-found results replace physical dependencies. Uncertain/conflict/
     * transient results preserve the last known dependencies so a future Naming event can still
     * repair the Projection.</p>
     *
     * @param key projection key
     * @param computed current computation
     * @return applied update, or empty when the Projection was concurrently released
     */
    public synchronized Optional<AgentProjectionUpdate> apply(AgentProjectionKey key,
        AgentProjectionState computed, Set<AgentProjectionChangeReason> reasons) {
        Entry entry = entries.get(key);
        if (entry == null) {
            return Optional.empty();
        }
        AgentProjectionState previous = entry.state;
        Set<Service> previousDependencies = previous == null ? Collections.<Service>emptySet()
            : previous.getPhysicalDependencies();
        Set<Service> currentDependencies = computed.replacesPhysicalDependencies()
            ? computed.getPhysicalDependencies() : previousDependencies;
        AgentProjectionState current = computed.withPhysicalDependencies(currentDependencies);
        if (!previousDependencies.equals(currentDependencies)) {
            replacePhysicalIndexes(key, previousDependencies, currentDependencies);
        }
        entry.state = current;
        return Optional.of(new AgentProjectionUpdate(key, previous, current, reasons));
    }
    
    public synchronized Optional<AgentProjectionState> getState(AgentProjectionKey key) {
        Entry entry = entries.get(key);
        return entry == null ? Optional.empty() : Optional.ofNullable(entry.state);
    }
    
    public synchronized boolean isActive(AgentProjectionKey key) {
        return entries.containsKey(key);
    }
    
    public synchronized int getReferenceCount(AgentProjectionKey key) {
        Entry entry = entries.get(key);
        return entry == null ? 0 : entry.references;
    }
    
    public synchronized int size() {
        return entries.size();
    }
    
    public synchronized Set<AgentProjectionKey> findByAgent(String namespaceId,
        String agentName) {
        return copyKeys(logicalIndex.get(new AgentLogicalKey(namespaceId, agentName)));
    }
    
    public synchronized Set<AgentProjectionKey> findByService(Service service) {
        return copyKeys(physicalIndex.get(service));
    }
    
    /**
     * Return a stable sorted snapshot of active projection keys.
     *
     * @return active keys
     */
    public synchronized List<AgentProjectionKey> activeKeys() {
        List<AgentProjectionKey> result = new ArrayList<AgentProjectionKey>(entries.keySet());
        Collections.sort(result);
        return result;
    }
    
    private void replacePhysicalIndexes(AgentProjectionKey key, Set<Service> previous,
        Set<Service> current) {
        for (Service dependency : previous) {
            if (!current.contains(dependency)) {
                removeIndex(physicalIndex, dependency, key);
            }
        }
        for (Service dependency : current) {
            if (!previous.contains(dependency)) {
                addIndex(physicalIndex, dependency, key);
            }
        }
    }
    
    private AgentLogicalKey logicalKey(AgentProjectionKey key) {
        return new AgentLogicalKey(key.getNamespaceId(), key.getAgentName());
    }
    
    private <K> void addIndex(Map<K, Set<AgentProjectionKey>> index, K dependency,
        AgentProjectionKey key) {
        Set<AgentProjectionKey> keys = index.get(dependency);
        if (keys == null) {
            keys = new LinkedHashSet<AgentProjectionKey>();
            index.put(dependency, keys);
        }
        keys.add(key);
    }
    
    private <K> void removeIndex(Map<K, Set<AgentProjectionKey>> index, K dependency,
        AgentProjectionKey key) {
        Set<AgentProjectionKey> keys = index.get(dependency);
        if (keys == null) {
            return;
        }
        keys.remove(key);
        if (keys.isEmpty()) {
            index.remove(dependency);
        }
    }
    
    private Set<AgentProjectionKey> copyKeys(Set<AgentProjectionKey> source) {
        return source == null || source.isEmpty() ? Collections.<AgentProjectionKey>emptySet()
            : Collections.unmodifiableSet(new LinkedHashSet<AgentProjectionKey>(source));
    }
    
    private static class Entry {
        
        private int references = 1;
        
        private AgentProjectionState state;
    }
}
