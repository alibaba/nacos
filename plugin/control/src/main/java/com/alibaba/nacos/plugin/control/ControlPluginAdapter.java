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

package com.alibaba.nacos.plugin.control;

import com.alibaba.nacos.api.plugin.ConfigItemDefinition;
import com.alibaba.nacos.api.plugin.ConfigItemEffectMode;
import com.alibaba.nacos.api.plugin.PluginConfigSpec;
import com.alibaba.nacos.api.plugin.PluginStartupLifecycle;
import com.alibaba.nacos.plugin.control.connection.ConnectionControlManager;
import com.alibaba.nacos.plugin.control.connection.DefaultConnectionControlManager;
import com.alibaba.nacos.plugin.control.spi.ControlManagerBuilder;
import com.alibaba.nacos.plugin.control.tps.DefaultTpsControlManager;
import com.alibaba.nacos.plugin.control.tps.TpsControlManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Stable plugin adapter for one control manager builder.
 *
 * @author Nacos
 */
public final class ControlPluginAdapter implements PluginConfigSpec, PluginStartupLifecycle {
    
    private final ControlManagerBuilder builder;
    
    private final ControlManagerCenter managerCenter;
    
    private final List<ConfigItemDefinition> configDefinitions;
    
    private volatile Map<String, String> currentConfig = Collections.emptyMap();
    
    private boolean initialized;
    
    /**
     * Create control plugin adapter.
     *
     * @param builder control manager builder
     */
    public ControlPluginAdapter(ControlManagerBuilder builder) {
        this(builder, ControlManagerCenter.getInstance());
    }
    
    ControlPluginAdapter(ControlManagerBuilder builder, ControlManagerCenter managerCenter) {
        this.builder = Objects.requireNonNull(builder, "Control manager builder cannot be null");
        this.managerCenter =
            Objects.requireNonNull(managerCenter, "Control manager center cannot be null");
        this.configDefinitions = filterConfigDefinitions(builder);
    }
    
    /**
     * Get control plugin name.
     *
     * @return plugin name
     */
    public String getPluginName() {
        return builder.getName();
    }
    
    @Override
    public List<ConfigItemDefinition> getConfigDefinitions() {
        return configDefinitions;
    }
    
    @Override
    public void applyConfig(Map<String, String> config) {
        Map<String, String> target =
            config == null ? Collections.emptyMap() : new LinkedHashMap<>(config);
        currentConfig = Collections.unmodifiableMap(target);
    }
    
    @Override
    public Map<String, String> getCurrentConfig() {
        return new LinkedHashMap<>(currentConfig);
    }
    
    @Override
    public synchronized void initialize() {
        if (initialized) {
            return;
        }
        Map<String, String> configSnapshot = currentConfig;
        ConnectionControlManager connectionControlManager =
            buildConnectionControlManager(configSnapshot);
        TpsControlManager tpsControlManager = buildTpsControlManager(configSnapshot);
        managerCenter.install(
            new ControlManagerBundle(connectionControlManager, tpsControlManager));
        initialized = true;
    }
    
    private ConnectionControlManager buildConnectionControlManager(
        Map<String, String> configSnapshot) {
        try {
            ConnectionControlManager result =
                builder.buildConnectionControlManager(configSnapshot);
            if (result != null) {
                return result;
            }
            Loggers.CONTROL.warn(
                "Control plugin '{}' returned null connection manager, use no-limit manager.",
                getPluginName());
        } catch (RuntimeException e) {
            Loggers.CONTROL.warn(
                "Control plugin '{}' failed to build connection manager, use no-limit manager.",
                getPluginName(), e);
        }
        return new DefaultConnectionControlManager();
    }
    
    private TpsControlManager buildTpsControlManager(Map<String, String> configSnapshot) {
        try {
            TpsControlManager result = builder.buildTpsControlManager(configSnapshot);
            if (result != null) {
                return result;
            }
            Loggers.CONTROL.warn(
                "Control plugin '{}' returned null TPS manager, use no-limit manager.",
                getPluginName());
        } catch (RuntimeException e) {
            Loggers.CONTROL.warn(
                "Control plugin '{}' failed to build TPS manager, use no-limit manager.",
                getPluginName(), e);
        }
        return new DefaultTpsControlManager();
    }
    
    private static List<ConfigItemDefinition> filterConfigDefinitions(
        ControlManagerBuilder builder) {
        List<ConfigItemDefinition> definitions = builder.getConfigDefinitions();
        if (definitions == null || definitions.isEmpty()) {
            return Collections.emptyList();
        }
        List<ConfigItemDefinition> result = new ArrayList<>(definitions.size());
        for (ConfigItemDefinition definition : definitions) {
            if (definition == null) {
                Loggers.CONTROL.warn("Ignore null config definition from control plugin '{}'.",
                    builder.getName());
                continue;
            }
            if (ConfigItemEffectMode.RESTART != definition.getEffectMode()) {
                Loggers.CONTROL.warn("Ignore non-RESTART config definition from control plugin "
                    + "'{}', key={}.", builder.getName(), definition.getKey());
                continue;
            }
            result.add(definition);
        }
        return Collections.unmodifiableList(result);
    }
}
