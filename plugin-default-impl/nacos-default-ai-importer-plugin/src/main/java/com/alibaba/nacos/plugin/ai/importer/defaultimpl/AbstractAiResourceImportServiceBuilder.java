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
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.api.plugin.ConfigItemDefinition;
import com.alibaba.nacos.api.plugin.ConfigItemEffectMode;
import com.alibaba.nacos.api.plugin.ConfigItemType;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.plugin.ai.importer.AiResourceImportConstants;
import com.alibaba.nacos.plugin.ai.importer.spi.AiResourceImportService;
import com.alibaba.nacos.plugin.ai.importer.spi.AiResourceImportServiceBuilder;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Common immutable configuration lifecycle for built-in AI resource import builders.
 *
 * @author Nacos
 */
public abstract class AbstractAiResourceImportServiceBuilder
    implements AiResourceImportServiceBuilder {
    
    private final String pluginName;
    
    private final String importerType;
    
    private final String defaultDisplayName;
    
    private final String defaultDescription;
    
    private final Set<String> resourceTypes;
    
    private final String fixedEndpoint;
    
    private final boolean configurableEndpoint;
    
    private final List<ConfigItemDefinition> definitions;
    
    private volatile ConfigSnapshot snapshot;
    
    private volatile boolean initialized;
    
    protected AbstractAiResourceImportServiceBuilder(String pluginName, String importerType,
        String defaultDisplayName, String defaultDescription, Set<String> resourceTypes,
        String fixedEndpoint, String legacyPrefix, String... endpointAliases) {
        this.pluginName = pluginName;
        this.importerType = importerType;
        this.defaultDisplayName = defaultDisplayName;
        this.defaultDescription = defaultDescription;
        this.resourceTypes =
            Collections.unmodifiableSet(new LinkedHashSet<>(resourceTypes));
        this.fixedEndpoint = fixedEndpoint;
        this.configurableEndpoint = fixedEndpoint == null;
        this.definitions = buildDefinitions(legacyPrefix, endpointAliases);
        this.snapshot = parseConfig(Collections.emptyMap());
    }
    
    @Override
    public final String pluginName() {
        return pluginName;
    }
    
    @Override
    public final String importerType() {
        return importerType;
    }
    
    @Override
    public final String displayName() {
        return snapshot.getDisplayName();
    }
    
    @Override
    public final String description() {
        return snapshot.getDescription();
    }
    
    @Override
    public final Set<String> supportedResourceTypes() {
        return resourceTypes;
    }
    
    @Override
    public final List<ConfigItemDefinition> getConfigDefinitions() {
        return definitions;
    }
    
    @Override
    public final synchronized void applyConfig(Map<String, String> config) {
        snapshot = parseConfig(config == null ? Collections.emptyMap() : config);
        initialized = true;
    }
    
    @Override
    public final Map<String, String> getCurrentConfig() {
        return new LinkedHashMap<>(snapshot.getValues());
    }
    
    @Override
    public final AiResourceImportService build() throws NacosException {
        if (!initialized) {
            throw invalid("AI resource import plugin has not been initialized: " + pluginName);
        }
        ConfigSnapshot current = snapshot;
        validateEndpoint(current);
        return createService(current);
    }
    
    protected abstract AiResourceImportService createService(ConfigSnapshot config)
        throws NacosException;
    
    private List<ConfigItemDefinition> buildDefinitions(String legacyPrefix,
        String... endpointAliases) {
        List<ConfigItemDefinition> result = new ArrayList<>();
        if (configurableEndpoint) {
            result.add(definition(AiResourceImportConstants.CONFIG_ENDPOINT,
                "Source endpoint", ConfigItemType.STRING, "",
                "External source endpoint used by this importer",
                ConfigItemEffectMode.RESTART, Arrays.asList(endpointAliases)));
            result.add(definition(AiResourceImportConstants.CONFIG_ALLOW_HTTP,
                "Allow HTTP", ConfigItemType.BOOLEAN, Boolean.FALSE.toString(),
                "Allow non-HTTPS requests to the configured endpoint",
                ConfigItemEffectMode.RESTART,
                legacyAliases(legacyPrefix, AiResourceImportConstants.CONFIG_ALLOW_HTTP)));
            result.add(definition(AiResourceImportConstants.CONFIG_ALLOW_PRIVATE_NETWORK,
                "Allow private network", ConfigItemType.BOOLEAN, Boolean.FALSE.toString(),
                "Allow requests to local and private network addresses",
                ConfigItemEffectMode.RESTART, legacyAliases(legacyPrefix,
                    AiResourceImportConstants.CONFIG_ALLOW_PRIVATE_NETWORK)));
        }
        result.add(definition(AiResourceImportConstants.CONFIG_DISPLAY_NAME,
            "Display name", ConfigItemType.STRING, defaultDisplayName,
            "Display name returned by the import source API", ConfigItemEffectMode.RUNTIME,
            displayNameAliases(legacyPrefix)));
        result.add(definition(AiResourceImportConstants.CONFIG_DESCRIPTION,
            "Description", ConfigItemType.STRING, defaultDescription,
            "Description returned by the import source API", ConfigItemEffectMode.RUNTIME,
            legacyAliases(legacyPrefix, AiResourceImportConstants.CONFIG_DESCRIPTION)));
        result.add(definition(AiResourceImportConstants.CONFIG_MAX_ITEM_COUNT,
            "Maximum item count", ConfigItemType.NUMBER,
            Integer.toString(AiResourceImportConstants.DEFAULT_MAX_ITEM_COUNT),
            "Maximum number of items processed by one import request",
            ConfigItemEffectMode.RUNTIME,
            legacyAliases(legacyPrefix, AiResourceImportConstants.CONFIG_MAX_ITEM_COUNT)));
        result.add(definition(AiResourceImportConstants.CONFIG_MAX_ARTIFACT_SIZE,
            "Maximum artifact size", ConfigItemType.NUMBER,
            Long.toString(AiResourceImportConstants.DEFAULT_MAX_ARTIFACT_SIZE),
            "Maximum accepted artifact size in bytes", ConfigItemEffectMode.RUNTIME,
            legacyAliases(legacyPrefix, AiResourceImportConstants.CONFIG_MAX_ARTIFACT_SIZE)));
        return Collections.unmodifiableList(result);
    }
    
    private ConfigItemDefinition definition(String key, String name, ConfigItemType type,
        String defaultValue, String description, ConfigItemEffectMode effectMode,
        List<String> aliases) {
        return new ConfigItemDefinition.Builder(key, name, type).defaultValue(defaultValue)
            .description(description).effectMode(effectMode).aliases(aliases).build();
    }
    
    private List<String> displayNameAliases(String legacyPrefix) {
        if (StringUtils.isBlank(legacyPrefix)) {
            return Collections.emptyList();
        }
        return Arrays.asList(legacyPrefix + AiResourceImportConstants.CONFIG_DISPLAY_NAME,
            legacyPrefix + "displayName");
    }
    
    private List<String> legacyAliases(String legacyPrefix, String itemKey) {
        return StringUtils.isBlank(legacyPrefix) ? Collections.emptyList()
            : Collections.singletonList(legacyPrefix + itemKey);
    }
    
    private ConfigSnapshot parseConfig(Map<String, String> config) {
        Map<String, String> values = new LinkedHashMap<>();
        String endpoint = fixedEndpoint;
        boolean allowHttp = false;
        boolean allowPrivateNetwork = false;
        if (configurableEndpoint) {
            endpoint = value(config, AiResourceImportConstants.CONFIG_ENDPOINT, "");
            allowHttp = Boolean.parseBoolean(value(config,
                AiResourceImportConstants.CONFIG_ALLOW_HTTP, Boolean.FALSE.toString()));
            allowPrivateNetwork = Boolean.parseBoolean(value(config,
                AiResourceImportConstants.CONFIG_ALLOW_PRIVATE_NETWORK,
                Boolean.FALSE.toString()));
            values.put(AiResourceImportConstants.CONFIG_ENDPOINT, endpoint);
            values.put(AiResourceImportConstants.CONFIG_ALLOW_HTTP,
                Boolean.toString(allowHttp));
            values.put(AiResourceImportConstants.CONFIG_ALLOW_PRIVATE_NETWORK,
                Boolean.toString(allowPrivateNetwork));
        }
        String displayName = value(config, AiResourceImportConstants.CONFIG_DISPLAY_NAME,
            defaultDisplayName);
        String description = value(config, AiResourceImportConstants.CONFIG_DESCRIPTION,
            defaultDescription);
        int maxItemCount = positiveInt(value(config,
            AiResourceImportConstants.CONFIG_MAX_ITEM_COUNT,
            Integer.toString(AiResourceImportConstants.DEFAULT_MAX_ITEM_COUNT)),
            AiResourceImportConstants.CONFIG_MAX_ITEM_COUNT);
        long maxArtifactSize = positiveLong(value(config,
            AiResourceImportConstants.CONFIG_MAX_ARTIFACT_SIZE,
            Long.toString(AiResourceImportConstants.DEFAULT_MAX_ARTIFACT_SIZE)),
            AiResourceImportConstants.CONFIG_MAX_ARTIFACT_SIZE);
        values.put(AiResourceImportConstants.CONFIG_DISPLAY_NAME, displayName);
        values.put(AiResourceImportConstants.CONFIG_DESCRIPTION, description);
        values.put(AiResourceImportConstants.CONFIG_MAX_ITEM_COUNT,
            Integer.toString(maxItemCount));
        values.put(AiResourceImportConstants.CONFIG_MAX_ARTIFACT_SIZE,
            Long.toString(maxArtifactSize));
        return new ConfigSnapshot(values, endpoint, allowHttp, allowPrivateNetwork,
            displayName, description, maxItemCount, maxArtifactSize);
    }
    
    private String value(Map<String, String> config, String key, String defaultValue) {
        String result = config.get(key);
        return result == null ? defaultValue : result.trim();
    }
    
    private int positiveInt(String value, String key) {
        int result = Integer.parseInt(value);
        if (result <= 0) {
            throw new IllegalArgumentException(key + " must be greater than 0.");
        }
        return result;
    }
    
    private long positiveLong(String value, String key) {
        long result = Long.parseLong(value);
        if (result <= 0) {
            throw new IllegalArgumentException(key + " must be greater than 0.");
        }
        return result;
    }
    
    private void validateEndpoint(ConfigSnapshot config) throws NacosException {
        if (StringUtils.isBlank(config.getEndpoint())) {
            throw invalid("AI resource import plugin endpoint is missing: " + pluginName);
        }
        try {
            URI endpoint = URI.create(config.getEndpoint());
            if (!endpoint.isAbsolute() || StringUtils.isBlank(endpoint.getHost())) {
                throw invalid("AI resource import plugin endpoint is invalid: " + pluginName);
            }
        } catch (IllegalArgumentException e) {
            throw invalid("AI resource import plugin endpoint is invalid: " + pluginName);
        }
    }
    
    private NacosException invalid(String message) {
        return new NacosApiException(NacosException.INVALID_PARAM,
            ErrorCode.PARAMETER_VALIDATE_ERROR, message);
    }
    
    /**
     * Immutable configuration captured by one request-scoped service.
     */
    protected static final class ConfigSnapshot {
        
        private final Map<String, String> values;
        
        private final String endpoint;
        
        private final boolean allowHttp;
        
        private final boolean allowPrivateNetwork;
        
        private final String displayName;
        
        private final String description;
        
        private final int maxItemCount;
        
        private final long maxArtifactSize;
        
        private ConfigSnapshot(Map<String, String> values, String endpoint, boolean allowHttp,
            boolean allowPrivateNetwork, String displayName, String description,
            int maxItemCount, long maxArtifactSize) {
            this.values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
            this.endpoint = endpoint;
            this.allowHttp = allowHttp;
            this.allowPrivateNetwork = allowPrivateNetwork;
            this.displayName = displayName;
            this.description = description;
            this.maxItemCount = maxItemCount;
            this.maxArtifactSize = maxArtifactSize;
        }
        
        public Map<String, String> getValues() {
            return values;
        }
        
        public String getEndpoint() {
            return endpoint;
        }
        
        public boolean isAllowHttp() {
            return allowHttp;
        }
        
        public boolean isAllowPrivateNetwork() {
            return allowPrivateNetwork;
        }
        
        public String getDisplayName() {
            return displayName;
        }
        
        public String getDescription() {
            return description;
        }
        
        public int getMaxItemCount() {
            return maxItemCount;
        }
        
        public long getMaxArtifactSize() {
            return maxArtifactSize;
        }
    }
}
