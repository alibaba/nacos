/*
 * Copyright 1999-2022 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.client.env;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

class PropertiesPropertySource extends AbstractPropertySource {
    
    private final Properties globalProperties = new Properties();
    
    private final Map<Object, Properties> childProperties = new HashMap<>(8);
    
    @Override
    SourceType getType() {
        return SourceType.PROPERTIES;
    }
    
    @Override
    String getProperty(String key) {
        return globalProperties.getProperty(key);
    }
    
    String getProperty(Object scope, String key) {
        if (scope == null) {
            return globalProperties.getProperty(key);
        }
        String value;
        final Properties properties = childProperties.get(scope);
        if (properties == null) {
            value = null;
        } else {
            value = properties.getProperty(key);
        }
        
        if (value == null) {
            value = globalProperties.getProperty(key);
        }
        return value;
    }
    
    @Override
    boolean containsKey(String key) {
        return globalProperties.containsKey(key);
    }
    
    boolean containsKey(Object scope, String key) {
        if (scope == null) {
            return globalProperties.containsKey(key);
        }
        boolean containing;
        final Properties properties = childProperties.get(scope);
        if (properties == null) {
            containing = false;
        } else {
            containing = properties.containsKey(key);
        }
        if (!containing) {
            containing = globalProperties.containsKey(key);
        }
        return containing;
    }
    
    @Override
    Properties asProperties() {
        Properties properties = new Properties();
        properties.putAll(globalProperties);
        return properties;
    }
    
    Properties asProperties(Object applyScope) {
        if (applyScope == null) {
            Properties properties = new Properties();
            properties.putAll(globalProperties);
            return properties;
        }
        Properties properties = new Properties();
        properties.putAll(globalProperties);
    
        final Properties applyScopeProperties = childProperties.get(applyScope);
        if (applyScopeProperties != null) {
            properties.putAll(applyScopeProperties);
        }
        
        return properties;
    }
    
    synchronized void setProperty(Object scope, String key, String value) {
        if (scope == null) {
            globalProperties.setProperty(key, value);
            return;
        }
        final Properties properties = this.childProperties.computeIfAbsent(scope, s -> new Properties());
        properties.setProperty(key, value);
    }
    
    synchronized void addProperties(Object scope, Properties properties) {
        if (scope == null) {
            globalProperties.putAll(properties);
            return;
        }
        final Properties existProperties = this.childProperties.computeIfAbsent(scope, s -> new Properties());
        existProperties.putAll(properties);
    }
}
