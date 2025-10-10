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

import com.alibaba.nacos.ai.constant.McpServerValidationConstants;
import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.api.ai.model.mcp.FrontEndpointConfig;
import com.alibaba.nacos.api.ai.model.mcp.McpServerDetailInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpServerImportRequest;
import com.alibaba.nacos.api.ai.model.mcp.McpServerImportResponse;
import com.alibaba.nacos.api.ai.model.mcp.McpServerImportValidationResult;
import com.alibaba.nacos.api.ai.model.mcp.McpServerRemoteServiceConfig;
import com.alibaba.nacos.api.ai.model.mcp.McpServerValidationItem;
import com.alibaba.nacos.api.exception.NacosException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
        when(transformService.transformToNacosFormat(anyString(), anyString(), any(), any(), any())).thenReturn(
                servers);
        
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
    void testValidateImportTransformationFailure() throws Exception {
        // Given
        McpServerImportRequest request = new McpServerImportRequest();
        request.setData("invalid-json");
        request.setImportType("json");
        
        when(transformService.transformToNacosFormat(anyString(), anyString(), any(), any(), any())).thenThrow(
                new RuntimeException("Invalid JSON format"));
        
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
        
        when(transformService.transformToNacosFormat(anyString(), anyString(), any(), any(), any())).thenThrow(
                new RuntimeException("Invalid data"));
        
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
        request.setSelectedServers(new String[] {"server1"});
        request.setOverrideExisting(false);
        
        // Mock transformation
        List<McpServerDetailInfo> servers = new ArrayList<>();
        McpServerDetailInfo server = new McpServerDetailInfo();
        server.setId("server1");
        server.setName("Test Server");
        servers.add(server);
        when(transformService.transformToNacosFormat(anyString(), anyString(), any(), any(), any())).thenReturn(
                servers);
        
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
    
    @Test
    void testExecuteImportWithSkipInvalidAndNoValidServers() throws Exception {
        // Given
        McpServerImportRequest request = new McpServerImportRequest();
        request.setData("data");
        request.setImportType("json");
        request.setSkipInvalid(true); // Skip invalid servers
        
        // Mock transformation
        List<McpServerDetailInfo> servers = new ArrayList<>();
        when(transformService.transformToNacosFormat(anyString(), anyString(), any(), any(), any())).thenReturn(
                servers);
        
        // Mock validation - invalid result with no valid servers
        McpServerImportValidationResult validationResult = new McpServerImportValidationResult();
        validationResult.setValid(false);
        List<String> errors = new ArrayList<>();
        errors.add("Validation error");
        validationResult.setErrors(errors);
        when(validationService.validateServers(anyString(), any())).thenReturn(validationResult);
        
        // When
        McpServerImportResponse response = importService.executeImport("test-namespace", request);
        
        // Then
        assertNotNull(response);
        assertFalse(response.isSuccess());
        assertTrue(response.getErrorMessage().contains("Import validation failed and no valid servers to import"));
        assertEquals(0, response.getTotalCount());
    }
    
    @Test
    void testExecuteImportWithSkipInvalidAndHasValidServers() throws Exception {
        // Given
        McpServerImportRequest request = new McpServerImportRequest();
        request.setData("{\"servers\":[]}");
        request.setImportType("json");
        request.setSkipInvalid(true); // Skip invalid servers
        request.setOverrideExisting(false);
        
        // Mock transformation
        List<McpServerDetailInfo> servers = new ArrayList<>();
        McpServerDetailInfo server = new McpServerDetailInfo();
        server.setId("server1");
        server.setName("Test Server");
        servers.add(server);
        when(transformService.transformToNacosFormat(anyString(), anyString(), any(), any(), any())).thenReturn(
                servers);
        
        // Mock validation - mixed result with some invalid and some valid
        McpServerImportValidationResult validationResult = new McpServerImportValidationResult();
        validationResult.setValid(false); // Overall invalid
        List<String> errors = new ArrayList<>();
        errors.add("Some validation errors");
        validationResult.setErrors(errors);
        validationResult.setInvalidCount(1);
        
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
        assertTrue(response.isSuccess()); // Should still be successful since valid servers were imported
        assertEquals(1, response.getTotalCount());
        assertEquals(1, response.getSuccessCount());
        assertEquals(0, response.getFailedCount());
        assertEquals(0, response.getSkippedCount());
        assertTrue(response.getErrorMessage() != null && response.getErrorMessage()
                .contains("Some invalid servers were skipped"));
    }
    
    @Test
    void testExecuteImportWithExceptionInImportProcess() throws Exception {
        // When
        McpServerImportResponse response = importService.executeImport("test-namespace", null);
        
        // Then
        assertNotNull(response);
        assertFalse(response.isSuccess());
        assertTrue(response.getErrorMessage().contains("Import execution failed"));
    }
    
    @Test
    void testExecuteImportWithNullValidationServers() throws Exception {
        // Given
        McpServerImportRequest request = new McpServerImportRequest();
        request.setData("data");
        request.setImportType("json");
        request.setOverrideExisting(false);
        
        // Mock transformation
        List<McpServerDetailInfo> servers = new ArrayList<>();
        when(transformService.transformToNacosFormat(anyString(), anyString(), any(), any(), any())).thenReturn(
                servers);
        
        // Mock validation - valid result but with null servers list
        McpServerImportValidationResult validationResult = new McpServerImportValidationResult();
        validationResult.setValid(true);
        validationResult.setServers(null); // Null servers list
        when(validationService.validateServers(anyString(), any())).thenReturn(validationResult);
        
        // When
        McpServerImportResponse response = importService.executeImport("test-namespace", request);
        
        // Then
        assertNotNull(response);
        assertTrue(response.isSuccess());
        assertEquals(0, response.getTotalCount());
        assertEquals(0, response.getSuccessCount());
        assertEquals(0, response.getFailedCount());
        assertEquals(0, response.getSkippedCount());
    }
    
    @Test
    void testExecuteImportWithSelectedServersFiltering() throws Exception {
        // Given
        McpServerImportRequest request = new McpServerImportRequest();
        request.setData("{\"servers\":[]}");
        request.setImportType("json");
        request.setSelectedServers(new String[] {"server1", "server3"}); // Select specific servers
        request.setOverrideExisting(false);
        
        // Mock transformation
        List<McpServerDetailInfo> servers = new ArrayList<>();
        when(transformService.transformToNacosFormat(anyString(), anyString(), any(), any(), any())).thenReturn(
                servers);
        
        // Mock validation with multiple servers
        McpServerImportValidationResult validationResult = new McpServerImportValidationResult();
        validationResult.setValid(true);
        
        // Server1 - valid and selected
        McpServerValidationItem item1 = new McpServerValidationItem();
        item1.setServerId("server1");
        item1.setServerName("Test Server 1");
        item1.setStatus(McpServerValidationConstants.STATUS_VALID);
        item1.setExists(false);
        McpServerDetailInfo server1 = new McpServerDetailInfo();
        server1.setId("server1");
        server1.setName("Test Server 1");
        item1.setServer(server1);
        
        // Server2 - valid but not selected (should not be imported)
        McpServerValidationItem item2 = new McpServerValidationItem();
        item2.setServerId("server2");
        item2.setServerName("Test Server 2");
        item2.setStatus(McpServerValidationConstants.STATUS_VALID);
        item2.setExists(false);
        McpServerDetailInfo server2 = new McpServerDetailInfo();
        server2.setId("server2");
        server2.setName("Test Server 2");
        item2.setServer(server2);
        
        // Server3 - valid and selected
        McpServerValidationItem item3 = new McpServerValidationItem();
        item3.setServerId("server3");
        item3.setServerName("Test Server 3");
        item3.setStatus(McpServerValidationConstants.STATUS_VALID);
        item3.setExists(false);
        McpServerDetailInfo server3 = new McpServerDetailInfo();
        server3.setId("server3");
        server3.setName("Test Server 3");
        item3.setServer(server3);
        
        List<McpServerValidationItem> validationItems = new ArrayList<>();
        validationItems.add(item1);
        validationItems.add(item2);
        validationItems.add(item3);
        validationResult.setServers(validationItems);
        when(validationService.validateServers(anyString(), any())).thenReturn(validationResult);
        
        // When
        McpServerImportResponse response = importService.executeImport("test-namespace", request);
        
        // Then
        assertNotNull(response);
        assertTrue(response.isSuccess());
        // Only server1 and server3 are valid and selected
        assertEquals(2, response.getTotalCount());
        assertEquals(2, response.getSuccessCount());
        assertEquals(0, response.getFailedCount());
        assertEquals(0, response.getSkippedCount());
    }
    
    @Test
    void testExecuteImportSkipExistingServer() throws Exception {
        // Given
        McpServerImportRequest request = new McpServerImportRequest();
        request.setData("{\"servers\":[]}");
        request.setImportType("json");
        request.setOverrideExisting(false); // Do not override existing servers
        
        // Mock transformation
        List<McpServerDetailInfo> servers = new ArrayList<>();
        when(transformService.transformToNacosFormat(anyString(), anyString(), any(), any(), any())).thenReturn(
                servers);
        
        // Mock validation with existing server
        McpServerImportValidationResult validationResult = new McpServerImportValidationResult();
        validationResult.setValid(true);
        
        McpServerValidationItem item = new McpServerValidationItem();
        item.setServerId("server1");
        item.setServerName("Test Server");
        item.setStatus(McpServerValidationConstants.STATUS_VALID);
        item.setExists(true); // Server already exists
        McpServerDetailInfo server = new McpServerDetailInfo();
        server.setId("server1");
        server.setName("Test Server");
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
        assertEquals(0, response.getSuccessCount());
        assertEquals(0, response.getFailedCount());
        assertEquals(1, response.getSkippedCount()); // Server should be skipped
        verify(operationService, never()).updateMcpServer(anyString(), anyBoolean(), any(), any(), any());
        verify(operationService, never()).createMcpServer(anyString(), any(), any(), any());
    }
    
    @Test
    void testExecuteImportUpdateExistingServer() throws Exception {
        // Given
        McpServerImportRequest request = new McpServerImportRequest();
        request.setData("{\"servers\":[]}");
        request.setImportType("json");
        request.setOverrideExisting(true); // Override existing servers
        
        // Mock transformation
        List<McpServerDetailInfo> servers = new ArrayList<>();
        when(transformService.transformToNacosFormat(anyString(), anyString(), any(), any(), any())).thenReturn(
                servers);
        
        // Mock validation with existing server
        McpServerImportValidationResult validationResult = new McpServerImportValidationResult();
        validationResult.setValid(true);
        
        McpServerValidationItem item = new McpServerValidationItem();
        item.setServerId("server1");
        item.setServerName("Test Server");
        item.setStatus(McpServerValidationConstants.STATUS_VALID);
        item.setExists(true); // Server already exists
        McpServerDetailInfo server = new McpServerDetailInfo();
        server.setId("server1");
        server.setName("Test Server");
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
        verify(operationService).updateMcpServer(eq("test-namespace"), eq(true), any(), any(), any());
        verify(operationService, never()).createMcpServer(anyString(), any(), any(), any());
    }
    
    @Test
    void testExecuteImportCreateNewServer() throws Exception {
        // Given
        McpServerImportRequest request = new McpServerImportRequest();
        request.setData("{\"servers\":[]}");
        request.setImportType("json");
        request.setOverrideExisting(false); // Do not override existing servers
        
        // Mock transformation
        List<McpServerDetailInfo> servers = new ArrayList<>();
        when(transformService.transformToNacosFormat(anyString(), anyString(), any(), any(), any())).thenReturn(
                servers);
        
        // Mock validation with new server
        McpServerImportValidationResult validationResult = new McpServerImportValidationResult();
        validationResult.setValid(true);
        
        McpServerValidationItem item = new McpServerValidationItem();
        item.setServerId("server1");
        item.setServerName("Test Server");
        item.setStatus(McpServerValidationConstants.STATUS_VALID);
        item.setExists(false); // Server does not exist
        McpServerDetailInfo server = new McpServerDetailInfo();
        server.setId("server1");
        server.setName("Test Server");
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
        verify(operationService).createMcpServer(eq("test-namespace"), any(), any(), any());
        verify(operationService, never()).updateMcpServer(anyString(), anyBoolean(), any(), any(), any());
    }
    
    @Test
    void testExecuteImportWithEndpointSpecConversion() throws Exception {
        // Given
        McpServerImportRequest request = new McpServerImportRequest();
        request.setData("{\"servers\":[]}");
        request.setImportType("json");
        request.setOverrideExisting(false);
        
        // Mock transformation
        List<McpServerDetailInfo> servers = new ArrayList<>();
        when(transformService.transformToNacosFormat(anyString(), anyString(), any(), any(), any())).thenReturn(
                servers);
        
        // Mock validation with server that has endpoint config
        McpServerImportValidationResult validationResult = new McpServerImportValidationResult();
        validationResult.setValid(true);
        
        McpServerValidationItem item = new McpServerValidationItem();
        item.setServerId("server1");
        item.setServerName("Test Server");
        item.setStatus(McpServerValidationConstants.STATUS_VALID);
        item.setExists(false);
        
        McpServerDetailInfo server = new McpServerDetailInfo();
        server.setId("server1");
        server.setName("Test Server");
        server.setProtocol("http");
        
        McpServerRemoteServiceConfig remoteConfig = new McpServerRemoteServiceConfig();
        FrontEndpointConfig endpointConfig = new FrontEndpointConfig();
        endpointConfig.setEndpointData("127.0.0.1:8080");
        remoteConfig.setFrontEndpointConfigList(Arrays.asList(endpointConfig));
        server.setRemoteServerConfig(remoteConfig);
        
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
        verify(operationService).createMcpServer(eq("test-namespace"), any(), any(), any());
    }
    
    @Test
    void testExecuteImportWithStdioProtocolNoEndpointSpec() throws Exception {
        // Given
        McpServerImportRequest request = new McpServerImportRequest();
        request.setData("{\"servers\":[]}");
        request.setImportType("json");
        request.setOverrideExisting(false);
        
        // Mock transformation
        List<McpServerDetailInfo> servers = new ArrayList<>();
        when(transformService.transformToNacosFormat(anyString(), anyString(), any(), any(), any())).thenReturn(
                servers);
        
        // Mock validation with server that has stdio protocol
        McpServerImportValidationResult validationResult = new McpServerImportValidationResult();
        validationResult.setValid(true);
        
        McpServerValidationItem item = new McpServerValidationItem();
        item.setServerId("server1");
        item.setServerName("Test Server");
        item.setStatus(McpServerValidationConstants.STATUS_VALID);
        item.setExists(false);
        
        McpServerDetailInfo server = new McpServerDetailInfo();
        server.setId("server1");
        server.setName("Test Server");
        server.setProtocol(AiConstants.Mcp.MCP_PROTOCOL_STDIO); // STDIO protocol
        
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
        verify(operationService).createMcpServer(eq("test-namespace"), any(), any(), any());
    }
    
    @Test
    void testExecuteImportFailureDuringServerCreation() throws Exception {
        // Given
        McpServerImportRequest request = new McpServerImportRequest();
        request.setData("{\"servers\":[]}");
        request.setImportType("json");
        request.setOverrideExisting(false);
        
        // Mock transformation
        List<McpServerDetailInfo> servers = new ArrayList<>();
        when(transformService.transformToNacosFormat(anyString(), anyString(), any(), any(), any())).thenReturn(
                servers);
        
        // Mock validation
        McpServerImportValidationResult validationResult = new McpServerImportValidationResult();
        validationResult.setValid(true);
        
        McpServerValidationItem item = new McpServerValidationItem();
        item.setServerId("server1");
        item.setServerName("Test Server");
        item.setStatus(McpServerValidationConstants.STATUS_VALID);
        item.setExists(false);
        
        McpServerDetailInfo server = new McpServerDetailInfo();
        server.setId("server1");
        server.setName("Test Server");
        item.setServer(server);
        
        List<McpServerValidationItem> validationItems = new ArrayList<>();
        validationItems.add(item);
        validationResult.setServers(validationItems);
        when(validationService.validateServers(anyString(), any())).thenReturn(validationResult);
        
        // Mock operation service to throw exception
        doThrow(new NacosException(500, "Failed to create server")).when(operationService)
                .createMcpServer(anyString(), any(), any(), any());
        
        // When
        McpServerImportResponse response = importService.executeImport("test-namespace", request);
        
        // Then
        assertNotNull(response);
        assertFalse(response.isSuccess());
        assertEquals(1, response.getTotalCount());
        assertEquals(0, response.getSuccessCount());
        assertEquals(1, response.getFailedCount());
        assertEquals(0, response.getSkippedCount());
        assertNotNull(response.getResults());
        assertEquals(1, response.getResults().size());
        assertEquals("failed", response.getResults().get(0).getStatus());
    }
    
    @Test
    void testExecuteImportWithEndpointSpecExceptionHandling() throws Exception {
        // Given
        McpServerImportRequest request = new McpServerImportRequest();
        request.setData("{\"servers\":[]}");
        request.setImportType("json");
        request.setOverrideExisting(false);
        
        // Mock transformation
        List<McpServerDetailInfo> servers = new ArrayList<>();
        when(transformService.transformToNacosFormat(anyString(), anyString(), any(), any(), any())).thenReturn(
                servers);
        
        // Mock validation with server that causes exception in endpoint spec conversion
        McpServerImportValidationResult validationResult = new McpServerImportValidationResult();
        validationResult.setValid(true);
        
        McpServerValidationItem item = new McpServerValidationItem();
        item.setServerId("server1");
        item.setServerName("Test Server");
        item.setStatus(McpServerValidationConstants.STATUS_VALID);
        item.setExists(false);
        
        McpServerDetailInfo server = new McpServerDetailInfo();
        server.setId("server1");
        server.setName("Test Server");
        server.setProtocol("http");
        
        McpServerRemoteServiceConfig remoteConfig = new McpServerRemoteServiceConfig();
        FrontEndpointConfig endpointConfig = new FrontEndpointConfig();
        // Create an object that will cause exception when processed in convertToEndpointSpec
        endpointConfig.setEndpointData(new Object() {
            @Override
            public String toString() {
                throw new RuntimeException("Test exception");
            }
        });
        remoteConfig.setFrontEndpointConfigList(Arrays.asList(endpointConfig));
        server.setRemoteServerConfig(remoteConfig);
        
        item.setServer(server);
        
        List<McpServerValidationItem> validationItems = new ArrayList<>();
        validationItems.add(item);
        validationResult.setServers(validationItems);
        when(validationService.validateServers(anyString(), any())).thenReturn(validationResult);
        
        // When
        McpServerImportResponse response = importService.executeImport("test-namespace", request);
        
        // Then
        assertNotNull(response);
        assertTrue(response.isSuccess()); // Should still succeed as exception in endpoint spec is caught
        assertEquals(1, response.getTotalCount());
        assertEquals(1, response.getSuccessCount());
        verify(operationService).createMcpServer(eq("test-namespace"), any(), any(), any());
    }
}