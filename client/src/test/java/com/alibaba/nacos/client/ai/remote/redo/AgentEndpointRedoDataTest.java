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

package com.alibaba.nacos.client.ai.remote.redo;

import com.alibaba.nacos.api.ai.model.a2a.AgentEndpoint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class AgentEndpointRedoDataTest {
    
    private AgentEndpoint newEndpoint(String address, int port) {
        AgentEndpoint endpoint = new AgentEndpoint();
        endpoint.setAddress(address);
        endpoint.setPort(port);
        return endpoint;
    }
    
    @Test
    void testGetAgentName() {
        AgentEndpointWrapper wrapper = AgentEndpointWrapper.wrap(newEndpoint("127.0.0.1", 8080));
        AgentEndpointRedoData data = new AgentEndpointRedoData("agentX", wrapper);
        assertEquals("agentX", data.getAgentName());
        assertSame(wrapper, data.get());
    }
    
    @Test
    void testEquals() {
        AgentEndpointWrapper wrapper = AgentEndpointWrapper.wrap(newEndpoint("127.0.0.1", 8080));
        AgentEndpointRedoData data = new AgentEndpointRedoData("agentX", wrapper);
        assertEquals(data, data);
        assertNotEquals(data, null);
        assertNotEquals(data, new Object());
        
        AgentEndpointRedoData same = new AgentEndpointRedoData("agentX", wrapper);
        assertEquals(data, same);
        assertEquals(data.hashCode(), same.hashCode());
        
        AgentEndpointRedoData diffName = new AgentEndpointRedoData("agentY", wrapper);
        assertNotEquals(data, diffName);
        
        AgentEndpointWrapper otherWrapper =
            AgentEndpointWrapper.wrap(newEndpoint("127.0.0.2", 9090));
        AgentEndpointRedoData diffWrapper = new AgentEndpointRedoData("agentX", otherWrapper);
        assertNotEquals(data, diffWrapper);
    }
}
