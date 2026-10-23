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

package com.alibaba.nacos.client.ai;

import com.alibaba.nacos.api.ai.model.a2a.AgentEndpoint;
import com.alibaba.nacos.api.ai.model.agent.AgentEndpointRegistrationBatch;
import com.alibaba.nacos.api.ai.model.agent.Endpoint;
import com.alibaba.nacos.api.ai.model.agent.RuntimeVersionBinding;
import com.alibaba.nacos.api.ai.utils.A2aEndpointUtils;
import com.alibaba.nacos.api.ai.utils.AgentValidationUtils;
import com.alibaba.nacos.api.ai.utils.EndpointNaturalKey;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.client.ai.utils.AgentModelUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Immutable legacy exact-Version references and retained compatibility ranges for one publication.
 *
 * @author Nacos
 */
final class A2aEndpointIntent {
    
    private final Map<String, List<Endpoint>> versions;
    
    private final Map<EndpointNaturalKey, Range> ranges;
    
    private final AgentEndpointRegistrationBatch batch;
    
    private A2aEndpointIntent(String namespaceId, String agentName,
        Map<String, List<Endpoint>> versions, Map<EndpointNaturalKey, Range> previousRanges)
        throws NacosException {
        this.versions = versions;
        this.ranges = new HashMap<EndpointNaturalKey, Range>();
        Map<EndpointNaturalKey, Endpoint> endpoints = new TreeMap<EndpointNaturalKey, Endpoint>();
        Map<EndpointNaturalKey, String> runtimeVersions = new HashMap<EndpointNaturalKey, String>();
        for (Map.Entry<String, List<Endpoint>> entry : versions.entrySet()) {
            String version = entry.getKey();
            for (Endpoint endpoint : entry.getValue()) {
                EndpointNaturalKey key =
                    EndpointNaturalKey.of(namespaceId, agentName, "a2a", endpoint);
                Endpoint existing = endpoints.get(key);
                if (existing != null && !samePayload(existing, endpoint)) {
                    throw invalid("Conflicting A2A Endpoint payloads for " + key);
                }
                endpoints.put(key, endpoint);
                String runtime = runtimeVersions.get(key);
                if (runtime == null || AgentValidationUtils.compareVersions(runtime, version) < 0) {
                    runtimeVersions.put(key, version);
                }
                Range range = ranges.containsKey(key) ? ranges.get(key) : previousRanges.get(key);
                ranges.put(key,
                    range == null ? new Range(version, version) : range.include(version));
            }
        }
        AgentEndpointRegistrationBatch candidate = new AgentEndpointRegistrationBatch();
        candidate.setAgentName(agentName);
        candidate.setProtocol("a2a");
        List<Endpoint> output = new ArrayList<Endpoint>();
        for (Map.Entry<EndpointNaturalKey, Endpoint> entry : endpoints.entrySet()) {
            // A fresh payload keeps the immutable reference snapshots independent of bindings.
            Endpoint endpoint = copyPayload(entry.getValue());
            RuntimeVersionBinding binding = new RuntimeVersionBinding();
            binding.setRuntimeVersion(runtimeVersions.get(entry.getKey()));
            binding.setVersionRange(ranges.get(entry.getKey()).expression());
            endpoint.setBindings(Collections.singletonList(binding));
            output.add(endpoint);
        }
        candidate.setEndpoints(output);
        this.batch =
            output.isEmpty() ? null : AgentModelUtils.copyRegistrationBatch(candidate, namespaceId);
    }
    
    static A2aEndpointIntent replace(String namespaceId, String agentName,
        A2aEndpointIntent previous, Collection<AgentEndpoint> endpoints) throws NacosException {
        if (endpoints == null || endpoints.isEmpty()) {
            throw invalid("A2A Endpoint batch must not be empty.");
        }
        Map<String, List<Endpoint>> versions = previous == null
            ? new HashMap<String, List<Endpoint>>()
            : new HashMap<String, List<Endpoint>>(previous.versions);
        String version = null;
        List<Endpoint> converted = new ArrayList<Endpoint>();
        try {
            AgentValidationUtils.validateNamespaceId(namespaceId);
            AgentValidationUtils.validateAgentName(agentName);
            for (AgentEndpoint endpoint : endpoints) {
                if (endpoint == null) {
                    throw new IllegalArgumentException("A2A Endpoint must not be null.");
                }
                AgentValidationUtils.validateVersion(endpoint.getVersion());
                if (version != null && !version.equals(endpoint.getVersion())) {
                    throw new IllegalArgumentException(
                        "A2A Endpoint batch must have one exact version.");
                }
                version = endpoint.getVersion();
                converted.add(A2aEndpointUtils.toEndpoint(endpoint));
            }
            // Validate each submitted snapshot before merging, including duplicate natural keys.
            AgentEndpointRegistrationBatch submitted = new AgentEndpointRegistrationBatch();
            submitted.setAgentName(agentName);
            submitted.setProtocol("a2a");
            submitted.setRuntimeVersion(version);
            submitted.setEndpoints(converted);
            AgentModelUtils.copyRegistrationBatch(submitted, namespaceId);
            versions.put(version, converted);
            return new A2aEndpointIntent(namespaceId, agentName, versions,
                previous == null ? Collections.<EndpointNaturalKey, Range>emptyMap()
                    : previous.ranges);
        } catch (IllegalArgumentException e) {
            throw invalid(e.getMessage());
        }
    }
    
    A2aEndpointIntent remove(String namespaceId, String agentName, String version)
        throws NacosException {
        if (!versions.containsKey(version)) {
            return this;
        }
        Map<String, List<Endpoint>> remaining = new HashMap<String, List<Endpoint>>(versions);
        remaining.remove(version);
        return new A2aEndpointIntent(namespaceId, agentName, remaining, ranges);
    }
    
    AgentEndpointRegistrationBatch batch() {
        return batch;
    }
    
    private static Endpoint copyPayload(Endpoint source) {
        Endpoint result = new Endpoint();
        result.setUri(source.getUri());
        result.setTransport(source.getTransport());
        result.setPriority(source.getPriority());
        result.setWeight(source.getWeight());
        result.setHealthy(source.getHealthy());
        result.setEnabled(source.getEnabled());
        result.setMetadata(source.getMetadata() == null ? null
            : new HashMap<String, String>(source.getMetadata()));
        return result;
    }
    
    private static boolean samePayload(Endpoint left, Endpoint right) {
        return Objects.equals(left.getUri(), right.getUri())
            && Objects.equals(left.getTransport(), right.getTransport())
            && Objects.equals(left.getPriority(), right.getPriority())
            && Objects.equals(left.getWeight(), right.getWeight())
            && Objects.equals(left.getHealthy(), right.getHealthy())
            && Objects.equals(left.getEnabled(), right.getEnabled())
            && Objects.equals(left.getMetadata(), right.getMetadata());
    }
    
    private static NacosException invalid(String message) {
        return new NacosException(NacosException.INVALID_PARAM, message);
    }
    
    private static final class Range {
        
        private final String minimum;
        
        private final String maximum;
        
        private Range(String minimum, String maximum) {
            this.minimum = minimum;
            this.maximum = maximum;
        }
        
        private Range include(String version) {
            return new Range(
                AgentValidationUtils.compareVersions(minimum, version) > 0 ? version : minimum,
                AgentValidationUtils.compareVersions(maximum, version) < 0 ? version : maximum);
        }
        
        private String expression() {
            return minimum.equals(maximum) ? "[" + minimum + "]"
                : "[" + minimum + "," + maximum + "]";
        }
    }
}
