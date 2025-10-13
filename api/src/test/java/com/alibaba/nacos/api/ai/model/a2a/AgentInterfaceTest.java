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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentInterfaceTest extends BasicRequestTest {
    
    @Test
    void testSerialize() throws JsonProcessingException {
        AgentInterface agentInterface = new AgentInterface();
        agentInterface.setUrl("http://test.com/api");
        agentInterface.setTransport("JSONRPC");
        
        String json = mapper.writeValueAsString(agentInterface);
        assertNotNull(json);
        assertTrue(json.contains("\"url\":\"http://test.com/api\""));
        assertTrue(json.contains("\"transport\":\"JSONRPC\""));
    }
    
    @Test
    void testDeserialize() throws JsonProcessingException {
        String json = "{\"url\":\"http://test.com/api\",\"transport\":\"JSONRPC\"}";
        
        AgentInterface agentInterface = mapper.readValue(json, AgentInterface.class);
        assertNotNull(agentInterface);
        assertEquals("http://test.com/api", agentInterface.getUrl());
        assertEquals("JSONRPC", agentInterface.getTransport());
    }
    
    @Test
    void testEqualsAndHashCode() {
        AgentInterface interface1 = new AgentInterface();
        interface1.setUrl("http://test.com/api");
        interface1.setTransport("JSONRPC");
        
        AgentInterface interface2 = new AgentInterface();
        interface2.setUrl("http://test.com/api");
        interface2.setTransport("JSONRPC");
        
        AgentInterface interface3 = new AgentInterface();
        interface3.setUrl("http://other.com/api");
        
        assertEquals(interface1, interface2);
        assertEquals(interface1.hashCode(), interface2.hashCode());
        assertNotEquals(interface1, interface3);
        assertNotEquals(interface1.hashCode(), interface3.hashCode());
        assertNotEquals(interface1, null);
        assertNotEquals(interface1, new Object());
    }
}