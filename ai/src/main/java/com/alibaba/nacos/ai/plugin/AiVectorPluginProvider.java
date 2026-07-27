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

package com.alibaba.nacos.ai.plugin;

import com.alibaba.nacos.api.plugin.PluginProvider;
import com.alibaba.nacos.api.plugin.PluginType;
import com.alibaba.nacos.plugin.ai.vector.AiResourceVectorIndexRegistry;
import com.alibaba.nacos.plugin.ai.vector.spi.AiResourceVectorIndex;

import java.util.Map;

/**
 * Bridges installed AI resource vector index plugins to
 * {@link com.alibaba.nacos.core.plugin.PluginManager}.
 *
 * @author nacos
 */
public class AiVectorPluginProvider implements PluginProvider<AiResourceVectorIndex> {
    
    private final AiResourceVectorIndexRegistry registry;
    
    public AiVectorPluginProvider() {
        this(AiResourceVectorIndexRegistry.getInstance());
    }
    
    AiVectorPluginProvider(AiResourceVectorIndexRegistry registry) {
        this.registry = registry;
    }
    
    @Override
    public PluginType getPluginType() {
        return PluginType.AI_VECTOR;
    }
    
    @Override
    public Map<String, AiResourceVectorIndex> getAllPlugins() {
        return registry.getAllIndexes();
    }
}
