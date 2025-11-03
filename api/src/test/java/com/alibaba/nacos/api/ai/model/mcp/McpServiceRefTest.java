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

import com.alibaba.nacos.api.remote.request.BasicRequestTest;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McpServiceRefTest extends BasicRequestTest {
    
    @Test
    void testSerialize() throws JsonProcessingException {
        McpServiceRef serviceRef = new McpServiceRef();
        serviceRef.setNamespaceId("public");
        serviceRef.setGroupName("DEFAULT_GROUP");
        serviceRef.setServiceName("mcp-service");
        serviceRef.setTransportProtocol("http");
        
        String json = mapper.writeValueAsString(serviceRef);
        assertTrue(json.contains("\"namespaceId\":\"public\""));
        assertTrue(json.contains("\"groupName\":\"DEFAULT_GROUP\""));
        assertTrue(json.contains("\"serviceName\":\"mcp-service\""));
        assertTrue(json.contains("\"transportProtocol\":\"http\""));
    }
    
    @Test
    void testDeserialize() throws JsonProcessingException {
        String json = "{\"namespaceId\":\"public\",\"groupName\":\"DEFAULT_GROUP\","
                + "\"serviceName\":\"mcp-service\",\"transportProtocol\":\"http\"}";
        
        McpServiceRef result = mapper.readValue(json, McpServiceRef.class);
        assertNotNull(result);
        assertEquals("public", result.getNamespaceId());
        assertEquals("DEFAULT_GROUP", result.getGroupName());
        assertEquals("mcp-service", result.getServiceName());
        assertEquals("http", result.getTransportProtocol());
    }
}