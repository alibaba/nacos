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

import com.alibaba.nacos.ai.remote.manager.AiConnectionBasedClientManager;
import com.alibaba.nacos.api.ai.remote.AiRemoteConstants;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.runtime.NacosRuntimeException;
import com.alibaba.nacos.api.remote.RemoteConstants;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.core.remote.ClientConnectionEventListener;
import com.alibaba.nacos.core.remote.Connection;
import com.alibaba.nacos.core.remote.ConnectionMeta;
import com.alibaba.nacos.naming.constants.ClientConstants;
import com.alibaba.nacos.naming.core.v2.client.AbstractClient;
import com.alibaba.nacos.naming.core.v2.client.Client;
import com.alibaba.nacos.naming.core.v2.client.ClientAttributes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Owns deterministic exact-Version A2A child publishers for one AI connection.
 *
 * <p>The canonical legacy-A2A adapter requires one child per exact Version so independent SDK
 * redo identities cannot overwrite each other. Temporary upgrade migration layouts reuse the
 * same lifecycle owner with a distinct layout token instead of duplicating connection cleanup.</p>
 *
 * @author Nacos
 */
@Component
public class A2aEndpointChildPublisherManager extends ClientConnectionEventListener {
    
    private static final Logger LOGGER =
        LoggerFactory.getLogger(A2aEndpointChildPublisherManager.class);
    
    public static final String CHILD_CLIENT_ID_PREFIX = "A2A_ENDPOINT_";
    
    private final AiConnectionBasedClientManager clientManager;
    
    private final ConcurrentMap<String, Set<String>> childClientIds =
        new ConcurrentHashMap<String, Set<String>>();
    
    private final ConcurrentMap<String, String> childOwners =
        new ConcurrentHashMap<String, String>();
    
    private final Object childLifecycleLock = new Object();
    
    public A2aEndpointChildPublisherManager(AiConnectionBasedClientManager clientManager) {
        this.clientManager = clientManager;
    }
    
    /**
     * Get or create one deterministic child bound to an active parent connection.
     *
     * @param parentClientId original AI connection id
     * @param namespaceId namespace identifier
     * @param agentName Agent name
     * @param version exact Agent Version
     * @param layout physical layout token
     * @return child handle and whether this call created it
     */
    public ChildPublisher ensureChild(String parentClientId, String namespaceId,
        String agentName, String version, String layout) {
        if (StringUtils.isBlank(layout)) {
            throw new IllegalArgumentException("A2A child publisher layout must not be empty");
        }
        requireParent(parentClientId);
        String childClientId = childClientId(resolvePublisherIdentity(parentClientId),
            namespaceId, agentName, version, layout);
        synchronized (childLifecycleLock) {
            requireParent(parentClientId);
            boolean created = ensureResponsibleChild(childClientId, true);
            bindOwner(parentClientId, childClientId);
            if (!clientManager.contains(parentClientId)) {
                disconnectOwnedChild(parentClientId, childClientId);
                throw disconnected(parentClientId);
            }
            return new ChildPublisher(childClientId, created);
        }
    }
    
    /**
     * Find an existing deterministic child without creating it.
     *
     * @param parentClientId original AI connection id
     * @param namespaceId namespace identifier
     * @param agentName Agent name
     * @param version exact Agent Version
     * @param layout physical layout token
     * @return child id, or {@code null} when no live child exists
     */
    public String findChild(String parentClientId, String namespaceId, String agentName,
        String version, String layout) {
        if (!clientManager.contains(parentClientId)) {
            return null;
        }
        String childClientId = childClientId(resolvePublisherIdentity(parentClientId),
            namespaceId, agentName, version, layout);
        synchronized (childLifecycleLock) {
            if (!clientManager.contains(childClientId)) {
                removeChildBinding(parentClientId, childClientId);
                return null;
            }
            ensureResponsibleChild(childClientId, false);
            bindOwner(parentClientId, childClientId);
            return childClientId;
        }
    }
    
    /**
     * Disconnect one child and remove its parent binding.
     *
     * @param parentClientId original AI connection id
     * @param childClientId child publisher id
     */
    public void disconnectChild(String parentClientId, String childClientId) {
        synchronized (childLifecycleLock) {
            disconnectOwnedChild(parentClientId, childClientId);
        }
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
        synchronized (childLifecycleLock) {
            for (String childClientId : children) {
                disconnectOwnedChild(connect.getMetaInfo().getConnectionId(), childClientId);
            }
        }
    }
    
