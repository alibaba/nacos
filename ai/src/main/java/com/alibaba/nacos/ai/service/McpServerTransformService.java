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
import com.alibaba.nacos.api.ai.model.mcp.FrontEndpointConfig;
import com.alibaba.nacos.api.ai.model.mcp.McpServerDetailInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpServerRemoteServiceConfig;
import com.alibaba.nacos.api.ai.model.mcp.registry.McpRegistryServer;
import com.alibaba.nacos.api.ai.model.mcp.registry.McpRegistryServerDetail;
import com.alibaba.nacos.api.ai.model.mcp.registry.McpRegistryServerList;
import com.alibaba.nacos.api.ai.model.mcp.registry.Package;
import com.alibaba.nacos.api.ai.model.mcp.registry.Argument;
import com.alibaba.nacos.api.ai.model.mcp.registry.NamedArgument;
import com.alibaba.nacos.api.ai.model.mcp.registry.PositionalArgument;
import com.alibaba.nacos.api.ai.model.mcp.registry.Remote;
import com.alibaba.nacos.api.ai.model.mcp.registry.ServerVersionDetail;
import com.alibaba.nacos.api.ai.model.mcp.registry.Meta;
import com.alibaba.nacos.api.ai.model.mcp.registry.OfficialMeta;
import com.alibaba.nacos.common.utils.StringUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * MCP Server Transform Service.
 *
 * <p>
 *     TODO: It can be abstract a {@code McpServerParser} to handle the different parse logic.
 *     It's better to use a common interface to handle the different parse logic.
 * </p>
 *
 * @author nacos
 */
@Service
public class McpServerTransformService {
    
    private static final String SERVERS_FIELD = "servers";

    private static final String HTTP_PREFIX = "http://";
    
    private static final String HTTPS_PREFIX = "https://";
    
    private static final String PROTOCOL_JAVASCRIPT = "javascript:";
    
    private static final String PROTOCOL_DATA = "data:";
    
    private static final String PROTOCOL_FILE = "file:";

    private static final String METADATA_FIELD = "metadata";

    private static final String NEXT_CURSOR_FIELD = "next_cursor";

    private static final String CURSOR_QUERY_NAME = "cursor";

    private static final String LIMIT_QUERY_NAME = "limit";

    private static final String SEARCH_QUERY_NAME = "search";

    private static final String HEADER_ACCEPT = "Accept";

    private static final String HEADER_AUTHORIZATION = "Authorization";

    private static final String HEADER_ACCEPT_JSON = "application/json";

    private static final String QUERY_MARK = "?";

    private static final String AMPERSAND = "&";

    private static final String AUTH_SCHEME_BEARER = "Bearer ";

    // Use official transport constants defined in AiConstants.Mcp

    private static final int HTTP_STATUS_SUCCESS_MIN = 200;

    private static final int HTTP_STATUS_SUCCESS_MAX = 299;

    private static final int CONNECT_TIMEOUT_SECONDS = 10;

    private static final int READ_TIMEOUT_SECONDS = 20;

    /**
     * Safety guard to avoid infinite loops when server keeps returning cursors.
     * Limits the maximum number of pages iterated when fetching from URL.
     */
    private static final int MAX_PAGES_GUARD = 200;

    private final ObjectMapper objectMapper = new ObjectMapper();
    
    /**
     * Holder for a single URL page fetch.
     */
    public static class UrlPageResult {

        private final List<McpServerDetailInfo> servers;

        private final String nextCursor;

        public UrlPageResult(List<McpServerDetailInfo> servers, String nextCursor) {
            this.servers = servers;
            this.nextCursor = nextCursor;
        }

        public List<McpServerDetailInfo> getServers() {
            return servers;
        }

        public String getNextCursor() {
            return nextCursor;
        }
    }

    /**
     * Import type enum to avoid string-based switches.
     */
    private enum ImportType {
        /** Import from local file content. */
        FILE,
        /** Import from a JSON string. */
        JSON,
        /** Import from a remote URL. */
        URL;

