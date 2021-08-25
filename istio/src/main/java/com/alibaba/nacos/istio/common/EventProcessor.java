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
import com.alibaba.nacos.istio.xds.NacosXdsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/**
 * @author special.fy
 */
@Component
public class EventProcessor {

    @Autowired
    private NacosMcpService nacosMcpService;

    @Autowired
    private NacosXdsService nacosXdsService;

    @Autowired
    private NacosResourceManager resourceManager;

    private final BlockingQueue<Event> events = new ArrayBlockingQueue<>(20);

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
            while (true) {
                try {
                    Event event = events.take();
                    if (nacosMcpService.hasClientConnection() || nacosXdsService.hasClientConnection()) {
                        populateEvent(event);
                    }
                } catch (InterruptedException e) {
                    Loggers.MAIN.warn("Thread {} is be interrupted.", getName());
                }
            }
        }

        private void populateEvent(Event event) {
            ResourceSnapshot snapshot = resourceManager.createResourceSnapshot();
            nacosXdsService.handleEvent(snapshot, event);
            nacosMcpService.handleEvent(snapshot, event);
        }
    }
}
