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
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NacosAiMaintainerServiceImplTest {
    
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
        mcpServerDetailInfo.setVersion("1.0.0");
        mockRestResult.setData(JacksonUtils.toJson(Result.success(mcpServerDetailInfo)));
        when(clientHttpProxy.executeSyncHttpRequest(any(HttpRequest.class))).thenReturn(mockRestResult);
        McpServerDetailInfo actual = aiMaintainerService.getMcpServerDetail("test");
        assertEquals(mcpServerDetailInfo.getName(), actual.getName());
        assertEquals(mcpServerDetailInfo.getVersion(), actual.getVersion());
    }
    
    @Test
    void createLocalMcpServer() throws NacosException {
        final HttpRestResult<String> mockRestResult = new HttpRestResult<>();
        mockRestResult.setData(JacksonUtils.toJson(Result.success("ok")));
        when(clientHttpProxy.executeSyncHttpRequest(any(HttpRequest.class))).thenReturn(mockRestResult);
        assertTrue(aiMaintainerService.createLocalMcpServer("test", "1.0.0"));
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
        McpToolSpecification toolSpec = new McpToolSpecification();
        McpTool mcpTool = new McpTool();
        mcpTool.setName("testTool");
        mcpTool.setName("testToolDescription");
        toolSpec.setTools(Collections.singletonList(mcpTool));
        toolSpec.setToolsMeta(Collections.singletonMap("testTool", new McpToolMeta()));
        final HttpRestResult<String> mockRestResult = new HttpRestResult<>();
        mockRestResult.setData(JacksonUtils.toJson(Result.success("ok")));
        when(clientHttpProxy.executeSyncHttpRequest(any(HttpRequest.class))).thenReturn(mockRestResult);
        assertTrue(aiMaintainerService.createLocalMcpServer("test", serverSpec, toolSpec));
    }
    
    @Test
    void createRemoteMcpServer() throws NacosException {
        final HttpRestResult<String> mockRestResult = new HttpRestResult<>();
        mockRestResult.setData(JacksonUtils.toJson(Result.success("ok")));
        when(clientHttpProxy.executeSyncHttpRequest(any(HttpRequest.class))).thenReturn(mockRestResult);
        McpEndpointSpec endpointSpec = new McpEndpointSpec();
        endpointSpec.setType(AiConstants.Mcp.MCP_ENDPOINT_TYPE_DIRECT);
        endpointSpec.setData(Collections.singletonMap("address", "127.0.0.1"));
        assertTrue(aiMaintainerService.createRemoteMcpServer("test", "1.0.0", AiConstants.Mcp.MCP_PROTOCOL_SSE,
                endpointSpec));
    }
    
    @Test
    void createRemoteMcpServerWithSpec() throws NacosException {
        final HttpRestResult<String> mockRestResult = new HttpRestResult<>();
        mockRestResult.setData(JacksonUtils.toJson(Result.success("ok")));
        when(clientHttpProxy.executeSyncHttpRequest(any(HttpRequest.class))).thenReturn(mockRestResult);
        McpServerBasicInfo serverSpec = new McpServerBasicInfo();
        serverSpec.setName("test");
        serverSpec.setProtocol(AiConstants.Mcp.MCP_PROTOCOL_SSE);
        McpEndpointSpec endpointSpec = new McpEndpointSpec();
        endpointSpec.setType(AiConstants.Mcp.MCP_ENDPOINT_TYPE_DIRECT);
        endpointSpec.setData(Collections.singletonMap("address", "127.0.0.1"));
        assertTrue(aiMaintainerService.createRemoteMcpServer("test", serverSpec, endpointSpec));
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
    void deleteMcpServer() throws NacosException {
        final HttpRestResult<String> mockRestResult = new HttpRestResult<>();
        mockRestResult.setData(JacksonUtils.toJson(Result.success("ok")));
        when(clientHttpProxy.executeSyncHttpRequest(any(HttpRequest.class))).thenReturn(mockRestResult);
        assertTrue(aiMaintainerService.deleteMcpServer("test"));
    }
}