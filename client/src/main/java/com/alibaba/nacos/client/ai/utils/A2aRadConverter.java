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

package com.alibaba.nacos.client.ai.utils;

import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.api.ai.model.a2a.AgentCard;
import com.alibaba.nacos.api.ai.model.a2a.AgentCardDetailInfo;
import com.alibaba.nacos.api.ai.model.a2a.AgentInterface;
import com.alibaba.nacos.api.ai.model.agent.AgentCallInterface;
import com.alibaba.nacos.api.ai.model.agent.AgentDiscoveryFilter;
import com.alibaba.nacos.api.ai.model.agent.AgentDiscoveryRequest;
import com.alibaba.nacos.api.ai.model.agent.AgentDiscoveryResult;
import com.alibaba.nacos.api.ai.model.agent.AgentProvider;
import com.alibaba.nacos.api.ai.model.agent.AgentReference;
import com.alibaba.nacos.api.ai.model.agent.Endpoint;
import com.alibaba.nacos.api.ai.model.agent.EndpointSet;
import com.alibaba.nacos.api.ai.model.agent.EndpointSource;
import com.alibaba.nacos.api.ai.model.agent.client.AgentPublishRequest;
import com.alibaba.nacos.api.ai.utils.A2aAgentCardUtils;
import com.alibaba.nacos.api.ai.utils.A2aEndpointUtils;
import com.alibaba.nacos.api.ai.utils.EndpointCanonicalizer;
import com.alibaba.nacos.api.ai.utils.EndpointNaturalKey;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.api.utils.json.JsonUtils;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Stateless conversion between complete A2A Cards and RAD discovery/publication.
 *
 * @author Nacos
 */
public final class A2aRadConverter {
    
    public static final String A2A_PROTOCOL = "a2a";
    
    private A2aRadConverter() {
    }
    
    /**
     * Bind a legacy selector to explicit RAD latest or exact Version discovery.
     * @param namespaceId SDK namespace
     * @param name Agent name
     * @param version exact Version, or blank for latest
     * @return validated request retaining both endpoint sources
     * @throws NacosException when the selector is invalid
     */
    public static AgentDiscoveryRequest discoveryRequest(String namespaceId, String name,
        String version) throws NacosException {
        AgentReference reference = new AgentReference();
        reference.setAgentName(name);
        if (blank(version)) {
            reference.setLabel("latest");
        } else {
            reference.setVersion(version);
        }
        AgentDiscoveryFilter filter = new AgentDiscoveryFilter();
        filter.setProtocols(Collections.singletonList(A2A_PROTOCOL));
        return AgentModelUtils.copyDiscoveryRequest(reference, filter, namespaceId);
    }
    
