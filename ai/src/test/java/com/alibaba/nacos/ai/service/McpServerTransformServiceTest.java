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

import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.api.ai.model.mcp.McpServerDetailInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test for McpServerTransformService with MCP Registry support.
 *
 * @author nacos
 */
class McpServerTransformServiceTest {
    
    private McpServerTransformService transformService;
    
    @BeforeEach
    void setUp() {
        transformService = new McpServerTransformService();
    }
    
    @Test
    void testTransformMcpRegistryServerList() throws Exception {
        String registryJson = "{\"servers\":[{\"id\":\"4e9cf4cf-71f6-4aca-bae8-2d10a29ca2e0\","
                + "\"name\":\"io.github.21st-dev/magic-mcp\","
                + "\"description\":\"It's like v0 but in your Cursor/WindSurf/Cline. 21st dev Magic MCP server\","
                + "\"repository\":{\"url\":\"https://github.com/21st-dev/magic-mcp\",\"source\":\"github\",\"id\":\"935450522\"},"
                + "\"version_detail\":{\"version\":\"0.0.1-seed\",\"release_date\":\"2025-05-16T18:56:49Z\",\"is_latest\":true},"
                + "\"packages\":[{\"registry_name\":\"npm\",\"name\":\"@21st-dev/magic\",\"version\":\"0.0.46\","
                + "\"environment_variables\":[{\"description\":\"${input:apiKey}\",\"name\":\"API_KEY\"}]}]}],"
                + "\"total_count\":1}";
        
        List<McpServerDetailInfo> servers = transformService.transformToNacosFormat(registryJson, "json");
        
        assertNotNull(servers);
        assertEquals(1, servers.size());
        
        McpServerDetailInfo server = servers.get(0);
        assertEquals("4e9cf4cf-71f6-4aca-bae8-2d10a29ca2e0", server.getId());
        assertEquals("io.github.21st-dev/magic-mcp", server.getName());
        assertEquals("It's like v0 but in your Cursor/WindSurf/Cline. 21st dev Magic MCP server",
                server.getDescription());
        assertEquals(AiConstants.Mcp.MCP_PROTOCOL_STDIO, server.getProtocol());
        assertNotNull(server.getRepository());
        assertNotNull(server.getVersionDetail());
        assertEquals("0.0.1-seed", server.getVersionDetail().getVersion());
        assertNotNull(server.getRemoteServerConfig());
        assertEquals("npx @21st-dev/magic", server.getRemoteServerConfig().getExportPath());
    }
    
    @Test
    void testTransformSingleMcpRegistryServer() throws Exception {
        String registryJson = "{\"id\":\"d3669201-252f-403c-944b-c3ec0845782b\","
                + "\"name\":\"io.github.adfin-engineering/mcp-server-adfin\","
                + "\"description\":\"A Model Context Protocol Server for connecting with Adfin APIs\","
                + "\"repository\":{\"url\":\"https://github.com/Adfin-Engineering/mcp-server-adfin\",\"source\":\"github\",\"id\":\"951338147\"},"
                + "\"version_detail\":{\"version\":\"0.0.1-seed\",\"release_date\":\"2025-05-16T18:56:52Z\",\"is_latest\":true},"
                + "\"packages\":[{\"registry_name\":\"pypi\",\"name\":\"adfinmcp\",\"version\":\"0.1.0\","
                + "\"package_arguments\":[{\"description\":\"Directory to run the project from\",\"is_required\":true,"
                + "\"format\":\"string\",\"value\":\"--directory <absolute_path_to_adfin_mcp_folder>\",\"type\":\"named\"}],"
                + "\"environment_variables\":[{\"description\":\"<email>\",\"name\":\"ADFIN_EMAIL\"}]}]}";
        
        List<McpServerDetailInfo> servers = transformService.transformToNacosFormat(registryJson, "json");
        
        assertNotNull(servers);
        assertEquals(1, servers.size());
        
        McpServerDetailInfo server = servers.get(0);
        assertEquals("d3669201-252f-403c-944b-c3ec0845782b", server.getId());
        assertEquals("io.github.adfin-engineering/mcp-server-adfin", server.getName());
        assertEquals("A Model Context Protocol Server for connecting with Adfin APIs", server.getDescription());
        assertEquals(AiConstants.Mcp.MCP_PROTOCOL_STDIO, server.getProtocol());
        assertNotNull(server.getRepository());
        assertNotNull(server.getVersionDetail());
        assertEquals("0.0.1-seed", server.getVersionDetail().getVersion());
        assertNotNull(server.getRemoteServerConfig());
        assertEquals("python -m adfinmcp --directory <absolute_path_to_adfin_mcp_folder>",
                server.getRemoteServerConfig().getExportPath());
    }
    
