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
import com.alibaba.nacos.core.utils.Loggers;

import java.util.HashSet;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.Supplier;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
@SuppressWarnings("all")
public class NotifyCenter {

	// Internal ArrayBlockingQueue buffer size. For applications with high write throughput,
	// this value needs to be increased appropriately. default value is 16384

	public static int RING_BUFFER_SIZE = 16384;

	static {
		String ringBufferSizeProperty = "com.alibaba.nacos.core.notify.ringBufferSize";
		String val = System.getProperty(ringBufferSizeProperty, "16384");
		RING_BUFFER_SIZE = Integer.parseInt(val);
	}

	private static final Map<String, Publisher> PUBLISHER_MAP = new ConcurrentHashMap<>(
			16);

	private static final Set<SmartSubscribe> SMART_SUBSCRIBES = new ConcurrentHashSet<>();

	private static final Publisher SHARE_PUBLISHER = new Publisher(SlowEvent.class,
			new BiPredicate<Event, Subscribe>() {
				@Override
				public boolean test(Event event, Subscribe subscribe) {
					final String sourceName = event.getClass().getCanonicalName();
					if (subscribe instanceof SmartSubscribe) {
						return true;
					}
					final String targetName = subscribe.subscribeType()
							.getCanonicalName();
					return Objects.equals(sourceName, targetName);
				}
			}, 1024);

	@JustForTest
	public static Map<String, Publisher> getPublisherMap() {
		return PUBLISHER_MAP;
	}

	@JustForTest
	public static Set<SmartSubscribe> getSmartSubscribes() {
		return SMART_SUBSCRIBES;
	}

	@JustForTest
	public static Publisher getSharePublisher() {
		return SHARE_PUBLISHER;
	}

	private static boolean stopDeferPublish = false;

	static {
		SHARE_PUBLISHER.setSupplier(() -> null);
		SHARE_PUBLISHER.start();

		ShutdownUtils.addShutdownHook(new Thread(() -> {
			System.out.println("[NotifyCenter] Start destroying Publisher");
			try {
				PUBLISHER_MAP.forEach(new BiConsumer<String, Publisher>() {
					@Override
					public void accept(String s, Publisher publisher) {
						publisher.shutdown();
					}
				});

				SHARE_PUBLISHER.shutdown();
			}
			catch (Exception e) {
				e.printStackTrace();
			}
			System.out.println("[NotifyCenter] Destruction of the end");
		}));
	}

	public static void stopDeferPublish() {
		stopDeferPublish = true;
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
			SMART_SUBSCRIBES.add((SmartSubscribe) consumer);
			return;
		}
		// If the event does not require additional queue resources,
		// go to share-publisher to reduce resource waste
		if (SlowEvent.class.isAssignableFrom(cls)) {
			SHARE_PUBLISHER.addSubscribe(consumer);
			return;
		}
		final String topic = consumer.subscribeType().getCanonicalName();
		PUBLISHER_MAP.computeIfAbsent(topic, s -> new Publisher(cls));
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
		final Class<? extends Event> cls = consumer.subscribeType();
		if (consumer instanceof SmartSubscribe) {
			SMART_SUBSCRIBES.remove((SmartSubscribe) consumer);
			return;
		}
		if (SlowEvent.class.isAssignableFrom(cls)) {
			SHARE_PUBLISHER.unSubscribe(consumer);
			return;
		}
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
	 * Publishers load lazily, calling publisher. Start () only when the event is actually published
	 *
	 * @param event
	 */
	public static void publishEvent(final Event event) {
		publishEvent(event.getClass(), event);
	}

	/**
	 * request publisher publish event
	 * Publishers load lazily, calling publisher. Start () only when the event is actually published
	 *
	 * @param eventType
	 * @param event
	 */
	private static void publishEvent(final Class<? extends Event> eventType,
			final Event event) {
		final String topic = eventType.getCanonicalName();

		if (SlowEvent.class.isAssignableFrom(eventType)) {
			SHARE_PUBLISHER.publish(event);
			return;
		}

		if (PUBLISHER_MAP.containsKey(topic)) {
			Publisher publisher = PUBLISHER_MAP.get(topic);
			if (!publisher.isInitialized()) {
				publisher.start();
			}
			publisher.publish(event);
			return;
		}
		throw new NoSuchElementException(
				"There are no event publishers for this event, please register");
	}

	/**
	 * register to share-publisher
	 *
	 * @param supplier
	 * @param eventType
	 * @return
	 */
	public static Publisher registerToSharePublisher(
			final Supplier<? extends SlowEvent> supplier,
			final Class<? extends SlowEvent> eventType) {
		return SHARE_PUBLISHER;
	}

