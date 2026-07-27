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
    
    public static final String LEGACY_ENABLED_PROPERTY = "nacos.ai.resource.import.enabled";
    
    public static final String LEGACY_MCP_API_ENABLED_PROPERTY =
        "nacos.ai.resource.import.legacy-mcp-api-enabled";
    
    public static final String ALLOW_USER_URL_PROPERTY =
        "nacos.ai.resource.import.allow-user-url";
    
    private boolean enabled;
    
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
            return false;
        }
        String standardValue = properties.getProperty(ENABLED_PROPERTY);
        if (StringUtils.isNotBlank(standardValue)) {
            return Boolean.parseBoolean(standardValue.trim());
        }
        return getBoolean(properties, LEGACY_ENABLED_PROPERTY, false);
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
    
    public boolean isLegacyMcpImportApiEnabled() {
        return legacyMcpImportApiEnabled;
    }
    
    public void setLegacyMcpImportApiEnabled(boolean legacyMcpImportApiEnabled) {
        this.legacyMcpImportApiEnabled = legacyMcpImportApiEnabled;
    }
    
    public boolean isAllowUserUrl() {
        return allowUserUrl;
    }
    
    public void setAllowUserUrl(boolean allowUserUrl) {
        this.allowUserUrl = allowUserUrl;
    }
}