        static ImportType from(String s) {
            if (s == null) {
                throw new IllegalArgumentException("Unsupported import type: null");
            }
            switch (s.trim().toLowerCase()) {
                case "file":
                    return FILE;
                case "json":
                    return JSON;
                case "url":
                    return URL;
                default:
                    throw new IllegalArgumentException("Unsupported import type: " + s);
            }
        }
    }

    /**
     * Transform with optional URL pagination parameters and search keyword.
     * Only effective when importType equals to 'url'.
     */
    public List<McpServerDetailInfo> transformToNacosFormat(String importData, String importType,
            String cursor, Integer limit, String search) throws Exception {
        return transformToNacosFormat(importData, ImportType.from(importType), cursor, limit, search);
    }
    
    /**
     * Enum-based dispatcher with pagination and search support.
     */
    public List<McpServerDetailInfo> transformToNacosFormat(String importData, ImportType type,
            String cursor, Integer limit, String search) throws Exception {
        List<McpServerDetailInfo> servers;
        switch (type) {
            case FILE:
                servers = parseFileToNacosServers(importData);
                break;
            case JSON:
                servers = parseJsonToNacosServers(importData);
                break;
            case URL:
                servers = parseUrlData(importData, cursor, limit, search);
                break;
            default:
                throw new IllegalArgumentException("Unsupported import type: " + type);
        }

        // Generate IDs for servers without IDs
        servers.forEach(server -> {
            if (StringUtils.isBlank(server.getId())) {
                server.setId(generateServerId(server.getName()));
            }
        });
        return servers;
    }
    
    /**
     * Fetch one registry page with optional search keyword.
     */
    public UrlPageResult fetchUrlPage(String urlData, String cursor, Integer limit, String search) throws Exception {
        if (StringUtils.isBlank(urlData)) {
            throw new IllegalArgumentException("URL is blank");
        }

        String base = urlData.trim();
        HttpClient client = createHttpClient();
        String bearer = resolveBearerToken();

        String pageUrl = buildPageUrl(base, cursor, limit, search);
        HttpRequest request = buildGetRequest(pageUrl, bearer);
        HttpResponse<String> resp = client.send(request, HttpResponse.BodyHandlers.ofString());
        int code = resp.statusCode();
        if (!isSuccessStatus(code)) {
            throw new IllegalStateException("HTTP " + code + " when fetching " + pageUrl);
        }

        List<McpServerDetailInfo> servers = new ArrayList<>();
        String next = null;

        try {
            // Prefer typed parse for servers array when possible
            McpRegistryServerList listPage = objectMapper.readValue(resp.body(), McpRegistryServerList.class);
            if (listPage != null && listPage.getServers() != null) {
                for (McpRegistryServer registryServer : listPage.getServers()) {
                    McpServerDetailInfo server = transformRegistryServerToNacos(registryServer);
                    if (server != null) {
                        servers.add(server);
                    }
                }
            }
        } catch (Exception ignore) {
            throw new IllegalStateException("Failed to parse response body", ignore);
        }

        // Always parse as tree to extract next cursor and ensure servers on fallback
        JsonNode root = objectMapper.readTree(resp.body());
        if (servers.isEmpty()) {
            appendServersFromRoot(root, servers);
        }
        next = extractNextCursor(root);

        // Ensure IDs
        servers.forEach(s -> {
            if (StringUtils.isBlank(s.getId())) {
                s.setId(generateServerId(s.getName()));
            }
        });

        return new UrlPageResult(servers, next);
    }
    
    /**
     * Iterate URL pages with search and collect all servers until no more pages or
     * guard limit.
     */
    public List<McpServerDetailInfo> fetchUrlServersAll(String urlData, String search) throws Exception {
        List<McpServerDetailInfo> collected = new ArrayList<>();
        String cursor = null;
        int pages = 0;
        while (pages < MAX_PAGES_GUARD) {
            pages++;
            UrlPageResult page = fetchUrlPage(urlData, cursor, 30, search);
            if (page.getServers() != null && !page.getServers().isEmpty()) {
                collected.addAll(page.getServers());
            }
            String next = page.getNextCursor();
            if (next == null) {
                break;
            }
            cursor = next;
        }
        return collected;
    }

