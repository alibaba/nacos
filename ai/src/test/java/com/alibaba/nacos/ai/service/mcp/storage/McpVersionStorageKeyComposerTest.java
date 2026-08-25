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

package com.alibaba.nacos.ai.service.mcp.storage;

import com.alibaba.nacos.ai.model.mcp.McpServerStorageInfo;
import com.alibaba.nacos.ai.model.mcp.McpVersionStorageDescriptor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class McpVersionStorageKeyComposerTest {
    
    private static final String MCP_ID = "4d7939c0-72ea-4ef4-b232-418d1e16b45c";
    
    @Test
    void testComposeUsesExistingPhysicalCoordinates() {
        McpVersionStorageDescriptor descriptor = McpVersionStorageKeyComposer.compose(
            "public", MCP_ID, "legacy-v1", true, true);
        
        assertEquals("public:mcp-server:" + MCP_ID + "-legacy-v1-mcp-server.json",
            descriptor.getServerKey());
        assertEquals("public:mcp-tools:" + MCP_ID + "-legacy-v1-mcp-tools.json",
            descriptor.getToolKey());
        assertEquals("public:mcp-resources:" + MCP_ID + "-legacy-v1-mcp-resources.json",
            descriptor.getResourceKey());
    }
    
    @Test
    void testComposeOmitsAbsentOptionalContent() {
        McpVersionStorageDescriptor descriptor = McpVersionStorageKeyComposer.compose(
            "namespace_1", MCP_ID, "1.0.0", false, false);
        assertNull(descriptor.getToolKey());
        assertNull(descriptor.getResourceKey());
    }
    
    @Test
    void testLegacyMappingUsesReferencedOptionalDataIds() {
        McpServerStorageInfo legacy = new McpServerStorageInfo();
        legacy.setToolsDescriptionRef("custom-1.0.0-mcp-tools.json");
        legacy.setResourceDescriptionRef("custom-1.0.0-mcp-resources.json");
        legacy.setPromptDescriptionRef("unchanged-prompt-ref");
        
        McpVersionStorageDescriptor descriptor = McpVersionStorageKeyComposer.fromLegacy(
            "public", MCP_ID, "1.0.0", legacy);
        
        assertEquals("public:mcp-tools:custom-1.0.0-mcp-tools.json",
            descriptor.getToolKey());
        assertEquals("public:mcp-resources:custom-1.0.0-mcp-resources.json",
            descriptor.getResourceKey());
        assertEquals("unchanged-prompt-ref", legacy.getPromptDescriptionRef());
    }
    
    @Test
    void testLegacyMappingTreatsBlankReferencesAsAbsent() {
        McpServerStorageInfo legacy = new McpServerStorageInfo();
        legacy.setToolsDescriptionRef(" ");
        legacy.setResourceDescriptionRef(null);
        McpVersionStorageDescriptor descriptor = McpVersionStorageKeyComposer.fromLegacy(
            "public", MCP_ID, "1.0.0", legacy);
        assertNull(descriptor.getToolKey());
        assertNull(descriptor.getResourceKey());
    }
    
    @Test
    void testRejectInvalidIdentityAndLegacyReference() {
        assertThrows(IllegalArgumentException.class, () -> McpVersionStorageKeyComposer.compose(
            "invalid namespace", MCP_ID, "1.0.0", false, false));
        assertThrows(IllegalArgumentException.class, () -> McpVersionStorageKeyComposer.compose(
            "public", "invalid", "1.0.0", false, false));
        assertThrows(IllegalArgumentException.class, () -> McpVersionStorageKeyComposer.compose(
            "public", MCP_ID, "", false, false));
        assertThrows(IllegalArgumentException.class, () -> McpVersionStorageKeyComposer.compose(
            "public", MCP_ID, repeat("v", 65), false, false));
        assertThrows(IllegalArgumentException.class, () -> McpVersionStorageKeyComposer.fromLegacy(
            "public", MCP_ID, "1.0.0", null));
        McpServerStorageInfo legacy = new McpServerStorageInfo();
        legacy.setToolsDescriptionRef("wrong.json");
        assertThrows(IllegalArgumentException.class, () -> McpVersionStorageKeyComposer.fromLegacy(
            "public", MCP_ID, "1.0.0", legacy));
    }
    
    private static String repeat(String value, int count) {
        StringBuilder result = new StringBuilder(value.length() * count);
        for (int i = 0; i < count; i++) {
            result.append(value);
        }
        return result.toString();
    }
}
