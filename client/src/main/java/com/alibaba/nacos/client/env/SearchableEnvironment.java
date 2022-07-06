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

/**
 * Searchable environment.
 *
 * @author onewe
 */
class SearchableEnvironment implements NacosEnvironment {
    
    private final PropertySourceSearch sourceSearch;
    
    SearchableEnvironment(Properties properties) {
        this.sourceSearch = PropertySourceSearch.build(properties);
    }
    
    @Override
    public String getProperty(String key) {
        return getProperty(key, null);
    }
    
    @Override
    public String getProperty(String key, String defaultValue) {
        return sourceSearch.search(key, String.class).orElse(defaultValue);
    }
    
    @Override
    public Boolean getBoolean(String key) {
        return getBoolean(key, null);
    }
    
    @Override
    public Boolean getBoolean(String key, Boolean defaultValue) {
        return sourceSearch.search(key, Boolean.class).orElse(defaultValue);
    }
    
    @Override
    public Integer getInteger(String key) {
        return getInteger(key, null);
    }
    
    @Override
    public Integer getInteger(String key, Integer defaultValue) {
        return sourceSearch.search(key, Integer.class).orElse(defaultValue);
    }
    
    @Override
    public Long getLong(String key) {
        return getLong(key, null);
    }
    
    @Override
    public Long getLong(String key, Long defaultValue) {
        return sourceSearch.search(key, Long.class).orElse(defaultValue);
    }
    
}
