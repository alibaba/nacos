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

package com.alibaba.nacos.api.plugin;

import java.util.Collections;
import java.util.Set;

/**
 * Internal domain policy for one plugin type.
 *
 * <p>A policy describes initial implementation state and active critical requirements. Core owns
 * validation and lifecycle orchestration so domain implementations do not duplicate those flows.
 *
 * @author Nacos
 * @since 3.3.0
 */
public interface PluginTypePolicy {
    
    /**
     * Initialize startup-only policy state before plugin discovery.
     *
     * <p>Selection properties with restart semantics must be captured here so later server
     * configuration refreshes cannot change the required implementation without a restart.
     *
     * @param configuration server configuration
     */
    default void initialize(PluginTypeConfiguration configuration) {
    }
    
    /**
     * Get the governed plugin type.
     *
     * @return plugin type
     */
    PluginType getPluginType();
    
    /**
     * Whether the owning domain currently requires this plugin type.
     *
     * @param configuration server configuration
     * @return true when active
     */
    default boolean isActive(PluginTypeConfiguration configuration) {
        return false;
    }
    
    /**
     * Whether implementations of this non-critical plugin type should be loaded now.
     *
     * <p>This predicate is re-evaluated after server configuration changes. Returning
     * {@code false} delays implementation discovery until it becomes {@code true}; implementations
     * that have already been loaded are retained. Active critical types are always loaded by the
     * core plugin manager regardless of this value.
     *
     * @param configuration server configuration
     * @return true when implementation discovery is enabled
     */
    default boolean isLoadingEnabled(PluginTypeConfiguration configuration) {
        return true;
    }
    
    /**
     * Whether critical availability can be validated before the Spring context refreshes.
     *
     * <p>Return {@code false} when implementations are constructed from Spring-managed resources
     * during context refresh. Such types are still validated by the unified plugin manager before
     * Nacos is marked as started.
     *
     * @return true when plugin providers expose usable instances before context refresh
     */
    default boolean supportsPreRefreshValidation() {
        return true;
    }
    
    /**
     * Resolve the initial enabled state of one implementation.
     *
     * @param pluginName plugin implementation name
     * @param configuration server configuration
     * @return initial enabled state
     */
    default boolean isPluginEnabledByDefault(String pluginName,
        PluginTypeConfiguration configuration) {
        String property = "nacos.plugin." + getPluginType().getType() + "." + pluginName
            + ".enabled";
        return configuration.getBooleanProperty(property, true);
    }
    
    /**
     * Get concrete implementations required by the active domain.
     *
     * <p>An empty set means that any enabled implementation satisfies the requirement. An active
     * exclusive type with an empty set is invalid because it has no selected implementation.
     *
     * @param configuration server configuration
     * @return required implementation names
     */
    default Set<String> getRequiredPluginNames(PluginTypeConfiguration configuration) {
        return Collections.emptySet();
    }
    
    /**
     * Get the static implementation selection property for diagnostics.
     *
     * @return selection property
     */
    default String getSelectionProperty() {
        return "nacos.plugin." + getPluginType().getType() + ".type";
    }
    
    /**
     * Describe why the plugin type is active.
     *
     * @return activation description
     */
    default String getActivationDescription() {
        return getPluginType().getDescription();
    }
}
