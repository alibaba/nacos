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

package com.alibaba.nacos.api.config.remote.request;

/**
 * Represents a request to notify changes when a fuzzy watched configuration changed.
 *
 * <p>This request is used to notify clients about changes in configurations that match fuzzy listening patterns.
 *
 * @author stone-98
 * @date 2024/3/13
 */
public class ConfigFuzzyWatchChangeNotifyRequest extends AbstractFuzzyWatchNotifyRequest {
    
    /**
     * The groupKey of the configuration that has changed.
     */
    private String groupKey;
    
    /**
     * Indicates whether the configuration exists or not.
     */
    private String changeType;
    
    /**
     * Constructs an empty FuzzyListenNotifyChangeRequest.
     */
    public ConfigFuzzyWatchChangeNotifyRequest() {
    }
    
    /**
     * Constructs a FuzzyListenNotifyChangeRequest with the specified parameters.
     *
     * @param groupKey   The group of the configuration that has changed
     * @param changeType Indicates whether the configuration exists or not
     */
    public ConfigFuzzyWatchChangeNotifyRequest(String groupKey, String changeType) {
        this.groupKey = groupKey;
        this.changeType = changeType;
    }
    
    public String getGroupKey() {
        return groupKey;
    }
    
    public void setGroupKey(String groupKey) {
        this.groupKey = groupKey;
    }
    
    public String getChangeType() {
        return changeType;
    }
    
    public void setChangeType(String changeType) {
        this.changeType = changeType;
    }
    
    /**
     * Returns a string representation of the FuzzyListenNotifyChangeRequest.
     *
     * @return A string representation of the request
     */
    @Override
    public String toString() {
        return "FuzzyListenNotifyChangeRequest{" + '\'' + ", groupKey='" + groupKey + '\'' + ", changeType="
                + changeType + '}';
    }
    
}
