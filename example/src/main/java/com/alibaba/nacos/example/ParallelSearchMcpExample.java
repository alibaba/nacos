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

package com.alibaba.nacos.example;

import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.ai.AiFactory;
import com.alibaba.nacos.api.ai.AiService;
import com.alibaba.nacos.api.ai.McpService;
import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.api.ai.model.mcp.McpEndpointInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpEndpointSpec;
import com.alibaba.nacos.api.ai.model.mcp.McpServerBasicInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpServerDetailInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpServerRemoteServiceConfig;
import com.alibaba.nacos.api.ai.model.mcp.registry.ServerVersionDetail;
import com.alibaba.nacos.common.utils.VersionUtils;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;

import java.io.PrintStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;

/**
 * Register and discover Parallel Search MCP through Nacos, then search and fetch a public page.
 */
public class ParallelSearchMcpExample {
    
    private static final String REGISTRATION_VERSION = "1.0.0";
    
    private static final URI PARALLEL_ENDPOINT = URI.create("https://search.parallel.ai/mcp");
    
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(60);
    
    static final String USER_AGENT = "alibaba/nacos"
        + (VersionUtils.version == null ? "" : "/" + VersionUtils.version);
    
    public static void main(String[] args) throws Exception {
        if (args.length > 2) {
            throw new IllegalArgumentException(
                "Usage: ParallelSearchMcpExample [serverAddr] [mcpName]");
        }
        Properties properties = new Properties();
        properties.setProperty(PropertyKeyConst.SERVER_ADDR,
            args.length > 0 ? args[0] : "localhost:8848");
        properties.setProperty(PropertyKeyConst.NAMESPACE,
            System.getenv().getOrDefault("NACOS_NAMESPACE", "public"));
        setCredential(properties, PropertyKeyConst.USERNAME, "NACOS_USERNAME");
        setCredential(properties, PropertyKeyConst.PASSWORD, "NACOS_PASSWORD");
        String name = args.length > 1 ? args[1] : "parallel-search-example";
        AiService service = AiFactory.createAiService(properties);
        try {
            run(service.mcp(), name, System.out);
        } finally {
            service.shutdown();
        }
    }
    
    private static void setCredential(Properties properties, String key, String environmentName) {
        String value = System.getenv(environmentName);
        if (value != null && !value.isBlank()) {
            properties.setProperty(key, value);
        }
    }
    
    static void run(McpService service, String name, PrintStream output) throws Exception {
        McpServerBasicInfo specification = new McpServerBasicInfo();
        specification.setName(name);
        specification.setDescription("Parallel Search MCP registration and discovery example");
        specification.setProtocol(AiConstants.Mcp.MCP_PROTOCOL_STREAMABLE);
        specification.setFrontProtocol(AiConstants.Mcp.MCP_PROTOCOL_STREAMABLE);
        ServerVersionDetail version = new ServerVersionDetail();
        version.setVersion(REGISTRATION_VERSION);
        specification.setVersionDetail(version);
        McpServerRemoteServiceConfig remote = new McpServerRemoteServiceConfig();
        remote.setExportPath(PARALLEL_ENDPOINT.getPath());
        specification.setRemoteServerConfig(remote);
        McpEndpointSpec endpoint = new McpEndpointSpec();
        endpoint.setType(AiConstants.Mcp.MCP_ENDPOINT_TYPE_DIRECT);
        endpoint.setData(Map.of("address", PARALLEL_ENDPOINT.getHost(), "port", "443",
            "transportProtocol", PARALLEL_ENDPOINT.getScheme()));
        service.releaseMcpServer(specification, null, endpoint);
        output.println("Registered " + name + " version " + REGISTRATION_VERSION);
        
        URI discovered = discoverEndpoint(service, name);
        output.println("Discovered endpoint: " + discovered);
        // The connection uses Nacos discovery, not the registration constant.
        try (McpSyncClient client = createClient(discovered)) {
            client.initialize();
            Set<String> toolNames = new HashSet<>();
            String cursor = null;
            do {
                McpSchema.ListToolsResult tools = client.listTools(cursor);
                tools.tools().forEach(tool -> toolNames.add(tool.name()));
                cursor = tools.nextCursor();
            } while (cursor != null && !cursor.isEmpty());
            if (!toolNames.containsAll(List.of("web_search", "web_fetch"))) {
                throw new IllegalStateException("Expected web_search and web_fetch: " + toolNames);
            }
            output.println("Discovered tools: web_search, web_fetch");
            String sessionId = UUID.randomUUID().toString();
            printResult(output, "web_search", client.callTool(new McpSchema.CallToolRequest(
                "web_search",
                Map.of("objective", "Find the official Nacos MCP registry documentation",
                    "search_queries", List.of("Nacos MCP registry documentation"),
                    "session_id", sessionId))));
            printResult(output, "web_fetch", client.callTool(new McpSchema.CallToolRequest(
                "web_fetch", Map.of("urls", List.of("https://nacos.io/en/"),
                    "objective", "Describe Nacos service discovery and MCP registry capabilities",
                    "session_id", sessionId))));
        }
    }
    
