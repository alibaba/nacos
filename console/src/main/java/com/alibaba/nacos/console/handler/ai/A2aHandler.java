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

package com.alibaba.nacos.console.handler.ai;

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
import com.alibaba.nacos.core.model.form.PageForm;

import java.util.List;

/**
 * A2a handler.
 *
 * @author KiteSoar
 */
public interface A2aHandler {
    
    /**
     * Register agent.
     *
     * @param agentCard     registered Agent Card
     * @param agentCardForm agent card form
     * @throws NacosException nacos exception
     */
    void registerAgent(AgentCard agentCard, AgentCardForm agentCardForm) throws NacosException;
    
    /**
     * Get agent card with versions.
     *
     * @param form agent form
     * @return agent card
     * @throws NacosException nacos exception
     */
    AgentCardDetailInfo getAgentCardWithVersions(AgentForm form) throws NacosException;
    
    /**
     * Delete agent.
     *
     * @param form agent form
     * @throws NacosException nacos exception
     */
    void deleteAgent(AgentForm form) throws NacosException;
    
    /**
     * Update agent card.
     *
     * @param agentCard agent card to updated
     * @param form      agent update form
     * @throws NacosException nacos exception
     */
    void updateAgentCard(AgentCard agentCard, AgentCardUpdateForm form) throws NacosException;

    /**
     * List agents.
     *
     * @param agentListForm agent list form
     * @param pageForm page form
     * @return agent card list
     * @throws NacosException nacos exception
     */
    Page<AgentCardVersionInfo> listAgents(AgentListForm agentListForm, PageForm pageForm) throws NacosException;
    
    /**
     * List agent versions.
     * @param namespaceId namespace id of target agent
     * @param name        name of target agent
     * @return agent version detail list
     * @throws NacosException nacos exception
     */
    List<AgentVersionDetail> listAgentVersions(String namespaceId, String name) throws NacosException;
}
