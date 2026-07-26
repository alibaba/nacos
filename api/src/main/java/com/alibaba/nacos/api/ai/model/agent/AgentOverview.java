/*
 * Copyright 1999-2026 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.api.ai.model.agent;

import com.alibaba.nacos.api.model.Page;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.io.Serializable;

/**
 * Agent management overview with a bounded version page.
 *
 * @author Nacos
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AgentOverview implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    private Agent agent;
    
    private Page<AgentVersionSummary> versionPage;
    
    public Agent getAgent() {
        return agent;
    }
    
    public void setAgent(Agent agent) {
        this.agent = agent;
    }
    
    public Page<AgentVersionSummary> getVersionPage() {
        return versionPage;
    }
    
    public void setVersionPage(Page<AgentVersionSummary> versionPage) {
        this.versionPage = versionPage;
    }
}
