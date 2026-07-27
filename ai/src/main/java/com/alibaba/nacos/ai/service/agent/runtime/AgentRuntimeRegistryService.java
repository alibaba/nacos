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

package com.alibaba.nacos.ai.service.agent.runtime;

import com.alibaba.nacos.ai.constant.Constants;
import com.alibaba.nacos.ai.service.agent.fingerprint.RuntimeEndpointRevision;
import com.alibaba.nacos.ai.service.agent.identity.RadServiceNameComposer;
import com.alibaba.nacos.ai.service.agent.metadata.AgentVersionComparator;
import com.alibaba.nacos.api.ai.model.agent.Endpoint;
import com.alibaba.nacos.api.ai.model.agent.EndpointSource;
import com.alibaba.nacos.api.ai.model.agent.RuntimeEndpointSnapshot;
import com.alibaba.nacos.api.ai.model.agent.RuntimeEndpointSnapshotItem;
import com.alibaba.nacos.api.ai.model.agent.RuntimeEndpointState;
import com.alibaba.nacos.api.ai.model.agent.RuntimeVersionBinding;
import com.alibaba.nacos.api.ai.model.rad.AgentEndpointRegistrationBatch;
import com.alibaba.nacos.api.ai.model.rad.EndpointSet;
import com.alibaba.nacos.api.ai.utils.AgentModelValidator;
import com.alibaba.nacos.api.ai.utils.AgentValidationUtils;
import com.alibaba.nacos.api.ai.utils.EndpointCanonicalizer;
import com.alibaba.nacos.api.ai.utils.EndpointNaturalKey;
import com.alibaba.nacos.api.ai.utils.RadModelValidator;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.exception.runtime.NacosRuntimeException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.naming.pojo.ServiceInfo;
import com.alibaba.nacos.api.naming.utils.NamingUtils;
import com.alibaba.nacos.naming.core.v2.index.ServiceStorage;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import com.alibaba.nacos.naming.core.v2.service.impl.EphemeralClientOperationServiceImpl;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Agent Runtime Registry backed directly by Naming client publications.
 *
 * <p>One registration is the complete batch for one publisher and Naming Service. Naming owns
 * replacement, liveness, AP convergence, indexing, and cleanup. This service only composes the
 * Naming identity, converts data structures, and builds public read models.</p>
 *
 * @author Nacos
 */
@Component
public class AgentRuntimeRegistryService {
    
    private static final int MAX_RUNTIME_ENDPOINTS = 1000;
    
    private static final Comparator<RuntimeVersionBinding> BINDING_COMPARATOR =
        new Comparator<RuntimeVersionBinding>() {
            
            @Override
            public int compare(RuntimeVersionBinding left, RuntimeVersionBinding right) {
                int result = AgentVersionComparator.compare(left.getRuntimeVersion(),
                    right.getRuntimeVersion());
                if (result != 0) {
                    return result;
                }
                return left.getVersionRange().compareTo(right.getVersionRange());
            }
        };
    
    private final ServiceStorage serviceStorage;
    
    private final EphemeralClientOperationServiceImpl clientOperationService;
    
    public AgentRuntimeRegistryService(ServiceStorage serviceStorage,
        EphemeralClientOperationServiceImpl clientOperationService) {
        this.serviceStorage = serviceStorage;
        this.clientOperationService = clientOperationService;
    }
    
    /**
     * Replace one publisher's complete Runtime Endpoint batch.
     *
     * @param publisherId Naming client identity
     * @param batch complete batch for one Agent, protocol, and shared Version values
     * @throws NacosException when Naming rejects the batch
     */
    public void register(String publisherId, AgentEndpointRegistrationBatch batch)
        throws NacosException {
        RadModelValidator.validate(batch);
        List<Instance> instances = new ArrayList<Instance>(batch.getEndpoints().size());
        for (Endpoint endpoint : batch.getEndpoints()) {
            instances.add(AgentRuntimeEndpointMapper.toInstance(endpoint,
                batch.getRuntimeVersion(), batch.getVersionRange()));
        }
        NamingUtils.batchCheckInstanceIsLegal(instances);
        clientOperationService.batchRegisterInstance(composeService(batch.getNamespaceId(),
            batch.getAgentName(), batch.getProtocol()), instances, publisherId);
    }
    
