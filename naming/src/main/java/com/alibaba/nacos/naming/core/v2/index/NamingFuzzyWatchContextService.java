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

package com.alibaba.nacos.naming.core.v2.index;

import com.alibaba.nacos.api.exception.runtime.NacosRuntimeException;
import com.alibaba.nacos.api.naming.utils.NamingUtils;
import com.alibaba.nacos.common.notify.Event;
import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.common.notify.listener.SmartSubscriber;
import com.alibaba.nacos.common.utils.ConcurrentHashSet;
import com.alibaba.nacos.common.utils.FuzzyGroupKeyPattern;
import com.alibaba.nacos.core.utils.GlobalExecutor;
import com.alibaba.nacos.naming.core.v2.ServiceManager;
import com.alibaba.nacos.naming.core.v2.event.client.ClientOperationEvent;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import com.alibaba.nacos.naming.misc.Loggers;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static com.alibaba.nacos.api.common.Constants.ServiceChangedType.ADD_SERVICE;
import static com.alibaba.nacos.api.common.Constants.ServiceChangedType.DELETE_SERVICE;
import static com.alibaba.nacos.api.model.v2.ErrorCode.FUZZY_WATCH_PATTERN_OVER_LIMIT;
import static com.alibaba.nacos.common.utils.FuzzyGroupKeyPattern.getNamespaceFromPattern;

/**
 * naming fuzzy watch context service
 * <p>
 * 1. handler client fuzzy watch event and cancel watch event 2. service changed notify fuzzy watched clients.
 */
@Component
public class NamingFuzzyWatchContextService extends SmartSubscriber {
    
    /**
     * watched client ids of a pattern,  {fuzzy watch pattern -> Set[watched clientID]}.
     */
    private final ConcurrentMap<String, Set<String>> watchedClients = new ConcurrentHashMap<>();
    
    /**
     * The pattern matched service keys for pattern.{fuzzy watch pattern -> Set[matched service keys]}. initialized a
     * new entry pattern when a client register a new pattern. destroyed a new entry pattern by task when no clients
     * watch pattern in max 30s delay.
     */
    private final ConcurrentMap<String, Set<String>> matchedServiceKeys = new ConcurrentHashMap<>();
    
    private final int FUZZY_WATCH_MAX_PATTERN_COUNT = 50;
    
    private final int FUZZY_WATCH_MAX_PATTERN_MATCHED_GROUP_KEY_COUNT = 200;
    
    
    public NamingFuzzyWatchContextService() {
        GlobalExecutor.scheduleWithFixDelayByCommon(() -> trimFuzzyWatchContext(), 30000);
        NotifyCenter.registerSubscriber(this);
    }
    
