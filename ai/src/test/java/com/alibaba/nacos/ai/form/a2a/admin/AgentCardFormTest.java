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

class AgentCardFormTest {
    
    @Test
    void testValidateSuccess() throws NacosApiException {
        AgentCardForm agentCardForm = new AgentCardForm();
        agentCardForm.setAgentName("test-agent");
        agentCardForm.setAgentCard("{\"name\":\"test-agent\"}");
        agentCardForm.validate();
        // Should not throw exception
    }
    
    @Test
    void testValidateWithEmptyAgentCardShouldThrowException() {
        AgentCardForm agentCardForm = new AgentCardForm();
        agentCardForm.setAgentName("test-agent");
        agentCardForm.setAgentCard("");
        assertThrows(NacosApiException.class, agentCardForm::validate);
    }
    
    @Test
    void testValidateWithNullAgentCardShouldThrowException() {
        AgentCardForm agentCardForm = new AgentCardForm();
        agentCardForm.setAgentName("test-agent");
        agentCardForm.setAgentCard(null);
        assertThrows(NacosApiException.class, agentCardForm::validate);
    }
    
    @Test
    void testValidateShouldFillDefaultNamespaceIdAndRegistrationType() throws NacosApiException {
        AgentCardForm agentCardForm = new AgentCardForm();
        agentCardForm.setAgentName("test-agent");
        agentCardForm.setAgentCard("{\"name\":\"test-agent\"}");
        agentCardForm.validate();
        assertEquals("public", agentCardForm.getNamespaceId());
        assertEquals("URL", agentCardForm.getRegistrationType());
    }
    
    @Test
    void testValidateWithValidRegistrationType() throws NacosApiException {
        AgentCardForm agentCardForm = new AgentCardForm();
        agentCardForm.setAgentName("test-agent");
        agentCardForm.setAgentCard("{\"name\":\"test-agent\"}");
        agentCardForm.setRegistrationType("URL");
        agentCardForm.validate();
        // Should not throw exception
        
        agentCardForm.setRegistrationType("SERVICE");
        agentCardForm.validate();
        // Should not throw exception
    }
    
    @Test
    void testValidateWithInvalidRegistrationTypeShouldThrowException() {
        AgentCardForm agentCardForm = new AgentCardForm();
        agentCardForm.setAgentName("test-agent");
        agentCardForm.setAgentCard("{\"name\":\"test-agent\"}");
        agentCardForm.setRegistrationType("INVALID");
        assertThrows(NacosApiException.class, agentCardForm::validate);
    }
    
    @Test
    void testFillDefaultRegistrationType() {
        AgentCardForm agentCardForm = new AgentCardForm();
        agentCardForm.fillDefaultRegistrationType();
        assertEquals("URL", agentCardForm.getRegistrationType());
    }
    
    @Test
    void testFillDefaultRegistrationTypeWithExistingValue() {
        AgentCardForm agentCardForm = new AgentCardForm();
        agentCardForm.setRegistrationType("SERVICE");
        agentCardForm.fillDefaultRegistrationType();
        assertEquals("SERVICE", agentCardForm.getRegistrationType());
    }
    
    @Test
    void testGetterAndSetter() {
        AgentCardForm agentCardForm = new AgentCardForm();
        
        agentCardForm.setNamespaceId("test-namespace");
        agentCardForm.setAgentName("test-agent");
        agentCardForm.setVersion("1.0.0");
        agentCardForm.setRegistrationType("SERVICE");
        agentCardForm.setAgentCard("{\"name\":\"test-agent\"}");
        
        assertEquals("test-namespace", agentCardForm.getNamespaceId());
        assertEquals("test-agent", agentCardForm.getAgentName());
        assertEquals("1.0.0", agentCardForm.getVersion());
        assertEquals("SERVICE", agentCardForm.getRegistrationType());
        assertEquals("{\"name\":\"test-agent\"}", agentCardForm.getAgentCard());
    }
}