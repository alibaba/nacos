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
import com.alibaba.nacos.ai.form.a2a.admin.AgentDetailForm;
import com.alibaba.nacos.api.ai.model.a2a.AgentCard;
import com.alibaba.nacos.api.ai.model.a2a.AgentCardDetailInfo;
import com.alibaba.nacos.api.ai.model.a2a.AgentCardVersionInfo;
import com.alibaba.nacos.api.ai.model.a2a.AgentVersionDetail;

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
    
    /**
     * Build Agent Card from Agent Detail form.
     *
     * @param form agent detail form
     * @return Agent Card
     */
    public static AgentCard buildAgentCard(AgentDetailForm form) {
        AgentCard agentCard = new AgentCard();
        injectAgentCardInfo(agentCard, form);
        return agentCard;
    }
    
    /**
     * Build Agent Card Storage Info from Agent Detail form.
     *
     * @param form agent detail form
     * @return Agent Card Storage Info
     */
    public static AgentCardDetailInfo buildAgentCardDetailInfo(AgentDetailForm form) {
        AgentCardDetailInfo agentCardDetailInfo = new AgentCardDetailInfo();
        injectAgentCardInfo(agentCardDetailInfo, form);
        agentCardDetailInfo.setRegistrationType(form.getRegistrationType());
        return agentCardDetailInfo;
    }
    
    /**
     * Build Agent Card Storage Info from Agent Detail form.
     *
     * @param form agent detail form
     * @return Agent Card Version Info
     */
    public static AgentCardVersionInfo buildAgentCardVersionInfo(AgentDetailForm form, boolean isLatest) {
        AgentCardVersionInfo agentCardVersionInfo = new AgentCardVersionInfo();
        injectAgentCardInfo(agentCardVersionInfo, form);
        agentCardVersionInfo.setRegistrationType(form.getRegistrationType());
        if (isLatest) {
            agentCardVersionInfo.setLatestPublishedVersion(form.getVersion());
        }
        agentCardVersionInfo.setVersionDetails(Collections.singletonList(buildAgentVersionDetail(form, isLatest)));
        return agentCardVersionInfo;
    }
    
    /**
     * Build Agent version detail from Agent Detail form.
     *
     * @param form agent detail form
     * @return Agent Version Detail
     */
    public static AgentVersionDetail buildAgentVersionDetail(AgentDetailForm form, boolean isLatest) {
        AgentVersionDetail agentVersionDetail = new AgentVersionDetail();
        agentVersionDetail.setCreatedAt(getCurrentTime());
        agentVersionDetail.setUpdatedAt(getCurrentTime());
        agentVersionDetail.setVersion(form.getVersion());
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
    
    private static String getCurrentTime() {
        ZonedDateTime currentTime = ZonedDateTime.now(ZoneOffset.UTC);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(Constants.RELEASE_DATE_FORMAT);
        return currentTime.format(formatter);
    }
    
    private static void injectAgentCardInfo(AgentCard agentCard, AgentDetailForm form) {
        agentCard.setProtocolVersion(form.getProtocolVersion());
        agentCard.setName(form.getName());
        agentCard.setDescription(form.getDescription());
        agentCard.setUrl(form.getUrl());
        agentCard.setVersion(form.getVersion());
        agentCard.setPreferredTransport(form.getPreferredTransport());
        agentCard.setAdditionalInterfaces(form.getAdditionalInterfaces());
        agentCard.setIconUrl(form.getIconUrl());
        agentCard.setProvider(form.getProvider());
        agentCard.setCapabilities(form.getCapabilities());
        agentCard.setSecuritySchemes(form.getSecuritySchemes());
        agentCard.setSecurity(form.getSecurity());
        agentCard.setDefaultInputModes(form.getDefaultInputModes());
        agentCard.setDefaultOutputModes(form.getDefaultOutputModes());
        agentCard.setSkills(form.getSkills());
        agentCard.setSupportsAuthenticatedExtendedCard(form.getSupportsAuthenticatedExtendedCard());
        agentCard.setDocumentationUrl(form.getDocumentationUrl());
    }
}
