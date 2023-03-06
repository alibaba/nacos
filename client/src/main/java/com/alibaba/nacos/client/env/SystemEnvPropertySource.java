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

import java.util.Map;
import java.util.Properties;

class SystemEnvPropertySource extends AbstractPropertySource {
    
    private final Map<String, String> env = System.getenv();
    
    @Override
    SourceType getType() {
        return SourceType.ENV;
    }
    
    @Override
    String getProperty(String key) {
        String checkedKey = checkPropertyName(key);
        if (checkedKey == null) {
            final String upperCaseKey = key.toUpperCase();
            if (!upperCaseKey.equals(key)) {
                checkedKey = checkPropertyName(upperCaseKey);
            }
        }
        if (checkedKey == null) {
            return null;
        }
        return env.get(checkedKey);
    }
    
    /**
     * copy from https://github.com/spring-projects/spring-framework.git
     * Copyright 2002-2021 the original author or authors.
     * Since:
     * 3.1
     * Author:
     * Chris Beams, Juergen Hoeller
     */
    private String checkPropertyName(String name) {
        // Check name as-is
        if (containsKey(name)) {
            return name;
        }
        // Check name with just dots replaced
        String noDotName = name.replace('.', '_');
        if (!name.equals(noDotName) && containsKey(noDotName)) {
            return noDotName;
        }
        // Check name with just hyphens replaced
        String noHyphenName = name.replace('-', '_');
        if (!name.equals(noHyphenName) && containsKey(noHyphenName)) {
            return noHyphenName;
        }
        // Check name with dots and hyphens replaced
        String noDotNoHyphenName = noDotName.replace('-', '_');
        if (!noDotName.equals(noDotNoHyphenName) && containsKey(noDotNoHyphenName)) {
            return noDotNoHyphenName;
        }
        // Give up
        return null;
    }
    
    @Override
    boolean containsKey(String name) {
        return this.env.containsKey(name);
    }
    
    @Override
    Properties asProperties() {
        Properties properties = new Properties();
        properties.putAll(this.env);
        return properties;
    }
}
