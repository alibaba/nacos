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
 * Watch notify event, including service change/watch initial.
 *
 * @author tanyongquan
 */
public class NamingFuzzyWatchNotifyEvent extends Event {
    
    private final String scope;
    
    private String watcherUuid;
    
    private String serviceKey;
    
    private String pattern;
    
    private final String changedType;
    
    private final String syncType;
    
    private NamingFuzzyWatchNotifyEvent(String scope, String pattern, String serviceKey, String changedType,
            String syncType, String watcherUuid) {
        this.scope = scope;
        this.pattern = pattern;
        this.serviceKey = serviceKey;
        this.changedType = changedType;
        this.syncType = syncType;
        this.watcherUuid = watcherUuid;
    }
    
    public static NamingFuzzyWatchNotifyEvent build(String eventScope, String pattern, String serviceKey,
            String changedType, String syncType) {
        return new NamingFuzzyWatchNotifyEvent(eventScope, pattern, serviceKey, changedType, syncType, null);
    }
    
    public static NamingFuzzyWatchNotifyEvent build(String eventScope, String pattern, String serviceKey,
            String changedType, String syncType, String watcherUuid) {
        return new NamingFuzzyWatchNotifyEvent(eventScope, pattern, serviceKey, changedType, syncType, watcherUuid);
    }
    
    public String getPattern() {
        return pattern;
    }
    
    public String getChangedType() {
        return changedType;
    }
    
    @Override
    public String scope() {
        return this.scope;
    }
    
    public String getWatcherUuid() {
        return watcherUuid;
    }
    
    public String getServiceKey() {
        return serviceKey;
    }
    
    public String getScope() {
        return scope;
    }
    
    public String getSyncType() {
        return syncType;
    }
}
