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

class McpServerDetailInfoTest extends BasicRequestTest {
    
    @Test
    void testSerializeForStdio() throws JsonProcessingException {
        McpServerDetailInfo mcpServerDetailInfo = new McpServerDetailInfo();
        String id = UUID.randomUUID().toString();
        mcpServerDetailInfo.setName("stdioServer");
        mcpServerDetailInfo.setId(id);
        mcpServerDetailInfo.setProtocol(AiConstants.Mcp.MCP_PROTOCOL_STDIO);
        mcpServerDetailInfo.setFrontProtocol(AiConstants.Mcp.MCP_PROTOCOL_STDIO);
        mcpServerDetailInfo.setDescription("test stdio server");
        mcpServerDetailInfo.setVersionDetail(new ServerVersionDetail());
        mcpServerDetailInfo.getVersionDetail().setVersion("1.0.0");
        mcpServerDetailInfo.getVersionDetail().setIs_latest(false);
        mcpServerDetailInfo.getVersionDetail().setRelease_date("2025-07-15 23:59:59");
        mcpServerDetailInfo.setLocalServerConfig(new HashMap<>());
        mcpServerDetailInfo.setCapabilities(Collections.singletonList(McpCapability.TOOL));
        mcpServerDetailInfo.setToolSpec(new McpToolSpecification());
        mcpServerDetailInfo.setAllVersions(Collections.singletonList(mcpServerDetailInfo.getVersionDetail()));
        mcpServerDetailInfo.setNamespaceId(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE);
        mcpServerDetailInfo.setVersion("1.0.0");
        
        String json = mapper.writeValueAsString(mcpServerDetailInfo);
        assertTrue(json.contains("\"name\":\"stdioServer\""));
        assertTrue(json.contains(String.format("\"id\":\"%s\"", id)));
        assertTrue(json.contains("\"protocol\":\"stdio\""));
        assertTrue(json.contains("\"frontProtocol\":\"stdio\""));
        assertTrue(json.contains("\"description\":\"test stdio server\""));
        assertTrue(json.contains("\"versionDetail\":{"));
        assertTrue(json.contains("\"version\":\"1.0.0\""));
        assertTrue(json.contains("\"is_latest\":false"));
        assertTrue(json.contains("\"release_date\":\"2025-07-15 23:59:59\""));
        assertTrue(json.contains("\"localServerConfig\":{}"));
        assertTrue(json.contains("\"capabilities\":[\"TOOL\"]"));
        assertTrue(json.contains("\"toolSpec\":{"));
        assertTrue(json.contains("\"allVersions\":[{"));
    }
    
    @Test
    void testDeserializeForStdio() throws JsonProcessingException {
        String json =
                "{\"id\":\"3a2c535c-d0a8-44a4-8913-0cef98904ebd\",\"name\":\"stdioServer\",\"protocol\":\"stdio\","
                        + "\"frontProtocol\":\"stdio\",\"description\":\"test stdio server\",\"versionDetail\":{\"version\":\"1.0.0\","
                        + "\"release_date\":\"2025-07-15 23:59:59\",\"is_latest\":false},\"localServerConfig\":{},\"enabled\":true,"
                        + "\"capabilities\":[\"TOOL\"],\"toolSpec\":{\"tools\":[],\"toolsMeta\":{}},\"allVersions\":[{\"version\":\"1.0.0\","
                        + "\"release_date\":\"2025-07-15 23:59:59\",\"is_latest\":false}],\"namespaceId\":\"public\", \"version\":\"1.0.0\"}";
        McpServerDetailInfo result = mapper.readValue(json, McpServerDetailInfo.class);
        assertNotNull(result);
        assertEquals("3a2c535c-d0a8-44a4-8913-0cef98904ebd", result.getId());
        assertEquals("stdioServer", result.getName());
        assertEquals(AiConstants.Mcp.MCP_PROTOCOL_STDIO, result.getProtocol());
        assertEquals(AiConstants.Mcp.MCP_PROTOCOL_STDIO, result.getFrontProtocol());
        assertEquals("test stdio server", result.getDescription());
        assertNotNull(result.getVersionDetail());
        assertEquals("1.0.0", result.getVersionDetail().getVersion());
        assertEquals("2025-07-15 23:59:59", result.getVersionDetail().getRelease_date());
        assertFalse(result.getVersionDetail().getIs_latest());
        assertNotNull(result.getLocalServerConfig());
        assertTrue(result.isEnabled());
        assertNotNull(result.getCapabilities());
        assertEquals(1, result.getCapabilities().size());
        assertEquals(McpCapability.TOOL, result.getCapabilities().get(0));
        assertNotNull(result.getToolSpec());
        assertNotNull(result.getAllVersions());
        assertEquals(1, result.getAllVersions().size());
        assertEquals(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE, result.getNamespaceId());
        assertEquals("1.0.0", result.getVersion());
    }
    
