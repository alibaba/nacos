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

package com.alibaba.nacos.ai.service;

import com.alibaba.nacos.ai.constant.Constants;
import com.alibaba.nacos.ai.index.McpServerIndex;
import com.alibaba.nacos.ai.model.mcp.McpServerIndexData;
import com.alibaba.nacos.ai.model.mcp.McpServerStorageInfo;
import com.alibaba.nacos.ai.utils.McpConfigUtils;
import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.api.ai.model.mcp.McpEndpointSpec;
import com.alibaba.nacos.api.ai.model.mcp.McpServerBasicInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpServerDetailInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpServerRemoteServiceConfig;
import com.alibaba.nacos.api.ai.model.mcp.McpServerVersionInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpServiceRef;
import com.alibaba.nacos.api.ai.model.mcp.McpTool;
import com.alibaba.nacos.api.ai.model.mcp.McpToolSpecification;
import com.alibaba.nacos.api.ai.model.mcp.registry.ServerVersionDetail;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.config.server.model.ConfigRequestInfo;
import com.alibaba.nacos.config.server.model.form.ConfigFormV3;
import com.alibaba.nacos.config.server.service.ConfigOperationService;
import com.alibaba.nacos.config.server.service.query.ConfigQueryChainService;
import com.alibaba.nacos.config.server.service.query.model.ConfigQueryChainRequest;
import com.alibaba.nacos.config.server.service.query.model.ConfigQueryChainResponse;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class McpServerOperationServiceTest {
    
    @Mock
    private ConfigQueryChainService configQueryChainService;
    
    @Mock
    private ConfigOperationService configOperationService;
    
    @Mock
    private McpToolOperationService toolOperationService;
    
    @Mock
    private McpEndpointOperationService endpointOperationService;
    
    @Mock
    private McpServerIndex mcpServerIndex;
    
    McpServerOperationService serverOperationService;
    
    @BeforeEach
    void setUp() {
        serverOperationService = new McpServerOperationService(configQueryChainService, configOperationService,
                toolOperationService, endpointOperationService, mcpServerIndex);
    }
    
    @AfterEach
    void tearDown() {
    }
    
    @Test
    void listMcpServerWithPage() {
        String id = mockId();
        Page<McpServerIndexData> mockIndexData = mockIndexData(id);
        McpServerVersionInfo mockMcpServer = mockServerVersionInfo(id);
        when(mcpServerIndex.searchMcpServerByName(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE, null,
                Constants.MCP_LIST_SEARCH_ACCURATE, 0, 100)).thenReturn(mockIndexData);
        ConfigQueryChainResponse mockResponse = mockConfigQueryChainResponse(mockMcpServer);
        when(configQueryChainService.handle(any(ConfigQueryChainRequest.class))).thenReturn(mockResponse);
        Page<McpServerBasicInfo> result = serverOperationService.listMcpServerWithPage(
                AiConstants.Mcp.MCP_DEFAULT_NAMESPACE, null, Constants.MCP_LIST_SEARCH_ACCURATE, 1, 100);
        assertEquals(1, result.getPageNumber());
        assertEquals(1, result.getPagesAvailable());
        assertEquals(1, result.getTotalCount());
        assertEquals(1, result.getPageItems().size());
        assertEquals(id, result.getPageItems().get(0).getId());
        assertEquals("mcpName", result.getPageItems().get(0).getName());
        assertEquals("9.9.9", result.getPageItems().get(0).getVersion());
    }
    
    @Test
    void listMcpServerWithOffset() {
        String id = mockId();
        Page<McpServerIndexData> mockIndexData = mockIndexData(id);
        McpServerVersionInfo mockMcpServer = mockServerVersionInfo(id);
        when(mcpServerIndex.searchMcpServerByName(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE, null,
                Constants.MCP_LIST_SEARCH_ACCURATE, 0, 100)).thenReturn(mockIndexData);
        ConfigQueryChainResponse mockResponse = mockConfigQueryChainResponse(mockMcpServer);
        when(configQueryChainService.handle(any(ConfigQueryChainRequest.class))).thenReturn(mockResponse);
        Page<McpServerBasicInfo> result = serverOperationService.listMcpServerWithOffset(
                AiConstants.Mcp.MCP_DEFAULT_NAMESPACE, null, Constants.MCP_LIST_SEARCH_ACCURATE, 0, 100);
        assertEquals(1, result.getPageNumber());
        assertEquals(1, result.getPagesAvailable());
        assertEquals(1, result.getTotalCount());
        assertEquals(1, result.getPageItems().size());
        assertEquals(id, result.getPageItems().get(0).getId());
        assertEquals("mcpName", result.getPageItems().get(0).getName());
        assertEquals("9.9.9", result.getPageItems().get(0).getVersion());
    }
    
    @Test
    void listMcpServerWithOverPageNo() {
        String id = mockId();
        Page<McpServerIndexData> mockIndexData = new Page<>();
        mockIndexData.setPageNumber(10);
        mockIndexData.setPagesAvailable(1);
        mockIndexData.setTotalCount(1);
        mockIndexData.setPageItems(Collections.emptyList());
        when(mcpServerIndex.searchMcpServerByName(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE, null,
                Constants.MCP_LIST_SEARCH_ACCURATE, 900, 100)).thenReturn(mockIndexData);
        Page<McpServerBasicInfo> result = serverOperationService.listMcpServerWithPage(
                AiConstants.Mcp.MCP_DEFAULT_NAMESPACE, null, Constants.MCP_LIST_SEARCH_ACCURATE, 10, 100);
        assertEquals(10, result.getPageNumber());
        assertEquals(1, result.getPagesAvailable());
        assertEquals(1, result.getTotalCount());
        assertEquals(0, result.getPageItems().size());
    }
    
    @Test
    void getMcpServerDetailByIdFoundStdioTypeWithToolsWithNamespace() throws NacosException {
        String id = mockId();
        ConfigQueryChainResponse versionDataResponse = mockConfigQueryChainResponse(mockServerVersionInfo(id));
        ConfigQueryChainResponse storageDataResponse = mockConfigQueryChainResponse(
                mockStorageInfo(id, true, true, AiConstants.Mcp.MCP_PROTOCOL_STDIO));
        when(configQueryChainService.handle(any(ConfigQueryChainRequest.class))).thenReturn(versionDataResponse,
                storageDataResponse);
        when(toolOperationService.getMcpTool(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE,
                McpConfigUtils.formatServerToolSpecDataId(id, "9.9.9"))).thenReturn(new McpToolSpecification());
        McpServerDetailInfo actual = serverOperationService.getMcpServerDetail(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE,
                id, null, null);
        assertEquals(id, actual.getId());
        assertEquals("mcpName", actual.getName());
        assertEquals("9.9.9", actual.getVersion());
        assertEquals("9.9.9", actual.getVersionDetail().getVersion());
        assertTrue(actual.getVersionDetail().getIs_latest());
        assertNotNull(actual.getToolSpec());
    }
    
    @Test
    void getMcpServerDetailByIdFoundSseTypeWithoutToolsWithNamespace() throws NacosException {
        String id = mockId();
        final ConfigQueryChainResponse versionDataResponse = mockConfigQueryChainResponse(mockServerVersionInfo(id));
        final McpServerStorageInfo mockStorageInfo = mockStorageInfo(id, true, false, AiConstants.Mcp.MCP_PROTOCOL_SSE);
        final McpServerRemoteServiceConfig remoteServiceConfig = new McpServerRemoteServiceConfig();
        McpServiceRef serviceRef = new McpServiceRef();
        serviceRef.setNamespaceId(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE);
        serviceRef.setNamespaceId(Constants.MCP_SERVER_ENDPOINT_GROUP);
        serviceRef.setServiceName("mcpName");
        remoteServiceConfig.setServiceRef(serviceRef);
        mockStorageInfo.setRemoteServerConfig(remoteServiceConfig);
        ConfigQueryChainResponse storageDataResponse = mockConfigQueryChainResponse(mockStorageInfo);
        when(configQueryChainService.handle(any(ConfigQueryChainRequest.class))).thenReturn(versionDataResponse,
                storageDataResponse);
        Instance instance = new Instance();
        instance.setIp("127.0.0.1");
        instance.setPort(8848);
        when(endpointOperationService.getMcpServerEndpointInstances(any(McpServiceRef.class))).thenReturn(
                Collections.singletonList(instance));
        McpServerDetailInfo actual = serverOperationService.getMcpServerDetail(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE,
                id, null, null);
        assertEquals(id, actual.getId());
        assertEquals("mcpName", actual.getName());
        assertEquals("9.9.9", actual.getVersion());
        assertEquals("9.9.9", actual.getVersionDetail().getVersion());
        assertTrue(actual.getVersionDetail().getIs_latest());
        assertNull(actual.getToolSpec());
        verify(toolOperationService, never()).getMcpTool(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE,
                McpConfigUtils.formatServerToolSpecDataId(id, "9.9.9"));
        assertEquals("127.0.0.1", actual.getBackendEndpoints().get(0).getAddress());
        assertEquals(8848, actual.getBackendEndpoints().get(0).getPort());
    }
    
    @Test
    void getMcpServerDetailByIdFoundHttpTypeWithoutToolsWithNamespace() throws NacosException {
        String id = mockId();
        final ConfigQueryChainResponse versionDataResponse = mockConfigQueryChainResponse(mockServerVersionInfo(id));
        final McpServerStorageInfo mockStorageInfo = mockStorageInfo(id, true, false,
                AiConstants.Mcp.MCP_PROTOCOL_HTTP);
        final McpServerRemoteServiceConfig remoteServiceConfig = new McpServerRemoteServiceConfig();
        McpServiceRef serviceRef = new McpServiceRef();
        serviceRef.setNamespaceId(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE);
        serviceRef.setNamespaceId(Constants.MCP_SERVER_ENDPOINT_GROUP);
        serviceRef.setServiceName("mcpName");
        remoteServiceConfig.setServiceRef(serviceRef);
        mockStorageInfo.setRemoteServerConfig(remoteServiceConfig);
        ConfigQueryChainResponse storageDataResponse = mockConfigQueryChainResponse(mockStorageInfo);
        when(configQueryChainService.handle(any(ConfigQueryChainRequest.class))).thenReturn(versionDataResponse,
                storageDataResponse);
        Instance instance = new Instance();
        instance.setIp("127.0.0.1");
        instance.setPort(8848);
        instance.setMetadata(Collections.singletonMap(Constants.META_PATH, "/nacos"));
        when(endpointOperationService.getMcpServerEndpointInstances(any(McpServiceRef.class))).thenReturn(
                Collections.singletonList(instance));
        McpServerDetailInfo actual = serverOperationService.getMcpServerDetail(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE,
                id, null, null);
        assertEquals(id, actual.getId());
        assertEquals("mcpName", actual.getName());
        assertEquals("9.9.9", actual.getVersion());
        assertEquals("9.9.9", actual.getVersionDetail().getVersion());
        assertTrue(actual.getVersionDetail().getIs_latest());
        assertNull(actual.getToolSpec());
        verify(toolOperationService, never()).getMcpTool(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE,
                McpConfigUtils.formatServerToolSpecDataId(id, "9.9.9"));
        assertEquals("127.0.0.1", actual.getBackendEndpoints().get(0).getAddress());
        assertEquals(8848, actual.getBackendEndpoints().get(0).getPort());
        assertEquals("/nacos", actual.getBackendEndpoints().get(0).getPath());
    }
    
    @Test
    void getMcpServerDetailByIdNotFoundWithNamespace() throws NacosException {
        String id = mockId();
        when(configQueryChainService.handle(any(ConfigQueryChainRequest.class))).thenReturn(
                mockConfigQueryChainResponse(null));
        assertThrows(NacosApiException.class,
                () -> serverOperationService.getMcpServerDetail(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE, id, null, null));
    }
    
    @Test
    void getMcpServerDetailByIdFoundWithNamespace() throws NacosException {
        String id = mockId();
        ConfigQueryChainResponse versionDataResponse = mockConfigQueryChainResponse(mockServerVersionInfo(id));
        ConfigQueryChainResponse storageDataResponse = mockConfigQueryChainResponse(
                mockStorageInfo(id, true, false, AiConstants.Mcp.MCP_PROTOCOL_STDIO));
        when(configQueryChainService.handle(any(ConfigQueryChainRequest.class))).thenReturn(versionDataResponse,
                storageDataResponse);
        McpServerDetailInfo actual = serverOperationService.getMcpServerDetail(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE,
                id, null, null);
        assertEquals(id, actual.getId());
        assertEquals("mcpName", actual.getName());
        assertEquals("9.9.9", actual.getVersion());
        assertEquals("9.9.9", actual.getVersionDetail().getVersion());
        assertTrue(actual.getVersionDetail().getIs_latest());
        assertNull(actual.getToolSpec());
    }
    
    @Test
    void getMcpServerDetailByIdNotFoundWithVersion() throws NacosException {
        String id = mockId();
        ConfigQueryChainResponse versionDataResponse = mockConfigQueryChainResponse(mockServerVersionInfo(id));
        ConfigQueryChainResponse storageDataResponse = mockConfigQueryChainResponse(null);
        when(configQueryChainService.handle(any(ConfigQueryChainRequest.class))).thenReturn(versionDataResponse,
                storageDataResponse);
        assertThrows(NacosApiException.class,
                () -> serverOperationService.getMcpServerDetail(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE, id, null,
                        "non-exist"));
    }
    
    @Test
    void getMcpServerDetailByIdFoundWithVersion() throws NacosException {
        String id = mockId();
        ConfigQueryChainResponse versionDataResponse = mockConfigQueryChainResponse(mockServerVersionInfo(id));
        ConfigQueryChainResponse storageDataResponse = mockConfigQueryChainResponse(
                mockStorageInfo(id, false, false, AiConstants.Mcp.MCP_PROTOCOL_STDIO));
        when(configQueryChainService.handle(any(ConfigQueryChainRequest.class))).thenReturn(versionDataResponse,
                storageDataResponse);
        McpServerDetailInfo actual = serverOperationService.getMcpServerDetail(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE,
                id, null, "1.0.0");
        assertEquals(id, actual.getId());
        assertEquals("mcpName", actual.getName());
        assertEquals("1.0.0", actual.getVersion());
        assertEquals("1.0.0", actual.getVersionDetail().getVersion());
        assertFalse(actual.getVersionDetail().getIs_latest());
        assertNull(actual.getToolSpec());
    }
    
    @Test
    void getMcpServerDetailByNameNotFound() throws NacosException {
        when(configQueryChainService.handle(any(ConfigQueryChainRequest.class))).thenReturn(
                mockConfigQueryChainResponse(null));
        assertThrows(NacosApiException.class,
                () -> serverOperationService.getMcpServerDetail(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE, null, "mcpName",
                        null));
    }
    
    @Test
    void getMcpServerDetailByNameFound() throws NacosException {
        String id = mockId();
        when(mcpServerIndex.getMcpServerByName(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE, "mcpName")).thenReturn(
                mockIndexData(id).getPageItems().get(0));
        ConfigQueryChainResponse versionDataResponse = mockConfigQueryChainResponse(mockServerVersionInfo(id));
        ConfigQueryChainResponse storageDataResponse = mockConfigQueryChainResponse(
                mockStorageInfo(id, true, false, AiConstants.Mcp.MCP_PROTOCOL_STDIO));
        when(configQueryChainService.handle(any(ConfigQueryChainRequest.class))).thenReturn(versionDataResponse,
                storageDataResponse);
        McpServerDetailInfo actual = serverOperationService.getMcpServerDetail(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE,
                null, "mcpName", null);
        assertEquals(id, actual.getId());
        assertEquals("mcpName", actual.getName());
        assertEquals("9.9.9", actual.getVersion());
        assertEquals("9.9.9", actual.getVersionDetail().getVersion());
        assertTrue(actual.getVersionDetail().getIs_latest());
        assertNull(actual.getToolSpec());
    }
    
    @Test
    void createMcpServerExistedName() {
        McpServerBasicInfo mockServerBasicInfo = mockServerVersionInfo("");
        when(mcpServerIndex.getMcpServerByName(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE, "mcpName")).thenReturn(
                mockIndexData("id").getPageItems().get(0));
        assertThrows(NacosApiException.class,
                () -> serverOperationService.createMcpServer(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE, mockServerBasicInfo,
                        null, null));
    }
    
    @Test
    void createMcpServerWithoutVersion() throws NacosException {
        McpServerBasicInfo mockServerBasicInfo = mockServerVersionInfo("");
        mockServerBasicInfo.setVersionDetail(null);
        assertThrows(NacosApiException.class,
                () -> serverOperationService.createMcpServer(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE, mockServerBasicInfo,
                        null, null));
    }
    
    @Test
    void createMcpServerWithOldSpec() throws NacosException {
        McpServerBasicInfo mockServerBasicInfo = mockServerVersionInfo("");
        mockServerBasicInfo.setVersionDetail(null);
        mockServerBasicInfo.setVersion("1.0.0");
        String id = serverOperationService.createMcpServer(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE, mockServerBasicInfo,
                null, null);
        assertNotNull(id);
        verify(configOperationService, times(2)).publishConfig(any(ConfigFormV3.class), any(ConfigRequestInfo.class),
                isNull());
    }
    
    @Test
    void createMcpServerWithNewSpec() throws NacosException {
        McpServerBasicInfo mockServerBasicInfo = mockServerVersionInfo("");
        mockServerBasicInfo.setVersion(null);
        mockServerBasicInfo.setVersionDetail(mockVersion("1.0.0"));
        String id = serverOperationService.createMcpServer(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE, mockServerBasicInfo,
                null, null);
        assertNotNull(id);
        verify(configOperationService, times(2)).publishConfig(any(ConfigFormV3.class), any(ConfigRequestInfo.class),
                isNull());
    }
    
    @Test
    void createMcpServerWithToolSpec() throws NacosException {
        McpServerBasicInfo mockServerBasicInfo = mockServerVersionInfo("");
        mockServerBasicInfo.setVersionDetail(mockVersion("1.0.0"));
        McpToolSpecification toolSpecification = new McpToolSpecification();
        toolSpecification.getTools().add(new McpTool());
        String id = serverOperationService.createMcpServer(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE, mockServerBasicInfo,
                toolSpecification, null);
        assertNotNull(id);
        verify(configOperationService, times(2)).publishConfig(any(ConfigFormV3.class), any(ConfigRequestInfo.class),
                isNull());
        verify(toolOperationService).refreshMcpTool(eq(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE),
                any(McpServerStorageInfo.class), eq(toolSpecification));
    }
    
    @Test
    void createMcpServerWithEndpointSpec() throws NacosException {
        McpServerBasicInfo mockServerBasicInfo = mockServerVersionInfo("");
        mockServerBasicInfo.setVersionDetail(mockVersion("1.0.0"));
        mockServerBasicInfo.setRemoteServerConfig(new McpServerRemoteServiceConfig());
        McpEndpointSpec endpointSpec = new McpEndpointSpec();
        endpointSpec.setType(AiConstants.Mcp.MCP_ENDPOINT_TYPE_DIRECT);
        endpointSpec.setData(new HashMap<>());
        endpointSpec.getData().put(Constants.MCP_SERVER_ENDPOINT_ADDRESS, "127.0.0.1");
        endpointSpec.getData().put(Constants.MCP_SERVER_ENDPOINT_PORT, "8848");
        when(endpointOperationService.createMcpServerEndpointServiceIfNecessary(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE,
                "mcpName", endpointSpec)).thenReturn(
                Service.newService(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE, Constants.MCP_SERVER_ENDPOINT_GROUP,
                        "mcpName"));
        String id = serverOperationService.createMcpServer(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE, mockServerBasicInfo,
                null, endpointSpec);
        assertNotNull(id);
        verify(configOperationService, times(2)).publishConfig(any(ConfigFormV3.class), any(ConfigRequestInfo.class),
                isNull());
    }
    
    @Test
    void updateMcpServerByIdNotFound() {
        String id = mockId();
        McpServerBasicInfo mockServerBasicInfo = mockServerVersionInfo(id);
        assertThrows(NacosApiException.class,
                () -> serverOperationService.updateMcpServer(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE, true,
                        mockServerBasicInfo, null, null));
    }
    
    @Test
    void updateMcpServerByIdWithoutVersion() {
        String id = mockId();
        McpServerBasicInfo mockServerBasicInfo = mockServerVersionInfo(id);
        mockServerBasicInfo.setVersionDetail(null);
        mockServerBasicInfo.setVersion(null);
        assertThrows(NacosApiException.class,
                () -> serverOperationService.updateMcpServer(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE, true,
                        mockServerBasicInfo, null, null));
    }
    
    @Test
    void updateMcpServerByIdWithOldSpec() throws NacosException {
        String id = mockId();
        McpServerVersionInfo mockServerBasicInfo = mockServerVersionInfo(id);
        mockServerBasicInfo.setVersionDetail(null);
        mockServerBasicInfo.setVersion("1.0.0");
        ConfigQueryChainResponse response = mockConfigQueryChainResponse(mockServerBasicInfo);
        when(configQueryChainService.handle(any(ConfigQueryChainRequest.class))).thenReturn(response);
        serverOperationService.updateMcpServer(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE, true, mockServerBasicInfo, null,
                null);
        verify(configOperationService, times(2)).publishConfig(any(ConfigFormV3.class), any(ConfigRequestInfo.class),
                isNull());
    }
    
    @Test
    void updateMcpServerByIdWithNewSpec() throws NacosException {
        String id = mockId();
        McpServerVersionInfo mockServerBasicInfo = mockServerVersionInfo(id);
        mockServerBasicInfo.setVersionDetail(mockVersion("9.9.9"));
        mockServerBasicInfo.setVersion(null);
        ConfigQueryChainResponse response = mockConfigQueryChainResponse(mockServerBasicInfo);
        when(configQueryChainService.handle(any(ConfigQueryChainRequest.class))).thenReturn(response);
        serverOperationService.updateMcpServer(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE, true, mockServerBasicInfo, null,
                null);
        verify(configOperationService, times(2)).publishConfig(any(ConfigFormV3.class), any(ConfigRequestInfo.class),
                isNull());
    }
    
    @Test
    void updateMcpServerByIdNewVersion() throws NacosException {
        String id = mockId();
        McpServerVersionInfo mockServerBasicInfo = mockServerVersionInfo(id);
        mockServerBasicInfo.setVersionDetail(mockVersion("1.0.1"));
        mockServerBasicInfo.setVersion(null);
        ConfigQueryChainResponse response = mockConfigQueryChainResponse(mockServerBasicInfo);
        when(configQueryChainService.handle(any(ConfigQueryChainRequest.class))).thenReturn(response);
        serverOperationService.updateMcpServer(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE, true, mockServerBasicInfo, null,
                null);
        verify(configOperationService, times(2)).publishConfig(any(ConfigFormV3.class), any(ConfigRequestInfo.class),
                isNull());
    }
    
    @Test
    void updateMcpServerByNameNotFound() {
        McpServerVersionInfo mockServerBasicInfo = mockServerVersionInfo(null);
        assertThrows(NacosApiException.class,
                () -> serverOperationService.updateMcpServer(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE, true,
                        mockServerBasicInfo, null, null));
    }
    
    @Test
    void updateMcpServerByNameFound() throws NacosException {
        String id = mockId();
        McpServerVersionInfo mockServerBasicInfo = mockServerVersionInfo(null);
        when(mcpServerIndex.getMcpServerByName(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE, "mcpName")).thenReturn(
                mockIndexData(id).getPageItems().get(0));
        mockServerBasicInfo.setVersionDetail(mockVersion("9.9.9"));
        ConfigQueryChainResponse response = mockConfigQueryChainResponse(mockServerBasicInfo);
        when(configQueryChainService.handle(any(ConfigQueryChainRequest.class))).thenReturn(response);
        serverOperationService.updateMcpServer(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE, true, mockServerBasicInfo, null,
                null);
        verify(configOperationService, times(2)).publishConfig(any(ConfigFormV3.class), any(ConfigRequestInfo.class),
                isNull());
    }
    
    @Test
    void deleteMcpServerByIdNotFound() {
        String id = mockId();
        when(configQueryChainService.handle(any(ConfigQueryChainRequest.class))).thenReturn(
                mockConfigQueryChainResponse(null));
        assertThrows(NacosApiException.class,
                () -> serverOperationService.deleteMcpServer(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE, null, id, null));
    }
    
    @Test
    void deleteMcpServerById() throws NacosException {
        String id = mockId();
        when(configQueryChainService.handle(any(ConfigQueryChainRequest.class))).thenReturn(
                mockConfigQueryChainResponse(mockServerVersionInfo(id)));
        serverOperationService.deleteMcpServer(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE, null, id, null);
        verify(endpointOperationService, times(2)).deleteMcpServerEndpointService(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE,
                "mcpName");
        String serverVersionDataId = McpConfigUtils.formatServerVersionInfoDataId(id);
        verify(configOperationService, times(2)).deleteConfig(serverVersionDataId, Constants.MCP_SERVER_VERSIONS_GROUP,
                AiConstants.Mcp.MCP_DEFAULT_NAMESPACE, null, null, "nacos", null);
        for (ServerVersionDetail each : mockServerVersionInfo(id).getVersionDetails()) {
            verify(toolOperationService).deleteMcpTool(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE, id, each.getVersion());
            String serverSpecDataId = McpConfigUtils.formatServerSpecInfoDataId(id, each.getVersion());
            verify(configOperationService).deleteConfig(serverSpecDataId, Constants.MCP_SERVER_GROUP,
                    AiConstants.Mcp.MCP_DEFAULT_NAMESPACE, null, null, "nacos", null);
        }
    }
    
    @Test
    void deleteMcpServerByName() throws NacosException {
        String id = mockId();
        McpServerVersionInfo mockServerBasicInfo = mockServerVersionInfo(null);
        when(mcpServerIndex.getMcpServerByName(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE, "mcpName")).thenReturn(
                mockIndexData(id).getPageItems().get(0));
        when(configQueryChainService.handle(any(ConfigQueryChainRequest.class))).thenReturn(
                mockConfigQueryChainResponse(mockServerBasicInfo));
        serverOperationService.deleteMcpServer(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE, "mcpName", null, null);
        verify(endpointOperationService, times(2)).deleteMcpServerEndpointService(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE,
                "mcpName");
        String serverVersionDataId = McpConfigUtils.formatServerVersionInfoDataId(id);
        verify(configOperationService, times(2)).deleteConfig(serverVersionDataId, Constants.MCP_SERVER_VERSIONS_GROUP,
                AiConstants.Mcp.MCP_DEFAULT_NAMESPACE, null, null, "nacos", null);
        for (ServerVersionDetail each : mockServerVersionInfo(id).getVersionDetails()) {
            verify(toolOperationService).deleteMcpTool(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE, id, each.getVersion());
            String serverSpecDataId = McpConfigUtils.formatServerSpecInfoDataId(id, each.getVersion());
            verify(configOperationService).deleteConfig(serverSpecDataId, Constants.MCP_SERVER_GROUP,
                    AiConstants.Mcp.MCP_DEFAULT_NAMESPACE, null, null, "nacos", null);
        }
    }
    
    @Test
    void deleteMcpServerForTargetVersion() throws NacosException {
        String id = mockId();
        when(configQueryChainService.handle(any(ConfigQueryChainRequest.class))).thenReturn(
                mockConfigQueryChainResponse(mockServerVersionInfo(id)));
        serverOperationService.deleteMcpServer(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE, null, id, "1.0.0");
        verify(endpointOperationService).deleteMcpServerEndpointService(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE,
                "mcpName");
        String serverVersionDataId = McpConfigUtils.formatServerVersionInfoDataId(id);
        verify(configOperationService).deleteConfig(serverVersionDataId, Constants.MCP_SERVER_VERSIONS_GROUP,
                AiConstants.Mcp.MCP_DEFAULT_NAMESPACE, null, null, "nacos", null);
        verify(toolOperationService).deleteMcpTool(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE, id, "1.0.0");
        String serverSpecDataId = McpConfigUtils.formatServerSpecInfoDataId(id, "1.0.0");
        verify(configOperationService).deleteConfig(serverSpecDataId, Constants.MCP_SERVER_GROUP,
                AiConstants.Mcp.MCP_DEFAULT_NAMESPACE, null, null, "nacos", null);
    }
    
    private Page<McpServerIndexData> mockIndexData(String id) {
        Page<McpServerIndexData> indexDataPage = new Page<>();
        indexDataPage.setPageNumber(1);
        indexDataPage.setPagesAvailable(1);
        indexDataPage.setTotalCount(1);
        indexDataPage.getPageItems().add(new McpServerIndexData());
        indexDataPage.getPageItems().get(0).setId(id);
        indexDataPage.getPageItems().get(0).setNamespaceId(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE);
        return indexDataPage;
    }
    
    private ConfigQueryChainResponse mockConfigQueryChainResponse(Object obj) {
        ConfigQueryChainResponse mockResponse = new ConfigQueryChainResponse();
        if (null != obj) {
            mockResponse.setContent(JacksonUtils.toJson(obj));
            mockResponse.setStatus(ConfigQueryChainResponse.ConfigQueryStatus.CONFIG_FOUND_FORMAL);
        } else {
            mockResponse.setStatus(ConfigQueryChainResponse.ConfigQueryStatus.CONFIG_NOT_FOUND);
        }
        return mockResponse;
    }
    
    private McpServerVersionInfo mockServerVersionInfo(String id) {
        McpServerVersionInfo serverVersionInfo = new McpServerVersionInfo();
        serverVersionInfo.setId(id);
        serverVersionInfo.setName("mcpName");
        serverVersionInfo.setLatestPublishedVersion("9.9.9");
        List<ServerVersionDetail> versionDetails = new LinkedList<>();
        versionDetails.add(mockVersion("1.0.0"));
        versionDetails.add(mockVersion("9.9.9"));
        serverVersionInfo.setVersions(versionDetails);
        return serverVersionInfo;
    }
    
    private ServerVersionDetail mockVersion(String version) {
        ServerVersionDetail versionDetail = new ServerVersionDetail();
        versionDetail.setVersion(version);
        return versionDetail;
    }
    
    private McpServerStorageInfo mockStorageInfo(String id, boolean isLatest, boolean withTools, String protocol) {
        McpServerStorageInfo storageInfo = new McpServerStorageInfo();
        storageInfo.setId(id);
        storageInfo.setProtocol(protocol);
        storageInfo.setName("mcpName");
        String version = isLatest ? "9.9.9" : "1.0.0";
        storageInfo.setVersion(version);
        storageInfo.setVersionDetail(mockVersion(version));
        storageInfo.setToolsDescriptionRef(withTools ? McpConfigUtils.formatServerToolSpecDataId(id, version) : null);
        return storageInfo;
    }
    
    private String mockId() {
        return UUID.randomUUID().toString();
    }
}