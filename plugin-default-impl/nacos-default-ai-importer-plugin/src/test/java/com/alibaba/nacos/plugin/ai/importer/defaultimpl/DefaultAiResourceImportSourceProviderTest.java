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

package com.alibaba.nacos.plugin.ai.importer.defaultimpl;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.plugin.ai.importer.AiResourceImportConstants;
import com.alibaba.nacos.plugin.ai.importer.defaultimpl.mcp.McpRegistryImportServiceBuilder;
import com.alibaba.nacos.plugin.ai.importer.defaultimpl.skill.SkillsShImportServiceBuilder;
import com.alibaba.nacos.plugin.ai.importer.defaultimpl.skill.SkillWellKnownImportServiceBuilder;
import com.alibaba.nacos.plugin.ai.importer.model.AiResourceImportSource;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultAiResourceImportSourceProviderTest {
    
    private final DefaultAiResourceImportSourceProvider provider =
        new DefaultAiResourceImportSourceProvider();
    
    @Test
    void testLoadDefaultEnabledSources() throws Exception {
        List<AiResourceImportSource> sources = toList(provider.loadSources(new Properties()));
        
        assertEquals(2, sources.size());
        assertEquals("mcp-official", sources.get(0).getSourceId());
        assertEquals(McpRegistryImportServiceBuilder.IMPORTER_TYPE,
            sources.get(0).getPluginName());
        assertEquals("skills-sh", sources.get(1).getSourceId());
        assertEquals(SkillsShImportServiceBuilder.IMPORTER_TYPE, sources.get(1).getPluginName());
    }
    
    @Test
    void testLoadOfficialMcpSourceWithDefaultEndpoint() throws Exception {
        Properties properties = new Properties();
        properties.setProperty("nacos.plugin.ai.importer.mcp.official.enabled", "true");
        properties.setProperty("nacos.plugin.ai.importer.skills.skills-sh.enabled", "false");
        
        List<AiResourceImportSource> sources = toList(provider.loadSources(properties));
        
        assertEquals(1, sources.size());
        AiResourceImportSource source = sources.get(0);
        assertEquals("mcp-official", source.getSourceId());
        assertEquals(McpRegistryImportServiceBuilder.IMPORTER_TYPE, source.getPluginName());
        assertEquals(DefaultAiResourceImportSourceProvider.OFFICIAL_MCP_ENDPOINT,
            source.getEndpoint());
        assertEquals(AiResourceImportConstants.RESOURCE_TYPE_MCP,
            source.getResourceTypes().get(0));
        assertTrue(source.isEnabled());
    }
    
    @Test
    void testLoadSkillWellKnownSourceFromUrl() throws Exception {
        Properties properties = new Properties();
        properties.setProperty("nacos.plugin.ai.importer.mcp.official.enabled", "false");
        properties.setProperty("nacos.plugin.ai.importer.skills.skills-sh.enabled", "false");
        properties.setProperty("nacos.plugin.ai.importer.skills.well-known.enabled", "true");
        properties.setProperty("nacos.plugin.ai.importer.skills.well-known.url",
            "https://developers.cloudflare.com");
        properties.setProperty("nacos.plugin.ai.importer.skills.well-known.source-id",
            "cloudflare-skills");
        
        List<AiResourceImportSource> sources = toList(provider.loadSources(properties));
        
        assertEquals(1, sources.size());
        AiResourceImportSource source = sources.get(0);
        assertEquals("cloudflare-skills", source.getSourceId());
        assertEquals(SkillWellKnownImportServiceBuilder.IMPORTER_TYPE, source.getPluginName());
        assertEquals("https://developers.cloudflare.com", source.getEndpoint());
        assertEquals(AiResourceImportConstants.RESOURCE_TYPE_SKILL,
            source.getResourceTypes().get(0));
    }
    
    @Test
    void testLoadSkillWellKnownRejectsMissingUrl() {
        Properties properties = new Properties();
        properties.setProperty("nacos.plugin.ai.importer.mcp.official.enabled", "false");
        properties.setProperty("nacos.plugin.ai.importer.skills.skills-sh.enabled", "false");
        properties.setProperty("nacos.plugin.ai.importer.skills.well-known.enabled", "true");
        
        assertThrows(NacosException.class, () -> provider.loadSources(properties));
    }
    
    @Test
    void testLoadSkillsShSourceWithDefaultEndpoint() throws Exception {
        Properties properties = new Properties();
        properties.setProperty("nacos.plugin.ai.importer.mcp.official.enabled", "false");
        properties.setProperty("nacos.plugin.ai.importer.skills.skills-sh.enabled", "true");
        
        List<AiResourceImportSource> sources = toList(provider.loadSources(properties));
        
        assertEquals(1, sources.size());
        AiResourceImportSource source = sources.get(0);
        assertEquals("skills-sh", source.getSourceId());
        assertEquals(SkillsShImportServiceBuilder.IMPORTER_TYPE, source.getPluginName());
        assertEquals(DefaultAiResourceImportSourceProvider.SKILLS_SH_ENDPOINT,
            source.getEndpoint());
        assertEquals(AiResourceImportConstants.RESOURCE_TYPE_SKILL,
            source.getResourceTypes().get(0));
    }
    
    @Test
    void testLoadSourceSecurityOptions() throws Exception {
        Properties properties = new Properties();
        properties.setProperty("nacos.plugin.ai.importer.mcp.official.enabled", "false");
        properties.setProperty("nacos.plugin.ai.importer.skills.skills-sh.enabled", "true");
        properties.setProperty("nacos.plugin.ai.importer.skills.skills-sh.allow-http", "true");
        properties.setProperty("nacos.plugin.ai.importer.skills.skills-sh.allow-private-network",
            "true");
        
        List<AiResourceImportSource> sources = toList(provider.loadSources(properties));
        
        assertEquals(1, sources.size());
        AiResourceImportSource source = sources.get(0);
        assertNotNull(source.getProperties());
        assertEquals("true", source.getProperties().get("allow-http"));
        assertEquals("true", source.getProperties().get("allow-private-network"));
    }
    
    private List<AiResourceImportSource> toList(Collection<AiResourceImportSource> sources) {
        return new ArrayList<>(sources);
    }
}
