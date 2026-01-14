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

package com.alibaba.nacos.maintainer.client.ai;

import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.api.ai.model.a2a.AgentCard;
import com.alibaba.nacos.api.ai.model.a2a.AgentCardDetailInfo;
import com.alibaba.nacos.api.ai.model.a2a.AgentCardVersionInfo;
import com.alibaba.nacos.api.ai.model.a2a.AgentVersionDetail;
import com.alibaba.nacos.api.ai.model.mcp.McpEndpointSpec;
import com.alibaba.nacos.api.ai.model.mcp.McpServerBasicInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpServerDetailInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpTool;
import com.alibaba.nacos.api.ai.model.mcp.McpToolMeta;
import com.alibaba.nacos.api.ai.model.mcp.McpToolSpecification;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.common.http.HttpRestResult;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.maintainer.client.model.HttpRequest;
import com.alibaba.nacos.maintainer.client.remote.ClientHttpProxy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Unit tests for NacosAiMaintainerServiceImplTest.
 *
 * @author nacos
 */
@ExtendWith(MockitoExtension.class)
public class NacosAiMaintainerServiceImplTest {
    
    @Mock
    ClientHttpProxy clientHttpProxy;
    
    AiMaintainerService aiMaintainerService;
    
    @BeforeEach
    void setUp() throws NacosException, NoSuchFieldException, IllegalAccessException {
        Properties properties = new Properties();
        properties.put(PropertyKeyConst.SERVER_ADDR, "127.0.0.1:8848");
        aiMaintainerService = AiMaintainerFactory.createAiMaintainerService(properties);
        Field clientHttpProxyField = NacosAiMaintainerServiceImpl.class.getDeclaredField("clientHttpProxy");
        clientHttpProxyField.setAccessible(true);
        clientHttpProxyField.set(aiMaintainerService, clientHttpProxy);
    }
    
    @Test
    void listMcpServer() throws NacosException {
        final HttpRestResult<String> mockRestResult = new HttpRestResult<>();
        Page<McpServerBasicInfo> page = new Page<>();
        page.setPagesAvailable(1);
        page.setTotalCount(1);
        page.setPageNumber(1);
        page.setPageItems(Collections.singletonList(new McpServerBasicInfo()));
        mockRestResult.setData(JacksonUtils.toJson(Result.success(page)));
        when(clientHttpProxy.executeSyncHttpRequest(any(HttpRequest.class))).thenReturn(mockRestResult);
        Page<McpServerBasicInfo> actual = aiMaintainerService.listMcpServer();
        assertEquals(page.getPageNumber(), actual.getPageNumber());
        assertEquals(page.getTotalCount(), actual.getTotalCount());
        assertEquals(page.getPagesAvailable(), actual.getPagesAvailable());
        assertEquals(page.getPageItems().size(), actual.getPageItems().size());
    }
    
    @Test
    void searchMcpServer() throws NacosException {
        final HttpRestResult<String> mockRestResult = new HttpRestResult<>();
        Page<McpServerBasicInfo> page = new Page<>();
        page.setPagesAvailable(1);
        page.setTotalCount(1);
        page.setPageNumber(1);
        page.setPageItems(Collections.singletonList(new McpServerBasicInfo()));
        mockRestResult.setData(JacksonUtils.toJson(Result.success(page)));
        when(clientHttpProxy.executeSyncHttpRequest(any(HttpRequest.class))).thenReturn(mockRestResult);
        Page<McpServerBasicInfo> actual = aiMaintainerService.searchMcpServer("");
        assertEquals(page.getPageNumber(), actual.getPageNumber());
        assertEquals(page.getTotalCount(), actual.getTotalCount());
        assertEquals(page.getPagesAvailable(), actual.getPagesAvailable());
        assertEquals(page.getPageItems().size(), actual.getPageItems().size());
    }
    
    @Test
    void getMcpServerDetail() throws NacosException {
        final HttpRestResult<String> mockRestResult = new HttpRestResult<>();
        McpServerDetailInfo mcpServerDetailInfo = new McpServerDetailInfo();
        mcpServerDetailInfo.setName("test");
        mockRestResult.setData(JacksonUtils.toJson(Result.success(mcpServerDetailInfo)));
        when(clientHttpProxy.executeSyncHttpRequest(any(HttpRequest.class))).thenReturn(mockRestResult);
        McpServerDetailInfo actual = aiMaintainerService.getMcpServerDetail("test");
        assertEquals(mcpServerDetailInfo.getName(), actual.getName());
    }
    
