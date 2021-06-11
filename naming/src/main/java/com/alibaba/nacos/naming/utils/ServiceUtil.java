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

package com.alibaba.nacos.naming.utils;

import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.naming.pojo.ServiceInfo;
import com.alibaba.nacos.api.selector.SelectorType;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.naming.core.Instance;
import com.alibaba.nacos.naming.core.Service;
import com.alibaba.nacos.naming.core.v2.metadata.ServiceMetadata;
import com.alibaba.nacos.naming.misc.Loggers;
import com.fasterxml.jackson.databind.JsonNode;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Service util.
 *
 * @author xiweng.yy
 */
public class ServiceUtil {
    
    /**
     * Select service name with group name.
     *
     * @param services  service map
     * @param groupName group name
     * @return service names with group name
     */
    public static Map<String, Service> selectServiceWithGroupName(Map<String, Service> services, String groupName) {
        if (null == services || services.isEmpty()) {
            return new HashMap<>(0);
        }
        Map<String, Service> result = new HashMap<>(services.size());
        String groupKey = groupName + Constants.SERVICE_INFO_SPLITER;
        for (Map.Entry<String, Service> each : services.entrySet()) {
            if (each.getKey().startsWith(groupKey)) {
                result.put(each.getKey(), each.getValue());
            }
        }
        return result;
    }
    
    /**
     * Select service name by selector.
     *
     * @param serviceMap     service name list
     * @param selectorString selector serialize string
     * @return service names filter by group name
     */
    public static Map<String, Service> selectServiceBySelector(Map<String, Service> serviceMap, String selectorString) {
        Map<String, Service> result = serviceMap;
        if (StringUtils.isNotBlank(selectorString)) {
            
            JsonNode selectorJson = JacksonUtils.toObj(selectorString);
            
            SelectorType selectorType = SelectorType.valueOf(selectorJson.get("type").asText());
            String expression = selectorJson.get("expression").asText();
            
            if (SelectorType.label.equals(selectorType) && StringUtils.isNotBlank(expression)) {
                expression = StringUtils.deleteWhitespace(expression);
                // Now we only support the following expression:
                // INSTANCE.metadata.xxx = 'yyy' or
                // SERVICE.metadata.xxx = 'yyy'
                String[] terms = expression.split("=");
                String[] factors = terms[0].split("\\.");
                switch (factors[0]) {
                    case "INSTANCE":
                        result = filterInstanceMetadata(serviceMap, factors[factors.length - 1],
                                terms[1].replace("'", ""));
                        break;
                    case "SERVICE":
                        result = filterServiceMetadata(serviceMap, factors[factors.length - 1],
                                terms[1].replace("'", ""));
                        break;
                    default:
                        break;
                }
            }
        }
        return result;
    }
    
    private static Map<String, Service> filterInstanceMetadata(Map<String, Service> serviceMap, String key,
            String value) {
        Map<String, Service> result = new HashMap<>(serviceMap.size());
        for (Map.Entry<String, Service> each : serviceMap.entrySet()) {
            for (Instance address : each.getValue().allIPs()) {
                if (address.getMetadata() != null && value.equals(address.getMetadata().get(key))) {
                    result.put(each.getKey(), each.getValue());
                    break;
                }
            }
        }
        return result;
    }
    
    private static Map<String, Service> filterServiceMetadata(Map<String, Service> serviceMap, String key,
            String value) {
        Map<String, Service> result = new HashMap<>(serviceMap.size());
        for (Map.Entry<String, Service> each : serviceMap.entrySet()) {
            if (value.equals(each.getValue().getMetadata().get(key))) {
                result.put(each.getKey(), each.getValue());
            }
        }
        return result;
    }
    
    /**
     * Page service name.
     *
     * @param pageNo     page number
     * @param pageSize   size per page
     * @param serviceMap service source map
     * @return service name list by paged
     */
    public static List<String> pageServiceName(int pageNo, int pageSize, Map<String, Service> serviceMap) {
        return pageServiceName(pageNo, pageSize, serviceMap.keySet());
    }
    
