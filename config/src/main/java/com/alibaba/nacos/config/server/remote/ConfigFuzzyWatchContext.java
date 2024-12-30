package com.alibaba.nacos.config.server.remote;


import com.alibaba.nacos.common.utils.CollectionUtils;
import com.alibaba.nacos.common.utils.FuzzyGroupKeyPattern;
import com.alibaba.nacos.config.server.utils.GroupKey2;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ConfigFuzzyWatchContext {
    
    /**
     * groupKeyPattern -> connection set.
     */
    private final Map<String, Set<String>> keyPatternContext = new ConcurrentHashMap<>();
    
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
    
    
    public void removeFuzzyWatchContext(String connectionId){
        for (Map.Entry<String, Set<String>> keyPatternContextEntry : keyPatternContext.entrySet()) {
            String keyPattern = keyPatternContextEntry.getKey();
            Set<String> connectionIds = keyPatternContextEntry.getValue();
            if (CollectionUtils.isEmpty(connectionIds)) {
                keyPatternContext.remove(keyPattern);
            } else {
                connectionIds.remove(connectionId);
                if (CollectionUtils.isEmpty(connectionIds)) {
                    keyPatternContext.remove(keyPattern);
                }
            }
        }
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
            String[] strings = GroupKey2.parseKey(groupKey);
            
            if (FuzzyGroupKeyPattern.matchPattern(groupKey,strings[0],strings[1],strings[2])) {
                // If matched, add the associated connection IDs to the set
                Set<String> connectIdSet = keyPatternContext.get(keyPattern);
                if (CollectionUtils.isNotEmpty(connectIdSet)) {
                    connectIds.addAll(connectIdSet);
                }
            }
        }
        return connectIds;
    }
    
}
