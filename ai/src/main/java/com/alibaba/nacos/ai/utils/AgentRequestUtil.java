/*
 * Copyright 1999-2025 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.ai.utils;

import com.alibaba.nacos.ai.form.a2a.admin.AgentCardForm;
import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.api.ai.model.a2a.AgentCapabilities;
import com.alibaba.nacos.api.ai.model.a2a.AgentCard;
import com.alibaba.nacos.api.ai.remote.request.AbstractAgentRequest;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.exception.runtime.NacosDeserializationException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Agent and AgentCard request util.
 *
 * @author xiweng.yy
 */
public class AgentRequestUtil {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(McpRequestUtil.class);
    
    /**
     * Parse Agent card request form to {@link AgentCard}.
     *
     * @param agentCardForm agent card request.
     * @return agent card
     * @throws NacosApiException if parse failed or request parameter is conflicted.
     */
    public static AgentCard parseAgentCard(AgentCardForm agentCardForm) throws NacosApiException {
        try {
            AgentCard result = JacksonUtils.toObj(agentCardForm.getAgentCard(), new TypeReference<>() {
            });
            validateAgentCard(result);
            return result;
        } catch (NacosDeserializationException e) {
            LOGGER.error(String.format("Deserialize %s from %s failed, ", AgentCard.class.getSimpleName(),
                    agentCardForm.getAgentCard()), e);
            throw new NacosApiException(NacosApiException.INVALID_PARAM, ErrorCode.PARAMETER_VALIDATE_ERROR,
                    "agentCard is invalid. Can't be parsed.");
        }
    }
    
    /**
     * Validate agent card is legal.
     *
     * @param agentCard agent card
     * @throws NacosApiException if agent card is illegal.
     */
    public static void validateAgentCard(AgentCard agentCard) throws NacosApiException {
        validateAgentCardField("name", agentCard.getName());
        validateAgentCardField("version", agentCard.getVersion());
        validateAgentCardField("protocolVersion", agentCard.getProtocolVersion());
        validateAgentCardField("preferredTransport", agentCard.getPreferredTransport());
        validateAgentCardField("url", agentCard.getUrl());
        if (null == agentCard.getDescription()) {
            agentCard.setDescription(StringUtils.EMPTY);
        }
        if (null == agentCard.getCapabilities()) {
            agentCard.setCapabilities(new AgentCapabilities());
        }
        if (null == agentCard.getDefaultInputModes()) {
            agentCard.setDefaultInputModes(List.of());
        }
        if (null == agentCard.getDefaultOutputModes()) {
            agentCard.setDefaultOutputModes(List.of());
        }
        if (null == agentCard.getSkills()) {
            agentCard.setSkills(List.of());
        }
    }
    
    /**
     * If request contains valid namespaceId, do nothing. If not, fill default namespaceId.
     *
     * @param request agent request
     */
    public static void fillNamespaceId(AbstractAgentRequest request) {
        if (StringUtils.isEmpty(request.getNamespaceId())) {
            request.setNamespaceId(AiConstants.A2a.A2A_DEFAULT_NAMESPACE);
        }
    }
    
    private static void validateAgentCardField(String fieldName, String fieldValue) throws NacosApiException {
        if (StringUtils.isEmpty(fieldValue)) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_MISSING,
                    "Required parameter `agentCard." + fieldName + "` not present");
        }
    }
}
