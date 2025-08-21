/*
 * Copyright 1999-2023 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.common.utils;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.alibaba.nacos.api.common.Constants.ALL_PATTERN;
import static com.alibaba.nacos.api.common.Constants.DEFAULT_NAMESPACE_ID;
import static com.alibaba.nacos.api.common.Constants.FUZZY_WATCH_PATTERN_SPLITTER;

/**
 * Utility class for matching group keys against a given pattern.
 *
 * <p>This class provides methods to match group keys based on a pattern specified. It supports matching based on
 * dataId, group, and namespace components of the group key.
 *
 * @author stone-98
 * @date 2024/3/14
 */
public class FuzzyGroupKeyPattern {
    
    /**
     * Generates a fuzzy listen group key pattern based on the given dataId pattern, group, and optional tenant. pattern
     * result as: fixNamespace>>groupPattern>>dataIdPattern
     *
     * @param resourcePattern The pattern for matching dataIds or service names.
     * @param groupPattern    The groupPattern associated with the groups.
     * @param fixNamespace    (Optional) The tenant associated with the dataIds (can be null or empty).
     * @return A unique group key pattern for fuzzy listen.
     * @throws IllegalArgumentException If the dataId pattern or group is blank.
     */
    public static String generatePattern(final String resourcePattern, final String groupPattern, String fixNamespace) {
        if (StringUtils.isBlank(resourcePattern)) {
            throw new IllegalArgumentException("Param 'resourcePattern' is illegal, resourcePattern is blank");
        }
        if (StringUtils.isBlank(groupPattern)) {
            throw new IllegalArgumentException("Param 'groupPattern' is illegal, group is blank");
        }
        if (StringUtils.isBlank(fixNamespace)) {
            fixNamespace = DEFAULT_NAMESPACE_ID;
        }
        StringBuilder sb = new StringBuilder();
        sb.append(fixNamespace);
        sb.append(FUZZY_WATCH_PATTERN_SPLITTER);
        sb.append(groupPattern);
        sb.append(FUZZY_WATCH_PATTERN_SPLITTER);
        sb.append(resourcePattern);
        return sb.toString().intern();
    }
    
    /**
     * Given a dataId, group, and a collection of completed group key patterns, returns the patterns that match.
     *
     * @param resourceName     The dataId or service name to match.
     * @param group            The group to match.
     * @param namespace        The group to match.
     * @param groupKeyPatterns The collection of completed group key patterns to match against.
     * @return A set of patterns that match the dataId and group.
     */
    public static Set<String> filterMatchedPatterns(Collection<String> groupKeyPatterns, String resourceName,
            String group, String namespace) {
        if (CollectionUtils.isEmpty(groupKeyPatterns)) {
            return new HashSet<>(1);
        }
        Set<String> matchedPatternList = new HashSet<>();
        for (String keyPattern : groupKeyPatterns) {
            if (matchPattern(keyPattern, resourceName, group, namespace)) {
                matchedPatternList.add(keyPattern);
            }
        }
        return matchedPatternList;
    }
    
    /**
     * check if the resource match the groupKeyPattern.
     * @param resourceName     The dataId or service name to match.
     * @param group            The group to match.
     * @param namespace        The group to match.
     * @param groupKeyPattern  The pattern to match.
     * @return  matched or not.
     */
    public static boolean matchPattern(String groupKeyPattern, String resourceName, String group, String namespace) {
        if (StringUtils.isBlank(namespace)) {
            namespace = DEFAULT_NAMESPACE_ID;
        }
        String[] splitPatterns = groupKeyPattern.split(FUZZY_WATCH_PATTERN_SPLITTER);
        return splitPatterns[0].equals(namespace) && itemMatched(splitPatterns[1], group) && itemMatched(
                splitPatterns[2], resourceName);
    }
    
    public static String getNamespaceFromPattern(String groupKeyPattern) {
        return groupKeyPattern.split(FUZZY_WATCH_PATTERN_SPLITTER)[0];
    }
    
