/*
 * Copyright 1999-2024 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.api.plugin;

/**
 * Plugin type enumeration, supports all Nacos plugin types.
 *
 * @author WangzJi
 * @since 3.2.0
 */
public enum PluginType {
    
    /**
     * Authentication plugin.
     */
    AUTH("auth", "Authentication plugin", PluginExecutionMode.EXCLUSIVE, true),
    
    /**
     * Datasource dialect plugin.
     */
    DATASOURCE_DIALECT("datasource-dialect", "Datasource dialect plugin",
        PluginExecutionMode.EXCLUSIVE, true),
    
    /**
     * Config change plugin.
     */
    CONFIG_CHANGE("config-change", "Config change plugin", PluginExecutionMode.CHAIN, false),
    
    /**
     * Encryption plugin.
     */
    ENCRYPTION("encryption", "Encryption plugin", PluginExecutionMode.ROUTED, false),
    
    /**
     * Trace plugin.
     */
    TRACE("trace", "Trace plugin", PluginExecutionMode.BROADCAST, false),
    
    /**
     * Environment plugin.
     */
    ENVIRONMENT("environment", "Environment plugin", PluginExecutionMode.CHAIN, false,
        PluginInitializationPhase.PRE_CONTEXT),
    
    /**
     * Control plugin.
     */
    CONTROL("control", "Control plugin", PluginExecutionMode.EXCLUSIVE, false),
    
    /**
     * Visibility plugin.
     */
    VISIBILITY("visibility", "Visibility plugin", PluginExecutionMode.ROUTED, false),
    
    /**
     * AI publish pipeline plugin.
     */
    AI_PIPELINE("ai-pipeline", "AI publish pipeline plugin", PluginExecutionMode.CHAIN, false),
    
    /**
     * AI resource storage plugin.
     */
    AI_STORAGE("ai-storage", "AI resource storage plugin", PluginExecutionMode.ROUTED, true),
    
    /**
     * AI resource import plugin.
     */
    AI_RESOURCE_IMPORT("ai-resource-import", "AI resource import plugin",
        PluginExecutionMode.ROUTED, false);
    
    private final String type;
    
    private final String description;
    
    private final PluginExecutionMode executionMode;
    
    private final boolean critical;
    
    private final PluginInitializationPhase initializationPhase;
    
    PluginType(String type, String description, PluginExecutionMode executionMode,
        boolean critical) {
        this(type, description, executionMode, critical, PluginInitializationPhase.STANDARD);
    }
    
    PluginType(String type, String description, PluginExecutionMode executionMode,
        boolean critical, PluginInitializationPhase initializationPhase) {
        this.type = type;
        this.description = description;
        this.executionMode = executionMode;
        this.critical = critical;
        this.initializationPhase = initializationPhase;
    }
    
    public String getType() {
        return type;
    }
    
    public String getDescription() {
        return description;
    }
    
    /**
     * Get the execution mode shared by implementations of this plugin type.
     *
     * @return execution mode
     */
    public PluginExecutionMode getExecutionMode() {
        return executionMode;
    }
    
    /**
     * Whether implementations of this plugin type are mutually exclusive.
     *
     * @return true if only one implementation should be enabled
     */
    public boolean isExclusive() {
        return PluginExecutionMode.EXCLUSIVE == executionMode;
    }
    
    /**
     * Whether this plugin type must retain at least one usable implementation.
     *
     * @return true if the plugin type is critical
     */
    public boolean isCritical() {
        return critical;
    }
    
    /**
     * Get the initialization phase shared by implementations of this plugin type.
     *
     * @return initialization phase
     */
    public PluginInitializationPhase getInitializationPhase() {
        return initializationPhase;
    }
    
}
