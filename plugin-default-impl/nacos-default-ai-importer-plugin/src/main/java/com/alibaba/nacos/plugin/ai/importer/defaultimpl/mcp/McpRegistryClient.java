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

package com.alibaba.nacos.plugin.ai.importer.defaultimpl.mcp;

import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.api.ai.model.mcp.FrontEndpointConfig;
import com.alibaba.nacos.api.ai.model.mcp.McpServerDetailInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpServerRemoteServiceConfig;
import com.alibaba.nacos.api.ai.model.mcp.registry.McpRegistryServerDetail;
import com.alibaba.nacos.api.ai.model.mcp.registry.McpRegistryServerList;
import com.alibaba.nacos.api.ai.model.mcp.registry.OfficialMeta;
import com.alibaba.nacos.api.ai.model.mcp.registry.Remote;
import com.alibaba.nacos.api.ai.model.mcp.registry.ServerResponse;
import com.alibaba.nacos.api.ai.model.mcp.registry.ServerVersionDetail;
import com.alibaba.nacos.common.utils.CollectionUtils;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.plugin.ai.importer.defaultimpl.http.DefaultImportHttpClient;
import com.alibaba.nacos.plugin.ai.importer.defaultimpl.http.ImportHttpResponse;
import com.alibaba.nacos.plugin.ai.importer.model.AiResourceImportSource;

import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Minimal MCP official registry client used by the default importer plugin.
 *
 * @author xiweng.yy
 * @since 3.2.1
 */
class McpRegistryClient {
    
    private static final String CURSOR_QUERY_NAME = "cursor";
    
    private static final String LIMIT_QUERY_NAME = "limit";
    
    private static final String SEARCH_QUERY_NAME = "search";
    
    private static final String HEADER_ACCEPT_JSON = "application/json";
    
    private static final String QUERY_MARK = "?";
    
    private static final String AMPERSAND = "&";
    
    private static final int HTTP_STATUS_SUCCESS_MIN = 200;
    
    private static final int HTTP_STATUS_SUCCESS_MAX = 299;
    
    private static final int READ_TIMEOUT_SECONDS = 20;
    
    private DefaultImportHttpClient httpClient;
    
    McpRegistryClient() {
        this(new DefaultImportHttpClient());
    }
    
    McpRegistryClient(HttpClient httpClient) {
        this(new DefaultImportHttpClient(httpClient));
    }
    
    McpRegistryClient(DefaultImportHttpClient httpClient) {
        this.httpClient = httpClient;
    }
    
    Page fetchOfficialRegistryPage(AiResourceImportSource source, String cursor, Integer limit,
        String search) throws Exception {
        if (source == null || StringUtils.isBlank(source.getEndpoint())) {
            throw new IllegalArgumentException("URL is blank");
        }
        return fetchUrlPage(source, source.getEndpoint().trim(), cursor, limit, search);
    }
    
    McpServerDetailInfo fetchOfficialRegistryServer(AiResourceImportSource source,
        String externalId, int limit) throws Exception {
        if (StringUtils.isBlank(externalId)) {
            throw new IllegalArgumentException("MCP server external id is blank");
        }
        int actualLimit = limit > 0 ? limit : 30;
        Page page = fetchOfficialRegistryPage(source, null, actualLimit, externalId);
        if (CollectionUtils.isNotEmpty(page.getServers())) {
            for (McpServerDetailInfo each : page.getServers()) {
                if (StringUtils.equals(externalId, each.getName())
                    || StringUtils.equals(externalId, each.getId())) {
                    return each;
                }
            }
        }
        throw new IllegalStateException("MCP server not found in registry: " + externalId);
    }
    
