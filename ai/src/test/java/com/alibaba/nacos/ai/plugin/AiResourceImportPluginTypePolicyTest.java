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

package com.alibaba.nacos.ai.plugin;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.alibaba.nacos.ai.config.AiEnabledFilter;
import com.alibaba.nacos.ai.importer.config.AiResourceImportProperties;
import com.alibaba.nacos.api.plugin.PluginType;
import com.alibaba.nacos.api.plugin.PluginTypeConfiguration;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiResourceImportPluginTypePolicyTest {
    
    private final AiResourceImportPluginTypePolicy policy =
        new AiResourceImportPluginTypePolicy();
    
    @Test
    void testTypeAndLoadingSwitches() {
        MapConfiguration configuration = new MapConfiguration();
        assertEquals(PluginType.AI_RESOURCE_IMPORT, policy.getPluginType());
        assertTrue(policy.isLoadingEnabled(configuration));
        
        configuration.setProperty(AiResourceImportProperties.LEGACY_ENABLED_PROPERTY, "false");
        assertFalse(policy.isLoadingEnabled(configuration));
        
        configuration.setProperty(AiResourceImportProperties.ENABLED_PROPERTY, " ");
        assertTrue(policy.isLoadingEnabled(configuration));
        
        configuration.setProperty(AiResourceImportProperties.ENABLED_PROPERTY, "false");
        assertFalse(policy.isLoadingEnabled(configuration));
        
        configuration.setProperty(AiResourceImportProperties.ENABLED_PROPERTY, "true");
        configuration.setProperty(AiEnabledFilter.AI_ENABLED_KEY, "false");
        assertFalse(policy.isLoadingEnabled(configuration));
        
        configuration.setProperty(AiEnabledFilter.AI_ENABLED_KEY, "true");
        configuration.setProperty("nacos.functionMode", "config");
        assertFalse(policy.isLoadingEnabled(configuration));
        
        configuration.setProperty("nacos.functionMode", "ai");
        assertTrue(policy.isLoadingEnabled(configuration));
    }
    
    @Test
    void testBuiltInDefaultStates() {
        MapConfiguration configuration = new MapConfiguration();
        
        assertTrue(policy.isPluginEnabledByDefault("mcp-official", configuration));
        assertFalse(policy.isPluginEnabledByDefault("mcp-registry-protocol", configuration));
        assertFalse(policy.isPluginEnabledByDefault("skills-well-known", configuration));
        assertTrue(policy.isPluginEnabledByDefault("skills-sh", configuration));
        assertFalse(policy.isPluginEnabledByDefault("external", configuration));
    }
    
    @Test
    void testStandardAndLegacyImplementationStates() {
        MapConfiguration configuration = new MapConfiguration();
        configuration.setProperty("nacos.plugin.ai.importer.mcp.official.enabled", "false");
        assertFalse(policy.isPluginEnabledByDefault("mcp-official", configuration));
        
        configuration.setProperty("nacos.plugin.ai-resource-import.mcp-official.enabled",
            "true");
        assertTrue(policy.isPluginEnabledByDefault("mcp-official", configuration));
        
        configuration.setProperty(
            "nacos.plugin.ai-resource-import.mcp-registry-protocol.enabled", "true");
        assertTrue(policy.isPluginEnabledByDefault("mcp-registry-protocol", configuration));
    }
    
    @Test
    void testLegacySkillsShStateEnablesAuthenticatedImporter() {
        MapConfiguration configuration = new MapConfiguration();
        configuration.setProperty("nacos.plugin.ai.importer.skills.skills-sh.enabled", "true");

        assertTrue(policy.isPluginEnabledByDefault("skills-sh-authenticated", configuration));
    }

    @Test
    void testCanonicalAuthenticatedImporterStateOverridesLegacyState() {
        MapConfiguration configuration = new MapConfiguration();
        configuration.setProperty("nacos.plugin.ai.importer.skills.skills-sh.enabled", "false");
        configuration.setProperty(
            "nacos.plugin.ai-resource-import.skills-sh-authenticated.enabled", "true");

        assertTrue(policy.isPluginEnabledByDefault("skills-sh-authenticated", configuration));
    }

    @Test
    void testCanonicalAuthenticatedImporterFalseIsNotOverriddenByLegacyTrue() {
        MapConfiguration configuration = new MapConfiguration();
        configuration.setProperty("nacos.plugin.ai.importer.skills.skills-sh.enabled", "true");
        configuration.setProperty(
            "nacos.plugin.ai-resource-import.skills-sh-authenticated.enabled", "false");

        assertFalse(policy.isPluginEnabledByDefault("skills-sh-authenticated", configuration));
    }

    @Test
    void testLegacySkillsShStateEmitsMigrationWarningForAuthenticatedImporter() {
        Logger logger =
            (Logger) LoggerFactory.getLogger(AiResourceImportPluginTypePolicy.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            MapConfiguration configuration = new MapConfiguration();
            configuration.setProperty("nacos.plugin.ai.importer.skills.skills-sh.enabled",
                "true");

            assertTrue(policy.isPluginEnabledByDefault("skills-sh-authenticated", configuration));
            assertTrue(appender.list.stream().anyMatch(event ->
                event.getFormattedMessage().contains("Legacy AI resource import plugin state key")
                    && event.getFormattedMessage()
                    .contains("nacos.plugin.ai-resource-import.skills-sh-authenticated.enabled")));
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    private static class MapConfiguration implements PluginTypeConfiguration {

        private final Map<String, String> properties = new HashMap<>();

        void setProperty(String key, String value) {
            properties.put(key, value);
        }
        
        @Override
        public String getProperty(String key) {
            return properties.get(key);
        }
        
        @Override
        public boolean containsProperty(String key) {
            return properties.containsKey(key);
        }
    }
}