    @Test
    void createLocalMcpServer() throws NacosException {
        final HttpRestResult<String> mockRestResult = new HttpRestResult<>();
        String mcpId = UUID.randomUUID().toString();
        mockRestResult.setData(JacksonUtils.toJson(Result.success(mcpId)));
        when(clientHttpProxy.executeSyncHttpRequest(any(HttpRequest.class))).thenReturn(mockRestResult);
        assertEquals(aiMaintainerService.createLocalMcpServer("test", "1.0.0"), mcpId);
    }
    
    @Test
    void createLocalMcpServerWithNullSpec() {
        assertThrows(NacosException.class,
                () -> aiMaintainerService.createLocalMcpServer("test", (McpServerBasicInfo) null, null),
                "Mcp server specification cannot be null.");
    }
    
    @Test
    void createLocalMcpServerWithIllegalProtocol() {
        McpServerBasicInfo serverSpec = new McpServerBasicInfo();
        serverSpec.setName("test");
        serverSpec.setProtocol(AiConstants.Mcp.MCP_PROTOCOL_SSE);
        assertThrows(NacosException.class, () -> aiMaintainerService.createLocalMcpServer("test", serverSpec, null),
                String.format("Mcp server type must be `local`, input is `%s`", AiConstants.Mcp.MCP_PROTOCOL_SSE));
    }
    
    @Test
    void createLocalMcpServerWithTool() throws NacosException {
        McpServerBasicInfo serverSpec = new McpServerBasicInfo();
        serverSpec.setName("test");
        serverSpec.setProtocol(AiConstants.Mcp.MCP_PROTOCOL_STDIO);
        String mcpId = UUID.randomUUID().toString();
        serverSpec.setId(mcpId);
        McpToolSpecification toolSpec = new McpToolSpecification();
        McpTool mcpTool = new McpTool();
        mcpTool.setName("testTool");
        mcpTool.setName("testToolDescription");
        toolSpec.setTools(Collections.singletonList(mcpTool));
        toolSpec.setToolsMeta(Collections.singletonMap("testTool", new McpToolMeta()));
        final HttpRestResult<String> mockRestResult = new HttpRestResult<>();
        mockRestResult.setData(JacksonUtils.toJson(Result.success(mcpId)));
        when(clientHttpProxy.executeSyncHttpRequest(any(HttpRequest.class))).thenReturn(mockRestResult);
        assertEquals(aiMaintainerService.createLocalMcpServer("test", serverSpec, toolSpec), mcpId);
    }
    
    @Test
    void createRemoteMcpServer() throws NacosException {
        final HttpRestResult<String> mockRestResult = new HttpRestResult<>();
        String mcpId = UUID.randomUUID().toString();
        mockRestResult.setData(JacksonUtils.toJson(Result.success(mcpId)));
        when(clientHttpProxy.executeSyncHttpRequest(any(HttpRequest.class))).thenReturn(mockRestResult);
        McpEndpointSpec endpointSpec = new McpEndpointSpec();
        endpointSpec.setType(AiConstants.Mcp.MCP_ENDPOINT_TYPE_DIRECT);
        endpointSpec.setData(Collections.singletonMap("address", "127.0.0.1"));
        assertEquals(aiMaintainerService.createRemoteMcpServer("test", "1.0.0", AiConstants.Mcp.MCP_PROTOCOL_SSE,
                endpointSpec), mcpId);
    }
    
    @Test
    void createRemoteMcpServerWithSpec() throws NacosException {
        final HttpRestResult<String> mockRestResult = new HttpRestResult<>();
        String mcpId = UUID.randomUUID().toString();
        mockRestResult.setData(JacksonUtils.toJson(Result.success(mcpId)));
        when(clientHttpProxy.executeSyncHttpRequest(any(HttpRequest.class))).thenReturn(mockRestResult);
        McpServerBasicInfo serverSpec = new McpServerBasicInfo();
        serverSpec.setId(mcpId);
        serverSpec.setName("test");
        serverSpec.setProtocol(AiConstants.Mcp.MCP_PROTOCOL_SSE);
        McpEndpointSpec endpointSpec = new McpEndpointSpec();
        endpointSpec.setType(AiConstants.Mcp.MCP_ENDPOINT_TYPE_DIRECT);
        endpointSpec.setData(Collections.singletonMap("address", "127.0.0.1"));
        assertEquals(aiMaintainerService.createRemoteMcpServer("test", serverSpec, endpointSpec), mcpId);
    }
    
    @Test
    void createRemoteMcpServerWithNullSpec() {
        assertThrows(NacosException.class, () -> aiMaintainerService.createRemoteMcpServer("test", null, null),
                "Mcp server specification cannot be null.");
        assertThrows(NacosException.class,
                () -> aiMaintainerService.createRemoteMcpServer("test", new McpServerBasicInfo(), null),
                "Mcp server endpoint specification cannot be null.");
    }
    
