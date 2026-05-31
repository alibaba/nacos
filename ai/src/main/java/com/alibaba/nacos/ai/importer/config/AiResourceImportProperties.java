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

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * AI resource import runtime properties.
 *
 * @author xiweng.yy
 * @since 3.2.1
 */
public class AiResourceImportProperties {
    
    public static final String PREFIX = "nacos.ai.resource.import.";
    
    public static final int DEFAULT_CONNECT_TIMEOUT_MILLIS = 3000;
    
    public static final int DEFAULT_READ_TIMEOUT_MILLIS = 10000;
    
    public static final int DEFAULT_MAX_PAGE_COUNT = 20;
    
    public static final int DEFAULT_MAX_ITEM_COUNT = 500;
    
    public static final long DEFAULT_MAX_ARTIFACT_SIZE = 10L * 1024 * 1024;
    
    private static final String SOURCES_PREFIX = PREFIX + "sources[";
    
    private boolean enabled;
    
    private boolean legacyMcpImportApiEnabled;
    
    private boolean allowUserUrl;
    
    private int defaultConnectTimeoutMillis = DEFAULT_CONNECT_TIMEOUT_MILLIS;
    
    private int defaultReadTimeoutMillis = DEFAULT_READ_TIMEOUT_MILLIS;
    
    private int defaultMaxPageCount = DEFAULT_MAX_PAGE_COUNT;
    
    private int defaultMaxItemCount = DEFAULT_MAX_ITEM_COUNT;
    
    private long defaultMaxArtifactSize = DEFAULT_MAX_ARTIFACT_SIZE;
    
    private boolean blockPrivateNetwork = true;
    
    private List<AiResourceImportSourceConfig> sources = Collections.emptyList();
    
    /**
     * Load import properties from the current Nacos environment.
     *
     * @return loaded properties
     */
    public static AiResourceImportProperties loadFromEnvironment() {
        return load(EnvUtil.getProperties());
    }
    
    /**
     * Load import properties from raw properties.
     *
     * @param properties raw properties
     * @return loaded properties
     */
    public static AiResourceImportProperties load(Properties properties) {
        AiResourceImportProperties result = new AiResourceImportProperties();
        result.setEnabled(getBoolean(properties, PREFIX + "enabled", false));
        result.setLegacyMcpImportApiEnabled(getBoolean(properties,
            PREFIX + "legacy-mcp-api-enabled", false));
        result.setAllowUserUrl(getBoolean(properties, PREFIX + "allow-user-url", false));
        result.setDefaultConnectTimeoutMillis(getInt(properties,
            PREFIX + "default-connect-timeout-ms", DEFAULT_CONNECT_TIMEOUT_MILLIS));
        result.setDefaultReadTimeoutMillis(getInt(properties,
            PREFIX + "default-read-timeout-ms", DEFAULT_READ_TIMEOUT_MILLIS));
        result.setDefaultMaxPageCount(getInt(properties, PREFIX + "default-max-page-count",
            DEFAULT_MAX_PAGE_COUNT));
        result.setDefaultMaxItemCount(getInt(properties, PREFIX + "default-max-item-count",
            DEFAULT_MAX_ITEM_COUNT));
        result.setDefaultMaxArtifactSize(getLong(properties, PREFIX + "default-max-artifact-size",
            DEFAULT_MAX_ARTIFACT_SIZE));
        result.setBlockPrivateNetwork(getBoolean(properties, PREFIX + "block-private-network",
            true));
        result.setSources(loadSources(properties, result));
        return result;
    }
    
    private static List<AiResourceImportSourceConfig> loadSources(Properties properties,
        AiResourceImportProperties defaults) {
        List<AiResourceImportSourceConfig> result = new ArrayList<>();
        for (int i = 0; hasSourceIndex(properties, i); i++) {
            String base = SOURCES_PREFIX + i + "].";
            AiResourceImportSourceConfig source = new AiResourceImportSourceConfig();
            source.setSourceId(getString(properties, base, "source-id", "sourceId"));
            source.setDisplayName(getString(properties, base, "display-name", "displayName"));
            source.setDescription(getString(properties, base, "description", "description"));
            source.setPluginName(getString(properties, base, "plugin-name", "pluginName"));
            source.setResourceTypes(getStringList(properties, base, "resource-types",
                "resourceTypes"));
            source.setEndpoint(getString(properties, base, "endpoint", "endpoint"));
            source.setEnabled(getBoolean(properties, base + "enabled", true));
            source.setAuthRef(getString(properties, base, "auth-ref", "authRef"));
            source.setConnectTimeoutMillis(getInt(properties, base + "connect-timeout-ms",
                defaults.getDefaultConnectTimeoutMillis()));
            source.setReadTimeoutMillis(getInt(properties, base + "read-timeout-ms",
                defaults.getDefaultReadTimeoutMillis()));
            source.setMaxPageCount(getInt(properties, base + "max-page-count",
                defaults.getDefaultMaxPageCount()));
            source.setMaxItemCount(getInt(properties, base + "max-item-count",
                defaults.getDefaultMaxItemCount()));
            source.setMaxArtifactSize(getLong(properties, base + "max-artifact-size",
                defaults.getDefaultMaxArtifactSize()));
            source.setProperties(getSourceProperties(properties, base));
            result.add(source);
        }
        return result;
    }
    
