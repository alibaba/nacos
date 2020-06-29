/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.api.config;

/**
 * ConfigChangeItem.
 *
 * @author rushsky518
 */
public class ConfigChangeItem {
    
    private String key;
    
    private String oldValue;
    
    private String newValue;
    
    private PropertyChangeType type;
    
    public ConfigChangeItem(String key, String oldValue, String newValue) {
        this.key = key;
        this.oldValue = oldValue;
        this.newValue = newValue;
    }
    
    public String getKey() {
        return key;
    }
    
    public void setKey(String key) {
        this.key = key;
    }
    
    public String getOldValue() {
        return oldValue;
    }
    
    public void setOldValue(String oldValue) {
        this.oldValue = oldValue;
    }
    
    public String getNewValue() {
        return newValue;
    }
    
    public void setNewValue(String newValue) {
        this.newValue = newValue;
    }
    
    public PropertyChangeType getType() {
        return type;
    }
    
    public void setType(PropertyChangeType type) {
        this.type = type;
    }
    
    @Override
    public String toString() {
        return "ConfigChangeItem{" + "key='" + key + '\'' + ", oldValue='" + oldValue + '\'' + ", newValue='" + newValue
                + '\'' + ", type=" + type + '}';
    }
}