    @Test
    void createRemoteMcpServerWithIllegalProtocol() {
        McpServerBasicInfo serverSpec = new McpServerBasicInfo();
        serverSpec.setName("test");
        serverSpec.setProtocol(AiConstants.Mcp.MCP_PROTOCOL_STDIO);
        McpEndpointSpec endpointSpec = new McpEndpointSpec();
        endpointSpec.setType(AiConstants.Mcp.MCP_ENDPOINT_TYPE_DIRECT);
        endpointSpec.setData(Collections.singletonMap("address", "127.0.0.1"));
        assertThrows(NacosException.class,
                () -> aiMaintainerService.createRemoteMcpServer("test", serverSpec, endpointSpec),
                "Mcp server protocol cannot be `stdio` or empty.");
    }
    
    @Test
    void updateMcpServer() throws NacosException {
        McpServerBasicInfo serverSpec = new McpServerBasicInfo();
        serverSpec.setName("test");
        serverSpec.setProtocol(AiConstants.Mcp.MCP_PROTOCOL_STDIO);
        McpToolSpecification toolSpec = new McpToolSpecification();
        McpTool mcpTool = new McpTool();
        mcpTool.setName("testTool");
        mcpTool.setName("testToolDescription");
        toolSpec.setTools(Collections.singletonList(mcpTool));
        toolSpec.setToolsMeta(Collections.singletonMap("testTool", new McpToolMeta()));
        final HttpRestResult<String> mockRestResult = new HttpRestResult<>();
        mockRestResult.setData(JacksonUtils.toJson(Result.success("ok")));
        when(clientHttpProxy.executeSyncHttpRequest(any(HttpRequest.class))).thenReturn(mockRestResult);
        assertTrue(aiMaintainerService.updateMcpServer("test", serverSpec, toolSpec, null));
    }
    
    @Test
    void updateMcpServerWithOverrideExisting() throws NacosException {
        McpServerBasicInfo serverSpec = new McpServerBasicInfo();
        serverSpec.setName("test");
        serverSpec.setProtocol(AiConstants.Mcp.MCP_PROTOCOL_STDIO);
        McpToolSpecification toolSpec = new McpToolSpecification();
        McpTool mcpTool = new McpTool();
        mcpTool.setName("testTool");
        mcpTool.setName("testToolDescription");
        toolSpec.setTools(Collections.singletonList(mcpTool));
        toolSpec.setToolsMeta(Collections.singletonMap("testTool", new McpToolMeta()));
        final HttpRestResult<String> mockRestResult = new HttpRestResult<>();
        mockRestResult.setData(JacksonUtils.toJson(Result.success("ok")));
        when(clientHttpProxy.executeSyncHttpRequest(any(HttpRequest.class))).thenReturn(mockRestResult);
        assertTrue(aiMaintainerService.updateMcpServer("public", "test", true, serverSpec, toolSpec, null, true));
    }
    
    @Test
    void deleteMcpServer() throws NacosException {
        final HttpRestResult<String> mockRestResult = new HttpRestResult<>();
        mockRestResult.setData(JacksonUtils.toJson(Result.success("ok")));
        when(clientHttpProxy.executeSyncHttpRequest(any(HttpRequest.class))).thenReturn(mockRestResult);
        assertTrue(aiMaintainerService.deleteMcpServer("test"));
    }
    
    @Test
    void registerAgent() throws NacosException {
        AgentCard agentCard = new AgentCard();
        agentCard.setName("testAgent");
        agentCard.setVersion("1.0.0");
        
        final HttpRestResult<String> mockRestResult = new HttpRestResult<>();
        mockRestResult.setData(JacksonUtils.toJson(Result.success("ok")));
        when(clientHttpProxy.executeSyncHttpRequest(any(HttpRequest.class))).thenReturn(mockRestResult);
        
        assertTrue(aiMaintainerService.registerAgent(agentCard, "public", "url"));
    }
    
    @Test
    void getAgentCard() throws NacosException {
        AgentCardDetailInfo expected = new AgentCardDetailInfo();
        expected.setName("testAgent");
        expected.setVersion("1.0.0");
        expected.setRegistrationType("url");
        expected.setLatestVersion(true);
        
        final HttpRestResult<String> mockRestResult = new HttpRestResult<>();
        mockRestResult.setData(JacksonUtils.toJson(Result.success(expected)));
        when(clientHttpProxy.executeSyncHttpRequest(any(HttpRequest.class))).thenReturn(mockRestResult);
        
        AgentCardDetailInfo actual = aiMaintainerService.getAgentCard("testAgent", "public", "url");
        assertNotNull(actual);
        assertEquals("testAgent", actual.getName());
        assertEquals("1.0.0", actual.getVersion());
        assertEquals("url", actual.getRegistrationType());
        assertTrue(actual.isLatestVersion());
    }
    
