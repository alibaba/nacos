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

import com.alibaba.nacos.ai.constant.Constants;
import com.alibaba.nacos.api.ai.model.a2a.AgentCard;
import com.alibaba.nacos.api.ai.model.a2a.AgentCardDetailInfo;
import com.alibaba.nacos.api.ai.model.a2a.AgentCardVersionInfo;
import com.alibaba.nacos.api.ai.model.a2a.AgentInterface;
import com.alibaba.nacos.api.ai.model.a2a.AgentVersionDetail;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.common.utils.StringUtils;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;

/**
 * Utils for Agent Card of A2A.
 *
 * @author xiweng.yy
 */
public class AgentCardUtil {
    
    private static final String AGENT_INTERFACE_URL_PATTERN = "%s://%s:%s";
    
    /**
     * Build Agent Card Storage Info from Agent Detail form.
     *
     * @param agentCard agent card
     * @return Agent Card Storage Info
     */
    public static AgentCardDetailInfo buildAgentCardDetailInfo(AgentCard agentCard, String registrationType) {
        AgentCardDetailInfo agentCardDetailInfo = new AgentCardDetailInfo();
        copyAgentCardInfo(agentCardDetailInfo, agentCard);
        agentCardDetailInfo.setRegistrationType(registrationType);
        return agentCardDetailInfo;
    }
    
    /**
     * Build Agent Card Storage Info from AgentCard.
     *
     * @param agentCard agent detail form
     * @param registrationType target registrationType
     * @param isLatest is latest version
     * @return Agent Card Version Info
     */
    public static AgentCardVersionInfo buildAgentCardVersionInfo(AgentCard agentCard, String registrationType,
            boolean isLatest) {
        AgentCardVersionInfo agentCardVersionInfo = new AgentCardVersionInfo();
        copyAgentCardInfo(agentCardVersionInfo, agentCard);
        agentCardVersionInfo.setRegistrationType(registrationType);
        if (isLatest) {
            agentCardVersionInfo.setLatestPublishedVersion(agentCard.getVersion());
        }
        agentCardVersionInfo.setVersionDetails(Collections.singletonList(buildAgentVersionDetail(agentCard, isLatest)));
        return agentCardVersionInfo;
    }
    
    /**
     * Build Agent version detail from Agent Detail form.
     *
     * @param agentCard agent detail form
     * @return Agent Version Detail
     */
    public static AgentVersionDetail buildAgentVersionDetail(AgentCard agentCard, boolean isLatest) {
        AgentVersionDetail agentVersionDetail = new AgentVersionDetail();
        agentVersionDetail.setCreatedAt(getCurrentTime());
        agentVersionDetail.setUpdatedAt(getCurrentTime());
        agentVersionDetail.setVersion(agentCard.getVersion());
        agentVersionDetail.setLatest(isLatest);
        return agentVersionDetail;
    }
    
    /**
     * Update update time of agent version detail.
     *
     * @param versionDetail agent version detail
     */
    public static void updateUpdateTime(AgentVersionDetail versionDetail) {
        versionDetail.setUpdatedAt(getCurrentTime());
    }
    
    /**
     * Build {@link AgentInterface} from service {@link Instance}.
     *
     * @param instance service instance.
     * @return agent interface (endpoint)
     */
    public static AgentInterface buildAgentInterface(Instance instance) {
        AgentInterface agentInterface = new AgentInterface();
        boolean isSupportTls = Boolean.parseBoolean(
                instance.getMetadata().get(Constants.A2A.NACOS_AGENT_ENDPOINT_SUPPORT_TLS));
        String protocol = isSupportTls ? Constants.PROTOCOL_TYPE_HTTPS : Constants.PROTOCOL_TYPE_HTTP;
        String url = String.format(AGENT_INTERFACE_URL_PATTERN, protocol, instance.getIp(), instance.getPort());
        String path = instance.getMetadata().get(Constants.A2A.AGENT_ENDPOINT_PATH_KEY);
        if (StringUtils.isNotBlank(path)) {
            url += path.startsWith("/") ? path : "/" + path;
        }
        agentInterface.setUrl(url);
        agentInterface.setTransport(instance.getMetadata().get(Constants.A2A.AGENT_ENDPOINT_TRANSPORT_KEY));
        return agentInterface;
    }
    
    private static String getCurrentTime() {
        ZonedDateTime currentTime = ZonedDateTime.now(ZoneOffset.UTC);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(Constants.RELEASE_DATE_FORMAT);
        return currentTime.format(formatter);
    }
    
    private static void copyAgentCardInfo(AgentCard target, AgentCard source) {
        target.setProtocolVersion(source.getProtocolVersion());
        target.setName(source.getName());
        target.setDescription(source.getDescription());
        target.setUrl(source.getUrl());
        target.setVersion(source.getVersion());
        target.setPreferredTransport(source.getPreferredTransport());
        target.setAdditionalInterfaces(source.getAdditionalInterfaces());
        target.setIconUrl(source.getIconUrl());
        target.setProvider(source.getProvider());
        target.setCapabilities(source.getCapabilities());
        target.setSecuritySchemes(source.getSecuritySchemes());
        target.setSecurity(source.getSecurity());
        target.setDefaultInputModes(source.getDefaultInputModes());
        target.setDefaultOutputModes(source.getDefaultOutputModes());
        target.setSkills(source.getSkills());
        target.setSupportsAuthenticatedExtendedCard(source.getSupportsAuthenticatedExtendedCard());
        target.setDocumentationUrl(source.getDocumentationUrl());
    }
}
