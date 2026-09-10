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

package com.alibaba.nacos.api.ai;

import com.alibaba.nacos.api.ai.listener.AbstractNacosMcpServerListener;
import com.alibaba.nacos.api.ai.model.mcp.McpEndpointSpec;
import com.alibaba.nacos.api.ai.model.mcp.McpResourceSpecification;
import com.alibaba.nacos.api.ai.model.mcp.McpServerBasicInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpServerDetailInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpToolSpecification;
import com.alibaba.nacos.api.exception.NacosException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class McpServiceDefaultMethodTest {
    
    private McpService service;
    
    private McpServerBasicInfo server;
    
    private McpToolSpecification tools;
    
    @BeforeEach
    void setUp() {
        service = mock(McpService.class, CALLS_REAL_METHODS);
        server = new McpServerBasicInfo();
        tools = new McpToolSpecification();
    }
    
    @Test
    void latestQueryPreservesCoreResultAndException() throws Exception {
        McpServerDetailInfo detail = new McpServerDetailInfo();
        NacosException missing = new NacosException(NacosException.NOT_FOUND, "missing");
        when(service.getMcpServer("mcp", null)).thenReturn(detail).thenThrow(missing);
        
        assertSame(detail, service.getMcpServer("mcp"));
        assertSame(missing, assertThrows(NacosException.class, () -> service.getMcpServer("mcp")));
    }
    
    @Test
    void releaseWithoutOptionalContentUsesLegacyEndpointOverload() throws Exception {
        when(service.releaseMcpServer(server, tools, (McpEndpointSpec) null)).thenReturn("mcp-id");
        
        assertEquals("mcp-id", service.releaseMcpServer(server, tools));
        verify(service).releaseMcpServer(server, tools, (McpEndpointSpec) null);
        verify(service, never()).releaseMcpServer(server, tools, null, null);
    }
    
    @Test
    void releaseWithResourcesPreservesContent() throws Exception {
        McpResourceSpecification resources = new McpResourceSpecification();
        when(service.releaseMcpServer(server, tools, resources, null)).thenReturn("mcp-id");
        
        assertEquals("mcp-id", service.releaseMcpServer(server, tools, resources));
        verify(service).releaseMcpServer(server, tools, resources, null);
    }
    
    @Test
    void falseDraftFlagPreservesLegacyFourArgumentOverride() throws Exception {
        McpResourceSpecification resources = new McpResourceSpecification();
        McpEndpointSpec endpoint = new McpEndpointSpec();
        when(service.releaseMcpServer(server, tools, resources, endpoint)).thenReturn("complete");
        when(service.releaseMcpServer(server, tools, null, null)).thenReturn("minimal");
        
        assertEquals("complete",
            service.releaseMcpServer(server, tools, resources, endpoint, false));
        assertEquals("minimal", service.releaseMcpServer(server, tools, false));
        verify(service).releaseMcpServer(server, tools, resources, endpoint);
        verify(service).releaseMcpServer(server, tools, null, null);
    }
    
    @Test
    void unsupportedDraftIsRejectedWithoutDirectPublication() throws Exception {
        NacosException failure = assertThrows(NacosException.class,
            () -> service.releaseMcpServer(server, tools, true));
        assertEquals(NacosException.SERVER_NOT_IMPLEMENTED, failure.getErrCode());
        verify(service, never()).releaseMcpServer(server, tools, null, null);
    }
    
    @Test
    void draftConvenienceMethodHonorsSupportingImplementation() throws Exception {
        doReturn("draft-id").when(service).releaseMcpServer(server, tools, null, null, true);
        
        assertEquals("draft-id", service.releaseMcpServer(server, tools, true));
        verify(service).releaseMcpServer(server, tools, null, null, true);
        verify(service, never()).releaseMcpServer(server, tools, null, null);
    }
    
    @Test
    void unversionedEndpointAndSubscriptionsPreserveArguments() throws Exception {
        McpServerDetailInfo detail = new McpServerDetailInfo();
        AbstractNacosMcpServerListener listener = mock(AbstractNacosMcpServerListener.class);
        when(service.subscribeMcpServer("mcp", null, listener)).thenReturn(detail);
        
        service.registerMcpServerEndpoint("mcp", "127.0.0.1", 8080);
        assertSame(detail, service.subscribeMcpServer("mcp", listener));
        service.unsubscribeMcpServer("mcp", listener);
        
        verify(service).registerMcpServerEndpoint("mcp", "127.0.0.1", 8080, null);
        verify(service).unsubscribeMcpServer("mcp", null, listener);
    }
}
