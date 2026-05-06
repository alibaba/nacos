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

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.common.utils.CollectionUtils;
import com.alibaba.nacos.common.utils.FuzzyGroupKeyPattern;
import com.alibaba.nacos.config.server.configuration.ConfigCommonConfig;
import com.alibaba.nacos.config.server.utils.GroupKey;
import com.alibaba.nacos.config.server.utils.GroupKey2;
import com.alibaba.nacos.config.server.utils.LogUtil;
import com.alibaba.nacos.core.utils.GlobalExecutor;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static com.alibaba.nacos.api.common.Constants.ConfigChangedType.ADD_CONFIG;
import static com.alibaba.nacos.api.common.Constants.ConfigChangedType.CONFIG_CHANGED;
import static com.alibaba.nacos.api.common.Constants.ConfigChangedType.DELETE_CONFIG;
import static com.alibaba.nacos.api.model.v2.ErrorCode.FUZZY_WATCH_PATTERN_OVER_LIMIT;

/**
 * fuzzy watch context for config.
 *
 * @author shiyiyue
 */
@Component
public class ConfigFuzzyWatchContextService {
    
    /**
     * groupKeyPattern -> watched client id set.
     */
    private final Map<String, Set<String>> watchedClientsMap = new ConcurrentHashMap<>();
    
    /**
     * groupKeyPattern -> matched groupKeys set.
     */
    private final Map<String, Set<String>> matchedGroupKeysMap = new ConcurrentHashMap<>();
    
    public ConfigFuzzyWatchContextService() {
    }
    
    @PostConstruct
    public void init() {
        GlobalExecutor.scheduleWithFixDelayByCommon(() -> trimFuzzyWatchContext(), 30000);
    }
    