    int childCount(String parentClientId) {
        Set<String> children = childClientIds.get(parentClientId);
        return children == null ? 0 : children.size();
    }
    
    private void requireParent(String parentClientId) {
        if (!clientManager.contains(parentClientId)) {
            throw disconnected(parentClientId);
        }
    }
    
    private NacosRuntimeException disconnected(String parentClientId) {
        return new NacosRuntimeException(NacosException.CLIENT_DISCONNECT,
            "AI client connection already disconnected: " + parentClientId);
    }
    
    private boolean ensureResponsibleChild(String childClientId, boolean createWhenMissing) {
        boolean created = false;
        Client child = clientManager.getClient(childClientId);
        boolean promoteReplica = child != null && !clientManager.isResponsibleClient(child);
        if (promoteReplica) {
            LOGGER.info("[A2A-ENDPOINT-REDO] Promote replicated child publisher after SDK "
                + "reconnect, childId={}", childClientId);
            clientManager.clientDisconnected(childClientId);
        }
        boolean missing = !clientManager.contains(childClientId);
        if (missing && (createWhenMissing || promoteReplica)) {
            ClientAttributes attributes = new ClientAttributes();
            attributes.addClientAttribute(ClientConstants.CONNECTION_TYPE,
                ClientConstants.DEFAULT_FACTORY);
            created = clientManager.clientConnected(childClientId, attributes);
        }
        if ((createWhenMissing || promoteReplica) && !clientManager.contains(childClientId)) {
            throw new NacosRuntimeException(NacosException.SERVER_ERROR,
                "Failed to create A2A child publisher for active AI connection");
        }
        return created;
    }
    
    private void bindOwner(String parentClientId, String childClientId) {
        String previousOwner = childOwners.put(childClientId, parentClientId);
        if (previousOwner != null && !previousOwner.equals(parentClientId)) {
            removeChildId(previousOwner, childClientId);
        }
        childClientIds.computeIfAbsent(parentClientId,
            key -> ConcurrentHashMap.newKeySet()).add(childClientId);
    }
    
    private void disconnectOwnedChild(String parentClientId, String childClientId) {
        if (childOwners.remove(childClientId, parentClientId)) {
            clientManager.clientDisconnected(childClientId);
        }
        removeChildId(parentClientId, childClientId);
    }
    
    private void removeChildBinding(String parentClientId, String childClientId) {
        childOwners.remove(childClientId, parentClientId);
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
    
    private String resolvePublisherIdentity(String parentClientId) {
        Client parent = clientManager.getClient(parentClientId);
        if (!(parent instanceof AbstractClient)) {
            return "connection:" + parentClientId;
        }
        ClientAttributes attributes = ((AbstractClient) parent).getClientAttributes();
        ConnectionMeta metadata = attributes == null ? null
            : attributes.getClientAttribute(ClientConstants.CONNECTION_METADATA);
        String clientUuid = metadata == null ? null
            : metadata.getLabel(AiRemoteConstants.LABEL_CLIENT_UUID);
        if (StringUtils.isNotBlank(clientUuid)) {
            try {
                String normalized = UUID.fromString(clientUuid).toString();
                if (normalized.equalsIgnoreCase(clientUuid)) {
                    return "client:" + normalized;
                }
            } catch (IllegalArgumentException ignored) {
                // Older or non-SDK clients retain the connection-scoped compatibility path.
                return "connection:" + parentClientId;
            }
        }
        return "connection:" + parentClientId;
    }
    
    private String childClientId(String publisherIdentity, String namespaceId, String agentName,
        String version, String layout) {
        String identity = component(publisherIdentity) + component(namespaceId)
            + component(agentName) + component(version) + component(layout);
        return CHILD_CLIENT_ID_PREFIX
            + UUID.nameUUIDFromBytes(identity.getBytes(StandardCharsets.UTF_8));
    }
    
    private String component(String value) {
        String normalized = value == null ? "" : value;
        return normalized.length() + ":" + normalized;
    }
    
    /**
     * One resolved child publisher.
     */
    public static final class ChildPublisher {
        
        private final String clientId;
        
        private final boolean created;
        
        private ChildPublisher(String clientId, boolean created) {
            this.clientId = clientId;
            this.created = created;
        }
        
        public String getClientId() {
            return clientId;
        }
        
        public boolean isCreated() {
            return created;
        }
    }
}
