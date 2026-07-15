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

import com.alibaba.nacos.core.plugin.model.PluginConfigSourceType;
import com.alibaba.nacos.core.plugin.model.PluginInfo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Registry for internal plugin configuration source resolvers.
 *
 * @author Nacos
 */
class PluginConfigSourceRegistry {
    
    private static final List<PluginConfigSourceType> SOURCE_ORDER = Arrays.asList(
        PluginConfigSourceType.LOCAL_ONLY, PluginConfigSourceType.RUNTIME_PERSISTED,
        PluginConfigSourceType.STATIC, PluginConfigSourceType.DEFAULT);
    
    private final Map<PluginConfigSourceType, PluginConfigSourceResolver> sourceResolvers =
        new EnumMap<>(PluginConfigSourceType.class);
    
    PluginConfigSourceRegistry() {
        this(Arrays.asList(new LocalOnlyPluginConfigSourceResolver(),
            new RuntimePersistedPluginConfigSourceResolver(),
            new StaticPluginConfigSourceResolver(), new DefaultPluginConfigSourceResolver()));
    }
    
    PluginConfigSourceRegistry(List<PluginConfigSourceResolver> sourceResolvers) {
        for (PluginConfigSourceResolver sourceResolver : sourceResolvers) {
            PluginConfigSourceType sourceType = sourceResolver.getSourceType();
            if (this.sourceResolvers.put(sourceType, sourceResolver) != null) {
                throw new IllegalArgumentException(
                    "Duplicate plugin config source: " + sourceType);
            }
        }
        for (PluginConfigSourceType sourceType : SOURCE_ORDER) {
            if (!this.sourceResolvers.containsKey(sourceType)) {
                throw new IllegalArgumentException(
                    "Required plugin config source not found: " + sourceType);
            }
        }
    }
    
    List<PluginConfigSourceResolver> getSourceResolvers() {
        List<PluginConfigSourceResolver> result = new ArrayList<>(SOURCE_ORDER.size());
        for (PluginConfigSourceType sourceType : SOURCE_ORDER) {
            result.add(sourceResolvers.get(sourceType));
        }
        return Collections.unmodifiableList(result);
    }
    
    PluginConfigSourceResolver getSourceResolver(PluginConfigSourceType sourceType) {
        PluginConfigSourceResolver result = sourceResolvers.get(sourceType);
        if (result == null) {
            throw new IllegalArgumentException("Plugin config source not found: " + sourceType);
        }
        return result;
    }
    
    void initializeConfig(PluginConfigSourceType sourceType, PluginInfo pluginInfo) {
        getSourceResolver(sourceType).initializeConfig(pluginInfo);
    }
    
    void refreshConfig(PluginConfigSourceType sourceType, PluginInfo pluginInfo) {
        getSourceResolver(sourceType).refreshConfig(pluginInfo);
    }
}
