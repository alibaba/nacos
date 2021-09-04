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

package com.alibaba.nacos.istio.common;

import com.alibaba.nacos.istio.mcp.NacosMcpService;
import com.alibaba.nacos.istio.misc.Loggers;
import com.alibaba.nacos.istio.util.IstioExecutor;
import com.alibaba.nacos.istio.xds.NacosXdsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * @author special.fy
 */
@Component
public class EventProcessor {

    private static final int MAX_WAIT_EVENT_TIME = 100;

    @Autowired
    private NacosMcpService nacosMcpService;

    @Autowired
    private NacosXdsService nacosXdsService;

    @Autowired
    private NacosResourceManager resourceManager;

    private final BlockingQueue<Event> events;

    public EventProcessor() {
        events = new ArrayBlockingQueue<>(20);
    }

    public void notify(Event event) {
        try {
            events.put(event);
        } catch (InterruptedException e) {
            Loggers.MAIN.warn("There are too many events, this event {} will be ignored.", event.getType());
        }
    }

    @PostConstruct
    public void handleEvents() {
        new Consumer("handle events").start();
    }

    private class Consumer extends Thread {

        Consumer(String name) {
            setName(name);
        }

        @Override
        @SuppressWarnings("InfiniteLoopStatement")
        public void run() {
            Future<Void> task = null;
            boolean hasNewEvent = false;
            Event lastEvent = null;
            while (true) {
                try {
                    // Today we only care about service event,
                    // so we simply ignore event until the last task has been completed.
                    Event event = events.poll(MAX_WAIT_EVENT_TIME, TimeUnit.MILLISECONDS);
                    if (event != null) {
                        hasNewEvent = true;
                        lastEvent = event;
                    }
                    if (hasClientConnection() && needNewTask(hasNewEvent, task)) {
                        task = IstioExecutor.asyncHandleEvent(new EventHandleTask(lastEvent));
                        hasNewEvent = false;
                        lastEvent = null;
                    }
                } catch (InterruptedException e) {
                    Loggers.MAIN.warn("Thread {} is be interrupted.", getName());
                }
            }
        }
    }

    private boolean hasClientConnection() {
        return nacosMcpService.hasClientConnection() || nacosXdsService.hasClientConnection();
    }

    private boolean needNewTask(boolean hasNewEvent, Future<Void> task) {
        return hasNewEvent && (task == null || task.isDone());
    }

    private class EventHandleTask implements Callable<Void> {

        private final Event event;

        EventHandleTask(Event event) {
            this.event = event;
        }

        @Override
        public Void call() throws Exception {
            ResourceSnapshot snapshot = resourceManager.createResourceSnapshot();
            nacosXdsService.handleEvent(snapshot, event);
            nacosMcpService.handleEvent(snapshot, event);

            return null;
        }
    }
}
