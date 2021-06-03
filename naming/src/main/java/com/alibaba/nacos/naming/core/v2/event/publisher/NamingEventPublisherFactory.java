/*
 * Copyright 1999-2020 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.naming.core.v2.event.publisher;

import com.alibaba.nacos.common.notify.Event;
import com.alibaba.nacos.common.notify.EventPublisher;
import com.alibaba.nacos.common.notify.EventPublisherFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * event publisher factory for naming event.
 *
 * <p>
 * Some naming event is in order, so these event need publish by sync(with same thread and same queue).
 * </p>
 *
 * @author xiweng.yy
 */
public class NamingEventPublisherFactory implements EventPublisherFactory {
    
    private static final NamingEventPublisherFactory INSTANCE = new NamingEventPublisherFactory();
    
    private final Map<Class<? extends Event>, NamingEventPublisher> publisher;
    
    private NamingEventPublisherFactory() {
        publisher = new ConcurrentHashMap<>();
    }
    
    public static NamingEventPublisherFactory getInstance() {
        return INSTANCE;
    }
    
    @Override
    public EventPublisher apply(final Class<? extends Event> eventType, final Integer maxQueueSize) {
        // Like ClientEvent$ClientChangeEvent cache by ClientEvent
        Class<? extends Event> cachedEventType =
                eventType.isMemberClass() ? (Class<? extends Event>) eventType.getEnclosingClass() : eventType;
        publisher.computeIfAbsent(cachedEventType, eventClass -> {
            NamingEventPublisher result = new NamingEventPublisher();
            result.init(eventClass, maxQueueSize);
            return result;
        });
        return publisher.get(cachedEventType);
    }
    
    public String getAllPublisherStatues() {
        StringBuilder result = new StringBuilder("Naming event publisher statues:\n");
        for (NamingEventPublisher each : publisher.values()) {
            result.append("\t").append(each.getStatus()).append("\n");
        }
        return result.toString();
    }
}