    /**
     * Transform MCP Registry Server to Nacos MCP Server format.
     *
     * @param registryServer MCP Registry Server
     * @return Nacos MCP Server Detail Info
     */
    private McpServerDetailInfo transformRegistryServerToNacos(McpRegistryServer registryServer) {
        if (registryServer == null) {
            return null;
        }
        
        try {
            McpServerDetailInfo server = new McpServerDetailInfo();

            // 1) Basic information
            fillBasicInfo(registryServer, server);

            // 2) Version details
            ServerVersionDetail versionDetail = buildVersion(registryServer);
            if (versionDetail != null) {
                server.setVersionDetail(versionDetail);
            }

            // 2.1) Early protocol inference
            String earlyProtocol = inferServerProtocol(registryServer);
            if (StringUtils.isNotBlank(earlyProtocol)) {
                server.setProtocol(earlyProtocol);
                server.setFrontProtocol(earlyProtocol);
            }

            // 3) Package config
            if (registryServer instanceof McpRegistryServerDetail) {
                applyPackageConfigIfAny((McpRegistryServerDetail) registryServer, server);
            }

            // 4) Remote config
            if (registryServer instanceof McpRegistryServerDetail) {
                applyRemoteConfigIfAny((McpRegistryServerDetail) registryServer, server);
            }

            // 5) Protocol fallback
            applyDefaultProtocolIfMissing(server);

            return server;
        } catch (Exception e) {
            // Log error and skip invalid server
            return null;
        }
    }
    
    /**
     * Fill basic info: id/name/description/repository.
     */
    private void fillBasicInfo(McpRegistryServer registryServer, McpServerDetailInfo out) {
        String id = resolveRegistryId(registryServer);
        out.setId(id);
        out.setName(registryServer.getName());
        out.setDescription(registryServer.getDescription());
        // Map status from registry to nacos model (default to active)
        out.setStatus(registryServer.getStatus());
        if (registryServer.getRepository() != null) {
            out.setRepository(registryServer.getRepository());
        }
    }

    /**
     * Derive registry id from _meta.official.id or repository.id.
     */
    private String resolveRegistryId(McpRegistryServer registryServer) {
        if (registryServer instanceof McpRegistryServerDetail) {
            McpRegistryServerDetail detail = (McpRegistryServerDetail) registryServer;
            Meta meta = detail.getMeta();
            if (meta != null && meta.getOfficial() != null) {
                String serverId = meta.getOfficial().getServerId();
                if (StringUtils.isNotBlank(serverId)) {
                    return serverId;
                }
            }
        }
        return registryServer.getRepository() != null ? registryServer.getRepository().getId() : null;
    }

    /**
     * Build version detail: version, release_date, is_latest.
     */
    private ServerVersionDetail buildVersion(McpRegistryServer registryServer) {
        ServerVersionDetail v = null;
        if (StringUtils.isNotBlank(registryServer.getVersion())) {
            v = new ServerVersionDetail();
            v.setVersion(registryServer.getVersion());
        }
        if (registryServer instanceof McpRegistryServerDetail) {
            McpRegistryServerDetail detail = (McpRegistryServerDetail) registryServer;
            Meta meta = detail.getMeta();
            OfficialMeta official = meta != null ? meta.getOfficial() : null;
            if (v == null) {
                v = new ServerVersionDetail();
            }
            String release = official != null ? official.getPublishedAt() : null;
            if (StringUtils.isNotBlank(release)) {
                v.setRelease_date(release);
            }
            if (official != null && official.getIsLatest() != null) {
                v.setIs_latest(official.getIsLatest());
            }
        }
        return v;
    }

    /**
     * If package is declared, build remote service config from package (no protocol
     * inference here).
     */
    private void applyPackageConfigIfAny(McpRegistryServerDetail detail, McpServerDetailInfo out) {
        if (detail.getPackages() == null || detail.getPackages().isEmpty()) {
            return;
        }
        out.setPackages(detail.getPackages());
    }

