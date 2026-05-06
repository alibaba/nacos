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
import com.alibaba.nacos.config.server.model.ConfigListenState;
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
     * groupKey-> connection set.
     */
    private ConcurrentHashMap<String, HashSet<String>> groupKeyContext = new ConcurrentHashMap<>();
    
    /**
     * connectionId-> group key set.
     */
    private ConcurrentHashMap<String, HashMap<String, ConfigListenState>> connectionIdContext = new ConcurrentHashMap<>();
    
    /**
     * add listen.
     *
     * @param groupKey     groupKey.
     * @param connectionId connectionId.
     */
    public synchronized void addListen(String groupKey, String md5, String connectionId, boolean isNamespaceTransfer) {
        // 1.add groupKeyContext
        groupKeyContext.computeIfAbsent(groupKey, k -> new HashSet<>()).add(connectionId);
        // 2.add connectionIdContext
        ConfigListenState listenState = new ConfigListenState(md5);
        listenState.setNamespaceTransfer(isNamespaceTransfer);
        connectionIdContext.computeIfAbsent(connectionId, k -> new HashMap<>(16)).put(groupKey, listenState);
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
        HashMap<String, ConfigListenState> groupKeys = connectionIdContext.get(connectionId);
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
    }
    
    /**
     * get listen keys.
     *
     * @param connectionId connection id.
     * @return listen group keys of the connection id, key:group key,value:md5
     */
    public synchronized Map<String, String> getListenKeys(String connectionId) {
        HashMap<String, ConfigListenState> stringStringHashMap = connectionIdContext.get(connectionId);
        if (stringStringHashMap != null) {
            HashMap<String, String> md5Map = new HashMap<>(stringStringHashMap.size());
            for (Map.Entry<String, ConfigListenState> entry : stringStringHashMap.entrySet()) {
                md5Map.put(entry.getKey(), entry.getValue().getMd5());
            }
            return md5Map;
        } else {
            return null;
        }
    }
    
    /**
     * get md5.
     *
     * @param connectionId connection id.
     * @return md5 of the listen group key.
     */
    public String getListenKeyMd5(String connectionId, String groupKey) {
        Map<String, ConfigListenState> groupKeyContexts = connectionIdContext.get(connectionId);
        return groupKeyContexts == null ? null : groupKeyContexts.get(groupKey).getMd5();
    }
    
    public ConfigListenState getConfigListenState(String connectionId, String groupKey) {
        Map<String, ConfigListenState> groupKeyContexts = connectionIdContext.get(connectionId);
        return groupKeyContexts == null ? null : groupKeyContexts.get(groupKey);
    }
    
    public synchronized HashMap<String, ConfigListenState> getConfigListenStates(String connectionId) {
        HashMap<String, ConfigListenState> configListenStateHashMap = connectionIdContext.get(connectionId);
        return configListenStateHashMap == null ? null : new HashMap<>(configListenStateHashMap);
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
