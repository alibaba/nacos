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

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentExtensionTest extends BasicRequestTest {
    
    @Test
    void testSerialize() throws JsonProcessingException {
        AgentExtension agentExtension = new AgentExtension();
        agentExtension.setUri("test-uri");
        agentExtension.setDescription("test description");
        agentExtension.setRequired(true);
        
        Map<String, Object> params = new HashMap<>();
        params.put("param1", "value1");
        params.put("param2", 123);
        agentExtension.setParams(params);
        
        String json = mapper.writeValueAsString(agentExtension);
        assertNotNull(json);
        assertTrue(json.contains("\"uri\":\"test-uri\""));
        assertTrue(json.contains("\"description\":\"test description\""));
        assertTrue(json.contains("\"required\":true"));
        assertTrue(json.contains("\"params\":{\"param1\":\"value1\",\"param2\":123}"));
    }
    
    @Test
    void testDeserialize() throws JsonProcessingException {
        String json = "{\"uri\":\"test-uri\",\"description\":\"test description\",\"required\":true,"
                + "\"params\":{\"param1\":\"value1\",\"param2\":123}}";
        
        AgentExtension agentExtension = mapper.readValue(json, AgentExtension.class);
        assertNotNull(agentExtension);
        assertEquals("test-uri", agentExtension.getUri());
        assertEquals("test description", agentExtension.getDescription());
        assertEquals(true, agentExtension.getRequired());
        assertEquals("value1", agentExtension.getParams().get("param1"));
        assertEquals(123, agentExtension.getParams().get("param2"));
    }
    
    @Test
    void testEqualsAndHashCode() {
        AgentExtension ext1 = new AgentExtension();
        ext1.setUri("test-uri");
        ext1.setDescription("test description");
        ext1.setRequired(true);
        Map<String, Object> params1 = new HashMap<>();
        params1.put("param1", "value1");
        ext1.setParams(params1);
        
        AgentExtension ext2 = new AgentExtension();
        ext2.setUri("test-uri");
        ext2.setDescription("test description");
        ext2.setRequired(true);
        Map<String, Object> params2 = new HashMap<>();
        params2.put("param1", "value1");
        ext2.setParams(params2);
        
        AgentExtension ext3 = new AgentExtension();
        ext3.setUri("other-uri");
        
        assertEquals(ext1, ext2);
        assertEquals(ext1.hashCode(), ext2.hashCode());
        assertNotEquals(ext1, ext3);
        assertNotEquals(ext1.hashCode(), ext3.hashCode());
        assertNotEquals(ext1, null);
        assertNotEquals(ext1, new Object());
    }
}