    /**
     * If remote exists, and package did not produce a remote config, build remote
     * service config (no protocol inference).
     */
    private void applyRemoteConfigIfAny(McpRegistryServerDetail detail, McpServerDetailInfo out) {
        if (out.getRemoteServerConfig() != null) {
            return;
        }
        if (detail.getRemotes() == null || detail.getRemotes().isEmpty()) {
            return;
        }
        String protocol = resolveProtocolOrDefault(out);
        McpServerRemoteServiceConfig remoteConfig = new McpServerRemoteServiceConfig();
        configureRemoteService(remoteConfig, detail.getRemotes(), protocol);
        out.setRemoteServerConfig(remoteConfig);
    }

    /**
     * Resolve current protocol or return stdio as default for building remote
     * config.
     */
    private String resolveProtocolOrDefault(McpServerDetailInfo out) {
        return StringUtils.isNotBlank(out.getProtocol()) ? out.getProtocol() : AiConstants.Mcp.MCP_PROTOCOL_STDIO;
    }

    /**
     * Early protocol inference after version detail:
     * - If packages exist, return stdio.
     * - Else, from first remote.transportType decide sse/streamable.
     * - Otherwise, return null (later fallback will apply).
     */
    private String inferServerProtocol(McpRegistryServer registryServer) {
        if (!(registryServer instanceof McpRegistryServerDetail)) {
            return null;
        }
        McpRegistryServerDetail detail = (McpRegistryServerDetail) registryServer;

        // Prefer stdio when packages exist
        if (detail.getPackages() != null && !detail.getPackages().isEmpty()) {
            return AiConstants.Mcp.MCP_PROTOCOL_STDIO;
        }

        // Without packages, try transportType from the first remote
        if (detail.getRemotes() != null && !detail.getRemotes().isEmpty()) {
            Remote first = detail.getRemotes().get(0);
            String tt = first != null ? first.getType() : null;
            if (tt != null) {
                String lower = tt.trim().toLowerCase();
                if (AiConstants.Mcp.OFFICIAL_TRANSPORT_SSE.equals(lower)) {
                    return AiConstants.Mcp.MCP_PROTOCOL_SSE;
                }
                if (AiConstants.Mcp.OFFICIAL_TRANSPORT_STREAMABLE.equals(lower)) {
                    return AiConstants.Mcp.MCP_PROTOCOL_STREAMABLE;
                }
            }
        }
        return null;
    }

    /**
     * If protocol is not set, default to stdio.
     */
    private void applyDefaultProtocolIfMissing(McpServerDetailInfo out) {
        if (StringUtils.isBlank(out.getProtocol())) {
            out.setProtocol(AiConstants.Mcp.MCP_PROTOCOL_STDIO);
            out.setFrontProtocol(AiConstants.Mcp.MCP_PROTOCOL_STDIO);
        }
    }

