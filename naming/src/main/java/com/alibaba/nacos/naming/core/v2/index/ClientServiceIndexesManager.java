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

import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.naming.utils.NamingUtils;
import com.alibaba.nacos.common.notify.Event;
import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.common.notify.listener.SmartSubscriber;
import com.alibaba.nacos.common.trace.DeregisterInstanceReason;
import com.alibaba.nacos.common.trace.event.naming.DeregisterInstanceTraceEvent;
import com.alibaba.nacos.common.utils.CollectionUtils;
import com.alibaba.nacos.common.utils.ConcurrentHashSet;
import com.alibaba.nacos.naming.core.v2.ServiceManager;
import com.alibaba.nacos.naming.core.v2.client.Client;
import com.alibaba.nacos.naming.core.v2.event.client.ClientOperationEvent;
import com.alibaba.nacos.naming.core.v2.event.publisher.NamingEventPublisherFactory;
import com.alibaba.nacos.naming.core.v2.event.service.ServiceEvent;
import com.alibaba.nacos.naming.core.v2.pojo.InstancePublishInfo;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import com.alibaba.nacos.naming.misc.Loggers;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Client and service index manager.
 *
 * @author xiweng.yy
 */
@Component
public class ClientServiceIndexesManager extends SmartSubscriber {
    
    private final ConcurrentMap<Service, Set<String>> publisherIndexes = new ConcurrentHashMap<>();
    
    private final ConcurrentMap<Service, Set<String>> subscriberIndexes = new ConcurrentHashMap<>();
    
    /**
     * The content of map is {fuzzy watch pattern -> Set[watcher clientID]}.
     */
    private final ConcurrentMap<String, Set<String>> fuzzyWatcherIndexes = new ConcurrentHashMap<>();
    
    /**
     * The content of map is {service -> Set[matched fuzzy watch patterns]}.
     */
    private final ConcurrentMap<Service, Set<String>> fuzzyWatchPatternMatchIndexes = new ConcurrentHashMap<>();
    
    public ClientServiceIndexesManager() {
        NotifyCenter.registerSubscriber(this, NamingEventPublisherFactory.getInstance());
    }
    
    public Collection<String> getAllClientsRegisteredService(Service service) {
        return publisherIndexes.containsKey(service) ? publisherIndexes.get(service) : new ConcurrentHashSet<>();
    }
    
    public Collection<String> getAllClientsSubscribeService(Service service) {
        return subscriberIndexes.containsKey(service) ? subscriberIndexes.get(service) : new ConcurrentHashSet<>();
    }
    
    public Collection<Service> getSubscribedService() {
        return subscriberIndexes.keySet();
    }
    
    public Collection<String> getServiceMatchedPatterns(Service service) {
        return fuzzyWatchPatternMatchIndexes.containsKey(service)
                ? fuzzyWatchPatternMatchIndexes.get(service) : new ConcurrentHashSet<>();
    }
    
    public Collection<String> getAllClientFuzzyWatchedPattern(String pattern) {
        return fuzzyWatcherIndexes.containsKey(pattern) ? fuzzyWatcherIndexes.get(pattern) : new ConcurrentHashSet<>();
    }
    
    /**
     * Clear the service index without instances.
     *
     * @param service The service of the Nacos.
     */
    public void removePublisherIndexesByEmptyService(Service service) {
        if (publisherIndexes.containsKey(service) && publisherIndexes.get(service).isEmpty()) {
            publisherIndexes.remove(service);
            fuzzyWatchPatternMatchIndexes.remove(service);
        }
    }
    
    @Override
    public List<Class<? extends Event>> subscribeTypes() {
        List<Class<? extends Event>> result = new LinkedList<>();
        result.add(ClientOperationEvent.ClientRegisterServiceEvent.class);
        result.add(ClientOperationEvent.ClientDeregisterServiceEvent.class);
        result.add(ClientOperationEvent.ClientSubscribeServiceEvent.class);
        result.add(ClientOperationEvent.ClientUnsubscribeServiceEvent.class);
        result.add(ClientOperationEvent.ClientFuzzyWatchEvent.class);
        result.add(ClientOperationEvent.ClientCancelFuzzyWatchEvent.class);
        result.add(ClientOperationEvent.ClientReleaseEvent.class);
        return result;
    }
    
