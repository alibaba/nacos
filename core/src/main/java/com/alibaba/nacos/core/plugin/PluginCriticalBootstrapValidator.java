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

package com.alibaba.nacos.core.plugin;

import com.alibaba.nacos.api.plugin.PluginProvider;
import com.alibaba.nacos.api.plugin.PluginType;
import com.alibaba.nacos.common.spi.NacosServiceLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Validates active critical plugin implementations before Spring creates business beans.
 *
 * @author Nacos
 */
public final class PluginCriticalBootstrapValidator {
    
    private static final Logger LOGGER =
        LoggerFactory.getLogger(PluginCriticalBootstrapValidator.class);
    
    private PluginCriticalBootstrapValidator() {
    }
    
    /**
     * Validate critical plugin availability using service-loaded policies and providers.
     */
    @SuppressWarnings("rawtypes")
    public static void validate() {
        PluginTypePolicyRegistry policyRegistry = new PluginTypePolicyRegistry();
        policyRegistry.initialize();
        validate(policyRegistry, NacosServiceLoader.load(PluginProvider.class));
    }
    
    @SuppressWarnings("rawtypes")
    static void validate(PluginTypePolicyRegistry policyRegistry,
        Collection<PluginProvider> providers) {
        Map<PluginType, List<PluginProvider>> providersByType = groupProvidersByType(providers);
        for (PluginType type : PluginType.values()) {
            if (!type.isCritical() || !policyRegistry.isActive(type)
                || !policyRegistry.supportsPreRefreshValidation(type)) {
                continue;
            }
            Map<String, Boolean> implementations = discoverImplementations(type,
                providersByType.getOrDefault(type, Collections.emptyList()), policyRegistry);
            String validationError = PluginTypePolicyRegistry.getCriticalValidationError(
                policyRegistry, type, implementations);
            if (validationError != null) {
                LOGGER.error("[PluginManager] {}", validationError);
                throw new IllegalStateException(validationError);
            }
        }
    }
    
    @SuppressWarnings("rawtypes")
    private static Map<PluginType, List<PluginProvider>> groupProvidersByType(
        Collection<PluginProvider> providers) {
        Map<PluginType, List<PluginProvider>> result = new EnumMap<>(PluginType.class);
        for (PluginProvider provider : providers) {
            try {
                result.computeIfAbsent(provider.getPluginType(), key -> new ArrayList<>())
                    .add(provider);
            } catch (RuntimeException e) {
                LOGGER.warn("[PluginManager] Failed to identify plugin provider: {}",
                    provider.getClass().getName(), e);
            }
        }
        return result;
    }
    
    @SuppressWarnings("rawtypes")
    private static Map<String, Boolean> discoverImplementations(PluginType type,
        List<PluginProvider> providers, PluginTypePolicyRegistry policyRegistry) {
        Map<String, Boolean> result = new LinkedHashMap<>();
        for (PluginProvider provider : providers) {
            Map<String, ?> plugins;
            try {
                plugins = provider.getAllPlugins();
            } catch (RuntimeException e) {
                throw new IllegalStateException("Failed to discover implementations for active "
                    + "critical plugin type '" + type.getType() + "' from provider '"
                    + provider.getClass().getName() + "'.", e);
            }
            if (plugins == null) {
                continue;
            }
            plugins.forEach((name, instance) -> {
                if (instance != null) {
                    result.put(name,
                        policyRegistry.isPluginEnabledByDefault(type, name));
                }
            });
        }
        return result;
    }
}
