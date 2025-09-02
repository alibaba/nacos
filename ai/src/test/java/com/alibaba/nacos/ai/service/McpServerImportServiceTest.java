/*
 * Copyright 1999-2021 Alibaba Group Holding Ltd.
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

import com.alibaba.nacos.ai.constants.McpServerValidationConstants;
import com.alibaba.nacos.api.ai.model.mcp.McpServerDetailInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpServerImportRequest;
import com.alibaba.nacos.api.ai.model.mcp.McpServerImportResponse;
import com.alibaba.nacos.api.ai.model.mcp.McpServerImportValidationResult;
import com.alibaba.nacos.api.ai.model.mcp.McpServerValidationItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Unit tests for McpServerImportService.
 *
 * @author WangzJi
 */
@ExtendWith(MockitoExtension.class)
class McpServerImportServiceTest {

    @Mock
    private McpServerTransformService transformService;

    @Mock
    private McpServerValidationService validationService;

    @Mock
    private McpServerOperationService operationService;

    private McpServerImportService importService;

    @BeforeEach
    void setUp() {
        importService = new McpServerImportService(transformService, validationService, operationService);
    }

    @Test
    void testValidateImportSuccess() throws Exception {
        // Given
        McpServerImportRequest request = new McpServerImportRequest();
        request.setData("{\"servers\":[]}");
        request.setImportType("json");

        List<McpServerDetailInfo> servers = new ArrayList<>();
        when(transformService.transformToNacosFormat(anyString(), anyString())).thenReturn(servers);

        McpServerImportValidationResult validationResult = new McpServerImportValidationResult();
        validationResult.setValid(true);
        when(validationService.validateServers(anyString(), any())).thenReturn(validationResult);

        // When
        McpServerImportValidationResult result = importService.validateImport("test-namespace", request);

        // Then
        assertNotNull(result);
        assertTrue(result.isValid());
    }

    @Test
    void testValidateImportExceedsBatchSize() throws Exception {
        // Given
        McpServerImportRequest request = new McpServerImportRequest();
        request.setData("{\"servers\":[]}");
        request.setImportType("json");

        List<McpServerDetailInfo> servers = new ArrayList<>();
        // Create more than MAX_IMPORT_BATCH_SIZE (100) servers
        for (int i = 0; i < 101; i++) {
            servers.add(new McpServerDetailInfo());
        }
        when(transformService.transformToNacosFormat(anyString(), anyString())).thenReturn(servers);

        // When
        McpServerImportValidationResult result = importService.validateImport("test-namespace", request);

        // Then
        assertNotNull(result);
        assertFalse(result.isValid());
        assertEquals(101, result.getTotalCount());
        assertNotNull(result.getErrors());
        assertFalse(result.getErrors().isEmpty());
        assertTrue(result.getErrors().get(0).contains("Import batch size exceeds maximum limit"));
    }

    @Test
    void testValidateImportTransformationFailure() throws Exception {
        // Given
        McpServerImportRequest request = new McpServerImportRequest();
        request.setData("invalid-json");
        request.setImportType("json");

        when(transformService.transformToNacosFormat(anyString(), anyString()))
                .thenThrow(new RuntimeException("Invalid JSON format"));

        // When
        McpServerImportValidationResult result = importService.validateImport("test-namespace", request);

        // Then
        assertNotNull(result);
        assertFalse(result.isValid());
        assertNotNull(result.getErrors());
        assertFalse(result.getErrors().isEmpty());
        assertTrue(result.getErrors().get(0).contains("Import validation failed"));
    }

    @Test
    void testExecuteImportValidationFailure() throws Exception {
        // Given
        McpServerImportRequest request = new McpServerImportRequest();
        request.setData("invalid-data");
        request.setImportType("json");

        when(transformService.transformToNacosFormat(anyString(), anyString()))
                .thenThrow(new RuntimeException("Invalid data"));

        // When
        McpServerImportResponse response = importService.executeImport("test-namespace", request);

        // Then
        assertNotNull(response);
        assertFalse(response.isSuccess());
        assertNotNull(response.getErrorMessage());
        assertTrue(response.getErrorMessage().contains("Import validation failed"));
    }

    @Test
    void testExecuteImportSuccess() throws Exception {
        // Given
        McpServerImportRequest request = new McpServerImportRequest();
        request.setData("{\"servers\":[]}");
        request.setImportType("json");
        request.setSelectedServers(new String[]{"server1"});
        request.setOverrideExisting(false);

        // Mock transformation
        List<McpServerDetailInfo> servers = new ArrayList<>();
        McpServerDetailInfo server = new McpServerDetailInfo();
        server.setId("server1");
        server.setName("Test Server");
        servers.add(server);
        when(transformService.transformToNacosFormat(anyString(), anyString())).thenReturn(servers);

        // Mock validation
        McpServerImportValidationResult validationResult = new McpServerImportValidationResult();
        validationResult.setValid(true);
        McpServerValidationItem item = new McpServerValidationItem();
        item.setServerId("server1");
        item.setServerName("Test Server");
        item.setStatus(McpServerValidationConstants.STATUS_VALID);
        item.setExists(false);
        item.setServer(server);
        List<McpServerValidationItem> validationItems = new ArrayList<>();
        validationItems.add(item);
        validationResult.setServers(validationItems);
        when(validationService.validateServers(anyString(), any())).thenReturn(validationResult);

        // When
        McpServerImportResponse response = importService.executeImport("test-namespace", request);

        // Then
        assertNotNull(response);
        assertTrue(response.isSuccess());
        assertEquals(1, response.getTotalCount());
        assertEquals(1, response.getSuccessCount());
        assertEquals(0, response.getFailedCount());
        assertEquals(0, response.getSkippedCount());
    }
}