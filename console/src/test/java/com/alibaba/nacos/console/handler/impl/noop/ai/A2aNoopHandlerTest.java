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

package com.alibaba.nacos.console.handler.impl.noop.ai;

import com.alibaba.nacos.ai.form.a2a.admin.AgentCardForm;
import com.alibaba.nacos.ai.form.a2a.admin.AgentCardUpdateForm;
import com.alibaba.nacos.ai.form.a2a.admin.AgentForm;
import com.alibaba.nacos.ai.form.a2a.admin.AgentListForm;
import com.alibaba.nacos.api.ai.model.a2a.AgentCard;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.core.model.form.PageForm;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit tests for A2aNoopHandler.
 *
 * @author nacos
 */
public class A2aNoopHandlerTest {
    
    private static final String A2A_NOT_ENABLED_MESSAGE = "Nacos AI A2A module and API required both `naming` and `config` module.";
    
    private A2aNoopHandler a2aNoopHandler;
    
    @BeforeEach
    void setUp() {
        a2aNoopHandler = new A2aNoopHandler();
    }
    
    @Test
    void registerAgent() {
        AgentCard agentCard = new AgentCard();
        AgentCardForm form = new AgentCardForm();
        
        assertThrows(NacosApiException.class, () -> a2aNoopHandler.registerAgent(agentCard, form),
                A2A_NOT_ENABLED_MESSAGE);
    }
    
    @Test
    void getAgentCardWithVersions() {
        AgentForm form = new AgentForm();
        
        assertThrows(NacosApiException.class, () -> a2aNoopHandler.getAgentCardWithVersions(form),
                A2A_NOT_ENABLED_MESSAGE);
    }
    
    @Test
    void deleteAgent() {
        AgentForm form = new AgentForm();
        
        assertThrows(NacosApiException.class, () -> a2aNoopHandler.deleteAgent(form),
                A2A_NOT_ENABLED_MESSAGE);
    }
    
    @Test
    void updateAgentCard() {
        AgentCard agentCard = new AgentCard();
        AgentCardUpdateForm form = new AgentCardUpdateForm();
        
        assertThrows(NacosApiException.class, () -> a2aNoopHandler.updateAgentCard(agentCard, form),
                A2A_NOT_ENABLED_MESSAGE);
    }
    
    @Test
    void listAgents() {
        AgentListForm agentListForm = new AgentListForm();
        PageForm pageForm = new PageForm();
        
        assertThrows(NacosApiException.class, () -> a2aNoopHandler.listAgents(agentListForm, pageForm),
                A2A_NOT_ENABLED_MESSAGE);
    }
    
    @Test
    void listAgentVersions() {
        assertThrows(NacosApiException.class, () -> a2aNoopHandler.listAgentVersions("namespace", "agentName"),
                A2A_NOT_ENABLED_MESSAGE);
    }
}