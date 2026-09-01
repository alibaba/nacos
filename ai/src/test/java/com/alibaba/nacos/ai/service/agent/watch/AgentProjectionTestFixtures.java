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

package com.alibaba.nacos.ai.service.agent.watch;

import com.alibaba.nacos.ai.constant.Constants;
import com.alibaba.nacos.ai.service.agent.identity.RadServiceNameComposer;
import com.alibaba.nacos.api.ai.model.agent.EndpointSource;
import com.alibaba.nacos.api.ai.model.agent.RuntimeVersionBinding;
import com.alibaba.nacos.api.ai.model.rad.AgentDiscoveryCallInterface;
import com.alibaba.nacos.api.ai.model.rad.AgentDiscoveryEndpoint;
import com.alibaba.nacos.api.ai.model.rad.AgentDiscoveryRequest;
import com.alibaba.nacos.api.ai.model.rad.AgentDiscoveryResult;
import com.alibaba.nacos.api.ai.model.rad.AgentReference;
import com.alibaba.nacos.api.ai.model.rad.EndpointSet;
import com.alibaba.nacos.naming.core.v2.pojo.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

final class AgentProjectionTestFixtures {
    
    static final String NAMESPACE_ID = "public";
    
    static final String AGENT_NAME = "projection-agent";
    
    static final String CONTENT_DIGEST =
        "sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    
    static final String RUNTIME_REVISION =
        "murmur3-x64-128-v1:00000000000000000000000000000000";
    
    private AgentProjectionTestFixtures() {
    }
    
    static AgentDiscoveryRequest request(String agentName) {
        AgentReference reference = new AgentReference();
        reference.setAgentName(agentName);
        AgentDiscoveryRequest result = new AgentDiscoveryRequest();
        result.setNamespaceId(NAMESPACE_ID);
        result.setReference(reference);
        return result;
    }
    
    static AgentProjectionKey key(String agentName) {
        return AgentProjectionKey.of(request(agentName));
    }
    
    static Service service(String agentName, String protocol) {
        return Service.newService(NAMESPACE_ID, Constants.Agent.AGENT_ENDPOINT_GROUP,
            RadServiceNameComposer.compose(agentName, protocol));
    }
    
    static AgentProjectionState available(String fingerprint, long computedAt,
        Service... dependencies) {
        return AgentProjectionState.available(fingerprint,
            new LinkedHashSet<Service>(Arrays.asList(dependencies)), computedAt);
    }
    
    static AgentDiscoveryResult snapshot(String agentName, String... runtimeProtocols) {
        List<AgentDiscoveryCallInterface> interfaces =
            new ArrayList<AgentDiscoveryCallInterface>();
        for (String protocol : runtimeProtocols) {
            interfaces.add(callInterface(protocol, EndpointSource.RUNTIME));
        }
        AgentDiscoveryResult result = new AgentDiscoveryResult();
        result.setNamespaceId(NAMESPACE_ID);
        result.setAgentName(agentName);
        result.setVersion("1.0.0");
        result.setContentDigest(CONTENT_DIGEST);
        result.setCallInterfaces(interfaces);
        return result;
    }
    
    static AgentDiscoveryCallInterface callInterface(String protocol, EndpointSource source) {
        AgentDiscoveryEndpoint endpoint = new AgentDiscoveryEndpoint();
        endpoint.setUri("https://example.com/" + protocol);
        endpoint.setTransport("http");
        endpoint.setPriority(0);
        endpoint.setWeight(1D);
        if (source == EndpointSource.RUNTIME) {
            endpoint.setHealthy(true);
            RuntimeVersionBinding binding = new RuntimeVersionBinding();
            binding.setRuntimeVersion("1.0.0");
            binding.setVersionRange("[1.0.0]");
            endpoint.setBindings(Collections.singletonList(binding));
        }
        EndpointSet endpointSet = new EndpointSet();
        endpointSet.setSource(source);
        endpointSet.setSourceRevision(source == EndpointSource.RUNTIME ? RUNTIME_REVISION
            : CONTENT_DIGEST);
        endpointSet.setEndpoints(Collections.singletonList(endpoint));
        AgentDiscoveryCallInterface result = new AgentDiscoveryCallInterface();
        result.setProtocol(protocol);
        result.setProtocolVersion("1.0");
        result.setDescriptorMediaType("application/json");
        result.setNativeDescriptor(Collections.<String, Object>singletonMap("name", protocol));
        result.setEndpointSets(Collections.singletonList(endpointSet));
        return result;
    }
}
