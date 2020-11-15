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
     * groupKey-> connnection set.
     */
    private Map<String, Set<String>> groupKeyContext = new ConcurrentHashMap<String, Set<String>>();
    
    /**
     * connectionId-> groupkey set.
     */
    private Map<String, Set<GroupKeyContext>> connectionIdContext = new ConcurrentHashMap<String, Set<GroupKeyContext>>();
    
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
        Set<GroupKeyContext> groupKeys = connectionIdContext.get(connectionId);
        if (groupKeys == null) {
            connectionIdContext.putIfAbsent(connectionId, new HashSet<>());
            groupKeys = connectionIdContext.get(connectionId);
        }
    
        Iterator<GroupKeyContext> iterator = groupKeys.iterator();
        GroupKeyContext findKey = null;
        while (iterator.hasNext()) {
            GroupKeyContext next = iterator.next();
            if (next.groupkey.equals(listenKey)) {
                findKey = next;
            }
        }
    
        if (findKey != null) {
            findKey.setMd5(md5);
        } else {
            groupKeys.add(new GroupKeyContext(listenKey, md5));
        }
        
    }
    
    /**
     * remove listen context for connection id .
     * @param listenKey    listenKey.
     * @param connectionId connection id.
     */
    public void removeListen(String listenKey, String connectionId) {
        
        //1. remove groupKeyContext
        Set<String> connectionIds = groupKeyContext.get(listenKey);
        if (connectionIds != null) {
            connectionIds.remove(connectionId);
            if (connectionIds.isEmpty()) {
                groupKeyContext.remove(listenKey);
            }
        }
        
        //2.remove connectionIdContext
        Set<GroupKeyContext> groupKeys = connectionIdContext.get(connectionId);
        if (groupKeys != null) {
            Iterator<GroupKeyContext> iterator = groupKeys.iterator();
            GroupKeyContext findKey = null;
            while (iterator.hasNext()) {
                GroupKeyContext next = iterator.next();
                if (next.groupkey.equals(listenKey)) {
                    findKey = next;
                }
            }
            if (findKey != null) {
                groupKeys.remove(findKey);
            }
            if (CollectionUtils.isEmpty(groupKeys)) {
                connectionIdContext.remove(connectionId);
            }
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
    
        Set<GroupKeyContext> listenKeys = getListenKeys(connectionId);
    
        if (CollectionUtils.isNotEmpty(listenKeys)) {
            for (GroupKeyContext groupKey : listenKeys) {
                Set<String> listeners = getListeners(groupKey.groupkey);
                if (CollectionUtils.isNotEmpty(listeners)) {
                    listeners.remove(connectionId);
                }
            }
        }
        connectionIdContext.remove(connectionId);
    }
    
    /**
     * get listenkeys.
     *
     * @param connectionId connetionid.
     * @return
     */
    public Set<GroupKeyContext> getListenKeys(String connectionId) {
        return connectionIdContext.get(connectionId);
    }
    
    /**
     * get listenkey.
     *
     * @param connectionId connetionid.
     * @return
     */
    public GroupKeyContext getListenKey(String connectionId, String groupKey) {
        Set<GroupKeyContext> groupKeyContexts = connectionIdContext.get(connectionId);
        if (groupKeyContexts == null) {
            return null;
        }
        
        Iterator<GroupKeyContext> iterator = groupKeyContexts.iterator();
        while (iterator.hasNext()) {
            GroupKeyContext next = iterator.next();
            if (next.groupkey.equals(groupKey)) {
                return next;
            }
        }
        return null;
    }
}