    /**
     * check pattern matched the resource.
     * @param pattern pattern contain *.
     * @param resource resource to check.
     * @return
     */
    private static boolean itemMatched(String pattern, String resource) {
        
        //accurate match without *
        if (!pattern.contains(ALL_PATTERN)) {
            return pattern.equals(resource);
        }
        
        //match for '*' pattern
        if (pattern.equals(ALL_PATTERN)) {
            return true;
        }
        
        //match for *{string}*
        if (pattern.startsWith(ALL_PATTERN) && pattern.endsWith(ALL_PATTERN)) {
            String pureString = pattern.replace(ALL_PATTERN, "");
            return resource.contains(pureString);
        }
        
        //match for postfix match *{string}
        if (pattern.startsWith(ALL_PATTERN)) {
            String pureString = pattern.replace(ALL_PATTERN, "");
            return resource.endsWith(pureString);
        }
        
        //match for prefix match {string}*
        if (pattern.endsWith(ALL_PATTERN)) {
            String pureString = pattern.replace(ALL_PATTERN, "");
            return resource.startsWith(pureString);
        }
        
        return false;
    }
    
    /**
     * Calculates and merges the differences between the matched group keys and the client's existing group keys into a
     * list of ConfigState objects.
     *
     * @param basedGroupKeys    The matched group keys set
     * @param followedGroupKeys The followed existing group keys set
     * @return a different list of GroupKeyState objects representing the states which the followed sets should be added
     * or removed GroupKeyState#exist true presents follow set should add,GroupKeyState#exist false presents follow set
     * should removed.
     */
    public static List<GroupKeyState> diffGroupKeys(Set<String> basedGroupKeys, Set<String> followedGroupKeys) {
        // Calculate the set of group keys to be added and removed
        Set<String> addGroupKeys = new HashSet<>();
        if (CollectionUtils.isNotEmpty(basedGroupKeys)) {
            addGroupKeys.addAll(basedGroupKeys);
        }
        if (CollectionUtils.isNotEmpty(followedGroupKeys)) {
            addGroupKeys.removeAll(followedGroupKeys);
        }
        
        Set<String> removeGroupKeys = new HashSet<>();
        if (CollectionUtils.isNotEmpty(followedGroupKeys)) {
            removeGroupKeys.addAll(followedGroupKeys);
        }
        if (CollectionUtils.isNotEmpty(basedGroupKeys)) {
            removeGroupKeys.removeAll(basedGroupKeys);
        }
        
        // Convert the group keys to be added and removed into corresponding ConfigState objects and merge them into a list
        return Stream.concat(addGroupKeys.stream().map(groupKey -> new GroupKeyState(groupKey, true)),
                        removeGroupKeys.stream().map(groupKey -> new GroupKeyState(groupKey, false)))
                .collect(Collectors.toList());
    }
    
    public static class GroupKeyState {
        
        String groupKey;
        
        boolean exist;
        
        /**
         * Constructs a new ConfigState instance with the given group key and existence flag.
         *
         * @param groupKey The group key associated with the configuration.
         * @param exist    {@code true} if the configuration exists, {@code false} otherwise.
         */
        public GroupKeyState(String groupKey, boolean exist) {
            this.groupKey = groupKey;
            this.exist = exist;
        }
        
        /**
         * Retrieves the group key associated with the configuration.
         *
         * @return The group key.
         */
        public String getGroupKey() {
            return groupKey;
        }
        
        /**
         * Sets the group key associated with the configuration.
         *
         * @param groupKey The group key to set.
         */
        public void setGroupKey(String groupKey) {
            this.groupKey = groupKey;
        }
        
        /**
         * Checks whether the configuration exists or not.
         *
         * @return {@code true} if the configuration exists, {@code false} otherwise.
         */
        public boolean isExist() {
            return exist;
        }
        
        /**
         * Sets the existence flag of the configuration.
         *
         * @param exist {@code true} if the configuration exists, {@code false} otherwise.
         */
        public void setExist(boolean exist) {
            this.exist = exist;
        }
    }
}
