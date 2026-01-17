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
    AUTH("auth", "Authentication plugin"),

    /**
     * Datasource dialect plugin.
     */
    DATASOURCE_DIALECT("datasource-dialect", "Datasource dialect plugin"),

    /**
     * Config change plugin.
     */
    CONFIG_CHANGE("config-change", "Config change plugin"),

    /**
     * Encryption plugin.
     */
    ENCRYPTION("encryption", "Encryption plugin"),

    /**
     * Trace plugin.
     */
    TRACE("trace", "Trace plugin"),

    /**
     * Environment plugin.
     */
    ENVIRONMENT("environment", "Environment plugin"),

    /**
     * Control plugin.
     */
    CONTROL("control", "Control plugin");

    private final String type;

    private final String description;

    PluginType(String type, String description) {
        this.type = type;
        this.description = description;
    }

    public String getType() {
        return type;
    }

    public String getDescription() {
        return description;
    }

    /**
     * Get PluginType from type string.
     *
     * @param type type string
     * @return PluginType
     * @throws IllegalArgumentException if type is unknown
     */
    public static PluginType fromType(String type) {
        for (PluginType pt : values()) {
            if (pt.type.equals(type)) {
                return pt;
            }
        }
        throw new IllegalArgumentException("Unknown plugin type: " + type);
    }
}
