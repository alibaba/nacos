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

import com.alibaba.nacos.ai.constant.Constants;
import com.alibaba.nacos.ai.form.a2a.admin.AgentCardForm;
import com.alibaba.nacos.ai.form.a2a.admin.AgentCardUpdateForm;
import com.alibaba.nacos.ai.form.a2a.admin.AgentForm;
import com.alibaba.nacos.ai.form.a2a.admin.AgentListForm;
import com.alibaba.nacos.api.ai.model.a2a.AgentCard;
import com.alibaba.nacos.api.ai.model.a2a.AgentCardDetailInfo;
import com.alibaba.nacos.api.ai.model.a2a.AgentCardVersionInfo;
import com.alibaba.nacos.api.ai.model.a2a.AgentVersionDetail;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.console.handler.ai.A2aHandler;
import com.alibaba.nacos.console.handler.impl.ConditionFunctionEnabled;
import com.alibaba.nacos.console.handler.impl.remote.EnabledRemoteHandler;
import com.alibaba.nacos.console.handler.impl.remote.NacosMaintainerClientHolder;
import com.alibaba.nacos.core.model.form.PageForm;
import com.alibaba.nacos.maintainer.client.ai.AiMaintainerService;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Service;

import java.util.List;

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
    public void registerAgent(AgentCard agentCard, AgentCardForm agentCardForm) throws NacosException {
        clientHolder.getAiMaintainerService()
                .registerAgent(agentCard, agentCardForm.getNamespaceId(), agentCardForm.getRegistrationType());
    }
    
    @Override
    public AgentCardDetailInfo getAgentCardWithVersions(AgentForm form) throws NacosException {
        return clientHolder.getAiMaintainerService()
                .getAgentCard(form.getAgentName(), form.getNamespaceId(), form.getRegistrationType());
    }
    
    @Override
    public void deleteAgent(AgentForm form) throws NacosException {
        clientHolder.getAiMaintainerService().deleteAgent(form.getAgentName(), form.getNamespaceId());
    }
    
    @Override
    public void updateAgentCard(AgentCard agentCard, AgentCardUpdateForm form) throws NacosException {
        clientHolder.getAiMaintainerService()
                .updateAgentCard(agentCard, form.getNamespaceId(), form.getSetAsLatest(), form.getRegistrationType());
    }
    
    @Override
    public Page<AgentCardVersionInfo> listAgents(AgentListForm agentListForm, PageForm pageForm) throws NacosException {
        AiMaintainerService aiMaintainerService = clientHolder.getAiMaintainerService();
        return Constants.MCP_LIST_SEARCH_BLUR.equalsIgnoreCase(agentListForm.getSearch())
                ? aiMaintainerService.searchAgentCardsByName(agentListForm.getNamespaceId(), agentListForm.getAgentName(),
                pageForm.getPageNo(), pageForm.getPageSize())
                : aiMaintainerService.listAgentCards(agentListForm.getNamespaceId(), agentListForm.getAgentName(),
                        pageForm.getPageNo(), pageForm.getPageSize());
    }
    
    @Override
    public List<AgentVersionDetail> listAgentVersions(String namespaceId, String name) throws NacosException {
        return clientHolder.getAiMaintainerService().listAllVersionOfAgent(name, namespaceId);
    }
}
