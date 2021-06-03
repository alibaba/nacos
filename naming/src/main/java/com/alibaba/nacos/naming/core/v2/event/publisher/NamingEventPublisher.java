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

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.common.notify.Event;
import com.alibaba.nacos.common.notify.ShardedEventPublisher;
import com.alibaba.nacos.common.notify.listener.Subscriber;
import com.alibaba.nacos.common.utils.ThreadUtils;
import com.alibaba.nacos.naming.misc.Loggers;
import com.alipay.sofa.jraft.util.concurrent.ConcurrentHashSet;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

/**
 * Event publisher for naming event.
 *
 * @author xiweng.yy
 */
public class NamingEventPublisher extends Thread implements ShardedEventPublisher {
    
    private static final String THREAD_NAME = "naming.publisher-";
    
    private static final int DEFAULT_WAIT_TIME = 60;
    
    private final Map<Class<? extends Event>, Set<Subscriber<? extends Event>>> subscribes = new ConcurrentHashMap<>();
    
    private volatile boolean initialized = false;
    
    private volatile boolean shutdown = false;
    
    private int queueMaxSize = -1;
    
    private BlockingQueue<Event> queue;
    
    private String publisherName;
    
    @Override
    public void init(Class<? extends Event> type, int bufferSize) {
        this.queueMaxSize = bufferSize;
        this.queue = new ArrayBlockingQueue<>(bufferSize);
        this.publisherName = type.getSimpleName();
        super.setName(THREAD_NAME + this.publisherName);
        super.setDaemon(true);
        super.start();
        initialized = true;
    }
    
    @Override
    public long currentEventSize() {
        return this.queue.size();
    }
    
    @Override
    public void addSubscriber(Subscriber subscriber) {
        addSubscriber(subscriber, subscriber.subscribeType());
    }
    
    @Override
    public void addSubscriber(Subscriber subscriber, Class<? extends Event> subscribeType) {
        subscribes.computeIfAbsent(subscribeType, inputType -> new ConcurrentHashSet<>());
        subscribes.get(subscribeType).add(subscriber);
    }
    
    @Override
    public void removeSubscriber(Subscriber subscriber) {
        removeSubscriber(subscriber, subscriber.subscribeType());
    }
    
    @Override
    public void removeSubscriber(Subscriber subscriber, Class<? extends Event> subscribeType) {
        subscribes.computeIfPresent(subscribeType, (inputType, subscribers) -> {
            subscribers.remove(subscriber);
            return subscribers.isEmpty() ? null : subscribers;
        });
    }
    
    @Override
    public boolean publish(Event event) {
        checkIsStart();
        boolean success = this.queue.offer(event);
        if (!success) {
            Loggers.EVT_LOG.warn("Unable to plug in due to interruption, synchronize sending time, event : {}", event);
            handleEvent(event);
            return true;
        }
        return true;
    }
    
    @Override
    public void notifySubscriber(Subscriber subscriber, Event event) {
        if (Loggers.EVT_LOG.isDebugEnabled()) {
            Loggers.EVT_LOG.debug("[NotifyCenter] the {} will received by {}", event, subscriber);
        }
        final Runnable job = () -> subscriber.onEvent(event);
        final Executor executor = subscriber.executor();
        if (executor != null) {
            executor.execute(job);
        } else {
            try {
                job.run();
            } catch (Throwable e) {
                Loggers.EVT_LOG.error("Event callback exception: ", e);
            }
        }
    }
    
    @Override
    public void shutdown() throws NacosException {
        this.shutdown = true;
        this.queue.clear();
    }
    
    @Override
    public void run() {
        try {
            waitSubscriberForInit();
            handleEvents();
        } catch (Exception e) {
            Loggers.EVT_LOG.error("Naming Event Publisher {}, stop to handle event due to unexpected exception: ",
                    this.publisherName, e);
        }
    }
    
    private void waitSubscriberForInit() {
        // To ensure that messages are not lost, enable EventHandler when
        // waiting for the first Subscriber to register
        for (int waitTimes = DEFAULT_WAIT_TIME; waitTimes > 0; waitTimes--) {
            if (shutdown || !subscribes.isEmpty()) {
                break;
            }
            ThreadUtils.sleep(1000L);
        }
    }
    
    private void handleEvents() {
        while (!shutdown) {
            try {
                final Event event = queue.take();
                handleEvent(event);
            } catch (InterruptedException e) {
                Loggers.EVT_LOG.warn("Naming Event Publisher {} take event from queue failed:", this.publisherName, e);
            }
        }
    }
    
    private void handleEvent(Event event) {
        Class<? extends Event> eventType = event.getClass();
        Set<Subscriber<? extends Event>> subscribers = subscribes.get(eventType);
        if (null == subscribers) {
            if (Loggers.EVT_LOG.isDebugEnabled()) {
                Loggers.EVT_LOG.debug("[NotifyCenter] No subscribers for slow event {}", eventType.getName());
            }
            return;
        }
        for (Subscriber subscriber : subscribers) {
            notifySubscriber(subscriber, event);
        }
    }
    
    void checkIsStart() {
        if (!initialized || shutdown) {
            throw new IllegalStateException("Publisher does not start");
        }
    }
    
    public String getStatus() {
        return String.format("Publisher %-30s: shutdown=%5s, queue=%7d/%-7d", publisherName, shutdown,
                currentEventSize(), queueMaxSize);
    }
}
