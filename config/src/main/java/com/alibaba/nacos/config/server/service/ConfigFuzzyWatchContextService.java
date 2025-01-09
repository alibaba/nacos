/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.config.server.service;


import com.alibaba.nacos.api.exception.runtime.NacosRuntimeException;
import com.alibaba.nacos.common.utils.CollectionUtils;
import com.alibaba.nacos.common.utils.FuzzyGroupKeyPattern;
import com.alibaba.nacos.config.server.utils.GroupKey;
import com.alibaba.nacos.config.server.utils.GroupKey2;
import com.alibaba.nacos.config.server.utils.LogUtil;
import com.alibaba.nacos.core.utils.GlobalExecutor;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static com.alibaba.nacos.api.common.Constants.ConfigChangedType.ADD_CONFIG;
import static com.alibaba.nacos.api.common.Constants.ConfigChangedType.CONFIG_CHANGED;
import static com.alibaba.nacos.api.common.Constants.ConfigChangedType.DELETE_CONFIG;
import static com.alibaba.nacos.api.model.v2.ErrorCode.FUZZY_WATCH_PATTERN_OVER_LIMIT;

@Component
public class ConfigFuzzyWatchContextService {
    
    /**
     * groupKeyPattern -> watched client id set.
     */
    private final Map<String, Set<String>> watchedClients = new ConcurrentHashMap<>();
    
    /**
     * groupKeyPattern -> matched groupKeys set.
     */
    private final Map<String, Set<String>> matchedGroupKeys = new ConcurrentHashMap<>();
    
    private final int FUZZY_WATCH_MAX_PATTERN_COUNT = 50;
    
    private final int FUZZY_WATCH_MAX_PATTERN_MATCHED_GROUP_KEY_COUNT = 1000;
    
    
    public ConfigFuzzyWatchContextService() {
        
        GlobalExecutor.scheduleWithFixDelayByCommon(() -> trimFuzzyWatchContext(), 30000);
    }
    
