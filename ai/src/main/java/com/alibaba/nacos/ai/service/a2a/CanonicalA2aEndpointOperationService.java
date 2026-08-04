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
import com.alibaba.nacos.ai.remote.manager.AiConnectionBasedClientManager;
import com.alibaba.nacos.ai.service.agent.identity.RadServiceNameComposer;
import com.alibaba.nacos.ai.service.agent.runtime.AgentRuntimeEndpointMapper;
import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.api.ai.model.a2a.AgentEndpoint;
import com.alibaba.nacos.api.ai.model.agent.Endpoint;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.exception.runtime.NacosRuntimeException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.naming.utils.NamingUtils;
import com.alibaba.nacos.api.remote.RemoteConstants;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.core.remote.ClientConnectionEventListener;
import com.alibaba.nacos.core.remote.Connection;
import com.alibaba.nacos.naming.constants.ClientConstants;
import com.alibaba.nacos.naming.core.v2.client.ClientAttributes;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import com.alibaba.nacos.naming.core.v2.service.impl.EphemeralClientOperationServiceImpl;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

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
public class CanonicalA2aEndpointOperationService extends ClientConnectionEventListener {
    
    static final String CHILD_CLIENT_ID_PREFIX = "A2A_ENDPOINT_";
    
    private static final String A2A_PROTOCOL = "a2a";
    
    private static final String CHILD_ID_SEPARATOR = "@@";
    
    private static final int MAX_RUNTIME_ENDPOINTS = 1000;
    
    private final AiConnectionBasedClientManager clientManager;
    
    private final EphemeralClientOperationServiceImpl clientOperationService;
    
    private final ConcurrentMap<String, Set<String>> childClientIds =
        new ConcurrentHashMap<String, Set<String>>();
    
    public CanonicalA2aEndpointOperationService(AiConnectionBasedClientManager clientManager,
        EphemeralClientOperationServiceImpl clientOperationService) {
        this.clientManager = clientManager;
        this.clientOperationService = clientOperationService;
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
                instances.add(toInstance(endpoint));
            } catch (IllegalArgumentException e) {
                throw invalidEndpoint(e.getMessage());
            }
        }
        NamingUtils.batchCheckInstanceIsLegal(instances);
        Service service = composeService(namespaceId, agentName);
        String expectedChildClientId =
            childClientId(parentClientId, namespaceId, agentName, version);
        boolean childAlreadyExisted = clientManager.contains(expectedChildClientId);
        String childClientId = ensureChildClient(parentClientId, namespaceId, agentName, version);
        try {
            clientOperationService.batchRegisterInstance(service, instances, childClientId);
        } catch (RuntimeException e) {
            if (!childAlreadyExisted) {
                disconnectChild(parentClientId, childClientId);
            }
            throw e;
        }
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
        String childClientId = childClientId(parentClientId, namespaceId, agentName, version);
        if (!clientManager.contains(childClientId)) {
            removeChildId(parentClientId, childClientId);
            return;
        }
        clientOperationService.deregisterInstance(composeService(namespaceId, agentName),
            new Instance(), childClientId);
        disconnectChild(parentClientId, childClientId);
    }
    
    @Override
    public void clientConnected(Connection connect) {
    }
    
    @Override
    public void clientDisConnected(Connection connect) {
        if (!RemoteConstants.LABEL_MODULE_AI
            .equals(connect.getMetaInfo().getLabel(RemoteConstants.LABEL_MODULE))) {
            return;
        }
        Set<String> children = childClientIds.remove(connect.getMetaInfo().getConnectionId());
        if (children == null) {
            return;
        }
        for (String childClientId : children) {
            clientManager.clientDisconnected(childClientId);
        }
    }
    
    private String ensureChildClient(String parentClientId, String namespaceId, String agentName,
        String version) {
        if (!clientManager.contains(parentClientId)) {
            throw new NacosRuntimeException(NacosException.CLIENT_DISCONNECT,
                "AI client connection already disconnected: " + parentClientId);
        }
        String childClientId = childClientId(parentClientId, namespaceId, agentName, version);
        if (!clientManager.contains(childClientId)) {
            ClientAttributes attributes = new ClientAttributes();
            attributes.addClientAttribute(ClientConstants.CONNECTION_TYPE,
                ClientConstants.DEFAULT_FACTORY);
            clientManager.clientConnected(childClientId, attributes);
        }
        childClientIds.computeIfAbsent(parentClientId,
            key -> ConcurrentHashMap.newKeySet()).add(childClientId);
        if (!clientManager.contains(parentClientId)) {
            disconnectChild(parentClientId, childClientId);
            throw new NacosRuntimeException(NacosException.CLIENT_DISCONNECT,
                "AI client connection already disconnected: " + parentClientId);
        }
        return childClientId;
    }
    
    private void disconnectChild(String parentClientId, String childClientId) {
        clientManager.clientDisconnected(childClientId);
        removeChildId(parentClientId, childClientId);
    }
    
    private void removeChildId(String parentClientId, String childClientId) {
        Set<String> children = childClientIds.get(parentClientId);
        if (children == null) {
            return;
        }
        children.remove(childClientId);
        if (children.isEmpty()) {
            childClientIds.remove(parentClientId, children);
        }
    }
    
    private String childClientId(String parentClientId, String namespaceId, String agentName,
        String version) {
        String identity = parentClientId + CHILD_ID_SEPARATOR + namespaceId + CHILD_ID_SEPARATOR
            + agentName + CHILD_ID_SEPARATOR + version;
        return CHILD_CLIENT_ID_PREFIX
            + UUID.nameUUIDFromBytes(identity.getBytes(StandardCharsets.UTF_8));
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
}
