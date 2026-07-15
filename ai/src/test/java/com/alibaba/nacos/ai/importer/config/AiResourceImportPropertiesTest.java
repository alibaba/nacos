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

package com.alibaba.nacos.ai.importer.config;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiResourceImportPropertiesTest {
    
    @Test
    void testLoadSourcesFromProperties() {
        Properties raw = new Properties();
        raw.setProperty("nacos.ai.resource.import.enabled", "true");
        raw.setProperty("nacos.ai.resource.import.legacy-mcp-api-enabled", "true");
        raw.setProperty("nacos.ai.resource.import.default-connect-timeout-ms", "2000");
        raw.setProperty("nacos.ai.resource.import.default-max-artifact-size", "2048");
        raw.setProperty("nacos.ai.resource.import.sources[0].source-id", "mcp-official");
        raw.setProperty("nacos.ai.resource.import.sources[0].display-name", "MCP Official");
        raw.setProperty("nacos.ai.resource.import.sources[0].description", "official source");
        raw.setProperty("nacos.ai.resource.import.sources[0].plugin-name", "mcp-registry");
        raw.setProperty("nacos.ai.resource.import.sources[0].resource-types", "mcp, skill");
        raw.setProperty("nacos.ai.resource.import.sources[0].endpoint", "https://example.com");
        raw.setProperty("nacos.ai.resource.import.sources[0].auth-ref", "token-ref");
        raw.setProperty("nacos.ai.resource.import.sources[0].properties.protocol-version", "v0");
        
        AiResourceImportProperties properties = AiResourceImportProperties.load(raw);
        
        assertTrue(properties.isEnabled());
        assertTrue(properties.isLegacyMcpImportApiEnabled());
        assertFalse(properties.isAllowUserUrl());
        assertEquals(2000, properties.getDefaultConnectTimeoutMillis());
        assertEquals(2048, properties.getDefaultMaxArtifactSize());
        assertEquals(1, properties.getSources().size());
        AiResourceImportSourceConfig source = properties.getSources().get(0);
        assertEquals("mcp-official", source.getSourceId());
        assertEquals("MCP Official", source.getDisplayName());
        assertEquals("official source", source.getDescription());
        assertEquals("mcp-registry", source.getPluginName());
        assertEquals(Arrays.asList("mcp", "skill"), source.getResourceTypes());
        assertEquals("https://example.com", source.getEndpoint());
        assertEquals("token-ref", source.getAuthRef());
        assertEquals(2000, source.getConnectTimeoutMillis());
        assertEquals(2048, source.getMaxArtifactSize());
        assertEquals("v0", source.getProperties().get("protocol-version"));
    }
}
