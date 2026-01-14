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
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentAuthenticationTest extends BasicRequestTest {
    
    @Test
    void testSerialize() throws JsonProcessingException {
        AgentAuthentication agentAuthentication = new AgentAuthentication();
        agentAuthentication.setCredentials("test-credentials");
        List<String> schemes = Arrays.asList("oauth2", "basic");
        agentAuthentication.setSchemes(schemes);
        
        String json = mapper.writeValueAsString(agentAuthentication);
        assertNotNull(json);
        assertTrue(json.contains("\"credentials\":\"test-credentials\""));
        assertTrue(json.contains("\"schemes\":[\"oauth2\",\"basic\"]"));
    }
    
    @Test
    void testDeserialize() throws JsonProcessingException {
        String json = "{\"credentials\":\"test-credentials\",\"schemes\":[\"oauth2\",\"basic\"]}";
        
        AgentAuthentication agentAuthentication = mapper.readValue(json, AgentAuthentication.class);
        assertNotNull(agentAuthentication);
        assertEquals("test-credentials", agentAuthentication.getCredentials());
        assertEquals(2, agentAuthentication.getSchemes().size());
        assertEquals("oauth2", agentAuthentication.getSchemes().get(0));
        assertEquals("basic", agentAuthentication.getSchemes().get(1));
    }
    
    @Test
    void testEqualsAndHashCode() {
        AgentAuthentication auth1 = new AgentAuthentication();
        auth1.setCredentials("test-credentials");
        auth1.setSchemes(Arrays.asList("oauth2", "basic"));
        
        AgentAuthentication auth2 = new AgentAuthentication();
        auth2.setCredentials("test-credentials");
        auth2.setSchemes(Arrays.asList("oauth2", "basic"));
        
        AgentAuthentication auth3 = new AgentAuthentication();
        auth3.setCredentials("other-credentials");
        
        assertEquals(auth1, auth2);
        assertEquals(auth1.hashCode(), auth2.hashCode());
        assertNotEquals(auth1, auth3);
        assertNotEquals(auth1.hashCode(), auth3.hashCode());
        assertNotEquals(auth1, null);
        assertNotEquals(auth1, new Object());
    }
}