    /**
     * Build one complete Client publication without changing the caller's Card.
     * @param namespaceId SDK namespace used for natural identity validation
     * @param source caller-owned Card
     * @param registrationType definition preference, default SERVICE
     * @param autoSubmit ordinary submission flag
     * @return isolated Client request
     * @throws NacosException when conversion fails
     */
    public static AgentPublishRequest publishRequest(String namespaceId, AgentCard source,
        String registrationType, boolean autoSubmit) throws NacosException {
        String type = normalizeRegistrationType(registrationType,
            AiConstants.A2a.A2A_ENDPOINT_TYPE_SERVICE);
        try {
            AgentCard card = JsonUtils.toObj(JsonUtils.toJson(source), AgentCard.class);
            A2aAgentCardUtils.validateAgentCard(card);
            AgentCallInterface call = new AgentCallInterface();
            call.setProtocol(A2A_PROTOCOL);
            call.setProtocolVersion(card.getProtocolVersion());
            call.setDescriptorMediaType("application/json");
            call.setNativeDescriptor(JsonUtils.toObj(JsonUtils.toJson(card), Map.class));
            call.setEndpointSourceOrder(AiConstants.A2a.A2A_ENDPOINT_TYPE_URL.equals(type)
                ? Arrays.asList(EndpointSource.DECLARED, EndpointSource.RUNTIME)
                : Arrays.asList(EndpointSource.RUNTIME, EndpointSource.DECLARED));
            EndpointSet declared = new EndpointSet();
            declared.setSource(EndpointSource.DECLARED);
            List<Endpoint> addresses = new ArrayList<Endpoint>();
            Set<EndpointNaturalKey> keys = new LinkedHashSet<EndpointNaturalKey>();
            for (AgentInterface address : card.getSupportedInterfaces()) {
                Endpoint endpoint = new Endpoint();
                endpoint.setUri(address.getUrl());
                endpoint.setTransport(address.getProtocolBinding());
                endpoint = EndpointCanonicalizer.canonicalize(endpoint);
                if (keys.add(EndpointNaturalKey.of(namespaceId, card.getName(), A2A_PROTOCOL,
                    endpoint))) {
                    addresses.add(endpoint);
                }
            }
            declared.setEndpoints(addresses);
            call.setEndpointSets(Collections.singletonList(declared));
            AgentPublishRequest request = new AgentPublishRequest();
            request.setAgentName(card.getName());
            request.setVersion(card.getVersion());
            request.setDescription(card.getDescription());
            request.setIconUrl(card.getIconUrl());
            if (card.getProvider() != null) {
                AgentProvider provider = new AgentProvider();
                provider.setName(card.getProvider().getOrganization());
                provider.setUrl(card.getProvider().getUrl());
                request.setProvider(provider);
            }
            request.setCallInterfaces(Collections.singletonList(call));
            request.setAutoSubmit(autoSubmit);
            request.validate();
            return request;
        } catch (RuntimeException e) {
            throw invalid("AgentCard cannot be converted to a RAD definition");
        }
    }
    
    /**
     * Project an unfiltered-source discovery snapshot into a complete legacy Card.
     * @param source RAD snapshot
     * @param request expected identity and latest/exact selector
     * @param registrationType optional projection override
     * @return isolated Card with a degraded exact-Version latest flag
     * @throws NacosException when no valid A2A Card can be projected
     */
    public static AgentCardDetailInfo project(AgentDiscoveryResult source,
        AgentDiscoveryRequest request, String registrationType) throws NacosException {
        String override = normalizeRegistrationType(registrationType, null);
        try {
            if (source == null || !request.getNamespaceId().equals(source.getNamespaceId())
                || !request.getReference().getAgentName().equals(source.getAgentName())
                || request.getReference().getVersion() != null
                    && !request.getReference().getVersion().equals(source.getVersion())) {
                throw notFound();
            }
            AgentCallInterface call = requireInterface(source);
            AgentCardDetailInfo card = JsonUtils.toObj(JsonUtils.toJson(call.getNativeDescriptor()),
                AgentCardDetailInfo.class);
            A2aAgentCardUtils.validateAgentCard(card);
            if (!source.getAgentName().equals(card.getName())
                || !source.getVersion().equals(card.getVersion())) {
                throw notFound();
            }
            List<EndpointSet> sets = call.getEndpointSets();
            if (sets == null || sets.size() != 2 || sets.get(0) == null || sets.get(1) == null
                || sets.get(0).getSource() == null || sets.get(1).getSource() == null
                || sets.get(0).getSource() == sets.get(1).getSource()) {
                throw notFound();
            }
            String storedType = sets.get(0).getSource() == EndpointSource.RUNTIME
                ? AiConstants.A2a.A2A_ENDPOINT_TYPE_SERVICE : AiConstants.A2a.A2A_ENDPOINT_TYPE_URL;
            card.setRegistrationType(storedType);
            card.setLatestVersion(
                request.getReference().getVersion() == null ? Boolean.TRUE : null);
            if (AiConstants.A2a.A2A_ENDPOINT_TYPE_SERVICE
                .equals(override == null ? storedType : override)) {
                projectRuntime(card, call, source);
            }
            return card;
        } catch (NacosException | RuntimeException e) {
            throw notFound();
        }
    }
    
