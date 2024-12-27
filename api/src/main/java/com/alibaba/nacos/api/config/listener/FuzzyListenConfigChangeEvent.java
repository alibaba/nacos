/*
 * Copyright 1999-2023 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.api.config.listener;

/**
 * Represents a fuzzy listening configuration change event.
 *
 * <p>This event indicates that a change has occurred in a configuration that matches a fuzzy listening pattern.
 *
 * @author stone-98
 * @date 2024/3/12
 */
public class FuzzyListenConfigChangeEvent {
    
    /**
     * The group of the configuration that has changed.
     */
    private String group;
    
    /**
     * The data ID of the configuration that has changed.
     */
    private String dataId;
    
    /**
     * The type of change that has occurred (e.g., "ADD_CONFIG", "DELETE_CONFIG").
     */
    private String type;
    
    /**
     * Constructs an empty FuzzyListenConfigChangeEvent.
     */
    public FuzzyListenConfigChangeEvent() {
    }
    
    /**
     * Constructs a FuzzyListenConfigChangeEvent with the specified parameters.
     *
     * @param group  The group of the configuration that has changed
     * @param dataId The data ID of the configuration that has changed
     * @param type   The type of change that has occurred
     */
    public FuzzyListenConfigChangeEvent(String group, String dataId, String type) {
        this.group = group;
        this.dataId = dataId;
        this.type = type;
    }
    
    /**
     * Constructs and returns a new FuzzyListenConfigChangeEvent with the specified parameters.
     *
     * @param group  The group of the configuration that has changed
     * @param dataId The data ID of the configuration that has changed
     * @param type   The type of change that has occurred
     * @return A new FuzzyListenConfigChangeEvent instance
     */
    public static FuzzyListenConfigChangeEvent build(String group, String dataId, String type) {
        return new FuzzyListenConfigChangeEvent(group, dataId, type);
    }
    
    public String getGroup() {
        return group;
    }
    
    public void setGroup(String group) {
        this.group = group;
    }
    
    public String getDataId() {
        return dataId;
    }
    
    public void setDataId(String dataId) {
        this.dataId = dataId;
    }
    
    public String getType() {
        return type;
    }
    
    public void setType(String type) {
        this.type = type;
    }
    
    /**
     * Returns a string representation of the FuzzyListenConfigChangeEvent.
     *
     * @return A string representation of the event
     */
    @Override
    public String toString() {
        return "FuzzyListenConfigChangeEvent{" + "group='" + group + '\'' + ", dataId='" + dataId + '\'' + ", type='"
                + type + '\'' + '}';
    }
}
