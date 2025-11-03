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

class McpServerRemoteServiceConfigTest extends BasicRequestTest {
    
    @Test
    void testSerialize() throws JsonProcessingException {
        McpServerRemoteServiceConfig remoteServiceConfig = new McpServerRemoteServiceConfig();
        remoteServiceConfig.setExportPath("/mcp/export");
        
        McpServiceRef serviceRef = new McpServiceRef();
        serviceRef.setNamespaceId("public");
        serviceRef.setGroupName("DEFAULT_GROUP");
        serviceRef.setServiceName("mcp-service");
        remoteServiceConfig.setServiceRef(serviceRef);
        
        final List<FrontEndpointConfig> frontEndpointConfigs = new ArrayList<>();
        FrontEndpointConfig frontEndpointConfig = new FrontEndpointConfig();
        frontEndpointConfig.setType("sse");
        frontEndpointConfig.setProtocol("http");
        frontEndpointConfig.setEndpointType("DIRECT");
        frontEndpointConfig.setEndpointData("127.0.0.1:8080");
        frontEndpointConfig.setPath("/front");
        
        List<KeyValueInput> headers = new ArrayList<>();
        KeyValueInput header = new KeyValueInput();
        header.setName("Authorization");
        header.setValue("Bearer token");
        headers.add(header);
        frontEndpointConfig.setHeaders(headers);
        
        frontEndpointConfigs.add(frontEndpointConfig);
        remoteServiceConfig.setFrontEndpointConfigList(frontEndpointConfigs);
        
        String json = mapper.writeValueAsString(remoteServiceConfig);
        assertTrue(json.contains("\"exportPath\":\"/mcp/export\""));
        assertTrue(json.contains("\"serviceRef\":{"));
        assertTrue(json.contains("\"namespaceId\":\"public\""));
        assertTrue(json.contains("\"groupName\":\"DEFAULT_GROUP\""));
        assertTrue(json.contains("\"serviceName\":\"mcp-service\""));
        assertTrue(json.contains("\"frontEndpointConfigList\":["));
        assertTrue(json.contains("\"type\":\"sse\""));
        assertTrue(json.contains("\"protocol\":\"http\""));
        assertTrue(json.contains("\"endpointType\":\"DIRECT\""));
        assertTrue(json.contains("\"endpointData\":\"127.0.0.1:8080\""));
        assertTrue(json.contains("\"path\":\"/front\""));
        assertTrue(json.contains("\"headers\":["));
    }
    
    @Test
    void testDeserialize() throws JsonProcessingException {
        String json = "{\"serviceRef\":{\"namespaceId\":\"public\",\"groupName\":\"DEFAULT_GROUP\","
                + "\"serviceName\":\"mcp-service\"},\"exportPath\":\"/mcp/export\","
                + "\"frontEndpointConfigList\":[{\"type\":\"sse\",\"protocol\":\"http\","
                + "\"endpointType\":\"DIRECT\",\"endpointData\":\"127.0.0.1:8080\",\"path\":\"/front\","
                + "\"headers\":[{\"name\":\"Authorization\",\"value\":\"Bearer token\"}]}]}";
        
        McpServerRemoteServiceConfig result = mapper.readValue(json, McpServerRemoteServiceConfig.class);
        assertNotNull(result);
        assertEquals("/mcp/export", result.getExportPath());
        assertNotNull(result.getServiceRef());
        assertEquals("public", result.getServiceRef().getNamespaceId());
        assertEquals("DEFAULT_GROUP", result.getServiceRef().getGroupName());
        assertEquals("mcp-service", result.getServiceRef().getServiceName());
        assertNotNull(result.getFrontEndpointConfigList());
        assertEquals(1, result.getFrontEndpointConfigList().size());
        FrontEndpointConfig frontEndpointConfig = result.getFrontEndpointConfigList().get(0);
        assertEquals("sse", frontEndpointConfig.getType());
        assertEquals("http", frontEndpointConfig.getProtocol());
        assertEquals("DIRECT", frontEndpointConfig.getEndpointType());
        assertEquals("127.0.0.1:8080", frontEndpointConfig.getEndpointData());
        assertEquals("/front", frontEndpointConfig.getPath());
        assertNotNull(frontEndpointConfig.getHeaders());
        assertEquals(1, frontEndpointConfig.getHeaders().size());
        assertEquals("Authorization", frontEndpointConfig.getHeaders().get(0).getName());
        assertEquals("Bearer token", frontEndpointConfig.getHeaders().get(0).getValue());
    }
}