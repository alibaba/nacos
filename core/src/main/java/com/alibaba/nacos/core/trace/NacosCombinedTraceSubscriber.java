/*
 * Copyright 1999-2021 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.core.trace;

import com.alibaba.nacos.common.notify.Event;
import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.common.notify.listener.SmartSubscriber;
import com.alibaba.nacos.common.trace.event.TraceEvent;
import com.alibaba.nacos.common.trace.publisher.TraceEventPublisherFactory;
import com.alibaba.nacos.plugin.trace.NacosTracePluginManager;
import com.alibaba.nacos.plugin.trace.spi.NacosTraceSubscriber;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Combined trace events subscriber.
 *
 * @author xiweng.yy
 */
public class NacosCombinedTraceSubscriber extends SmartSubscriber {
    
    private final Map<Class<? extends TraceEvent>, Set<NacosTraceSubscriber>> interestedEvents;
    
    public NacosCombinedTraceSubscriber(Class<? extends TraceEvent> combinedEvent) {
        this.interestedEvents = new ConcurrentHashMap<>();
        TraceEventPublisherFactory.getInstance().addPublisherEvent(combinedEvent);
        for (NacosTraceSubscriber each : NacosTracePluginManager.getInstance().getAllTraceSubscribers()) {
            filterInterestedEvents(each, combinedEvent);
        }
        NotifyCenter.registerSubscriber(this, TraceEventPublisherFactory.getInstance());
    }
    
    private void filterInterestedEvents(NacosTraceSubscriber plugin, Class<? extends TraceEvent> combinedEvent) {
        for (Class<? extends TraceEvent> each : plugin.subscribeTypes()) {
            if (combinedEvent.isAssignableFrom(each)) {
                interestedEvents.compute(each, (eventClass, nacosTraceSubscribers) -> {
                    if (null == nacosTraceSubscribers) {
                        nacosTraceSubscribers = new HashSet<>();
                    }
                    nacosTraceSubscribers.add(plugin);
                    return nacosTraceSubscribers;
                });
            }
        }
    }
    
    @Override
    public List<Class<? extends Event>> subscribeTypes() {
        return new LinkedList<>(interestedEvents.keySet());
    }
    
    @Override
    public void onEvent(Event event) {
        Set<NacosTraceSubscriber> subscribers = interestedEvents.get(event.getClass());
        if (null == subscribers) {
            return;
        }
        TraceEvent traceEvent = (TraceEvent) event;
        for (NacosTraceSubscriber each : subscribers) {
            if (null != each.executor()) {
                each.executor().execute(() -> onEvent0(each, traceEvent));
            } else {
                onEvent0(each, traceEvent);
            }
        }
    }
    
    private void onEvent0(NacosTraceSubscriber subscriber, TraceEvent event) {
        try {
            subscriber.onEvent(event);
        } catch (Exception ignored) {
        }
    }
    
    public void shutdown() {
        NotifyCenter.deregisterSubscriber(this);
    }
}
