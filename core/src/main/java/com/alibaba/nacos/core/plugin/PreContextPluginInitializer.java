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

import com.alibaba.nacos.api.plugin.PluginConfigSpec;
import com.alibaba.nacos.api.plugin.PluginInitializationPhase;
import com.alibaba.nacos.api.plugin.PluginProvider;
import com.alibaba.nacos.api.plugin.PluginStartupLifecycle;
import com.alibaba.nacos.api.plugin.PluginType;
import com.alibaba.nacos.common.spi.NacosServiceLoader;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.core.plugin.config.PluginConfigApplier;
import com.alibaba.nacos.core.plugin.config.PluginConfigBasicChecker;
import com.alibaba.nacos.core.plugin.config.PluginConfigDefinitionNormalizer;
import com.alibaba.nacos.core.plugin.config.PluginConfigResolution;
import com.alibaba.nacos.core.plugin.config.PluginConfigResolver;
import com.alibaba.nacos.core.plugin.model.PluginInfo;
import com.alibaba.nacos.plugin.environment.CustomEnvironmentPluginManager;
import com.alibaba.nacos.plugin.environment.spi.CustomEnvironmentPluginService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Initializes startup-only plugins before custom environment processing.
 *
 * @author Nacos
 */
public class PreContextPluginInitializer implements PluginInitializer {
    
    private static final Logger LOGGER =
        LoggerFactory.getLogger(PreContextPluginInitializer.class);
    
    private final ConfigurableListableBeanFactory beanFactory;
    
    private final PluginTypePolicyRegistry policyRegistry;
    
    private final Collection<PluginProvider<?>> providers;
    
    private final PluginConfigResolver configResolver;
    
    private final PluginConfigBasicChecker configChecker;
    
    private final PluginConfigApplier configApplier;
    
    public PreContextPluginInitializer(ConfigurableApplicationContext context) {
        this(context.getBeanFactory(), new PluginTypePolicyRegistry(), loadProviders(),
            new PluginConfigResolver(), new PluginConfigBasicChecker(),
            new PluginConfigApplier());
    }
    
    PreContextPluginInitializer(ConfigurableListableBeanFactory beanFactory,
        PluginTypePolicyRegistry policyRegistry, Collection<PluginProvider<?>> providers,
        PluginConfigResolver configResolver, PluginConfigBasicChecker configChecker,
        PluginConfigApplier configApplier) {
        this.beanFactory = beanFactory;
        this.policyRegistry = policyRegistry;
        this.providers = providers;
        this.configResolver = configResolver;
        this.configChecker = configChecker;
        this.configApplier = configApplier;
    }
    
    @Override
    public PluginInitializationPhase getInitializationPhase() {
        return PluginInitializationPhase.PRE_CONTEXT;
    }
    
    @Override
    public void initialize() {
        if (beanFactory.containsSingleton(PreContextPluginInitializationResult.BEAN_NAME)) {
            return;
        }
        policyRegistry.initialize();
        Map<String, PluginInfo> pluginInfos = new LinkedHashMap<>();
        Map<String, Object> pluginInstances = new LinkedHashMap<>();
        Map<String, PluginConfigResolution> configResolutions = new LinkedHashMap<>();
        List<PluginProvider<?>> orderedProviders = new ArrayList<>(providers);
        orderedProviders.sort(Comparator.comparingInt(PluginProvider::getOrder));
        for (PluginProvider<?> provider : orderedProviders) {
            initializeProvider(provider, pluginInfos, pluginInstances, configResolutions);
        }
        initializeEnvironmentManager(pluginInfos, pluginInstances);
        PreContextPluginInitializationResult result =
            new PreContextPluginInitializationResult(pluginInfos, pluginInstances,
                configResolutions);
        beanFactory.registerSingleton(PreContextPluginInitializationResult.BEAN_NAME, result);
        LOGGER.info("[PreContextPluginInitializer] Initialized {} plugins", pluginInfos.size());
    }
    
    private void initializeProvider(PluginProvider<?> provider,
        Map<String, PluginInfo> pluginInfos, Map<String, Object> pluginInstances,
        Map<String, PluginConfigResolution> configResolutions) {
        PluginType pluginType;
        try {
            pluginType = provider.getPluginType();
        } catch (RuntimeException e) {
            LOGGER.warn("[PreContextPluginInitializer] Failed to identify plugin provider: {}",
                provider.getClass().getName(), e);
            return;
        }
        if (pluginType == null
            || PluginInitializationPhase.PRE_CONTEXT != pluginType.getInitializationPhase()
            || !policyRegistry.shouldLoad(pluginType)) {
            return;
        }
        Map<String, ?> plugins;
        try {
            plugins = provider.getAllPlugins();
        } catch (RuntimeException e) {
            throw new IllegalStateException("Failed to discover pre-context plugins from provider: "
                + provider.getClass().getName(), e);
        }
        if (plugins == null) {
            return;
        }
        plugins.forEach((name, instance) -> initializePlugin(pluginType, name, instance,
            pluginInfos, pluginInstances, configResolutions));
    }
    
