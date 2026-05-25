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

package com.alibaba.nacos.ai.service;

import com.alibaba.nacos.ai.enums.ExternalDataTypeEnum;
import com.alibaba.nacos.ai.importer.config.AiResourceImportProperties;
import com.alibaba.nacos.ai.importer.manager.AiResourceImportManager;
import com.alibaba.nacos.api.ai.model.importer.AiResourceImportCandidateItem;
import com.alibaba.nacos.api.ai.model.importer.AiResourceImportExecuteResponse;
import com.alibaba.nacos.api.ai.model.importer.AiResourceImportResultItem;
import com.alibaba.nacos.api.ai.model.importer.AiResourceImportResultStatus;
import com.alibaba.nacos.api.ai.model.importer.AiResourceImportSearchResponse;
import com.alibaba.nacos.api.ai.model.importer.AiResourceImportValidateRequest;
import com.alibaba.nacos.api.ai.model.importer.AiResourceImportValidateResponse;
import com.alibaba.nacos.api.ai.model.importer.AiResourceImportValidationItem;
import com.alibaba.nacos.api.ai.model.importer.AiResourceImportValidationStatus;
import com.alibaba.nacos.api.ai.model.mcp.McpServerImportRequest;
import com.alibaba.nacos.api.ai.model.mcp.McpServerImportResponse;
import com.alibaba.nacos.api.ai.model.mcp.McpServerImportValidationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link McpLegacyImportAdapter}.
 *
 * @author xiweng.yy
 */
@ExtendWith(MockitoExtension.class)
class McpLegacyImportAdapterTest {
    
    @Mock
    private McpServerImportService mcpServerImportService;
    
    @Mock
    private AiResourceImportManager importManager;
    
    private McpLegacyImportAdapter adapter;
    
    @BeforeEach
    void setUp() {
        adapter = new McpLegacyImportAdapter(mcpServerImportService, importManager);
        AiResourceImportProperties properties = new AiResourceImportProperties();
        properties.setLegacyMcpImportApiEnabled(true);
        ReflectionTestUtils.setField(adapter, "propertiesSupplier",
            (Supplier<AiResourceImportProperties>) () -> properties);
    }
    
    @Test
    void testLegacyApiDisabledByDefault() throws Exception {
        AiResourceImportProperties properties = new AiResourceImportProperties();
        ReflectionTestUtils.setField(adapter, "propertiesSupplier",
            (Supplier<AiResourceImportProperties>) () -> properties);
        McpServerImportRequest request = request("source-1");
        
        McpServerImportValidationResult result = adapter.validateImport("public", request);
        
        assertFalse(result.isValid());
        assertTrue(result.getErrors().get(0).contains("Legacy MCP import API is disabled"));
        verifyNoInteractions(mcpServerImportService, importManager);
    }
    
    @Test
    void testRejectsLegacyUrlWhenUserUrlDisabled() throws Exception {
        McpServerImportRequest request = request("https://registry.example.com/v0/servers");
        
        McpServerImportValidationResult result = adapter.validateImport("public", request);
        
        assertFalse(result.isValid());
        assertTrue(result.getErrors().get(0).contains("Legacy URL import is disabled"));
        verifyNoInteractions(mcpServerImportService, importManager);
    }
    
    @Test
    void testValidateRoutesSourceIdToUnifiedManager() throws Exception {
        when(importManager.search(any())).thenReturn(searchResponse());
        when(importManager.validate(any())).thenReturn(validateResponse());
        McpServerImportRequest request = request("source-1");
        request.setSearch("redis");
        request.setLimit(10);
        
        McpServerImportValidationResult result = adapter.validateImport("public", request);
        
        assertTrue(result.isValid());
        assertEquals(1, result.getValidCount());
        ArgumentCaptor<AiResourceImportValidateRequest> captor =
            ArgumentCaptor.forClass(AiResourceImportValidateRequest.class);
        verify(importManager).validate(captor.capture());
        assertEquals("source-1", captor.getValue().getSourceId());
        assertEquals("server-1", captor.getValue().getSelectedItems().get(0).getExternalId());
    }
    
    @Test
    void testExecuteRoutesSourceIdToUnifiedManager() throws Exception {
        when(importManager.search(any())).thenReturn(searchResponse());
        when(importManager.execute(any())).thenReturn(executeResponse());
        McpServerImportRequest request = request("source-1");
        
        McpServerImportResponse result = adapter.executeImport("public", request);
        
        assertTrue(result.isSuccess());
        assertEquals(1, result.getSuccessCount());
        assertEquals("success", result.getResults().get(0).getStatus());
        assertEquals("server-1", result.getResults().get(0).getServerId());
    }
    
    @Test
    void testUserUrlFallbackWhenExplicitlyAllowed() throws Exception {
        AiResourceImportProperties properties = new AiResourceImportProperties();
        properties.setLegacyMcpImportApiEnabled(true);
        properties.setAllowUserUrl(true);
        ReflectionTestUtils.setField(adapter, "propertiesSupplier",
            (Supplier<AiResourceImportProperties>) () -> properties);
        McpServerImportRequest request = request("https://registry.example.com/v0/servers");
        McpServerImportResponse expected = new McpServerImportResponse();
        expected.setSuccess(true);
        when(mcpServerImportService.executeImport(eq("public"), eq(request))).thenReturn(expected);
        
        McpServerImportResponse result = adapter.executeImport("public", request);
        
        assertTrue(result.isSuccess());
        verify(mcpServerImportService).executeImport("public", request);
    }
    
    private McpServerImportRequest request(String data) {
        McpServerImportRequest request = new McpServerImportRequest();
        request.setImportType(ExternalDataTypeEnum.URL.getName());
        request.setData(data);
        return request;
    }
    
    private AiResourceImportSearchResponse searchResponse() {
        AiResourceImportCandidateItem item = new AiResourceImportCandidateItem();
        item.setExternalId("server-1");
        item.setName("test-server");
        item.setVersion("1.0.0");
        AiResourceImportSearchResponse response = new AiResourceImportSearchResponse();
        response.setItems(Collections.singletonList(item));
        return response;
    }
    
    private AiResourceImportValidateResponse validateResponse() {
        AiResourceImportValidationItem item = new AiResourceImportValidationItem();
        item.setExternalId("server-1");
        item.setName("test-server");
        item.setVersion("1.0.0");
        item.setStatus(AiResourceImportValidationStatus.VALID);
        AiResourceImportValidateResponse response = new AiResourceImportValidateResponse();
        response.setItems(Collections.singletonList(item));
        return response;
    }
    
    private AiResourceImportExecuteResponse executeResponse() {
        AiResourceImportResultItem item = new AiResourceImportResultItem();
        item.setExternalId("server-1");
        item.setResourceName("test-server");
        item.setVersion("1.0.0");
        item.setStatus(AiResourceImportResultStatus.SUCCESS);
        AiResourceImportExecuteResponse response = new AiResourceImportExecuteResponse();
        response.setSuccess(true);
        response.setTotalCount(1);
        response.setSuccessCount(1);
        response.setResults(Collections.singletonList(item));
        return response;
    }
}