    /**
     * trim  fuzzy watch context. <br/> 1.remove watchedClients if watched client is empty. 2.remove matchedServiceKeys
     * if watchedClients is null. pattern matchedServiceKeys will be removed in second period to avoid frequently
     * matchedServiceKeys init.
     */
    void trimFuzzyWatchContext() {
        try {
            Iterator<Map.Entry<String, Set<String>>> iterator = matchedGroupKeysMap.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<String, Set<String>> matchedGroupKeys = iterator.next();
                Set<String> watchedClients = this.watchedClientsMap.get(matchedGroupKeys.getKey());
                
                if (watchedClients == null) {
                    iterator.remove();
                    LogUtil.DEFAULT_LOG.info(
                            "[fuzzy-watch] no watchedClients context for pattern {},remove matchedGroupKeys context",
                            matchedGroupKeys.getKey());
                } else if (watchedClients.isEmpty()) {
                    LogUtil.DEFAULT_LOG.info("[fuzzy-watch] no client watched pattern {},remove watchedClients context",
                            matchedGroupKeys.getKey());
                    this.watchedClientsMap.remove(matchedGroupKeys.getKey());
                } else if (reachToUpLimit(matchedGroupKeys.getValue().size())) {
                    LogUtil.DEFAULT_LOG.warn(
                            "[fuzzy-watch] pattern {} matched config count has reached to upper limit {}, fuzzy watch has been suppressed ",
                            matchedGroupKeys.getKey(), matchedGroupKeys.getValue().size());
                } else if (reachToUpLimit((int) (matchedGroupKeys.getValue().size() * 1.25))) {
                    LogUtil.DEFAULT_LOG.warn(
                            "[fuzzy-watch] pattern {} matched config count has reached to 80% of the upper limit {} "
                                    + ",it may has a risk of notify suppressed in the near further",
                            matchedGroupKeys.getKey(), matchedGroupKeys.getValue().size());
                }
            }
        } catch (Throwable throwable) {
            LogUtil.DEFAULT_LOG.warn("[fuzzy-watch] trim fuzzy watch context fail", throwable);
        }
    }
    
    /**
     * get matched exist group keys with the groupKeyPattern. return null if not matched.
     *
     * @param groupKeyPattern groupKeyPattern.
     * @return
     */
    public Set<String> matchGroupKeys(String groupKeyPattern) {
        Set<String> stringSet = matchedGroupKeysMap.get(groupKeyPattern);
        return stringSet == null ? new HashSet<>() : new HashSet<>(matchedGroupKeysMap.get(groupKeyPattern));
    }
    
    /**
     * sync group key change to fuzzy context.
     *
     * @param groupKey    groupKey.
     * @param changedType changedType.
     * @return need notify ot not.
     */
    public boolean syncGroupKeyContext(String groupKey, String changedType) {
        
        boolean needNotify = false;
        
        String[] groupKeyItems = GroupKey.parseKey(groupKey);
        String dataId = groupKeyItems[0];
        String group = groupKeyItems[1];
        String namespace = groupKeyItems[2];
        boolean tryAdd = changedType.equals(ADD_CONFIG) || changedType.equals(CONFIG_CHANGED);
        boolean tryRemove = changedType.equals(DELETE_CONFIG);
        
        Iterator<Map.Entry<String, Set<String>>> iterator = matchedGroupKeysMap.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Set<String>> entry = iterator.next();
            if (FuzzyGroupKeyPattern.matchPattern(entry.getKey(), dataId, group, namespace)) {
                boolean containsAlready = entry.getValue().contains(groupKey);
                boolean reachToUpLimit = reachToUpLimit(entry.getValue().size());
                if (tryAdd && !containsAlready && reachToUpLimit) {
                    LogUtil.DEFAULT_LOG.warn("[fuzzy-watch] pattern matched config count is over limit , "
                                    + "current config will be ignored for pattern {} ,current count is {}", entry.getKey(),
                            entry.getValue().size());
                    continue;
                }
                
                if (tryAdd && !containsAlready && entry.getValue().add(groupKey)) {
                    needNotify = true;
                }
                if (tryRemove && containsAlready && entry.getValue().remove(groupKey)) {
                    needNotify = true;
                    if (reachToUpLimit) {
                        makeupMatchedGroupKeys(entry.getKey());
                    }
                }
            }
        }
        return needNotify;
    }
    
    /**
     * make matched group key when deleted configs on loa protection model.
     *
     * @param groupKeyPattern group key pattern.
     */
    public void makeupMatchedGroupKeys(String groupKeyPattern) {
        
        Set<String> matchedGroupKeys = matchedGroupKeysMap.get(groupKeyPattern);
        if (matchedGroupKeys == null || reachToUpLimit(matchedGroupKeys.size())) {
            return;
        }
        
        for (String groupKey : ConfigCacheService.CACHE.keySet()) {
            String[] groupKeyItems = GroupKey.parseKey(groupKey);
            if (FuzzyGroupKeyPattern.matchPattern(groupKeyPattern, groupKeyItems[0], groupKeyItems[1], groupKeyItems[2])
                    && !matchedGroupKeys.contains(groupKey)) {
                matchedGroupKeys.add(groupKey);
                LogUtil.DEFAULT_LOG.info("[fuzzy-watch] pattern {} makeup group key {}", groupKeyPattern, groupKey);
                if (reachToUpLimit(matchedGroupKeys.size())) {
                    LogUtil.DEFAULT_LOG.warn(
                            "[fuzzy-watch] pattern {] matched config count is over limit ,makeup group keys skip.",
                            groupKeyPattern);
                    return;
                }
            }
        }
    }
    
    private boolean reachToUpLimit(int size) {
        return size >= ConfigCommonConfig.getInstance().getMaxMatchedConfigCount();
    }
    
    public boolean reachToUpLimit(String groupKeyPattern) {
        Set<String> strings = matchedGroupKeysMap.get(groupKeyPattern);
        return strings != null && (reachToUpLimit(strings.size()));
    }
    
    /**
     * Matches the client effective group keys based on the specified group key pattern, client IP, and tag.
     *
     * @param groupKeyPattern The pattern to match group keys.
     */
    private void initMatchGroupKeys(String groupKeyPattern) throws NacosException {
        if (matchedGroupKeysMap.containsKey(groupKeyPattern)) {
            return;
        }
        
        if (matchedGroupKeysMap.size() >= ConfigCommonConfig.getInstance().getMaxPatternCount()) {
            LogUtil.DEFAULT_LOG.warn(
                    "[fuzzy-watch] pattern count is over limit ,pattern {} init fail,current count is {}",
                    groupKeyPattern, matchedGroupKeysMap.size());
            throw new NacosException(FUZZY_WATCH_PATTERN_OVER_LIMIT.getCode(), FUZZY_WATCH_PATTERN_OVER_LIMIT.getMsg());
        }
        
        matchedGroupKeysMap.computeIfAbsent(groupKeyPattern, k -> new HashSet<>());
        Set<String> matchedGroupKeys = this.matchedGroupKeysMap.get(groupKeyPattern);
        long matchBeginTime = System.currentTimeMillis();
        boolean overMatchCount = false;
        for (String groupKey : ConfigCacheService.CACHE.keySet()) {
            String[] groupKeyItems = GroupKey.parseKey(groupKey);
            if (FuzzyGroupKeyPattern.matchPattern(groupKeyPattern, groupKeyItems[0], groupKeyItems[1],
                    groupKeyItems[2])) {
                
                if (reachToUpLimit(matchedGroupKeys.size())) {
                    LogUtil.DEFAULT_LOG.warn("[fuzzy-watch]   pattern matched service count is over limit , "
                                    + "other services will stop notify for pattern {} ,current count is {}", groupKeyPattern,
                            matchedGroupKeys.size());
                    overMatchCount = true;
                    break;
                }
                matchedGroupKeys.add(groupKey);
            }
        }
        LogUtil.DEFAULT_LOG.info("[fuzzy-watch]  pattern {} match {} group keys,overMatchCount={}, cost {}ms",
                groupKeyPattern, matchedGroupKeys.size(), overMatchCount, System.currentTimeMillis() - matchBeginTime);
        
    }
    
    /**
     * Adds a fuzzy listen connection ID associated with the specified group key pattern. If the key pattern does not
     * exist in the context, a new entry will be created. If the key pattern already exists, the connection ID will be
     * added to the existing set.
     *
     * @param groupKeyPattern The group key pattern to associate with the listen connection.
     * @param connectId       The connection ID to be added.
     * @throws NacosException over max pattern count.
     */
    public synchronized void addFuzzyWatch(String groupKeyPattern, String connectId) throws NacosException {
        watchedClientsMap.computeIfAbsent(groupKeyPattern, k -> new HashSet<>());
        initMatchGroupKeys(groupKeyPattern);
        // Add the connection ID to the set associated with the key pattern in keyPatternContext
        watchedClientsMap.get(groupKeyPattern).add(connectId);
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
        Set<String> connectIds = watchedClientsMap.get(groupKeyPattern);
        if (CollectionUtils.isNotEmpty(connectIds)) {
            // Remove the connection ID from the set if it exists
            connectIds.remove(connectionId);
        }
    }
    
    /**
     * remove watch context for connection id.
     *
     * @param connectionId connection id.
     */
    public void clearFuzzyWatchContext(String connectionId) {
        for (Map.Entry<String, Set<String>> keyPatternContextEntry : watchedClientsMap.entrySet()) {
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
        Iterator<Map.Entry<String, Set<String>>> watchClientIterator = watchedClientsMap.entrySet().iterator();
        
        String[] groupItems = GroupKey2.parseKey(groupKey);
        
        while (watchClientIterator.hasNext()) {
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