    /**
     * Configure remote service based on protocol and remote info.
     *
     * @param remoteConfig Remote service config to configure
     * @param remotes      Remote information
     * @param protocol     Protocol type
     */
    private void configureRemoteService(McpServerRemoteServiceConfig remoteConfig, List<Remote> remotes, String protocol) {
        if (remoteConfig == null || remotes == null || remotes.isEmpty()) {
            return;
        }

        List<FrontEndpointConfig> endpoints = new ArrayList<>();
        for (Remote remote : remotes) {
            if (remote == null || StringUtils.isBlank(remote.getUrl())) {
                continue;
            }

            String url = remote.getUrl().trim();
            // Only accept http(s) URLs for front endpoint mapping
            if (!isValidUrl(url, AiConstants.Mcp.MCP_PROTOCOL_HTTP)) {
                continue;
            }

            try {
                URI uri = URI.create(url);
                String scheme = uri.getScheme();
                String host = uri.getHost();
                int port = uri.getPort();
                String path = uri.getRawPath() + (uri.getRawQuery() != null ? "?" + uri.getRawQuery() : "")
                        + (uri.getRawFragment() != null ? "#" + uri.getRawFragment() : "");

                if (StringUtils.isBlank(host)) {
                    // Malformed URL; skip
                    continue;
                }

                boolean isHttps = "https".equalsIgnoreCase(scheme);
                int effectivePort = (port > 0) ? port : (isHttps ? 443 : 80);
                String endpointData = host + ":" + effectivePort;

                remoteConfig.setExportPath(path);

                FrontEndpointConfig cfg = new FrontEndpointConfig();
                cfg.setEndpointData(endpointData);
                cfg.setPath(StringUtils.isNotBlank(path) ? path : "/");
                cfg.setType(remote.getType());
                cfg.setProtocol(isHttps ? "https" : AiConstants.Mcp.MCP_PROTOCOL_HTTP);
                cfg.setEndpointType(AiConstants.Mcp.MCP_ENDPOINT_TYPE_DIRECT);
                cfg.setHeaders(remote.getHeaders());

                endpoints.add(cfg);
            } catch (Exception e) {
                throw new IllegalStateException("Invalid URL: " + url, e);
            }
        }

        if (!endpoints.isEmpty()) {
            remoteConfig.setFrontEndpointConfigList(endpoints);
        }
    }
    
    /**
     * Map registry transport type to Nacos MCP front type constants.
     */
    @SuppressWarnings("unused")
    private String mapTransportType(String transportType, String fallback) {
        String t = transportType == null ? null : transportType.trim().toLowerCase();
        if (AiConstants.Mcp.OFFICIAL_TRANSPORT_SSE.equals(t) || AiConstants.Mcp.MCP_PROTOCOL_SSE.equalsIgnoreCase(transportType)) {
            return AiConstants.Mcp.MCP_PROTOCOL_SSE;
        }
        if (AiConstants.Mcp.OFFICIAL_TRANSPORT_STREAMABLE.equals(t)
                || AiConstants.Mcp.MCP_PROTOCOL_STREAMABLE.equalsIgnoreCase(transportType)) {
            return AiConstants.Mcp.MCP_PROTOCOL_STREAMABLE;
        }
        // Fallback: use provided protocol if it's a known front type; else default SSE
        if (AiConstants.Mcp.MCP_PROTOCOL_SSE.equalsIgnoreCase(fallback)) {
            return AiConstants.Mcp.MCP_PROTOCOL_SSE;
        }
        if (AiConstants.Mcp.MCP_PROTOCOL_STREAMABLE.equalsIgnoreCase(fallback)) {
            return AiConstants.Mcp.MCP_PROTOCOL_STREAMABLE;
        }
        return AiConstants.Mcp.MCP_PROTOCOL_SSE;
    }
    
    /**
     * Configure remote service from package information.
     *
     * @param remoteConfig Remote service config to configure
     * @param mcpPackage   MCP package information
     * @param protocol     Protocol type
     */
    @SuppressWarnings("unused")
    private void configureRemoteServiceFromPackage(McpServerRemoteServiceConfig remoteConfig,
            Package mcpPackage, String protocol) {
        if (mcpPackage == null || remoteConfig == null) {
            return;
        }

        String registryName = mcpPackage.getRegistryType();
        String packageName = mcpPackage.getIdentifier();

        if (StringUtils.isNotBlank(registryName) && StringUtils.isNotBlank(packageName)) {
            String command = buildPackageCommand(registryName, packageName, mcpPackage);
            remoteConfig.setExportPath(command);
        }
    }
    
