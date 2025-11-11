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

package com.alibaba.nacos.api.ai.model.mcp.registry;

import com.alibaba.nacos.api.remote.request.BasicRequestTest;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TransportTest extends BasicRequestTest {
    
    @Test
    void testStdioTransportSerialize() throws JsonProcessingException {
        StdioTransport transport = new StdioTransport();
        String json = mapper.writeValueAsString(transport);
        
        assertNotNull(json);
        assertTrue(json.contains("\"type\":\"stdio\""));
    }
    
    @Test
    void testStdioTransportDeserialize() throws JsonProcessingException {
        String json = "{\"type\":\"stdio\"}";
        StdioTransport transport = mapper.readValue(json, StdioTransport.class);
        
        assertNotNull(transport);
        assertEquals("stdio", transport.getType());
    }
    
    @Test
    void testStreamableHttpTransportSerialize() throws JsonProcessingException {
        StreamableHttpTransport transport = new StreamableHttpTransport();
        transport.setUrl("http://localhost:8080/api");
        
        KeyValueInput header1 = new KeyValueInput();
        header1.setName("Authorization");
        header1.setValue("Bearer token123");
        
        KeyValueInput header2 = new KeyValueInput();
        header2.setName("Content-Type");
        header2.setValue("application/json");
        
        transport.setHeaders(Arrays.asList(header1, header2));
        
        String json = mapper.writeValueAsString(transport);
        assertNotNull(json);
        assertTrue(json.contains("\"type\":\"streamable-http\""));
        assertTrue(json.contains("\"url\":\"http://localhost:8080/api\""));
        assertTrue(json.contains("\"headers\":["));
        assertTrue(json.contains("\"Authorization\""));
    }
    
    @Test
    void testStreamableHttpTransportDeserialize() throws JsonProcessingException {
        String json = "{\"type\":\"streamable-http\",\"url\":\"http://localhost:8080/api\","
                + "\"headers\":[{\"name\":\"Authorization\",\"value\":\"Bearer token123\"}]}";
        StreamableHttpTransport transport = mapper.readValue(json, StreamableHttpTransport.class);
        
        assertNotNull(transport);
        assertEquals("streamable-http", transport.getType());
        assertEquals("http://localhost:8080/api", transport.getUrl());
        assertNotNull(transport.getHeaders());
        assertEquals(1, transport.getHeaders().size());
        assertEquals("Authorization", transport.getHeaders().get(0).getName());
    }
    
    @Test
    void testSseTransportSerialize() throws JsonProcessingException {
        SseTransport transport = new SseTransport();
        transport.setUrl("https://example.com/sse");
        
        KeyValueInput header = new KeyValueInput();
        header.setName("Accept");
        header.setValue("text/event-stream");
        transport.setHeaders(Arrays.asList(header));
        
        String json = mapper.writeValueAsString(transport);
        assertNotNull(json);
        assertTrue(json.contains("\"type\":\"sse\""));
        assertTrue(json.contains("\"url\":\"https://example.com/sse\""));
    }
    
    @Test
    void testSseTransportDeserialize() throws JsonProcessingException {
        String json = "{\"type\":\"sse\",\"url\":\"https://example.com/sse\"}";
        SseTransport transport = mapper.readValue(json, SseTransport.class);
        
        assertNotNull(transport);
        assertEquals("sse", transport.getType());
        assertEquals("https://example.com/sse", transport.getUrl());
    }
    
    @Test
    void testTransportTypeDetection() throws JsonProcessingException {
        // Test polymorphic deserialization with @JsonTypeInfo
        String stdioJson = "{\"type\":\"stdio\"}";
        String httpJson = "{\"type\":\"streamable-http\",\"url\":\"http://localhost:8080\"}";
        String sseJson = "{\"type\":\"sse\",\"url\":\"https://example.com\"}";
        
        StdioTransport stdio = mapper.readValue(stdioJson, StdioTransport.class);
        StreamableHttpTransport http = mapper.readValue(httpJson, StreamableHttpTransport.class);
        SseTransport sse = mapper.readValue(sseJson, SseTransport.class);
        
        assertEquals("stdio", stdio.getType());
        assertEquals("streamable-http", http.getType());
        assertEquals("sse", sse.getType());
    }
}
