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
    
    private final Map<ApplyScope, Properties> propertiesMap = new HashMap<>(8);
    
    public PropertiesPropertySource() {
        Properties properties = new Properties();
        propertiesMap.put(ApplyScope.GLOBAL, properties);
    }
    
    @Override
    SourceType getType() {
        return SourceType.PROPERTIES;
    }
    
    @Override
    String getProperty(String key) {
        return propertiesMap.get(ApplyScope.GLOBAL).getProperty(key);
    }
    
    String getProperty(ApplyScope scope, String key) {
        if (scope == null || ApplyScope.GLOBAL.equals(scope)) {
            return propertiesMap.get(ApplyScope.GLOBAL).getProperty(key);
        }
        String value;
        final Properties properties = propertiesMap.get(scope);
        if (properties == null) {
            value = null;
        } else {
            value = properties.getProperty(key);
        }
        
        if (value == null) {
            value = propertiesMap.get(ApplyScope.GLOBAL).getProperty(key);
        }
        return value;
    }
    
    @Override
    boolean containsKey(String key) {
        return propertiesMap.get(ApplyScope.GLOBAL).containsKey(key);
    }
    
    boolean containsKey(ApplyScope scope, String key) {
        if (scope == null || ApplyScope.GLOBAL.equals(scope)) {
            return propertiesMap.get(ApplyScope.GLOBAL).containsKey(key);
        }
        boolean containing;
        final Properties properties = propertiesMap.get(scope);
        if (properties == null) {
            containing = false;
        } else {
            containing = properties.containsKey(key);
        }
        if (!containing) {
            containing = propertiesMap.get(ApplyScope.GLOBAL).containsKey(key);
        }
        return containing;
    }
    
    @Override
    Properties asProperties() {
        Properties properties = new Properties();
        properties.putAll(propertiesMap.get(ApplyScope.GLOBAL));
        return properties;
    }
    
    Properties asProperties(ApplyScope applyScope) {
        if (applyScope == null || ApplyScope.GLOBAL.equals(applyScope)) {
            Properties properties = new Properties();
            properties.putAll(propertiesMap.get(ApplyScope.GLOBAL));
            return properties;
        }
        Properties properties = new Properties();
        properties.putAll(propertiesMap.get(ApplyScope.GLOBAL));
    
        final Properties applyScopeProperties = propertiesMap.get(applyScope);
        if (applyScopeProperties != null) {
            properties.putAll(applyScopeProperties);
        }
        
        return properties;
    }
    
    synchronized void setProperty(ApplyScope scope, String key, String value) {
        if (scope == null) {
            scope = ApplyScope.GLOBAL;
        }
        final Properties properties = this.propertiesMap.computeIfAbsent(scope, s -> new Properties());
        properties.setProperty(key, value);
    }
    
    synchronized void addProperties(ApplyScope scope, Properties properties) {
        if (scope == null) {
            scope = ApplyScope.GLOBAL;
        }
        final Properties existProperties = this.propertiesMap.computeIfAbsent(scope, s -> new Properties());
        
        existProperties.putAll(properties);
    }
}
