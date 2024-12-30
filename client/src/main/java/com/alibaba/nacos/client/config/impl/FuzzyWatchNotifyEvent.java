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
public class FuzzyWatchNotifyEvent extends Event {
    
    /**
     * The uuid of this watcher for which that this notify event .
     */
    private String uuid;
    
    /**
     * The groupKeyPattern of configuration.
     */
    private String groupKeyPattern;
    
    /**
     * The namespace of the configuration.
     */
    private String namespace;
    
    /**
     * The group of the configuration.
     */
    private String group;
    
    /**
     * The dataId of the configuration.
     */
    private String dataId;
    
    /**
     * The type of notification (e.g., ADD_CONFIG, DELETE_CONFIG).
     */
    private String type;
    
    /**
     * Constructs a new FuzzyListenNotifyEvent.
     */
    public FuzzyWatchNotifyEvent() {
    }

    /**
     * Constructs a new FuzzyListenNotifyEvent with the specified group, dataId, and type.
     *
     * @param group  The group of the configuration.
     * @param dataId The dataId of the configuration.
     * @param type   The type of notification.
     */
    public FuzzyWatchNotifyEvent(String namespace,String group, String dataId, String type, String groupKeyPattern) {
        this.group = group;
        this.dataId = dataId;
        this.namespace=namespace;
        this.type = type;
        this.groupKeyPattern = groupKeyPattern;
    }
    
    
    /**
     * Builds a new FuzzyListenNotifyEvent with the specified group, dataId, and type.
     *
     * @param group  The group of the configuration.
     * @param dataId The dataId of the configuration.
     * @param type   The type of notification.
     * @return A new FuzzyListenNotifyEvent instance.
     */
    public static FuzzyWatchNotifyEvent buildNotifyPatternAllListenersEvent(String namespace,String group, String dataId,
            String groupKeyPattern, String type) {
        return new FuzzyWatchNotifyEvent(namespace,group, dataId, type, groupKeyPattern);
    }
    
    /**
     * Gets the UUID (Unique Identifier) of the listener.
     *
     * @return The UUID of the listener.
     */
    public String getUuid() {
        return uuid;
    }
    
    /**
     * Sets the UUID (Unique Identifier) of the listener.
     *
     * @param uuid The UUID to set.
     */
    public void setUuid(String uuid) {
        this.uuid = uuid;
    }
    
    public String getGroupKeyPattern() {
        return groupKeyPattern;
    }
    
    public void setGroupKeyPattern(String groupKeyPattern) {
        this.groupKeyPattern = groupKeyPattern;
    }
    
    /**
     * Gets the group of the configuration.
     *
     * @return The group of the configuration.
     */
    public String getGroup() {
        return group;
    }
    
    /**
     * Sets the group of the configuration.
     *
     * @param group The group to set.
     */
    public void setGroup(String group) {
        this.group = group;
    }
    
    /**
     * Gets the dataId of the configuration.
     *
     * @return The dataId of the configuration.
     */
    public String getDataId() {
        return dataId;
    }
    
    /**
     * Sets the dataId of the configuration.
     *
     * @param dataId The dataId to set.
     */
    public void setDataId(String dataId) {
        this.dataId = dataId;
    }
    
    /**
     * Gets the type of notification.
     *
     * @return The type of notification.
     */
    public String getType() {
        return type;
    }
    
    /**
     * Sets the type of notification.
     *
     * @param type The type to set.
     */
    public void setType(String type) {
        this.type = type;
    }
}
