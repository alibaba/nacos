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

package com.alibaba.nacos.ai.web;

import com.alibaba.nacos.ai.constant.AiResourceConstants;
import com.alibaba.nacos.ai.model.AiResource;
import com.alibaba.nacos.ai.service.mcp.McpLifecycleManagementStateService;
import com.alibaba.nacos.ai.service.mcp.McpResourceLocator;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.auth.parser.http.AiHttpResourceParser;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class McpLifecycleIdentityFilterTest {
    
    private static final String MCP_ID = "4d7939c0-72ea-4ef4-b232-418d1e16b45c";
    
    @Mock
    private McpLifecycleManagementStateService managementStateService;
    
    @Mock
    private McpResourceLocator resourceLocator;
    
    private McpLifecycleIdentityFilter filter;
    
    @BeforeEach
    void setUp() {
        filter = new McpLifecycleIdentityFilter(managementStateService, resourceLocator);
    }
    
    @Test
    void testManagedIdOnlyReadSetsCanonicalNameForAuthParser() throws Exception {
        MockHttpServletRequest request = request("GET");
        request.setParameter("mcpId", MCP_ID);
        when(managementStateService.isLifecycleManaged()).thenReturn(true);
        when(resourceLocator.locate("public", null, MCP_ID)).thenReturn(resource());
        AtomicReference<Object> observedName = new AtomicReference<>();
        FilterChain chain = (servletRequest, servletResponse) -> observedName.set(
            servletRequest.getAttribute(AiHttpResourceParser.MCP_CANONICAL_NAME_ATTRIBUTE));
        
        filter.doFilter(request, new MockHttpServletResponse(), chain);
        
        assertEquals("demo-mcp", observedName.get());
    }
    
    @Test
    void testSyncingReadDoesNotConsultLifecycleRows() throws Exception {
        MockHttpServletRequest request = request("GET");
        request.setParameter("mcpId", MCP_ID);
        when(managementStateService.isLifecycleManaged()).thenReturn(false);
        FilterChain chain = org.mockito.Mockito.mock(FilterChain.class);
        MockHttpServletResponse response = new MockHttpServletResponse();
        
        filter.doFilter(request, response, chain);
        
        verify(resourceLocator, never()).locate(any(), any(), any());
        verify(chain).doFilter(request, response);
    }
    
    @Test
    void testNameReadAndNonGetRequestAreNotNormalized() throws Exception {
        when(managementStateService.isLifecycleManaged()).thenReturn(true);
        MockHttpServletRequest named = request("GET");
        named.setParameter("mcpName", "demo-mcp");
        named.setParameter("mcpId", MCP_ID);
        filter.doFilter(named, new MockHttpServletResponse(),
            org.mockito.Mockito.mock(FilterChain.class));
        
        MockHttpServletRequest post = request("POST");
        post.setParameter("mcpId", MCP_ID);
        filter.doFilter(post, new MockHttpServletResponse(),
            org.mockito.Mockito.mock(FilterChain.class));
        
        verify(resourceLocator, never()).locate(any(), any(), any());
    }
    
    @Test
    void testResolutionFailureDoesNotExposeLookupResultAndContinues() throws Exception {
        MockHttpServletRequest request = request("GET");
        request.setParameter("mcpId", MCP_ID);
        when(managementStateService.isLifecycleManaged()).thenReturn(true);
        when(resourceLocator.locate(any(), any(), any())).thenThrow(new NacosApiException(
            NacosException.NOT_FOUND, ErrorCode.MCP_SERVER_NOT_FOUND, "missing"));
        FilterChain chain = org.mockito.Mockito.mock(FilterChain.class);
        MockHttpServletResponse response = new MockHttpServletResponse();
        
        filter.doFilter(request, response, chain);
        
        assertNull(request.getAttribute(AiHttpResourceParser.MCP_CANONICAL_NAME_ATTRIBUTE));
        verify(chain).doFilter(request, response);
    }
    
    private MockHttpServletRequest request(String method) {
        return new MockHttpServletRequest(method, "/v3/admin/ai/mcp");
    }
    
    private AiResource resource() {
        AiResource result = new AiResource();
        result.setNamespaceId("public");
        result.setName("demo-mcp");
        result.setType(AiResourceConstants.RESOURCE_TYPE_MCP);
        return result;
    }
}
