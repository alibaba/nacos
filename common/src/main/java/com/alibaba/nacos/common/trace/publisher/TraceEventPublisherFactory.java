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

package com.alibaba.nacos.common.trace.publisher;

import com.alibaba.nacos.common.notify.Event;
import com.alibaba.nacos.common.notify.EventPublisher;
import com.alibaba.nacos.common.notify.EventPublisherFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * event publisher factory for trace event.
 *
 * @author yanda
 */

public class TraceEventPublisherFactory implements EventPublisherFactory {
    private static final TraceEventPublisherFactory INSTANCE = new TraceEventPublisherFactory();

    private final Map<Class<? extends Event>, TraceEventPublisher> publisher;

    private TraceEventPublisherFactory() {
        publisher = new ConcurrentHashMap<>();
    }

    public static TraceEventPublisherFactory getInstance() {
        return INSTANCE;
    }

    @Override
    public EventPublisher apply(final Class<? extends Event> eventType, final Integer maxQueueSize) {
        // Like ClientEvent$ClientChangeEvent cache by ClientEvent
        Class<? extends Event> cachedEventType =
                eventType.isMemberClass() ? (Class<? extends Event>) eventType.getEnclosingClass() : eventType;
        publisher.computeIfAbsent(cachedEventType, eventClass -> {
            TraceEventPublisher result = new TraceEventPublisher();
            result.init(eventClass, maxQueueSize);
            return result;
        });
        return publisher.get(cachedEventType);
    }

    public String getAllPublisherStatues() {
        StringBuilder result = new StringBuilder("Trace event publisher statues:\n");
        for (TraceEventPublisher each : publisher.values()) {
            result.append('\t').append(each.getStatus()).append('\n');
        }
        return result.toString();
    }
}
