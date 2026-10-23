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

import com.alibaba.nacos.api.ai.model.a2a.AgentCapabilities;
import com.alibaba.nacos.api.ai.model.a2a.AgentCard;
import com.alibaba.nacos.api.ai.model.a2a.AgentInterface;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.api.utils.StringUtils;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Shared normalization of legacy and current A2A Cards for server and SDK adapters.
 *
 * @author Nacos
 */
public final class A2aAgentCardUtils {
    
    private A2aAgentCardUtils() {
    }
    
    /**
     * Validate and normalize a caller-isolated Card using the legacy server rules.
     * @param agentCard Card to normalize
     * @throws NacosApiException when required fields are missing
     */
    public static void validateAgentCard(AgentCard agentCard) throws NacosApiException {
        if (agentCard == null) {
            throw new NacosApiException(NacosException.INVALID_PARAM,
                ErrorCode.PARAMETER_MISSING, "AgentCard must not be null");
        }
        validateAgentCardField("name", agentCard.getName());
        validateAgentCardField("version", agentCard.getVersion());
        normalizeAgentCard(agentCard);
        boolean hasLegacyRequiredFields = isLegacyAgentCard(agentCard);
        boolean hasV1RequiredFields = isV1AgentCard(agentCard);
        if (!hasLegacyRequiredFields && !hasV1RequiredFields) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_MISSING,
                "Required parameter `agentCard.supportedInterfaces` not present, and old protocol fields "
                    + "(`agentCard.protocolVersion`, `agentCard.preferredTransport`, `agentCard.url`) are "
                    + "incomplete. Please prefer `agentCard.supportedInterfaces` for A2A 1.0.0.");
        }
        if (null == agentCard.getDescription()) {
            agentCard.setDescription(StringUtils.EMPTY);
        }
        if (null == agentCard.getCapabilities()) {
            agentCard.setCapabilities(new AgentCapabilities());
        }
        if (null == agentCard.getDefaultInputModes()) {
            agentCard.setDefaultInputModes(Collections.emptyList());
        }
        if (null == agentCard.getDefaultOutputModes()) {
            agentCard.setDefaultOutputModes(Collections.emptyList());
        }
        if (null == agentCard.getSkills()) {
            agentCard.setSkills(Collections.emptyList());
        }
    }
    
    private static void validateAgentCardField(String fieldName, String fieldValue)
        throws NacosApiException {
        if (StringUtils.isEmpty(fieldValue)) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_MISSING,
                "Required parameter `agentCard." + fieldName + "` not present");
        }
    }
    
    /**
     * Normalize supported and historical interface fields in place.
     * @param agentCard Card to normalize, or null
     */
    public static void normalizeAgentCard(AgentCard agentCard) {
        if (null == agentCard) {
            return;
        }
        List<AgentInterface> normalizedSupportedInterfaces =
            normalizeSupportedInterfaces(agentCard);
        if (!normalizedSupportedInterfaces.isEmpty()) {
            agentCard.setSupportedInterfaces(normalizedSupportedInterfaces);
            AgentInterface preferredInterface = normalizedSupportedInterfaces.get(0);
            agentCard.setUrl(preferredInterface.getUrl());
            agentCard.setPreferredTransport(preferredInterface.getProtocolBinding());
            agentCard.setProtocolVersion(preferredInterface.getProtocolVersion());
            if (normalizedSupportedInterfaces.size() > 1) {
                agentCard.setAdditionalInterfaces(
                    new ArrayList<>(normalizedSupportedInterfaces.subList(1,
                        normalizedSupportedInterfaces.size())));
            } else {
                agentCard.setAdditionalInterfaces(new ArrayList<>());
            }
        }
        normalizeExtendedAgentCard(agentCard);
    }
    
    private static List<AgentInterface> normalizeSupportedInterfaces(AgentCard agentCard) {
        List<AgentInterface> result = new ArrayList<>();
        if (null != agentCard.getSupportedInterfaces()) {
            for (AgentInterface each : agentCard.getSupportedInterfaces()) {
                AgentInterface normalized = normalizeAgentInterface(each);
                if (isValidAgentInterface(normalized)) {
                    result.add(normalized);
                }
            }
        }
        if (!result.isEmpty()) {
            return result;
        }
        if (isLegacyAgentCard(agentCard)) {
            AgentInterface preferred = new AgentInterface();
            preferred.setUrl(agentCard.getUrl());
            preferred.setProtocolBinding(agentCard.getPreferredTransport());
            preferred.setProtocolVersion(agentCard.getProtocolVersion());
            preferred.setTransport(agentCard.getPreferredTransport());
            result.add(preferred);
            if (null != agentCard.getAdditionalInterfaces()) {
                for (AgentInterface each : agentCard.getAdditionalInterfaces()) {
                    AgentInterface normalized = normalizeAgentInterface(each);
                    if (StringUtils.isEmpty(normalized.getProtocolVersion())) {
                        normalized.setProtocolVersion(agentCard.getProtocolVersion());
                    }
                    if (StringUtils.isEmpty(normalized.getProtocolBinding())) {
                        normalized.setProtocolBinding(agentCard.getPreferredTransport());
                    }
                    if (isValidAgentInterface(normalized)) {
                        result.add(normalized);
                    }
                }
            }
        }
        return result;
    }
    
    private static AgentInterface normalizeAgentInterface(AgentInterface agentInterface) {
        AgentInterface result = null == agentInterface ? new AgentInterface() : agentInterface;
        if (StringUtils.isEmpty(result.getProtocolBinding())
            && !StringUtils.isEmpty(result.getTransport())) {
            result.setProtocolBinding(result.getTransport());
        }
        if (StringUtils.isEmpty(result.getTransport())
            && !StringUtils.isEmpty(result.getProtocolBinding())) {
            result.setTransport(result.getProtocolBinding());
        }
        return result;
    }
    
    private static void normalizeExtendedAgentCard(AgentCard agentCard) {
        AgentCapabilities capabilities = agentCard.getCapabilities();
        if (null == capabilities) {
            capabilities = new AgentCapabilities();
            agentCard.setCapabilities(capabilities);
        }
        if (null != capabilities.getExtendedAgentCard()) {
            agentCard.setSupportsAuthenticatedExtendedCard(capabilities.getExtendedAgentCard());
            return;
        }
        if (null != agentCard.getSupportsAuthenticatedExtendedCard()) {
            capabilities.setExtendedAgentCard(agentCard.getSupportsAuthenticatedExtendedCard());
        }
    }
    
    private static boolean isLegacyAgentCard(AgentCard agentCard) {
        return !StringUtils.isEmpty(agentCard.getProtocolVersion()) && !StringUtils.isEmpty(
            agentCard.getPreferredTransport()) && !StringUtils.isEmpty(agentCard.getUrl());
    }
    
    private static boolean isV1AgentCard(AgentCard agentCard) {
        if (null == agentCard.getSupportedInterfaces()
            || agentCard.getSupportedInterfaces().isEmpty()) {
            return false;
        }
        for (AgentInterface each : agentCard.getSupportedInterfaces()) {
            if (!isValidAgentInterface(each)) {
                return false;
            }
        }
        return true;
    }
    
    private static boolean isValidAgentInterface(AgentInterface agentInterface) {
        return null != agentInterface && !StringUtils.isEmpty(agentInterface.getUrl())
            && !StringUtils.isEmpty(
                agentInterface.getProtocolBinding())
            && !StringUtils.isEmpty(agentInterface.getProtocolVersion());
    }
}
