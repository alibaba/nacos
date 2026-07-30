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

import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.sys.env.EnvUtil;

import java.util.Properties;

/**
 * AI resource import module configuration.
 *
 * @author xiweng.yy
 * @since 3.2.1
 */
public class AiResourceImportProperties {
    
    public static final String ENABLED_PROPERTY = "nacos.plugin.ai-resource-import.enabled";
    
    /**
     * Legacy AI resource import module switch.
     *
     * @deprecated use {@link #ENABLED_PROPERTY} instead. Planned for removal in Nacos 4.0.0.
     */
    @Deprecated
    public static final String LEGACY_ENABLED_PROPERTY = "nacos.ai.resource.import.enabled";
    
    /**
     * Compatibility switch for the legacy MCP import API.
     *
     * @deprecated migrate to the unified {@code /v3/{admin|console}/ai/import/*} APIs. Planned
     *     for removal in Nacos 3.4.0.
     */
    @Deprecated
    public static final String LEGACY_MCP_API_ENABLED_PROPERTY =
        "nacos.ai.resource.import.legacy-mcp-api-enabled";
    
    /**
     * Compatibility switch allowing user URLs through the legacy MCP import API.
     *
     * @deprecated configure a managed AI resource import source endpoint instead. Planned for
     *     removal in Nacos 3.4.0.
     */
    @Deprecated
    public static final String ALLOW_USER_URL_PROPERTY =
        "nacos.ai.resource.import.allow-user-url";
    
    private boolean enabled = true;
    
    private boolean legacyMcpImportApiEnabled;
    
    private boolean allowUserUrl;
    
    /**
     * Load import module properties from the current Nacos environment.
     *
     * @return loaded properties
     */
    public static AiResourceImportProperties loadFromEnvironment() {
        return load(EnvUtil.getProperties());
    }
    
    /**
     * Load import module properties from raw properties.
     *
     * @param properties raw properties
     * @return loaded properties
     */
    public static AiResourceImportProperties load(Properties properties) {
        Properties values = properties == null ? new Properties() : properties;
        AiResourceImportProperties result = new AiResourceImportProperties();
        result.setEnabled(resolveEnabled(values));
        result.setLegacyMcpImportApiEnabled(getBoolean(values,
            LEGACY_MCP_API_ENABLED_PROPERTY, false));
        result.setAllowUserUrl(getBoolean(values, ALLOW_USER_URL_PROPERTY, false));
        return result;
    }
    
    /**
     * Resolve the module switch with the standard key taking precedence over the legacy alias.
     *
     * @param properties raw properties
     * @return whether AI resource import is enabled
     */
    public static boolean resolveEnabled(Properties properties) {
        if (properties == null) {
            return true;
        }
        if (properties.containsKey(ENABLED_PROPERTY)) {
            return !isExplicitlyFalse(properties.getProperty(ENABLED_PROPERTY));
        }
        return !isExplicitlyFalse(properties.getProperty(LEGACY_ENABLED_PROPERTY));
    }
    
    private static boolean isExplicitlyFalse(String value) {
        return value != null && Boolean.FALSE.toString().equalsIgnoreCase(value.trim());
    }
    
    private static boolean getBoolean(Properties properties, String key, boolean defaultValue) {
        String value = properties.getProperty(key);
        return StringUtils.isBlank(value) ? defaultValue : Boolean.parseBoolean(value.trim());
    }
    
    public boolean isEnabled() {
        return enabled;
    }
    
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
    
    /**
     * Whether the legacy MCP import API is enabled.
     *
     * @return whether the legacy API is enabled
     * @deprecated migrate clients to the unified AI resource import APIs. Planned for removal in
     *     Nacos 3.4.0.
     */
    @Deprecated
    public boolean isLegacyMcpImportApiEnabled() {
        return legacyMcpImportApiEnabled;
    }
    
    /**
     * Set whether the legacy MCP import API is enabled.
     *
     * @param legacyMcpImportApiEnabled whether the legacy API is enabled
     * @deprecated migrate clients to the unified AI resource import APIs. Planned for removal in
     *     Nacos 3.4.0.
     */
    @Deprecated
    public void setLegacyMcpImportApiEnabled(boolean legacyMcpImportApiEnabled) {
        this.legacyMcpImportApiEnabled = legacyMcpImportApiEnabled;
    }
    
    /**
     * Whether user-provided URLs are allowed by the legacy MCP import API.
     *
     * @return whether user-provided URLs are allowed
     * @deprecated configure a managed AI resource import source endpoint instead. Planned for
     *     removal in Nacos 3.4.0.
     */
    @Deprecated
    public boolean isAllowUserUrl() {
        return allowUserUrl;
    }
    
    /**
     * Set whether user-provided URLs are allowed by the legacy MCP import API.
     *
     * @param allowUserUrl whether user-provided URLs are allowed
     * @deprecated configure a managed AI resource import source endpoint instead. Planned for
     *     removal in Nacos 3.4.0.
     */
    @Deprecated
    public void setAllowUserUrl(boolean allowUserUrl) {
        this.allowUserUrl = allowUserUrl;
    }
}
