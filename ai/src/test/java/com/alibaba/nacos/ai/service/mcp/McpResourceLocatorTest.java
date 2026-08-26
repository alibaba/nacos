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
import com.alibaba.nacos.ai.model.AiResource;
import com.alibaba.nacos.ai.model.mcp.McpResourceExt;
import com.alibaba.nacos.ai.service.mcp.storage.McpResourceExtSerializer;
import com.alibaba.nacos.ai.service.repository.AiResourcePersistService;
import com.alibaba.nacos.ai.service.repository.QueryCondition;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class McpResourceLocatorTest {
    
    private static final String MCP_ID = "4d7939c0-72ea-4ef4-b232-418d1e16b45c";
    
    private static final String OTHER_MCP_ID = "11111111-1111-1111-1111-111111111111";
    
    @Mock
    private AiResourcePersistService resourcePersistService;
    
    private McpResourceLocator locator;
    
    @BeforeEach
    void setUp() {
        locator = new McpResourceLocator(resourcePersistService);
    }
    
    @Test
    void testLocateByNameUsesExactMcpResourceQuery() throws Exception {
        AiResource expected = resource("public", "demo", MCP_ID);
        when(resourcePersistService.list(any(QueryCondition.class), eq(1), eq(2)))
            .thenReturn(page(1, 1, expected));
        
        AiResource result = locator.locate("public", "demo", null);
        
        assertEquals(expected, result);
        ArgumentCaptor<QueryCondition> conditionCaptor =
            ArgumentCaptor.forClass(QueryCondition.class);
        verify(resourcePersistService).list(conditionCaptor.capture(), eq(1), eq(2));
        assertEquals("public", conditionCaptor.getValue().getNamespaceId());
        assertEquals(AiResourceConstants.RESOURCE_TYPE_MCP,
            conditionCaptor.getValue().getType());
        assertEquals("demo", conditionCaptor.getValue().getOrGroup().get("name"));
    }
    
    @Test
    void testLocateByNameAndIdCrossChecksAlias() throws Exception {
        when(resourcePersistService.list(any(QueryCondition.class), eq(1), eq(2)))
            .thenReturn(page(1, 1, resource("public", "demo", MCP_ID)));
        assertEquals("demo", locator.locate("public", "demo", MCP_ID).getName());
        NacosApiException conflict = assertThrows(NacosApiException.class,
            () -> locator.locate("public", "demo", OTHER_MCP_ID));
        assertEquals(NacosException.INVALID_PARAM, conflict.getErrCode());
        assertEquals(ErrorCode.PARAMETER_VALIDATE_ERROR.getCode(), conflict.getDetailErrCode());
    }
    
    @Test
    void testLocateByIdPagesUntilUniqueMatch() throws Exception {
        Page<AiResource> first = page(2, 2, resource("public", "other", OTHER_MCP_ID));
        Page<AiResource> second = page(2, 2, resource("public", "demo", MCP_ID));
        when(resourcePersistService.list(any(QueryCondition.class), anyInt(), eq(100)))
            .thenReturn(first, second);
        
        AiResource result = locator.locate("public", null, MCP_ID);
        
        assertEquals("demo", result.getName());
        verify(resourcePersistService, times(2))
            .list(any(QueryCondition.class), anyInt(), eq(100));
    }
    
    @Test
    void testLocateByIdRejectsDuplicateAliasAcrossPages() {
        Page<AiResource> first = page(2, 2, resource("public", "one", MCP_ID));
        Page<AiResource> second = page(2, 2, resource("public", "two", MCP_ID));
        when(resourcePersistService.list(any(QueryCondition.class), anyInt(), eq(100)))
            .thenReturn(first, second);
        NacosApiException conflict = assertThrows(NacosApiException.class,
            () -> locator.locate("public", null, MCP_ID));
        assertEquals(NacosException.CONFLICT, conflict.getErrCode());
        assertEquals(ErrorCode.RESOURCE_CONFLICT.getCode(), conflict.getDetailErrCode());
    }
    
    @Test
    void testLocateByNameRejectsMultipleSourceRows() {
        List<AiResource> duplicates = Arrays.asList(resource("public", "demo", MCP_ID),
            resource("public", "demo", OTHER_MCP_ID));
        when(resourcePersistService.list(any(QueryCondition.class), eq(1), eq(2)))
            .thenReturn(page(2, 1, duplicates));
        NacosApiException conflict = assertThrows(NacosApiException.class,
            () -> locator.locate("public", "demo", null));
        assertEquals(NacosException.CONFLICT, conflict.getErrCode());
    }
    
    @Test
    void testLocateByNameHandlesNullPageAndInconsistentDuplicateRows() {
        when(resourcePersistService.list(any(QueryCondition.class), eq(1), eq(2)))
            .thenReturn(null);
        NacosApiException missing = assertThrows(NacosApiException.class,
            () -> locator.locate("public", "demo", null));
        assertEquals(NacosException.NOT_FOUND, missing.getErrCode());
        
        List<AiResource> duplicates = Arrays.asList(resource("public", "demo", MCP_ID),
            resource("public", "demo", OTHER_MCP_ID));
        when(resourcePersistService.list(any(QueryCondition.class), eq(1), eq(2)))
            .thenReturn(page(1, 1, duplicates));
        NacosApiException conflict = assertThrows(NacosApiException.class,
            () -> locator.locate("public", "demo", null));
        assertEquals(NacosException.CONFLICT, conflict.getErrCode());
    }
    
    @Test
    void testLocateByIdRejectsInvalidPageResponses() {
        when(resourcePersistService.list(any(QueryCondition.class), anyInt(), eq(100)))
            .thenReturn(null);
        assertIntegrityFailure(() -> locator.locate("public", null, MCP_ID));
        
        when(resourcePersistService.list(any(QueryCondition.class), anyInt(), eq(100)))
            .thenReturn(page(0, 0, (List<AiResource>) null));
        assertIntegrityFailure(() -> locator.locate("public", null, MCP_ID));
    }
    
    @Test
    void testLocateByIdCalculatesPagesFromTotalCount() throws Exception {
        Page<AiResource> first = page(101, 0,
            resource("public", "other", OTHER_MCP_ID));
        Page<AiResource> second = page(101, 0, resource("public", "demo", MCP_ID));
        when(resourcePersistService.list(any(QueryCondition.class), anyInt(), eq(100)))
            .thenReturn(first, second);
        
        assertEquals("demo", locator.locate("public", null, MCP_ID).getName());
        verify(resourcePersistService, times(2))
            .list(any(QueryCondition.class), anyInt(), eq(100));
    }
    
    @Test
    void testLocateByIdReturnsControlledNotFound() {
        when(resourcePersistService.list(any(QueryCondition.class), anyInt(), eq(100)))
            .thenReturn(page(0, 0, Collections.emptyList()));
        NacosApiException missing = assertThrows(NacosApiException.class,
            () -> locator.locate("public", null, MCP_ID));
        assertEquals(NacosException.NOT_FOUND, missing.getErrCode());
        assertEquals(ErrorCode.MCP_SERVER_NOT_FOUND.getCode(), missing.getDetailErrCode());
    }
    
    @Test
    void testLocateRejectsInconsistentRows() {
        AiResource wrongNamespace = resource("other", "demo", MCP_ID);
        AiResource wrongType = resource("public", "demo", MCP_ID);
        wrongType.setType("skill");
        AiResource wrongName = resource("public", "other", MCP_ID);
        for (AiResource invalid : Arrays.asList(null, wrongNamespace, wrongType, wrongName)) {
            when(resourcePersistService.list(any(QueryCondition.class), eq(1), eq(2)))
                .thenReturn(page(1, 1, invalid));
            assertIntegrityFailure(() -> locator.locate("public", "demo", null));
        }
    }
    
    @Test
    void testLocateRejectsMalformedStoredExtension() {
        AiResource invalid = resource("public", "demo", MCP_ID);
        invalid.setExt("{\"schemaVersion\":1}");
        when(resourcePersistService.list(any(QueryCondition.class), eq(1), eq(2)))
            .thenReturn(page(1, 1, invalid));
        NacosApiException conflict = assertThrows(NacosApiException.class,
            () -> locator.locate("public", "demo", null));
        assertEquals(NacosException.CONFLICT, conflict.getErrCode());
        assertEquals(ErrorCode.RESOURCE_CONFLICT.getCode(), conflict.getDetailErrCode());
    }
    
    @Test
    void testLocateReturnsControlledNotFound() {
        when(resourcePersistService.list(any(QueryCondition.class), eq(1), eq(2)))
            .thenReturn(page(0, 0, Collections.emptyList()));
        NacosApiException missing = assertThrows(NacosApiException.class,
            () -> locator.locate("public", "missing", null));
        assertEquals(NacosException.NOT_FOUND, missing.getErrCode());
        assertEquals(ErrorCode.MCP_SERVER_NOT_FOUND.getCode(), missing.getDetailErrCode());
    }
    
    @Test
    void testLocateValidatesInputBeforeQuery() {
        assertThrows(NacosApiException.class, () -> locator.locate("public", null, null));
        assertThrows(NacosApiException.class, () -> locator.locate("public", null, "bad-id"));
        assertThrows(NacosApiException.class,
            () -> locator.locate("invalid namespace", "demo", null));
        verifyNoInteractions(resourcePersistService);
    }
    
    @Test
    void testBlankNamespaceUsesMcpDefault() throws Exception {
        when(resourcePersistService.list(any(QueryCondition.class), eq(1), eq(2)))
            .thenReturn(page(1, 1, resource("public", "demo", MCP_ID)));
        locator.locate(" ", "demo", null);
        ArgumentCaptor<QueryCondition> captor = ArgumentCaptor.forClass(QueryCondition.class);
        verify(resourcePersistService).list(captor.capture(), eq(1), eq(2));
        assertEquals("public", captor.getValue().getNamespaceId());
        verify(resourcePersistService, never()).find(any(), any(), any());
    }
    
    private AiResource resource(String namespaceId, String name, String mcpId) {
        AiResource result = new AiResource();
        result.setNamespaceId(namespaceId);
        result.setName(name);
        result.setType(AiResourceConstants.RESOURCE_TYPE_MCP);
        McpResourceExt ext = new McpResourceExt();
        ext.setSchemaVersion(McpResourceExt.SCHEMA_VERSION);
        ext.setMcpId(mcpId);
        result.setExt(McpResourceExtSerializer.serialize(ext));
        return result;
    }
    
    private Page<AiResource> page(int totalCount, int pagesAvailable, AiResource... resources) {
        return page(totalCount, pagesAvailable, Arrays.asList(resources));
    }
    
    private Page<AiResource> page(int totalCount, int pagesAvailable,
        List<AiResource> resources) {
        Page<AiResource> result = new Page<>();
        result.setTotalCount(totalCount);
        result.setPagesAvailable(pagesAvailable);
        result.setPageItems(resources);
        return result;
    }
    
    private void assertIntegrityFailure(ThrowingLocatorCall call) {
        NacosApiException conflict = assertThrows(NacosApiException.class, call::locate);
        assertEquals(NacosException.CONFLICT, conflict.getErrCode());
        assertEquals(ErrorCode.RESOURCE_CONFLICT.getCode(), conflict.getDetailErrCode());
    }
    
    private interface ThrowingLocatorCall {
        
        void locate() throws NacosApiException;
    }
}
