package com.alibaba.nacos.naming.core.v2.index;

import com.alibaba.nacos.api.naming.utils.NamingUtils;
import com.alibaba.nacos.common.notify.Event;
import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.common.notify.listener.SmartSubscriber;
import com.alibaba.nacos.common.utils.CollectionUtils;
import com.alibaba.nacos.common.utils.ConcurrentHashSet;
import com.alibaba.nacos.common.utils.FuzzyGroupKeyPattern;
import com.alibaba.nacos.naming.core.v2.ServiceManager;
import com.alibaba.nacos.naming.core.v2.client.Client;
import com.alibaba.nacos.naming.core.v2.event.client.ClientOperationEvent;
import com.alibaba.nacos.naming.core.v2.event.service.ServiceEvent;
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

import static com.alibaba.nacos.common.utils.FuzzyGroupKeyPattern.getNamespaceFromGroupKeyPattern;

@Component
public class ClientFuzzyWatchIndexesManager extends SmartSubscriber {
    
    /**
     * The content of map is {fuzzy watch pattern -> Set[watcher clientID]}.
     */
    private final ConcurrentMap<String, Set<String>> fuzzyWatcherIndexes = new ConcurrentHashMap<>();
    
    /**
     * The content of map is {service -> Set[matched fuzzy watch patterns]}.
     */
    private final ConcurrentMap<Service, Set<String>> fuzzyWatchPatternMatchIndexes = new ConcurrentHashMap<>();
    
    
    @Override
    public List<Class<? extends Event>> subscribeTypes() {
        List<Class<? extends Event>> result = new LinkedList<>();
        result.add(ClientOperationEvent.ClientFuzzyWatchEvent.class);
        result.add(ClientOperationEvent.ClientCancelFuzzyWatchEvent.class);
        result.add(ClientOperationEvent.ClientReleaseEvent.class);
        result.add(ClientOperationEvent.ClientRegisterServiceEvent.class);
        return result;
    }
    
    @Override
    public void onEvent(Event event) {
        if (!(event instanceof ClientOperationEvent)){
             return;
        }
        ClientOperationEvent clientOperationEvent=(ClientOperationEvent)event;
        String clientId = clientOperationEvent.getClientId();
    
        if (event instanceof ClientOperationEvent.ClientRegisterServiceEvent) {
        
        } else if (event instanceof ClientOperationEvent.ClientFuzzyWatchEvent) {
            String completedPattern = ((ClientOperationEvent.ClientFuzzyWatchEvent) event).getPattern();
            addFuzzyWatcherIndexes(completedPattern, clientId);
        } else if (event instanceof ClientOperationEvent.ClientCancelFuzzyWatchEvent) {
            String completedPattern = ((ClientOperationEvent.ClientCancelFuzzyWatchEvent) event).getPattern();
            removeFuzzyWatcherIndexes(completedPattern, clientId);
        } else if(event instanceof ClientOperationEvent.ClientReleaseEvent){
            ClientOperationEvent.ClientReleaseEvent clientReleaseEvent=(ClientOperationEvent.ClientReleaseEvent)event;
            Client client = clientReleaseEvent.getClient();
            for (String eachPattern : client.getFuzzyWatchedPattern()) {
                removeFuzzyWatcherIndexes(eachPattern, client.getClientId());
            }
        }
    }
    
    public Collection<String> getServiceMatchedPatterns(Service service) {
        return fuzzyWatchPatternMatchIndexes.containsKey(service)
                ? fuzzyWatchPatternMatchIndexes.get(service) : new ConcurrentHashSet<>();
    }
    
    public Collection<String> getAllClientFuzzyWatchedPattern(String pattern) {
        return fuzzyWatcherIndexes.containsKey(pattern) ? fuzzyWatcherIndexes.get(pattern) : new ConcurrentHashSet<>();
    }
    
    
    /**
     * This method will build/update the fuzzy watch match index of all patterns.
     *
     * @param service The service of the Nacos.
     */
    public void updateWatchMatchIndex(Service service) {
        long matchBeginTime = System.currentTimeMillis();
        Set<String> filteredPattern = FuzzyGroupKeyPattern.filterMatchedPatterns(fuzzyWatcherIndexes.keySet(),service.getName(),service.getGroup(),service.getNamespace());
        
        if (CollectionUtils.isNotEmpty(filteredPattern)) {
            fuzzyWatchPatternMatchIndexes.computeIfAbsent(service, key -> new ConcurrentHashSet<>());
            for (String each : filteredPattern) {
                fuzzyWatchPatternMatchIndexes.get(service).add(each);
            }
            Loggers.PERFORMANCE_LOG.info("WATCH: new service {} match {} pattern, {}ms", service.getGroupedServiceName(),
                    fuzzyWatchPatternMatchIndexes.size(), System.currentTimeMillis() - matchBeginTime);
        }
    }
    
    public void removeWatchMatchIndex(Service service) {
        fuzzyWatchPatternMatchIndexes.remove(service);
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
    
    /**
     * This method will build/update the fuzzy watch match index for given patterns.
     *
     * @param completedPattern the completed pattern of watch (with namespace id).
     * @return Updated set of services in Nacos server that can match this pattern.
     */
    public Collection<Service> updateWatchMatchIndex(String completedPattern) {
        long matchBeginTime = System.currentTimeMillis();
        Collection<Service> serviceSet = ServiceManager.getInstance().getSingletons(getNamespaceFromGroupKeyPattern(completedPattern));
        
        Set<Service> matchedService = new HashSet<>();
        for (Service service : serviceSet) {
            String serviceName = service.getName();
            String groupName = service.getGroup();
            if (FuzzyGroupKeyPattern.matchPattern(completedPattern,service.getNamespace(),groupName,serviceName)) {
                fuzzyWatchPatternMatchIndexes.computeIfAbsent(service, key -> new ConcurrentHashSet<>());
                fuzzyWatchPatternMatchIndexes.get(service).add(completedPattern);
                matchedService.add(service);
            }
        }
        Loggers.PERFORMANCE_LOG.info("WATCH: pattern {} match {} services, {}ms", completedPattern,
                matchedService.size(), System.currentTimeMillis() - matchBeginTime);
        return matchedService;
    }
    
}