    private void initializePlugin(PluginType pluginType, String pluginName, Object instance,
        Map<String, PluginInfo> pluginInfos, Map<String, Object> pluginInstances,
        Map<String, PluginConfigResolution> configResolutions) {
        if (StringUtils.isBlank(pluginName) || instance == null) {
            LOGGER.warn("[PreContextPluginInitializer] Ignore invalid {} plugin, name={}, "
                + "instancePresent={}", pluginType.getType(), pluginName, instance != null);
            return;
        }
        String pluginId = pluginType.getType() + ":" + pluginName;
        PluginInfo existing = pluginInfos.get(pluginId);
        if (existing != null) {
            LOGGER.warn("[PreContextPluginInitializer] Ignore duplicate plugin, pluginId={}, "
                + "existingClass={}, ignoredClass={}.", pluginId, existing.getClassName(),
                instance.getClass().getName());
            return;
        }
        PluginInfo pluginInfo = createPluginInfo(pluginType, pluginName, pluginId, instance);
        PluginConfigResolution resolution = initializePluginConfig(pluginInfo, instance);
        initializePluginLifecycle(pluginInfo, instance);
        pluginInfos.put(pluginId, pluginInfo);
        pluginInstances.put(pluginId, instance);
        configResolutions.put(pluginId, resolution);
    }
    
    private PluginInfo createPluginInfo(PluginType pluginType, String pluginName, String pluginId,
        Object instance) {
        PluginInfo result = new PluginInfo();
        result.setPluginId(pluginId);
        result.setPluginName(pluginName);
        result.setPluginType(pluginType);
        result.setClassName(instance.getClass().getName());
        result.setLoadTimestamp(System.currentTimeMillis());
        result.setEnabled(
            policyRegistry.isPluginEnabledByDefault(pluginType, pluginName));
        if (instance instanceof PluginConfigSpec) {
            PluginConfigSpec configSpec = (PluginConfigSpec) instance;
            result.setConfigurable(configSpec.isConfigurable());
            if (result.isConfigurable()) {
                result.setConfigDefinitions(PluginConfigDefinitionNormalizer.normalize(pluginId,
                    configSpec.getConfigDefinitions(), pluginType.getInitializationPhase()));
                result.setConfig(copyConfig(configSpec.getCurrentConfig()));
            }
        }
        return result;
    }
    
    private PluginConfigResolution initializePluginConfig(PluginInfo pluginInfo, Object instance) {
        configResolver.initializeStaticConfig(pluginInfo);
        PluginConfigResolution resolution = configResolver.resolve(pluginInfo, false);
        if (pluginInfo.isConfigurable()) {
            try {
                configChecker.validateEffectiveConfig(pluginInfo, resolution.getConfig());
                configApplier.apply(pluginInfo.getPluginId(), instance, resolution.getConfig());
            } catch (RuntimeException e) {
                throw new IllegalStateException(
                    "Failed to initialize pre-context plugin config: "
                        + pluginInfo.getPluginId(),
                    e);
            }
            pluginInfo.setConfig(copyConfig(resolution.getConfig()));
        }
        return configResolver.resolve(pluginInfo, true);
    }
    
    private void initializePluginLifecycle(PluginInfo pluginInfo, Object instance) {
        if (!pluginInfo.isEnabled() || !(instance instanceof PluginStartupLifecycle)) {
            return;
        }
        try {
            ((PluginStartupLifecycle) instance).initialize();
        } catch (RuntimeException e) {
            throw new IllegalStateException(
                "Failed to initialize pre-context plugin lifecycle: "
                    + pluginInfo.getPluginId(),
                e);
        }
    }
    
    private Map<String, String> copyConfig(Map<String, String> config) {
        return config == null ? new LinkedHashMap<>() : new LinkedHashMap<>(config);
    }
    
    private void initializeEnvironmentManager(Map<String, PluginInfo> pluginInfos,
        Map<String, Object> pluginInstances) {
        List<CustomEnvironmentPluginService> services = new ArrayList<>();
        pluginInfos.forEach((pluginId, pluginInfo) -> {
            Object instance = pluginInstances.get(pluginId);
            if (PluginType.ENVIRONMENT == pluginInfo.getPluginType() && pluginInfo.isEnabled()
                && instance instanceof CustomEnvironmentPluginService) {
                services.add((CustomEnvironmentPluginService) instance);
            }
        });
        CustomEnvironmentPluginManager.getInstance().initialize(services);
    }
    
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Collection<PluginProvider<?>> loadProviders() {
        return (Collection) NacosServiceLoader.load(PluginProvider.class);
    }
}