    /**
     * Page service name.
     *
     * @param pageNo         page number
     * @param pageSize       size per page
     * @param serviceNameSet service name set
     * @return service name list by paged
     */
    public static List<String> pageServiceName(int pageNo, int pageSize, Collection<String> serviceNameSet) {
        List<String> result = new ArrayList<>(serviceNameSet);
        int start = (pageNo - 1) * pageSize;
        if (start < 0) {
            start = 0;
        }
        if (start >= result.size()) {
            return Collections.emptyList();
        }
        int end = start + pageSize;
        if (end > result.size()) {
            end = result.size();
        }
        for (int i = start; i < end; i++) {
            String serviceName = result.get(i);
            int indexOfSplitter = serviceName.indexOf(Constants.SERVICE_INFO_SPLITER);
            if (indexOfSplitter > 0) {
                serviceName = serviceName.substring(indexOfSplitter + 2);
            }
            result.set(i, serviceName);
        }
        return result.subList(start, end);
    }
    
    /**
     * Select healthy instance of service info.
     *
     * @param serviceInfo original service info
     * @return new service info
     */
    public static ServiceInfo selectHealthyInstances(ServiceInfo serviceInfo) {
        return selectInstances(serviceInfo, true, false);
    }
    
    /**
     * Select healthy instance of service info.
     *
     * @param serviceInfo original service info
     * @return new service info
     */
    public static ServiceInfo selectEnabledInstances(ServiceInfo serviceInfo) {
        return selectInstances(serviceInfo, false, true);
    }
    
    /**
     * Select instance of service info.
     *
     * @param serviceInfo original service info
     * @param cluster     cluster of instances
     * @return new service info
     */
    public static ServiceInfo selectInstances(ServiceInfo serviceInfo, String cluster) {
        return selectInstances(serviceInfo, cluster, false, false);
    }
    
    /**
     * Select instance of service info.
     *
     * @param serviceInfo original service info
     * @param healthyOnly whether only select instance which healthy
     * @param enableOnly  whether only select instance which enabled
     * @return new service info
     */
    public static ServiceInfo selectInstances(ServiceInfo serviceInfo, boolean healthyOnly, boolean enableOnly) {
        return selectInstances(serviceInfo, StringUtils.EMPTY, healthyOnly, enableOnly);
    }
    
    /**
     * Select instance of service info.
     *
     * @param serviceInfo original service info
     * @param cluster     cluster of instances
     * @param healthyOnly whether only select instance which healthy
     * @return new service info
     */
    public static ServiceInfo selectInstances(ServiceInfo serviceInfo, String cluster, boolean healthyOnly) {
        return selectInstances(serviceInfo, cluster, healthyOnly, false);
    }
    
    /**
     * Select instance of service info.
     *
     * @param serviceInfo original service info
     * @param cluster     cluster of instances
     * @param healthyOnly whether only select instance which healthy
     * @param enableOnly  whether only select instance which enabled
     * @return new service info
     */
    public static ServiceInfo selectInstances(ServiceInfo serviceInfo, String cluster, boolean healthyOnly,
            boolean enableOnly) {
        return doSelectInstances(serviceInfo, cluster, healthyOnly, enableOnly, null);
    }
    
    /**
     * Select instance of service info with healthy protection.
     *
     * @param serviceInfo     original service info
     * @param serviceMetadata service meta info
     * @param cluster         cluster of instances
     * @return new service info
     */
    public static ServiceInfo selectInstancesWithHealthyProtection(ServiceInfo serviceInfo,
                                                                   ServiceMetadata serviceMetadata,
                                                                   String cluster) {
        return selectInstancesWithHealthyProtection(serviceInfo, serviceMetadata, cluster, false, false);
    }

    /**
     * Select instance of service info with healthy protection.
     *
     * @param serviceInfo     original service info
     * @param serviceMetadata service meta info
     * @param healthyOnly     whether only select instance which healthy
     * @param enableOnly      whether only select instance which enabled
     * @return new service info
     */
    public static ServiceInfo selectInstancesWithHealthyProtection(ServiceInfo serviceInfo,
                                                                   ServiceMetadata serviceMetadata,
                                                                   boolean healthyOnly,
                                                                   boolean enableOnly) {
        return selectInstancesWithHealthyProtection(serviceInfo, serviceMetadata, StringUtils.EMPTY, healthyOnly, enableOnly);
    }