    private Page fetchUrlPage(AiResourceImportSource source, String urlData, String cursor,
        Integer limit, String search) throws Exception {
        String pageUrl = buildPageUrl(urlData.trim(), cursor, limit, search);
        ImportHttpResponse response = httpClient.get(source, pageUrl, READ_TIMEOUT_SECONDS,
            HEADER_ACCEPT_JSON);
        int code = response.getStatusCode();
        if (!isSuccessStatus(code)) {
            throw new IllegalStateException("HTTP " + code + " when fetching " + pageUrl);
        }
        try {
            McpRegistryServerList listPage =
                JacksonUtils.toObj(response.getBody(), McpRegistryServerList.class);
            List<McpServerDetailInfo> servers = Collections.emptyList();
            String next = null;
            if (listPage != null && listPage.getServers() != null) {
                servers = listPage.getServers().stream()
                    .map(this::adaptOfficialMcpServerFromResponse)
                    .collect(Collectors.toList());
            }
            if (listPage != null && listPage.getMetadata() != null) {
                next = listPage.getMetadata().getNextCursor();
            }
            return new Page(servers, next);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse response body", e);
        }
    }
    
    private McpServerDetailInfo adaptOfficialMcpServerFromResponse(ServerResponse response) {
        McpServerDetailInfo server = adaptOfficialMcpServer(response.getServer());
        ServerVersionDetail versionDetail = server.getVersionDetail();
        OfficialMeta official =
            response.getMeta() == null ? null : response.getMeta().getOfficial();
        if (versionDetail != null && official != null) {
            versionDetail.setRelease_date(official.getPublishedAt());
            versionDetail.setIs_latest(true);
            String status = official.getStatus();
            if (StringUtils.isNotBlank(status)) {
                server.setStatus(status);
            }
        }
        return server;
    }
    
    private McpServerDetailInfo adaptOfficialMcpServer(McpRegistryServerDetail registryServer) {
        if (registryServer == null) {
            return null;
        }
        McpServerDetailInfo server = new McpServerDetailInfo();
        applyBasicInfo(registryServer, server);
        applyVersionInfo(registryServer, server);
        applyProtocolInfo(registryServer, server);
        applyLocalAndRemoteConfig(registryServer, server);
        return server;
    }
    
    private void applyBasicInfo(McpRegistryServerDetail registryServer,
        McpServerDetailInfo out) {
        out.setId(generateMcpServerId(registryServer.getName()));
        out.setName(registryServer.getName());
        out.setDescription(registryServer.getDescription());
        out.setRepository(registryServer.getRepository());
    }
    
    private void applyVersionInfo(McpRegistryServerDetail registryServer,
        McpServerDetailInfo out) {
        ServerVersionDetail versionDetail = null;
        if (StringUtils.isNotBlank(registryServer.getVersion())) {
            versionDetail = new ServerVersionDetail();
            versionDetail.setVersion(registryServer.getVersion());
        }
        out.setVersionDetail(versionDetail);
    }
    
    private void applyProtocolInfo(McpRegistryServerDetail registryServer,
        McpServerDetailInfo out) {
        String protocol = resolveServerProtocol(registryServer);
        if (StringUtils.isNotBlank(protocol)) {
            out.setProtocol(protocol);
            out.setFrontProtocol(protocol);
        }
    }
    
    private void applyLocalAndRemoteConfig(McpRegistryServerDetail registryServer,
        McpServerDetailInfo server) {
        server.setPackages(registryServer.getPackages());
        server.setRemoteServerConfig(generateRemoteServiceConfig(registryServer.getRemotes()));
    }
    
