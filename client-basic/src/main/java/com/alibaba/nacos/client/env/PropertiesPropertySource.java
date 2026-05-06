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

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.Properties;

class PropertiesPropertySource extends AbstractPropertySource {
    
    private final Properties properties = new Properties();
    
    private final PropertiesPropertySource parent;
    
    PropertiesPropertySource() {
        this.parent = null;
    }
    
    PropertiesPropertySource(PropertiesPropertySource parent) {
        this.parent = parent;
    }
    
    @Override
    SourceType getType() {
        return SourceType.PROPERTIES;
    }
    
    @Override
    String getProperty(String key) {
        return getProperty(this, key);
    }
    
    private String getProperty(PropertiesPropertySource propertiesPropertySource, String key) {
        final String value = propertiesPropertySource.properties.getProperty(key);
        if (value != null) {
            return value;
        }
        final PropertiesPropertySource parent = propertiesPropertySource.parent;
        if (parent == null) {
            return null;
        }
        return getProperty(parent, key);
    }
    
    @Override
    boolean containsKey(String key) {
        return containsKey(this, key);
    }
    
    boolean containsKey(PropertiesPropertySource propertiesPropertySource, String key) {
        final boolean exist = propertiesPropertySource.properties.containsKey(key);
        if (exist) {
            return true;
        }
        final PropertiesPropertySource parent = propertiesPropertySource.parent;
        if (parent == null) {
            return false;
        }
        return containsKey(parent, key);
    }
    
    @Override
    Properties asProperties() {
        List<Properties> propertiesList = new ArrayList<>(8);
        
        propertiesList = lookForProperties(this, propertiesList);
        
        Properties ret = new Properties();
        final ListIterator<Properties> iterator = propertiesList.listIterator(propertiesList.size());
        while (iterator.hasPrevious()) {
            final Properties properties = iterator.previous();
            ret.putAll(properties);
        }
        return ret;
    }
    
    List<Properties> lookForProperties(PropertiesPropertySource propertiesPropertySource, List<Properties> propertiesList) {
        propertiesList.add(propertiesPropertySource.properties);
        final PropertiesPropertySource parent = propertiesPropertySource.parent;
        if (parent == null) {
            return propertiesList;
        }
        return lookForProperties(parent, propertiesList);
    }
    
    synchronized void setProperty(String key, String value) {
        properties.setProperty(key, value);
    }
    
    synchronized void addProperties(Properties source) {
        properties.putAll(source);
    }
}
