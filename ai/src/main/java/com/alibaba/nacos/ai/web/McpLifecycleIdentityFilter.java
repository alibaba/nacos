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

import com.alibaba.nacos.ai.model.AiResource;
import com.alibaba.nacos.ai.service.mcp.McpLifecycleManagementStateService;
import com.alibaba.nacos.ai.service.mcp.McpResourceLocator;
import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.auth.parser.http.AiHttpResourceParser;
import com.alibaba.nacos.common.utils.StringUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Resolves deprecated Admin ID-only reads to a canonical MCP name before authorization.
 *
 * <p>The filter is inactive while lifecycle reconciliation is SYNCING. Resolution failures are
 * deliberately left to the normal authorization and controller flow, so this pre-authentication
 * normalization never exposes an identity lookup result to an unauthenticated caller.</p>
 *
 * @author Nacos
 */
public class McpLifecycleIdentityFilter extends OncePerRequestFilter {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(McpLifecycleIdentityFilter.class);
    
    private final McpLifecycleManagementStateService managementStateService;
    
    private final McpResourceLocator resourceLocator;
    
    public McpLifecycleIdentityFilter(McpLifecycleManagementStateService managementStateService,
        McpResourceLocator resourceLocator) {
        this.managementStateService = managementStateService;
        this.resourceLocator = resourceLocator;
    }
    
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
        FilterChain filterChain) throws ServletException, IOException {
        normalizeManagedIdOnlyRead(request);
        filterChain.doFilter(request, response);
    }
    
    private void normalizeManagedIdOnlyRead(HttpServletRequest request) {
        if (!"GET".equalsIgnoreCase(request.getMethod())
            || !managementStateService.isLifecycleManaged()
            || StringUtils.isNotBlank(request.getParameter("mcpName"))
            || StringUtils.isBlank(request.getParameter("mcpId"))) {
            return;
        }
        try {
            String namespaceId = request.getParameter("namespaceId");
            if (StringUtils.isBlank(namespaceId)) {
                namespaceId = AiConstants.Mcp.MCP_DEFAULT_NAMESPACE;
            }
            AiResource resource = resourceLocator.locate(namespaceId, null,
                request.getParameter("mcpId"));
            request.setAttribute(AiHttpResourceParser.MCP_CANONICAL_NAME_ATTRIBUTE,
                resource.getName());
        } catch (Exception e) {
            LOGGER.debug("Unable to normalize deprecated MCP ID-only read before authorization",
                e);
        }
    }
}
