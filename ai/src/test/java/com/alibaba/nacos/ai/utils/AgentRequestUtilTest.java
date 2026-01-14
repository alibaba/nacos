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

package com.alibaba.nacos.ai.utils;

import com.alibaba.nacos.ai.form.a2a.admin.AgentCardForm;
import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.api.ai.model.a2a.AgentCapabilities;
import com.alibaba.nacos.api.ai.model.a2a.AgentCard;
import com.alibaba.nacos.api.ai.remote.request.AbstractAgentRequest;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.common.utils.JacksonUtils;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AgentRequestUtilTest {
    
    @Test
    void testParseAgentCardSuccessfully() throws NacosApiException {
        // Given
        AgentCardForm agentCardForm = new AgentCardForm();
        AgentCard agentCard = createValidAgentCard();
        String agentCardJson = JacksonUtils.toJson(agentCard);
        agentCardForm.setAgentCard(agentCardJson);
        
        // When
        AgentCard result = AgentRequestUtil.parseAgentCard(agentCardForm);
        
        // Then
        assertNotNull(result);
        assertEquals(agentCard.getName(), result.getName());
        assertEquals(agentCard.getVersion(), result.getVersion());
        assertEquals(agentCard.getProtocolVersion(), result.getProtocolVersion());
        assertEquals(agentCard.getPreferredTransport(), result.getPreferredTransport());
        assertEquals(agentCard.getUrl(), result.getUrl());
        assertEquals(agentCard.getDescription(), result.getDescription());
    }
    
    @Test
    void testParseAgentCardWithInvalidJson() {
        // Given
        AgentCardForm agentCardForm = new AgentCardForm();
        agentCardForm.setAgentCard("{ invalid json }");
        
        // When & Then
        assertThrows(NacosApiException.class, () -> AgentRequestUtil.parseAgentCard(agentCardForm));
    }
    
    @Test
    void testValidateAgentCardWithValidCard() {
        // Given
        AgentCard agentCard = createValidAgentCard();
        
        // When & Then
        assertDoesNotThrow(() -> AgentRequestUtil.validateAgentCard(agentCard));
    }
    
    @Test
    void testValidateAgentCardWithMissingName() {
        // Given
        AgentCard agentCard = createValidAgentCard();
        agentCard.setName(null);
        
        // When & Then
        NacosApiException exception = assertThrows(NacosApiException.class,
                () -> AgentRequestUtil.validateAgentCard(agentCard));
        assertEquals("Required parameter `agentCard.name` not present", exception.getMessage());
    }
    
    @Test
    void testValidateAgentCardWithEmptyName() {
        // Given
        AgentCard agentCard = createValidAgentCard();
        agentCard.setName("");
        
        // When & Then
        NacosApiException exception = assertThrows(NacosApiException.class,
                () -> AgentRequestUtil.validateAgentCard(agentCard));
        assertEquals("Required parameter `agentCard.name` not present", exception.getMessage());
    }
    
    @Test
    void testValidateAgentCardWithMissingVersion() {
        // Given
        AgentCard agentCard = createValidAgentCard();
        agentCard.setVersion(null);
        
        // When & Then
        NacosApiException exception = assertThrows(NacosApiException.class,
                () -> AgentRequestUtil.validateAgentCard(agentCard));
        assertEquals("Required parameter `agentCard.version` not present", exception.getMessage());
    }
    
    @Test
    void testValidateAgentCardWithMissingProtocolVersion() {
        // Given
        AgentCard agentCard = createValidAgentCard();
        agentCard.setProtocolVersion(null);
        
        // When & Then
        NacosApiException exception = assertThrows(NacosApiException.class,
                () -> AgentRequestUtil.validateAgentCard(agentCard));
        assertEquals("Required parameter `agentCard.protocolVersion` not present", exception.getMessage());
    }
    
    @Test
    void testValidateAgentCardWithMissingPreferredTransport() {
        // Given
        AgentCard agentCard = createValidAgentCard();
        agentCard.setPreferredTransport(null);
        
        // When & Then
        NacosApiException exception = assertThrows(NacosApiException.class,
                () -> AgentRequestUtil.validateAgentCard(agentCard));
        assertEquals("Required parameter `agentCard.preferredTransport` not present", exception.getMessage());
    }
    
    @Test
    void testValidateAgentCardWithMissingUrl() {
        // Given
        AgentCard agentCard = createValidAgentCard();
        agentCard.setUrl(null);
        
        // When & Then
        NacosApiException exception = assertThrows(NacosApiException.class,
                () -> AgentRequestUtil.validateAgentCard(agentCard));
        assertEquals("Required parameter `agentCard.url` not present", exception.getMessage());
    }
    
    @Test
    void testValidateAgentCardWithNullDescriptionShouldSetEmptyString() throws NacosApiException {
        // Given
        AgentCard agentCard = createValidAgentCard();
        agentCard.setDescription(null);
        
        // When
        AgentRequestUtil.validateAgentCard(agentCard);
        
        // Then
        assertEquals("", agentCard.getDescription());
    }
    
    @Test
    void testValidateAgentCardWithNullCapabilitiesShouldSetDefault() throws NacosApiException {
        // Given
        AgentCard agentCard = createValidAgentCard();
        agentCard.setCapabilities(null);
        
        // When
        AgentRequestUtil.validateAgentCard(agentCard);
        
        // Then
        assertNotNull(agentCard.getCapabilities());
    }
    
    @Test
    void testValidateAgentCardWithNullDefaultInputModesShouldSetEmptyList() throws NacosApiException {
        // Given
        AgentCard agentCard = createValidAgentCard();
        agentCard.setDefaultInputModes(null);
        
        // When
        AgentRequestUtil.validateAgentCard(agentCard);
        
        // Then
        assertNotNull(agentCard.getDefaultInputModes());
        assertEquals(0, agentCard.getDefaultInputModes().size());
    }
    
    @Test
    void testValidateAgentCardWithNullDefaultOutputModesShouldSetEmptyList() throws NacosApiException {
        // Given
        AgentCard agentCard = createValidAgentCard();
        agentCard.setDefaultOutputModes(null);
        
        // When
        AgentRequestUtil.validateAgentCard(agentCard);
        
        // Then
        assertNotNull(agentCard.getDefaultOutputModes());
        assertEquals(0, agentCard.getDefaultOutputModes().size());
    }
    
    @Test
    void testValidateAgentCardWithNullSkillsShouldSetEmptyList() throws NacosApiException {
        // Given
        AgentCard agentCard = createValidAgentCard();
        agentCard.setSkills(null);
        
        // When
        AgentRequestUtil.validateAgentCard(agentCard);
        
        // Then
        assertNotNull(agentCard.getSkills());
        assertEquals(0, agentCard.getSkills().size());
    }
    
    @Test
    void testFillNamespaceIdWithEmptyNamespaceId() {
        // Given
        AbstractAgentRequest request = new AbstractAgentRequest() {
        };
        request.setNamespaceId("");
        
        // When
        AgentRequestUtil.fillNamespaceId(request);
        
        // Then
        assertEquals(AiConstants.A2a.A2A_DEFAULT_NAMESPACE, request.getNamespaceId());
    }
    
    @Test
    void testFillNamespaceIdWithNullNamespaceId() {
        // Given
        AbstractAgentRequest request = new AbstractAgentRequest() {
        };
        
        // When
        AgentRequestUtil.fillNamespaceId(request);
        
        // Then
        assertEquals(AiConstants.A2a.A2A_DEFAULT_NAMESPACE, request.getNamespaceId());
    }
    
    @Test
    void testFillNamespaceIdWithValidNamespaceId() {
        // Given
        String customNamespaceId = "custom-namespace";
        AbstractAgentRequest request = new AbstractAgentRequest() {
        };
        request.setNamespaceId(customNamespaceId);
        
        // When
        AgentRequestUtil.fillNamespaceId(request);
        
        // Then
        assertEquals(customNamespaceId, request.getNamespaceId());
    }
    
    private AgentCard createValidAgentCard() {
        AgentCard agentCard = new AgentCard();
        agentCard.setProtocolVersion("1.0");
        agentCard.setName("test-agent");
        agentCard.setVersion("1.0.0");
        agentCard.setPreferredTransport("JSONRPC");
        agentCard.setUrl("http://example.com/agent");
        agentCard.setDescription("Test Agent");
        agentCard.setCapabilities(new AgentCapabilities());
        agentCard.setDefaultInputModes(Collections.emptyList());
        agentCard.setDefaultOutputModes(Collections.emptyList());
        agentCard.setSkills(Collections.emptyList());
        return agentCard;
    }
}