    /**
     * Select instance of service info with healthy protection.
     *
     * @param serviceInfo     original service info
     * @param serviceMetadata service meta info
     * @param cluster         cluster of instances
     * @param healthyOnly     whether only select instance which healthy
     * @param enableOnly      whether only select instance which enabled
     * @return new service info
     */
    public static ServiceInfo selectInstancesWithHealthyProtection(ServiceInfo serviceInfo,
                                                                   ServiceMetadata serviceMetadata,
                                                                   String cluster,
                                                                   boolean healthyOnly,
                                                                   boolean enableOnly) {
        InstancesFilter filter = (filteredResult, allInstances, healthyCount) -> {
            if (serviceMetadata == null) {
                return;
            }
            // TODO: filter ips using selector
            float threshold = serviceMetadata.getProtectThreshold();
            if (threshold < 0) {
                threshold = 0F;
            }
            if ((float) healthyCount / allInstances.size() <= threshold) {
                Loggers.SRV_LOG.warn("protect threshold reached, return all ips, service: {}", filteredResult.getName());
                filteredResult.setReachProtectionThreshold(true);
                List<com.alibaba.nacos.api.naming.pojo.Instance> filteredInstances = allInstances.stream()
                        .map(i -> {
                            if (!i.isHealthy()) {
                                i = InstanceUtil.deepCopy(i);
                                // set all to `healthy` state to protect
                                i.setHealthy(true);
                            } // else deepcopy is unnecessary
                            return i;
                        })
                        .collect(Collectors.toCollection(LinkedList::new));
                filteredResult.setHosts(filteredInstances);
            }
        };
        return doSelectInstances(serviceInfo, cluster, healthyOnly, enableOnly, filter);
    }

    /**
     * Select instance of service info.
     *
     * @param serviceInfo original service info
     * @param cluster     cluster of instances
     * @param healthyOnly whether only select instance which healthy
     * @param enableOnly  whether only select instance which enabled
     * @param filter      do some other filter operation
     * @return new service info
     */
    private static ServiceInfo doSelectInstances(ServiceInfo serviceInfo, String cluster,
                                                 boolean healthyOnly, boolean enableOnly,
                                                 InstancesFilter filter) {
        ServiceInfo result = new ServiceInfo();
        result.setName(serviceInfo.getName());
        result.setGroupName(serviceInfo.getGroupName());
        result.setCacheMillis(serviceInfo.getCacheMillis());
        result.setLastRefTime(System.currentTimeMillis());
        result.setClusters(cluster);
        result.setReachProtectionThreshold(false);
        Set<String> clusterSets = com.alibaba.nacos.common.utils.StringUtils.isNotBlank(cluster) ? new HashSet<>(
                Arrays.asList(cluster.split(","))) : new HashSet<>();
        long healthyCount = 0L;
        // The instance list won't be modified almost time.
        List<com.alibaba.nacos.api.naming.pojo.Instance> filteredInstances = new LinkedList<>();
        // The instance list of all filtered by cluster/enabled condition.
        List<com.alibaba.nacos.api.naming.pojo.Instance> allInstances = new LinkedList<>();
        for (com.alibaba.nacos.api.naming.pojo.Instance ip : serviceInfo.getHosts()) {
            if (checkCluster(clusterSets, ip) && checkEnabled(enableOnly, ip)) {
                if (!healthyOnly || ip.isHealthy()) {
                    filteredInstances.add(ip);
                }
                if (ip.isHealthy()) {
                    healthyCount += 1;
                }
                allInstances.add(ip);
            }
        }
        result.setHosts(filteredInstances);
        if (filter != null) {
            filter.doFilter(result, allInstances, healthyCount);
        }
        return result;
    }
    
    private static boolean checkCluster(Set<String> clusterSets, com.alibaba.nacos.api.naming.pojo.Instance instance) {
        if (clusterSets.isEmpty()) {
            return true;
        }
        return clusterSets.contains(instance.getClusterName());
    }
    
    private static boolean checkEnabled(boolean enableOnly, com.alibaba.nacos.api.naming.pojo.Instance instance) {
        return !enableOnly || instance.isEnabled();
    }

    private interface InstancesFilter {

        /**
         * Do customized filtering.
         *
         * @param filteredResult result with instances already been filtered cluster/enabled/healthy
         * @param allInstances   all instances filtered by cluster/enabled
         * @param healthyCount   healthy instances count filtered by cluster/enabled
         */
        void doFilter(ServiceInfo filteredResult,
                      List<com.alibaba.nacos.api.naming.pojo.Instance> allInstances,
                      long healthyCount);

    }

}