    @Test
    void testSerializeForSse() throws JsonProcessingException {
        // Repository是空对象
        mapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
        McpServerDetailInfo mcpServerDetailInfo = new McpServerDetailInfo();
        String id = UUID.randomUUID().toString();
        mcpServerDetailInfo.setName("stdioServer");
        mcpServerDetailInfo.setId(id);
        mcpServerDetailInfo.setProtocol(AiConstants.Mcp.MCP_PROTOCOL_SSE);
        mcpServerDetailInfo.setFrontProtocol(AiConstants.Mcp.MCP_PROTOCOL_SSE);
        mcpServerDetailInfo.setDescription("test sse server");
        mcpServerDetailInfo.setVersionDetail(new ServerVersionDetail());
        mcpServerDetailInfo.getVersionDetail().setVersion("1.0.0");
        mcpServerDetailInfo.getVersionDetail().setIs_latest(false);
        mcpServerDetailInfo.getVersionDetail().setRelease_date("2025-07-15 23:59:59");
        mcpServerDetailInfo.setRemoteServerConfig(new McpServerRemoteServiceConfig());
        mcpServerDetailInfo.getRemoteServerConfig().setExportPath("/test");
        mcpServerDetailInfo.getRemoteServerConfig().setServiceRef(new McpServiceRef());
        mcpServerDetailInfo.getRemoteServerConfig().getServiceRef()
                .setNamespaceId(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE);
        mcpServerDetailInfo.getRemoteServerConfig().getServiceRef().setGroupName("testG");
        mcpServerDetailInfo.getRemoteServerConfig().getServiceRef().setServiceName("testS");
        mcpServerDetailInfo.getRemoteServerConfig()
                .setFrontEndpointConfigList(Collections.singletonList(new FrontEndpointConfig()));
        mcpServerDetailInfo.getRemoteServerConfig().getFrontEndpointConfigList().get(0)
                .setType(AiConstants.Mcp.MCP_PROTOCOL_SSE);
        mcpServerDetailInfo.getRemoteServerConfig().getFrontEndpointConfigList().get(0)
                .setProtocol(AiConstants.Mcp.MCP_PROTOCOL_HTTP);
        mcpServerDetailInfo.getRemoteServerConfig().getFrontEndpointConfigList().get(0)
                .setEndpointType(AiConstants.Mcp.MCP_ENDPOINT_TYPE_DIRECT);
        mcpServerDetailInfo.getRemoteServerConfig().getFrontEndpointConfigList().get(0).setEndpointData("1.1.1.1:8080");
        mcpServerDetailInfo.getRemoteServerConfig().getFrontEndpointConfigList().get(0).setPath("/testFront");
        mcpServerDetailInfo.setRepository(new Repository());
        mcpServerDetailInfo.setCapabilities(Collections.singletonList(McpCapability.TOOL));
        mcpServerDetailInfo.setToolSpec(new McpToolSpecification());
        mcpServerDetailInfo.setAllVersions(Collections.singletonList(mcpServerDetailInfo.getVersionDetail()));
        mcpServerDetailInfo.setNamespaceId(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE);
        mcpServerDetailInfo.setBackendEndpoints(Collections.singletonList(new McpEndpointInfo()));
        mcpServerDetailInfo.getBackendEndpoints().get(0).setPath("/testBack");
        mcpServerDetailInfo.getBackendEndpoints().get(0).setAddress("1.1.1.1");
        mcpServerDetailInfo.getBackendEndpoints().get(0).setPort(3306);
        mcpServerDetailInfo.setFrontendEndpoints(Collections.emptyList());
        
        String json = mapper.writeValueAsString(mcpServerDetailInfo);
        assertTrue(json.contains("\"name\":\"stdioServer\""));
        assertTrue(json.contains(String.format("\"id\":\"%s\"", id)));
        assertTrue(json.contains("\"protocol\":\"mcp-sse\""));
        assertTrue(json.contains("\"frontProtocol\":\"mcp-sse\""));
        assertTrue(json.contains("\"description\":\"test sse server\""));
        assertTrue(json.contains("\"versionDetail\":{"));
        assertTrue(json.contains("\"version\":\"1.0.0\""));
        assertTrue(json.contains("\"is_latest\":false"));
        assertTrue(json.contains("\"release_date\":\"2025-07-15 23:59:59\""));
        assertTrue(json.contains("\"remoteServerConfig\":{"));
        assertTrue(json.contains("\"exportPath\":\"/test\""));
        assertTrue(json.contains("\"serviceRef\":{"));
        assertTrue(json.contains("\"namespaceId\":\"public\""));
        assertTrue(json.contains("\"groupName\":\"testG\""));
        assertTrue(json.contains("\"serviceName\":\"testS\""));
        assertTrue(json.contains("\"capabilities\":[\"TOOL\"]"));
        assertTrue(json.contains("\"toolSpec\":{"));
        assertTrue(json.contains("\"allVersions\":[{"));
        assertTrue(json.contains("\"repository\":{}"));
        assertTrue(json.contains("\"frontendEndpoints\":[]"));
    }
    
