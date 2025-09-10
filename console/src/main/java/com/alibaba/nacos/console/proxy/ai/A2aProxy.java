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

package com.alibaba.nacos.console.proxy.ai;

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
import com.alibaba.nacos.core.model.form.PageForm;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * A2a proxy.
 *
 * @author KiteSoar
 */
@Component
public class A2aProxy {
    
    private final A2aHandler a2aHandler;
    
    public A2aProxy(A2aHandler a2aHandler) {
        this.a2aHandler = a2aHandler;
    }
    
    /**
     * Register agent card.
     *
     * @param agentCard     agent card to register
     * @param agentCardForm agent card form
     * @throws NacosException exception when register agent card
     */
    public void registerAgent(AgentCard agentCard, AgentCardForm agentCardForm) throws NacosException {
        a2aHandler.registerAgent(agentCard, agentCardForm);
    }
    
    public AgentCardDetailInfo getAgentCard(AgentForm form) throws NacosException {
        return a2aHandler.getAgentCardWithVersions(form);
    }
    
    public void deleteAgent(AgentForm form) throws NacosException {
        a2aHandler.deleteAgent(form);
    }
    
    public void updateAgentCard(AgentCard agentCard, AgentCardUpdateForm form) throws NacosException {
        a2aHandler.updateAgentCard(agentCard, form);
    }
    
    public Page<AgentCardVersionInfo> listAgents(AgentListForm agentListForm, PageForm pageForm) throws NacosException {
        return a2aHandler.listAgents(agentListForm, pageForm);
    }
    
    public List<AgentVersionDetail> listAgentVersions(String namespaceId, String name) throws NacosException {
        return a2aHandler.listAgentVersions(namespaceId, name);
    }
}