    /**
     * Build command from package information.
     *
     * @param registryName Package registry name
     * @param packageName  Package name
     * @param mcpPackage   Package details
     * @return command string
     */
    private String buildPackageCommand(String registryName, String packageName, Package mcpPackage) {
        StringBuilder command = new StringBuilder();

        // Prefer runtime_hint if present (e.g., npx, uvx, dnx)
        String runtimeHint = null;
        try {
            runtimeHint = mcpPackage.getRuntimeHint();
        } catch (Throwable ignore) {
            // field may not exist on older model versions
        }

        if (StringUtils.isNotBlank(runtimeHint)) {
            command.append(runtimeHint).append(" ");
            command.append(packageName);
        } else {
            switch (registryName.toLowerCase()) {
                case "npm":
                    command.append("npx ").append(packageName);
                    break;
                case "pypi":
                    command.append("python -m ").append(packageName);
                    break;
                case "oci":
                case "docker":
                    // Heuristic: use docker run-like invocation; exact flags are registry-specific
                    command.append("docker run ").append(packageName);
                    break;
                default:
                    command.append(packageName);
                    break;
            }
        }

        // Add runtime arguments if available
        try {
            if (mcpPackage.getRuntimeArguments() != null) {
                for (Argument arg : mcpPackage.getRuntimeArguments()) {
                    String argValue = extractArgumentValue(arg);
                    if (StringUtils.isNotBlank(argValue)) {
                        command.append(" ").append(argValue);
                    }
                }
            }
        } catch (Throwable ignore) {
            // runtimeArguments may not exist on older model versions
        }

        // Add package arguments if available
        if (mcpPackage.getPackageArguments() != null) {
            for (Argument arg : mcpPackage.getPackageArguments()) {
                String argValue = extractArgumentValue(arg);
                if (StringUtils.isNotBlank(argValue)) {
                    command.append(" ").append(argValue);
                }
            }
        }
        
        return command.toString();
    }
    
    /**
     * Extract argument value from polymorphic Argument interface.
     * 
     * @param arg Argument instance (either NamedArgument or PositionalArgument)
     * @return argument value
     */
    private String extractArgumentValue(Argument arg) {
        if (arg instanceof NamedArgument) {
            NamedArgument namedArg = (NamedArgument) arg;
            return namedArg.getValue();
        } else if (arg instanceof PositionalArgument) {
            PositionalArgument positionalArg = (PositionalArgument) arg;
            return positionalArg.getValue();
        }
        return null;
    }
    
    /**
     * Parse URL data with optional search keyword and pagination.
     */
    private List<McpServerDetailInfo> parseUrlData(String urlData, String cursor, Integer limit, String search)
            throws Exception {
        if (StringUtils.isBlank(urlData)) {
            throw new IllegalArgumentException("URL is blank");
        }

        // limit == -1 means fetch all pages
        if (limit != null && limit == -1) {
            // fetch all pages with search filtering if provided
            return fetchUrlServersAll(urlData.trim(), search);
        }

        // Otherwise, fetch a single page using fetchUrlPage
        UrlPageResult page = fetchUrlPage(urlData.trim(), cursor, limit, search);
        List<McpServerDetailInfo> result = page.getServers();
        return (result != null) ? result : new ArrayList<>();
    }

    /**
     * File import wrapper: parse into a list of RegistryDetails and convert to
     * Nacos servers.
     */
    private List<McpServerDetailInfo> parseFileToNacosServers(String data) throws Exception {
        List<McpServerDetailInfo> out = new ArrayList<>();
        List<McpRegistryServerDetail> details = parseFileToRegistryDetails(data);
        for (McpRegistryServerDetail d : details) {
            McpServerDetailInfo info = transformRegistryServerToNacos(d);
            if (info != null) {
                out.add(info);
            }
        }
        return out;
    }

    /**
     * JSON import wrapper: parse into a single RegistryDetail and convert to a
     * Nacos server list.
     */
    private List<McpServerDetailInfo> parseJsonToNacosServers(String data) throws Exception {
        List<McpServerDetailInfo> out = new ArrayList<>();
        McpRegistryServerDetail detail = parseJsonToRegistryDetail(data);
        McpServerDetailInfo info = transformRegistryServerToNacos(detail);
        if (info != null) {
            out.add(info);
        }
        return out;
    }

