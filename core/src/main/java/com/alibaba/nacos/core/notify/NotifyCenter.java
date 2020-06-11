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

import com.alibaba.nacos.common.JustForTest;
import com.alibaba.nacos.common.utils.ConcurrentHashSet;
import com.alibaba.nacos.common.utils.ShutdownUtils;
import com.alibaba.nacos.core.notify.listener.SmartSubscribe;
import com.alibaba.nacos.core.notify.listener.Subscribe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
@SuppressWarnings("all")
public class NotifyCenter {

	private static final Logger LOGGER = LoggerFactory.getLogger(NotifyCenter.class);

	public static int RING_BUFFER_SIZE = 16384;

	public static int SHATE_BUFFER_SIZE = 1024;

	private static final AtomicBoolean CLOSED = new AtomicBoolean(false);

	private static BiFunction<Class<? extends Event>, Integer, EventPublisher> BUILD_FACTORY = null;

	private static final NotifyCenter INSTANCE = new NotifyCenter();

	private EventPublisher sharePublisher;

	/**
	 * Publisher management container
	 */
	private final Map<String, EventPublisher> publisherMap = new ConcurrentHashMap<>(16);

	/**
	 * Multi-event listening list
	 */
	private final Set<SmartSubscribe> smartSubscribes = new ConcurrentHashSet<>();

	static {
		// Internal ArrayBlockingQueue buffer size. For applications with high write throughput,
		// this value needs to be increased appropriately. default value is 16384
		String ringBufferSizeProperty = "nacos.core.notify.ring-buffer-size";
		RING_BUFFER_SIZE = Integer.getInteger(ringBufferSizeProperty, 16384);

		// The size of the public publisher's message staging queue buffer
		String shareBufferSizeProperty = "nacos.core.notify.share-buffer-size";
		SHATE_BUFFER_SIZE = Integer.getInteger(shareBufferSizeProperty, 1024);

		ServiceLoader<EventPublisher> loader = ServiceLoader.load(EventPublisher.class);
		Iterator<EventPublisher> iterator = loader.iterator();

		if (iterator.hasNext()) {
			BUILD_FACTORY = (cls, buffer) -> {
				loader.reload();
				EventPublisher publisher = ServiceLoader.load(EventPublisher.class).iterator().next();
				publisher.init(cls, buffer);
				return publisher;
			};
		} else {
			BUILD_FACTORY = (cls, buffer) -> {
				EventPublisher publisher = new DefaultPublisher();
				publisher.init(cls, buffer);
				return publisher;
			};
		}

		INSTANCE.sharePublisher = BUILD_FACTORY.apply(SlowEvent.class, SHATE_BUFFER_SIZE);
		ShutdownUtils.addShutdownHook(new Thread(() -> {
			shutdown();
		}));

	}

	@JustForTest
	public static Map<String, EventPublisher> getPublisherMap() {
		return INSTANCE.publisherMap;
	}

	@JustForTest
	public static EventPublisher getPublisher(Class<? extends Event> topic) {
		if (SlowEvent.class.isAssignableFrom(topic)) {
			return INSTANCE.sharePublisher;
		}
		return INSTANCE.publisherMap.get(topic.getCanonicalName());
	}

	@JustForTest
	public static Set<SmartSubscribe> getSmartSubscribes() {
		return EventPublisher.SMART_SUBSCRIBES;
	}

	@JustForTest
	public static EventPublisher getSharePublisher() {
		return INSTANCE.sharePublisher;
	}

	private static final AtomicBoolean closed = new AtomicBoolean(false);

