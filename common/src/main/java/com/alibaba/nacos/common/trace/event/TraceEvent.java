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

package com.alibaba.nacos.common.trace.event;

import com.alibaba.nacos.common.notify.Event;

/**
 * Trace event.
 *
 * @author yanda
 */
public class TraceEvent extends Event {
    
    private static final long serialVersionUID = -3065900892505697062L;
    
    private final String type;
    
    private final long eventTime;
    
    private final String namespace;
    
    private final String group;
    
    private final String name;
    
    public String getType() {
        return type;
    }
    
    public long getEventTime() {
        return eventTime;
    }
    
    public String getNamespace() {
        return namespace;
    }
    
    public String getGroup() {
        return group;
    }
    
    public String getName() {
        return name;
    }
    
    public TraceEvent(String eventType, long eventTime, String namespace, String group, String name) {
        this.type = eventType;
        this.eventTime = eventTime;
        this.namespace = namespace;
        this.group = group;
        this.name = name;
    }
}
