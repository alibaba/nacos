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

package com.alibaba.nacos.core.plugin.config;

import java.util.Map;

/**
 * Resolver contract for a persisted plugin configuration source.
 *
 * @author Nacos
 */
interface PersistedPluginConfigSourceResolver extends PluginConfigSourceResolver {
    
    /**
     * Load the complete persisted source during startup.
     */
    void initialize();
    
    /**
     * Whether the selected physical storage is available.
     *
     * @return true when runtime persisted reads and writes are available
     */
    boolean isAvailable();
    
    /**
     * Get the complete persisted source snapshot.
     *
     * @return plugin ID to source config map
     */
    Map<String, Map<String, String>> getAllConfigs();
    
    /**
     * Replace the complete persisted source while restoring a snapshot.
     *
     * @param configs complete persisted source snapshot
     */
    void restoreConfigs(Map<String, Map<String, String>> configs);
    
    /**
     * Release the selected physical storage.
     */
    void shutdown();
}
