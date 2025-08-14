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

import com.alibaba.nacos.ai.form.a2a.admin.AgentDetailForm;
import com.alibaba.nacos.ai.form.a2a.admin.AgentForm;
import com.alibaba.nacos.ai.form.a2a.admin.AgentListForm;
import com.alibaba.nacos.ai.form.a2a.admin.AgentUpdateForm;
import com.alibaba.nacos.api.ai.model.a2a.AgentCardVersionInfo;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.core.model.form.PageForm;

/**
 * A2a handler.
 *
 * @author KiteSoar
 */
public interface A2aHandler {
    
    /**
     * Register agent.
     *
     * @param form agent detail form
     * @throws NacosException nacos exception
     */
    void registerAgent(AgentDetailForm form) throws NacosException;
    
    /**
     * Get agent card.
     *
     * @param form agent form
     * @return agent card
     * @throws NacosException nacos exception
     */
    AgentCardVersionInfo getAgentCard(AgentForm form) throws NacosException;
    
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
     * @param form agent update form
     * @throws NacosException nacos exception
     */
    void updateAgentCard(AgentUpdateForm form) throws NacosException;

    /**
     * List agents.
     *
     * @param agentListForm agent list form
     * @param pageForm page form
     * @return agent card list
     * @throws NacosException nacos exception
     */
    Page<AgentCardVersionInfo> listAgents(AgentListForm agentListForm, PageForm pageForm);
}
