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

import org.springframework.stereotype.Component;

/**
 * Resolves the implementation for one complete MCP operation.
 *
 * <p>The lifecycle operation engine is intentionally not activated by this change. Until the
 * cluster-wide cutover gate and permanent marker are introduced, every production request remains
 * in {@link McpCompatibilityMode#SYNCING}.</p>
 *
 * @author Nacos
 */
@Component
public class McpCompatibilityModeResolver {
    
    /**
     * Resolve the operation authority for the current request.
     *
     * @return {@link McpCompatibilityMode#SYNCING} until atomic cutover is implemented
     */
    public McpCompatibilityMode resolve() {
        return McpCompatibilityMode.SYNCING;
    }
}
