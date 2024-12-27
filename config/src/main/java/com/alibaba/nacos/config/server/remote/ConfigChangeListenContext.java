/*
 * Copyright 1999-2020 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.config.server.remote;

import com.alibaba.nacos.common.utils.CollectionUtils;
import com.alibaba.nacos.common.utils.GroupKeyPattern;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * config change listen context.
 *
 * @author liuzunfei
 * @version $Id: ConfigChangeListenContext.java, v 0.1 2020年07月20日 1:37 PM liuzunfei Exp $
 */
@Component
public class ConfigChangeListenContext {
    
    /**
     * groupKeyPattern -> connection set.
     */
    private final Map<String, Set<String>> keyPatternContext = new ConcurrentHashMap<>();
    
    /**
     * groupKey-> connection set.
     */
    private final ConcurrentHashMap<String, HashSet<String>> groupKeyContext = new ConcurrentHashMap<>();
    
    /**
     * connectionId-> group key set.
     */
    private final ConcurrentHashMap<String, HashMap<String, String>> connectionIdContext = new ConcurrentHashMap<>();
    
    /**
     * Adds a fuzzy listen connection ID associated with the specified group key pattern. If the key pattern does not
     * exist in the context, a new entry will be created. If the key pattern already exists, the connection ID will be
     * added to the existing set.
     *
     * @param groupKeyPattern The group key pattern to associate with the listen connection.
     * @param connectId       The connection ID to be added.
     */
    public synchronized void addFuzzyListen(String groupKeyPattern, String connectId) {
        // Add the connection ID to the set associated with the key pattern in keyPatternContext
        keyPatternContext.computeIfAbsent(groupKeyPattern, k -> new HashSet<>()).add(connectId);
    }
    
    /**
     * Removes a fuzzy listen connection ID associated with the specified group key pattern. If the group key pattern
     * exists in the context and the connection ID is found in the associated set, the connection ID will be removed
     * from the set. If the set becomes empty after removal, the entry for the group key pattern will be removed from
     * the context.
     *
     * @param groupKeyPattern The group key pattern associated with the listen connection to be removed.
     * @param connectionId    The connection ID to be removed.
     */
    public synchronized void removeFuzzyListen(String groupKeyPattern, String connectionId) {
        // Retrieve the set of connection IDs associated with the group key pattern
        Set<String> connectIds = keyPatternContext.get(groupKeyPattern);
        if (CollectionUtils.isNotEmpty(connectIds)) {
            // Remove the connection ID from the set if it exists
            connectIds.remove(connectionId);
            // Remove the entry for the group key pattern if the set becomes empty after removal
            if (connectIds.isEmpty()) {
                keyPatternContext.remove(groupKeyPattern);
            }
        }
    }
    
    /**
     * Retrieves the set of fuzzy listen connection IDs associated with the specified group key pattern.
     *
     * @param groupKeyPattern The group key pattern to retrieve the associated connection IDs.
     * @return The set of connection IDs associated with the group key pattern, or null if no connections are found.
     */
    public synchronized Set<String> getFuzzyListeners(String groupKeyPattern) {
        // Retrieve the set of connection IDs associated with the group key pattern
        Set<String> connectionIds = keyPatternContext.get(groupKeyPattern);
        // If the set is not empty, create a new set and safely copy the connection IDs into it
        if (CollectionUtils.isNotEmpty(connectionIds)) {
            Set<String> listenConnections = new HashSet<>();
            safeCopy(connectionIds, listenConnections);
            return listenConnections;
        }
        // Return null if no connections are found for the specified group key pattern
        return null;
    }
    
    /**
     * Retrieves the set of connection IDs matched with the specified group key.
     *
     * @param groupKey The group key to match with the key patterns.
     * @return The set of connection IDs matched with the group key.
     */
    public Set<String> getConnectIdMatchedPatterns(String groupKey) {
        // Initialize a set to store the matched connection IDs
        Set<String> connectIds = new HashSet<>();
        // Iterate over each key pattern in the context
        for (String keyPattern : keyPatternContext.keySet()) {
            // Check if the group key matches the current key pattern
            if (GroupKeyPattern.isMatchPatternWithNamespace(groupKey, keyPattern)) {
                // If matched, add the associated connection IDs to the set
                Set<String> connectIdSet = keyPatternContext.get(keyPattern);
                if (CollectionUtils.isNotEmpty(connectIdSet)) {
                    connectIds.addAll(connectIdSet);
                }
            }
        }
        return connectIds;
    }
    
