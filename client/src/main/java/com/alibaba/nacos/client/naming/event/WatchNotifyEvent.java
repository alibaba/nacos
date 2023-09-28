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

import com.alibaba.nacos.api.naming.pojo.Service;
import com.alibaba.nacos.common.notify.Event;

/**
 * Watch notify event, including service change/watch initial.
 *
 * @author tanyongquan
 */
public class WatchNotifyEvent extends Event {
    
    private final String eventScope;
    
    private final Service changedService;
    
    private String pattern;
    
    private String uuid;
    
    private final String serviceChangedType;
    
    private WatchNotifyEvent(String eventScope, Service changedService, String pattern, String uuid, String serviceChangedType) {
        this(eventScope, changedService, pattern, serviceChangedType);
        this.uuid = uuid;
    }
    
    private WatchNotifyEvent(String eventScope, Service changedService, String pattern, String serviceChangedType) {
        this.eventScope = eventScope;
        this.changedService = changedService;
        this.serviceChangedType = serviceChangedType;
        this.pattern = pattern;
    }
    
    public static WatchNotifyEvent buildNotifyPatternSpecificListenerEvent(String eventScope, Service changedService,
            String pattern, String uuid, String serviceChangedType) {
        return new WatchNotifyEvent(eventScope, changedService, pattern, uuid, serviceChangedType);
    }
    
    public static WatchNotifyEvent buildNotifyPatternAllListenersEvent(String eventScope, Service changedService,
            String pattern, String serviceChangedType) {
        return new WatchNotifyEvent(eventScope, changedService, pattern, serviceChangedType);
    }
    
    public Service getChangedService() {
        return changedService;
    }
    
    public String getPattern() {
        return pattern;
    }
    
    public void setPattern(String pattern) {
        this.pattern = pattern;
    }
    
    public String getUuid() {
        return uuid;
    }
    
    public String getServiceChangedType() {
        return serviceChangedType;
    }
    
    @Override
    public String scope() {
        return this.eventScope;
    }
}
