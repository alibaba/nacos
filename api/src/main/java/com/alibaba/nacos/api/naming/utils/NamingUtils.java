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

package com.alibaba.nacos.api.naming.utils;

import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.utils.StringUtils;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import static com.alibaba.nacos.api.common.Constants.CLUSTER_NAME_PATTERN_STRING;
import static com.alibaba.nacos.api.common.Constants.NUMBER_PATTERN_STRING;

/**
 * NamingUtils.
 *
 * @author nkorange
 * @since 1.0.0
 */
public class NamingUtils {
    
    private static final Pattern CLUSTER_NAME_PATTERN = Pattern.compile(CLUSTER_NAME_PATTERN_STRING);
    
    private static final Pattern NUMBER_PATTERN = Pattern.compile(NUMBER_PATTERN_STRING);
    
    /**
     * Returns a combined string with serviceName and groupName. serviceName can not be nil.
     *
     * <p>In most cases, serviceName can not be nil. In other cases, for search or anything, See {@link
     * com.alibaba.nacos.api.naming.utils.NamingUtils#getGroupedNameOptional(String, String)}
     *
     * <p>etc:
     * <p>serviceName | groupName | result</p>
     * <p>serviceA    | groupA    | groupA@@serviceA</p>
     * <p>nil         | groupA    | threw IllegalArgumentException</p>
     *
     * @return 'groupName@@serviceName'
     */
    public static String getGroupedName(final String serviceName, final String groupName) {
        if (StringUtils.isBlank(serviceName)) {
            throw new IllegalArgumentException("Param 'serviceName' is illegal, serviceName is blank");
        }
        if (StringUtils.isBlank(groupName)) {
            throw new IllegalArgumentException("Param 'groupName' is illegal, groupName is blank");
        }
        final String resultGroupedName = groupName + Constants.SERVICE_INFO_SPLITER + serviceName;
        return resultGroupedName.intern();
    }
    
    public static String getServiceName(final String serviceNameWithGroup) {
        if (StringUtils.isBlank(serviceNameWithGroup)) {
            return StringUtils.EMPTY;
        }
        if (!serviceNameWithGroup.contains(Constants.SERVICE_INFO_SPLITER)) {
            return serviceNameWithGroup;
        }
        return serviceNameWithGroup.split(Constants.SERVICE_INFO_SPLITER)[1];
    }
    
    public static String getGroupName(final String serviceNameWithGroup) {
        if (StringUtils.isBlank(serviceNameWithGroup)) {
            return StringUtils.EMPTY;
        }
        if (!serviceNameWithGroup.contains(Constants.SERVICE_INFO_SPLITER)) {
            return Constants.DEFAULT_GROUP;
        }
        return serviceNameWithGroup.split(Constants.SERVICE_INFO_SPLITER)[0];
    }
    
    /**
     * check combineServiceName format. the serviceName can't be blank.
     * <pre>
     * serviceName = "@@";                 the length = 0; illegal
     * serviceName = "group@@";            the length = 1; illegal
     * serviceName = "@@serviceName";      the length = 2; illegal
     * serviceName = "group@@serviceName"; the length = 2; legal
     * </pre>
     *
     * @param combineServiceName such as: groupName@@serviceName
     */
    public static void checkServiceNameFormat(String combineServiceName) {
        String[] split = combineServiceName.split(Constants.SERVICE_INFO_SPLITER);
        if (split.length <= 1) {
            throw new IllegalArgumentException(
                    "Param 'serviceName' is illegal, it should be format as 'groupName@@serviceName'");
        }
        if (split[0].isEmpty()) {
            throw new IllegalArgumentException("Param 'serviceName' is illegal, groupName can't be empty");
        }
    }
    
    /**
     * Returns a combined string with serviceName and groupName. Such as 'groupName@@serviceName'
     * <p>This method works similar with {@link com.alibaba.nacos.api.naming.utils.NamingUtils#getGroupedName} But not
     * verify any parameters.
     *
     * </p> etc:
     * <p>serviceName | groupName | result</p>
     * <p>serviceA    | groupA    | groupA@@serviceA</p>
     * <p>nil         | groupA    | groupA@@</p>
     * <p>nil         | nil       | @@</p>
     *
     * @return 'groupName@@serviceName'
     */
    public static String getGroupedNameOptional(final String serviceName, final String groupName) {
        return groupName + Constants.SERVICE_INFO_SPLITER + serviceName;
    }
    
