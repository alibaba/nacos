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

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * config change listen context.
 *
 * @author liuzunfei
 * @version $Id: ConfigChangeListenContext.java, v 0.1 2020年07月20日 1:37 PM liuzunfei Exp $
 */
@Component
public class ConfigChangeListenContext {
    
    /**
     * groupKey-> connnection set.
     */
    private Map<String, Set<String>> groupKeyContext = new HashMap<String, Set<String>>();
    
    /**
     * connectionId-> groupkey set.
     */
    private Map<String, Set<String>> connectionIdContext = new HashMap<String, Set<String>>();
    
    /**
     * add listen .
     *
     * @param listenKey    listenKey.
     * @param connectionId connectionId.
     */
    public void addListen(String listenKey, String connectionId) {
        
        // 1.add groupKeyContext
        Set<String> listenClients = groupKeyContext.get(listenKey);
        if (listenClients == null) {
            groupKeyContext.putIfAbsent(listenKey, new HashSet<String>());
            listenClients = groupKeyContext.get(listenKey);
        }
        listenClients.add(connectionId);
        
        // 2.add connectionIdContext
        Set<String> groupKeys = connectionIdContext.get(connectionId);
        if (groupKeys == null) {
            connectionIdContext.putIfAbsent(connectionId, new HashSet<>());
            groupKeys = connectionIdContext.get(connectionId);
        }
        groupKeys.add(listenKey);
        
    }
    
    /**
     * remove listen context for connectionId ..
     *
     * @param lisnteKey    lisnteKey
     * @param connectionId connectionId
     */
    public void removeListen(String lisnteKey, String connectionId) {
        
        //1. remove groupKeyContext
        Set<String> connectionIds = groupKeyContext.get(lisnteKey);
        if (connectionIds != null) {
            connectionIds.remove(connectionId);
        }
        
        //2.remove connectionIdContext
        Set<String> groupKeys = connectionIdContext.get(connectionId);
        if (groupKeys != null) {
            groupKeys.remove(lisnteKey);
        }
    }
    
    public Set<String> getListeners(String listenKey) {
        if (groupKeyContext.containsKey(listenKey)) {
            return groupKeyContext.get(listenKey);
        }
        return null;
    }
    
    /**
     * remove the context related to the connectionid.
     *
     * @param connectionId connectionId.
     */
    public void removeConnectionId(final String connectionId) {
        Set<String> groupKeysinner = connectionIdContext.get(connectionId);
        
        if (groupKeysinner != null) {
            Set<String> groupKeys = new HashSet<String>(groupKeysinner);
            for (String groupKey : groupKeys) {
                removeListen(groupKey, connectionId);
            }
        }
        connectionIdContext.remove(connectionId);
    }
}