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

import java.io.Serializable;
import java.util.Map;

/**
 * Plugin state operation for Raft consensus.
 * Represents state changes or config updates that need to be replicated across cluster.
 *
 * @author WangzJi
 * @since 3.2.0
 */
public class PluginStateOperation implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Operation type enum.
     */
    public enum OperationType {
        /**
         * Change plugin enabled/disabled state.
         */
        CHANGE_STATE,

        /**
         * Update plugin configuration.
         */
        UPDATE_CONFIG
    }

    private OperationType type;

    private String pluginId;

    private Boolean enabled;

    private Map<String, String> config;

    public PluginStateOperation() {
    }

    public OperationType getType() {
        return type;
    }

    public void setType(OperationType type) {
        this.type = type;
    }

    public String getPluginId() {
        return pluginId;
    }

    public void setPluginId(String pluginId) {
        this.pluginId = pluginId;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public Map<String, String> getConfig() {
        return config;
    }

    public void setConfig(Map<String, String> config) {
        this.config = config;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private final PluginStateOperation operation = new PluginStateOperation();

        public Builder type(OperationType type) {
            operation.type = type;
            return this;
        }

        public Builder pluginId(String pluginId) {
            operation.pluginId = pluginId;
            return this;
        }

        public Builder enabled(boolean enabled) {
            operation.enabled = enabled;
            return this;
        }

        public Builder config(Map<String, String> config) {
            operation.config = config;
            return this;
        }

        /**
         * Build the PluginStateOperation.
         *
         * @return the built PluginStateOperation
         */
        public PluginStateOperation build() {
            if (operation.pluginId == null || operation.pluginId.isEmpty()) {
                throw new IllegalStateException("pluginId is required");
            }
            if (operation.type == null) {
                throw new IllegalStateException("type is required");
            }
            if (operation.type == OperationType.CHANGE_STATE && operation.enabled == null) {
                throw new IllegalStateException("enabled is required for CHANGE_STATE operation");
            }
            if (operation.type == OperationType.UPDATE_CONFIG && operation.config == null) {
                throw new IllegalStateException("config is required for UPDATE_CONFIG operation");
            }
            return operation;
        }
    }
}
