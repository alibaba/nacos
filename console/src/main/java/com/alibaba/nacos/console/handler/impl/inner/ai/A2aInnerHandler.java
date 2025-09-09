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

package com.alibaba.nacos.console.handler.impl.inner.ai;

import com.alibaba.nacos.ai.form.a2a.admin.AgentDetailForm;
import com.alibaba.nacos.ai.form.a2a.admin.AgentForm;
import com.alibaba.nacos.ai.form.a2a.admin.AgentListForm;
import com.alibaba.nacos.ai.form.a2a.admin.AgentUpdateForm;
import com.alibaba.nacos.ai.service.A2aServerOperationService;
import com.alibaba.nacos.api.ai.model.a2a.AgentCardDetailInfo;
import com.alibaba.nacos.api.ai.model.a2a.AgentCardVersionInfo;
import com.alibaba.nacos.api.ai.model.a2a.AgentVersionDetail;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.console.handler.ai.A2aHandler;
import com.alibaba.nacos.console.handler.impl.ConditionFunctionEnabled;
import com.alibaba.nacos.console.handler.impl.inner.EnabledInnerHandler;
import com.alibaba.nacos.core.model.form.PageForm;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * A2a inner handler.
 *
 * @author KiteSoar
 */
@Component
@EnabledInnerHandler
@Conditional(ConditionFunctionEnabled.ConditionAiEnabled.class)
public class A2aInnerHandler implements A2aHandler {
    
    private final A2aServerOperationService a2aServerOperationService;
    
    public A2aInnerHandler(A2aServerOperationService a2aServerOperationService) {
        this.a2aServerOperationService = a2aServerOperationService;
    }
    
    @Override
    public void registerAgent(AgentDetailForm form) throws NacosException {
        a2aServerOperationService.registerAgent(form);
    }
    
    @Override
    public AgentCardDetailInfo getAgentCardWithVersions(AgentForm form) throws NacosException {
        return a2aServerOperationService.getAgentCard(form);
    }
    
    @Override
    public void deleteAgent(AgentForm form) throws NacosException {
        a2aServerOperationService.deleteAgent(form);
    }
    
    @Override
    public void updateAgentCard(AgentUpdateForm form) throws NacosException {
        a2aServerOperationService.updateAgentCard(form);
    }

    @Override
    public Page<AgentCardVersionInfo> listAgents(AgentListForm agentListForm, PageForm pageForm) {
        return a2aServerOperationService.listAgents(agentListForm, pageForm);
    }
    
    @Override
    public List<AgentVersionDetail> listAgentVersions(String namespaceId, String name) throws NacosException {
        return a2aServerOperationService.listAgentVersions(namespaceId, name);
    }
}
