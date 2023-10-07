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

package com.alibaba.nacos.client.naming.cache;

import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.naming.pojo.Service;
import com.alibaba.nacos.api.naming.remote.request.AbstractFuzzyWatchNotifyRequest;
import com.alibaba.nacos.api.naming.remote.request.FuzzyWatchNotifyChangeRequest;
import com.alibaba.nacos.api.naming.remote.request.FuzzyWatchNotifyInitRequest;
import com.alibaba.nacos.api.naming.utils.NamingUtils;
import com.alibaba.nacos.client.env.NacosClientProperties;
import com.alibaba.nacos.client.naming.event.FuzzyWatchNotifyEvent;
import com.alibaba.nacos.client.naming.utils.CollectionUtils;
import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.common.utils.ConcurrentHashSet;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Naming client fuzzy watch service list holder.
 *
 * @author tanyongquan
 */
public class FuzzyWatchServiceListHolder {
    
    private String notifierEventScope;
    
    /**
     * The contents of {@code patternMatchMap} are Map{pattern -> Set[matched services]}.
     */
    private Map<String, ConcurrentHashSet<Service>> patternMatchMap = new ConcurrentHashMap<>();
    
    public FuzzyWatchServiceListHolder(String notifierEventScope, NacosClientProperties properties) {
        this.notifierEventScope = notifierEventScope;
    }
    
    /**
     * Publish service change event notify from nacos server about fuzzy watch.
     *
     * @param request watch notify request from Nacos server
     */
    public void processFuzzyWatchNotify(AbstractFuzzyWatchNotifyRequest request) {
        if (request instanceof FuzzyWatchNotifyInitRequest) {
            FuzzyWatchNotifyInitRequest watchNotifyInitRequest = (FuzzyWatchNotifyInitRequest) request;
            Set<Service> matchedServiceSet = patternMatchMap.computeIfAbsent(watchNotifyInitRequest.getPattern(),
                    keyInner -> new ConcurrentHashSet<>());
            Collection<String> servicesName = watchNotifyInitRequest.getServicesName();
            for (String groupedName : servicesName) {
                Service service = new Service(NamingUtils.getServiceName(groupedName), NamingUtils.getGroupName(groupedName));
                // may have a 'change event' sent to client before 'init event'
                if (matchedServiceSet.add(service)) {
                    NotifyCenter.publishEvent(FuzzyWatchNotifyEvent.buildNotifyPatternAllListenersEvent(notifierEventScope,
                            service, watchNotifyInitRequest.getPattern(), Constants.ServiceChangedType.ADD_SERVICE));
                }
            }
        } else if (request instanceof FuzzyWatchNotifyChangeRequest) {
            FuzzyWatchNotifyChangeRequest notifyChangeRequest = (FuzzyWatchNotifyChangeRequest) request;
            Collection<String> matchedPattern = NamingUtils.getServiceMatchedPatterns(notifyChangeRequest.getServiceName(),
                    notifyChangeRequest.getGroupName(),  patternMatchMap.keySet());
            Service service = new Service(notifyChangeRequest.getServiceName(), notifyChangeRequest.getGroupName());
            String serviceChangeType = request.getServiceChangedType();
            
            switch (serviceChangeType) {
                case Constants.ServiceChangedType.ADD_SERVICE:
                case Constants.ServiceChangedType.INSTANCE_CHANGED:
                    for (String pattern : matchedPattern) {
                        Set<Service> matchedServiceSet = patternMatchMap.get(pattern);
                        if (matchedServiceSet != null && matchedServiceSet.add(service)) {
                            NotifyCenter.publishEvent(
                                    FuzzyWatchNotifyEvent.buildNotifyPatternAllListenersEvent(notifierEventScope,
                                            service, pattern, Constants.ServiceChangedType.ADD_SERVICE));
                        }
                    }
                    break;
                case Constants.ServiceChangedType.DELETE_SERVICE:
                    for (String pattern : matchedPattern) {
                        Set<Service> matchedServiceSet = patternMatchMap.get(pattern);
                        if (matchedServiceSet != null && matchedServiceSet.remove(service)) {
                            NotifyCenter.publishEvent(
                                    FuzzyWatchNotifyEvent.buildNotifyPatternAllListenersEvent(notifierEventScope,
                                            service, pattern, Constants.ServiceChangedType.DELETE_SERVICE));
                        }
                    }
                    break;
                default:
                    break;
            }
        }
    }
    
    /**
     * For a duplicate fuzzy watch of a certain pattern, initiate an initialization event to the corresponding Listener.
     *
     * @param serviceNamePattern service name pattern.
     * @param groupNamePattern group name pattern.
     * @param uuid The UUID that identifies the Listener.
     */
    public void duplicateFuzzyWatchInit(String serviceNamePattern, String groupNamePattern, String uuid) {
        String pattern = NamingUtils.getGroupedName(serviceNamePattern, groupNamePattern);
        Collection<Service> cacheServices = patternMatchMap.get(pattern);
        if (cacheServices == null) {
            return;
        }
        for (Service service : cacheServices) {
            NotifyCenter.publishEvent(
                    FuzzyWatchNotifyEvent.buildNotifyPatternSpecificListenerEvent(notifierEventScope, service,
                    pattern, uuid, Constants.ServiceChangedType.ADD_SERVICE));
        }
    }
    
    public boolean containsPatternMatchCache(String serviceNamePattern, String groupNamePattern) {
        String pattern = NamingUtils.getGroupedName(serviceNamePattern, groupNamePattern);
        return CollectionUtils.isEmpty(patternMatchMap.get(pattern));
    }
    
    public void removePatternMatchCache(String serviceNamePattern, String groupNamePattern) {
        String pattern = NamingUtils.getGroupedName(serviceNamePattern, groupNamePattern);
        patternMatchMap.remove(pattern);
    }
    
    public void addPatternMatchCache(String serviceNamePattern, String groupNamePattern) {
        String pattern = NamingUtils.getGroupedName(serviceNamePattern, groupNamePattern);
        patternMatchMap.computeIfAbsent(pattern, keyInner -> new ConcurrentHashSet<>());
    }
}
