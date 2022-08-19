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

import java.util.Properties;

class JvmArgsPropertySource extends AbstractPropertySource {
    
    private final Properties properties;
    
    JvmArgsPropertySource() {
        this.properties = System.getProperties();
    }
    
    @Override
    SourceType getType() {
        return SourceType.JVM;
    }
    
    @Override
    String getProperty(String key) {
        return properties.getProperty(key);
    }
    
    @Override
    boolean containsKey(String key) {
        return properties.containsKey(key);
    }
    
    @Override
    Properties asProperties() {
        Properties properties = new Properties();
        properties.putAll(this.properties);
        return properties;
    }
}
