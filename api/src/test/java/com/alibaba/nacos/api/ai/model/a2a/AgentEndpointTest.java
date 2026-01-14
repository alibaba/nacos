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
import static org.junit.jupiter.api.Assertions.assertFalse;

class AgentEndpointTest extends BasicRequestTest {
    
    @Test
    void testSerialize() throws JsonProcessingException {
        AgentEndpoint agentEndpoint = new AgentEndpoint();
        agentEndpoint.setAddress("127.0.0.1");
        agentEndpoint.setPort(8080);
        agentEndpoint.setTransport("JSONRPC");
        agentEndpoint.setPath("/test");
        agentEndpoint.setSupportTls(true);
        agentEndpoint.setVersion("1.0.0");
        agentEndpoint.setProtocol("HTTP");
        agentEndpoint.setQuery("param1=value1&param2=value2");
        
        String json = mapper.writeValueAsString(agentEndpoint);
        assertNotNull(json);
        assertTrue(json.contains("\"address\":\"127.0.0.1\""));
        assertTrue(json.contains("\"port\":8080"));
        assertTrue(json.contains("\"transport\":\"JSONRPC\""));
        assertTrue(json.contains("\"path\":\"/test\""));
        assertTrue(json.contains("\"supportTls\":true"));
        assertTrue(json.contains("\"version\":\"1.0.0\""));
        assertTrue(json.contains("\"protocol\":\"HTTP\""));
        assertTrue(json.contains("\"query\":\"param1=value1&param2=value2\""));
    }
    
    @Test
    void testDeserialize() throws JsonProcessingException {
        String json = "{\"address\":\"127.0.0.1\",\"port\":8080,\"transport\":\"JSONRPC\","
                + "\"path\":\"/test\",\"supportTls\":true,\"version\":\"1.0.0\",\"protocol\":\"HTTP\",\"query\":\"param1=value1&param2=value2\"}";
        
        AgentEndpoint agentEndpoint = mapper.readValue(json, AgentEndpoint.class);
        assertNotNull(agentEndpoint);
        assertEquals("127.0.0.1", agentEndpoint.getAddress());
        assertEquals(8080, agentEndpoint.getPort());
        assertEquals("JSONRPC", agentEndpoint.getTransport());
        assertEquals("/test", agentEndpoint.getPath());
        assertTrue(agentEndpoint.isSupportTls());
        assertEquals("1.0.0", agentEndpoint.getVersion());
        assertEquals("HTTP", agentEndpoint.getProtocol());
        assertEquals("param1=value1&param2=value2", agentEndpoint.getQuery());
    }
    
    @Test
    void testEqualsAndHashCode() {
        AgentEndpoint endpoint1 = new AgentEndpoint();
        endpoint1.setAddress("127.0.0.1");
        endpoint1.setPort(8080);
        endpoint1.setTransport("JSONRPC");
        endpoint1.setPath("/test");
        endpoint1.setSupportTls(true);
        endpoint1.setVersion("1.0.0");
        endpoint1.setProtocol("HTTP");
        endpoint1.setQuery("param1=value1");
        
        AgentEndpoint endpoint2 = new AgentEndpoint();
        endpoint2.setAddress("127.0.0.1");
        endpoint2.setPort(8080);
        endpoint2.setTransport("JSONRPC");
        endpoint2.setPath("/test");
        endpoint2.setSupportTls(true);
        endpoint2.setVersion("1.0.0");
        endpoint2.setProtocol("HTTP");
        endpoint2.setQuery("param1=value1");
        
        AgentEndpoint endpoint3 = new AgentEndpoint();
        endpoint3.setAddress("127.0.0.2");
        endpoint3.setProtocol("HTTPS");
        endpoint3.setQuery("param1=value2");
        
        assertEquals(endpoint1, endpoint1);
        assertEquals(endpoint1, endpoint2);
        assertEquals(endpoint1.hashCode(), endpoint2.hashCode());
        assertNotEquals(endpoint1, endpoint3);
        assertNotEquals(endpoint1.hashCode(), endpoint3.hashCode());
        assertNotEquals(endpoint1, null);
        assertNotEquals(endpoint1, new Object());
    }
    
    @Test
    void testSimpleEquals() {
        AgentEndpoint endpoint1 = new AgentEndpoint();
        endpoint1.setAddress("127.0.0.1");
        endpoint1.setPort(8080);
        
        AgentEndpoint endpoint2 = new AgentEndpoint();
        endpoint2.setAddress("127.0.0.1");
        endpoint2.setPort(8080);
        
        AgentEndpoint endpoint3 = new AgentEndpoint();
        endpoint3.setAddress("127.0.0.2");
        endpoint3.setPort(8080);
        
        AgentEndpoint endpoint4 = new AgentEndpoint();
        endpoint4.setAddress("127.0.0.1");
        endpoint4.setPort(9090);
        
        assertTrue(endpoint1.simpleEquals(endpoint2));
        assertFalse(endpoint1.simpleEquals(endpoint3));
        assertFalse(endpoint1.simpleEquals(endpoint4));
    }
    
    @Test
    void testToString() {
        AgentEndpoint agentEndpoint = new AgentEndpoint();
        agentEndpoint.setAddress("127.0.0.1");
        agentEndpoint.setPort(8080);
        agentEndpoint.setTransport("JSONRPC");
        agentEndpoint.setPath("/test");
        agentEndpoint.setSupportTls(true);
        agentEndpoint.setVersion("1.0.0");
        agentEndpoint.setProtocol("HTTP");
        agentEndpoint.setQuery("param1=value1");
        
        String toStringResult = agentEndpoint.toString();
        assertNotNull(toStringResult);
        assertTrue(toStringResult.contains("transport='JSONRPC'"));
        assertTrue(toStringResult.contains("address='127.0.0.1'"));
        assertTrue(toStringResult.contains("port=8080"));
        assertTrue(toStringResult.contains("path='/test'"));
        assertTrue(toStringResult.contains("supportTls=true"));
        assertTrue(toStringResult.contains("version='1.0.0'"));
        assertTrue(toStringResult.contains("protocol='HTTP'"));
        assertTrue(toStringResult.contains("query='param1=value1'"));
    }
}