    /**
     * <p>Check instance param about keep alive.</p>
     *
     * <pre>
     * heart beat timeout must > heart beat interval
     * ip delete timeout must  > heart beat interval
     * </pre>
     *
     * @param instance need checked instance
     * @throws NacosException if check failed, throw exception
     */
    public static void checkInstanceIsLegal(Instance instance) throws NacosException {
        if (instance.getInstanceHeartBeatTimeOut() < instance.getInstanceHeartBeatInterval()
                || instance.getIpDeleteTimeout() < instance.getInstanceHeartBeatInterval()) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.INSTANCE_ERROR,
                    "Instance 'heart beat interval' must less than 'heart beat timeout' and 'ip delete timeout'.");
        }
        if (!StringUtils.isEmpty(instance.getClusterName()) && !CLUSTER_NAME_PATTERN.matcher(instance.getClusterName()).matches()) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.INSTANCE_ERROR,
                    String.format("Instance 'clusterName' should be characters with only 0-9a-zA-Z-. (current: %s)",
                            instance.getClusterName()));
        }
    }
    
    /**
     * check batch register is Ephemeral.
     * @param instance instance
     * @throws NacosException NacosException
     */
    public static void checkInstanceIsEphemeral(Instance instance) throws NacosException {
        if (!instance.isEphemeral()) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.INSTANCE_ERROR,
                    String.format("Batch registration does not allow persistent instance registration , Instance：%s", instance));
        }
    }
    
    /**
     * Batch verify the validity of instances.
     * @param instances List of instances to be registered
     * @throws NacosException Nacos
     */
    public static void batchCheckInstanceIsLegal(List<Instance> instances) throws NacosException {
        Set<Instance> newInstanceSet = new HashSet<>(instances);
        for (Instance instance : newInstanceSet) {
            checkInstanceIsEphemeral(instance);
            checkInstanceIsLegal(instance);
        }
    }
    
    public static String getPatternWithNamespace(final String namespaceId, final String groupedPattern) {
        if (StringUtils.isBlank(namespaceId)) {
            throw new IllegalArgumentException("Param 'namespaceId' is illegal, namespaceId is blank");
        }
        if (StringUtils.isBlank(groupedPattern)) {
            throw new IllegalArgumentException("Param 'groupedPattern' is illegal, groupedPattern is blank");
        }
        final String resultGroupedPattern = namespaceId + Constants.NAMESPACE_ID_SPLITER + groupedPattern;
        return resultGroupedPattern.intern();
    }
    
    public static String getNamespaceFromPattern(String completedPattern) {
        if (StringUtils.isBlank(completedPattern)) {
            return StringUtils.EMPTY;
        }
        if (!completedPattern.contains(Constants.NAMESPACE_ID_SPLITER)) {
            return Constants.DEFAULT_NAMESPACE_ID;
        }
        return completedPattern.split(Constants.NAMESPACE_ID_SPLITER)[0];
    }
    
    public static String getPatternRemovedNamespace(String completedPattern) {
        if (StringUtils.isBlank(completedPattern)) {
            return StringUtils.EMPTY;
        }
        if (!completedPattern.contains(Constants.NAMESPACE_ID_SPLITER)) {
            return completedPattern;
        }
        return completedPattern.split(Constants.NAMESPACE_ID_SPLITER)[1];
    }
    
    /**
     * Get the Pattern subscribed to under this NamespaceId.
     * @param namespaceId name space id
     * @param completedPattern a set of all watch pattern(with namespace id)
     * @return filtered pattern set
     */
    public static Set<String> filterPatternWithNamespace(String namespaceId, Set<String> completedPattern) {
        Set<String> patterns = new HashSet<>();
        for (String each : completedPattern) {
            String eachId = getNamespaceFromPattern(each);
            if (namespaceId.equals(eachId)) {
                patterns.add(getPatternRemovedNamespace(each));
            }
        }
        return patterns;
    }
    
    /**
     * Returns a combined string with matchPattern and matchType.
     * @param matchPattern a match pattern. Such as a 'serviceNamePrefix'
     * @param matchType The match type want to use
     * @return 'matchPattern##matchType'
     */
    public static String getGroupedPattern(final String matchPattern, final String matchType) {
        if (StringUtils.isBlank(matchPattern) && !matchType.equals(Constants.WatchMatchRule.MATCH_ALL)) {
            throw new IllegalArgumentException("Param 'matchPattern' is illegal, matchPattern is blank");
        }
        if (StringUtils.isBlank(matchType)) {
            throw new IllegalArgumentException("Param 'matchType' is illegal, matchType is blank");
        } else if (matchType.equals(Constants.WatchMatchRule.MATCH_ALL)) {
            return Constants.WatchMatchRule.MATCH_ALL;
        }
        final String resultGroupedName = matchPattern + Constants.MATCH_PATTERN_SPLITER + matchType;
        return resultGroupedName.intern();
    }
    
    /**
     *  Given a Pattern, return the string to be used for the match.
     * @param groupedPattern a grouped pattern (match string ## match type)
     * @return the string to be used for the match.
     */
    public static String getMatchName(String groupedPattern) {
        if (StringUtils.isBlank(groupedPattern)) {
            return StringUtils.EMPTY;
        }
        if (!groupedPattern.contains(Constants.MATCH_PATTERN_SPLITER)) {
            return groupedPattern;
        }
        return groupedPattern.split(Constants.MATCH_PATTERN_SPLITER)[0];
    }
    
    /**
     * Given a Pattern, return the matching rule type.
     * @param groupedPattern a grouped pattern (match string ## match type)
     * @return the matching rule type.
     */
    public static String getMatchRule(String groupedPattern) {
        if (StringUtils.isBlank(groupedPattern)) {
            return StringUtils.EMPTY;
        }
        if (!groupedPattern.contains(Constants.MATCH_PATTERN_SPLITER)) {
            return Constants.WatchMatchRule.MATCH_ALL;
        }
        return groupedPattern.split(Constants.MATCH_PATTERN_SPLITER)[1];
    }
    
    /**
     * Given a service, and a list of watched patterns, return the patterns that the service can match.
     *
     * @param serviceName service Name
     * @param groupName group Name
     * @param watchPattern a list of completed watch patterns
     * @return the patterns list that the service can match.
     */
    public static Set<String> getServiceMatchedPatterns(String serviceName, String groupName, Collection<String> watchPattern) {
        if (watchPattern == null || watchPattern.isEmpty()) {
            return new HashSet<>(1);
        }
        Set<String> matchedPatternList = new HashSet<>();
        for (String eachPattern : watchPattern) {
            if (isMatchPattern(serviceName, groupName, getServiceName(eachPattern), getGroupName(eachPattern))) {
                matchedPatternList.add(eachPattern);
            }
        }
        return matchedPatternList;
    }
    
    /**
     * Given a list of service's name, and a pattern to watch, return the services that can match the pattern.
     *
     * @param servicesList a list of service's name
     * @param serviceNamePattern service name Pattern
     * @param groupNamePattern group name Pattern
     * @return the patterns list that the service can match.
     */
    public static Set<String> getPatternMatchedServices(Collection<String> servicesList, String serviceNamePattern,
            String groupNamePattern) {
        if (servicesList == null || servicesList.isEmpty()) {
            return new HashSet<>(1);
        }
        Set<String> matchList = new HashSet<>();
        for (String eachService : servicesList) {
            if (isMatchPattern(getServiceName(eachService), getGroupName(eachService), serviceNamePattern, groupNamePattern)) {
                matchList.add(eachService);
            }
        }
        return matchList;
    }
    
    /**
     * Given a service name and a pattern to match, determine whether it can match.
     *
     * @param serviceName service name to judge
     * @param groupName group name to judge
     * @param serviceNamePattern service name Pattern
     * @param groupNamePattern group name Pattern
     * @return matching result
     */
    public static boolean isMatchPattern(String serviceName, String groupName, String serviceNamePattern, String groupNamePattern) {
        String serviceMatchName = getMatchName(serviceNamePattern);
        String serviceMatchType = getMatchRule(serviceNamePattern);
        // Only support prefix match or all match right now
        // Only support fixed group name right now
        if (serviceMatchType.equals(Constants.WatchMatchRule.MATCH_ALL)) {
            return groupName.equals(groupNamePattern);
        } else if (serviceMatchType.equals(Constants.WatchMatchRule.MATCH_PREFIX)) {
            return prefixMatchWithFixedGroupName(serviceName, serviceMatchName, groupName, getMatchName(groupNamePattern));
        }
        return false;
    }
    
    private static boolean prefixMatchWithFixedGroupName(String serviceName, String serviceNamePrefix, String groupName, String fixedGroupName) {
        return groupName.equals(fixedGroupName) && serviceName.startsWith(serviceNamePrefix);
    }
    
    /**
     * Check string is a number or not.
     *
     * @param str a string of digits
     * @return if it is a string of digits, return true
     */
    public static boolean isNumber(String str) {
        return !StringUtils.isEmpty(str) && NUMBER_PATTERN.matcher(str).matches();
    }
}
