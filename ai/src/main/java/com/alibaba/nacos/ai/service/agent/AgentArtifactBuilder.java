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

package com.alibaba.nacos.ai.service.agent;

import com.alibaba.nacos.ai.utils.AgentRequestUtil;
import com.alibaba.nacos.api.ai.model.a2a.AgentCard;
import com.alibaba.nacos.api.ai.model.agent.AgentCallInterface;
import com.alibaba.nacos.api.ai.model.agent.AgentVersionDetail;
import com.alibaba.nacos.common.utils.JacksonUtils;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Builds complete, version-pinned Agent artifact representations.
 *
 * @author Nacos
 */
public final class AgentArtifactBuilder {
    
    public static final String ARTIFACT_KIND_A2A_AGENT_CARD = "a2a-agent-card";
    
    public static final String ARTIFACT_KIND_NACOS_AGENT = "nacos-agent";
    
    private static final String A2A_PROTOCOL = "a2a";
    
    private AgentArtifactBuilder() {
    }
    
    /**
     * Find the valid A2A Agent Card owned by one exact Agent Version.
     *
     * @param agentName canonical Agent name
     * @param version exact Agent Version
     * @return normalized valid card, or {@code null} when unavailable
     */
    public static AgentCard findA2aAgentCard(String agentName, AgentVersionDetail version) {
        if (version == null || version.getCallInterfaces() == null) {
            return null;
        }
        for (AgentCallInterface callInterface : version.getCallInterfaces()) {
            if (callInterface == null || !A2A_PROTOCOL.equalsIgnoreCase(
                callInterface.getProtocol())) {
                continue;
            }
            try {
                AgentCard card = JacksonUtils.toObj(
                    JacksonUtils.toJson(callInterface.getNativeDescriptor()), AgentCard.class);
                AgentRequestUtil.validateAgentCard(card);
                if (Objects.equals(agentName, card.getName()) && Objects.equals(
                    version.getVersion(), card.getVersion())) {
                    return card;
                }
            } catch (Exception ignored) {
                // An invalid descriptor is not an exportable A2A representation.
            }
        }
        return null;
    }
    
    /**
     * Build the protocol-neutral Nacos Agent artifact defined by the shared schema.
     *
     * @param version exact Agent Version
     * @return schema-constrained artifact fields
     */
    public static Map<String, Object> buildNacosAgentArtifact(AgentVersionDetail version) {
        if (version == null) {
            throw new IllegalArgumentException("Agent Version must not be null");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("schemaVersion", "1.0");
        result.put("agentName", version.getAgentName());
        result.put("version", version.getVersion());
        result.put("contentDigest", version.getContentDigest());
        result.put("callInterfaces", version.getCallInterfaces());
        return result;
    }
}
