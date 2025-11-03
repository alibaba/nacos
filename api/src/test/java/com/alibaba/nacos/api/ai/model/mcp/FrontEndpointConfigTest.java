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

class FrontEndpointConfigTest extends BasicRequestTest {
    
    @Test
    void testSerialize() throws JsonProcessingException {
        FrontEndpointConfig frontEndpointConfig = new FrontEndpointConfig();
        frontEndpointConfig.setType("sse");
        frontEndpointConfig.setProtocol("http");
        frontEndpointConfig.setEndpointType("DIRECT");
        frontEndpointConfig.setEndpointData("127.0.0.1:8080");
        frontEndpointConfig.setPath("/test");
        
        List<KeyValueInput> headers = new ArrayList<>();
        KeyValueInput header = new KeyValueInput();
        header.setName("Authorization");
        header.setValue("Bearer token");
        headers.add(header);
        frontEndpointConfig.setHeaders(headers);
        
        String json = mapper.writeValueAsString(frontEndpointConfig);
        assertTrue(json.contains("\"type\":\"sse\""));
        assertTrue(json.contains("\"protocol\":\"http\""));
        assertTrue(json.contains("\"endpointType\":\"DIRECT\""));
        assertTrue(json.contains("\"endpointData\":\"127.0.0.1:8080\""));
        assertTrue(json.contains("\"path\":\"/test\""));
        assertTrue(json.contains("\"headers\":["));
    }
    
    @Test
    void testDeserialize() throws JsonProcessingException {
        String json = "{\"type\":\"sse\",\"protocol\":\"http\",\"endpointType\":\"DIRECT\","
                + "\"endpointData\":\"127.0.0.1:8080\",\"path\":\"/test\","
                + "\"headers\":[{\"name\":\"Authorization\",\"value\":\"Bearer token\"}]}";
        
        FrontEndpointConfig result = mapper.readValue(json, FrontEndpointConfig.class);
        assertNotNull(result);
        assertEquals("sse", result.getType());
        assertEquals("http", result.getProtocol());
        assertEquals("DIRECT", result.getEndpointType());
        assertEquals("127.0.0.1:8080", result.getEndpointData());
        assertEquals("/test", result.getPath());
        assertNotNull(result.getHeaders());
        assertEquals(1, result.getHeaders().size());
        assertEquals("Authorization", result.getHeaders().get(0).getName());
        assertEquals("Bearer token", result.getHeaders().get(0).getValue());
    }
}