    @Test
    void testDeserializeForSse() throws JsonProcessingException {
        String json =
                "{\"id\":\"c769b89b-edb5-4912-8e39-71bf5dc31eab\",\"name\":\"stdioServer\",\"protocol\":\"mcp-sse\","
                        + "\"frontProtocol\":\"mcp-sse\",\"description\":\"test sse server\",\"repository\":{},\"versionDetail\":"
                        + "{\"version\":\"1.0.0\",\"release_date\":\"2025-07-15 23:59:59\",\"is_latest\":false},"
                        + "\"remoteServerConfig\":{\"serviceRef\":{\"namespaceId\":\"public\",\"groupName\":\"testG\","
                        + "\"serviceName\":\"testS\"},\"exportPath\":\"/test\",\"frontEndpointConfigList\":[{\"type\":"
                        + "\"mcp-sse\",\"protocol\":\"http\",\"endpointType\":\"DIRECT\",\"endpointData\":\"1.1.1.1:8080\","
                        + "\"path\":\"/testFront\"}]},\"enabled\":true,\"capabilities\":[\"TOOL\"],\"backendEndpoints\":"
                        + "[{\"address\":\"1.1.1.1\",\"port\":3306,\"path\":\"/testBack\"}],\"frontendEndpoints\":[],\"toolSpec\":{\"tools\":[],"
                        + "\"toolsMeta\":{}},\"allVersions\":[{\"version\":\"1.0.0\",\"release_date\":\"2025-07-15 23:59:59\","
                        + "\"is_latest\":false}],\"namespaceId\":\"public\"}";
        McpServerDetailInfo result = mapper.readValue(json, McpServerDetailInfo.class);
        assertNotNull(result);
        assertEquals("c769b89b-edb5-4912-8e39-71bf5dc31eab", result.getId());
        assertEquals("stdioServer", result.getName());
        assertEquals(AiConstants.Mcp.MCP_PROTOCOL_SSE, result.getProtocol());
        assertEquals(AiConstants.Mcp.MCP_PROTOCOL_SSE, result.getFrontProtocol());
        assertEquals("test sse server", result.getDescription());
        assertNotNull(result.getVersionDetail());
        assertEquals("1.0.0", result.getVersionDetail().getVersion());
        assertEquals("2025-07-15 23:59:59", result.getVersionDetail().getRelease_date());
        assertFalse(result.getVersionDetail().getIs_latest());
        assertNotNull(result.getRemoteServerConfig());
        assertNotNull(result.getRemoteServerConfig().getServiceRef());
        assertEquals(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE,
                result.getRemoteServerConfig().getServiceRef().getNamespaceId());
        assertEquals("testG", result.getRemoteServerConfig().getServiceRef().getGroupName());
        assertEquals("testS", result.getRemoteServerConfig().getServiceRef().getServiceName());
        assertNotNull(result.getRemoteServerConfig().getExportPath());
        assertEquals("/test", result.getRemoteServerConfig().getExportPath());
        assertTrue(result.isEnabled());
        assertNotNull(result.getCapabilities());
        assertEquals(1, result.getCapabilities().size());
        assertEquals(McpCapability.TOOL, result.getCapabilities().get(0));
        assertNotNull(result.getToolSpec());
        assertNotNull(result.getAllVersions());
        assertEquals(1, result.getAllVersions().size());
        assertEquals(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE, result.getNamespaceId());
        assertNotNull(result.getRepository());
        assertNotNull(result.getBackendEndpoints());
        assertEquals(1, result.getBackendEndpoints().size());
        assertEquals("1.1.1.1", result.getBackendEndpoints().get(0).getAddress());
        assertEquals(3306, result.getBackendEndpoints().get(0).getPort());
        assertEquals("/testBack", result.getBackendEndpoints().get(0).getPath());
        assertEquals(1, result.getRemoteServerConfig().getFrontEndpointConfigList().size());
        FrontEndpointConfig frontEndpointConfig = result.getRemoteServerConfig().getFrontEndpointConfigList().get(0);
        assertEquals(AiConstants.Mcp.MCP_PROTOCOL_SSE, frontEndpointConfig.getType());
        assertEquals(AiConstants.Mcp.MCP_ENDPOINT_TYPE_DIRECT, frontEndpointConfig.getEndpointType());
        assertEquals("1.1.1.1:8080", frontEndpointConfig.getEndpointData());
        assertEquals("/testFront", frontEndpointConfig.getPath());
        assertEquals(AiConstants.Mcp.MCP_PROTOCOL_HTTP, frontEndpointConfig.getProtocol());
    }
}