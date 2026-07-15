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

package com.alibaba.nacos.core.plugin.model.vo;

import com.alibaba.nacos.core.plugin.model.PluginConfigSourceType;

/**
 * Plugin configuration value metadata.
 *
 * @author Nacos
 */
public class PluginConfigValueMeta {
    
    private String key;
    
    private PluginConfigSourceType source;
    
    private boolean overridden;
    
    public String getKey() {
        return key;
    }
    
    public void setKey(String key) {
        this.key = key;
    }
    
    public PluginConfigSourceType getSource() {
        return source;
    }
    
    public void setSource(PluginConfigSourceType source) {
        this.source = source;
    }
    
    public boolean isOverridden() {
        return overridden;
    }
    
    public void setOverridden(boolean overridden) {
        this.overridden = overridden;
    }
    
    @Override
    public String toString() {
        return "PluginConfigValueMeta{" + "key='" + key + '\'' + ", source=" + source
            + ", overridden=" + overridden + '}';
    }
}
