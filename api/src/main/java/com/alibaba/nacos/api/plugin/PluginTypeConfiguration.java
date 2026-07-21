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

/**
 * Read-only configuration access for an internal plugin type policy.
 *
 * <p>The abstraction keeps domain policies independent from the server environment implementation.
 *
 * @author Nacos
 * @since 3.3.0
 */
public interface PluginTypeConfiguration {
    
    /**
     * Get a property value.
     *
     * @param key property key
     * @return property value, or {@code null} when absent
     */
    String getProperty(String key);
    
    /**
     * Get a property value with a fallback.
     *
     * @param key property key
     * @param defaultValue fallback value
     * @return property value or fallback
     */
    default String getProperty(String key, String defaultValue) {
        String result = getProperty(key);
        return result == null ? defaultValue : result;
    }
    
    /**
     * Whether a property is explicitly present.
     *
     * @param key property key
     * @return true when present
     */
    boolean containsProperty(String key);
    
    /**
     * Get a boolean property value.
     *
     * @param key property key
     * @param defaultValue fallback value
     * @return parsed boolean value or fallback
     */
    default boolean getBooleanProperty(String key, boolean defaultValue) {
        String result = getProperty(key);
        return result == null ? defaultValue : Boolean.parseBoolean(result.trim());
    }
}
