/*
 * Copyright 1999-2026 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.ai.service.a2a.migration;

import com.alibaba.nacos.ai.constant.Constants;
import com.alibaba.nacos.ai.service.a2a.identity.AgentIdCodecHolder;
import com.alibaba.nacos.ai.service.agent.identity.RadServiceNameComposer;
import com.alibaba.nacos.api.ai.utils.AgentValidationUtils;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.naming.pojo.ServiceInfo;
import com.alibaba.nacos.naming.core.v2.ServiceManager;
import com.alibaba.nacos.naming.core.v2.index.ServiceStorage;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Temporary compatibility support for migrating Nacos 3.0-3.2 A2A data.
 *
 * <p>TODO(remove in 4.0): remove after the historical A2A migration window closes.</p>
 *
 * @author Nacos
 */
@Component
public class A2aMigrationRuntimeReadinessGate {
    
    private static final Logger LOGGER =
        LoggerFactory.getLogger(A2aMigrationRuntimeReadinessGate.class);
    
    private static final String EXACT_VERSION_SEPARATOR = "::";
    
    private static final String A2A_PROTOCOL = "a2a";
    
    private final AgentIdCodecHolder agentIdCodecHolder;
    
    private final A2aRuntimeSnapshotComparator comparator;
    
    private final A2aMigrationEndpointRouter endpointRouter;
    
    private final Supplier<Set<String>> namespaceSupplier;
    
    private final Function<String, Set<Service>> serviceSupplier;
    
    private final Function<Service, Service> singletonResolver;
    
    private final Function<Service, List<Instance>> instanceLoader;
    
    @Autowired
    public A2aMigrationRuntimeReadinessGate(AgentIdCodecHolder agentIdCodecHolder,
        A2aRuntimeSnapshotComparator comparator, A2aMigrationEndpointRouter endpointRouter,
        ServiceStorage serviceStorage) {
        this(agentIdCodecHolder, comparator, endpointRouter,
            ServiceManager.getInstance()::getAllNamespaces,
            ServiceManager.getInstance()::getSingletons,
            service -> ServiceManager.getInstance().getSingletonIfExist(service).orElse(service),
            service -> instances(serviceStorage.getPushData(service)));
    }
    
    A2aMigrationRuntimeReadinessGate(AgentIdCodecHolder agentIdCodecHolder,
        A2aRuntimeSnapshotComparator comparator, A2aMigrationEndpointRouter endpointRouter,
        Supplier<Set<String>> namespaceSupplier,
        Function<String, Set<Service>> serviceSupplier,
        Function<Service, Service> singletonResolver,
        Function<Service, List<Instance>> instanceLoader) {
        this.agentIdCodecHolder = agentIdCodecHolder;
        this.comparator = comparator;
        this.endpointRouter = endpointRouter;
        this.namespaceSupplier = namespaceSupplier;
        this.serviceSupplier = serviceSupplier;
        this.singletonResolver = singletonResolver;
        this.instanceLoader = instanceLoader;
    }
    
    /**
     * Verify every active historical exact-Version service against canonical RAD Runtime.
     *
     * @return whether all active snapshots and required local mirror retries are converged
     */
    public boolean isReady() {
        if (endpointRouter.hasPendingRetries()) {
            return false;
        }
        try {
            Set<String> namespaces = namespaceSupplier.get();
            if (namespaces == null) {
                return false;
            }
            for (String namespaceId : namespaces) {
                Set<Service> services = serviceSupplier.apply(namespaceId);
                if (services == null) {
                    return false;
                }
                for (Service service : services) {
                    if (isHistoricalCandidate(service) && !equivalent(service)) {
                        return false;
                    }
                }
            }
            return !endpointRouter.hasPendingRetries();
        } catch (Exception e) {
            LOGGER.warn("Failed to validate historical A2A Runtime migration snapshots", e);
            return false;
        }
    }
    
    /**
     * Check this member's connection-local required mirror retry queue.
     *
     * @return whether no required Runtime mirror retry remains
     */
    public boolean isLocalMirrorReady() {
        return !endpointRouter.hasPendingRetries();
    }
    
    private boolean equivalent(Service historicalService) {
        long historicalRevision = historicalService.getRevision();
        List<Instance> historicalInstances = instanceLoader.apply(historicalService);
        if (historicalInstances.isEmpty()) {
            return true;
        }
        HistoricalIdentity identity = parse(historicalService.getName());
        Service canonicalTemplate = Service.newService(historicalService.getNamespace(),
            Constants.Agent.AGENT_ENDPOINT_GROUP,
            RadServiceNameComposer.compose(identity.agentName, A2A_PROTOCOL));
        Service canonicalService = singletonResolver.apply(canonicalTemplate);
        long canonicalRevision = canonicalService.getRevision();
        List<Instance> canonicalInstances = instanceLoader.apply(canonicalService);
        if (historicalRevision != historicalService.getRevision()
            || canonicalRevision != canonicalService.getRevision()) {
            return false;
        }
        return comparator.equivalent(historicalInstances, canonicalInstances,
            identity.version);
    }
    
    private boolean isHistoricalCandidate(Service service) {
        return service != null && Constants.Agent.AGENT_ENDPOINT_GROUP.equals(service.getGroup())
            && service.getName().lastIndexOf(EXACT_VERSION_SEPARATOR) > 0;
    }
    
    private HistoricalIdentity parse(String serviceName) {
        int separator = serviceName.lastIndexOf(EXACT_VERSION_SEPARATOR);
        if (separator <= 0
            || separator + EXACT_VERSION_SEPARATOR.length() >= serviceName.length()) {
            throw new IllegalArgumentException(
                "Invalid historical A2A Runtime service identity");
        }
        String encodedAgentName = serviceName.substring(0, separator);
        String agentName = agentIdCodecHolder.decode(encodedAgentName);
        String version = serviceName.substring(separator + EXACT_VERSION_SEPARATOR.length());
        AgentValidationUtils.validateAgentName(agentName);
        AgentValidationUtils.validateVersion(version);
        if (!encodedAgentName.equals(agentIdCodecHolder.encode(agentName))) {
            throw new IllegalArgumentException(
                "Non-canonical historical A2A Runtime service identity");
        }
        return new HistoricalIdentity(agentName, version);
    }
    
    static List<Instance> instances(ServiceInfo serviceInfo) {
        if (serviceInfo == null || serviceInfo.getHosts() == null) {
            return Collections.emptyList();
        }
        return new ArrayList<Instance>(serviceInfo.getHosts());
    }
    
    private static final class HistoricalIdentity {
        
        private final String agentName;
        
        private final String version;
        
        private HistoricalIdentity(String agentName, String version) {
            this.agentName = agentName;
            this.version = version;
        }
    }
}