    @Override
    public void onEvent(Event event) {
        if (event instanceof ClientOperationEvent.ClientReleaseEvent) {
            handleClientDisconnect((ClientOperationEvent.ClientReleaseEvent) event);
        } else if (event instanceof ClientOperationEvent) {
            handleClientOperation((ClientOperationEvent) event);
        }
    }
    
    private void handleClientDisconnect(ClientOperationEvent.ClientReleaseEvent event) {
        Client client = event.getClient();
        for (Service each : client.getAllSubscribeService()) {
            removeSubscriberIndexes(each, client.getClientId());
        }
        for (String eachPattern : client.getAllFuzzyWatchPattern()) {
            removeFuzzyWatcherIndexes(eachPattern, client.getClientId());
        }
        DeregisterInstanceReason reason = event.isNative()
                ? DeregisterInstanceReason.NATIVE_DISCONNECTED : DeregisterInstanceReason.SYNCED_DISCONNECTED;
        long currentTimeMillis = System.currentTimeMillis();
        for (Service each : client.getAllPublishedService()) {
            removePublisherIndexes(each, client.getClientId());
            InstancePublishInfo instance = client.getInstancePublishInfo(each);
            NotifyCenter.publishEvent(new DeregisterInstanceTraceEvent(currentTimeMillis,
                    "", false, reason, each.getNamespace(), each.getGroup(), each.getName(),
                    instance.getIp(), instance.getPort()));
        }
    }
    
    private void handleClientOperation(ClientOperationEvent event) {
        Service service = event.getService();
        String clientId = event.getClientId();
        if (event instanceof ClientOperationEvent.ClientRegisterServiceEvent) {
            addPublisherIndexes(service, clientId);
        } else if (event instanceof ClientOperationEvent.ClientDeregisterServiceEvent) {
            removePublisherIndexes(service, clientId);
        } else if (event instanceof ClientOperationEvent.ClientSubscribeServiceEvent) {
            addSubscriberIndexes(service, clientId);
        } else if (event instanceof ClientOperationEvent.ClientUnsubscribeServiceEvent) {
            removeSubscriberIndexes(service, clientId);
        } else if (event instanceof ClientOperationEvent.ClientFuzzyWatchEvent) {
            String completedPattern = ((ClientOperationEvent.ClientFuzzyWatchEvent) event).getPattern();
            addFuzzyWatcherIndexes(completedPattern, clientId);
        } else if (event instanceof ClientOperationEvent.ClientCancelFuzzyWatchEvent) {
            String completedPattern = ((ClientOperationEvent.ClientCancelFuzzyWatchEvent) event).getPattern();
            removeFuzzyWatcherIndexes(completedPattern, clientId);
        }
    }
    
    private void addPublisherIndexes(Service service, String clientId) {
        String serviceChangedType = Constants.ServiceChangedType.INSTANCE_CHANGED;
        if (!publisherIndexes.containsKey(service)) {
            // The only time the index needs to be updated is when the service is first created
            updateWatchMatchIndex(service);
            serviceChangedType = Constants.ServiceChangedType.ADD_SERVICE;
        }
        NotifyCenter.publishEvent(new ServiceEvent.ServiceChangedEvent(service, serviceChangedType, true));
        publisherIndexes.computeIfAbsent(service, key -> new ConcurrentHashSet<>()).add(clientId);
    }
    
    private void removePublisherIndexes(Service service, String clientId) {
        publisherIndexes.computeIfPresent(service, (s, ids) -> {
            ids.remove(clientId);
            String serviceChangedType = ids.isEmpty() ? Constants.ServiceChangedType.DELETE_SERVICE :
                    Constants.ServiceChangedType.INSTANCE_CHANGED;
            NotifyCenter.publishEvent(new ServiceEvent.ServiceChangedEvent(service, serviceChangedType, true));
            return ids.isEmpty() ? null : ids;
        });
    }
    
    private void addSubscriberIndexes(Service service, String clientId) {
        Set<String> clientIds = subscriberIndexes.computeIfAbsent(service, key -> new ConcurrentHashSet<>());
        // Fix #5404, Only first time add need notify event.
        if (clientIds.add(clientId)) {
            NotifyCenter.publishEvent(new ServiceEvent.ServiceSubscribedEvent(service, clientId));
        }
    }
    
    private void removeSubscriberIndexes(Service service, String clientId) {
        Set<String> clientIds = subscriberIndexes.get(service);
        if (clientIds == null) {
            return;
        }
        clientIds.remove(clientId);
        if (clientIds.isEmpty()) {
            subscriberIndexes.remove(service);
        }
    }
    