    private static AgentCallInterface requireInterface(AgentDiscoveryResult source)
        throws NacosException {
        if (source.getCallInterfaces() != null) {
            for (AgentCallInterface call : source.getCallInterfaces()) {
                if (call != null && A2A_PROTOCOL.equals(call.getProtocol())
                    && call.getNativeDescriptor() != null) {
                    return call;
                }
            }
        }
        throw notFound();
    }
    
    private static void projectRuntime(AgentCardDetailInfo card, AgentCallInterface call,
        AgentDiscoveryResult source) {
        List<Endpoint> endpoints = new ArrayList<Endpoint>();
        for (EndpointSet set : call.getEndpointSets()) {
            if (set.getSource() == EndpointSource.RUNTIME && set.getEndpoints() != null) {
                for (Endpoint endpoint : set.getEndpoints()) {
                    if (endpoint != null && Boolean.TRUE.equals(endpoint.getEnabled())) {
                        endpoints.add(endpoint);
                    }
                }
            }
        }
        endpoints.sort(Comparator.comparingInt((Endpoint endpoint) -> endpoint.getPriority())
            .thenComparing(endpoint -> EndpointNaturalKey.of(source.getNamespaceId(),
                source.getAgentName(), A2A_PROTOCOL, endpoint).toString()));
        if (endpoints.isEmpty()) {
            return;
        }
        List<AgentInterface> interfaces = new ArrayList<AgentInterface>();
        AgentInterface preferred = null;
        for (Endpoint endpoint : endpoints) {
            AgentInterface address =
                A2aEndpointUtils.toAgentInterface(endpoint, call.getProtocolVersion());
            interfaces.add(address);
            if (preferred == null
                && address.getProtocolBinding().equalsIgnoreCase(card.getPreferredTransport())) {
                preferred = address;
            }
        }
        if (preferred == null) {
            preferred = interfaces.get(0);
        }
        card.setSupportedInterfaces(interfaces);
        card.setAdditionalInterfaces(new ArrayList<AgentInterface>(interfaces));
        card.setUrl(preferred.getUrl());
        card.setPreferredTransport(preferred.getProtocolBinding());
        card.setProtocolVersion(preferred.getProtocolVersion());
    }
    
    /**
     * Normalize a legacy type without inventing a query override when absent.
     * @param type optional type
     * @param defaultType default for blank input
     * @return URL, SERVICE or the supplied default
     * @throws NacosException when the type is invalid
     */
    public static String normalizeRegistrationType(String type, String defaultType)
        throws NacosException {
        if (blank(type)) {
            return defaultType;
        }
        if (AiConstants.A2a.A2A_ENDPOINT_TYPE_URL.equalsIgnoreCase(type)) {
            return AiConstants.A2a.A2A_ENDPOINT_TYPE_URL;
        }
        if (AiConstants.A2a.A2A_ENDPOINT_TYPE_SERVICE.equalsIgnoreCase(type)) {
            return AiConstants.A2a.A2A_ENDPOINT_TYPE_SERVICE;
        }
        throw invalid("registrationType must be URL or SERVICE");
    }
    
    private static boolean blank(String value) {
        return com.alibaba.nacos.api.utils.StringUtils.isBlank(value);
    }
    
    private static NacosApiException invalid(String message) {
        return new NacosApiException(NacosException.INVALID_PARAM,
            ErrorCode.PARAMETER_VALIDATE_ERROR, message);
    }
    
    private static NacosApiException notFound() {
        return new NacosApiException(NacosException.NOT_FOUND, ErrorCode.AGENT_VERSION_NOT_FOUND,
            "The selected Version does not contain a valid A2A AgentCard");
    }
}
