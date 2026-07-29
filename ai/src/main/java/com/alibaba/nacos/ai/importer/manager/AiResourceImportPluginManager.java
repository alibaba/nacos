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

package com.alibaba.nacos.ai.importer.manager;

import com.alibaba.nacos.ai.importer.config.AiResourceImportProperties;
import com.alibaba.nacos.api.ai.model.importer.AiResourceImportSourceInfo;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.api.plugin.PluginStateCheckerHolder;
import com.alibaba.nacos.api.plugin.PluginType;
import com.alibaba.nacos.common.spi.NacosServiceLoader;
import com.alibaba.nacos.common.spi.PluginRegistryUtils;
import com.alibaba.nacos.common.utils.CollectionUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.plugin.ai.importer.spi.AiResourceImportServiceBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Stable registry and request router for managed AI resource import builders.
 *
 * @author xiweng.yy
 * @since 3.2.1
 */
@Service
public class AiResourceImportPluginManager {
    
    private static final Logger LOGGER =
        LoggerFactory.getLogger(AiResourceImportPluginManager.class);
    
    private static final List<String> DEFAULT_CAPABILITIES =
        List.of("search", "validate", "execute");
    
    private final Supplier<Collection<AiResourceImportServiceBuilder>> buildersSupplier;
    
    private Supplier<AiResourceImportProperties> propertiesSupplier;
    
    private volatile Map<String, AiResourceImportServiceBuilder> builders;
    
    public AiResourceImportPluginManager() {
        this(() -> NacosServiceLoader.load(AiResourceImportServiceBuilder.class));
    }
    
    AiResourceImportPluginManager(
        Supplier<Collection<AiResourceImportServiceBuilder>> buildersSupplier) {
        this.buildersSupplier = buildersSupplier;
        this.propertiesSupplier = AiResourceImportProperties::loadFromEnvironment;
    }
    
    /**
     * Load stable builder instances when the unified plugin provider is activated.
     *
     * @return immutable plugin map
     */
    public synchronized Map<String, AiResourceImportServiceBuilder> loadPlugins() {
        if (builders != null) {
            return builders;
        }
        Collection<AiResourceImportServiceBuilder> discovered = buildersSupplier.get();
        Map<String, AiResourceImportServiceBuilder> result = new LinkedHashMap<>();
        if (CollectionUtils.isNotEmpty(discovered)) {
            for (AiResourceImportServiceBuilder each : discovered) {
                String pluginName = each == null ? null : each.pluginName();
                PluginRegistryUtils.registerFirst(result,
                    PluginType.AI_RESOURCE_IMPORT.getType(), pluginName, each, LOGGER);
            }
        }
        builders = Collections.unmodifiableMap(result);
        return builders;
    }
    
    /**
     * List enabled import sources from managed builder metadata.
     *
     * @param resourceType optional resource type filter
     * @return source information
     * @throws NacosException if the module is disabled
     */
    public List<AiResourceImportSourceInfo> listSourceInfos(String resourceType)
        throws NacosException {
        requireModuleEnabled();
        List<AiResourceImportSourceInfo> result = new ArrayList<>();
        for (AiResourceImportServiceBuilder each : getLoadedPlugins().values()) {
            if (!isPluginEnabled(each.pluginName())
                || !supportsResourceType(each, resourceType)) {
                continue;
            }
            result.add(toSourceInfo(each));
        }
        return result;
    }
    
    /**
     * Resolve an enabled managed builder.
     *
     * @param pluginName managed plugin name and source id
     * @param resourceType expected resource type
     * @return resolved builder
     * @throws NacosException if routing fails
     */
    public AiResourceImportServiceBuilder resolveBuilder(String pluginName, String resourceType)
        throws NacosException {
        requireModuleEnabled();
        AiResourceImportServiceBuilder result = getLoadedPlugins().get(pluginName);
        if (result == null) {
            throw new NacosApiException(NacosException.NOT_FOUND,
                ErrorCode.RESOURCE_NOT_FOUND,
                "AI resource import plugin not found: " + pluginName);
        }
        if (!isPluginEnabled(pluginName)) {
            throw new NacosApiException(NacosException.INVALID_PARAM,
                ErrorCode.PARAMETER_VALIDATE_ERROR,
                "AI resource import plugin is disabled: " + pluginName);
        }
        if (!supportsResourceType(result, resourceType)) {
            throw new NacosApiException(NacosException.INVALID_PARAM,
                ErrorCode.PARAMETER_VALIDATE_ERROR,
                "AI resource import plugin does not support resource type: " + resourceType);
        }
        return result;
    }
    
    private Map<String, AiResourceImportServiceBuilder> getLoadedPlugins() {
        Map<String, AiResourceImportServiceBuilder> result = builders;
        return result == null ? Collections.emptyMap() : result;
    }
    
    private void requireModuleEnabled() throws NacosException {
        if (!propertiesSupplier.get().isEnabled()) {
            throw new NacosApiException(NacosException.SERVER_NOT_IMPLEMENTED,
                ErrorCode.API_FUNCTION_DISABLED, "AI resource import is disabled.");
        }
    }
    
    private boolean isPluginEnabled(String pluginName) {
        return PluginStateCheckerHolder.isPluginEnabled(
            PluginType.AI_RESOURCE_IMPORT.getType(), pluginName);
    }
    
    private boolean supportsResourceType(AiResourceImportServiceBuilder builder,
        String resourceType) {
        return StringUtils.isBlank(resourceType)
            || builder.supportedResourceTypes().contains(resourceType);
    }
    
    private AiResourceImportSourceInfo toSourceInfo(AiResourceImportServiceBuilder builder) {
        AiResourceImportSourceInfo result = new AiResourceImportSourceInfo();
        result.setSourceId(builder.pluginName());
        result.setPluginName(builder.importerType());
        result.setDisplayName(builder.displayName());
        result.setDescription(builder.description());
        result.setResourceTypes(new ArrayList<>(builder.supportedResourceTypes()));
        result.setEnabled(true);
        result.setCapabilities(DEFAULT_CAPABILITIES);
        return result;
    }
}