    /**
     * Remove one publisher's complete Runtime Endpoint publication.
     *
     * <p>Partial removal is a client-side batch operation: the client computes the retained
     * instances and registers the replacement batch. This method is used only when no publication
     * remains.</p>
     *
     * @param publisherId Naming client identity
     * @param namespaceId namespace identifier
     * @param agentName Agent name
     * @param protocol Agent protocol
     */
    public void deregisterPublisher(String publisherId, String namespaceId, String agentName,
        String protocol) {
        validateReadIdentity(namespaceId, agentName, protocol, null);
        clientOperationService.deregisterInstance(
            composeService(namespaceId, agentName, protocol), new Instance(), publisherId);
    }
    
    /**
     * Return the management Runtime Endpoint snapshot for one Agent protocol.
     *
     * @param namespaceId namespace identifier
     * @param agentName Agent name
     * @param protocol Agent protocol
     * @param version optional Agent Version filter
     * @return Runtime Endpoint snapshot
     * @throws NacosException when Naming state cannot be represented by the Agent contract
     */
    public RuntimeEndpointSnapshot getRuntimeEndpointSnapshot(String namespaceId,
        String agentName,
        String protocol, String version) throws NacosException {
        validateReadIdentity(namespaceId, agentName, protocol, version);
        RuntimeEndpointSnapshot result = new RuntimeEndpointSnapshot();
        result.setNamespaceId(namespaceId);
        result.setAgentName(agentName);
        result.setProtocol(protocol);
        result.setVersion(version);
        result.setItems(loadSnapshotItems(namespaceId, agentName, protocol, version));
        AgentModelValidator.validateRuntimeEndpointSnapshot(result);
        return result;
    }
    
    /**
     * Return enabled Runtime Endpoints for RAD discovery.
     *
     * @param namespaceId namespace identifier
     * @param agentName Agent name
     * @param protocol Agent protocol
     * @param version exact target Agent Version
     * @return Runtime Endpoint set and deterministic source revision
     * @throws NacosException when Naming state cannot be represented by the Agent contract
     */
    public EndpointSet getRuntimeEndpointSet(String namespaceId, String agentName,
        String protocol, String version) throws NacosException {
        validateReadIdentity(namespaceId, agentName, protocol, version);
        if (version == null) {
            throw new IllegalArgumentException("Runtime EndpointSet Version must not be null");
        }
        List<Endpoint> endpoints =
            loadRuntimeEndpoints(namespaceId, agentName, protocol, version);
        sortRuntimeEndpoints(namespaceId, agentName, protocol, endpoints);
        EndpointSet result = new EndpointSet();
        result.setSource(EndpointSource.RUNTIME);
        result.setSourceRevision(
            RuntimeEndpointRevision.compute(namespaceId, agentName, protocol, endpoints));
        result.setEndpoints(endpoints);
        RadModelValidator.validate(result);
        return result;
    }
    
