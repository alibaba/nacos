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

import java.util.Arrays;

/**
 * Immutable operation-scoped bytes for the three MCP Version content objects.
 *
 * @author Nacos
 */
public final class McpVersionStorageContents {
    
    private final byte[] serverContent;
    
    private final byte[] toolContent;
    
    private final byte[] resourceContent;
    
    public McpVersionStorageContents(byte[] serverContent, byte[] toolContent,
        byte[] resourceContent) {
        validateContent(serverContent, "Server", false);
        validateContent(toolContent, "Tools", true);
        validateContent(resourceContent, "Resources", true);
        this.serverContent = copy(serverContent);
        this.toolContent = copy(toolContent);
        this.resourceContent = copy(resourceContent);
    }
    
    public byte[] getServerContent() {
        return copy(serverContent);
    }
    
    public byte[] getToolContent() {
        return copy(toolContent);
    }
    
    public byte[] getResourceContent() {
        return copy(resourceContent);
    }
    
    boolean hasToolContent() {
        return toolContent != null;
    }
    
    boolean hasResourceContent() {
        return resourceContent != null;
    }
    
    private static void validateContent(byte[] content, String name, boolean optional) {
        if (content == null) {
            if (optional) {
                return;
            }
            throw new IllegalArgumentException("MCP " + name + " content must not be null");
        }
        if (content.length == 0) {
            throw new IllegalArgumentException("MCP " + name + " content must not be empty");
        }
    }
    
    private static byte[] copy(byte[] source) {
        return source == null ? null : Arrays.copyOf(source, source.length);
    }
}
