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

package com.alibaba.nacos.ai.form.a2a.admin;

import com.alibaba.nacos.api.exception.api.NacosApiException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AgentListFormTest {
    
    @Test
    void testValidateWithAccurateSearch() throws NacosApiException {
        AgentListForm agentListForm = new AgentListForm();
        agentListForm.setAgentName("test-agent");
        agentListForm.setSearch("accurate");
        agentListForm.validate();
        // Should not throw exception
    }
    
    @Test
    void testValidateWithBlurSearch() throws NacosApiException {
        AgentListForm agentListForm = new AgentListForm();
        agentListForm.setAgentName("test-agent");
        agentListForm.setSearch("blur");
        agentListForm.validate();
        // Should not throw exception
    }
    
    @Test
    void testValidateWithUpperCaseSearch() throws NacosApiException {
        AgentListForm agentListForm = new AgentListForm();
        agentListForm.setAgentName("test-agent");
        agentListForm.setSearch("ACCURATE");
        agentListForm.validate();
        // Should not throw exception
        
        agentListForm.setSearch("BLUR");
        agentListForm.validate();
        // Should not throw exception
    }
    
    @Test
    void testValidateWithInvalidSearchShouldThrowException() {
        AgentListForm agentListForm = new AgentListForm();
        agentListForm.setAgentName("test-agent");
        agentListForm.setSearch("invalid");
        assertThrows(NacosApiException.class, agentListForm::validate);
    }
    
    @Test
    void testValidateWithNullSearchShouldThrowException() {
        AgentListForm agentListForm = new AgentListForm();
        agentListForm.setAgentName("test-agent");
        agentListForm.setSearch(null);
        assertThrows(NacosApiException.class, agentListForm::validate);
    }
    
    @Test
    void testValidateShouldFillDefaultNamespaceId() throws NacosApiException {
        AgentListForm agentListForm = new AgentListForm();
        agentListForm.setAgentName("test-agent");
        agentListForm.setSearch("accurate");
        agentListForm.validate();
        assertEquals("public", agentListForm.getNamespaceId());
    }
    
    @Test
    void testGetterAndSetter() {
        AgentListForm agentListForm = new AgentListForm();
        
        agentListForm.setNamespaceId("test-namespace");
        agentListForm.setAgentName("test-agent");
        agentListForm.setVersion("1.0.0");
        agentListForm.setRegistrationType("URL");
        agentListForm.setSearch("accurate");
        
        assertEquals("test-namespace", agentListForm.getNamespaceId());
        assertEquals("test-agent", agentListForm.getAgentName());
        assertEquals("1.0.0", agentListForm.getVersion());
        assertEquals("URL", agentListForm.getRegistrationType());
        assertEquals("accurate", agentListForm.getSearch());
    }
}