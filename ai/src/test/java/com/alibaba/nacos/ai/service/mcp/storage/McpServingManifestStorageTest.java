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

package com.alibaba.nacos.ai.service.mcp.storage;

import com.alibaba.nacos.ai.constant.Constants;
import com.alibaba.nacos.ai.service.SyncEffectService;
import com.alibaba.nacos.ai.service.mcp.storage.McpServingManifestStorage.ReconciliationPage;
import com.alibaba.nacos.ai.utils.McpConfigUtils;
import com.alibaba.nacos.api.ai.model.mcp.McpServerVersionInfo;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.runtime.NacosSerializationException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.config.server.model.ConfigInfo;
import com.alibaba.nacos.config.server.model.form.ConfigFormV3;
import com.alibaba.nacos.config.server.service.ConfigDetailService;
import com.alibaba.nacos.config.server.service.ConfigOperationService;
import com.alibaba.nacos.config.server.service.query.ConfigQueryChainService;
import com.alibaba.nacos.config.server.service.query.model.ConfigQueryChainRequest;
import com.alibaba.nacos.config.server.service.query.model.ConfigQueryChainResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class McpServingManifestStorageTest {
    
    private static final String MCP_ID = "4d7939c0-72ea-4ef4-b232-418d1e16b45c";
    
    @Mock
    private ConfigQueryChainService configQueryChainService;
    
    @Mock
    private ConfigOperationService configOperationService;
    
    @Mock
    private ConfigDetailService configDetailService;
    
    @Mock
    private SyncEffectService syncEffectService;
    
    private McpServingManifestStorage storage;
    
    @BeforeEach
    void setUp() {
        storage = new McpServingManifestStorage(configQueryChainService,
            configOperationService, configDetailService, syncEffectService);
    }
    
    @Test
    void testGetUsesExistingCoordinateAndDecodesManifest() throws Exception {
        ConfigQueryChainResponse response = foundResponse(JacksonUtils.toJson(manifest()));
        when(configQueryChainService.handle(any(ConfigQueryChainRequest.class)))
            .thenReturn(response);
        
        McpServerVersionInfo result = storage.get("public", MCP_ID);
        
        assertEquals(MCP_ID, result.getId());
        assertEquals("demo", result.getName());
        ArgumentCaptor<ConfigQueryChainRequest> captor =
            ArgumentCaptor.forClass(ConfigQueryChainRequest.class);
        verify(configQueryChainService).handle(captor.capture());
        assertEquals(McpConfigUtils.formatServerVersionInfoDataId(MCP_ID),
            captor.getValue().getDataId());
        assertEquals(Constants.MCP_SERVER_VERSIONS_GROUP, captor.getValue().getGroup());
        assertEquals("public", captor.getValue().getTenant());
    }
    
    @Test
    void testGetReturnsNullWhenManifestDoesNotExist() throws Exception {
        ConfigQueryChainResponse response = new ConfigQueryChainResponse();
        response.setStatus(ConfigQueryChainResponse.ConfigQueryStatus.CONFIG_NOT_FOUND);
        when(configQueryChainService.handle(any(ConfigQueryChainRequest.class)))
            .thenReturn(response);
        assertNull(storage.get("public", MCP_ID));
    }
    
    @Test
    void testGetRejectsMissingOrConflictingQueryResponse() {
        when(configQueryChainService.handle(any(ConfigQueryChainRequest.class)))
            .thenReturn(null);
        assertEquals("MCP serving Manifest query returned no response",
            assertThrows(NacosException.class, () -> storage.get("public", MCP_ID)).getErrMsg());
        
        ConfigQueryChainResponse conflict = new ConfigQueryChainResponse();
        conflict.setStatus(ConfigQueryChainResponse.ConfigQueryStatus.CONFIG_QUERY_CONFLICT);
        conflict.setMessage("conflict");
        when(configQueryChainService.handle(any(ConfigQueryChainRequest.class)))
            .thenReturn(conflict);
        assertEquals("MCP serving Manifest cannot be read: conflict",
            assertThrows(NacosException.class, () -> storage.get("public", MCP_ID)).getErrMsg());
    }
    
    @Test
    void testGetRejectsMalformedOrConflictingManifest() {
        when(configQueryChainService.handle(any(ConfigQueryChainRequest.class)))
            .thenReturn(foundResponse("not-json"));
        assertEquals("MCP serving Manifest cannot be decoded",
            assertThrows(NacosException.class, () -> storage.get("public", MCP_ID)).getErrMsg());
        McpServerVersionInfo conflicting = manifest();
        conflicting.setId("11111111-1111-1111-1111-111111111111");
        when(configQueryChainService.handle(any(ConfigQueryChainRequest.class)))
            .thenReturn(foundResponse(JacksonUtils.toJson(conflicting)));
        assertEquals("MCP serving Manifest id does not match its Config coordinate",
            assertThrows(NacosException.class, () -> storage.get("public", MCP_ID)).getErrMsg());
        when(configQueryChainService.handle(any(ConfigQueryChainRequest.class)))
            .thenReturn(foundResponse("{}"));
        assertEquals("MCP serving Manifest has invalid identity fields",
            assertThrows(NacosException.class, () -> storage.get("public", MCP_ID)).getErrMsg());
    }
    
    @Test
    void testListPagesAndValidatesManifestCoordinates() throws Exception {
        Page<ConfigInfo> page = new Page<>();
        page.setPageNumber(2);
        page.setPagesAvailable(3);
        page.setTotalCount(5);
        page.setPageItems(Collections.singletonList(configInfo(manifest())));
        when(configDetailService.findConfigInfoPage(Constants.MCP_LIST_SEARCH_BLUR, 2, 2,
            Constants.ALL_PATTERN, Constants.MCP_SERVER_VERSIONS_GROUP, "public",
            Collections.emptyMap())).thenReturn(page);
        
        ReconciliationPage result = storage.list("public", 2, 2);
        
        assertEquals(2, result.getPageNumber());
        assertEquals(3, result.getPagesAvailable());
        assertEquals(5, result.getTotalCount());
        assertTrue(result.getFailures().isEmpty());
        assertEquals("public", result.getPageItems().get(0).getNamespaceId());
        assertEquals(MCP_ID, result.getPageItems().get(0).getId());
    }
    
    @Test
    void testListRejectsInvalidPageAndStorageFailures() {
        assertThrows(IllegalArgumentException.class, () -> storage.list("public", 0, 1));
        assertThrows(IllegalArgumentException.class, () -> storage.list("public", 1, 0));
        assertThrows(IllegalArgumentException.class,
            () -> storage.list("invalid namespace", 1, 1));
        verifyNoInteractions(configDetailService);
        
        when(configDetailService.findConfigInfoPage(any(), anyInt(), anyInt(), any(),
            any(), any(), any())).thenThrow(new IllegalStateException("failed"));
        assertEquals("MCP serving Manifest page cannot be read",
            assertThrows(NacosException.class, () -> storage.list("public", 1, 1)).getErrMsg());
    }
    
    @Test
    void testListRejectsMissingPageItemsAndRecordsInvalidRows() throws Exception {
        when(configDetailService.findConfigInfoPage(any(), anyInt(), anyInt(), any(),
            any(), any(), any())).thenReturn(null);
        assertEquals("MCP serving Manifest query returned no page",
            assertThrows(NacosException.class, () -> storage.list("public", 1, 1)).getErrMsg());
        
        Page<ConfigInfo> missingItems = new Page<>();
        missingItems.setPageItems(null);
        when(configDetailService.findConfigInfoPage(any(), anyInt(), anyInt(), any(),
            any(), any(), any())).thenReturn(missingItems);
        assertEquals("MCP serving Manifest query returned no page",
            assertThrows(NacosException.class, () -> storage.list("public", 1, 1)).getErrMsg());
        
        Page<ConfigInfo> nullRow = configPage(null);
        when(configDetailService.findConfigInfoPage(any(), anyInt(), anyInt(), any(),
            any(), any(), any())).thenReturn(nullRow);
        ReconciliationPage result = storage.list("public", 1, 1);
        assertTrue(result.getPageItems().isEmpty());
        assertEquals(1, result.getFailures().size());
        assertTrue(result.getFailures().get(0).contains("<unknown>"));
    }
    
    @Test
    void testListContinuesAfterMalformedContentAndCoordinateMismatch() throws Exception {
        ConfigInfo malformed = new ConfigInfo();
        malformed.setDataId(McpConfigUtils.formatServerVersionInfoDataId(MCP_ID));
        malformed.setGroup(Constants.MCP_SERVER_VERSIONS_GROUP);
        malformed.setContent("not-json");
        ConfigInfo wrongDataId = configInfo(manifest());
        wrongDataId.setDataId("wrong");
        ConfigInfo wrongGroup = configInfo(manifest());
        wrongGroup.setGroup("wrong");
        McpServerVersionInfo validManifest = manifest();
        validManifest.setName("valid");
        ConfigInfo valid = configInfo(validManifest);
        Page<ConfigInfo> page = new Page<>();
        page.setPageItems(Arrays.asList(malformed, valid, wrongDataId, wrongGroup));
        page.setTotalCount(4);
        page.setPagesAvailable(1);
        when(configDetailService.findConfigInfoPage(any(), anyInt(), anyInt(), any(),
            any(), any(), any())).thenReturn(page);
        
        ReconciliationPage result = storage.list("public", 1, 10);
        
        assertEquals(1, result.getPageItems().size());
        assertEquals("valid", result.getPageItems().get(0).getName());
        assertEquals(3, result.getFailures().size());
        assertTrue(result.getFailures().stream().anyMatch(message -> message.contains("wrong")));
        assertTrue(result.getFailures().stream()
            .anyMatch(message -> message.contains("invalid content")));
        assertTrue(result.getFailures().stream()
            .anyMatch(message -> message.contains("identity does not match")));
    }
    
    @Test
    void testPublishPreservesCoordinateMetadataAndContent() throws Exception {
        McpServerVersionInfo manifest = manifest();
        
        storage.publish("public", manifest);
        
        ArgumentCaptor<ConfigFormV3> captor = ArgumentCaptor.forClass(ConfigFormV3.class);
        verify(configOperationService).publishConfig(captor.capture(), any(), isNull());
        ConfigFormV3 form = captor.getValue();
        assertEquals(McpConfigUtils.formatServerVersionInfoDataId(MCP_ID), form.getDataId());
        assertEquals(Constants.MCP_SERVER_VERSIONS_GROUP, form.getGroup());
        assertEquals(Constants.MCP_SERVER_VERSIONS_GROUP, form.getGroupName());
        assertEquals("public", form.getNamespaceId());
        assertEquals("demo", form.getAppName());
        assertEquals(McpConfigUtils.buildMcpServerVersionConfigTags("demo"),
            form.getConfigTags());
        McpServerVersionInfo stored = JacksonUtils.toObj(form.getContent(),
            McpServerVersionInfo.class);
        assertEquals(MCP_ID, stored.getId());
        assertEquals("demo", stored.getName());
        verify(syncEffectService).toSync(same(form), anyLong());
    }
    
    @Test
    void testPublishRejectsInvalidManifestBeforeConfigAccess() {
        McpServerVersionInfo manifest = manifest();
        manifest.setName(" ");
        assertThrows(IllegalArgumentException.class,
            () -> storage.publish("public", manifest));
        verifyNoInteractions(configOperationService, syncEffectService);
    }
    
    @Test
    void testPublishRejectsNullManifestBeforeConfigAccess() {
        assertThrows(IllegalArgumentException.class, () -> storage.publish("public", null));
        verifyNoInteractions(configOperationService, syncEffectService);
    }
    
    @Test
    void testPublishWorksWithoutSyncEffectService() throws Exception {
        McpServingManifestStorage storageWithoutSync = new McpServingManifestStorage(
            configQueryChainService, configOperationService, configDetailService, null);
        
        storageWithoutSync.publish("public", manifest());
        
        verify(configOperationService).publishConfig(any(ConfigFormV3.class), any(), isNull());
        verifyNoInteractions(syncEffectService);
    }
    
    @Test
    void testPublishWrapsSerializationFailure() {
        McpServerVersionInfo manifest = manifest();
        try (MockedStatic<JacksonUtils> jacksonMock = Mockito.mockStatic(JacksonUtils.class)) {
            jacksonMock.when(() -> JacksonUtils.toJson(manifest))
                .thenThrow(new NacosSerializationException());
            assertEquals("MCP serving Manifest cannot be encoded",
                assertThrows(NacosException.class,
                    () -> storage.publish("public", manifest)).getErrMsg());
        }
        verifyNoInteractions(configOperationService, syncEffectService);
    }
    
    @Test
    void testDeleteUsesExistingCoordinate() throws Exception {
        storage.delete("public", MCP_ID);
        verify(configOperationService).deleteConfig(
            McpConfigUtils.formatServerVersionInfoDataId(MCP_ID),
            Constants.MCP_SERVER_VERSIONS_GROUP, "public", null, null, "nacos", null);
        verify(syncEffectService, never()).toSync(any(), anyLong());
    }
    
    @Test
    void testRejectInvalidCoordinateBeforeConfigAccess() {
        assertThrows(IllegalArgumentException.class,
            () -> storage.get("invalid namespace", MCP_ID));
        assertThrows(IllegalArgumentException.class, () -> storage.delete("public", "bad-id"));
        verifyNoInteractions(configQueryChainService, configOperationService);
    }
    
    private McpServerVersionInfo manifest() {
        McpServerVersionInfo result = new McpServerVersionInfo();
        result.setId(MCP_ID);
        result.setName("demo");
        return result;
    }
    
    private ConfigQueryChainResponse foundResponse(String content) {
        ConfigQueryChainResponse result = new ConfigQueryChainResponse();
        result.setStatus(ConfigQueryChainResponse.ConfigQueryStatus.CONFIG_FOUND_FORMAL);
        result.setContent(content);
        return result;
    }
    
    private ConfigInfo configInfo(McpServerVersionInfo manifest) {
        ConfigInfo result = new ConfigInfo();
        result.setDataId(McpConfigUtils.formatServerVersionInfoDataId(manifest.getId()));
        result.setGroup(Constants.MCP_SERVER_VERSIONS_GROUP);
        result.setContent(JacksonUtils.toJson(manifest));
        return result;
    }
    
    private Page<ConfigInfo> configPage(ConfigInfo configInfo) {
        Page<ConfigInfo> result = new Page<>();
        result.setPageItems(Collections.singletonList(configInfo));
        result.setTotalCount(1);
        result.setPagesAvailable(1);
        return result;
    }
    
}
