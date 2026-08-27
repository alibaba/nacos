/*
 * Copyright 1999-2026 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.ai.service.agent.watch;

import com.alibaba.nacos.api.ai.model.rad.AgentDiscoveryRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentProjectionKeyTest {
    
    @Test
    void testCanonicalIdentityAndDefensiveRequest() {
        AgentDiscoveryRequest source = AgentProjectionTestFixtures.request("AgentA");
        source.setNamespaceId(null);
        AgentProjectionKey first = AgentProjectionKey.of(source);
        AgentProjectionKey second =
            AgentProjectionKey.of(AgentProjectionTestFixtures.request("AgentA"));
        source.getReference().setAgentName("changed");
        AgentDiscoveryRequest copy = first.getRequest();
        copy.getReference().setAgentName("copy-changed");
        
        assertEquals(second, first);
        assertEquals(second.hashCode(), first.hashCode());
        assertEquals("public", first.getNamespaceId());
        assertEquals("AgentA", first.getAgentName());
        assertEquals("AgentA", first.getRequest().getReference().getAgentName());
        assertEquals(first.getValue(), first.toString());
        assertEquals(0, first.compareTo(second));
        assertNotEquals(first, AgentProjectionTestFixtures.key("AgentB"));
        assertFalse(first.equals("not-a-key"));
        assertTrue(first.equals(first));
    }
    
    @Test
    void testInvalidRequestRejected() {
        assertThrows(IllegalArgumentException.class, () -> AgentProjectionKey.of(null));
    }
    
    @Test
    void testLogicalKeyEqualityContract() {
        AgentLogicalKey first = new AgentLogicalKey("public", "demo");
        AgentLogicalKey same = new AgentLogicalKey("public", "demo");
        AgentLogicalKey different = new AgentLogicalKey("public", "other");
        
        assertTrue(first.equals(first));
        assertTrue(first.equals(same));
        assertEquals(first.hashCode(), same.hashCode());
        assertFalse(first.equals("not-a-logical-key"));
        assertFalse(first.equals(different));
    }
}
