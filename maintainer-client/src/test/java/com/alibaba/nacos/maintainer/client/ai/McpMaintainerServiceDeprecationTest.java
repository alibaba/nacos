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

package com.alibaba.nacos.maintainer.client.ai;

import com.alibaba.nacos.api.ai.model.mcp.McpEndpointSpec;
import com.alibaba.nacos.api.ai.model.mcp.McpServerBasicInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpServerDraftRequest;
import com.alibaba.nacos.api.ai.model.mcp.McpToolSpecification;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the MCP Maintainer SDK deprecation boundary.
 *
 * @author xiweng.yy
 */
class McpMaintainerServiceDeprecationTest {
    
    private static final Set<String> DEPRECATED_METHOD_NAMES = new HashSet<>(Arrays.asList(
        "getMcpServerDetail", "createLocalMcpServer", "createRemoteMcpServer", "createMcpServer",
        "updateMcpServer"));
    
    private static final Set<String> RETAINED_METHOD_NAMES = new HashSet<>(Arrays.asList(
        "listMcpServer", "searchMcpServer", "deleteMcpServer"));
    
    private static final Set<String> CANONICAL_METHOD_NAMES = new HashSet<>(Arrays.asList(
        "listMcpServerVersions", "getMcpServerVersion", "deleteMcpServerDraft",
        "submitMcpServerVersion", "publishMcpServerVersion", "forcePublishMcpServerVersion",
        "redraftMcpServerVersion", "onlineMcpServerVersion", "offlineMcpServerVersion",
        "updateMcpServerLabels"));
    
    @Test
    void legacyMethodsWithCanonicalReplacementsShouldBeDeprecated() {
        int deprecatedMethodCount = 0;
        int retainedMethodCount = 0;
        int canonicalMethodCount = 0;
        for (Method method : McpMaintainerService.class.getDeclaredMethods()) {
            if (DEPRECATED_METHOD_NAMES.contains(method.getName())) {
                if (isCanonicalDraftOverload(method)) {
                    assertFalse(method.isAnnotationPresent(Deprecated.class), method::toString);
                    canonicalMethodCount++;
                } else {
                    assertTrue(method.isAnnotationPresent(Deprecated.class), method::toString);
                    deprecatedMethodCount++;
                }
            }
            if (RETAINED_METHOD_NAMES.contains(method.getName())) {
                assertFalse(method.isAnnotationPresent(Deprecated.class), method::toString);
                retainedMethodCount++;
            }
            if (CANONICAL_METHOD_NAMES.contains(method.getName())) {
                assertFalse(method.isAnnotationPresent(Deprecated.class), method::toString);
                canonicalMethodCount++;
            }
        }
        assertEquals(21, deprecatedMethodCount);
        assertEquals(9, retainedMethodCount);
        assertEquals(24, canonicalMethodCount);
    }
    
    @Test
    void compatibilityImplementationShouldExposeDeprecation() throws NoSuchMethodException {
        assertTrue(AiMaintainerService.class.getDeclaredMethod("getMcpServerDetail", String.class,
            String.class, String.class, String.class).isAnnotationPresent(Deprecated.class));
        assertTrue(AiMaintainerService.class.getDeclaredMethod("createMcpServer", String.class,
            String.class, McpServerBasicInfo.class, McpToolSpecification.class,
            McpEndpointSpec.class).isAnnotationPresent(Deprecated.class));
        assertTrue(AiMaintainerService.class.getDeclaredMethod("updateMcpServer", String.class,
            String.class, boolean.class, McpServerBasicInfo.class, McpToolSpecification.class,
            McpEndpointSpec.class, boolean.class).isAnnotationPresent(Deprecated.class));
        assertTrue(McpMaintainerServiceImpl.class.getDeclaredMethod("getMcpServerDetail",
            String.class, String.class, String.class, String.class)
            .isAnnotationPresent(Deprecated.class));
        assertTrue(McpMaintainerServiceImpl.class.getDeclaredMethod("createMcpServer",
            String.class, String.class, McpServerBasicInfo.class, McpToolSpecification.class,
            McpEndpointSpec.class)
            .isAnnotationPresent(Deprecated.class));
        assertTrue(McpMaintainerServiceImpl.class.getDeclaredMethod("updateMcpServer",
            String.class, String.class, boolean.class, McpServerBasicInfo.class,
            McpToolSpecification.class, McpEndpointSpec.class, boolean.class)
            .isAnnotationPresent(Deprecated.class));
        assertFalse(AiMaintainerService.class.getDeclaredMethod("createMcpServer", String.class,
            McpServerDraftRequest.class).isAnnotationPresent(Deprecated.class));
        assertFalse(AiMaintainerService.class.getDeclaredMethod("updateMcpServer", String.class,
            McpServerDraftRequest.class).isAnnotationPresent(Deprecated.class));
        assertFalse(McpMaintainerServiceImpl.class.getDeclaredMethod("createMcpServer",
            String.class, McpServerDraftRequest.class).isAnnotationPresent(Deprecated.class));
        assertFalse(McpMaintainerServiceImpl.class.getDeclaredMethod("updateMcpServer",
            String.class, McpServerDraftRequest.class).isAnnotationPresent(Deprecated.class));
    }
    
    private boolean isCanonicalDraftOverload(Method method) {
        return Arrays.asList(method.getParameterTypes()).contains(McpServerDraftRequest.class);
    }
}
