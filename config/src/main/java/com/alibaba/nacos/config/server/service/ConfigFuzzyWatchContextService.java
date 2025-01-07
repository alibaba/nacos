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
import com.alibaba.nacos.core.utils.GlobalExecutor;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static com.alibaba.nacos.api.model.v2.ErrorCode.FUZZY_WATCH_PATTERN_MATCH_GROUP_KEY_OVER_LIMIT;
import static com.alibaba.nacos.api.model.v2.ErrorCode.FUZZY_WATCH_PATTERN_OVER_LIMIT;

@Component
public class ConfigFuzzyWatchContextService {
    
    /**
     * groupKeyPattern -> watched connection id set.
     */
    private final Map<String, Set<String>> keyPatternWatchClients = new ConcurrentHashMap<>();
    
    /**
     * groupKeyPattern -> groupKeys set.
     */
    private final Map<String, Set<String>> groupKeyMatched = new ConcurrentHashMap<>();
    
    private final int FUZZY_WATCH_MAX_PATTERN_COUNT = 50;
    
    private final int FUZZY_WATCH_MAX_PATTERN_MATCHED_GROUP_KEY_COUNT = 200;
    
    
    public ConfigFuzzyWatchContextService(){
    
        GlobalExecutor.scheduleWithFixDelayByCommon(() -> clearFuzzyWatchContext(), 30000);
    }
    
    private void clearFuzzyWatchContext(){
        Iterator<Map.Entry<String, Set<String>>> iterator = groupKeyMatched.entrySet().iterator();
        while (iterator.hasNext()){
            Map.Entry<String, Set<String>> next = iterator.next();
            Set<String> watchedClients = keyPatternWatchClients.get(next.getKey());
            if(CollectionUtils.isEmpty(watchedClients)){
                iterator.remove();
                if (watchedClients!=null){
                    keyPatternWatchClients.remove(next.getKey());
                }
            }
        }
    }
    
    /**
     * get matched exist group keys with the groupKeyPattern.
     * return null if not matched
     * @param groupKeyPattern
     * @return
     */
    public Set<String> matchGroupKeys(String groupKeyPattern) {
        if (groupKeyMatched.containsKey(groupKeyPattern)) {
            return groupKeyMatched.get(groupKeyPattern);
        } else {
            return null;
        }
    }
    
    public boolean newConfigAdded(String groupKey){
        String[] groupKeyItems = GroupKey.parseKey(groupKey);
        String dataId=groupKeyItems[0];
        String group=groupKeyItems[1];
        String namespace=groupKeyItems[2];
        Iterator<Map.Entry<String, Set<String>>> iterator = groupKeyMatched.entrySet().iterator();
        boolean added=false;
        while (iterator.hasNext()){
            Map.Entry<String, Set<String>> entry = iterator.next();
            if(FuzzyGroupKeyPattern.matchPattern(entry.getKey(),dataId,group,namespace)){
               if(entry.getValue().add(groupKey)){
                   added=true;
               }
               
            }
        }
        
        return added;
    
    }
    
    public boolean configRemoved(String groupKey){
        String[] groupKeyItems = GroupKey.parseKey(groupKey);
        String dataId=groupKeyItems[0];
        String group=groupKeyItems[1];
        String namespace=groupKeyItems[2];
        Iterator<Map.Entry<String, Set<String>>> iterator = groupKeyMatched.entrySet().iterator();
        boolean removed=false;
        while (iterator.hasNext()){
            Map.Entry<String, Set<String>> entry = iterator.next();
            if(FuzzyGroupKeyPattern.matchPattern(entry.getKey(),dataId,group,namespace)){
                if(entry.getValue().remove(groupKey)){
                    removed=true;
                }
                
            }
        }
        
        return removed;
        
    }
    
    /**
     * Matches the client effective group keys based on the specified group key pattern, client IP, and tag.
     *
     * @param groupKeyPattern The pattern to match group keys.
     * @return A set of group keys that match the pattern and are effective for the client.
     * @throws NacosRuntimeException,
     */
    private void initMatchGroupKeys(String groupKeyPattern) {
        if (groupKeyMatched.containsKey(groupKeyPattern)) {
            return;
        }
        
        if (groupKeyMatched.size() >= FUZZY_WATCH_MAX_PATTERN_COUNT) {
            throw new NacosRuntimeException(FUZZY_WATCH_PATTERN_OVER_LIMIT.getCode(),FUZZY_WATCH_PATTERN_OVER_LIMIT.getMsg());
        }
        Set<String> matchedGroupKeys = groupKeyMatched.computeIfAbsent(groupKeyPattern, k -> new HashSet<>());
        
        for (String groupKey : ConfigCacheService.CACHE.keySet()) {
            String[] groupKeyItems = GroupKey.parseKey(groupKey);
            if (FuzzyGroupKeyPattern.matchPattern(groupKeyPattern, groupKeyItems[0], groupKeyItems[1],
                    groupKeyItems[2])) {
                
                if (matchedGroupKeys.size() >= FUZZY_WATCH_MAX_PATTERN_MATCHED_GROUP_KEY_COUNT) {
                    throw new NacosRuntimeException(FUZZY_WATCH_PATTERN_MATCH_GROUP_KEY_OVER_LIMIT.getCode(), FUZZY_WATCH_PATTERN_MATCH_GROUP_KEY_OVER_LIMIT.getMsg());
                }
                matchedGroupKeys.add(groupKey);
            }
        }
        
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
        keyPatternWatchClients.computeIfAbsent(groupKeyPattern, k -> new HashSet<>()).add(connectId);
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
        Set<String> connectIds = keyPatternWatchClients.get(groupKeyPattern);
        if (CollectionUtils.isNotEmpty(connectIds)) {
            // Remove the connection ID from the set if it exists
            connectIds.remove(connectionId);
        }
    }
    
    /**
     * remove watch context for connection id.
     * @param connectionId
     */
    public void clearFuzzyWatchContext(String connectionId) {
        for (Map.Entry<String, Set<String>> keyPatternContextEntry : keyPatternWatchClients.entrySet()) {
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
        for (String keyPattern : keyPatternWatchClients.keySet()) {
            // Check if the group key matches the current key pattern
            String[] strings = GroupKey2.parseKey(groupKey);
            
            if (FuzzyGroupKeyPattern.matchPattern(groupKey, strings[0], strings[1], strings[2])) {
                // If matched, add the associated connection IDs to the set
                Set<String> connectIdSet = keyPatternWatchClients.get(keyPattern);
                if (CollectionUtils.isNotEmpty(connectIdSet)) {
                    connectIds.addAll(connectIdSet);
                }
            }
        }
        return connectIds;
    }
    
}
