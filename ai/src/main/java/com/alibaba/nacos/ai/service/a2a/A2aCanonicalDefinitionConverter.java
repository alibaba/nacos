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

import com.alibaba.nacos.ai.utils.AgentRequestUtil;
import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.api.ai.model.a2a.AgentCard;
import com.alibaba.nacos.api.ai.model.a2a.AgentInterface;
import com.alibaba.nacos.api.ai.model.agent.AgentCallInterface;
import com.alibaba.nacos.api.ai.model.agent.AgentDraftCreateRequest;
import com.alibaba.nacos.api.ai.model.agent.AgentProvider;
import com.alibaba.nacos.api.ai.model.agent.Endpoint;
import com.alibaba.nacos.api.ai.model.agent.EndpointSource;
import com.alibaba.nacos.api.ai.utils.EndpointCanonicalizer;
import com.alibaba.nacos.api.ai.utils.EndpointNaturalKey;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Pure conversion from one legacy A2A AgentCard to the canonical Agent definition model.
 *
 * <p>This converter contains no compatibility-mode or migration state. The public legacy facade
 * and historical migration reconciler deliberately share it so both paths normalize the same
 * AgentCard into byte-for-byte equivalent Agent Version content.</p>
 *
 * @author Nacos
 */
@Component
public class A2aCanonicalDefinitionConverter {
    
    public static final String A2A_PROTOCOL = "a2a";
    
    private static final String JSON_MEDIA_TYPE = "application/json";
    
    /**
     * Convert one complete AgentCard into the canonical create-request representation.
     *
     * @param namespaceId effective namespace
     * @param source complete legacy AgentCard
     * @param registrationType normalized legacy URL or SERVICE registration type
     * @param includeMetadata whether Agent-level metadata should be copied
     * @return canonical protocol-neutral definition
     * @throws NacosException when the card or registration type is invalid
     */
    public AgentDraftCreateRequest convert(String namespaceId, AgentCard source,
        String registrationType, boolean includeMetadata) throws NacosException {
        String normalizedType = normalizeRegistrationType(registrationType, null);
        AgentCard card = copyAgentCard(source);
        AgentRequestUtil.validateAgentCard(card);
        AgentCallInterface callInterface = new AgentCallInterface();
        callInterface.setProtocol(A2A_PROTOCOL);
        callInterface.setProtocolVersion(card.getProtocolVersion());
        callInterface.setDescriptorMediaType(JSON_MEDIA_TYPE);
        callInterface.setNativeDescriptor(toNativeDescriptor(card));
        callInterface.setEndpointSourceOrder(sourceOrder(normalizedType));
        callInterface.setDeclaredEndpoints(declaredEndpoints(namespaceId, card));
        AgentDraftCreateRequest result = new AgentDraftCreateRequest();
        result.setAgentName(card.getName());
        result.setVersion(card.getVersion());
        result.setCallInterfaces(Collections.singletonList(callInterface));
        if (includeMetadata) {
            result.setDescription(card.getDescription());
            result.setIconUrl(card.getIconUrl());
            result.setProvider(toAgentProvider(card));
        }
        return result;
    }
    
    /**
     * Normalize one legacy registration type without rewriting unsupported values.
     *
     * @param value supplied registration type
     * @param defaultValue default used only when value is blank
     * @return canonical upper-case URL or SERVICE token
     * @throws NacosApiException when neither value nor default is valid
     */
    public String normalizeRegistrationType(String value, String defaultValue)
        throws NacosApiException {
        String candidate =
            StringUtils.isBlank(value) && defaultValue != null ? defaultValue : value;
        if (AiConstants.A2a.A2A_ENDPOINT_TYPE_URL.equalsIgnoreCase(candidate)) {
            return AiConstants.A2a.A2A_ENDPOINT_TYPE_URL;
        }
        if (AiConstants.A2a.A2A_ENDPOINT_TYPE_SERVICE.equalsIgnoreCase(candidate)) {
            return AiConstants.A2a.A2A_ENDPOINT_TYPE_SERVICE;
        }
        throw new NacosApiException(NacosException.INVALID_PARAM,
            ErrorCode.PARAMETER_VALIDATE_ERROR,
            "registrationType must be URL or SERVICE: " + value);
    }
    
    private AgentCard copyAgentCard(AgentCard source) {
        if (source == null) {
            throw new IllegalArgumentException("AgentCard must not be null");
        }
        return JacksonUtils.toObj(JacksonUtils.toJson(source), AgentCard.class);
    }
    
    private Object toNativeDescriptor(AgentCard card) {
        return JacksonUtils.toObj(JacksonUtils.toJson(card), Map.class);
    }
    
    private AgentProvider toAgentProvider(AgentCard card) {
        if (card.getProvider() == null) {
            return null;
        }
        AgentProvider result = new AgentProvider();
        result.setName(card.getProvider().getOrganization());
        result.setUrl(card.getProvider().getUrl());
        return result;
    }
    
    private List<EndpointSource> sourceOrder(String registrationType) {
        List<EndpointSource> result = new ArrayList<EndpointSource>(2);
        if (AiConstants.A2a.A2A_ENDPOINT_TYPE_SERVICE.equals(registrationType)) {
            result.add(EndpointSource.RUNTIME);
            result.add(EndpointSource.DECLARED);
        } else {
            result.add(EndpointSource.DECLARED);
            result.add(EndpointSource.RUNTIME);
        }
        return result;
    }
    
    private List<Endpoint> declaredEndpoints(String namespaceId, AgentCard card) {
        List<Endpoint> result = new ArrayList<Endpoint>();
        Set<EndpointNaturalKey> keys = new LinkedHashSet<EndpointNaturalKey>();
        for (AgentInterface agentInterface : card.getSupportedInterfaces()) {
            Endpoint endpoint = new Endpoint();
            endpoint.setUri(agentInterface.getUrl());
            endpoint.setTransport(agentInterface.getProtocolBinding());
            Endpoint canonical = EndpointCanonicalizer.canonicalize(endpoint);
            EndpointNaturalKey key = EndpointNaturalKey.of(namespaceId, card.getName(),
                A2A_PROTOCOL, canonical);
            if (keys.add(key)) {
                result.add(canonical);
            }
        }
        return result;
    }
}
