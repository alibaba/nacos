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

import com.alibaba.nacos.plugin.ai.importer.AiResourceImportConstants;
import com.alibaba.nacos.plugin.ai.importer.defaultimpl.AbstractAiResourceImportServiceBuilder;
import com.alibaba.nacos.plugin.ai.importer.spi.AiResourceImportService;

import java.util.Collections;

/**
 * Configurable MCP Registry protocol import plugin.
 *
 * @author xiweng.yy
 * @since 3.3.0
 */
public class McpRegistryImportServiceBuilder
    extends AbstractAiResourceImportServiceBuilder {
    
    public static final String PLUGIN_NAME = "mcp-registry-protocol";
    
    public static final String IMPORTER_TYPE = "mcp-registry";
    
    public McpRegistryImportServiceBuilder() {
        this(PLUGIN_NAME, "MCP Registry Protocol",
            "Import MCP servers from an MCP Registry protocol endpoint.", null, null);
    }
    
    protected McpRegistryImportServiceBuilder(String pluginName, String displayName,
        String description, String fixedEndpoint, String legacyPrefix) {
        super(pluginName, IMPORTER_TYPE, displayName, description,
            Collections.singleton(AiResourceImportConstants.RESOURCE_TYPE_MCP),
            fixedEndpoint, legacyPrefix);
    }
    
    @Override
    protected AiResourceImportService createService(ConfigSnapshot config) {
        return new McpRegistryImportService(config.getEndpoint(), config.isAllowHttp(),
            config.isAllowPrivateNetwork(), config.getMaxArtifactSize());
    }
}
