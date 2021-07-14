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
    private ConcurrentHashMap<String, HashSet<String>> groupKeyContext = new ConcurrentHashMap<String, HashSet<String>>();
    
    /**
     * connectionId-> group key set.
     */
    private ConcurrentHashMap<String, HashMap<String, String>> connectionIdContext = new ConcurrentHashMap<String, HashMap<String, String>>();
    
    /**
     * add listen.
     *
     * @param groupKey     groupKey.
     * @param connectionId connectionId.
     */
    public synchronized void addListen(String groupKey, String md5, String connectionId) {
        // 1.add groupKeyContext
        Set<String> listenClients = groupKeyContext.get(groupKey);
        if (listenClients == null) {
            groupKeyContext.putIfAbsent(groupKey, new HashSet<String>());
            listenClients = groupKeyContext.get(groupKey);
        }
        listenClients.add(connectionId);
        
        // 2.add connectionIdContext
        HashMap<String, String> groupKeys = connectionIdContext.get(connectionId);
        if (groupKeys == null) {
            connectionIdContext.putIfAbsent(connectionId, new HashMap<String, String>(16));
            groupKeys = connectionIdContext.get(connectionId);
        }
        groupKeys.put(groupKey, md5);
        
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
            Set<String> listenConnections = new HashSet<String>();
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
        HashMap<String, String> stringStringHashMap = connectionIdContext.get(connectionId);
        return stringStringHashMap == null ? null : new HashMap<String, String>(stringStringHashMap);
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
    
}
