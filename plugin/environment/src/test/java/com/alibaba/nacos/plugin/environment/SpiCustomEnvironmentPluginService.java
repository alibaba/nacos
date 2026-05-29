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

package com.alibaba.nacos.plugin.environment;

import com.alibaba.nacos.plugin.environment.spi.CustomEnvironmentPluginService;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

public class SpiCustomEnvironmentPluginService implements CustomEnvironmentPluginService {
    
    @Override
    public Map<String, Object> customValue(Map<String, Object> property) {
        return property;
    }
    
    @Override
    public Set<String> propertyKey() {
        return Collections.singleton("spi.key");
    }
    
    @Override
    public Integer order() {
        return 10;
    }
    
    @Override
    public String pluginName() {
        return "spi-environment";
    }
}
