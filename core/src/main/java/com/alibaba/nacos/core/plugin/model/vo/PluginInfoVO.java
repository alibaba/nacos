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

/**
 * Plugin Info VO for list display.
 *
 * @author Nacos
 */
public class PluginInfoVO {

    private String pluginId;

    private String pluginType;

    private String pluginName;

    private Boolean enabled;

    private Boolean critical;

    private Boolean configurable;

    private Integer availableNodeCount;

    private Integer totalNodeCount;

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

    public Integer getAvailableNodeCount() {
        return availableNodeCount;
    }

    public void setAvailableNodeCount(Integer availableNodeCount) {
        this.availableNodeCount = availableNodeCount;
    }

    public Integer getTotalNodeCount() {
        return totalNodeCount;
    }

    public void setTotalNodeCount(Integer totalNodeCount) {
        this.totalNodeCount = totalNodeCount;
    }

    @Override
    public String toString() {
        return "PluginInfoVO{" + "pluginId='" + pluginId + '\'' + ", pluginType='" + pluginType + '\''
                + ", pluginName='" + pluginName + '\'' + ", enabled=" + enabled + ", critical=" + critical
                + ", configurable=" + configurable + ", availableNodeCount=" + availableNodeCount + ", totalNodeCount="
                + totalNodeCount + '}';
    }
}
