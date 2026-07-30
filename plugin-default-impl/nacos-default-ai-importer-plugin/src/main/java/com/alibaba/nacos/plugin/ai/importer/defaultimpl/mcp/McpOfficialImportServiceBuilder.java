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

/**
 * Built-in official MCP Registry import plugin.
 *
 * @author xiweng.yy
 * @since 3.3.0
 */
public class McpOfficialImportServiceBuilder extends McpRegistryImportServiceBuilder {
    
    public static final String PLUGIN_NAME = "mcp-official";
    
    public static final String OFFICIAL_ENDPOINT =
        "https://registry.modelcontextprotocol.io/v0/servers";
    
    /**
     * Legacy official MCP importer configuration prefix.
     *
     * @deprecated use {@code nacos.plugin.ai-resource-import.mcp-official.} instead. Planned for
     *     removal in Nacos 4.0.0.
     */
    @Deprecated
    private static final String LEGACY_PREFIX = "nacos.plugin.ai.importer.mcp.official.";
    
    public McpOfficialImportServiceBuilder() {
        super(PLUGIN_NAME, "Official MCP Registry",
            "Import MCP servers from the official MCP registry.", OFFICIAL_ENDPOINT,
            LEGACY_PREFIX);
    }
}
