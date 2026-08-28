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

package com.alibaba.nacos.ai.service.mcp;

import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.core.context.RequestContext;
import com.alibaba.nacos.core.context.RequestContextHolder;
import org.springframework.stereotype.Component;

/**
 * Resolves the implementation for one complete MCP operation.
 *
 * <p>A real HTTP or gRPC request pins its first result in {@link RequestContext}. This prevents a
 * permanent marker observed in the middle of a compound request from selecting two authorities.</p>
 *
 * @author Nacos
 */
@Component
public class McpCompatibilityModeResolver {
    
    private static final String REQUEST_MODE_CONTEXT_KEY =
        McpCompatibilityModeResolver.class.getName() + ".mode";
    
    private final McpLifecycleManagementStateService stateService;
    
    public McpCompatibilityModeResolver(McpLifecycleManagementStateService stateService) {
        this.stateService = stateService;
    }
    
    /**
     * Resolve the operation authority for the current request.
     *
     * @return the request-pinned or durable management mode
     */
    public McpCompatibilityMode resolve() {
        RequestContext context = RequestContextHolder.getContext();
        boolean requestScoped = StringUtils.isNotBlank(
            context.getBasicContext().getRequestProtocol());
        if (requestScoped) {
            Object pinned = context.getExtensionContext(REQUEST_MODE_CONTEXT_KEY);
            if (pinned instanceof McpCompatibilityMode mode) {
                return mode;
            }
        }
        McpCompatibilityMode result = stateService.resolveMode();
        if (requestScoped) {
            context.addExtensionContext(REQUEST_MODE_CONTEXT_KEY, result);
        }
        return result;
    }
    
    /**
     * Check whether the local member may process managed MCP traffic.
     */
    public boolean localMemberSupportsManagedLifecycle() {
        return stateService.localMemberSupportsManagedLifecycle();
    }
}