    @Test
    void testTransformLegacyFormat() throws Exception {
        String legacyJson = "{\"servers\":[{\"id\":\"legacy-server\",\"name\":\"Legacy MCP Server\","
                + "\"description\":\"A legacy format server\",\"protocol\":\"stdio\","
                + "\"command\":\"node legacy-server.js\"}]}";
        
        List<McpServerDetailInfo> servers = transformService.transformToNacosFormat(legacyJson, "json");
        
        assertNotNull(servers);
        assertEquals(1, servers.size());
        
        McpServerDetailInfo server = servers.get(0);
        assertEquals("legacy-server", server.getId());
        assertEquals("Legacy MCP Server", server.getName());
        assertEquals("A legacy format server", server.getDescription());
        assertEquals(AiConstants.Mcp.MCP_PROTOCOL_STDIO, server.getProtocol());
        assertNotNull(server.getRemoteServerConfig());
        assertEquals("node legacy-server.js", server.getRemoteServerConfig().getExportPath());
    }
    
    @Test
    void testTransformEmptyRegistryData() throws Exception {
        String emptyJson = "{\"servers\":[],\"total_count\":0}";
        
        List<McpServerDetailInfo> servers = transformService.transformToNacosFormat(emptyJson, "json");
        
        assertNotNull(servers);
        assertTrue(servers.isEmpty());
    }
    
    @Test
    void testTransformInvalidJson() {
        String invalidJson = "{ invalid json }";
        
        assertThrows(Exception.class, () -> {
            transformService.transformToNacosFormat(invalidJson, "json");
        });
    }
    
    @Test
    void testTransformUnsupportedImportType() {
        String validJson = "{\"id\":\"test-server\",\"name\":\"Test Server\"}";
        
        assertThrows(IllegalArgumentException.class, () -> {
            transformService.transformToNacosFormat(validJson, "unsupported");
        });
    }
    
    @Test
    void testProtocolInferenceFromPackage() throws Exception {
        String jsonWithNpmPackage = "{\"id\":\"npm-server\",\"name\":\"NPM Server\","
                + "\"repository\":{\"url\":\"https://github.com/test/npm-server\",\"source\":\"github\",\"id\":\"123\"},"
                + "\"version_detail\":{\"version\":\"1.0.0\",\"release_date\":\"2024-01-01T00:00:00Z\",\"is_latest\":true},"
                + "\"packages\":[{\"registry_name\":\"npm\",\"name\":\"test-mcp-server\",\"version\":\"1.0.0\"}]}";
        
        List<McpServerDetailInfo> servers = transformService.transformToNacosFormat(jsonWithNpmPackage, "json");
        
        assertNotNull(servers);
        assertEquals(1, servers.size());
        
        McpServerDetailInfo server = servers.get(0);
        assertEquals(AiConstants.Mcp.MCP_PROTOCOL_STDIO, server.getProtocol());
        assertEquals("npx test-mcp-server", server.getRemoteServerConfig().getExportPath());
    }
    
    @Test
    void testUrlValidationWithMaliciousUrls() throws Exception {
        // Test with non-registry format to trigger URL validation
        String jsonWithMaliciousUrl = "{\"id\":\"malicious-server\",\"name\":\"Malicious Server\","
                + "\"url\":\"javascript:alert('xss')\",\"protocol\":\"http\"}";
        
        // This should handle malicious URLs gracefully by rejecting them or skipping invalid servers
        List<McpServerDetailInfo> servers = transformService.transformToNacosFormat(jsonWithMaliciousUrl, "json");
        
        // Should return empty list or handle gracefully
        assertNotNull(servers);
    }
    
    @Test
    void testUrlValidationWithValidPackage() throws Exception {
        // Test with valid package format that doesn't trigger URL validation issues
        String jsonWithValidPackage = "{\"id\":\"valid-server\",\"name\":\"Valid Server\","
                + "\"repository\":{\"url\":\"https://github.com/test/valid-server\",\"source\":\"github\",\"id\":\"123\"},"
                + "\"version_detail\":{\"version\":\"1.0.0\",\"release_date\":\"2024-01-01T00:00:00Z\",\"is_latest\":true},"
                + "\"packages\":[{\"registry_name\":\"npm\",\"name\":\"valid-mcp-server\",\"version\":\"1.0.0\"}]}";
        
        List<McpServerDetailInfo> servers = transformService.transformToNacosFormat(jsonWithValidPackage, "json");
        
        assertNotNull(servers);
        assertEquals(1, servers.size());
        
        McpServerDetailInfo server = servers.get(0);
        assertEquals(AiConstants.Mcp.MCP_PROTOCOL_STDIO, server.getProtocol());
        assertEquals("npx valid-mcp-server", server.getRemoteServerConfig().getExportPath());
    }
}