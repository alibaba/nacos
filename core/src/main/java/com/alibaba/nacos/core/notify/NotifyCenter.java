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
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
@SuppressWarnings("all")
public class NotifyCenter {

    private static final Map<String, Publisher> PUBLISHER_MAP = new ConcurrentHashMap<>(16);

    static {

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            PUBLISHER_MAP.forEach(new BiConsumer<String, Publisher>() {
                @Override
                public void accept(String s, Publisher publisher) {
                    publisher.shutdown();
                }
            });
        }));

    }

    /**
     * Register a Subscriber. If the Publisher concerned by the
     * Subscriber does not exist, then PublihserMap will preempt
     * a placeholder Publisher first. not call {@link Publisher#start()}
     *
     * @param eventType Types of events that Subscriber cares about
     * @param consumer  subscriber
     * @param <T>       event type
     */
    public static <T> void registerSubscribe(final Subscribe consumer) {
        final String topic = consumer.subscribeType().getCanonicalName();
        PUBLISHER_MAP.computeIfAbsent(topic, s -> new Publisher(consumer.subscribeType()));
        Publisher publisher = PUBLISHER_MAP.get(topic);
        publisher.addSubscribe(consumer);
    }

    /**
     * deregister subscriber
     *
     * @param consumer subscriber
     * @param <T>
     */
    public static <T> void deregisterSubscribe(final Subscribe consumer) {
        final String topic = consumer.subscribeType().getCanonicalName();
        if (PUBLISHER_MAP.containsKey(topic)) {
            Publisher publisher = PUBLISHER_MAP.get(topic);
            publisher.unSubscribe(consumer);
            return;
        }
        throw new NoSuchElementException("The subcriber has no event publisher");
    }

    /**
     * request publisher publish event
     *
     * @param event
     */
    public static void publishEvent(final Event event) {
        publishEvent(event.eventType(), event);
    }

    /**
     * request publisher publish event
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
            return;
        }
        throw new NoSuchElementException("There are no event publishers for this event, please register");
    }

    /**
     * register publisher
     *
     * @param supplier
     * @param eventType
     * @return
     */
    public static Publisher registerPublisher(final Supplier<? extends Event> supplier,
                                              final Class<? extends Event> eventType) {
        final String topic = eventType.getCanonicalName();
        PUBLISHER_MAP.computeIfAbsent(topic, s -> {
            Publisher publisher = new Publisher(eventType);
            return publisher;
        });
        Publisher publisher = PUBLISHER_MAP.get(topic);
        publisher.setSupplier(supplier);
        publisher.start();
        return publisher;
    }

    private static class Publisher {

        private final Class<? extends Event> eventType;
        private final CopyOnWriteArraySet<Subscribe> subscribes = new CopyOnWriteArraySet<>();
        private final AtomicBoolean initialized = new AtomicBoolean(false);
        private Disruptor<EventHandle> disruptor;
        private Supplier<? extends Event> supplier;
        private volatile boolean canOpen = false;

        public Publisher(final Class<? extends Event> eventType) {
            this.eventType = eventType;
        }

        void setSupplier(Supplier<? extends Event> supplier) {
            this.supplier = supplier;
        }

        void start() {
            if (initialized.compareAndSet(false, true)) {
                this.disruptor = DisruptorFactory.build((EventFactory) () -> {
                    return new EventHandle(supplier.get());
                }, eventType);
                openEventHandler();
                this.disruptor.start();
            }
        }

        void openEventHandler() {
            this.disruptor.handleEventsWith(new EventHandler<EventHandle>() {
                @Override
                public void onEvent(EventHandle handle, long sequence, boolean endOfBatch) throws Exception {

                    // To ensure that messages are not lost, enable EventHandler when
                    // waiting for the first Subscriber to register

                    for (; ; ) {
                        if (canOpen) {
                            break;
                        }
                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException ignore) {
                            Thread.interrupted();
                        }
                    }

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
        }

        void addSubscribe(Subscribe subscribe) {
            subscribes.add(subscribe);
            canOpen = true;
        }

        void unSubscribe(Subscribe subscribe) {
            subscribes.remove(subscribe);
        }

        void publish(Event event) {
            checkIsStart();
            this.disruptor.publishEvent(new EventTranslator<EventHandle>() {
                @Override
                public void translateTo(EventHandle eventHandle, long sequence) {
                    eventHandle.setEvent(event);
                }
            });
        }

        void checkIsStart() {
            if (!initialized.get()) {
                throw new IllegalStateException("Publisher does not start");
            }
        }

        void shutdown() {
            disruptor.shutdown();
        }

    }

}