    /**
     * trim  fuzzy watch context. <br/> 1.remove watchedClients if watched client is empty. 2.remove matchedServiceKeys
     * if watchedClients is null. pattern matchedServiceKeys will be removed in second period to avoid frequently
     * matchedServiceKeys init.
     */
    private void trimFuzzyWatchContext() {
        try {
            Iterator<Map.Entry<String, Set<String>>> iterator = matchedServiceKeys.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<String, Set<String>> next = iterator.next();
                Set<String> watchedClients = this.watchedClients.get(next.getKey());
                if (watchedClients == null) {
                    iterator.remove();
                } else if (watchedClients.isEmpty()) {
                    this.watchedClients.remove(next.getKey());
                }
            }
        } catch (Throwable throwable) {
            throwable.printStackTrace();
        }
    }
    
    @Override
    public List<Class<? extends Event>> subscribeTypes() {
        List<Class<? extends Event>> result = new LinkedList<>();
        result.add(ClientOperationEvent.ClientReleaseEvent.class);
        return result;
    }
    
    @Override
    public void onEvent(Event event) {
        
        //handle client disconnected event.
        if (event instanceof ClientOperationEvent.ClientReleaseEvent) {
            removeFuzzyWatchContext(((ClientOperationEvent.ClientReleaseEvent) event).getClientId());
        }
    }
    
    
    public Set<String> getFuzzyWatchedClients(Service service) {
        Set<String> matchedClients = new HashSet<>();
        Iterator<Map.Entry<String, Set<String>>> iterator = watchedClients.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Set<String>> entry = iterator.next();
            if (FuzzyGroupKeyPattern.matchPattern(entry.getKey(), service.getName(), service.getGroup(),
                    service.getNamespace())) {
                matchedClients.addAll(entry.getValue());
            }
        }
        return matchedClients;
    }
    
    public boolean syncServiceContext(Service changedEventService, String changedType) {
        
        boolean needNotify = false;
        if (!changedType.equals(ADD_SERVICE) && !changedType.equals(DELETE_SERVICE)) {
            return false;
        }
        
        String serviceKey = NamingUtils.getServiceKey(changedEventService.getNamespace(),
                changedEventService.getGroup(), changedEventService.getName());
        Loggers.SRV_LOG.warn("FUZZY_WATCH:  service change matched,service key {},changed type {} ", serviceKey,
                changedType);
        
        Iterator<Map.Entry<String, Set<String>>> iterator = matchedServiceKeys.entrySet().iterator();
        
        while (iterator.hasNext()) {
            Map.Entry<String, Set<String>> next = iterator.next();
            if (FuzzyGroupKeyPattern.matchPattern(next.getKey(), changedEventService.getName(),
                    changedEventService.getGroup(), changedEventService.getNamespace())) {
                
                Set<String> matchedServiceKeys = next.getValue();
                if (changedType.equals(ADD_SERVICE) && !matchedServiceKeys.contains(serviceKey)) {
                    if (matchedServiceKeys.size() >= FUZZY_WATCH_MAX_PATTERN_MATCHED_GROUP_KEY_COUNT) {
                        Loggers.SRV_LOG.warn(
                                "FUZZY_WATCH:  pattern matched service count is over limit , current service will be ignore for pattern {} ,current count is {}",
                                next.getKey(), matchedServiceKeys.size());
                        continue;
                    }
                    if (matchedServiceKeys.add(serviceKey)) {
                        Loggers.SRV_LOG.info("FUZZY_WATCH: pattern {} matched service keys count changed to {}",
                                next.getKey(), matchedServiceKeys.size());
                        needNotify = true;
                    }
                    
                } else if (changedType.equals(DELETE_SERVICE) && matchedServiceKeys.contains(serviceKey)) {
                    if (matchedServiceKeys.remove(serviceKey)) {
                        Loggers.SRV_LOG.info("FUZZY_WATCH:  pattern {} matched service keys count changed to {}",
                                next.getKey(), matchedServiceKeys.size());
                        needNotify = true;
                    }
                }
            }
        }
        return needNotify;
    }
    
    public Set<String> syncFuzzyWatcherContext(String groupKeyPattern, String clientId) {
        
        watchedClients.computeIfAbsent(groupKeyPattern, key -> new ConcurrentHashSet<>()).add(clientId);
        Set<String> matchedServiceKeys = initWatchMatchService(groupKeyPattern);
        return matchedServiceKeys;
    }
    
    private void removeFuzzyWatchContext(String clientId) {
        Iterator<Map.Entry<String, Set<String>>> iterator = watchedClients.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Set<String>> next = iterator.next();
            next.getValue().remove(clientId);
        }
    }
    
    public void removeFuzzyWatchContext(String groupKeyPattern, String clientId) {
        
        if (watchedClients.containsKey(groupKeyPattern)) {
            watchedClients.get(groupKeyPattern).remove(clientId);
        }
    }
    
    /**
     * This method will build/update the fuzzy watch match index for given patterns.
     *
     * @param completedPattern the completed pattern of watch (with namespace id).
     * @return a copy set of matched service keys in Nacos server
     */
    public Set<String> initWatchMatchService(String completedPattern) {
        
        if (!matchedServiceKeys.containsKey(completedPattern)) {
            if (matchedServiceKeys.size() >= FUZZY_WATCH_MAX_PATTERN_COUNT) {
                Loggers.SRV_LOG.warn(
                        "FUZZY_WATCH: fuzzy watch pattern count is over limit ,pattern {} init fail,current count is {}",
                        completedPattern, matchedServiceKeys.size());
                throw new NacosRuntimeException(FUZZY_WATCH_PATTERN_OVER_LIMIT.getCode(),
                        FUZZY_WATCH_PATTERN_OVER_LIMIT.getMsg());
            }
            
            long matchBeginTime = System.currentTimeMillis();
            Set<Service> namespaceServices = ServiceManager.getInstance()
                    .getSingletons(getNamespaceFromPattern(completedPattern));
            Set<String> matchedServices = matchedServiceKeys.computeIfAbsent(completedPattern, k -> new HashSet<>());
            
            for (Service service : namespaceServices) {
                if (FuzzyGroupKeyPattern.matchPattern(completedPattern, service.getName(), service.getGroup(),
                        service.getNamespace())) {
                    if (matchedServices.size() >= FUZZY_WATCH_MAX_PATTERN_MATCHED_GROUP_KEY_COUNT) {
                        
                        Loggers.SRV_LOG.warn(
                                "FUZZY_WATCH:  pattern matched service count is over limit , other services will stop notify for pattern {} ,current count is {}",
                                completedPattern, matchedServices.size());
                        break;
                        //                        throw new NacosRuntimeException(FUZZY_WATCH_PATTERN_MATCH_GROUP_KEY_OVER_LIMIT.getCode(),
                        //                                FUZZY_WATCH_PATTERN_MATCH_GROUP_KEY_OVER_LIMIT.getMsg());
                    }
                    matchedServices.add(
                            NamingUtils.getServiceKey(service.getNamespace(), service.getGroup(), service.getName()));
                }
            }
            matchedServiceKeys.putIfAbsent(completedPattern, matchedServices);
            Loggers.SRV_LOG.info("FUZZY_WATCH: pattern {} match {} services, cost {}ms", completedPattern,
                    matchedServices.size(), System.currentTimeMillis() - matchBeginTime);
            
        }
        
        return new HashSet(matchedServiceKeys.get(completedPattern));
    }
    
    
}
