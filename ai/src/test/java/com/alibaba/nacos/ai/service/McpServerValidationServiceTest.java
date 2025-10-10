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

import com.alibaba.nacos.ai.constant.McpServerValidationConstants;
import com.alibaba.nacos.ai.index.McpServerIndex;
import com.alibaba.nacos.ai.model.mcp.McpServerIndexData;
import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.api.ai.model.mcp.McpServerDetailInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpServerImportValidationResult;
import com.alibaba.nacos.api.ai.model.mcp.McpServerRemoteServiceConfig;
import com.alibaba.nacos.api.ai.model.mcp.McpServerValidationItem;
import com.alibaba.nacos.api.ai.model.mcp.McpTool;
import com.alibaba.nacos.api.ai.model.mcp.McpToolSpecification;
import com.alibaba.nacos.api.ai.model.mcp.registry.Package;
import com.alibaba.nacos.api.exception.NacosException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class McpServerValidationServiceTest {

    private McpServerValidationService mcpServerValidationService;

    @Mock
    private McpServerIndex mcpServerIndex;

    @BeforeEach
    void setUp() {
        mcpServerValidationService = new McpServerValidationService();
        ReflectionTestUtils.setField(mcpServerValidationService, "mcpServerIndex", mcpServerIndex);
    }

    @Test
    void validateServersWithNullServers() throws NacosException {
        String namespaceId = "test-namespace";
        McpServerImportValidationResult result = mcpServerValidationService.validateServers(namespaceId, null);

        assertFalse(result.isValid());
        assertEquals(0, result.getTotalCount());
        assertEquals(0, result.getValidCount());
        assertEquals(0, result.getInvalidCount());
        assertEquals(0, result.getDuplicateCount());
        assertNotNull(result.getErrors());
        assertFalse(result.getErrors().isEmpty());
    }

    @Test
    void validateServersWithEmptyServers() throws NacosException {
        String namespaceId = "test-namespace";
        McpServerImportValidationResult result = mcpServerValidationService.validateServers(namespaceId, new ArrayList<>());

        assertTrue(result.isValid());
        assertEquals(0, result.getTotalCount());
        assertEquals(0, result.getValidCount());
        assertEquals(0, result.getInvalidCount());
        assertEquals(0, result.getDuplicateCount());
        assertNotNull(result.getErrors());
        assertTrue(result.getErrors().isEmpty());
    }

    @Test
    void validateServersWithValidServer() throws NacosException {
        String namespaceId = "test-namespace";
        List<McpServerDetailInfo> servers = new ArrayList<>();

        McpServerDetailInfo validServer = createValidServer();
        servers.add(validServer);

        when(mcpServerIndex.getMcpServerByName(namespaceId, validServer.getName())).thenReturn(null);

        McpServerImportValidationResult result = mcpServerValidationService.validateServers(namespaceId, servers);

        assertTrue(result.isValid());
        assertEquals(1, result.getTotalCount());
        assertEquals(1, result.getValidCount());
        assertEquals(0, result.getInvalidCount());
        assertEquals(0, result.getDuplicateCount());
        assertNotNull(result.getServers());
        assertEquals(1, result.getServers().size());
        assertEquals(McpServerValidationConstants.STATUS_VALID, result.getServers().get(0).getStatus());
    }

    @Test
    void validateServersWithInvalidServer() throws NacosException {
        final String namespaceId = "test-namespace";
        final List<McpServerDetailInfo> servers = new ArrayList<>();

        McpServerDetailInfo invalidServer = new McpServerDetailInfo();
        invalidServer.setName(""); // Empty name should be invalid
        invalidServer.setProtocol("invalid-protocol");
        invalidServer.setDescription("");

        servers.add(invalidServer);

        when(mcpServerIndex.getMcpServerByName(namespaceId, "")).thenReturn(null);

        McpServerImportValidationResult result = mcpServerValidationService.validateServers(namespaceId, servers);

        assertFalse(result.isValid());
        assertEquals(1, result.getTotalCount());
        assertEquals(0, result.getValidCount());
        assertEquals(1, result.getInvalidCount());
        assertEquals(0, result.getDuplicateCount());
        assertNotNull(result.getServers());
        assertEquals(1, result.getServers().size());
        assertEquals(McpServerValidationConstants.STATUS_INVALID, result.getServers().get(0).getStatus());
    }

    @Test
    void validateServersWithDuplicateServerInBatch() throws NacosException {
        final String namespaceId = "test-namespace";
        List<McpServerDetailInfo> servers = new ArrayList<>();

        McpServerDetailInfo server1 = createValidServer();
        server1.setName("duplicate-server");

        McpServerDetailInfo server2 = createValidServer();
        server2.setName("duplicate-server"); // Same name as server1

        servers.add(server1);
        servers.add(server2);

        when(mcpServerIndex.getMcpServerByName(namespaceId, "duplicate-server")).thenReturn(null).thenReturn(null);

        McpServerImportValidationResult result = mcpServerValidationService.validateServers(namespaceId, servers);

        assertTrue(result.isValid());
        assertEquals(2, result.getTotalCount());
        assertEquals(1, result.getValidCount());
        assertEquals(0, result.getInvalidCount());
        assertEquals(1, result.getDuplicateCount());
        assertNotNull(result.getServers());
        assertEquals(2, result.getServers().size());
        
        McpServerValidationItem item1 = result.getServers().get(0);
        McpServerValidationItem item2 = result.getServers().get(1);
        
        // First one should be valid
        assertEquals(McpServerValidationConstants.STATUS_VALID, item1.getStatus());
        // Second one should be duplicate
        assertEquals(McpServerValidationConstants.STATUS_DUPLICATE, item2.getStatus());
    }

    @Test
    void validateServersWithExistingServer() throws NacosException {
        final String namespaceId = "test-namespace";
        List<McpServerDetailInfo> servers = new ArrayList<>();

        McpServerDetailInfo server = createValidServer();
        server.setName("existing-server");

        servers.add(server);

        // Mock that server already exists
        McpServerIndexData existingServer = new McpServerIndexData();
        existingServer.setId(UUID.randomUUID().toString());
        existingServer.setNamespaceId(namespaceId);
        when(mcpServerIndex.getMcpServerByName(namespaceId, "existing-server")).thenReturn(existingServer);

        McpServerImportValidationResult result = mcpServerValidationService.validateServers(namespaceId, servers);

        assertTrue(result.isValid());
        assertEquals(1, result.getTotalCount());
        assertEquals(0, result.getValidCount());
        assertEquals(0, result.getInvalidCount());
        assertEquals(1, result.getDuplicateCount());
        assertNotNull(result.getServers());
        assertEquals(1, result.getServers().size());
        McpServerValidationItem item = result.getServers().get(0);
        assertEquals(McpServerValidationConstants.STATUS_DUPLICATE, item.getStatus());
        assertTrue(item.isExists());
    }

    @Test
    void validateServersWithExceptionDuringValidation() throws NacosException {
        String namespaceId = "test-namespace";
        List<McpServerDetailInfo> servers = new ArrayList<>();

        McpServerDetailInfo server = createValidServer();
        servers.add(server);

        // Mock exception during validation
        when(mcpServerIndex.getMcpServerByName(namespaceId, server.getName())).thenThrow(new RuntimeException("Test exception"));

        McpServerImportValidationResult result = mcpServerValidationService.validateServers(namespaceId, servers);

        assertFalse(result.isValid());
        assertEquals(1, result.getTotalCount());
        assertEquals(0, result.getValidCount());
        assertEquals(1, result.getInvalidCount());
        assertEquals(0, result.getDuplicateCount());
        assertNotNull(result.getErrors());
        assertTrue(result.getErrors().isEmpty());
    }

    @Test
    void validateSingleServerWithValidServer() throws Exception {
        String namespaceId = "test-namespace";
        McpServerDetailInfo server = createValidServer();
        server.setName("valid-server");

        when(mcpServerIndex.getMcpServerByName(namespaceId, "valid-server")).thenReturn(null);

        // 使用反射调用私有方法
        Method method = McpServerValidationService.class.getDeclaredMethod(
                "validateSingleServer", String.class, McpServerDetailInfo.class, Set.class);
        method.setAccessible(true);
        McpServerValidationItem item = (McpServerValidationItem) method.invoke(
                mcpServerValidationService, namespaceId, server, new HashSet<>());

        assertNotNull(item);
        assertEquals("valid-server", item.getServerName());
        assertEquals(McpServerValidationConstants.STATUS_VALID, item.getStatus());
        assertNotNull(item.getErrors());
        assertTrue(item.getErrors().isEmpty());
        assertFalse(item.isExists());
        assertEquals(server, item.getServer());
    }

    @Test
    void validateSingleServerWithMissingName() throws Exception {
        final String namespaceId = "test-namespace";
        McpServerDetailInfo server = new McpServerDetailInfo();
        server.setName(""); // Empty name
        server.setProtocol(AiConstants.Mcp.MCP_PROTOCOL_HTTP);
        server.setDescription("Test description");

        when(mcpServerIndex.getMcpServerByName(namespaceId, "")).thenReturn(null);

        // 使用反射调用私有方法
        Method method = McpServerValidationService.class.getDeclaredMethod(
                "validateSingleServer", String.class, McpServerDetailInfo.class, Set.class);
        method.setAccessible(true);
        McpServerValidationItem item = (McpServerValidationItem) method.invoke(
                mcpServerValidationService, namespaceId, server, new HashSet<>());

        assertNotNull(item);
        assertEquals("", item.getServerName());
        assertEquals(McpServerValidationConstants.STATUS_INVALID, item.getStatus());
        assertNotNull(item.getErrors());
        assertFalse(item.getErrors().isEmpty());
        assertTrue(item.getErrors().contains("Server name is required"));
    }

    @Test
    void validateSingleServerWithMissingProtocol() throws Exception {
        final String namespaceId = "test-namespace";
        McpServerDetailInfo server = new McpServerDetailInfo();
        server.setName("test-server");
        server.setProtocol(""); // Empty protocol
        server.setDescription("Test description");

        when(mcpServerIndex.getMcpServerByName(namespaceId, "test-server")).thenReturn(null);

        // 使用反射调用私有方法
        Method method = McpServerValidationService.class.getDeclaredMethod(
                "validateSingleServer", String.class, McpServerDetailInfo.class, Set.class);
        method.setAccessible(true);
        McpServerValidationItem item = (McpServerValidationItem) method.invoke(
                mcpServerValidationService, namespaceId, server, new HashSet<>());

        assertNotNull(item);
        assertEquals("test-server", item.getServerName());
        assertEquals(McpServerValidationConstants.STATUS_INVALID, item.getStatus());
        assertNotNull(item.getErrors());
        assertFalse(item.getErrors().isEmpty());
        assertTrue(item.getErrors().contains("Protocol is required"));
    }

    @Test
    void validateSingleServerWithInvalidProtocol() throws Exception {
        final String namespaceId = "test-namespace";
        McpServerDetailInfo server = new McpServerDetailInfo();
        server.setName("test-server");
        server.setProtocol("invalid-protocol");
        server.setDescription("Test description");

        when(mcpServerIndex.getMcpServerByName(namespaceId, "test-server")).thenReturn(null);

        // 使用反射调用私有方法
        Method method = McpServerValidationService.class.getDeclaredMethod(
                "validateSingleServer", String.class, McpServerDetailInfo.class, Set.class);
        method.setAccessible(true);
        McpServerValidationItem item = (McpServerValidationItem) method.invoke(
                mcpServerValidationService, namespaceId, server, new HashSet<>());

        assertNotNull(item);
        assertEquals("test-server", item.getServerName());
        assertEquals(McpServerValidationConstants.STATUS_INVALID, item.getStatus());
        assertNotNull(item.getErrors());
        assertFalse(item.getErrors().isEmpty());
        assertTrue(item.getErrors().contains("Invalid protocol: invalid-protocol"));
    }

    @Test
    void validateSingleServerWithMissingDescription() throws Exception {
        final String namespaceId = "test-namespace";
        McpServerDetailInfo server = new McpServerDetailInfo();
        server.setName("test-server");
        server.setProtocol(AiConstants.Mcp.MCP_PROTOCOL_HTTP);
        server.setDescription(""); // Empty description

        when(mcpServerIndex.getMcpServerByName(namespaceId, "test-server")).thenReturn(null);

        // 使用反射调用私有方法
        Method method = McpServerValidationService.class.getDeclaredMethod(
                "validateSingleServer", String.class, McpServerDetailInfo.class, Set.class);
        method.setAccessible(true);
        McpServerValidationItem item = (McpServerValidationItem) method.invoke(
                mcpServerValidationService, namespaceId, server, new HashSet<>());

        assertNotNull(item);
        assertEquals("test-server", item.getServerName());
        assertEquals(McpServerValidationConstants.STATUS_INVALID, item.getStatus());
        assertNotNull(item.getErrors());
        assertFalse(item.getErrors().isEmpty());
        assertTrue(item.getErrors().contains("Description is required"));
    }

    @Test
    void validateSingleServerWithDuplicateInBatch() throws Exception {
        String namespaceId = "test-namespace";
        McpServerDetailInfo server = createValidServer();
        server.setName("duplicate-server");

        when(mcpServerIndex.getMcpServerByName(namespaceId, "duplicate-server")).thenReturn(null);

        // Add server name to existing names set to simulate duplicate in batch
        java.util.Set<String> existingNames = new java.util.HashSet<>();
        existingNames.add("duplicate-server");

        // 使用反射调用私有方法
        Method method = McpServerValidationService.class.getDeclaredMethod(
                "validateSingleServer", String.class, McpServerDetailInfo.class, Set.class);
        method.setAccessible(true);
        McpServerValidationItem item = (McpServerValidationItem) method.invoke(
                mcpServerValidationService, namespaceId, server, existingNames);

        assertNotNull(item);
        assertEquals("duplicate-server", item.getServerName());
        assertEquals(McpServerValidationConstants.STATUS_DUPLICATE, item.getStatus());
        assertNotNull(item.getErrors());
        assertFalse(item.getErrors().isEmpty());
        assertTrue(item.getErrors().contains("Duplicate server name in import batch: duplicate-server"));
    }

    @Test
    void validateSingleServerWithExceptionDuringExistenceCheck() throws Exception {
        String namespaceId = "test-namespace";
        McpServerDetailInfo server = createValidServer();
        server.setName("test-server");

        when(mcpServerIndex.getMcpServerByName(namespaceId, "test-server")).thenThrow(new RuntimeException("Test exception"));

        // 使用反射调用私有方法
        Method method = McpServerValidationService.class.getDeclaredMethod(
                "validateSingleServer", String.class, McpServerDetailInfo.class, Set.class);
        method.setAccessible(true);
        McpServerValidationItem item = (McpServerValidationItem) method.invoke(
                mcpServerValidationService, namespaceId, server, new HashSet<>());

        assertNotNull(item);
        assertEquals("test-server", item.getServerName());
        assertEquals(McpServerValidationConstants.STATUS_INVALID, item.getStatus()); // Should still be valid if only existence check fails
        assertNotNull(item.getErrors());
        assertFalse(item.getErrors().isEmpty());
        assertTrue(item.getErrors().contains("Error checking existing server: Test exception"));
    }

    @Test
    void isValidProtocolWithValidProtocols() throws Exception {
        // 使用反射调用私有方法
        Method method = McpServerValidationService.class.getDeclaredMethod("isValidProtocol", String.class);
        method.setAccessible(true);

        // Test all valid protocols
        assertTrue((Boolean) method.invoke(mcpServerValidationService, AiConstants.Mcp.MCP_PROTOCOL_STDIO));
        assertTrue((Boolean) method.invoke(mcpServerValidationService, AiConstants.Mcp.MCP_PROTOCOL_SSE));
        assertTrue((Boolean) method.invoke(mcpServerValidationService, AiConstants.Mcp.MCP_PROTOCOL_STREAMABLE));
        assertTrue((Boolean) method.invoke(mcpServerValidationService, AiConstants.Mcp.MCP_PROTOCOL_HTTP));
        assertTrue((Boolean) method.invoke(mcpServerValidationService, AiConstants.Mcp.MCP_PROTOCOL_DUBBO));
    }

    @Test
    void isValidProtocolWithInvalidProtocol() throws Exception {
        // 使用反射调用私有方法
        Method method = McpServerValidationService.class.getDeclaredMethod("isValidProtocol", String.class);
        method.setAccessible(true);

        assertFalse((Boolean) method.invoke(mcpServerValidationService, "invalid-protocol"));
        assertFalse((Boolean) method.invoke(mcpServerValidationService, ""));
        assertFalse((Boolean) method.invoke(mcpServerValidationService, (String) null));
    }

    @Test
    void validateProtocolSpecificConfigWithValidStdioConfig() throws Exception {
        McpServerDetailInfo server = new McpServerDetailInfo();
        server.setProtocol(AiConstants.Mcp.MCP_PROTOCOL_STDIO);
        
        // Valid stdio config with localServerConfig
        Map<String, Object> localConfig = new HashMap<>();
        localConfig.put("command", "test-command");
        server.setLocalServerConfig(localConfig);

        List<String> errors = new ArrayList<>();
        
        // 使用反射调用私有方法
        Method method = McpServerValidationService.class.getDeclaredMethod(
                "validateProtocolSpecificConfig", McpServerDetailInfo.class, List.class);
        method.setAccessible(true);
        method.invoke(mcpServerValidationService, server, errors);

        assertNotNull(errors);
        assertTrue(errors.isEmpty());
    }

    @Test
    void validateProtocolSpecificConfigWithValidStdioConfigWithPackages() throws Exception {
        McpServerDetailInfo server = new McpServerDetailInfo();
        server.setProtocol(AiConstants.Mcp.MCP_PROTOCOL_STDIO);
        
        // Valid stdio config with packages
        List<Package> packages = new ArrayList<>();
        packages.add(new Package());
        server.setPackages(packages);
        server.setLocalServerConfig(null); // No local config, but has packages

        List<String> errors = new ArrayList<>();
        
        // 使用反射调用私有方法
        Method method = McpServerValidationService.class.getDeclaredMethod(
                "validateProtocolSpecificConfig", McpServerDetailInfo.class, List.class);
        method.setAccessible(true);
        method.invoke(mcpServerValidationService, server, errors);

        assertNotNull(errors);
        assertTrue(errors.isEmpty());
    }

    @Test
    void validateProtocolSpecificConfigWithInvalidStdioConfig() throws Exception {
        McpServerDetailInfo server = new McpServerDetailInfo();
        server.setProtocol(AiConstants.Mcp.MCP_PROTOCOL_STDIO);
        
        // Invalid stdio config - no local config and no packages
        server.setLocalServerConfig(null);
        server.setPackages(null);

        List<String> errors = new ArrayList<>();
        
        // 使用反射调用私有方法
        Method method = McpServerValidationService.class.getDeclaredMethod(
                "validateProtocolSpecificConfig", McpServerDetailInfo.class, List.class);
        method.setAccessible(true);
        method.invoke(mcpServerValidationService, server, errors);

        assertNotNull(errors);
        assertFalse(errors.isEmpty());
        assertTrue(errors.contains("Local server configuration or packages are required for stdio protocol"));
    }

    @Test
    void validateProtocolSpecificConfigWithValidRemoteConfig() throws Exception {
        McpServerDetailInfo server = new McpServerDetailInfo();
        server.setProtocol(AiConstants.Mcp.MCP_PROTOCOL_HTTP);
        
        // Valid remote config
        McpServerRemoteServiceConfig remoteConfig = new McpServerRemoteServiceConfig();
        server.setRemoteServerConfig(remoteConfig);

        List<String> errors = new ArrayList<>();
        
        // 使用反射调用私有方法
        Method method = McpServerValidationService.class.getDeclaredMethod(
                "validateProtocolSpecificConfig", McpServerDetailInfo.class, List.class);
        method.setAccessible(true);
        method.invoke(mcpServerValidationService, server, errors);

        assertNotNull(errors);
        assertTrue(errors.isEmpty());
    }

    @Test
    void validateProtocolSpecificConfigWithInvalidRemoteConfig() throws Exception {
        McpServerDetailInfo server = new McpServerDetailInfo();
        server.setProtocol(AiConstants.Mcp.MCP_PROTOCOL_HTTP);
        
        // Invalid remote config - no remote config
        server.setRemoteServerConfig(null);

        List<String> errors = new ArrayList<>();
        
        // 使用反射调用私有方法
        Method method = McpServerValidationService.class.getDeclaredMethod(
                "validateProtocolSpecificConfig", McpServerDetailInfo.class, List.class);
        method.setAccessible(true);
        method.invoke(mcpServerValidationService, server, errors);

        assertNotNull(errors);
        assertFalse(errors.isEmpty());
        assertTrue(errors.contains("Remote server configuration is required for " + AiConstants.Mcp.MCP_PROTOCOL_HTTP + " protocol"));
    }

    @Test
    void validateProtocolSpecificConfigWithValidToolSpec() throws Exception {
        McpServerDetailInfo server = new McpServerDetailInfo();
        server.setProtocol(AiConstants.Mcp.MCP_PROTOCOL_HTTP);
        server.setRemoteServerConfig(new McpServerRemoteServiceConfig());
        
        // Valid tool spec with tools
        McpToolSpecification toolSpec = new McpToolSpecification();
        List<McpTool> tools = new ArrayList<>();
        McpTool tool = new McpTool();
        tool.setName("test-tool");
        tools.add(tool);
        toolSpec.setTools(tools);
        server.setToolSpec(toolSpec);

        List<String> errors = new ArrayList<>();
        
        // 使用反射调用私有方法
        Method method = McpServerValidationService.class.getDeclaredMethod(
                "validateProtocolSpecificConfig", McpServerDetailInfo.class, List.class);
        method.setAccessible(true);
        method.invoke(mcpServerValidationService, server, errors);

        assertNotNull(errors);
        assertTrue(errors.isEmpty());
    }

    @Test
    void validateProtocolSpecificConfigWithInvalidToolSpec() throws Exception {
        McpServerDetailInfo server = new McpServerDetailInfo();
        server.setProtocol(AiConstants.Mcp.MCP_PROTOCOL_HTTP);
        server.setRemoteServerConfig(new McpServerRemoteServiceConfig());
        
        // Invalid tool spec - empty tools list
        McpToolSpecification toolSpec = new McpToolSpecification();
        toolSpec.setTools(new ArrayList<>()); // Empty tools list
        server.setToolSpec(toolSpec);

        List<String> errors = new ArrayList<>();
        
        // 使用反射调用私有方法
        Method method = McpServerValidationService.class.getDeclaredMethod(
                "validateProtocolSpecificConfig", McpServerDetailInfo.class, List.class);
        method.setAccessible(true);
        method.invoke(mcpServerValidationService, server, errors);

        assertNotNull(errors);
        assertFalse(errors.isEmpty());
        assertTrue(errors.contains("Tool specification should contain at least one tool"));
    }

    @Test
    void validateProtocolSpecificConfigWithNullToolSpec() throws Exception {
        McpServerDetailInfo server = new McpServerDetailInfo();
        server.setProtocol(AiConstants.Mcp.MCP_PROTOCOL_HTTP);
        server.setRemoteServerConfig(new McpServerRemoteServiceConfig());
        
        // No tool spec - should be valid
        server.setToolSpec(null);

        List<String> errors = new ArrayList<>();
        
        // 使用反射调用私有方法
        Method method = McpServerValidationService.class.getDeclaredMethod(
                "validateProtocolSpecificConfig", McpServerDetailInfo.class, List.class);
        method.setAccessible(true);
        method.invoke(mcpServerValidationService, server, errors);

        assertNotNull(errors);
        assertTrue(errors.isEmpty());
    }

    @Test
    void validateSingleServerDoesNotCallIndexWhenNameIsBlank() throws Exception {
        final String namespaceId = "test-namespace";
        McpServerDetailInfo server = new McpServerDetailInfo();
        server.setName(""); // Blank name
        server.setProtocol(AiConstants.Mcp.MCP_PROTOCOL_HTTP);
        server.setDescription("Test description");

        // Should not call the index when name is blank
        // 使用反射调用私有方法
        Method method = McpServerValidationService.class.getDeclaredMethod(
                "validateSingleServer", String.class, McpServerDetailInfo.class, Set.class);
        method.setAccessible(true);
        McpServerValidationItem item = (McpServerValidationItem) method.invoke(
                mcpServerValidationService, namespaceId, server, new HashSet<>());

        assertNotNull(item);
        assertEquals("", item.getServerName());
        assertEquals(McpServerValidationConstants.STATUS_INVALID, item.getStatus());
        assertEquals("Server name is required", item.getErrors().get(0));
    }

    @Test
    void validateServersWithMixedValidAndInvalidServers() throws NacosException {
        final String namespaceId = "test-namespace";
        List<McpServerDetailInfo> servers = new ArrayList<>();

        // Add a valid server
        McpServerDetailInfo validServer = createValidServer();
        validServer.setName("valid-server");
        servers.add(validServer);

        // Add an invalid server
        McpServerDetailInfo invalidServer = new McpServerDetailInfo();
        invalidServer.setName(""); // Missing name
        invalidServer.setProtocol(AiConstants.Mcp.MCP_PROTOCOL_HTTP);
        invalidServer.setDescription("Test description");
        servers.add(invalidServer);

        // Add a duplicate server
        McpServerDetailInfo duplicateServer = createValidServer();
        duplicateServer.setName("valid-server"); // Same name as validServer
        servers.add(duplicateServer);

        when(mcpServerIndex.getMcpServerByName(namespaceId, "valid-server")).thenReturn(null);
        when(mcpServerIndex.getMcpServerByName(namespaceId, "")).thenReturn(null);

        McpServerImportValidationResult result = mcpServerValidationService.validateServers(namespaceId, servers);

        assertFalse(result.isValid());
        assertEquals(3, result.getTotalCount());
        assertEquals(1, result.getValidCount());
        assertEquals(1, result.getInvalidCount());
        assertEquals(1, result.getDuplicateCount());
        assertNotNull(result.getServers());
        assertEquals(3, result.getServers().size());
    }

    private McpServerDetailInfo createValidServer() {
        McpServerDetailInfo server = new McpServerDetailInfo();
        server.setName("test-server");
        server.setProtocol(AiConstants.Mcp.MCP_PROTOCOL_HTTP);
        server.setDescription("Test description");
        
        McpServerRemoteServiceConfig remoteConfig = new McpServerRemoteServiceConfig();
        server.setRemoteServerConfig(remoteConfig);
        
        return server;
    }
}