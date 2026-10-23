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

package com.alibaba.nacos.api.ai.converter;

import com.alibaba.nacos.api.ai.model.a2a.AgentCard;
import com.alibaba.nacos.api.ai.model.a2a.AgentInterface;
import com.alibaba.nacos.api.ai.model.agent.AgentCallInterface;
import com.alibaba.nacos.api.ai.model.agent.AgentDiscoveryResult;
import com.alibaba.nacos.api.ai.model.agent.Endpoint;
import com.alibaba.nacos.api.ai.model.agent.EndpointSet;
import com.alibaba.nacos.api.ai.utils.A2aAgentCardUtils;
import com.alibaba.nacos.api.ai.utils.A2aEndpointUtils;
import com.alibaba.nacos.api.ai.utils.AgentWatchLogUtils;
import com.alibaba.nacos.api.ai.utils.RadModelValidator;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.utils.json.JsonUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Stateless A2A projection, separate from legacy facade selection and fallback rules.
 *
 * @author Nacos
 */
final class DefaultA2aAgentConverter implements AgentConverter<AgentCard> {
    
    private static final Logger LOGGER = Logger.getLogger(AgentConverters.class.getName());
    
    @Override
    public AgentCard convert(AgentDiscoveryResult source) {
        String stage = "discovery snapshot";
        try {
            RadModelValidator.validate(source);
            stage = "A2A interface";
            AgentCallInterface call = a2aInterface(source);
            stage = "nativeDescriptor";
            AgentCard card = descriptor(call);
            A2aAgentCardUtils.validateAgentCard(card);
            stage = "nativeDescriptor identity";
            if (!source.getAgentName().equals(card.getName())) {
                throw new IllegalArgumentException(
                    "nativeDescriptor.name does not match agentName");
            }
            if (!source.getVersion().equals(card.getVersion())) {
                throw new IllegalArgumentException(
                    "nativeDescriptor.version does not match version");
            }
            stage = "endpointSets";
            List<AgentInterface> interfaces = interfaces(call);
            AgentInterface first = interfaces.get(0);
            card.setSupportedInterfaces(interfaces);
            card.setAdditionalInterfaces(new ArrayList<AgentInterface>(interfaces));
            card.setUrl(first.getUrl());
            card.setPreferredTransport(first.getProtocolBinding());
            card.setProtocolVersion(first.getProtocolVersion());
            return card;
        } catch (NacosException | RuntimeException cause) {
            IllegalArgumentException failure = new IllegalArgumentException(
                "Cannot convert A2A Agent [" + identity(source) + "] at " + stage + ": "
                    + cause.getMessage(),
                cause);
            logFailure(source, stage, cause);
            throw failure;
        }
    }
    
    private AgentCallInterface a2aInterface(AgentDiscoveryResult source) {
        for (AgentCallInterface call : source.getCallInterfaces()) {
            if ("a2a".equals(call.getProtocol())) {
                return call;
            }
        }
        throw new IllegalArgumentException("No A2A CallInterface in discovery snapshot");
    }
    
    private AgentCard descriptor(AgentCallInterface call) {
        if (!(call.getNativeDescriptor() instanceof Map)) {
            throw new IllegalArgumentException("nativeDescriptor must be an A2A JSON object");
        }
        try {
            return JsonUtils.toObj(JsonUtils.toJson(call.getNativeDescriptor()), AgentCard.class);
        } catch (RuntimeException cause) {
            throw new IllegalArgumentException("nativeDescriptor cannot be decoded as AgentCard",
                cause);
        }
    }
    
    private List<AgentInterface> interfaces(AgentCallInterface call) {
        for (EndpointSet set : call.getEndpointSets()) {
            if (!set.getEndpoints().isEmpty()) {
                List<AgentInterface> result = new ArrayList<AgentInterface>();
                for (Endpoint endpoint : set.getEndpoints()) {
                    result.add(
                        A2aEndpointUtils.toAgentInterface(endpoint, call.getProtocolVersion()));
                }
                return result;
            }
        }
        throw new IllegalArgumentException("No addresses in retained endpointSets");
    }
    
    private String identity(AgentDiscoveryResult source) {
        if (source == null) {
            return "null";
        }
        return source.getAgentName() + "/" + source.getVersion();
    }
    
    private void logFailure(AgentDiscoveryResult source, String stage, Exception cause) {
        try {
            // API has no logging-framework dependency. Reuse RAD's safe input renderer;
            // parser exception messages may contain descriptor values and are not logged.
            LOGGER.log(Level.WARNING, "A2A conversion failed at {0}; input={1}; causeType={2}",
                new Object[] {stage, AgentWatchLogUtils.describeResult(source),
                    cause.getClass().getName()});
        } catch (RuntimeException ignored) {
            // Diagnostic rendering or a user logging handler must not replace the failure.
        }
    }
}
