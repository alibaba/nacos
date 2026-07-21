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

import com.alibaba.nacos.api.plugin.PluginType;
import com.alibaba.nacos.api.plugin.PluginTypeConfiguration;
import com.alibaba.nacos.api.plugin.PluginTypePolicy;
import com.alibaba.nacos.common.spi.NacosServiceLoader;

import java.util.Collection;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Registry of domain-owned plugin type policies.
 *
 * @author Nacos
 */
class PluginTypePolicyRegistry {
    
    private final Map<PluginType, PluginTypePolicy> policies = new EnumMap<>(PluginType.class);
    
    private final PluginTypeConfiguration configuration;
    
    private boolean initialized;
    
    PluginTypePolicyRegistry() {
        this(NacosServiceLoader.load(PluginTypePolicy.class), new EnvPluginTypeConfiguration());
    }
    
    PluginTypePolicyRegistry(Collection<PluginTypePolicy> policies,
        PluginTypeConfiguration configuration) {
        this.configuration = configuration;
        for (PluginTypePolicy policy : policies) {
            PluginType type = policy.getPluginType();
            if (this.policies.put(type, policy) != null) {
                throw new IllegalStateException("Duplicate plugin type policy: " + type.getType());
            }
        }
    }
    
    synchronized void initialize() {
        if (initialized) {
            return;
        }
        policies.values().forEach(policy -> policy.initialize(configuration));
        initialized = true;
    }
    
    boolean isActive(PluginType type) {
        PluginTypePolicy policy = policies.get(type);
        return policy != null && policy.isActive(configuration);
    }
    
    boolean supportsPreRefreshValidation(PluginType type) {
        return getPolicy(type).supportsPreRefreshValidation();
    }
    
    boolean isPluginEnabledByDefault(PluginType type, String pluginName) {
        return getPolicy(type).isPluginEnabledByDefault(pluginName, configuration);
    }
    
    Set<String> getRequiredPluginNames(PluginType type) {
        return new LinkedHashSet<>(getPolicy(type).getRequiredPluginNames(configuration));
    }
    
    String getSelectionProperty(PluginType type) {
        return getPolicy(type).getSelectionProperty();
    }
    
    String getActivationDescription(PluginType type) {
        return getPolicy(type).getActivationDescription();
    }
    
    static String getCriticalValidationError(PluginTypePolicyRegistry registry, PluginType type,
        Map<String, Boolean> implementations) {
        if (!type.isCritical() || !registry.isActive(type)) {
            return null;
        }
        String prefix = "Active critical plugin type '" + type.getType() + "' ("
            + registry.getActivationDescription(type) + ") ";
        if (implementations.isEmpty()) {
            return prefix + "has no discovered implementation.";
        }
        Set<String> requiredPlugins = registry.getRequiredPluginNames(type);
        if (type.isExclusive() && requiredPlugins.isEmpty()) {
            return prefix + "has no selected implementation. Configure '"
                + registry.getSelectionProperty(type) + "' and restart Nacos.";
        }
        if (requiredPlugins.isEmpty()) {
            return implementations.values().stream().anyMatch(Boolean.TRUE::equals) ? null
                : prefix + "has no enabled implementation.";
        }
        for (String pluginName : requiredPlugins) {
            if (!implementations.containsKey(pluginName)) {
                return prefix + "requires implementation '" + pluginName
                    + "', but it was not discovered. Selection property: '"
                    + registry.getSelectionProperty(type) + "'.";
            }
            if (!Boolean.TRUE.equals(implementations.get(pluginName))) {
                return prefix + "requires implementation '" + pluginName
                    + "', but it is disabled.";
            }
        }
        return null;
    }
    
    private PluginTypePolicy getPolicy(PluginType type) {
        PluginTypePolicy policy = policies.get(type);
        return policy == null ? new DefaultPluginTypePolicy(type) : policy;
    }
    
    private static final class DefaultPluginTypePolicy implements PluginTypePolicy {
        
        private final PluginType type;
        
        private DefaultPluginTypePolicy(PluginType type) {
            this.type = type;
        }
        
        @Override
        public PluginType getPluginType() {
            return type;
        }
    }
}
