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

package com.alibaba.nacos.core.remote;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;

/**
 * AsyncListenContext. conserve which clientId that insterest in which congif key.
 *
 * @author liuzunfei
 * @version $Id: AsyncListenContext.java, v 0.1 2020年07月14日 10:13 AM liuzunfei Exp $
 */
@Service
public class AsyncListenContext {
    
    private Map<String, Map<String, Set<String>>> listenContexts = new HashMap<String, Map<String, Set<String>>>();
    
    /**
     * add listen .
     *
     * @param requestType  requestType
     * @param listenKey    listenKey.
     * @param connectionId connectionId.
     */
    public void addListen(String requestType, String listenKey, String connectionId) {
        Map<String, Set<String>> listenClients = listenContexts.get(requestType);
    
        if (listenClients == null) {
            listenContexts.putIfAbsent(requestType, new HashMap<String, Set<String>>());
            listenClients = listenContexts.get(requestType);
        }
    
        Set<String> connectionIds = listenClients.get(listenKey);
        if (connectionIds == null) {
            listenClients.putIfAbsent(listenKey, new HashSet<String>());
            connectionIds = listenClients.get(listenKey);
        }
    
        boolean addSuccess = connectionIds.add(connectionId);
        if (addSuccess) {
            //TODO add log ...success to add listen
        }
    
    }
    
    /**
     * remove listen context for connectionId ..
     *
     * @param requestType  requestType
     * @param lisnteKey    lisnteKey
     * @param connectionId connectionId
     */
    public void removeListen(String requestType, String lisnteKey, String connectionId) {
        
        Map<String, Set<String>> stringSetMap = listenContexts.get(requestType);
        if (stringSetMap == null || stringSetMap.isEmpty()) {
            return;
        }
        
        Set<String> connectionIds = stringSetMap.get(lisnteKey);
        if (connectionIds == null) {
            return;
        }
        
        boolean remove = connectionIds.remove(connectionId);
        if (remove) {
            //TODO add log ...success to remove listen
        }
    }
    
    public Set<String> getListeners(String requestType, String listenKey) {
        if (listenContexts.containsKey(requestType)) {
            Map<String, Set<String>> stringSetMap = listenContexts.get(requestType);
            return stringSetMap.get(listenKey);
        }
        return null;
    }
    
    /**
     * Judge whether contain listener for item.
     *
     * @param requestType request type
     * @param listenKey listen key
     * @return true if has contained, otherwise false
     */
    public boolean containListener(String requestType, String listenKey) {
        if (!listenContexts.containsKey(requestType)) {
            return false;
        }
        return listenContexts.get(requestType).containsKey(listenKey);
    }
}
