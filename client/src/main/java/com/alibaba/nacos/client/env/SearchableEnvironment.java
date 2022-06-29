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

import com.alibaba.nacos.client.constant.Constants;
import com.alibaba.nacos.common.utils.StringUtils;

import java.util.Properties;

/**
 * Searchable environment.
 *
 * @author onewe
 */
class SearchableEnvironment implements NacosEnvironment {
    
    private final PropertySourceSearch sourceSearch;
    
    private final DefaultSettingPropertySource defaultSettingPropertySource;
    
    SearchableEnvironment(Properties properties) {
        if (properties == null) {
            properties = new Properties();
        }
        PropertiesPropertySource customizePropertySource = new PropertiesPropertySource(properties);
        JvmArgsPropertySource jvmArgsPropertySource = new JvmArgsPropertySource();
        SystemEnvPropertySource systemEnvPropertySource = new SystemEnvPropertySource();
        
        String searchPattern = jvmArgsPropertySource.getProperty(Constants.SysEnv.NACOS_ENVS_SEARCH);
        if (StringUtils.isBlank(searchPattern)) {
            searchPattern = systemEnvPropertySource.getProperty(
                    Constants.SysEnv.NACOS_ENVS_SEARCH.toUpperCase().replace('.', '_'));
        }
        
        this.sourceSearch = PropertySourceSearch.resolve(searchPattern, customizePropertySource, jvmArgsPropertySource,
                systemEnvPropertySource);
        
        this.defaultSettingPropertySource = new DefaultSettingPropertySource();
    }
    
    @Override
    public String getProperty(String key) {
        return getProperty(key, null);
    }
    
    @Override
    public String getProperty(String key, String defaultValue) {
        return sourceSearch.search(propertySource -> propertySource.getProperty(key),
                () -> defaultSettingPropertySource.getProperty(key), value -> {
                    if (StringUtils.isBlank(value)) {
                        return defaultValue;
                    }
                    return value;
                });
    }
    
    @Override
    public Boolean getBoolean(String key) {
        return getBoolean(key, null);
    }
    
    @Override
    public Boolean getBoolean(String key, Boolean defaultValue) {
    
        return sourceSearch.search(propertySource -> propertySource.getProperty(key),
                () -> defaultSettingPropertySource.getProperty(key), value -> {
                    if (value == null) {
                        return defaultValue;
                    }
                    if (StringUtils.equalsIgnoreCase(value, Boolean.TRUE.toString())) {
                        return Boolean.TRUE;
                    }
                
                    if (StringUtils.equalsIgnoreCase(value, Boolean.FALSE.toString())) {
                        return Boolean.FALSE;
                    }
                    return defaultValue;
                });
    }
    
    @Override
    public Integer getInteger(String key) {
        return getInteger(key, null);
    }
    
    @Override
    public Integer getInteger(String key, Integer defaultValue) {
        return sourceSearch.search(propertySource -> propertySource.getProperty(key),
                () -> defaultSettingPropertySource.getProperty(key), value -> {
                    if (StringUtils.isBlank(value)) {
                        return defaultValue;
                    }
                    try {
                        return Integer.valueOf(value);
                    } catch (Exception e) {
                        // ignore
                        return defaultValue;
                    }
                });
    }
    
    @Override
    public Long getLong(String key) {
        return getLong(key, null);
    }
    
    @Override
    public Long getLong(String key, Long defaultValue) {
        return sourceSearch.search(propertySource -> propertySource.getProperty(key),
                () -> defaultSettingPropertySource.getProperty(key), value -> {
                    if (StringUtils.isBlank(value)) {
                        return defaultValue;
                    }
                    try {
                        return Long.valueOf(value);
                    } catch (Exception e) {
                        // ignore
                        return defaultValue;
                    }
                });
    }
    
}