    private List<RuntimeEndpointSnapshotItem> loadSnapshotItems(String namespaceId,
        String agentName, String protocol,
        String version) throws NacosException {
        ServiceInfo serviceInfo = getServiceInfo(namespaceId, agentName, protocol);
        Map<EndpointNaturalKey, RuntimeEndpointSnapshotItem> result =
            new TreeMap<EndpointNaturalKey, RuntimeEndpointSnapshotItem>();
        for (Instance instance : serviceInfo.getHosts()) {
            RuntimeEndpointSnapshotItem contribution =
                mapInstance(instance, serviceInfo.getLastRefTime());
            List<RuntimeVersionBinding> matchingBindings =
                matchingBindings(contribution.getBindings(), version);
            if (matchingBindings.isEmpty()) {
                continue;
            }
            contribution.setBindings(matchingBindings);
            Endpoint endpoint = contribution.getEndpoint();
            EndpointNaturalKey key =
                EndpointNaturalKey.of(namespaceId, agentName, protocol, endpoint);
            RuntimeEndpointSnapshotItem current = result.get(key);
            if (current == null) {
                result.put(key, contribution);
            } else {
                if (!samePayload(current.getEndpoint(), endpoint)) {
                    throw conflict(key);
                }
                mergeSnapshotItem(current, contribution);
            }
        }
        validateCapacity(result.size());
        return new ArrayList<RuntimeEndpointSnapshotItem>(result.values());
    }
    
    private List<Endpoint> loadRuntimeEndpoints(String namespaceId, String agentName,
        String protocol, String version) throws NacosException {
        ServiceInfo serviceInfo = getServiceInfo(namespaceId, agentName, protocol);
        Map<EndpointNaturalKey, Endpoint> payloads =
            new TreeMap<EndpointNaturalKey, Endpoint>();
        Map<EndpointNaturalKey, Endpoint> result =
            new TreeMap<EndpointNaturalKey, Endpoint>();
        for (Instance instance : serviceInfo.getHosts()) {
            RuntimeEndpointSnapshotItem contribution =
                mapInstance(instance, serviceInfo.getLastRefTime());
            if (matchingBindings(contribution.getBindings(), version).isEmpty()) {
                continue;
            }
            Endpoint endpoint = contribution.getEndpoint();
            EndpointNaturalKey key =
                EndpointNaturalKey.of(namespaceId, agentName, protocol, endpoint);
            Endpoint previous = payloads.putIfAbsent(key, endpoint);
            if (previous != null && !samePayload(previous, endpoint)) {
                throw conflict(key);
            }
            if (!contribution.getEnabled()) {
                continue;
            }
            Endpoint current = result.get(key);
            if (current == null) {
                current = copyEndpoint(endpoint);
                current.setHealthy(contribution.getHealthy());
                result.put(key, current);
            } else {
                current.setHealthy(current.getHealthy() || contribution.getHealthy());
            }
        }
        validateCapacity(payloads.size());
        return new ArrayList<Endpoint>(result.values());
    }
    
    private ServiceInfo getServiceInfo(String namespaceId, String agentName, String protocol) {
        ServiceInfo result =
            serviceStorage.getData(composeService(namespaceId, agentName, protocol));
        if (result == null) {
            throw new NacosRuntimeException(NacosException.SERVER_ERROR,
                "Naming ServiceStorage returned no Runtime Endpoint data");
        }
        return result;
    }
    
    private RuntimeEndpointSnapshotItem mapInstance(Instance instance, long lastUpdatedTime) {
        try {
            return AgentRuntimeEndpointMapper.fromInstance(instance, lastUpdatedTime);
        } catch (IllegalArgumentException e) {
            throw new NacosRuntimeException(NacosException.SERVER_ERROR,
                "Invalid Agent Runtime Endpoint in Naming ServiceStorage", e);
        }
    }
    
    private List<RuntimeVersionBinding> matchingBindings(
        List<RuntimeVersionBinding> bindings, String version) {
        List<RuntimeVersionBinding> result = new ArrayList<RuntimeVersionBinding>();
        for (RuntimeVersionBinding binding : bindings) {
            if (version == null
                || RuntimeVersionRangeSupport.contains(binding.getVersionRange(), version)) {
                result.add(binding);
            }
        }
        return result;
    }
    
