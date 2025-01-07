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
import com.alibaba.nacos.common.notify.listener.SmartSubscriber;
import com.alibaba.nacos.common.utils.CollectionUtils;
import com.alibaba.nacos.common.utils.ConcurrentHashSet;
import com.alibaba.nacos.common.utils.FuzzyGroupKeyPattern;
import com.alibaba.nacos.core.utils.GlobalExecutor;
import com.alibaba.nacos.naming.core.v2.ServiceManager;
import com.alibaba.nacos.naming.core.v2.event.client.ClientOperationEvent;
import com.alibaba.nacos.naming.core.v2.event.service.ServiceEvent;
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
import static com.alibaba.nacos.api.model.v2.ErrorCode.FUZZY_WATCH_PATTERN_MATCH_GROUP_KEY_OVER_LIMIT;
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
    private final ConcurrentMap<String, Set<String>> keyPatternWatchClients = new ConcurrentHashMap<>();
    
    /**
     * The pattern matched service keys for pattern.{fuzzy watch pattern -> Set[matched service keys]}. initialized a
     * new entry pattern when a client register a new pattern. destroyed a new entry pattern by task when no clients
     * watch pattern in max 30s delay.
     */
    private final ConcurrentMap<String, Set<String>> fuzzyWatchPatternMatchServices = new ConcurrentHashMap<>();
    
    private final int FUZZY_WATCH_MAX_PATTERN_COUNT = 50;
    
    private final int FUZZY_WATCH_MAX_PATTERN_MATCHED_GROUP_KEY_COUNT = 200;
    
    
    public NamingFuzzyWatchContextService() {
        GlobalExecutor.scheduleWithFixDelayByCommon(() -> trimFuzzyWatchContext(), 30000);
    }
    
    /**
     * trim empty fuzzy watch context. <br/>
     * 1.remove matched service keys context which no client watched. <br/>
     * 2.remove pattern watched clients context if not client watched.
     */
    private void trimFuzzyWatchContext() {
        try {
            Iterator<Map.Entry<String, Set<String>>> iterator = fuzzyWatchPatternMatchServices.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<String, Set<String>> next = iterator.next();
                Set<String> watchedClients = keyPatternWatchClients.get(next.getKey());
                if (CollectionUtils.isEmpty(watchedClients)) {
                    iterator.remove();
                    if (watchedClients != null) {
                        keyPatternWatchClients.remove(next.getKey());
                    }
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
        result.add(ServiceEvent.ServiceChangedEvent.class);
        return result;
    }
    
    @Override
    public void onEvent(Event event) {
        
        //handle client disconnected event.
        if (event instanceof ClientOperationEvent.ClientReleaseEvent) {
            removeFuzzyWatchContext(((ClientOperationEvent.ClientReleaseEvent) event).getClientId());
        }
    
        //handle service add or deleted event
        if (event instanceof ServiceEvent.ServiceChangedEvent) {
            ServiceEvent.ServiceChangedEvent serviceChangedEvent = (ServiceEvent.ServiceChangedEvent) event;
            String changedType = serviceChangedEvent.getChangedType();
            Service service = serviceChangedEvent.getService();
            
            syncServiceContext(service,changedType);
        }
    }
    
    
    public Set<String> getFuzzyWatchedClients(Service service) {
        Set<String> matchedClients = new HashSet<>();
        Iterator<Map.Entry<String, Set<String>>> iterator = keyPatternWatchClients.entrySet().iterator();
        while (iterator.hasNext()) {
            if (FuzzyGroupKeyPattern.matchPattern(iterator.next().getKey(), service.getName(), service.getGroup(),
                    service.getNamespace())) {
                matchedClients.addAll(iterator.next().getValue());
            }
        }
        return matchedClients;
    }
    
    /**
     * This method will build/update the fuzzy watch match index of all patterns.
     *
     * @param service The service of the Nacos.
     */
    public void addNewService(Service service) {
        Set<String> filteredPattern = FuzzyGroupKeyPattern.filterMatchedPatterns(keyPatternWatchClients.keySet(),
                service.getName(), service.getGroup(), service.getNamespace());
        
        if (CollectionUtils.isNotEmpty(filteredPattern)) {
            for (String each : filteredPattern) {
                fuzzyWatchPatternMatchServices.get(each)
                        .add(NamingUtils.getServiceKey(service.getNamespace(), service.getGroup(), service.getName()));
            }
            Loggers.PERFORMANCE_LOG.info("WATCH: new service {} match {} pattern",
                    service.getGroupedServiceName(), fuzzyWatchPatternMatchServices.size());
        }
    }
    
    public void syncServiceContext(Service changedEventService, String changedType) {
    
        Iterator<Map.Entry<String, Set<String>>> iterator = fuzzyWatchPatternMatchServices.entrySet()
                .iterator();
    
        while (iterator.hasNext()) {
            Map.Entry<String, Set<String>> next = iterator.next();
            if (FuzzyGroupKeyPattern.matchPattern(next.getKey(),
                    changedEventService.getName(),
                    changedEventService.getGroup(), changedEventService.getNamespace())) {
                String serviceKey = NamingUtils.getServiceKey(changedEventService.getNamespace(),
                        changedEventService.getGroup(), changedEventService.getName());
                if (changedType.equals(ADD_SERVICE)){
                    next.getValue().add(serviceKey);
                
                }else if (changedType.equals(DELETE_SERVICE)){
                    next.getValue().remove(serviceKey);
                }
            }
        }
    }
    
    public Set<String> syncFuzzyWatcherContext(String groupKeyPattern, String clientId) {
        
        keyPatternWatchClients.computeIfAbsent(groupKeyPattern, key -> new ConcurrentHashSet<>())
                .add(clientId);
        Set<String> matchedServiceKeys = initWatchMatchService(groupKeyPattern);
        return matchedServiceKeys;
    }
    
    private void removeFuzzyWatchContext(String clientId) {
        Iterator<Map.Entry<String, Set<String>>> iterator = keyPatternWatchClients.entrySet().iterator();
        
        while (iterator.hasNext()) {
            Map.Entry<String, Set<String>> next = iterator.next();
            next.getValue().remove(clientId);
        }
    }
    
    public void removeFuzzyWatchContext(String groupKeyPattern, String clientId) {
        
        if (keyPatternWatchClients.containsKey(groupKeyPattern)) {
            keyPatternWatchClients.get(groupKeyPattern).remove(clientId);
        }
    }
    
    /**
     * This method will build/update the fuzzy watch match index for given patterns.
     *
     * @param completedPattern the completed pattern of watch (with namespace id).
     * @return a copy set of matched service keys in Nacos server
     */
    public Set<String> initWatchMatchService(String completedPattern) {
        
        if (!fuzzyWatchPatternMatchServices.containsKey(completedPattern)) {
            if (fuzzyWatchPatternMatchServices.size() >= FUZZY_WATCH_MAX_PATTERN_COUNT) {
                throw new NacosRuntimeException(FUZZY_WATCH_PATTERN_OVER_LIMIT.getCode(),
                        FUZZY_WATCH_PATTERN_OVER_LIMIT.getMsg());
            }
    
            long matchBeginTime = System.currentTimeMillis();
            Set<Service> namespaceServices = ServiceManager.getInstance()
                    .getSingletons(getNamespaceFromPattern(completedPattern));
            Set<String> matchedServices = fuzzyWatchPatternMatchServices.computeIfAbsent(completedPattern,
                    k -> new HashSet<>());
    
            for (Service service : namespaceServices) {
                if (FuzzyGroupKeyPattern.matchPattern(completedPattern, service.getName(), service.getGroup(),
                        service.getNamespace())) {
                    if (matchedServices.size() >= FUZZY_WATCH_MAX_PATTERN_MATCHED_GROUP_KEY_COUNT) {
                        throw new NacosRuntimeException(FUZZY_WATCH_PATTERN_MATCH_GROUP_KEY_OVER_LIMIT.getCode(),
                                FUZZY_WATCH_PATTERN_MATCH_GROUP_KEY_OVER_LIMIT.getMsg());
                    }
                    matchedServices.add(
                            NamingUtils.getServiceKey(service.getNamespace(), service.getGroup(), service.getName()));
                }
            }
            fuzzyWatchPatternMatchServices.putIfAbsent(completedPattern,matchedServices);
            Loggers.PERFORMANCE_LOG.info("WATCH: pattern {} match {} services, cost {}ms", completedPattern,
                    matchedServices.size(), System.currentTimeMillis() - matchBeginTime);
    
        }
        
        return new HashSet(fuzzyWatchPatternMatchServices.get(completedPattern));
    }
    
    
    
}