    /**
     * Add listen.
     *
     * @param groupKey     groupKey.
     * @param connectionId connectionId.
     */
    public synchronized void addListen(String groupKey, String md5, String connectionId) {
        // 1.add groupKeyContext
        groupKeyContext.computeIfAbsent(groupKey, k -> new HashSet<>()).add(connectionId);
        // 2.add connectionIdContext
        connectionIdContext.computeIfAbsent(connectionId, k -> new HashMap<>(16)).put(groupKey, md5);
    }
    
    /**
     * remove listen context for connection id .
     *
     * @param groupKey     groupKey.
     * @param connectionId connection id.
     */
    public synchronized void removeListen(String groupKey, String connectionId) {
        
        //1. remove groupKeyContext
        Set<String> connectionIds = groupKeyContext.get(groupKey);
        if (connectionIds != null) {
            connectionIds.remove(connectionId);
            if (connectionIds.isEmpty()) {
                groupKeyContext.remove(groupKey);
            }
        }
        
        //2.remove connectionIdContext
        HashMap<String, String> groupKeys = connectionIdContext.get(connectionId);
        if (groupKeys != null) {
            groupKeys.remove(groupKey);
        }
    }
    
    /**
     * get listeners of the group key.
     *
     * @param groupKey groupKey.
     * @return the copy of listeners, may be return null.
     */
    public synchronized Set<String> getListeners(String groupKey) {
        
        HashSet<String> strings = groupKeyContext.get(groupKey);
        if (CollectionUtils.isNotEmpty(strings)) {
            Set<String> listenConnections = new HashSet<>();
            safeCopy(strings, listenConnections);
            return listenConnections;
        }
        return null;
    }
    
    /**
     * copy collections.
     *
     * @param src  may be modified concurrently
     * @param dest dest collection
     */
    private void safeCopy(Collection src, Collection dest) {
        Iterator iterator = src.iterator();
        while (iterator.hasNext()) {
            dest.add(iterator.next());
        }
    }
    
    /**
     * remove the context related to the connection id.
     *
     * @param connectionId connectionId.
     */
    public synchronized void clearContextForConnectionId(final String connectionId) {
        
        Map<String, String> listenKeys = getListenKeys(connectionId);
        
        if (listenKeys == null) {
            connectionIdContext.remove(connectionId);
            return;
        }
        for (Map.Entry<String, String> groupKey : listenKeys.entrySet()) {
    
            Set<String> connectionIds = groupKeyContext.get(groupKey.getKey());
            if (CollectionUtils.isNotEmpty(connectionIds)) {
                connectionIds.remove(connectionId);
                if (connectionIds.isEmpty()) {
                    groupKeyContext.remove(groupKey.getKey());
                }
            } else {
                groupKeyContext.remove(groupKey.getKey());
            }
    
        }
        connectionIdContext.remove(connectionId);
    
        // Remove any remaining fuzzy listen connections
        for (Map.Entry<String, Set<String>> keyPatternContextEntry : keyPatternContext.entrySet()) {
            String keyPattern = keyPatternContextEntry.getKey();
            Set<String> connectionIds = keyPatternContextEntry.getValue();
            if (CollectionUtils.isEmpty(connectionIds)) {
                keyPatternContext.remove(keyPattern);
            } else {
                connectionIds.remove(keyPattern);
                if (CollectionUtils.isEmpty(connectionIds)) {
                    keyPatternContext.remove(keyPattern);
                }
            }
        }
    }
    
    /**
     * get listen keys.
     *
     * @param connectionId connection id.
     * @return listen group keys of the connection id, key:group key,value:md5
     */
    public synchronized Map<String, String> getListenKeys(String connectionId) {
        HashMap<String, String> stringStringHashMap = connectionIdContext.get(connectionId);
        return stringStringHashMap == null ? null : new HashMap<>(stringStringHashMap);
    }
    
    /**
     * get md5.
     *
     * @param connectionId connection id.
     * @return md5 of the listen group key.
     */
    public String getListenKeyMd5(String connectionId, String groupKey) {
        Map<String, String> groupKeyContexts = connectionIdContext.get(connectionId);
        return groupKeyContexts == null ? null : groupKeyContexts.get(groupKey);
    }
    
    /**
     * get connection count.
     *
     * @return count of long connections.
     */
    public int getConnectionCount() {
        return connectionIdContext.size();
    }
    
}
