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

package com.alibaba.nacos.core.plugin.model;

import com.alibaba.nacos.api.plugin.ConfigItemDefinition;
import com.alibaba.nacos.api.plugin.PluginType;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * Plugin information model.
 *
 * @author WangzJi
 * @since 3.2.0
 */
public class PluginInfo implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Plugin ID, format: "{type}:{name}".
     */
    private String pluginId;

    /**
     * Plugin name.
     */
    private String pluginName;

    /**
     * Plugin type.
     */
    private PluginType pluginType;

    /**
     * Plugin class name.
     */
    private String className;

    /**
     * Plugin description.
     */
    private String description;

    /**
     * Whether the plugin is enabled.
     */
    private boolean enabled;

    /**
     * Whether this is a critical plugin (cannot be disabled).
     */
    private boolean critical;

    /**
     * Whether the plugin supports configuration.
     */
    private boolean configurable;

    /**
     * Plugin load timestamp.
     */
    private long loadTimestamp;

    /**
     * Current configuration.
     */
    private Map<String, String> config;

    /**
     * Configuration item definitions.
     */
    private List<ConfigItemDefinition> configDefinitions;

    /**
     * Number of nodes where plugin is available.
     */
    private int availableNodeCount;

    /**
     * Total number of nodes in cluster.
     */
    private int totalNodeCount;

    /**
     * Per-node availability: nodeIp -> available.
     */
    private Map<String, Boolean> nodeAvailability;

    public PluginInfo() {
    }

    public String getPluginId() {
        return pluginId;
    }

    public void setPluginId(String pluginId) {
        this.pluginId = pluginId;
    }

    public String getPluginName() {
        return pluginName;
    }

    public void setPluginName(String pluginName) {
        this.pluginName = pluginName;
    }

    public PluginType getPluginType() {
        return pluginType;
    }

    public void setPluginType(PluginType pluginType) {
        this.pluginType = pluginType;
    }

    public String getClassName() {
        return className;
    }

    public void setClassName(String className) {
        this.className = className;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isCritical() {
        return critical;
    }

    public void setCritical(boolean critical) {
        this.critical = critical;
    }

    public boolean isConfigurable() {
        return configurable;
    }

    public void setConfigurable(boolean configurable) {
        this.configurable = configurable;
    }

    public long getLoadTimestamp() {
        return loadTimestamp;
    }

    public void setLoadTimestamp(long loadTimestamp) {
        this.loadTimestamp = loadTimestamp;
    }

    public Map<String, String> getConfig() {
        return config;
    }

    public void setConfig(Map<String, String> config) {
        this.config = config;
    }

    public List<ConfigItemDefinition> getConfigDefinitions() {
        return configDefinitions;
    }

    public void setConfigDefinitions(List<ConfigItemDefinition> configDefinitions) {
        this.configDefinitions = configDefinitions;
    }

    public int getAvailableNodeCount() {
        return availableNodeCount;
    }

    public void setAvailableNodeCount(int availableNodeCount) {
        this.availableNodeCount = availableNodeCount;
    }

    public int getTotalNodeCount() {
        return totalNodeCount;
    }

    public void setTotalNodeCount(int totalNodeCount) {
        this.totalNodeCount = totalNodeCount;
    }

    public Map<String, Boolean> getNodeAvailability() {
        return nodeAvailability;
    }

    public void setNodeAvailability(Map<String, Boolean> nodeAvailability) {
        this.nodeAvailability = nodeAvailability;
    }
}
