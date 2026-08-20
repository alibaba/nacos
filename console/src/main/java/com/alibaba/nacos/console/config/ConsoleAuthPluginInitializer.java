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

package com.alibaba.nacos.console.config;

import com.alibaba.nacos.api.plugin.PluginInitializationPhase;
import com.alibaba.nacos.api.plugin.PluginStartupLifecycle;
import com.alibaba.nacos.api.plugin.PluginType;
import com.alibaba.nacos.common.event.ServerConfigChangeEvent;
import com.alibaba.nacos.common.notify.Event;
import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.common.notify.listener.Subscriber;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.core.auth.AuthPluginTypeResolver;
import com.alibaba.nacos.core.plugin.config.PluginConfigApplier;
import com.alibaba.nacos.core.plugin.config.PluginConfigBasicChecker;
import com.alibaba.nacos.core.plugin.config.PluginConfigDefinitionNormalizer;
import com.alibaba.nacos.core.plugin.config.PluginConfigResolution;
import com.alibaba.nacos.core.plugin.config.PluginConfigResolver;
import com.alibaba.nacos.core.plugin.model.PluginInfo;
import com.alibaba.nacos.core.utils.GlobalExecutor;
import com.alibaba.nacos.plugin.auth.spi.server.AuthPluginManager;
import com.alibaba.nacos.plugin.auth.spi.server.AuthPluginService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.SmartInitializingSingleton;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * Initializes local auth plugins for an independently deployed Console.
 *
 * @author Nacos
 */