    private String resolveServerProtocol(McpRegistryServerDetail detail) {
        if (CollectionUtils.isNotEmpty(detail.getPackages())) {
            return AiConstants.Mcp.MCP_PROTOCOL_STDIO;
        }
        if (CollectionUtils.isNotEmpty(detail.getRemotes())) {
            Remote first = detail.getRemotes().get(0);
            String type = first == null ? null : first.getType();
            if (type != null) {
                String lower = type.trim().toLowerCase();
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
    
    private McpServerRemoteServiceConfig generateRemoteServiceConfig(List<Remote> remotes) {
        if (CollectionUtils.isEmpty(remotes)) {
            return null;
        }
        McpServerRemoteServiceConfig remoteConfig = new McpServerRemoteServiceConfig();
        List<FrontEndpointConfig> endpoints = new ArrayList<>();
        for (Remote remote : remotes) {
            String url = remote.getUrl().trim();
            try {
                UrlComponents components = parseUrlComponents(url);
                boolean https = "https".equalsIgnoreCase(components.getScheme());
                int effectivePort =
                    components.getPort() > 0 ? components.getPort() : https ? 443 : 80;
                FrontEndpointConfig config = new FrontEndpointConfig();
                config.setEndpointData(components.getHost() + ":" + effectivePort);
                config.setPath(StringUtils.isNotBlank(components.getPath())
                    ? components.getPath() : "/");
                config.setType(remote.getType());
                config.setProtocol(components.getScheme());
                config.setEndpointType(AiConstants.Mcp.MCP_FRONT_ENDPOINT_TYPE_TO_BACK);
                config.setHeaders(remote.getHeaders());
                endpoints.add(config);
                if (remoteConfig.getExportPath() == null) {
                    remoteConfig.setExportPath(
                        components.getPath() == null ? "/" : components.getPath());
                }
            } catch (Exception e) {
                throw new IllegalStateException("Invalid URL: " + url, e);
            }
        }
        remoteConfig.setFrontEndpointConfigList(endpoints);
        return remoteConfig;
    }
    
    private UrlComponents parseUrlComponents(String url) {
        String scheme = null;
        int schemeEnd = url.indexOf("://");
        if (schemeEnd > 0) {
            scheme = url.substring(0, schemeEnd);
            url = url.substring(schemeEnd + 3);
        }
        int pathStart = url.indexOf('/');
        String hostPart;
        String path;
        if (pathStart > 0) {
            hostPart = url.substring(0, pathStart);
            path = url.substring(pathStart);
        } else {
            hostPart = url;
            path = null;
        }
        String host;
        int port = -1;
        int portStart = hostPart.lastIndexOf(':');
        if (portStart > 0) {
            host = hostPart.substring(0, portStart);
            try {
                port = Integer.parseInt(hostPart.substring(portStart + 1));
            } catch (NumberFormatException e) {
                host = hostPart;
                port = -1;
            }
        } else {
            host = hostPart;
        }
        return new UrlComponents(scheme, host, port, path);
    }
    
    private String buildPageUrl(String base, String cursor, Integer limit, String search) {
        StringBuilder url = new StringBuilder(base);
        boolean hasQuery = base.contains(QUERY_MARK);
        if (StringUtils.isNotBlank(cursor)) {
            url.append(hasQuery ? AMPERSAND : QUERY_MARK).append(CURSOR_QUERY_NAME).append("=")
                .append(URLEncoder.encode(cursor, StandardCharsets.UTF_8));
            hasQuery = true;
        }
        if (limit != null && limit > 0) {
            url.append(hasQuery ? AMPERSAND : QUERY_MARK).append(LIMIT_QUERY_NAME).append("=")
                .append(limit);
            hasQuery = true;
        }
        if (StringUtils.isNotBlank(search)) {
            url.append(hasQuery ? AMPERSAND : QUERY_MARK).append(SEARCH_QUERY_NAME).append("=")
                .append(URLEncoder.encode(search, StandardCharsets.UTF_8));
        }
        return url.toString();
    }
    
    private boolean isSuccessStatus(int code) {
        return code >= HTTP_STATUS_SUCCESS_MIN && code <= HTTP_STATUS_SUCCESS_MAX;
    }
    
    private String generateMcpServerId(String name) {
        return UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8)).toString();
    }
    
    static class Page {
        
        private final List<McpServerDetailInfo> servers;
        
        private final String nextCursor;
        
        Page(List<McpServerDetailInfo> servers, String nextCursor) {
            this.servers = servers;
            this.nextCursor = nextCursor;
        }
        
        List<McpServerDetailInfo> getServers() {
            return servers;
        }
        
        String getNextCursor() {
            return nextCursor;
        }
    }
    
    private static class UrlComponents {
        
        private final String scheme;
        
        private final String host;
        
        private final int port;
        
        private final String path;
        
        UrlComponents(String scheme, String host, int port, String path) {
            this.scheme = scheme;
            this.host = host;
            this.port = port;
            this.path = path;
        }
        
        String getScheme() {
            return scheme;
        }
        
        String getHost() {
            return host;
        }
        
        int getPort() {
            return port;
        }
        
        String getPath() {
            return path;
        }
    }
}
