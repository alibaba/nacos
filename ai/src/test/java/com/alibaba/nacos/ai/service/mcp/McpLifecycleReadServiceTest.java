/*
 * Copyright 1999-2026 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.ai.service.mcp;

import com.alibaba.nacos.ai.constant.AiResourceConstants;
import com.alibaba.nacos.ai.constant.Constants;
import com.alibaba.nacos.ai.model.AiResource;
import com.alibaba.nacos.ai.model.AiResourceVersion;
import com.alibaba.nacos.ai.model.mcp.McpResourceExt;
import com.alibaba.nacos.ai.model.mcp.McpServerStorageInfo;
import com.alibaba.nacos.ai.model.mcp.McpVersionStorageDescriptor;
import com.alibaba.nacos.ai.service.McpEndpointReadService;
import com.alibaba.nacos.ai.service.mcp.storage.McpResourceExtSerializer;
import com.alibaba.nacos.ai.service.mcp.storage.McpServingManifestStorage;
import com.alibaba.nacos.ai.service.mcp.storage.McpVersionStorageContents;
import com.alibaba.nacos.ai.service.mcp.storage.McpVersionStorageDescriptorSerializer;
import com.alibaba.nacos.ai.service.mcp.storage.McpVersionStorageKeyComposer;
import com.alibaba.nacos.ai.service.mcp.storage.McpVersionStorageService;
import com.alibaba.nacos.ai.service.repository.QueryCondition;
import com.alibaba.nacos.ai.service.resource.AiResourceManager;
import com.alibaba.nacos.ai.service.resource.ResourceVersionInfo;
import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.api.ai.model.mcp.McpEndpointInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpResourceSpecification;
import com.alibaba.nacos.api.ai.model.mcp.McpServerBasicInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpServerDetailInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpServerRemoteServiceConfig;
import com.alibaba.nacos.api.ai.model.mcp.McpServerVersionInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpToolSpecification;
import com.alibaba.nacos.api.ai.model.mcp.registry.ServerVersionDetail;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.plugin.visibility.constant.VisibilityConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class McpLifecycleReadServiceTest {
    
    private static final String NAMESPACE_ID = "public";
    
    private static final String MCP_NAME = "demo-mcp";
    
    private static final String MCP_ID = "4d7939c0-72ea-4ef4-b232-418d1e16b45c";
    
    private static final String VERSION_ONE = "1.0.0";
    
    private static final String VERSION_TWO = "2.0.0";
    
    @Mock
    private McpResourceLocator resourceLocator;
    
    @Mock
    private AiResourceManager resourceManager;
    
    @Mock
    private McpVersionStorageService versionStorageService;
    
    @Mock
    private McpServingManifestStorage manifestStorage;
    
    @Mock
    private McpEndpointReadService endpointReadService;
    
    @Mock
    private McpCanonicalAuthorizationService authorizationService;
    
    private McpLifecycleReadService readService;
    
    private AiResource resource;
    
    private McpServerVersionInfo manifest;
    
    private AiResourceVersion versionOne;
    
    private AiResourceVersion versionTwo;
    
    @BeforeEach
    void setUp() throws Exception {
        readService = new McpLifecycleReadService(resourceLocator, resourceManager,
            versionStorageService, manifestStorage, endpointReadService, authorizationService);
        resource = resource();
        manifest = manifest();
        versionOne = version(VERSION_ONE, false, false);
        versionTwo = version(VERSION_TWO, false, false);
        when(resourceLocator.locate(anyString(), any(), any())).thenReturn(resource);
        doNothing().when(authorizationService).authorizeRead(anyString(), anyString());
        doNothing().when(resourceManager).ensureReadableOrNotFound(any(), anyString());
        when(manifestStorage.get(NAMESPACE_ID, MCP_ID)).thenReturn(manifest);
        when(resourceManager.listVersions(NAMESPACE_ID, MCP_NAME,
            AiResourceConstants.RESOURCE_TYPE_MCP, AiResourceConstants.VERSION_STATUS_ONLINE,
            1, 100)).thenReturn(page(2, 1, versionOne, versionTwo));
        when(resourceManager.findVersion(eq(NAMESPACE_ID), eq(MCP_NAME),
            eq(AiResourceConstants.RESOURCE_TYPE_MCP), anyString()))
            .thenAnswer(invocation -> VERSION_ONE.equals(invocation.getArgument(3)) ? versionOne
                : versionTwo);
        when(versionStorageService.load(any(McpVersionStorageDescriptor.class)))
            .thenAnswer(invocation -> contents(versionFromDescriptor(invocation.getArgument(0)),
                false, false, AiConstants.Mcp.MCP_PROTOCOL_STDIO));
    }
    
    @Test
    void testGetByIdLoadsLifecycleDescriptor() throws Exception {
        McpServerDetailInfo result = readService.getMcpServer(NAMESPACE_ID, null, MCP_ID,
            VERSION_ONE);
        
        assertEquals(MCP_ID, result.getId());
        assertEquals(MCP_NAME, result.getName());
        assertEquals(VERSION_ONE, result.getVersion());
        assertFalse(result.getVersionDetail().getIs_latest());
        assertEquals(2, result.getAllVersions().size());
        assertTrue(result.getAllVersions().get(1).getIs_latest());
        verify(resourceLocator).locate(NAMESPACE_ID, null, MCP_ID);
        verify(versionStorageService).load(any(McpVersionStorageDescriptor.class));
        verify(endpointReadService, never()).injectEndpoint(any());
    }
    
    @Test
    void testGetLatestLoadsToolsResourcesAndEndpoints() throws Exception {
        versionTwo = version(VERSION_TWO, true, true);
        when(resourceManager.findVersion(NAMESPACE_ID, MCP_NAME,
            AiResourceConstants.RESOURCE_TYPE_MCP, VERSION_TWO)).thenReturn(versionTwo);
        when(versionStorageService.load(any(McpVersionStorageDescriptor.class)))
            .thenReturn(contents(VERSION_TWO, true, true, AiConstants.Mcp.MCP_PROTOCOL_HTTP));
        doAnswer(invocation -> {
            McpServerDetailInfo detail = invocation.getArgument(0);
            McpEndpointInfo endpoint = new McpEndpointInfo();
            endpoint.setAddress("127.0.0.1");
            detail.setFrontendEndpoints(Collections.singletonList(endpoint));
            return null;
        }).when(endpointReadService).injectEndpoint(any());
        
        McpServerDetailInfo result = readService.getMcpServer(NAMESPACE_ID, MCP_NAME, MCP_ID,
            null);
        
        assertEquals(VERSION_TWO, result.getVersion());
        assertTrue(result.getVersionDetail().getIs_latest());
        assertNotNull(result.getToolSpec());
        assertNotNull(result.getResourceSpec());
        assertEquals("127.0.0.1", result.getFrontendEndpoints().get(0).getAddress());
        verify(endpointReadService).injectEndpoint(result);
    }
    
    @Test
    void testGetAuthorizesCanonicalNameBeforeContentRead() throws Exception {
        readService.getMcpServer(NAMESPACE_ID, null, MCP_ID, VERSION_ONE);
        
        InOrder order = inOrder(resourceLocator, authorizationService, resourceManager,
            manifestStorage, versionStorageService);
        order.verify(resourceLocator).locate(NAMESPACE_ID, null, MCP_ID);
        order.verify(authorizationService).authorizeRead(NAMESPACE_ID, MCP_NAME);
        order.verify(resourceManager).ensureReadableOrNotFound(resource,
            "MCP server not found: " + MCP_NAME);
        order.verify(manifestStorage).get(NAMESPACE_ID, MCP_ID);
        order.verify(versionStorageService).load(any(McpVersionStorageDescriptor.class));
    }
    
    @Test
    void testGetDeniedStopsBeforeManifestAndContent() throws Exception {
        NacosApiException denied = new NacosApiException(NacosException.NO_RIGHT,
            com.alibaba.nacos.api.model.v2.ErrorCode.ACCESS_DENIED, "denied");
        doThrow(denied).when(authorizationService).authorizeRead(NAMESPACE_ID, MCP_NAME);
        
        assertEquals(denied, assertThrows(NacosApiException.class,
            () -> readService.getMcpServer(NAMESPACE_ID, null, MCP_ID, VERSION_ONE)));
        verifyNoInteractions(manifestStorage, versionStorageService);
    }
    
    @Test
    void testGetMissingVersionReturnsCompatibleError() throws Exception {
        when(resourceManager.findVersion(NAMESPACE_ID, MCP_NAME,
            AiResourceConstants.RESOURCE_TYPE_MCP, "missing")).thenReturn(null);
        
        NacosApiException error = assertThrows(NacosApiException.class,
            () -> readService.getMcpServer(NAMESPACE_ID, MCP_NAME, null, "missing"));
        
        assertEquals(NacosException.NOT_FOUND, error.getErrCode());
        assertEquals(com.alibaba.nacos.api.model.v2.ErrorCode.MCP_SEVER_VERSION_NOT_FOUND.getCode(),
            error.getDetailErrCode());
        verify(versionStorageService, never()).load(any());
    }
    
    @Test
    void testGetRejectsOptionalContentMismatch() throws Exception {
        versionTwo = version(VERSION_TWO, true, false);
        when(resourceManager.findVersion(NAMESPACE_ID, MCP_NAME,
            AiResourceConstants.RESOURCE_TYPE_MCP, VERSION_TWO)).thenReturn(versionTwo);
        McpVersionStorageContents mismatched = contents(VERSION_TWO, true, false,
            AiConstants.Mcp.MCP_PROTOCOL_STDIO);
        McpServerStorageInfo server = JacksonUtils.toObj(
            new String(mismatched.getServerContent(), StandardCharsets.UTF_8),
            McpServerStorageInfo.class);
        server.setToolsDescriptionRef(null);
        when(versionStorageService.load(any())).thenReturn(new McpVersionStorageContents(
            bytes(server), mismatched.getToolContent(), null));
        
        assertIntegrityFailure(() -> readService.getMcpServer(NAMESPACE_ID, MCP_NAME, null,
            VERSION_TWO));
    }
    
    @Test
    void testGetRejectsInvalidDescriptorAndServerIdentity() throws Exception {
        versionOne.setStorage("{}");
        assertIntegrityFailure(() -> readService.getMcpServer(NAMESPACE_ID, MCP_NAME, null,
            VERSION_ONE));
        
        versionOne = version(VERSION_ONE, false, false);
        when(resourceManager.findVersion(NAMESPACE_ID, MCP_NAME,
            AiResourceConstants.RESOURCE_TYPE_MCP, VERSION_ONE)).thenReturn(versionOne);
        McpServerStorageInfo invalidServer = server(VERSION_ONE,
            AiConstants.Mcp.MCP_PROTOCOL_STDIO);
        invalidServer.setName("other");
        when(versionStorageService.load(any())).thenReturn(new McpVersionStorageContents(
            bytes(invalidServer), null, null));
        assertIntegrityFailure(() -> readService.getMcpServer(NAMESPACE_ID, MCP_NAME, null,
            VERSION_ONE));
    }
    
    @Test
    void testListUsesVisibilityQueryAndCompatibleManifestProjection() throws Exception {
        QueryCondition condition = new QueryCondition();
        when(resourceManager.generateLikeArgument("*demo*")).thenReturn("%demo%");
        when(resourceManager.buildQueryCondition(NAMESPACE_ID,
            AiResourceConstants.RESOURCE_TYPE_MCP, "%demo%", null,
            VisibilityConstants.ACTION_READ)).thenReturn(condition);
        when(resourceManager.listMeta(condition, 2, 10)).thenReturn(page(11, 2, resource));
        
        Page<McpServerBasicInfo> result = readService.listMcpServers(NAMESPACE_ID, "demo",
            Constants.MCP_LIST_SEARCH_BLUR, 2, 10);
        
        assertEquals(11, result.getTotalCount());
        assertEquals(2, result.getPageNumber());
        assertEquals(1, result.getPageItems().size());
        McpServerBasicInfo item = result.getPageItems().get(0);
        assertTrue(item instanceof McpServerVersionInfo);
        assertEquals(MCP_ID, item.getId());
        assertEquals(MCP_NAME, item.getName());
        assertEquals(VERSION_TWO, item.getVersion());
        assertTrue(item.getVersionDetail().getIs_latest());
        verify(resourceManager).buildQueryCondition(NAMESPACE_ID,
            AiResourceConstants.RESOURCE_TYPE_MCP, "%demo%", null,
            VisibilityConstants.ACTION_READ);
    }
    
    @Test
    void testListAccurateUsesUnmodifiedName() throws Exception {
        QueryCondition condition = new QueryCondition();
        when(resourceManager.buildQueryCondition(NAMESPACE_ID,
            AiResourceConstants.RESOURCE_TYPE_MCP, MCP_NAME, null,
            VisibilityConstants.ACTION_READ)).thenReturn(condition);
        when(resourceManager.listMeta(condition, 1, 10)).thenReturn(page(1, 1, resource));
        
        readService.listMcpServers(NAMESPACE_ID, MCP_NAME,
            Constants.MCP_LIST_SEARCH_ACCURATE, 1, 10);
        
        verify(resourceManager, never()).generateLikeArgument(anyString());
    }
    
    @Test
    void testListAlwaysEmptySkipsLifecycleStorage() throws Exception {
        QueryCondition condition = new QueryCondition();
        condition.setAlwaysEmpty(true);
        when(resourceManager.buildQueryCondition(NAMESPACE_ID,
            AiResourceConstants.RESOURCE_TYPE_MCP, null, null,
            VisibilityConstants.ACTION_READ)).thenReturn(condition);
        
        Page<McpServerBasicInfo> result = readService.listMcpServers(NAMESPACE_ID, null,
            Constants.MCP_LIST_SEARCH_ACCURATE, 3, 10);
        
        assertTrue(result.getPageItems().isEmpty());
        assertEquals(3, result.getPageNumber());
        verify(resourceManager, never()).listMeta(any(), anyInt(), anyInt());
        verifyNoInteractions(manifestStorage, versionStorageService);
    }
    
    @Test
    void testRejectsManifestIdentityLatestAndStatusDrift() throws Exception {
        manifest.setName("other");
        assertIntegrityFailure(() -> readService.getMcpServer(NAMESPACE_ID, MCP_NAME, null, null));
        
        manifest = manifest();
        manifest.setLatestPublishedVersion(VERSION_ONE);
        when(manifestStorage.get(NAMESPACE_ID, MCP_ID)).thenReturn(manifest);
        assertIntegrityFailure(() -> readService.getMcpServer(NAMESPACE_ID, MCP_NAME, null, null));
        
        manifest = manifest();
        manifest.setEnabled(false);
        when(manifestStorage.get(NAMESPACE_ID, MCP_ID)).thenReturn(manifest);
        assertIntegrityFailure(() -> readService.getMcpServer(NAMESPACE_ID, MCP_NAME, null, null));
    }
    
    @Test
    void testRejectsVersionSetAndOnlineCountDrift() throws Exception {
        when(resourceManager.listVersions(NAMESPACE_ID, MCP_NAME,
            AiResourceConstants.RESOURCE_TYPE_MCP, AiResourceConstants.VERSION_STATUS_ONLINE,
            1, 100)).thenReturn(page(1, 1, versionTwo));
        assertIntegrityFailure(() -> readService.getMcpServer(NAMESPACE_ID, MCP_NAME, null, null));
        
        when(resourceManager.listVersions(NAMESPACE_ID, MCP_NAME,
            AiResourceConstants.RESOURCE_TYPE_MCP, AiResourceConstants.VERSION_STATUS_ONLINE,
            1, 100)).thenReturn(page(2, 1, versionOne, versionTwo));
        ResourceVersionInfo versionInfo = AiResourceManager.parseVersionInfo(
            resource.getVersionInfo());
        versionInfo.setOnlineCnt(1);
        resource.setVersionInfo(JacksonUtils.toJson(versionInfo));
        assertIntegrityFailure(() -> readService.getMcpServer(NAMESPACE_ID, MCP_NAME, null, null));
    }
    
    @Test
    void testRejectsMalformedResourceAndManifestVersionData() throws Exception {
        resource.setExt("{}");
        assertIntegrityFailure(() -> readService.getMcpServer(NAMESPACE_ID, MCP_NAME, null, null));
        
        resource = resource();
        when(resourceLocator.locate(anyString(), any(), any())).thenReturn(resource);
        manifest = manifest();
        manifest.setVersions(Arrays.asList(versionDetail(VERSION_ONE, false),
            versionDetail(VERSION_ONE, true)));
        when(manifestStorage.get(NAMESPACE_ID, MCP_ID)).thenReturn(manifest);
        assertIntegrityFailure(() -> readService.getMcpServer(NAMESPACE_ID, MCP_NAME, null, null));
    }
    
    @Test
    void testRejectsInvalidResourceRowAndMissingManifest() throws Exception {
        resource.setType("other");
        assertIntegrityFailure(() -> readService.getMcpServer(NAMESPACE_ID, MCP_NAME, null, null));
        
        resource = resource();
        when(resourceLocator.locate(anyString(), any(), any())).thenReturn(resource);
        when(manifestStorage.get(NAMESPACE_ID, MCP_ID)).thenReturn(null);
        assertIntegrityFailure(() -> readService.getMcpServer(NAMESPACE_ID, MCP_NAME, null, null));
    }
    
    @Test
    void testRejectsMissingVersionInfoAndInvalidStatus() throws Exception {
        resource.setVersionInfo("null");
        assertIntegrityFailure(() -> readService.getMcpServer(NAMESPACE_ID, MCP_NAME, null, null));
        
        resource = resource();
        resource.setStatus("UNKNOWN");
        when(resourceLocator.locate(anyString(), any(), any())).thenReturn(resource);
        assertIntegrityFailure(() -> readService.getMcpServer(NAMESPACE_ID, MCP_NAME, null, null));
    }
    
    @Test
    void testRejectsNullVersionPageAndDuplicateRows() throws Exception {
        when(resourceManager.listVersions(NAMESPACE_ID, MCP_NAME,
            AiResourceConstants.RESOURCE_TYPE_MCP, AiResourceConstants.VERSION_STATUS_ONLINE,
            1, 100)).thenReturn(null);
        assertIntegrityFailure(() -> readService.getMcpServer(NAMESPACE_ID, MCP_NAME, null, null));
        
        when(resourceManager.listVersions(NAMESPACE_ID, MCP_NAME,
            AiResourceConstants.RESOURCE_TYPE_MCP, AiResourceConstants.VERSION_STATUS_ONLINE,
            1, 100)).thenReturn(page(2, 1, versionOne, versionOne));
        assertIntegrityFailure(() -> readService.getMcpServer(NAMESPACE_ID, MCP_NAME, null, null));
    }
    
    @Test
    void testRejectsEmptyManifestVersionsAndInvalidVersionRow() throws Exception {
        manifest.setVersions(Collections.emptyList());
        assertIntegrityFailure(() -> readService.getMcpServer(NAMESPACE_ID, MCP_NAME, null, null));
        
        manifest = manifest();
        when(manifestStorage.get(NAMESPACE_ID, MCP_ID)).thenReturn(manifest);
        AiResourceVersion invalidVersion = version(VERSION_ONE, false, false);
        invalidVersion.setName("other");
        when(resourceManager.listVersions(NAMESPACE_ID, MCP_NAME,
            AiResourceConstants.RESOURCE_TYPE_MCP, AiResourceConstants.VERSION_STATUS_ONLINE,
            1, 100)).thenReturn(page(2, 1, invalidVersion, versionTwo));
        assertIntegrityFailure(() -> readService.getMcpServer(NAMESPACE_ID, MCP_NAME, null, null));
    }
    
    @Test
    void testVersionPagingFallsBackToTotalCount() throws Exception {
        when(resourceManager.listVersions(NAMESPACE_ID, MCP_NAME,
            AiResourceConstants.RESOURCE_TYPE_MCP, AiResourceConstants.VERSION_STATUS_ONLINE,
            1, 100)).thenReturn(page(101, 0, versionOne));
        when(resourceManager.listVersions(NAMESPACE_ID, MCP_NAME,
            AiResourceConstants.RESOURCE_TYPE_MCP, AiResourceConstants.VERSION_STATUS_ONLINE,
            2, 100)).thenReturn(page(101, 0, versionTwo));
        
        McpServerDetailInfo result = readService.getMcpServer(NAMESPACE_ID, MCP_NAME, null, null);
        
        assertEquals(VERSION_TWO, result.getVersion());
        verify(resourceManager).listVersions(NAMESPACE_ID, MCP_NAME,
            AiResourceConstants.RESOURCE_TYPE_MCP, AiResourceConstants.VERSION_STATUS_ONLINE,
            2, 100);
    }
    
    @Test
    void testListRejectsMissingLatestVersionRow() throws Exception {
        QueryCondition condition = new QueryCondition();
        when(resourceManager.buildQueryCondition(NAMESPACE_ID,
            AiResourceConstants.RESOURCE_TYPE_MCP, MCP_NAME, null,
            VisibilityConstants.ACTION_READ)).thenReturn(condition);
        when(resourceManager.listMeta(condition, 1, 10)).thenReturn(page(1, 1, resource));
        when(resourceManager.findVersion(NAMESPACE_ID, MCP_NAME,
            AiResourceConstants.RESOURCE_TYPE_MCP, VERSION_TWO)).thenReturn(null);
        
        NacosApiException error = assertThrows(NacosApiException.class,
            () -> readService.listMcpServers(NAMESPACE_ID, MCP_NAME,
                Constants.MCP_LIST_SEARCH_ACCURATE, 1, 10));
        
        assertEquals(NacosException.NOT_FOUND, error.getErrCode());
    }
    
    @Test
    void testRejectsMissingVersionDetailAndInvalidServerContent() throws Exception {
        McpServerStorageInfo missingDetail = server(VERSION_ONE,
            AiConstants.Mcp.MCP_PROTOCOL_STDIO);
        missingDetail.setVersionDetail(null);
        when(versionStorageService.load(any())).thenReturn(new McpVersionStorageContents(
            bytes(missingDetail), null, null));
        assertIntegrityFailure(() -> readService.getMcpServer(NAMESPACE_ID, MCP_NAME, null,
            VERSION_ONE));
        
        when(versionStorageService.load(any())).thenReturn(new McpVersionStorageContents(
            "null".getBytes(StandardCharsets.UTF_8), null, null));
        assertIntegrityFailure(() -> readService.getMcpServer(NAMESPACE_ID, MCP_NAME, null,
            VERSION_ONE));
        
        when(versionStorageService.load(any())).thenReturn(new McpVersionStorageContents(
            "invalid".getBytes(StandardCharsets.UTF_8), null, null));
        assertIntegrityFailure(() -> readService.getMcpServer(NAMESPACE_ID, MCP_NAME, null,
            VERSION_ONE));
    }
    
    private AiResource resource() {
        McpResourceExt ext = new McpResourceExt();
        ext.setSchemaVersion(McpResourceExt.SCHEMA_VERSION);
        ext.setMcpId(MCP_ID);
        ResourceVersionInfo versionInfo = new ResourceVersionInfo();
        versionInfo.setOnlineCnt(2);
        versionInfo.setLabels(new HashMap<>());
        versionInfo.getLabels().put(AiResourceConstants.LABEL_LATEST, VERSION_TWO);
        AiResource result = new AiResource();
        result.setNamespaceId(NAMESPACE_ID);
        result.setName(MCP_NAME);
        result.setType(AiResourceConstants.RESOURCE_TYPE_MCP);
        result.setDesc("description");
        result.setStatus(AiResourceConstants.META_STATUS_ENABLE);
        result.setExt(McpResourceExtSerializer.serialize(ext));
        result.setVersionInfo(JacksonUtils.toJson(versionInfo));
        return result;
    }
    
    private McpServerVersionInfo manifest() {
        McpServerVersionInfo result = new McpServerVersionInfo();
        result.setId(MCP_ID);
        result.setName(MCP_NAME);
        result.setDescription("description");
        result.setProtocol(AiConstants.Mcp.MCP_PROTOCOL_STDIO);
        result.setEnabled(true);
        result.setLatestPublishedVersion(VERSION_TWO);
        result.setVersions(Arrays.asList(versionDetail(VERSION_ONE, false),
            versionDetail(VERSION_TWO, true)));
        return result;
    }
    
    private ServerVersionDetail versionDetail(String version, boolean latest) {
        ServerVersionDetail result = new ServerVersionDetail();
        result.setVersion(version);
        result.setRelease_date("2026-01-01T00:00:00Z");
        result.setIs_latest(latest);
        return result;
    }
    
    private AiResourceVersion version(String version, boolean tools, boolean resources) {
        AiResourceVersion result = new AiResourceVersion();
        result.setNamespaceId(NAMESPACE_ID);
        result.setName(MCP_NAME);
        result.setType(AiResourceConstants.RESOURCE_TYPE_MCP);
        result.setVersion(version);
        result.setStatus(AiResourceConstants.VERSION_STATUS_ONLINE);
        result.setStorage(McpVersionStorageDescriptorSerializer.serialize(
            McpVersionStorageKeyComposer.compose(NAMESPACE_ID, MCP_ID, version, tools,
                resources)));
        return result;
    }
    
    private McpVersionStorageContents contents(String version, boolean tools, boolean resources,
        String protocol) {
        McpServerStorageInfo server = server(version, protocol);
        if (tools) {
            server.setToolsDescriptionRef("tool-ref");
        }
        if (resources) {
            server.setResourceDescriptionRef("resource-ref");
        }
        return new McpVersionStorageContents(bytes(server),
            tools ? bytes(new McpToolSpecification()) : null,
            resources ? bytes(new McpResourceSpecification()) : null);
    }
    
    private McpServerStorageInfo server(String version, String protocol) {
        McpServerStorageInfo result = new McpServerStorageInfo();
        result.setNamespaceId(NAMESPACE_ID);
        result.setId(MCP_ID);
        result.setName(MCP_NAME);
        result.setDescription("description");
        result.setProtocol(protocol);
        result.setVersion(version);
        result.setVersionDetail(versionDetail(version, VERSION_TWO.equals(version)));
        if (!AiConstants.Mcp.MCP_PROTOCOL_STDIO.equals(protocol)) {
            result.setRemoteServerConfig(new McpServerRemoteServiceConfig());
        }
        return result;
    }
    
    private byte[] bytes(Object value) {
        return JacksonUtils.toJson(value).getBytes(StandardCharsets.UTF_8);
    }
    
    private String versionFromDescriptor(McpVersionStorageDescriptor descriptor) {
        String key = descriptor.getServerKey();
        return key.contains('-' + VERSION_ONE + '-') ? VERSION_ONE : VERSION_TWO;
    }
    
    @SafeVarargs
    private final <T> Page<T> page(int totalCount, int pagesAvailable, T... items) {
        Page<T> result = new Page<>();
        result.setTotalCount(totalCount);
        result.setPagesAvailable(pagesAvailable);
        result.setPageItems(Arrays.asList(items));
        return result;
    }
    
    private void assertIntegrityFailure(ThrowingRead call) {
        NacosApiException error = assertThrows(NacosApiException.class, call::read);
        assertEquals(NacosException.CONFLICT, error.getErrCode());
        assertEquals(com.alibaba.nacos.api.model.v2.ErrorCode.RESOURCE_CONFLICT.getCode(),
            error.getDetailErrCode());
    }
    
    private interface ThrowingRead {
        
        void read() throws NacosException;
    }
}
