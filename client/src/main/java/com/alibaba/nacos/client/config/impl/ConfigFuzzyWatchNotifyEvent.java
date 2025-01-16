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

package com.alibaba.nacos.client.config.impl;

import com.alibaba.nacos.common.notify.Event;

/**
 * Event class for fuzzy listen notifications.
 *
 * <p>This class represents an event used for notifying fuzzy listen changes. It extends {@link Event}, indicating
 * that it may be processed asynchronously. The event contains information about the group, dataId, type, and UUID of
 * the notification.
 *
 * @author stone-98
 * @date 2024/3/4
 */
public class ConfigFuzzyWatchNotifyEvent extends Event {
    
    private String clientUuid;
    
    /**
     * The uuid of this watcher for which that this notify event .
     */
    private String watcherUuid;
    
    /**
     * The groupKeyPattern of configuration.
     */
    private String groupKeyPattern;
    
    private String groupKey;
    
    /**
     * The type of notification (e.g., ADD_CONFIG, DELETE_CONFIG).
     */
    private String changedType;
    
    private String syncType;
    
    /**
     * Constructs a new FuzzyListenNotifyEvent.
     */
    public ConfigFuzzyWatchNotifyEvent() {
    }
    
    /**
     * Constructs a new FuzzyListenNotifyEvent with the specified group, dataId, and type.
     *
     * @param groupKey    The groupKey of the configuration.
     * @param changedType The type of notification.
     */
    private ConfigFuzzyWatchNotifyEvent(String groupKey, String changedType, String syncType, String groupKeyPattern,
            String clientUuid, String watcherUuid) {
        this.groupKey = groupKey;
        this.syncType = syncType;
        this.changedType = changedType;
        this.groupKeyPattern = groupKeyPattern;
        this.clientUuid = clientUuid;
        this.watcherUuid = watcherUuid;
    }
    
    /**
     * Builds a new FuzzyListenNotifyEvent with the specified group, dataId, and type.
     *
     * @param groupKey The groupKey of the configuration.
     * @return A new FuzzyListenNotifyEvent instance.
     */
    public static ConfigFuzzyWatchNotifyEvent buildEvent(String groupKey,
            String groupKeyPattern, String changedType, String syncType, String clientUuid) {
        return buildEvent(groupKey, groupKeyPattern, changedType, syncType, clientUuid, null);
    }
    
    /**
     * Builds a new FuzzyListenNotifyEvent with the specified group, dataId, and type.
     *
     * @param groupKey The groupKey of the configuration.
     * @return A new FuzzyListenNotifyEvent instance.
     */
    public static ConfigFuzzyWatchNotifyEvent buildEvent(String groupKey,
            String groupKeyPattern, String changedType, String syncType, String clientUuid, String watcherUuid) {
        ConfigFuzzyWatchNotifyEvent configFuzzyWatchNotifyEvent = new ConfigFuzzyWatchNotifyEvent(groupKey, changedType,
                syncType, groupKeyPattern, clientUuid, watcherUuid);
        return configFuzzyWatchNotifyEvent;
    }
    
    /**
     * Gets the UUID (Unique Identifier) of the listener.
     *
     * @return The UUID of the listener.
     */
    public String getWatcherUuid() {
        return watcherUuid;
    }
    
    public String getClientUuid() {
        return clientUuid;
    }
    
    public String getGroupKeyPattern() {
        return groupKeyPattern;
    }
    
    public String getGroupKey() {
        return groupKey;
    }
    
    public String getSyncType() {
        return syncType;
    }
    
    /**
     * Gets the type of notification.
     *
     * @return The type of notification.
     */
    public String getChangedType() {
        return changedType;
    }
}
