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

package com.alibaba.nacos.api.ai.model.a2a;

import com.alibaba.nacos.api.remote.request.BasicRequestTest;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Map;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentCardTest extends BasicRequestTest {
    
    @Test
    void testSerialize() throws JsonProcessingException {
        AgentCard agentCard = getAgentCard();
        
        String json = mapper.writeValueAsString(agentCard);
        assertNotNull(json);
        assertTrue(json.contains("\"protocolVersion\":\"1.0\""));
        assertTrue(json.contains("\"name\":\"test agent\""));
        assertTrue(json.contains("\"description\":\"test description\""));
        assertTrue(json.contains("\"version\":\"1.0.0\""));
        assertTrue(json.contains("\"iconUrl\":\"http://test.com/icon.png\""));
        assertTrue(json.contains("\"url\":\"http://test.com/agent\""));
        assertTrue(json.contains("\"preferredTransport\":\"JSONRPC\""));
        assertTrue(json.contains("\"documentationUrl\":\"http://test.com/docs\""));
        assertTrue(json.contains("\"defaultInputModes\":[\"text\",\"voice\"]"));
        assertTrue(json.contains("\"defaultOutputModes\":[\"text\",\"image\"]"));
        assertTrue(json.contains("\"supportsAuthenticatedExtendedCard\":true"));
        assertTrue(json.contains("\"provider\":{\"organization\":\"test-org\""));
        assertTrue(json.contains("\"securitySchemes\":{\"default\":{\"type\":\"apiKey\""));
    }
    
    @Test
    void testDeserialize() throws JsonProcessingException {
        String json = "{\"protocolVersion\":\"1.0\",\"name\":\"test agent\",\"description\":\"test description\","
                + "\"version\":\"1.0.0\",\"iconUrl\":\"http://test.com/icon.png\","
                + "\"url\":\"http://test.com/agent\",\"preferredTransport\":\"JSONRPC\","
                + "\"documentationUrl\":\"http://test.com/docs\","
                + "\"defaultInputModes\":[\"text\",\"voice\"],"
                + "\"defaultOutputModes\":[\"text\",\"image\"],"
                + "\"supportsAuthenticatedExtendedCard\":true,"
                + "\"capabilities\":{\"streaming\":true},"
                + "\"provider\":{\"organization\":\"test-org\",\"url\":\"http://test.org\"},"
                + "\"securitySchemes\":{\"default\":{\"type\":\"apiKey\",\"name\":\"Authorization\",\"in\":\"header\"}}}";
        
        AgentCard agentCard = mapper.readValue(json, AgentCard.class);
        assertNotNull(agentCard);
        assertEquals("1.0", agentCard.getProtocolVersion());
        assertEquals("test agent", agentCard.getName());
        assertEquals("test description", agentCard.getDescription());
        assertEquals("1.0.0", agentCard.getVersion());
        assertEquals("http://test.com/icon.png", agentCard.getIconUrl());
        assertEquals("http://test.com/agent", agentCard.getUrl());
        assertEquals("JSONRPC", agentCard.getPreferredTransport());
        assertEquals("http://test.com/docs", agentCard.getDocumentationUrl());
        assertEquals(2, agentCard.getDefaultInputModes().size());
        assertEquals("text", agentCard.getDefaultInputModes().get(0));
        assertEquals("voice", agentCard.getDefaultInputModes().get(1));
        assertEquals(2, agentCard.getDefaultOutputModes().size());
        assertEquals("text", agentCard.getDefaultOutputModes().get(0));
        assertEquals("image", agentCard.getDefaultOutputModes().get(1));
        assertEquals(true, agentCard.getSupportsAuthenticatedExtendedCard());
        assertNotNull(agentCard.getCapabilities());
        assertEquals(true, agentCard.getCapabilities().getStreaming());
        assertNotNull(agentCard.getProvider());
        assertEquals("test-org", agentCard.getProvider().getOrganization());
        assertEquals("http://test.org", agentCard.getProvider().getUrl());
        assertNotNull(agentCard.getSecuritySchemes());
        assertEquals("apiKey", agentCard.getSecuritySchemes().get("default").get("type"));
        assertEquals("Authorization", agentCard.getSecuritySchemes().get("default").get("name"));
        assertEquals("header", agentCard.getSecuritySchemes().get("default").get("in"));
    }
    
    @Test
    void testEqualsAndHashCode() {
        AgentCard card1 = new AgentCard();
        card1.setProtocolVersion("1.0");
        card1.setName("test agent");
        card1.setDescription("test description");
        card1.setVersion("1.0.0");
        card1.setIconUrl("http://test.com/icon.png");
        card1.setUrl("http://test.com/agent");
        card1.setPreferredTransport("JSONRPC");
        
        AgentCard card2 = new AgentCard();
        card2.setProtocolVersion("1.0");
        card2.setName("test agent");
        card2.setDescription("test description");
        card2.setVersion("1.0.0");
        card2.setIconUrl("http://test.com/icon.png");
        card2.setUrl("http://test.com/agent");
        card2.setPreferredTransport("JSONRPC");
        
        AgentCard card3 = new AgentCard();
        card3.setProtocolVersion("2.0");
        
        assertEquals(card1, card1);
        assertEquals(card1, card2);
        assertEquals(card1.hashCode(), card2.hashCode());
        assertNotEquals(card1, card3);
        assertNotEquals(card1.hashCode(), card3.hashCode());
        assertNotEquals(card1, null);
        assertNotEquals(card1, new Object());
    }
    
    @Test
    void testAdditionalInterfaces() {
        AgentInterface agentInterface = new AgentInterface();
        agentInterface.setUrl("http://test.com/interface");
        agentInterface.setTransport("JSONRPC");
        
        List<AgentInterface> interfaces = new ArrayList<>();
        interfaces.add(agentInterface);
        
        AgentCard agentCard = new AgentCard();
        agentCard.setAdditionalInterfaces(interfaces);
        
        assertNotNull(agentCard.getAdditionalInterfaces());
        assertEquals(1, agentCard.getAdditionalInterfaces().size());
        assertEquals("http://test.com/interface", agentCard.getAdditionalInterfaces().get(0).getUrl());
        assertEquals("JSONRPC", agentCard.getAdditionalInterfaces().get(0).getTransport());
    }
    
    @Test
    void testSecurity() {
        Map<String, List<String>> securityMap = new HashMap<>();
        List<String> scopes = new ArrayList<>();
        scopes.add("read");
        scopes.add("write");
        securityMap.put("oauth2", scopes);
        
        List<Map<String, List<String>>> security = new ArrayList<>();
        security.add(securityMap);
        
        AgentCard agentCard = new AgentCard();
        agentCard.setSecurity(security);
        
        assertNotNull(agentCard.getSecurity());
        assertEquals(1, agentCard.getSecurity().size());
        assertEquals(1, agentCard.getSecurity().get(0).size());
        assertTrue(agentCard.getSecurity().get(0).containsKey("oauth2"));
        assertEquals(2, agentCard.getSecurity().get(0).get("oauth2").size());
        assertEquals("read", agentCard.getSecurity().get(0).get("oauth2").get(0));
        assertEquals("write", agentCard.getSecurity().get(0).get("oauth2").get(1));
    }
    
    private static AgentCard getAgentCard() {
        AgentCard agentCard = new AgentCard();
        agentCard.setProtocolVersion("1.0");
        agentCard.setName("test agent");
        agentCard.setDescription("test description");
        agentCard.setVersion("1.0.0");
        agentCard.setIconUrl("http://test.com/icon.png");
        agentCard.setUrl("http://test.com/agent");
        agentCard.setPreferredTransport("JSONRPC");
        agentCard.setDocumentationUrl("http://test.com/docs");
        agentCard.setDefaultInputModes(Arrays.asList("text", "voice"));
        agentCard.setDefaultOutputModes(Arrays.asList("text", "image"));
        agentCard.setSupportsAuthenticatedExtendedCard(true);
        
        // Create capabilities
        AgentCapabilities capabilities = new AgentCapabilities();
        capabilities.setStreaming(true);
        agentCard.setCapabilities(capabilities);
        
        // Create provider
        AgentProvider provider = new AgentProvider();
        provider.setOrganization("test-org");
        provider.setUrl("http://test.org");
        agentCard.setProvider(provider);
        
        // Create security scheme
        SecurityScheme securityScheme = new SecurityScheme();
        securityScheme.put("type", "apiKey");
        securityScheme.put("name", "Authorization");
        securityScheme.put("in", "header");
        
        HashMap<String, SecurityScheme> securitySchemes = new HashMap<>();
        securitySchemes.put("default", securityScheme);
        agentCard.setSecuritySchemes(securitySchemes);
        return agentCard;
    }
}