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

package com.alibaba.nacos.airegistry.service;

import com.alibaba.nacos.ai.service.mcp.McpOperationService;
import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.api.ai.model.mcp.McpEndpointInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpServerDetailInfo;
import com.alibaba.nacos.api.ai.model.mcp.registry.McpServerStatusEnum;
import com.alibaba.nacos.api.ai.model.mcp.registry.ServerResponse;
import com.alibaba.nacos.api.ai.model.mcp.registry.ServerVersionDetail;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.core.service.NamespaceOperationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link NacosMcpRegistryService} remotes URL building and official meta status.
 *
 * @author Goldloli
 */
@ExtendWith(MockitoExtension.class)
class NacosMcpRegistryServiceTest {
    
    private static final String NAMESPACE_ID = "public";
    
    private static final String SERVER_NAME = "direct-mcp-server";
    
    @Mock
    private McpOperationService mcpServerOperationService;
    
    @Mock
    private NamespaceOperationService namespaceOperationService;
    
    private NacosMcpRegistryService nacosMcpRegistryService;
    
    @BeforeEach
    void setUp() {
        nacosMcpRegistryService = new NacosMcpRegistryService(mcpServerOperationService,
            namespaceOperationService);
    }
    
    @Test
    void testGetServerDirectEndpointWithoutProtocolAndPathBuildsValidUrl() throws NacosException {
        McpServerDetailInfo detail = buildDetailWithEndpoint(buildDirectEndpoint());
        when(mcpServerOperationService.getMcpServerDetail(NAMESPACE_ID, null, SERVER_NAME, null))
            .thenReturn(detail);
        
        ServerResponse response =
            nacosMcpRegistryService.getServer(SERVER_NAME, NAMESPACE_ID, null);
        
        assertNotNull(response);
        assertNotNull(response.getServer().getRemotes());
        assertEquals(1, response.getServer().getRemotes().size());
        assertEquals("http://host.docker.internal:3001",
            response.getServer().getRemotes().get(0).getUrl());
    }
    
    @Test
    void testGetServerEndpointWithExplicitProtocolAndPathKeepsThem() throws NacosException {
        McpEndpointInfo endpoint = buildDirectEndpoint();
        endpoint.setProtocol("https");
        endpoint.setPath("/sse");
        endpoint.setPort(443);
        McpServerDetailInfo detail = buildDetailWithEndpoint(endpoint);
        when(mcpServerOperationService.getMcpServerDetail(NAMESPACE_ID, null, SERVER_NAME, null))
            .thenReturn(detail);
        
        ServerResponse response =
            nacosMcpRegistryService.getServer(SERVER_NAME, NAMESPACE_ID, null);
        
        assertNotNull(response);
        assertEquals("https://host.docker.internal/sse",
            response.getServer().getRemotes().get(0).getUrl());
    }
    
    @Test
    void testGetServerHttpDefaultPortIsOmitted() throws NacosException {
        McpEndpointInfo endpoint = buildDirectEndpoint();
        endpoint.setProtocol("http");
        endpoint.setPath("/mcp");
        endpoint.setPort(80);
        McpServerDetailInfo detail = buildDetailWithEndpoint(endpoint);
        when(mcpServerOperationService.getMcpServerDetail(NAMESPACE_ID, null, SERVER_NAME, null))
            .thenReturn(detail);
        
        ServerResponse response =
            nacosMcpRegistryService.getServer(SERVER_NAME, NAMESPACE_ID, null);
        
        assertNotNull(response);
        assertEquals("http://host.docker.internal/mcp",
            response.getServer().getRemotes().get(0).getUrl());
    }
    
    @Test
    void testGetServerPopulatesOfficialMetaStatusFromServerStatus() throws NacosException {
        McpServerDetailInfo detail = buildDetailWithEndpoint(buildDirectEndpoint());
        detail.setStatus(McpServerStatusEnum.DEPRECATED.getName());
        when(mcpServerOperationService.getMcpServerDetail(NAMESPACE_ID, null, SERVER_NAME, null))
            .thenReturn(detail);
        
        ServerResponse response =
            nacosMcpRegistryService.getServer(SERVER_NAME, NAMESPACE_ID, null);
        
        assertNotNull(response);
        assertNotNull(response.getMeta());
        assertNotNull(response.getMeta().getOfficial());
        assertEquals(McpServerStatusEnum.DEPRECATED.getName(),
            response.getMeta().getOfficial().getStatus());
    }
    
    @Test
    void testGetServerDefaultStatusIsActiveAndVersionFieldsStillFilled() throws NacosException {
        McpServerDetailInfo detail = buildDetailWithEndpoint(buildDirectEndpoint());
        ServerVersionDetail currentVersion = new ServerVersionDetail();
        currentVersion.setVersion("1.0.0");
        currentVersion.setRelease_date("2026-11-01T00:00:00Z");
        detail.setVersionDetail(currentVersion);
        ServerVersionDetail latestVersion = new ServerVersionDetail();
        latestVersion.setVersion("2.0.0");
        latestVersion.setRelease_date("2026-11-05T00:00:00Z");
        detail.setAllVersions(List.of(currentVersion, latestVersion));
        when(mcpServerOperationService.getMcpServerDetail(NAMESPACE_ID, null, SERVER_NAME, null))
            .thenReturn(detail);
        
        ServerResponse response =
            nacosMcpRegistryService.getServer(SERVER_NAME, NAMESPACE_ID, null);
        
        assertNotNull(response);
        ServerResponse.Meta meta = response.getMeta();
        assertEquals(McpServerStatusEnum.ACTIVE.getName(), meta.getOfficial().getStatus());
        assertEquals("2026-11-01T00:00:00Z", meta.getOfficial().getPublishedAt());
        assertEquals("2026-11-05T00:00:00Z", meta.getOfficial().getUpdatedAt());
        assertEquals(Boolean.FALSE, meta.getOfficial().getIsLatest());
    }
    
    private McpEndpointInfo buildDirectEndpoint() {
        McpEndpointInfo endpoint = new McpEndpointInfo();
        endpoint.setAddress("host.docker.internal");
        endpoint.setPort(3001);
        return endpoint;
    }
    
    private McpServerDetailInfo buildDetailWithEndpoint(McpEndpointInfo endpoint) {
        McpServerDetailInfo detail = new McpServerDetailInfo();
        detail.setName(SERVER_NAME);
        detail.setNamespaceId(NAMESPACE_ID);
        detail.setFrontProtocol(AiConstants.Mcp.MCP_PROTOCOL_SSE);
        detail.setFrontendEndpoints(Collections.singletonList(endpoint));
        return detail;
    }
}
