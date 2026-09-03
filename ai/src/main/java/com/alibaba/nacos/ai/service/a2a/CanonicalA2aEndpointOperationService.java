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

package com.alibaba.nacos.ai.service.a2a;

import com.alibaba.nacos.ai.constant.Constants;
import com.alibaba.nacos.ai.service.a2a.A2aEndpointChildPublisherManager.ChildPublisher;
import com.alibaba.nacos.ai.service.agent.identity.RadServiceNameComposer;
import com.alibaba.nacos.ai.service.agent.runtime.AgentRuntimeEndpointMapper;
import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.api.ai.model.a2a.AgentEndpoint;
import com.alibaba.nacos.api.ai.model.agent.Endpoint;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.naming.utils.NamingUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import com.alibaba.nacos.naming.core.v2.service.impl.EphemeralClientOperationServiceImpl;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Locale;

/**
 * Adapts legacy exact-Version A2A Endpoint operations to the canonical RAD Runtime layout.
 *
 * <p>Each exact Version is represented by an internal child publisher bound to the original
 * gRPC connection. This preserves the legacy independent redo identity without merging publisher
 * state on the server and without allowing one Version to overwrite another. Disconnecting the
 * original AI connection releases all of its child publishers.</p>
 *
 * @author Nacos
 */
@Component
public class CanonicalA2aEndpointOperationService {
    
    static final String CHILD_CLIENT_ID_PREFIX =
        A2aEndpointChildPublisherManager.CHILD_CLIENT_ID_PREFIX;
    
    private static final String A2A_PROTOCOL = "a2a";
    
    static final String CHILD_LAYOUT = "canonical";
    
    private static final int MAX_RUNTIME_ENDPOINTS = 1000;
    
    private final A2aEndpointChildPublisherManager childPublisherManager;
    
    private final EphemeralClientOperationServiceImpl clientOperationService;
    
    public CanonicalA2aEndpointOperationService(
        A2aEndpointChildPublisherManager childPublisherManager,
        EphemeralClientOperationServiceImpl clientOperationService) {
        this.childPublisherManager = childPublisherManager;
        this.clientOperationService = clientOperationService;
    }
    
    /**
     * Validate one legacy exact-Version publication against the canonical Runtime mapping.
     *
     * @param namespaceId namespace identifier
     * @param agentName Agent name
     * @param endpoints complete legacy batch
     * @throws NacosException when the publication cannot be represented canonically
     */
    public void validate(String namespaceId, String agentName,
        Collection<AgentEndpoint> endpoints) throws NacosException {
        prepare(namespaceId, agentName, endpoints);
    }
    
    /**
     * Convert one historical A2A Endpoint to its canonical Runtime Naming representation.
     *
     * <p>This pure adapter is shared by migration snapshot verification so the mirror writer and
     * cutover gate cannot drift in URI, transport, binding, or metadata semantics.</p>
     *
     * @param source historical exact-Version Endpoint
     * @return canonical Naming instance without a Service identity
     */
    public Instance toCanonicalInstance(AgentEndpoint source) {
        return toInstance(source);
    }
    
    /**
     * Replace one legacy exact-Version Endpoint publication in the canonical Runtime service.
     *
     * @param parentClientId original AI gRPC connection id
     * @param namespaceId namespace identifier
     * @param agentName Agent name
     * @param endpoints complete legacy batch for one exact Version
     * @throws NacosException when validation or Naming registration fails
     */
    public void register(String parentClientId, String namespaceId, String agentName,
        Collection<AgentEndpoint> endpoints) throws NacosException {
        PreparedPublication prepared = prepare(namespaceId, agentName, endpoints);
        ChildPublisher child = childPublisherManager.ensureChild(parentClientId, namespaceId,
            agentName, prepared.version, CHILD_LAYOUT);
        try {
            clientOperationService.batchRegisterInstance(prepared.service, prepared.instances,
                child.getClientId());
        } catch (RuntimeException e) {
            if (child.isCreated()) {
                childPublisherManager.disconnectChild(parentClientId, child.getClientId());
            }
            throw e;
        }
    }
    
