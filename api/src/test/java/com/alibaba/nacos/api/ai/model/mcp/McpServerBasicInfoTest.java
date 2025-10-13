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

import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.api.ai.model.mcp.registry.Package;
import com.alibaba.nacos.api.ai.model.mcp.registry.Repository;
import com.alibaba.nacos.api.ai.model.mcp.registry.ServerVersionDetail;
import com.alibaba.nacos.api.remote.request.BasicRequestTest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McpServerBasicInfoTest extends BasicRequestTest {
    
    @Test
    void testSerializeForStdio() throws JsonProcessingException {
        mapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
        
        McpServerBasicInfo serverBasicInfo = new McpServerBasicInfo();
        String id = UUID.randomUUID().toString();
        serverBasicInfo.setId(id);
        serverBasicInfo.setName("stdioServer");
        serverBasicInfo.setProtocol(AiConstants.Mcp.MCP_PROTOCOL_STDIO);
        serverBasicInfo.setFrontProtocol(AiConstants.Mcp.MCP_PROTOCOL_STDIO);
        serverBasicInfo.setDescription("test stdio server");
        serverBasicInfo.setRepository(new Repository());
        serverBasicInfo.setVersionDetail(new ServerVersionDetail());
        serverBasicInfo.getVersionDetail().setVersion("1.0.0");
        serverBasicInfo.getVersionDetail().setIs_latest(true);
        serverBasicInfo.setLocalServerConfig(new HashMap<>());
        serverBasicInfo.setEnabled(true);
        serverBasicInfo.setCapabilities(Collections.singletonList(McpCapability.TOOL));
        serverBasicInfo.setStatus(AiConstants.Mcp.MCP_STATUS_ACTIVE);
        
        // 添加 Package 测试
        Package pkg = new Package();
        pkg.setIdentifier("test-package");
        pkg.setVersion("1.0.0");
        serverBasicInfo.setPackages(Collections.singletonList(pkg));
        
        String json = mapper.writeValueAsString(serverBasicInfo);
        assertTrue(json.contains(String.format("\"id\":\"%s\"", id)));
        assertTrue(json.contains("\"name\":\"stdioServer\""));
        assertTrue(json.contains("\"protocol\":\"stdio\""));
        assertTrue(json.contains("\"frontProtocol\":\"stdio\""));
        assertTrue(json.contains("\"description\":\"test stdio server\""));
        assertTrue(json.contains("\"repository\":{}"));
        assertTrue(json.contains("\"versionDetail\":{"));
        assertTrue(json.contains("\"version\":\"1.0.0\""));
        assertTrue(json.contains("\"is_latest\":true"));
        assertTrue(json.contains("\"localServerConfig\":{}"));
        assertTrue(json.contains("\"enabled\":true"));
        assertTrue(json.contains("\"capabilities\":[\"TOOL\"]"));
        assertTrue(json.contains("\"status\":\"active\""));
        assertTrue(json.contains("\"packages\":["));
    }
    
    @Test
    void testDeserializeForStdio() throws JsonProcessingException {
        String json = "{\"id\":\"3a2c535c-d0a8-44a4-8913-0cef98904ebd\",\"name\":\"stdioServer\","
                + "\"protocol\":\"stdio\",\"frontProtocol\":\"stdio\",\"description\":\"test stdio server\","
                + "\"repository\":{},\"versionDetail\":{\"version\":\"1.0.0\",\"is_latest\":true},"
                + "\"localServerConfig\":{},\"enabled\":true,\"capabilities\":[\"TOOL\"],\"status\":\"active\","
                + "\"packages\":[{\"identifier\":\"test-package\",\"version\":\"1.0.0\"}]}";
        
        McpServerBasicInfo result = mapper.readValue(json, McpServerBasicInfo.class);
        assertNotNull(result);
        assertEquals("3a2c535c-d0a8-44a4-8913-0cef98904ebd", result.getId());
        assertEquals("stdioServer", result.getName());
        assertEquals(AiConstants.Mcp.MCP_PROTOCOL_STDIO, result.getProtocol());
        assertEquals(AiConstants.Mcp.MCP_PROTOCOL_STDIO, result.getFrontProtocol());
        assertEquals("test stdio server", result.getDescription());
        assertNotNull(result.getRepository());
        assertNotNull(result.getVersionDetail());
        assertEquals("1.0.0", result.getVersionDetail().getVersion());
        assertTrue(result.getVersionDetail().getIs_latest());
        assertNotNull(result.getLocalServerConfig());
        assertTrue(result.isEnabled());
        assertNotNull(result.getCapabilities());
        assertEquals(1, result.getCapabilities().size());
        assertEquals(McpCapability.TOOL, result.getCapabilities().get(0));
        assertEquals(AiConstants.Mcp.MCP_STATUS_ACTIVE, result.getStatus());
        assertNotNull(result.getPackages());
        assertEquals(1, result.getPackages().size());
        assertEquals("test-package", result.getPackages().get(0).getIdentifier());
        assertEquals("1.0.0", result.getPackages().get(0).getVersion());
    }
    
    @Test
    void testSerializeForSse() throws JsonProcessingException {
        mapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
        
        McpServerBasicInfo serverBasicInfo = new McpServerBasicInfo();
        String id = UUID.randomUUID().toString();
        serverBasicInfo.setId(id);
        serverBasicInfo.setName("sseServer");
        serverBasicInfo.setProtocol(AiConstants.Mcp.MCP_PROTOCOL_SSE);
        serverBasicInfo.setFrontProtocol(AiConstants.Mcp.MCP_PROTOCOL_SSE);
        serverBasicInfo.setDescription("test sse server");
        serverBasicInfo.setRepository(new Repository());
        serverBasicInfo.setVersionDetail(new ServerVersionDetail());
        serverBasicInfo.getVersionDetail().setVersion("1.0.0");
        serverBasicInfo.getVersionDetail().setIs_latest(false);
        serverBasicInfo.setRemoteServerConfig(new McpServerRemoteServiceConfig());
        serverBasicInfo.getRemoteServerConfig().setExportPath("/test");
        serverBasicInfo.setEnabled(false);
        serverBasicInfo.setCapabilities(Collections.singletonList(McpCapability.RESOURCE));
        serverBasicInfo.setStatus(AiConstants.Mcp.MCP_STATUS_DEPRECATED);
        
        // 添加 Package 测试
        Package pkg = new Package();
        pkg.setIdentifier("test-package");
        pkg.setVersion("1.0.0");
        serverBasicInfo.setPackages(Collections.singletonList(pkg));
        
        String json = mapper.writeValueAsString(serverBasicInfo);
        assertTrue(json.contains(String.format("\"id\":\"%s\"", id)));
        assertTrue(json.contains("\"name\":\"sseServer\""));
        assertTrue(json.contains("\"protocol\":\"mcp-sse\""));
        assertTrue(json.contains("\"frontProtocol\":\"mcp-sse\""));
        assertTrue(json.contains("\"description\":\"test sse server\""));
        assertTrue(json.contains("\"repository\":{}"));
        assertTrue(json.contains("\"versionDetail\":{"));
        assertTrue(json.contains("\"version\":\"1.0.0\""));
        assertTrue(json.contains("\"is_latest\":false"));
        assertTrue(json.contains("\"remoteServerConfig\":{"));
        assertTrue(json.contains("\"exportPath\":\"/test\""));
        assertTrue(json.contains("\"enabled\":false"));
        assertTrue(json.contains("\"capabilities\":[\"RESOURCE\"]"));
        assertTrue(json.contains("\"status\":\"deprecated\""));
        assertTrue(json.contains("\"packages\":["));
    }
    
    @Test
    void testDeserializeForSse() throws JsonProcessingException {
        String json = "{\"id\":\"c769b89b-edb5-4912-8e39-71bf5dc31eab\",\"name\":\"sseServer\","
                + "\"protocol\":\"mcp-sse\",\"frontProtocol\":\"mcp-sse\",\"description\":\"test sse server\","
                + "\"repository\":{},\"versionDetail\":{\"version\":\"1.0.0\",\"is_latest\":false},"
                + "\"remoteServerConfig\":{\"exportPath\":\"/test\"},\"enabled\":false,"
                + "\"capabilities\":[\"RESOURCE\"],\"status\":\"deprecated\","
                + "\"packages\":[{\"identifier\":\"test-package\",\"version\":\"1.0.0\"}]}";
        
        McpServerBasicInfo result = mapper.readValue(json, McpServerBasicInfo.class);
        assertNotNull(result);
        assertEquals("c769b89b-edb5-4912-8e39-71bf5dc31eab", result.getId());
        assertEquals("sseServer", result.getName());
        assertEquals(AiConstants.Mcp.MCP_PROTOCOL_SSE, result.getProtocol());
        assertEquals(AiConstants.Mcp.MCP_PROTOCOL_SSE, result.getFrontProtocol());
        assertEquals("test sse server", result.getDescription());
        assertNotNull(result.getRepository());
        assertNotNull(result.getVersionDetail());
        assertEquals("1.0.0", result.getVersionDetail().getVersion());
        assertFalse(result.getVersionDetail().getIs_latest());
        assertNotNull(result.getRemoteServerConfig());
        assertEquals("/test", result.getRemoteServerConfig().getExportPath());
        assertFalse(result.isEnabled());
        assertNotNull(result.getCapabilities());
        assertEquals(1, result.getCapabilities().size());
        assertEquals(McpCapability.RESOURCE, result.getCapabilities().get(0));
        assertEquals(AiConstants.Mcp.MCP_STATUS_DEPRECATED, result.getStatus());
        assertNotNull(result.getPackages());
        assertEquals(1, result.getPackages().size());
        assertEquals("test-package", result.getPackages().get(0).getIdentifier());
        assertEquals("1.0.0", result.getPackages().get(0).getVersion());
    }
}