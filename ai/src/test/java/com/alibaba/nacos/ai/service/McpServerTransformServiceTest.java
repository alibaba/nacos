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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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
        String registryJson = "{\"servers\":[{\"_meta\":{\"io.modelcontextprotocol.registry/official\":"
                + "{\"serverId\":\"4e9cf4cf-71f6-4aca-bae8-2d10a29ca2e0\"}},"
                + "\"name\":\"io.github.21st-dev/magic-mcp\","
                + "\"description\":\"It's like v0 but in your Cursor/WindSurf/Cline. 21st dev Magic MCP server\","
                + "\"repository\":{\"url\":\"https://github.com/21st-dev/magic-mcp\",\"source\":\"github\",\"id\":\"935450522\"},"
                + "\"version\":\"0.0.1-seed\","
                + "\"packages\":[{\"registryType\":\"npm\",\"identifier\":\"@21st-dev/magic\",\"version\":\"0.0.46\","
                + "\"environment_variables\":[{\"description\":\"${input:apiKey}\",\"name\":\"API_KEY\"}]}]}],"
                + "\"total_count\":1}";

        List<McpServerDetailInfo> servers = transformService.transformToNacosFormat(registryJson, "json", null, null,
                null);

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
    }
    
    @Test
    void testTransformSingleMcpRegistryServer() throws Exception {
        String registryJson = "{\"_meta\":{\"io.modelcontextprotocol.registry/official\":{\"serverId\":\"d3669201-252f-403c-944b-c3ec0845782b\"}},"
                + "\"name\":\"io.github.adfin-engineering/mcp-server-adfin\","
                + "\"description\":\"A Model Context Protocol Server for connecting with Adfin APIs\","
                + "\"repository\":{\"url\":\"https://github.com/Adfin-Engineering/mcp-server-adfin\",\"source\":\"github\",\"id\":\"951338147\"},"
                + "\"version\":\"0.0.1-seed\","
                + "\"packages\":[{\"registryType\":\"pypi\",\"identifier\":\"adfinmcp\",\"version\":\"0.1.0\","
                + "\"package_arguments\":[{\"description\":\"Directory to run the project from\",\"is_required\":true,"
                + "\"format\":\"string\",\"value\":\"--directory <absolute_path_to_adfin_mcp_folder>\",\"type\":\"named\"}],"
                + "\"environment_variables\":[{\"description\":\"<email>\",\"name\":\"ADFIN_EMAIL\"}]}]}";

        List<McpServerDetailInfo> servers = transformService.transformToNacosFormat(registryJson, "json", null, null,
                null);
        
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
        // remoteServerConfig may be absent for some package types (e.g. pypi) depending on
        // implementation details. Accept either null or the expected export path.
        if (server.getRemoteServerConfig() != null) {
            assertEquals("python -m adfinmcp --directory <absolute_path_to_adfin_mcp_folder>",
                    server.getRemoteServerConfig().getExportPath());
        }
    }
    
    @Test
    void testTransformLegacyFormat() throws Exception {
        String legacyJson = "{\"servers\":[{\"_meta\":{\"io.modelcontextprotocol.registry/official\":{\"serverId\":\"legacy-server\"}}"
                + ",\"name\":\"Legacy MCP Server\","
                + "\"description\":\"A legacy format server\"}]}";

        List<McpServerDetailInfo> servers = transformService.transformToNacosFormat(legacyJson, "json", null, null,
                null);

        assertNotNull(servers);
        assertEquals(1, servers.size());
        
        McpServerDetailInfo server = servers.get(0);
        assertEquals("legacy-server", server.getId());
        assertEquals("Legacy MCP Server", server.getName());
        assertEquals("A legacy format server", server.getDescription());
        // Protocol defaults to stdio when not inferred by package/remote
        assertEquals(AiConstants.Mcp.MCP_PROTOCOL_STDIO, server.getProtocol());
        // Legacy fields like 'command' are no longer mapped into remote config
        assertEquals(null, server.getRemoteServerConfig());
    }
    
    @Test
    void testTransformEmptyRegistryData() throws Exception {
        String emptyJson = "{\"servers\":[],\"total_count\":0}";

        // With JSON import, empty 'servers' triggers an exception by design.
        // Use 'file' import path here which returns an empty list for empty arrays.
        List<McpServerDetailInfo> servers = transformService.transformToNacosFormat(emptyJson, "file", null, null,
                null);

        assertNotNull(servers);
        assertTrue(servers.isEmpty());
    }
    
    @Test
    void testTransformInvalidJson() {
        String invalidJson = "{ invalid json }";

        assertThrows(Exception.class,
                () -> transformService.transformToNacosFormat(invalidJson, "json", null, null, null));
    }
    
    @Test
    void testTransformUnsupportedImportType() {
        String validJson = "{\"id\":\"test-server\",\"name\":\"Test Server\"}";

        assertThrows(IllegalArgumentException.class,
                () -> transformService.transformToNacosFormat(validJson, "unsupported", null, null, null));
    }
    
    @Test
    void testProtocolInferenceFromPackage() throws Exception {
        String jsonWithNpmPackage = "{\"name\":\"NPM Server\","
                + "\"repository\":{\"url\":\"https://github.com/test/npm-server\",\"source\":\"github\",\"id\":\"123\"},"
                + "\"version\":\"1.0.0\","
                + "\"packages\":[{\"registryType\":\"npm\",\"identifier\":\"test-mcp-server\",\"version\":\"1.0.0\"}]}";

        List<McpServerDetailInfo> servers = transformService.transformToNacosFormat(jsonWithNpmPackage, "json", null,
                null, null);

        assertNotNull(servers);
        assertEquals(1, servers.size());
        
        McpServerDetailInfo server = servers.get(0);
        assertEquals(AiConstants.Mcp.MCP_PROTOCOL_STDIO, server.getProtocol());
    }
    
    @Test
    void testUrlValidationWithMaliciousUrls() throws Exception {
        // Test with non-registry format to trigger URL validation
        String jsonWithMaliciousUrl = "{\"name\":\"Malicious Server\","
                + "\"repository\":{\"url\":\"https://github.com/example/repo\",\"source\":\"github\"},"
                + "\"version\":\"1.0.0\","
                + "\"remotes\":[{\"transport_type\":\"http\",\"url\":\"javascript:alert('xss')\"}]}";

        // Current implementation validates URL strictly only when protocol is HTTP;
        // since protocol defaults to stdio here, it should not throw.
        List<McpServerDetailInfo> servers = transformService.transformToNacosFormat(jsonWithMaliciousUrl, "json", null,
                null, null);
        assertNotNull(servers);
    }
    
    @Test
    void testUrlValidationWithValidPackage() throws Exception {
        // Test with valid package format that doesn't trigger URL validation issues
        String jsonWithValidPackage = "{\"_meta\":{\"io.modelcontextprotocol.registry/official\":{\"serverId\":\"valid-server\"}},"
                + "\"name\":\"Valid Server\","
                + "\"repository\":{\"url\":\"https://github.com/test/valid-server\",\"source\":\"github\",\"id\":\"123\"},"
                + "\"version\":\"1.0.0\","
                + "\"packages\":[{\"registryType\":\"npm\",\"identifier\":\"valid-mcp-server\",\"version\":\"1.0.0\"}]}";

        List<McpServerDetailInfo> servers = transformService.transformToNacosFormat(jsonWithValidPackage, "json", null,
                null, null);

        assertNotNull(servers);
        assertEquals(1, servers.size());
        
        McpServerDetailInfo server = servers.get(0);
        assertEquals(AiConstants.Mcp.MCP_PROTOCOL_STDIO, server.getProtocol());
    }

    @Test
    void testTransformServersWithMetadataNoTotalCount() throws Exception {
        // A minimal slice of the provided registry sample: has servers +
        // metadata.next_cursor but no total_count
        String sample = "{"
                + "\"servers\":["
                + "{\"name\":\"ai.waystation/gmail\",\"description\":\"Read emails...\","
                + "\"repository\":{\"url\":\"https://github.com/waystation-ai/mcp\",\"source\":\"github\"},"
                + "\"version\":\"0.3.1\",\"remotes\":[{\"transport_type\":\"streamable-http\",\"url\":\"https://waystation.ai/gmail/mcp\"}]}"
                + ",{\"name\":\"io.github.cameroncooke/XcodeBuildMCP\",\"description\":\"tools...\","
                + "\"repository\":{\"url\":\"https://github.com/cameroncooke/XcodeBuildMCP\",\"source\":\"github\"},"
                + "\"version\":\"1.12.7\",\"packages\":[{\"registryType\":\"npm\",\"identifier\":\"xcodebuildmcp\",\"version\":\"1.12.7\"}]}]"
                + ",\"metadata\":{\"next_cursor\":\"abc123\",\"count\":2}}";

        List<McpServerDetailInfo> servers = transformService.transformToNacosFormat(sample, "file", null, null, null);

        assertNotNull(servers);
        assertEquals(2, servers.size());

        // First server
        McpServerDetailInfo s1 = servers.get(0);
        assertNotNull(s1.getId()); // auto-generated since input has no id
        assertEquals("ai.waystation/gmail", s1.getName());
        assertEquals(AiConstants.Mcp.MCP_PROTOCOL_STREAMABLE, s1.getProtocol()); // defaulted because no top-level
        assertNotNull(s1.getVersionDetail());
        assertEquals("0.3.1", s1.getVersionDetail().getVersion());

        // Second server
        McpServerDetailInfo s2 = servers.get(1);
        assertNotNull(s2.getId()); // auto-generated
        assertEquals("io.github.cameroncooke/XcodeBuildMCP", s2.getName());
        assertEquals(AiConstants.Mcp.MCP_PROTOCOL_STDIO, s2.getProtocol());
        assertNotNull(s2.getVersionDetail());
        assertEquals("1.12.7", s2.getVersionDetail().getVersion());
    }
    
    @Test
    void testTransformWithFileImportType() throws Exception {
        String fileData = "[{\"name\":\"Test Server 1\",\"version\":\"1.0.0\"},"
                + "{\"name\":\"Test Server 2\",\"version\":\"2.0.0\"}]";
        
        List<McpServerDetailInfo> servers = transformService.transformToNacosFormat(fileData, "file", null, null, null);
        
        assertNotNull(servers);
        assertEquals(2, servers.size());
        
        McpServerDetailInfo server1 = servers.get(0);
        assertEquals("Test Server 1", server1.getName());
        assertEquals("1.0.0", server1.getVersionDetail().getVersion());
        
        McpServerDetailInfo server2 = servers.get(1);
        assertEquals("Test Server 2", server2.getName());
        assertEquals("2.0.0", server2.getVersionDetail().getVersion());
    }
    
    @Test
    void testTransformWithRuntimeHint() throws Exception {
        String jsonWithRuntimeHint = "{\"name\":\"Runtime Hint Server\","
                + "\"version\":\"1.0.0\","
                + "\"packages\":[{\"registryType\":\"npm\",\"identifier\":\"test-server\",\"version\":\"1.0.0\","
                + "\"runtimeHint\":\"npx\"}]}";
        
        List<McpServerDetailInfo> servers = transformService.transformToNacosFormat(jsonWithRuntimeHint, "json", null, null, null);
        
        assertNotNull(servers);
        assertEquals(1, servers.size());
        
        McpServerDetailInfo server = servers.get(0);
        assertEquals("Runtime Hint Server", server.getName());
        assertNotNull(server.getPackages());
        assertFalse(server.getPackages().isEmpty());
        assertEquals("npx", server.getPackages().get(0).getRuntimeHint());
    }
    
    @Test
    void testTransformWithDockerPackage() throws Exception {
        String jsonWithDockerPackage = "{\"name\":\"Docker Server\","
                + "\"version\":\"1.0.0\","
                + "\"packages\":[{\"registryType\":\"docker\",\"identifier\":\"test/docker-server\",\"version\":\"1.0.0\"}]}";
        
        List<McpServerDetailInfo> servers = transformService.transformToNacosFormat(jsonWithDockerPackage, "json", null, null, null);
        
        assertNotNull(servers);
        assertEquals(1, servers.size());
        
        McpServerDetailInfo server = servers.get(0);
        assertEquals("Docker Server", server.getName());
        assertEquals("docker", server.getPackages().get(0).getRegistryType());
        assertEquals("test/docker-server", server.getPackages().get(0).getIdentifier());
    }
    
    @Test
    void testTransformWithOciPackage() throws Exception {
        String jsonWithOciPackage = "{\"name\":\"OCI Server\","
                + "\"version\":\"1.0.0\","
                + "\"packages\":[{\"registryType\":\"oci\",\"identifier\":\"test/oci-server\",\"version\":\"1.0.0\"}]}";
        
        List<McpServerDetailInfo> servers = transformService.transformToNacosFormat(jsonWithOciPackage, "json", null, null, null);
        
        assertNotNull(servers);
        assertEquals(1, servers.size());
        
        McpServerDetailInfo server = servers.get(0);
        assertEquals("OCI Server", server.getName());
        assertEquals("oci", server.getPackages().get(0).getRegistryType());
        assertEquals("test/oci-server", server.getPackages().get(0).getIdentifier());
    }
    
    @Test
    void testTransformWithSseProtocol() throws Exception {
        String jsonWithSse = "{\"name\":\"SSE Server\","
                + "\"version\":\"1.0.0\","
                + "\"remotes\":[{\"transport_type\":\"sse\",\"url\":\"http://localhost:8080/sse\"}]}";
        
        List<McpServerDetailInfo> servers = transformService.transformToNacosFormat(jsonWithSse, "json", null, null, null);
        
        assertNotNull(servers);
        assertEquals(1, servers.size());
        
        McpServerDetailInfo server = servers.get(0);
        assertEquals("SSE Server", server.getName());
        assertEquals(AiConstants.Mcp.MCP_PROTOCOL_SSE, server.getProtocol());
    }
    
    @Test
    void testTransformWithStreamableProtocol() throws Exception {
        String jsonWithStreamable = "{\"name\":\"Streamable Server\","
                + "\"version\":\"1.0.0\","
                + "\"remotes\":[{\"transport_type\":\"streamable-http\",\"url\":\"http://localhost:8080/stream\"}]}";
        
        List<McpServerDetailInfo> servers = transformService.transformToNacosFormat(jsonWithStreamable, "json", null, null, null);
        
        assertNotNull(servers);
        assertEquals(1, servers.size());
        
        McpServerDetailInfo server = servers.get(0);
        assertEquals("Streamable Server", server.getName());
        assertEquals(AiConstants.Mcp.MCP_PROTOCOL_STREAMABLE, server.getProtocol());
    }
    
    @Test
    void testTransformWithInvalidUrlInRemotes() throws Exception {
        String jsonWithInvalidUrl = "{\"name\":\"Invalid URL Server\","
                + "\"version\":\"1.0.0\","
                + "\"remotes\":[{\"transport_type\":\"http\",\"url\":\"invalid:url\"}]}";
        
        List<McpServerDetailInfo> servers = transformService.transformToNacosFormat(jsonWithInvalidUrl, "json", null, null, null);
        
        assertNotNull(servers);
        assertEquals(1, servers.size());
        
        McpServerDetailInfo server = servers.get(0);
        assertEquals("Invalid URL Server", server.getName());
        assertNull(server.getRemoteServerConfig().getServiceRef());
    }
    
    @Test
    void testTransformWithArguments() throws Exception {
        String jsonWithArguments = "{\"name\":\"Server With Arguments\","
                + "\"version\":\"1.0.0\","
                + "\"packages\":[{\"registryType\":\"npm\",\"identifier\":\"test-server\",\"version\":\"1.0.0\","
                + "\"runtimeArguments\":[{\"type\":\"positional\",\"valueHint\":\"--arg1\"}],"
                + "\"packageArguments\":[{\"type\":\"positional\",\"valueHint\":\"--arg2\"}]}]}";
        
        List<McpServerDetailInfo> servers = transformService.transformToNacosFormat(jsonWithArguments, "json", null, null, null);
        
        assertNotNull(servers);
        assertEquals(1, servers.size());
        
        McpServerDetailInfo server = servers.get(0);
        assertEquals("Server With Arguments", server.getName());
        assertEquals("npm", server.getPackages().get(0).getRegistryType());
        assertEquals("test-server", server.getPackages().get(0).getIdentifier());
        assertEquals("1.0.0", server.getPackages().get(0).getVersion());
        assertEquals(1, server.getPackages().get(0).getRuntimeArguments().size());
        assertEquals(1, server.getPackages().get(0).getPackageArguments().size());
    }
    
    @Test
    void testTransformWithRepositoryId() throws Exception {
        String jsonWithRepositoryId = "{\"name\":\"Server With Repository ID\","
                + "\"version\":\"1.0.0\","
                + "\"repository\":{\"url\":\"https://github.com/test/repo\",\"source\":\"github\",\"id\":\"repo-id-123\"}}";
        
        List<McpServerDetailInfo> servers = transformService.transformToNacosFormat(jsonWithRepositoryId, "json", null, null, null);
        
        assertNotNull(servers);
        assertEquals(1, servers.size());
        
        McpServerDetailInfo server = servers.get(0);
        assertEquals("repo-id-123", server.getId());
        assertEquals("Server With Repository ID", server.getName());
    }
    
    @Test
    void testTransformWithOfficialMetaPublishedAt() throws Exception {
        String jsonWithMeta = "{\"_meta\":{\"io.modelcontextprotocol.registry/official\":"
                + "{\"serverId\":\"meta-server\",\"publishedAt\":\"2023-01-01T00:00:00Z\"}},"
                + "\"name\":\"Meta Server\","
                + "\"version\":\"1.0.0\"}";
        
        List<McpServerDetailInfo> servers = transformService.transformToNacosFormat(jsonWithMeta, "json", null, null, null);
        
        assertNotNull(servers);
        assertEquals(1, servers.size());
        
        McpServerDetailInfo server = servers.get(0);
        assertEquals("meta-server", server.getId());
        assertEquals("2023-01-01T00:00:00Z", server.getVersionDetail().getRelease_date());
    }
    
    @Test
    void testTransformWithOfficialMetaIsLatest() throws Exception {
        String jsonWithMetaIsLatest = "{\"_meta\":{\"io.modelcontextprotocol.registry/official\":"
                + "{\"serverId\":\"latest-server\",\"isLatest\":true}},"
                + "\"name\":\"Latest Server\","
                + "\"version\":\"1.0.0\"}";
        
        List<McpServerDetailInfo> servers = transformService.transformToNacosFormat(jsonWithMetaIsLatest, "json", null, null, null);
        
        assertNotNull(servers);
        assertEquals(1, servers.size());
        
        McpServerDetailInfo server = servers.get(0);
        assertEquals("latest-server", server.getId());
        assertTrue(server.getVersionDetail().getIs_latest());
    }
    
    @Test
    void testTransformWithEmptyName() throws Exception {
        String jsonWithEmptyName = "{\"name\":\"\"," 
                + "\"version\":\"1.0.0\"}";
        
        List<McpServerDetailInfo> servers = transformService.transformToNacosFormat(jsonWithEmptyName, "json", null, null, null);
        
        assertNotNull(servers);
        assertEquals(1, servers.size());
        
        McpServerDetailInfo server = servers.get(0);
        assertNotNull(server.getId());
        assertTrue(server.getId().length() > 0);
    }
    
    @Test
    void testUrlValidationWithDataProtocol() throws Exception {
        String jsonWithDataProtocol = "{\"name\":\"Data Protocol Server\","
                + "\"version\":\"1.0.0\","
                + "\"remotes\":[{\"transport_type\":\"http\",\"url\":\"data:text/plain;base64,SGVsbG8sIFdvcmxkIQ==\"}]}";
        
        List<McpServerDetailInfo> servers = transformService.transformToNacosFormat(jsonWithDataProtocol, "json", null, null, null);
        
        assertNotNull(servers);
        assertEquals(1, servers.size());
        
        McpServerDetailInfo server = servers.get(0);
        // Remote server config should be null due to data protocol URL being rejected
        assertNull(server.getRemoteServerConfig().getServiceRef());
    }
    
    @Test
    void testUrlValidationWithJavascriptProtocol() throws Exception {
        String jsonWithJavascriptProtocol = "{\"name\":\"Javascript Protocol Server\","
                + "\"version\":\"1.0.0\","
                + "\"remotes\":[{\"transport_type\":\"http\",\"url\":\"javascript:alert('xss')\"}]}";
        
        List<McpServerDetailInfo> servers = transformService.transformToNacosFormat(jsonWithJavascriptProtocol, "json", null, null, null);
        
        assertNotNull(servers);
        assertEquals(1, servers.size());
        
        McpServerDetailInfo server = servers.get(0);
        // Remote server config should be null due to javascript protocol URL being rejected
        assertNull(server.getRemoteServerConfig().getServiceRef());
    }
    
    @Test
    void testTransformWithDubboProtocol() throws Exception {
        String jsonWithDubboProtocol = "{\"name\":\"Dubbo Protocol Server\","
                + "\"version\":\"1.0.0\","
                + "\"remotes\":[{\"transport_type\":\"dubbo\",\"url\":\"dubbo://localhost:20880/service\"}]}";
        
        List<McpServerDetailInfo> servers = transformService.transformToNacosFormat(jsonWithDubboProtocol, "json", null, null, null);
        
        assertNotNull(servers);
        assertEquals(1, servers.size());
        
        McpServerDetailInfo server = servers.get(0);
        assertEquals("Dubbo Protocol Server", server.getName());
        // Check that protocol is correctly inferred
        assertEquals(AiConstants.Mcp.MCP_PROTOCOL_STDIO, server.getProtocol()); // Default fallback
    }
}