    private void addFuzzyWatcherIndexes(String completedPattern, String clientId) {
        fuzzyWatcherIndexes.computeIfAbsent(completedPattern, key -> new ConcurrentHashSet<>());
        fuzzyWatcherIndexes.get(completedPattern).add(clientId);
        Collection<Service> matchedService = updateWatchMatchIndex(completedPattern);
        NotifyCenter.publishEvent(new ServiceEvent.ServiceFuzzyWatchInitEvent(clientId, completedPattern, matchedService));
    }
    
    private void removeFuzzyWatcherIndexes(String completedPattern, String clientId) {
        if (!fuzzyWatcherIndexes.containsKey(completedPattern)) {
            return;
        }
        fuzzyWatcherIndexes.get(completedPattern).remove(clientId);
        if (fuzzyWatcherIndexes.get(completedPattern).isEmpty()) {
            fuzzyWatcherIndexes.remove(completedPattern);
        }
    }
    
    /**
     * This method will build/update the fuzzy watch match index of all patterns.
     *
     * @param service The service of the Nacos.
     */
    public void updateWatchMatchIndex(Service service) {
        long matchBeginTime = System.currentTimeMillis();
        Set<String> filteredPattern = NamingUtils.filterPatternWithNamespace(service.getNamespace(), fuzzyWatcherIndexes.keySet());
        Set<String> matchedPattern = NamingUtils.getServiceMatchedPatterns(service.getName(), service.getGroup(),
                filteredPattern);
        if (CollectionUtils.isNotEmpty(matchedPattern)) {
            fuzzyWatchPatternMatchIndexes.computeIfAbsent(service, key -> new ConcurrentHashSet<>());
            for (String each : matchedPattern) {
                fuzzyWatchPatternMatchIndexes.get(service).add(NamingUtils.getPatternWithNamespace(service.getNamespace(), each));
            }
            Loggers.PERFORMANCE_LOG.info("WATCH: new service {} match {} pattern, {}ms", service.getGroupedServiceName(),
                    matchedPattern.size(), System.currentTimeMillis() - matchBeginTime);
        }
    }
    
    /**
     * This method will build/update the fuzzy watch match index for given patterns.
     *
     * @param completedPattern the completed pattern of watch (with namespace id).
     * @return Updated set of services in Nacos server that can match this pattern.
     */
    public Collection<Service> updateWatchMatchIndex(String completedPattern) {
        long matchBeginTime = System.currentTimeMillis();
        String namespaceId = NamingUtils.getNamespaceFromPattern(completedPattern);
        Collection<Service> serviceSet = ServiceManager.getInstance().getSingletons(namespaceId);
        String pattern = NamingUtils.getPatternRemovedNamespace(completedPattern);
        
        Set<Service> matchedService = new HashSet<>();
        for (Service service : serviceSet) {
            String serviceName = service.getName();
            String groupName = service.getGroup();
            String serviceNamePattern = NamingUtils.getServiceName(pattern);
            String groupNamePattern = NamingUtils.getGroupName(pattern);
            if (NamingUtils.isMatchPattern(serviceName, groupName, serviceNamePattern, groupNamePattern)) {
                fuzzyWatchPatternMatchIndexes.computeIfAbsent(service, key -> new ConcurrentHashSet<>());
                fuzzyWatchPatternMatchIndexes.get(service).add(completedPattern);
                matchedService.add(service);
            }
        }
        Loggers.PERFORMANCE_LOG.info("WATCH: pattern {} match {} services, {}ms", completedPattern,
                matchedService.size(), System.currentTimeMillis() - matchBeginTime);
        return matchedService;
    }
    
    /**
     * This method will remove the match index of fuzzy watch pattern.
     *
     * @param service The service of the Nacos.
     * @param matchedPattern the pattern to remove
     */
    public void removeWatchPatternMatchIndex(Service service, String matchedPattern) {
        if (!fuzzyWatchPatternMatchIndexes.containsKey(service)) {
            return;
        }
        fuzzyWatchPatternMatchIndexes.get(service).remove(matchedPattern);
        if (fuzzyWatchPatternMatchIndexes.get(service).isEmpty()) {
            fuzzyWatchPatternMatchIndexes.remove(service);
        }
    }
}
