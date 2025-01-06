package com.alibaba.nacos.naming.core.v2.index;

import com.alibaba.nacos.api.exception.runtime.NacosRuntimeException;
import com.alibaba.nacos.api.naming.utils.NamingUtils;
import com.alibaba.nacos.common.notify.Event;
import com.alibaba.nacos.common.notify.NotifyCenter;
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

@Component
public class NamingFuzzyWatchContextService extends SmartSubscriber {
    
    /**
     * watched client ids of a pattern,  {fuzzy watch pattern -> Set[watched clientID]}.
     */
    private final ConcurrentMap<String, Set<String>> keyPatternWatchClients = new ConcurrentHashMap<>();
    
    /**
     * The pattern matched service keys for pattern.{fuzzy watch pattern -> Set[matched service keys]}.
     * initialized a new entry pattern when a client register a new pattern.
     * destroyed a new entry pattern by task when no clients watch pattern in max 30s delay.
     */
    private final ConcurrentMap<String, Set<String>> fuzzyWatchPatternMatchServices = new ConcurrentHashMap<>();
    
    private final int FUZZY_WATCH_MAX_PATTERN_COUNT = 50;
    
    private final int FUZZY_WATCH_MAX_PATTERN_MATCHED_GROUP_KEY_COUNT = 200;
    
    
    public NamingFuzzyWatchContextService() {
        GlobalExecutor.scheduleWithFixDelayByCommon(() -> clearFuzzyWatchContext(), 30000);
    }
    
