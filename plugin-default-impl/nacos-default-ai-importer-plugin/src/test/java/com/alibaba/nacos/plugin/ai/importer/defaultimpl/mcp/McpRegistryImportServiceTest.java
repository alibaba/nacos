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
import com.alibaba.nacos.api.ai.model.mcp.McpServerDetailInfo;
import com.alibaba.nacos.api.ai.model.mcp.registry.Repository;
import com.alibaba.nacos.api.ai.model.mcp.registry.ServerVersionDetail;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.plugin.ai.importer.model.AiResourceImportArtifact;
import com.alibaba.nacos.plugin.ai.importer.model.AiResourceImportCandidatePage;
import com.alibaba.nacos.plugin.ai.importer.model.AiResourceImportContext;
import com.alibaba.nacos.plugin.ai.importer.model.AiResourceImportItem;
import com.alibaba.nacos.plugin.ai.importer.model.AiResourceImportPayloadKind;
import com.alibaba.nacos.plugin.ai.importer.model.AiResourceImportSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.reset;

/**
 * Unit tests for {@link McpRegistryImportService}.
 *
 * @author xiweng.yy
 */
@ExtendWith(MockitoExtension.class)
class McpRegistryImportServiceTest {
    
    private static final String ENDPOINT = "https://registry.example.com/v0/servers";
    
    @Mock
    private McpRegistryClient client;
    
    private McpRegistryImportService importService;
    
    @BeforeEach
    void setUp() {
        importService = new McpRegistryImportService(client);
    }
    
    @Test
    void testSearchReturnsCandidateMetadata() throws Exception {
        McpServerDetailInfo server = newMcpServer();
        when(client.fetchOfficialRegistryPage(any(AiResourceImportSource.class), eq("cursor-1"),
            eq(20), eq("redis")))
            .thenReturn(new McpRegistryClient.Page(Collections.singletonList(server),
                "cursor-2"));
        
        AiResourceImportCandidatePage result = importService.search(newContext());
        
        assertEquals(1, result.getItems().size());
        assertEquals("io.nacos/test-server", result.getItems().get(0).getExternalId());
        assertEquals("1.0.0", result.getItems().get(0).getVersion());
        assertEquals(AiConstants.Mcp.MCP_PROTOCOL_STDIO,
            result.getItems().get(0).getMetadata().get("protocol"));
        assertEquals("cursor-2", result.getNextCursor());
    }
    
    @Test
    void testFetchReturnsMcpDetailArtifact() throws Exception {
        McpServerDetailInfo server = newMcpServer();
        when(client.fetchOfficialRegistryServer(any(AiResourceImportSource.class),
            eq("io.nacos/test-server"), eq(30)))
            .thenReturn(server);
        AiResourceImportItem item = new AiResourceImportItem();
        item.setExternalId("io.nacos/test-server");
        
        AiResourceImportArtifact result = importService.fetch(newFetchContext(), item);
        
        assertEquals(McpRegistryImportService.RESOURCE_TYPE_MCP, result.getResourceType());
        assertEquals(AiResourceImportPayloadKind.MCP_DETAIL, result.getPayloadKind());
        assertEquals("io.nacos/test-server", result.getName());
        McpServerDetailInfo parsed =
            JacksonUtils.toObj(result.getPayloadJson(), McpServerDetailInfo.class);
        assertEquals("io.nacos/test-server", parsed.getName());
    }
    
    @Test
    void testSearchRejectsMissingEndpoint() {
        AiResourceImportContext context = newContext();
        context.getSource().setEndpoint(null);
        
        assertThrows(NacosException.class, () -> importService.search(context));
    }
    
    @Test
    void testSearchWrapsClientFailureAndHandlesEmptyPage() throws Exception {
        when(client.fetchOfficialRegistryPage(any(AiResourceImportSource.class), any(), any(),
            any())).thenThrow(new IllegalStateException("boom"));
        assertThrows(NacosException.class, () -> importService.search(newContext()));
        
        reset(client);
        McpRegistryImportService emptyService = new McpRegistryImportService(client);
        when(client.fetchOfficialRegistryPage(any(AiResourceImportSource.class), eq("cursor-1"),
            eq(20), eq("redis"))).thenReturn(new McpRegistryClient.Page(null, null));
        AiResourceImportCandidatePage result = emptyService.search(newContext());
        assertEquals(0, result.getItems().size());
    }
    
    @Test
    void testFetchRejectsInvalidItemAndWrapsClientFailure() throws Exception {
        assertThrows(NacosException.class, () -> importService.fetch(newFetchContext(), null));
        assertThrows(NacosException.class,
            () -> importService.fetch(newFetchContext(), new AiResourceImportItem()));
        
        AiResourceImportItem item = new AiResourceImportItem();
        item.setName("io.nacos/test-server");
        when(client.fetchOfficialRegistryServer(any(AiResourceImportSource.class),
            eq("io.nacos/test-server"), eq(30))).thenThrow(new IllegalStateException("boom"));
        assertThrows(NacosException.class, () -> importService.fetch(newFetchContext(), item));
    }
    
    @Test
    void testFetchUsesContextLimitAndFallbackVersionMetadata() throws Exception {
        McpServerDetailInfo server = newMcpServer();
        server.setVersion("2.0.0");
        server.setVersionDetail(null);
        server.setStatus("active");
        Repository repository = new Repository();
        repository.setUrl("https://github.com/nacos/test-server");
        server.setRepository(repository);
        when(client.fetchOfficialRegistryServer(any(AiResourceImportSource.class),
            eq("io.nacos/test-server"), eq(7))).thenReturn(server);
        AiResourceImportContext context = newFetchContext();
        context.setLimit(7);
        AiResourceImportItem item = new AiResourceImportItem();
        item.setName("io.nacos/test-server");
        
        AiResourceImportArtifact result = importService.fetch(context, item);
        
        assertEquals("2.0.0", result.getVersion());
        assertEquals("active", result.getSourceMetadata().get("status"));
        assertEquals("https://github.com/nacos/test-server",
            result.getSourceMetadata().get("repository"));
    }
    
    @Test
    void testSupportedResourceTypeAndImporterType() {
        assertEquals(McpRegistryImportServiceBuilder.IMPORTER_TYPE, importService.importerType());
        assertFalse(importService.supportedResourceTypes().isEmpty());
    }
    
    private AiResourceImportContext newContext() {
        AiResourceImportContext context = newFetchContext();
        context.setCursor("cursor-1");
        context.setLimit(20);
        context.setQuery("redis");
        return context;
    }
    
    private AiResourceImportContext newFetchContext() {
        AiResourceImportContext context = new AiResourceImportContext();
        AiResourceImportSource source = new AiResourceImportSource();
        source.setEndpoint(ENDPOINT);
        context.setSource(source);
        return context;
    }
    
    private McpServerDetailInfo newMcpServer() {
        McpServerDetailInfo server = new McpServerDetailInfo();
        server.setId("server-id");
        server.setName("io.nacos/test-server");
        server.setDescription("test server");
        server.setProtocol(AiConstants.Mcp.MCP_PROTOCOL_STDIO);
        ServerVersionDetail versionDetail = new ServerVersionDetail();
        versionDetail.setVersion("1.0.0");
        server.setVersionDetail(versionDetail);
        return server;
    }
}
