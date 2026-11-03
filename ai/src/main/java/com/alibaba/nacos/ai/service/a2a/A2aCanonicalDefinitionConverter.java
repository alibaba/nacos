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
import com.alibaba.nacos.api.ai.model.agent.admin.AgentDraftCreateRequest;
import com.alibaba.nacos.api.ai.model.agent.EndpointSource;
import com.alibaba.nacos.api.ai.utils.A2aRequestUtils;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.api.utils.json.JsonUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

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
        return A2aRequestUtils.populateDraft(new AgentDraftCreateRequest(), card,
            sourceOrder(normalizedType),
            A2aRequestUtils.legacyDeclaredEndpoints(namespaceId, card), includeMetadata);
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
        return JsonUtils.toObj(JsonUtils.toJson(source), AgentCard.class);
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
    
}
