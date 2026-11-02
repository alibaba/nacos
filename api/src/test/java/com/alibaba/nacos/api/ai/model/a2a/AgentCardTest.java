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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Arrays;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Map;
import java.util.List;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentCardTest extends BasicRequestTest {
    
    @ParameterizedTest
    @ValueSource(strings = {
        "[{\"schemes\":{\"oauth2\":{\"list\":[\"read\",\"write\"]},\"apiKey\":{\"list\":[]}}},"
            + "{\"schemes\":{\"bearer\":{}}}]",
        "[{\"oauth2\":[\"read\",\"write\"],\"apiKey\":[]},{\"bearer\":[]}]"
    })
    void testSecurityRequirementsRoundTrip(String requirements) throws Exception {
        String json = "{\"securityRequirements\":" + requirements
            + ",\"security\":[{\"legacy\":[\"legacy-scope\"]}]}";
        AgentCard card = mapper.readValue(json, AgentCard.class);
        assertEquals(2, card.getSecurityRequirements().size());
        assertEquals(Arrays.asList("read", "write"),
            card.getSecurityRequirements().get(0).get("oauth2"));
        assertEquals(Collections.emptyList(), card.getSecurityRequirements().get(0).get("apiKey"));
        assertEquals(Collections.emptyList(), card.getSecurityRequirements().get(1).get("bearer"));
        String encoded = mapper.writeValueAsString(card);
        String expected = requirements.contains("\"list\"")
            ? "[{\"schemes\":{\"oauth2\":{\"list\":[\"read\",\"write\"]},"
                + "\"apiKey\":{\"list\":[]}}},{\"schemes\":{\"bearer\":{\"list\":[]}}}]"
            : requirements;
        assertEquals(mapper.readTree(expected),
            mapper.readTree(encoded).get("securityRequirements"));
        assertEquals(mapper.readTree(json).get("security"),
            mapper.readTree(encoded).get("security"));
        assertEquals(card, mapper.readValue(encoded, AgentCard.class));
        AgentCardDetailInfo detail = mapper.readValue(json, AgentCardDetailInfo.class);
        assertEquals(card.getSecurityRequirements(), detail.getSecurityRequirements());
        assertEquals(mapper.readTree(encoded).get("securityRequirements"),
            mapper.readTree(mapper.writeValueAsString(detail)).get("securityRequirements"));
    }
    
    @Test
    void testSecurityRequirementsJavaAccessorsRemainCompatible() throws Exception {
        AgentCard card = new AgentCard();
        Map<String, List<String>> requirement = new HashMap<>();
        requirement.put("schemes", new ArrayList<>(Collections.singletonList("read")));
        card.setSecurityRequirements(new ArrayList<>(Collections.singletonList(requirement)));
        card.getSecurityRequirements().get(0).get("schemes").add("write");
        String encoded = mapper.writeValueAsString(card);
        assertEquals(mapper.readTree("[{\"schemes\":[\"read\",\"write\"]}]"),
            mapper.readTree(encoded).get("securityRequirements"));
        assertEquals(Arrays.asList("read", "write"),
            mapper.readValue(encoded, AgentCard.class).getSecurityRequirements().get(0)
                .get("schemes"));
        AgentCard legacy = mapper.readValue("{\"securityRequirements\":[{\"schemes\":[\"read\"]}]}",
            AgentCard.class);
        assertEquals(Collections.singletonList("read"),
            legacy.getSecurityRequirements().get(0).get("schemes"));
    }
    
    @Test
    void testStandardRequirementsSurviveJavaModelCopiesAndMutations() throws Exception {
        AgentCard source = mapper.readValue("{\"securityRequirements\":[{\"schemes\":{"
            + "\"oauth2\":{\"list\":[\"read\"]}}}]}", AgentCard.class);
        AgentCardDetailInfo target = new AgentCardDetailInfo();
        target.setSecurityRequirements(source.getSecurityRequirements());
        target.getSecurityRequirements().get(0).get("oauth2").add("write");
        assertEquals(
            mapper.readTree("[{\"schemes\":{\"oauth2\":{\"list\":[\"read\",\"write\"]}}}]"),
            mapper.readTree(mapper.writeValueAsString(target)).get("securityRequirements"));
    }
    
    @Test
    void testSecurityRequirementsEmptyAndAbsent() throws Exception {
        assertNull(mapper.readValue("{}", AgentCard.class).getSecurityRequirements());
        assertNull(mapper.readValue("{\"securityRequirements\":null}", AgentCard.class)
            .getSecurityRequirements());
        AgentCard empty = mapper.readValue("{\"securityRequirements\":[]}", AgentCard.class);
        assertTrue(empty.getSecurityRequirements().isEmpty());
        assertTrue(mapper.readTree(mapper.writeValueAsString(empty)).get("securityRequirements")
            .isArray());
        AgentCard emptyAlternative =
            mapper.readValue("{\"securityRequirements\":[{}, {\"schemes\":{}}]}",
                AgentCard.class);
        assertEquals(Arrays.asList(Collections.emptyMap(), Collections.emptyMap()),
            emptyAlternative.getSecurityRequirements());
    }
    
    @ParameterizedTest
    @ValueSource(strings = {"[null]", "[1]", "{}", "[{\"schemes\":null}]",
        "[{\"schemes\":{\"apiKey\":[]}}]", "[{\"schemes\":{\"oauth2\":{\"list\":\"read\"}}}]",
        "[{\"schemes\":{\"oauth2\":{\"list\":[1]}}}]", "[{\"oauth2\":[null]}]",
        "[{\"schemes\":{},\"apiKey\":[]}]"})
    void testMalformedSecurityRequirementsFailDecoding(String requirements) {
        assertThrows(JsonProcessingException.class,
            () -> mapper.readValue("{\"securityRequirements\":" + requirements + "}",
                AgentCard.class));
    }
    
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
        assertTrue(json.contains("\"supportedInterfaces\":[{\"url\":\"http://test.com/v1\""));
        assertTrue(json.contains("\"documentationUrl\":\"http://test.com/docs\""));
        assertTrue(json.contains("\"signatures\":[{\"alg\":\"EdDSA\"}]"));
        assertTrue(json.contains("\"defaultInputModes\":[\"text\",\"voice\"]"));
        assertTrue(json.contains("\"defaultOutputModes\":[\"text\",\"image\"]"));
        assertTrue(json.contains("\"supportsAuthenticatedExtendedCard\":true"));
        assertTrue(json.contains("\"provider\":{\"organization\":\"test-org\""));
        assertTrue(json.contains("\"securitySchemes\":{\"default\":{\"type\":\"apiKey\""));
    }
    
    @Test
    void testDeserialize() throws JsonProcessingException {
        String json =
            "{\"protocolVersion\":\"1.0\",\"name\":\"test agent\",\"description\":\"test description\","
                + "\"version\":\"1.0.0\",\"iconUrl\":\"http://test.com/icon.png\","
                + "\"url\":\"http://test.com/agent\",\"preferredTransport\":\"JSONRPC\","
                + "\"supportedInterfaces\":[{\"url\":\"http://test.com/v1\",\"protocolBinding\":\"JSONRPC\","
                + "\"protocolVersion\":\"1.0\",\"tenant\":\"public\"}],"
                + "\"documentationUrl\":\"http://test.com/docs\","
                + "\"signatures\":[{\"alg\":\"EdDSA\"}],"
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
        assertEquals(1, agentCard.getSupportedInterfaces().size());
        assertEquals("http://test.com/v1", agentCard.getSupportedInterfaces().get(0).getUrl());
        assertEquals("JSONRPC", agentCard.getSupportedInterfaces().get(0).getProtocolBinding());
        assertEquals("1.0", agentCard.getSupportedInterfaces().get(0).getProtocolVersion());
        assertEquals("public", agentCard.getSupportedInterfaces().get(0).getTenant());
        assertEquals("http://test.com/docs", agentCard.getDocumentationUrl());
        assertEquals(1, agentCard.getSignatures().size());
        assertEquals("EdDSA", agentCard.getSignatures().get(0).get("alg"));
        assertEquals(2, agentCard.getDefaultInputModes().size());
        assertEquals("text", agentCard.getDefaultInputModes().get(0));
        assertEquals("voice", agentCard.getDefaultInputModes().get(1));
        assertEquals(2, agentCard.getDefaultOutputModes().size());
        assertEquals("text", agentCard.getDefaultOutputModes().get(0));
        assertEquals("image", agentCard.getDefaultOutputModes().get(1));
        assertEquals(true, agentCard.getSupportsAuthenticatedExtendedCard());
        assertNotNull(agentCard.getCapabilities());
        assertEquals(true, agentCard.getCapabilities().getStreaming());
        assertNotNull(agentCard.getCapabilities());
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
        card1.setSupportedInterfaces(Collections.emptyList());
        
        AgentCard card2 = new AgentCard();
        card2.setProtocolVersion("1.0");
        card2.setName("test agent");
        card2.setDescription("test description");
        card2.setVersion("1.0.0");
        card2.setIconUrl("http://test.com/icon.png");
        card2.setUrl("http://test.com/agent");
        card2.setPreferredTransport("JSONRPC");
        card2.setSupportedInterfaces(Collections.emptyList());
        
        AgentCard card3 = new AgentCard();
        card3.setProtocolVersion("2.0");
        
        AgentCard card4 = new AgentCard();
        card4.setProtocolVersion("1.0");
        card4.setName("test agent");
        card4.setDescription("test description");
        card4.setVersion("1.0.0");
        card4.setIconUrl("http://test.com/icon.png");
        card4.setUrl("http://test.com/agent");
        card4.setPreferredTransport("JSONRPC");
        AgentInterface changedInterface = new AgentInterface();
        changedInterface.setUrl("http://test.com/changed");
        changedInterface.setProtocolBinding("JSONRPC");
        changedInterface.setProtocolVersion("1.0");
        card4.setSupportedInterfaces(Collections.singletonList(changedInterface));
        
        assertEquals(card1, card1);
        assertEquals(card1, card2);
        assertEquals(card1.hashCode(), card2.hashCode());
        assertNotEquals(card1, card3);
        assertNotEquals(card1, card4);
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
        assertEquals("http://test.com/interface",
            agentCard.getAdditionalInterfaces().get(0).getUrl());
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
        agentCard.setSignatures(
            Collections.singletonList(Collections.<String, Object>singletonMap("alg", "EdDSA")));
        
        // Create capabilities
        AgentCapabilities capabilities = new AgentCapabilities();
        capabilities.setStreaming(true);
        capabilities.setExtendedAgentCard(true);
        agentCard.setCapabilities(capabilities);
        
        AgentInterface supportedInterface = new AgentInterface();
        supportedInterface.setUrl("http://test.com/v1");
        supportedInterface.setProtocolBinding("JSONRPC");
        supportedInterface.setProtocolVersion("1.0");
        supportedInterface.setTenant("public");
        agentCard.setSupportedInterfaces(Collections.singletonList(supportedInterface));
        
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
