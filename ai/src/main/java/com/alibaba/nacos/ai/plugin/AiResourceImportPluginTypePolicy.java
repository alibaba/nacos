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

import com.alibaba.nacos.ai.config.AiEnabledFilter;
import com.alibaba.nacos.ai.importer.config.AiResourceImportProperties;
import com.alibaba.nacos.api.plugin.PluginType;
import com.alibaba.nacos.api.plugin.PluginTypeConfiguration;
import com.alibaba.nacos.api.plugin.PluginTypePolicy;
import com.alibaba.nacos.common.utils.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * AI resource import plugin type policy.
 *
 * @author Nacos
 */
public class AiResourceImportPluginTypePolicy implements PluginTypePolicy {
    
    private static final Logger LOGGER =
        LoggerFactory.getLogger(AiResourceImportPluginTypePolicy.class);
    
    private static final String FUNCTION_MODE_PROPERTY = "nacos.functionMode";
    
    private static final String FUNCTION_MODE_AI = "ai";
    
    private static final String STANDARD_STATE_PREFIX = "nacos.plugin.ai-resource-import.";
    
    private static final Map<String, String> LEGACY_STATE_PROPERTIES =
        buildLegacyStateProperties();
    
    private static final Map<String, Boolean> DEFAULT_STATES = buildDefaultStates();
    
    @Override
    public PluginType getPluginType() {
        return PluginType.AI_RESOURCE_IMPORT;
    }
    
    @Override
    public boolean isLoadingEnabled(PluginTypeConfiguration configuration) {
        String functionMode = configuration.getProperty(FUNCTION_MODE_PROPERTY);
        boolean supportedMode = StringUtils.isEmpty(functionMode)
            || FUNCTION_MODE_AI.equals(functionMode);
        return supportedMode
            && configuration.getBooleanProperty(AiEnabledFilter.AI_ENABLED_KEY, true)
            && isImportEnabled(configuration);
    }
    
    @Override
    public boolean isPluginEnabledByDefault(String pluginName,
        PluginTypeConfiguration configuration) {
        String standardProperty = STANDARD_STATE_PREFIX + pluginName + ".enabled";
        if (configuration.containsProperty(standardProperty)) {
            warnIgnoredLegacyState(pluginName, configuration);
            return configuration.getBooleanProperty(standardProperty,
                defaultState(pluginName));
        }
        String legacyProperty = LEGACY_STATE_PROPERTIES.get(pluginName);
        if (legacyProperty != null && configuration.containsProperty(legacyProperty)) {
            LOGGER.warn("Legacy AI resource import plugin state key '{}' is deprecated; "
                + "migrate to '{}'.", legacyProperty, standardProperty);
            return configuration.getBooleanProperty(legacyProperty, defaultState(pluginName));
        }
        return defaultState(pluginName);
    }
    
    private boolean isImportEnabled(PluginTypeConfiguration configuration) {
        boolean hasStandard =
            configuration.containsProperty(AiResourceImportProperties.ENABLED_PROPERTY);
        boolean hasLegacy =
            configuration.containsProperty(AiResourceImportProperties.LEGACY_ENABLED_PROPERTY);
        if (hasStandard) {
            if (hasLegacy) {
                LOGGER.warn("Both '{}' and legacy '{}' are configured; the standard key wins.",
                    AiResourceImportProperties.ENABLED_PROPERTY,
                    AiResourceImportProperties.LEGACY_ENABLED_PROPERTY);
            }
            return !isExplicitlyFalse(
                configuration.getProperty(AiResourceImportProperties.ENABLED_PROPERTY));
        }
        if (hasLegacy) {
            LOGGER.warn("Legacy AI resource import switch '{}' is deprecated; migrate to '{}'.",
                AiResourceImportProperties.LEGACY_ENABLED_PROPERTY,
                AiResourceImportProperties.ENABLED_PROPERTY);
            return !isExplicitlyFalse(
                configuration.getProperty(AiResourceImportProperties.LEGACY_ENABLED_PROPERTY));
        }
        return true;
    }
    
    private static boolean isExplicitlyFalse(String value) {
        return value != null && Boolean.FALSE.toString().equalsIgnoreCase(value.trim());
    }
    
    private void warnIgnoredLegacyState(String pluginName,
        PluginTypeConfiguration configuration) {
        String legacyProperty = LEGACY_STATE_PROPERTIES.get(pluginName);
        if (legacyProperty != null && configuration.containsProperty(legacyProperty)) {
            LOGGER.warn("Both standard state key '{}{}.enabled' and legacy '{}' are configured; "
                + "the standard key wins.", STANDARD_STATE_PREFIX, pluginName, legacyProperty);
        }
    }
    
    private boolean defaultState(String pluginName) {
        return DEFAULT_STATES.getOrDefault(pluginName, false);
    }
    
    private static Map<String, String> buildLegacyStateProperties() {
        Map<String, String> result = new LinkedHashMap<>();
        result.put("mcp-official", "nacos.plugin.ai.importer.mcp.official.enabled");
        result.put("skills-well-known",
            "nacos.plugin.ai.importer.skills.well-known.enabled");
        result.put("skills-sh", "nacos.plugin.ai.importer.skills.skills-sh.enabled");
        result.put("skills-sh-authenticated",
            "nacos.plugin.ai.importer.skills.skills-sh.enabled");
        return Collections.unmodifiableMap(result);
    }
    
    private static Map<String, Boolean> buildDefaultStates() {
        Map<String, Boolean> result = new LinkedHashMap<>();
        result.put("mcp-official", true);
        result.put("mcp-registry-protocol", false);
        result.put("skills-well-known", false);
        result.put("skills-sh", true);
        return Collections.unmodifiableMap(result);
    }
}
