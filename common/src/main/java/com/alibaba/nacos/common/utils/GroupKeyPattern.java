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

import com.alibaba.nacos.api.common.Constants;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import static com.alibaba.nacos.api.common.Constants.DATA_ID_SPLITTER;
import static com.alibaba.nacos.api.common.Constants.FUZZY_LISTEN_PATTERN_WILDCARD;
import static com.alibaba.nacos.api.common.Constants.NAMESPACE_ID_SPLITTER;

/**
 * Utility class for matching group keys against a given pattern.
 *
 * <p>This class provides methods to match group keys based on a pattern specified. It supports matching based on
 * dataId, group, and namespace components of the group key.
 *
 * @author stone-98
 * @date 2024/3/14
 */
public class GroupKeyPattern {
    
    /**
     * Generates a fuzzy listen group key pattern based on the given dataId pattern, group, and optional tenant.
     *
     * <p>This method generates a unique group key pattern for fuzzy listen based on the specified dataId pattern,
     * group, and optional tenant. It concatenates the dataId pattern, group, and tenant (if provided) with a delimiter
     * and returns the resulting string. The resulting string is interned to improve memory efficiency.
     *
     * @param dataIdPattern The pattern for matching dataIds.
     * @param group         The group associated with the dataIds.
     * @param namespace     (Optional) The tenant associated with the dataIds (can be null or empty).
     * @return A unique group key pattern for fuzzy listen.
     * @throws IllegalArgumentException If the dataId pattern or group is blank.
     */
    public static String generateFuzzyListenGroupKeyPattern(final String dataIdPattern, final String group,
            final String namespace) {
        if (StringUtils.isBlank(dataIdPattern)) {
            throw new IllegalArgumentException("Param 'dataIdPattern' is illegal, dataIdPattern is blank");
        }
        if (StringUtils.isBlank(group)) {
            throw new IllegalArgumentException("Param 'group' is illegal, group is blank");
        }
        StringBuilder sb = new StringBuilder();
        if (StringUtils.isNotBlank(namespace)) {
            sb.append(namespace);
        }
        sb.append(NAMESPACE_ID_SPLITTER);
        sb.append(group);
        sb.append(DATA_ID_SPLITTER);
        sb.append(dataIdPattern);
        return sb.toString().intern();
    }
    
    /**
     * Generates a fuzzy listen group key pattern based on the given dataId pattern and group.
     *
     * <p>This method generates a unique group key pattern for fuzzy listen based on the specified dataId pattern and
     * group. It concatenates the dataId pattern and group with a delimiter and returns the resulting string. The
     * resulting string is interned to improve memory efficiency.
     *
     * @param dataIdPattern The pattern for matching dataIds.
     * @param group         The group associated with the dataIds.
     * @return A unique group key pattern for fuzzy listen.
     * @throws IllegalArgumentException If the dataId pattern or group is blank.
     */
    public static String generateFuzzyListenGroupKeyPattern(final String dataIdPattern, final String group) {
        if (StringUtils.isBlank(dataIdPattern)) {
            throw new IllegalArgumentException("Param 'dataIdPattern' is illegal, dataIdPattern is blank");
        }
        if (StringUtils.isBlank(group)) {
            throw new IllegalArgumentException("Param 'group' is illegal, group is blank");
        }
        final String fuzzyListenGroupKey = group + DATA_ID_SPLITTER + dataIdPattern;
        return fuzzyListenGroupKey.intern();
    }
    
    /**
     * Checks whether a group key matches the specified pattern.
     *
     * @param groupKey        The group key to match.
     * @param groupKeyPattern The pattern to match against.
     * @return {@code true} if the group key matches the pattern, otherwise {@code false}.
     */
    public static boolean isMatchPatternWithNamespace(String groupKey, String groupKeyPattern) {
        String[] parseKey = GroupKey.parseKey(groupKey);
        String dataId = parseKey[0];
        String group = parseKey[1];
        String namespace = parseKey.length > 2 ? parseKey[2] : Constants.DEFAULT_NAMESPACE_ID;
        
        String namespacePattern = getNamespace(groupKeyPattern);
        String groupPattern = getGroup(groupKeyPattern);
        String dataIdPattern = getDataIdPattern(groupKeyPattern);
        
        if (dataIdPattern.equals(FUZZY_LISTEN_PATTERN_WILDCARD)) {
            return namespace.equals(namespacePattern) && group.equals(groupPattern);
        }
        
        if (dataIdPattern.endsWith(FUZZY_LISTEN_PATTERN_WILDCARD)) {
            String dataIdPrefix = dataIdPattern.substring(0, dataIdPattern.length() - 1);
            return namespace.equals(namespacePattern) && groupPattern.equals(group) && dataId.startsWith(dataIdPrefix);
        }
        
        return namespace.equals(namespacePattern) && group.equals(groupPattern) && dataId.equals(dataIdPattern);
    }
    
