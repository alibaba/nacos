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
 * event published when a fuzzy watch pattern being suppressed because of pattern count or pattern matched config count  is over limit.
 * @author shiyiyue
 * @date 2025/01/13
 */
public class ConfigFuzzyWatchLoadEvent extends Event {
    
    private String clientUuid;
    
    /**
     * The groupKeyPattern of configuration.
     */
    private String groupKeyPattern;
    
    private int code;
    
    /**
     * Constructs a new ConfigFuzzyWatchLoadEvent.
     */
    public ConfigFuzzyWatchLoadEvent() {
    }
    
    /**
     * Constructs a new FuzzyListenNotifyEvent with the specified group, dataId, and type.
     *
     * @param code            The type of notification.
     * @param groupKeyPattern The groupKeyPattern of notification.
     */
    private ConfigFuzzyWatchLoadEvent(int code, String groupKeyPattern, String clientUuid) {
        this.code = code;
        this.groupKeyPattern = groupKeyPattern;
        this.clientUuid = clientUuid;
    }
    
    /**
     * Builds a new FuzzyListenNotifyEvent with the specified group, dataId, and type.
     *
     * @param groupKeyPattern The groupKey of the configuration.
     * @return A new FuzzyListenNotifyEvent instance.
     */
    public static ConfigFuzzyWatchLoadEvent buildEvent(int code, String groupKeyPattern, String clientUuid) {
        return new ConfigFuzzyWatchLoadEvent(code, groupKeyPattern, clientUuid);
    }
    
    public String getClientUuid() {
        return clientUuid;
    }
    
    public String getGroupKeyPattern() {
        return groupKeyPattern;
    }
    
    public int getCode() {
        return code;
    }
}
