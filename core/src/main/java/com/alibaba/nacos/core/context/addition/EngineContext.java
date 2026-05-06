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

package com.alibaba.nacos.core.context.addition;

import com.alibaba.nacos.common.utils.VersionUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * Nacos engine context, to store some environment and engine information context. Such as version or system information.
 *
 * @author xiweng.yy
 */
public class EngineContext {
    
    /**
     * Nacos server version, such as v2.4.0.
     */
    private String version;
    
    private final Map<String, String> contexts;
    
    public EngineContext() {
        version = VersionUtils.version;
        contexts = new HashMap<>(1);
    }
    
    public String getVersion() {
        return version;
    }
    
    public void setVersion(String version) {
        this.version = version;
    }
    
    public String getContext(String key) {
        return contexts.get(key);
    }
    
    public String getContext(String key, String defaultValue) {
        return contexts.getOrDefault(key, defaultValue);
    }
    
    public void setContext(String key, String value) {
        contexts.put(key, value);
    }
}