    /**
     * File import: parse into a list of McpRegistryServerDetail.
     */
    private List<McpRegistryServerDetail> parseFileToRegistryDetails(String data) throws Exception {
        List<McpRegistryServerDetail> out = new ArrayList<>();
        JsonNode root = objectMapper.readTree(data);
        if (root.has(SERVERS_FIELD)) {
            JsonNode serversNode = root.get(SERVERS_FIELD);
            if (serversNode.isArray()) {
                for (JsonNode s : serversNode) {
                    McpRegistryServerDetail d = objectMapper.treeToValue(s, McpRegistryServerDetail.class);
                    if (d != null) {
                        out.add(d);
                    }
                }
            }
        } else if (root.isArray()) {
            for (JsonNode s : root) {
                McpRegistryServerDetail d = objectMapper.treeToValue(s, McpRegistryServerDetail.class);
                if (d != null) {
                    out.add(d);
                }
            }
        } else {
            // Also support single object: wrap into a list
            McpRegistryServerDetail d = objectMapper.treeToValue(root, McpRegistryServerDetail.class);
            if (d != null) {
                out.add(d);
            }
        }
        return out;
    }
    
    /**
     * JSON import: parse into a single McpRegistryServerDetail object.
     */
    private McpRegistryServerDetail parseJsonToRegistryDetail(String data) throws Exception {
        JsonNode root = objectMapper.readTree(data);
        if (root.has(SERVERS_FIELD)) {
            JsonNode serversNode = root.get(SERVERS_FIELD);
            if (serversNode.isArray() && serversNode.size() > 0) {
                return objectMapper.treeToValue(serversNode.get(0), McpRegistryServerDetail.class);
            }
            throw new IllegalArgumentException("Invalid json import: 'servers' is not an array or empty");
        }
        if (root.isArray()) {
            if (root.size() == 0) {
                throw new IllegalArgumentException("Invalid json import: empty array");
            }
            return objectMapper.treeToValue(root.get(0), McpRegistryServerDetail.class);
        }
        return objectMapper.treeToValue(root, McpRegistryServerDetail.class);
    }