public class ConsoleAuthPluginInitializer extends Subscriber<ServerConfigChangeEvent>
    implements SmartInitializingSingleton, DisposableBean {
    
    private static final Logger LOGGER =
        LoggerFactory.getLogger(ConsoleAuthPluginInitializer.class);
    
    private static final String DEFAULT_AUTH_PLUGIN = "nacos";
    
    private static final Executor EXECUTOR = GlobalExecutor::executeByCommon;
    
    private final Map<String, AuthPluginService> authPlugins;
    
    private final PluginConfigResolver configResolver;
    
    private final PluginConfigBasicChecker configChecker;
    
    private final PluginConfigApplier configApplier;
    
    private final Map<String, PluginInfo> pluginInfos = new LinkedHashMap<>();
    
    private volatile boolean initialized;
    
    private volatile boolean subscriberRegistered;
    
    private String selectedPlugin;
    
    public ConsoleAuthPluginInitializer() {
        this(AuthPluginManager.getInstance().getAllPlugins(), new PluginConfigResolver(),
            new PluginConfigBasicChecker(), new PluginConfigApplier());
    }
    
    ConsoleAuthPluginInitializer(Map<String, AuthPluginService> authPlugins,
        PluginConfigResolver configResolver, PluginConfigBasicChecker configChecker,
        PluginConfigApplier configApplier) {
        this.authPlugins = new LinkedHashMap<>(authPlugins);
        this.configResolver = configResolver;
        this.configChecker = configChecker;
        this.configApplier = configApplier;
    }
    
    @Override
    public void afterSingletonsInstantiated() {
        initialize();
    }
    
    synchronized void initialize() {
        if (initialized) {
            return;
        }
        selectedPlugin = resolveSelectedPlugin();
        AuthPluginService selectedInstance = authPlugins.get(selectedPlugin);
        if (selectedInstance == null) {
            throw new IllegalStateException("Selected auth plugin '" + selectedPlugin
                + "' was not discovered for independently deployed Console");
        }
        Map<String, PluginInfo> initializedPluginInfos = new LinkedHashMap<>();
        for (Map.Entry<String, AuthPluginService> entry : authPlugins.entrySet()) {
            initializePlugin(entry.getKey(), entry.getValue(), initializedPluginInfos);
        }
        initializeSelectedPluginLifecycle(selectedInstance);
        pluginInfos.putAll(initializedPluginInfos);
        NotifyCenter.registerSubscriber(this);
        subscriberRegistered = true;
        initialized = true;
        LOGGER.info("[ConsoleAuthPluginInitializer] Initialized {} auth plugins, selected={}",
            pluginInfos.size(), selectedPlugin);
    }
    
    private void initializePlugin(String pluginName, AuthPluginService plugin,
        Map<String, PluginInfo> initializedPluginInfos) {
        if (StringUtils.isBlank(pluginName) || plugin == null) {
            LOGGER.warn("[ConsoleAuthPluginInitializer] Ignore invalid auth plugin, name={}, "
                + "instancePresent={}", pluginName, plugin != null);
            return;
        }
        PluginInfo pluginInfo = createPluginInfo(pluginName, plugin);
        if (pluginInfo.isConfigurable()) {
            initializePluginConfig(pluginInfo, plugin);
        }
        initializedPluginInfos.put(pluginInfo.getPluginId(), pluginInfo);
    }
    
    private PluginInfo createPluginInfo(String pluginName, AuthPluginService plugin) {
        String pluginId = PluginType.AUTH.getType() + ':' + pluginName;
        PluginInfo result = new PluginInfo();
        result.setPluginId(pluginId);
        result.setPluginName(pluginName);
        result.setPluginType(PluginType.AUTH);
        result.setClassName(plugin.getClass().getName());
        result.setLoadTimestamp(System.currentTimeMillis());
        result.setEnabled(pluginName.equals(selectedPlugin));
        result.setConfigurable(plugin.isConfigurable());
        if (result.isConfigurable()) {
            result.setConfigDefinitions(PluginConfigDefinitionNormalizer.normalize(pluginId,
                plugin.getConfigDefinitions(), PluginInitializationPhase.STANDARD));
            result.setConfig(copyConfig(plugin.getCurrentConfig()));
        }
        return result;
    }
    
    private void initializePluginConfig(PluginInfo pluginInfo, AuthPluginService plugin) {
        configResolver.initializeStaticConfig(pluginInfo);
        PluginConfigResolution resolution = configResolver.resolve(pluginInfo, false);
        try {
            configChecker.validateEffectiveConfig(pluginInfo, resolution.getConfig());
            configApplier.apply(pluginInfo.getPluginId(), plugin, resolution.getConfig());
        } catch (RuntimeException e) {
            throw new IllegalStateException("Failed to initialize Console auth plugin config: "
                + pluginInfo.getPluginId(), e);
        }
        pluginInfo.setConfig(copyConfig(resolution.getConfig()));
    }
    
    private void initializeSelectedPluginLifecycle(AuthPluginService selectedInstance) {
        if (!(selectedInstance instanceof PluginStartupLifecycle)) {
            return;
        }
        try {
            ((PluginStartupLifecycle) selectedInstance).initialize();
        } catch (RuntimeException e) {
            throw new IllegalStateException("Failed to initialize selected Console auth plugin "
                + "lifecycle: " + selectedPlugin, e);
        }
    }
    
    @Override
    public void onEvent(ServerConfigChangeEvent event) {
        warnIfSelectionChanged();
        for (Map.Entry<String, PluginInfo> entry : pluginInfos.entrySet()) {
            PluginInfo pluginInfo = entry.getValue();
            if (!pluginInfo.isConfigurable()) {
                continue;
            }
            try {
                refreshPluginConfig(pluginInfo, authPlugins.get(pluginInfo.getPluginName()));
            } catch (RuntimeException e) {
                LOGGER.error("[ConsoleAuthPluginInitializer] Failed to refresh static auth "
                    + "plugin config, pluginId={}", entry.getKey(), e);
            }
        }
    }
    
    private void refreshPluginConfig(PluginInfo pluginInfo, AuthPluginService plugin) {
        synchronized (pluginInfo) {
            configResolver.refreshStaticConfig(pluginInfo);
            PluginConfigResolution resolution = configResolver.resolve(pluginInfo, false);
            if (resolution.getConfig().equals(pluginInfo.getConfig())) {
                return;
            }
            configChecker.validateEffectiveConfig(pluginInfo, resolution.getConfig());
            configApplier.apply(pluginInfo.getPluginId(), plugin, resolution.getConfig());
            pluginInfo.setConfig(copyConfig(resolution.getConfig()));
        }
    }
    
    private void warnIfSelectionChanged() {
        String currentSelection = resolveSelectedPlugin();
        if (!selectedPlugin.equals(currentSelection)) {
            LOGGER.warn("[ConsoleAuthPluginInitializer] Ignore runtime auth plugin selection "
                + "change from '{}' to '{}'; restart the Console to apply it.", selectedPlugin,
                currentSelection);
        }
    }
    
    private String resolveSelectedPlugin() {
        String result = AuthPluginTypeResolver.resolve();
        return StringUtils.isBlank(result) ? DEFAULT_AUTH_PLUGIN : result;
    }
    
    private Map<String, String> copyConfig(Map<String, String> config) {
        return config == null ? new LinkedHashMap<>() : new LinkedHashMap<>(config);
    }
    
    @Override
    public Executor executor() {
        return EXECUTOR;
    }
    
    @Override
    public Class<? extends Event> subscribeType() {
        return ServerConfigChangeEvent.class;
    }
    
    @Override
    public synchronized void destroy() {
        if (subscriberRegistered) {
            NotifyCenter.deregisterSubscriber(this);
            subscriberRegistered = false;
        }
    }
}
