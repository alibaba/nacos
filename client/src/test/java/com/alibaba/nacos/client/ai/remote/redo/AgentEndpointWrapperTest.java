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

import java.util.Arrays;
import java.util.Collection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentEndpointWrapperTest {
    
    private AgentEndpoint newEndpoint(String address, int port) {
        AgentEndpoint endpoint = new AgentEndpoint();
        endpoint.setAddress(address);
        endpoint.setPort(port);
        return endpoint;
    }
    
    @Test
    void testWrapSingle() {
        AgentEndpoint endpoint = newEndpoint("127.0.0.1", 8080);
        AgentEndpointWrapper wrapper = AgentEndpointWrapper.wrap(endpoint);
        assertFalse(wrapper.isBatch());
        assertSame(endpoint, wrapper.getData());
        assertThrows(UnsupportedOperationException.class, wrapper::getBatchData);
    }
    
    @Test
    void testWrapBatch() {
        Collection<AgentEndpoint> list =
            Arrays.asList(newEndpoint("127.0.0.1", 8080), newEndpoint("127.0.0.2", 9090));
        AgentEndpointWrapper wrapper = AgentEndpointWrapper.wrap(list);
        assertTrue(wrapper.isBatch());
        assertEquals(2, wrapper.getBatchData().size());
        assertThrows(UnsupportedOperationException.class, wrapper::getData);
    }
    
    @Test
    void testEquals() {
        AgentEndpoint e1 = newEndpoint("127.0.0.1", 8080);
        AgentEndpointWrapper wrapper1 = AgentEndpointWrapper.wrap(e1);
        AgentEndpointWrapper wrapper2 = AgentEndpointWrapper.wrap(e1);
        assertEquals(wrapper1, wrapper1);
        assertNotEquals(wrapper1, null);
        assertNotEquals(wrapper1, new Object());
        assertEquals(wrapper1, wrapper2);
        assertEquals(wrapper1.hashCode(), wrapper2.hashCode());
        
        AgentEndpointWrapper diffData = AgentEndpointWrapper.wrap(newEndpoint("127.0.0.2", 9090));
        assertNotEquals(wrapper1, diffData);
        
        AgentEndpointWrapper batch = AgentEndpointWrapper.wrap(Arrays.asList(e1));
        assertNotEquals(wrapper1, batch);
    }
}