	/**
	 * register publisher
	 *
	 * @param supplier
	 * @param eventType
	 * @param queueMaxSize
	 * @return
	 */
	public static Publisher registerToPublisher(final Supplier<? extends Event> supplier,
			final Class<? extends Event> eventType, final int queueMaxSize) {
		final String topic = eventType.getCanonicalName();
		PUBLISHER_MAP.computeIfAbsent(topic, s -> {
			Publisher publisher = new Publisher(eventType, queueMaxSize);
			return publisher;
		});
		Publisher publisher = PUBLISHER_MAP.get(topic);
		publisher.queueMaxSize = queueMaxSize;
		publisher.setSupplier(supplier);
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
		Publisher publisher = PUBLISHER_MAP.remove(topic);
		publisher.shutdown();
	}

	public static class Publisher extends Thread {

		private final Class<? extends Event> eventType;
		private final CopyOnWriteArraySet<Subscribe> subscribes = new CopyOnWriteArraySet<>();
		private int queueMaxSize = -1;
		private volatile boolean initialized = false;
		private volatile boolean canOpen = false;
		private volatile boolean shutdown = false;
		private BlockingQueue<Event> queue;
		private Supplier<? extends Event> supplier;
		private long lastEventSequence = -1L;

		// judge the subscribe can deal Event

		private BiPredicate<Event, Subscribe> filter = new BiPredicate<Event, Subscribe>() {
			@Override
			public boolean test(Event event, Subscribe subscribe) {
				return true;
			}
		};

		Publisher(final Class<? extends Event> eventType) {
			this.eventType = eventType;
		}

		Publisher(final Class<? extends Event> eventType, final int queueMaxSize) {
			this.eventType = eventType;
			this.queueMaxSize = queueMaxSize;
			this.queue = new ArrayBlockingQueue<>(queueMaxSize);
		}

		Publisher(final Class<? extends Event> eventType,
				BiPredicate<Event, Subscribe> filter, final int queueMaxSize) {
			this.eventType = eventType;
			this.filter = filter;
			this.queueMaxSize = queueMaxSize;
			this.queue = new ArrayBlockingQueue<>(queueMaxSize);
		}

		void setSupplier(Supplier<? extends Event> supplier) {
			this.supplier = supplier;
		}

		public CopyOnWriteArraySet<Subscribe> getSubscribes() {
			return subscribes;
		}

		@Override
		public synchronized void start() {
			super.start();
			if (!initialized) {
				if (queueMaxSize == -1) {
					queueMaxSize = RING_BUFFER_SIZE;
				}
				initialized = true;
			}
		}

		@Override
		public void run() {
			openEventHandler();
		}

		void openEventHandler() {
			try {
				for (; ; ) {

					if (shutdown) {
						break;
					}
					// To ensure that messages are not lost, enable EventHandler when
					// waiting for the first Subscriber to register
					for (; ; ) {
						if (shutdown || canOpen || stopDeferPublish) {
							break;
						}
						try {
							Thread.sleep(1000);
						}
						catch (InterruptedException ignore) {
							Thread.interrupted();
						}
					}
					Set<Subscribe> tmp = new HashSet<>();
					tmp.addAll(SMART_SUBSCRIBES);
					tmp.addAll(subscribes);

					final Event event = queue.take();
					final long currentEventSequence = event.sequence();

					for (Subscribe subscribe : tmp) {

						// Determines whether the event is acceptable to this subscriber
						if (!filter.test(event, subscribe)) {
							continue;
						}

						// If you are a multi-event listener, you need to make additional logical judgments
						if (subscribe instanceof SmartSubscribe) {
							if (!((SmartSubscribe) subscribe).canNotify(event)) {
								continue;
							}
						}

						// Whether to ignore expiration events
						if (subscribe.ignoreExpireEvent() && lastEventSequence > currentEventSequence) {
							continue;
						}

						final Runnable job = () -> subscribe.onEvent(event);
						final Executor executor = subscribe.executor();
						if (Objects.nonNull(executor)) {
							executor.execute(job);
						}
						else {
							try {
								job.run();
							}
							catch (Throwable e) {
								Loggers.CORE.error("Event callback exception : {}", e);
							}
						}
					}

					lastEventSequence = currentEventSequence;
				}
			}
			catch (Throwable ex) {
                Loggers.CORE.error("Event listener exception : {}", ex);
			}
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
			this.queue.offer(event);
		}

		void checkIsStart() {
			if (!initialized) {
				throw new IllegalStateException("Publisher does not start");
			}
		}

		void shutdown() {
			this.shutdown = true;
			this.queue.clear();
		}

		public boolean isInitialized() {
			return initialized;
		}
	}

}
