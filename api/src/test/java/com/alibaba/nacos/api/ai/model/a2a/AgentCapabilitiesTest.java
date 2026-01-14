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
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentCapabilitiesTest extends BasicRequestTest {
    
    @Test
    void testSerialize() throws JsonProcessingException {
        AgentCapabilities agentCapabilities = new AgentCapabilities();
        agentCapabilities.setStreaming(true);
        agentCapabilities.setPushNotifications(false);
        agentCapabilities.setStateTransitionHistory(true);
        
        // Create extension
        AgentExtension extension = new AgentExtension();
        extension.setUri("test-uri");
        extension.setDescription("test description");
        extension.setRequired(true);
        Map<String, Object> params = new HashMap<>();
        params.put("param1", "value1");
        extension.setParams(params);
        
        agentCapabilities.setExtensions(Arrays.asList(extension));
        
        String json = mapper.writeValueAsString(agentCapabilities);
        assertNotNull(json);
        assertTrue(json.contains("\"streaming\":true"));
        assertTrue(json.contains("\"pushNotifications\":false"));
        assertTrue(json.contains("\"stateTransitionHistory\":true"));
        assertTrue(json.contains("\"uri\":\"test-uri\""));
        assertTrue(json.contains("\"description\":\"test description\""));
        assertTrue(json.contains("\"required\":true"));
        assertTrue(json.contains("\"params\":{\"param1\":\"value1\"}"));
    }
    
    @Test
    void testDeserialize() throws JsonProcessingException {
        String json = "{\"streaming\":true,\"pushNotifications\":false,\"stateTransitionHistory\":true,"
                + "\"extensions\":[{\"uri\":\"test-uri\",\"description\":\"test description\",\"required\":true,"
                + "\"params\":{\"param1\":\"value1\"}}]}";
        
        AgentCapabilities agentCapabilities = mapper.readValue(json, AgentCapabilities.class);
        assertNotNull(agentCapabilities);
        assertEquals(true, agentCapabilities.getStreaming());
        assertEquals(false, agentCapabilities.getPushNotifications());
        assertEquals(true, agentCapabilities.getStateTransitionHistory());
        assertEquals(1, agentCapabilities.getExtensions().size());
        
        AgentExtension extension = agentCapabilities.getExtensions().get(0);
        assertEquals("test-uri", extension.getUri());
        assertEquals("test description", extension.getDescription());
        assertEquals(true, extension.getRequired());
        assertEquals("value1", extension.getParams().get("param1"));
    }
    
    @Test
    void testEqualsAndHashCode() {
        AgentCapabilities cap1 = new AgentCapabilities();
        cap1.setStreaming(true);
        cap1.setPushNotifications(false);
        cap1.setStateTransitionHistory(true);
        
        AgentCapabilities cap2 = new AgentCapabilities();
        cap2.setStreaming(true);
        cap2.setPushNotifications(false);
        cap2.setStateTransitionHistory(true);
        
        AgentCapabilities cap3 = new AgentCapabilities();
        cap3.setStreaming(false);
        
        assertEquals(cap1, cap2);
        assertEquals(cap1.hashCode(), cap2.hashCode());
        assertNotEquals(cap1, cap3);
        assertNotEquals(cap1.hashCode(), cap3.hashCode());
        assertNotEquals(cap1, null);
        assertNotEquals(cap1, new Object());
    }
}