    private PreparedPublication prepare(String namespaceId, String agentName,
        Collection<AgentEndpoint> endpoints) throws NacosException {
        if (endpoints == null || endpoints.isEmpty()) {
            throw invalidEndpoint("Legacy A2A Endpoint batch must not be empty");
        }
        if (endpoints.size() > MAX_RUNTIME_ENDPOINTS) {
            throw new NacosException(NacosException.OVER_THRESHOLD,
                "Legacy A2A Endpoint batch exceeds " + MAX_RUNTIME_ENDPOINTS + " endpoints");
        }
        AgentEndpoint firstEndpoint = endpoints.iterator().next();
        if (firstEndpoint == null || StringUtils.isBlank(firstEndpoint.getVersion())) {
            throw invalidEndpoint("Legacy A2A Endpoint Version must not be empty");
        }
        String version = firstEndpoint.getVersion();
        ArrayList<Instance> instances = new ArrayList<Instance>(endpoints.size());
        for (AgentEndpoint endpoint : endpoints) {
            if (endpoint == null || !version.equals(endpoint.getVersion())) {
                throw invalidEndpoint(
                    "Legacy A2A Endpoint batch must contain one exact Version");
            }
            try {
                instances.add(toCanonicalInstance(endpoint));
            } catch (IllegalArgumentException e) {
                throw invalidEndpoint(e.getMessage());
            }
        }
        NamingUtils.batchCheckInstanceIsLegal(instances);
        Service service = composeService(namespaceId, agentName);
        return new PreparedPublication(version, service, instances);
    }
    
    /**
     * Remove one legacy exact-Version Endpoint publication from the canonical Runtime service.
     *
     * @param parentClientId original AI gRPC connection id
     * @param namespaceId namespace identifier
     * @param agentName Agent name
     * @param version exact legacy Agent Version
     */
    public void deregister(String parentClientId, String namespaceId, String agentName,
        String version) {
        String childClientId = childPublisherManager.findChild(parentClientId, namespaceId,
            agentName, version, CHILD_LAYOUT);
        if (childClientId == null) {
            return;
        }
        clientOperationService.deregisterInstance(composeService(namespaceId, agentName),
            new Instance(), childClientId);
        childPublisherManager.disconnectChild(parentClientId, childClientId);
    }
    
    private Service composeService(String namespaceId, String agentName) {
        return Service.newService(namespaceId, Constants.Agent.AGENT_ENDPOINT_GROUP,
            RadServiceNameComposer.compose(agentName, A2A_PROTOCOL));
    }
    
    private Instance toInstance(AgentEndpoint source) {
        if (StringUtils.isBlank(source.getAddress())) {
            throw new IllegalArgumentException("Legacy A2A Endpoint address must not be empty");
        }
        Endpoint endpoint = new Endpoint();
        endpoint.setUri(composeUri(source));
        endpoint.setTransport(source.getTransport());
        return AgentRuntimeEndpointMapper.toLegacyA2aInstance(endpoint, source.getVersion(),
            source.getProtocolVersion(), source.getTenant());
    }
    
    private String composeUri(AgentEndpoint endpoint) {
        String protocol = StringUtils.isBlank(endpoint.getProtocol())
            ? AiConstants.A2a.A2A_ENDPOINT_DEFAULT_PROTOCOL : endpoint.getProtocol();
        if (AiConstants.A2a.A2A_ENDPOINT_DEFAULT_PROTOCOL.equalsIgnoreCase(protocol)
            && endpoint.isSupportTls()) {
            protocol = "https";
        }
        String address = endpoint.getAddress();
        String host = address != null && address.indexOf(':') >= 0 && !address.startsWith("[")
            ? '[' + address + ']' : address;
        String path = StringUtils.isBlank(endpoint.getPath()) ? ""
            : endpoint.getPath().startsWith("/") ? endpoint.getPath() : '/' + endpoint.getPath();
        String query = StringUtils.isBlank(endpoint.getQuery()) ? "" : '?' + endpoint.getQuery();
        return protocol.toLowerCase(Locale.ROOT) + "://" + host + ':'
            + endpoint.getPort() + path + query;
    }
    
    private NacosApiException invalidEndpoint(String message) {
        return new NacosApiException(NacosException.INVALID_PARAM,
            ErrorCode.PARAMETER_VALIDATE_ERROR, message);
    }
    
    private static final class PreparedPublication {
        
        private final String version;
        
        private final Service service;
        
        private final ArrayList<Instance> instances;
        
        private PreparedPublication(String version, Service service,
            ArrayList<Instance> instances) {
            this.version = version;
            this.service = service;
            this.instances = instances;
        }
    }
}
