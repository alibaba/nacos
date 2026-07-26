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
import com.alibaba.nacos.api.ai.model.mcp.registry.McpRegistryServerDetail;
import com.alibaba.nacos.api.ai.model.mcp.registry.Remote;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.plugin.ai.importer.defaultimpl.http.DefaultImportHttpClient;
import com.alibaba.nacos.plugin.ai.importer.defaultimpl.http.ImportHttpResponse;
import com.alibaba.nacos.plugin.ai.importer.model.AiResourceImportSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.http.HttpHeaders;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class McpRegistryClientTest {
    
    private static final String ENDPOINT = "https://registry.example.com/v0/servers";
    
    @Mock
    private DefaultImportHttpClient httpClient;
    
    private McpRegistryClient client;
    
    @BeforeEach
    void setUp() {
        client = new McpRegistryClient(httpClient);
    }
    
    @Test
    void testFetchOfficialRegistryPageBuildsQueryAndAdaptsServers() throws Exception {
        when(httpClient.get(any(AiResourceImportSource.class),
            eq(ENDPOINT + "?cursor=cursor-1&limit=3&search=redis+cache"), eq(20),
            eq("application/json"))).thenReturn(response(200, registryPageJson()));
        
        McpRegistryClient.Page page = client.fetchOfficialRegistryPage(source(), "cursor-1", 3,
            "redis cache");
        
        assertEquals("next-1", page.getNextCursor());
        assertEquals(3, page.getServers().size());
        assertEquals("io.nacos/stdio", page.getServers().get(0).getName());
        assertEquals(AiConstants.Mcp.MCP_PROTOCOL_STDIO, page.getServers().get(0).getProtocol());
        assertEquals("1.0.0", page.getServers().get(0).getVersionDetail().getVersion());
        assertEquals("2026-06-01T00:00:00Z",
            page.getServers().get(0).getVersionDetail().getRelease_date());
        assertEquals(Boolean.TRUE, page.getServers().get(0).getVersionDetail().getIs_latest());
        assertEquals("active", page.getServers().get(0).getStatus());
        assertEquals(AiConstants.Mcp.MCP_PROTOCOL_SSE, page.getServers().get(1).getProtocol());
        assertEquals("example.com:8443", page.getServers().get(1).getRemoteServerConfig()
            .getFrontEndpointConfigList().get(0).getEndpointData());
        assertEquals("/sse", page.getServers().get(1).getRemoteServerConfig().getExportPath());
        assertEquals(AiConstants.Mcp.MCP_PROTOCOL_STREAMABLE,
            page.getServers().get(2).getProtocol());
        assertEquals("example.org:80", page.getServers().get(2).getRemoteServerConfig()
            .getFrontEndpointConfigList().get(0).getEndpointData());
        assertEquals("/", page.getServers().get(2).getRemoteServerConfig().getExportPath());
    }
    
    @Test
    void testFetchOfficialRegistryPageSupportsExistingQueryAndEmptyList() throws Exception {
        String endpoint = ENDPOINT + "?sort=name";
        AiResourceImportSource source = source();
        source.setEndpoint(endpoint);
        when(httpClient.get(any(AiResourceImportSource.class), eq(endpoint + "&limit=30"),
            eq(20), eq("application/json"))).thenReturn(response(200, "{}"));
        
        McpRegistryClient.Page page =
            client.fetchOfficialRegistryPage(source, null, 30, null);
        
        assertEquals(0, page.getServers().size());
        assertNull(page.getNextCursor());
    }
    
    @Test
    void testFetchOfficialRegistryPageRejectsMissingEndpoint() {
        AiResourceImportSource source = source();
        source.setEndpoint(" ");
        
        assertThrows(IllegalArgumentException.class,
            () -> client.fetchOfficialRegistryPage(source, null, null, null));
        assertThrows(IllegalArgumentException.class,
            () -> client.fetchOfficialRegistryPage(null, null, null, null));
    }
    
    @Test
    void testFetchOfficialRegistryPageRejectsHttpError() throws Exception {
        when(httpClient.get(any(AiResourceImportSource.class), eq(ENDPOINT), eq(20),
            eq("application/json"))).thenReturn(response(500, "{}"));
        
        assertThrows(IllegalStateException.class,
            () -> client.fetchOfficialRegistryPage(source(), null, null, null));
    }
    
    @Test
    void testFetchOfficialRegistryPageRejectsInvalidJson() throws Exception {
        when(httpClient.get(any(AiResourceImportSource.class), eq(ENDPOINT), eq(20),
            eq("application/json"))).thenReturn(response(200, "{"));
        
        assertThrows(IllegalStateException.class,
            () -> client.fetchOfficialRegistryPage(source(), null, null, null));
    }
    
    @Test
    void testFetchOfficialRegistryServerFindsByName() throws Exception {
        when(httpClient.get(any(AiResourceImportSource.class),
            eq(ENDPOINT + "?limit=30&search=io.nacos%2Fstdio"), eq(20),
            eq("application/json"))).thenReturn(response(200, registryPageJson()));
        
        assertEquals("io.nacos/stdio",
            client.fetchOfficialRegistryServer(source(), "io.nacos/stdio", 0).getName());
    }
    
    @Test
    void testFetchOfficialRegistryServerRejectsBlankOrMissingServer() throws Exception {
        assertThrows(IllegalArgumentException.class,
            () -> client.fetchOfficialRegistryServer(source(), " ", 30));
        when(httpClient.get(any(AiResourceImportSource.class), eq(ENDPOINT + "?limit=1&search=x"),
            eq(20), eq("application/json"))).thenReturn(response(200, registryPageJson()));
        
        assertThrows(IllegalStateException.class,
            () -> client.fetchOfficialRegistryServer(source(), "x", 1));
    }
    
    @Test
    void testFetchOfficialRegistryPageRejectsMissingRemoteUrl() throws Exception {
        when(httpClient.get(any(AiResourceImportSource.class), eq(ENDPOINT), eq(20),
            eq("application/json"))).thenReturn(response(200, invalidRemotePageJson()));
        
        assertThrows(IllegalStateException.class,
            () -> client.fetchOfficialRegistryPage(source(), null, null, null));
    }
    
    @Test
    void testConstructorWithHttpClientAndNullServerEntry() throws Exception {
        assertThrows(Exception.class,
            () -> new McpRegistryClient(new HttpClient() {
                
                @Override
                public java.util.Optional<java.net.CookieHandler> cookieHandler() {
                    return java.util.Optional.empty();
                }
                
                @Override
                public java.util.Optional<java.time.Duration> connectTimeout() {
                    return java.util.Optional.empty();
                }
                
                @Override
                public Redirect followRedirects() {
                    return Redirect.NEVER;
                }
                
                @Override
                public java.util.Optional<java.net.ProxySelector> proxy() {
                    return java.util.Optional.empty();
                }
                
                @Override
                public javax.net.ssl.SSLContext sslContext() {
                    return null;
                }
                
                @Override
                public javax.net.ssl.SSLParameters sslParameters() {
                    return null;
                }
                
                @Override
                public java.util.Optional<java.net.Authenticator> authenticator() {
                    return java.util.Optional.empty();
                }
                
                @Override
                public Version version() {
                    return Version.HTTP_1_1;
                }
                
                @Override
                public java.util.Optional<java.util.concurrent.Executor> executor() {
                    return java.util.Optional.empty();
                }
                
                @Override
                public <T> java.net.http.HttpResponse<T> send(java.net.http.HttpRequest request,
                    java.net.http.HttpResponse.BodyHandler<T> responseBodyHandler) {
                    throw new UnsupportedOperationException();
                }
                
                @Override
                public <T> java.util.concurrent.CompletableFuture<java.net.http.HttpResponse<T>> sendAsync(
                    java.net.http.HttpRequest request,
                    java.net.http.HttpResponse.BodyHandler<T> responseBodyHandler) {
                    return java.util.concurrent.CompletableFuture.failedFuture(
                        new UnsupportedOperationException());
                }
                
                @Override
                public <T> java.util.concurrent.CompletableFuture<java.net.http.HttpResponse<T>> sendAsync(
                    java.net.http.HttpRequest request,
                    java.net.http.HttpResponse.BodyHandler<T> responseBodyHandler,
                    java.net.http.HttpResponse.PushPromiseHandler<T> pushPromiseHandler) {
                    return java.util.concurrent.CompletableFuture.failedFuture(
                        new UnsupportedOperationException());
                }
            }).fetchOfficialRegistryPage(source(), null, null, null));
        
        when(httpClient.get(any(AiResourceImportSource.class), eq(ENDPOINT), eq(20),
            eq("application/json"))).thenReturn(response(200, "{\"servers\":[{\"server\":null}]}"));
        assertThrows(IllegalStateException.class,
            () -> client.fetchOfficialRegistryPage(source(), null, null, null));
    }
    
    @Test
    void testAdaptOfficialMcpServerCatchesRemoteConfigFailure() throws Exception {
        Remote remote = Mockito.mock(Remote.class);
        when(remote.getUrl()).thenReturn("https://example.com/sse");
        when(remote.getType()).thenReturn("sse");
        when(remote.getHeaders()).thenThrow(
            new IllegalStateException("bad type"));
        McpRegistryServerDetail detail = new McpRegistryServerDetail();
        detail.setName("io.nacos/bad-remote");
        detail.setRemotes(Arrays.asList(remote));
        Method method = McpRegistryClient.class.getDeclaredMethod("adaptOfficialMcpServer",
            McpRegistryServerDetail.class);
        method.setAccessible(true);
        
        assertThrows(Exception.class, () -> method.invoke(client, detail));
    }
    
    private AiResourceImportSource source() {
        AiResourceImportSource source = new AiResourceImportSource();
        source.setEndpoint(ENDPOINT);
        return source;
    }
    
    private ImportHttpResponse response(int status, String body) {
        return new ImportHttpResponse("https://registry.example.com/v0/servers", status,
            HttpHeaders.of(Collections.emptyMap(), (key, value) -> true),
            body.getBytes(StandardCharsets.UTF_8));
    }
    
    private String registryPageJson() {
        Map<String, Object> root = new HashMap<>();
        root.put("servers", java.util.Arrays.asList(stdioServer(), sseServer(), streamServer()));
        root.put("metadata", Collections.singletonMap("nextCursor", "next-1"));
        return JacksonUtils.toJson(root);
    }
    
    private Map<String, Object> stdioServer() {
        Map<String, Object> detail = new HashMap<>();
        detail.put("name", "io.nacos/stdio");
        detail.put("description", "stdio server");
        detail.put("repository", Collections.singletonMap("url", "https://github.com/nacos/stdio"));
        detail.put("version", "1.0.0");
        detail.put("packages", Collections.singletonList(Collections.singletonMap("registry_name",
            "npm")));
        return serverResponse(detail, "2026-06-01T00:00:00Z", "active");
    }
    
    private Map<String, Object> sseServer() {
        Map<String, Object> remote = new HashMap<>();
        remote.put("type", "sse");
        remote.put("url", "https://example.com:8443/sse");
        remote.put("headers", Collections.singletonList(Collections.singletonMap("name", "token")));
        Map<String, Object> detail = new HashMap<>();
        detail.put("name", "io.nacos/sse");
        detail.put("description", "sse server");
        detail.put("version", "2.0.0");
        detail.put("remotes", Collections.singletonList(remote));
        return serverResponse(detail, "2026-06-02T00:00:00Z", null);
    }
    
    private Map<String, Object> streamServer() {
        Map<String, Object> remote = new HashMap<>();
        remote.put("type", "STREAMABLE-HTTP");
        remote.put("url", "http://example.org");
        Map<String, Object> detail = new HashMap<>();
        detail.put("name", "io.nacos/stream");
        detail.put("id", "server-stream");
        detail.put("description", "stream server");
        detail.put("remotes", Collections.singletonList(remote));
        return serverResponse(detail, null, null);
    }
    
    private Map<String, Object> serverResponse(Map<String, Object> detail, String publishedAt,
        String status) {
        Map<String, Object> response = new HashMap<>();
        response.put("server", detail);
        if (publishedAt != null || status != null) {
            Map<String, Object> official = new HashMap<>();
            official.put("publishedAt", publishedAt);
            official.put("status", status);
            response.put("_meta",
                Collections.singletonMap("io.modelcontextprotocol.registry/official", official));
        }
        return response;
    }
    
    private String invalidRemotePageJson() {
        Map<String, Object> remote = new HashMap<>();
        remote.put("type", "sse");
        remote.put("url", null);
        Map<String, Object> detail = new HashMap<>();
        detail.put("name", "io.nacos/bad");
        detail.put("remotes", Collections.singletonList(remote));
        return JacksonUtils.toJson(Collections.singletonMap("servers",
            Collections.singletonList(serverResponse(detail, null, null))));
    }
}
