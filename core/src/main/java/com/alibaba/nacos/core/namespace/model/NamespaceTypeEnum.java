/*
 * Copyright 1999-2023 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.core.namespace.model;

/**
 * Namespace type enum.
 * Note: Changes to this enum may require updates to the frontend code (e.g., Namespace.js).
 *
 * @author chenglu
 * @date 2021-05-25 17:01
 */
public enum NamespaceTypeEnum {
    
    /**
     * Global configuration.
     */
    GLOBAL(0, "Global configuration"),
    
    /**
     * Custom namespace for naming and config.
     */
    CUSTOM(1, "Custom namespace for naming and config"),
    
    /**
     * Nacos AI module MCP type namespace.
     */
    AI_MCP(2, "Default private namespace");
    
    /**
     * the namespace type.
     */
    private final int type;
    
    /**
     * the description.
     */
    private final String description;
    
    NamespaceTypeEnum(int type, String description) {
        this.type = type;
        this.description = description;
    }
    
    public int getType() {
        return type;
    }
    
    public String getDescription() {
        return description;
    }
    
    public static NamespaceTypeEnum getByType(String type) {
        try {
            int typeInt = Integer.parseInt(type);
            for (NamespaceTypeEnum value : values()) {
                if (value.getType() == typeInt) {
                    return value;
                }
            }
        } catch (NumberFormatException ignored) {
        }
        return null;
    }
    
}