    /**
     * trim  fuzzy watch context. <br/>
     * 1.remove watchedClients if watched client is empty.
     * 2.remove matchedServiceKeys if watchedClients is null.
     * pattern matchedServiceKeys will be removed in second period to avoid frequently matchedServiceKeys init.
     */
    private void trimFuzzyWatchContext() {
        Iterator<Map.Entry<String, Set<String>>> iterator = matchedGroupKeys.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Set<String>> next = iterator.next();
            Set<String> watchedClients = this.watchedClients.get(next.getKey());
            
            if (watchedClients == null) {
                iterator.remove();
                LogUtil.DEFAULT_LOG.info("[fuzzy-watch] no watchedClients context for pattern {},remove matchedGroupKeys context",next.getKey());
            } else if (watchedClients.isEmpty()) {
                LogUtil.DEFAULT_LOG.info("[fuzzy-watch] no client watched pattern {},remove watchedClients context",next.getKey());
                this.watchedClients.remove(next.getKey());
            }
        }
    }
    
    
    /**
     * get matched exist group keys with the groupKeyPattern. return null if not matched
     *
     * @param groupKeyPattern
     * @return
     */
    public Set<String> matchGroupKeys(String groupKeyPattern) {
        return matchedGroupKeys.get(groupKeyPattern);
    }
    
    
    
    public boolean syncGroupKeyContext(String groupKey, String changedType) {
    
        boolean needNotify=false;
    
        String[] groupKeyItems = GroupKey.parseKey(groupKey);
        String dataId = groupKeyItems[0];
        String group = groupKeyItems[1];
        String namespace = groupKeyItems[2];
        Iterator<Map.Entry<String, Set<String>>> iterator = matchedGroupKeys.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Set<String>> entry = iterator.next();
            if (FuzzyGroupKeyPattern.matchPattern(entry.getKey(), dataId, group, namespace)) {
                if ((changedType.equals(ADD_CONFIG)||changedType.equals(CONFIG_CHANGED))&&entry.getValue().add(groupKey)) {
                    needNotify = true;
                }
    
                if (changedType.equals(DELETE_CONFIG)&&entry.getValue().remove(groupKey)) {
                    needNotify = true;
                }
            }
        }
        return needNotify;
    }
    
    /**
     * Matches the client effective group keys based on the specified group key pattern, client IP, and tag.
     *
     * @param groupKeyPattern The pattern to match group keys.
     * @return A set of group keys that match the pattern and are effective for the client.
     * @throws NacosRuntimeException,
     */
    private void initMatchGroupKeys(String groupKeyPattern) {
        if (matchedGroupKeys.containsKey(groupKeyPattern)) {
            return;
        }
        
        if (matchedGroupKeys.size() >= FUZZY_WATCH_MAX_PATTERN_COUNT) {
            LogUtil.DEFAULT_LOG.warn("[fuzzy-watch] pattern count is over limit ,pattern {} init fail,current count is {}",
                    groupKeyPattern, matchedGroupKeys.size());
            throw new NacosRuntimeException(FUZZY_WATCH_PATTERN_OVER_LIMIT.getCode(),
                    FUZZY_WATCH_PATTERN_OVER_LIMIT.getMsg());
        }
        
        matchedGroupKeys.computeIfAbsent(groupKeyPattern, k -> new HashSet<>());
        Set<String> matchedGroupKeys = this.matchedGroupKeys.get(groupKeyPattern);
        long matchBeginTime=System.currentTimeMillis();
        for (String groupKey : ConfigCacheService.CACHE.keySet()) {
            String[] groupKeyItems = GroupKey.parseKey(groupKey);
            if (FuzzyGroupKeyPattern.matchPattern(groupKeyPattern, groupKeyItems[0], groupKeyItems[1],
                    groupKeyItems[2])) {
                
                if (matchedGroupKeys.size() >= FUZZY_WATCH_MAX_PATTERN_MATCHED_GROUP_KEY_COUNT) {
                    LogUtil.DEFAULT_LOG.warn("[fuzzy-watch]   pattern matched service count is over limit , other services will stop notify for pattern {} ,current count is {}",
                            groupKeyPattern,matchedGroupKeys.size());
                  
                    break;
//                    throw new NacosRuntimeException(FUZZY_WATCH_PATTERN_MATCH_GROUP_KEY_OVER_LIMIT.getCode(),
//                            FUZZY_WATCH_PATTERN_MATCH_GROUP_KEY_OVER_LIMIT.getMsg());
                }
                matchedGroupKeys.add(groupKey);
            }
        }
        LogUtil.DEFAULT_LOG.info("[fuzzy-watch]  pattern {} match {} group keys, cost {}ms", groupKeyPattern,
                matchedGroupKeys.size(), System.currentTimeMillis() - matchBeginTime);
        
    }
    
    /**
     * Adds a fuzzy listen connection ID associated with the specified group key pattern. If the key pattern does not
     * exist in the context, a new entry will be created. If the key pattern already exists, the connection ID will be
     * added to the existing set.
     *
     * @param groupKeyPattern The group key pattern to associate with the listen connection.
     * @param connectId       The connection ID to be added.
     */
    public synchronized void addFuzzyListen(String groupKeyPattern, String connectId) {
        
        initMatchGroupKeys(groupKeyPattern);
        // Add the connection ID to the set associated with the key pattern in keyPatternContext
        watchedClients.computeIfAbsent(groupKeyPattern, k -> new HashSet<>());
        watchedClients.get(groupKeyPattern).add(connectId);
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
        Set<String> connectIds = watchedClients.get(groupKeyPattern);
        if (CollectionUtils.isNotEmpty(connectIds)) {
            // Remove the connection ID from the set if it exists
            connectIds.remove(connectionId);
        }
    }
    
    /**
     * remove watch context for connection id.
     *
     * @param connectionId
     */
    public void clearFuzzyWatchContext(String connectionId) {
        for (Map.Entry<String, Set<String>> keyPatternContextEntry : watchedClients.entrySet()) {
            Set<String> connectionIds = keyPatternContextEntry.getValue();
            if (CollectionUtils.isNotEmpty(connectionIds)) {
                connectionIds.remove(connectionId);
            }
        }
    }
    
    /**
     * Retrieves the set of connection IDs matched with the specified group key.
     *
     * @param groupKey The group key to match with the key patterns.
     * @return The set of connection IDs matched with the group key.
     */
    public Set<String> getMatchedClients(String groupKey) {
        // Initialize a set to store the matched connection IDs
        Set<String> connectIds = new HashSet<>();
        // Iterate over each key pattern in the context
        Iterator<Map.Entry<String, Set<String>>> watchClientIterator = watchedClients.entrySet().iterator();
    
        String[] groupItems = GroupKey2.parseKey(groupKey);
        
        while (watchClientIterator.hasNext()){
            Map.Entry<String, Set<String>> watchClientEntry = watchClientIterator.next();
    
            String keyPattern = watchClientEntry.getKey();
            if (FuzzyGroupKeyPattern.matchPattern(keyPattern, groupItems[0], groupItems[1], groupItems[2])) {
                if (CollectionUtils.isNotEmpty(watchClientEntry.getValue())) {
                    connectIds.addAll(watchClientEntry.getValue());
                }
            }
        }
        return connectIds;
    }
    
}