    private void mergeSnapshotItem(RuntimeEndpointSnapshotItem current,
        RuntimeEndpointSnapshotItem contribution) {
        Set<RuntimeVersionBinding> bindings =
            new TreeSet<RuntimeVersionBinding>(BINDING_COMPARATOR);
        bindings.addAll(current.getBindings());
        bindings.addAll(contribution.getBindings());
        current.setBindings(new ArrayList<RuntimeVersionBinding>(bindings));
        current.setEnabled(current.getEnabled() || contribution.getEnabled());
        current.setHealthy(current.getHealthy() || contribution.getHealthy());
        current.setState(runtimeState(current.getEnabled(), current.getHealthy()));
    }
    
    private RuntimeEndpointState runtimeState(boolean enabled, boolean healthy) {
        if (!enabled) {
            return RuntimeEndpointState.DISABLED;
        }
        if (!healthy) {
            return RuntimeEndpointState.UNHEALTHY;
        }
        return RuntimeEndpointState.AVAILABLE;
    }
    
    private void validateCapacity(int size) throws NacosException {
        if (size > MAX_RUNTIME_ENDPOINTS) {
            throw new NacosException(NacosException.OVER_THRESHOLD,
                "Runtime Endpoint result exceeds " + MAX_RUNTIME_ENDPOINTS
                    + " natural keys");
        }
    }
    
    private Service composeService(String namespaceId, String agentName, String protocol) {
        return Service.newService(namespaceId, Constants.Agent.AGENT_ENDPOINT_GROUP,
            RadServiceNameComposer.compose(agentName, protocol));
    }
    
    private void validateReadIdentity(String namespaceId, String agentName, String protocol,
        String version) {
        AgentValidationUtils.validateNamespaceId(namespaceId);
        AgentValidationUtils.validateAgentName(agentName);
        AgentValidationUtils.validateProtocol(protocol);
        if (version != null) {
            AgentValidationUtils.validateVersion(version);
        }
    }
    
    private void sortRuntimeEndpoints(String namespaceId, String agentName, String protocol,
        List<Endpoint> endpoints) {
        Collections.sort(endpoints, new Comparator<Endpoint>() {
            
            @Override
            public int compare(Endpoint left, Endpoint right) {
                int result = Integer.compare(left.getPriority(), right.getPriority());
                if (result != 0) {
                    return result;
                }
                EndpointNaturalKey leftKey =
                    EndpointNaturalKey.of(namespaceId, agentName, protocol, left);
                EndpointNaturalKey rightKey =
                    EndpointNaturalKey.of(namespaceId, agentName, protocol, right);
                return leftKey.compareTo(rightKey);
            }
        });
    }
    
    private NacosApiException conflict(EndpointNaturalKey key) {
        return new NacosApiException(NacosException.CONFLICT, ErrorCode.RESOURCE_CONFLICT,
            "Naming contains conflicting Runtime Endpoints for natural key: " + key);
    }
    
    private boolean samePayload(Endpoint left, Endpoint right) {
        Endpoint first = EndpointCanonicalizer.canonicalize(left);
        Endpoint second = EndpointCanonicalizer.canonicalize(right);
        return first.getUri().equals(second.getUri())
            && first.getTransport().equals(second.getTransport())
            && first.getPriority().equals(second.getPriority())
            && sameWeight(first.getWeight(), second.getWeight())
            && Objects.equals(first.getMetadata(), second.getMetadata());
    }
    
    private boolean sameWeight(Double left, Double right) {
        double first = left == 0D ? 0D : left;
        double second = right == 0D ? 0D : right;
        return Double.doubleToLongBits(first) == Double.doubleToLongBits(second);
    }
    
    private static Endpoint copyEndpoint(Endpoint source) {
        Endpoint result = new Endpoint();
        result.setUri(source.getUri());
        result.setTransport(source.getTransport());
        result.setPriority(source.getPriority());
        result.setWeight(source.getWeight());
        result.setHealthy(source.getHealthy());
        if (source.getMetadata() != null) {
            result.setMetadata(new LinkedHashMap<String, String>(source.getMetadata()));
        }
        return result;
    }
}
