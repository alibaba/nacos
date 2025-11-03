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

package com.alibaba.nacos.api.ai.model.mcp;

import com.alibaba.nacos.api.ai.model.mcp.registry.KeyValueInput;
import com.alibaba.nacos.api.remote.request.BasicRequestTest;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McpEndpointInfoTest extends BasicRequestTest {
    
    @Test
    void testSerialize() throws JsonProcessingException {
        McpEndpointInfo endpointInfo = new McpEndpointInfo();
        endpointInfo.setProtocol("https");
        endpointInfo.setAddress("127.0.0.1");
        endpointInfo.setPort(8080);
        endpointInfo.setPath("/api/mcp");
        
        List<KeyValueInput> headers = new ArrayList<>();
        KeyValueInput header = new KeyValueInput();
        header.setName("Content-Type");
        header.setValue("application/json");
        headers.add(header);
        endpointInfo.setHeaders(headers);
        
        String json = mapper.writeValueAsString(endpointInfo);
        assertTrue(json.contains("\"protocol\":\"https\""));
        assertTrue(json.contains("\"address\":\"127.0.0.1\""));
        assertTrue(json.contains("\"port\":8080"));
        assertTrue(json.contains("\"path\":\"/api/mcp\""));
        assertTrue(json.contains("\"headers\":["));
    }
    
    @Test
    void testDeserialize() throws JsonProcessingException {
        String json = "{\"protocol\":\"https\",\"address\":\"127.0.0.1\",\"port\":8080,\"path\":\"/api/mcp\","
                + "\"headers\":[{\"name\":\"Content-Type\",\"value\":\"application/json\"}]}";
        
        McpEndpointInfo result = mapper.readValue(json, McpEndpointInfo.class);
        assertNotNull(result);
        assertEquals("https", result.getProtocol());
        assertEquals("127.0.0.1", result.getAddress());
        assertEquals(8080, result.getPort());
        assertEquals("/api/mcp", result.getPath());
        assertNotNull(result.getHeaders());
        assertEquals(1, result.getHeaders().size());
        assertEquals("Content-Type", result.getHeaders().get(0).getName());
        assertEquals("application/json", result.getHeaders().get(0).getValue());
    }
}