    @Test
    void updateAgentCard() throws NacosException {
        AgentCard agentCard = new AgentCard();
        agentCard.setName("testAgent");
        agentCard.setVersion("1.0.0");
        
        final HttpRestResult<String> mockRestResult = new HttpRestResult<>();
        mockRestResult.setData(JacksonUtils.toJson(Result.success("ok")));
        when(clientHttpProxy.executeSyncHttpRequest(any(HttpRequest.class))).thenReturn(mockRestResult);
        
        assertTrue(aiMaintainerService.updateAgentCard(agentCard, "public", true, "url"));
    }
    
    @Test
    void deleteAgent() throws NacosException {
        final HttpRestResult<String> mockRestResult = new HttpRestResult<>();
        mockRestResult.setData(JacksonUtils.toJson(Result.success("ok")));
        when(clientHttpProxy.executeSyncHttpRequest(any(HttpRequest.class))).thenReturn(mockRestResult);
        
        assertTrue(aiMaintainerService.deleteAgent("testAgent", "public", "1.0.0"));
    }
    
    @Test
    void listAllVersionOfAgent() throws NacosException {
        AgentVersionDetail versionDetail = new AgentVersionDetail();
        versionDetail.setVersion("1.0.0");
        versionDetail.setCreatedAt("2024-01-01T00:00:00");
        versionDetail.setUpdatedAt("2024-01-01T00:00:00");
        versionDetail.setLatest(true);
        
        List<AgentVersionDetail> expectedVersions = Collections.singletonList(versionDetail);
        
        final HttpRestResult<String> mockRestResult = new HttpRestResult<>();
        mockRestResult.setData(JacksonUtils.toJson(Result.success(expectedVersions)));
        when(clientHttpProxy.executeSyncHttpRequest(any(HttpRequest.class))).thenReturn(mockRestResult);
        
        List<AgentVersionDetail> actualVersions = aiMaintainerService.listAllVersionOfAgent("testAgent", "public");
        assertNotNull(actualVersions);
        assertEquals(1, actualVersions.size());
        assertEquals("1.0.0", actualVersions.get(0).getVersion());
        assertEquals("2024-01-01T00:00:00", actualVersions.get(0).getCreatedAt());
        assertTrue(actualVersions.get(0).isLatest());
    }
    
    @Test
    void searchAgentCardsByName() throws NacosException {
        AgentCardVersionInfo agentCardVersionInfo = new AgentCardVersionInfo();
        agentCardVersionInfo.setName("testAgent");
        agentCardVersionInfo.setLatestPublishedVersion("1.0.0");
        
        Page<AgentCardVersionInfo> page = new Page<>();
        page.setPagesAvailable(1);
        page.setTotalCount(1);
        page.setPageNumber(1);
        page.setPageItems(Collections.singletonList(agentCardVersionInfo));
        
        final HttpRestResult<String> mockRestResult = new HttpRestResult<>();
        mockRestResult.setData(JacksonUtils.toJson(Result.success(page)));
        when(clientHttpProxy.executeSyncHttpRequest(any(HttpRequest.class))).thenReturn(mockRestResult);
        
        Page<AgentCardVersionInfo> actual = aiMaintainerService.searchAgentCardsByName("public", "test", 1, 10);
        assertNotNull(actual);
        assertEquals(1, actual.getTotalCount());
        assertEquals(1, actual.getPageItems().size());
        assertEquals("testAgent", actual.getPageItems().get(0).getName());
    }
    
    @Test
    void listAgentCards() throws NacosException {
        AgentCardVersionInfo agentCardVersionInfo = new AgentCardVersionInfo();
        agentCardVersionInfo.setName("testAgent");
        agentCardVersionInfo.setLatestPublishedVersion("1.0.0");
        
        Page<AgentCardVersionInfo> page = new Page<>();
        page.setPagesAvailable(1);
        page.setTotalCount(1);
        page.setPageNumber(1);
        page.setPageItems(Collections.singletonList(agentCardVersionInfo));
        
        final HttpRestResult<String> mockRestResult = new HttpRestResult<>();
        mockRestResult.setData(JacksonUtils.toJson(Result.success(page)));
        when(clientHttpProxy.executeSyncHttpRequest(any(HttpRequest.class))).thenReturn(mockRestResult);
        
        Page<AgentCardVersionInfo> actual = aiMaintainerService.listAgentCards("public", "testAgent", 1, 10);
        assertNotNull(actual);
        assertEquals(1, actual.getTotalCount());
        assertEquals(1, actual.getPageItems().size());
        assertEquals("testAgent", actual.getPageItems().get(0).getName());
    }
}