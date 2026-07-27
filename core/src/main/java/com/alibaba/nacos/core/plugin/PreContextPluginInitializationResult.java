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

package com.alibaba.nacos.core.plugin;

import com.alibaba.nacos.core.plugin.config.PluginConfigResolution;
import com.alibaba.nacos.core.plugin.model.PluginInfo;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Immutable handoff from pre-context initialization to the standard plugin manager.
 *
 * @author Nacos
 */
public final class PreContextPluginInitializationResult {
    
    public static final String BEAN_NAME = "preContextPluginInitializationResult";
    
    private static final PreContextPluginInitializationResult EMPTY =
        new PreContextPluginInitializationResult(Collections.emptyMap(), Collections.emptyMap(),
            Collections.emptyMap());
    
    private final Map<String, PluginInfo> pluginInfos;
    
    private final Map<String, Object> pluginInstances;
    
    private final Map<String, PluginConfigResolution> configResolutions;
    
    PreContextPluginInitializationResult(Map<String, PluginInfo> pluginInfos,
        Map<String, Object> pluginInstances,
        Map<String, PluginConfigResolution> configResolutions) {
        this.pluginInfos = immutableCopy(pluginInfos);
        this.pluginInstances = immutableCopy(pluginInstances);
        this.configResolutions = immutableCopy(configResolutions);
    }
    
    public static PreContextPluginInitializationResult empty() {
        return EMPTY;
    }
    
    Map<String, PluginInfo> getPluginInfos() {
        return pluginInfos;
    }
    
    Map<String, Object> getPluginInstances() {
        return pluginInstances;
    }
    
    Map<String, PluginConfigResolution> getConfigResolutions() {
        return configResolutions;
    }
    
    private static <T> Map<String, T> immutableCopy(Map<String, T> source) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }
}
