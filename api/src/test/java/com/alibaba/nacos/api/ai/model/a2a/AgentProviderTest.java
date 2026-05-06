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

class AgentProviderTest extends BasicRequestTest {
    
    @Test
    void testSerialize() throws JsonProcessingException {
        AgentProvider agentProvider = new AgentProvider();
        agentProvider.setOrganization("test-org");
        agentProvider.setUrl("http://test.org");
        
        String json = mapper.writeValueAsString(agentProvider);
        assertNotNull(json);
        assertTrue(json.contains("\"organization\":\"test-org\""));
        assertTrue(json.contains("\"url\":\"http://test.org\""));
    }
    
    @Test
    void testDeserialize() throws JsonProcessingException {
        String json = "{\"organization\":\"test-org\",\"url\":\"http://test.org\"}";
        
        AgentProvider agentProvider = mapper.readValue(json, AgentProvider.class);
        assertNotNull(agentProvider);
        assertEquals("test-org", agentProvider.getOrganization());
        assertEquals("http://test.org", agentProvider.getUrl());
    }
    
    @Test
    void testEqualsAndHashCode() {
        AgentProvider provider1 = new AgentProvider();
        provider1.setOrganization("test-org");
        provider1.setUrl("http://test.org");
        
        AgentProvider provider2 = new AgentProvider();
        provider2.setOrganization("test-org");
        provider2.setUrl("http://test.org");
        
        AgentProvider provider3 = new AgentProvider();
        provider3.setOrganization("other-org");
        
        assertEquals(provider1, provider2);
        assertEquals(provider1.hashCode(), provider2.hashCode());
        assertNotEquals(provider1, provider3);
        assertNotEquals(provider1.hashCode(), provider3.hashCode());
        assertNotEquals(provider1, null);
        assertNotEquals(provider1, new Object());
    }
}