    /**
     * Checks whether a group key matches the specified pattern.
     *
     * @param groupKey        The group key to match.
     * @param groupKeyPattern The pattern to match against.
     * @return {@code true} if the group key matches the pattern, otherwise {@code false}.
     */
    public static boolean isMatchPatternWithoutNamespace(String groupKey, String groupKeyPattern) {
        String[] parseKey = GroupKey.parseKey(groupKey);
        String dataId = parseKey[0];
        String group = parseKey[1];
        
        String groupPattern = getGroup(groupKeyPattern);
        String dataIdPattern = getDataIdPattern(groupKeyPattern);
        
        if (dataIdPattern.equals(FUZZY_LISTEN_PATTERN_WILDCARD)) {
            return group.equals(groupPattern);
        }
        
        if (dataIdPattern.endsWith(FUZZY_LISTEN_PATTERN_WILDCARD)) {
            String dataIdPrefix = dataIdPattern.substring(0, dataIdPattern.length() - 1);
            return groupPattern.equals(group) && dataId.startsWith(dataIdPrefix);
        }
        
        return group.equals(groupPattern) && dataId.equals(dataIdPattern);
    }
    
    /**
     * Given a dataId, group, dataId pattern, and group pattern, determines whether it can match.
     *
     * @param dataId        The dataId to match.
     * @param group         The group to match.
     * @param dataIdPattern The dataId pattern to match against.
     * @param groupPattern  The group pattern to match against.
     * @return {@code true} if the dataId and group match the patterns, otherwise {@code false}.
     */
    public static boolean isMatchPatternWithoutNamespace(String dataId, String group, String dataIdPattern,
            String groupPattern) {
        String groupKey = GroupKey.getKey(dataId, group);
        String groupKeyPattern = generateFuzzyListenGroupKeyPattern(dataIdPattern, groupPattern);
        return isMatchPatternWithoutNamespace(groupKey, groupKeyPattern);
    }
    
    /**
     * Given a dataId, group, and a collection of completed group key patterns, returns the patterns that match.
     *
     * @param dataId           The dataId to match.
     * @param group            The group to match.
     * @param groupKeyPatterns The collection of completed group key patterns to match against.
     * @return A set of patterns that match the dataId and group.
     */
    public static Set<String> getConfigMatchedPatternsWithoutNamespace(String dataId, String group,
            Collection<String> groupKeyPatterns) {
        if (CollectionUtils.isEmpty(groupKeyPatterns)) {
            return new HashSet<>(1);
        }
        Set<String> matchedPatternList = new HashSet<>();
        for (String keyPattern : groupKeyPatterns) {
            if (isMatchPatternWithoutNamespace(dataId, group, getDataIdPattern(keyPattern), getGroup(keyPattern))) {
                matchedPatternList.add(keyPattern);
            }
        }
        return matchedPatternList;
    }
    
    /**
     * Extracts the namespace from the given group key pattern.
     *
     * @param groupKeyPattern The group key pattern from which to extract the namespace.
     * @return The namespace extracted from the group key pattern.
     */
    public static String getNamespace(final String groupKeyPattern) {
        if (StringUtils.isBlank(groupKeyPattern)) {
            return StringUtils.EMPTY;
        }
        if (!groupKeyPattern.contains(NAMESPACE_ID_SPLITTER)) {
            return StringUtils.EMPTY;
        }
        return groupKeyPattern.split(NAMESPACE_ID_SPLITTER)[0];
    }
    
    /**
     * Extracts the group from the given group key pattern.
     *
     * @param groupKeyPattern The group key pattern from which to extract the group.
     * @return The group extracted from the group key pattern.
     */
    public static String getGroup(final String groupKeyPattern) {
        if (StringUtils.isBlank(groupKeyPattern)) {
            return StringUtils.EMPTY;
        }
        String groupWithNamespace;
        if (!groupKeyPattern.contains(DATA_ID_SPLITTER)) {
            groupWithNamespace = groupKeyPattern;
        } else {
            groupWithNamespace = groupKeyPattern.split(DATA_ID_SPLITTER)[0];
        }
        
        if (!groupKeyPattern.contains(NAMESPACE_ID_SPLITTER)) {
            return groupWithNamespace;
        }
        return groupWithNamespace.split(NAMESPACE_ID_SPLITTER)[1];
    }
    
    /**
     * Extracts the dataId pattern from the given group key pattern.
     *
     * @param groupKeyPattern The group key pattern from which to extract the dataId pattern.
     * @return The dataId pattern extracted from the group key pattern.
     */
    public static String getDataIdPattern(final String groupKeyPattern) {
        if (StringUtils.isBlank(groupKeyPattern)) {
            return StringUtils.EMPTY;
        }
        if (!groupKeyPattern.contains(DATA_ID_SPLITTER)) {
            return StringUtils.EMPTY;
        }
        return groupKeyPattern.split(DATA_ID_SPLITTER)[1];
    }
    
    /**
     * Given a completed pattern, removes the namespace.
     *
     * @param completedPattern The completed pattern from which to remove the namespace.
     * @return The pattern with the namespace removed.
     */
    public static String getPatternRemovedNamespace(String completedPattern) {
        if (StringUtils.isBlank(completedPattern)) {
            return StringUtils.EMPTY;
        }
        if (!completedPattern.contains(NAMESPACE_ID_SPLITTER)) {
            return completedPattern;
        }
        return completedPattern.split(NAMESPACE_ID_SPLITTER)[1];
    }
}
