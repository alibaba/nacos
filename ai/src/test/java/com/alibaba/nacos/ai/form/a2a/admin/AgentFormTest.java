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

class AgentFormTest {
    
    @Test
    void testValidateSuccess() throws NacosApiException {
        AgentForm agentForm = new AgentForm();
        agentForm.setAgentName("test-agent");
        agentForm.validate();
        // Should not throw exception
    }
    
    @Test
    void testValidateWithEmptyNameShouldThrowException() {
        AgentForm agentForm = new AgentForm();
        assertThrows(NacosApiException.class, agentForm::validate);
    }
    
    @Test
    void testValidateWithNullNameShouldThrowException() {
        AgentForm agentForm = new AgentForm();
        agentForm.setAgentName(null);
        assertThrows(NacosApiException.class, agentForm::validate);
    }
    
    @Test
    void testFillDefaultNamespaceId() {
        AgentForm agentForm = new AgentForm();
        agentForm.fillDefaultNamespaceId();
        assertEquals("public", agentForm.getNamespaceId());
    }
    
    @Test
    void testFillDefaultNamespaceIdWithExistingValue() {
        AgentForm agentForm = new AgentForm();
        agentForm.setNamespaceId("test-namespace");
        agentForm.fillDefaultNamespaceId();
        assertEquals("test-namespace", agentForm.getNamespaceId());
    }
    
    @Test
    void testValidateShouldFillDefaultNamespaceId() throws NacosApiException {
        AgentForm agentForm = new AgentForm();
        agentForm.setAgentName("test-agent");
        agentForm.validate();
        assertEquals("public", agentForm.getNamespaceId());
    }
    
    @Test
    void testGetterAndSetter() {
        AgentForm agentForm = new AgentForm();
        
        agentForm.setNamespaceId("test-namespace");
        agentForm.setAgentName("test-agent");
        agentForm.setVersion("1.0.0");
        agentForm.setRegistrationType("URL");
        
        assertEquals("test-namespace", agentForm.getNamespaceId());
        assertEquals("test-agent", agentForm.getAgentName());
        assertEquals("1.0.0", agentForm.getVersion());
        assertEquals("URL", agentForm.getRegistrationType());
    }
}