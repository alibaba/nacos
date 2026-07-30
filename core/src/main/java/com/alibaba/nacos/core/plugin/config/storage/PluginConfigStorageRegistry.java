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

package com.alibaba.nacos.core.plugin.config.storage;

import com.alibaba.nacos.common.spi.NacosServiceLoader;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.core.plugin.storage.PluginStatePersistenceService;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceConfigurationError;
import java.util.function.BiFunction;
import java.util.function.Supplier;

/**
 * Selects the single physical storage behind the runtime persisted source.
 *
 * @author Nacos
 */
public class PluginConfigStorageRegistry {
    
    static final String ENABLED_PROPERTY_PREFIX = "nacos.plugin.config.source.";
    
    static final String ENABLED_PROPERTY_SUFFIX = ".enabled";
    
    private static final Logger LOGGER =
        LoggerFactory.getLogger(PluginConfigStorageRegistry.class);
    
    private final PluginConfigStorageProvider selectedProvider;
    
    public PluginConfigStorageRegistry(PluginStatePersistenceService persistence) {
        this(new LocalFilePluginConfigStorageProvider(persistence),
            () -> NacosServiceLoader.load(PluginConfigStorageProvider.class),
            PluginConfigStorageRegistry::readEnabledProperty);
    }
    
    PluginConfigStorageRegistry(PluginConfigStorageProvider builtInProvider,
        Supplier<Collection<PluginConfigStorageProvider>> providerSupplier,
        BiFunction<String, Boolean, Boolean> enabledResolver) {
        List<ProviderCandidate> candidates = loadCandidates(builtInProvider, providerSupplier);
        candidates.sort(Comparator.comparingInt(ProviderCandidate::getOrder)
            .thenComparing(ProviderCandidate::getName)
            .thenComparing(ProviderCandidate::getClassName));
        this.selectedProvider = selectProvider(candidates, enabledResolver);
    }
    
    /**
     * Get the provider selected for this process.
     *
     * @return selected provider, or {@code null} when all providers are disabled
     */
    public PluginConfigStorageProvider getSelectedProvider() {
        return selectedProvider;
    }
    
    private List<ProviderCandidate> loadCandidates(PluginConfigStorageProvider builtInProvider,
        Supplier<Collection<PluginConfigStorageProvider>> providerSupplier) {
        List<ProviderCandidate> result = new ArrayList<>();
        try {
            Collection<PluginConfigStorageProvider> providers = providerSupplier.get();
            if (providers != null) {
                for (PluginConfigStorageProvider provider : providers) {
                    if (!addCandidate(result, provider)) {
                        return Collections.emptyList();
                    }
                }
            }
        } catch (RuntimeException | LinkageError | ServiceConfigurationError e) {
            LOGGER.error("[PluginConfigStorageRegistry] Failed to discover internal plugin "
                + "config storage providers; runtime persisted config is unavailable.", e);
            return Collections.emptyList();
        }
        if (!addCandidate(result, builtInProvider)) {
            return Collections.emptyList();
        }
        return result;
    }
    
    private boolean addCandidate(List<ProviderCandidate> candidates,
        PluginConfigStorageProvider provider) {
        if (provider == null) {
            LOGGER.error("[PluginConfigStorageRegistry] Discovered null storage provider; runtime "
                + "persisted config is unavailable.");
            return false;
        }
        try {
            String name = provider.getName();
            if (StringUtils.isBlank(name)) {
                LOGGER.error("[PluginConfigStorageRegistry] Discovered storage provider without "
                    + "name: {}; runtime persisted config is unavailable.",
                    provider.getClass().getName());
                return false;
            }
            candidates.add(new ProviderCandidate(provider, name, provider.getOrder(),
                provider.isEnabledByDefault()));
            return true;
        } catch (RuntimeException | LinkageError e) {
            LOGGER.error("[PluginConfigStorageRegistry] Failed to inspect storage provider: {}; "
                + "runtime persisted config is unavailable.", provider.getClass().getName(), e);
            return false;
        }
    }
    
    private PluginConfigStorageProvider selectProvider(List<ProviderCandidate> candidates,
        BiFunction<String, Boolean, Boolean> enabledResolver) {
        Map<String, ProviderCandidate> uniqueCandidates = new LinkedHashMap<>();
        for (ProviderCandidate candidate : candidates) {
            ProviderCandidate existing = uniqueCandidates.putIfAbsent(candidate.getName(),
                candidate);
            if (existing != null) {
                LOGGER.warn("[PluginConfigStorageRegistry] Ignore duplicate storage provider, "
                    + "name={}, existingClass={}, ignoredClass={}.", candidate.getName(),
                    existing.getClassName(), candidate.getClassName());
            }
        }
        
        ProviderCandidate selected = null;
        for (ProviderCandidate candidate : uniqueCandidates.values()) {
            boolean enabled;
            try {
                enabled = isEnabled(candidate, enabledResolver);
            } catch (RuntimeException | LinkageError e) {
                LOGGER.error("[PluginConfigStorageRegistry] Failed to read enable property for "
                    + "storage {}; runtime persisted config is unavailable.",
                    candidate.getName(), e);
                return null;
            }
            if (!enabled) {
                continue;
            }
            if (selected == null) {
                selected = candidate;
            } else {
                LOGGER.warn("[PluginConfigStorageRegistry] Ignore enabled storage provider, "
                    + "selected={}, ignored={}.", selected.getName(), candidate.getName());
            }
        }
        if (selected == null) {
            LOGGER.warn("[PluginConfigStorageRegistry] No runtime persisted plugin config storage "
                + "is enabled. Startup will continue without this source.");
            return null;
        }
        LOGGER.info("[PluginConfigStorageRegistry] Selected plugin config storage: {}",
            selected.getName());
        return selected.getProvider();
    }
    
    private boolean isEnabled(ProviderCandidate candidate,
        BiFunction<String, Boolean, Boolean> enabledResolver) {
        String property = ENABLED_PROPERTY_PREFIX + candidate.getName() + ENABLED_PROPERTY_SUFFIX;
        return Boolean.TRUE.equals(enabledResolver.apply(property,
            candidate.isEnabledByDefault()));
    }
    
    private static Boolean readEnabledProperty(String property, Boolean defaultValue) {
        return EnvUtil.getProperty(property, Boolean.class, defaultValue);
    }
    
    private static class ProviderCandidate {
        
        private final PluginConfigStorageProvider provider;
        
        private final String name;
        
        private final int order;
        
        private final boolean enabledByDefault;
        
        ProviderCandidate(PluginConfigStorageProvider provider, String name, int order,
            boolean enabledByDefault) {
            this.provider = provider;
            this.name = name;
            this.order = order;
            this.enabledByDefault = enabledByDefault;
        }
        
        PluginConfigStorageProvider getProvider() {
            return provider;
        }
        
        String getName() {
            return name;
        }
        
        int getOrder() {
            return order;
        }
        
        boolean isEnabledByDefault() {
            return enabledByDefault;
        }
        
        String getClassName() {
            return provider.getClass().getName();
        }
    }
}
