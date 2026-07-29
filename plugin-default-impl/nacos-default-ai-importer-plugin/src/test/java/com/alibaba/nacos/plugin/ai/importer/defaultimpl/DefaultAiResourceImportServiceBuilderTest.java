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
import com.alibaba.nacos.api.plugin.ConfigItemDefinition;
import com.alibaba.nacos.api.plugin.ConfigItemEffectMode;
import com.alibaba.nacos.plugin.ai.importer.AiResourceImportConstants;
import com.alibaba.nacos.plugin.ai.importer.defaultimpl.mcp.McpOfficialImportServiceBuilder;
import com.alibaba.nacos.plugin.ai.importer.defaultimpl.mcp.McpRegistryImportService;
import com.alibaba.nacos.plugin.ai.importer.defaultimpl.mcp.McpRegistryImportServiceBuilder;
import com.alibaba.nacos.plugin.ai.importer.defaultimpl.skill.SkillWellKnownImportService;
import com.alibaba.nacos.plugin.ai.importer.defaultimpl.skill.SkillWellKnownImportServiceBuilder;
import com.alibaba.nacos.plugin.ai.importer.defaultimpl.skill.SkillsShImportService;
import com.alibaba.nacos.plugin.ai.importer.defaultimpl.skill.SkillsShImportServiceBuilder;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultAiResourceImportServiceBuilderTest {
    
    @Test
    void testConfigurableBuilderMetadataAndDefinitions() {
        SkillWellKnownImportServiceBuilder builder = new SkillWellKnownImportServiceBuilder();
        
        assertEquals(SkillWellKnownImportServiceBuilder.PLUGIN_NAME, builder.pluginName());
        assertEquals(SkillWellKnownImportServiceBuilder.IMPORTER_TYPE, builder.importerType());
        assertEquals("Skill Well-known Registry", builder.displayName());
        assertEquals("Import Skills from a well-known Skill discovery endpoint.",
            builder.description());
        assertEquals(Collections.singleton(AiResourceImportConstants.RESOURCE_TYPE_SKILL),
            builder.supportedResourceTypes());
        assertThrows(UnsupportedOperationException.class,
            () -> builder.supportedResourceTypes().add("other"));
        assertThrows(NacosException.class, builder::build);
        
        Map<String, ConfigItemDefinition> definitions = definitions(builder);
        assertEquals(7, definitions.size());
        assertEquals(ConfigItemEffectMode.RESTART,
            definitions.get(AiResourceImportConstants.CONFIG_ENDPOINT).getEffectMode());
        assertEquals(ConfigItemEffectMode.RUNTIME,
            definitions.get(AiResourceImportConstants.CONFIG_DISPLAY_NAME).getEffectMode());
        assertTrue(definitions.get(AiResourceImportConstants.CONFIG_ENDPOINT).getAliases()
            .contains("nacos.plugin.ai.importer.skills.well-known.url"));
        assertTrue(definitions.get(AiResourceImportConstants.CONFIG_DISPLAY_NAME).getAliases()
            .contains("nacos.plugin.ai.importer.skills.well-known.displayName"));
        assertThrows(UnsupportedOperationException.class,
            () -> builder.getConfigDefinitions().clear());
        
        builder.applyConfig(null);
        assertEquals("", builder.getCurrentConfig()
            .get(AiResourceImportConstants.CONFIG_ENDPOINT));
        assertThrows(NacosException.class, builder::build);
    }
    
    @Test
    void testConfigurableBuilderAppliesImmutableSnapshot() throws Exception {
        SkillWellKnownImportServiceBuilder builder = new SkillWellKnownImportServiceBuilder();
        Map<String, String> config = completeConfig(" https://registry.example.com/root ");
        
        builder.applyConfig(config);
        config.put(AiResourceImportConstants.CONFIG_DISPLAY_NAME, "mutated");
        
        Map<String, String> current = builder.getCurrentConfig();
        assertEquals("https://registry.example.com/root",
            current.get(AiResourceImportConstants.CONFIG_ENDPOINT));
        assertEquals("Custom source",
            current.get(AiResourceImportConstants.CONFIG_DISPLAY_NAME));
        assertEquals("Custom description",
            current.get(AiResourceImportConstants.CONFIG_DESCRIPTION));
        assertEquals("12", current.get(AiResourceImportConstants.CONFIG_MAX_ITEM_COUNT));
        assertEquals("34", current.get(AiResourceImportConstants.CONFIG_MAX_ARTIFACT_SIZE));
        assertEquals("Custom source", builder.displayName());
        assertEquals("Custom description", builder.description());
        current.clear();
        assertFalse(builder.getCurrentConfig().isEmpty());
        assertInstanceOf(SkillWellKnownImportService.class, builder.build());
        
        builder.applyConfig(Collections.singletonMap(AiResourceImportConstants.CONFIG_ENDPOINT,
            "https://registry.example.com/replaced"));
        assertEquals("Skill Well-known Registry", builder.displayName());
        assertEquals("500", builder.getCurrentConfig()
            .get(AiResourceImportConstants.CONFIG_MAX_ITEM_COUNT));
    }
    
    @Test
    void testBuilderRejectsInvalidConfiguration() {
        McpRegistryImportServiceBuilder builder = new McpRegistryImportServiceBuilder();
        
        assertThrows(NumberFormatException.class,
            () -> builder.applyConfig(config(AiResourceImportConstants.CONFIG_MAX_ITEM_COUNT,
                "not-a-number")));
        assertThrows(IllegalArgumentException.class,
            () -> builder.applyConfig(config(AiResourceImportConstants.CONFIG_MAX_ITEM_COUNT,
                "0")));
        assertThrows(NumberFormatException.class,
            () -> builder.applyConfig(config(AiResourceImportConstants.CONFIG_MAX_ARTIFACT_SIZE,
                "not-a-number")));
        assertThrows(IllegalArgumentException.class,
            () -> builder.applyConfig(config(AiResourceImportConstants.CONFIG_MAX_ARTIFACT_SIZE,
                "-1")));
        
        builder.applyConfig(config(AiResourceImportConstants.CONFIG_ENDPOINT, "relative"));
        assertThrows(NacosException.class, builder::build);
        builder.applyConfig(config(AiResourceImportConstants.CONFIG_ENDPOINT, "https:///path"));
        assertThrows(NacosException.class, builder::build);
        builder.applyConfig(config(AiResourceImportConstants.CONFIG_ENDPOINT, "https://[bad"));
        assertThrows(NacosException.class, builder::build);
    }
    
    @Test
    void testAllBuiltInBuildersCreateExpectedServices() throws Exception {
        McpRegistryImportServiceBuilder mcpProtocol = new McpRegistryImportServiceBuilder();
        mcpProtocol.applyConfig(config(AiResourceImportConstants.CONFIG_ENDPOINT,
            "https://registry.example.com/v0/servers"));
        assertInstanceOf(McpRegistryImportService.class, mcpProtocol.build());
        
        McpOfficialImportServiceBuilder mcpOfficial = new McpOfficialImportServiceBuilder();
        mcpOfficial.applyConfig(Collections.emptyMap());
        assertEquals(McpOfficialImportServiceBuilder.PLUGIN_NAME, mcpOfficial.pluginName());
        assertEquals("Official MCP Registry", mcpOfficial.displayName());
        assertEquals("Import MCP servers from the official MCP registry.",
            mcpOfficial.description());
        assertFixedEndpointDefinitions(mcpOfficial.getConfigDefinitions());
        assertInstanceOf(McpRegistryImportService.class, mcpOfficial.build());
        
        SkillsShImportServiceBuilder skillsSh = new SkillsShImportServiceBuilder();
        Map<String, String> ignoredEndpoint = config(AiResourceImportConstants.CONFIG_ENDPOINT,
            "http://ignored.invalid");
        skillsSh.applyConfig(ignoredEndpoint);
        assertEquals(SkillsShImportServiceBuilder.PLUGIN_NAME, skillsSh.pluginName());
        assertEquals("skills.sh", skillsSh.displayName());
        assertEquals("Import Skills from skills.sh.", skillsSh.description());
        assertFixedEndpointDefinitions(skillsSh.getConfigDefinitions());
        assertFalse(skillsSh.getCurrentConfig()
            .containsKey(AiResourceImportConstants.CONFIG_ENDPOINT));
        assertInstanceOf(SkillsShImportService.class, skillsSh.build());
    }
    
    private void assertFixedEndpointDefinitions(List<ConfigItemDefinition> definitions) {
        Map<String, ConfigItemDefinition> result = definitions.stream()
            .collect(Collectors.toMap(ConfigItemDefinition::getKey, definition -> definition));
        assertEquals(4, result.size());
        assertFalse(result.containsKey(AiResourceImportConstants.CONFIG_ENDPOINT));
        assertFalse(result.containsKey(AiResourceImportConstants.CONFIG_ALLOW_HTTP));
        assertFalse(result.containsKey(AiResourceImportConstants.CONFIG_ALLOW_PRIVATE_NETWORK));
    }
    
    private Map<String, ConfigItemDefinition> definitions(
        AbstractAiResourceImportServiceBuilder builder) {
        return builder.getConfigDefinitions().stream()
            .collect(Collectors.toMap(ConfigItemDefinition::getKey, definition -> definition));
    }
    
    private Map<String, String> completeConfig(String endpoint) {
        Map<String, String> result = new HashMap<>();
        result.put(AiResourceImportConstants.CONFIG_ENDPOINT, endpoint);
        result.put(AiResourceImportConstants.CONFIG_ALLOW_HTTP, " true ");
        result.put(AiResourceImportConstants.CONFIG_ALLOW_PRIVATE_NETWORK, " true ");
        result.put(AiResourceImportConstants.CONFIG_DISPLAY_NAME, " Custom source ");
        result.put(AiResourceImportConstants.CONFIG_DESCRIPTION, " Custom description ");
        result.put(AiResourceImportConstants.CONFIG_MAX_ITEM_COUNT, " 12 ");
        result.put(AiResourceImportConstants.CONFIG_MAX_ARTIFACT_SIZE, " 34 ");
        return result;
    }
    
    private Map<String, String> config(String key, String value) {
        return Collections.singletonMap(key, value);
    }
}