	public static void shutdown() {
		if (!closed.compareAndSet(false, true)) {
			return;
		}
		LOGGER.warn("[NotifyCenter] Start destroying Publisher");
		try {
			INSTANCE.publisherMap.forEach(new BiConsumer<String, EventPublisher>() {
				@Override
				public void accept(String s, EventPublisher publisher) {
					publisher.shutdown();
				}
			});

			INSTANCE.sharePublisher.shutdown();
		}
		catch (Throwable e) {
			LOGGER.error("NotifyCenter shutdown has error : {}", e);
		}
		LOGGER.warn("[NotifyCenter] Destruction of the end");
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
		final Class<? extends Event> cls = consumer.subscribeType();
		// If you want to listen to multiple events, you do it separately,
		// without automatically registering the appropriate publisher
		if (consumer instanceof SmartSubscribe) {
			EventPublisher.SMART_SUBSCRIBES.add((SmartSubscribe) consumer);
			return;
		}
		// If the event does not require additional queue resources,
		// go to share-publisher to reduce resource waste
		if (SlowEvent.class.isAssignableFrom(cls)) {
			INSTANCE.sharePublisher.addSubscribe(consumer);
			return;
		}
		final String topic = consumer.subscribeType().getCanonicalName();
		INSTANCE.publisherMap.computeIfAbsent(topic, s -> BUILD_FACTORY.apply(cls, RING_BUFFER_SIZE));
		EventPublisher publisher = INSTANCE.publisherMap.get(topic);
		publisher.addSubscribe(consumer);
	}

	/**
	 * deregister subscriber
	 *
	 * @param consumer subscriber
	 * @param <T>
	 */
	public static <T> void deregisterSubscribe(final Subscribe consumer) {
		final Class<? extends Event> cls = consumer.subscribeType();
		if (consumer instanceof SmartSubscribe) {
			EventPublisher.SMART_SUBSCRIBES.remove((SmartSubscribe) consumer);
			return;
		}
		if (SlowEvent.class.isAssignableFrom(cls)) {
			INSTANCE.sharePublisher.unSubscribe(consumer);
			return;
		}
		final String topic = consumer.subscribeType().getCanonicalName();
		if (INSTANCE.publisherMap.containsKey(topic)) {
			EventPublisher publisher = INSTANCE.publisherMap.get(topic);
			publisher.unSubscribe(consumer);
			return;
		}
		throw new NoSuchElementException("The subcriber has no event publisher");
	}

	/**
	 * request publisher publish event
	 * Publishers load lazily, calling publisher. Start () only when the event is actually published
	 *
	 * @param event
	 */
	public static boolean publishEvent(final Event event) {
		try {
			return publishEvent(event.getClass(), event);
		} catch (Throwable ex) {
			LOGGER.error("There was an exception to the message publishing : {}", ex);
			return false;
		}
	}

	/**
	 * request publisher publish event
	 * Publishers load lazily, calling publisher. Start () only when the event is actually published
	 *
	 * @param eventType
	 * @param event
	 */
	private static boolean publishEvent(final Class<? extends Event> eventType,
			final Event event) {
		final String topic = eventType.getCanonicalName();
		if (SlowEvent.class.isAssignableFrom(eventType)) {
			return INSTANCE.sharePublisher.publish(event);
		}

		if (INSTANCE.publisherMap.containsKey(topic)) {
			EventPublisher publisher = INSTANCE.publisherMap.get(topic);
			return publisher.publish(event);
		}
		throw new NoSuchElementException(
				"There are no [" + topic + "] publishers for this event, please register");
	}

	/**
	 * register to share-publisher
	 *
	 * @param supplier
	 * @param eventType
	 * @return
	 */
	public static EventPublisher registerToSharePublisher(
			final Class<? extends SlowEvent> eventType) {
		return INSTANCE.sharePublisher;
	}

	/**
	 * register publisher
	 *
	 * @param supplier
	 * @param eventType
	 * @param queueMaxSize
	 * @return
	 */
	public static EventPublisher registerToPublisher(final Class<? extends Event> eventType,
			final int queueMaxSize) {

		if (SlowEvent.class.isAssignableFrom(eventType)) {
			return INSTANCE.sharePublisher;
		}

		final String topic = eventType.getCanonicalName();
		INSTANCE.publisherMap.computeIfAbsent(topic, s -> BUILD_FACTORY.apply(eventType, queueMaxSize));
		EventPublisher publisher = INSTANCE.publisherMap.get(topic);
		return publisher;
	}

	/**
	 * deregister publisher
	 *
	 * @param eventType
	 * @return
	 */
	public static void deregisterPublisher(final Class<? extends Event> eventType) {
		final String topic = eventType.getCanonicalName();
		EventPublisher publisher = INSTANCE.publisherMap.remove(topic);
		publisher.shutdown();
	}

}
