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

import com.alibaba.nacos.ai.enums.ExternalDataTypeEnum;
import com.alibaba.nacos.ai.model.mcp.UrlPageResult;
import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.api.ai.model.mcp.FrontEndpointConfig;
import com.alibaba.nacos.api.ai.model.mcp.McpServerDetailInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpServerImportRequest;
import com.alibaba.nacos.api.ai.model.mcp.McpServerRemoteServiceConfig;
import com.alibaba.nacos.api.ai.model.mcp.registry.McpRegistryServerDetail;
import com.alibaba.nacos.api.ai.model.mcp.registry.McpRegistryServerList;
import com.alibaba.nacos.api.ai.model.mcp.registry.Remote;
import com.alibaba.nacos.api.ai.model.mcp.registry.ServerResponse;
import com.alibaba.nacos.api.ai.model.mcp.registry.ServerVersionDetail;
import com.alibaba.nacos.api.ai.model.mcp.registry.OfficialMeta;
import com.alibaba.nacos.common.utils.CollectionUtils;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.stream.Collectors;

/**
 * <p>Adapt the External data(mcp server json file, mcp registry api data) to Nacos MCP server format
 * {@link McpServerDetailInfo}. MCP official formats docs.</p>
 *
 * <p>1. MCP Server format is defined in 
 * <a href="https://github.com/modelcontextprotocol/registry/blob/main/docs/reference/server-json/server.schema.json">
 * server.schema.json</a>.</p>
 *
 * <p>2. MCP Registry Api is defined in 
 * <a href="https://github.com/modelcontextprotocol/registry/blob/main/docs/reference/api/openapi.yaml">
 * openapi.yaml</a>.</p>
 *
 * @author nacos
 */
@Service
public class McpExternalDataAdaptor {

    private HttpClient httpClient;

    private static final String CURSOR_QUERY_NAME = "cursor";

    private static final String LIMIT_QUERY_NAME = "limit";

    private static final String SEARCH_QUERY_NAME = "search";

    private static final String HEADER_ACCEPT = "Accept";

    private static final String HEADER_ACCEPT_JSON = "application/json";

    private static final String QUERY_MARK = "?";

    private static final String AMPERSAND = "&";

    private static final int HTTP_STATUS_SUCCESS_MIN = 200;

    private static final int HTTP_STATUS_SUCCESS_MAX = 299;

    private static final int CONNECT_TIMEOUT_SECONDS = 10;

    private static final int READ_TIMEOUT_SECONDS = 20;

    private static final int FETCH_ALL_LIMIT_MARK = -1;

    /**
     * Safety guard to avoid infinite loops when server keeps returning cursors.
     * Limits the maximum number of pages iterated when fetching from URL.
     */
    private static final int MAX_PAGES_GUARD = 200;

    /**
     * Adapt the external data to Nacos MCP server format.
     *
     * @param request import request
     * @return Nacos MCP server format
     * @throws Exception if adapt failed
     */
    public List<McpServerDetailInfo> adaptExternalDataToNacosMcpServerFormat(McpServerImportRequest request) throws Exception {
        ExternalDataTypeEnum externalDataTypeEnum = ExternalDataTypeEnum.parseType(request.getImportType());
        if (ExternalDataTypeEnum.FILE.equals(externalDataTypeEnum)) {
            return adaptOfficialSeedFile(request.getData());
        } else if (ExternalDataTypeEnum.JSON.equals(externalDataTypeEnum)) {
            return adaptOfficialMcpServerJsonText(request.getData());
        } else if (ExternalDataTypeEnum.URL.equals(externalDataTypeEnum)) {
            return adaptOfficialRegistryUrl(request.getData(), request.getCursor(),
                    request.getLimit(), request.getSearch());
        } else {
            throw new IllegalArgumentException("Unsupported import type: " + externalDataTypeEnum);
        }
    }

