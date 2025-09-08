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
 *
 */

package com.alibaba.nacos.console.handler.impl.remote.ai;

import com.alibaba.nacos.ai.form.a2a.admin.AgentDetailForm;
import com.alibaba.nacos.ai.form.a2a.admin.AgentForm;
import com.alibaba.nacos.ai.form.a2a.admin.AgentListForm;
import com.alibaba.nacos.ai.form.a2a.admin.AgentUpdateForm;
import com.alibaba.nacos.api.ai.model.a2a.AgentCard;
import com.alibaba.nacos.api.ai.model.a2a.AgentCardVersionInfo;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.console.handler.ai.A2aHandler;
import com.alibaba.nacos.console.handler.impl.ConditionFunctionEnabled;
import com.alibaba.nacos.console.handler.impl.remote.EnabledRemoteHandler;
import com.alibaba.nacos.console.handler.impl.remote.NacosMaintainerClientHolder;
import com.alibaba.nacos.core.model.form.PageForm;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Service;

/**
 * A2aRemoteHandler.
 *
 * @author KiteSoar
 */
@Service
@EnabledRemoteHandler
@Conditional(ConditionFunctionEnabled.ConditionAiEnabled.class)
public class A2aRemoteHandler implements A2aHandler {
    
    private final NacosMaintainerClientHolder clientHolder;
    
    public A2aRemoteHandler(NacosMaintainerClientHolder clientHolder) {
        this.clientHolder = clientHolder;
    }
    
    @Override
    public void registerAgent(AgentDetailForm form) throws NacosException {
        clientHolder.getAiMaintainerService().registerAgent(transferAgentCard(form));
    }
    
    @Override
    public AgentCardVersionInfo getAgentCardWithVersions(AgentForm form) throws NacosException {
        return clientHolder.getAiMaintainerService().getAgentCardWithVersions(form.getName(), form.getNamespaceId(),
                form.getRegistrationType());
    }
    
    @Override
    public void deleteAgent(AgentForm form) throws NacosException {
        clientHolder.getAiMaintainerService().deleteAgent(form.getName(), form.getNamespaceId());
    }
    
    @Override
    public void updateAgentCard(AgentUpdateForm form) throws NacosException {
        clientHolder.getAiMaintainerService().updateAgentCard(transferAgentCard(form), form.getNamespaceId());
    }
    
    @Override
    public Page<AgentCardVersionInfo> listAgents(AgentListForm agentListForm, PageForm pageForm) {
        return null;
    }
    
    private AgentCard transferAgentCard(AgentDetailForm form) {
        AgentCard agentCard = new AgentCard();
        agentCard.setProtocolVersion(form.getProtocolVersion());
        agentCard.setName(form.getName());
        agentCard.setDescription(form.getDescription());
        agentCard.setUrl(form.getUrl());
        agentCard.setPreferredTransport(form.getPreferredTransport());
        agentCard.setAdditionalInterfaces(form.getAdditionalInterfaces());
        agentCard.setIconUrl(form.getIconUrl());
        agentCard.setProvider(form.getProvider());
        agentCard.setVersion(form.getVersion());
        agentCard.setDocumentationUrl(form.getDocumentationUrl());
        agentCard.setCapabilities(form.getCapabilities());
        agentCard.setSecuritySchemes(form.getSecuritySchemes());
        agentCard.setSecurity(form.getSecurity());
        agentCard.setDefaultInputModes(form.getDefaultInputModes());
        agentCard.setDefaultOutputModes(form.getDefaultOutputModes());
        agentCard.setSkills(form.getSkills());
        agentCard.setSupportsAuthenticatedExtendedCard(form.getSupportsAuthenticatedExtendedCard());
        
        return agentCard;
    }
}
