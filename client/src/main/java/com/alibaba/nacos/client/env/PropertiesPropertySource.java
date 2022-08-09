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
    
    private PropertiesPropertySource parent;
    
    PropertiesPropertySource(){}
    
    PropertiesPropertySource(PropertiesPropertySource parent) {
        this.parent = parent;
    }
    
    @Override
    SourceType getType() {
        return SourceType.PROPERTIES;
    }
    
    @Override
    String getProperty(String key) {
    
        String value = properties.getProperty(key);
        if (value != null) {
            return value;
        }
        PropertiesPropertySource parent = this.parent;
        while (parent != null) {
            value = parent.properties.getProperty(key);
            if (value != null) {
                return value;
            }
            parent = parent.parent;
        }
        
        return null;
    }
    
    @Override
    boolean containsKey(String key) {
        boolean exist = properties.containsKey(key);
        if (exist) {
            return true;
        }
        PropertiesPropertySource parent = this.parent;
        while (parent != null) {
            exist = parent.properties.containsKey(key);
            if (exist) {
                return true;
            }
            parent = parent.parent;
        }
        return false;
    }
    
    @Override
    Properties asProperties() {
        List<Properties> propertiesList = new ArrayList<>(8);
        propertiesList.add(properties);
    
        PropertiesPropertySource parent = this.parent;
        while (parent != null) {
            propertiesList.add(parent.properties);
            parent = parent.parent;
        }
    
        Properties ret = new Properties();
        final ListIterator<Properties> iterator = propertiesList.listIterator(propertiesList.size());
        while (iterator.hasPrevious()) {
            final Properties properties = iterator.previous();
            ret.putAll(properties);
        }
        return ret;
    }
    
    synchronized void setProperty(String key, String value) {
        properties.setProperty(key, value);
    }
    
    synchronized void addProperties(Properties source) {
        properties.putAll(source);
    }
}
