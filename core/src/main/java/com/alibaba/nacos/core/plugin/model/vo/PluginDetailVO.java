/*
 * Copyright 1999-2025 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.core.plugin.model.vo;

import com.alibaba.nacos.api.plugin.ConfigItemDefinition;

import java.util.List;
import java.util.Map;

/**
 * Plugin Detail VO.
 *
 * @author Nacos
 */
public class PluginDetailVO {

    private String pluginId;

    private String pluginType;

    private String pluginName;

    private Boolean enabled;

    private Boolean critical;

    private Boolean configurable;

    private Map<String, String> config;

    private List<ConfigItemDefinition> configDefinitions;
    
    public String getPluginId() {
        return pluginId;
    }

    public void setPluginId(String pluginId) {
        this.pluginId = pluginId;
    }

    public String getPluginType() {
        return pluginType;
    }

    public void setPluginType(String pluginType) {
        this.pluginType = pluginType;
    }

    public String getPluginName() {
        return pluginName;
    }

    public void setPluginName(String pluginName) {
        this.pluginName = pluginName;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public Boolean getCritical() {
        return critical;
    }

    public void setCritical(Boolean critical) {
        this.critical = critical;
    }

    public Boolean getConfigurable() {
        return configurable;
    }

    public void setConfigurable(Boolean configurable) {
        this.configurable = configurable;
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
    
    @Override
    public String toString() {
        return "PluginDetailVO{" + "pluginId='" + pluginId + '\'' + ", pluginType='" + pluginType + '\''
                + ", pluginName='" + pluginName + '\'' + ", enabled=" + enabled + ", critical=" + critical
                + ", configurable=" + configurable + ", config=" + config + ", configDefinitions=" + configDefinitions
                + '}';
    }
}
