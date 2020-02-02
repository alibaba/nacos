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

package com.alibaba.nacos.core.notify;

import com.alibaba.nacos.core.notify.listener.Subscribe;
import com.alibaba.nacos.core.utils.DisruptorFactory;
import com.lmax.disruptor.EventFactory;
import com.lmax.disruptor.EventHandler;
import com.lmax.disruptor.EventTranslator;
import com.lmax.disruptor.dsl.Disruptor;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
@SuppressWarnings("all")
public class NotifyManager {

    private static final Map<String, Publisher> PUBLISHER_MAP = new ConcurrentHashMap<>(16);

    /**
     *
     * @param eventType
     * @param consumer
     * @param <T>
     */
    public static <T> void registerSubscribe(final Subscribe consumer) {
        final String topic = consumer.subscribeType().getCanonicalName();
        if (PUBLISHER_MAP.containsKey(topic)) {
            Publisher publisher = PUBLISHER_MAP.get(topic);
            publisher.addSubscribe(consumer);
        }
    }

    /**
     *
     * @param consumer
     * @param <T>
     */
    public static <T> void deregisterSubscribe(final Subscribe consumer) {
        final String topic = consumer.subscribeType().getCanonicalName();
        if (PUBLISHER_MAP.containsKey(topic)) {
            Publisher publisher = PUBLISHER_MAP.get(topic);
            publisher.unSubscribe(consumer);
        }
    }

    /**
     *
     * @param eventType
     * @param event
     */
    public static void publishEvent(final Class<? extends Event> eventType,
                                    final Event event) {
        final String topic = eventType.getCanonicalName();
        if (PUBLISHER_MAP.containsKey(topic)) {
            Publisher publisher = PUBLISHER_MAP.get(topic);
            publisher.publish(event);
        }
    }

    /**
     *
     * @param supplier
     * @param eventType
     * @return
     */
    public static Publisher registerPublisher(final Supplier<? extends Event> supplier,
                                              final Class<? extends Event> eventType) {
        final String topic = eventType.getCanonicalName();
        PUBLISHER_MAP.computeIfAbsent(topic, s -> {
            Publisher publisher = new Publisher(supplier, eventType);
            return publisher;
        });
        return PUBLISHER_MAP.get(topic);
    }

    private static class Publisher {

        private final Disruptor<EventHandle> disruptor;

        private final Class<? extends Event> eventType;

        private final CopyOnWriteArraySet<Subscribe> subscribes = new CopyOnWriteArraySet<>();

        public Publisher(final Supplier<? extends Event> supplier,
                         final Class<? extends Event> eventType) {
            this.eventType = eventType;

            this.disruptor = DisruptorFactory.build((EventFactory) () -> {
                return new EventHandle(supplier.get());
            }, eventType);
            this.disruptor.handleEventsWith(new EventHandler<EventHandle>() {
                @Override
                public void onEvent(EventHandle handle, long sequence, boolean endOfBatch) throws Exception {
                    for (Subscribe subscribe : subscribes) {
                        final Runnable job = () -> subscribe.onEvent(handle.getEvent());
                        final Executor executor = subscribe.executor();
                        if (Objects.nonNull(executor)) {
                            executor.execute(job);
                        } else {
                            job.run();
                        }
                    }
                }
            });
            this.disruptor.start();
        }

        void addSubscribe(Subscribe subscribe) {
            subscribes.add(subscribe);
        }

        void unSubscribe(Subscribe subscribe) {
            subscribes.remove(subscribe);
        }

        void publish(Event event) {
            this.disruptor.publishEvent(new EventTranslator<EventHandle>() {
                @Override
                public void translateTo(EventHandle eventHandle, long sequence) {
                    eventHandle.setEvent(event);
                }
            });
        }

    }

}
