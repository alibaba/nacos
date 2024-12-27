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

import com.alibaba.nacos.api.common.Constants;

/**
 * Represents a fuzzy listening configuration change event.
 *
 * <p>This event indicates that a change has occurred in a configuration that matches a fuzzy listening pattern.
 *
 * @author stone-98
 * @date 2024/3/12
 */
public class ConfigFuzzyWatchChangeEvent {
    
    /**
     * The group of the configuration that has changed.
     */
    private String group;
    
    /**
     * The data ID of the configuration that has changed.
     */
    private String dataId;
    
    /**
     * The namespace of the configuration that has changed.
     */
    private String namespace;
    
    /**
     * The change type of local watcher , contains {"ADD_CONFIG", "DELETE_CONFIG"}.
     * {@link Constants.ConfigChangedType}.
     */
    private String changedType;
    
    /**
     * the sync type that trigger this changed,contains {"FUZZY_WATCH_INIT_NOTIFY","FUZZY_WATCH_RESOURCE_CHANGED",
     * "FUZZY_WATCH_DIFF_SYNC_NOTIFY"}.
     */
    private String syncType;
    
    /**
     * Constructs a FuzzyListenConfigChangeEvent with the specified parameters.
     *
     * @param group       The group of the configuration that has changed
     * @param dataId      The data ID of the configuration that has changed
     * @param changedType The type of change that has occurred
     */
    private ConfigFuzzyWatchChangeEvent(String namespace, String group, String dataId, String changedType,
            String syncType) {
        this.group = group;
        this.dataId = dataId;
        this.namespace = namespace;
        this.changedType = changedType;
        this.syncType = syncType;
    }
    
    /**
     * Constructs and returns a new FuzzyListenConfigChangeEvent with the specified parameters.
     *
     * @param group       The group of the configuration that has changed
     * @param dataId      The data ID of the configuration that has changed
     * @param changedType The type of change that has occurred
     * @return A new FuzzyListenConfigChangeEvent instance
     */
    public static ConfigFuzzyWatchChangeEvent build(String namespace, String group, String dataId, String changedType,
            String syncType) {
        return new ConfigFuzzyWatchChangeEvent(namespace, group, dataId, changedType, syncType);
    }
    
    public String getNamespace() {
        return namespace;
    }
    
    public String getGroup() {
        return group;
    }
    
    public String getDataId() {
        return dataId;
    }
    
    public String getChangedType() {
        return changedType;
    }
    
    public String getSyncType() {
        return syncType;
    }
    
    @Override
    public String toString() {
        return "ConfigFuzzyWatchChangeEvent{" + "group='" + group + '\'' + ", dataId='" + dataId + '\''
                + ", namespace='" + namespace + '\'' + ", changedType='" + changedType + '\'' + ", syncType='"
                + syncType + '\'' + '}';
    }
}
