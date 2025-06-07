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

package com.alibaba.nacos.ai.index;

import com.alibaba.nacos.ai.constant.Constants;
import com.alibaba.nacos.ai.model.mcp.McpServerIndexData;
import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.api.ai.model.mcp.McpServerBasicInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpServerVersionInfo;
import com.alibaba.nacos.api.ai.model.mcp.registry.ServerVersionDetail;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.api.model.response.Namespace;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.config.server.model.ConfigInfo;
import com.alibaba.nacos.config.server.service.ConfigDetailService;
import com.alibaba.nacos.config.server.service.query.ConfigQueryChainService;
import com.alibaba.nacos.config.server.service.query.model.ConfigQueryChainRequest;
import com.alibaba.nacos.config.server.service.query.model.ConfigQueryChainResponse;
import com.alibaba.nacos.core.service.NamespaceOperationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlainMcpServerIndexTest {
    
    @Mock
    private ConfigDetailService configDetailService;
    
    @Mock
    private NamespaceOperationService namespaceOperationService;
    
    @Mock
    private ConfigQueryChainService configQueryChainService;
    
    PlainMcpServerIndex plainMcpServerIndex;
    
    @BeforeEach
    void setUp() {
        plainMcpServerIndex = new PlainMcpServerIndex(namespaceOperationService, configDetailService,
                configQueryChainService);
    }
    
    @AfterEach
    void tearDown() {
    }
    
    @Test
    void searchMcpServerByNameWithNamespaceIdByAccurate() {
        Page<ConfigInfo> countPage = mockConfigInfo(1, 1, AiConstants.Mcp.MCP_DEFAULT_NAMESPACE);
        when(configDetailService.findConfigInfoPage(eq(Constants.MCP_LIST_SEARCH_ACCURATE), eq(1), eq(1), isNull(),
                eq(Constants.MCP_SERVER_VERSIONS_GROUP), eq(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE),
                anyMap())).thenReturn(countPage);
        Page<ConfigInfo> searchPage = mockConfigInfo(1, 1, AiConstants.Mcp.MCP_DEFAULT_NAMESPACE);
        when(configDetailService.findConfigInfoPage(eq(Constants.MCP_LIST_SEARCH_ACCURATE), eq(1), eq(10), isNull(),
                eq(Constants.MCP_SERVER_VERSIONS_GROUP), eq(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE),
                anyMap())).thenReturn(searchPage);
        Page<McpServerIndexData> result = plainMcpServerIndex.searchMcpServerByName(
                AiConstants.Mcp.MCP_DEFAULT_NAMESPACE, "mcpName", Constants.MCP_LIST_SEARCH_ACCURATE, 0, 10);
        assertEquals(1, result.getTotalCount());
        assertEquals(1, result.getPageNumber());
        assertEquals(1, result.getPagesAvailable());
        assertEquals(1, result.getPageItems().size());
    }
    
    @Test
    void searchMcpServerByNameWithMultiNamespaceIdByAccurate() {
        when(namespaceOperationService.getNamespaceList()).thenReturn(mockNamespaceList(2, true));
        Page<ConfigInfo> countNs1Page = mockConfigInfo(1, 1, AiConstants.Mcp.MCP_DEFAULT_NAMESPACE);
        Page<ConfigInfo> countNs2Page = mockConfigInfo(1, 1, "namespaceId-0");
        when(configDetailService.findConfigInfoPage(eq(Constants.MCP_LIST_SEARCH_ACCURATE), eq(1), eq(1), isNull(),
                eq(Constants.MCP_SERVER_VERSIONS_GROUP), eq(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE),
                anyMap())).thenReturn(countNs1Page);
        when(configDetailService.findConfigInfoPage(eq(Constants.MCP_LIST_SEARCH_ACCURATE), eq(1), eq(1), isNull(),
                eq(Constants.MCP_SERVER_VERSIONS_GROUP), eq("namespaceId-0"), anyMap())).thenReturn(countNs2Page);
        Page<ConfigInfo> searchNs1Page = mockConfigInfo(1, 1, AiConstants.Mcp.MCP_DEFAULT_NAMESPACE);
        when(configDetailService.findConfigInfoPage(eq(Constants.MCP_LIST_SEARCH_ACCURATE), eq(1), eq(9), isNull(),
                eq(Constants.MCP_SERVER_VERSIONS_GROUP), eq(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE),
                anyMap())).thenReturn(searchNs1Page);
        Page<ConfigInfo> searchNs2Page = mockConfigInfo(1, 1, "namespaceId-0");
        when(configDetailService.findConfigInfoPage(eq(Constants.MCP_LIST_SEARCH_ACCURATE), eq(1), eq(10), isNull(),
                eq(Constants.MCP_SERVER_VERSIONS_GROUP), eq("namespaceId-0"), anyMap())).thenReturn(searchNs2Page);
        Page<McpServerIndexData> result = plainMcpServerIndex.searchMcpServerByName(null, "mcpName",
                Constants.MCP_LIST_SEARCH_ACCURATE, 0, 10);
        assertEquals(2, result.getTotalCount());
        assertEquals(1, result.getPageNumber());
        assertEquals(1, result.getPagesAvailable());
        assertEquals(2, result.getPageItems().size());
    }
    
    @Test
    void searchMcpServerByNameWithNamespaceIdByBlur() {
        Page<ConfigInfo> countPage = mockConfigInfo(10, 1, AiConstants.Mcp.MCP_DEFAULT_NAMESPACE);
        when(configDetailService.findConfigInfoPage(eq(Constants.MCP_LIST_SEARCH_BLUR), eq(1), eq(1), eq("*"),
                eq(Constants.MCP_SERVER_VERSIONS_GROUP), eq(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE),
                anyMap())).thenReturn(countPage);
        Page<ConfigInfo> searchPage = mockConfigInfo(10, 10, AiConstants.Mcp.MCP_DEFAULT_NAMESPACE);
        when(configDetailService.findConfigInfoPage(eq(Constants.MCP_LIST_SEARCH_BLUR), eq(1), eq(10), eq("*"),
                eq(Constants.MCP_SERVER_VERSIONS_GROUP), eq(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE),
                anyMap())).thenReturn(searchPage);
        Page<McpServerIndexData> result = plainMcpServerIndex.searchMcpServerByName(
                AiConstants.Mcp.MCP_DEFAULT_NAMESPACE, null, Constants.MCP_LIST_SEARCH_BLUR, 0, 10);
        assertEquals(10, result.getTotalCount());
        assertEquals(1, result.getPageNumber());
        assertEquals(1, result.getPagesAvailable());
        assertEquals(10, result.getPageItems().size());
    }
    
    @Test
    void searchMcpServerByNameWithMultiNamespaceIdByBlur() {
        when(namespaceOperationService.getNamespaceList()).thenReturn(mockNamespaceList(2, true));
        Page<ConfigInfo> countNs1Page = mockConfigInfo(2, 1, AiConstants.Mcp.MCP_DEFAULT_NAMESPACE);
        Page<ConfigInfo> countNs2Page = mockConfigInfo(2, 1, "namespaceId-0");
        when(configDetailService.findConfigInfoPage(eq(Constants.MCP_LIST_SEARCH_BLUR), eq(1), eq(1), eq("*"),
                eq(Constants.MCP_SERVER_VERSIONS_GROUP), eq(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE),
                anyMap())).thenReturn(countNs1Page);
        when(configDetailService.findConfigInfoPage(eq(Constants.MCP_LIST_SEARCH_BLUR), eq(1), eq(1), eq("*"),
                eq(Constants.MCP_SERVER_VERSIONS_GROUP), eq("namespaceId-0"), anyMap())).thenReturn(countNs2Page);
        Page<ConfigInfo> searchNs1Page = mockConfigInfo(2, 2, AiConstants.Mcp.MCP_DEFAULT_NAMESPACE);
        when(configDetailService.findConfigInfoPage(eq(Constants.MCP_LIST_SEARCH_BLUR), eq(1), eq(8), eq("*"),
                eq(Constants.MCP_SERVER_VERSIONS_GROUP), eq(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE),
                anyMap())).thenReturn(searchNs1Page);
        Page<ConfigInfo> searchNs2Page = mockConfigInfo(2, 2, "namespaceId-0");
        when(configDetailService.findConfigInfoPage(eq(Constants.MCP_LIST_SEARCH_BLUR), eq(1), eq(10), eq("*"),
                eq(Constants.MCP_SERVER_VERSIONS_GROUP), eq("namespaceId-0"), anyMap())).thenReturn(searchNs2Page);
        Page<McpServerIndexData> result = plainMcpServerIndex.searchMcpServerByName(null, "mcpName",
                Constants.MCP_LIST_SEARCH_BLUR, 0, 10);
        assertEquals(4, result.getTotalCount());
        assertEquals(1, result.getPageNumber());
        assertEquals(1, result.getPagesAvailable());
        assertEquals(4, result.getPageItems().size());
    }
    
    @Test
    void searchMcpServerByNameWithMultiNamespaceIdByBlurLimit() {
        when(namespaceOperationService.getNamespaceList()).thenReturn(mockNamespaceList(2, true));
        Page<ConfigInfo> countNs1Page = mockConfigInfo(2, 1, AiConstants.Mcp.MCP_DEFAULT_NAMESPACE);
        Page<ConfigInfo> countNs2Page = mockConfigInfo(2, 1, "namespaceId-0");
        when(configDetailService.findConfigInfoPage(eq(Constants.MCP_LIST_SEARCH_BLUR), eq(1), eq(1), eq("*"),
                eq(Constants.MCP_SERVER_VERSIONS_GROUP), eq(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE),
                anyMap())).thenReturn(countNs1Page);
        when(configDetailService.findConfigInfoPage(eq(Constants.MCP_LIST_SEARCH_BLUR), eq(1), eq(1), eq("*"),
                eq(Constants.MCP_SERVER_VERSIONS_GROUP), eq("namespaceId-0"), anyMap())).thenReturn(countNs2Page);
        Page<ConfigInfo> searchNs2Page = mockConfigInfo(2, 2, "namespaceId-0");
        when(configDetailService.findConfigInfoPage(eq(Constants.MCP_LIST_SEARCH_BLUR), eq(1), eq(2), eq("*"),
                eq(Constants.MCP_SERVER_VERSIONS_GROUP), eq("namespaceId-0"), anyMap())).thenReturn(searchNs2Page);
        Page<McpServerIndexData> result = plainMcpServerIndex.searchMcpServerByName(null, "mcpName",
                Constants.MCP_LIST_SEARCH_BLUR, 0, 2);
        assertEquals(4, result.getTotalCount());
        assertEquals(1, result.getPageNumber());
        assertEquals(2, result.getPagesAvailable());
        assertEquals(2, result.getPageItems().size());
    }
    
    @Test
    void searchMcpServerByNameWithMultiNamespaceIdByBlurOffsetLimit() {
        when(namespaceOperationService.getNamespaceList()).thenReturn(mockNamespaceList(2, true));
        Page<ConfigInfo> countNs1Page = mockConfigInfo(2, 1, AiConstants.Mcp.MCP_DEFAULT_NAMESPACE);
        Page<ConfigInfo> countNs2Page = mockConfigInfo(2, 1, "namespaceId-0");
        when(configDetailService.findConfigInfoPage(eq(Constants.MCP_LIST_SEARCH_BLUR), eq(1), eq(1), eq("*"),
                eq(Constants.MCP_SERVER_VERSIONS_GROUP), eq(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE),
                anyMap())).thenReturn(countNs1Page);
        when(configDetailService.findConfigInfoPage(eq(Constants.MCP_LIST_SEARCH_BLUR), eq(1), eq(1), eq("*"),
                eq(Constants.MCP_SERVER_VERSIONS_GROUP), eq("namespaceId-0"), anyMap())).thenReturn(countNs2Page);
        Page<ConfigInfo> searchNs1Page = mockConfigInfo(2, 2, AiConstants.Mcp.MCP_DEFAULT_NAMESPACE);
        when(configDetailService.findConfigInfoPage(eq(Constants.MCP_LIST_SEARCH_BLUR), eq(1), eq(2), eq("*"),
                eq(Constants.MCP_SERVER_VERSIONS_GROUP), eq(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE),
                anyMap())).thenReturn(searchNs1Page);
        Page<ConfigInfo> searchNs2Page = mockConfigInfo(2, 2, "namespaceId-0");
        when(configDetailService.findConfigInfoPage(eq(Constants.MCP_LIST_SEARCH_BLUR), eq(1), eq(4), eq("*"),
                eq(Constants.MCP_SERVER_VERSIONS_GROUP), eq("namespaceId-0"), anyMap())).thenReturn(searchNs2Page);
        Page<McpServerIndexData> result = plainMcpServerIndex.searchMcpServerByName(null, "mcpName",
                Constants.MCP_LIST_SEARCH_BLUR, 2, 2);
        assertEquals(4, result.getTotalCount());
        assertEquals(2, result.getPageNumber());
        assertEquals(2, result.getPagesAvailable());
        assertEquals(2, result.getPageItems().size());
    }
    
    @Test
    void searchMcpServerByNameWithMultiNamespaceIdByBlurLargeOffset() {
        when(namespaceOperationService.getNamespaceList()).thenReturn(mockNamespaceList(2, true));
        Page<ConfigInfo> countNs1Page = mockConfigInfo(2, 1, AiConstants.Mcp.MCP_DEFAULT_NAMESPACE);
        Page<ConfigInfo> countNs2Page = mockConfigInfo(2, 1, "namespaceId-0");
        when(configDetailService.findConfigInfoPage(eq(Constants.MCP_LIST_SEARCH_BLUR), eq(1), eq(1), eq("*"),
                eq(Constants.MCP_SERVER_VERSIONS_GROUP), eq(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE),
                anyMap())).thenReturn(countNs1Page);
        when(configDetailService.findConfigInfoPage(eq(Constants.MCP_LIST_SEARCH_BLUR), eq(1), eq(1), eq("*"),
                eq(Constants.MCP_SERVER_VERSIONS_GROUP), eq("namespaceId-0"), anyMap())).thenReturn(countNs2Page);
        Page<McpServerIndexData> result = plainMcpServerIndex.searchMcpServerByName(null, "mcpName",
                Constants.MCP_LIST_SEARCH_BLUR, 100, 2);
        assertEquals(4, result.getTotalCount());
        assertEquals(51, result.getPageNumber());
        assertEquals(2, result.getPagesAvailable());
        assertEquals(0, result.getPageItems().size());
    }
    
    @Test
    void getMcpServerByIdWithEmptyId() {
        assertNull(plainMcpServerIndex.getMcpServerById(""));
    }
    
    @Test
    void getMcpServerByIdNotFound() {
        String id = UUID.randomUUID().toString();
        when(namespaceOperationService.getNamespaceList()).thenReturn(mockNamespaceList(1, false));
        when(configQueryChainService.handle(any(ConfigQueryChainRequest.class))).thenReturn(
                mockConfigQueryChainResponse(null));
        assertNull(plainMcpServerIndex.getMcpServerById(id));
    }
    
    @Test
    void getMcpServerByIdFound() {
        String id = UUID.randomUUID().toString();
        when(namespaceOperationService.getNamespaceList()).thenReturn(mockNamespaceList(1, false));
        McpServerBasicInfo mcpServerBasicInfo = mockServerVersionInfo(id);
        when(configQueryChainService.handle(any(ConfigQueryChainRequest.class))).thenReturn(
                mockConfigQueryChainResponse(mcpServerBasicInfo));
        McpServerIndexData result = plainMcpServerIndex.getMcpServerById(id);
        assertEquals(id, result.getId());
        assertEquals("namespaceId-0", result.getNamespaceId());
    }
    
    @Test
    void getMcpServerByNameNotFound() {
        Page<ConfigInfo> countPage = mockConfigInfo(0, 0, AiConstants.Mcp.MCP_DEFAULT_NAMESPACE);
        when(configDetailService.findConfigInfoPage(eq(Constants.MCP_LIST_SEARCH_ACCURATE), eq(1), eq(1), isNull(),
                eq(Constants.MCP_SERVER_VERSIONS_GROUP), eq(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE),
                anyMap())).thenReturn(countPage);
        assertNull(plainMcpServerIndex.getMcpServerByName(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE, "mcpName"));
    }
    
    @Test
    void getMcpServerByNameFound() {
        Page<ConfigInfo> countPage = mockConfigInfo(1, 1, AiConstants.Mcp.MCP_DEFAULT_NAMESPACE);
        when(configDetailService.findConfigInfoPage(eq(Constants.MCP_LIST_SEARCH_ACCURATE), eq(1), eq(1), isNull(),
                eq(Constants.MCP_SERVER_VERSIONS_GROUP), eq(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE),
                anyMap())).thenReturn(countPage);
        McpServerIndexData result = plainMcpServerIndex.getMcpServerByName(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE,
                "mcpName");
        assertNotNull(result);
        assertEquals(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE, result.getNamespaceId());
        assertDoesNotThrow(() -> UUID.fromString(result.getId()));
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
    
    private List<Namespace> mockNamespaceList(int size, boolean withDefaultNs) {
        List<Namespace> list = new LinkedList<>();
        if (withDefaultNs) {
            Namespace namespace = new Namespace();
            namespace.setNamespace(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE);
            namespace.setNamespaceShowName(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE);
            list.add(namespace);
        }
        size = withDefaultNs ? size - 1 : size;
        for (int i = 0; i < size; i++) {
            Namespace namespace = new Namespace();
            namespace.setNamespace("namespaceId-" + i);
            namespace.setNamespaceShowName("namespace-" + i);
            list.add(namespace);
        }
        return list;
    }
    
    private Page<ConfigInfo> mockConfigInfo(int total, int size, String namespaceId) {
        Page<ConfigInfo> mockConfigInfo = new Page<>();
        mockConfigInfo.setTotalCount(total);
        mockConfigInfo.setPagesAvailable(size == 0 ? 0 : total / size);
        mockConfigInfo.setPageNumber(1);
        List<ConfigInfo> list = new LinkedList<>();
        for (int i = 0; i < size; i++) {
            ConfigInfo configInfo = new ConfigInfo();
            configInfo.setTenant(namespaceId);
            configInfo.setContent(JacksonUtils.toJson(mockServerVersionInfo(UUID.randomUUID().toString())));
            list.add(configInfo);
        }
        mockConfigInfo.setPageItems(list);
        return mockConfigInfo;
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
}