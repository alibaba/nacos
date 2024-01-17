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
    
    private final Map<String, Object> whereParamMap;
    
    private final Map<String, Object> updateParamMap;
    
    private final Map<String, String> contextParamMap;
    
    private int startRow;
    
    private int pageSize;
    
    public MapperContext() {
        this.whereParamMap = new HashMap<>();
        this.updateParamMap = new HashMap<>();
        this.contextParamMap = new HashMap<>();
    }
    
    public MapperContext(int startRow, int pageSize) {
        this();
        this.startRow = startRow;
        this.pageSize = pageSize;
    }
    
    /**
     * Returns the value to which the key is mapped, it will return the WHERE parameter in the SQL statement.
     *
     * @param key The key whose associated value is to be returned
     * @return The value to which the key is mapped
     */
    public Object getWhereParameter(String key) {
        return whereParamMap.get(key);
    }
    
    /**
     * Associates the value with the key in this map, it will contain the WHERE parameter in the SQL statement.
     *
     * @param key   Key with which the value is to be associated
     * @param value Value to be associated with the specified key
     */
    public void putWhereParameter(String key, Object value) {
        this.whereParamMap.put(key, value);
    }
    
    /**
     * Returns the value to which the key is mapped, it will return the context param.
     *
     * @param key The key whose associated value is to be returned
     * @return The value to which the key is mapped
     */
    public String getContextParameter(String key) {
        return contextParamMap.get(key);
    }
    
    /**
     * Associates the value with the key in this map, it will contain the context parameter.
     *
     * @param key   Key with which the value is to be associated
     * @param value Value to be associated with the specified key
     */
    public void putContextParameter(String key, String value) {
        this.contextParamMap.put(key, value);
    }
    
    /**
     * Returns the value to which the key is mapped, it will return the UPDATE parameter in the SQL statement.
     *
     * @param key The key whose associated value is to be returned
     * @return The value to which the key is mapped
     */
    public Object getUpdateParameter(String key) {
        return updateParamMap.get(key);
    }
    
    /**
     * Associates the value with the key in this map, it will contain the UPDATE parameter in the SQL statement.
     *
     * @param key   Key with which the value is to be associated
     * @param value Value to be associated with the specified key
     */
    public void putUpdateParameter(String key, Object value) {
        this.updateParamMap.put(key, value);
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
        return "MapperContext{" + "whereParamMap=" + whereParamMap + '}';
    }
    
    public int getStartRow() {
        return startRow;
    }
    
    public void setStartRow(int startRow) {
        this.startRow = startRow;
    }
    
    public int getPageSize() {
        return pageSize;
    }
    
    public void setPageSize(int pageSize) {
        this.pageSize = pageSize;
    }
}