    private static boolean hasSourceIndex(Properties properties, int index) {
        String base = SOURCES_PREFIX + index + "].";
        for (String each : properties.stringPropertyNames()) {
            if (each.startsWith(base)) {
                return true;
            }
        }
        return false;
    }
    
    private static String getString(Properties properties, String base, String kebabKey,
        String camelKey) {
        String value = properties.getProperty(base + kebabKey);
        if (StringUtils.isBlank(value)) {
            value = properties.getProperty(base + camelKey);
        }
        return StringUtils.isBlank(value) ? null : StringUtils.trim(value);
    }
    
    private static List<String> getStringList(Properties properties, String base, String kebabKey,
        String camelKey) {
        String value = getString(properties, base, kebabKey, camelKey);
        if (StringUtils.isBlank(value)) {
            return Collections.emptyList();
        }
        String[] values = value.split(",");
        List<String> result = new ArrayList<>(values.length);
        for (String each : values) {
            if (StringUtils.isNotBlank(each)) {
                result.add(StringUtils.trim(each));
            }
        }
        return result;
    }
    
    private static Map<String, String> getSourceProperties(Properties properties, String base) {
        String prefix = base + "properties.";
        Map<String, String> result = new LinkedHashMap<>();
        for (String each : properties.stringPropertyNames()) {
            if (each.startsWith(prefix)) {
                result.put(each.substring(prefix.length()), properties.getProperty(each));
            }
        }
        return result;
    }
    
    private static boolean getBoolean(Properties properties, String key, boolean defaultValue) {
        String value = properties.getProperty(key);
        return StringUtils.isBlank(value) ? defaultValue : Boolean.parseBoolean(value);
    }
    
    private static int getInt(Properties properties, String key, int defaultValue) {
        String value = properties.getProperty(key);
        return StringUtils.isBlank(value) ? defaultValue : Integer.parseInt(value);
    }
    
    private static long getLong(Properties properties, String key, long defaultValue) {
        String value = properties.getProperty(key);
        return StringUtils.isBlank(value) ? defaultValue : Long.parseLong(value);
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
    
    public int getDefaultConnectTimeoutMillis() {
        return defaultConnectTimeoutMillis;
    }
    
    public void setDefaultConnectTimeoutMillis(int defaultConnectTimeoutMillis) {
        this.defaultConnectTimeoutMillis = defaultConnectTimeoutMillis;
    }
    
    public int getDefaultReadTimeoutMillis() {
        return defaultReadTimeoutMillis;
    }
    
    public void setDefaultReadTimeoutMillis(int defaultReadTimeoutMillis) {
        this.defaultReadTimeoutMillis = defaultReadTimeoutMillis;
    }
    
    public int getDefaultMaxPageCount() {
        return defaultMaxPageCount;
    }
    
    public void setDefaultMaxPageCount(int defaultMaxPageCount) {
        this.defaultMaxPageCount = defaultMaxPageCount;
    }
    
    public int getDefaultMaxItemCount() {
        return defaultMaxItemCount;
    }
    
    public void setDefaultMaxItemCount(int defaultMaxItemCount) {
        this.defaultMaxItemCount = defaultMaxItemCount;
    }
    
    public long getDefaultMaxArtifactSize() {
        return defaultMaxArtifactSize;
    }
    
    public void setDefaultMaxArtifactSize(long defaultMaxArtifactSize) {
        this.defaultMaxArtifactSize = defaultMaxArtifactSize;
    }
    
    public boolean isBlockPrivateNetwork() {
        return blockPrivateNetwork;
    }
    
    public void setBlockPrivateNetwork(boolean blockPrivateNetwork) {
        this.blockPrivateNetwork = blockPrivateNetwork;
    }
    
    public List<AiResourceImportSourceConfig> getSources() {
        return sources;
    }
    
    public void setSources(List<AiResourceImportSourceConfig> sources) {
        this.sources = sources;
    }
}
