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

package com.alibaba.nacos.api.ai.utils;

import com.alibaba.nacos.api.ai.model.a2a.AgentCard;
import com.alibaba.nacos.api.ai.model.a2a.AgentInterface;
import com.alibaba.nacos.api.ai.model.agent.AgentCallInterface;
import com.alibaba.nacos.api.ai.model.agent.AgentProvider;
import com.alibaba.nacos.api.ai.model.agent.Endpoint;
import com.alibaba.nacos.api.ai.model.agent.EndpointSet;
import com.alibaba.nacos.api.ai.model.agent.EndpointSource;
import com.alibaba.nacos.api.ai.model.agent.base.AbstractAgentDraftRequest;
import com.alibaba.nacos.api.utils.json.JsonUtils;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Pure definition mapping shared by request builders, legacy facades and migration.
 * Callers retain their own validation and endpoint projection policy.
 * @author Nacos
 */
public final class A2aRequestUtils {
    
    private A2aRequestUtils() {
    }
    
    /**
     * Populate a caller-owned draft using a normalized Card and explicit endpoint projection.
     * @param <T> concrete draft request type
     * @param result isolated request to populate
     * @param card isolated normalized Card
     * @param sourceOrder ordered endpoint sources
     * @param endpoints isolated declared projection
     * @param includeMetadata whether to include Agent-level presentation metadata
     * @return populated request with the full native descriptor
     */
    public static <T extends AbstractAgentDraftRequest> T populateDraft(T result, AgentCard card,
        List<EndpointSource> sourceOrder, List<Endpoint> endpoints, boolean includeMetadata) {
        AgentCallInterface call = new AgentCallInterface();
        call.setProtocol("a2a");
        call.setProtocolVersion(card.getProtocolVersion());
        call.setDescriptorMediaType("application/json");
        call.setNativeDescriptor(JsonUtils.toObj(JsonUtils.toJson(card), Map.class));
        call.setEndpointSourceOrder(new ArrayList<EndpointSource>(sourceOrder));
        EndpointSet declared = new EndpointSet();
        declared.setSource(EndpointSource.DECLARED);
        declared.setEndpoints(endpoints);
        call.setEndpointSets(Collections.singletonList(declared));
        result.setAgentName(card.getName());
        result.setVersion(card.getVersion());
        result.setCallInterfaces(Collections.singletonList(call));
        if (includeMetadata) {
            result.setDescription(card.getDescription());
            result.setIconUrl(card.getIconUrl());
            if (card.getProvider() != null) {
                AgentProvider provider = new AgentProvider();
                provider.setName(card.getProvider().getOrganization());
                provider.setUrl(card.getProvider().getUrl());
                result.setProvider(provider);
            }
        }
        return result;
    }
    
    /**
     * Preserve the historical declared projection, including first-wins deduplication.
     * Do not add runtime metadata or ordered priorities here: migration digests depend on it.
     * @param namespaceId effective namespace
     * @param card normalized Card
     * @return canonical legacy declared endpoints
     */
    public static List<Endpoint> legacyDeclaredEndpoints(String namespaceId, AgentCard card) {
        List<Endpoint> result = new ArrayList<Endpoint>();
        Set<EndpointNaturalKey> keys = new LinkedHashSet<EndpointNaturalKey>();
        for (AgentInterface address : card.getSupportedInterfaces()) {
            Endpoint endpoint = new Endpoint();
            endpoint.setUri(address.getUrl());
            endpoint.setTransport(address.getProtocolBinding());
            endpoint = EndpointCanonicalizer.canonicalize(endpoint);
            if (keys.add(EndpointNaturalKey.of(namespaceId, card.getName(), "a2a", endpoint))) {
                result.add(endpoint);
            }
        }
        return result;
    }
    
    /**
     * Convert ordered native interfaces without losing colliding endpoint identities.
     * @param interfaces complete native interface list
     * @return ordered endpoints with per-interface metadata
     * @throws IllegalArgumentException when an interface is invalid or identities collide
     */
    public static List<Endpoint> fromAgentInterfaces(List<AgentInterface> interfaces) {
        if (interfaces == null || interfaces.isEmpty()) {
            throw new IllegalArgumentException("A2A interfaces must not be empty");
        }
        List<Endpoint> result = new ArrayList<Endpoint>();
        Set<EndpointNaturalKey> keys = new LinkedHashSet<EndpointNaturalKey>();
        for (AgentInterface address : interfaces) {
            Endpoint endpoint = A2aEndpointUtils.fromAgentInterface(address);
            endpoint.setPriority(result.size());
            // Namespace and name are constant within a batch; only endpoint identity varies.
            if (!keys.add(EndpointNaturalKey.of("validation", "validation", "a2a", endpoint))) {
                throw new IllegalArgumentException("Duplicate A2A Endpoint natural key");
            }
            result.add(endpoint);
        }
        return result;
    }
}
