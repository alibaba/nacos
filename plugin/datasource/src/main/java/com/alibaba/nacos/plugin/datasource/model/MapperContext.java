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

package com.alibaba.nacos.plugin.datasource.model;

import java.util.HashMap;
import java.util.Map;

/**
 * Unified input parameters of the Mapper class.
 *
 * @author hyx
 **/

public class MapperContext {
    
    private Map<String, Object> paramMap;
    
    public MapperContext() {
        this.paramMap = new HashMap<>();
    }
    
    /**
     * Returns the value to which the key is mapped.
     *
     * @param key The key whose associated value is to be returned
     * @return The value to which the key is mapped
     */
    public Object get(String key) {
        return paramMap.get(key);
    }
    
    /**
     * Associates the value with the key in this map.
     *
     * @param key Key with which the value is to be associated
     * @param value Value to be associated with the specified key
     */
    public void put(String key, Object value) {
        this.paramMap.put(key, value);
    }
    
    @Override
    public boolean equals(Object o) {
        return super.equals(o);
    }
    
    @Override
    public int hashCode() {
        return super.hashCode();
    }
    
    @Override
    public String toString() {
        return "MapperContext{" + "paramMap=" + paramMap + '}';
    }
}
