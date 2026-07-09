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

import java.util.Collections;
import java.util.List;

/**
 * Plugin configuration key candidate.
 *
 * @author Nacos
 */
public class PluginConfigKeyCandidate {
    
    private final String itemKey;
    
    private final String standardKey;
    
    private final List<String> aliasKeys;
    
    public PluginConfigKeyCandidate(String itemKey, String standardKey, List<String> aliasKeys) {
        this.itemKey = itemKey;
        this.standardKey = standardKey;
        this.aliasKeys = aliasKeys == null ? Collections.emptyList() : aliasKeys;
    }
    
    public String getItemKey() {
        return itemKey;
    }
    
    public String getStandardKey() {
        return standardKey;
    }
    
    public List<String> getAliasKeys() {
        return aliasKeys;
    }
}
