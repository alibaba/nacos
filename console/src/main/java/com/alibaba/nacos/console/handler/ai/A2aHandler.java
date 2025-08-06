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

public interface A2aHandler {
    
    void registerAgent(AgentDetailForm form) throws NacosException;
    
    AgentCardVersionInfo getAgentCard(AgentForm form);
    
    void deleteAgent(AgentForm form);
    
    void updateAgentCard(AgentUpdateForm form) throws NacosException;

    Page<AgentCardVersionInfo> listAgents(AgentListForm agentListForm, PageForm pageForm);
}
