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

package com.alibaba.nacos.client.naming.event;

import com.alibaba.nacos.common.notify.Event;

/**
 * Event class for fuzzy listen notifications.
 *
 * <p>This class represents an event used for notifying fuzzy listen changes. It extends {@link Event}, indicating
 * that it may be processed asynchronously. The event contains information about the group, dataId, type, and UUID of
 * the notification.
 *
 * @author shiyiyue
 * @date 2025/01/13
 */
public class NamingFuzzyWatchLoadEvent extends Event {
    
    private String eventScope;
    
    /**
     * The groupKeyPattern of configuration.
     */
    private String groupKeyPattern;
    
    private int code;
    
    /**
     * Constructs a new FuzzyListenNotifyEvent with the specified group, dataId, and type.
     *
     * @param code            The type of notification.
     * @param groupKeyPattern The groupKeyPattern of notification.
     */
    private NamingFuzzyWatchLoadEvent(int code, String groupKeyPattern, String eventScope) {
        this.code = code;
        this.groupKeyPattern = groupKeyPattern;
        this.eventScope = eventScope;
    }
    
    /**
     * Builds a new FuzzyListenNotifyEvent with the specified group, dataId, and type.
     *
     * @param groupKeyPattern The groupKey of the configuration.
     * @return A new FuzzyListenNotifyEvent instance.
     */
    public static NamingFuzzyWatchLoadEvent buildEvent(int code, String groupKeyPattern, String scope) {
        return new NamingFuzzyWatchLoadEvent(code, groupKeyPattern, scope);
    }
    
    @Override
    public String scope() {
        return eventScope;
    }
    
    public String getGroupKeyPattern() {
        return groupKeyPattern;
    }
    
    public int getCode() {
        return code;
    }
}