    private HttpClient createHttpClient() {
        return HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(CONNECT_TIMEOUT_SECONDS))
                .build();
    }

    private String resolveBearerToken() {
        String bearer = System.getProperty("nacos.mcp.registry.token");
        if (StringUtils.isBlank(bearer)) {
            bearer = System.getenv("MCP_REGISTRY_TOKEN");
        }
        return StringUtils.isBlank(bearer) ? null : bearer.trim();
    }

    private String buildPageUrl(String base, String cursor, Integer limit, String search) {
        StringBuilder url = new StringBuilder(base);
        boolean hasQuery = base.contains(QUERY_MARK);
        if (StringUtils.isNotBlank(cursor)) {
            String enc = URLEncoder.encode(cursor, StandardCharsets.UTF_8);
            url.append(hasQuery ? AMPERSAND : QUERY_MARK).append(CURSOR_QUERY_NAME).append("=").append(enc);
            hasQuery = true;
        }
        if (limit != null && limit > 0) {
            url.append(hasQuery ? AMPERSAND : QUERY_MARK).append(LIMIT_QUERY_NAME).append("=").append(limit);
            hasQuery = true;
        }
        if (StringUtils.isNotBlank(search)) {
            String encSearch = URLEncoder.encode(search, StandardCharsets.UTF_8);
            url.append(hasQuery ? AMPERSAND : QUERY_MARK).append(SEARCH_QUERY_NAME).append("=").append(encSearch);
        }
        return url.toString();
    }

    private HttpRequest buildGetRequest(String url, String bearer) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(READ_TIMEOUT_SECONDS))
                .GET()
                .header(HEADER_ACCEPT, HEADER_ACCEPT_JSON);
        if (StringUtils.isNotBlank(bearer)) {
            builder.header(HEADER_AUTHORIZATION, AUTH_SCHEME_BEARER + bearer);
        }
        return builder.build();
    }

    private void appendServersFromRoot(JsonNode root, List<McpServerDetailInfo> out) {
        JsonNode serversNode = root.get(SERVERS_FIELD);
        if (serversNode != null && serversNode.isArray()) {
            for (JsonNode serverNode : serversNode) {
                try {
                    McpRegistryServer registryServer = objectMapper.treeToValue(serverNode, McpRegistryServer.class);
                    McpServerDetailInfo server = transformRegistryServerToNacos(registryServer);
                    if (server != null) {
                        out.add(server);
                    }
                } catch (Exception e) {
                    // skip invalid item
                }
            }
            return;
        }

        if (root.isArray()) {
            for (JsonNode serverNode : root) {
                try {
                    McpRegistryServer registryServer = objectMapper.treeToValue(serverNode, McpRegistryServer.class);
                    McpServerDetailInfo server = transformRegistryServerToNacos(registryServer);
                    if (server != null) {
                        out.add(server);
                    }
                } catch (Exception e) {
                    // skip invalid item
                }
            }
            return;
        }

        try {
            McpRegistryServer registryServer = objectMapper.treeToValue(root, McpRegistryServer.class);
            McpServerDetailInfo server = transformRegistryServerToNacos(registryServer);
            if (server != null) {
                out.add(server);
            }
        } catch (Exception ignore) {
            // not a server shape
        }
    }

    private String extractNextCursor(JsonNode root) {
        JsonNode metadata = root.get(METADATA_FIELD);
        if (metadata != null && metadata.isObject()) {
            JsonNode nextNode = metadata.get(NEXT_CURSOR_FIELD);
            if (nextNode != null && nextNode.isTextual()) {
                String next = nextNode.asText();
                return StringUtils.isBlank(next) ? null : next;
            }
        }
        return null;
    }

    private boolean isSuccessStatus(int code) {
        return code >= HTTP_STATUS_SUCCESS_MIN && code <= HTTP_STATUS_SUCCESS_MAX;
    }

    // removed: transformJsonNodeToServer/inferProtocolFromConfig/getTextValue

    /**
     * Generate server ID from name.
     *
     * @param name server name
     * @return generated ID
     */
    private String generateServerId(String name) {
        if (StringUtils.isBlank(name)) {
            return UUID.randomUUID().toString().replace("-", "");
        }
    
        // Use name-based ID with random suffix
        String baseId = name.toLowerCase().replaceAll("[^a-z0-9]", "");
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        return baseId + "-" + suffix;
    }
    
    /**
     * Validate URL security and validity based on protocol.
     *
     * @param url      URL to validate
     * @param protocol Protocol type
     * @return true if URL is valid
     */
    private boolean isValidUrl(String url, String protocol) {
        if (StringUtils.isBlank(url)) {
            return false;
        }
        
        // Basic security checks - prevent potential malicious URLs
        String lowerUrl = url.toLowerCase();
        if (lowerUrl.contains(PROTOCOL_JAVASCRIPT) || lowerUrl.contains(PROTOCOL_DATA)
                || lowerUrl.contains(PROTOCOL_FILE)) {
            return false;
        }
        
        switch (protocol) {
            case AiConstants.Mcp.MCP_PROTOCOL_HTTP:
                // HTTP protocol requires valid HTTP/HTTPS URLs
                return lowerUrl.startsWith(HTTP_PREFIX) || lowerUrl.startsWith(HTTPS_PREFIX);
            case AiConstants.Mcp.MCP_PROTOCOL_STDIO:
                // STDIO protocol allows commands and paths, more flexible validation
                return !lowerUrl.contains("..") && !lowerUrl.contains("&") && !lowerUrl.contains("|");
            case AiConstants.Mcp.MCP_PROTOCOL_DUBBO:
                // Dubbo protocol allows dubbo:// URLs or standard service URLs
                return lowerUrl.startsWith("dubbo://") || lowerUrl.startsWith(HTTP_PREFIX)
                        || lowerUrl.startsWith(HTTPS_PREFIX);
            default:
                // For unknown protocols, apply basic validation
                return !lowerUrl.contains("..") && !lowerUrl.contains(PROTOCOL_JAVASCRIPT)
                        && !lowerUrl.contains(PROTOCOL_DATA);
        }
    }
}