    private UrlPageResult fetchUrlPage(String urlData, String cursor, Integer limit, String search) throws Exception {
        String base = urlData.trim();
        HttpClient client = getHttpClient();
        String pageUrl = buildPageUrl(base, cursor, limit, search);
        HttpRequest request = buildGetRequest(pageUrl);
        HttpResponse<String> resp = client.send(request, HttpResponse.BodyHandlers.ofString());
        int code = resp.statusCode();
        if (!isSuccessStatus(code)) {
            throw new IllegalStateException("HTTP " + code + " when fetching " + pageUrl);
        }
        List<McpServerDetailInfo> servers = null;
        String next = null;
        try {
            McpRegistryServerList listPage = JacksonUtils.toObj(resp.body(), McpRegistryServerList.class);
            if (listPage != null && listPage.getServers() != null) {
                servers = listPage.getServers().stream()
                        .map(this::adaptOfficialMcpServerFromResponse)
                        .collect(Collectors.toList());
            }
            if (listPage != null && listPage.getMetadata() != null) {
                next = listPage.getMetadata().getNextCursor();
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse response body", e);
        }
        return new UrlPageResult(servers, next);
    }

    private List<McpServerDetailInfo> fetchUrlServersAll(String urlData, String search) throws Exception {
        List<McpServerDetailInfo> collected = new ArrayList<>();
        String cursor = null;
        int pages = 0;
        while (pages < MAX_PAGES_GUARD) {
            pages++;
            UrlPageResult page = fetchUrlPage(urlData, cursor, 30, search);
            if (CollectionUtils.isNotEmpty(page.getServers())) {
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

    /**
     * Adapt official mcp server from server response.
     * Just append version meta info to the result of adaptOfficialMcpServer.
     *
     * @param response the server response object
     * @return adapted mcp server detail info
     */
    private McpServerDetailInfo adaptOfficialMcpServerFromResponse(ServerResponse response) {
        McpServerDetailInfo adaptOfficialMcpServer = adaptOfficialMcpServer(response.getServer());
        OfficialMeta official = response.getMeta().getOfficial();
        ServerVersionDetail versionDetail = adaptOfficialMcpServer.getVersionDetail();
        if (versionDetail != null) {
            versionDetail.setRelease_date(official.getPublishedAt());
            versionDetail.setIs_latest(true);
            String status = official.getStatus();
            if (StringUtils.isNotBlank(status)) {
                adaptOfficialMcpServer.setStatus(status);
            }
        }
        return adaptOfficialMcpServer;
    }

    private void applyBasicInfo(McpRegistryServerDetail registryServer, McpServerDetailInfo out) {
        String id = generateMcpServerId(registryServer.getName());
        out.setId(id);
        out.setName(registryServer.getName());
        out.setDescription(registryServer.getDescription());
        out.setRepository(registryServer.getRepository());
    }

    private void applyVersionInfo(McpRegistryServerDetail registryServer, McpServerDetailInfo out) {
        ServerVersionDetail v = null;
        if (StringUtils.isNotBlank(registryServer.getVersion())) {
            v = new ServerVersionDetail();
            v.setVersion(registryServer.getVersion());
        }
        out.setVersionDetail(v);
    }

    private void applyProtocolInfo(McpRegistryServerDetail registryServer, McpServerDetailInfo out) {
        String protocol = resolveServerProtocol(registryServer);
        if (StringUtils.isNotBlank(protocol)) {
            out.setProtocol(protocol);
            out.setFrontProtocol(protocol);
        }
    }

    private void applyLocalAndRemoteConfig(McpRegistryServerDetail registryServer, McpServerDetailInfo server) {
        if (registryServer != null) {
            server.setPackages(registryServer.getPackages());
            server.setRemoteServerConfig(generateRemoteServiceConfig(registryServer.getRemotes()));
        }
    }

    private String resolveServerProtocol(McpRegistryServerDetail detail) {
        if (CollectionUtils.isNotEmpty(detail.getPackages())) {
            return AiConstants.Mcp.MCP_PROTOCOL_STDIO;
        }

        if (CollectionUtils.isNotEmpty(detail.getRemotes())) {
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
                boolean isHttps = "https".equalsIgnoreCase(components.getScheme());
                int effectivePort = (components.getPort() > 0) ? components.getPort() : (isHttps ? 443 : 80);
                String endpointData = components.getHost() + ":" + effectivePort;
                FrontEndpointConfig cfg = new FrontEndpointConfig();
                cfg.setEndpointData(endpointData);
                cfg.setPath(StringUtils.isNotBlank(components.getPath()) ? components.getPath() : "/");
                cfg.setType(remote.getType());
                cfg.setProtocol(components.getScheme());
                cfg.setEndpointType(AiConstants.Mcp.MCP_FRONT_ENDPOINT_TYPE_TO_BACK);
                cfg.setHeaders(remote.getHeaders());
                endpoints.add(cfg);
                
                // Use first remote's path as export path
                if (remoteConfig.getExportPath() == null) {
                    remoteConfig.setExportPath(components.getPath() != null ? components.getPath() : "/");
                }
            } catch (Exception e) {
                throw new IllegalStateException("Invalid URL: " + url, e);
            }
        }
        
        remoteConfig.setFrontEndpointConfigList(endpoints);
        return remoteConfig;
    }

    /**
     * Parse URL into components (scheme, host, port, path).
     * Manual parsing without using URI class.
     *
     * @param url the URL string to parse
     * @return UrlComponents containing scheme, host, port, and path
     */
    private UrlComponents parseUrlComponents(String url) {
        String scheme = null;
        String host = null;
        int port = -1;
        String path = null;

        // Parse scheme
        int schemeEnd = url.indexOf("://");
        if (schemeEnd > 0) {
            scheme = url.substring(0, schemeEnd);
            url = url.substring(schemeEnd + 3);
        }

        // Parse host, port, and path
        int pathStart = url.indexOf('/');
        String hostPart;
        if (pathStart > 0) {
            hostPart = url.substring(0, pathStart);
            path = url.substring(pathStart);
        } else {
            hostPart = url;
            path = null;
        }

        // Parse host and port
        int portStart = hostPart.lastIndexOf(':');
        if (portStart > 0) {
            host = hostPart.substring(0, portStart);
            try {
                port = Integer.parseInt(hostPart.substring(portStart + 1));
            } catch (NumberFormatException e) {
                // Invalid port, treat the whole thing as host
                host = hostPart;
                port = -1;
            }
        } else {
            host = hostPart;
        }

        return new UrlComponents(scheme, host, port, path);
    }

    /**
     * Inner class to hold URL components parsed from a URI.
     */
    private static class UrlComponents {
        private final String scheme;
        private final String host;
        private final int port;
        private final String path;

        public UrlComponents(String scheme, String host, int port, String path) {
            this.scheme = scheme;
            this.host = host;
            this.port = port;
            this.path = path;
        }

        public String getScheme() {
            return scheme;
        }

        public String getHost() {
            return host;
        }

        public int getPort() {
            return port;
        }

        public String getPath() {
            return path;
        }
    }

    /**
     * URL import wrapper: fetch contents from specified URL and adapt to Nacos mcp servers.
     * Fetch specified contents from specified URL and adapt to Nacos mcp servers.
     *
     * @param urlData URL data to parse. Only support official mcp registry api.
     * @param cursor Cursor for pagination
     * @param limit Limit for pagination. Fetch all pages when limit = -1
     * @param search fuzzy search keyword
     * @return list of adapted mcp servers
     * @throws Exception if adaptation failed
     */
    private List<McpServerDetailInfo> adaptOfficialRegistryUrl(String urlData, String cursor, Integer limit, String search)
            throws Exception {
        if (StringUtils.isBlank(urlData)) {
            throw new IllegalArgumentException("URL is blank");
        }

        // If limit = -1, fetch all pages
        if (limit != null && limit == FETCH_ALL_LIMIT_MARK) {
            return fetchUrlServersAll(urlData.trim(), search);
        }

        // Otherwise, fetch a single page using fetchUrlPage
        UrlPageResult page = fetchUrlPage(urlData.trim(), cursor, limit, search);
        return page.getServers();
    }

    /**
     * File import wrapper: parse into a list of RegistryDetails and convert to
     * Nacos servers.
     */
    private List<McpServerDetailInfo> adaptOfficialSeedFile(String data) {
        return unmarshaledSeedToServerList(data).stream()
                .map(this::adaptOfficialMcpServer)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private List<McpServerDetailInfo> adaptOfficialMcpServerJsonText(String data) {
        McpRegistryServerDetail detail = JacksonUtils.toObj(data, McpRegistryServerDetail.class);
        return Collections.singletonList(adaptOfficialMcpServer(detail));
    }

    private List<McpRegistryServerDetail> unmarshaledSeedToServerList(String data) {
        return JacksonUtils.toObj(data, new TypeReference<>() { });
    }

    private HttpClient getHttpClient() {
        if (httpClient == null) {
            httpClient = HttpClient.newBuilder()
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .connectTimeout(Duration.ofSeconds(CONNECT_TIMEOUT_SECONDS))
                    .build();
        }
        return httpClient;
    }

    public void setHttpClient(HttpClient client) {
        this.httpClient = client;
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

    private HttpRequest buildGetRequest(String url) {
        return HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(READ_TIMEOUT_SECONDS))
                .GET()
                .header(HEADER_ACCEPT, HEADER_ACCEPT_JSON).build();
    }

    private boolean isSuccessStatus(int code) {
        return code >= HTTP_STATUS_SUCCESS_MIN && code <= HTTP_STATUS_SUCCESS_MAX;
    }

    private String generateMcpServerId(String name) {
        return UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8)).toString();
    }
}