    static URI discoverEndpoint(McpService service, String name) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        do {
            McpServerDetailInfo detail = service.getMcpServer(name, REGISTRATION_VERSION);
            if (detail != null) {
                List<McpEndpointInfo> endpoints = detail.getFrontendEndpoints();
                if (endpoints == null || endpoints.isEmpty()) {
                    endpoints = detail.getBackendEndpoints();
                }
                if (endpoints != null && !endpoints.isEmpty()) {
                    return endpointUri(endpoints.get(0));
                }
            }
            Thread.sleep(200);
        } while (System.nanoTime() < deadline);
        throw new IllegalStateException("Nacos did not return an endpoint for " + name);
    }
    
    static URI endpointUri(McpEndpointInfo endpoint) throws URISyntaxException {
        if (!"https".equals(endpoint.getProtocol()) && !"http".equals(endpoint.getProtocol())) {
            throw new IllegalArgumentException("Expected an HTTP or HTTPS endpoint from Nacos");
        }
        if (endpoint.getAddress() == null || endpoint.getAddress().isBlank()
            || endpoint.getPort() < 1 || endpoint.getPort() > 65535
            || endpoint.getPath() == null || !endpoint.getPath().startsWith("/")
            || endpoint.getPath().startsWith("//")) {
            throw new IllegalArgumentException("Incomplete MCP endpoint from Nacos");
        }
        return new URI(endpoint.getProtocol(), null, endpoint.getAddress(), endpoint.getPort(),
            endpoint.getPath(), null, null);
    }
    
    static McpSyncClient createClient(URI endpoint) {
        HttpClientStreamableHttpTransport transport = HttpClientStreamableHttpTransport
            .builder(endpoint.getScheme() + "://" + endpoint.getRawAuthority())
            .endpoint(endpoint.getRawPath())
            .connectTimeout(Duration.ofSeconds(10))
            .customizeClient(builder -> builder.followRedirects(HttpClient.Redirect.NEVER))
            // Identify aggregate Nacos usage; never add user or installation identifiers.
            .customizeRequest(request -> request.header("User-Agent", USER_AGENT)
                .timeout(REQUEST_TIMEOUT))
            .build();
        return McpClient.sync(transport).requestTimeout(REQUEST_TIMEOUT).build();
    }
    
    static void printResult(PrintStream output, String tool, McpSchema.CallToolResult result) {
        if (Boolean.TRUE.equals(result.isError())) {
            throw new IllegalStateException(tool + " failed: " + result.content());
        }
        output.println(tool + ":");
        // Print the text representation once, preserving citations, warnings and per-URL errors.
        boolean printed = false;
        for (McpSchema.Content content : result.content()) {
            if (content instanceof McpSchema.TextContent text) {
                output.println(text.text());
                printed = true;
            }
        }
        if (!printed) {
            throw new IllegalStateException(tool + " returned no text content");
        }
    }
}
