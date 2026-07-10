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

import com.alibaba.nacos.core.plugin.model.vo.PluginConfigValueMeta;

import java.util.Collections;
import java.util.Map;

/**
 * Plugin configuration resolution result.
 *
 * @author Nacos
 */
public class PluginConfigResolution {
    
    private final Map<String, String> config;
    
    private final Map<String, PluginConfigValueMeta> valueMetas;
    
    public PluginConfigResolution(Map<String, String> config,
        Map<String, PluginConfigValueMeta> valueMetas) {
        this.config = config == null ? Collections.emptyMap() : config;
        this.valueMetas = valueMetas == null ? Collections.emptyMap() : valueMetas;
    }
    
    public Map<String, String> getConfig() {
        return config;
    }
    
    public Map<String, PluginConfigValueMeta> getValueMetas() {
        return valueMetas;
    }
}