    private void clearFuzzyWatchContext() {
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
        }catch(Throwable throwable){
            throwable.printStackTrace();
        }
    }
    
    @Override
    public List<Class<? extends Event>> subscribeTypes() {
        List<Class<? extends Event>> result = new LinkedList<>();
        result.add(ClientOperationEvent.ClientFuzzyWatchEvent.class);
        result.add(ClientOperationEvent.ClientCancelFuzzyWatchEvent.class);
        result.add(ClientOperationEvent.ClientReleaseEvent.class);
        result.add(ServiceEvent.ServiceChangedEvent.class);
        
        return result;
    }
    
    @Override
    public void onEvent(Event event) {
        if (!(event instanceof ClientOperationEvent) && !(event instanceof ServiceEvent.ServiceChangedEvent)) {
            return;
        }
        
        if (event instanceof ClientOperationEvent) {
            ClientOperationEvent clientOperationEvent = (ClientOperationEvent) event;
            String clientId = clientOperationEvent.getClientId();
            
            if (event instanceof ClientOperationEvent.ClientFuzzyWatchEvent) {
                ClientOperationEvent.ClientFuzzyWatchEvent clientFuzzyWatchEvent = (ClientOperationEvent.ClientFuzzyWatchEvent) event;
                String completedPattern = clientFuzzyWatchEvent.getPattern();
                if (clientFuzzyWatchEvent.isInitializing()){
                    //fuzzy watch init
                    addFuzzyWatcherIndexes(completedPattern, clientId);
                }else{
                    //fuzzy watch sync
                    syncFuzzyWatcherClientIdContext(completedPattern,clientFuzzyWatchEvent.getClientReceivedGroupKeys(),clientId);
                }
                
            } else if (event instanceof ClientOperationEvent.ClientCancelFuzzyWatchEvent) {
                String completedPattern = ((ClientOperationEvent.ClientCancelFuzzyWatchEvent) event).getPattern();
                removeFuzzyWatchContext(completedPattern, clientId);
            } else if (event instanceof ClientOperationEvent.ClientReleaseEvent) {
                removeFuzzyWatchContext(((ClientOperationEvent.ClientReleaseEvent) event).getClientId());
            }
        }
        
        if (event instanceof ServiceEvent.ServiceChangedEvent) {
            ServiceEvent.ServiceChangedEvent serviceChangedEvent = (ServiceEvent.ServiceChangedEvent) event;
            String changedType = serviceChangedEvent.getChangedType();
            if (changedType.equals(ADD_SERVICE)) {
                Iterator<Map.Entry<String, Set<String>>> iterator = fuzzyWatchPatternMatchServices.entrySet()
                        .iterator();
                Service changedEventService = serviceChangedEvent.getService();
    
                while (iterator.hasNext()) {
                    Map.Entry<String, Set<String>> next = iterator.next();
                    if (FuzzyGroupKeyPattern.matchPattern(next.getKey(),
                            changedEventService.getName(),
                            changedEventService.getGroup(), changedEventService.getNamespace())) {
                        next.getValue().add(NamingUtils.getServiceKey(changedEventService.getNamespace(),changedEventService.getGroup(),changedEventService.getName()));
                    }
                }
                
            } else if (changedType.equals(DELETE_SERVICE)) {
                Iterator<Map.Entry<String, Set<String>>> iterator = fuzzyWatchPatternMatchServices.entrySet()
                        .iterator();
                Service changedEventService = serviceChangedEvent.getService();
    
                while (iterator.hasNext()) {
                    Map.Entry<String, Set<String>> next = iterator.next();
                    if (FuzzyGroupKeyPattern.matchPattern(next.getKey(),
                            changedEventService.getName(),
                            changedEventService.getGroup(), changedEventService.getNamespace())) {
                        String serviceKey = NamingUtils.getServiceKey(changedEventService.getNamespace(),
                                changedEventService.getGroup(), changedEventService.getName());
                        next.getValue().remove(serviceKey);
                    }
                }
            }
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
    public void addNewSevice(Service service) {
        long matchBeginTime = System.currentTimeMillis();
        Set<String> filteredPattern = FuzzyGroupKeyPattern.filterMatchedPatterns(keyPatternWatchClients.keySet(),
                service.getName(), service.getGroup(), service.getNamespace());
        
        if (CollectionUtils.isNotEmpty(filteredPattern)) {
            for (String each : filteredPattern) {
                fuzzyWatchPatternMatchServices.get(each).add(NamingUtils.getServiceKey(service.getNamespace(),service.getGroup(),service.getName()));
            }
            Loggers.PERFORMANCE_LOG.info("WATCH: new service {} match {} pattern, {}ms",
                    service.getGroupedServiceName(), fuzzyWatchPatternMatchServices.size(),
                    System.currentTimeMillis() - matchBeginTime);
        }
    }
    
    private void addFuzzyWatcherIndexes(String completedPattern, String clientId) {
        
        Set<String> matchedServiceKeys = initWatchMatchService(completedPattern);
        boolean added =keyPatternWatchClients.computeIfAbsent(completedPattern, key -> new ConcurrentHashSet<>()).add(clientId);
        if (added) {
            NotifyCenter.publishEvent(
                    new ServiceEvent.ServiceFuzzyWatchInitEvent(clientId, completedPattern, matchedServiceKeys));
        }
    }
    
    private void syncFuzzyWatcherClientIdContext(String completedPattern,Set<String> clientReceivedServiceKeys, String clientId) {
        
        Set<String> matchedServiceKeys = getPatternMatchedServiceKeys(completedPattern);
        keyPatternWatchClients.computeIfAbsent(completedPattern, key -> new ConcurrentHashSet<>());
        boolean added = keyPatternWatchClients.get(completedPattern).add(clientId);
        if (added) {
            NotifyCenter.publishEvent(
                    new ServiceEvent.ServiceFuzzyWatchInitEvent(clientId, completedPattern, matchedServiceKeys));
        }
    }
    
    
    
    private void removeFuzzyWatchContext(String clientId) {
        Iterator<Map.Entry<String, Set<String>>> iterator = keyPatternWatchClients.entrySet().iterator();
        
        while (iterator.hasNext()) {
            Map.Entry<String, Set<String>> next = iterator.next();
            next.getValue().remove(clientId);
        }
    }
    
    private void removeFuzzyWatchContext(String groupKeyPattern, String clientId) {
        
        if (keyPatternWatchClients.containsKey(groupKeyPattern)) {
            keyPatternWatchClients.get(groupKeyPattern).remove(clientId);
        }
    }
    
    /**
     * This method will remove the match index of fuzzy watch pattern.
     *
     * @param service        The service of the Nacos.
     * @param matchedPattern the pattern to remove
     */
    public void removeFuzzyWatchContext(Service service, String matchedPattern) {
        if (!fuzzyWatchPatternMatchServices.containsKey(service)) {
            return;
        }
        fuzzyWatchPatternMatchServices.get(service).remove(matchedPattern);
        if (fuzzyWatchPatternMatchServices.get(service).isEmpty()) {
            fuzzyWatchPatternMatchServices.remove(service);
        }
    }
    
    
    public Set<String> getPatternMatchedServiceKeys(String completedPattern) {
        return   fuzzyWatchPatternMatchServices.get(completedPattern);
    }
    
    /**
     * This method will build/update the fuzzy watch match index for given patterns.
     *
     * @param completedPattern the completed pattern of watch (with namespace id).
     * @return Updated set of services in Nacos server that can match this pattern.
     */
    public Set<String> initWatchMatchService(String completedPattern) {
        
        if (fuzzyWatchPatternMatchServices.containsKey(completedPattern)) {
            return fuzzyWatchPatternMatchServices.get(completedPattern);
        }
        
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
                matchedServices.add(NamingUtils.getServiceKey(service.getNamespace(),service.getGroup(),service.getName()));
            }
        }
        
        Loggers.PERFORMANCE_LOG.info("WATCH: pattern {} match {} services, cost {}ms", completedPattern,
                matchedServices.size(), System.currentTimeMillis() - matchBeginTime);
        
        return matchedServices;
    }
    
}
