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
import com.alibaba.nacos.common.utils.MapUtil;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.HashSet;
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
     * groupKey-> connnection set.
     */
    private final Map<String, Set<String>> groupKeyContext = new ConcurrentHashMap<String, Set<String>>(128);
    
    /**
     * connectionId-> groupkey set.
     */
    private final Map<String, HashMap<String, String>> connectionIdContext = new ConcurrentHashMap<String, HashMap<String, String>>(128);
    
    /**
     * add listen .
     *
     * @param listenKey    listenKey.
     * @param connectionId connectionId.
     */
    public void addListen(String listenKey, String md5, String connectionId) {
        
        // 1.add groupKeyContext
        Set<String> listenClients = groupKeyContext.get(listenKey);
        if (listenClients == null) {
            groupKeyContext.putIfAbsent(listenKey, new HashSet<String>());
            listenClients = groupKeyContext.get(listenKey);
        }
        listenClients.add(connectionId);
        
        // 2.add connectionIdContext
        HashMap<String, String> groupKeys = connectionIdContext.get(connectionId);
        if (groupKeys == null) {
            connectionIdContext.putIfAbsent(connectionId, new HashMap<String, String>(16));
            groupKeys = connectionIdContext.get(connectionId);
        }
        groupKeys.put(listenKey, md5);
        
    }
    
    /**
     * remove listen context for connection id .
     *
     * @param listenKey    listenKey.
     * @param connectionId connection id.
     */
    public void removeListen(String listenKey, String connectionId) {
        
        //1. remove groupKeyContext
        Set<String> connectionIds = groupKeyContext.get(listenKey);
        if (connectionIds != null) {
            connectionIds.remove(connectionId);
            if (connectionIds.isEmpty()) {
                MapUtil.removeKey(groupKeyContext, listenKey, CollectionUtils::isEmpty);
            }
        }
        
        //2.remove connectionIdContext
        HashMap<String, String> groupKeys = connectionIdContext.get(connectionId);
        if (groupKeys != null) {
            groupKeys.remove(listenKey);
        }
    }
    
    public Set<String> getListeners(String listenKey) {
        return groupKeyContext.get(listenKey);
    }
    
    /**
     * remove the context related to the connectionid.
     *
     * @param connectionId connectionId.
     */
    public void clearContextForConnectionId(final String connectionId) {
    
        Map<String, String> listenKeys = getListenKeys(connectionId);
    
        if (listenKeys != null) {
            for (Map.Entry<String, String> groupKey : listenKeys.entrySet()) {
            
                Set<String> connectionIds = groupKeyContext.get(groupKey.getKey());
                if (CollectionUtils.isNotEmpty(connectionIds)) {
                    connectionIds.remove(connectionId);
                } else {
                    MapUtil.removeKey(groupKeyContext, groupKey.getKey(), CollectionUtils::isEmpty);
                }
            
            }
        }
        MapUtil.removeKey(connectionIdContext, connectionId, MapUtil::isEmpty);
    }
    
    /**
     * get listenkeys.
     *
     * @param connectionId connetionid.
     * @return
     */
    public Map<String, String> getListenKeys(String connectionId) {
        return connectionIdContext.get(connectionId);
    }
    
    /**
     * get listenkey.
     *
     * @param connectionId connetionid.
     * @return
     */
    public String getListenKeyMd5(String connectionId, String groupKey) {
        Map<String, String> groupKeyContexts = connectionIdContext.get(connectionId);
        return groupKeyContexts == null ? null : groupKeyContexts.get(groupKey);
    }
}
